-- Migration: Fix dispute audit trail actor_id null constraint
-- When a dispute is filed, the actor_id should fall back to filed_by_id if no admin is assigned yet

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
