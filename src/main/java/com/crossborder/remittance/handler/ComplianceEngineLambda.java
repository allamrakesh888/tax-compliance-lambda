package com.crossborder.remittance.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.crossborder.remittance.util.CommonUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

public class ComplianceEngineLambda implements RequestHandler<SQSEvent, Void> {

    private static final ObjectMapper mapper = new ObjectMapper();
    //private static final String DB_URL = System.getenv("DB_URL");
    //private static final String DB_USER = System.getenv("DB_USER");
    //private static final String DB_PASS = System.getenv("DB_PASS");
    
    private static final String DB_URL = "jdbc:postgresql://localhost:5432/tax_compliance_engine";
    private static final String DB_USER = "rakesh";
    private static final String DB_PASS = "";
    
    //private static final BigDecimal LRS_THRESHOLD = new BigDecimal("1000000.00");

    @Override
    public Void handleRequest(SQSEvent event, Context context) {
        
        for (SQSEvent.SQSMessage message : event.getRecords()) {
            try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS)) {
                
                conn.setAutoCommit(false); 

                try {
                    JsonNode payload = mapper.readTree(message.getBody());
                    UUID transactionId = UUID.fromString(payload.get("transactionId").asText());
                    UUID userId = UUID.fromString(payload.get("userId").asText());
                    String purposeCode = payload.get("purposeCode").asText();
                    BigDecimal remitAmount = new BigDecimal(payload.get("amount").asText());
                    
                    String financialYear = CommonUtil.getCurrentFinancialYear(); //ex-2025-26
                    

                    //Fetch currentRemitted amount from ledger table 
                    BigDecimal currentRemitted = BigDecimal.ZERO;
                    int expectedVersion = 1;
                    boolean ledgerExists = false;

                    String selectLedger = "SELECT total_remitted_amount, version FROM fy_ledgers WHERE user_id=? AND financial_year=? AND purpose_code=?";
                    try (PreparedStatement ps = conn.prepareStatement(selectLedger)) {
                        ps.setObject(1, userId);
                        ps.setString(2, financialYear);
                        ps.setString(3, purposeCode);
                        ResultSet rs = ps.executeQuery();
                        if (rs.next()) {
                            ledgerExists = true;
                            currentRemitted = rs.getBigDecimal("total_remitted_amount");
                            expectedVersion = rs.getInt("version"); // Store the version we read
                        }
                    }

                    //Calculate TCS
                    BigDecimal tcsAmount = CommonUtil.calculateTCS(currentRemitted, remitAmount, purposeCode);
                    BigDecimal totalDeduction = remitAmount.add(tcsAmount);

                    //TODO -- continue from here
                    
                    // 3. Atomic Balance Deduction (Optimistic Check for Insufficient Funds)
                    // We don't need a SELECT FOR UPDATE here. We just enforce the balance check in the UPDATE itself.
                    String updateUser = "UPDATE users SET balance = balance - ? WHERE user_id = ? AND balance >= ?";
                    try (PreparedStatement ps = conn.prepareStatement(updateUser)) {
                        ps.setBigDecimal(1, totalDeduction);
                        ps.setObject(2, userId);
                        ps.setBigDecimal(3, totalDeduction);
                        
                        int userUpdated = ps.executeUpdate();
                        if (userUpdated == 0) {
                            // If 0 rows updated, they either don't exist or don't have enough money.
                            updateTransactionStatus(conn, transactionId, "REJECTED_INSUFFICIENT_FUNDS");
                            conn.commit();
                            context.getLogger().log("Transaction rejected: Insufficient funds.");
                            continue; 
                        }
                    }

                    // 4. Upsert the Ledger (THE OPTIMISTIC LOCK)
                    BigDecimal newTotalRemitted = currentRemitted.add(remitAmount);
                    
                    if (ledgerExists) {
                        // Notice the crucial additions to the WHERE clause!
                        String updateLedger = "UPDATE fy_ledgers SET total_remitted_amount=?, version=? WHERE user_id=? AND financial_year=? AND purpose_code=? AND version=?";
                        try (PreparedStatement ps = conn.prepareStatement(updateLedger)) {
                            ps.setBigDecimal(1, newTotalRemitted);
                            ps.setInt(2, expectedVersion + 1); // Increment version
                            ps.setObject(3, userId);
                            ps.setString(4, financialYear);
                            ps.setString(5, purposeCode);
                            ps.setInt(6, expectedVersion);     // Require the old version to match
                            
                            int ledgerUpdated = ps.executeUpdate();
                            if (ledgerUpdated == 0) {
                                // OPTIMISTIC LOCK FAILURE! Someone else updated this exact row since we read it.
                                throw new IllegalStateException("Optimistic Lock Exception: Ledger version mismatch.");
                            }
                        }
                    } else {
                        String insertLedger = "INSERT INTO fy_ledgers (user_id, financial_year, purpose_code, total_remitted_amount, version) VALUES (?, ?, ?, ?, 1)";
                        try (PreparedStatement ps = conn.prepareStatement(insertLedger)) {
                            ps.setObject(1, userId);
                            ps.setString(2, financialYear);
                            ps.setString(3, purposeCode);
                            ps.setBigDecimal(4, remitAmount);
                            ps.executeUpdate();
                        } catch (SQLException e) {
                            // If two threads try to INSERT the exact same missing ledger simultaneously,
                            // one will fail with a Unique Constraint Violation. We treat this as a concurrent conflict.
                            if ("23505".equals(e.getSQLState())) { // PostgreSQL unique_violation code
                                throw new IllegalStateException("Concurrent Insert Conflict.", e);
                            }
                            throw e;
                        }
                    }

                    // 5. Mark Transaction as APPROVED
                    updateTransactionStatus(conn, transactionId, "APPROVED");

                    conn.commit();
                    context.getLogger().log("Successfully processed transaction: " + transactionId);

                } catch (Exception e) {
                    conn.rollback();
                    context.getLogger().log("Transaction aborted. Reason: " + e.getMessage());
                    
                    // Throw to trigger the SQS 60-second retry loop!
                    throw new RuntimeException("Triggering SQS Retry due to processing error or version conflict.", e);
                }
            } catch (Exception e) {
                throw new RuntimeException("Database connection/execution failure", e);
            }
        }
        return null;
    }

    private void updateTransactionStatus(Connection conn, UUID transactionId, String status) throws Exception {
        String updateTx = "UPDATE remittance_transactions SET status = ? WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(updateTx)) {
            ps.setString(1, status);
            ps.setObject(2, transactionId);
            ps.executeUpdate();
        }
    }

   
}
