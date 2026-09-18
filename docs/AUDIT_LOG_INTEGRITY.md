# Audit Log Integrity -- HMAC Signing & Chain Checkpoints

> **Product terms:** tamper-evident monitoring; identifiable operator identity
> **Normative standard:** Tamper-evidence, not tamper-proof (same approach as HashiCorp Vault audit backend)

## Overview

Ezkey applies two complementary integrity mechanisms to audit logs in self-hosted deployments:

| Layer | Purpose | Granularity | Detects |
|-------|---------|-------------|---------|
| **Per-entry HMAC** | Prove individual entries have not been modified | Single row | Field modification |
| **Chain checkpoints** | Prove no entries were inserted, deleted, or reordered | 5-minute windows | Insertion, deletion, reordering |

Both layers use **HMAC-SHA256** via `javax.crypto.Mac` (standard JDK -- no Tink dependency). The HMAC key is **separate** from the Tink encryption master key (separation of duties: integrity vs. confidentiality).

---

## 1. Per-Entry HMAC Signing

### How It Works

When `AuditLogService.log()` saves an entry, `AuditHmacService` automatically:

1. Builds a **canonical form** -- a pipe-delimited string of all significant fields, with null fields as empty strings and timestamps normalized to UTC ISO-8601
2. Computes `HMAC-SHA256(canonical_form, key)` using the dedicated audit key
3. Stores the Base64-encoded result in `entry_hmac` alongside the entry

```
auditLogId|eventType|eventAction|eventStatus|apiName|ipAddress|adminId|
integrationId|enrollmentId|tenantId|eventDetails|errorMessage|createdAt|instanceId|reason
```

`reason` is **field 15** (optional justification; null serializes as empty). Changing its position or omitting it invalidates stored HMACs.
### Verification

`GET /api/v1/audit-logs/integrity-check` (Global Admin only) recomputes the HMAC for each entry in a date range and compares it to the stored value. Any mismatch is reported as a potential integrity violation. **Query parameters `from` and `to` (ISO-8601, inclusive start / exclusive end) are required;** omitting either returns 400 Bad Request. The response includes `entryViolations` (`items`, `totalCount`, `returnedCount`, `truncated`) with structured rows (`auditLogId`, `eventType`, `createdAt`, `reason`) — uncapped for the requested window.

`GET /api/v1/audit-logs/chain-integrity` returns `chainViolations` with the same list metadata shape plus checkpoint fields (`checkpointId`, `windowStart`, `windowEnd`, `violationType`, `detail`). Legacy string `violations` remains for backward compatibility.

### Instance Tracking

Each entry records the `instance_id` of the application instance that created it (from `EZKEY_INSTANCE_ID` env var). This enables forensic analysis in HA deployments -- you can trace which instance logged which event.

---

## 2. HMAC Key Management

### Key Specification

| Property | Value |
|----------|-------|
| Algorithm | HMAC-SHA256 |
| Key size | 256 bits (32 bytes) |
| Encoding | Base64 in a single-line text file |
| Default path (Docker) | `/etc/ezkey/secrets/audit-hmac.key` |
| Default path (Windows) | `C:\ProgramData\ezkey\secrets\audit-hmac.key` |

### Key Generation

**Option A -- Manual (recommended for production):**

```bash
openssl rand -base64 32 > /etc/ezkey/secrets/audit-hmac.key
chmod 600 /etc/ezkey/secrets/audit-hmac.key
```

**Option B -- Automatic (convenience for first startup):**

If the configured key file does not exist at startup, `AuditHmacService` auto-generates a 256-bit key using `java.security.SecureRandom` and writes it to the configured path. This mirrors the Tink master key auto-generation behavior.

### Key Sharing in HA Deployments

All application instances (admin-api, auth-api, integration-api) **must share the same HMAC key file**. In Docker, this is a shared volume mount; in Kubernetes, a shared Secret.

The key must be identical across instances because:
- Any instance may write audit entries
- Verification recomputes HMACs using the current key -- if different keys were used, valid entries would appear tampered

### Key Rotation

