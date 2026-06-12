-- Migration: Add dispute fields to job_requests and B2C payout fields to withdrawal_requests
-- Created: 2026

-- Add dispute and lifecycle fields to job_requests table
ALTER TABLE job_requests ADD COLUMN IF NOT EXISTS started_at TIMESTAMP;
ALTER TABLE job_requests ADD COLUMN IF NOT EXISTS submitted_at TIMESTAMP;
ALTER TABLE job_requests ADD COLUMN IF NOT EXISTS approved_at TIMESTAMP;
ALTER TABLE job_requests ADD COLUMN IF NOT EXISTS disputed_at TIMESTAMP;
ALTER TABLE job_requests ADD COLUMN IF NOT EXISTS resolved_at TIMESTAMP;
ALTER TABLE job_requests ADD COLUMN IF NOT EXISTS deadline TIMESTAMP;

ALTER TABLE job_requests ADD COLUMN IF NOT EXISTS dispute_reason TEXT;
ALTER TABLE job_requests ADD COLUMN IF NOT EXISTS dispute_evidence TEXT;
ALTER TABLE job_requests ADD COLUMN IF NOT EXISTS dispute_attachment_url VARCHAR(255);

ALTER TABLE job_requests ADD COLUMN IF NOT EXISTS dispute_response TEXT;
ALTER TABLE job_requests ADD COLUMN IF NOT EXISTS dispute_response_evidence TEXT;
ALTER TABLE job_requests ADD COLUMN IF NOT EXISTS dispute_response_attachment_url VARCHAR(255);

ALTER TABLE job_requests ADD COLUMN IF NOT EXISTS admin_decision_reason TEXT;
ALTER TABLE job_requests ADD COLUMN IF NOT EXISTS admin_evidence_notes TEXT;
ALTER TABLE job_requests ADD COLUMN IF NOT EXISTS worker_partial_amount NUMERIC(14,2);
ALTER TABLE job_requests ADD COLUMN IF NOT EXISTS client_partial_amount NUMERIC(14,2);

-- Add B2C payout columns to withdrawal_requests table
ALTER TABLE withdrawal_requests ADD COLUMN IF NOT EXISTS b2c_conversation_id VARCHAR(100);
ALTER TABLE withdrawal_requests ADD COLUMN IF NOT EXISTS b2c_originator_conversation_id VARCHAR(100);
ALTER TABLE withdrawal_requests ADD COLUMN IF NOT EXISTS b2c_transaction_id VARCHAR(100);
ALTER TABLE withdrawal_requests ADD COLUMN IF NOT EXISTS b2c_initiated_at TIMESTAMP;
ALTER TABLE withdrawal_requests ADD COLUMN IF NOT EXISTS b2c_completed_at TIMESTAMP;
ALTER TABLE withdrawal_requests ADD COLUMN IF NOT EXISTS b2c_retry_count INTEGER DEFAULT 0;
ALTER TABLE withdrawal_requests ADD COLUMN IF NOT EXISTS b2c_next_retry_at TIMESTAMP;

-- Create indexes for withdrawal requests B2C fields
CREATE INDEX IF NOT EXISTS idx_withdrawals_b2c_conversation_id ON withdrawal_requests(b2c_conversation_id);
CREATE INDEX IF NOT EXISTS idx_withdrawals_b2c_transaction_id ON withdrawal_requests(b2c_transaction_id);
