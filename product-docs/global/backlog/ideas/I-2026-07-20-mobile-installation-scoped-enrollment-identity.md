# Backlog Idea — `I-2026-07-20-mobile-installation-scoped-enrollment-identity` Installation-scoped local enrollment identity

## Metadata

- **ID:** `I-2026-07-20-mobile-installation-scoped-enrollment-identity`
- **Status:** `ready`
- **Priority:** `P1`
- **Created at:** `2026-07-20`
- **Updated at:** `2026-07-20`
- **Last reviewed at:** `2026-07-20`
- **Progression markers:** `P1-operability`
- **Component tags:** `mobile`
- **Lane:** `D`
- **Captured by:** Marc (Grill Me on MOB-011 handoff, 2026-07-20)
- **GitHub issue:** _(none yet)_
- **Prerequisite:** `I-2026-07-20-mobile-installation-trust-zone-canon` (complete first)

## Intent

Deliver the **full first-principles** local enrollment identity design for multi-installation:
collision-free local identity and crypto/storage handles scoped to the installation trust zone,
including no silent Keystore ensure on pending/respond and orphan-key cleanup after failed verify.
Design as if the app had been built correctly with today’s knowledge — not a minimal hygiene patch.

## Problem and value

- **Problem (MOB-011):** Wizard and storage use `String(serverEnrollmentId)` as `StoredEnrollment.id`,
  Keystore alias `ezkey_enrollment_{id}`, and sealed-secret keys. Two Auth APIs can both allocate
  `enrollment_id = 1` on one phone → key reuse, seal overwrite, metadata drop. Installation metadata
  exists for UI/routing but does not scope those surfaces.
- **Related (same chantier):** MOB-013 (silent `ensureEnrollmentKeyPair` on pending/respond) and
  MOB-016 (orphan Keystore keys after failed verify) amplify collision and misdiagnosis.
- **Expected value:**
  - Independent trust zones remain isolated in metadata **and** Keystore/seal handles.
  - Auth API bodies keep numeric `enrollmentId` (servers stay installation-local).
  - Fail-closed identity behavior (no silent overwrite of another zone’s material).
  - One coherent design cut covering identity + ensure policy + orphan cleanup.

## Scope

- **In scope:**
  - Local enrollment identity aligned with trust zone (preferred shape **O3**: deterministic local
    id / handles from `installationId` + `serverEnrollmentId`, Keystore-safe encoding; **O4** opaque
    UUID local primary key acceptable if uniqueness `(installationId, serverEnrollmentId)` is
    enforced).
  - Scope identity from **`buildDraft` (7A)**; create Keystore in `finalizeEnrollment` before verify;
    orphan cleanup on verify/save failure (MOB-016).
  - Keystore aliases, seal/SecureStore logical keys, AsyncStorage collection identity, React Query
    keys, navigation params, wipe/delete paths — same uniqueness rule as the data model.
  - Remove silent key generation/attach on pending/respond (MOB-013) as part of the same design.
  - Unit tests for A/B collision (`enrollment_id = 1` on two normalized Auth URLs); instrumentation
    for distinct aliases; docs update for the new contract.
  - Greenfield cutover (no production fleet migration program).
- **Out of scope:**
  - Trust-zone canon / “belongs to” documentation foundation (prerequisite idea).
  - Auth API / OpenAPI changes (unless analysis proves required — unexpected).
  - Installation UUID; per-installation StrongBox partitioning as a separate abstraction.
  - MOB-012 unlocked-device API gate (separate).
  - MOB-001 Track B, MOB-003 attestation, pinning, integrity APIs.
  - iOS parity as a release gate.
  - Production fleet migration tooling.

## Collision scenario (validation bar — preserve)

1. Enroll on installation A → server A allocates `enrollment_id = 1`.
2. Enroll on installation B on the same phone → server B allocates `enrollment_id = 1`.
3. **Must not:** reuse A’s EC private key, overwrite A’s sealed secrets, or drop A’s metadata row.
4. Non-claim: this is local multi-installation isolation, not a remote MFA bypass of an honest
   single-installation device.

## Key assumptions

- Prerequisite canon is done: installation = normalized Auth URL; enrollment belongs to trust zone.
- No special crypto isolation layer beyond collision-free handles derived from the same identity.
- Outside production: prefer complete redesign over reversible half-slice.
- MOB-013 and MOB-016 are **not** separate hygiene PRs once this program is funded.

## Risks and exceptions

- Large blast radius across wizard, storage, native crypto module, query keys, wipe — mitigated by
  existing mobile test corpus, clean-start procedures, and explicit evidence plan on the TB.
- Dev devices with leftover aliases after experiments — wipe/clear-all; not a fleet migration.
- Encoding mistakes for Keystore aliases (raw URLs unsafe) — require documented safe derivation +
  tests.

## Promotion notes

Grill Me 2026-07-20 complete. Execute only after
`I-2026-07-20-mobile-installation-trust-zone-canon` / its TB exit criteria. Tracer bullet:
[`TB-2026-07-20-mobile-installation-scoped-enrollment-identity`](../TB-2026-07-20-mobile-installation-scoped-enrollment-identity.md).

## Links

- Assessment §14.2 MOB-011 / MOB-013 / MOB-016:
  [`docs/security/mobile-protocol-crypto-assessment-2026-07.md`](../../../../docs/security/mobile-protocol-crypto-assessment-2026-07.md)
- Campaign: [`2026-07-19-pass-2.md`](../../hygiene/mobile-protocol-security/2026-07-19-pass-2.md)
  (ephemeral MOB-011/013/016 handoffs deleted after promotion)
- Prerequisite: [`I-2026-07-20-mobile-installation-trust-zone-canon`](I-2026-07-20-mobile-installation-trust-zone-canon.md)
