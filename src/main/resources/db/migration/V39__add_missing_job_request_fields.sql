-- Add the remaining job_requests columns expected by the current entity model
ALTER TABLE job_requests
    ADD COLUMN IF NOT EXISTS title TEXT;

ALTER TABLE job_requests
    ADD COLUMN IF NOT EXISTS cancellation_reason TEXT;

ALTER TABLE job_requests
    ADD COLUMN IF NOT EXISTS cancelled_by UUID;

ALTER TABLE job_requests
    ADD COLUMN IF NOT EXISTS cancelled_at TIMESTAMP;

ALTER TABLE job_requests
    ADD COLUMN IF NOT EXISTS expiry_date TIMESTAMP;
