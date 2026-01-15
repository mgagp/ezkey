# Ezkey Admin API

This module provides the administrative REST API for Ezkey (default port: `9080`).

## What this module does

- Admin provisioning (global/tenant admins) and admin-only operations
- Passwordless admin authentication (“eat your own dog food”)
- Admin MFA bootstrap (system integration + global admin enrollment)
- Rate limiting, token cleanup/rotation, operational controls

## Run locally

From the repository root:

```bash
mvn spring-boot:run -pl ezkey-admin-api
```

Default config is in `ezkey-admin-api/config/application.properties`. Profile-specific defaults exist in:

- `ezkey-admin-api/config/application-docker.properties`
- `ezkey-admin-api/config/application-windows.properties`

## Required bootstrap configuration (SOC 2)

The initial global admin identity is enforced at startup by `InitialGlobalAdminService`.

Configure (profile-specific recommended):

```properties
ezkey.admin.initial.username=john.doe
ezkey.admin.initial.email=john.doe@example.com
ezkey.admin.initial.first-name=John
ezkey.admin.initial.last-name=Doe
```

See `README_INITIAL_GLOBAL_ADMIN.md` for the full bootstrap and troubleshooting guide.

## System tenant configuration

The “system tenant” represents the organization hosting this Ezkey instance.

- Properties: `ezkey.organization.name`, `ezkey.organization.description`
- Class: `org.ezkey.admin.config.OrganizationProperties`

Important: bootstrap currently looks up the system tenant by name. If you change
`ezkey.organization.name`, the database must contain a tenant with the same name (otherwise
bootstrap fails).

## Documentation index

- `README_INITIAL_GLOBAL_ADMIN.md`: initial global admin + passwordless bootstrap (SOC 2)
- `README_RATE_LIMITING.md`: rate limiting strategy and configuration
- `README_TOKEN_CLEANUP_ROTATION.md`: bearer token cleanup and rotation-on-login
- `VALIDATION_FIX_I18N.md`: validation/i18n notes
- `NATIVE_BUILD.md`: native build notes (if relevant for this module)

## Deprecated documents

These files are kept only as short stubs to avoid breaking older references:

- `ADMIN_ZERO_MIGRATION_ANALYSIS.md`
- `ADMIN_ZERO_OPTION_B_IMPLEMENTATION.md`

The current source of truth is:

- `ezkey-core/src/main/resources/db/migration/V3__create_system_tenant_and_admin_zero.sql`
- `org.ezkey.admin.service.InitialGlobalAdminService`
- `org.ezkey.admin.service.AdminBootstrapService`
