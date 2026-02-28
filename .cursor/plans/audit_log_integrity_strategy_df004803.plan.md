---
name: Audit Log Integrity Strategy
overview: Comprehensive normative analysis and pragmatic implementation strategy for audit log tamper-evidence, cryptographic chaining, HA instance management, and long-term archival with optional Git-based storage, aligned with SOC 2 requirements for self-hosted deployments.
todos:
  - id: phase1a-hmac
    content: "Phase 1a: Add instance_id and entry_hmac columns (Flyway V33), implement AuditHmacService with javax.crypto.Mac HmacSHA256, modify AuditLogService to compute HMAC at write time, add HMAC key configuration and loading from /etc/ezkey/audit-hmac-key"
    status: completed
  - id: phase1b-verify
    content: "Phase 1b: Add AuditIntegrityService for entry verification, add GET /api/v1/audit-logs/integrity-check endpoint, write unit and integration tests"
    status: completed
  - id: phase1c-chain
    content: "Phase 1c: Periodic chain checkpoints - ezkey_audit_chain_checkpoint table, AuditChainScheduler (5-min windows, 1-hour lookback), chain HMAC linking consecutive windows, verification endpoint and logic"
    status: completed
  - id: phase2-export
    content: "Phase 2: Implement AuditArchiveService for NDJSON partition export with manifest.json (including chain checkpoint data), modify AuditLogCleanupScheduler to archive-then-drop instead of delete"
    status: pending
  - id: phase2-git
    content: "Phase 2b: Git repository integration for archived partitions with GPG-signed tags, configurable remote push"
    status: pending
  - id: phase3-tooling
    content: "Phase 3: CLI commands for audit verify and audit export, scheduled background HMAC verification job, documentation of archival procedures"
    status: pending
isProject: false
---

# Audit Log Integrity and Non-Repudiation Strategy

## 1. SOC 2 Normative Requirements Analysis

### What SOC 2 Actually Requires (Trust Services Criteria)

- **CC7.2** (System Monitoring): The entity monitors system components for anomalies indicative of malicious acts, natural disasters, and errors; audit trail must be **complete and reliable**
- **CC7.3** (Evaluation of Events): Detected events are evaluated to determine if they could represent security incidents
- **CC8.1** (Change Management): Changes to infrastructure and software are authorized, designed, developed, configured, documented, tested, approved, and implemented
- **CC6.1** (Logical Access): Controls exist to restrict logical access; **audit logs proving this must be trustworthy**

### The Key Requirement: Tamper-Evidence, Not Tamper-Proof

SOC 2 does NOT require that audit logs be mathematically impossible to alter. It requires:

1. **Evidence** that logs have not been tampered with (tamper-evidence)
2. **Separation of duties**: the person/system generating logs should not be the same as the person/system who can modify them
3. **Monitoring**: mechanisms to detect if tampering has occurred
4. **Retention**: logs must be retained for the audit period (typically 12 months for Type II, with some frameworks expecting up to 7 years for financial data)

In cloud environments (AWS CloudWatch, Azure Monitor, GCP Cloud Logging), this is handled by the cloud provider (immutable log stores, access controls, separate IAM). For **self-hosted / on-prem**, the operator must implement equivalent controls.

### What Competitors Do


| Product             | Audit Integrity Mechanism                                                  | Self-Hosted Support         |
| ------------------- | -------------------------------------------------------------------------- | --------------------------- |
| **Duo**             | Cloud-only, Cisco-managed logs                                             | N/A (SaaS)                  |
| **Okta**            | System Log API, immutable in cloud                                         | N/A (SaaS)                  |
| **PrivacyIDEA**     | No cryptographic signing; relies on DB access controls + syslog forwarding | syslog + rsyslog to SIEM    |
| **HashiCorp Vault** | SHA-256 HMAC per audit entry with rotating HMAC keys                       | Yes, file + syslog backends |


**Key insight**: HashiCorp Vault is the closest comparable self-hosted product. Their approach is **per-entry HMAC** (not a chain), which is simpler and proven sufficient for SOC 2.

---

## 2. Analysis of Approaches

### Option A: Cryptographic Hash Chain (Blockchain-Lite)

Each audit log entry includes a hash of (previous_hash + current_entry_data), forming a chain.

**Pros:**

- Tamper-evidence for the entire sequence (modifying one entry breaks the chain)
- Non-repudiation of ordering

**Cons:**

