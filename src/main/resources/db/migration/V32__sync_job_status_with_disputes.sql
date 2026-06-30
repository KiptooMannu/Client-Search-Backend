-- Migration: Sync job status with dispute status
-- This ensures that jobs with active disputes have their status set to DISPUTED
-- and jobs with resolved disputes have their has_active_dispute flag updated

-- Update jobs that have active disputes (not resolved/closed) to DISPUTED status
UPDATE job_requests jr
SET status = 'DISPUTED',
    has_active_dispute = true,
    disputed_at = COALESCE(jr.disputed_at, d.created_at)
FROM disputes d
WHERE d.job_request_id = jr.id
  AND d.dispute_status NOT IN ('RESOLVED', 'CLOSED')
  AND jr.status != 'DISPUTED';

-- Update jobs that have resolved disputes to remove has_active_dispute flag
UPDATE job_requests jr
SET has_active_dispute = false,
    resolved_at = COALESCE(jr.resolved_at, d.resolved_at)
FROM disputes d
WHERE d.job_request_id = jr.id
  AND d.dispute_status IN ('RESOLVED', 'CLOSED')
  AND jr.has_active_dispute = true;

-- Ensure all jobs with disputes have the has_active_dispute flag set correctly
UPDATE job_requests jr
SET has_active_dispute = (
    SELECT EXISTS (
        SELECT 1 FROM disputes d
        WHERE d.job_request_id = jr.id
        AND d.dispute_status NOT IN ('RESOLVED', 'CLOSED')
    )
)
WHERE EXISTS (
    SELECT 1 FROM disputes d
    WHERE d.job_request_id = jr.id
);
