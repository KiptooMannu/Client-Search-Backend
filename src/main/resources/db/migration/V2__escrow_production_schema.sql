-- ══════════════════════════════════════════════════════════════════════
-- KaziKonnect — Escrow Payment Production Schema
-- Run this migration after deploying the updated Java code.
-- ══════════════════════════════════════════════════════════════════════

-- ──────────────────────────────────────────────────────────────────────
-- 1. Enhance escrow_payments table
-- ──────────────────────────────────────────────────────────────────────
ALTER TABLE escrow_payments
    ADD COLUMN IF NOT EXISTS idempotency_key      VARCHAR(255) UNIQUE,
    ADD COLUMN IF NOT EXISTS callback_raw_payload  JSONB,
    ADD COLUMN IF NOT EXISTS payment_timeout_at    TIMESTAMP,
    ADD COLUMN IF NOT EXISTS version               BIGINT DEFAULT 0 NOT NULL;

-- Back-fill timeout for existing PENDING rows (10 min from creation)
UPDATE escrow_payments
SET    payment_timeout_at = created_at + INTERVAL '10 minutes'
WHERE  payment_timeout_at IS NULL
  AND  status = 'PENDING';

-- Indexes for scheduler and idempotency queries
CREATE INDEX IF NOT EXISTS idx_escrow_timeout      ON escrow_payments(payment_timeout_at) WHERE status = 'PENDING';
CREATE INDEX IF NOT EXISTS idx_escrow_idempotency  ON escrow_payments(idempotency_key);

-- ──────────────────────────────────────────────────────────────────────
-- 2. Payment audit log
-- ──────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS payment_audit_log (
    id                  UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    escrow_payment_id   UUID         REFERENCES escrow_payments(id) ON DELETE SET NULL,
    event_type          VARCHAR(50)  NOT NULL,
    payload             JSONB,
    actor               VARCHAR(100),
    reason              VARCHAR(500),
    created_at          TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_audit_payment ON payment_audit_log(escrow_payment_id);
CREATE INDEX IF NOT EXISTS idx_audit_event   ON payment_audit_log(event_type);
CREATE INDEX IF NOT EXISTS idx_audit_created ON payment_audit_log(created_at DESC);

-- ──────────────────────────────────────────────────────────────────────
-- 3. Worker wallets
-- ──────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS wallets (
    id               UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id          UUID           NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    balance          NUMERIC(14,2)  NOT NULL DEFAULT 0.00,
    total_earned     NUMERIC(14,2)  NOT NULL DEFAULT 0.00,
    total_withdrawn  NUMERIC(14,2)  NOT NULL DEFAULT 0.00,
    version          BIGINT         NOT NULL DEFAULT 0,
    updated_at       TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_wallet_user ON wallets(user_id);

-- ──────────────────────────────────────────────────────────────────────
-- 4. Wallet transactions ledger
-- ──────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS wallet_transactions (
    id             UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id        UUID           NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    txn_type       VARCHAR(10)    NOT NULL CHECK (txn_type IN ('CREDIT', 'DEBIT')),
    amount         NUMERIC(14,2)  NOT NULL,
    balance_after  NUMERIC(14,2)  NOT NULL,
    reference_id   UUID,
    description    VARCHAR(300),
    created_at     TIMESTAMP      NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_txn_user      ON wallet_transactions(user_id);
CREATE INDEX IF NOT EXISTS idx_txn_reference ON wallet_transactions(reference_id);
CREATE INDEX IF NOT EXISTS idx_txn_type      ON wallet_transactions(txn_type);
CREATE INDEX IF NOT EXISTS idx_txn_created   ON wallet_transactions(created_at DESC);
