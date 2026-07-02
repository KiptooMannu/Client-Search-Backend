CREATE TABLE IF NOT EXISTS job_progresses (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    job_request_id UUID NOT NULL REFERENCES job_requests(id) ON DELETE CASCADE,
    description TEXT NOT NULL,
    attachment_url VARCHAR(1024),
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_job_progress_job_request ON job_progresses(job_request_id);
CREATE INDEX IF NOT EXISTS idx_job_progress_created_at ON job_progresses(created_at);
