-- Demo-only: when combined with ezkey.demo.mitm-signature-enabled on Auth API, the Pending
-- response body is altered after signing to simulate a MITM (integration signature mismatch).
ALTER TABLE ezkey_auth_attempt
    ADD COLUMN demo_mitm_signature_enabled BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN ezkey_auth_attempt.demo_mitm_signature_enabled IS
    'When true, Pending JSON may be tampered after signing if Auth API demo MITM is enabled.';
