package com.crossborder.remittance.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Collections;

class ComplianceEngineLambdaTest {

	// Test case only for local lambda testing
	@Test
	void testLambdaLocally() throws Exception {

		// String DB_URL = "jdbc:postgresql://localhost:5432/tax_compliance_engine";
		// String DB_USER = "rakesh";
		// String DB_PASS = "";

		// Create the exact JSON payload your Spring Boot app will send
		String jsonPayload = """
				{
				  "transactionId": "322374a2-6e85-4e65-9cf6-e32fcc9caf53",
				  "userId": "9a63884b-3f79-4b04-ac4c-7b45b477d5ff",
				  "purposeCode": "S0305-EDUCATION_LOAN",
				  "amount": "1200000.00"
				}
				""";

		// Mock the SQS Event
		SQSEvent.SQSMessage message = new SQSEvent.SQSMessage();
		message.setBody(jsonPayload);

		SQSEvent event = new SQSEvent();
		event.setRecords(Collections.singletonList(message));

		// 4. Mock the AWS Context (so context.getLogger() doesn't throw a
		// NullPointerException)
		Context context = Mockito.mock(Context.class);
		LambdaLogger lambdaLogger = Mockito.mock(LambdaLogger.class);

		Mockito.when(context.getLogger()).thenReturn(lambdaLogger);

		// Trigger the Lambda!
		ComplianceEngineLambda lambda = new ComplianceEngineLambda();
		lambda.handleRequest(event, context);

		System.out.println("Local test execution finished. Check your database!");
	}
}
