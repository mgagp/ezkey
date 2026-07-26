# Tracer Bullet Brief — `TB-2026-07-26-mobile-installation-scoped-seal-key` Installation-scoped app seal key

## Metadata

- **ID:** `TB-2026-07-26-mobile-installation-scoped-seal-key`
- **Status:** `done`
- **Related idea:** `I-2026-07-26-mobile-installation-scoped-seal-key`
- **Lane:** `D`
- **Posture:** `single-pass`
- **GitHub issue:** _(none — hygiene lane, see `product-docs/methodology/github-issues-workflow.md` for when a GitHub issue helps)_
- **Created at:** `2026-07-26`
- **Updated at:** `2026-07-26`
- **Captured by:** Marc (Grill Me MOB-017, 2026-07-26)
- **Absorbs findings:** MOB-017

## Objective

Replace the single app-wide AES seal key (`ezkey_app_seal_v1`) with one AES seal key per
installation trust zone, so at-rest protection of `enrollmentProofToken` and `integrationPublicKey`
follows the same installation-scoped isolation boundary as device signing keys and local identity
(MOB-011). Local storage hardening only — no signing-key change, no Auth API/OpenAPI change.

## Boundaries in scope

- `EzkeyCryptoModule.kt` — new alias derivation for the seal key, `installationId` param threaded
  through `sealSecret` / `unsealSecret` / `deleteAppSealKey` (and `getOrCreateAppSealKey` /
  `createAppSealKey` renamed or parameterized accordingly)
- `nativeCrypto.ts` — bridge signature updates
- `secureStorage.ts` — delegate signature updates (key derived per installation, not app-global)
- `enrollmentStorage.ts` — thread `installationId` through `saveEnrollment`, `replaceAll`,
  `deleteEnrollment`, `clearAll`
- Unit tests (JS) + instrumented Keystore test extension (Kotlin `androidTest`)
- Docs: `MOBILE_CRYPTO_REFERENCE.md`, `MOBILE_DATA_MODEL.md`, `NATIVE_MODULES.md`; new
  `ADR-MOB-0006`

## Out of scope

- Device signing key generation/derivation (`ezkey_enrollment_{localId}`) — unaffected, already
  correctly per-enrollment and installation-scoped
- Auth API / OpenAPI contract changes
- Migration/re-seal path from the old shared `ezkey_app_seal_v1` (greenfield cutover — no
  production fleet)
- New "remove this installation" bulk UX action
- iOS parity as a release gate

## First executable slice

1. Add a Keystore-safe alias derivation for the seal key from `installationId` (mirror
   `getEnrollmentAlias` encoding safety — reuse the same normalization/hash approach as
   `deriveLocalEnrollmentId` if a raw URL-derived id is unsafe as an alias).
2. Parameterize `EzkeyCryptoModule` seal/unseal/delete-seal-key methods by `installationId`; keep
   `StrongBox`-when-available + AES-256-GCM unchanged.
3. Update `nativeCrypto.ts` bridge signatures and `secureStorage.ts` to accept/forward
   `installationId`.
4. Update `enrollmentStorage.ts` choke points to pass `record.installation.id` (already available on
   every `StoredEnrollment`) into the storage delegate calls.
5. Greenfield cutover: no unseal-with-old-key/reseal-with-new-key logic; old `ezkey_app_seal_v1`
   references are removed, `clearAll()` sweeps whatever per-installation seal keys exist on the
   device.
6. Tests: two installations sealing/unsealing independently; deleting one installation's enrollments
   does not affect the other's sealed secrets; `clearAll()` still leaves no orphaned seal-key alias.
