-- Fix user role check constraint to match Java enum values (uppercase)
-- The Java enum UserRole uses: WORKER, CLIENT, ADMIN
-- The old constraint used title case: 'Client', 'Worker', 'Admin'

-- Drop the old constraint
ALTER TABLE users DROP CONSTRAINT IF EXISTS users_role_check;

-- Add the new constraint with uppercase values to match the Java enum
ALTER TABLE users ADD CONSTRAINT users_role_check 
    CHECK (role IN ('WORKER', 'CLIENT', 'ADMIN'));
