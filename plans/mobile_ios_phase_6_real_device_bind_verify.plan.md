---
name: mobile-ios-phase-6-real-device-bind-verify
overview: Move early to a physical iPhone once the crypto base exists, and validate the first real device path for Secure Enclave-adjacent behavior, QR scanning, bind, and verify.
todos:
  - id: prepare-real-device-test-brief
    content: Define the exact real-device objectives, setup, and success criteria before test execution.
    status: pending
  - id: validate-camera-and-qr
    content: Validate live camera access and QR scanning on a physical iPhone.
    status: pending
  - id: validate-bind-verify-path
    content: Execute the first real bind and verify flow with the iPhone app against the Ezkey backend.
    status: pending
  - id: capture-gap-list
    content: Document any hardware-only gaps, failures, or trust-boundary clarifications discovered during real-device execution.
    status: pending
isProject: false
---

# Mobile iOS Phase 6 - Real Device Bind and Verify

## Goal
Use a real iPhone as soon as the native crypto base is credible, so the team does not postpone hardware truth until too late.

## Why this timing matters
This is the earliest phase where the cost of real-device validation is justified by the value of the answers it provides.

## In scope
- Physical iPhone setup and execution brief.
- Camera and QR validation on real hardware.
- Real `bind` and `verify` flow execution.
- Observed behavior relevant to Secure Enclave, storage tier, and enrollment correctness.

## Out of scope
- Full authentication parity.
- Release hardening.
- Broad multi-device QA.

## Deliverables
- A concise real-device test brief.
- First real iPhone execution result for `bind` and `verify`.
- A gap log separating fix-now issues from acceptable later-phase work.

## Exit gate
Phase 6 is complete when the team has either:
- a successful real iPhone `bind` and `verify` flow, or
- a precise, bounded blocker list with evidence and the next corrective action.

## Main risks
- Waiting too long to validate hardware-specific behavior.
- Treating a one-off successful manual run as sufficient confidence.
- Discovering late that simulator assumptions were too optimistic.

## Replan trigger
Replan immediately after this phase if the real-device findings materially change camera strategy, key-storage reporting, or enrollment flow assumptions.
