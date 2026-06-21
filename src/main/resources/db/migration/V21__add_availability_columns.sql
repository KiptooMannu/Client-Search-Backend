-- Add availability embedded columns to worker_profiles table
ALTER TABLE worker_profiles
    ADD COLUMN IF NOT EXISTS last_seen TIMESTAMP,
    ADD COLUMN IF NOT EXISTS approved_by UUID REFERENCES users(id),
    ADD COLUMN IF NOT EXISTS approved_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS rejected_by UUID REFERENCES users(id),
    ADD COLUMN IF NOT EXISTS rejected_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS availability_weekdays BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS availability_weekends BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS availability_evenings BOOLEAN NOT NULL DEFAULT FALSE;