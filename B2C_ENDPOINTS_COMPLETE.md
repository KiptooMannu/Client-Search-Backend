# B2C Webhook Endpoints - Complete Specification

## Overview

| Endpoint | Method | Purpose | Response Code |
|----------|--------|---------|----------------|
| `/api/payments/mpesa/result` | POST | Handle B2C payout success/failure | 200 OK |
| `/api/payments/mpesa/timeout` | POST | Handle B2C payout timeout | 200 OK |
| `/api/payments/status/{jobId}` | GET | Check payment status | 200 OK |

---

## Endpoint 1: B2C Result Handler

### Endpoint
```
POST /api/payments/mpesa/result
```

### Purpose
Receives webhook from M-Pesa when a B2C (bulk send money) transaction completes. This handles both successful payouts and failure scenarios.

### Content-Type
```
Content-Type: application/json
```

### Optional Headers
```
X-Forwarded-For: 196.201.214.200  # M-Pesa server IP (for validation)
```

---

### Request Body Variations

#### Variation 1: Success (resultCode = 0)

**HTTP Status**: 200 OK

**Request**:
```json
{
  "Result": {
    "ResultCode": 0,
    "ResultDesc": "The service request has been processed successfully.",
    "OriginatorConversationID": "19082-123456-1",
    "ConversationID": "AG_20260611_12345678901234567890",
    "TransactionID": "LIJ7791S60",
    "TransactionAmount": 2500.00,
    "TransactionReceipt": "LIJ7791S60",
    "B2CUtilityAccountAvailable": 4500.00,
    "B2CChargesPaidAccountAvailable": 0,
    "B2CRecipientIsRegisteredCustomer": true,
    "S2STransactionID": "0"
  }
}
```

**Response**:
```json
{
  "status": "received",
  "message": "Webhook acknowledged"
}
```

**Backend Actions**:
1. ✅ Validates resultCode == 0
2. ✅ Finds EscrowPayment by transactionId (LIJ7791S60)
3. ✅ Transitions status: PENDING/ESCROWED → RELEASED
4. ✅ Sets updatedAt to current timestamp
5. ✅ Creates audit log: `B2C_PAYOUT_CONFIRMED`
   - Reason: "B2C transaction LIJ7791S60 completed"
   - Payload: "transactionId=LIJ7791S60, conversationId=AG_20260611_..."
6. ✅ Logs: "B2C transaction LIJ7791S60 (conversation: AG_20260611...) completed with code 0"

**Database Changes**:
```sql
-- escrow_payments table
UPDATE escrow_payments 
SET status = 'RELEASED', 
    updated_at = NOW() 
WHERE mpesa_receipt_number = 'LIJ7791S60';

-- payment_audit_log table (new entry)
INSERT INTO payment_audit_log 
(escrow_payment_id, event_type, reason, payload, created_at) 
VALUES 
('<payment-id>', 'B2C_PAYOUT_CONFIRMED', 
'B2C transaction LIJ7791S60 completed', 
'transactionId=LIJ7791S60, conversationId=AG_20260611_...', NOW());
```

---

#### Variation 2: Failure - Insufficient Funds (resultCode = 1)

**HTTP Status**: 200 OK

**Request**:
```json
{
  "Result": {
    "ResultCode": 1,
    "ResultDesc": "Insufficient funds.",
    "OriginatorConversationID": "19082-654321-1",
    "ConversationID": "AG_20260611_12345678901234567890",
    "TransactionID": null,
    "TransactionAmount": 2500.00
  }
}
```

**Response**:
```json
{
  "status": "received",
  "message": "Webhook acknowledged"
}
```

**Backend Actions**:
1. ⚠️ Logs ERROR: "B2C result processing failed: resultCode=1, transactionId=null"
2. ⚠️ **Does NOT transition payment status**
3. ⚠️ **Does NOT create audit log entry**
4. ✅ Returns HTTP 200 (acknowledgement only)

**Note**: Admin must manually review and decide next action

---

#### Variation 3: Failure - Wrong PIN (resultCode = 1034)

**HTTP Status**: 200 OK

**Request**:
```json
{
  "Result": {
    "ResultCode": 1034,
    "ResultDesc": "WRONG PIN.",
    "OriginatorConversationID": "19082-789012-1",
    "ConversationID": "AG_20260611_12345678901234567890",
    "TransactionID": null,
    "TransactionAmount": 2500.00
  }
}
```

