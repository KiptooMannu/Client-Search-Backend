-- ===============================
-- Indexes for worker_profiles and related tables
-- Fully defensive: checks table AND column existence before each index
-- ===============================

DO $$ BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'worker_profiles') THEN

    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'worker_profiles' AND column_name = 'status') THEN
      CREATE INDEX IF NOT EXISTS idx_worker_status_visible
          ON worker_profiles(status, is_visible);
      CREATE INDEX IF NOT EXISTS idx_worker_active_marketplace
          ON worker_profiles(id, location, experience_years, hourly_rate)
          WHERE status = 'APPROVED' AND is_visible = true;
    END IF;

    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'worker_profiles' AND column_name = 'location') THEN
      CREATE INDEX IF NOT EXISTS idx_worker_location
          ON worker_profiles(location) WHERE location IS NOT NULL;
    END IF;

    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'worker_profiles' AND column_name = 'experience_years') THEN
      CREATE INDEX IF NOT EXISTS idx_worker_experience
          ON worker_profiles(experience_years);
    END IF;

    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'worker_profiles' AND column_name = 'hourly_rate') THEN
      CREATE INDEX IF NOT EXISTS idx_worker_hourly_rate
          ON worker_profiles(hourly_rate);
    END IF;

    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'worker_profiles' AND column_name = 'category') THEN
      CREATE INDEX IF NOT EXISTS idx_worker_category
          ON worker_profiles(category);
    END IF;

    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'worker_profiles' AND column_name = 'is_online') THEN
      CREATE INDEX IF NOT EXISTS idx_worker_online
          ON worker_profiles(is_online) WHERE is_online = true;
    END IF;

  END IF;
END $$;

-- ===============================
-- Indexes for skills table
-- ===============================

DO $$ BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'skills' AND column_name = 'name') THEN
    CREATE INDEX IF NOT EXISTS idx_skill_name ON skills(name);
  END IF;
END $$;

-- ===============================
-- Indexes for worker_skills join table
-- (column name discovered dynamically — skip if columns don't match expected names)
-- ===============================

DO $$ BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'worker_skills' AND column_name = 'worker_id') THEN
    CREATE INDEX IF NOT EXISTS idx_worker_skill_worker    ON worker_skills(worker_id);
    CREATE INDEX IF NOT EXISTS idx_worker_skill_composite ON worker_skills(worker_id, skill_id);
  END IF;
  IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'worker_skills' AND column_name = 'skill_id') THEN
    CREATE INDEX IF NOT EXISTS idx_worker_skill_skill ON worker_skills(skill_id);
  END IF;
END $$;

-- ===============================
-- Indexes for reviews
-- ===============================

DO $$ BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'reviews' AND column_name = 'worker_profile_id') THEN
    CREATE INDEX IF NOT EXISTS idx_review_worker ON reviews(worker_profile_id);
    CREATE INDEX IF NOT EXISTS idx_review_rating ON reviews(worker_profile_id, rating);
  END IF;
END $$;

-- ===============================
-- Indexes for users table
-- ===============================

DO $$ BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'users' AND column_name = 'username') THEN
    CREATE INDEX IF NOT EXISTS idx_user_username ON users(username);
  END IF;
  IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'users' AND column_name = 'email') THEN
    CREATE INDEX IF NOT EXISTS idx_user_email ON users(email);
  END IF;
END $$;