There is currently **no automatic key rotation** for the audit HMAC key. This is intentional:

- Rotating the key would make all previously signed entries unverifiable unless you also store which key version signed each entry
- HMAC signing keys are not encryption keys; rotate only if compromise is suspected
- If rotation becomes necessary, the procedure is:

1. Generate a new key file
2. Add a `key_version` column to `ezkey_audit_log` (migration)
3. Update `AuditHmacService` to load multiple keys by version
4. Verification would select the correct key based on `key_version`

**Current recommendation:** rotate the key only if it is suspected to be compromised. Document the key creation date for auditors.

### Configuration Reference

```properties
# Enable/disable HMAC signing (disable for dev without key file)
ezkey.audit.integrity.enabled=true

# Path to the HMAC key file
ezkey.audit.integrity.hmac-key-file=/etc/ezkey/secrets/audit-hmac.key

# Instance ID for HA traceability (from env var)
ezkey.audit.integrity.instance-id=${EZKEY_INSTANCE_ID:}
```

---

## 3. Chain Checkpoints -- Periodic Batch Chaining

### Problem Solved

Per-entry HMAC proves that **individual entries** have not been modified, but it cannot detect:
- An entry was **deleted** from the database
- A new entry was **inserted** between existing ones
- Entries were **reordered**

Chain checkpoints fill this gap by periodically "sealing" batches of entries into a linked chain.

### How It Works

A scheduled job (`AuditChainScheduler`) runs every 5 minutes (configurable) and processes time windows:

```
Time →
[00:00─00:05) [00:05─00:10) [00:10─00:15) [00:15─00:20) ...
   CP #1    →    CP #2    →    CP #3    →    CP #4    → ...
```

**For each uncheckpointed 5-minute window:**

#### Step 1 -- Compute entries_digest

Retrieve all audit entries in `[window_start, window_end)` ordered by `audit_log_id ASC`. Concatenate their `entry_hmac` values with pipe separators:

```
entries_digest = HMAC( hmac_1 | hmac_2 | hmac_3 | ... | hmac_N )
```

If the window has no entries: `entries_digest = HMAC("EMPTY_WINDOW")`

#### Step 2 -- Link to previous checkpoint

Fetch the most recent checkpoint's `chain_hmac`. This is the `prev_chain_hmac`.

#### Step 3 -- Compute chain_hmac

```
chain_hmac = HMAC( entries_digest | prev_chain_hmac )
```

For the very first checkpoint (no predecessor): `prev_chain_hmac` is replaced by the literal `"GENESIS"`.

#### Step 4 -- Save checkpoint

Store the checkpoint row in `ezkey_audit_chain_checkpoint`:

| Column | Description |
|--------|-------------|
| `window_start` | Start of the time window (inclusive) |
| `window_end` | End of the time window (exclusive) |
| `entry_count` | Number of audit entries in this window |
| `first_entry_id` | Lowest `audit_log_id` in the window |
| `last_entry_id` | Highest `audit_log_id` in the window |
| `entries_digest` | HMAC of concatenated entry HMACs |
| `prev_chain_hmac` | Previous checkpoint's `chain_hmac` (NULL for genesis) |
| `chain_hmac` | HMAC linking this window to all predecessors |

### Chain Linkage Visualized

```
Checkpoint #1 (genesis):
  entries_digest_1 = HMAC( e1.hmac | e2.hmac | e3.hmac )
  chain_hmac_1     = HMAC( entries_digest_1 | "GENESIS" )

Checkpoint #2:
  entries_digest_2 = HMAC( e4.hmac | e5.hmac )
  chain_hmac_2     = HMAC( entries_digest_2 | chain_hmac_1 )
                                               ↑ linked

Checkpoint #3:
  entries_digest_3 = HMAC( e6.hmac | e7.hmac | e8.hmac | e9.hmac )
  chain_hmac_3     = HMAC( entries_digest_3 | chain_hmac_2 )
                                               ↑ linked
```

