# Database Partitioning - Compliance and Non-Repudiation Considerations

## Overview

This document addresses critical compliance and security considerations for database table partitioning in EZKEY, specifically regarding:

1. **Non-repudiation and electronic signature chaining** for audit logs (future feature)
2. **SOC2 compliance requirements** for audit log retention and integrity
3. **Other regulatory considerations** (GDPR, PCI-DSS, HIPAA if applicable)

---

## Non-Repudiation and Electronic Signature Chaining

### Future Feature Context

The EZKEY project plans to implement electronic signature non-repudiation for audit log entries. This feature would include:

- **Cryptographic signing** of each audit log entry
- **Chaining mechanism** linking entries by generation (per Admin API instance)
- **Hash chain** where each entry contains hash of previous entry

**Status:** Analysis phase - not yet implemented

### Impact Analysis on Partitioning Strategy

#### ✅ **NO NEGATIVE IMPACT**

**Key Finding:** Partitioning by month does **NOT** break signature chains or compromise non-repudiation.

**Why Partitioning is Compatible:**

1. **Logical vs Physical Storage:**
   - Chain relationships are **logical** (stored as data columns)
   - Partitioning is **physical** (data organization for performance)
   - PostgreSQL allows queries to span partitions seamlessly

2. **Chain Verification Queries:**
   - Chain verification queries can use `created_at` filters
   - PostgreSQL automatically prunes irrelevant partitions
   - Cross-partition queries are efficient with proper indexing

3. **Data Integrity:**
   - Signatures and hashes are stored in data columns
   - Partitioning does not modify data content
   - Chain integrity is preserved regardless of partition location

#### Recommended Design for Future Implementation

**Schema Additions for Signature Chaining:**

```sql
ALTER TABLE ezkey_audit_log ADD COLUMN previous_entry_hash TEXT;
ALTER TABLE ezkey_audit_log ADD COLUMN chain_generation_id VARCHAR(100);
ALTER TABLE ezkey_audit_log ADD COLUMN entry_signature TEXT;
ALTER TABLE ezkey_audit_log ADD COLUMN signature_algorithm VARCHAR(50);
```

**Query Pattern for Chain Verification:**

```sql
-- Chain verification query (works across partitions)
SELECT 
    audit_log_id,
    created_at,
    previous_entry_hash,
    entry_signature,
    chain_generation_id
FROM ezkey_audit_log 
WHERE chain_generation_id = ? 
  AND created_at >= ? 
  AND created_at <= ?
ORDER BY created_at ASC;
```

**Partition Pruning Benefits:**
- Query automatically prunes partitions outside date range
- Only relevant partitions are scanned
- Performance remains optimal even with chain verification

**Index Strategy for Chain Verification:**

```sql
-- Index for chain verification queries
CREATE INDEX idx_audit_log_chain_generation 
ON ezkey_audit_log(chain_generation_id, created_at ASC);
```

**Note:** This index will be created **locally** on each partition, ensuring optimal performance.

#### Implementation Recommendations

1. **Store Chain Metadata in Data:**
   - `previous_entry_hash`: Hash of previous entry in chain
   - `chain_generation_id`: Identifier for Admin API instance generation
   - `entry_signature`: Cryptographic signature of entry content
   - `signature_algorithm`: Algorithm used (e.g., "ECDSA-SHA256")

2. **Query Design:**
   - Always include `created_at` filters in chain verification queries
   - Use `chain_generation_id` for filtering by instance
   - Leverage partition pruning for optimal performance

3. **Partitioning Compatibility:**
   - Monthly partitioning remains optimal strategy
   - No modifications needed to partitioning design
   - Chain verification works seamlessly across partitions

---

## SOC2 Compliance Requirements

### Audit Log Retention (CC7.2)

**Requirement:**
- **Minimum Retention:** 1 year for security logs
- **Recommended Retention:** 7 years for audit logs (SOC2 Type II best practice)
- **Legal Hold:** Must support legal hold capabilities (prevent deletion during investigations)

**Partitioning Benefits:**
- ✅ **Efficient Retention Enforcement:**
  - Old partitions can be archived/dropped after retention period
  - Simplifies compliance reporting (query specific date ranges)
  - Reduces storage costs for archived data

- ✅ **Legal Hold Support:**
  - Can mark specific partitions as "legal hold" (prevent archival)
  - Simplifies compliance with legal hold requirements
  - Enables selective retention based on investigation needs

**Implementation Strategy:**
```sql
-- Example: Archive partitions older than 7 years (except legal hold)
-- This would be part of scheduled cleanup job
DO $$
DECLARE
    partition_name TEXT;
BEGIN
    FOR partition_name IN 
        SELECT tablename 
        FROM pg_tables 
        WHERE tablename LIKE 'ezkey_audit_log_%'
          AND tablename < 'ezkey_audit_log_' || to_char(CURRENT_DATE - INTERVAL '7 years', 'YYYY_MM')
    LOOP
        -- Check for legal hold flag (would be stored in metadata table)
        -- If no legal hold, archive partition
        PERFORM pg_archive_table(partition_name);
    END LOOP;
END $$;
```

### Log Integrity (CC5.3, CC7.2)

**Requirement:**
- Audit logs must be **tamper-evident**
- Must detect unauthorized modifications
- Must support integrity verification

**Partitioning Impact:**
- ✅ **NO NEGATIVE IMPACT:**
  - Cryptographic signatures/hashes stored in data columns
  - Partitioning does not affect data integrity
  - Chain verification works across partitions (see non-repudiation analysis above)

