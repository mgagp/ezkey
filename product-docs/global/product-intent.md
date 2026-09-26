# Product Intent — Ezkey

## Purpose

This document defines the product intent, positioning, core requirements, and design principles for Ezkey. It is the global truth about what Ezkey is, why it exists, and what "working well" looks like. Detailed endpoint semantics, operational runbooks, and component-specific behavior belong in the [component packs](../components/README.md) and the legacy [`../../docs/`](../../docs/) hub.

## Product Position

Ezkey is an open-source cryptographic MFA platform created as a distinct alternative to browser-centric authentication models. It is not a FIDO2 implementation, not a WebAuthn layer, and not a simplified passkey variant. Ezkey follows its own protocol, its own trust boundaries, and its own product logic.

Ezkey exists for teams that want strong authentication with direct backend control, explicit cryptographic verification, and practical integration patterns. The product is intentionally backend-first. Its foundation is not browser mediation or ecosystem breadth, but trusted server-side state, cryptographic continuity, and a mobile application that participates as a secure device in that chain.

Ezkey may use secure on-device private-key storage when the platform provides it. That overlap with passkey-capable environments is an implementation building block, not a statement of protocol similarity.

## Problem Statement

Many authentication approaches become difficult to adopt because they combine real security value with ecosystem complexity, integration overhead, or operational opacity.

The problem Ezkey addresses is not "how to copy an existing browser-centric model with fewer steps." The problem is how to provide a strong, modern MFA path that is:

- practical for backend teams to reason about,
- straightforward to integrate through APIs,
- self-hosted and operationally visible,
- cryptographically rigorous from enrollment to authentication.

## Product Thesis

Ezkey is built on four core beliefs:

1. Strong authentication can be backend-first.
2. Security value should come from cryptographic continuity, not protocol sprawl.
3. Developers adopt systems they can understand, integrate, and operate.
4. A mobile-assisted MFA flow can be modern and strong without being defined by browser-centric standards.

## Target Audience

### Primary audience

- Backend developers integrating MFA into existing systems.
- Fullstack developers who prefer server-side control over authentication flows.
- Teams that want self-hosted security infrastructure with explicit trust boundaries.
- Operators and technical decision makers who value transparency and controllable deployment.
- **Global Admin** operators responsible for tenant management, platform operations, and sensitive platform actions such as cryptographic key operations.
- **Tenant Admin** operators responsible for integrations, enrollments, API keys, and day-to-day tenant administration.

