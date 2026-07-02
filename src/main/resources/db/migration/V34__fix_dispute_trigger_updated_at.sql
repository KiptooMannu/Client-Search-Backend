-- Migration: Fix dispute trigger to remove non-existent updated_at column
-- The trigger update_job_request_timestamp_on_dispute_change was trying to update
-- job_requests.updated_at which doesn't exist in the table schema

-- Drop and recreate the trigger function without the updated_at update
CREATE OR REPLACE FUNCTION update_job_request_timestamp_on_dispute_change()
RETURNS TRIGGER AS $$
BEGIN
    UPDATE job_requests 
    SET has_active_dispute = (NEW.dispute_status NOT IN ('RESOLVED', 'CLOSED'))
    WHERE id = (SELECT job_request_id FROM disputes WHERE id = NEW.id);
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Recreate the trigger
DROP TRIGGER IF EXISTS trg_dispute_update_job_request_timestamp ON disputes;
CREATE TRIGGER trg_dispute_update_job_request_timestamp
AFTER UPDATE ON disputes
FOR EACH ROW
WHEN (OLD.dispute_status IS DISTINCT FROM NEW.dispute_status)
EXECUTE FUNCTION update_job_request_timestamp_on_dispute_change();
