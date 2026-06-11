# M-Pesa B2C Webhook Testing Guide

This guide provides endpoint documentation and Postman test cases for the M-Pesa B2C webhook handlers.

---

## Endpoints Overview

| Endpoint | Method | Purpose |
|----------|--------|---------|
| `/api/payments/mpesa/result` | POST | Handle successful B2C payout results |
| `/api/payments/mpesa/timeout` | POST | Handle B2C payout timeouts |

---

## 1. B2C Payout Result Webhook

**Endpoint**: `POST /api/payments/mpesa/result`

**Purpose**: Handles successful B2C (worker payout) transaction results from M-Pesa

### Request Headers
```
Content-Type: application/json
X-Forwarded-For: 196.201.214.200  (Optional: M-Pesa IP for validation)
```

### Request Body

#### Success Case (resultCode = 0)
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

#### Failure Cases

**Insufficient Funds** (resultCode = 1)
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

**Wrong PIN** (resultCode = 1034)
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

### Expected Response

**HTTP Status**: `200 OK`

```json
{
  "status": "received",
  "message": "Webhook acknowledged"
}
```

### What Happens Behind the Scenes

**When resultCode = 0 (Success)**:
1. ✅ Finds `EscrowPayment` by matching `TransactionID` with `mpesaReceiptNumber`
2. ✅ Updates payment status: `PENDING/ESCROWED` → `RELEASED`
3. ✅ Marks escrow as released (funds confirmed transferred to worker)
4. ✅ Creates audit log entry: `B2C_PAYOUT_CONFIRMED`
5. ✅ Logs transaction completion with transaction ID and conversation ID
6. ✅ Returns HTTP 200 to M-Pesa (idempotent - safe to retry)

**When resultCode ≠ 0 (Failure/Timeout)**:
1. ⚠️ Logs error level message with response code
2. ⚠️ **Does NOT auto-transition payment status** (requires manual review)
3. ⚠️ Skips audit log creation (failure cases handled separately)
4. ✅ Still returns HTTP 200 to acknowledge receipt

### Audit Log Details

**Event Type**: `B2C_PAYOUT_CONFIRMED`
```
Reason: "B2C transaction LIJ7791S60 completed"
Payload: "transactionId=LIJ7791S60, conversationId=AG_20260611_12345678901234567890"
```

### Testing Steps

1. **Create an escrow payment first** (if not already created):
   - POST `/api/payments/mpesa/stkpush` with a job ID
   - This creates `EscrowPayment` with status `PENDING`

2. **Send B2C result webhook**:
   - POST to `http://localhost:8080/api/payments/mpesa/result`
   - Use request body above with successful resultCode

3. **Verify the payment transitioned**:
   - GET `/api/payments/status/{jobId}`
   - Expected: `status: "PAID"` (mapped from `RELEASED`)

4. **Check audit log**:
   - Query `payment_audit_log` table for `eventType = 'B2C_PAYOUT_CONFIRMED'`
   - Verify timestamp and reason are recorded

---

## 2. B2C Payout Timeout Webhook

**Endpoint**: `POST /api/payments/mpesa/timeout`

**Purpose**: Handles B2C transaction timeouts when M-Pesa cannot complete the transfer

### Request Headers
```
Content-Type: application/json
X-Forwarded-For: 196.201.214.200  (Optional: M-Pesa IP for validation)
```

### Request Body

#### Timeout Case
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

#### Other Timeout Codes
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

### Expected Response

**HTTP Status**: `200 OK`

```json
{
  "status": "received",
  "message": "Timeout acknowledged"
}
```

### What Happens Behind the Scenes

**Immediate Actions**:
1. ⚠️ Logs ERROR level message with response code
2. ⚠️ Finds `EscrowPayment` by matching `ConversationID`
3. ⚠️ **Does NOT auto-transition payment status** (stays `PENDING/ESCROWED`)
4. ✅ Creates audit log entry: `B2C_PAYOUT_TIMEOUT`

**Retry Logic**:
1. Counts previous timeout attempts from audit log
2. Calculates exponential backoff: 1min → 2min → 4min → 8min → 16min
3. Allows up to 5 retry attempts before escalation
4. Creates audit log entry: `B2C_PAYOUT_RETRY_SCHEDULED`
5. Logs retry details with backoff duration

**Admin Escalation** (after 5 retries):
1. Creates `B2C_PAYOUT_RETRY_EXHAUSTED` audit log entry
2. Finds system admin user
3. Creates `WARNING` type notification for admin
4. Notification title: "B2C Payout Timeout - Manual Review Required"
5. Notification message includes job ID and response code
6. Creates audit log entry: `B2C_PAYOUT_ESCALATED_TO_ADMIN`

### Audit Log Details

**Event 1 - Timeout**:
```
Event Type: B2C_PAYOUT_TIMEOUT
Reason: "B2C transaction timed out with code 2001"
Payload: "conversationId=AG_20260611_12345678901234567890, description=Request timeout."
```

