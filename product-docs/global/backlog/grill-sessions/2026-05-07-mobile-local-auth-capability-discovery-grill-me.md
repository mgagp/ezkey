# Grill Me Analysis — `TB-2026-0001-mobile-local-auth-capability-discovery`

## Purpose

Stress-test the discovery tracer bullet before design lock-in.

## Critical questions

0. What is the right policy granularity for Ezkey's market position — local user preference only,
   per-enrollment, per-installation (organization-owned), or all three with an explicit "strongest
   tier wins" merge rule? (2026-07-26 addendum — see Option D below.)
1. What must be true in Android key capabilities for per-enrollment local-auth enforcement to avoid forced re-enrollment?
2. Can local-auth requirements be changed post-enrollment without invalidating the enrollment signing key?
3. Which key attributes are fixed at generation time versus configurable at runtime?
4. Is the current local-auth gate a UI preference only, or can it be cryptographically bound to key usage in current architecture?
5. What is the exact failure mode when user authentication requirements change and stored keys do not satisfy new policy?
6. What happens on devices without `BIOMETRIC_STRONG` configured but with device credential available?
7. What migration behavior is acceptable if an enrollment was created under weaker local policy?
8. Which behavior differences are Android-version dependent and can break consistency?
9. What is the minimal user-facing wording that remains honest (local protection vs backend-verified assurance)?
10. If backend policy is added later, how do we prevent dead-end flows for existing enrollments?
11. Should enrollment-scoped preference be optional override or strict requirement?
12. Do we need policy states beyond boolean (`STANDARD`, `PROTECTED`, `STRICT_REENROLL_REQUIRED`)?
13. Which events should be auditable locally and server-side once policy posture evolves?
14. Which assumptions from current docs are contradicted by implementation details?
15. What minimal evidence would falsify the preferred design option?

## Risk list (ordered)

1. **Key lifecycle mismatch risk**: policy strengthening may require key regeneration and lead to involuntary re-enrollment.
2. **Assurance overstatement risk**: UX or docs may imply server-verified local-auth proof that does not exist.
3. **Fragmentation risk**: Android API/device capability differences may create inconsistent behavior.
4. **Policy dead-end risk**: future backend requirements may reject responses from legacy enrollments.
5. **UX confusion risk**: per-enrollment controls may be misunderstood without clear status labels and fallback behavior.

## Exception and error paths

- Enrollment requires stronger local-auth than device can satisfy.
- Existing enrollment policy is upgraded, but key attributes cannot comply.
- User toggles local policy and later cannot respond due to key or authenticator state drift.
- Enrollment-scoped policy conflicts with global app preference.
- Backend later expects stronger proof semantics than mobile can attest.

## Decision pressure points

1. **Preference-only vs key-bound enforcement**:
   - preference-only is safer for continuity now,
   - key-bound may improve local assurance but raises migration complexity.
2. **Backward compatibility posture**:
   - maintain existing enrollments as-is,
   - or require progressive re-enrollment for strict tiers.
3. **Policy granularity**:
   - boolean posture is simpler,
   - tiered posture is clearer for future policy contracts.
4. **When to involve backend**:
   - keep phase 1 local-only,
   - design explicit extension points for future backend policy.
5. **Policy scope (2026-07-26 addendum)**:
   - local-preference-only keeps the org out of the decision entirely,
   - per-enrollment-only lets a tenant admin scope policy per business relationship but has no
     "floor" concept for a whole installation,
   - per-installation (organization-owned) gives a Global Admin a way to impose a security floor
     across all enrollments issued by that instance, which matters for the SME-adopter market
     Ezkey targets — but requires DB, Admin API, and Admin UI surface in a later increment.

## Design options

### Option A — Enrollment-scoped preference only (recommended V1)

- Store local-auth posture per enrollment as local policy metadata.
- Keep key generation model unchanged in V1.
- Enforce local confirmation UX per enrollment before `respond`.
- Do not claim protocol-level attestation change.

