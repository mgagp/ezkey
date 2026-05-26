# Backlog Idea — `I-2026-0001` Per-enrollment local authentication policy for mobile `respond`

## Metadata

- **ID:** `I-2026-0001`
- **Status:** `incubating`
- **Priority:** `P1`
- **Created at:** `2026-05-07`
- **Updated at:** `2026-05-07`
- **Last reviewed at:** `2026-05-07`
- **Phase tags:** `P2-hardening`, `P1-operability`
- **Component tags:** `mobile`, `auth-api`, `admin-api`, `admin-ui`
- **Captured by:** Marc

## Intent

Evolve mobile local-auth behavior from a global toggle to a per-enrollment security preference for `respond`, while preserving honest security guarantees and avoiding accidental re-enrollment lock-in.

## Problem and value

- **Problem:** Local protected confirmation for `respond` is currently global, optional, and not represented at protocol level. It is not explicitly tied to enrollment-level policy and creates ambiguity about what is protected by keystore key material versus app-level local preference.
- **Expected value:** Better security posture and operator clarity by enabling enrollment-scoped policy evolution, with clear technical limits and migration-safe behavior.

## Scope

- **In scope:**
  - Clarify Android local-auth capability boundaries for enrollment keys.
  - Evaluate per-enrollment local-auth preference model in mobile.
  - Evaluate product consequences of requiring stronger local-auth posture.
  - Identify protocol and UX implications if backend policy is introduced later.
  - Define safe migration options that avoid unnecessary re-enrollment.
- **Out of scope:**
  - Immediate backend policy enforcement implementation.
  - Claims of server-verified local-auth proof or attestation.
  - iOS parity delivery in this first analysis slice.

## Key assumptions

- Android is the current reference security path.
- Current local confirmation improves honest-client posture but is not backend-attested.
- Some design options may require key lifecycle changes that can affect enrollment continuity.
- A discovery-first tracer bullet is needed before implementation commitments.

## Risks and exceptions

- Key-generation flags and local-auth requirements may imply key invalidation patterns that trigger re-enrollment.
- Confusing UI or documentation could overstate assurance beyond current protocol proofs.
- Per-enrollment settings may increase UX complexity if defaults and downgrade rules are unclear.
- Policy mismatch between backend expectations and mobile capabilities could create dead-end flows.

## Promotion notes

Move this idea to `ready` only after:

1. A concise Android capability matrix is documented (auth-bound keys, StrongBox interactions, invalidation behavior).
2. At least two design options are compared (preference-only vs key-bound enforcement).
3. Re-enrollment risk is explicitly characterized for each option.
4. A minimal test plan slice is defined across unit, functional, and UI layers.

## Links

- Related vision notes: `../../vision/product-orientation-notes.md` (`V-2026-0001`)
- Related features and phases: `../../features-and-phases.md` (`F-mobile-reference-app`, `F-auth-pending-respond`)
- Related component docs:
  - `../../../../ezkey_mobile/docs/MOBILE_CRYPTO_REFERENCE.md`
  - `../../../../docs/MOBILE_DEVELOPER_GUIDE.md`
  - `../../../../docs/security/mobile-p1-sensitive-storage-investigation-2026-05.md`
  - `../../../../docs/security/mobile-security-assessment-2026-05.md`
