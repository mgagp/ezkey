# Backlog Idea - I-2026-07-17 admin enrollment reset missing Authorization header returns 500

## Metadata

- **ID:** I-2026-07-17-admin-enrollment-reset-missing-authorization-header-500
- **Status:** captured
- **Priority:** P1
- **Created at:** 2026-07-17
- **Updated at:** 2026-07-17
- **Last reviewed at:** 2026-07-17
- **Progression markers:** P2-security-readiness
- **Component tags:** admin-api, security, testing, docs
- **Lane:** B
- **Captured by:** Marc + agent (Schemathesis LGbal6 replay)
- **GitHub issue:** pending
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

## Links

- Method log: ../method-logs/ML-2026-07-17-admin-enrollment-reset-missing-authorization-500.md
- Campaign note: ../../hygiene/security-pentest/2026-07-17-pass-01.md
- Parent program idea: I-2026-07-12-security-pentest-curated-hygiene.md