**Response**:
```json
{
  "status": "received",
  "message": "Webhook acknowledged"
}
```

**Backend Actions**:
1. ⚠️ Logs ERROR: "B2C result processing failed: resultCode=1034, transactionId=null"
2. ⚠️ **Does NOT transition payment status**
3. ⚠️ **Does NOT create audit log entry**
4. ✅ Returns HTTP 200

**Note**: Admin must retry with correct PIN

---

#### Variation 4: Failure - Wrong PIN Recipient (resultCode = 1037)

**HTTP Status**: 200 OK

**Request**:
```json
{
  "Result": {
    "ResultCode": 1037,
    "ResultDesc": "Recipient's MSISDN is invalid.",
    "OriginatorConversationID": "19082-345678-1",
    "ConversationID": "AG_20260611_12345678901234567890",
    "TransactionID": null,
    "TransactionAmount": 2500.00
  }
}
```

**Response**:
```json
{
  "status": "received",
  "message": "Webhook acknowledged"
}
```

**Backend Actions**: Same as Wrong PIN - logs error but no status change

---

### Error Handling

**If webhook processing throws exception**:
```json
{
  "status": "received",
  "message": "Webhook acknowledged"
}
```

**HTTP Status**: 200 OK (always)

**Note**: Even if backend error occurs, we return 200 so M-Pesa doesn't retry. Error is logged for debugging.

---

## Endpoint 2: B2C Timeout Handler

### Endpoint
```
POST /api/payments/mpesa/timeout
```

### Purpose
Receives timeout notification from M-Pesa when B2C transaction doesn't complete within timeout window. Triggers automatic retry logic with exponential backoff.

### Content-Type
```
Content-Type: application/json
```

### Optional Headers
```
X-Forwarded-For: 196.201.214.200  # M-Pesa server IP (for validation)
```

---

### Request Body Variations

#### Variation 1: Network Timeout (resultCode = 2001)

**HTTP Status**: 200 OK

**Request**:
```json
{
  "Result": {
    "ResultCode": 2001,
    "ResultDesc": "Request timeout.",
    "OriginatorConversationID": "19082-999999-1",
    "ConversationID": "AG_20260611_12345678901234567890"
  }
}
```

**Response**:
```json
{
  "status": "received",
  "message": "Timeout acknowledged"
}
```

**Backend Actions - First Timeout**:
1. 🔴 Logs ERROR: "B2C timeout processing: conversationId=AG_20260611_..., code=2001, desc=Request timeout."
2. ⚠️ Finds EscrowPayment by conversationId (substring match)
3. ⚠️ **Does NOT transition payment status** (stays PENDING/ESCROWED)
4. ✅ Creates audit log: `B2C_PAYOUT_TIMEOUT`
   - Reason: "B2C transaction timed out with code 2001"
   - Payload: "conversationId=AG_20260611_..., description=Request timeout."
5. ✅ Calls `implementB2cRetryLogic()`
6. ✅ Calls `escalateB2cTimeoutToAdmin()` [on 6th attempt]

**Audit Log Entries Created**:
```
Entry 1:
  event_type: B2C_PAYOUT_TIMEOUT
  reason: "B2C transaction timed out with code 2001"
  payload: "conversationId=AG_20260611_..., description=Request timeout."

Entry 2:
  event_type: B2C_PAYOUT_RETRY_SCHEDULED
  reason: "Retry attempt 1 of 5 scheduled in 1 minutes"
  payload: "backoffMinutes=1, conversationId=AG_20260611_..."
```

**Database Changes**:
```sql
-- escrow_payments table
-- NO CHANGES (status remains PENDING/ESCROWED)

-- payment_audit_log table (2 new entries)
INSERT INTO payment_audit_log 
(escrow_payment_id, event_type, reason, payload, created_at) 
VALUES 
('<payment-id>', 'B2C_PAYOUT_TIMEOUT', 
'B2C transaction timed out with code 2001',
'conversationId=AG_20260611_..., description=Request timeout.', NOW());

INSERT INTO payment_audit_log 
(escrow_payment_id, event_type, reason, payload, created_at) 
VALUES 
('<payment-id>', 'B2C_PAYOUT_RETRY_SCHEDULED',
'Retry attempt 1 of 5 scheduled in 1 minutes',
'backoffMinutes=1, conversationId=AG_20260611_...', NOW());
```

