-- KaziKonnect — Escrow retry model updates
-- Add failure reason metadata and ensure job_request_id is indexed without requiring uniqueness.

ALTER TABLE escrow_payments
    ADD COLUMN IF NOT EXISTS failure_reason VARCHAR(255);

-- Replace any unique job_request_id index with a normal index for multi-attempt support.
DROP INDEX IF EXISTS idx_escrow_job;
CREATE INDEX IF NOT EXISTS idx_escrow_job ON escrow_payments(job_request_id);
