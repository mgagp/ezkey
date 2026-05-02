# Admin UI — Exception and Error Model

## Intent

This document defines how the Admin UI handles errors coming from the Admin API and from the UI itself. Its philosophy is **"fail readable, fail safe, translate once"**: every error is surfaced with a meaningful translated message when possible, never silently swallowed, and never left as a raw technical artifact to the operator.

## Error Philosophy

- External errors from the Admin API arrive as **RFC 9457 Problem Details** with stable `type`, `title`, `status`, `detail`, and optional `parameters`.
- The UI **branches on `type` and HTTP status**, not on free-text `detail`.
- Locale resolution goes through `getTranslatedApiError`; fallback is the server-provided `detail`, then a generic translated fallback.
- Session-expired errors are handled centrally, not by each page.

## Error Categories

| Category | Description | Typical UI response |
|----------|-------------|---------------------|
| **Validation** | Inputs fail business rules. | Inline alert near the form; preserve input. |
| **Authentication** | Session missing or expired. | Redirect to `/login` (for session-authenticated calls). |
| **Authorization** | Role or tenant scope insufficient. | Global alert with explanatory text. |
| **Conflict** | State prevents the action (409). | Alert with hint to refresh or alter inputs. |
| **Rate-limited** | Server returns 429. | Friendly alert with `Retry-After` hint. |
| **External** | Downstream dependency failed. | Generic translated fallback; encourage retry. |
| **Internal** | Unexpected client-side error. | Toast error and log for developers. |

## Error Contract

- **External contract.** RFC 9457 Problem Details on all 4xx/5xx from Admin API; uniform `type` URIs under `https://ezkey.io/problems/`.
- **Internal contract.** `ApiError` type produced by `api-client.ts`. Components consume it through:
  - `getTranslatedApiError(error, t, fallback)` — preferred path.
  - `getApiErrorMessage(error, fallback)` — raw English fallback.

## Decision Matrix

| Scenario | HTTP | Problem `type` | UI response |
|----------|------|----------------|-------------|
| Expired session on authenticated page | 401 | (any) | Clear session, redirect to `/login`, preserve intended location when possible. |
| Unauthenticated login call fails | 400 / 401 | `authentication.*` | Inline alert; allow retry. |
| Rate-limited login | 429 | (any) | Global alert with `Retry-After`. |
| Validation error on create/edit | 400 | `*.invalid-argument` and similar | Inline alert, keep form input, `detail` wins over generic title when non-empty. |
| Conflict on write | 409 | (any) | Inline alert; propose reload when applicable (for example optimistic lock). |
| Forbidden action | 403 | (any) | Global alert with translated text. |
| Downstream unavailable | 503 | `system.*` | Banner with retry guidance and `Retry-After` when present. |
| Network error | n/a | n/a | Toast "network error" and retry option. |

## Error Inventory (Seed)

### `auth-rejected`

- **Category.** Authentication.
- **Trigger.** Device rejects the pending login attempt.
- **Externalization.** HTTP 400 with `type = https://ezkey.io/problems/authentication/auth-rejected`.
- **UI response.** Inline alert on the login form with `errors.authentication.auth-rejected`.
- **Audit.** Server-side (`login_mfa_rejected`).
- **Related workflow.** [`functional-flows.md#ex-login-rejected`](functional-flows.md#ex-login-rejected).

### `auth-timeout`

- **Category.** Authentication.
- **Trigger.** Login attempt expires before device approves.
- **Externalization.** HTTP 408 with `type = https://ezkey.io/problems/authentication/auth-timeout`.
- **UI response.** Inline alert; operator may retry.
- **Audit.** Server-side.
- **Related workflow.** [`functional-flows.md#ex-login-timeout`](functional-flows.md#ex-login-timeout).

### `invalid-signature`

- **Category.** Authentication.
- **Trigger.** Cryptographic signature verification fails on the server.
- **Externalization.** HTTP 400 with `type = https://ezkey.io/problems/authentication/invalid-signature`.
- **UI response.** Inline alert; usually indicates a device or backend issue that the operator cannot self-recover.

### `duplicate-integration-name`

- **Category.** Validation.
- **Trigger.** Creating an integration with a name that already exists for the tenant.
- **Externalization.** HTTP 400 with an `admin.invalid-argument`-style problem type and `detail` describing the conflict.
- **UI response.** Inline alert inside the create dialog; keep input.

## Cross-Component Propagation

- Admin API ↔ Admin UI is the only backend boundary this component handles; cross-component propagation is limited to this surface.
- Problem extensions:
  - `parameters` — JSON scalars for localization interpolation. Used by `getTranslatedApiError`.
  - `path` — echo of the request path; used for logging context only.

## Central Handlers

- **`api-client.ts`** — injects the Bearer token (when applicable), detects 401 on session-authenticated calls, and redirects to `/login`. Parses RFC 9457 body for unauthenticated calls (for example login with `requireAuth: false`).
- **`api-error-i18n.ts`** — maps Problem `type` URIs to i18n keys. Allows curated keys (for example `authentication.*`) while letting server `detail` win for dynamic validation messages.
- **Toast provider** — displays transient errors that do not need inline context (network, unexpected client-side exceptions).

## Related Documents

- [`stack-and-architecture.md`](stack-and-architecture.md).
- [`api-and-boundary-mappings.md`](api-and-boundary-mappings.md).
- [`functional-flows.md`](functional-flows.md).
- Global decision: [ADR-0003](../../global/architecture-decisions.md#adr-0003-rfc9457-as-external-error-contract).
- Legacy inventory: [`../../../docs/admin-ui-admin-api-error-inventory.md`](../../../docs/admin-ui-admin-api-error-inventory.md) when present.
