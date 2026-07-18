-- Ezkey DML grants — idempotent.
-- Canonical matrix: docs/DATABASE_ROLE_PERMISSIONS_MATRIX.md
-- Run as postgres (superuser) after Flyway completes as ezkey_migrate.
-- Invoked by scripts/db/apply-grants.sh and the docker db-grants service.

-- ---------------------------------------------------------------------------
-- Schema usage
-- ---------------------------------------------------------------------------
GRANT USAGE ON SCHEMA public TO ezkey_admin, ezkey_auth, ezkey_integration;

-- ---------------------------------------------------------------------------
-- Ensure partition helper is owned by migrate role (SECURITY DEFINER)
-- ---------------------------------------------------------------------------
DO $$
BEGIN
  IF EXISTS (
    SELECT 1 FROM pg_proc p
    JOIN pg_namespace n ON n.oid = p.pronamespace
    WHERE n.nspname = 'public' AND p.proname = 'create_monthly_partition'
  ) THEN
    ALTER FUNCTION create_monthly_partition(TEXT, TEXT, TIMESTAMPTZ, TIMESTAMPTZ)
      OWNER TO ezkey_migrate;
  END IF;
END
$$;

REVOKE EXECUTE ON FUNCTION create_monthly_partition(TEXT, TEXT, TIMESTAMPTZ, TIMESTAMPTZ) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION create_monthly_partition(TEXT, TEXT, TIMESTAMPTZ, TIMESTAMPTZ) TO ezkey_admin;

-- ---------------------------------------------------------------------------
-- Reset table privileges for app roles (idempotent re-apply)
-- ---------------------------------------------------------------------------
REVOKE ALL ON ALL TABLES IN SCHEMA public FROM ezkey_admin, ezkey_auth, ezkey_integration;
REVOKE ALL ON ALL SEQUENCES IN SCHEMA public FROM ezkey_admin, ezkey_auth, ezkey_integration;

GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public
  TO ezkey_admin, ezkey_auth, ezkey_integration;

-- ---------------------------------------------------------------------------
-- ezkey_admin — broadest runtime role + schedulers
-- ---------------------------------------------------------------------------
GRANT SELECT, INSERT, UPDATE ON TABLE
  ezkey_tenant,
  ezkey_admin,
  ezkey_encryption_key,
  ezkey_reencryption_batch,
  ezkey_keyset_blob,
  ezkey_audit_chain_incident,
  ezkey_audit_entry_integrity_conciliation,
  ezkey_alert
TO ezkey_admin;

GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE
  ezkey_admin_tokens,
  ezkey_integration,
  ezkey_enrollment,
  ezkey_api_key,
  ezkey_audit_chain_checkpoint,
  ezkey_shedlock
TO ezkey_admin;

GRANT SELECT, INSERT, UPDATE ON TABLE ezkey_auth_attempt TO ezkey_admin;

-- Audit log: INSERT/UPDATE for HMAC seal; DELETE for lifecycle purge only
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE ezkey_audit_log TO ezkey_admin;

GRANT SELECT, UPDATE ON TABLE ezkey_scheduled_job_last_run TO ezkey_admin;

-- ---------------------------------------------------------------------------
-- ezkey_auth — device-facing Auth API
-- ---------------------------------------------------------------------------
GRANT SELECT ON TABLE
  ezkey_tenant,
  ezkey_admin,
  ezkey_integration,
  ezkey_encryption_key,
  ezkey_audit_chain_checkpoint
TO ezkey_auth;

GRANT SELECT, UPDATE ON TABLE
  ezkey_enrollment,
  ezkey_auth_attempt
TO ezkey_auth;

-- INSERT + UPDATE: AuditLogService two-step HMAC seal (identity then UPDATE entry_hmac).
-- DELETE denied (immutability / no purge from Auth API).
GRANT SELECT, INSERT, UPDATE ON TABLE ezkey_audit_log TO ezkey_auth;

GRANT SELECT, INSERT, UPDATE ON TABLE
  ezkey_keyset_blob,
  ezkey_audit_chain_incident,
  ezkey_alert
TO ezkey_auth;

-- ---------------------------------------------------------------------------
-- ezkey_integration — M2M Integration API
-- ---------------------------------------------------------------------------
GRANT SELECT ON TABLE
  ezkey_tenant,
  ezkey_admin,
  ezkey_integration,
  ezkey_enrollment,
  ezkey_encryption_key,
  ezkey_audit_chain_checkpoint
TO ezkey_integration;

GRANT SELECT, INSERT, UPDATE ON TABLE ezkey_auth_attempt TO ezkey_integration;

GRANT SELECT, UPDATE ON TABLE ezkey_api_key TO ezkey_integration;

-- INSERT + UPDATE: AuditLogService two-step HMAC seal (see ezkey_auth note).
GRANT SELECT, INSERT, UPDATE ON TABLE ezkey_audit_log TO ezkey_integration;

GRANT SELECT, INSERT, UPDATE ON TABLE
  ezkey_keyset_blob,
  ezkey_audit_chain_incident,
  ezkey_alert
TO ezkey_integration;

-- ---------------------------------------------------------------------------
-- Flyway history — migrate role only (no app access)
-- ---------------------------------------------------------------------------
DO $$
BEGIN
  IF EXISTS (
    SELECT 1 FROM information_schema.tables
    WHERE table_schema = 'public' AND table_name = 'flyway_schema_history'
  ) THEN
    REVOKE ALL ON TABLE flyway_schema_history FROM ezkey_admin, ezkey_auth, ezkey_integration;
  END IF;
END
$$;

-- ---------------------------------------------------------------------------
-- Default privileges for future objects created by ezkey_migrate
-- (including partitions from create_monthly_partition SECURITY DEFINER)
-- ---------------------------------------------------------------------------
ALTER DEFAULT PRIVILEGES FOR ROLE ezkey_migrate IN SCHEMA public
  GRANT USAGE, SELECT ON SEQUENCES TO ezkey_admin, ezkey_auth, ezkey_integration;

-- Admin baseline on new tables (tighten via re-run of this script after schema changes)
ALTER DEFAULT PRIVILEGES FOR ROLE ezkey_migrate IN SCHEMA public
  GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO ezkey_admin;

ALTER DEFAULT PRIVILEGES FOR ROLE ezkey_migrate IN SCHEMA public
  GRANT SELECT, INSERT ON TABLES TO ezkey_auth, ezkey_integration;
