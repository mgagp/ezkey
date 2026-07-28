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

The system tenant is identified by the `is_system_tenant` flag (`TenantRepository.findByIsSystemTenantTrue()`),
not by name. `ezkey.organization.name` only drives the display name synced onto that tenant at
startup (`AdminBootstrapService.syncSystemTenantFromOrganization()`); renaming it does not break
bootstrap. Rationale and history:
[`ADR-API-0005`](../product-docs/components/admin-api/design-decisions.md#adr-api-0005-system-tenant-identified-by-flag-not-by-name).

## Documentation index

- `README_INITIAL_GLOBAL_ADMIN.md`: initial global admin + passwordless bootstrap (SOC 2)
- `README_RATE_LIMITING.md`: rate limiting strategy and configuration

Token cleanup + rotation are documented in `docs/ADMIN_API_SECURITY_GUIDE.md` (Token Management).
