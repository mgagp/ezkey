---
name: mobile-ios-phase-4-native-crypto-parity
overview: Rebuild the iOS native crypto path to match Ezkey’s real mobile contract, with EC P-256, trustworthy public-key export, signing, storage-tier reporting, and solid unit tests.
todos:
  - id: replace-wrong-ios-crypto-baseline
    content: Replace the current misleading iOS crypto baseline with an Ezkey-aligned EC P-256 implementation strategy.
    status: pending
  - id: define-secure-enclave-strategy
    content: Define the target Secure Enclave and fallback behavior for simulator and unsupported devices.
    status: pending
  - id: implement-key-lifecycle
    content: Implement per-enrollment key generation, lookup, signing, public-key export, and deletion for iOS.
    status: pending
  - id: add-ios-crypto-tests
    content: Add focused native tests that prove the iOS crypto path matches Ezkey payload and key-format expectations.
    status: pending
isProject: false
---

# Mobile iOS Phase 4 - Native Crypto Parity

## Goal
Build the first trustworthy iOS native crypto layer for Ezkey, aligned with the Android reference model where appropriate and honest about iOS-specific differences.

## Why this phase is critical
This is the security foundation. Without it, iPhone work can demonstrate UI structure but not real Ezkey device trust.

## In scope
- Native iOS key generation and storage strategy.
- EC P-256 signing path for Ezkey payloads.
- Public key export in the format expected by the Auth API contract.
- Storage-tier reporting policy for simulator, fallback devices, and stronger hardware paths.
- Native unit tests and bridge-level verification.

## Out of scope
- Camera integration.
- Full end-to-end enrollment on real backend.
- Production polish around UX messaging.

## Deliverables
- An iOS crypto implementation note covering Secure Enclave, fallback behavior, and simulator limits.
- Working iOS native methods for key generation, signing, public key retrieval, and deletion.
- Focused native tests for round-trip key lifecycle and payload signing expectations.

## Exit gate
Phase 4 is complete when iOS has a credible EC P-256 crypto foundation with tests strong enough to justify moving from a mock-backed shell toward real flow integration.

## Main risks
- Implementing an iOS path that appears to work but does not match Ezkey’s payload or key expectations.
- Overclaiming Secure Enclave behavior on simulator or unsupported devices.
- Letting convenience shortcuts weaken later trust guarantees.

## Replan trigger
Replan after this phase if real iOS constraints force a meaningful change to the storage-tier model, key lifecycle assumptions, or simulator strategy.