Each checkpoint's `chain_hmac` transitively depends on **every previous checkpoint and every entry that existed in those windows**. Modifying, deleting, or inserting any entry breaks the chain from that point forward.

### Resilience and HA Safety

| Concern | Solution |
|---------|----------|
| **HA / multiple instances** | ShedLock ensures only one instance runs the job at a time |
| **Missed executions** | 60-minute lookback window catches up on all uncheckpointed windows |
| **Process restarts** | Lookback covers any gaps; the job is idempotent |
| **Concurrent writes** | Operates on already-committed entries (past windows) -- no write contention |
| **Duplicate runs** | UNIQUE constraint on `(window_start, window_end)` prevents double-inserts |

**First-run bootstrap:** On the very first scheduler run (no checkpoints exist), only the last completed 5-minute window is checkpointed instead of the full 60-minute lookback. On subsequent runs, the lookback window is clamped so that checkpoints are never created before the earliest existing checkpoint. This avoids generating empty "past" checkpoints that would suggest the system was running before it was first started.

### Verification

`GET /api/v1/audit-logs/chain-integrity` (Global Admin only) performs four checks:

1. **Recompute `entries_digest`** from current audit log entries (per checkpoint) -- detects entry insertion/deletion/reordering
2. **Verify chain linkage** -- `prev_chain_hmac` must match the previous checkpoint's `chain_hmac`
3. **Recompute `chain_hmac`** -- detects direct checkpoint tampering
4. **Temporal continuity** -- between each pair of consecutive checkpoints, verifies that the previous checkpoint's `window_end` equals the next checkpoint's `window_start`. Any gap (missing windows) is reported as an **undeclared gap**. Boundary coverage: if the requested range extends beyond the first or last checkpoint in the DB, the service only reports a **leading** or **trailing** undeclared gap when that period is within the system's checkpoint extent (i.e. there are checkpoints before `from` or after the last in range). When the first checkpoint in range is the earliest in the DB, a requested `from` before it is not reported as a gap ("before EZKey time"). When the last checkpoint in range is the latest in the DB, a requested `to` after it is not reported as a gap ("after EZKey time" or future).

The response includes `undeclaredGaps` (list of `{ gapStart, gapEnd, gapMinutes }`), `coverageStart`/`coverageEnd` (actual checkpoint boundaries), `effectiveFrom`/`effectiveTo` (range used for boundary gap reporting: clamped to coverage when the request extended before the first or after the last checkpoint), `continuousCoverage` (false when any undeclared gap exists), and `status`: `OK` (valid, no gaps), `UNDECLARED_GAP_DETECTED` (valid chain but temporal gaps), or `CHAIN_INTEGRITY_VIOLATION_DETECTED` (cryptographic violations). `intact` is true only when the chain is cryptographically valid and there are no undeclared gaps.

**Query parameters `from` and `to` (ISO-8601, inclusive start / exclusive end) are required;** omitting either returns 400 Bad Request.

### Configuration Reference

```properties
# Enable/disable chain checkpoints (runs only in admin-api)
ezkey.audit.chain.enabled=true

# Window size in minutes
ezkey.audit.chain.window-minutes=5

# Lookback for catch-up after restarts (minutes)
ezkey.audit.chain.lookback-minutes=60

# Cron schedule (every 5 minutes, one second after the window boundary)
ezkey.audit.chain.cron=1 */5 * * * ?
```

### Peripheral heartbeat supervision (`ezkey.audit.chain.heartbeat.*`)

Auth API and Integration API treat the **latest completed checkpoint** as an operational heartbeat from Admin API.
Peripheral processes compare wall-clock time against the checkpoint schedule window plus configurable grace phases.

**Semantic distinction:**

- **`GAP_DECLARATION` checkpoints** (`declare-gap`): temporal holes where **no audit activity exists** (total downtime).
- **Operational incidents** (`lifecycle/incidents`): audit entries may exist (cryptographic chain intact), but peripheral supervision detected stalled checkpoints — operators declare closure after recovery.

When peripherals enter fail-closed mode they respond with **HTTP 503** and stable RFC 9457 problem type
`https://ezkey.io/problems/system/audit-chain-heartbeat-degraded`, plus `Retry-After: 60` on blocked routes.

