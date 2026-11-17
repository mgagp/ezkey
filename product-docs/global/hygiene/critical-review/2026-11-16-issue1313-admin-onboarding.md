# Critical review — 2026-11-16 — `issue1313-admin-onboarding`

## Metadata

- **Date:** 2026-11-16 (decisions closed 2026-11-17)
- **Keyword:** `critical-review`
- **Target:** GitHub issue [#1313](https://github.com/mgagp/ezkey/issues/1313) (description and
  plan), code read on `main` at `9e8f6590d`, re-checked on `392392ec4`
- **Extras:** #1365 / #1368 (evaluator retirement, delivery order), #1266, #1180, #1206
- **Model / effort:** Claude, high effort. **Not the canonical setup:** the review ran in an
  interactive session with Marc, not in a fresh cloud agent of a forced different model family. The
  critical-review gate is mandatory only for P0/P1 visions and G1 briefs; #1313 is p2.
- **Angle:** general, with attention to the security posture of admin bind material, QR delivery
  and email
- **Launcher:** Marc
- **Reviewer agent:** interactive session (no cloud run id)
- **Verdict:** GO with amendments
- **Amend rate:** 9 / 9
- **Status:** Closed

## Decisions

| # | Finding | Severity | Decision | Notes |
| --- | --- | --- | --- | --- |
| 1 | Taken literally, "every `/enrollments` endpoint answers 409 for an admin enrollment" stops the enrollment record from opening (contradicts the back-link criterion), and a list cannot answer 409 | S1 | amend | GET and search return the record with token and challenge null; only `/qrcode` answers `409 admin-enrollment` |
| 2 | Admin bind material was read with no audit, while admin sign-in is passwordless | S1 | amend | Reveal on demand, one audit row per response that carries material (`ADMIN_BIND_MATERIAL_REVEALED`); fail-open, no alert. Item 1 is the single door that makes it enforceable |
| 3 | Blocking a third party from the QR after an activation code is used leaves a dead end (code consumed, no reissue once the enrollment exists) | S2 | amend | Same rule in both modes; the reveal stays possible, under the audit |
| 4 | "The admin QR never goes out by email" made a claim about what operators do | S3 | amend | "Ezkey never sends an administrator's QR or activation code. Passing them on is the operator's act." |
| 5 | The 1:1 admin ↔ enrollment rule was relied on by sign-in but not enforced | S2 | amend | Unique index (V30, strict, migration fails on a duplicate); `EzkeyAdmin.enrollment` becomes `@OneToOne` |
| 6 | Backend PR alone leaves a silent dead "Show QR" button on the admin enrollment record | S3 | amend | PR 1 carries the UI guard; each PR leaves `main` coherent, no "same release" coupling |
| 7 | "Create" listed as a `contactEmail` write path collides with the existing 403 `system-integration-create-not-allowed` | S3 | amend | Removed; the existing refusal stays, covered by a non-regression test |
| 8 | Server-computed `enrollmentViewable` copied the role rule; the cited precedent did not exist | S3 | amend | No field; the Admin UI uses the session role; the id is hidden in the UI only |
| 9 | Activation-code lifetime not written anywhere, while the code is the recommended path for someone not present | S3 | amend | 7 days, constant kept, documented in `ENDPOINT.md` and `SECURITY_POSTURE.md` |

## Lessons (1–3)

- A refusal rule written as "every endpoint answers X" must be checked against list endpoints and
  against the navigation the same plan adds; a list cannot refuse, and a record that cannot open
  breaks its own back-link.
- When a plan splits work into several PRs, check what breaks between merges and move the minimal
  consumer change into the PR that causes it. `main` is what gets deployed; a "same release" rule
  depends on memory.
- An invariant that existing code relies on (here, one admin per enrollment at sign-in) is worth
  enforcing in the schema when the plan touches the same table anyway; the cost is one index.

## Follow-ups

- Amendments landed in the #1313 description (edited in place, with dated trace comments).
- Implementation: #1313 PR 1 (backend, V30, UI guard, docs, this note), then PR 2 (Admin UI).
