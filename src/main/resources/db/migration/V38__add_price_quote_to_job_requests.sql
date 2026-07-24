-- Add the price_quote column expected by the current JobRequest entity
ALTER TABLE job_requests
    ADD COLUMN IF NOT EXISTS price_quote NUMERIC(14, 2);
