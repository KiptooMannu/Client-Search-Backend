-- Add escrow_funded column to job_requests table (correction for missing schema)
ALTER TABLE job_requests
    ADD COLUMN IF NOT EXISTS escrow_funded BOOLEAN DEFAULT false;
