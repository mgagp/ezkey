# Plan: Enrollment Challenge Policy Surfacing

> **Status : ROLLED BACK — 2026-04-30**

## Outcome

- This workstream was implemented experimentally, then rolled back before release.
- The rollback removed `authAttemptChallengeRequiredByPolicy` from the protocol-facing contract and client surfaces to restore the prior minimal protocol and eliminate non-essential policy disclosure to mobile clients.
- The plan is kept here only as historical traceability for the explored direction and the subsequent rollback decision.

## Plan: Enrollment Challenge Policy Surfacing

Implement a focused protocol change for active-development mode where breaking changes are acceptable because nothing is released publicly yet. The approved scope for this plan is: Auth API/backend contract updates, signed payload updates, docs/spec/Postman refresh, DemoDevice alignment/testing, and keeping the experimental Dart crypto/client support code aligned with the new signed contract. The React Native mobile application work is intentionally split into a separate follow-up plan after the contract/spec work is complete and validated with DemoDevice.

**Steps**
1. Lock the protocol semantics before any code change. Preserve current `AuthAttemptPendingResponseDto.authAttemptChallengeRequired` as the effective challenge requirement for the current auth attempt, meaning `policy OR ad hoc request`. Do not redefine it.
2. Add the new additive policy field on pending. Use `authAttemptChallengeRequiredByPolicy` on the Auth API pending response, populated directly from `Enrollment.authAttemptChallengeRequired`, so clients can distinguish `always required by enrollment policy` from `required for this attempt only`. This step depends on step 1.
3. Add the same field on bind. Include `authAttemptChallengeRequiredByPolicy` in the bind response as an initial signed enrollment-policy snapshot so the client learns the enrollment-level setting immediately after bind. Treat it as a snapshot, not a live guarantee. This step can run in parallel with step 2 at the DTO/spec level.
4. Extend the bind signature contract. Update the canonical bind payload and all first-party bind payload builders/verifiers so `authAttemptChallengeRequiredByPolicy` is cryptographically protected and cannot be altered in transit.
5. Extend the pending signature contract. Update the canonical pending payload and all first-party pending payload builders/verifiers so `authAttemptChallengeRequiredByPolicy` is signed alongside `authAttemptChallengeRequired`, `contextTitle`, and `contextMessage`. This step depends on step 2.
6. Update backend implementation and generated contract surfaces. This includes DTOs, service population, signature builders, the mobile developer guide, generated OpenAPI artifacts, and impacted Postman collections. This step depends on steps 2 through 5.
7. Update DemoDevice as a mandatory first-party compatibility client for this change. Align its generated models, canonical payload utilities, and verification flow with the new bind and pending payload formats, then use it as the first end-to-end validation surface. This step depends on step 6.
8. Update the experimental Dart crypto/client support subproject in the same workstream. Align its payload builders, live auth-session parsing, and tests with the new bind and pending fields so the experimental Dart surface stays protocol-correct. This step depends on step 6 and can run in parallel with step 7.
9. Close the current workstream after contract refresh, DemoDevice validation, and experimental Dart alignment. Once `update-specs` output is current, Postman collections are updated, DemoDevice passes the new bind/pending verification flow, and the experimental Dart support code is updated, this plan is complete.
10. Record a future protocol-evolution phase. Because breaking changes are acceptable right now, no backward-compatibility mechanism is required in this iteration. A later plan should define how Ezkey will evolve protocol versions once public releases exist.
11. Record a separate future mobile-app phase. The React Native mobile app should consume the new field, persist it locally, and surface it in the enrollment UI, but that implementation belongs to a distinct follow-up plan after the contract-first workstream is done.
12. Document the residual stale-data model explicitly. State in the protocol guide that the bind value is an initial signed snapshot and pending provides opportunistic refresh on non-empty requests; with the current API set there is still no out-of-band refresh for policy-only changes when no auth attempt exists.

