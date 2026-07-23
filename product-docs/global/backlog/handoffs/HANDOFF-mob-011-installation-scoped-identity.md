# Handoff — MOB-011 installation-scoped local enrollment identity

**Status:** `closed` — Grill Me + analysis complete (2026-07-20); backlog promoted  
**Lane:** Mobile protocol security hygiene (pass-2) → **program** (two sequential activities)  
**Finding:** MOB-011 (P1, Confirmed) — disposition **defer (program)**  
**Campaign:** [`product-docs/global/hygiene/mobile-protocol-security/2026-07-19-pass-2.md`](../../hygiene/mobile-protocol-security/2026-07-19-pass-2.md)  
**Assessment register:** [`docs/security/mobile-protocol-crypto-assessment-2026-07.md`](../../../../docs/security/mobile-protocol-crypto-assessment-2026-07.md) §14.2 MOB-011

## Backlog (canonical next)

1. [`I-2026-07-20-mobile-installation-trust-zone-canon`](../ideas/I-2026-07-20-mobile-installation-trust-zone-canon.md) /
   [`TB-2026-07-20-mobile-installation-trust-zone-canon`](../TB-2026-07-20-mobile-installation-trust-zone-canon.md)
2. [`I-2026-07-20-mobile-installation-scoped-enrollment-identity`](../ideas/I-2026-07-20-mobile-installation-scoped-enrollment-identity.md) /
   [`TB-2026-07-20-mobile-installation-scoped-enrollment-identity`](../TB-2026-07-20-mobile-installation-scoped-enrollment-identity.md)
   (absorbs MOB-013 + MOB-016)

Do **not** implement remediations from this handoff file; execute via the TBs above.

---

## One-sentence problem

The enrollment wizard builds local crypto and storage identity from the server’s numeric `enrollmentId` alone; `installation` is attached afterward for UI/routing but **does not scope** Keystore aliases, sealed-secret keys, collection overwrite, or React Query / wipe keys — so two independent Ezkey installations can collide on the same phone.

---

## Scenario that led to the observation (preserve this narrative)

This was **not** a remote exploit. It came from white-box re-reading of the post–Lot A Android enrollment path during Mobile Crypto Assessment Pass-2, confronting the product’s already-shipped **multi-installation UX** (Home groups by installation; pending/respond route via `installation.authUrl`).

Concrete collision scenario:

1. User enrolls on **installation A** (`https://auth-a…`). Server A allocates `enrollment_id = 1` (DB identity, local to A).
2. Wizard sets `StoredEnrollment.id = "1"`, creates Android Keystore alias `ezkey_enrollment_1`, seals secrets under `ezkey-mobile/enrollment-proof-token.1` (and integration public key likewise).
3. On the **same phone**, user enrolls on **installation B** (`https://auth-b…`). Server B independently allocates `enrollment_id = 1`.
4. Wizard B again uses `id = "1"`:
   - Keystore `containsAlias("ezkey_enrollment_1")` → **reuses A’s EC private key** (no new key pair).
   - `saveEnrollment` filters `item.id !== record.id` → **drops A’s metadata row** and overwrites sealed secrets for id `1`.
5. Independent trust zones that the UI presents as separate installations are no longer isolated in crypto/storage.

**Observation label used in HITL:** *Wizard enrollment is not installation-scoped.*

**Non-claim:** this is **not** a remote MFA bypass of an honest enrolled device talking to a single Auth API over ordinary TLS. It is a **local multi-installation isolation** failure.

---

## Grill Me decisions (2026-07-20) — summary

| Topic | Decision |
| --- | --- |
| Product claim | Multi-install end-to-end including crypto |
| Installation identity | Normalized Auth URL only (no installation UUID) |
| Model posture | Enrollment belongs to installation trust zone |
| Crypto | No special per-zone crypto layer; handles must share uniqueness with the model |
| Draft timing | **7A** — scoped local identity from `buildDraft`; Keystore in finalize |
| Shape | **O3** preferred; O4 acceptable |
| Sequencing | Canon TB → identity/crypto TB (absorbs MOB-013/016) |
| Disposition | **defer (program)**; outside production → full first-principles design |

---

## Evidence map (historical — still valid)

### Backend — enrollment IDs are installation-local

- `ezkey-core/.../enrollment/domain/entity/Enrollment.java` — `@GeneratedValue(strategy = GenerationType.IDENTITY)` on `enrollment_id`.

### Wizard — identity fixed too early / too narrow

- `ezkey_mobile/app/hooks/useEnrollmentWizard.ts`
  - `buildDraft` — `id: String(rawId)` from bind response / request only (no `authUrl` / installation component).
  - `finalizeEnrollment` — `ensureEnrollmentKeyPair(draft.id)` then later `buildInstallation(...)` and `saveEnrollment` with `id: draft.id` + nested `installation`.

### Installation exists but is presentation/routing metadata

- `ezkey_mobile/app/utils/installationMetadata.ts` — `buildInstallation` / `normalizeInstallationId(authUrl)`.
- `ezkey_mobile/docs/MOBILE_DATA_MODEL.md` — states installation is nested in enrollment; `StoredEnrollment.id` is the main local identifier; installation identity is normalized Auth URL. **Design tension:** docs treat installation as subordinate to enrollment lifecycle; crypto keys currently follow enrollment id only.

### Storage — keys and overwrite by `record.id` only

- `ezkey_mobile/app/services/storage/enrollmentStorage.ts`
  - `proofTokenStorageKey(id)` → `ezkey-mobile/enrollment-proof-token.{id}`
  - `integrationPublicKeyStorageKey(id)` → `ezkey-mobile/integration-public-key.{id}`
  - `saveEnrollment` — `filter(item => item.id !== record.id)` then concat.

### Native Keystore — alias and silent reuse

- `ezkey_mobile/android/.../crypto/EzkeyCryptoModule.kt`
  - `getEnrollmentAlias` → `ezkey_enrollment_$enrollmentId`
  - `generateEnrollmentKeyPair` — if `containsAlias(alias)` → resolve `true` and return (no ownership / installation check).

### Related pass-2 findings

- **MOB-013** / **MOB-016** — absorbed into activity 2 program TB.
- **MOB-012** — remains a separate hygiene handoff.
