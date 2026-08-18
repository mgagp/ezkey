# Ezkey Admin API — Agent Notes

This file is intended for coding agents working in `ezkey-admin-api/`.

## Non-negotiables

- All new/updated project content must be **in English**.
- Do not introduce `@Autowired`. Use constructor injection.
- Keep admin authentication **passwordless-only** (no reintroducing password schema).

## Enrollment QR payload

Enrollment and admin-onboarding QR codes are **JSON** from `QrCodePayloadService`
(`enrollmentId`, `enrollmentProofToken`, optional `authUrl`). `authUrl` comes from
`ezkey.qr.auth-base-url` (`EZKEY_QR_AUTH_BASE_URL`). Omit the field when unset — mobile falls back
to `EZKEY_API_BASE_URL`. Do **not** revert the generator to pipe-only. Do **not** add a URL
shortener. Canon: [`docs/ENDPOINT.md`](../docs/ENDPOINT.md) (search `ezkey.qr.auth-base-url`).

## Admin session tokens (opaque, not JWT)

Admin sessions are **opaque DB-backed** tokens (`ezkey_admin_tokens`, lookup by
`bearer_token_hash`). Revocation is immediate (`active = false`). Do **not** introduce JWT for
admin sessions: Ezkey is not an IdP, and revocation would still need server state. Stay on a
**single** session token; do **not** add an OAuth Access/Refresh pair. Idle-extend is sliding TTL
(`ezkey.admin.token.expiration-hours`, default 2h) on each validated request — not a second token.
Browser delivery is Mode A Bearer vs Mode B HttpOnly cookie —
[`docs/admin-ui-security.md`](../docs/admin-ui-security.md). Hash-only precedent: ADR-0007 in
[`product-docs/global/architecture-decisions.md`](../product-docs/global/architecture-decisions.md).

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
- **Consume vs reactivate:** first-time onboarding is unauthenticated
  `POST /api/v1/admin/auth/activate`. Operator reactivation of a deactivated admin is
  `POST /api/v1/admins/{id}/activate`. Do not collapse those paths.
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

## Admin MFA credentials vs enrollment GET

Admin MFA enrollments bind to the **system integration** (system tenant). `GET /api/v1/enrollments/{id}`
authorizes via **enrollment → integration → tenant**, so a Tenant Admin gets **403** on admin MFA
rows. Use `GET /api/v1/admins/{id}/onboarding` and `/onboarding/qrcode` (authorize **admin → tenant**).
`GET /enrollments/{id}` remains correct for enrollments on the Tenant Admin’s **own** integrations.
Canon: [`docs/ENDPOINT.md`](../docs/ENDPOINT.md) § When to Use Admin Onboarding API vs Enrollment API.

## Idempotent / bulk no-op

Routine lifecycle actions that match zero eligible records are **200** with a summary (`affectedCount`,
`skippedCount`, `noOp`), not an error. Skip success audit when nothing changed. High-sensitivity
ops (encryption keys / re-encryption) may still audit intent. UI must not claim work that `noOp`
says did not happen. Canon: `docs/ENDPOINT.md` § Bulk enrollment lifecycle.

## Tenant deactivation and update

`Tenant.active` is the runtime master switch (`TenantService.ensureTenantActive`). Do **not**
cascade inactive onto child integrations, enrollments, or API keys. The system tenant cannot be
deactivated **or updated**. `PUT /api/v1/tenants/{id}` is **partial** (null fields ignored) — do
**not** add PATCH or JSON Patch. Canon: [`docs/LIFECYCLE_GOVERNANCE.md`](../docs/LIFECYCLE_GOVERNANCE.md)
§3.1; [`docs/ENDPOINT.md`](../docs/ENDPOINT.md) tenant update/deactivate.

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
