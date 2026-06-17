-- Migration V20: Fix numeric column type mismatches
-- Converts DOUBLE (FLOAT8) columns to NUMERIC(14, 2) for precise decimal handling
-- Matches Hibernate entity expectations with JdbcTypeCode(Types.NUMERIC)

ALTER TABLE job_requests
    ALTER COLUMN total_cost TYPE NUMERIC(14, 2) USING total_cost::NUMERIC(14, 2),
    ALTER COLUMN job_price TYPE NUMERIC(14, 2) USING job_price::NUMERIC(14, 2),
    ALTER COLUMN negotiated_price TYPE NUMERIC(14, 2) USING negotiated_price::NUMERIC(14, 2),
    ALTER COLUMN worker_partial_amount TYPE NUMERIC(14, 2) USING worker_partial_amount::NUMERIC(14, 2),
    ALTER COLUMN client_partial_amount TYPE NUMERIC(14, 2) USING client_partial_amount::NUMERIC(14, 2);