- **HA complexity is severe**: Multiple instances writing concurrently means multiple chains, requiring either a global sequence lock (performance bottleneck) or per-instance chains (complex validation)
- Requires per-instance chain management: `instance_id` + `sequence_number` + `previous_hash`
- Chain restart on process restart requires reading the last entry from DB
- Partition archival requires exporting the chain metadata
- Adds 3-4 new columns to an already wide table
- **Performance**: sequential dependency on previous entry hash creates a write serialization point

**Verdict**: High effort, high complexity, marginal SOC 2 benefit over simpler approaches. Over-engineered for the 80/20 goal.

### Option B: Per-Entry HMAC Signature (HashiCorp Vault Model)

Each audit log entry gets an HMAC-SHA256 computed over its canonical content, using a dedicated signing key.

**Pros:**

- Each entry is independently verifiable (no chain dependency)
- HA-friendly: all instances use the same HMAC key (loaded from shared secret)
- No ordering dependency, no sequence numbers needed
- Simple validation: recompute HMAC and compare
- Partition archival: HMAC travels with the entry, self-contained
- Proven SOC 2 acceptable (HashiCorp Vault audit backend)

**Cons:**

- Does not prove ordering (but `created_at` + `audit_log_id` provide this)
- HMAC key management: must be protected (same level as Tink master key)

**Verdict**: **Recommended**. 20% effort for 80% of the security value. Aligned with project values.

### Option C: Git-Based Archival as Sole Integrity Mechanism

Export audit log partitions to JSON, commit to a Git repository.

**Pros:**

- Git provides content-addressable storage (SHA-1/SHA-256 integrity)
- Familiar to developers (DX alignment)
- Built-in diff and history
- Can be replicated/mirrored for redundancy

**Cons:**

- **Does NOT protect live data**: Git only protects exported/archived data; between creation and archival, entries can be tampered with
- **Not real-time**: integrity is only established at export time
- Git SHA integrity can be bypassed if attacker has write access to the repo
- SOC 2 auditors may question Git as a sole control (not an industry-standard audit log integrity mechanism)
- Large JSON files in Git are inefficient (binary diff is poor for large JSON)

**Verdict**: Excellent as an **archival complement** (Phase 2), but insufficient as the sole integrity mechanism. Should be paired with per-entry HMAC.

### Option D: Hybrid Approach (Recommended Foundation)

Combine **per-entry HMAC** (live protection) with **Git-based archival** (long-term retention + independent verification).

### Option E: Periodic Chain Checkpoints (Completeness Enhancement) -- INCLUDED

**Concept**: A batch job runs every N minutes, takes all audit entries in that time window, computes a digest HMAC over the ordered set of entries, and chains it with the previous checkpoint's HMAC. This creates a linked sequence of checkpoints without the HA complexity of real-time chaining.

**What gap does this fill?**

Per-entry HMAC (Option B) proves each entry is individually intact, but it cannot detect:

- **Row deletion**: An attacker deletes entries; remaining HMACs are still valid
- **Row insertion**: An attacker fabricates entries (if they have the HMAC key); each new entry has a valid HMAC
- **Reordering**: Entries could be shuffled without breaking individual HMACs

The periodic chain specifically detects all three: any change to the set of entries in a given window breaks the chain at the next checkpoint.

**Why this works where real-time chaining (Option A) doesn't:**

- Operates on **already-committed data** (past tense) -- no write contention, no HA serialization
- Single instance executes via ShedLock -- no multi-instance chain coordination
- Process restarts are handled by a 1-hour lookback window (12 x 5-minute slots)
- Zero impact on audit log write performance

**Is the 5-minute delay normatively defensible?**

**Yes.** Key references:

- **AWS CloudTrail** delivers logs with ~15-minute delay and is the SOC 2 gold standard
- **NIST SP 800-92** specifies "timely" protection, not instantaneous
- The attack that exploits the window requires **simultaneous** DB write access + HMAC key access + action before next checkpoint -- an extremely narrow scenario
- SOC 2 auditors evaluate whether controls are **reasonable**, not mathematically perfect
- A 5-minute operational chain is qualitatively different from a weeks-long archival delay (Git): auditors see **continuous operational monitoring** vs **periodic batch archival**

**Verdict**: **Included as Phase 1c.** Low marginal cost (~1 day), fills a specific and real gap in completeness proof, elevates the integrity story from "adequate for SOC 2" to "strong for a self-hosted security product." Configurable and optional (can be disabled for simple deployments).

---

## 3. Recommended Strategy: Hybrid HMAC + Git Archival

### Phase 1: Per-Entry HMAC Signing (Core Integrity)

**New columns on `ezkey_audit_log`:**

- `instance_id` (VARCHAR 50): identifies which application instance created the entry
- `entry_hmac` (VARCHAR 88): Base64-encoded HMAC-SHA256 of the canonical entry content

