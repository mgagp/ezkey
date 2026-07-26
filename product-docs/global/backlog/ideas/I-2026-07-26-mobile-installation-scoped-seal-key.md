# Backlog Idea — `I-2026-07-26-mobile-installation-scoped-seal-key` Installation-scoped app seal key

## Metadata

- **ID:** `I-2026-07-26-mobile-installation-scoped-seal-key`
- **Status:** `done`
- **Priority:** `P3`
- **Created at:** `2026-07-26`
- **Updated at:** `2026-07-26`
- **Last reviewed at:** `2026-07-26`
- **Progression markers:** `P1-operability`
- **Component tags:** `mobile`
- **Lane:** `D`
- **Captured by:** Marc (Grill Me on MOB-017, 2026-07-26)
- **GitHub issue:** _(none yet)_

## Intent

Replace the single app-wide AES seal key (`ezkey_app_seal_v1`) with one AES seal key per
installation trust zone (`ezkey_seal_{installationId}`), so local at-rest protection of
`enrollmentProofToken` and `integrationPublicKey` follows the same installation-scoped isolation
boundary the app already uses for device signing keys and local identity (MOB-011).

## Problem and value

- **Problem:** the app's only remaining single-key artifact is the app-level AES seal key added by
  `ADR-MOB-0004`. It seals secrets for every installation on the device with one shared key. That
  ADR's alternatives analysis considered "one key per enrollment" (rejected for Keystore slot
  pressure) but never evaluated "one key per installation," which sits between the two extremes and
  matches the existing trust-zone model at a granularity that does not recreate the slot-pressure
  concern (1-3 installations per device, not N enrollments).
- **Expected value:**
  - Local at-rest secrets follow the same installation-scoped blast-radius boundary as device
    signing keys and local identity, closing the one remaining gap in that story.
  - Real, if modest, defense-in-depth: an in-process bug or compromised dependency invoking the
    native seal/unseal bridge across installation boundaries fails closed instead of succeeding.
  - Architectural and narrative consistency for a product whose credibility depends on an honestly
    described, coherent key-architecture story (no passkey/FIDO2-equivalence overclaim).

## Scope

- **In scope:**
  - New Keystore alias derivation for the seal key, scoped by `installationId`
    (`ezkey_seal_{installationId}`), mirroring `getEnrollmentAlias` encoding safety.
  - Thread `installationId` through `EzkeyCryptoModule` seal/unseal/delete native methods and the TS
    storage choke points (`saveEnrollment`, `replaceAll`, `deleteEnrollment`, `clearAll` in
    `enrollmentStorage.ts`).
  - Greenfield cutover: no migration from the old shared `ezkey_app_seal_v1` key (no production
    fleet yet); `clearAll()` remains the recovery path for any leftover dev-device state.
  - Unit tests for per-installation seal isolation (mirroring MOB-011 collision tests); instrumented
    Keystore test extension.
  - Docs: `MOBILE_CRYPTO_REFERENCE.md`, `MOBILE_DATA_MODEL.md`, `NATIVE_MODULES.md`; a new ADR
    (`ADR-MOB-0006`) documenting the revised alternative without rewriting `ADR-MOB-0004`'s original
    historical reasoning.
  - Assessment MOB-017 disposition update; pass-3 campaign closeout.
- **Out of scope:**
  - Device signing key generation/derivation — already correctly per-enrollment,
    installation-scoped since MOB-011; no derivation chain exists anywhere to redesign.
  - Auth API / OpenAPI contract changes (this is local storage hardening only, per `ADR-MOB-0004`'s
    own boundary statement).
  - A new "remove this installation" bulk UX action (not currently in scope; per-installation seal
    key deletion beyond `clearAll()` is not required for this idea).
  - iOS parity as a release gate (Android-first).
  - Production fleet migration tooling.

## Key assumptions

- Product claim (unchanged, MOB-011 canon): one app may hold enrollments for multiple independent
  Ezkey installations; trust zones must not encroach on each other. This idea closes the seal-key
  gap in that story.
- No production customer fleet; greenfield cutover risk is acceptable for a clean design.
- `enrollmentStorage.ts` choke points already carry the full `StoredEnrollment` (with nested
  `installation`) at every call site that would need `installationId` — confirmed during the
  2026-07-26 Grill Me, so this is not expected to require a wider blast radius than estimated.
- Existing `logicalKey` AAD scoping (installation-scoped local enrollment id) already provides
  enrollment-grain fail-closed authentication even with a shared key; the per-installation key is an
  additional depth layer, not a fix for a currently broken guarantee.

## Risks and exceptions

- Wording overclaim risk: must describe this as local at-rest defense-in-depth, not as a new
  cryptographic installation-isolation guarantee — an in-process attacker that already controls the
  native bridge call site can still target one installation's key. Follow the same guardrails as
  `MOBILE_CRYPTO_REFERENCE.md` (no passkey/FIDO2-equivalence language).
- Dev devices with leftover per-installation seal-key aliases after experiments — mitigated by
  `clearAll()`; not a fleet migration concern.
- Scope creep into a new "remove installation" UX action — keep out of scope explicit; `clearAll()`
  remains the only seal-key cleanup path for this slice.

## Promotion notes

Grill Me 2026-07-26 (MOB-017) locked the corrected framing and the fix decision with greenfield
cutover posture. Implemented the same day under tracer bullet
[`TB-2026-07-26-mobile-installation-scoped-seal-key`](../TB-2026-07-26-mobile-installation-scoped-seal-key.md)
(status `done`) as `ADR-MOB-0006`; validated with unit (225/225) and Android instrumented (7/7,
physical device) test suites.

## Links

- Assessment: [`docs/security/mobile-protocol-crypto-assessment-2026-07.md`](../../../../docs/security/mobile-protocol-crypto-assessment-2026-07.md) §15 MOB-017
- Campaign: [`product-docs/global/hygiene/mobile-protocol-security/2026-07-26-pass-3.md`](../../hygiene/mobile-protocol-security/2026-07-26-pass-3.md)
- Prior program (precedent + prerequisite context): [`I-2026-07-20-mobile-installation-scoped-enrollment-identity`](I-2026-07-20-mobile-installation-scoped-enrollment-identity.md)
- Mobile data model: [`ezkey_mobile/docs/MOBILE_DATA_MODEL.md`](../../../../ezkey_mobile/docs/MOBILE_DATA_MODEL.md)
- Mobile crypto reference: [`ezkey_mobile/docs/MOBILE_CRYPTO_REFERENCE.md`](../../../../ezkey_mobile/docs/MOBILE_CRYPTO_REFERENCE.md)
- Design decisions: [`product-docs/components/mobile/design-decisions.md`](../../../components/mobile/design-decisions.md) ADR-MOB-0004
