-- Migration: Comprehensive Dispute Management System
-- Introduces dedicated tables for disputes, evidence, messaging, and audit trails

-- ──────────────────────────────────────────────────────────────────────
-- 1. Dispute Reasons Enum Table (Lookup)
-- ──────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS dispute_reasons (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    reason_key VARCHAR(100) NOT NULL UNIQUE,
    reason_name VARCHAR(255) NOT NULL,
    description TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Populate dispute reasons
INSERT INTO dispute_reasons (reason_key, reason_name, description) VALUES
    ('WORK_NOT_COMPLETED', 'Work Not Completed', 'The worker did not complete the agreed-upon work'),
    ('POOR_QUALITY', 'Poor Quality', 'The work quality does not meet expectations'),
    ('INCOMPLETE_DELIVERY', 'Incomplete Delivery', 'Only partial work was delivered'),
    ('MISSED_DEADLINE', 'Missed Deadline', 'The worker failed to meet the agreed deadline'),
    ('COMMUNICATION_BREAKDOWN', 'Communication Breakdown', 'Unable to communicate effectively with the other party'),
    ('PAYMENT_DISPUTE', 'Payment Dispute', 'Disagreement about the payment amount or terms'),
    ('MATERIAL_ISSUES', 'Material/Supply Issues', 'Issues with materials or supplies provided'),
    ('SCOPE_CHANGE_DISPUTE', 'Scope Change Dispute', 'Disagreement about scope changes or additions'),
    ('SERVICE_NOT_PROVIDED', 'Service Not Provided', 'The service was not provided as agreed'),
    ('OTHER', 'Other', 'Other reason not listed above')
ON CONFLICT DO NOTHING;

-- ──────────────────────────────────────────────────────────────────────
-- 2. Main Disputes Table
-- ──────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS disputes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    job_request_id UUID NOT NULL UNIQUE REFERENCES job_requests(id) ON DELETE CASCADE,
    escrow_payment_id UUID NOT NULL REFERENCES escrow_payments(id) ON DELETE RESTRICT,
    filed_by_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    assigned_to_admin_id UUID REFERENCES users(id) ON DELETE SET NULL,
    
    -- Dispute Information
    dispute_reason_key VARCHAR(100) NOT NULL,
    dispute_description TEXT NOT NULL,
    dispute_priority VARCHAR(20) NOT NULL DEFAULT 'MEDIUM' 
        CHECK (dispute_priority IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    dispute_status VARCHAR(50) NOT NULL DEFAULT 'OPEN'
        CHECK (dispute_status IN ('OPEN', 'AWAITING_EVIDENCE', 'IN_REVIEW', 'RESOLVED', 'CLOSED')),
    
    -- Resolution Information (NULL until resolved)
    resolution_type VARCHAR(50),
        -- CHECK (resolution_type IN ('FULL_REFUND_TO_CLIENT', 'FULL_PAYMENT_TO_WORKER', 'SPLIT'))
    client_resolution_amount NUMERIC(14,2),
    worker_resolution_amount NUMERIC(14,2),
    admin_resolution_reason TEXT,
    admin_internal_notes TEXT,
    resolved_at TIMESTAMP,
    resolved_by_admin_id UUID REFERENCES users(id) ON DELETE SET NULL,
    
    -- Timestamps
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    evidence_requested_at TIMESTAMP,
    
    -- Version/Concurrency
    version BIGINT DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_dispute_job ON disputes(job_request_id);
CREATE INDEX IF NOT EXISTS idx_dispute_escrow ON disputes(escrow_payment_id);
CREATE INDEX IF NOT EXISTS idx_dispute_filed_by ON disputes(filed_by_id);
CREATE INDEX IF NOT EXISTS idx_dispute_status ON disputes(dispute_status);
CREATE INDEX IF NOT EXISTS idx_dispute_created_at ON disputes(created_at);
CREATE INDEX IF NOT EXISTS idx_dispute_priority ON disputes(dispute_priority);

-- ──────────────────────────────────────────────────────────────────────
-- 3. Dispute Evidence Table (File Uploads)
-- ──────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS dispute_evidence (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    dispute_id UUID NOT NULL REFERENCES disputes(id) ON DELETE CASCADE,
    uploaded_by_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    
    -- File Information
    file_name VARCHAR(500) NOT NULL,
    file_url VARCHAR(1000) NOT NULL,
    file_type VARCHAR(50),  -- 'screenshot', 'photo', 'video', 'pdf', 'contract', 'receipt', 'other'
    file_size_bytes BIGINT,
    mime_type VARCHAR(100),
    
    -- Metadata
    description TEXT,
    is_admin_requested BOOLEAN DEFAULT FALSE,
    admin_evidence_request_type VARCHAR(100),  -- 'screenshot', 'video', 'receipt', 'work_progress', 'other'
    
    -- Timestamps
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_evidence_dispute ON dispute_evidence(dispute_id);
CREATE INDEX IF NOT EXISTS idx_evidence_uploaded_by ON dispute_evidence(uploaded_by_id);
CREATE INDEX IF NOT EXISTS idx_evidence_created_at ON dispute_evidence(created_at);

-- ──────────────────────────────────────────────────────────────────────
-- 4. Dispute Evidence Request Table (Track Evidence Requests)
-- ──────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS dispute_evidence_requests (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    dispute_id UUID NOT NULL REFERENCES disputes(id) ON DELETE CASCADE,
    requested_by_admin_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    requested_from_user_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    
    -- Request Details
    request_type VARCHAR(100) NOT NULL,  -- 'screenshot', 'video', 'receipt', 'work_progress', 'other'
    request_description TEXT,
    request_status VARCHAR(50) NOT NULL DEFAULT 'PENDING'
        CHECK (request_status IN ('PENDING', 'PROVIDED', 'SATISFIED', 'OVERDUE')),
    
    -- Due Date
    due_date TIMESTAMP,
    
    -- Timestamps
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    fulfilled_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_evidence_request_dispute ON dispute_evidence_requests(dispute_id);
CREATE INDEX IF NOT EXISTS idx_evidence_request_status ON dispute_evidence_requests(request_status);
CREATE INDEX IF NOT EXISTS idx_evidence_request_requested_from ON dispute_evidence_requests(requested_from_user_id);

-- ──────────────────────────────────────────────────────────────────────
-- 5. Dispute Messages Table (Communications)
-- ──────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS dispute_messages (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    dispute_id UUID NOT NULL REFERENCES disputes(id) ON DELETE CASCADE,
    sender_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    message_type VARCHAR(50) NOT NULL DEFAULT 'REGULAR'
        CHECK (message_type IN ('REGULAR', 'EVIDENCE_REQUEST', 'ADMIN_NOTE', 'RESOLUTION', 'NOTIFICATION')),
    
    -- Message Content
    message_text TEXT NOT NULL,
    is_admin_only BOOLEAN DEFAULT FALSE,  -- Admin internal notes
    
    -- Timestamps
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    is_read_by_client BOOLEAN DEFAULT FALSE,
    is_read_by_worker BOOLEAN DEFAULT FALSE,
    is_read_by_admin BOOLEAN DEFAULT FALSE
);

CREATE INDEX IF NOT EXISTS idx_message_dispute ON dispute_messages(dispute_id);
CREATE INDEX IF NOT EXISTS idx_message_sender ON dispute_messages(sender_id);
CREATE INDEX IF NOT EXISTS idx_message_created_at ON dispute_messages(created_at);

-- ──────────────────────────────────────────────────────────────────────
-- 6. Dispute Audit Trail Table (Immutable)
-- ──────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS dispute_audit_trail (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    dispute_id UUID NOT NULL REFERENCES disputes(id) ON DELETE CASCADE,
    actor_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    
    -- Action Information
    action_type VARCHAR(100) NOT NULL,
        -- 'DISPUTE_FILED', 'STATUS_CHANGED', 'EVIDENCE_UPLOADED', 'MESSAGE_ADDED', 
        -- 'EVIDENCE_REQUESTED', 'PRIORITY_CHANGED', 'ASSIGNED_TO_ADMIN', 
        -- 'RESOLUTION_ISSUED', 'FUNDS_DISTRIBUTED', etc.
    action_description TEXT,
    old_value TEXT,
    new_value TEXT,
    additional_data JSONB,  -- For storing complex data structures
    
    -- Timestamps (Immutable)
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_audit_dispute ON dispute_audit_trail(dispute_id);
CREATE INDEX IF NOT EXISTS idx_audit_actor ON dispute_audit_trail(actor_id);
CREATE INDEX IF NOT EXISTS idx_audit_action_type ON dispute_audit_trail(action_type);
CREATE INDEX IF NOT EXISTS idx_audit_created_at ON dispute_audit_trail(created_at);

-- ──────────────────────────────────────────────────────────────────────
-- 7. Add Dispute Status to Escrow Payments
-- ──────────────────────────────────────────────────────────────────────
ALTER TABLE escrow_payments 
    ADD COLUMN IF NOT EXISTS is_locked_by_dispute BOOLEAN DEFAULT FALSE;

CREATE INDEX IF NOT EXISTS idx_escrow_dispute_locked ON escrow_payments(is_locked_by_dispute);

-- ──────────────────────────────────────────────────────────────────────
-- 8. Add Dispute Status to Job Requests
-- ──────────────────────────────────────────────────────────────────────
ALTER TABLE job_requests 
    ADD COLUMN IF NOT EXISTS has_active_dispute BOOLEAN DEFAULT FALSE;

CREATE INDEX IF NOT EXISTS idx_job_active_dispute ON job_requests(has_active_dispute);

-- ──────────────────────────────────────────────────────────────────────
-- 9. Trigger: Update job_requests.updated_at when dispute status changes
-- ──────────────────────────────────────────────────────────────────────
CREATE OR REPLACE FUNCTION update_job_request_timestamp_on_dispute_change()
RETURNS TRIGGER AS $$
BEGIN
    UPDATE job_requests 
    SET updated_at = NOW(), has_active_dispute = (NEW.dispute_status NOT IN ('RESOLVED', 'CLOSED'))
    WHERE id = (SELECT job_request_id FROM disputes WHERE id = NEW.id);
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_dispute_update_job_request_timestamp ON disputes;
CREATE TRIGGER trg_dispute_update_job_request_timestamp
AFTER UPDATE ON disputes
FOR EACH ROW
WHEN (OLD.dispute_status IS DISTINCT FROM NEW.dispute_status)
EXECUTE FUNCTION update_job_request_timestamp_on_dispute_change();

-- ──────────────────────────────────────────────────────────────────────
-- 10. Trigger: Log all dispute changes to audit trail
-- ──────────────────────────────────────────────────────────────────────
CREATE OR REPLACE FUNCTION log_dispute_audit_trail()
RETURNS TRIGGER AS $$
BEGIN
    INSERT INTO dispute_audit_trail (
        dispute_id, actor_id, action_type, action_description, 
        old_value, new_value, additional_data, created_at
    ) VALUES (
        NEW.id,
        COALESCE(NEW.resolved_by_admin_id, NEW.assigned_to_admin_id, NEW.filed_by_id),
        CASE 
            WHEN OLD IS NULL THEN 'DISPUTE_FILED'
            WHEN OLD.dispute_status != NEW.dispute_status THEN 'STATUS_CHANGED'
            WHEN OLD.dispute_priority != NEW.dispute_priority THEN 'PRIORITY_CHANGED'
            WHEN OLD.assigned_to_admin_id != NEW.assigned_to_admin_id THEN 'ASSIGNED_TO_ADMIN'
            WHEN OLD.dispute_status = 'IN_REVIEW' AND NEW.dispute_status = 'RESOLVED' THEN 'RESOLUTION_ISSUED'
            ELSE 'UPDATED'
        END,
        'Dispute updated',
        CASE 
            WHEN OLD.dispute_status != NEW.dispute_status THEN OLD.dispute_status
            WHEN OLD.dispute_priority != NEW.dispute_priority THEN OLD.dispute_priority
            ELSE NULL
        END,
        CASE 
            WHEN OLD.dispute_status != NEW.dispute_status THEN NEW.dispute_status
            WHEN OLD.dispute_priority != NEW.dispute_priority THEN NEW.dispute_priority
            ELSE NULL
        END,
        CASE 
            WHEN NEW.resolution_type IS NOT NULL THEN jsonb_build_object(
                'resolution_type', NEW.resolution_type,
                'client_amount', NEW.client_resolution_amount,
                'worker_amount', NEW.worker_resolution_amount
            )
            ELSE NULL
        END,
        NOW()
    );
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_log_dispute_changes ON disputes;
CREATE TRIGGER trg_log_dispute_changes
AFTER INSERT OR UPDATE ON disputes
FOR EACH ROW
EXECUTE FUNCTION log_dispute_audit_trail();
