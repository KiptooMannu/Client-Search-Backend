-- Add client_counter_offer column to track client's counter-offers separately from worker's negotiated price
ALTER TABLE job_requests 
ADD COLUMN client_counter_offer NUMERIC(14, 2) NULL;

-- Create index for efficient queries on client counter-offers
CREATE INDEX idx_job_client_counter_offer ON job_requests(client_counter_offer);
