-- Platform Fee Withdrawals Table
CREATE TABLE IF NOT EXISTS platform_fee_withdrawals (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    version BIGINT NOT NULL DEFAULT 0,
    amount DECIMAL(15,2) NOT NULL,
    status VARCHAR(50) NOT NULL,
    withdrawal_method VARCHAR(100),
    withdrawal_details TEXT,
    mpesa_phone_number VARCHAR(20),
    mpesa_receipt_number VARCHAR(100),
    mpesa_conversation_id VARCHAR(100),
    failure_reason TEXT,
    requested_by VARCHAR(255),
    processed_by VARCHAR(255),
    requested_at TIMESTAMP NOT NULL,
    processed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_withdrawal_amount_positive CHECK (amount > 0),
    CONSTRAINT chk_withdrawal_status CHECK (status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED', 'CANCELLED'))
);

-- Indexes for performance
CREATE INDEX IF NOT EXISTS idx_platform_fee_withdrawals_status ON platform_fee_withdrawals(status);
CREATE INDEX IF NOT EXISTS idx_platform_fee_withdrawals_requested_at ON platform_fee_withdrawals(requested_at);
CREATE INDEX IF NOT EXISTS idx_platform_fee_withdrawals_requested_by ON platform_fee_withdrawals(requested_by);

-- Trigger to update updated_at timestamp
CREATE OR REPLACE FUNCTION update_platform_fee_withdrawals_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trigger_update_platform_fee_withdrawals_updated_at
    BEFORE UPDATE ON platform_fee_withdrawals
    FOR EACH ROW
    EXECUTE FUNCTION update_platform_fee_withdrawals_updated_at();