**Relevant files**
- c:\github\ezkey-worktree3\docs\MOBILE_DEVELOPER_GUIDE.md — update protocol semantics, signed-field meaning, and the documented stale-snapshot model.
- c:\github\ezkey-worktree3\docs\AUTH_ATTEMPT_SIGNATURE_PAYLOAD.md — extend the signed pending payload to cover `authAttemptChallengeRequiredByPolicy`.
- c:\github\ezkey-worktree3\docs\ENROLLMENT_SIGNATURE_PAYLOAD.md — extend the signed bind payload to cover `authAttemptChallengeRequiredByPolicy`.
- c:\github\ezkey-worktree3\ezkey-auth-api\src\main\java\org\ezkey\authattempt\dto\AuthAttemptPendingResponseDto.java — add the new pending policy field.
- c:\github\ezkey-worktree3\ezkey-auth-api\src\main\java\org\ezkey\enrollment\dto\EnrollmentBindResponseDto.java — add the new bind policy field.
- c:\github\ezkey-worktree3\ezkey-core\src\main\java\org\ezkey\authattempt\service\AuthAttemptPendingService.java — populate the new pending field and sign the updated canonical payload.
- c:\github\ezkey-worktree3\ezkey-core\src\main\java\org\ezkey\authattempt\service\AuthAttemptService.java — existing `policy OR request` logic remains the semantic anchor.
- c:\github\ezkey-worktree3\ezkey-admin-api\src\main\java\org\ezkey\admin\dto\request\EnrollmentUpdateRequestDto.java — confirms the enrollment policy can change after bind.
- c:\github\ezkey-worktree3\ezkey-admin-ui\src\pages\enrollment-detail.tsx — confirms the policy is mutable in operator workflows.
- c:\github\ezkey-worktree3\specs\auth-api\openapi-spec.json — generated contract to refresh.
- c:\github\ezkey-worktree3\postman\collections — mandatory update for affected auth/mobile collections.
- c:\github\ezkey-worktree3\ezkey-demo-device\src\main\java\org\ezkey\demo\device\service\AuthAttemptPayloadUtil.java — update DemoDevice pending payload builder.
- c:\github\ezkey-worktree3\ezkey-demo-device\src\main\java\org\ezkey\demo\device\controller\EzkeyAppController.java — update DemoDevice verification flow for bind and pending.
- c:\github\ezkey-worktree3\ezkey_dart\lib\src\payload.dart — update experimental Dart canonical bind and pending payload builders.
- c:\github\ezkey-worktree3\ezkey_dart\lib\src\live\auth_api_models.dart — update experimental Dart bind/pending model parsing for the new field.
- c:\github\ezkey-worktree3\ezkey_dart\lib\src\live\auth_session.dart — update experimental Dart verification flow to consume the new signed payload shape.
- c:\github\ezkey-worktree3\ezkey_dart\test\payload_test.dart — update payload-order and canonical-string tests.
- c:\github\ezkey-worktree3\ezkey_dart\test\auth_session_test.dart — update live-session verification tests for bind and pending.

**Verification**
1. Semantics verification: cover `policy=false, request=false`; `policy=false, request=true`; `policy=true, request=false` and confirm the pair of fields is unambiguous.
2. Mutability verification: change the enrollment policy from Admin UI/Admin API after bind, then confirm a later non-empty pending response exposes the updated `authAttemptChallengeRequiredByPolicy` value.
3. Bind signature verification: verify backend and DemoDevice rebuild the same canonical bind payload with `authAttemptChallengeRequiredByPolicy` included and validate the integration signature end to end.
4. Pending signature verification: verify backend and DemoDevice rebuild the same canonical pending payload with both `authAttemptChallengeRequired` and `authAttemptChallengeRequiredByPolicy` included and validate the integration signature end to end.
5. Experimental Dart verification: update and run the experimental Dart payload/session tests so its canonical bind/pending helpers and verification flow match the updated protocol.
6. Contract refresh verification: run the contract refresh, confirm [specs/auth-api/openapi-spec.json](specs/auth-api/openapi-spec.json) is updated, and update impacted Postman collections in the same change set.
7. End-of-plan verification: treat the current plan as complete only when DemoDevice is updated, the refreshed bind/pending flow works against the updated backend, and the experimental Dart support code is aligned.
8. Documentation verification: confirm the protocol guide explicitly documents the bind snapshot behavior, opportunistic pending refresh, and the absence of guaranteed out-of-band refresh.

**Decisions**
- Included scope: Auth API/backend contract changes, signature-payload changes, protocol documentation, OpenAPI refresh, Postman updates, and DemoDevice alignment/testing.
- Included scope: breaking changes are acceptable in this iteration because the project is still in active development and has no released clients in the wild.
- Included scope: extending bind and pending signature payloads so the new field is cryptographically integrity-protected on both surfaces.
- Excluded scope: React Native mobile app implementation and UI persistence/display changes; that work belongs to a separate follow-up plan.
- Excluded scope: introducing a new refresh endpoint in this quick-win iteration.
- Excluded scope: changing the semantic meaning of existing `authAttemptChallengeRequired` on pending.
- Approved product decision: implement the new field on both bind and pending, accept temporary staleness, and end this plan after contract refresh plus DemoDevice validation.

**Further Considerations**
1. Future protocol evolution: once Ezkey approaches public releases, add a separate phase for protocol versioning and breaking-change governance.
2. Naming remains sound: `authAttemptChallengeRequiredByPolicy` is long but the clearest additive name because it avoids semantic collision with the existing effective per-attempt field.
3. Mobile app follow-up: the next plan should focus only on local persistence, UI wording, and user-facing display of the new policy snapshot/refresh behavior.
