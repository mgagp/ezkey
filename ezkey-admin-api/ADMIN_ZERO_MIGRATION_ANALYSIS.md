# Admin Zero (Historical Note) — Deprecated

This document is **deprecated**.

It describes a **previous implementation era** where the admin bootstrap used passwords, placeholder BCrypt hashes, and MFA flags (`password_hash`, `mfa_enabled`, `mfa_required`, etc.). That architecture was **removed** when Ezkey moved to **passwordless-only** admin authentication.

## Source of truth (current)

- **Migration**: `ezkey-core/src/main/resources/db/migration/V3__create_system_tenant_and_admin_zero.sql`
  - Removes password columns from `ezkey_admin`
  - Creates the **System Tenant** and a placeholder global admin (`username = 'admin'`)
  - Updates the admin hierarchy constraint to allow `GLOBAL_ADMIN` to have a tenant
- **Bootstrap services (Admin API)**:
  - `org.ezkey.admin.service.InitialGlobalAdminService` (SOC 2 compliant identity: username + email + first/last name)
  - `org.ezkey.admin.service.AdminBootstrapService` (System Integration + Global Admin Enrollment + recovery codes)
- **Documentation**: `README_INITIAL_GLOBAL_ADMIN.md`

## Why this file still exists

Some older documents reference `ADMIN_ZERO_*` files. Keeping a short stub avoids broken links while we consolidate documentation.