**HMAC Key Management:**

- Dedicated HMAC key, separate from Tink encryption master key (separation of concerns)
- Stored in the same secure location as Tink master key (`/etc/ezkey/audit-hmac-key`)
- 256-bit key, Base64-encoded, loaded at startup
- All HA instances share the same key (mounted via shared volume, same as Tink keyset)
- Key rotation: supported via key versioning (include `hmac_key_version` in the signed content or as a column)

**Canonical form for HMAC computation:**

```
audit_log_id|event_type|event_action|event_status|api_name|ip_address|admin_id|
integration_id|enrollment_id|tenant_id|event_details|error_message|created_at|instance_id
```

Pipe-delimited, null fields as empty string, `created_at` in ISO-8601 UTC. Deterministic, no ambiguity.

**Signing at write time:**

- [AuditLogService.log()](ezkey-core/src/main/java/org/ezkey/audit/service/AuditLogService.java): After building the `AuditLog` entity and before `save()`, compute HMAC and set on entity
- Instance ID injected via `EZKEY_INSTANCE_ID` environment variable (already exists in HA compose)

**Validation:**

- New service `AuditIntegrityService` with method `verifyEntry(AuditLog entry) -> boolean`
- Admin API endpoint: `GET /api/v1/audit-logs/integrity-check?from=...&to=...` returns summary of verified/failed entries
- Scheduled job (optional): periodic background verification of recent entries

**Should we use Google Tink?**

- **No** for HMAC signing. Tink is designed for encryption (AEAD primitives). Using standard `javax.crypto.Mac` with `HmacSHA256` is simpler, has zero additional dependencies, and is the same approach HashiCorp Vault uses.
- **Existing Tink infrastructure** stays dedicated to encryption-at-rest. Clean separation of concerns.

**Should we use the same key?**

- **No**. The HMAC signing key must be separate from the Tink master key. Reasons:
  - Different purpose (integrity vs. confidentiality)
  - Different rotation lifecycle
  - Compromise of one should not compromise the other
  - SOC 2 separation of duties principle

**HA and Instance Chaining:**

There is no chaining between instances. Each entry is independently signed. The `instance_id` field allows forensic analysis of which instance produced which entries.

**Process Restart Continuity:**

No special handling needed. Since entries are independently signed (not chained), there is no "bridge" to build between old and new sequences. The process simply starts signing new entries with the same shared HMAC key. The `instance_id` + `created_at` + `audit_log_id` provide continuity tracking.

### Phase 1c: Periodic Chain Checkpoints (Completeness Proof)

**New table: `ezkey_audit_chain_checkpoint`**

```sql
CREATE TABLE ezkey_audit_chain_checkpoint (
    checkpoint_id    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    window_start     TIMESTAMPTZ NOT NULL,
    window_end       TIMESTAMPTZ NOT NULL,
    entry_count      INT NOT NULL,
    first_entry_id   BIGINT,                 -- NULL if entry_count = 0
    last_entry_id    BIGINT,                 -- NULL if entry_count = 0
    entries_digest   VARCHAR(88) NOT NULL,   -- HMAC of ordered entry_hmac concatenation
    prev_chain_hmac  VARCHAR(88),            -- NULL for the very first checkpoint
    chain_hmac       VARCHAR(88) NOT NULL,   -- HMAC(entries_digest + "|" + prev_chain_hmac)
    created_at       TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP NOT NULL,
    UNIQUE (window_start, window_end)
);
```

**Batch algorithm (runs every 5 minutes, configurable):**

1. Determine current time, round down to nearest 5-minute boundary
2. Look back 1 hour (12 windows) to find any uncheckpointed windows
3. For each missing window (oldest first):
  a. Query `SELECT entry_hmac FROM ezkey_audit_log WHERE created_at >= window_start AND created_at < window_end ORDER BY audit_log_id ASC`
   b. Compute `entries_digest = HMAC(entry_hmac_1 + "|" + entry_hmac_2 + "|" + ... + "|" + entry_hmac_N)` -- empty windows use `HMAC("EMPTY_WINDOW")`
   c. Fetch previous checkpoint's `chain_hmac` (or NULL if first ever)
   d. Compute `chain_hmac = HMAC(entries_digest + "|" + prev_chain_hmac)` -- if prev is NULL, use `HMAC(entries_digest + "|" + "GENESIS")`
   e. Insert checkpoint row

**Key design properties:**

