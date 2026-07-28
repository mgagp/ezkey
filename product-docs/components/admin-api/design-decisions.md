# Admin API — Design Decisions

## Purpose

This document records architecture and design decisions **scoped to the Admin API**. Global decisions live in [`../../global/architecture-decisions.md`](../../global/architecture-decisions.md). Each entry uses the [architecture decision template](../../templates/architecture-decision.template.md).

## Index

| ID | Title | Status | Date |
|----|-------|--------|------|
| [ADR-API-0001](#adr-api-0001-passwordless-only-admin-authentication) | Passwordless-only admin authentication | accepted | 2025-08-20 |
| [ADR-API-0002](#adr-api-0002-constructor-injection-no-autowired) | Constructor injection only; no `@Autowired` | accepted | 2025-07-01 |
| [ADR-API-0003](#adr-api-0003-transactional-boundary-on-service-entry-points) | `@Transactional` on service entry points, not helper methods | accepted | 2025-12-20 |
| [ADR-API-0004](#adr-api-0004-generated-openapi-never-hand-edited) | Generated OpenAPI artifacts are never hand-edited | accepted | 2025-10-05 |
| [ADR-API-0005](#adr-api-0005-system-tenant-identified-by-flag-not-by-name) | System tenant identified by `is_system_tenant` flag, not by name | accepted | 2026-03-27 |

## ADR-API-0001 — Passwordless-only admin authentication

### Metadata

- **ID:** ADR-API-0001.
- **Date:** 2025-08-20.
- **Status:** accepted.
- **Scope:** component:admin-api.

### Context

Earlier iterations considered supporting password-based fallback for administrators. This adds credential storage, breach exposure, password reset flows, and a second authentication path to maintain in parallel with the Ezkey cryptographic path. The product thesis argues for eating our own dogfood: administrators should authenticate exactly the way integrating applications expect their users to.

### Decision

The Admin API supports exactly two authentication paths for administrators:

1. **Passwordless login** using an Ezkey enrollment (the primary path).
2. **Recovery codes** for emergency access when the device is unavailable.

No password fields exist in the schema. No password APIs exist. Recovery codes are BCrypt-hashed at rest and are single-use.

### Alternatives Considered

- **Add password fallback for convenience.** Rejected — introduces a known-weak credential path and contradicts the product story.
- **Use OAuth-based login with an external provider.** Rejected — adds dependency on an external system and does not exercise Ezkey itself.

### Consequences

- **Positive.** Stronger security posture, consistent with the product thesis, simpler auth surface.
- **Negative.** Operators must have a bound enrollment; recovery is the only alternative path.

### Impact

- Admin API auth endpoints.
- Admin UI login flow.
- Bootstrap responsibilities in [`stack-and-architecture.md`](stack-and-architecture.md).

### Related Decisions

- Global: [ADR-0001](../../global/architecture-decisions.md#adr-0001-backend-first-cryptographic-protocol).

## ADR-API-0002 — Constructor injection only; no `@Autowired`

### Metadata

- **ID:** ADR-API-0002.
- **Date:** 2025-07-01.
- **Status:** accepted.
- **Scope:** component:admin-api.

### Context

Field and setter injection make wiring implicit, hurt test ergonomics, and encourage lazy design. Constructor injection makes dependencies explicit, supports immutability, and simplifies unit testing by allowing beans to be constructed without Spring.

### Decision

The Admin API forbids `@Autowired` in application code. Beans are defined with constructors receiving all dependencies. Test wiring uses constructor arguments directly or `@SpringBootTest` / `@MockBean` as appropriate.

### Alternatives Considered

- **Allow `@Autowired` for convenience.** Rejected — pressure to revert once wiring is implicit.
- **Mix strategies per team preference.** Rejected — inconsistency without benefit.

### Consequences

- **Positive.** Explicit dependencies, simple tests, immutable bean state.
- **Negative.** Slightly more boilerplate in constructors; mitigated by Lombok-free explicit code or by records where appropriate.

### Impact

- All services, repositories, and controllers in the module.

## ADR-API-0003 — `@Transactional` on service entry points, not helper methods

### Metadata

- **ID:** ADR-API-0003.
- **Date:** 2025-12-20.
- **Status:** accepted.
- **Scope:** component:admin-api.

### Context

A regression in the admin bootstrap path showed that a helper method (`doBootstrapAdminMfa`) passed as a method reference to `LockingTaskExecutor` bypassed the Spring proxy. `@Transactional` on that helper had no effect. The actual entry point (`bootstrapAdminMfa`) was not marked transactional, so the lock handler ran outside a transaction, and late writes failed silently.

### Decision

`@Transactional` is declared on the public service **entry point** that users of the service call. Helper methods used internally (and any method reference passed to an executor) are not considered the transactional boundary. Documentation for each service states which methods are transactional.

### Alternatives Considered

- **Mark every public method as transactional.** Rejected — encourages sloppy boundaries and hides the decision.
- **Use programmatic transactions (`TransactionTemplate`).** Acceptable for special cases but noisy as a default.

### Consequences

- **Positive.** Predictable transactions; clear boundary documentation.
- **Negative.** Authors must understand which method is the real entry point.

### Impact

- `AdminBootstrapService` and similar services that delegate through executors.
- Reference: [`../../../docs/plan/JPA_TRANSACTION_DESIGN_NOTES.md`](../../../docs/plan/JPA_TRANSACTION_DESIGN_NOTES.md) when present.

## ADR-API-0004 — Generated OpenAPI artifacts are never hand-edited

### Metadata

- **ID:** ADR-API-0004.
- **Date:** 2025-10-05.
- **Status:** accepted.
- **Scope:** component:admin-api.

### Context

Hand-edits to generated OpenAPI artifacts under `specs/` reliably create contract drift. The generator is the only correct source of those artifacts.

### Decision

- Java code and Springdoc annotations are the only direct inputs to the contract.
- Generated specs are produced by `scripts/update-specs.sh` after a clean-start stack run.
- Agents never hand-edit any file under [`../../../specs/admin-api/`](../../../specs/admin-api/).

### Alternatives Considered

- **Allow manual tweaks to fix small issues.** Rejected — drift is guaranteed.

### Consequences

- **Positive.** Reliable, reproducible contract artifacts.
- **Negative.** Requires a running stack to refresh the spec; acceptable.

### Impact

- All endpoints and DTOs.
- Orval-generated clients in the Admin UI.
- Reference: [`../../../.cursor/rules/openapi-specs.mdc`](../../../.cursor/rules/openapi-specs.mdc).

## ADR-API-0005 — System tenant identified by flag, not by name

### Metadata

- **ID:** ADR-API-0005.
- **Date:** 2026-03-27.
- **Status:** accepted.
- **Scope:** component:admin-api.

### Context

The initial schema (`V1__core_domain_and_multi_tenant.sql`) seeded the system tenant as an ordinary
row in `ezkey_tenant` and located it by matching `tenant_name = 'Ezkey System'` in both the seed
script and `AdminBootstrapService`. This tied a structural identity (the one tenant that hosts
global administrators) to a display string. If an operator changed `ezkey.organization.name`
before a flag-based lookup existed, a name-based bootstrap query would no longer find the row it
depends on — the V1 migration already carries a forward-looking comment anticipating this fix.

### Decision

Migration `V5__operations_tenant_integration_enrollment.sql` adds a boolean `is_system_tenant`
column to `ezkey_tenant`, backfills it once by name for the existing seed row, and enforces
exactly one system tenant with a partial unique index
(`idx_tenant_system_tenant_unique ... WHERE is_system_tenant = TRUE`). `AdminBootstrapService`
resolves the system tenant exclusively through `TenantRepository.findByIsSystemTenantTrue()`
(`createSystemIntegration()`, `syncSystemTenantFromOrganization()`). `ezkey.organization.name` is
now purely a **display** value synced onto the flagged tenant at startup; it no longer participates
in identity resolution.

### Alternatives Considered

- **Keep name-based lookup and document the rename risk.** Rejected — an operational footgun that
  requires operators to remember an undocumented coupling between a config property and bootstrap
  correctness.
- **Use a fixed, hardcoded tenant id (e.g. always id 1).** Rejected — fragile across environments
  where seed order or id sequences differ (e.g. restored databases, HA bootstrap races).

### Consequences

- **Positive.** Renaming the organization (`ezkey.organization.name`) is safe at any time; the
  partial unique index makes "more than one system tenant" a database-level impossibility rather
  than an application-level assumption.
- **Negative.** None identified; the flag and index are a strict improvement over the name-based
  lookup they replaced.

### Impact

- `AdminBootstrapService` (bootstrap and idempotent re-sync on every startup).
- `TenantRepository.findByIsSystemTenantTrue()`.
- Migration `V5__operations_tenant_integration_enrollment.sql`.

### Related Decisions

- None yet at global scope; this is a component-local structural fix, not a cross-cutting policy.
