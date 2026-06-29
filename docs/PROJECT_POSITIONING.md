# Ezkey Project Positioning

## What Ezkey Is

Ezkey is an open-source cryptographic MFA platform built as a distinct alternative to browser-centric authentication models.

It is not a FIDO2 implementation, not a WebAuthn layer, and not a simplified passkey variant. Ezkey follows its own protocol, its own trust boundaries, and its own product logic.

The project should be understood as a parallel path: a backend-first approach to strong authentication for teams that want direct control over the cryptographic and operational model.

## Why Ezkey Exists

Ezkey exists because strong authentication does not have to be defined primarily by browser UX, platform mediation, or protocol breadth.

It can also be designed from the backend outward, with:

- trusted APIs,
- durable server-side state,
- explicit cryptographic verification,
- a mobile application acting as a secure participant in the chain.

The goal is not to recreate an established ecosystem with fewer steps. The goal is to offer a coherent alternative for teams that want strong authentication with clearer backend control and a more direct integration model.

## Built By a Developer for Developers

Ezkey is shaped by a backend developer perspective.

That means the project gives priority to things developers have to live with every day:

- understandable flows,
- inspectable state transitions,
- practical APIs,
- explicit trust boundaries,
- operational visibility,
- security that can be reasoned about without needing an entire ecosystem story.

This is not anti-UI. The mobile application matters, and the user experience matters. But the project starts from the conviction that the visible interface should reflect the integrity of the backend, not compensate for weak foundations.

That includes the Admin UI. It is a real operational surface for Ezkey administrators, not an afterthought layered on top of the APIs.

The audience is split in two practical ways: Global Admins handle tenants, platform operations, and sensitive actions such as cryptographic key operations; Tenant Admins handle integrations, enrollments, API keys, and day-to-day tenant administration.

## Backend-First Security Thesis

Ezkey is based on a simple thesis: the real source of trust in a strong authentication system lives in backend guarantees.

Those guarantees include:

- cryptographic verification,
- server-side ownership of state,
- clear lifecycle transitions,
- one-time proof material,
- durable storage of security-relevant events,
- APIs that expose the system in a predictable way.

The UI is important as an integration surface. It is not the root of trust.

In Ezkey, the backend is not a passive receiver of user interaction. It is an active verifier in a cryptographic chain that spans the full flow.

## The Ezkey Cryptographic Chain

Ezkey's differentiator is not cosmetic simplicity. Its differentiator is a coherent cryptographic chain between the trusted backend and the mobile device.

At a high level:

- During enrollment, `bind` and `verify` are cryptographically linked.
- During authentication, `pending` and `respond` are cryptographically linked.
- One-time proof tokens and signatures ensure that requests and decisions are tied to the correct flow.
- The backend remains authoritative for verification and final state transitions.

This continuity is central to the product. It is not a side effect of implementation. It is the reason the protocol exists in its current shape.

## What Ezkey Is Not

Ezkey is intentionally not:

- a passkey compatibility claim,
- a browser-first authentication product,
- a standards-equivalence shortcut,
- a feature race against broader identity platforms,
- a security story built on UI sophistication alone.

Ezkey may use secure private-key storage on supported devices. That is useful and important. But it should be understood as a building block, not as the basis of Ezkey's identity.

## Design Principles

- **Simplicity**: solve the real problem without unnecessary conceptual weight.
- **Pragmatism**: favor what developers can integrate, operate, and debug.
- **Explicit trust boundaries**: keep responsibilities clear between backend, mobile device, and integrating application.
- **Backend-first integrity**: treat the backend as the source of durable verification and state.
- **Open source transparency**: make the protocol and implementation inspectable.
- **Self-hosted control**: preserve operational ownership for teams that want it.

## Honest Security Posture

Ezkey states plainly what it does, what it does not do, and what it aims to do — including the current
integrity ceiling and the deliberately-deferred, vendor-neutral external archival direction. See
[`SECURITY_POSTURE.md`](SECURITY_POSTURE.md). The claim is **tamper-evident, not tamper-proof**.

## Closing Position

Ezkey is an opinionated project.

It takes the position that strong cryptographic MFA does not need to be defined by browser-centric standards to be modern, credible, or valuable. It can be designed as a backend-first system with a mobile participant, explicit trust boundaries, and end-to-end cryptographic continuity.

That is the path Ezkey chooses on purpose.
