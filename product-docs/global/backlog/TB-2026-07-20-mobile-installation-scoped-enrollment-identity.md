# Tracer Bullet Brief — `TB-2026-07-20-mobile-installation-scoped-enrollment-identity` Scoped enrollment identity + crypto handles

## Metadata

- **ID:** `TB-2026-07-20-mobile-installation-scoped-enrollment-identity`
- **Status:** `done`
- **Related idea:** `I-2026-07-20-mobile-installation-scoped-enrollment-identity`
- **Lane:** `D`
- **Posture:** `single-pass`
- **Prerequisite TB:** `TB-2026-07-20-mobile-installation-trust-zone-canon` (must exit first) — **done**
- **GitHub issue:** _(none — canon sufficient)_
- **Created at:** `2026-07-20`
- **Updated at:** `2026-07-23`
- **Closed at:** `2026-07-23`
- **Captured by:** Marc (Grill Me MOB-011, 2026-07-20)
- **Absorbs findings:** MOB-011, MOB-013, MOB-016

## Progress / closeout (2026-07-23)

Implemented and merged via PR [#401](https://github.com/mgagp/ezkey/pull/401) (`feature/mobile-installation-trust-zone`):

- `deriveLocalEnrollmentId` / `resolveServerEnrollmentId` (O3 Keystore-safe handles)
- Wizard `buildDraft` (7A) + finalize uses local id for Keystore; server id for Auth API; orphan delete on verify/save failure (MOB-016)
- Pending/respond `requireEnrollmentKeyPair` (MOB-013 — no silent generate)
- Unit tests: identity, storage A/B collision, claim missing-key
- Operator Pixel clean-install + multi-install enroll smoke — **passed** (2026-07-23)

**Deferred:** dedicated Android instrumentation asserting distinct Keystore aliases — optional; covered by unit collision tests + device smoke. Revisit only if a Keystore-alias encoding regression is suspected.

## Objective

Implement installation-scoped local enrollment identity and crypto/storage handles as a **complete**
design (not a minimal patch): draft-time scoped identity (7A), Keystore/seal uniqueness, fail-closed
save semantics, no silent pending/respond ensure (MOB-013), orphan cleanup after failed verify
(MOB-016). Prove the A/B `enrollment_id = 1` collision cannot destroy isolation.

## Boundaries in scope

- Enrollment wizard (`buildDraft` / `finalizeEnrollment`)
- `enrollmentStorage` collection id, seal logical keys, wipe/delete
- Android `EzkeyCryptoModule` alias derivation and generate/ensure/delete paths used by enrollment
- Pending/respond paths that call `ensureEnrollmentKeyPair`
- React Query / navigation keys that key off local enrollment id
- Unit + instrumentation evidence for distinct aliases across two installations

## Out of scope

- Trust-zone canon TB work (prerequisite)
- Auth API / OpenAPI / server `enrollment_id` semantics
- MOB-012 unlocked-device gate
- Per-installation StrongBox policy abstraction
- Production fleet migration
- iOS parity gate

## First executable slice

1. Introduce local identity derivation (O3 preferred: deterministic safe handles from
   `installationId` + `serverEnrollmentId`; keep numeric server id for Auth API bodies).
2. Apply that identity from `buildDraft` through finalize, storage, Keystore, query, wipe.
3. Replace silent pending/respond ensure with fail-closed “key must already exist” (MOB-013).
4. On verify/save failure after key create, delete orphan alias (MOB-016).
5. Tests: collision scenario A/B both server id `1`; distinct aliases and both metadata rows retained.
6. Update mobile docs for the new identity contract; close assessment dispositions for
   MOB-011/013/016 when validated.

## Rollback or fallback posture

- Outside production: prefer fix-forward on the feature branch; app clear-all for dirty dev devices.
- If alias encoding proves unsafe, switch to O4 opaque local UUID while keeping the same uniqueness
  invariant — do not revert to server-id-only handles.

## Critical flows

- **Nominal:** Enroll on A then B with both servers returning `enrollmentId = 1` → two local records,
  two Keystore aliases, two seal keys; pending/respond on each uses the correct key.
- **Exception:** Verify fails after key create → orphan alias removed; pending/respond never
  silently generates a replacement key for a verified enrollment.

## Evidence plan

| Layer | Required | Result |
| --- | --- | --- |
| Unit | Collision save/list/delete; draft identity; ensure-no-silent-generate | Done |
| Instrumentation | Distinct Keystore aliases for two installation ids | Deferred (optional) |
| Manual | Two-installation enroll on device | Done (Pixel smoke, operator) |
| Docs | Data model + crypto/storage notes; assessment dispositions | Done at closeout |
| Traceability | Campaign pass-2 decisions → Fixed for MOB-011/013/016 | Done at closeout |

## Quality gates

- **Analysis gate:** Grill Me locked; prerequisite TB exited.
- **Design gate:** No OpenAPI change; installation remains normalized URL; no half-scoped handles.
- **Implementation gate:** Mobile validate/tests + device smoke; fail-closed on identity ambiguity.

## Exit criteria

1. A/B collision scenario cannot reuse key, overwrite seals, or drop the other zone’s row. — **met**
2. Auth API requests still send numeric `enrollmentId`. — **met**
3. Pending/respond do not silently create enrollment keys. — **met**
4. Failed verify/save does not leave sticky orphans without cleanup. — **met**
5. Assessment MOB-011 / MOB-013 / MOB-016 dispositions updated; pass-2 campaign rationale closed for
   those rows. — **met** (2026-07-23)

## Residual risks

- MOB-012 (unlocked-device gate) can still destroy Keystore material on Android 12–14; fail-closed
  pending/respond (MOB-013 fix) surfaces that as missing key rather than silent regen.
- MOB-015 still collapses some storage failures to empty list — separate hygiene handoff.

## Links

- Idea: [`ideas/I-2026-07-20-mobile-installation-scoped-enrollment-identity.md`](ideas/I-2026-07-20-mobile-installation-scoped-enrollment-identity.md)
- Prerequisite TB: [`TB-2026-07-20-mobile-installation-trust-zone-canon.md`](TB-2026-07-20-mobile-installation-trust-zone-canon.md)
- Campaign: [`../hygiene/mobile-protocol-security/2026-07-19-pass-2.md`](../hygiene/mobile-protocol-security/2026-07-19-pass-2.md)
- PR: [#401](https://github.com/mgagp/ezkey/pull/401)
