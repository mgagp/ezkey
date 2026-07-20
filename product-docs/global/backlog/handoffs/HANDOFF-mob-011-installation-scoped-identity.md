# Handoff — MOB-011 installation-scoped local enrollment identity

**Status:** `open` — analysis / design only (2026-07-19)  
**Lane:** Mobile protocol security hygiene (pass-2) → possible **program** promotion after Grill Me  
**Finding:** MOB-011 (P1, Confirmed)  
**Campaign:** [`product-docs/global/hygiene/mobile-protocol-security/2026-07-19-pass-2.md`](../../hygiene/mobile-protocol-security/2026-07-19-pass-2.md)  
**Assessment register:** [`docs/security/mobile-protocol-crypto-assessment-2026-07.md`](../../../../docs/security/mobile-protocol-crypto-assessment-2026-07.md) §14.2 MOB-011

Use this prompt to start a **new Cursor session** for a **mini Grill Me + complementary analysis/design** on MOB-011.  
Do **not** implement remediations in that session unless the operator explicitly changes scope after decisions are recorded.

---

## Operator intent for this session

1. Read this handoff and the assessment finding.
2. Run a **mini Grill Me** (skill `.cursor/skills/grill-me/SKILL.md`) on the observation and on candidate identity models.
3. Produce a short **analysis-design note** (options, migration pressure, fail-open/fail-closed, what stays hygiene vs what becomes `I-*` / `TB-*`).
4. Return a clear recommendation for the pass-2 HITL decision on MOB-011: **fix** (hygiene-sized) / **defer** (program) / **suppress** / **skip** — with rationale.
5. Do **not** create `I-*` / `TB-*` / GitHub issue unless the operator explicitly asks after Grill Me.

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

## Evidence map (read these first)

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

### Related pass-2 findings (do not “fix” here; note coupling)

- **MOB-013** — pending/respond `ensureEnrollmentKeyPair` can silently generate or attach the wrong alias under collision.
- **MOB-016** — orphan keys after failed verify stick harder when aliases are enrollment-id-only.
- **MOB-012** — platform key loss interacts badly with non-scoped aliases (misdiagnosis risk).

---

## Product / methodology constraints

- Pass-2 is **Lane D hygiene**. Do not auto-spawn one `I-*`/`TB-*` per MOB row.
- MOB-011 is explicitly called out in the campaign note as a **candidate for program promotion** (installation-scoped identity migration).
- Keep numeric `enrollmentId` in Auth API request/response bodies — servers do not know a mobile composite id.
- Sealed-secret AAD / logical key names and Keystore aliases are a **migration** surface if identity format changes; call this out in Grill Me.
- iOS parity is deferred unless the design note needs a one-line future stub.
- No OpenAPI / Auth API contract change is required for a pure local-identity scoping fix (confirm or refute in analysis).
- No commit/PR unless the operator asks after HITL.

---

## Mini Grill Me — pressure questions (start here)

Adapt from `.cursor/skills/grill-me/SKILL.md`. Keep answers concrete.

1. What must remain true for multi-installation on one phone to be a supported product claim?
2. Is `StoredEnrollment.id === String(serverEnrollmentId)` an accidental leak of DB identity into local crypto namespace, or an intentional simplification that should be retired?
3. Should local identity be `(installationId, enrollmentId)`, a single composite string, or a mobile-generated UUID with server id kept as a field only?
4. Where must the composite appear (Keystore alias, seal logical keys / AAD, AsyncStorage collection id, React Query keys, navigation params, wipe paths) — and where must the raw server id remain?
5. What happens on upgrade for existing installs that already have `ezkey_enrollment_1` and `…proof-token.1`?
6. Fail-open vs fail-closed: if two records would collide under the old scheme, should save refuse, migrate, or overwrite with loud error?
7. Does `buildDraft` need installation scoping at bind time (before verify), or only at persist — and what does early Keystore create imply?
8. How does this interact with MOB-013 (`ensure` on pending/respond) and MOB-016 (orphans)?
9. Minimal evidence that would **disprove** that this is a real product risk (e.g. product explicitly forbids multi-install on one device)?
10. Hygiene-sized first slice vs full program: what is the smallest reversible change that stops new collisions without a complete storage rewrite?

Record decision pressure points and open questions before proposing a preferred model.

---

## Expected deliverables from the analysis session

1. **Short design options matrix** (2–4 options) with blast radius and migration cost.
2. **Recommended next disposition** for pass-2 HITL on MOB-011.
3. If program: draft outline only for a future `I-*` (title + problem + non-goals) — do not create the file unless asked.
4. If hygiene fix: bounded slice description (files, tests, migration stance) — do not implement unless asked.
5. Update suggestion (text only is fine) for the campaign note § MOB-011 rationale after operator decides.

Optional durable write location if the operator wants a retained working note in-repo:

- Prefer appending under the campaign note rationale, or a short note linked from the handoff — avoid half-linked plans outside the clone.

---

## Suggested first agent turns

1. Read: this handoff, assessment §14.2 MOB-011, `MOBILE_DATA_MODEL.md` (Installation + StoredEnrollment), `useEnrollmentWizard.ts` (`buildDraft` / `finalizeEnrollment`), `enrollmentStorage.ts` (`saveEnrollment` + key helpers), `EzkeyCryptoModule.kt` (`getEnrollmentAlias` / `containsAlias` short-circuit).
2. Run mini Grill Me with the operator (one pressure cluster at a time if they prefer HITL style).
3. Converge on options + recommendation; stop before coding.

---

## Out of scope for this handoff session

- Implementing Keystore/storage migration
- Fixing MOB-012 / MOB-013 / MOB-014 / MOB-015 / MOB-016 in the same change set
- Active exploit / MITM lab
- Reopening closed Lot A / Lot B findings
- Auth API / OpenAPI changes (unless analysis proves they are required — unexpected)

---

## Paste-ready starter message (for the other agent)

```text
Read product-docs/global/backlog/handoffs/HANDOFF-mob-011-installation-scoped-identity.md end-to-end.
Then read docs/security/mobile-protocol-crypto-assessment-2026-07.md §14.2 MOB-011 and the evidence files listed in the handoff.

Task: complementary analysis-design + mini Grill Me on MOB-011 (wizard enrollment identity is not installation-scoped). Preserve the collision scenario in the handoff. Do not implement code. Do not create I-*/TB-* unless I explicitly ask after we finish Grill Me.

Start Grill Me with questions 1–3 from the handoff, one cluster at a time, and wait for my answers before proposing a preferred identity model.
```
