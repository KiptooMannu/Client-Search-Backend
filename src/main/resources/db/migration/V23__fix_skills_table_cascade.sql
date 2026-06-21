-- Fix skills and worker_skills tables from bigint to UUID
-- This aligns the database schema with the Java Skill entity (UUID-based)

-- Drop the existing constraint on job_requests if it exists
ALTER TABLE IF EXISTS job_requests DROP CONSTRAINT IF EXISTS job_requests_status_check;

-- Add the corrected constraint back (includes SUCCESS status)
ALTER TABLE IF EXISTS job_requests ADD CONSTRAINT job_requests_status_check
    CHECK (status IN ('PENDING', 'ACCEPTED', 'REJECTED', 'CANCELLED', 'IN_PROGRESS', 'COMPLETED', 'DISPUTED', 'SUCCESS'));

-- Drop the existing constraint on escrow_payments if it exists
ALTER TABLE IF EXISTS escrow_payments DROP CONSTRAINT IF EXISTS escrow_payments_status_check;

-- Add the corrected constraint back (includes SUCCESS status)
ALTER TABLE IF EXISTS escrow_payments ADD CONSTRAINT escrow_payments_status_check
    CHECK (status IN ('PENDING', 'PAID', 'FAILED', 'RELEASED', 'REFUNDED', 'DISPUTED', 'SUCCESS'));

-- Fix skills table: drop bigint columns and recreate with UUID
DROP TABLE IF EXISTS worker_skills CASCADE;

-- Drop any sequence or constraint on old skills id
ALTER TABLE IF EXISTS skills DROP CONSTRAINT IF EXISTS skills_pkey;
ALTER TABLE IF EXISTS skills DROP CONSTRAINT IF EXISTS skills_name_key;

-- Now safely drop and recreate skills with UUID
DROP TABLE IF EXISTS skills CASCADE;

CREATE TABLE IF NOT EXISTS skills (
    id   UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(100) NOT NULL UNIQUE
);

-- Recreate worker_skills with UUID columns
CREATE TABLE IF NOT EXISTS worker_skills (
    worker_id UUID NOT NULL REFERENCES worker_profiles(id) ON DELETE CASCADE,
    skill_id  UUID NOT NULL REFERENCES skills(id) ON DELETE CASCADE,
    PRIMARY KEY (worker_id, skill_id)
);
