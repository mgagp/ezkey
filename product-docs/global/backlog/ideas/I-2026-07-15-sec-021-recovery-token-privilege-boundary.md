# Backlog Idea — I-2026-07-15 SEC-021 Recovery token privilege boundary

## Metadata

- **ID:** `I-2026-07-15-sec-021-recovery-token-privilege-boundary`
- **Status:** `done`
- **Priority:** `P0`
- **Created at:** `2026-07-15`
- **Updated at:** `2026-07-15`
- **Last reviewed at:** `2026-07-15`
- **Progression markers:** `P2-security-readiness`
- **Component tags:** `admin-api`, `core`, `security`, `docs`, `ezkey-tests`
- **Lane:** `B`
- **Captured by:** Marc (Security Challenge Report 2026-07 plan incubation)
- **GitHub issue:** `#357`
- **Issue labels:** `lane:b`, `type:security`, `component:admin-api`, `component:core`, `priority:p0`, `status:ready`
- **Tracer bullet:** `TB-2026-07-15-sec-021-recovery-token-privilege-boundary`

## Intent

Enforce the documented recovery-token contract: a recovery bearer must not authenticate as a full
administrator session. It may only drive the MFA enrollment-reset funnel.

## Problem and value

- **Problem:** Recovery tokens were persisted as ordinary `AdminToken` rows. The shared
  `AdminTokenAuthenticationFilter` granted `ROLE_ADMIN` / `ROLE_*_ADMIN` for any active token,
  so a recovery bearer could call ordinary Admin API routes until expiry (SEC-021).
- **Expected value:** Durable `token_purpose` (`SESSION` | `RECOVERY`), session-path rejection of
  `RECOVERY`, reset-path keep-working, deactivate-after-reset, and regression tests so this
  privilege boundary cannot silently reopen.

## Scope

- **In scope:**
  - Flyway `token_purpose` on `ezkey_admin_tokens`
  - Issuance: login → `SESSION`; recover → `RECOVERY`
  - `AdminTokenValidationService` rejects `RECOVERY` for ordinary session auth
  - Deactivate recovery token after successful enrollment reset
  - Unit + functional SEC-021 regression tests
  - Docs alignment (`ENDPOINT.md`, `ADMIN_UI_RECOVERY.md`)
- **Out of scope:**
  - SEC-017 (encryption-key Tenant Admin authz)
  - SEC-022 (cross-tenant API key revoke)
  - Admin UI recovery lifecycle SEC-026/027

## Key assumptions

- Enrollment reset remains on its dedicated `AdminRecoveryService` path (`permitAll` + manual
  recovery validation), not a narrow Spring role spread across controllers.
- Prefixed plaintext (`ezkey_recovery_*`) remains defense-in-depth, not the sole durable control.

## Risks and exceptions

- Existing active recovery tokens issued before migration backfill as `SESSION` only if they were
  not identified; greenfield/backfill defaults rows to `SESSION`. Recovery tokens already in DB from
  before the fix retain short TTL; new recoveries always set `RECOVERY`.

## Promotion notes

Implemented in the same session as Security Challenge July 2026 P0 remediation. Tracer bullet:
`TB-2026-07-15-sec-021-recovery-token-privilege-boundary`.

## Links

- Security report: [`docs/SECURITY_CHALLENGE_REPORT_2026-07.md`](../../../../docs/SECURITY_CHALLENGE_REPORT_2026-07.md) SEC-021
- GitHub issue: [#357](https://github.com/mgagp/ezkey/issues/357)
- Functional test: `ezkey-tests/.../RecoveryTokenPrivilegeBoundarySecurityTest.java`
