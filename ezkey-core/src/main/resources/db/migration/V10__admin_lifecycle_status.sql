ALTER TABLE ezkey_admin
    ADD COLUMN lifecycle_status VARCHAR(32);

UPDATE ezkey_admin
SET lifecycle_status = CASE
    WHEN active = TRUE THEN 'ACTIVE'
    ELSE 'DEACTIVATED'
END
WHERE lifecycle_status IS NULL;

ALTER TABLE ezkey_admin
    ALTER COLUMN lifecycle_status SET NOT NULL;

ALTER TABLE ezkey_admin
    ADD CONSTRAINT chk_admin_lifecycle_status
    CHECK (lifecycle_status IN ('PENDING_ACTIVATION', 'ACTIVE', 'DEACTIVATED'));

COMMENT ON COLUMN ezkey_admin.lifecycle_status IS
'Explicit administrator lifecycle state. Distinguishes pending first activation from active and deactivated administrators.';