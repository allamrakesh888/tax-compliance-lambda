<div align="center"\>
<h1>TCS tax Compliance Engine (AWS Lambda)</h1>
</div>

<p>Serverless Java AWS Lambda engine for real-time cross-border remittance compliance. Processes SQS events to enforce LRS limits, dynamically calculate TCS tax, and execute high-throughput transactional database updates using optimistic locking.</p>



-----

### 🔗 Related Repository

This is a decoupled microservice. It is designed to work in tandem with the core API.

  * **[Core Remittance API Repo ↗](https://github.com/allamrakesh888/tax-compliance-engine)** - The Spring Boot application that exposes the REST endpoints, persists the initial transactions, and pushes events to SQS.

-----

### 1. High-Level Overview

- This repository contains the serverless function responsible for enforcing Indian tax and regulatory compliance on international money transfers.

- Instead of blocking the primary API during heavy database aggregations, the main system offloads the transaction ID to an Amazon SQS queue. This Lambda function consumes that event, calculates the required Tax Collected at Source (TCS), and updates the final PostgreSQL ledger inside an isolated AWS Virtual Private Cloud (VPC).

<br>

### 2. Core Business Logic (LRS & TCS Rules)

Financial backend systems require strict adherence to regulatory math. This function enforces the Liberalised Remittance Scheme (LRS) limits and applies a dynamic Tax Collected at Source (TCS) based on the specific nature of the transfer.

The Lambda executes the following logical flow:

<details open>     
<summary>Click to collapse flow details</summary>  
<br> 
 
> **1. Get Annual Spend by Purpose Code(Category):** Queries the database for the sender's total completed remittances for the current financial year for the purpose code specified in the sqs event.

> **2. Limit Validation:** Checks if the new transaction pushes the user over the ₹10 Lakhs per year base LRS threshold. Transitions the status to REJECTED if the user has Insufficient account balance. 

> **3. Purpose-Driven Tax Calculation:** The tax engine does not apply a flat rate. It evaluates the transaction against 6 primary regulatory purpose codes to calculate the exact TCS liability: [S0305-EDUCATION_LOAN, S0305-EDUCATION_SELF_FUNDED, S0306-TRAVEL_TOURISM, S0304-MEDICAL_TREATMENT,S0001-INVESTMENT_ABROAD, S1301-FAMILY_MAINTENANCE]

> **4. Overseas Education (Loan Financed):** Applies the zero TCS rate (0%) for amounts exceeding the limit.

> **5. Overseas Education (Self-Funded):** Applies a standard concessional rate (5%) for amounts exceeding the limit.

> **6. Medical Treatment:** Applies the medical necessity rate (5%) for amounts exceeding the limit.

> **7. Overseas Tour Packages:** Applies a tiered penalty rate depending on whether the amount falls under or over the threshold.

> **8. Other Purposes (Investments, Gifts, Maintenance):** Applies the maximum standard rate (20%) for amounts exceeding the limit.

> **9. Ledger and Remittance record Execution:** The engine computes the final tax liability and updates the Ledgers Annual Aggregate and also updates the specific Remittance transaction row in PostgreSQL with both the tax amount and the applied purpose code, and transitions the status to APPROVED  from PENDING_COMPLIANCE_CHECK.

</details>
 
> [\!Note]  
> **Idempotency:** The Lambda function is designed to be fully idempotent. If a network timeout causes SQS to redeliver the same message, the function checks the remittance transaction status whether it is already APPROVED and exits without double-charging the customer's account balance and tax ledger.

<br>

### 3. Architecture & Cloud Security

Because this function handles sensitive tax data and connects directly to the master database, it does not execute in the standard, public AWS Lambda environment.

  * **VPC Injection:** The function is deployed directly into the `tax-compliance-cluster` VPC and assigned to the isolated Private Subnets.
  * **Security Group Boundaries:** The Lambda utilizes a dedicated Security Group. The RDS PostgreSQL database contains strict inbound rules to accept port `5432` traffic *only* from this specific Lambda Security Group and the EKS Node Security Group.
  * **IAM Least Privilege:** The execution role only possesses permissions to read from the specific `ComplianceCheckQueue` and create Elastic Network Interfaces (ENIs) for VPC connectivity.

<br> 

### 4. SQS Event Payload Contract

The function expects a standardized JSON payload from the SQS queue, pushed by the core Spring Boot API.

```json
{
   "transactionId": "322374a2-6e85-4e65-9cf6-e32fcc9caf53",
   "userId": "9a63884b-3f79-4b04-ac4c-7b45b477d5ff",
   "purposeCode": "S0305-EDUCATION_LOAN",
   "amount": "1200000.00"
}
```
<br> 

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
<br> 

### 6. Future RoadMap changes

  * [ ] **AWS Secrets Manager Integration:** Migrate the hardcoded RDS database credentials from environment variables to an encrypted Secrets Manager string retrieved at runtime.
