-- Create an ADMIN user for testing
-- Run this in your database if needed

-- For PostgreSQL:
INSERT INTO "user" (id, username, email, full_name, role, created_at)
VALUES (
  'aaaaaaaa-bbbb-cccc-dddd-000000000001',
  'admin',
  'admin@test.com',
  'Admin User',
  'ADMIN',
  NOW()
) ON CONFLICT (email) DO NOTHING;

INSERT INTO auth (id, user_id, password_hash, is_active, created_at, last_login)
VALUES (
  'aaaaaaaa-bbbb-cccc-dddd-100000000001',
  'aaaaaaaa-bbbb-cccc-dddd-000000000001',
  '$2a$10$ZIHmKYgW.7yHxyLVDVxvK.jvTx0LB3.4jDt6IFIQy3fP/Ju1Pb0mG', -- bcrypt hash of 'admin123'
  true,
  NOW(),
  NULL
) ON CONFLICT DO NOTHING;

-- Test credentials:
-- Email: admin@test.com
-- Password: admin123
-- Role: ADMIN