**Defined in:** `AuditChainHeartbeatProperties` (shared core); evaluated by `AuditChainHeartbeatGuardService`.

| Property | Type | Default | Notes |
|---|---|---|---|
| `ezkey.audit.chain.heartbeat.enabled` | `boolean` | `true` | Disable entirely only for isolated local troubleshooting (`docker-test` clean-start keeps peripheral heartbeat enabled). |
| `ezkey.audit.chain.heartbeat.required` | `boolean` | `true` | When `false`, MVC interceptor never blocks — incidents/alerts still evaluated when enabled. |
| `ezkey.audit.chain.heartbeat.grace-windows` | `int` | `2` | Outer supervision boundary measured in checkpoint window lengths after `latest.window_end`. |
| `ezkey.audit.chain.heartbeat.stop-before-next-window` | `Duration` | `PT1M` | Safety buffer subtracted before fail-closed threshold (inside grace boundary). |
| `ezkey.audit.chain.heartbeat.cache-ttl` | `Duration` | `PT5S` | Evaluation cache TTL to limit DB reads under load. |
| `ezkey.audit.chain.heartbeat.bootstrap-grace` | `Duration` | `PT10M` | After startup, tolerate missing checkpoints until first scheduler tick / empty DB. |
| `ezkey.audit.chain.heartbeat.admin-evaluate-scheduler-enabled` | `boolean` | `true` | Admin API-only fixed-delay ping so incidents/alerts sync without peripheral traffic. |
| `ezkey.audit.chain.heartbeat.admin-evaluate-fixed-delay-ms` | `long` | `30000` | Delay between Admin API heartbeat evaluations when scheduler enabled. |

**How the timing math lines up with Docker defaults (`window-minutes=5`, `grace-windows=2`, `stop-before-next-window=PT1M`):**

Relative to **`latest.window_end`** (exclusive end boundary of the most recent persisted checkpoint):

- **OK:** `now < latest.window_end + window_minutes`. The next periodic checkpoint interval has not elapsed yet relative to anchoring semantics — peripherals still treat checkpoints as punctual.
- **UNSUPERVISED_ACTIVITY:** starting at `latest.window_end + window_minutes` through `fail_closed_not_before_exclusive`, inclusive of the staleness onset but excluding the peripheral fail-close instant. Peripheral APIs **stay open** unless other controls apply.
- **Earliest peripheral fail-close instant:**  
  `fail_closed_not_before = latest.window_end + grace_windows × window_minutes − stop_before_next_window`  
  For the defaults above, that is **`latest.window_end + 9 minutes`**. Peripheral HTTP fail-closing is evaluated from that instant onward whenever `heartbeat.enabled=true` **and** `heartbeat.required=true`.
- **Scheduler boundary rule:** the Admin API checkpoint cron should run just after the five-minute
  boundary (`1 */5 * * * ?` by default), not exactly on second zero. Running exactly at the boundary
  can fire a few milliseconds early, causing the scheduler to treat the just-ending window as still
  open and defer its checkpoint to the next tick. That creates a false heartbeat loop where
  `DEGRADED_SERVICE` starts shortly before the normal deferred checkpoint appears.
- **Operational reminder:** peripherals look only at **`latest.checkpoint` timestamps** combined with **`ezkey.audit.chain.window-minutes`**. Misaligned `window-minutes` across Admin API vs Auth API / Integration API produces false-positive fail-closes; Docker profiles intentionally pin **`5`** on all three.

**Verifying cryptographic continuity across the last rolling hour:**

1. **Chain integrity (includes temporal + crypto checks):** `GET /api/v1/audit-logs/chain-integrity?from=...&to=...` (Global Admin). Choose `from/to` spanning the observation window — for an informal “past 60 minutes” pass, bracket the UTC hour boundary you care about. `intact=true` implies both valid chain linkage/HMAC recomputation inside the queried coverage window **and** no undeclared gaps inside that bounded range interpretation (see temporal gap rules documented above for boundary semantics).

