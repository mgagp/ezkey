-- Quick fix to enable passwordless login for admin zero
-- Run this after initial migration if admin was created by V3

UPDATE ezkey_admin 
SET password_change_required = false,
    passwordless_enabled = true,
    challenge_required = false
WHERE username = 'admin';

-- Verify the update
SELECT 
    username, 
    password_change_required, 
    passwordless_enabled,
    challenge_required,
    mfa_enrollment_id,
    array_length(recovery_codes, 1) as recovery_codes_count
FROM ezkey_admin 
WHERE username = 'admin';