**Integrity Verification:**
- Can verify signatures/hashes regardless of partition location
- Chain verification ensures no gaps or modifications
- Partition pruning improves verification query performance

### Access Controls (CC6.1, CC6.2)

**Requirement:**
- Access to audit logs must be controlled and logged
- Must support access reviews and audits

**Partitioning Benefits:**
- ✅ **Efficient Access Control Queries:**
  - Filter by date range reduces data scanned
  - Partition pruning improves query performance for access reviews
  - Enables efficient access pattern analysis

**Example Access Review Query:**
```sql
-- Access review query (benefits from partition pruning)
SELECT 
    admin_id,
    COUNT(*) as access_count,
    MIN(created_at) as first_access,
    MAX(created_at) as last_access
FROM ezkey_audit_log
WHERE created_at >= ? 
  AND created_at <= ?
  AND event_type = 'AUDIT_LOG_ACCESS'
GROUP BY admin_id
ORDER BY access_count DESC;
```

---

## GDPR Compliance (If Applicable)

**Note:** EZKEY primarily handles authentication data, not personal data. GDPR may not apply directly, but principles are good practice.

### Right to Erasure (Article 17)

**Requirement:**
- Data subjects can request deletion of personal data
- Must delete data within 30 days of request

**Partitioning Benefits:**
- ✅ **Efficient Data Deletion:**
  - Can drop entire partitions if all data in partition is subject to deletion
  - Reduces impact of deletion operations on active data
  - Simplifies compliance with erasure requests

**Implementation Consideration:**
- If personal data is stored, ensure partitioning strategy allows efficient deletion
- Consider sub-partitioning by tenant_id if multi-tenant with personal data

### Data Minimization (Article 5)

**Requirement:**
- Store only necessary data for required period
- Delete data after retention period expires

**Partitioning Benefits:**
- ✅ **Efficient Data Lifecycle Management:**
  - Old partitions can be archived/dropped after retention period
  - Simplifies compliance with data minimization principles
  - Enables automated data lifecycle management

---

## Other Regulatory Considerations

### Financial Services (PCI-DSS, if applicable)

**Requirement:**
- Audit logs for cardholder data access must be retained
- Must support compliance reporting

**Partitioning Benefits:**
- ✅ **Efficient Log Management:**
  - Enables efficient log retention and archival
  - Simplifies compliance reporting
  - Reduces storage costs

### Healthcare (HIPAA, if applicable)

**Requirement:**
- Audit logs for PHI access must be retained
- Must support access reviews

**Partitioning Benefits:**
- ✅ **Efficient Access Tracking:**
  - Enables efficient log retention and archival
  - Simplifies access review queries
  - Supports compliance reporting

### General Data Protection Principles

**Principle:** Secure storage and efficient access to audit data

**Partitioning Benefits:**
- ✅ **Performance:** Improves query performance
- ✅ **Cost:** Enables efficient archival and reduces storage costs
- ✅ **Compliance:** Simplifies compliance with retention requirements

---

## Recommendations Summary

### For Non-Repudiation Implementation

1. **✅ Proceed with Monthly Partitioning:**
   - No modifications needed to partitioning strategy
   - Chain verification works seamlessly across partitions
   - Partition pruning improves verification query performance

2. **Design Chain Metadata Storage:**
   - Store chain relationships in data columns
   - Use `created_at` filters in chain verification queries
   - Leverage partition pruning for optimal performance

3. **Index Strategy:**
   - Create indexes on `chain_generation_id` and `created_at`
   - Indexes will be local to each partition
   - Ensures optimal chain verification performance

### For SOC2 Compliance

1. **✅ Implement 7-Year Retention:**
   - Use partitioning to enable efficient archival
   - Archive old partitions after retention period
   - Support legal hold capabilities

2. **✅ Maintain Log Integrity:**
   - Implement signature chaining (future feature)
   - Verify signatures regardless of partition location
   - Use chain verification for tamper detection

3. **✅ Enable Access Controls:**
   - Use partition pruning for efficient access review queries
   - Support access pattern analysis
   - Enable compliance reporting

### For GDPR Compliance (If Applicable)

1. **✅ Support Right to Erasure:**
   - Use partitioning to enable efficient data deletion
   - Consider tenant-based sub-partitioning if needed
   - Ensure deletion operations don't impact active data

2. **✅ Implement Data Minimization:**
   - Use partitioning for efficient data lifecycle management
   - Archive/drop old partitions after retention period
   - Automate data lifecycle management

---

## Conclusion

**Key Finding:** Database partitioning by month is **fully compatible** with:

- ✅ Non-repudiation and electronic signature chaining (future feature)
- ✅ SOC2 compliance requirements
- ✅ GDPR compliance principles (if applicable)
- ✅ Other regulatory requirements

**Recommendation:** Proceed with monthly partitioning strategy as planned. No modifications needed to accommodate future non-repudiation features or compliance requirements.

---

## References

- SOC2 Trust Service Criteria: https://www.aicpa.org/interestareas/frc/assuranceadvisoryservices/trustdataintegritytaskforce.html
- GDPR Article 17 (Right to Erasure): https://gdpr-info.eu/art-17-gdpr/
- PostgreSQL Partitioning: https://www.postgresql.org/docs/current/ddl-partitioning.html
- EZKEY SOC2 Preparation: `docs/SOC2_PREPARATION.md`
- EZKEY Database Partitioning Plan: `docs/DATABASE_PARTITIONING_PLAN.md` (to be created)

---

**Document Version:** 1.0  
**Last Updated:** 2025-01-XX  
**Status:** Draft - Pending Review  
**Next Review:** After non-repudiation feature implementation

