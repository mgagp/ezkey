-- ============================================================================
-- Ezkey Migration V30: One administrator, one enrollment; no contact email
--                      on administrator enrollments (#1313)
-- ============================================================================
-- Description:
--   1. One administrator has one MFA enrollment, and an enrollment is never
--      shared between administrators. Sign-in finds the approving
--      administrator by enrollment, so the rule was load-bearing but not
--      enforced. A unique index on ezkey_admin.enrollment_id now enforces it.
--      Administrators pending activation (enrollment_id NULL) are not
--      affected: PostgreSQL unique indexes accept several NULLs.
--   2. Administrator enrollments sit on the system integration and carry no
--      contact email: the administrator's email (ezkey_admin.email) is the
--      single source. Any contact_email left on a system-integration
--      enrollment is cleared. The field had no function there (QR delivery
--      already refused administrator enrollments). Audit rows that hold an
--      old value are not touched.
--
-- Strict, no compatibility path: if two administrators share an enrollment,
--              the migration stops with an error and changes nothing. Decide
--              which administrator keeps the enrollment, fix the data, then
--              run the migration again. Duplicate check:
--                SELECT enrollment_id, COUNT(*) FROM ezkey_admin
--                WHERE enrollment_id IS NOT NULL
--                GROUP BY enrollment_id HAVING COUNT(*) > 1;
--
-- Idempotent effect: running the UPDATE again changes nothing.
--
-- Manual rollback (no Flyway undo): DROP INDEX uq_admin_enrollment_id;
--              delete version 30 from flyway_schema_history. Cleared contact
--              emails are not restored (they had no function).
--
-- Author: Ezkey contributors
-- Date: 2026-11-17
-- ============================================================================

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM ezkey_admin
        WHERE enrollment_id IS NOT NULL
        GROUP BY enrollment_id
        HAVING COUNT(*) > 1
    ) THEN
        RAISE EXCEPTION 'V30: two administrators share an enrollment. One administrator, one enrollment (#1313). Fix the data, then run the migration again.';
    END IF;
END $$;

UPDATE ezkey_enrollment e
SET contact_email = NULL
FROM ezkey_integration i
WHERE e.integration_id = i.integration_id
  AND i.is_system_integration = TRUE
  AND e.contact_email IS NOT NULL;

CREATE UNIQUE INDEX uq_admin_enrollment_id ON ezkey_admin (enrollment_id);

COMMENT ON INDEX uq_admin_enrollment_id IS
    'One administrator, one enrollment: an enrollment is never shared between administrators (#1313).';
