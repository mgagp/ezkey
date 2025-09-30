-- Migration V4: Reset admin password to placeholder for testing
-- This migration resets the admin password back to placeholder for testing the initialization

UPDATE ezkey_admin 
SET password_hash = '$2a$10$placeholder.defined.at.first.execution',
    password_change_required = true
WHERE username = 'admin';