**Pros**
- Lowest implementation and migration risk.
- No forced re-enrollment in V1.
- Fast path to better operator/user control.

**Cons**
- Assurance remains client-enforced, not backend-verifiable.
- Does not increase cryptographic binding strength yet.

### Option B — Enrollment-scoped key-bound enforcement

- Generate/rotate enrollment keys with user-auth requirements tied to stronger posture.
- Enforce protected respond via key-use semantics.

**Pros**
- Stronger local assurance posture.
- Better long-term alignment if backend policy is introduced.

**Cons**
- High risk of re-enrollment/regeneration complexity.
- Higher device capability and migration edge cases.
- Higher delivery cost and uncertainty for first increment.

### Option C — Hybrid phased model

- Start with Option A.
- Add optional strict enrollment tier that may require re-key/re-enroll with explicit UX.
- Keep strict tier off by default during first rollout.

**Pros**
- Preserves continuity while opening future hardening path.
- Controlled risk exposure.

**Cons**
- More states to explain and test.
- Requires disciplined phase management.

### Option D — Installation-scoped (organization) policy via signed bind attribute (documented direction, not V1)

- The Ezkey installation (Global Admin, instance-level concern — consistent with the Global Admin
  vs Tenant Admin split) sets a minimum local-auth requirement for all enrollments it issues.
- Transport: a new attribute in the enrollment **bind** payload, signed by the integration's
  Ed25519 key alongside the existing tenant/enrollment metadata (`docs/ENROLLMENT_SIGNATURE_PAYLOAD.md`
  § Bind response) — no new crypto primitive, reuses the signature the mobile app already verifies.
- Effective decision generalizes the existing rule to: `installationPolicyRequiresAuth OR
  enrollmentPolicyRequiresAuth OR userPreferenceRequiresAuth` (strongest tier wins).
- Consequence chain if funded later: DB representation (installation-scoped policy), Admin API
  surface, Admin UI control. **Not** committed in this tracer bullet — named so the discovery slice
  does not foreclose it.

**Pros**
- Anchors on the already-canonical installation trust zone (`I-2026-07-20-mobile-installation-trust-zone-canon`), which is now a complete crypto isolation boundary end to end — signing key alias (MOB-011), local enrollment id (MOB-011), and at-rest seal key (`MOB-017` / `ADR-MOB-0006`, PR #412, 2026-07-26) are all installation-scoped; no new mobile identity concept.
- Reuses an existing signed payload instead of a new protocol primitive.
- Gives organizations a real security floor without removing the end user's ability to raise their own bar locally.

**Cons**
- Real DB/Admin API/Admin UI scope once implemented — a separate, later increment, not this slice.
- Adds a third source of truth to reconcile in the merge rule (manageable — same OR-based pattern already used for two tiers).

## Recommended next clarifications

1. Build an Android capability matrix for auth-bound keys vs runtime policy changes.
2. Define posture vocabulary for product and UX copy with explicit honesty boundaries.
3. Define migration policy for existing enrollments under future stricter tiers.
4. Decide whether V1 scope is Option A or C.
5. Draft first implementation slice criteria and update `TB-2026-0001` accordingly.

## Recommendation

Proceed with **Option A** for first implementation slice, with explicit architecture notes preparing a future move to Option C if stronger local assurance is needed.

**2026-07-26 addendum.** Option D (installation-scoped policy) is a **documented future direction**,
not part of the first implementation slice. Recommended sequencing, from lowest to highest risk:

1. Android capability matrix (this tracer bullet's core deliverable) — no code risk.
2. Audit-first `respond` extension (declarative fields; living doc Workstream 1) — low risk, no key-lifecycle change.
3. Policy representation work: generalize the merge rule to three tiers and prepare the installation-level model in docs/data-model terms only (no DB/API/UI yet).
4. Only then, if funded: Option D transport (signed bind attribute) and/or Option B/C key-bound enforcement (Level 3, MOB-001 Track B) — the highest-risk step, requiring the capability matrix from step 1 first.
