# Backlog Idea - I-2026-07-17 admin enrollment reset missing Authorization header returns 500

## Metadata

- **ID:** I-2026-07-17-admin-enrollment-reset-missing-authorization-header-500
- **Status:** `done`
- **Priority:** `P1` (historical — closed; not remaining release work)
- **Created at:** 2026-07-17
- **Updated at:** 2026-08-24
- **Last reviewed at:** 2026-08-24
- **Closed at:** 2026-07-19
- **Progression markers:** P2-security-readiness
- **Component tags:** admin-api, security, testing, docs
- **Lane:** B
- **Captured by:** Marc + agent (Schemathesis LGbal6 replay)
- **GitHub issue:** none (delivered on PR only)
- **GitHub PR:** `#388` (merged 2026-07-19)
- **Issue labels:** lane:b, type:fix, component:admin-api, priority:p1, status:ready

## Intent

Eliminate server-side 500 responses on POST /api/v1/admin/enrollments/reset when Authorization
header is missing, and enforce explicit authentication-contract behavior (401 target) with
regression tests.

## Problem and value

- **Problem:** Missing Authorization header currently yields 500 on a security-sensitive endpoint,
  violating expected semantics and polluting runtime reliability/security signals.
- **Expected value:** Deterministic 401 behavior, cleaner pentest output, safer and clearer operator
  diagnostics, stronger contract consistency.

## Scope

- **In scope:**
  - Root cause analysis for missing-header execution path
  - Exception handling correction for missing request headers on this endpoint (and shared path if
    justified)
  - Regression tests (controller and/or integration level)
  - Contract and collection refresh when status-code contract changes
- **Out of scope:**
  - Full sweep of all current Schemathesis failures
  - Broad auth-contract standardization for every endpoint in one batch

## Key assumptions

- Reproduced in both CLI replay and authenticated browser context on 2026-07-17.
- Security config intentionally permits this route and delegates token semantics to controller logic.
- Current handler set suggests a missing dedicated mapping for request-header-missing errors.

## Promotion notes

Promoted directly from security-pentest curated pass 2026-07-17 pass-01 due to high signal and
reproducibility. A dedicated RCA should be performed before implementation decisions are finalized.

## Closeout (2026-07-19 / canon synced 2026-08-24)

- **Delivered:** missing `Authorization` on `POST /api/v1/admin/enrollments/reset` maps to RFC 9457
  **401** (`https://ezkey.io/problems/authentication/missing-authorization-header`). Other missing
  required headers stay **400**. Shared handler:
  `ValidationExceptionHandler.handleMissingRequestHeader`.
- **Evidence:** PR `#388` merged; `AdminEnrollmentControllerSecurityWebMvcTest`;
  `ValidationExceptionHandlerMissingRequestHeaderTest`; pentest pass-02 historical replay confirms
  LGbal6 is fixed.
- **Residual:** no remaining work on this idea. Broader Schemathesis 401-contract mismatches stay
  hygiene under `I-2026-07-12-security-pentest-curated-hygiene` (program already `done`).
- **Next:** none on this idea.

## Links

- Method log: [`../method-logs/ML-2026-07-17-admin-enrollment-reset-missing-authorization-500.md`](../method-logs/ML-2026-07-17-admin-enrollment-reset-missing-authorization-500.md)
- Campaign note: [`../../hygiene/security-pentest/2026-07-17-pass-01.md`](../../hygiene/security-pentest/2026-07-17-pass-01.md)
- Parent program idea: [`I-2026-07-12-security-pentest-curated-hygiene.md`](I-2026-07-12-security-pentest-curated-hygiene.md)
- GitHub PR: `#388`
