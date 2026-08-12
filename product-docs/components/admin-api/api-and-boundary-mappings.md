# Admin API — API and Boundary Mappings

## Intent

This document describes how the Admin API maps between its external HTTP contract (DTOs, OpenAPI, RFC 9457 errors) and its internal domain (core services, entities, mappers). Exhaustive endpoint-by-endpoint semantics remain in [`../../../docs/ENDPOINT.md`](../../../docs/ENDPOINT.md); this document captures the **boundary discipline**.

Phase 1 seeds two representative mappings and declares the contract artifact policy. Additional mappings will be added using the [mapping matrix template](../../templates/mapping-matrix.template.md).

## Contract Artifacts Policy

- **OpenAPI specs under [`../../../specs/admin-api/`](../../../specs/admin-api/) are generated.** They are produced by `scripts/update-specs.sh` after a clean-start stack. Agents never hand-edit them.
- **Bruno collections under [`../../../bruno/`](../../../bruno/)** are updated in the same change set as any endpoint or DTO change, except for internal-only adjustments.
- **Problem types** follow a stable URI scheme: `https://ezkey.io/problems/<family>/<name>`. Clients branch on `type` and HTTP status.

## Mapping Index

| ID | Scope | Direction | Status |
|----|-------|-----------|--------|
| `M-api-admin-auth` | Admin API passwordless auth DTO ↔ domain | bidirectional | `implemented` |
| `M-api-integrations` | Admin API integrations DTO ↔ domain | bidirectional | `implemented` |

---

## `M-api-admin-auth` — Passwordless Authentication DTOs

### Intent

Map the Admin UI or CLI passwordless auth DTOs (login, passwordless-wait, recover, enrollments/reset, logout, me) onto the Admin API's core authentication services and audit chain.

### Boundary

- **Left side.** Admin API controllers and request/response DTOs.
- **Right side.** Core authentication services, auth attempt state, audit chain, token store.
- **Direction.** Bidirectional.

### Reference Artifacts

- Endpoint narrative: [`../../../docs/ENDPOINT.md#admin-authentication-passwordless-only`](../../../docs/ENDPOINT.md).
- OpenAPI: [`../../../specs/admin-api/openapi-spec.json`](../../../specs/admin-api/openapi-spec.json) (admin auth section).
- Audit taxonomy: `ADMIN_LOGIN` family (see `event_action` notes in endpoint reference).

### Field Mapping — Login

| DTO field | Domain concept | Transformation | Required | Notes |
|-----------|----------------|----------------|----------|-------|
| `username` | `Admin.username` lookup key | trim, lowercase comparison as configured | yes | — |
| `challengeRequested` | Auth attempt challenge policy | boolean | yes | Controls single-call vs two-call mode. |

Login response (non-error):

| Domain outcome | DTO field | Transformation | Notes |
|----------------|-----------|----------------|-------|
| Issued token | `token` | opaque string | Only in terminal success. |
| Pending attempt | `status = "pending"`, `authAttemptId`, `challengeCode`, `expiresAt` | identity | Two-call mode. |
| Admin metadata | `adminType`, `username` | enum name + string | — |
| Expiry | `expiresAt` | UTC ISO-8601 | Always `Z` suffix with microsecond precision. |

Audit note for pending mode: the two-call pending branch is recorded as `login_mfa_requested`
with `SUCCESS` (step accepted, no session yet). Legacy rows may still carry `login_pending` and
remain valid for historical queries. (Source: R-2026-07-25-audit-vs-auth-expiry)

### Field Mapping — Passwordless-Wait

| DTO field | Domain concept | Transformation | Notes |
|-----------|----------------|----------------|-------|
| `authAttemptId`, `challengeCode` | Correlation handle | identity | If persisted attempt has `authAttemptChallenge`, `challengeCode` is required and must match; otherwise `challengeCode` must be omitted. |

Outcome mapping:

