# System Tenant + Initial Global Admin — Documentation Consolidation Stub (Deprecated)

This document is **deprecated**.

It previously described “Option B” (global admin linked to a system tenant) with an older migration plan (V5) and an older bootstrap approach. The current implementation is already aligned with the “system tenant” concept, but the details live elsewhere.

## Source of truth (current)

- **Migration**: `ezkey-core/src/main/resources/db/migration/V3__create_system_tenant_and_admin_zero.sql`
  - Creates the **System Tenant** (`tenant_name = 'Ezkey System'`) and links the placeholder global admin to it.
  - Updates the admin hierarchy constraint to allow `GLOBAL_ADMIN` to have a tenant.
- **Configuration**: `org.ezkey.admin.config.OrganizationProperties` (`ezkey.organization.*`)
  - Used by `AdminBootstrapService` to locate the system tenant.
- **Bootstrap**: `org.ezkey.admin.service.InitialGlobalAdminService` + `org.ezkey.admin.service.AdminBootstrapService`
- **Documentation**: `README_INITIAL_GLOBAL_ADMIN.md`

## Important note about `ezkey.organization.name`

Today, `AdminBootstrapService` expects the system tenant to exist and looks it up by `OrganizationProperties.name`.

- If you change `ezkey.organization.name`, ensure the database contains a matching tenant row (e.g., rename the tenant or adjust your migration/seed strategy). Otherwise bootstrap will fail with “System tenant not found”.

## Why this file still exists

Some older documents reference this filename. Keeping a short stub avoids broken links while we consolidate documentation.

