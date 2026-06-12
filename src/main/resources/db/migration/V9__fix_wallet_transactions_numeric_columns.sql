-- Fix wallet transaction numeric columns that were previously created as float8
ALTER TABLE wallet_transactions
    ALTER COLUMN amount TYPE NUMERIC(14,2) USING amount::numeric(14,2),
    ALTER COLUMN balance_after TYPE NUMERIC(14,2) USING balance_after::numeric(14,2);
