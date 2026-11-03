---
name: mobile-ios-phase-7-auth-flow-parity-and-hardening
overview: Complete the iPhone implementation of Ezkey’s core authentication flow and harden it into a repeatable, trustworthy path after bind and verify are proven.
todos:
  - id: implement-real-pending-respond
    content: Complete the real iPhone implementation of pending and respond on top of the validated crypto and enrollment base.
    status: completed
  - id: align-with-android-behavior
    content: Check parity with the Android reference app for crypto use, challenge handling, and signed-result trust checks.
    status: completed
  - id: harden-tests-and-docs
    content: Add the smallest high-value test and documentation updates needed to support ongoing iPhone development.
    status: completed
  - id: define-post-phase-backlog
    content: Separate true completion items from later enhancements, polish, or release-focused work.
    status: completed
isProject: false
retrofitted_by: R-2026-0003
retrofitted_at: 2026-05-24
---

# Mobile iOS Phase 7 - Auth Flow Parity and Hardening

## Goal
Finish the first credible iPhone version of Ezkey’s core mobile behavior by extending the validated `bind` and `verify` foundation into `pending` and `respond`, then hardening the result.

## In scope
- Real `pending` and `respond` implementation.
- Challenge handling and trust-check parity with Android semantics.
- Focused tests and documentation updates that reduce regression risk.
- Final separation between core completion and post-core enhancements.

## Out of scope
- Nice-to-have UX extensions.
- Broad release engineering and store publication work.
- Deep long-tail platform optimization unless it blocks core correctness.

## Deliverables
- Real iPhone support for the full Ezkey core flow: `bind`, `verify`, `pending`, `respond`.
- A parity checklist against the Android reference behavior.
- Small, focused hardening updates to tests and docs.
- A short follow-up backlog for remaining improvements.

## Exit gate
Phase 7 is complete when the iPhone app can execute Ezkey’s core mobile flow end to end with trustworthy cryptographic behavior and a documented list of any non-core follow-up work.

## Main risks
- Declaring parity based on UI similarity rather than trust-boundary correctness.
- Carrying temporary development shortcuts into the hardened path.
- Adding broad low-value tests instead of a few discriminating checks.

## Outcome (2026-11-03, lightened)

- `pending` / `respond`: shared TypeScript, proven end to end on the iOS simulator (step 5).
- Parity checklist: [`ezkey_mobile/docs/IOS_ANDROID_PARITY.md`](../ezkey_mobile/docs/IOS_ANDROID_PARITY.md).
- Secrets decision (MOB-017 counterpart): ADR-MOB-0007, Keychain items plus a clear-all sweep.
- Exit gate met on the simulator only; device behavior (Secure Enclave, Face ID, camera) stays with
  phase 6, which needs a physical iPhone.

## Final note
At this point, future work should split clearly between:
- core correctness gaps that still block iPhone confidence, and
- later improvements such as polish, release process, and convenience features.
