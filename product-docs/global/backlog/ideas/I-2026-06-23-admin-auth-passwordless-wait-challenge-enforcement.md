# Backlog Idea - `I-2026-06-23-admin-auth-passwordless-wait-challenge-enforcement` Enforce challenge presence on passwordless-wait

## Metadata

- **ID:** `I-2026-06-23-admin-auth-passwordless-wait-challenge-enforcement`
- **Status:** `done`
- **Priority:** `P0` (historical — closed; not remaining release work)
- **Created at:** `2026-06-23`
- **Updated at:** `2026-08-24`
- **Last reviewed at:** `2026-08-24`
- **Closed at:** `2026-06-26`
- **Progression markers:** `P0-foundations`, `P4-compliance-readiness`
- **Component tags:** `admin-api`, `security`, `authentication`, `admin-ui-contract`
- **Lane:** `D` (post-delivery correction - observed security gap in existing behavior)
- **Captured by:** Copilot
- **GitHub issue:** `#251` (closed)
- **GitHub PR:** `#255` (merged 2026-06-26)

## Intent

Restore the intended security guarantee of challenge mode by making challenge submission mandatory on
`/admin/auth/passwordless-wait` whenever the persisted auth attempt carries a challenge.

## Problem and value

- **Problem:** Current logic verifies challenge only when `challengeCode` is present in request.
  For challenge-backed attempts, omitting `challengeCode` bypasses the check and allows wait flow to
  continue with only `authAttemptId`.
- **Expected value:**
  - Close the bypass path and realign implementation with documented anti-enumeration intent.
  - Reduce session-hijack exposure under `authAttemptId` interception and timing race conditions.
  - Re-establish testable, explicit contract semantics for two-call login.

## Scope

- **In scope:**
  - Security rule in service: if stored attempt has challenge, request must provide matching challenge.
  - Error mapping and audit action for missing challenge in challenge-backed attempt.
  - Targeted service/controller tests and contract docs alignment.
  - Postman collection update for challenge-mode wait examples and negative case.
- **Out of scope:**
  - New authentication factors or protocol redesign.
  - Broad auth attempt architecture changes.
  - UI redesign beyond minimal error handling alignment.

## Key assumptions

- `authAttemptId` can be observed/intercepted in at least some adverse network or endpoint exposure
  scenarios (the exact threat vector is environment-dependent).
- Challenge mode is intended to be server-authoritative proof on wait call, not a client hint.
- Existing wait service semantics (final status replay behavior) remain as-is for this slice.

## Scenario Matrix (focused, actionability-first)

| ID | Scenario | Current outcome | Risk | Action in this slice |
|---|---|---|---|---|
| S1 | Challenge attempt, attacker sends wait with `authAttemptId` and no challenge | Check skipped, flow proceeds | Critical | Block: missing challenge must fail |
| S2 | Race: attacker wait (no challenge) vs legit wait with challenge | First successful completion may issue attacker session | Critical | Same guard as S1; add regression test |
| S3 | Replay wait after ACCEPTED (same attempt) | May still return final status and trigger token issuance path | High (depends on token policy) | Record as follow-up hardening candidate |
| S4 | Non-challenge attempt, wait without challenge | Works by design | Expected | Keep behavior unchanged |
| S5 | Challenge attempt, wrong challenge provided | Fails with invalid challenge | Expected | Preserve behavior |
| S6 | Superseded/expired attempt wait | Returns expired/timeout semantics | Expected | Preserve behavior |

## Complementary Risk Assessment

A short complementary analysis is worth doing and has already paid off: it surfaced not only the
primary bypass (S1), but also an operationally important race variant (S2), a potential replay
hardening candidate (S3), and test/contract drift. This justifies a bounded methodological slice
before code change, not a long exploratory campaign.

## Promotion notes

Promote immediately to a tracer bullet because direction is clear, first cut is bounded, and
validation is straightforward.

## Closeout (2026-06-26 / canon synced 2026-08-24)

- **Delivered:** challenge-backed `POST /api/v1/admin/auth/passwordless-wait` rejects missing or
  mismatched `challengeCode` before the wait loop (`AdminAuthService.waitForPasswordlessAuth`).
  Non-challenge attempts still accept a null challenge; a challenge on a non-challenge attempt is
  rejected.
- **Evidence:** PR `#255` merged; issue `#251` closed; `AdminAuthServiceWaitChallengeEnforcementTest`;
  Bruno `bruno/authentication-login-admin/passwordless-wait.bru`; SEC-001 marked **Fait** in
  [`docs/SECURITY_CHALLENGE_REPORT_2026-06.md`](../../../../docs/SECURITY_CHALLENGE_REPORT_2026-06.md).
- **Residual:** S3 (replay wait after `ACCEPTED`) stayed out of scope. Do not reopen this idea as
  remaining P0. Transport/wait evolution is a separate track
  (`I-2026-07-18-authentication-wait-evolution`).
- **Next:** none on this idea.

## Links

- Service: `ezkey-admin-api/src/main/java/org/ezkey/admin/service/AdminAuthService.java`
- DTO: `ezkey-admin-api/src/main/java/org/ezkey/admin/dto/request/AdminPasswordlessWaitRequestDto.java`
- Controller: `ezkey-admin-api/src/main/java/org/ezkey/admin/controller/AdminAuthController.java`
- Mapping doc: `product-docs/components/admin-api/api-and-boundary-mappings.md`
- Tracer bullet: `TB-2026-06-23-admin-auth-passwordless-wait-challenge-enforcement.md`
- GitHub issue: `#251`
- GitHub PR: `#255`
- GitHub branch: `feature/251-i-2026-06-23-admin-auth-passwordless-wait-challenge-enforcement`