For structural analogies (Global Admin ≈ IT/platform ops; Tenant Admin ≈ DevOps bridge) and the **adopter posture** (Ezkey is not the adopting organization's core business), see [`operator-alignment-guide.md`](operator-alignment-guide.md).

### Secondary audience

- Organizations that want a developer-friendly MFA option without depending on a closed vendor model.
- Teams looking for an API-first authentication component that remains easy to reason about.

## Core Product Requirements

### 1. Distinct protocol identity

Ezkey must be described and designed as its own cryptographic MFA approach.

- It must not depend on claims of passkey equivalence.
- It must not imply standards compatibility where none exists.
- It must preserve clear language around its own trust model and protocol boundaries.

### 2. Backend-first integration

Ezkey must be practical for backend integration.

- Core flows must be available through clear HTTP APIs.
- Authentication state must remain explicit and inspectable on the backend.
- Integrating applications must be able to create, observe, and manage authentication attempts without hidden browser dependencies.

### 3. Cryptographic continuity

Ezkey must preserve a coherent cryptographic chain across the full lifecycle.

- During enrollment, `bind` and `verify` must be cryptographically linked.
- During authentication, `pending` and `respond` must be cryptographically linked.
- Tokens and signatures must be scoped to their intended flow and resistant to replay and tampering.
- Backend and mobile roles must remain explicit in the trust model.

### 4. Practical developer experience

Ezkey must feel understandable and usable by developers.

- APIs should remain simple and well documented.
- Local startup should be straightforward.
- The product should be easy to demonstrate end to end.
- The system should favor clarity over unnecessary abstraction.

### 5. Self-hosted operational control

Ezkey must support teams that want direct operational ownership.

- Local and self-hosted deployment must remain first-class.
- Operational behavior should be inspectable through documentation, logs, and predictable APIs.
- Security-sensitive behavior should not depend on opaque third-party platform services.

## Core Concepts

### Integration

An integration is an application or backend system protected by Ezkey. It owns the business context of authentication requests and participates in the trust relationship with the mobile device through the Ezkey backend.

### Enrollment

An enrollment links a user context, an integration, and a mobile device. Enrollment is not just a registration record. It is a cryptographically established relationship that becomes the basis for later authentication.

### Authentication attempt

An authentication attempt is a single MFA decision flow tied to an enrolled device. It carries one-time proof material, state transitions, and the final user decision, with cryptographic validation throughout the flow.

### Administration

Ezkey includes administrative capabilities for operating the system, onboarding trusted administrators, and managing integrations, enrollments, and security-sensitive actions. These capabilities are exposed through the Admin UI and Admin API. The Admin UI is the main day-to-day human administration surface, while the backend remains authoritative for trust, verification, and control.

See the [glossary](../glossary.md) for additional protocol and role vocabulary.

## Security and Trust Model

Ezkey's security model is based on explicit backend trust and cryptographic verification across state transitions.

Key properties:

- One-time proof tokens limit replay.
- Signatures bind requests and responses to the correct flow.
- Enrollment and authentication are both modeled as cryptographic processes, not just API calls with business flags.
- Mobile secure storage can strengthen device key handling and local secret persistence when available, but those layers must be described honestly. In the current Android reference app, per-enrollment private keys live in `Android Keystore` with `StrongBox` requested when available, while long-lived local secrets such as `enrollmentProofToken` and `integrationPublicKey` are sealed at rest through a separate app-level `Android Keystore` AES path rather than being unsealed by the device signing key. See [`docs/CRYPTO.md`](../../docs/CRYPTO.md) and the [mobile component pack](../components/mobile/README.md).
- The backend remains the authoritative source of state and verification.

Component-level realization of this trust model is described in:

- [`components/admin-api/README.md`](../components/admin-api/README.md)
- [`components/mobile/README.md`](../components/mobile/README.md)

## Product Principles

See [`design-principles.md`](design-principles.md) for the full set of principles. Summary:

- **Simplicity** — solve the problem with as little accidental complexity as possible.
- **Pragmatism** — favor approaches developers can adopt, operate, and debug.
- **Explicit trust boundaries** — make clear which component is trusted for what, and why.
- **Developer-first design** — stay understandable from the integrator's perspective.
- **Open source transparency** — keep protocol, code, and operational behavior inspectable.

## Current Product Surface

At a high level, Ezkey currently includes:

- an `Admin UI`,
- an `Admin API`,
- an `Auth API`,
- an `Integration API`,
- a mobile application,
- local stack and test tooling,
- self-hosted deployment support.

**Community instance vs self-host:** A maintained **public alpha** community instance runs on
**ezkey.online** for evaluation (lab · discreet; alpha ≠ production; no SLA; no IdP parity).
Self-hosted and clean-start deployments remain first-class. Anonymous evaluator self-registration
(`ezkey.evaluator.self-registration.enabled`) is **OFF by default**; the community host may turn
it **ON explicitly**. Self-host operators do not inherit an open signup path unless they choose it.
Public runtime presets are named **`base`** and **`integrity`** — not an « eval profile ».

Detailed architecture and boundaries are described in [`architecture-overview.md`](architecture-overview.md). Component-specific behavior lives in the [component packs](../components/README.md).

## Non-Goals and Boundaries

Ezkey is not trying to be:

- a FIDO2 certification effort,
- a WebAuthn compatibility layer,
- a browser-first authentication product,
- a claim of standards equivalence by association,
- a SOC 2 Type I/II or other formal-attestation program,
- a feature-maximal identity platform.

Its goal is narrower and more opinionated: provide a coherent, strong, developer-oriented cryptographic MFA path with clear backend control.

## Success Criteria

Ezkey is succeeding when:

- developers can understand the trust model without needing a large ecosystem primer,
- integrations can be completed through well-documented APIs,
- enrollment and authentication remain cryptographically coherent end to end,
- the product remains self-hostable and operationally legible,
- the documentation reflects the same simplicity and pragmatism that the product claims to value.

## Related Documents

- [`roadmap.md`](roadmap.md) — major product steps and phase sequencing.
- [`features-and-phases.md`](features-and-phases.md) — feature catalog.
- [`architecture-overview.md`](architecture-overview.md) — architectural view and component boundaries.
- [`design-principles.md`](design-principles.md) — cross-product principles.
- [`normative-posture.md`](normative-posture.md) — operational discipline; not a certification program.
- [`lifecycle-model.md`](lifecycle-model.md) — global entity lifecycle rules.
- Repository entry points: [`../../README.md`](../../README.md), [`../../PRD.md`](../../PRD.md) (stub → this document), [`../../docs/PROJECT_POSITIONING.md`](../../docs/PROJECT_POSITIONING.md), [`../../docs/ENDPOINT.md`](../../docs/ENDPOINT.md) (section-scoped API reference), [`../../AGENTS.md`](../../AGENTS.md) (cold-start compass).