**Event 2 - Retry Scheduled**:
```
Event Type: B2C_PAYOUT_RETRY_SCHEDULED
Reason: "Retry attempt 1 of 5 scheduled in 1 minutes"
Payload: "backoffMinutes=1, conversationId=AG_20260611_12345678901234567890"
```

**Event 3 - Escalation (after 5 attempts)**:
```
Event Type: B2C_PAYOUT_ESCALATED_TO_ADMIN
Reason: "Admin notification created for timeout escalation"
Payload: "adminId=<admin-uuid>, conversationId=AG_20260611_12345678901234567890, responseCode=2001"
```

### Testing Steps

1. **Create an escrow payment first**:
   - POST `/api/payments/mpesa/stkpush` with a job ID
   - This creates `EscrowPayment` with status `PENDING`

2. **Send first B2C timeout webhook**:
   - POST to `http://localhost:8080/api/payments/mpesa/timeout`
   - Use timeout request body with resultCode 2001

3. **Verify payment status unchanged**:
   - GET `/api/payments/status/{jobId}`
   - Expected: Still shows `PENDING` (not transitioned)

4. **Check audit logs**:
   - Query `payment_audit_log` for this payment
   - Should see: `B2C_PAYOUT_TIMEOUT`, `B2C_PAYOUT_RETRY_SCHEDULED`
   - Verify timestamp and backoff calculation (1 minute for retry #1)

5. **Simulate max retries exceeded**:
   - Send the timeout webhook 5 more times
   - After 5th attempt, check for:
     - `B2C_PAYOUT_RETRY_EXHAUSTED` audit entry
     - `B2C_PAYOUT_ESCALATED_TO_ADMIN` audit entry

6. **Verify admin notification**:
   - Query `notifications` table
   - Find notification for admin user
   - Expected type: `WARNING`
   - Expected title: "B2C Payout Timeout - Manual Review Required"

---

## Postman Collection

### Test Case 1: B2C Success Webhook

**Name**: B2C Payout Success

**Request**:
```
POST http://localhost:8080/api/payments/mpesa/result
Content-Type: application/json

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

**Expected Response**:
```json
{
  "status": "received",
  "message": "Webhook acknowledged"
}
```

**Verification**:
```sql
-- Check payment status transition
SELECT id, status, updated_at FROM escrow_payments 
WHERE mpesa_receipt_number = 'LIJ7791S60';
-- Expected: status = 'RELEASED'

-- Check audit log
SELECT event_type, reason, payload, created_at FROM payment_audit_log
WHERE escrow_payment_id = '<payment-uuid>'
AND event_type = 'B2C_PAYOUT_CONFIRMED'
ORDER BY created_at DESC LIMIT 1;
-- Expected: recent entry with transaction details
```

---

### Test Case 2: B2C Timeout Webhook (First Time)

**Name**: B2C Payout Timeout - Retry 1

**Request**:
```
POST http://localhost:8080/api/payments/mpesa/timeout
Content-Type: application/json

{
  "Result": {
    "ResultCode": 2001,
    "ResultDesc": "Request timeout.",
    "OriginatorConversationID": "19082-999999-1",
    "ConversationID": "AG_20260611_12345678901234567890"
  }
}
```

**Expected Response**:
```json
{
  "status": "received",
  "message": "Timeout acknowledged"
}
```

**Verification**:
```sql
-- Check payment status (should NOT change)
SELECT id, status FROM escrow_payments 
WHERE checkout_request_id = 'AG_20260611_12345678901234567890';
-- Expected: status still = 'PENDING' or 'ESCROWED'

-- Check timeout audit logs
SELECT event_type, reason, payload, created_at FROM payment_audit_log
WHERE escrow_payment_id = '<payment-uuid>'
AND event_type IN ('B2C_PAYOUT_TIMEOUT', 'B2C_PAYOUT_RETRY_SCHEDULED')
ORDER BY created_at DESC LIMIT 2;
-- Expected: 
--   1. B2C_PAYOUT_TIMEOUT entry
--   2. B2C_PAYOUT_RETRY_SCHEDULED with "backoffMinutes=1"
```

---

### Test Case 3: B2C Timeout After 5 Retries (Escalation)

**Name**: B2C Payout Timeout - Escalation

**Steps**:
1. Create escrow payment (POST /api/payments/mpesa/stkpush)
2. Send timeout webhook 5 times
3. Send timeout webhook a 6th time

**6th Request**:
```
POST http://localhost:8080/api/payments/mpesa/timeout
Content-Type: application/json

{
  "Result": {
    "ResultCode": 2001,
    "ResultDesc": "Request timeout.",
    "OriginatorConversationID": "19082-999999-6",
    "ConversationID": "AG_20260611_12345678901234567890"
  }
}
```

**Verification**:
```sql
-- Check final audit logs
SELECT event_type, reason, payload FROM payment_audit_log
WHERE escrow_payment_id = '<payment-uuid>'
ORDER BY created_at DESC LIMIT 3;
-- Expected events:
--   1. B2C_PAYOUT_ESCALATED_TO_ADMIN
--   2. B2C_PAYOUT_RETRY_EXHAUSTED
--   3. B2C_PAYOUT_RETRY_SCHEDULED (backoffMinutes=16)

-- Check admin notification
SELECT id, title, message, type, is_read FROM notifications
WHERE user_id = '<admin-id>'
AND type = 'WARNING'
ORDER BY created_at DESC LIMIT 1;
-- Expected: 
--   title: "B2C Payout Timeout - Manual Review Required"
--   message: Contains job ID and response code
--   type: "WARNING"
--   is_read: false
```

---

## Testing Sequence (Complete Flow)

### Phase 1: Setup
```
1. Start Spring Boot backend: mvn spring-boot:run
2. Verify DB is running (PostgreSQL)
3. Open Postman
4. Set environment variables:
   - BASE_URL: http://localhost:8080
   - JOB_ID: <any-valid-uuid>
   - PHONE: +254712345678
```

### Phase 2: Create Escrow Payment
```
POST {{BASE_URL}}/api/payments/mpesa/stkpush
Headers: Authorization: Bearer <JWT-TOKEN>
Body:
{
  "jobId": "{{JOB_ID}}",
  "phoneNumber": "{{PHONE}}"
}

Expected: status=PENDING in database
```

### Phase 3: Test B2C Success
```
POST {{BASE_URL}}/api/payments/mpesa/result
Body: [Success webhook from above]

Check: 
- Payment status changed to RELEASED
- Audit log has B2C_PAYOUT_CONFIRMED
```

### Phase 4: Test B2C Timeout (New Payment)
```
1. Create new escrow payment

2. Send timeout webhook #1-5:
   POST {{BASE_URL}}/api/payments/mpesa/timeout
   Body: [Timeout webhook from above]
   
   Check each time:
   - Status stays PENDING
   - New audit log entry appears
   - Backoff increases: 1min → 2min → 4min → 8min → 16min

3. Send timeout webhook #6:
   Expected: Admin notification created
   Check notifications table
```

---

## Response Codes Reference

| Code | Meaning | Action |
|------|---------|--------|
| 0 | Success | Transition to RELEASED |
| 1 | Insufficient Funds | Log error, require manual review |
| 1031 | Timeout (teller) | Schedule retry with backoff |
| 1034 | Wrong PIN | Log error, require manual review |
| 1037 | Wrong PIN (recipient) | Log error, require manual review |
| 1001 | Timeout (network) | Schedule retry with backoff |
| 2001 | Request Timeout | Schedule retry with backoff |

---

## Common Issues & Troubleshooting

### Issue: "Webhook not processing"
- **Check**: Is `notificationRepository` injected in PaymentService?
- **Check**: Are audit logs being created?
- **Solution**: Verify `PaymentService` has `@RequiredArgsConstructor` and all dependencies

### Issue: "Admin notification not appearing"
- **Check**: Is admin user seeded in database?
- **Check**: Run: `SELECT * FROM users WHERE role = 'ADMIN' LIMIT 1;`
- **Solution**: If empty, run DataInitializer or seed admin manually

### Issue: "Timeout attempts not counted"
- **Check**: Are audit logs being saved?
- **Check**: Run: `SELECT * FROM payment_audit_log WHERE escrow_payment_id = '<id>' ORDER BY created_at;`
- **Solution**: Verify PaymentAuditLogRepository has custom query method

### Issue: "Status not transitioning"
- **Check**: Is correct transaction ID in webhook?
- **Check**: Does `mpesa_receipt_number` match webhook `TransactionID`?
- **Solution**: Verify payment exists before sending webhook

---

## Sample cURL Commands

### B2C Success (for testing without Postman):
```bash
curl -X POST http://localhost:8080/api/payments/mpesa/result \
  -H "Content-Type: application/json" \
  -d '{
    "Result": {
      "ResultCode": 0,
      "ResultDesc": "Success",
      "ConversationID": "AG_20260611_test123",
      "TransactionID": "TEST12345"
    }
  }'
```

### B2C Timeout (for testing without Postman):
```bash
curl -X POST http://localhost:8080/api/payments/mpesa/timeout \
  -H "Content-Type: application/json" \
  -d '{
    "Result": {
      "ResultCode": 2001,
      "ResultDesc": "Request timeout",
      "ConversationID": "AG_20260611_test123"
    }
  }'
```

---

## Notes

- ✅ All webhook endpoints return HTTP 200 regardless of processing outcome (idempotent)
- ✅ Retry logic only triggers on timeout codes (1031, 1001, 2001)
- ✅ Success codes (0) immediately transition to RELEASED
- ✅ Admin escalation happens automatically after 5 failed retry attempts
- ✅ All events are logged to `payment_audit_log` for compliance/debugging
