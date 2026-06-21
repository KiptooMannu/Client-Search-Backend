-- KaziKonnect Initial Schema (corrected)
-- ══════════════════════════════════════════════════════════════════════
-- KaziKonnect — Initial Schema
-- Base schema creation for KaziKonnect platform
-- ══════════════════════════════════════════════════════════════════════

-- ──────────────────────────────────────────────────────────────────────
-- 1. Users table
-- ──────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS users (
    id                  UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    username            VARCHAR(255) NOT NULL UNIQUE,
    email               VARCHAR(255) NOT NULL UNIQUE,
    first_name          VARCHAR(255),
    last_name           VARCHAR(255),
    full_name           VARCHAR(255),
    profile_picture_url VARCHAR(500),
    role                VARCHAR(20)  NOT NULL CHECK (role IN ('CLIENT', 'WORKER', 'ADMIN')),
    created_at          TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_user_role ON users(role);

-- ──────────────────────────────────────────────────────────────────────
-- 2. Auth table
-- ──────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS auth (
    id                              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id                         UUID         NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    password_hash                   VARCHAR(255) NOT NULL,
    last_login                      TIMESTAMP,
    is_active                       BOOLEAN      NOT NULL DEFAULT TRUE,
    email_verified                  BOOLEAN      NOT NULL DEFAULT FALSE,
    email_verification_token        VARCHAR(255),
    email_verification_token_expiry TIMESTAMP,
    created_at                      TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_auth_user ON auth(user_id);

-- ──────────────────────────────────────────────────────────────────────
-- 3. Worker profiles table
-- ──────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS worker_profiles (
    id               UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id          UUID         NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    full_name        VARCHAR(255),
    phone_number     VARCHAR(255),
    experience_years INTEGER,
    hourly_rate      NUMERIC(14,2),
    profile_picture_url VARCHAR(500),
    category         VARCHAR(255),
    location         VARCHAR(255),
    bio              TEXT,
    status           VARCHAR(20)  NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT', 'PENDING', 'APPROVED', 'REJECTED')),
    is_visible       BOOLEAN      NOT NULL DEFAULT FALSE,
    is_online        BOOLEAN      NOT NULL DEFAULT FALSE,
    rejection_reason TEXT,
    last_seen        TIMESTAMP,
    approved_by      UUID,
    approved_at      TIMESTAMP,
    rejected_by      UUID,
    rejected_at      TIMESTAMP,
    -- Availability embedded fields
    availability_weekdays BOOLEAN      NOT NULL DEFAULT TRUE,
    availability_weekends BOOLEAN      NOT NULL DEFAULT TRUE,
    availability_evenings BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at       TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_worker_status ON worker_profiles(status);
CREATE INDEX IF NOT EXISTS idx_worker_is_visible ON worker_profiles(is_visible);
CREATE INDEX IF NOT EXISTS idx_worker_location ON worker_profiles(location);

-- Worker locations (join table for preferred locations)
CREATE TABLE IF NOT EXISTS worker_locations (
    worker_id     UUID NOT NULL REFERENCES worker_profiles(id) ON DELETE CASCADE,
    location_name VARCHAR(255) NOT NULL,
    PRIMARY KEY (worker_id, location_name)
);

-- ──────────────────────────────────────────────────────────────────────
-- 4. Skills table
-- ──────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS skills (
    id   UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL UNIQUE
);

-- Worker skills (join table)
CREATE TABLE IF NOT EXISTS worker_skills (
    worker_id UUID NOT NULL REFERENCES worker_profiles(id) ON DELETE CASCADE,
    skill_id  UUID NOT NULL REFERENCES skills(id) ON DELETE CASCADE,
    PRIMARY KEY (worker_id, skill_id)
);

-- ──────────────────────────────────────────────────────────────────────
-- 5. Work history table
-- ──────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS worker_work_history (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    worker_id   UUID         NOT NULL REFERENCES worker_profiles(id) ON DELETE CASCADE,
    company     VARCHAR(255) NOT NULL,
    role        VARCHAR(255) NOT NULL,
    period      VARCHAR(255),
    description TEXT
);

CREATE INDEX IF NOT EXISTS idx_work_history_worker ON worker_work_history(worker_id);

-- ──────────────────────────────────────────────────────────────────────
-- 6. Certifications table
-- ──────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS worker_certifications (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    worker_id   UUID         NOT NULL REFERENCES worker_profiles(id) ON DELETE CASCADE,
    name        VARCHAR(255) NOT NULL,
    issuer      VARCHAR(255) NOT NULL,
    issue_year  INTEGER
);

CREATE INDEX IF NOT EXISTS idx_cert_worker ON worker_certifications(worker_id);

-- ──────────────────────────────────────────────────────────────────────
-- 7. Documents table
-- ──────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS documents (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    worker_id     UUID         NOT NULL REFERENCES worker_profiles(id) ON DELETE CASCADE,
    type          VARCHAR(255) NOT NULL,
    document_name VARCHAR(255),
    document_url  VARCHAR(500) NOT NULL,
    public_id     VARCHAR(255),
    verified_by   UUID REFERENCES users(id),
    verified_at   TIMESTAMP,
    uploaded_at   TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_doc_worker ON documents(worker_id);
CREATE INDEX IF NOT EXISTS idx_doc_type ON documents(type);

-- ──────────────────────────────────────────────────────────────────────
-- 8. Client profiles table
-- ──────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS client_profiles (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id       UUID         NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    full_name     VARCHAR(255),
    phone_number  VARCHAR(255),
    location      VARCHAR(255),
    created_at    TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- ──────────────────────────────────────────────────────────────────────
-- 9. Job requests table
-- ──────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS job_requests (
    id                              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    client_id                       UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    worker_id                       UUID         NOT NULL REFERENCES worker_profiles(id) ON DELETE CASCADE,
    description                     TEXT         NOT NULL,
    status                          VARCHAR(20)  NOT NULL DEFAULT 'PENDING' CHECK (status IN ('NEGOTIATING', 'PENDING', 'ACCEPTED', 'ASSIGNED', 'IN_PROGRESS', 'SUBMITTED', 'REVISION_REQUESTED', 'REVISION', 'APPROVED', 'COMPLETED', 'DISPUTED', 'REJECTED', 'CANCELLED')),
    total_cost                      NUMERIC(14,2),
    job_price                       NUMERIC(14,2),
    negotiated_price                NUMERIC(14,2),
    required_experience             INTEGER,
    review_required                 BOOLEAN      NOT NULL DEFAULT FALSE,
    escrow_funded                   BOOLEAN      NOT NULL DEFAULT FALSE,
    handled_by                      UUID,
    rejection_reason                TEXT,
    started_at                      TIMESTAMP,
    submitted_at                    TIMESTAMP,
    approved_at                     TIMESTAMP,
    disputed_at                     TIMESTAMP,
    resolved_at                     TIMESTAMP,
    deadline                        TIMESTAMP,
    dispute_reason                  TEXT,
    dispute_evidence                TEXT,
    dispute_attachment_url          VARCHAR(500),
    dispute_response                TEXT,
    dispute_response_evidence       TEXT,
    dispute_response_attachment_url VARCHAR(500),
    admin_decision_reason           TEXT,
    admin_evidence_notes            TEXT,
    worker_partial_amount           NUMERIC(14,2),
    client_partial_amount           NUMERIC(14,2),
    created_at                      TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_job_client ON job_requests(client_id);
CREATE INDEX IF NOT EXISTS idx_job_worker ON job_requests(worker_id);
CREATE INDEX IF NOT EXISTS idx_job_status ON job_requests(status);

-- ──────────────────────────────────────────────────────────────────────
-- 10. Reviews table
-- ──────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS reviews (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    client_id       UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    worker_id       UUID         NOT NULL REFERENCES worker_profiles(id) ON DELETE CASCADE,
    job_request_id  UUID         UNIQUE REFERENCES job_requests(id) ON DELETE SET NULL,
    rating          INTEGER      NOT NULL CHECK (rating >= 1 AND rating <= 5),
    comment         TEXT,
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_review_worker ON reviews(worker_id);
CREATE INDEX IF NOT EXISTS idx_review_client ON reviews(client_id);
CREATE INDEX IF NOT EXISTS idx_review_job ON reviews(job_request_id);

-- ──────────────────────────────────────────────────────────────────────
-- 11. Escrow payments table (basic schema)
-- ──────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS escrow_payments (
    id                             UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    version                        BIGINT       DEFAULT 0 NOT NULL,
    job_request_id                 UUID         NOT NULL REFERENCES job_requests(id) ON DELETE CASCADE,
    status                         VARCHAR(20)  NOT NULL CHECK (status IN ('PENDING', 'SUCCESS', 'ESCROWED', 'RELEASED', 'REFUNDED', 'FAILED', 'PARTIALLY_SETTLED', 'DISPUTED', 'B2C_INITIATED', 'B2C_PENDING', 'B2C_RETRY_PENDING', 'B2C_FAILED', 'B2C_MAX_RETRIES_EXCEEDED')),
    amount                         DOUBLE PRECISION NOT NULL,
    client_phone_number            VARCHAR(255)  NOT NULL,
    checkout_request_id            VARCHAR(255) UNIQUE,
    idempotency_key                VARCHAR(255) UNIQUE,
    failure_reason                 VARCHAR(255),
    mpesa_receipt_number           VARCHAR(255),
    transaction_date               TIMESTAMP,
    platform_fee                   DOUBLE PRECISION,
    worker_amount                  DOUBLE PRECISION,
    message                        TEXT,
    timeout_at                     TIMESTAMP,
    b2c_conversation_id            VARCHAR(255),
    b2c_originator_conversation_id VARCHAR(255),
    b2c_transaction_id             VARCHAR(255),
    b2c_initiated_at               TIMESTAMP,
    b2c_completed_at               TIMESTAMP,
    b2c_retry_count                INTEGER,
    b2c_next_retry_at              TIMESTAMP,
    created_at                     TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at                     TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_escrow_job ON escrow_payments(job_request_id);
CREATE INDEX IF NOT EXISTS idx_escrow_checkout ON escrow_payments(checkout_request_id);

-- ──────────────────────────────────────────────────────────────────────
-- 12. Wallets table
-- ──────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS wallets (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID         NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    balance         NUMERIC(14,2) NOT NULL DEFAULT 0.0,
    total_earned    NUMERIC(14,2) NOT NULL DEFAULT 0.0,
    total_withdrawn NUMERIC(14,2) NOT NULL DEFAULT 0.0,
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_wallet_user ON wallets(user_id);

-- ──────────────────────────────────────────────────────────────────────
-- 13. Wallet transactions table
-- ──────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS wallet_transactions (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    txn_type        VARCHAR(20)  NOT NULL CHECK (txn_type IN ('CREDIT', 'DEBIT')),
    amount          NUMERIC(14,2) NOT NULL,
    balance_after   NUMERIC(14,2) NOT NULL,
    reference_id    UUID,
    description     VARCHAR(300),
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_txn_user ON wallet_transactions(user_id);
CREATE INDEX IF NOT EXISTS idx_txn_reference ON wallet_transactions(reference_id);
CREATE INDEX IF NOT EXISTS idx_txn_type ON wallet_transactions(txn_type);
CREATE INDEX IF NOT EXISTS idx_txn_created ON wallet_transactions(created_at);

-- ──────────────────────────────────────────────────────────────────────
-- 14. Withdrawal requests table
-- ──────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS withdrawal_requests (
    id                           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id                      UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    amount                       NUMERIC(14,2) NOT NULL,
    phone_number                 VARCHAR(255)  NOT NULL,
    status                       VARCHAR(255)  NOT NULL,
    b2c_conversation_id          VARCHAR(255),
    b2c_originator_conversation_id VARCHAR(255),
    b2c_transaction_id           VARCHAR(255),
    b2c_initiated_at             TIMESTAMP,
    b2c_completed_at             TIMESTAMP,
    b2c_retry_count              INTEGER      NOT NULL DEFAULT 0,
    b2c_next_retry_at             TIMESTAMP,
    created_at                   TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at                   TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_withdrawals_user ON withdrawal_requests(user_id);
CREATE INDEX IF NOT EXISTS idx_withdrawals_status ON withdrawal_requests(status);

-- ──────────────────────────────────────────────────────────────────────
-- 15. M-Pesa webhook logs table
-- ──────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS mpesa_webhook_logs (
    id                  VARCHAR(255) PRIMARY KEY,
    checkout_request_id VARCHAR(255),
    result_code         INTEGER,
    result_description  TEXT,
    payload             TEXT,
    processed_at        TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- ──────────────────────────────────────────────────────────────────────
-- 16. Payment audit log table
-- ──────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS payment_audit_log (
    id                 UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    escrow_payment_id  UUID         REFERENCES escrow_payments(id) ON DELETE SET NULL,
    event_type         VARCHAR(255) NOT NULL,
    payload            TEXT,
    actor              VARCHAR(255),
    reason             VARCHAR(500),
    created_at         TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- ──────────────────────────────────────────────────────────────────────
-- 17. Messages table
-- ──────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS messages (
    id             UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    sender_id      UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    receiver_id    UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    content        TEXT         NOT NULL,
    attachment_url VARCHAR(500),
    is_read        BOOLEAN      NOT NULL DEFAULT FALSE,
    sent_at        TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_sender_receiver ON messages(sender_id, receiver_id);
CREATE INDEX IF NOT EXISTS idx_sent_at ON messages(sent_at);
CREATE INDEX IF NOT EXISTS idx_is_read ON messages(is_read);
CREATE INDEX IF NOT EXISTS idx_receiver_id_read ON messages(receiver_id, is_read);

-- ──────────────────────────────────────────────────────────────────────
-- 18. Notifications table
-- ──────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS notifications (
    id         UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    title      VARCHAR(255),
    message    TEXT,
    type       VARCHAR(255)  CHECK (type IN ('INFO', 'SUCCESS', 'WARNING', 'ERROR')),
    is_read    BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_notification_user ON notifications(user_id);
CREATE INDEX IF NOT EXISTS idx_notification_user_read ON notifications(user_id, is_read);

-- ──────────────────────────────────────────────────────────────────────
-- 19. Refresh tokens table
-- ──────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS refresh_tokens (
    id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token        VARCHAR(512) NOT NULL UNIQUE,
    expires_at   TIMESTAMP    NOT NULL,
    is_revoked   BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at   TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_refresh_token_user ON refresh_tokens(user_id);
CREATE INDEX IF NOT EXISTS idx_refresh_token_token ON refresh_tokens(token);
CREATE INDEX IF NOT EXISTS idx_refresh_token_expires ON refresh_tokens(expires_at);
CREATE INDEX IF NOT EXISTS idx_refresh_token_is_revoked ON refresh_tokens(is_revoked);

-- ──────────────────────────────────────────────────────────────────────
-- 20. Password reset tokens table
-- ──────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS password_reset_tokens (
    id         UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token      VARCHAR(255) NOT NULL UNIQUE,
    expiry_date TIMESTAMP   NOT NULL,
    created_at TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_password_reset_user ON password_reset_tokens(user_id);
CREATE INDEX IF NOT EXISTS idx_password_reset_token ON password_reset_tokens(token);

-- ──────────────────────────────────────────────────────────────────────
-- 21. Admin logs table
-- ──────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS admin_logs (
    id         UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    admin_id   UUID         NOT NULL,
    action     VARCHAR(255) NOT NULL,
    target_id  UUID,
    created_at TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_admin_log_admin ON admin_logs(admin_id);
CREATE INDEX IF NOT EXISTS idx_admin_log_action ON admin_logs(action);
CREATE INDEX IF NOT EXISTS idx_admin_log_created ON admin_logs(created_at DESC);
