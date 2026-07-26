# Tracer Bullet Brief — `TB-2026-0001` Mobile local-auth capability and design discovery

## Metadata

- **ID:** `TB-2026-0001`
- **Status:** `draft`
- **Related idea:** `I-2026-0001`
- **Created at:** `2026-05-07`
- **Updated at:** `2026-05-07`
- **Captured by:** Marc

## Objective

Produce a validated discovery slice that clarifies Android local-auth and key-lifecycle constraints, then selects a first product-safe design direction for enrollment-scoped local-auth posture.

## Boundaries in scope

- Mobile local-auth preference model for `respond`.
- Mobile key and secure-storage behavior relevant to local-auth posture.
- Contract and UX implications documented for future backend/operator alignment.
- Policy granularity across three tiers: local user preference, per-enrollment policy, and
  per-installation (organization) policy — anchored on the existing installation-as-trust-zone
  canon (`I-2026-07-20-mobile-installation-trust-zone-canon`). Document the generalized "strongest
  tier wins" merge rule; do not implement installation-level enforcement in this slice.
- Document (design direction only) the signed bind-attribute transport option for carrying
  installation/enrollment policy to the device, reusing the existing Ed25519 bind signature
  (`docs/ENROLLMENT_SIGNATURE_PAYLOAD.md` § Bind response) rather than a new primitive.

## Out of scope

- Full backend policy enforcement implementation.
- iOS parity implementation.
- Attestation-grade protocol redesign.

## Critical flows

- Nominal path: user selects an auth attempt, local confirmation gate runs as configured, respond proceeds.
- Critical exception path: required local-auth posture cannot be satisfied without destructive re-enrollment path.

## Evidence plan

- Documented Android capability matrix (what can and cannot be bound to key usage).
- Option comparison with trade-offs and re-enrollment risk.
- Three-tier policy granularity model and merge rule, cross-linked to the installation trust-zone canon.
- Documented signed bind-attribute transport direction for future installation/enrollment policy delivery.
- Updated backlog idea status and explicit recommendation.
- Test plan slice validated and refined for implementation stage.
- Grill Me analysis: `TB-2026-0001-grill-me.md`.

## Quality gates

- analysis gate
- design gate
- test gate

## Exit criteria

`TB-2026-0001` is validated when:

1. one recommended first implementation path is selected,
2. re-enrollment implications are explicit,
3. trust-boundary wording is explicit and honest,
4. implementation-ready follow-up scope is defined,
5. the sequencing recommendation is explicit: capability matrix + audit-first `respond` extension
   come before any Level 3 (Keystore/CryptoObject auth-bound key) work, which is the highest-risk
   workstream, not a quick win.
