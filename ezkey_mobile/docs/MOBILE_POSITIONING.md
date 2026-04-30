# Ezkey Mobile Positioning

## Purpose and Reading Scope

This document positions Ezkey Mobile as the reference end-user mobile participant in the Ezkey ecosystem.
It explains what the app is for, who it serves, what it is not, and how its scope relates to the shared Ezkey
backend-first product thesis.

It is intentionally short and product-facing. Detailed API semantics, cryptographic wording, and implementation
mapping remain in the root-level shared docs and the other mobile conceptual documents.

## What Ezkey Mobile Is

Ezkey Mobile is the reference end-user app for Ezkey's mobile side of the cryptographic MFA chain. It is not a thin
notification shell. It is the device participant that completes enrollment and later approves or denies authentication
requests through a protocol that remains backend-first.

Core characteristics:

- a cross-platform React Native mobile app for real enrollment and approval flows,
- a secure participant in the Ezkey cryptographic chain,
- a user-facing surface designed to stay simple and deliberate,
- a demonstration-quality product surface for prospective adopters,
- a reference implementation that helps coding agents and contributors understand how the Auth API is consumed in practice.

## What Ezkey Mobile Is Not

Ezkey Mobile is intentionally not:

- an admin console,
- a browser-first or passkey-style authentication product,
- a FIDO2 or WebAuthn compatibility layer,
- a feature-rich identity platform client,
- a background-polling push approval shell,
- a mobile app that claims stronger platform-hardware guarantees than the current implementation can prove,
- the canonical source of shared Ezkey protocol truth.

## Target Users and Operator Context

| Audience | Primary goal | Practical expectation | Notes |
| --- | --- | --- | --- |
| End user | Enroll a trusted device and approve or deny sign-in requests | Clear trust signals, little protocol jargon, predictable actions | Primary audience for the runtime UX. |
| Security / IT evaluator | Validate that the mobile flow is credible and aligned with the backend protocol | Evidence that the app follows the documented bind/verify/pending/respond chain | Secondary audience during pilots and internal rollout. |
| Demo operator / developer advocate | Showcase Ezkey's end-user experience in demos | Reliable, understandable flow that reflects the backend-first thesis | Important because the app also helps explain the platform. |
| Contributor or coding agent | Understand the intended mobile product boundary quickly | A simple conceptual map of what the app is trying to do and not do | This is a documentation audience rather than an end user. |

## Product Value in the Ezkey Ecosystem

| Stakeholder | Problem | Mobile value | Supporting Ezkey capability |
| --- | --- | --- | --- |
| End user | Needs a trusted device to approve sign-ins without reading backend internals | Simple enrollment and explicit approve/deny flow | Auth API plus local device crypto |
| Evaluator | Needs to see that Ezkey is more than backend endpoints | Concrete end-user surface that demonstrates the protocol chain | Documented bind/verify and pending/respond lifecycle |
| Operator | Needs a device participant that reflects backend trust boundaries | Mobile UI that does not hide or contradict the backend-first model | Shared crypto and endpoint contract |
| Project | Needs a coherent platform story, not only API surfaces | Mobile app completes the product narrative and demoability | Backend-first MFA with a mobile participant |

## v1 Scope Boundaries

| Included | Excluded | Notes |
| --- | --- | --- |
| QR-driven enrollment | Manual multi-step provisioning UX | Enrollment entry is intentionally narrow in v1. |
| Approve / deny authentication requests | Broad account management | Mobile stays focused on the auth participant role. |
| Local persistence of enrollments | Multi-instance environment switching | The app targets one effective Ezkey instance at a time per enrollment set. |
| User-initiated polling | Background polling and push notifications | Important trust and UX constraint. |
| English-only runtime copy | Full localization system | Forward-compatible later, not current scope. |
| Readable installation/integration context | Full admin metadata management | Display is pragmatic, not administrative. |

Current implementation note: the codebase now includes a `DangerZone` screen for destructive local actions, which is
broader than the original PRD's read-only v1 positioning for destructive management. That deviation should be treated
as an explicit product reality, not hidden.

## Relationship to Shared Ezkey Positioning Docs

| Document | Why it remains canonical | How this document uses it |
| --- | --- | --- |
| [../../docs/PROJECT_POSITIONING.md](../../docs/PROJECT_POSITIONING.md) | Shared Ezkey thesis, what the platform is, and what it is not | This document narrows that thesis to the mobile participant and end-user product surface. |
| [../../docs/CRYPTO.md](../../docs/CRYPTO.md) | Shared cryptographic wording and guarantees | This document avoids restating algorithmic guarantees beyond positioning-level language. |
| [../PRD.md](../PRD.md) | Mobile scope source and design decisions | This document distills the mobile vision and scope into a shorter positioning view. |
| [MOBILE_STACK_AND_ARCHITECTURE.md](MOBILE_STACK_AND_ARCHITECTURE.md) | Technical shape of the mobile app | This document stays at the product and conceptual boundary level. |
