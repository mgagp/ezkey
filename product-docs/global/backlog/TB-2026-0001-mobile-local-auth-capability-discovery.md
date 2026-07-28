# Tracer Bullet Brief — `TB-2026-0001` Mobile local-auth capability and design discovery

## Metadata

- **ID:** `TB-2026-0001`
- **Status:** `active` — capability matrix delivered 2026-07-26; awaiting operator decision on promoting `I-2026-0001` to `ready`
- **Related idea:** `I-2026-0001`
- **Created at:** `2026-05-07`
- **Updated at:** `2026-07-26`
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

- Documented Android capability matrix (what can and cannot be bound to key usage). **Delivered
  2026-07-26:** [`ezkey_mobile/docs/MOBILE_ANDROID_LOCAL_AUTH_CAPABILITY_MATRIX.md`](../../../../ezkey_mobile/docs/MOBILE_ANDROID_LOCAL_AUTH_CAPABILITY_MATRIX.md).
- Option comparison with trade-offs and re-enrollment risk.
- Three-tier policy granularity model and merge rule, cross-linked to the installation trust-zone canon.
- Documented signed bind-attribute transport direction for future installation/enrollment policy delivery.
- Updated backlog idea status and explicit recommendation.
- Test plan slice validated and refined for implementation stage.
- Grill Me analysis: [`grill-sessions/2026-05-07-mobile-local-auth-capability-discovery-grill-me.md`](grill-sessions/2026-05-07-mobile-local-auth-capability-discovery-grill-me.md).

## Capability matrix — key takeaways (2026-07-26)

Full detail in the capability matrix doc linked above. Headline findings that matter for sequencing:

- Every Keystore attribute relevant to auth-binding is fixed at key-generation time; none are
  runtime-alterable on an existing key. A Level 2 → Level 3 move is always a re-key event.
- Biometric-enrollment invalidation (`KeyPermanentlyInvalidatedException`) only fires for keys
  requiring auth on **every** use (timeout `0`) — but that is exactly the posture Ezkey's own living
  design note already recommends (per-action confirmation, no long grace window). The recommended
  UX posture and the most invalidation-exposed key configuration are the same one. This is a real,
  quantified cost, not a hypothetical edge case.
- StrongBox and auth-binding are independent, compatible flags — no new fallback shape needed beyond
  the existing `StrongBoxUnavailableException` retry pattern.
- Ezkey's Android 12+ (API 31) floor already exceeds every API level these Keystore features
  require, so there is no additional OS-fragmentation risk beyond what MOB-012 already handles.
- A biometric-only strict tier (no device-credential fallback) is expressible but would fail key
  generation outright on devices with no strong biometric enrolled — any future strict tier needs an
  explicit, honest failure path, not a silent downgrade.

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
