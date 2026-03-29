package com.crossborder.remittance.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Collections;

class ComplianceEngineLambdaTest {

    @Test
    void testLambdaLocally() throws Exception {
        // 1. Set Local Database Credentials (since System.getenv won't work locally without setup)
        // In a real test, you'd use a reflection utility to set these, or use a local test database.
        // For local execution, ensure your Lambda code falls back to these if env vars are null!
        String localDbUrl = "jdbc:postgresql://localhost:5433/postgres";
        String localDbUser = "postgres";
        String localDbPass = "your_password";

        // 2. Create the exact JSON payload your Spring Boot app will send
        String jsonPayload = """
            {
              "transactionId": "123e4567-e89b-12d3-a456-426614174000",
              "userId": "a1b2c3d4-e89b-12d3-a456-426614174000",
              "financialYear": "2025-2026",
              "purposeCode": "EDUCATION",
              "amount": "50000.00"
            }
            """;

        // 3. Mock the SQS Event
        SQSEvent.SQSMessage message = new SQSEvent.SQSMessage();
        message.setBody(jsonPayload);
        
        SQSEvent event = new SQSEvent();
        event.setRecords(Collections.singletonList(message));

        // 4. Mock the AWS Context (so context.getLogger() doesn't throw a NullPointerException)
        Context context = Mockito.mock(Context.class);
        //Mockito.when(context.getLogger()).thenReturn(System.out::println);

        // 5. Fire the Lambda!
        ComplianceEngineLambda lambda = new ComplianceEngineLambda();
        lambda.handleRequest(event, context);
        
        System.out.println("Local test execution finished. Check your database!");
    }
}