7. Update docs (`MOBILE_CRYPTO_REFERENCE.md` §"Secure Storage vs Android Keystore / StrongBox" and
   the Android Summary section; `MOBILE_DATA_MODEL.md` §"Enrollment Cryptographic Material and
   Secure-Storage Split"; `NATIVE_MODULES.md` seal-key lifecycle section) and add `ADR-MOB-0006`
   recording the revised alternative.
8. Close MOB-017 disposition in the assessment doc and pass-3 campaign note.

## Rollback or fallback posture

- Outside production: prefer fix-forward on the feature branch; `clearAll()` (Danger Zone) resets
  any dirty dev device state.
- If installation-id-derived aliasing proves unsafe or collision-prone, fall back to a hashed
  installation id (same technique already used in `deriveLocalEnrollmentId`) rather than reverting
  to the single app-wide key.

## Critical flows

- **Nominal:** two installations on one device each seal/unseal `enrollmentProofToken` and
  `integrationPublicKey` under their own installation-scoped AES key; neither installation's
  unsealing depends on or is affected by the other's key.
- **Exception:** `clearAll()` (Danger Zone) removes all installations' seal keys and sealed secrets
  in one true local reset — no orphaned per-installation seal key survives a full wipe.

## Evidence plan

| Layer | Required | Result |
| --- | --- | --- |
| Unit | Two-installation seal/unseal isolation; `clearAll` sweeps all per-installation seal keys | **Done** — `secureStorage.test.ts`, `enrollmentStorage.test.ts`, `localEnrollmentIdentity.test.ts`; full suite 225/225 passing |
| Instrumentation | Distinct Keystore AES aliases for two installation ids (extends MOB-006 instrumented suite) | **Done** — `sealSecret_isIsolatedPerInstallationScope_MOB017`, `deleteAllSealKeys_removesEveryInstallationSealAlias_MOB017`; 7/7 passing on physical Pixel 7 Pro |
| Docs | Crypto reference, data model, native modules doc, new ADR-MOB-0006 | **Done** |
| Traceability | Pass-3 campaign decision → Fixed for MOB-017; assessment §15 disposition updated | **Done** |

## Quality gates

- **Analysis gate:** Grill Me locked (2026-07-26); greenfield cutover posture confirmed.
- **Design gate:** No Auth API/OpenAPI change; signing-key path untouched; alias derivation is
  Keystore-safe.
- **Implementation gate:** Mobile validate/tests green; instrumented Keystore test extension where
  feasible on emulator/device. **Met** — unit 225/225, Android instrumented 7/7 on physical device.

## Exit criteria

1. Each installation's secrets are sealed and unsealed under a distinct AES Keystore key. **Met.**
2. Deleting or clearing one installation's enrollments does not corrupt or expose another
   installation's sealed secrets. **Met.**
3. `clearAll()` still performs a true local reset with no orphaned seal-key alias. **Met** —
   `deleteAllSealKeys` sweeps every `ezkey_seal_*` alias.
4. Device signing key generation/derivation is unchanged (regression check). **Met** — unaffected
   by this change; `generateGetPublicKeyAndSign_roundTrip` and `deleteKeyPair_removesEnrollmentAlias`
   pass unchanged.
5. MOB-017 disposition updated to **Fixed**; pass-3 campaign closed. **Met.**

## Residual risks

- No migration path means any dev/test device with secrets sealed under the old
  `ezkey_app_seal_v1` must be cleared and re-enrolled after this change lands — acceptable per the
  greenfield cutover decision (no production fleet).
- Future "remove this installation" bulk action (not in scope here) will need its own seal-key
  deletion wiring when it is eventually designed.

## Links

- Idea: [`ideas/I-2026-07-26-mobile-installation-scoped-seal-key.md`](ideas/I-2026-07-26-mobile-installation-scoped-seal-key.md)
- Campaign: [`../hygiene/mobile-protocol-security/2026-07-26-pass-3.md`](../hygiene/mobile-protocol-security/2026-07-26-pass-3.md)
- Assessment: [`../../../docs/security/mobile-protocol-crypto-assessment-2026-07.md`](../../../docs/security/mobile-protocol-crypto-assessment-2026-07.md) §15 MOB-017
- Prior precedent (same pattern, larger scope): [`TB-2026-07-20-mobile-installation-scoped-enrollment-identity.md`](TB-2026-07-20-mobile-installation-scoped-enrollment-identity.md)
