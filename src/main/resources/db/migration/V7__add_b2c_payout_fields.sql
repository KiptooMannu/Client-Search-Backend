-- Migration: Add B2C payout fields to escrow_payments table
-- Purpose: Support M-Pesa B2C worker payouts with retry tracking
-- Created: 2024

ALTER TABLE escrow_payments ADD COLUMN IF NOT EXISTS b2c_conversation_id VARCHAR(100);
ALTER TABLE escrow_payments ADD COLUMN IF NOT EXISTS b2c_originator_conversation_id VARCHAR(100);
ALTER TABLE escrow_payments ADD COLUMN IF NOT EXISTS b2c_transaction_id VARCHAR(100);
ALTER TABLE escrow_payments ADD COLUMN IF NOT EXISTS b2c_initiated_at TIMESTAMP;
ALTER TABLE escrow_payments ADD COLUMN IF NOT EXISTS b2c_completed_at TIMESTAMP;
ALTER TABLE escrow_payments ADD COLUMN IF NOT EXISTS b2c_retry_count INTEGER DEFAULT 0;
ALTER TABLE escrow_payments ADD COLUMN IF NOT EXISTS b2c_next_retry_at TIMESTAMP;

-- Create indexes for B2C lookups and retry scheduling
CREATE INDEX IF NOT EXISTS idx_escrow_b2c_conversation_id ON escrow_payments(b2c_conversation_id);
CREATE INDEX IF NOT EXISTS idx_escrow_b2c_transaction_id ON escrow_payments(b2c_transaction_id);
CREATE INDEX IF NOT EXISTS idx_escrow_b2c_retry_lookup ON escrow_payments(status, b2c_next_retry_at);

-- Update existing rows to have sensible defaults
UPDATE escrow_payments SET b2c_retry_count = 0 WHERE b2c_retry_count IS NULL;
