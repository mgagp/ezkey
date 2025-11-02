-- Ezkey Migration V11: Add Device Public Key Hash for Uniqueness Validation
-- Purpose: Add a separate hash column for device public key to enable uniqueness validation
-- independent of encryption format. This allows validation even if device_public_key is encrypted.

-- Add device_public_key_hash column
ALTER TABLE ezkey_enrollment
ADD COLUMN device_public_key_hash VARCHAR(64);

-- Add comment explaining the hash column
COMMENT ON COLUMN ezkey_enrollment.device_public_key_hash IS 
'SHA-256 hash of device_public_key (hexadecimal, 64 chars). Used for uniqueness validation independent of encryption format. NULL allowed for enrollments without device public key yet.';

-- Create unique index on device_public_key_hash (excluding NULL values)
-- This ensures that each device public key can only be used once for a verified enrollment
CREATE UNIQUE INDEX idx_enrollment_device_public_key_hash_unique 
ON ezkey_enrollment(device_public_key_hash) 
WHERE device_public_key_hash IS NOT NULL;

-- Add index comment
COMMENT ON INDEX idx_enrollment_device_public_key_hash_unique IS 
'Unique index on device public key hash to prevent reuse of device public keys. NULL values are excluded from uniqueness constraint.';

