<div align="center"\>
<h1>TCS Compliance Engine (AWS Lambda)</h1>
<p>Serverless Java AWS Lambda engine for real-time cross-border remittance compliance. Processes SQS events to enforce LRS limits, dynamically calculate TCS tax, and execute high-throughput transactional database updates using optimistic locking.</p>

</div>

-----

### 🔗 Related Repositories

This is a decoupled microservice. It is designed to work in tandem with the core API.

  * **[Core Remittance API ↗](https://github.com/allamrakesh888/tax-compliance-engine)** - The Spring Boot application that exposes the REST endpoints, persists the initial transactions, and pushes events to SQS.

-----

### 1. High-Level Overview

This repository contains the serverless function responsible for enforcing Indian tax and regulatory compliance on international money transfers.

Instead of blocking the primary API during heavy database aggregations, the main system offloads the transaction ID to an Amazon SQS queue. This Lambda function consumes that event, calculates the required Tax Collected at Source (TCS), and updates the final PostgreSQL ledger inside an isolated AWS Virtual Private Cloud (VPC).

### 2. Core Business Logic (LRS & TCS Rules)

Financial backend systems require strict adherence to regulatory math. This function enforces the Liberalised Remittance Scheme (LRS) limits and applies a dynamic Tax Collected at Source (TCS) based on the specific nature of the transfer.

The Lambda executes the following logical flow:

Aggregates Annual Spend: Queries the database for the sender's total completed remittances for the current financial year.

Limit Validation: Checks if the new transaction pushes the user over the ₹10 Lakhs per year base LRS threshold.

Purpose-Driven Tax Calculation: The tax engine does not apply a flat rate. It evaluates the transaction against 5 primary regulatory purpose codes to calculate the exact TCS liability:

Overseas Education (Loan Financed): Applies the lowest concessional TCS rate (0.5%) for amounts exceeding the limit.

Overseas Education (Self-Funded): Applies a standard concessional rate (5%) for amounts exceeding the limit.

Medical Treatment: Applies the medical necessity rate (5%) for amounts exceeding the limit.

Overseas Tour Packages: Applies a tiered penalty rate depending on whether the amount falls under or over the threshold.

Other Purposes (Investments, Gifts, Maintenance): Applies the maximum standard rate (20%) for amounts exceeding the limit.

Ledger Execution: The engine computes the final tax liability, updates the specific transaction row in PostgreSQL with both the tax amount and the applied purpose code, and transitions the status to COMPLETED.

> [\!TIP]  
> **Idempotency:** The Lambda function is designed to be fully idempotent. If a network timeout causes SQS to redeliver the same message, the function checks the database state and exits without double-charging the customer's tax ledger.

### 3. Architecture & Cloud Security

Because this function handles sensitive tax data and connects directly to the master database, it does not execute in the standard, public AWS Lambda environment.

  * **VPC Injection:** The function is deployed directly into the `tax-compliance-cluster` VPC and assigned to the isolated Private Subnets.
  * **Security Group Boundaries:** The Lambda utilizes a dedicated Security Group. The RDS PostgreSQL database contains strict inbound rules to accept port `5432` traffic *only* from this specific Lambda Security Group and the EKS Node Security Group.
  * **IAM Least Privilege:** The execution role only possesses permissions to read from the specific `ComplianceCheckQueue` and create Elastic Network Interfaces (ENIs) for VPC connectivity.

### 4. SQS Event Payload Contract

The function expects a standardized JSON payload from the SQS queue, pushed by the core Spring Boot API.

```json
{
  "eventId": "EVT-847294",
  "eventType": "REMITTANCE_INITIATED",
  "timestamp": "2026-04-07T12:00:00Z",
  "payload": {
    "transactionId": "TXN-9948271A",
    "senderId": "USR-84729",
    "amount": 50000.00,
    "currency": "INR"
  }
}
```

### 5. Local Setup & Deployment

<details>
<summary><b>Click to expand: Local Development Instructions</b></summary>

  <br>
  To test the business logic locally without deploying to AWS:

1.  Ensure you have the [AWS SAM CLI](https://aws.amazon.com/serverless/sam/) installed.
2.  Start your local PostgreSQL tunnel (as defined in the Core API repository).
3.  Build the function:
    ```bash
    sam build
    ```
4.  Invoke the function locally by passing a mock SQS event:
    ```bash
    sam local invoke TaxComplianceFunction --event events/mock-sqs-event.json
    ```

</details>

#### AWS Deployment

To package and deploy the updated function to your AWS environment, use the standard AWS CLI tools. Ensure your IAM user has permissions to update Lambda code.

```bash
# Package the Java application
mvn clean package

# Update the Lambda function code directly
aws lambda update-function-code \
    --function-name TaxComplianceEngine \
    --zip-file fileb://target/tax-compliance-lambda-1.0-SNAPSHOT.jar \
    --region us-east-1
```

### 6. Future Optimizations

  * [ ] **AWS Secrets Manager Integration:** Migrate the hardcoded RDS database credentials from environment variables to an encrypted Secrets Manager string retrieved at runtime.
  * [ ] **Dead Letter Queue (DLQ):** Attach a DLQ to the Lambda function to capture and alert on any transactions that fail processing after 3 retries due to database connection timeouts.
