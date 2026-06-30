-- Migration: Assign all unassigned disputes to the first admin user
-- This ensures that when an admin logs in, they can see all disputes

UPDATE disputes
SET assigned_to_admin_id = (
    SELECT id FROM users 
    WHERE role = 'ADMIN' 
    ORDER BY created_at ASC 
    LIMIT 1
)
WHERE assigned_to_admin_id IS NULL;