---

#### Variation 2: Teller Timeout (resultCode = 1031)

**HTTP Status**: 200 OK

**Request**:
```json
{
  "Result": {
    "ResultCode": 1031,
    "ResultDesc": "Timeout waiting for teller response.",
    "OriginatorConversationID": "19082-888888-1",
    "ConversationID": "AG_20260611_timeout123"
  }
}
```

**Response**:
```json
{
  "status": "received",
  "message": "Timeout acknowledged"
}
```

**Backend Actions**: Same as Network Timeout

---

### Retry Logic Sequence

**Attempt 1**: Backoff = 1 minute
```
event_type: B2C_PAYOUT_RETRY_SCHEDULED
reason: "Retry attempt 1 of 5 scheduled in 1 minutes"
payload: "backoffMinutes=1, conversationId=..."
```

**Attempt 2**: Backoff = 2 minutes
```
event_type: B2C_PAYOUT_RETRY_SCHEDULED
reason: "Retry attempt 2 of 5 scheduled in 2 minutes"
payload: "backoffMinutes=2, conversationId=..."
```

**Attempt 3**: Backoff = 4 minutes
```
event_type: B2C_PAYOUT_RETRY_SCHEDULED
reason: "Retry attempt 3 of 5 scheduled in 4 minutes"
payload: "backoffMinutes=4, conversationId=..."
```

**Attempt 4**: Backoff = 8 minutes
```
event_type: B2C_PAYOUT_RETRY_SCHEDULED
reason: "Retry attempt 4 of 5 scheduled in 8 minutes"
payload: "backoffMinutes=8, conversationId=..."
```

**Attempt 5**: Backoff = 16 minutes
```
event_type: B2C_PAYOUT_RETRY_SCHEDULED
reason: "Retry attempt 5 of 5 scheduled in 16 minutes"
payload: "backoffMinutes=16, conversationId=..."
```

**Attempt 6 (Max Retries Exceeded)**:
```
Entry 1:
  event_type: B2C_PAYOUT_TIMEOUT
  reason: "B2C transaction timed out with code 2001"
  payload: "conversationId=..., description=Request timeout."

Entry 2:
  event_type: B2C_PAYOUT_RETRY_EXHAUSTED
  reason: "Max retry attempts (5) exceeded. Manual intervention required."
  payload: "conversationId=..., lastResponseCode=2001"

Entry 3:
  event_type: B2C_PAYOUT_ESCALATED_TO_ADMIN
  reason: "Admin notification created for timeout escalation"
  payload: "adminId=<admin-uuid>, conversationId=..., responseCode=2001"
```

---

### Admin Escalation Details

**When**: After 5 timeout attempts

**Action**: Create notification for admin user

**Notification Object**:
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440099",
  "userId": "<admin-user-id>",
  "title": "B2C Payout Timeout - Manual Review Required",
  "message": "Worker payout for job <job-id> has timed out (response: 2001). Retry attempts are in progress. Please monitor and escalate if needed.",
  "type": "WARNING",
  "isRead": false,
  "createdAt": "2026-06-11T14:35:00Z"
}
```

**Database Changes**:
```sql
-- notifications table (1 new entry)
INSERT INTO notifications 
(id, user_id, title, message, type, is_read, created_at)
VALUES
(UUID(), '<admin-user-id>', 
'B2C Payout Timeout - Manual Review Required',
'Worker payout for job <job-id> has timed out (response: 2001). Retry attempts are in progress. Please monitor and escalate if needed.',
'WARNING', false, NOW());

