-- ──────────────────────────────────────────────────────────────────────
-- Fix refresh_tokens column naming: ensure expires_at is the only expiry column
-- ──────────────────────────────────────────────────────────────────────

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'refresh_tokens' AND column_name = 'expiry_date'
    ) AND EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'refresh_tokens' AND column_name = 'expires_at'
    ) THEN
        ALTER TABLE refresh_tokens DROP COLUMN expiry_date;
        RAISE NOTICE 'Dropped stale expiry_date column from refresh_tokens table';
    END IF;

    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'refresh_tokens' AND column_name = 'expiry_date'
    ) AND NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'refresh_tokens' AND column_name = 'expires_at'
    ) THEN
        ALTER TABLE refresh_tokens RENAME COLUMN expiry_date TO expires_at;
        RAISE NOTICE 'Renamed expiry_date to expires_at in refresh_tokens table';
    END IF;

    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'refresh_tokens' AND column_name = 'expires_at'
    ) AND NOT EXISTS (
        SELECT 1 FROM pg_indexes
        WHERE indexname = 'idx_refresh_token_expires' AND tablename = 'refresh_tokens'
    ) THEN
        CREATE INDEX idx_refresh_token_expires ON refresh_tokens(expires_at);
        RAISE NOTICE 'Created index idx_refresh_token_expires on refresh_tokens(expires_at)';
    END IF;
END $$;
