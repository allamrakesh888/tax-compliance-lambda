package com.crossborder.remittance.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;

public class CommonUtil {

   //format "2025-26" or "2024-25"
    
   public static String getCurrentFinancialYear() {
	   
		LocalDate today = LocalDate.now(ZoneId.of("Asia/Kolkata"));

		int currentYear = today.getYear();
		int currentMonth = today.getMonthValue(); // Jan = 1, April = 4

		int startYear;
		int endYear;

		if (currentMonth < 4) {
			startYear = currentYear - 1;
			endYear = currentYear;
		} else {
			startYear = currentYear;
			endYear = currentYear + 1;
		}

		// 2026 becomes 26
		String endYearShort = String.format("%02d", endYear % 100);

		return startYear + "-" + endYearShort;
   }
   
   
	public static BigDecimal calculateTCS(BigDecimal currentRemitted, BigDecimal remitAmount, String purpose) {

		BigDecimal LRS_THRESHOLD = new BigDecimal("1000000.00");

		BigDecimal newTotal = currentRemitted.add(remitAmount);

		// Fully Exempt Category
		if ("S0305-EDUCATION_LOAN".equalsIgnoreCase(purpose)) {
			return BigDecimal.ZERO;
		}

		// Rule: 5% up to 10L, 20% above 10L - Travel & Tourism
		if ("S0306-TRAVEL_TOURISM".equalsIgnoreCase(purpose)) {
			BigDecimal tax;
			if (currentRemitted.compareTo(LRS_THRESHOLD) >= 0) {
				// Entirely in the 20% bracket
				tax = remitAmount.multiply(new BigDecimal("0.20"));
			} else if (newTotal.compareTo(LRS_THRESHOLD) <= 0) {
				// Entirely in the 5% bracket
				tax = remitAmount.multiply(new BigDecimal("0.05"));
			} else {
				// Transaction crosses the threshold! Split it into two brackets.
				BigDecimal amountAt5Percent = LRS_THRESHOLD.subtract(currentRemitted);
				BigDecimal amountAt20Percent = newTotal.subtract(LRS_THRESHOLD);

				BigDecimal taxAt5 = amountAt5Percent.multiply(new BigDecimal("0.05"));
				BigDecimal taxAt20 = amountAt20Percent.multiply(new BigDecimal("0.20"));

				tax = taxAt5.add(taxAt20);
			}
			return tax.setScale(2, RoundingMode.HALF_UP);
		}

		// 3. Standard Exemption Categories (0% up to 10L, specific % above 10L)
		// If the total hasn't crossed 10L yet, no tax is applied.
		if (newTotal.compareTo(LRS_THRESHOLD) <= 0) {
			return BigDecimal.ZERO;
		}

		// Calculate exactly how much of THIS transaction falls into the taxable zone
		BigDecimal taxableAmount;
		if (currentRemitted.compareTo(LRS_THRESHOLD) > 0) {
			taxableAmount = remitAmount; // The user was already over 10L
		} else {
			taxableAmount = newTotal.subtract(LRS_THRESHOLD); // Only tax the spillover
		}

		// 4. Apply the specific tax rate for the remaining purpose codes
		BigDecimal taxRate;
		if ("S0305-EDUCATION_SELF_FUNDED".equalsIgnoreCase(purpose)
				|| "S0304-MEDICAL_TREATMENT".equalsIgnoreCase(purpose)) {
			taxRate = new BigDecimal("0.05"); // 5%
		} else {
			// Applies to S0001-INVESTMENT_ABROAD, S1301-FAMILY_MAINTENANCE, and default
			// fallback
			taxRate = new BigDecimal("0.20"); // 20%
		}

		return taxableAmount.multiply(taxRate).setScale(2, RoundingMode.HALF_UP);
	}
}
