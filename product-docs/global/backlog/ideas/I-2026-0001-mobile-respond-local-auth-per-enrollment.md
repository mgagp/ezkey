# Backlog Idea — `I-2026-0001` Per-enrollment local authentication policy for mobile `respond`

## Metadata

- **ID:** `I-2026-0001`
- **Status:** `incubating`
- **Priority:** `P1`
- **Created at:** `2026-05-07`
- **Updated at:** `2026-07-26`
- **Last reviewed at:** `2026-07-26`
- **Phase tags:** `P2-hardening`, `P1-operability`
- **Component tags:** `mobile`, `auth-api`, `admin-api`, `admin-ui`
- **Captured by:** Marc

## Intent

Evolve mobile local-auth behavior from a global toggle to a **three-tier** posture for `respond`:
a mobile-local user preference, a per-enrollment policy, and a per-installation
(organization-owned) policy — while preserving honest security guarantees and avoiding accidental
re-enrollment lock-in.

## Problem and value

- **Problem:** Local protected confirmation for `respond` is currently global, optional, and not represented at protocol level. It is not explicitly tied to enrollment-level policy and creates ambiguity about what is protected by keystore key material versus app-level local preference. Beyond that, there is currently no way for an Ezkey **installation operator** (e.g. an on-prem SME deployment) to require stronger local authentication across its own enrollments — only the individual end user can express a preference today.
- **Expected value:** Better security posture and operator clarity by enabling enrollment-scoped **and** installation-scoped policy evolution, with clear technical limits, migration-safe behavior, and a defensible market position (security-conscious end users can raise their own bar; organizations that need to can impose a floor — without turning authentication, which is never anyone's core business except Ezkey's, into unnecessary friction for the common case).

## Scope

- **In scope:**
  - Clarify Android local-auth capability boundaries for enrollment keys (capability matrix — `TB-2026-0001`).
  - Evaluate per-enrollment local-auth preference model in mobile.
  - Clarify **policy granularity across three tiers**: local user preference (device), per-enrollment policy, and per-installation (organization) policy. Anchor the installation tier on the existing installation-as-trust-zone canon (`I-2026-07-20-mobile-installation-trust-zone-canon`) rather than inventing a new mobile identity concept. Generalize the existing "OR" merge rule (`enrollmentPolicyRequiredAuth OR userPreferenceRequiredAuth`, see `MOBILE_LOCAL_AUTH_POLICY_AND_AUDIT_FUTURE_WORK.md`) to "the strongest of the three tiers wins."
  - Document the natural protocol extension point for **transporting** installation/enrollment policy to the device: a signed attribute carried in the enrollment **bind** payload, alongside the tenant/enrollment metadata already signed there (see `docs/ENROLLMENT_SIGNATURE_PAYLOAD.md` § Bind response) — verified with the same Ed25519 signature already used for bind, no new crypto primitive required. This is a documented **direction**, not a contract change in this slice.
  - Evaluate product consequences of requiring stronger local-auth posture (Level 3 — Keystore-enforced auth-bound keys, see MOB-001 Track B).
  - Identify protocol and UX implications if backend policy is introduced later.
  - Name, at a design-awareness level only, the downstream consequence chain **if** installation-level policy becomes backend-owned in a later increment: a DB representation (installation-scoped policy column/table), an Admin API surface (read/write for that policy), and an Admin UI control (Global Admin, since this is an installation/instance-level concern per the Global Admin vs Tenant Admin split). This idea does **not** commit to designing or implementing that chain — it only ensures the discovery slice does not silently foreclose it.
  - Define safe migration options that avoid unnecessary re-enrollment.
  - Confirm sequencing: the Android capability matrix (`TB-2026-0001`) and an audit-first protocol extension (declarative fields on `respond` — see living doc Workstream 1) are the actual low-risk next steps. Level 3 (Keystore/CryptoObject auth-bound keys, `MOB-001` Track B) is the **highest-risk** workstream — key invalidation on biometric enrollment changes and Android-version fragmentation are real migration costs, not a "quick win." Do not schedule Level 3 ahead of the capability matrix.