2. **Checkpoint inventory:** complement with `GET /api/v1/audit-logs/chain-checkpoints?windowStartAfter=...` (pagination) when you expect **exactly twelve** contiguous `window_minutes` checkpoints for idle-but-healthy workloads during that hour — empty windows still checkpoint because `EMPTY_WINDOW` digests seal quiet periods.

**Runtime diagnostics:**

- Processes log `Audit chain heartbeat phase transition` at **INFO** only when supervision phase changes (`OK ↔ UNSERVISED_ACTIVITY ↔ DEGRADED_SERVICE` / bootstrapping).
- On startup (`heartbeat.enabled=true`), `AuditChainHeartbeatGuardService` emits a WARN when derived thresholds collapse the UNSUPERVISED_ACTIVITY cushion (typically `grace-windows=1` with default `stop-before-next-window`).
- Blocking interceptors/handlers log WARN lines with **`anchorCheckpointId`**, staleness timestamps, and the stable degraded problem **`type`** for each rejected peripheral request carrying new MFA work (`Auth API /pending`, `Integration API POST /auth-attempts`).
- **Recommended operator drill (clean-start Docker):**
  - **steady state (~10 min):** run **Chain integrity** for a bounded hour window and confirm checkpoints appear every **`window-minutes`** in **Chain checkpoints**.
  - **simulated stalled Admin API:** pause only `admin-api`; after wall-clock time exceeds the derived peripheral fail-close threshold (**default ≈ `latest.window_end + 9 minutes` with five-minute windows**) expect peripheral **503 heartbeat-degraded**, an open **`AUDIT_CHAIN_HEARTBEAT_STALE`** alert, and an **`IN_PROGRESS`** heartbeat incident row; Auth API / Integration API logs emit phase transitions describing the move into **`DEGRADED_SERVICE`**.
  - **recovery:** resume `admin-api` — once checkpoints advance again, alerts auto-resolve (**`HEARTBEAT_RESTORED`**), incidents advance to **`RECOVERED_PENDING_DECLARATION`** awaiting formal operator closure, and peripherals accept new MFA initiation traffic again.

---

## 4. API Endpoints Summary

**Archive lifecycle (policy-owned):** checkpoints progress
`ACTIVE → SEALED → [EXPORTED] → PURGEABLE → PURGED`. Auto-seal under `ezkey.audit.archive.*`
is the product path (`ezkey-core/CONFIGURATION.md` § Audit Log Archive). When
`external-archival-enabled=false` (default), the FSM skips `EXPORTED`. Physical deletion is
never a parallel age-only mode. `POST .../lifecycle/seal-archive` is exceptional maintenance,
not the normal operator workflow. Real external export remains `I-2026-06-28`.

| Endpoint | Access | Description |
|----------|--------|-------------|
| `GET /api/v1/audit-logs` | Admin | Query audit logs with filters and pagination |
| `GET /api/v1/audit-logs/chain-checkpoints` | Global Admin | Search audit chain checkpoints with filters and pagination |
| `GET /api/v1/audit-logs/integrity-check` | Global Admin | Verify per-entry HMAC signatures |
| `GET /api/v1/audit-logs/chain-integrity` | Global Admin | Verify chain checkpoint linkage |
| `POST /api/v1/audit-logs/integrity-validation/run` | Global Admin | Run retroactive detect (may raise/touch rupture alert) |
| `GET /api/v1/audit-logs/lifecycle/archive-eligibility` | Global Admin | Read lifecycle archive eligibility and awaiting-confirmation tranche |
| `POST /api/v1/audit-logs/lifecycle/confirm-archived` | Global Admin | Record that a sealed tranche was archived externally |
| `POST /api/v1/audit-logs/lifecycle/seal-archive` | Global Admin | Exceptional: seal a period for archival |
| `POST /api/v1/audit-logs/lifecycle/declare-gap` | Global Admin | Declare a downtime gap |
| `GET /api/v1/audit-logs/lifecycle/incidents` | Global Admin | List operational heartbeat incidents |
| `POST /api/v1/audit-logs/lifecycle/incidents/{id}/declare` | Global Admin | Declare closure after recovery |