-- payment_audit_log table (1 new entry)
INSERT INTO payment_audit_log 
(escrow_payment_id, event_type, reason, payload, created_at) 
VALUES 
('<payment-id>', 'B2C_PAYOUT_ESCALATED_TO_ADMIN',
'Admin notification created for timeout escalation',
'adminId=<admin-uuid>, conversationId=..., responseCode=2001', NOW());
```

---

## Endpoint 3: Payment Status Check

### Endpoint
```
GET /api/payments/status/{jobId}
```

### Purpose
Retrieves current payment status for a job. Use after webhooks to verify state transitions.

### Path Parameters
```
{jobId} - UUID of the job
```

### Example Requests

#### Success Case
```
GET http://localhost:8080/api/payments/status/550e8400-e29b-41d4-a716-446655440000
```

#### Response (Payment Released)
```json
{
  "status": "PAID",
  "paymentId": "a1b2c3d4-e5f6-4789-0123-456789abcdef",
  "jobId": "550e8400-e29b-41d4-a716-446655440000",
  "amount": 2500.0,
  "phoneNumber": "+254712345678",
  "checkoutRequestId": "ws_CO_16062024090101234567",
  "mpesaReceiptNumber": "LIJ7791S60",
  "platformFee": 250.0,
  "workerAmount": 2250.0,
  "message": "Payment released to worker. Platform fee: KES 250",
  "transactionDate": "2026-06-11T14:30:45",
  "createdAt": "2026-06-11T14:15:00",
  "timeoutAt": null,
  "failureReason": null
}
```

#### Response (Payment Pending - After Timeout)
```json
{
  "status": "PENDING",
  "paymentId": "b2c3d4e5-f6a7-4789-0123-456789abcdef",
  "jobId": "550e8400-e29b-41d4-a716-446655440001",
  "amount": 2500.0,
  "phoneNumber": "+254712345678",
  "checkoutRequestId": "ws_CO_16062024090101234568",
  "mpesaReceiptNumber": null,
  "platformFee": null,
  "workerAmount": null,
  "message": "STK push sent",
  "transactionDate": null,
  "createdAt": "2026-06-11T14:15:00",
  "timeoutAt": "2026-06-11T14:30:00",
  "failureReason": null
}
```

---

## Response Status Codes Summary

| HTTP Code | Scenario | Body |
|-----------|----------|------|
| 200 | B2C Result Success (resultCode=0) | `{"status":"received"}` |
| 200 | B2C Result Failure (resultCode≠0) | `{"status":"received"}` |
| 200 | B2C Timeout (any code) | `{"status":"received"}` |
| 200 | Payment Status Retrieved | `{PaymentStatusResponse}` |
| 404 | Payment Not Found | No payment record |

---

## Testing Checklist

### B2C Result Endpoint

- [ ] Send success webhook (resultCode=0)
  - [ ] Verify HTTP 200 response
  - [ ] Check escrow_payments.status changed to RELEASED
  - [ ] Verify audit log has B2C_PAYOUT_CONFIRMED

- [ ] Send failure webhook (resultCode=1)
  - [ ] Verify HTTP 200 response
  - [ ] Check escrow_payments.status unchanged
  - [ ] Verify error logged in application logs

### B2C Timeout Endpoint

- [ ] Send 1st timeout
  - [ ] Verify HTTP 200 response
  - [ ] Check escrow_payments.status unchanged
  - [ ] Verify B2C_PAYOUT_TIMEOUT audit entry created
  - [ ] Verify B2C_PAYOUT_RETRY_SCHEDULED with 1 minute backoff

- [ ] Send 2nd-5th timeouts
  - [ ] Verify backoff increases (2, 4, 8, 16 minutes)

- [ ] Send 6th timeout
  - [ ] Verify HTTP 200 response
  - [ ] Check B2C_PAYOUT_RETRY_EXHAUSTED audit entry
  - [ ] Check B2C_PAYOUT_ESCALATED_TO_ADMIN audit entry
  - [ ] Verify notifications table has WARNING notification for admin
  - [ ] Verify notification title contains "B2C Payout Timeout"

### Payment Status Endpoint

- [ ] After success webhook
  - [ ] GET /api/payments/status/{jobId}
  - [ ] Verify status = "PAID"
  - [ ] Verify mpesaReceiptNumber is populated
  - [ ] Verify workerAmount calculated correctly

- [ ] After timeout webhooks
  - [ ] GET /api/payments/status/{jobId}
  - [ ] Verify status = "PENDING"
  - [ ] Verify mpesaReceiptNumber is null

---

## Production Readiness

✅ All webhooks return HTTP 200 regardless of outcome (idempotent)
✅ Errors are logged but don't break webhook processing
✅ Audit logs track all events for compliance
✅ Admin notifications enable manual intervention
✅ Exponential backoff prevents system overload
✅ Transaction IDs ensure payment tracking
