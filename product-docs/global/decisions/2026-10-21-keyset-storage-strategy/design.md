# Keyset storage strategy — analysis and detailed design

**Pack:** [`README.md`](README.md) · **Issue:** [#952](https://github.com/mgagp/ezkey/issues/952) · **Draft ADR:** [`adr-0016-draft.md`](adr-0016-draft.md) · **Community upgrade:** [`community-upgrade-impact.md`](community-upgrade-impact.md)

**Base:** `main` @ characterization PR head. Line numbers refer to that tree unless noted.

**Adopted direction (Marc, #952):** DB blob is the single source of truth; FILE and HYBRID removed; crypto-api gets a read-only DataSource (local lab only); eight invariants from closed PR #936 are ported; online rotation kept (DB-CAS-first, fail closed). Deferred to [#953](https://github.com/mgagp/ezkey/issues/953): encrypted off-host backup, KMS, RDS, secret separation, HA production.

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
- Explicit CLI import/export may exist later for recovery; **never** re-introduced as a silent cache. Runtime **never** deletes key material (invariant 7).

### 2.2 Modes and flags removed

Remove `StorageMode.FILE`, `StorageMode.HYBRID`, properties `keyset-file` (runtime), `degraded-mode-enabled` as a dual-path crutch, file version-check branches, `STARTUP_FILE_SYNC`, file backups in `TinkKeyManager`, HA shared keyset volume dependency, and Windows FILE profiles.

**Windows:** admin and auth `application-windows.properties` move to `DATABASE` (Postgres already required for the app).

**crypto-api:** see §2.5.

### 2.3 Online rotation via CAS on version

Target protocol (ports #936 intent):

1. Under write lock (or equivalent exclusive mutation section): **re-read** blob version (invariant 1).
2. Mutate candidate keyset in memory **or** prepare serialized envelope.
3. **CAS** persist: update row only if version matches expected; on success install in-memory handle and version **immediately after** CAS (invariant 2).
4. On CAS loss: **discard** candidate, reload blob, do not keep orphan local kids (invariant 3).
5. Periodic tick remains **shorter than any sync window** operators rely on (keep ≤ 5 s unless redesign justifies change) (invariant 4).
6. Unknown kid on decrypt → **fail closed** (invariant 5) — see §2.7.
7. DB errors during load/save → **never** generate a new keyset (invariant 6).
8. Legacy / unloadable envelope → **fail startup** with clear error; **no overwrite** (invariant 8).

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
| DB role | Prefer `ezkey_crypto` with **SELECT only** on `ezkey_keyset_blob` (and deny elsewhere), proven by `verify-grants.sh` |
| Wait | Bounded `bootstrap-wait`, then **explicit failure** |
| `required` | **Never** `required=false` for crypto-api (see [#942](https://github.com/mgagp/ezkey/issues/942)). Endpoints that need encryption fail closed if the manager is down; non-keyset endpoints may still be reachable only if product accepts splitting health — default lean: process fails closed if encryption cannot initialize when encrypt/decrypt are part of the same process contract. Exact health split is an open question (§8). |
| Secrets | Still needs `master.key`; adds DB credentials for SELECT-only role. Acceptable under Marc’s lab proportionality. |
| experimental-hybrid local | Do **not** open community Postgres to the internet; run crypto-api on the VM or use a documented one-shot export that APIs never re-read. |

### 2.6 SELECT-only role

- New LOGIN role `ezkey_crypto` (or equivalent name): `SELECT` on `ezkey_keyset_blob` only.
- Grants in `01-create-roles.sh` / `scripts/db/apply-grants.sql`; denials asserted in `verify-grants.sh`.
- Update `docs/DATABASE_ROLE_PERMISSIONS_MATRIX.md`.
- Application `writer=false` is defense in depth; **DB GRANT is the real barrier**.

### 2.7 Fix fail-open in `EncryptionService.decrypt`

Target: decrypt failures (unknown kid, AEAD failure after reload budget, corruption) **throw** or return a typed failure that callers treat as fail-closed — never return the ciphertext as if it were plaintext.

Coordinate with `AtRestEncryptionAccess` so read paths under `required` (and post-#942 always-on encryption) fail closed consistently.

### 2.8 Logs carry kids only

Continue (and audit) that logs may include unsigned/signed key IDs and blob version, never raw keyset bytes, master key material, or ciphertext dumps. Enforce in review + tests where feasible.

### 2.9 `audit-hmac.key` at mode 600

Align generation/deploy scripts and docs so `audit-hmac.key` is created and verified at `600` (same bar as `master.key` on POSIX). Windows: document ACL equivalent; skip POSIX check as today for master key.

---

## 3. Eight invariants → planned mutation-probed tests

Each invariant gets at least one **mutation-probed** test in the implementation PR (fail the assertion when the guard is removed or inverted). Characterization on this PR pins today’s related behavior where it already exists.

| # | Invariant | Planned mutation-probed test (implementation PR) | Characterization today |
|---|-----------|--------------------------------------------------|------------------------|
| 1 | Re-read under lock before any mutation | Force stale in-memory version; mutation that skips re-read must fail the probe | Partial: version check re-reads under write lock (`TinkKeyManagerConcurrencyTest` reload) |
| 2 | CAS on blob version, then in-memory install right after CAS | Two writers; winner’s version installed; loser must not keep local handle | Partial: `@Version` save; **gap** on rotate-after-failed-save |
| 3 | Race loser discards local keyset and reloads blob | Inject CAS failure after local mutate; assert kids match blob | `initialize_writerLosesClaim_adoptsPeerBlob` (first-boot only) |
| 4 | Periodic tick shorter than sync window | Assert schedule interval ≤ configured bound; stall injection | Char: 5 s constant / reload path |
| 5 | Fail closed on unknown kid | Decrypt with kid absent from keyset → exception / fail-closed API | Char: **today returns ciphertext** (fail-open) — marked EXPECTED TO CHANGE |
| 6 | DB error never triggers generation of a new keyset | Inject `DataAccessException` on find; assert no generate/save of new kids | Char: writer may fall back to file path today — EXPECTED TO CHANGE |
| 7 | Runtime never deletes key material | Mutation that deletes keyset file / clears handle without reload must fail | Char: no B6 delete on `main`; file survives failed DB load |
| 8 | Legacy blob fails startup, no overwrite | Seed legacy outer-AEAD blob; assert startup failure and unchanged row | Char: **today overwrites** to native envelope — EXPECTED TO CHANGE |

---

## 4. What is removed

| Category | Items |
|----------|--------|
| Modes / flags | `StorageMode.FILE`, `StorageMode.HYBRID`; runtime reliance on `keyset-file`; Windows `FILE` + `degraded-mode-enabled` as FILE companion |
| Code paths | `loadExistingKeysetFileOnly`; `STARTUP_FILE_SYNC`; `persistHandleToKeysetFile` in normal boot; file branch of version check; file backup/retention in `TinkKeyManager`; legacy overwrite in `claimOrAdoptGeneratedKeyset`; #936 B2/B5/B6 file-authority paths |
| Deploy | Shared keyset file as HA sync mechanism (`encryption-secrets-ha` keyset path); crypto-api FILE-only story; generate-encryption-keys scripts’ keyset file section (keep `master.key`) |
| Docs claims | “file as cache”, HYBRID, FILE restore runbooks as primary |

**Kept:** `master.key`; blob CAS/version; Admin writer; non-writer wait; online rotation (redesigned); optional explicit CLI import/export (later).

---

## 5. Documentation updates (implementation PR)

| Document | Change |
|----------|--------|
| `docs/OPERATIONAL.md` | Restore = pg_dump + `master.key` (+ escrow when #953 lands); no keyset-file primary; honest: ezkey.online has **no guaranteed backup** |
| `docs/SECURITY_POSTURE.md` | Blob-only keyset; PII cleartext outside keyset; kids non-secret |
| `docs/KEY_ROTATION_PROCEDURES.md` | Drop file-centric backup as SOOT; rotation = CAS blob; recovery via DB + master |
| ADR-0008 / 0011 / 0012 | Annotate storage-mode sentences **superseded by ADR-0016** |
| ADR-0016 | Accept short ADR (draft in this pack) |
| `ezkey-core/CONFIGURATION.md` | Single mode; crypto-api DataSource; remove FILE/HYBRID matrix rows |
| `docs/DATABASE_ROLE_PERMISSIONS_MATRIX.md` | Add `ezkey_crypto` SELECT-only |
| crypto-api README / CONFIGURATION | Remove “restart after rotation” FILE story; document RO blob load |

---

## 6. Validation plan

| Stage | Host | Focus |
|-------|------|--------|
| 1 | Mac (Marc) | Fast `./scripts/build.sh`, clean-start, writer/non-writer boot, rotation CAS, crypto-api RO blob, characterization → green then mutation probes |
| 2 | Windows (Rootbeer) | DATABASE Windows profiles; POSIX permission checks skipped; ACL/`audit-hmac.key` / master key path under `ProgramData`; no FILE fallback |
| 3 | Community (`<static-ip ezkey-online-community>`) | In-place upgrade per [`community-upgrade-impact.md`](community-upgrade-impact.md); kid continuity; canary decrypt; leftover keyset file handling |

Do not treat a full reinstall of ezkey.online as the default path.

---

## 7. Characterization suite (this PR)

Module: `ezkey-core-security` · class: `KeysetStorageCharacterizationTest` (+ decrypt fail-open methods in suite).

Tests **must pass on current `main`**. Methods expected to flip after the refactor carry:

`// EXPECTED TO CHANGE — design §N (title)`

---

## 8. Open questions for Marc

1. **crypto-api health vs #942:** If the blob is missing at clean-start, should crypto-api refuse to become healthy entirely (blocking bootstrap-init until Admin writes the blob), or expose a split health (liveness OK / encryption readiness separate) while still never using `required=false`?
2. **SELECT on `ezkey_encryption_key`:** Does crypto-api need metadata SELECT for decrypt recovery, or is blob-only SELECT enough?
3. **Leftover keyset file on community:** Prefer leave-in-place (renamed `.orphaned`) vs operator-deleted after verified canary — default proposal is rename+retain until one successful post-upgrade canary (§ community doc)?
4. **Rotate-after-failed-CAS:** Confirm product rule: in-memory rotation must not remain installed if blob CAS fails (fail closed the admin rotation API).
5. **Tick interval:** Keep 5 s, or make configurable with an upper bound enforced in tests?
6. **CLI import/export:** Same implementation PR as DB-only cutover, or a fast follow once community upgrade is proven?
7. **#942 sequencing:** Fold always-on encryption into the keyset refactor PR, or land #942 immediately before/after?
8. **experimental-hybrid local crypto-api:** Confirm “crypto-api on the VM only” for community debugging (no laptop→Lightsail Postgres).

---

## 9. Out of scope (tracked elsewhere)

Encrypted age/minisign backup, restore drill, KMS envelope, production HA IAM/RDS — [#953](https://github.com/mgagp/ezkey/issues/953). Christophe’s Q2 backup design informs #953; this pack only requires honest docs that community has no guaranteed backup today.
