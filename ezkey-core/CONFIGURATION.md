# Configuration Reference — ezkey-core

Ezkey-core is the shared library depended upon by all API modules. It defines **8 groups** of
`@ConfigurationProperties` classes covering encryption, audit integrity, authentication, and
instance metadata. Applications that depend on ezkey-core declare these properties in their own
`application*.properties` files.

---

## Quick Overview

| Property | Docker env var | Default | Obligation |
|---|---|---|---|
| `ezkey.encryption.enabled` | — | `true` | optionnel |
| `ezkey.encryption.required` | `EZKEY_ENCRYPTION_REQUIRED` | `false` | optionnel [prod] / **true [docker]** |
| `ezkey.encryption.master-key-file` | — | *(null)* | requis [docker] |
| `ezkey.encryption.keyset-file` | — | *(null)* | requis [docker] lors du mode FILE/HYBRID |
| `ezkey.encryption.algorithm` | — | `AES256_GCM` | optionnel |
| `ezkey.encryption.keyset.storage-mode` | — | `DATABASE` | optionnel |
| `ezkey.audit.integrity.enabled` | — | `true` | optionnel |
| `ezkey.audit.integrity.required` | `EZKEY_AUDIT_INTEGRITY_REQUIRED` | `false` | optionnel [prod] / **true [docker]** |
| `ezkey.audit.integrity.hmac-key-file` | — | *(null)* | requis [docker] |
| `ezkey.audit.integrity.instance-id` | `EZKEY_INSTANCE_ID` | *(null)* | optionnel |
| `ezkey.audit.chain.enabled` | — | `true` | optionnel |
| `ezkey.audit.archive.auto-seal.enabled` | — | `true` | optionnel |
| `ezkey.audit.archive.external-archival-enabled` | — | `false` | optionnel |
| `ezkey.audit.archive.retention-period` | — | `P12M` | optionnel |
| `ezkey.audit.archive.seal-delay` | — | `P0D` | optionnel |
| `ezkey.audit.archive.purge-delay` | — | `P30D` | optionnel |
| `ezkey.audit.archive.purge.enabled` | — | `true` | optionnel |
| `ezkey.audit.archive.purge.cron` | — | `0 0 2 * * ?` | optionnel |
| `ezkey.organization.name` | — | `Ezkey System` | optionnel |
| `ezkey.organization.about-url` | `EZKEY_ORGANIZATION_ABOUT_URL` | *(null)* | optionnel |
| `ezkey.qr.auth-base-url` | `EZKEY_QR_AUTH_BASE_URL` | *(null)* | requis [docker] |
| `ezkey.enrollment.pending-expiration-days` | — | `7` | optionnel |
| `ezkey.demo.mitm-signature-enabled` | `EZKEY_DEMO_MITM_SIGNATURE_ENABLED` | `false` | optionnel |
| `ezkey.core.crypto.rsa-key-size` | — | `2048` | optionnel |
| `ezkey.core.auth-attempt.challenge-digits` | — | `2` | optionnel |
| `ezkey.core.auth-attempt.ttl-seconds` | — | `120` | optionnel |

---

## Properties by Functional Group

### Core Crypto & Auth Attempt (`ezkey.core.*`)

**Description:** cryptographic algorithm parameters for RSA key pairs and signature verification,
plus authentication attempt challenge configuration.

**Defined in:** `EzkeyCoreProperties`

| Property | Type | Default | Obligation | Description |
|---|---|---|---|---|
| `ezkey.core.crypto.rsa-key-size` | `int` | `2048` | optionnel | RSA key size in bits. Minimum 2048. |
| `ezkey.core.crypto.rsa-algorithm` | `String` | `RSA` | optionnel | Algorithm name for key generation. |
| `ezkey.core.crypto.signature-algorithm` | `String` | `SHA256withRSA` | optionnel | Digital signature algorithm. |
| `ezkey.core.crypto.minimum-key-size` | `int` | `2048` | optionnel | Minimum RSA key size for validation; keys smaller than this are rejected. |
| `ezkey.core.auth-attempt.challenge-digits` | `int` | `2` | optionnel | Number of digits in the challenge code (range 1–6). |
| `ezkey.core.auth-attempt.ttl-seconds` | `int` | `120` | optionnel | Auth attempt time-to-live in seconds (range 30–600). |

