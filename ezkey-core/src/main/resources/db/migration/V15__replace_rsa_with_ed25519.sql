-- Migration: Replace RSA-2048 with Ed25519
-- Description: Updates column comments to reflect Ed25519 instead of RSA-2048.
-- Note: Column structure remains unchanged - only comments are updated.
-- The integration_private_key and integration_public_key columns are kept as they are
-- critical for mutual cryptographic authentication (backend signs authAttemptProofToken).

-- Update table comment
COMMENT ON TABLE ezkey_enrollment IS 'Device enrollment table establishing cryptographic binding between integration, user, and mobile device. Contains Ed25519 key pairs and tracks enrollment lifecycle from creation through verification. Each enrollment enables authentication attempts and includes security configuration like challenge requirements.';

-- Update integration_private_key column comment
COMMENT ON COLUMN ezkey_enrollment.integration_private_key IS 'Ed25519 private key seed for integration-side cryptographic operations (32 bytes raw, Base64 encoded) - SENSITIVE DATA requiring encryption at rest and secure handling. Used by backend to sign authAttemptProofToken for mutual authentication.';

-- Update integration_public_key column comment
COMMENT ON COLUMN ezkey_enrollment.integration_public_key IS 'Ed25519 public key for integration-side operations (32 bytes raw, Base64 encoded) - corresponding public key for integration_private_key, safe to expose. Used by mobile app to verify backend signatures (authAttemptProofTokenSignedByIntegration).';

-- Update device_public_key column comment
COMMENT ON COLUMN ezkey_enrollment.device_public_key IS 'Ed25519 public key from mobile device (32 bytes raw, Base64 encoded) - used to verify signatures from device during authentication attempts, set during binding process.';

