---
todos:
  - id: helper-module
    content: Add map-passwordless-wait-error (or equivalent) helper + wire login.tsx catch
    status: completed
  - id: unit-tests-helper
    content: Unit tests for helper (ApiError types vs connectionLost)
    status: completed
  - id: docs-endpoint
    content: Update docs/ENDPOINT.md POST /passwordless-wait failure responses (RFC 9457)
    status: completed
  - id: qa-manual
    content: Manual QA — deny vs offline
    status: completed
---

# Admin login: denied auth vs "Connexion perdue"

**Status:** Done — **fully implemented and tested.** Automated coverage: Vitest for `map-passwordless-wait-error`. Manual verification: deny on device shows the rejected-state UI (not “connection lost”); flow confirmed functional end-to-end.

**Original scope:** Accepted — dedicated error-mapping helper and `docs/ENDPOINT.md` alignment.

## Diagnosis

**Backend (already aligned with RFC 9457 for this case)**

- On device **deny**, [`AdminAuthService.waitForPasswordlessAuth`](ezkey-admin-api/src/main/java/org/ezkey/admin/service/AdminAuthService.java) throws [`AdminAuthenticationRejectedException`](ezkey-admin-api/src/main/java/org/ezkey/admin/exception/AdminAuthenticationRejectedException.java) (see `REJECTED` branch).
- [`AuthenticationExceptionHandler`](ezkey-admin-api/src/main/java/org/ezkey/exception/AuthenticationExceptionHandler.java) maps it to **HTTP 400** with Problem Detail: `type` `https://ezkey.io/problems/authentication/auth-rejected`, etc.
- Same handler covers **expired** (`auth-expired`), **timeout** (**408**, `auth-timeout`), **invalid signature**, etc.

**Frontend (root cause of the wrong copy)**

- [`passwordlessWait`](ezkey-admin-ui/src/generated/admin-api/admin-authentication/admin-authentication.ts) uses [`fetchApi`](ezkey-admin-ui/src/lib/api-client.ts); non-2xx throws [`ApiError`](ezkey-admin-ui/src/lib/api-client.ts) with `problemDetail`.
- In [`login.tsx`](ezkey-admin-ui/src/pages/login.tsx), the **`catch`** always sets `connectionLost` for non-abort errors, so HTTP 400 + RFC 9457 is mislabeled as a network failure.
- The success branch `data.status === 'rejected'` is effectively unused: the backend throws instead of returning 200 with that shape.

## Scope (accepted)

### 1. Extracted helper (required)

- Add a **small, testable module** (e.g. `ezkey-admin-ui/src/lib/map-passwordless-wait-error.ts` or similar) that:
  - Takes the caught `unknown` error plus i18n fallbacks / callbacks as needed.
  - Returns a discriminated result: e.g. `{ kind: 'rejected' } | { kind: 'expired' } | { kind: 'error'; message: string } | { kind: 'connectionLost' }`.
  - Implements mapping using **`ApiError` + `problemDetail.type`** (stable URI suffixes: `/auth-rejected`, `/auth-expired`, `/auth-timeout`) and **`status`** (e.g. 408).
  - Reserves **`connectionLost`** only for non-`ApiError` transport failures (`TypeError`, etc.).
- [`login.tsx`](ezkey-admin-ui/src/pages/login.tsx) calls this helper from the `passwordlessWait` `catch` and sets state from the result (reuse existing `rejected` / `expired` / `error` UI).

### 2. Unit tests (required)

- Test the helper with mocked `ApiError` instances: rejected → `rejected`; expired/timeout cases; generic API error → message via `getApiErrorMessage` semantics; `TypeError` → `connectionLost`.

### 3. Documentation (required)

- Update [`docs/ENDPOINT.md`](docs/ENDPOINT.md) in the **POST /passwordless-wait** section to document **failure responses** in line with the implementation:
  - **400** RFC 9457: device denied (`auth-rejected`), expired attempt (`auth-expired`), invalid signature, etc.
  - **401** if applicable (e.g. invalid challenge) — only what the API actually returns.
  - **408** wait timeout (`auth-timeout`).
- Keep wording concise; reference Problem Detail `type` URIs and typical `title`/`detail` so integrators and the admin UI stay aligned.

### 4. Backend / OpenAPI

- **No Java change** required for the deny case unless review finds a gap.
- Do **not** hand-edit `specs/**`; spec regeneration remains the maintainer workflow after code/annotation changes.

### 5. Manual verification

- Deny on device → **rejected** UI (not connection lost).
- Simulate offline / failed fetch → **connection lost** copy.

## Out of scope (unless issues appear in QA)

- **401** on `/passwordless-wait` + `fetchApi` redirect during login — revisit only if QA shows confusing behavior.
