-- Migration: Replace Ed25519 with EC P-256
-- Description: Updates column comments to reflect EC P-256 instead of Ed25519.
-- This migration aligns the backend with the mobile application's native hardware-backed cryptography.

-- Update table comment
COMMENT ON TABLE ezkey_enrollment IS 'Device enrollment table establishing cryptographic binding between integration, user, and mobile device. Contains EC P-256 key pairs and tracks enrollment lifecycle from creation through verification. Each enrollment enables authentication attempts and includes security configuration like challenge requirements.';

-- Update column comments
COMMENT ON COLUMN ezkey_enrollment.integration_private_key IS 'EC P-256 private key for integration-side cryptographic operations (PKCS#8 format, Base64 encoded) - SENSITIVE DATA requiring encryption at rest and secure handling. Used by backend to sign authAttemptProofToken for mutual authentication.';

COMMENT ON COLUMN ezkey_enrollment.integration_public_key IS 'EC P-256 public key for integration-side operations (X.509 SubjectPublicKeyInfo format, Base64 encoded) - corresponding public key for integration_private_key, safe to expose. Used by mobile app to verify backend signatures (authAttemptProofTokenSignedByIntegration).';

COMMENT ON COLUMN ezkey_enrollment.device_public_key IS 'EC P-256 public key from mobile device (X.509 SubjectPublicKeyInfo format, Base64 encoded) - used to verify signatures from device during authentication attempts, set during binding process.';

