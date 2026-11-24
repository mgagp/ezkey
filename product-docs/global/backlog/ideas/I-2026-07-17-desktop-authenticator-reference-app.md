# Backlog Idea — `I-2026-07-17-desktop-authenticator-reference-app` Desktop authenticator reference app (mobile-like enrollment and approval)

## Metadata

- **ID:** `I-2026-07-17-desktop-authenticator-reference-app`
- **Status:** `parked` (2026-11-24, see [Parking decision](#parking-decision-2026-11-24))
- **Priority:** `P3`
- **Created at:** `2026-07-17`
- **Updated at:** `2026-11-24`
- **Last reviewed at:** `2026-11-24`
- **GitHub issue:** `#1491`
- **Progression markers:** `P3-polish`
- **Component tags:** `desktop-authenticator`, `demo-device`, `auth-api`, `docs`, `mobile` (parity reference)
- **Lane:** `A`
- **Captured by:** Marc

## Intent

Introduce an installable desktop authenticator application that supports Ezkey enrollment and authentication-approval flows, following the same protocol behavior as the mobile participant and preserving the familiar demo-device visual language as a primary UX anchor.

The UX baseline is deliberately evolutionary, not disruptive: stay close to existing mobile and demo-device workflows and information hierarchy, then adapt only what is necessary for an operator-oriented desktop context.

## Problem and value

- **Problem:** Restricting approval-device participation to mobile can be too constraining for some operator or evaluator contexts.
- **Expected value:** Provide an additional practical participant surface for enrollment and approve/deny workflows on desktop, while keeping protocol parity and improving demo and evaluation flexibility.

## Scope

- **In scope:**
  - Build a desktop app that implements the Ezkey participant flow end to end: bind, verify, pending, and respond.
  - Reuse the current Demo Device visual posture as baseline look-and-feel (phone-like framing inside a desktop app).
  - Keep strong UI continuity with mobile screens and flow ordering when the same user intent is expressed.
  - Trim or hide development/test-only display elements from Demo Device when they do not add operator value.
  - Keep payload/signature behavior aligned with canonical protocol docs and existing mobile/demo-device semantics.
  - Use a contract-first security boundary (`SecureKeyProvider`) so business code remains independent from OS security technologies.
  - Include capability discovery (`GetCapabilities`) so UX can adapt without platform-specific logic in frontend code.
  - Keep this as a reference participant posture, not a full admin console.
- **Out of scope:**
  - Strong hardware-backed key guarantees equivalent to mobile Secure Enclave/StrongBox.
  - New authentication protocol semantics.
  - Any direct dependency from frontend/application services to TPM, CNG internals, Secure Enclave, or platform-specific APIs.
  - A brand-new UX paradigm or workflow model disconnected from mobile/demo-device references.
  - Broad UX redesign beyond first-cut parity with Demo Device.

## Key assumptions

- A desktop participant can add meaningful operational value even with a weaker hardware trust baseline than mobile secure keystore paths.
- Security posture must be explicit and honest in docs and UI copy (no over-claim versus mobile guarantees).
- First value comes from protocol and workflow parity before advanced desktop-specific hardening.
- OS-specific key-management details can be fully encapsulated behind adapters without leaking into business services.
- Capability flags can provide enough signal for safe UX adaptation while preserving a single cross-platform flow model.

## Risks and exceptions

- Security narrative drift if desktop posture is interpreted as equivalent to mobile hardware-bound protections.
- Stack choice and packaging complexity can inflate cost early (runtime, signing, cross-platform distribution).
- UX mismatch risk if visual parity with Demo Device is partial or inconsistent.
- Contract drift risk if adapters expose non-uniform semantics across platforms.
- Capability-reporting ambiguity risk if first-cut taxonomy is underspecified.

## UX quality bar (first cut)

- Desktop experience should remain recognizably close to mobile/demo-device for equivalent protocol actions.
- Quality is judged by practical adequacy across three axes:
  - **Visual adequacy:** layout and information grouping feel familiar versus existing references.
  - **Workflow adequacy:** bind/verify/pending/respond ordering and operator actions stay aligned.
  - **Operator signal-to-noise:** dev/test-only elements are removed or deprioritized when they do not help real operation.

## Promotion notes

Promote to `ready` when:

1. desktop stack posture is selected and documented for first cut,
2. security posture statement is explicit (what is and is not guaranteed),
3. first vertical slice is bounded (enrollment + one approval path + validation evidence),
4. contract-first architecture note is reviewed (layering + capability model + adapter boundaries),
5. first-cut capability matrix is drafted for Windows/macOS/Linux/software fallback.

## Grill Me outcome (2026-07-17)

- Session completed with operator-confirmed decisions in `grill-sessions/2026-07-17-desktop-authenticator-reference-app-grill-me.md`.
- Settled for first cut: cross-platform target (Windows/macOS/Linux), protocol parity with minimal local hardening, and end-to-end minimal slice (bind + verify + one approval response).
- Settled UX direction: mobile/demo-device are the explicit desktop visual and workflow anchors; avoid unnecessary UX reinvention.
- Stack mini-comparison completed in `grill-sessions/2026-07-17-desktop-authenticator-stack-mini-comparison.md` with Tauri as primary recommendation and Electron as fallback.
- Contract-first architecture synthesis captured in `grill-sessions/2026-07-18-desktop-authenticator-contract-first-architecture-synthesis.md` (SecureKeyProvider boundary, capability discovery, adapter encapsulation).
- Capability matrix v0 captured in `grill-sessions/2026-07-18-desktop-authenticator-capability-matrix-v0.md` for first-cut platform posture and UX adaptation baseline.
- Strategic posture confirmed for four critical capability questions (non-exportability pragmatism, opportunistic user verification, attestation as extension point, and runtime downgrade signaling).
- Stack decision locked (Tauri), MVP hardening baseline clarified (encryption at rest mandatory; non-exportability best effort; posture signaling and downgrade warning mandatory), and packaging posture clarified (Windows first).
- Remaining promotion blockers: convert these decisions into an explicit first-cut acceptance checklist and confirm post-Windows packaging order.

## Parking decision (2026-11-24)

**Parked by Marc (#1491).** Parked, not dropped: the analysis above stays valid.

**Positioning.**

- The canonical reference participant is the mobile app (`ezkey_mobile`): it is the protocol proof and the final product for the strongest case. The demo device serves evaluation only.
- A desktop client is not a fundamental canonical reference. Its role is an example: a conforming third-party authenticator built on the authenticator SDK (`ezkey_dart`) shows that the protocol can be taken up with a functional, credible SDK, and an adopter can use it as is within its documented technical limits.

**Why park now.**

- The first third-party consumer of `ezkey_dart`, the Open Authenticator fork `mgagp/openauthenticator` (Flutter; see [`V-2026-10-27-third-party-authenticator-integration-kit`](../../vision/V-2026-10-27-third-party-authenticator-integration-kit.md)), builds for desktop (Windows today) and has already built most of what this idea scoped:
  - one key store contract on every platform, the role this idea gives to `SecureKeyProvider`, with one adapter per platform below it (Android Keystore; Windows CNG on the TPM; Apple Secure Enclave planned);
  - the key's protection tier read back from the key itself and shown with platform-neutral labels, which covers this idea's posture signaling and downgrade warning;
  - a platform joins only with a hardware key store (Linux stays out for now), instead of a software fallback that could be read as equivalent.
- An in-house Tauri app would add a fourth client stack (Rust and React) for a single maintainer, and implement the same native key stores a second time.
- The remaining desktop gap is the same for any desktop client: enrolling on a device that cannot scan the invitation. It is an Ezkey protocol question first, also open for a phone that shows its own invitation.

**What stays useful.** The contract-first boundary, capability discovery, honest posture copy and the mobile / demo-device UX anchors are guidance for any desktop participant, a third-party one included. The four grill-session files remain the references.

**Re-entry triggers.**

1. No installable, signed desktop build of a conforming authenticator is available to adopters.
2. An adopter needs a desktop authenticator under a permissive license that it can rebrand (Open Authenticator is GPL-3.0; Ezkey and `ezkey_dart` are MIT).
3. Once the path for a device that cannot scan is defined, it cannot be delivered in a third-party desktop client.

## Links

- Related backlog idea: `I-2026-06-18-demo-device-qr-auth-url-parity`
- Related backlog idea: `I-2026-0010-phone-to-phone-enrollment-transfer`
- Grill session: `../grill-sessions/2026-07-17-desktop-authenticator-reference-app-grill-me.md`
- Stack comparison: `../grill-sessions/2026-07-17-desktop-authenticator-stack-mini-comparison.md`
- Architecture synthesis: `../grill-sessions/2026-07-18-desktop-authenticator-contract-first-architecture-synthesis.md`
- Capability matrix v0: `../grill-sessions/2026-07-18-desktop-authenticator-capability-matrix-v0.md`
- Related docs: `docs/ENROLLMENT_SIGNATURE_PAYLOAD.md`
- Related docs: `docs/AUTH_ATTEMPT_SIGNATURE_PAYLOAD.md`
- Related component posture: `ezkey_mobile/docs/MOBILE_POSITIONING.md`
