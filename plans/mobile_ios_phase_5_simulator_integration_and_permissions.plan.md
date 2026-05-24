---
name: mobile-ios-phase-5-simulator-integration-and-permissions
overview: Integrate the iPhone app more deeply on simulator, replace selected mocks with real services, and burn down the uncertainties that can be resolved without a physical device.
todos:
  - id: connect-real-services-selectively
    content: Replace high-value mocked paths with real integration points where simulator validation is meaningful.
    status: pending
  - id: map-simulator-vs-device-boundaries
    content: Explicitly classify what can be validated on simulator and what must wait for a physical iPhone.
    status: pending
  - id: validate-permission-surfaces
    content: Validate permission wiring and user-flow behavior for camera and security-related prompts as far as simulator allows.
    status: pending
  - id: stabilize-simulator-flow
    content: Produce a stable simulator workflow for repeated development and regression checks.
    status: pending
isProject: false
retrofitted_by: R-2026-0003
retrofitted_at: 2026-05-24
---

# Mobile iOS Phase 5 - Simulator Integration and Permissions

## Goal
Get maximum value from simulator-based validation before spending time on physical-device execution.

## Principle
Use simulator aggressively, but honestly. Validate everything it can truly validate, and write down the exact places where only a real iPhone can answer the question.

## In scope
- Replacing selected mocks with real service calls.
- Enrollment and authentication flow wiring that is meaningful on simulator.
- Camera and security permission surfaces to the extent simulator can expose them.
- A clear simulator-ready developer loop for repeated validation.

## Out of scope
- Claiming final confidence in camera capture or Secure Enclave behavior.
- App Store readiness.
- Broader UX polish beyond what is needed for flow validation.

## Deliverables
- A simulator validation matrix: validated, partially validated, device-required.
- A stable simulator path for repeated enrollment/auth flow work.
- A short note on remaining blockers that justify moving to real iPhone.

## Exit gate
Phase 5 is complete when simulator no longer hides major unknowns except the ones that truly require physical hardware, and when the next real-device step is precise rather than exploratory.

## Main risks
- Spending too long chasing fidelity the simulator cannot provide.
- Keeping too many mocks past the point where real integration would be more informative.
- Confusing permission wiring success with actual hardware capability proof.