**Exemple minimal :**

```properties
# All defaults are production-viable; override only if necessary.
ezkey.core.auth-attempt.challenge-digits=2
ezkey.core.auth-attempt.ttl-seconds=120
```

---

### Encryption at Rest (`ezkey.encryption.*`)

**Description:** controls Tink-based AES-256-GCM encryption applied to sensitive fields
(challenge codes, auth attempt payloads). Includes key rotation and re-encryption batch jobs.
Rotation and re-encryption jobs run **only in Admin API**; Auth API and Integration API set
`rotation.enabled=false` and `reencryption.enabled=false`.

**Concurrency model:** `TinkKeyManager` uses a read/write lock and a throttled async keyset
version check so encrypt/decrypt does not serialize on DB sync. See
[ADR-0008](../product-docs/global/architecture-decisions.md#adr-0008-tink-keyset-sync-concurrent-read-path)
(SEC-009).

**Defined in:** `TinkProperties`

#### Core

| Property | Type | Default | Obligation | Description |
|---|---|---|---|---|
| `ezkey.encryption.enabled` | `boolean` | `true` | optionnel | Enable/disable encryption at rest. |
| `ezkey.encryption.required` | `boolean` | `false` | optionnel [prod] / **requis [docker]** | When `true`, fail startup if encryption is enabled but Tink cannot initialize (SEC-002). Also fail-closed on **read** and **write** of at-rest fields via `AtRestEncryptionAccess` (ApiKey, Enrollment, AuthAttempt). Docker profile sets `true`. |
| `ezkey.encryption.master-key-file` | `String` | *(null)* | requis [docker] | Path to the Base64-encoded 256-bit master key file (e.g. `/etc/ezkey/secrets/master.key`). |
| `ezkey.encryption.keyset-file` | `String` | *(null)* | requis [docker] en mode FILE/HYBRID | Path to the encrypted Tink keyset file (e.g. `/etc/ezkey/keysets/keyset.json.encrypted`). |
| `ezkey.encryption.algorithm` | `String` | `AES256_GCM` | optionnel | AEAD algorithm. Accepted values: `AES256_GCM`, `CHACHA20_POLY1305`. |

#### Keyset Storage (`ezkey.encryption.keyset.*`)

In `DATABASE` and `HYBRID` modes, `ezkey_keyset_blob.keyset_data` stores Tink's
encrypted-keyset JSON envelope directly. The key material remains encrypted by the configured
master key; Tink envelope metadata such as key IDs and primary-key status may remain visible and is
treated as non-secret operational metadata.

| Property | Type | Default | Obligation | Description |
|---|---|---|---|---|
| `ezkey.encryption.keyset.storage-mode` | `StorageMode` | `DATABASE` | optionnel | Enum: `FILE` (file only), `DATABASE` (DB as source of truth), `HYBRID` (both, DB preferred). Use `DATABASE` in multi-instance deployments. |
| `ezkey.encryption.keyset.degraded-mode-enabled` | `boolean` | `false` | optionnel | When true, a decryption failure triggers a DB reload and a read-only degraded mode instead of a hard failure. |
| `ezkey.encryption.keyset.max-reload-retries` | `int` | `1` | optionnel | Maximum keyset reload attempts on decryption failure. |

#### Key Rotation (`ezkey.encryption.rotation.*`)

| Property | Type | Default | Obligation | Description |
|---|---|---|---|---|
| `ezkey.encryption.rotation.enabled` | `boolean` | `true` | optionnel | Enable scheduled key rotation. Disable in Auth API and Integration API. |
| `ezkey.encryption.rotation.schedule` | `String` | `0 0 2 * * ?` | optionnel | Cron schedule for rotation checks (daily at 2 AM by default). |
| `ezkey.encryption.rotation.max-key-age-days` | `int` | `90` | optionnel | Maximum primary key age in days before automatic rotation. |
| `ezkey.encryption.rotation.backup-before-rotation` | `boolean` | `true` | optionnel | Create a keyset backup before each rotation for rollback capability. |
| `ezkey.encryption.rotation.backup-retention-days` | `int` | `365` | optionnel | Backup retention period in days. |
| `ezkey.encryption.rotation.min-keys-in-keyset` | `int` | `2` | optionnel | Minimum number of keys to keep for decryption (old ciphertext decryptability). |
| `ezkey.encryption.rotation.max-keys-in-keyset` | `int` | `5` | optionnel | Maximum keys before cleanup. |
| `ezkey.encryption.rotation.auto-disable-days` | `int` | `180` | optionnel | Days after which old keys are automatically disabled. |
| `ezkey.encryption.rotation.sync-window-seconds` | `int` | `30` | optionnel | Seconds a new key stays `PENDING` before promotion to `PRIMARY` (allows HA instance synchronization). |
| `ezkey.encryption.rotation.promotion-check-interval-seconds` | `int` | `5` | optionnel | Interval in seconds between checks for `PENDING` keys ready for promotion. |

#### Re-encryption (`ezkey.encryption.reencryption.*`)

| Property | Type | Default | Obligation | Description |
|---|---|---|---|---|
| `ezkey.encryption.reencryption.enabled` | `boolean` | `true` | optionnel | Enable scheduled re-encryption batch processing. Disable in Auth API and Integration API. |
| `ezkey.encryption.reencryption.schedule` | `String` | `0 0 3 * * ?` | optionnel | Cron schedule (daily at 3 AM, after rotation). |
| `ezkey.encryption.reencryption.batch-size` | `int` | `500` | optionnel | Records per batch. |
| `ezkey.encryption.reencryption.throttle-ms` | `int` | `100` | optionnel | Delay in milliseconds between batches (prevents DB overload). |
| `ezkey.encryption.reencryption.max-batches-per-run` | `int` | `100` | optionnel | Maximum batches processed per scheduled execution. |
| `ezkey.encryption.reencryption.max-duration-minutes` | `int` | `60` | optionnel | Maximum wall-clock duration per execution in minutes. |
| `ezkey.encryption.reencryption.auto-retry-failed` | `boolean` | `true` | optionnel | Automatically retry failed batches. |
| `ezkey.encryption.reencryption.parallel-batch-workers` | `int` | `1` (Java) / Admin config **`4`** | optionnel | Parallel workers for distinct batches. Pair with `auth-attempt-shard-count` (`workers >= shards`). Enrollment is table-mutex serialized; auth-attempt shards run concurrently when sharding is on. |
| `ezkey.encryption.reencryption.parallel-batch-queue-capacity` | `int` | `100` | optionnel | Queue capacity for the parallel executor (back-pressure). |
| `ezkey.encryption.reencryption.temporal-batch-sizing-enabled` | `boolean` | `false` | optionnel | When true, the first fetch uses `recent-data-chunk-size`; subsequent slices use `stale-data-chunk-size`. |
| `ezkey.encryption.reencryption.recent-data-chunk-size` | `int` | `250` | optionnel | Chunk size for the first slice (leading edge). |
| `ezkey.encryption.reencryption.stale-data-chunk-size` | `int` | `1000` | optionnel | Chunk size after the cursor has advanced (resume). |
| `ezkey.encryption.reencryption.auth-attempt-shard-count` | `int` | `1` (Java) / Admin config **`4`** | optionnel | Configured ceiling for `ezkey_auth_attempt` shards (`mod(auth_attempt_id, N)`). `1` = off. Creation uses `N = min(configured, totalRecords)` and skips empty residue classes (or falls back to one non-sharded batch). Typical Admin pairing **4/4**; upper ops band **8**. See `docs/REENCRYPTION_OPERATIONS.md` §9. |

**Exemple Docker minimal :**

```properties
ezkey.encryption.enabled=true
ezkey.encryption.required=true
ezkey.encryption.master-key-file=/etc/ezkey/secrets/master.key
ezkey.encryption.keyset-file=/etc/ezkey/keysets/keyset.json.encrypted
ezkey.encryption.algorithm=AES256_GCM
ezkey.encryption.keyset.storage-mode=DATABASE
```

---

### Audit Log Integrity (`ezkey.audit.integrity.*`)

**Description:** per-entry HMAC-SHA256 signing of audit log records. Provides tamper-evidence for
SOC 2 compliance. The HMAC key is intentionally separate from the Tink master key (separation of
concerns: integrity vs. confidentiality).

**Defined in:** `AuditHmacProperties`

| Property | Type | Default | Obligation | Description |
|---|---|---|---|---|
| `ezkey.audit.integrity.enabled` | `boolean` | `true` | optionnel | Enable/disable HMAC signing of audit entries. Disable only in development. |
| `ezkey.audit.integrity.required` | `boolean` | `false` | optionnel [prod] / **requis [docker]** | When `true`, fail startup if integrity is enabled but HMAC signing is not active (SEC-008). Docker profile sets `true`. |
| `ezkey.audit.integrity.hmac-key-file` | `String` | *(null)* | requis [docker] | Path to the Base64-encoded 256-bit HMAC key file (e.g. `/etc/ezkey/secrets/audit-hmac.key`). |
| `ezkey.audit.integrity.instance-id` | `String` | *(null)* | optionnel | Instance identifier for HA tracking (e.g. `admin-api-1`). Set from `EZKEY_INSTANCE_ID` env var. |

**Exemple Docker minimal :**

```properties
ezkey.audit.integrity.enabled=true
ezkey.audit.integrity.required=true
ezkey.audit.integrity.hmac-key-file=/etc/ezkey/secrets/audit-hmac.key
ezkey.audit.integrity.instance-id=${EZKEY_INSTANCE_ID:admin-api}
```

---

### Audit Log Chain (`ezkey.audit.chain.*`)

**Description:** periodic chain checkpoints linking consecutive time windows. Detects row
insertions, deletions, and reordering between checkpoints. Chain jobs run **only in Admin API**.
Auth API and Integration API set `ezkey.audit.chain.enabled=false`.

**Defined in:** `AuditChainProperties`

| Property | Type | Default | Obligation | Description |
|---|---|---|---|---|
| `ezkey.audit.chain.enabled` | `boolean` | `true` | optionnel | Enable/disable chain checkpoint job. Set to `false` in Auth API and Integration API. |
| `ezkey.audit.chain.window-minutes` | `int` | `5` | optionnel | Time window size in minutes for each checkpoint. |
| `ezkey.audit.chain.lookback-minutes` | `int` | `60` | optionnel | Lookback window in minutes for catch-up after restarts. **Bounds:** 15–480 (validated at bind time). |
| `ezkey.audit.chain.cron` | `String` | `1 */5 * * * ?` | optionnel | Quartz-style cron controlling how often **`AuditChainScheduler`** attempts catch-up checkpoints (runs **only when** `enabled=true`; run just after the window boundary so a second-zero tick cannot seal an incomplete window or defer it by a full cycle; Admin API Docker profile sets this explicitly — see [`docs/AUDIT_LOG_INTEGRITY.md`](../docs/AUDIT_LOG_INTEGRITY.md)). |

**Derived peripheral timing reminder:** Auth API / Integration APIs compute earliest peripheral HTTP fail-close time as **`latest.window_end + grace_windows × window_minutes − stop_before_next_window`** (see **`ezkey.audit.chain.heartbeat.*`** plus [`docs/AUDIT_LOG_INTEGRITY.md`](../docs/AUDIT_LOG_INTEGRITY.md)). Keeping **`window-minutes`** identical everywhere guards against configuration-driven false positives.

**Multi-instance coherence (Admin API vs peripheral APIs)**

- Only **Admin API** runs the checkpoint scheduler (`ezkey.audit.chain.enabled=true`). Auth API and Integration API set `enabled=false` but still bind `AuditChainProperties`.
- **`ezkey.audit.chain.window-minutes` must match Admin API** on every JVM that evaluates peripheral heartbeat supervision (`AuditChainHeartbeatGuardService` compares wall-clock time to `latest.window_end` using `AuditChainProperties#getWindowMinutes()`). Relying on the Java default (`5`) without an explicit property is fragile — Docker profiles pin the same numeric value on Admin API, Auth API, and Integration API.
- Changing **`ezkey.audit.chain.heartbeat.grace-windows`** or **`stop-before-next-window`** affects fail-closed timing relative to those windows; tune deliberately and keep heartbeat-related settings aligned across Auth API, Integration API, and Admin API (including `docker-test`, which relaxes rate limits but keeps heartbeat semantics).

### Peripheral heartbeat (`ezkey.audit.chain.heartbeat.*`)

**Description:** Latest checkpoint acts as Admin API heartbeat for Auth API and Integration API supervision.
Fails closed on selected peripheral routes when checkpoints appear stalled beyond grace thresholds.
Grace durations are expressed as multiples of **`ezkey.audit.chain.window-minutes`** — keep that value aligned with Admin API (see **Multi-instance coherence** above).

**Defined in:** `AuditChainHeartbeatProperties`

| Property | Type | Default | Obligation | Description |
|---|---|---|---|---|
| `ezkey.audit.chain.heartbeat.enabled` | `boolean` | `true` | optionnel | Master toggle for **`AuditChainHeartbeatGuardService`** (when `false`, evaluation short-circuits to OK and MVC interceptors no-op — incidents/alerts likewise silent). |
| `ezkey.audit.chain.heartbeat.required` | `boolean` | `true` | optionnel | When `false`, peripherals never HTTP-block — local troubleshooting only (not for shared stacks). |
| `ezkey.audit.chain.heartbeat.grace-windows` | `int` | `2` | optionnel | Checkpoint-window multiples after `latest.window_end` forming outer supervision boundary. |
| `ezkey.audit.chain.heartbeat.stop-before-next-window` | `Duration` | `PT1M` | optionnel | Safety buffer subtracted before fail-closed threshold. |
| `ezkey.audit.chain.heartbeat.cache-ttl` | `Duration` | `PT5S` | optionnel | Cached evaluation TTL to reduce DB reads per request. |
| `ezkey.audit.chain.heartbeat.bootstrap-grace` | `Duration` | `PT10M` | optionnel | Startup tolerance before checkpoints exist (clean install). |
| `ezkey.audit.chain.heartbeat.admin-evaluate-scheduler-enabled` | `boolean` | `true` | optionnel | Admin API scheduler ping (`@Scheduled`) — keeps incidents/alerts fresh without peripheral traffic. |
| `ezkey.audit.chain.heartbeat.admin-evaluate-fixed-delay-ms` | `long` | `30000` | optionnel | Delay between Admin heartbeat evaluations (milliseconds). |

---

### Nightly retroactive integrity validation (`ezkey.audit.integrity.nightly.*`)

**Description:** Scheduled batch that retroactively validates per-entry HMAC integrity and checkpoint
chain continuity over a completed window (default 24 h). Raises `AUDIT_INTEGRITY_RUPTURE` on anomaly;
updates `ezkey_scheduled_job_last_run` for operator visibility (Wave B B1).

**Defined in:** `NightlyIntegrityProperties`

| Property | Type | Default | Obligation | Description |
|---|---|---|---|---|
| `ezkey.audit.integrity.nightly.enabled` | `boolean` | `true` | optionnel | Enable/disable nightly batch. Set `false` in native/Windows dev profiles without HMAC. |
| `ezkey.audit.integrity.nightly.cron` | `String` | `0 0 2 * * ?` | optionnel | Quartz cron for nightly run (default 02:00 UTC). Prefer a small margin after the hour (e.g. `0 2 2 * * ?`) if operators want extra headroom vs chain attach at second 1 of each 5-minute tick. |
| `ezkey.audit.integrity.nightly.window-hours` | `int` | `24` | optionnel | Retroactive validation window length in hours. |

**Window alignment:** The scheduled batch rounds `windowEnd` down to the audit-chain checkpoint grid
(same `ezkey.audit.chain.window-minutes`, default 5) before validating — see ADR-0009. Do not pass
raw wall-clock `now()` with sub-second precision into chain range queries.

**Registry:** successful runs update `NIGHTLY_INTEGRITY_VALIDATION` in `ezkey_scheduled_job_last_run`.
Batch infrastructure failures record `FAILED` on the registry row only (C9 — no “batch did not run” alert).

`enabled=false` idles the **scheduler only** — operator `POST /api/v1/audit-logs/integrity-validation/run` remains available when HMAC is active.

---

### Operator retroactive validation (`ezkey.audit.integrity.retroactive.*`)

**Description:** Caps and configuration for Global Admin `POST …/integrity-validation/run` (B2.7). Uses the same orchestration as the nightly batch; operator runs do **not** update the nightly job registry.

**Defined in:** `RetroactiveIntegrityProperties`

| Property | Type | Default | Obligation | Description |
|---|---|---|---|---|
| `ezkey.audit.integrity.retroactive.operator-max-window-hours` | `Integer` | *(null — no cap)* | optionnel | When set, hard-reject operator POST when `[from, to)` exceeds this many hours. When unset, operator POST accepts the same bounds as GET verify (no duration limit). Nightly scheduled window remains `nightly.window-hours`. |

---

### Audit Log Archive (`ezkey.audit.archive.*`)

**Description:** system-owned policy contract for the single audit-log lifecycle. Physical
deletion is never configured as a separate legacy mode; it is the terminal step of lifecycle
progression.

**Defined in:** `AuditArchiveProperties`

| Property | Type | Default | Obligation | Description |
|---|---|---|---|---|
| `ezkey.audit.archive.auto-seal.enabled` | `boolean` | `true` | optionnel | Declares that SEAL is intended to be executed by lifecycle automation in chain-on mode. |
| `ezkey.audit.archive.external-archival-enabled` | `boolean` | `false` | optionnel | Enables the `SEALED -> EXPORTED -> PURGEABLE` policy branch. When `false`, the lifecycle skips `EXPORTED` but still stays lifecycle-driven. |
| `ezkey.audit.archive.retention-period` | `Period` | `P12M` | optionnel | Archival policy horizon after which checkpoint windows become eligible for automated SEAL. |
| `ezkey.audit.archive.seal-delay` | `Period` | `P0D` | optionnel | Optional extra delay after the retention horizon before automated SEAL becomes eligible. |
| `ezkey.audit.archive.purge-delay` | `Period` | `P30D` | optionnel | Safety delay between lifecycle authorization and physical deletion eligibility. |
| `ezkey.audit.archive.purge.enabled` | `boolean` | `true` | optionnel | Enables the scheduled lifecycle purge executor. |
| `ezkey.audit.archive.purge.cron` | `String` | `0 0 2 * * ?` | optionnel | Cron schedule for the lifecycle purge executor. |

---

### Organization (`ezkey.organization.*`)

**Description:** metadata for the organization hosting this Ezkey instance. Exposed via
`GET /api/v1/public/instance-info` on Admin API and Auth API.

**Defined in:** `OrganizationProperties`

| Property | Type | Default | Obligation | Description |
|---|---|---|---|---|
| `ezkey.organization.name` | `String` | `Ezkey System` | optionnel | Name of the organization. Used to seed the system tenant. |
| `ezkey.organization.description` | `String` | `Ezkey MFA instance for your organization` | optionnel | Short description of this deployment, shown in the Admin UI and public instance-info. |
| `ezkey.organization.about-url` | `String` | *(null)* | optionnel | Optional URL for an "About" / "Learn more" link in the Admin UI. |

---

### QR Code (`ezkey.qr.*`)

**Description:** controls the content embedded in enrollment QR codes. The `auth-base-url` is the
public-facing URL of the Auth API reachable from the mobile device. **Not the internal Docker
network URL.**

**Defined in:** `QrCodeProperties`

| Property | Type | Default | Obligation | Description |
|---|---|---|---|---|
| `ezkey.qr.auth-base-url` | `String` | *(null)* | requis [docker] | Public Auth API base URL embedded in QR code payloads (e.g. `https://ezkey.acme.com:8080`). When absent, `authUrl` is omitted from QR codes and the mobile app falls back to its own default. |

---

### Enrollment (`ezkey.enrollment.*`)

**Description:** controls pending enrollment expiration and the cleanup job that marks expired
enrollments. Used only by Admin API.

**Defined in:** `EnrollmentProperties`

| Property | Type | Default | Obligation | Description |
|---|---|---|---|---|
| `ezkey.enrollment.pending-expiration-days` | `Integer` | `7` | optionnel | Days after creation before a pending enrollment expires when `POST /api/v1/enrollments` omits `expiresAt`. Use `0` to disable this default (explicit `expiresAt` on create still applies). |
| `ezkey.enrollment.expired-cleanup-cron` | `String` | `0 0 1 * * ?` | optionnel | Cron expression for the job that marks expired enrollments and emits `ENROLLMENT_EXPIRED` events. |

---

### Demo (`ezkey.demo.*`)

**Description:** optional demonstration / lab settings for MITM simulation. **Must never be
enabled in production.** Disabled by default.

**Defined in:** `EzkeyDemoProperties`

| Property | Type | Default | Obligation | Description |
|---|---|---|---|---|
| `ezkey.demo.mitm-signature-enabled` | `boolean` | `false` | optionnel | When `true`, auth attempts flagged `demo_mitm_signature_enabled` receive a tampered Pending response. For demos and security training only. |

---

## Profile Matrix

The following table shows the effective values in each profile. Blank cells indicate the property
is not set in that profile (Java class default applies).

### `ezkey.encryption.*`

| Property | default | docker | docker-test | native (admin-api) |
|---|---|---|---|---|
| `enabled` | `true` | `true` | `true` | `true` |
| `master-key-file` | *(null)* | `/etc/ezkey/secrets/master.key` | `/etc/ezkey/secrets/master.key` | *(null)* |
| `keyset-file` | *(null)* | `/etc/ezkey/keysets/keyset.json.encrypted` | `/etc/ezkey/keysets/keyset.json.encrypted` | *(null)* |
| `algorithm` | `AES256_GCM` | `AES256_GCM` | `AES256_GCM` | `AES256_GCM` |
| `keyset.storage-mode` | `DATABASE` | `DATABASE` | `DATABASE` | `FILE` |
| `rotation.enabled` | `true` | `true` (admin) / `false` (auth, int) | `true` | `true` |
| `rotation.schedule` | `0 0 2 * * ?` | `0 0 2 * * ?` | `0 */5 * * * ?` | `0 0 2 * * ?` |
| `reencryption.enabled` | `true` | `true` (admin) / `false` (auth, int) | `true` | `true` |
| `reencryption.batch-size` | `500` | `500` | `500` | `500` |

### `ezkey.audit.integrity.*`

| Property | default | docker | docker-test |
|---|---|---|---|
| `enabled` | `true` | `true` | `true` |
| `hmac-key-file` | *(null)* | `/etc/ezkey/secrets/audit-hmac.key` | `/etc/ezkey/secrets/audit-hmac.key` |
| `instance-id` | *(null)* | `${EZKEY_INSTANCE_ID:admin-api}` | `${EZKEY_INSTANCE_ID:admin-api}` |

### `ezkey.organization.*`

| Property | default | docker |
|---|---|---|
| `name` | `Ezkey System` | `Ezkey System` |
| `description` | `Ezkey MFA instance for your organization` | `Ezkey MFA instance for your organization` |
| `about-url` | *(null)* | `${EZKEY_ORGANIZATION_ABOUT_URL:}` |

---

## Docker Environment Variable Reference

| Spring property | Docker env var | Notes |
|---|---|---|
| `ezkey.audit.integrity.instance-id` | `EZKEY_INSTANCE_ID` | Per-instance. E.g. `admin-api-1`. |
| `ezkey.organization.about-url` | `EZKEY_ORGANIZATION_ABOUT_URL` | Set in `docker-compose.yml`. |
| `ezkey.qr.auth-base-url` | `EZKEY_QR_AUTH_BASE_URL` | Public Auth API URL for mobile QR. |
| `ezkey.demo.mitm-signature-enabled` | `EZKEY_DEMO_MITM_SIGNATURE_ENABLED` | Auth API only. Default `true` in dev stack (false in prod). |

> The master key and HMAC key files are not passed as env vars — they are mounted via the
> `encryption-secrets` Docker volume at `/etc/ezkey/`.

---

## Security Considerations

1. **Master key and HMAC key files** must never be committed to source control. Use Docker secrets
   or a secure vault.
2. **Demo MITM flag** (`ezkey.demo.mitm-signature-enabled`) must be `false` in any production
   environment.
3. **RSA key size** must be ≥ 2048 bits; never override to a lower value.
4. **Key rotation** is enabled by default and should remain so in production (Admin API only).
