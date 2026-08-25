# Method log - Admin enrollment reset missing Authorization returns 500

## Metadata

- **Date:** 2026-07-17
- **Program context:** Security pentest curated MVP follow-up
- **Trigger:** Schemathesis finding LGbal6 replay
- **Scope:** deterministic reproduction and RCA preparation
- **Status:** closed (remediation merged 2026-07-19; canon synced 2026-08-24)

## Symptom

For POST /api/v1/admin/enrollments/reset, when Authorization header is missing, server returns:

- HTTP 500 Internal Server Error
- RFC 9457 payload with type https://ezkey.io/problems/admin/internal-error

Expected by security contract and test intent: 401 Unauthorized.

## Reproduction summary

### A. Direct HTTP replay (proxy path)

- Endpoint: http://localhost:19080/api/v1/admin/enrollments/reset
- Headers: Content-Type: application/json
- Body: {"enrollmentId":0,"reason":"0000000000"}
- Result: 500 (reproduced)

### B. Browser session cross-check (admin logged in)

- Admin UI login performed with admin.docker via Demo Device approval flow
- Same endpoint called from browser page context without Authorization header
- Result: 500 (reproduced)

This confirms the issue is not tied only to anonymous mode.

## Code observations

- Controller requires Authorization request header:
  - AdminEnrollmentController.resetEnrollment(@RequestHeader("Authorization") ...)
- Security config explicitly permits /api/v1/admin/enrollments/**
- Validation exception handler has MissingServletRequestParameterException mapping but no explicit
  MissingRequestHeaderException mapping
- Global exception handler contains broad RuntimeException/Exception fallbacks mapped to 500

## Working RCA hypothesis

Likely path: missing Authorization triggers MissingRequestHeaderException which is not mapped by a
specific 4xx handler and falls through to generic global fallback, producing 500.

## Why this log matters

- First deterministic reproduction captured from both CLI and browser execution contexts
- Provides concrete anchor for upcoming RCA and remediation design
- Converts ephemeral pentest output into versioned canonical project memory

## Proposed next step slice

1. Confirm exact thrown exception class with request-scope logs or focused test.
2. Decide contract posture for this endpoint when header missing (401 target).
3. Add explicit exception mapping and endpoint regression tests.
4. Refresh generated contracts and Postman collections if status contract changes.

## Closeout

PR `#388` (2026-07-19) implemented the mapping and regression tests. Pentest pass-02 (2026-08-12)
replayed the unauthenticated reset and observed **401**. Idea
`I-2026-07-17-admin-enrollment-reset-missing-authorization-header-500` is `done`.

## Links

- Campaign note: ../../hygiene/security-pentest/2026-07-17-pass-01.md
- Promoted idea: ../ideas/I-2026-07-17-admin-enrollment-reset-missing-authorization-header-500.md
- Local raw reproduction note (gitignored): ../../../../logs/security-pentest/analysis/2026-07-17-lgbal6-repro-notes.md
