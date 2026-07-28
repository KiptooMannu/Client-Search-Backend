-- Platform wallet, its ledger, and platform withdrawals.
--
-- Same root cause as V41: the platformwallet feature shipped three JPA entities
-- and no migration, and `spring.jpa.hibernate.ddl-auto` is `none`, so the tables
-- were never created. Any read of the platform wallet failed with
-- `relation "platform_wallets" does not exist`, which took out
-- /api/analytics/enterprise (EnterpriseAnalyticsService reads the wallet while
-- assembling KPIs) and every /api/platform-wallet endpoint behind it.
--
-- Confirmed against the entity-vs-migration diff: with V41 and V42 applied, all
-- 34 @Table names in the codebase have a CREATE TABLE.
--
-- Note there is already a `platform_fee_withdrawals` table from V36, which is a
-- different, older concept (PlatformFeeWithdrawal in features/analytics). It is
-- deliberately left alone; `platform_withdrawals` below is the platformwallet
-- feature's own table.
--
-- No seed row for platform_wallets: PlatformWalletService creates the singleton
-- on first use, and EnterpriseAnalyticsService already tolerates its absence.

CREATE TABLE IF NOT EXISTS platform_wallets (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    available_balance  NUMERIC(14, 2) NOT NULL DEFAULT 0,
    pending_balance    NUMERIC(14, 2) NOT NULL DEFAULT 0,
    total_revenue      NUMERIC(14, 2) NOT NULL DEFAULT 0,
    total_withdrawn    NUMERIC(14, 2) NOT NULL DEFAULT 0,
    total_transactions BIGINT         NOT NULL DEFAULT 0,
    created_at         TIMESTAMP      DEFAULT CURRENT_TIMESTAMP,
    updated_at         TIMESTAMP      DEFAULT CURRENT_TIMESTAMP,
    -- Platform revenue is money the platform holds; it must never go negative.
    CONSTRAINT chk_platform_wallet_balances_non_negative
        CHECK (available_balance >= 0 AND pending_balance >= 0)
);

CREATE TABLE IF NOT EXISTS platform_wallet_ledger (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_reference VARCHAR(255)   NOT NULL UNIQUE,
    booking_id            UUID,
    escrow_id             UUID,
    client_id             UUID,
    worker_id             UUID,
    total_job_amount      NUMERIC(14, 2),
    platform_fee_percent  NUMERIC(5, 2),
    platform_fee_amount   NUMERIC(14, 2),
    worker_payout         NUMERIC(14, 2),
    balance_before        NUMERIC(14, 2),
    balance_after         NUMERIC(14, 2),
    transaction_type      VARCHAR(50)    NOT NULL,
    description           TEXT,
    -- Quoted because TIMESTAMP is a type keyword; the entity maps this field to
    -- a column literally named "timestamp".
    "timestamp"           TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at            TIMESTAMP      DEFAULT CURRENT_TIMESTAMP,
    -- ON DELETE SET NULL, not CASCADE: the ledger is a financial audit trail and
    -- must survive the deletion of either party to the transaction.
    CONSTRAINT fk_platform_ledger_client
        FOREIGN KEY (client_id) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT fk_platform_ledger_worker
        FOREIGN KEY (worker_id) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT chk_platform_ledger_type CHECK (transaction_type IN (
        'ESCROW_RELEASE', 'WITHDRAWAL', 'WITHDRAWAL_CANCELLED', 'WITHDRAWAL_FAILED',
        'ADMIN_ADJUSTMENT_CREDIT', 'ADMIN_ADJUSTMENT_DEBIT'
    ))
);

CREATE INDEX IF NOT EXISTS idx_platform_ledger_booking
    ON platform_wallet_ledger (booking_id);
CREATE INDEX IF NOT EXISTS idx_platform_ledger_escrow
    ON platform_wallet_ledger (escrow_id);
CREATE INDEX IF NOT EXISTS idx_platform_ledger_timestamp
    ON platform_wallet_ledger ("timestamp");

CREATE TABLE IF NOT EXISTS platform_withdrawals (
    id                               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    withdrawal_reference             VARCHAR(255)   NOT NULL UNIQUE,
    requested_by                     UUID           NOT NULL,
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
    CONSTRAINT fk_platform_withdrawal_user
        FOREIGN KEY (requested_by) REFERENCES users (id),
    CONSTRAINT chk_platform_withdrawal_amount_positive CHECK (amount > 0),
    CONSTRAINT chk_platform_withdrawal_method CHECK (withdrawal_method IN (
        'MPESA_B2C', 'BANK_TRANSFER'
    )),
    CONSTRAINT chk_platform_withdrawal_status CHECK (status IN (
        'PENDING', 'PROCESSING', 'SUCCESSFUL', 'FAILED', 'CANCELLED'
    ))
);

CREATE INDEX IF NOT EXISTS idx_platform_withdrawal_status
    ON platform_withdrawals (status);
CREATE INDEX IF NOT EXISTS idx_platform_withdrawal_timestamp
    ON platform_withdrawals (created_at);