**Chain checkpoints search** (`GET /api/v1/audit-logs/chain-checkpoints`): Paginated search with optional filters: `windowStartAfter`, `windowStartBefore` (ISO-8601), `entryCountMin`, `entryCountMax`, `checkpointType` (REGULAR, ARCHIVE_SEAL, GAP_DECLARATION), `createdAfter`, `createdBefore` (ISO-8601). Default sort: `windowStart,asc`. Use this primarily for lifecycle observability, anomaly investigation, and archive-confirmation context. It can also support exceptional maintenance flows such as `seal-archive` and `declare-gap` when needed.

**Archive eligibility** (`GET /api/v1/audit-logs/lifecycle/archive-eligibility`): Read-only lifecycle summary for observability. Returns whether external archival is enabled, whether confirmation is required, and which sealed tranche is currently awaiting confirmation.

**Archive confirmation** (`POST /api/v1/audit-logs/lifecycle/confirm-archived`): Records successful external archival by marking a sealed tranche as `EXPORTED`. This is the lifecycle confirmation step, not the external export workflow itself.

Both verification endpoints **require** `from` and `to` query parameters (ISO-8601). Omitting either returns 400 Bad Request with a message that a date range is required.

**Retroactive validation (detect path)** (`POST /api/v1/audit-logs/integrity-validation/run`): Global Admin only. Request body: `from` (inclusive), `to` (exclusive), optional `raiseAlert` (default `true`). Same orchestration as the nightly batch — may raise or touch `AUDIT_INTEGRITY_RUPTURE` when violations remain alert-eligible. Emits `NIGHTLY_INTEGRITY_VALIDATION_COMPLETED` with `event_details.triggerSource=OPERATOR`. Does **not** update the nightly job registry (operator runs are traced via HTTP response + completion audit). Operator POST accepts the **same** `[from, to)` bounds as GET verify; optional `ezkey.audit.integrity.retroactive.operator-max-window-hours` enforces a hard cap only when explicitly configured. Use GET verify endpoints for read-only forensic examine; use POST for detect → alert → reconcile lab flows.

**Declare gap** (`POST /api/v1/audit-logs/lifecycle/declare-gap`): Closes an undeclared gap by writing a `GAP_DECLARATION` checkpoint covering `[gapStart, gapEnd)` with a mandatory `justification` (min 10 chars). The endpoint accepts two equivalent modes:

- **Detected-gap mode (preferred for the Admin UI):** `{ gapStart, gapEnd, justification }`. The service binds the gap to the checkpoint immediately preceding `gapStart`. This is the only mode surfaced in the Admin UI, where each undeclared gap returned by `chain-integrity` is declared individually from its own row.
- **Anchor mode (CLI / scripted recovery):** `{ gapStart, gapEnd, anchorCheckpointId, justification }`. Use this when bootstrapping the chain or when scripting against a known checkpoint id. Available via the API and Postman collection; intentionally not exposed in the UI to keep the operator flow focused on what the chain check actually reports.

### Operator workflow (Admin UI)

The Integrity page (`/integrity`, Global Admin only) drives gap declaration from detection, not from manual input:

1. Open **Integrity**. Chain integrity **auto-runs** over the selected date
   range, or a 7-day UI default when none is set. Use **Chain integrity** to re-run
   after changing the range.
2. Review the **Undeclared gaps for consultation** list. For each row:
   - **Locate in timeline** focuses the checkpoint timeline on the gap window for context (toggle off with **Clear focus**).
   - **Declare** opens a dialog prefilled with the detected period and duration; the operator only enters a justification.
3. On success the dialog reports the new `GAP_DECLARATION` checkpoint and the chain check is automatically re-run, removing the row from the list.

When no undeclared gaps remain for the selected range the page shows an explicit "no undeclared gaps detected" confirmation rather than an empty list, so operators can distinguish "nothing to do" from "not yet checked".

