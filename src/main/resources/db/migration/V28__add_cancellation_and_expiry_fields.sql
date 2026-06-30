-- Add cancellation and expiry fields to job_requests table for enhanced workflow management
ALTER TABLE job_requests
    ADD COLUMN IF NOT EXISTS cancellation_reason TEXT,
    ADD COLUMN IF NOT EXISTS cancelled_by UUID,
    ADD COLUMN IF NOT EXISTS cancelled_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS expiry_date TIMESTAMP;

-- Add index on expiry_date for efficient expiry checks
CREATE INDEX IF NOT EXISTS idx_job_requests_expiry_date ON job_requests(expiry_date) WHERE expiry_date IS NOT NULL;

-- Add index on cancelled_by for tracking cancellation history
CREATE INDEX IF NOT EXISTS idx_job_requests_cancelled_by ON job_requests(cancelled_by) WHERE cancelled_by IS NOT NULL;
