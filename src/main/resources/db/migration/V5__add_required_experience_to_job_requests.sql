-- Add required_experience column to job_requests for storing requested experience years
ALTER TABLE job_requests
ADD COLUMN IF NOT EXISTS required_experience integer;
