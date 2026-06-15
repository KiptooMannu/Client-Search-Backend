-- Keep the database check constraint in sync with EscrowPaymentStatus.
ALTER TABLE escrow_payments
    DROP CONSTRAINT IF EXISTS escrow_payments_status_check;

ALTER TABLE escrow_payments
    ADD CONSTRAINT escrow_payments_status_check
    CHECK (status IN (
        'PENDING',
        'SUCCESS',
        'ESCROWED',
        'RELEASED',
        'REFUNDED',
        'FAILED',
        'PARTIALLY_SETTLED',
        'DISPUTED',
        'B2C_INITIATED',
        'B2C_PENDING',
        'B2C_RETRY_PENDING',
        'B2C_FAILED',
        'B2C_MAX_RETRIES_EXCEEDED'
    ));
