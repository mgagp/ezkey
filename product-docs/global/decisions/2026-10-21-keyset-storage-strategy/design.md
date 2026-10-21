# Keyset storage strategy — analysis and detailed design

**Pack:** [`README.md`](README.md) · **Issue:** [#952](https://github.com/mgagp/ezkey/issues/952) · **Draft ADR:** [`adr-0016-draft.md`](adr-0016-draft.md) · **Community upgrade:** [`community-upgrade-impact.md`](community-upgrade-impact.md)

**Base:** `main` @ characterization PR head. Line numbers refer to that tree unless noted.

**Adopted direction (Marc, #952):** DB blob is the single source of truth; FILE and HYBRID removed; crypto-api gets a read-only DataSource (local lab only); online rotation kept (DB-CAS-first, fail closed). Invariants **4–8** carry forward from the #936 hardening line; invariants **1–3** are a **new CAS protocol inspired by #936**, not a literal port (see §2.3 / §3). Deferred to [#953](https://github.com/mgagp/ezkey/issues/953): encrypted off-host backup, KMS, RDS, secret separation, HA production.

**Cutover sequencing:** decrypt fail-closed (§2.7) and the new CAS protocol (§2.3) land in the **same cutover PR**. The [#942](https://github.com/mgagp/ezkey/issues/942) `required=false` property purge and the explicit CLI import/export are **adjacent thin PRs**, not folded into the cutover.

---

## 1. Current behavior (status quo on `main`)

### 1.1 Storage modes and defaults

| Mode | Meaning (today) | Evidence |
|------|-----------------|----------|
| `FILE` | Keyset only on disk | `TinkProperties.java:393-404`, enum `:492-500` |
| `DATABASE` | DB source of truth, **file still used as cache** | same; default `storageMode = DATABASE` at `:404` |
| `HYBRID` | Both; DB preferred for reads; file also re-checked | enum `:499-500`; **no production profile** |

| Surface | Mode | Evidence |
|---------|------|----------|
| Admin / Auth / Integration docker | `DATABASE` | e.g. `ezkey-admin-api/config/application-docker.properties:194` |
| Community ezkey.online | `DATABASE` via `docker` profile | `experimental-hybrid/lightsail/docker-compose.yml` (`SPRING_PROFILES_ACTIVE` / volume `encryption-secrets:/etc/ezkey`) |
| HA compose | `DATABASE` + **shared file volume** | `docker/docker-compose.ha.yml:126,176` (`encryption-secrets-ha:/etc/ezkey`) |
| Windows admin/auth | `FILE` | `application-windows.properties` (admin ~`:167`, auth ~`:101`) |
| crypto-api | `FILE`, **no DataSource** | `ezkey-crypto-api/config/application.properties:36,68-69` |
| HYBRID | tests only | `TinkKeyManagerConcurrencyTest.java:118,492` |

### 1.2 Startup and bootstrap (`TinkKeyManager.initialize`)

Entry: `TinkKeyManager.java:150-238`.

1. Load `master.key` → master AEAD (`:165-169`).
2. If `DATABASE`/`HYBRID` and repository present: try `loadKeysetFromDatabase()` (`:181-201`).
3. Non-writer miss → `awaitDatabaseKeyset()` poll until `bootstrap-wait` (default 90s) (`:245-275`).
4. Still missing:
   - non-writer `FILE` → `loadExistingKeysetFileOnly` (`:283-307`);
   - non-writer DB → warn, do not generate (`:207-210`);
   - writer → `materializeWriterKeyset` (`:315-382`).
5. `enforceRequiredEncryption()` (`:509-523`): if `required=true` and not initialized → `IllegalStateException`. If `required=false`, init failures are swallowed (`:221-235`) and the process continues **without** encryption.

**Writer file↔blob paths (root of #936 B2/B5/B6):**

| Step | Behavior | Lines |
|------|----------|-------|
| Existing file | Load file, then `saveKeysetToDatabase("STARTUP_FILE_SYNC")` | `:331-349` |
| Empty file, blob present | Load blob, `persistHandleToKeysetFile` | `:353-358` |
| Neither | Generate file, `claimOrAdoptGeneratedKeyset` | `:361-381`, `:399-421` |
| Legacy unloadable blob | **Overwrite** via `saveKeysetToDatabase` after failed load | `:392-393`, `:419-420` |

`clearKeysetHandleFailClosed` (B6 file deletion) is **not on `main`** (landed only on the paused #936 branch). Characterization therefore pins that the runtime does not delete the keyset file on the paths exercised today.

### 1.3 Online sync and rotation

| Concern | Current behavior | Lines |
|---------|------------------|-------|
| Version tick | Async check every **5 s** from `getAeadPrimitive` | `DATABASE_CHECK_INTERVAL_MS` `:97`; schedule `:573-597` |
| DB reload | If `findVersion() > databaseKeysetVersion`, reload under write lock | `:599-657` |
| File reload | `FILE`/`HYBRID`: if `lastModified` advanced, reload file | `:618-683` |
| Rotate / add / promote | Write lock → mutate handle → save file → `saveKeysetToDatabase` | `:962-1187` |
| DB save | Writer-only; `repository.save` with entity `@Version` | `:1355-1410`; `KeysetBlob.java` `@Version` |
| First-boot claim | `insertSingletonIfAbsent` (`ON CONFLICT DO NOTHING`); loser reloads peer | `KeysetBlobRepository` + `:399-417` |

**Gap vs target invariants:** save path updates in-memory version from the saved entity (`:1401`) but does **not** yet implement an explicit “CAS then install only after successful CAS / loser discards and reloads” protocol for concurrent rotation writers. Hibernate `@Version` can throw on conflict; current `rotateKey` catches DB save failures and **logs** without rolling back the in-memory rotated handle (`:1017-1025`) — a mutation-probed gap for the refactor.

### 1.4 Decrypt fail-open

`EncryptionService.decrypt` (`EncryptionService.java:172-320`):

- On format/Base64/`GeneralSecurityException`/`IllegalStateException` failure after optional reload, **returns the original encrypted string** (`:167-170`, `:212-217`, `:320`).
- Contrast: persist path is fail-closed when `required=true` via `AtRestEncryptionAccess` (`AtRestEncryptionAccess.java:41-75`).

Christophe Q1: no PII under the keyset; fail-open on decrypt is a separate integrity issue (caller may treat ciphertext as plaintext).

### 1.5 Logging and file permissions

| Topic | Current | Lines / docs |
|-------|---------|--------------|
| Logs | Primary key IDs (kids), versions, paths — not cleartext key material | e.g. `TinkKeyManager.java:1340-1346`, `EncryptionService.java:199,244-246` |
| `master.key` POSIX | Owner read only; skip check on Windows | `TinkKeyManager.java:808-838` |
| Keyset file mode | `chmod 600` on non-Windows write | `:913-919` (approx.; `saveKeyset` path) |
| `audit-hmac.key` | Documented `chmod 600` | `docs/AUDIT_LOG_INTEGRITY.md:72-73` |

### 1.6 crypto-api today

- FILE keyset under `/etc/ezkey`; DataSource autoconfig **excluded** (`application.properties:36`).
- Role matrix: “Crypto API has **no** datasource and **no** role” (`docs/DATABASE_ROLE_PERMISSIONS_MATRIX.md:29`).
- Only `/encrypt` and `/decrypt` need the keyset; bootstrap-init depends on crypto-api health for keypair/sign only.

### 1.7 What the keyset protects (Christophe Q1)

Encrypted columns via `EncryptionEntityListener` / `EncryptionService`:

- `Enrollment.integrationPrivateKey`, `enrollmentProofToken`
- `AuthAttempt.authAttemptProofToken`
- `ApiKey.secretKeyHash` (BCrypt hash, additionally encrypted)

**PII is plaintext** in DB (contacts, admin names/emails, audit IP/UA, free-text auth context). Keyset loss → availability / re-enrollment, not PII confidentiality. Docs must say this honestly (`SECURITY_POSTURE`).

---

## 2. Target design

### 2.1 Single source of truth

- Runtime authority: **only** `ezkey_keyset_blob` (Tink encrypted-keyset JSON envelope, ADR-0011).
- `master.key` remains a file/secret (envelope key). No keyset file is read, written, or deleted by API processes.
- Explicit CLI import/export is an **adjacent thin PR** after cutover (recovery tool only); **never** re-introduced as a silent cache. Invariant 7 (blob-only): see §3.

### 2.2 Modes and flags removed

Remove `StorageMode.FILE`, `StorageMode.HYBRID`, properties `keyset-file` (runtime), `degraded-mode-enabled` as a dual-path crutch, file version-check branches, `STARTUP_FILE_SYNC`, file backups in `TinkKeyManager`, and Windows FILE profiles.

**HA volume:** keep `encryption-secrets-ha` (and community `encryption-secrets`) **only** for `master.key` and `audit-hmac.key`. The keyset file path on that volume is no longer a sync or authority mechanism.

**Windows cutover checklist** (`application-windows.properties` today: admin `:167-169`, auth `:101-102`):

| Property | Admin | Auth |
|----------|-------|------|
| `ezkey.encryption.keyset.storage-mode` | `DATABASE` | `DATABASE` |
| `ezkey.encryption.keyset.writer` | `true` | `false` |
| `ezkey.encryption.keyset.degraded-mode-enabled` | `false` | `false` |
| `ezkey.encryption.required` | `true` | `true` |

Postgres is already required for the app on Windows; FILE mode ends with this cutover.

**crypto-api:** see §2.5.

### 2.3 Online rotation via CAS on version

**Invariants 1–3 are a NEW CAS protocol inspired by #936**, not a port of the paused PR’s file↔blob machinery. Probes for 1–3 must **fail if the in-memory install happens before a successful CAS**.

Target protocol:

1. Under write lock (or equivalent exclusive mutation section): **re-read** blob version (invariant 1).
2. Mutate candidate keyset in a **staging** handle / serialized envelope — do **not** publish as the live primary yet.
3. **CAS** persist: update row only if version matches expected; **only after** successful CAS install in-memory handle and version (invariant 2). A probe that installs before CAS must fail.
4. On CAS loss: **discard** candidate, reload blob, do not keep orphan local kids (invariant 3).
5. Periodic tick remains **shorter than any sync window** operators rely on (keep ≤ 5 s unless redesign justifies change) (invariant 4 — carried from #936).
6. Unknown kid on decrypt → **fail closed** (invariant 5) — see §2.7.
7. DB errors during load/save → **never** generate a new keyset (invariant 6).
8. Legacy / unloadable envelope → **fail startup** with clear error; **no overwrite** (invariant 8).

**Today’s gap (characterized):** `rotateKey` installs the rotated handle at `TinkKeyManager.java:1007` then attempts DB save `:1014-1025`; on save failure it only logs — orphan in-memory primary remains. See `rotateKey_whenDbSaveFails_keepsOrphanInMemoryPrimary` (EXPECTED TO CHANGE).

Admin remains sole writer (`keyset.writer=true`, ADR-0012).

### 2.4 Startup and bootstrap ordering

| Role | Boot sequence |
|------|----------------|
| Admin writer | Load master → load blob if present → if absent, generate once and `insertSingletonIfAbsent` → on claim loss, adopt peer → never invent a second keyset after a DB error |
| Auth / Integration / crypto-api (non-writer) | Load master → poll blob up to `bootstrap-wait` → on success, install → on timeout or unloadable blob, **fail closed** |

Ordering vs existing `ApplicationReadyStartupOrder.KEYSET_SYNC`: keep Admin keyset materialization before MFA bootstrap; peripherals wait for blob (ADR-0012).

### 2.5 crypto-api — read-only DataSource (local lab only)

| Decision | Detail |
|----------|--------|
| Scope | Local clean-start / lab stacks only. **Not** in online/community compose. |
| Storage | `DATABASE`, `writer=false` |
| DataSource | Enable Spring DataSource; Flyway stays off |
| DB role | `ezkey_crypto` with **SELECT only** on `ezkey_keyset_blob` (deny elsewhere), proven by `verify-grants.sh` |
| AEAD recovery | On decrypt AEAD failure: **reload the blob** (force version check / `forceReloadFromDatabase`) and retry. **Do not** consult `ezkey_encryption_key` metadata — no GRANT on that table. |
| Wait | Bounded `bootstrap-wait`, then **explicit failure** |
| `required` | **Never** `required=false` for crypto-api (see [#942](https://github.com/mgagp/ezkey/issues/942)). Prefer process fail-closed when encryption cannot initialize; health-split remains a product dial (§8 Q1). |
| Secrets | Still needs `master.key`; adds DB credentials for SELECT-only role. Acceptable under Marc’s lab proportionality. |
| experimental-hybrid local | Do **not** open community Postgres to the internet; run crypto-api on the VM only (recommended — §8 Q8). |

### 2.6 SELECT-only role

- New LOGIN role `ezkey_crypto`: `SELECT` on `ezkey_keyset_blob` **only** (decided — no `ezkey_encryption_key`).
- Grants in `01-create-roles.sh` / `scripts/db/apply-grants.sql`; denials asserted in `verify-grants.sh`.
- Update `docs/DATABASE_ROLE_PERMISSIONS_MATRIX.md`.
- Application `writer=false` is defense in depth; **DB GRANT is the real barrier**.

### 2.7 Fix fail-open in `EncryptionService.decrypt`

Target: decrypt failures (unknown kid, AEAD failure after reload budget, corruption) **throw** or return a typed failure that callers treat as fail-closed — never return the ciphertext as if it were plaintext.

Lands in the **cutover PR** (same batch as CAS). Coordinate with `AtRestEncryptionAccess`. The [#942](https://github.com/mgagp/ezkey/issues/942) property purge remains an adjacent thin PR.

### 2.8 Logs carry kids only

Continue (and audit) that logs may include unsigned/signed key IDs and blob version, never raw keyset bytes, master key material, or ciphertext dumps. Enforce in review + tests where feasible.

### 2.9 `audit-hmac.key` at mode 600

Align generation/deploy scripts and docs so `audit-hmac.key` is created and verified at `600` (same bar as `master.key` on POSIX). Windows: document ACL equivalent; skip POSIX check as today for master key.

---

## 3. Eight invariants → planned mutation-probed tests

Each invariant gets at least one **mutation-probed** test in the implementation PR (fail the assertion when the guard is removed or inverted). Characterization on this PR pins today’s related behavior where it already exists.

**Lineage:** invariants **1–3** = **NEW CAS protocol** (inspired by #936; probes must fail if install precedes successful CAS). Invariants **4–8** = carried / adapted from the #936 hardening line.

| # | Invariant | Planned mutation-probed test (implementation PR) | Characterization today |
|---|-----------|--------------------------------------------------|------------------------|
| 1 | Re-read under lock before any mutation (**NEW CAS**) | Force stale in-memory version; mutation that skips re-read must fail the probe | Partial: version check re-reads under write lock (`TinkKeyManagerConcurrencyTest` reload) |
| 2 | CAS on blob version, then in-memory install **only after** successful CAS (**NEW CAS**) | Probe fails if live primary is published before CAS ack; two writers — winner installed, loser not | Partial: `@Version` save; **orphan gap** — `rotateKey_whenDbSaveFails_keepsOrphanInMemoryPrimary` (EXPECTED TO CHANGE, §2.3 / Q4) |
| 3 | Race loser discards local keyset and reloads blob (**NEW CAS**) | Inject CAS failure after staged mutate; assert kids match blob; no orphan primary | First-boot only today: `TinkKeyManagerAdminFirstBootstrapTest.java:133` (`initialize_writerLosesClaim_adoptsPeerBlob`) |
| 4 | Periodic tick shorter than sync window | Assert schedule interval ≤ configured bound; stall injection | Char: 5 s constant / DATABASE + HYBRID reload paths |
| 5 | Fail closed on unknown kid | Decrypt with kid absent from keyset → exception / fail-closed API | Char: **today returns ciphertext** (fail-open) — EXPECTED TO CHANGE |
| 6 | DB error never triggers generation of a new keyset | Inject `DataAccessException` on find; assert no generate/save of new kids | Char: writer may fall back to file path today — EXPECTED TO CHANGE |
| 7 | **Blob-only delete ban:** no `DELETE`/`TRUNCATE` of `ezkey_keyset_blob`; API processes never unlink key material files; disabling a kid does **not** delete the envelope | Mutation probe: grant/role or code path that issues DELETE/TRUNCATE on the blob must fail; disable-kid leaves envelope bytes intact; no `Files.delete` of key material from API code | Char: no B6 delete on `main`; file survives failed DB load (file-era proxy for the ban) |
| 8 | Legacy blob fails startup, no overwrite | Seed legacy outer-AEAD blob; assert startup failure and unchanged row | Char: **today overwrites** to native envelope — EXPECTED TO CHANGE |

---

## 4. What is removed

| Category | Items |
|----------|--------|
| Modes / flags | `StorageMode.FILE`, `StorageMode.HYBRID`; runtime reliance on `keyset-file`; Windows `FILE` + `degraded-mode-enabled` as FILE companion |
| Code paths | `loadExistingKeysetFileOnly`; `STARTUP_FILE_SYNC`; `persistHandleToKeysetFile` in normal boot; file branch of version check; file backup/retention in `TinkKeyManager`; legacy overwrite in `claimOrAdoptGeneratedKeyset`; #936 B2/B5/B6 file-authority paths |
| Deploy | Keyset file as HA sync mechanism (volume **kept** for master + audit keys only); crypto-api FILE-only story; generate-encryption-keys scripts’ keyset file section (keep `master.key`) |
| Docs claims | “file as cache”, HYBRID, FILE restore runbooks as primary |

**Kept:** `master.key`; `audit-hmac.key` on the secrets volume; blob CAS/version; Admin writer; non-writer wait; online rotation (new CAS); CLI import/export as adjacent thin PR.

---

## 5. Documentation updates (cutover PR)

| Document | Change |
|----------|--------|
| `docs/OPERATIONAL.md` | Restore = pg_dump + `master.key` (+ escrow when #953 lands); no keyset-file primary; honest: ezkey.online has **no guaranteed backup** |
| `docs/SECURITY_POSTURE.md` | Blob-only keyset; PII cleartext outside keyset; kids non-secret |
| `docs/KEY_ROTATION_PROCEDURES.md` | **Must update in the same cutover PR** — drop file-centric backup as SOOT; rotation = CAS blob; recovery via DB + master |
| ADR-0008 / 0011 / 0012 | Annotate storage-mode sentences **superseded by ADR-0016** |
| ADR-0016 | Accept short ADR (draft in this pack) |
| `ezkey-core/CONFIGURATION.md` | Single mode; crypto-api DataSource; remove FILE/HYBRID matrix rows; Windows checklist |
| `docs/DATABASE_ROLE_PERMISSIONS_MATRIX.md` | Add `ezkey_crypto` SELECT-only on blob |
| crypto-api README / CONFIGURATION | Remove “restart after rotation” FILE story; document RO blob load + blob-reload recovery |

**Adjacent thin PRs (not cutover):** [#942](https://github.com/mgagp/ezkey/issues/942) property purge; CLI import/export tooling.

---

## 6. Validation plan

| Stage | Host | Focus |
|-------|------|--------|
| 1 | Mac (Marc) | Fast `./scripts/build.sh`, clean-start, writer/non-writer boot, rotation CAS, crypto-api RO blob, characterization → green then mutation probes |
| 2 | Windows (Rootbeer) | §2.2 checklist (`DATABASE`, Admin `writer=true`, Auth `writer=false`, `degraded-mode-enabled=false`, `required=true`); ACL for master/audit keys under `ProgramData`; no FILE fallback |
| 3 | Community (`<static-ip ezkey-online-community>`) | In-place upgrade per [`community-upgrade-impact.md`](community-upgrade-impact.md) including **Tink-native envelope pre-check**; kid continuity; canary decrypt; leftover keyset file handling |

Do not treat a full reinstall of ezkey.online as the default path.

---

## 7. Characterization suite (this PR)

Module: `ezkey-core-security` · class: `KeysetStorageCharacterizationTest` (+ decrypt fail-open methods in suite).

Tests **must pass on current `main`**. Methods expected to flip after the refactor carry:

`// EXPECTED TO CHANGE — design §N (title)`

---

## 8. Open questions for Marc (with recommendations)

| # | Question | Recommendation |
|---|----------|----------------|
| 1 | **crypto-api health vs #942:** full fail-closed vs split liveness / encryption readiness? | **Split health** (liveness OK; encryption readiness separate) so bootstrap-init keypair/sign is not blocked 90s, while encrypt/decrypt and any `required` path stay fail-closed. Never `required=false`. |
| 2 | **SELECT on `ezkey_encryption_key`?** | **Decided: blob SELECT only.** AEAD recovery = reload blob + retry (§2.5 / §2.6). No metadata GRANT. |
| 3 | **Leftover community keyset file:** rename `.orphaned` vs delete after canary? | **Rename + retain** until one extra green restart after canary; then optional operator delete. |
| 4 | **Rotate-after-failed-CAS:** must in-memory rotation be rolled back? | **Yes — fail closed.** Staging handle only; live primary installs after CAS. Today’s orphan behavior is characterized and must flip (§2.3). |
| 5 | **Tick interval:** keep 5 s vs configurable upper bound? | **Keep 5 s** as the default constant; allow config only with a hard upper bound (≤ 5 s) enforced by tests. |
| 6 | **CLI import/export timing?** | **Adjacent thin PR after cutover** (not in the cutover PR). |
| 7 | **#942 sequencing?** | **Adjacent thin PR** immediately after (or just before) cutover — property purge only. Decrypt fail-closed stays in cutover. |
| 8 | **experimental-hybrid local crypto-api?** | **crypto-api on the VM only** — no laptop→Lightsail Postgres. |

---

## 9. Out of scope (tracked elsewhere)

Encrypted age/minisign backup, restore drill, KMS envelope, production HA IAM/RDS — [#953](https://github.com/mgagp/ezkey/issues/953). Christophe’s backup design informs #953; this pack only requires honest docs that community has no guaranteed backup today.
