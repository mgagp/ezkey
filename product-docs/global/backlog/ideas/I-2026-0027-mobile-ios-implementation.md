# Backlog Idea — `I-2026-0027` iOS app implementation — fresh native rebuild

## Metadata

- **ID:** `I-2026-0027`
- **Status:** `ready`
- **Priority:** `P1`
- **Created at:** `2026-05-24`
- **Updated at:** `2026-05-24`
- **Last reviewed at:** `2026-05-24`
- **Phase tags:** `mobile, ios`
- **Component tags:** `mobile`
- **Lane:** `A`
- **GitHub issue:** `#152`
- **Captured by:** Marc

## Intent

Implement the Ezkey mobile application on iOS (iPhone), from a clean iOS native scaffold on top of the existing shared React Native foundation. The Android implementation is the reference. This initiative delivers a trustworthy, cryptographically equivalent iPhone path through a phased sequence culminating in a real-device bind-and-verify flow and full auth flow parity.

## Problem and value

- **Problem:** Ezkey mobile currently targets Android only. The existing iOS-specific native code is embryonic, predates major Android refactoring cycles, and is not viable as a starting point. iPhone users and contributors working on iPhone have no functional path.
- **Expected value:** A credible, tested iPhone version of Ezkey's core mobile flow (bind, verify, pending, respond) with trustworthy EC P-256 crypto, Keychain integration, and parity with the Android reference behavior.

## Scope

- **In scope:**
  - Delete existing `ezkey_mobile/ios/` native-specific code
  - Clean iOS native scaffold (React Native 0.85.2 baseline)
  - Toolchain baseline: Xcode, CocoaPods, iOS 15.1 minimum target (to confirm)
  - Contract-first simulator shell using shared RN screens and Auth API DTOs
  - iOS native crypto: EC P-256, Keychain, Secure Enclave strategy, conservative reporting
  - Simulator integration and permission validation
  - Real-device bind and verify (Phase 6)
  - Auth flow parity: pending + respond (Phase 7)
  - Development-only QR injection path on simulator (temporary, Phase 3 only)
- **Out of scope:**
  - App Store publication and release engineering
  - Push notifications (separate initiative)
  - Certificate pinning on iOS (tracked in R-2026-0001, deferred)
  - Broad UX polish beyond flow correctness
  - Android-side changes

## Key assumptions

- Shared React Native surface (TypeScript, navigation, screens, generated DTOs) is reusable as-is
- React Native 0.85.2 supports iOS 15.1 minimum target without major compatibility issues
- The Ezkey Auth API contract (DTOs, endpoint shapes) does not require mobile-side changes for iOS

## Risks and exceptions

- Xcode / CocoaPods / RN 0.85.2 compatibility may require minimum iOS target adjustment
- Secure Enclave behavior differs between simulator and real device — must be documented conservatively
- Temporary simulator-only code (QR injection) must not leak into production builds

## Promotion notes

This idea is `ready`. A tracer bullet (TB-2026-0004) covers the first execution slice (Phases 1+2). The full 7-phase plan is documented in `plans/mobile_ios_phase_*.plan.md` and summarized in the retrofit slice R-2026-0003.

## Links

- Retrofit slice: `product-docs/global/legacy-retrofit/R-2026-0003-mobile-ios-implementation-plans.md`
- Design decision: `product-docs/components/mobile/design-decisions.md#adr-mob-0005`
- Phase plans: `plans/mobile_ios_phase_1_current_state_audit.plan.md` through `..._phase_7_auth_flow_parity_and_hardening.plan.md`
- Tracer bullet: `product-docs/global/backlog/TB-2026-0004-mobile-ios-phase2-apple-stack-baseline.md`
- Related pinning work: `product-docs/global/legacy-retrofit/R-2026-0001-mobile-certificate-pinning-spki.md`
