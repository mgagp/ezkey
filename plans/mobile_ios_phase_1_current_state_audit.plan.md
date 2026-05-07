---
name: mobile-ios-phase-1-current-state-audit
overview: Audit the current Ezkey mobile iOS surface, decide what to keep or rewrite, and lock the fresh-iOS-rebuild-with-shared-RN strategy.
todos:
  - id: inventory-ios-surface
    content: Inventory the current iOS native project, bridge files, QR path, build files, and relevant shared React Native surfaces.
    status: pending
  - id: compare-with-android-reference
    content: Compare current iOS capabilities with the Android reference implementation for crypto, QR, storage, and flow coverage.
    status: pending
  - id: classify-keep-rewrite-defer
    content: Produce a keep/rewrite/defer matrix for iOS-specific and shared mobile assets.
    status: pending
  - id: lock-restart-decision
    content: Confirm the target strategy as fresh iOS native rebuild with reuse of shared React Native code and contract-first assets.
    status: pending
isProject: false
---

# Mobile iOS Phase 1 - Current State Audit

## Goal
Establish the real starting point for iPhone work and remove ambiguity around what should be reused, rewritten, or deferred.

## Working assumptions
- Keep the shared React Native product surface where it already helps.
- Rebuild the iOS native foundation more aggressively where parity is misleading or incomplete.
- Treat Android as the current implementation of record for mobile crypto and security posture.

## In scope
- Current `ezkey_mobile/ios` project structure and buildability.
- Existing iOS bridge and plugin surfaces.
- Shared TypeScript, navigation, generated DTOs, and flow code that can remain cross-platform.
- Gap analysis against Android for crypto, secure storage, QR handling, and tests.

## Out of scope
- Implementing new iOS features.
- Choosing final real-device validation procedures in detail.
- Refactoring shared code beyond what the audit needs to classify.

## Deliverables
- A keep/rewrite/defer matrix covering iOS-native and shared assets.
- A short architecture note stating why Ezkey should not restart the whole mobile app from zero.
- A clear list of blocking gaps between current iOS code and the Android reference path.

## Exit gate
Phase 1 is complete when the repo contains a clear decision that the project will proceed with a fresh iOS native rebuild on top of shared React Native foundations, and when the main uncertainty areas are explicitly named.

## Main risks
- Overestimating the usefulness of existing iOS native code.
- Underestimating hidden coupling in shared TypeScript code that assumes Android behavior.
- Carrying forward documentation that overstates current iOS parity.

## Replan trigger
Replan immediately after this phase if the audit shows either:
- the existing iOS native project is too broken to reuse even as scaffolding, or
- shared React Native surfaces contain more platform coupling than expected.