### Operator alerts (`AUDIT_CHAIN_GAP_PENDING`)

When the audit-chain scheduler detects an undeclared gap during one of its periodic runs, it raises an
`AUDIT_CHAIN_GAP_PENDING` entry in the **alerts subsystem** (`/api/v1/alerts`, see [docs/ALERTS.md](ALERTS.md)).
The alert payload carries the anchor checkpoint id, the detected gap window, and an estimated duration.
While the gap remains undeclared, repeated detections **touch** the same open alert (incrementing
`occurrenceCount` and `lastSeenAt`) instead of producing duplicates. As soon as a Global Admin runs
**Declare gap** for that anchor (either from `/integrity` or via `POST /api/v1/audit-logs/lifecycle/declare-gap`),
the matching open alert is automatically resolved with reason `GAP_DECLARED`. This is the canonical
operator signal for chain-continuity issues; the dashboard surfaces the most recent open alerts for Global Admins.

### Operator alerts (`AUDIT_CHAIN_HEARTBEAT_STALE`)

When peripheral supervision detects fail-closed thresholds (checkpoint heartbeat stalled relative to configured grace),
the subsystem raises **`AUDIT_CHAIN_HEARTBEAT_STALE`** (`/api/v1/alerts`). Auto-resolution uses **`HEARTBEAT_RESTORED`**
once a fresh checkpoint advances the heartbeat again. Separate **`AUDIT_CHAIN_INCIDENT_DECLARED`** audit events record
operator declaration via `POST /api/v1/audit-logs/lifecycle/incidents/{id}/declare`.

### Entry integrity conciliation registry (B2.6)

Per-entry HMAC violations can be **acknowledged** by Global Admin reconcile without re-signing
`entry_hmac`. Rows live in `ezkey_audit_entry_integrity_conciliation` — an operational overlay
**distinct** from mutating audit log content.

| Aspect | Behavior |
|--------|----------|
| Entry reference | Immutable snapshot `(audit_log_id, audit_log_created_at)` at conciliation time |
| FK to audit log | **None** — registry must survive audit partition purge after archive seal |
| Fingerprint | SHA-256 of canonical entry form at conciliation; gates re-alert skip vs re-tamper |
| Meta-audit | `AUDIT_ENTRY_INTEGRITY_CONCILIATED` event + conciliation row for operator lookup |

After reconcile, verify still reports HMAC KO on the entry (tamper-evident); conciliation only
records that the organization accepted the known invalid state under justification.

**Archive / purge interaction:** conciliation rows are **durable overlays** — they must remain
queryable after the referenced audit partition is purged. This is the first Wave B table explicitly
designed for purge-surviving reference semantics; see
[`integrity-cluster-design-pack.md`](../product-docs/global/integrity-cluster-design-pack.md)
(§ Archive lifecycle and cross-table reference integrity).

---

## 5. Lab exploratory QA (Integrity cut-3)

Warm clean-start stacks usually have **no** open `AUDIT_INTEGRITY_RUPTURE` and
**no** sealed tranche with `confirmationRequired=true` (external archival defaults
off). For Admin UI cut-3 walks (Alerts deep-link → real reconcile; Confirm archived),
use the lab-only seed and operator steps in
[`docs/lab/INTEGRITY_CUT3_EXPLORATORY_QA.md`](lab/INTEGRITY_CUT3_EXPLORATORY_QA.md)
(`scripts/lab/seed-integrity-cut3-qa.sh`). Do not use `seed-alerts-ui-review` for
reconcile — that seed is Alerts list polish only.

---

## 6. What This Does NOT Cover (Accepted Limitations)

- **Real-time hash chain per entry:** rejected due to HA write-contention complexity; the 5-minute batch approach provides equivalent normative value
- **Automatic HMAC key rotation:** not implemented; rotation only needed if key is compromised
- **Google Tink for HMAC:** intentionally not used; standard JDK `javax.crypto.Mac` keeps the integrity layer independent from the confidentiality layer
- **Tamper-proof (absolute prevention):** this design provides tamper-evidence and detectability, not absolute prevention
