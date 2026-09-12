-- V21: Drop demo MITM simulation column from ezkey_auth_attempt.
-- Presentation-only Pending sign-then-tamper is removed; the column is unused.

ALTER TABLE ezkey_auth_attempt DROP COLUMN demo_mitm_signature_enabled;
