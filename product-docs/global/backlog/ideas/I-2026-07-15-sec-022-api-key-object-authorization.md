# Backlog Idea — I-2026-07-15 SEC-022 API key object authorization

## Metadata

- **ID:** `I-2026-07-15-sec-022-api-key-object-authorization`
- **Status:** `done`
- **Priority:** `P0`
- **Created at:** `2026-07-15`
- **Updated at:** `2026-07-15`
- **Last reviewed at:** `2026-07-15`
- **Progression markers:** `P2-security-readiness`
- **Component tags:** `admin-api`, `security`, `docs`, `ezkey-tests`
- **Lane:** `B`
- **Captured by:** Marc (Security Challenge Report 2026-07 plan incubation)
- **GitHub issue:** `#362`
- **Issue labels:** `lane:b`, `type:security`, `component:admin-api`, `priority:p0`, `status:ready`
- **Tracer bullet:** `TB-2026-07-15-sec-022-api-key-object-authorization`

## Intent

Enforce object-level authorization on Admin API API-key get, per-integration list, and revoke so
Tenant Admins cannot read or revoke credentials belonging to other tenants (SEC-022 + SEC-023).

## Problem and value

- **Problem:** `DELETE /api/v1/api-keys/{keyId}` mutated by primary key with no
  `canAccessIntegration` check. `GET` by key id and `GET` by integration id similarly lacked
  tenant gates, while create/update/list-all already had them.
- **Expected value:** Same controller pattern as PATCH — load (or use path integration id) →
  `AccessControlService.canAccessIntegration` → 403 deny / proceed. Negative functional tests
  lock the boundary.

## Scope

- **In scope:**
  - Authorize revoke, get-by-id, list-by-integration in `ApiKeyController`
  - Unit + functional SEC-022/023 regression tests
  - Docs alignment (`ENDPOINT.md`)
  - SEC-023 closed in the same slice (report disposition)
- **Out of scope:**
  - SEC-017 encryption-key Tenant Admin authz
  - SEC-024+ recovery enumeration
  - Repository-level revoke predicate hardening (defense in depth later)

## Key assumptions

- Denied responses match update: unknown key → 404; inaccessible key/integration → 403.
- Global Admin continues to access all tenants.

## Links

- Security report: [`docs/SECURITY_CHALLENGE_REPORT_2026-07.md`](../../../../docs/SECURITY_CHALLENGE_REPORT_2026-07.md) SEC-022 / SEC-023
- GitHub issue: [#362](https://github.com/mgagp/ezkey/issues/362)
- Tracer bullet: `TB-2026-07-15-sec-022-api-key-object-authorization`
