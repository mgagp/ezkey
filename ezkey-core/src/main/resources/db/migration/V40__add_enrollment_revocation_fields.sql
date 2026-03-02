-- V40: Add enrollment revocation audit fields
--
-- Adds four nullable audit columns to ezkey_enrollment to support the enrollment
-- revocation lifecycle (SOC 2 CC6.3 — removal of access).
--
-- deactivated_at / deactivated_by_admin_id : set when an admin soft-deactivates an
--   enrollment (reversible). Cleared on reactivation.
-- revoked_at / revoked_by_admin_id : set when an admin permanently revokes an
--   enrollment (irreversible). Never cleared.

ALTER TABLE ezkey_enrollment
    ADD COLUMN deactivated_at         TIMESTAMPTZ,
    ADD COLUMN deactivated_by_admin_id INTEGER REFERENCES ezkey_admin(admin_id),
    ADD COLUMN revoked_at              TIMESTAMPTZ,
    ADD COLUMN revoked_by_admin_id     INTEGER REFERENCES ezkey_admin(admin_id);