| Domain outcome | HTTP status | Problem `type` or response | Audit |
|----------------|-------------|-----------------------------|-------|
| Device approved | 200 | Session token response | `login_mfa_session_issued`. |
| Device rejected | 400 | `authentication/auth-rejected` | `login_mfa_rejected`. |
| Attempt expired | 408 | `authentication/auth-timeout` | `login_mfa_expired`. |
| Invalid signature | 400 | `authentication/invalid-signature` | audited. |

### Constraints and Invariants

- Rate limit: 5 logins per minute per IP; IP block after 10 consecutive failures (30 min).
- The pending response never leaks whether the username exists if an administrator has no enrollment (fail-closed).
- Token storage is server-authoritative; clients see only opaque strings.

### Lifecycle Coupling

- Contract version follows the Admin API OpenAPI spec. Additive changes are allowed without a major bump; incompatible changes require a coordinated rollout.

### Related Documents

- [`functional-flows.md#w-api-admin-login-passwordless`](functional-flows.md#w-api-admin-login-passwordless).
- [`../admin-ui/api-and-boundary-mappings.md#m-admin-auth`](../admin-ui/api-and-boundary-mappings.md#m-admin-auth).
- [`exception-and-error-model.md`](exception-and-error-model.md).

---

## `M-api-integrations` — Integrations DTOs

### Intent

Map the integration management DTOs onto the domain: create, list (with filters), retire, delete, bulk enrollment lifecycle actions.

### Boundary

- **Left side.** Admin API controllers and DTOs.
- **Right side.** Integration domain, enrollment lifecycle services, audit chain.
- **Direction.** Bidirectional.

### Reference Artifacts

- Endpoint narrative: [`../../../docs/ENDPOINT.md`](../../../docs/ENDPOINT.md) (Search Integrations, Bulk enrollment lifecycle, Integration retirement and deletion).
- OpenAPI: under `/integrations` in the Admin API spec.

### Field Mapping — Create

| DTO field | Domain field | Transformation | Required |
|-----------|--------------|----------------|----------|
| `integrationName` | `Integration.integrationName` | trim, uniqueness check | yes |
| `integrationDescription` | `Integration.integrationDescription` | trim | no |
| `tenantId` | `Integration.tenantId` | identity | conditional (Global Admin) |

### Field Mapping — List Response

| Domain attribute | DTO field | Notes |
|------------------|-----------|-------|
| `id` | `id` | sortable |
| `integrationName` | `integrationName` | — |
| `lifecycleStatus` | `lifecycleStatus` | `ACTIVE`, `INACTIVE`, `RETIRED`; source of truth. |
| `active` (derived) | `active` | Temporary compatibility boolean; do not rely on it when `lifecycleStatus` is available. |
| `createdAt` | `createdAt` | sortable |

### Field Mapping — Retire / Delete

| DTO field | Domain effect | Notes |
|-----------|---------------|-------|
| `reason` (query param) | Stored in audit log | Required for retire and delete; min 10 chars. |

### Decision Table — Allowed Transitions

| Current state | Retire | Delete |
|---------------|--------|--------|
| `ACTIVE` | Yes (bulk-revokes revocable enrollments first, then flips to `RETIRED`) | No |
| `RETIRED` with zero enrollments | — | Yes |
| `RETIRED` with enrollments | — | 409 Conflict |
| System integration | No | No |

### Error Mapping

| Scenario | HTTP | Problem `type` |
|----------|------|----------------|
| Validation (duplicate name) | 400 | `admin/invalid-argument` or similar |
| Forbidden cross-tenant | 403 | `authorization/access-denied` |
| Conflict on delete | 409 | `integration/delete-has-enrollments` |
| System integration modification | 400 | `integration/system-not-allowed` |

### Constraints and Invariants

- Bulk enrollment lifecycle endpoints are successful no-ops when nothing matches.
- Retire action is irreversible; delete requires explicit preconditions.
- The API does not reveal cross-tenant existence through 404 vs 403 distinctions.

### Related Documents

- [`functional-flows.md#w-api-integration-create`](functional-flows.md#w-api-integration-create).
- [`../admin-ui/api-and-boundary-mappings.md#m-integrations`](../admin-ui/api-and-boundary-mappings.md#m-integrations).
- [`../../global/lifecycle-model.md`](../../global/lifecycle-model.md).
