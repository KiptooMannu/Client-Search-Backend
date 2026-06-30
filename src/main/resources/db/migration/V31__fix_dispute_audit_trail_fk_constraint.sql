-- Migration: Fix dispute_audit_trail foreign key constraint to use CASCADE
-- This allows deletion of users without violating the constraint

ALTER TABLE dispute_audit_trail 
DROP CONSTRAINT dispute_audit_trail_actor_id_fkey;

ALTER TABLE dispute_audit_trail 
ADD CONSTRAINT dispute_audit_trail_actor_id_fkey 
FOREIGN KEY (actor_id) REFERENCES users(id) ON DELETE CASCADE;
