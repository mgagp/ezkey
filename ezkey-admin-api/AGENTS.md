# Ezkey Admin API — Agent Notes

This file is intended for coding agents working in `ezkey-admin-api/`.

## Non-negotiables

- All new/updated project content must be **in English**.
- Do not introduce `@Autowired`. Use constructor injection.
- Keep admin authentication **passwordless-only** (no reintroducing password schema).

## Where the “truth” lives

- **Migrations (Flyway)**: `ezkey-core/src/main/resources/db/migration/`
  - Initial global admin + system tenant are created in `V1__core_domain_and_multi_tenant.sql` (consolidated migrations).
- **Initial global admin identity (SOC 2)**: `org.ezkey.admin.service.InitialGlobalAdminService`
- **Admin MFA bootstrap** (system integration + enrollment + recovery codes):
  `org.ezkey.admin.service.AdminBootstrapService`
- **Organization/system tenant config**: `org.ezkey.admin.config.OrganizationProperties`

## Bootstrap invariants (high-signal)

- The migration creates a placeholder global admin (`username = 'admin'`).
- On startup, the placeholder is updated to the configured identity:
  - `ezkey.admin.initial.username`
  - `ezkey.admin.initial.email`
  - `ezkey.admin.initial.first-name`
  - `ezkey.admin.initial.last-name`
- `AdminBootstrapService` creates:
  - a system integration (`isSystemIntegration=true`)
  - a global admin enrollment (EC P-256 keys)
  - recovery codes (hashed in DB; plain text is only available at generation time)
- Recovery-code **regenerate** (`POST /api/v1/admins/{id}/recovery-codes/regenerate`) is a **full
  replace**: unused codes from the previous set are invalidated immediately. Do not add a top-up
  API. Plaintext codes appear only in the generation HTTP response.
- **Reissue activation code** (`POST /api/v1/admins/{id}/activation-code/regenerate`): Global Admin
  only; target must be `PENDING_ACTIVATION`, active, and have no first enrollment. Previous unused
  codes are invalidated. Do not use deactivation or enrollment reset for a lost unused activation
  code.
- Bootstrap log/export policy: `ezkey.admin.mfa.bootstrap.credentials-output-mode` — `full` (default; enrollment secrets + optional `bootstrap-credentials.json`) vs `recovery_primary` (recovery codes + instructions only; skips JSON export for Docker).
- **Bootstrap transaction:** `@Transactional` must be on `bootstrapAdminMfa()` (entry point), not only on `doBootstrapAdminMfa()`. Passing `this::doBootstrapAdminMfa` to `LockingTaskExecutor` bypasses the proxy; the inner method’s `@Transactional` would not apply. See `docs/plan/JPA_TRANSACTION_DESIGN_NOTES.md`.
- **ShedLock / HA:** Admin API enables ShedLock (`ShedLockConfiguration`). Scheduled jobs use
  `@SchedulerLock`; startup bootstrap shares lock name `ADMIN_STARTUP_BOOTSTRAP`. Table:
  `ezkey_shedlock` (Flyway V5). Strategy: [`docs/HA-JOB-COORDINATION.md`](../docs/HA-JOB-COORDINATION.md);
  local multi-instance exercise: [`docker/README-HA.md`](../docker/README-HA.md).

## Tenant-scoped list endpoints

Operator collection lists use **automatic tenant filtering from the principal** (same pattern as
integrations/enrollments): **one** `GET /api/v1/{resource}` — TenantAdmin sees own tenant only;
GlobalAdmin sees all (optional `tenantId` query where documented, e.g. `GET /api/v1/admins`). Do
**not** invent path-shaped alternatives like `/admins/tenant/{tenantId}` or `/me/peers` for listing.

## Idempotent / bulk no-op

Routine lifecycle actions that match zero eligible records are **200** with a summary (`affectedCount`,
`skippedCount`, `noOp`), not an error. Skip success audit when nothing changed. High-sensitivity
ops (encryption keys / re-encryption) may still audit intent. UI must not claim work that `noOp`
says did not happen. Canon: `docs/ENDPOINT.md` § Bulk enrollment lifecycle.

## List / search endpoints are paginated

Admin operator **list** and **search** endpoints return Spring Data `Page<T>` with `Pageable`
(`page`, `size`, `sort`) — not an unpaginated `List`. How-to and response shape:
[`docs/PAGINATION_GUIDELINES.md`](../docs/PAGINATION_GUIDELINES.md). Admin UI consumers use
`usePaginatedFromOrval` + `PaginatedTable` (see `ezkey-admin-ui/AGENTS.md`). Do not reintroduce
full-collection `List` responses for console list screens.

## Admin API CORS (browser split origins)

Prefix `ezkey.admin.cors.*` — see [`CONFIGURATION.md`](CONFIGURATION.md) §11. Empty
`allowed-origins` means CORS is **off** (same-origin Caddy / clean-start). Set explicit UI origins
for Cloudflare Pages → Admin API; pair `allow-credentials` with Mode B HttpOnly cookies
(`docs/admin-ui-security.md`). Do **not** add browser CORS to Auth API or Integration API.

## Logging and secrets

- **Never** log enrollment proof tokens, enrollment challenge codes, plaintext recovery codes, temporary recovery tokens, bearer tokens, or raw cryptographic signatures used as proof material. If operators need correlation, log **non-secret** identifiers only (e.g. `enrollmentId`, username). Enrollment reset and onboarding secrets belong in **HTTP responses** only.
- The **only** deliberate exception is optional one-time **bootstrap** output (`AdminBootstrapService`), controlled by `ezkey.admin.mfa.bootstrap.credentials-output-mode` (`full` vs `recovery_primary`). Do not copy that pattern into request/response handlers or enrollment services.

## Running and testing

- Run module:

```bash
mvn spring-boot:run -pl ezkey-admin-api
```

- Safe first validation after Java changes in this repo (from the repository root):

```bash
mvn spotless:apply
mvn checkstyle:check
mvn clean
mvn install -DskipTests
```

- Targeted follow-up tests after the baseline succeeds:

```bash
mvn test -pl 'ezkey-admin-api,!ezkey-tests'
```

## When changing endpoints / DTOs

- Keep REST semantics consistent with `docs/ENDPOINT.md`.
- Update any Bruno collection folders if they are impacted (`bruno/`).
- Prefer adding focused docs to an existing README rather than creating a new `.md`.
- **OpenAPI spec files under `specs/` are generated.** Do not edit `specs/**/openapi-spec.json`, dispatched copies (e.g. Admin UI), or other generated spec files manually. For contract-changing work, the default close-out is: Java + tests → clean-start → `scripts/update-specs.sh` → regenerate clients. See `.cursor/rules/openapi-specs.mdc`.