- **Out of scope:**
  - Immediate backend policy enforcement implementation.
  - Immediate DB migration, Admin API, or Admin UI implementation for installation-level policy (design-awareness only in this slice — see bullet above).
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

1. A concise Android capability matrix is documented (auth-bound keys, StrongBox interactions, invalidation behavior). — **Done 2026-07-26:** [`ezkey_mobile/docs/MOBILE_ANDROID_LOCAL_AUTH_CAPABILITY_MATRIX.md`](../../../../ezkey_mobile/docs/MOBILE_ANDROID_LOCAL_AUTH_CAPABILITY_MATRIX.md).
2. At least two design options are compared (preference-only vs key-bound enforcement). — **Done** ([`grill-sessions/2026-05-07-mobile-local-auth-capability-discovery-grill-me.md`](../grill-sessions/2026-05-07-mobile-local-auth-capability-discovery-grill-me.md) Options A/B/C/D).
3. Re-enrollment risk is explicitly characterized for each option. — **Done**, sharpened by the capability matrix (every Level 2→3 move is a re-key event; per-action posture is the most invalidation-exposed configuration).
4. A minimal test plan slice is defined across unit, functional, and UI layers. — **Done** ([`test-plans/TSP-2026-05-07-mobile-local-auth-per-enrollment-discovery.md`](../test-plans/TSP-2026-05-07-mobile-local-auth-per-enrollment-discovery.md)).
5. The three-tier granularity model and merge rule are documented, and the sequencing
   recommendation (capability matrix + audit-first before any Level 3 key-binding) is explicit. — **Done.**

**2026-07-26 status note:** all five criteria above are now satisfied. Promoting this idea to
`ready` is an operator decision, not an automatic next step — `ready` should mean "implementation
scope is well-understood enough to schedule," and the first schedulable increment is still narrow
(the audit-first `respond` extension, Workstream 1 in the living doc), not the full three-tier
policy.

**Decision (2026-07-26):** stay `incubating` for now. The discovery evidence is complete, but the
operator wants more reflection time before making this formally schedulable. Re-visit this decision
without redoing the discovery work above — it does not need to be repeated.

## Links

- Related vision notes: `../../vision/product-orientation-notes.md` (`V-2026-0001`)
- Related features and phases: `../../features-and-phases.md` (`F-mobile-reference-app`, `F-auth-pending-respond`)
- Related component docs:
  - `../../../../ezkey_mobile/docs/MOBILE_CRYPTO_REFERENCE.md`
  - `../../../../ezkey_mobile/docs/MOBILE_LOCAL_AUTH_POLICY_AND_AUDIT_FUTURE_WORK.md` — three-tier model, merge rule, signed bind-attribute direction (2026-07-26)
  - `../../../../ezkey_mobile/docs/MOBILE_ANDROID_LOCAL_AUTH_CAPABILITY_MATRIX.md` — Android capability matrix (2026-07-26)
  - `../../../../docs/MOBILE_DEVELOPER_GUIDE.md`
  - `../../../../docs/ENROLLMENT_SIGNATURE_PAYLOAD.md` — bind payload extension point for a future policy attribute
  - `../../../../docs/security/mobile-p1-sensitive-storage-investigation-2026-05.md`
  - `../../../../docs/security/mobile-security-assessment-2026-05.md`
  - `../../../../docs/security/mobile-protocol-crypto-assessment-2026-07.md` § MOB-001 — Level 3 (Keystore/CryptoObject) risk detail
- Related backlog: `I-2026-07-20-mobile-installation-trust-zone-canon` (done) — installation-as-trust-zone anchor for the installation policy tier
- Related backlog (2026-07-26, on `origin/main`, not yet in this branch): `MOB-017` /
  `ADR-MOB-0006` (`product-docs/components/mobile/design-decisions.md`) — installation-scoped
  Android seal key, PR [#412](https://github.com/mgagp/ezkey/pull/412). Completes end-to-end
  crypto isolation of the installation trust zone (signing key + local id + seal key), reinforcing
  the anchor used for the installation policy tier above.
