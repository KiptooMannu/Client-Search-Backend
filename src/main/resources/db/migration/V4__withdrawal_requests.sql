-- ══════════════════════════════════════════════════════════════════════
-- KaziKonnect — Withdrawal Requests Schema
-- ══════════════════════════════════════════════════════════════════════

CREATE TABLE IF NOT EXISTS withdrawal_requests (
    id            UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id       UUID           NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    amount        NUMERIC(14,2)  NOT NULL,
    phone_number  VARCHAR(50)    NOT NULL,
    status        VARCHAR(20)    NOT NULL, -- PENDING, APPROVED, REJECTED, FAILED
    created_at    TIMESTAMP      NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_withdrawal_user ON withdrawal_requests(user_id);
CREATE INDEX IF NOT EXISTS idx_withdrawal_status ON withdrawal_requests(status);
