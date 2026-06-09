-- ===============================
-- Indexes for WorkerProfile and related tables
-- ===============================

-- 1. Most critical composite index for marketplace filtering
CREATE INDEX IF NOT EXISTS idx_worker_marketplace 
ON worker_profile(status, is_visible, location, experience_years);

-- 2. Index for status and visibility (most common filter)
CREATE INDEX IF NOT EXISTS idx_worker_status_visible 
ON worker_profile(status, is_visible);

-- 3. Index for location searches
CREATE INDEX IF NOT EXISTS idx_worker_location 
ON worker_profile(location) WHERE location IS NOT NULL;

-- 4. Index for experience years filtering
CREATE INDEX IF NOT EXISTS idx_worker_experience 
ON worker_profile(experience_years);

-- 5. Index for hourly rate (if sorting by price)
CREATE INDEX IF NOT EXISTS idx_worker_hourly_rate 
ON worker_profile(hourly_rate);

-- 6. Index for category searches
CREATE INDEX IF NOT EXISTS idx_worker_category 
ON worker_profile(category);

-- 7. Index for online status
CREATE INDEX IF NOT EXISTS idx_worker_online 
ON worker_profile(is_online) WHERE is_online = true;

-- 8. Composite index for full‑text search simulation
CREATE INDEX IF NOT EXISTS idx_worker_search 
ON worker_profile(status, is_visible, location, category, experience_years);

-- ===============================
-- Indexes for Skills (Many‑to‑Many)
-- ===============================

-- 9. Index for skill name lookups
CREATE INDEX IF NOT EXISTS idx_skill_name 
ON skill(name);

-- 10. Indexes for worker_skill join table
CREATE INDEX IF NOT EXISTS idx_worker_skill_worker 
ON worker_skill(worker_profile_id);

CREATE INDEX IF NOT EXISTS idx_worker_skill_skill 
ON worker_skill(skill_id);

-- 11. Composite index for skill filtering
CREATE INDEX IF NOT EXISTS idx_worker_skill_composite 
ON worker_skill(worker_profile_id, skill_id);

-- ===============================
-- Indexes for Reviews
-- ===============================

-- 12. Index for review lookups by worker
CREATE INDEX IF NOT EXISTS idx_review_worker 
ON review(worker_profile_id);

-- 13. Index for rating calculations
CREATE INDEX IF NOT EXISTS idx_review_rating 
ON review(worker_profile_id, rating);

-- ===============================
-- Indexes for User table
-- ===============================

-- 14. Index for user lookups
CREATE INDEX IF NOT EXISTS idx_user_username 
ON users(username);

CREATE INDEX IF NOT EXISTS idx_user_email 
ON users(email);

-- ===============================
-- Partial Indexes (Most Efficient)
-- ===============================

-- 15. Only index approved and visible workers
CREATE INDEX IF NOT EXISTS idx_worker_active_marketplace 
ON worker_profile(id, location, experience_years, hourly_rate) 
WHERE status = 'APPROVED' AND is_visible = true;

-- 16. Index for workers with skills
CREATE INDEX IF NOT EXISTS idx_worker_with_skills 
ON worker_profile(id) 
WHERE id IN (SELECT DISTINCT worker_profile_id FROM worker_skill);
