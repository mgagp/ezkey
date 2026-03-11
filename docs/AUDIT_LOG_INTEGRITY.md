# Audit Log Integrity -- HMAC Signing & Chain Checkpoints

> **SOC 2 Controls:** CC7.2 (System Monitoring), CC6.1 (Logical Access)
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
integrationId|enrollmentId|tenantId|eventDetails|errorMessage|createdAt|instanceId
```

### Verification

`GET /api/v1/audit-logs/integrity-check` (Global Admin only) recomputes the HMAC for each entry in a date range and compares it to the stored value. Any mismatch is reported as a potential integrity violation. **Query parameters `from` and `to` (ISO-8601, inclusive start / exclusive end) are required;** omitting either returns 400 Bad Request.

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

All application instances (admin-api, auth-api, m2m-api) **must share the same HMAC key file**. In Docker, this is a shared volume mount; in Kubernetes, a shared Secret.

The key must be identical across instances because:
- Any instance may write audit entries
- Verification recomputes HMACs using the current key -- if different keys were used, valid entries would appear tampered

### Key Rotation

There is currently **no automatic key rotation** for the audit HMAC key. This is intentional:

- Rotating the key would make all previously signed entries unverifiable unless you also store which key version signed each entry
- For SOC 2, key rotation is recommended but not mandatory for HMAC signing keys (they are not encryption keys)
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

### Verification

`GET /api/v1/audit-logs/chain-integrity` (Global Admin only) performs three checks for each checkpoint in a date range:

1. **Recompute `entries_digest`** from current audit log entries -- detects entry insertion/deletion/reordering
2. **Verify chain linkage** -- `prev_chain_hmac` must match the previous checkpoint's `chain_hmac`
3. **Recompute `chain_hmac`** -- detects direct checkpoint tampering

**Query parameters `from` and `to` (ISO-8601, inclusive start / exclusive end) are required;** omitting either returns 400 Bad Request.

### Configuration Reference

```properties
# Enable/disable chain checkpoints (runs only in admin-api)
ezkey.audit.chain.enabled=true

# Window size in minutes
ezkey.audit.chain.window-minutes=5

# Lookback for catch-up after restarts (minutes)
ezkey.audit.chain.lookback-minutes=60

# Cron schedule (every 5 minutes)
ezkey.audit.chain.cron=0 */5 * * * ?
```

---

## 4. API Endpoints Summary

| Endpoint | Access | Description |
|----------|--------|-------------|
| `GET /api/v1/audit-logs` | Admin | Query audit logs with filters and pagination |
| `GET /api/v1/audit-logs/chain-checkpoints` | Global Admin | Search audit chain checkpoints with filters and pagination |
| `GET /api/v1/audit-logs/integrity-check` | Global Admin | Verify per-entry HMAC signatures |
| `GET /api/v1/audit-logs/chain-integrity` | Global Admin | Verify chain checkpoint linkage |
| `POST /api/v1/audit-logs/lifecycle/seal-archive` | Global Admin | Seal a period for archival |
| `POST /api/v1/audit-logs/lifecycle/declare-gap` | Global Admin | Declare a downtime gap |

**Chain checkpoints search** (`GET /api/v1/audit-logs/chain-checkpoints`): Paginated search with optional filters: `windowStartAfter`, `windowStartBefore` (ISO-8601), `entryCountMin`, `entryCountMax`, `checkpointType` (REGULAR, ARCHIVE_SEAL, GAP_DECLARATION), `createdAfter`, `createdBefore` (ISO-8601). Default sort: `windowStart,asc`. Use this to discover checkpoints for SEAL range selection (e.g. `checkpointIdFrom` / `checkpointIdTo`) and Declare Gap (anchor checkpoint).

Both verification endpoints **require** `from` and `to` query parameters (ISO-8601). Omitting either returns 400 Bad Request with a message that a date range is required.

---

## 5. What This Does NOT Cover (Accepted Limitations)

- **Real-time hash chain per entry:** rejected due to HA write-contention complexity; the 5-minute batch approach provides equivalent normative value
- **Automatic HMAC key rotation:** not implemented; rotation only needed if key is compromised
- **Google Tink for HMAC:** intentionally not used; standard JDK `javax.crypto.Mac` keeps the integrity layer independent from the confidentiality layer
- **Tamper-proof (absolute prevention):** SOC 2 requires tamper-evidence and detectability, which this design satisfies
