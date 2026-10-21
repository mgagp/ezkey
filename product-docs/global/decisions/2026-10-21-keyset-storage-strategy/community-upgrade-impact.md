# Upgrade-impact assessment — community instance (ezkey.online)

**Host:** `<static-ip ezkey-online-community>` (never publish the origin IP in docs or tickets).  
**Surface:** Marc-operated public alpha community host — single all-in-one Docker VM, **no SLA, no guaranteed backup**, may be recreated; still the **first long-lived environment** and the lab for orderly in-place upgrades ([#952](https://github.com/mgagp/ezkey/issues/952)).  
**Ledger:** [`docs/lightsail/community/DEPLOYED.md`](../../../../docs/lightsail/community/DEPLOYED.md).  
**Stack:** `experimental-hybrid/lightsail/docker-compose.yml` — profile `docker` → **DATABASE** mode today; named volume `encryption-secrets` mounted at `/etc/ezkey` (contains `master.key` **and** a keyset file cache).

A **full reinstall is last resort** only if instability remains unexplained after reasonable in-place effort.

---

## 1. Starting state (pre-upgrade)

| Artefact | Expected on community today |
|----------|-----------------------------|
| `ezkey_keyset_blob` | Present (Tink-native envelope after ADR-0011 clean-start lineage) |
| `master.key` | On volume `/etc/ezkey/...` (compose `encryption-secrets`) |
| Keyset **file** on volume | Likely present as DATABASE cache (`keysets/keyset.json.encrypted` or equivalent under `/etc/ezkey`) — **not** the authority once DB-only lands |
| `storage-mode` override | None in repo compose; inherits DATABASE |
| crypto-api | Not part of the online community compose (lab-only after change) |

**Pre-check (read-only on the VM):** confirm `SPRING_PROFILES_ACTIVE` / env has no `FILE`/`HYBRID` override; confirm blob row exists (`SELECT version, length(keyset_data), last_updated_at FROM ezkey_keyset_blob` — no key material printed); list `/etc/ezkey` paths **by name only** (do not `cat` secrets); **Tink-native envelope gate** (§2.2).

---

## 2. In-place upgrade path (preferred)

Goal: new images that speak **blob-only** boot against the **existing** blob and `master.key`, preserving kids so existing `ENC:{kid}:…` rows keep decrypting.

### 2.1 Order of operations

1. **Record baseline** in the operator notes (not in public chat): deploy SHA from `DEPLOYED.md`, blob `version`, ordered list of **kids** (unsigned primary + enabled ids from Admin encryption-key UI or a SELECT on `ezkey_encryption_key` metadata only), and a **canary** plan (§2.3).
2. **Snapshot / tag rollback image** if the current Lightsail/deploy practice still tags `rollback-pre-<sha>` (see ledger notes).
3. **Deploy** Admin → Auth → Integration (same compose rolling habit as today). Admin writer must become healthy first so peripherals can pass bootstrap-wait.
4. **Do not** delete `master.key`. **Do not** truncate `ezkey_keyset_blob`.
5. **Leftover keyset file:** after healthy boot + canary, rename in place to a non-runtime name (e.g. `keyset.json.encrypted.orphaned-<date>`) with mode `600`, leave on the volume until one more successful restart; then optional delete. APIs must ignore it (DB-only code). Prefer rename-over-delete so rollback still has a file if an emergency temporary FILE hotfix were ever needed (should be unnecessary).

### 2.2 Pre-checks (gate deploy)

| Check | Pass criteria |
|-------|----------------|
| Blob readable | Admin (current build) already serves encrypted fields |
| **Blob is Tink-native envelope** | Structural markers only — see below. **Abort deploy if legacy.** |
| Master key present | Path exists; permissions owner-only on Linux |
| Disk / volume | Enough space for rename; volume not read-only |
| No HYBRID/FILE env override | Compose/env inspection |
| Flyway | No migration required for blob-only if schema already has `ezkey_keyset_blob` (confirm against release notes) |

**Tink-native envelope pre-check (abort on legacy):**

Convert `keyset_data` to text in SQL **only long enough** to test markers; **never** print or log the bytes:

```sql
-- Returns true when the blob looks like ADR-0011 Tink encrypted-keyset JSON.
-- Do NOT SELECT keyset_data into tickets, chat, or logs.
SELECT
  position('"encryptedKeyset"' in convert_from(keyset_data, 'UTF8')) > 0
  AND position('"keysetInfo"' in convert_from(keyset_data, 'UTF8')) > 0
FROM ezkey_keyset_blob;
```

- **Pass:** both markers present → proceed.
- **Fail (legacy / unloadable):** either marker missing → **abort the deploy**. Do not cut over to blob-only fail-closed until the blob is a native envelope (or a controlled one-shot import is run offline). Never paste `keyset_data`.

### 2.3 Verification (kid continuity + canary decrypt)

| Step | Action | Pass |
|------|--------|------|
| Boot | Admin healthy; Auth/Integration healthy within bootstrap-wait | Actuator health green |
| Kids | Compare post-boot primary/enabled kid set to baseline | Exact match (no surprise new primary from accidental generate) |
| Canary decrypt | Decrypt a known `ENC:` value (test enrollment proof token or dedicated canary row created before upgrade) | Plaintext matches pre-computed hash/known value |
| Rotation smoke (optional, lab) | Non-prod-impacting add-without-promote or documented dry rotation | CAS version increments; peripherals reload within tick |
| Logs | Grep recent logs for key material patterns | Kids/versions only |

Never paste master key, blob bytes, or ciphertext into tickets.

### 2.4 Rollback

| Situation | Action |
|-----------|--------|
| New Admin fails required encryption / legacy fail-closed | Redeploy previous image tag (`rollback-pre-…`); blob untouched; leftover file still present |
| Kids mismatch / canary fail | Stop; do **not** generate a new keyset; restore previous images; investigate blob vs master mismatch offline |
| Partial peripheral failure | Restart peripheral after Admin confirmed; do not flip writer flags on Auth |

Rollback does **not** require restoring the keyset **file** if the blob and `master.key` are intact — that is the point of DB-only. The orphaned file is only a belt-and-suspenders artefact.

### 2.5 What happens to the leftover keyset file

| Phase | File role |
|-------|-----------|
| Before upgrade | Cache written by DATABASE mode (`STARTUP_FILE_SYNC` / materialize) |
| During upgrade | Ignored by new code; must not be deleted mid-flight |
| After canary OK | Renamed `.orphaned-*`, mode 600, retained briefly |
| After second green restart | Optional delete by operator; document in private ops notes |

If someone had been treating the file as a backup: **community has no guaranteed backup**; recovering from file alone without blob is not a supported story post-cutover. Full restore always needs DB dump + `master.key` (when backups exist — they are not guaranteed here).

---

## 3. Last-resort reinstall

Only if in-place upgrade leaves unexplained crypto instability after: image rollback, blob/master verification, canary retries, and craft/security review.

Reinstall implies losing community state unless an external dump+master copy exists (today: **not guaranteed**). Treat as environment recreation, not a migration.

---

## 4. Proportionality reminder

Community is not production-grade HA. Security and restore discipline stay **honest and proportional**: best efforts, clear docs, no false backup claims (`OPERATIONAL.md` / `SECURITY_POSTURE` updates in the implementation PR).
