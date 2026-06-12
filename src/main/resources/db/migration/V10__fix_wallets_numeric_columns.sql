-- Fix wallet numeric columns that were created as float8 instead of NUMERIC(14,2)
ALTER TABLE wallets
    ALTER COLUMN balance TYPE NUMERIC(14,2) USING balance::numeric(14,2),
    ALTER COLUMN total_earned TYPE NUMERIC(14,2) USING total_earned::numeric(14,2),
    ALTER COLUMN total_withdrawn TYPE NUMERIC(14,2) USING total_withdrawn::numeric(14,2);
