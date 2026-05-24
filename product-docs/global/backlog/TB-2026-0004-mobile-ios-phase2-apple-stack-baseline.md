# Tracer Bullet Brief — `TB-2026-0004` iOS rebuild — Apple stack baseline and clean scaffold

## Metadata

- **ID:** `TB-2026-0004`
- **Status:** `draft`
- **Related idea:** `I-2026-0027`
- **Lane:** `A`
- **GitHub issue:** `#152`
- **GitHub branch:** `feature/152-i-2026-0027-mobile-ios`
- **GitHub PR:** _(à créer)_
- **Created at:** `2026-05-24`
- **Updated at:** `2026-05-24`

## Objective

Establish the clean iOS starting point and the verified Apple toolchain baseline so that all subsequent iOS phases can proceed without stack-level uncertainty.

This tracer bullet covers what was originally Phase 1 + Phase 2 of the iOS plan. Phase 1 (audit and keep/rewrite decision) is resolved by ADR-MOB-0005: existing iOS-specific code is discarded. Phase 2 (Apple stack baseline) is the first real execution step.

## Boundaries in scope

- Delete or archive `ezkey_mobile/ios/` iOS-specific native content that predates the rebuild
- Confirm Xcode + CocoaPods version compatible with React Native 0.85.2
- Validate or adjust minimum iOS deployment target (default: 15.1)
- Regenerate clean iOS native scaffold via `npx react-native init` or equivalent RN CLI
- Verify a minimal iOS simulator build launches without stack-level errors
- Document the chosen baseline (Xcode version, min iOS target, CocoaPods version) in `ezkey_mobile/docs/`

## Out of scope

- Native crypto implementation
- Screen wiring and navigation
- QR path (camera or simulator injection)
- Any product feature beyond a blank shell that launches

## Critical flows

- **Nominal:** delete stale iOS code → scaffold clean iOS project → install pods → launch simulator → app shell appears
- **Replan trigger:** if RN 0.85.2 forces a minimum iOS target above 15.1, or if a required dependency (vision-camera, etc.) introduces toolchain incompatibility, document and flag before proceeding to Phase 3

## Evidence plan

- Simulator screenshot or run log showing the clean shell launching on iPhone simulator
- Updated `ezkey_mobile/docs/MOBILE_STACK_AND_ARCHITECTURE.md` — iOS baseline section
- New or updated `ezkey_mobile/docs/IOS_SETUP.md` — developer setup instructions (Xcode version, CocoaPods, simulator target)

## Quality gates

- **Analysis gate:** ADR-MOB-0005 accepted, R-2026-0003 integrated ✅
- **Design gate:** toolchain compatibility matrix confirmed during execution
- **Implementation gate:** clean simulator launch with no unresolved native dependency errors
