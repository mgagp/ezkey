# Admin API — Exception and Error Model

## Intent

This document defines the Admin API's error surface: how exceptions are categorized, how they translate into external responses, and how clients should branch on them. The Admin API is the primary external contract for operators; its error surface must be uniform, stable, and self-documenting.

## Philosophy

- **Fail closed.** When a guard is violated, reject. Do not "helpfully" approximate state.
- **Stable contract.** Clients branch on `type` and HTTP status, not on free-text `detail`.
- **Localize responsibly.** Parameterized dynamic text uses the RFC 9457 `parameters` extension; clients interpolate.
- **Audit security-relevant errors.** Authorization and authentication failures write audit rows even when the caller sees a generic error.

## External Contract

All 4xx and 5xx responses follow **RFC 9457 Problem Details** (`Content-Type: application/problem+json`):

| Field | Description |
|-------|-------------|
| `type` | Stable URI under `https://ezkey.io/problems/<family>/<name>`. |
| `title` | Short human-readable description. |
| `status` | HTTP status code (mirrors the response status). |
| `detail` | Operator-safe text; may include dynamic fragments. |
| `path` | Extension — request path echoed for context. |
| `parameters` | Extension — JSON object with scalar values for client-side localization (camelCase keys). |

Exceptions to RFC 9457:

- **Some endpoints still return legacy error payloads** (for example, specific 500 paths on rotate-key). These are scheduled for alignment with ADR-0003.
- **`POST /api/v1/auth-attempts/respond`** returns business-level `FAILED` in a 200 body for cryptographic failure modes that the protocol models explicitly.

## Internal Contract

- **Typed exceptions** in the domain and services layer: for example `IntegrationNotFoundException`, `AuthAttemptRequestFailedException`, `OptimisticLockException`.
- **Controller advice / exception handlers** translate internal exceptions into RFC 9457 responses.
- **Validation** uses Bean Validation (`@Valid`, custom validators). Failed validation produces 400 with structured problem responses.

## Error Categories

| Category | Description | Typical response |
|----------|-------------|------------------|
| **Validation** | Input violates business rules. | 400 + `admin/invalid-argument` or specific type. |
| **Authentication** | Credentials missing, expired, or rejected. | 400/401/408 + `authentication/*`. |
| **Authorization** | Role or scope insufficient. | 403 + `authorization/access-denied`. |
| **Not found** | Entity does not exist or is not visible to the caller. | 404. Existence is not leaked across tenants. |
| **Conflict** | State prevents the action. | 409 + topic-specific type. |
| **Rate-limited** | Client exceeded the rate limit. | 429 + `Retry-After`. |
| **Degraded** | Heartbeat or audit-chain supervision blocks operations. | 503 + `system/*`. |
| **Internal** | Unexpected failure. | 500 + generic problem; logged. |

## Decision Matrix (Seed)

| Scenario | HTTP | Problem `type` | Audit |
|----------|------|----------------|-------|
| Login rate limit exceeded | 429 | generic 429 text (legacy) | IP audit entry. |
| Device rejected login | 400 | `authentication/auth-rejected` | `login_mfa_rejected`. |
| Login attempt expired | 408 | `authentication/auth-timeout` | `login_mfa_expired`. |
| Invalid signature | 400 | `authentication/invalid-signature` | audited. |
| Integration name duplicate | 400 | `admin/invalid-argument` | as applicable. |
| System tenant update | 400 | `tenant/not-allowed` | optional. |
| Delete integration with enrollments | 409 | `integration/delete-has-enrollments` | optional. |
| Auth attempt already terminal | 409 | `auth-attempt/conflict` | optional. |
| Encryption key rotate with pending key | 409 | legacy Problem Details (being aligned with ADR-0003) | audited. |
| Audit-chain heartbeat degraded | 503 | `system/audit-chain-heartbeat-degraded` | audited. |

## Error Inventory (Seed Subset)

### `authentication/auth-rejected`

- **Category.** Authentication.
- **Trigger.** Device explicitly rejects a login attempt.
- **Externalization.** HTTP 400 Problem Details.
- **Operator response.** Operator retries; if repeated, check device binding.
- **Audit.** `login_mfa_rejected`.

### `authentication/auth-timeout`

- **Category.** Authentication.
- **Trigger.** Pending auth attempt expires before device approves.
- **Externalization.** HTTP 408 Problem Details.
- **Audit.** `login_mfa_expired`.

### `tenant/not-allowed`

- **Category.** Authorization / Validation.
- **Trigger.** Modifying or deactivating the system tenant, or acting on an inactive tenant.
- **Externalization.** HTTP 400 Problem Details.

### `integration/delete-has-enrollments`

- **Category.** Conflict.
- **Trigger.** Delete requested on a retired integration with remaining enrollments.
- **Externalization.** HTTP 409 Problem Details.
- **Operator response.** Handle enrollments (revoke, delete) first.

### `system/audit-chain-heartbeat-degraded`

- **Category.** Degraded.
- **Trigger.** Audit-chain supervision detects a problem that requires blocking new MFA work.
- **Externalization.** HTTP 503 Problem Details with `Retry-After: 60` (also applied at the Auth API and Integration API layers).

## Cross-Component Propagation

- Admin UI consumes problem types via `getTranslatedApiError`. See [`../admin-ui/exception-and-error-model.md`](../admin-ui/exception-and-error-model.md).
- Mobile consumes Auth API problem types. See [`../mobile/exception-and-error-model.md`](../mobile/exception-and-error-model.md).
- Integration API clients (server-side) should log `type` and HTTP status, not `detail`.

## Rules for Authors

- Do not log any of: enrollment proof tokens, challenge codes, plaintext recovery codes, recovery tokens, bearer tokens, signatures used as proof material.
- Every new endpoint defines, up front, which problem types it can emit and an entry in this document.
- Do not reuse an existing problem type for a semantically different scenario.
- Add a locale entry in the Admin UI (`src/locales/en/errors.json` and `src/locales/fr/errors.json`) in the same change set when the UI must render the new type.

## Related Documents

- Global decision: [ADR-0003](../../global/architecture-decisions.md#adr-0003-rfc9457-as-external-error-contract).
- [`functional-flows.md`](functional-flows.md).
- [`api-and-boundary-mappings.md`](api-and-boundary-mappings.md).
- Legacy: [`../../../docs/ADMIN_API_EXCEPTION_HANDLING_SYNTHESIS.md`](../../../docs/ADMIN_API_EXCEPTION_HANDLING_SYNTHESIS.md) when present.