- **Idempotent**: UNIQUE constraint on (window_start, window_end) prevents duplicate checkpoints
- **HA-safe**: Uses ShedLock (`@SchedulerLock(name = "AUDIT_CHAIN_CHECKPOINT")`)
- **Catch-up**: 1-hour lookback handles process restarts, deployments, and brief outages
- **Empty windows**: Still checkpointed (preserves chain continuity even during low-activity periods)
- **Same HMAC key**: Reuses the audit HMAC key from Phase 1a (no additional key management)

**Verification:**

- `AuditChainVerificationService.verifyChain(from, to)`: Recomputes entries_digest for each window, verifies chain linkage
- Admin API: `GET /api/v1/audit-logs/chain-integrity?from=...&to=...` returns chain verification report
- Detects: missing entries, inserted entries, deleted entries, reordered entries, modified entries, gap in checkpoints

**Configuration:**

```properties
ezkey.audit.chain.enabled=true
ezkey.audit.chain.window-minutes=5
ezkey.audit.chain.lookback-minutes=60
ezkey.audit.chain.cron=0 */5 * * * ?
```

**What the three layers prove together:**


| Layer             | Proves                      | Detects                             |
| ----------------- | --------------------------- | ----------------------------------- |
| Per-entry HMAC    | Individual entry integrity  | Entry content modification          |
| Chain checkpoints | Set completeness + ordering | Row insertion, deletion, reordering |
| Git archival      | Long-term archive integrity | Post-archival tampering             |


### Phase 2: Git-Based Partition Archival (Long-Term Retention)

**When the 13th month arrives (partition archival):**

1. **Export**: A scheduled job (or CLI command) exports the oldest partition as NDJSON (one JSON object per line, better for Git diff than a single JSON array)
2. **Include metadata file**: alongside the NDJSON, include a `manifest.json`:

```json
{
  "partition": "ezkey_audit_log_2025_01",
  "exportedAt": "2026-02-01T00:00:00Z",
  "exportedBy": "admin-api-1",
  "entryCount": 48573,
  "firstEntryId": 1,
  "lastEntryId": 48573,
  "hmacKeyVersion": 1,
  "sha256Checksum": "abc123...",
  "integrityReport": {
    "totalEntries": 48573,
    "validHmac": 48573,
    "invalidHmac": 0
  }
}
```

1. **Git repository structure**:

```
ezkey-audit-archive/
  2025/
    01/
      audit_log_2025_01.ndjson
      manifest.json
    02/
      ...
```

1. **Git commit**: Each monthly export is a single commit with a GPG-signed tag (using a dedicated signing key)
2. **Drop partition**: After successful export + commit + push, drop the DB partition

**Why Git works well here:**

- Git's content-addressable storage (SHA-256 since Git 2.29) provides independent integrity verification
- GPG-signed tags provide non-repudiation of the archive
- `git log --verify-tag` provides auditor-friendly verification
- Repository can be mirrored to multiple remotes for redundancy
- Developers and DevOps are familiar with Git workflows

**Why NDJSON over other formats:**

- One line per entry: Git tracks line-level diffs efficiently
- Streaming-friendly: can process without loading entire file in memory
- JSON is human-readable and tool-friendly (jq, etc.)
- Gzip compression for storage efficiency (Git handles this internally)

**Does Git replace the need for HMAC?**

- **No.** Git protects the archive after export. HMAC protects entries from creation to archival. They are complementary:
  - HMAC: "this entry has not been modified since it was written"
  - Git: "this archive has not been modified since it was exported"
  - Together: complete chain of custody from creation to long-term storage

### Phase 3: Verification Tooling (Operational)

- CLI command: `ezkey audit verify --month 2025-01` (verifies HMAC of all entries in a partition)
- CLI command: `ezkey audit export --month 2025-01 --output /path/to/repo` (exports partition to Git repo)
- Admin API endpoint: integrity check summary for dashboards
- Scheduled background verification (configurable, e.g., weekly)

---

## 4. Files to Create/Modify

### New Files

**Phase 1a (Per-entry HMAC):**

- `ezkey-core/src/main/java/org/ezkey/audit/integrity/AuditHmacService.java` - HMAC computation and verification
- `ezkey-core/src/main/java/org/ezkey/audit/integrity/AuditHmacProperties.java` - Configuration properties
- `ezkey-core/src/main/resources/db/migration/V33__add_audit_integrity_columns.sql` - instance_id + entry_hmac columns

**Phase 1b (Verification):**

- `ezkey-core/src/main/java/org/ezkey/audit/integrity/AuditIntegrityService.java` - Entry verification logic

**Phase 1c (Chain checkpoints):**

