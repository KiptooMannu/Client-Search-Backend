-- Client settlement wallets.
--
-- The settlementwallet feature shipped with three JPA entities but no migration,
-- and `spring.jpa.hibernate.ddl-auto` is `none`, so the tables were never created
-- on any deployed database. Every query against them failed with
-- `relation "client_settlement_wallets" does not exist`, which surfaced as a 400
-- from /api/analytics/enterprise because EnterpriseAnalyticsService reads the
-- settlement wallets while assembling the dashboard payload.
--
-- Column types mirror the entities: NUMERIC(14,2) money (the entities carry an
-- explicit @JdbcTypeCode(NUMERIC) for this reason), UUID keys with
-- gen_random_uuid() defaults, and TIMESTAMP audit columns written by
-- @PrePersist/@PreUpdate.

CREATE TABLE IF NOT EXISTS client_settlement_wallets (
    id                        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id                   UUID           NOT NULL UNIQUE,
    available_balance         NUMERIC(14, 2) NOT NULL DEFAULT 0,
    pending_credits           NUMERIC(14, 2) NOT NULL DEFAULT 0,
    total_refunded            NUMERIC(14, 2) NOT NULL DEFAULT 0,
    total_withdrawn           NUMERIC(14, 2) NOT NULL DEFAULT 0,
    total_settlement_credits  NUMERIC(14, 2) NOT NULL DEFAULT 0,
    is_frozen                 BOOLEAN        NOT NULL DEFAULT FALSE,
    freeze_reason             VARCHAR(255),
    frozen_at                 TIMESTAMP,
    unfrozen_at               TIMESTAMP,
    created_at                TIMESTAMP      DEFAULT CURRENT_TIMESTAMP,
    updated_at                TIMESTAMP      DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_settlement_wallet_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    -- A settlement wallet is a liability to the client; it must never go negative.
    CONSTRAINT chk_settlement_wallet_balances_non_negative
        CHECK (available_balance >= 0 AND pending_credits >= 0)
);

CREATE INDEX IF NOT EXISTS idx_settlement_wallet_user
    ON client_settlement_wallets (user_id);
CREATE INDEX IF NOT EXISTS idx_settlement_wallet_frozen
    ON client_settlement_wallets (is_frozen);

CREATE TABLE IF NOT EXISTS settlement_wallet_transactions (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    wallet_id             UUID           NOT NULL,
    transaction_reference VARCHAR(255)   NOT NULL UNIQUE,
    booking_id            UUID,
    escrow_id             UUID,
    transaction_type      VARCHAR(50)    NOT NULL,
    amount                NUMERIC(14, 2) NOT NULL,
    balance_before        NUMERIC(14, 2),
    balance_after         NUMERIC(14, 2),
    description           TEXT,
    status                VARCHAR(20)    NOT NULL,
    -- Quoted because TIMESTAMP is a type keyword; the entity maps this field to
    -- a column literally named "timestamp".
    "timestamp"           TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at            TIMESTAMP      DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_settlement_txn_wallet
        FOREIGN KEY (wallet_id) REFERENCES client_settlement_wallets (id) ON DELETE CASCADE,
    CONSTRAINT chk_settlement_txn_type CHECK (transaction_type IN (
        'REFUND', 'PARTIAL_REFUND', 'ESCROW_CANCELLATION', 'WORKER_REJECTION_REFUND',
        'DISPUTE_AWARD', 'PARTIAL_ESCROW_RELEASE_BALANCE', 'ADMIN_CREDIT', 'ADMIN_DEBIT',
        'WITHDRAWAL', 'WITHDRAWAL_CANCELLED', 'WITHDRAWAL_FAILED'
    )),
    CONSTRAINT chk_settlement_txn_status CHECK (status IN (
        'PENDING', 'COMPLETED', 'FAILED', 'CANCELLED'
    ))
);

CREATE INDEX IF NOT EXISTS idx_settlement_txn_wallet
    ON settlement_wallet_transactions (wallet_id);
CREATE INDEX IF NOT EXISTS idx_settlement_txn_booking
    ON settlement_wallet_transactions (booking_id);
CREATE INDEX IF NOT EXISTS idx_settlement_txn_escrow
    ON settlement_wallet_transactions (escrow_id);
CREATE INDEX IF NOT EXISTS idx_settlement_txn_type
    ON settlement_wallet_transactions (transaction_type);
CREATE INDEX IF NOT EXISTS idx_settlement_txn_timestamp
    ON settlement_wallet_transactions ("timestamp");

CREATE TABLE IF NOT EXISTS settlement_withdrawals (
    id                               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    wallet_id                        UUID           NOT NULL,
    requested_by                     UUID           NOT NULL,
    withdrawal_reference             VARCHAR(255)   NOT NULL UNIQUE,
    withdrawal_method                VARCHAR(50)    NOT NULL,
    amount                           NUMERIC(14, 2) NOT NULL,
    phone_number                     VARCHAR(32),
    account_name                     VARCHAR(255),
    account_number                   VARCHAR(64),
    bank_name                        VARCHAR(255),
    bank_branch                      VARCHAR(255),
    notes                            TEXT,
    status                           VARCHAR(20)    NOT NULL,
    receipt_number                   VARCHAR(100),
    failure_reason                   VARCHAR(255),
    mpesa_conversation_id            VARCHAR(100),
    mpesa_originator_conversation_id VARCHAR(100),
    mpesa_transaction_id             VARCHAR(100),
    mpesa_initiated_at               TIMESTAMP,
    mpesa_completed_at               TIMESTAMP,
    processed_at                     TIMESTAMP,
    created_at                       TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                       TIMESTAMP      DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_settlement_withdrawal_wallet
        FOREIGN KEY (wallet_id) REFERENCES client_settlement_wallets (id) ON DELETE CASCADE,
    CONSTRAINT fk_settlement_withdrawal_user
        FOREIGN KEY (requested_by) REFERENCES users (id),
    CONSTRAINT chk_settlement_withdrawal_amount_positive CHECK (amount > 0),
    CONSTRAINT chk_settlement_withdrawal_method CHECK (withdrawal_method IN (
        'MPESA_B2C', 'BANK_TRANSFER'
    )),
    CONSTRAINT chk_settlement_withdrawal_status CHECK (status IN (
        'PENDING', 'PROCESSING', 'SUCCESSFUL', 'FAILED', 'CANCELLED'
    ))
);

CREATE INDEX IF NOT EXISTS idx_settlement_withdrawal_wallet
    ON settlement_withdrawals (wallet_id);
CREATE INDEX IF NOT EXISTS idx_settlement_withdrawal_status
    ON settlement_withdrawals (status);
CREATE INDEX IF NOT EXISTS idx_settlement_withdrawal_timestamp
    ON settlement_withdrawals (created_at);
