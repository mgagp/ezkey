# Encryption Key Rotation Procedures

**Document Version**: 1.0  
**Last Updated**: December 2025  
**Classification**: Internal - Operational Procedures  
**Owner**: Security Team

---

## Table of Contents

1. [Overview](#overview)
2. [Key Rotation Policy](#key-rotation-policy)
3. [Operational Procedures](#operational-procedures)
4. [Audit](#audit)
5. [Recovery Procedures](#recovery-procedures)
6. [Runbook for Operators](#runbook-for-operators)
7. [Appendix](#appendix)

---

## Overview

### Purpose

This document defines the operational procedures for encryption key rotation in Ezkey (encryption-at-rest key lifecycle):

- Access controls around the Tink master key and Admin API key operations
- Encryption of sensitive data at rest
- Key management, rotation, and re-encryption
- Operator-visible audit of key lifecycle events

### Scope

This document covers:

- Automated key rotation procedures
- Manual key rotation procedures
- Batch re-encryption procedures
- Key lifecycle management
- Audit trail requirements
- Recovery and rollback procedures

### Definitions

| Term | Definition |
|------|------------|
| **Primary Key** | The encryption key currently used for encrypting new data |
| **Enabled Key** | A key that can decrypt data but is not used for new encryption |
| **Disabled Key** | A key that is no longer used and may be archived |
| **Key Rotation** | The process of introducing a new primary key and demoting the old primary key |
| **Re-encryption Batch** | A batch job that re-encrypts existing data from an old key to a new key |
| **Keyset** | The encrypted file containing all encryption keys managed by Tink |

---

## Key Rotation Policy

### Rotation Schedule

**Automated Rotation**:
- **Frequency**: Configurable (default: every 90 days)
- **Schedule**: Daily check at 2:00 AM UTC (configurable via `ezkey.encryption.rotation.schedule`)
- **Trigger**: Primary key age >= `max-key-age-days` (default: 90 days)

**Manual Rotation**:
- Available via Admin API endpoint: `POST /api/v1/encryption-keys/rotate`
- Requires admin authentication
- Can be triggered at any time for emergency rotation

### Key Lifecycle

```
INTRODUCED → ENABLED → PRIMARY → ENABLED → DISABLED
     ↓          ↓         ↓         ↓         ↓
   New key   Can decrypt  Active   Old key  Archived
```

### Key Retention

- **Minimum Keys in Keyset**: 2 (for decryption of old data)
- **Maximum Keys in Keyset**: 5 (to prevent keyset bloat)
- **Auto-Disable After**: 180 days (configurable via `auto-disable-days`)
- **Backup Retention**: 365 days (configurable via `backup-retention-days`)

---

## Operational Procedures

### Automated Key Rotation

#### Process Flow

1. **Scheduled Job Execution**
   - Job runs according to cron schedule
   - Checks if rotation is enabled (`ezkey.encryption.rotation.enabled=true`)
   - Verifies primary key age >= `max-key-age-days`

2. **Pre-Rotation Checks**
   - Verify keyset is initialized
   - Verify database connection
   - Check for active re-encryption batches (should be none or completed)

3. **Backup Creation** (if configured)
   - Create timestamped backup of keyset file
   - Store backup in secure location
   - Log backup creation in audit log

4. **Key Rotation**
   - Generate new key using Tink KeysetManager
   - Promote new key to PRIMARY status
   - Demote old primary key to ENABLED status
   - Save encrypted keyset to disk

5. **Database Synchronization**
   - Create `EncryptionKey` record for new key (status: PRIMARY)
   - Update old key record (status: PRIMARY → ENABLED)
   - Update timestamps (`promoted_primary_at` for new key)

6. **Audit Logging**
   - Log `KEY_INTRODUCED` event
   - Log `KEY_PROMOTED_PRIMARY` event
   - Log `KEY_DEMOTED` event (for old primary)
   - Include key IDs, timestamps, and trigger source

7. **Post-Rotation**
   - Verify new key is active
   - Verify old key can still decrypt
   - Trigger re-encryption batch creation (if configured)

#### Monitoring

Monitor the following metrics:

- **Rotation Success Rate**: Should be 100%
- **Rotation Duration**: Typically < 5 seconds
- **Database Sync Status**: Verify keys are synchronized
- **Audit Log Completeness**: All events logged

#### Alerts

Configure alerts for:

- Rotation failure (exception logged)
- Database sync failure
- Missing audit logs
- Keyset backup failure

### Manual Key Rotation

#### Prerequisites

- Admin authentication token
- Access to Admin API
- Knowledge of current key status

#### Procedure

1. **Check Current Key Status**
   ```bash
   GET /api/v1/encryption-keys
   ```

2. **Verify Rotation Readiness**
   - Check for active re-encryption batches
   - Verify keyset file exists and is accessible
   - Confirm database connectivity

3. **Trigger Manual Rotation**
   ```bash
   POST /api/v1/encryption-keys/rotate
   Authorization: Bearer <admin-token>
   ```

4. **Verify Rotation Success**
   - Check response: `newPrimaryKeyId` should be present
   - Query keys: `GET /api/v1/encryption-keys`
   - Verify new key has PRIMARY status
   - Verify old key has ENABLED status

5. **Review Audit Logs**
   - Query audit logs for `KEY_INTRODUCED` event
   - Verify all events are logged correctly
   - Check for any errors

#### Emergency Rotation

For emergency rotation (e.g., suspected key compromise):

1. **Immediate Actions**
   - Trigger manual rotation immediately
   - Disable old key immediately (via database update)
   - Create emergency backup

2. **Post-Emergency**
   - Investigate compromise (if applicable)
   - Document incident
   - Update security procedures if needed

### Batch Re-encryption

#### Process Flow

1. **Batch Creation**
   - Service identifies records encrypted with old keys
   - Creates `ReencryptionBatch` record for each table/column combination
   - Sets status to `PENDING`

2. **Batch Processing**
   - Processes records in configurable batch size (default: 500)
   - Updates batch progress (`records_done`, `progress_pct`)
   - Handles errors gracefully (increments `records_failed`)

3. **Progress Tracking**
   - Updates `last_batch_at` timestamp
   - Stores `last_record_id` for resume capability
   - Logs `REENCRYPTION_BATCH_PROGRESS` events

4. **Completion**
   - Sets status to `COMPLETED` when all records processed
   - Updates `completed_at` timestamp
   - Logs `REENCRYPTION_COMPLETED` event

#### Configuration

Key configuration parameters:

```properties
# Re-encryption enabled
ezkey.encryption.reencryption.enabled=true

# Schedule (default: daily at 3 AM)
ezkey.encryption.reencryption.schedule=0 0 3 * * ?

# Batch size (default: 500 records)
ezkey.encryption.reencryption.batch-size=500

# Throttle between batches (default: 100ms)
ezkey.encryption.reencryption.throttle-ms=100

# Maximum batches per run (default: 100)
ezkey.encryption.reencryption.max-batches-per-run=100

# Maximum duration per run (default: 60 minutes)
ezkey.encryption.reencryption.max-duration-minutes=60

# Auto-retry failed batches (default: true)
ezkey.encryption.reencryption.auto-retry-failed=true

# Auth-attempt throughput (Admin Docker defaults: 4 workers + 4 shards)
# See docs/REENCRYPTION_OPERATIONS.md §9 — pair workers >= auth-attempt-shard-count
ezkey.encryption.reencryption.parallel-batch-workers=4
ezkey.encryption.reencryption.auth-attempt-shard-count=4
```

#### Monitoring

Monitor the following:

- **Batch Completion Rate**: Should approach 100%
- **Batch Duration**: Varies by data volume
- **Failed Records**: Should be minimal (< 1%)
- **Resume Capability**: Verify `last_record_id` is stored

#### Error Handling

**Failed Batches**:
- Status set to `FAILED`
- Error message stored in `error_message`
- `error_count` incremented
- `retry_count` incremented
- Batch can be resumed manually or automatically

**Resume Procedure**:
```bash
POST /api/v1/encryption-keys/batches/{batchId}/resume
Authorization: Bearer <admin-token>
```

---

## Audit

### Audit Trail Requirements

All key rotation and re-encryption operations must be logged in `ezkey_audit_log` with the following event types:

#### Key Management Events

| Event Type | Description | Required Fields |
|------------|-------------|-----------------|
| `KEY_INTRODUCED` | New key added to keyset | `encryptionKeyId`, `algorithm`, `createdBy` |
| `KEY_PROMOTED_PRIMARY` | Key promoted to primary | `encryptionKeyId`, `previousPrimaryKeyId` |
| `KEY_DEMOTED` | Key demoted from primary | `encryptionKeyId`, `newPrimaryKeyId` |
| `KEY_DISABLED` | Key disabled | `encryptionKeyId`, `disabledAt` |
| `KEYSET_BACKUP_CREATED` | Keyset backup created | `backupPath`, `backupTimestamp` |

#### Re-encryption Events

| Event Type | Description | Required Fields |
|------------|-------------|-----------------|
| `REENCRYPTION_STARTED` | Batch started | `batchId`, `oldKeyId`, `newKeyId`, `targetTable`, `targetColumn` |
| `REENCRYPTION_BATCH_PROGRESS` | Batch progress update | `batchId`, `recordsDone`, `recordsTotal`, `progressPct` |
| `REENCRYPTION_COMPLETED` | Batch completed | `batchId`, `recordsDone`, `recordsTotal`, `durationMs` |
| `REENCRYPTION_FAILED` | Batch failed | `batchId`, `errorMessage`, `errorCount` |
| `REENCRYPTION_RESUMED` | Batch resumed | `batchId`, `retryCount`, `lastRecordId` |

### Audit Log Format

All audit events include:

```json
{
  "eventType": "KEY_INTRODUCED",
  "eventAction": "key_rotation",
  "eventStatus": "SUCCESS",
  "apiName": "ADMIN_API",
  "ipAddress": "127.0.0.1",
  "eventDetails": {
    "encryptionKeyId": 1234567890,
    "algorithm": "AES256_GCM",
    "createdBy": "SYSTEM",
    "triggeredBy": "scheduled_job"
  },
  "timestamp": "2025-12-03T21:49:11.856308Z"
}
```

### Compliance Verification

**Monthly Review**:
- Verify all rotations are logged
- Verify re-encryption batches complete successfully
- Review error rates and trends
- Verify backup retention compliance

**Quarterly Review**:
- Review key lifecycle (introduction → disablement)
- Verify key retention policies are followed
- Review audit log completeness
- Update procedures based on lessons learned

---

## Recovery Procedures

### Keyset Corruption

**Symptoms**:
- `TinkKeyManager` fails to initialize
- Decryption errors
- Keyset file cannot be read

**Recovery Steps**:

1. **Stop Application**
   ```bash
   systemctl stop ezkey-admin-api
   ```

2. **Restore from Backup**
   ```bash
   # Locate latest backup
   ls -lt /etc/ezkey/keysets/backups/
   
   # Restore keyset
   cp /etc/ezkey/keysets/backups/keyset-2025-12-03-02-00-00.json.encrypted \
      /etc/ezkey/keysets/keyset.json.encrypted
   
   # Verify permissions
   chmod 600 /etc/ezkey/keysets/keyset.json.encrypted
   ```

3. **Synchronize Database**
   - Database may be out of sync after restore
   - Service will auto-sync on startup via `ApplicationReadyEvent`
   - Verify sync in logs: `"Keyset synchronized to database"`

4. **Restart Application**
   ```bash
   systemctl start ezkey-admin-api
   ```

5. **Verify Operation**
   - Check logs for successful initialization
   - Verify keys are synchronized in database
   - Test encryption/decryption

### Database Out of Sync

**Symptoms**:
- Keyset exists but `ezkey_encryption_key` table is empty
- Rotation fails with "No PRIMARY key found"

**Recovery Steps**:

1. **Automatic Recovery** (Preferred)
   - Service automatically detects empty table on startup
   - `KeyRotationService.initializeKeysetSync()` runs via `ApplicationReadyEvent`
   - Logs: `"Keyset exists but encryption_key table is empty. Synchronizing..."`

2. **Manual Recovery** (If automatic fails)
   ```sql
   -- Query keyset to get key IDs (requires Tink CLI or custom script)
   -- Then insert manually:
   INSERT INTO ezkey_encryption_key (
     key_id, key_status, algorithm, introduced_at, 
     promoted_primary_at, created_by
   ) VALUES (
     1234567890, 'PRIMARY', 'AES256_GCM', 
     CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'MANUAL_RECOVERY'
   );
   ```

### Failed Re-encryption Batch

**Symptoms**:
- Batch status is `FAILED`
- `error_message` contains error details
- `retry_count` < `max_retries`

**Recovery Steps**:

1. **Review Error**
   ```bash
   GET /api/v1/encryption-keys/batches/{batchId}
   ```

2. **Resolve Root Cause**
   - Database connectivity issues → Fix connection
   - Decryption errors → Verify old key is still enabled
   - Timeout → Increase `max-duration-minutes`

3. **Resume Batch**
   ```bash
   POST /api/v1/encryption-keys/batches/{batchId}/resume
   ```

4. **Monitor Progress**
   - Check batch status periodically
   - Verify `records_done` increases
   - Verify `progress_pct` increases

### Master Key Loss

**Critical**: If master key is lost, keyset cannot be decrypted. This is **irrecoverable**.

**Prevention**:
- Store master key in secure backup location
- Use key management service (e.g., AWS KMS, HashiCorp Vault)
- Document master key location in secure runbook
- Rotate master key periodically (separate from encryption keys)

**Recovery** (if master key is lost):
- **Not possible** without master key
- Must restore from backup that includes master key
- All encrypted data encrypted with lost keyset is **permanently lost**

---

## Runbook for Operators

### Daily Operations

#### Morning Checklist

- [ ] Review rotation logs from previous night
- [ ] Verify no rotation failures
- [ ] Check re-encryption batch status
- [ ] Review audit logs for anomalies

#### Key Rotation Verification

```bash
# Check current primary key
curl -H "Authorization: Bearer $ADMIN_TOKEN" \
  http://localhost:8080/api/v1/encryption-keys/primary

# List all keys
curl -H "Authorization: Bearer $ADMIN_TOKEN" \
  http://localhost:8080/api/v1/encryption-keys

# Check rotation status
# Look for: "Key rotation completed" in logs
```

#### Re-encryption Status Check

```bash
# List active batches
curl -H "Authorization: Bearer $ADMIN_TOKEN" \
  http://localhost:8080/api/v1/encryption-keys/batches?status=IN_PROGRESS

# Check batch progress
curl -H "Authorization: Bearer $ADMIN_TOKEN" \
  http://localhost:8080/api/v1/encryption-keys/batches/{batchId}
```

### Weekly Operations

#### Key Lifecycle Review

```sql
-- Check key ages
SELECT 
  key_id,
  key_status,
  introduced_at,
  promoted_primary_at,
  disabled_at,
  CURRENT_TIMESTAMP - introduced_at AS age
FROM ezkey_encryption_key
ORDER BY introduced_at DESC;

-- Check for keys eligible for disablement
SELECT *
FROM ezkey_encryption_key
WHERE key_status = 'ENABLED'
  AND introduced_at < CURRENT_TIMESTAMP - INTERVAL '180 days';
```

#### Backup Verification

```bash
# List keyset backups
ls -lh /etc/ezkey/keysets/backups/

# Verify backup integrity (attempt to decrypt)
# This requires Tink CLI or custom script
```

### Monthly Operations

#### Audit Log Review

```sql
-- Review key rotation events
SELECT 
  event_type,
  event_status,
  event_details,
  created_at
FROM ezkey_audit_log
WHERE event_type IN (
  'KEY_INTRODUCED',
  'KEY_PROMOTED_PRIMARY',
  'KEY_DEMOTED',
  'KEY_DISABLED'
)
ORDER BY created_at DESC
LIMIT 100;

-- Review re-encryption events
SELECT 
  event_type,
  event_status,
  event_details,
  created_at
FROM ezkey_audit_log
WHERE event_type LIKE 'REENCRYPTION_%'
ORDER BY created_at DESC
LIMIT 100;
```

#### Performance Metrics

- Rotation success rate: Should be 100%
- Average rotation duration: Should be < 5 seconds
- Re-encryption completion rate: Should be > 99%
- Average batch duration: Varies by data volume

### Emergency Procedures

#### Suspected Key Compromise

1. **Immediate Actions** (within 5 minutes)
   ```bash
   # Trigger emergency rotation
   curl -X POST \
     -H "Authorization: Bearer $ADMIN_TOKEN" \
     http://localhost:8080/api/v1/encryption-keys/rotate
   
   # Disable compromised key (via database)
   UPDATE ezkey_encryption_key
   SET key_status = 'DISABLED',
       disabled_at = CURRENT_TIMESTAMP
   WHERE key_id = <compromised_key_id>;
   ```

2. **Investigation** (within 1 hour)
   - Review access logs
   - Check for unauthorized access
   - Document incident

3. **Recovery** (within 24 hours)
   - Complete re-encryption of all data
   - Update security procedures if needed
   - Notify stakeholders if required

#### Service Outage During Rotation

1. **Check Status**
   ```bash
   # Check if rotation is in progress
   # Review logs for rotation activity
   ```

2. **Verify State**
   ```sql
   -- Check for inconsistent state
   SELECT COUNT(*) FROM ezkey_encryption_key WHERE key_status = 'PRIMARY';
   -- Should be exactly 1
   ```

3. **Recovery**
   - If rotation incomplete: Restore from backup
   - If rotation complete: Verify keyset and database are synchronized
   - Restart service

---

## Appendix

### A. Configuration Reference

See `docs/ENCRYPTION_KEY_ROTATION_IMPLEMENTATION.md` for complete configuration reference.

### B. API Endpoints

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/v1/encryption-keys` | GET | List all keys |
| `/api/v1/encryption-keys/primary` | GET | Get primary key |
| `/api/v1/encryption-keys/{keyId}` | GET | Get key by ID |
| `/api/v1/encryption-keys/rotate` | POST | Rotate key manually |
| `/api/v1/encryption-keys/batches` | GET | List re-encryption batches |
| `/api/v1/encryption-keys/batches/{batchId}` | GET | Get batch details |
| `/api/v1/encryption-keys/batches/{batchId}/resume` | POST | Resume failed batch |

### C. Database Tables

**ezkey_encryption_key**:
- Tracks key lifecycle and metadata
- Foreign key: None (primary key is Tink key ID)

**ezkey_reencryption_batch**:
- Tracks re-encryption batch progress
- Foreign keys: `old_key_id`, `new_key_id` → `ezkey_encryption_key.key_id`

**ezkey_audit_log**:
- Stores all audit events
- JSON `event_details` column for flexible event data

### D. Related Documentation

- `docs/ENCRYPTION_KEY_ROTATION_IMPLEMENTATION.md` - Technical implementation details
- [`product-docs/global/normative-posture.md`](../product-docs/global/normative-posture.md) — mapping vocabulary only; not a certification claim
- `docs/OPERATIONAL.md` - General operational procedures

### E. Contact Information

**Security Team**: security@ezkey.dev  
**On-Call**: See internal on-call rotation schedule  
**Emergency**: Follow incident response procedures

---

**Document Control**

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2025-12-03 | Security Team | Initial release |