- `ezkey-core/src/main/java/org/ezkey/audit/integrity/AuditChainCheckpoint.java` - JPA entity for checkpoint table
- `ezkey-core/src/main/java/org/ezkey/audit/integrity/AuditChainCheckpointRepository.java` - Repository
- `ezkey-core/src/main/java/org/ezkey/audit/integrity/AuditChainScheduler.java` - Scheduled batch job (ShedLock)
- `ezkey-core/src/main/java/org/ezkey/audit/integrity/AuditChainVerificationService.java` - Chain verification logic
- `ezkey-core/src/main/resources/db/migration/V34__create_audit_chain_checkpoint.sql` - Checkpoint table

**Phase 2 (Archival):**

- `ezkey-core/src/main/java/org/ezkey/audit/archive/AuditArchiveService.java` - Partition export logic
- `ezkey-core/src/main/java/org/ezkey/audit/archive/AuditArchiveScheduler.java` - Monthly archival job

### Modified Files

- [AuditLog.java](ezkey-core/src/main/java/org/ezkey/audit/domain/entity/AuditLog.java) - Add `instanceId`, `entryHmac` fields
- [AuditLogService.java](ezkey-core/src/main/java/org/ezkey/audit/service/AuditLogService.java) - Compute HMAC before save
- [AuditLogController.java](ezkey-admin-api/src/main/java/org/ezkey/admin/controller/AuditLogController.java) - Add integrity-check endpoint
- [AuditLogCleanupScheduler.java](ezkey-core/src/main/java/org/ezkey/audit/service/AuditLogCleanupScheduler.java) - Replace delete with archive-then-drop

---

## 5. Decision Summary


| Question                          | Decision                         | Rationale                                                                                    |
| --------------------------------- | -------------------------------- | -------------------------------------------------------------------------------------------- |
| HMAC or hash chain?               | **Per-entry HMAC**               | HA-friendly, independently verifiable, proven (Vault)                                        |
| Use Google Tink?                  | **No**                           | Standard javax.crypto.Mac is simpler, no extra dependency                                    |
| Same key as encryption?           | **No**                           | Separate concerns, separate rotation, SOC 2 separation of duties                             |
| Real-time chain across instances? | **No**                           | Independent entries with instance_id; real-time chain adds HA complexity for minimal benefit |
| Periodic chain checkpoints?       | **Yes (Phase 1c)**               | Low cost, fills completeness gap (detects deletion/insertion), HA-safe via batch + ShedLock  |
| 5-minute checkpoint interval?     | **Yes, defensible**              | AWS CloudTrail uses ~15 min; NIST SP 800-92 requires "timely" not instantaneous              |
| Process restart bridge?           | **1-hour lookback window**       | Catch-up mechanism handles restarts, deployments, brief outages gracefully                   |
| Git for archival?                 | **Yes, as complement**           | Excellent for long-term retention, developer-friendly, integrity via SHA-256 + GPG tags      |
| Git replaces HMAC?                | **No**                           | Git protects archive; HMAC protects live data. Complementary layers                          |
| Archive format?                   | **NDJSON + manifest.json**       | Git-friendly, streaming, human-readable                                                      |
| Retention policy?                 | **12 months live, then archive** | Configurable; meets SOC 2 while keeping DB performant                                        |


---

## 6. Implementation Effort Estimate


| Phase                        | Scope                                                                        | Effort                | Security Value                                    |
| ---------------------------- | ---------------------------------------------------------------------------- | --------------------- | ------------------------------------------------- |
| Phase 1a: Per-entry HMAC     | Migration + AuditHmacService + AuditLogService mod + HMAC key management     | Medium (2-3 days)     | Individual entry integrity (SOC 2 baseline)       |
| Phase 1b: Entry verification | AuditIntegrityService + integrity-check endpoint + tests                     | Low (1 day)           | Operational verification capability               |
| Phase 1c: Chain checkpoints  | Checkpoint table + AuditChainScheduler + chain verification endpoint + tests | Low-Medium (1-2 days) | Completeness proof (deletion/insertion detection) |
| Phase 2: Git archival        | Export service + scheduler + manifest (incl. chain data) + Git integration   | Medium (2-3 days)     | Long-term retention + independent verification    |
| Phase 3: Tooling             | CLI commands + scheduled background verification + documentation             | Low (1-2 days)        | Operational excellence                            |


Total: ~6-10 days of focused development for comprehensive audit log integrity coverage in a self-hosted context.

**Incremental value curve:**

- Phase 1a alone: satisfies SOC 2 normative requirements (80% of value)
- Phase 1a + 1c: elevates from "adequate" to "strong" integrity (90% of value)
- Phase 1a + 1c + Phase 2: complete chain of custody from creation to long-term archive (98%)
- Phase 3: operational polish and developer experience (100%)

