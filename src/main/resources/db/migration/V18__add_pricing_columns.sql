-- Add pricing columns to job_requests table for negotiation feature
ALTER TABLE job_requests
    ADD COLUMN IF NOT EXISTS job_price NUMERIC(14, 2),
    ADD COLUMN IF NOT EXISTS negotiated_price NUMERIC(14, 2),
    ADD COLUMN IF NOT EXISTS escrow_funded BOOLEAN DEFAULT false;
