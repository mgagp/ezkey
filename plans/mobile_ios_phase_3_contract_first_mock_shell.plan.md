---
name: mobile-ios-phase-3-contract-first-mock-shell
overview: Stand up a simulator-friendly iPhone shell that follows the real Ezkey contract and core flows while allowing development to progress before camera and native crypto are fully complete.
todos:
  - id: confirm-contract-surfaces
    content: Confirm the iPhone app will remain contract-first on the Auth API generated DTOs and shared flow semantics.
    status: pending
  - id: build-ios-shell
    content: Bring up the essential iPhone shell, navigation, and primary screens on simulator.
    status: pending
  - id: create-dev-qr-input-path
    content: Add a minimal development-only QR payload injection path for simulator progress when live camera scanning is unavailable.
    status: pending
  - id: mock-core-auth-flows
    content: Provide mocked bind, verify, pending, and respond flows that exercise the real screen sequence and data shapes.
    status: pending
isProject: false
---

# Mobile iOS Phase 3 - Contract-First Mock Shell

## Goal
Get a useful iPhone build running on simulator with the real Ezkey flow structure, real DTO shapes, and a thin development-only QR input path so product and integration work can advance quickly.

## Design stance
- Not a decorative mock shell.
- Not a second fake contract universe.
- A simulator-first development shell that stays aligned with Ezkey flow and API structure.

## In scope
- Shared screens and navigation required for enrollment and authentication.
- Generated Auth API DTO usage and local wrapper alignment.
- A development-only path to inject QR payload content without depending on true camera behavior.
- Mocked bind, verify, pending, and respond flows that preserve real control flow.

## Out of scope
- Production-grade camera implementation.
- Production-grade native crypto.
- Real device validation.

## Deliverables
- iPhone simulator app with core screens wired.
- Development path for QR payload input on simulator.
- Mock-backed progression through `bind`, `verify`, `pending`, and `respond`.
- Clear annotation of what is temporary and must be removed or replaced later.

## Exit gate
Phase 3 is complete when you can launch the iPhone app on simulator, enter the enrollment flow, move through the core screens, and validate that the contract-first app structure is ready for real crypto and service integration.

## Main risks
- Temporary simulator-only paths becoming accidental permanent product behavior.
- Mock responses drifting from the real Auth API contract.
- Shared UI assumptions hiding platform-specific layout or permission issues.

## Notes for later phases
- The QR injection path is a development accelerator, not a final user feature.
- Camera and real QR validation move to later phases once they are worth the cost.
