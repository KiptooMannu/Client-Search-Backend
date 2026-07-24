-- Add the hidden visibility columns expected by the dispute evidence entity
ALTER TABLE dispute_evidence_requests
    ADD COLUMN IF NOT EXISTS hidden_from_admin BOOLEAN DEFAULT FALSE;

ALTER TABLE dispute_evidence_requests
    ADD COLUMN IF NOT EXISTS hidden_from_user BOOLEAN DEFAULT FALSE;

-- Backfill existing rows so they behave like the current application defaults
UPDATE dispute_evidence_requests
SET hidden_from_admin = COALESCE(hidden_from_admin, FALSE)
WHERE hidden_from_admin IS NULL;

UPDATE dispute_evidence_requests
SET hidden_from_user = COALESCE(hidden_from_user, FALSE)
WHERE hidden_from_user IS NULL;
