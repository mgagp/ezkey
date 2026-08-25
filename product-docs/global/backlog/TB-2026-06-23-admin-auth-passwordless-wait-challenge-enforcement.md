# Tracer Bullet Brief - `TB-2026-06-23-admin-auth-passwordless-wait-challenge-enforcement` Patch challenge bypass on passwordless-wait

## Metadata

- **ID:** `TB-2026-06-23-admin-auth-passwordless-wait-challenge-enforcement`
- **Status:** `done`
- **Related idea:** `I-2026-06-23-admin-auth-passwordless-wait-challenge-enforcement`
- **Lane:** `D`
- **Posture:** `single-pass`
- **GitHub issue:** `#251` (closed)
- **GitHub branch:** `feature/251-i-2026-06-23-admin-auth-passwordless-wait-challenge-enforcement`
- **GitHub PR:** `#255` (merged 2026-06-26)
- **Created at:** `2026-06-23`
- **Updated at:** `2026-08-24`
- **Closed at:** `2026-06-26`
- **Captured by:** Copilot

## Objective

Implement the smallest end-to-end correction that makes challenge-backed auth attempts reject
`/passwordless-wait` calls when challenge is missing or mismatched, while preserving valid
non-challenge flow behavior.

## Boundaries in scope

- `AdminAuthController` -> `AdminAuthService.waitForPasswordlessAuth`
- `AdminAuthService` -> `AuthAttemptRepository` / `AuthAttemptService`
- API contract docs and Postman collection for `/admin/auth/passwordless-wait`

## Out of scope

- Protocol redesign or new auth proof primitives
- Full replay-hardening redesign across all final attempt states
- UI journey redesign

## First executable slice

1. Read persisted auth attempt in wait flow.
2. Enforce conditional invariant:
   - if persisted `authAttemptChallenge` is not null, request `challengeCode` is required and must
     match;
   - if persisted `authAttemptChallenge` is null, allow null challenge (existing non-challenge
     behavior).
3. Return explicit auth failure on missing challenge for challenge-backed attempt.
4. Add/adjust targeted tests:
   - challenge-backed attempt + null challenge -> fail
   - challenge-backed attempt + wrong challenge -> fail
   - challenge-backed attempt + correct challenge -> success path unchanged
   - non-challenge attempt + null challenge -> success path unchanged
5. Align docs and examples:
   - boundary mapping note for conditional requirement
   - OpenAPI/Postman narrative alignment in same change set when contract semantics are touched.

## Rollback or fallback posture

- Rollback is straightforward (single conditional guard), but should only be used under emergency
  if regression is observed in non-challenge flow.
- If uncertainty remains about HTTP mapping, keep current exception family and adjust message only
  in this slice; defer status taxonomy changes.

## Critical flows

- Nominal challenge flow: `/login` returns challenge -> `/passwordless-wait` with matching challenge
  -> token issued on ACCEPTED.
- Critical exception flow: challenge-backed attempt + missing challenge -> immediate auth failure,
  no wait-based token issuance.

## Evidence plan

- Service-level tests for the new conditional invariant.
- Controller-level regression for error propagation and audit action consistency.
- Contract artifacts:
  - update `product-docs/components/admin-api/api-and-boundary-mappings.md`
  - update impacted Postman requests/examples for wait endpoint.

## Quality gates

- Analysis gate: bypass and race variant documented (S1/S2).
- Implementation gate: targeted tests pass in `ezkey-admin-api`.
- Contract gate: docs and Postman updated with the same semantics.
- Closeout gate: residual risk explicitly stated (S3 replay hardening candidate).

## Exit criteria

1. No path remains where a challenge-backed attempt can pass wait without a challenge value.
2. Non-challenge flow remains backward compatible.
3. Targeted tests capture the bypass regression and pass.
4. Contract references and Postman examples are aligned with enforced behavior.

## Closeout (2026-06-26 / canon synced 2026-08-24)

Exit criteria 1–4 met on merge of PR `#255` (issue `#251`). Residual S3 (replay after `ACCEPTED`)
remains an explicit non-goal of this tracer bullet.

## Links

- Idea: `I-2026-06-23-admin-auth-passwordless-wait-challenge-enforcement.md`
- GitHub PR: `#255`
- Service: `ezkey-admin-api/src/main/java/org/ezkey/admin/service/AdminAuthService.java`
- Controller: `ezkey-admin-api/src/main/java/org/ezkey/admin/controller/AdminAuthController.java`
- DTO: `ezkey-admin-api/src/main/java/org/ezkey/admin/dto/request/AdminPasswordlessWaitRequestDto.java`
