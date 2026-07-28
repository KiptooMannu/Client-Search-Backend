-- Migration V40: Fix escrow_payments numeric column types
-- Converts DOUBLE PRECISION columns to NUMERIC(14, 2) for precise decimal handling
-- Matches Hibernate entity expectations with JdbcTypeCode(Types.NUMERIC)

ALTER TABLE escrow_payments
    ALTER COLUMN amount TYPE NUMERIC(14, 2) USING amount::NUMERIC(14, 2),
    ALTER COLUMN platform_fee TYPE NUMERIC(14, 2) USING platform_fee::NUMERIC(14, 2),
    ALTER COLUMN worker_amount TYPE NUMERIC(14, 2) USING worker_amount::NUMERIC(14, 2);
