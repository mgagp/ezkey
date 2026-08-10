# Backlog Idea — `I-2026-07-25-mobile-certificate-pinning-middle-path` Mobile certificate pinning middle path

## Metadata

- **ID:** `I-2026-07-25-mobile-certificate-pinning-middle-path`
- **Status:** `incubating`
- **Priority:** `P2`
- **Created at:** `2026-07-25`
- **Updated at:** `2026-08-09`
- **Last reviewed at:** `2026-07-25`
- **Progression markers:** `P2-hardening`, `P3-distribution`
- **Component tags:** `mobile`, `auth-api`, `admin-api`, `docs`, `security`
- **Lane:** `D`
- **Captured by:** Marc
- **GitHub issue:** none

## Intent

Converge and formalize Ezkey's opinionated certificate pinning posture as a proportionate middle
path:

- materially stronger than TLS-only mobile transport,
- explicitly below passkey/FIDO-style formal assurance ceremony,
- operationally viable for Cloudflare-fronted self-hosted deployments.

## Problem and value

- **Problem:** The strategic direction exists in historical analysis and partial canon, but the
  final shape is still evolving and can drift when revisited informally.
- **Expected value:** Keep one active canonical idea that absorbs each new design refinement
  (protocol payload, recovery ceremony, claims boundary, complexity budget) without reviving
  point-in-time plan files.

## Scope

- **In scope:**
  - Finalize SPKI pinning posture at installation scope with enrollment-time TOFU.
  - Define the narrow Auth API trust-recovery contract (request/response canonical payloads,
    signing, verification, user-confirmed replacement).
  - Clarify Cloudflare rotation handling, pin-set versioning, and rollout constraints.
  - Define operator-facing and docs-facing honesty language for guarantees and limits.
  - Keep StrongBox semantics explicit: used when available, persisted as client-reported signal,
    not server-verified attestation in current protocol.
  - Define proportional rollout order (Android first, iOS follow-up decision gate).
- **Out of scope:**
  - Claiming passkey/FIDO2/WebAuthn equivalence or full attestation-chain assurance.
  - Building a maximal certificate-lifecycle ceremony that exceeds Ezkey's complexity budget.
  - Reframing Ezkey as a compliance/certification-grade assurance product.
  - Forcing iOS parity as an immediate gate before Android validation.

## Strategic guardrails

1. **Line in the sand on complexity:** do not reproduce every formal assurance layer from
   passkey ecosystems.
2. **Honest posture:** document what is best-effort, what is cryptographically proven, and what is
   client-reported.
3. **Security proportionnée:** optimize for meaningful practical gain in the target market, not for
   theoretical maximality.
4. **Adopter agency:** organizations decide whether this assurance level is acceptable for their
   risk profile.

## Candidate paths retained

1. Ezkey default: SPKI + TOFU + signed recovery + explicit user confirmation.
2. Higher-assurance optional path: dedicated mobile hostname / tighter cert lifecycle control
   where operator environment supports it.

## Risks and exceptions

- Over-complicating recovery can erase the operational value of the middle path.
- Under-specifying recovery can create ambiguous trust-refresh behavior and inconsistent UX.
- Overclaiming StrongBox or pinning guarantees creates trust debt and documentation drift.

## Promotion notes

Keep this idea `incubating` until:

- recovery payload canon is fixed,
- Cloudflare rotation posture is explicit,
- docs claim boundaries are fully synchronized,
- first implementation slice is bounded into a `TB-*`.

## Links

- Vision:
  [`V-2026-0006-mobile-certificate-pinning`](../../vision/V-2026-0006-mobile-certificate-pinning.md)
- Retrofit foundation:
  [`R-2026-0001-mobile-certificate-pinning-spki`](../../legacy-retrofit/R-2026-0001-mobile-certificate-pinning-spki.md)
- Adjacent response-integrity track (branding / instance-info, complementary to transport pinning):
  [`I-2026-08-09-mobile-signed-instance-info-integrity.md`](I-2026-08-09-mobile-signed-instance-info-integrity.md)
- Security claim boundary:
  [`docs/SECURITY_POSTURE.md`](../../../../docs/SECURITY_POSTURE.md)
- StrongBox trust-model boundary:
  [`docs/MOBILE_DEVELOPER_GUIDE.md`](../../../../docs/MOBILE_DEVELOPER_GUIDE.md)
- Mobile crypto wording guardrails:
  [`ezkey_mobile/docs/MOBILE_CRYPTO_REFERENCE.md`](../../../../ezkey_mobile/docs/MOBILE_CRYPTO_REFERENCE.md)
