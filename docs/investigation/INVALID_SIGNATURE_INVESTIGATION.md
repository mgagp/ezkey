# Investigation Plan: Invalid Signature Error After 3 Days

**Date**: 2025-12-26  
**Error**: `Invalid signature for enrollment: 1`  
**Context**: System running for 3 days, possible multi-tenancy changes

## Error Details

```
2025-12-26T19:30:39.851Z  WARN 1 --- [ezkey-auth-api] [nio-8080-exec-6] 
o.e.a.service.AuthAttemptPendingService  : Invalid signature for enrollment: 1
```

**Location**: `AuthAttemptPendingService.validateDeviceSignature()`  
**Validation**: `signatureService.validateSignature(deviceProofToken, deviceProofTokenSigned, devicePublicKey)`

## Hypotheses

### 1. **Multi-Tenancy Data Migration** (High Priority)
- **Hypothesis**: Enrollment data may have been modified during multi-tenancy implementation
- **Impact**: `devicePublicKey` in enrollment table might be corrupted or changed
- **Investigation**:
  ```sql
  -- Check enrollment 1 state (tenant_id comes from integration)
  SELECT 
    e.enrollment_id, 
    e.device_public_key, 
    e.enrollment_status, 
    i.tenant_id, 
    e.integration_id 
  FROM ezkey_enrollment e
  LEFT JOIN ezkey_integration i ON e.integration_id = i.integration_id
  WHERE e.enrollment_id = 1;
  
  -- Check if device_public_key is NULL or corrupted
  SELECT 
    enrollment_id, 
    LENGTH(device_public_key) as key_length,
    device_public_key IS NULL as is_null
  FROM ezkey_enrollment 
  WHERE enrollment_id = 1;
  ```

### 2. **Encryption Key Rotation** (Medium Priority)
- **Hypothesis**: Encryption keys were rotated, causing stored public keys to become invalid
- **Impact**: If `device_public_key` is encrypted and keys rotated, decryption fails
- **Investigation**:
  ```sql
  -- Check encryption key status
  SELECT key_id, key_status, introduced_at, promoted_primary_at 
  FROM ezkey_encryption_key 
  ORDER BY introduced_at DESC;
  
  -- Check re-encryption batches
  SELECT batch_id, status, target_table, target_column, old_key_id, new_key_id
  FROM ezkey_reencryption_batch
  WHERE target_table = 'ezkey_enrollment' AND target_column = 'device_public_key';
  ```

### 3. **Data Corruption** (Medium Priority)
- **Hypothesis**: Long-running system caused data corruption in database
- **Impact**: `device_public_key` field corrupted or truncated
- **Investigation**:
  ```sql
  -- Check for data integrity issues
  SELECT enrollment_id, 
         device_public_key,
         LENGTH(device_public_key) as key_length,
         CASE 
           WHEN device_public_key LIKE 'MII%' THEN 'X.509 format'
           WHEN device_public_key LIKE 'MFkw%' THEN 'EC format'
           ELSE 'Unknown format'
         END as key_format
  FROM ezkey_enrollment 
  WHERE enrollment_id = 1;
  ```

### 4. **Memory/Cache Issues** (Low Priority)
- **Hypothesis**: JVM memory issues or cache corruption after 3 days
- **Impact**: Cached enrollment data might be stale or corrupted
- **Investigation**:
  - Check JVM memory usage: `docker stats ezkey-auth-api`
  - Check for OutOfMemoryError in logs
  - Restart container to clear cache

### 5. **Clock/Timezone Issues** (Low Priority)
- **Hypothesis**: System clock drift or timezone changes
- **Impact**: Timestamp-based validation might fail
- **Investigation**:
  ```sql
  -- Check system time consistency
  SELECT NOW(), CURRENT_TIMESTAMP;
  ```

## Investigation Steps

### Step 1: Immediate Diagnostic (Before Restart)

1. **Check Enrollment State**:
   ```bash
   docker exec -it ezkey-postgres psql -U postgres -d ezkey_db -c \
     "SELECT enrollment_id, enrollment_status as status, device_public_key IS NULL as device_key_null, 
      LENGTH(device_public_key) as key_length, tenant_id, integration_id 
      FROM ezkey_enrollment WHERE enrollment_id = 1;"
   ```

2. **Check Device Public Key Format**:
   ```bash
   docker exec -it ezkey-postgres psql -U postgres -d ezkey_db -c \
     "SELECT enrollment_id, 
      SUBSTRING(device_public_key, 1, 50) as key_preview,
      LENGTH(device_public_key) as key_length
      FROM ezkey_enrollment WHERE enrollment_id = 1;"
   ```

3. **Check Recent Auth Attempts**:
   ```bash
   docker exec -it ezkey-postgres psql -U postgres -d ezkey_db -c \
     "SELECT auth_attempt_id, enrollment_id, auth_attempt_status as status, created_at 
      FROM ezkey_auth_attempt 
      WHERE enrollment_id = 1 
      ORDER BY created_at DESC LIMIT 5;"
   ```

4. **Check Encryption Key Status**:
   ```bash
   docker exec -it ezkey-postgres psql -U postgres -d ezkey_db -c \
     "SELECT key_id, key_status, introduced_at, promoted_primary_at 
      FROM ezkey_encryption_key 
      ORDER BY introduced_at DESC LIMIT 5;"
   ```

### Step 2: Restart Test

1. **Full Stack Restart**:
   ```bash
   cd docker
   docker-compose down
   docker-compose up -d
   ```

2. **Wait for Services**:
   ```bash
   # Wait for services to be healthy
   docker-compose ps
   ```

3. **Retry Same Request**:
   - Use same enrollment ID and credentials
   - Check if error persists

### Step 3: If Error Persists After Restart

1. **Check Multi-Tenancy Impact**:
   ```sql
   -- Check if enrollment was reassigned to different tenant
   SELECT e.enrollment_id, e.tenant_id, e.integration_id, 
          i.tenant_id as integration_tenant_id
FROM ezkey_enrollment e
LEFT JOIN ezkey_integration i ON e.integration_id = i.integration_id
WHERE e.enrollment_id = 1;
   ```

2. **Validate Device Public Key**:
   ```bash
   # Try to decode the public key
   docker exec -it ezkey-auth-api java -cp /app/classes \
     -Djava.security.egd=file:/dev/./urandom \
     org.ezkey.signature.SignatureServiceTest
   ```

3. **Check Application Logs**:
   ```bash
   docker logs ezkey-auth-api --tail 100 | grep -i "signature\|enrollment\|device"
   ```

### Step 4: Root Cause Analysis

If restart fixes the issue, investigate:

1. **Memory Leaks**:
   - Check for memory leaks in SignatureService
   - Monitor JVM heap usage over time

2. **Database Connection Issues**:
   - Check for connection pool exhaustion
   - Verify transaction isolation levels

3. **Concurrent Modification**:
   - Check if enrollment was modified by another process
   - Review audit logs for enrollment 1

## Diagnostic Queries

### Complete Enrollment Diagnostic
```sql
SELECT 
  e.enrollment_id,
  e.status,
  e.enrollment_name,
  e.device_public_key IS NULL as device_key_null,
  LENGTH(e.device_public_key) as device_key_length,
  SUBSTRING(e.device_public_key, 1, 100) as device_key_preview,
  e.tenant_id,
  e.integration_id,
  i.integration_name,
  i.tenant_id as integration_tenant_id,
  e.created_at,
  e.updated_at
FROM ezkey_enrollment e
LEFT JOIN ezkey_integration i ON e.integration_id = i.integration_id
WHERE e.enrollment_id = 1;
```

### Check for Recent Modifications
```sql
SELECT 
  audit_log_id,
  event_type,
  event_action,
  enrollment_id,
  event_details,
  created_at
FROM ezkey_audit_log
WHERE enrollment_id = 1
ORDER BY created_at DESC
LIMIT 10;
```

### Check Encryption Key Rotation History
```sql
SELECT 
  key_id,
  key_status,
  introduced_at,
  promoted_primary_at,
  disabled_at,
  created_by
FROM ezkey_encryption_key
ORDER BY introduced_at DESC;
```

## Expected Outcomes

### Scenario A: Restart Fixes Issue (⚠️ UNACCEPTABLE)
- **Conclusion**: **Root cause unknown** - restart is a workaround, not a solution
- **Critical Issue**: System failed after 3 days of idle uptime (zero load)
- **Action Required**: 
  1. **Investigation mandatory** - this is a serious stability bug
  2. Set up monitoring for progressive uptime tests
  3. Identify root cause through systematic testing
  4. Implement proper fix (not periodic restart)
- **Status**: Workaround applied, investigation in progress

### Scenario B: Error Persists After Restart
- **Conclusion**: Data corruption or multi-tenancy migration issue
- **Action**: 
  1. Check enrollment data integrity
  2. Verify device_public_key format
  3. Check encryption key rotation status
  4. Re-bind enrollment if necessary

### Scenario C: Error Only for Specific Enrollment
- **Conclusion**: Enrollment-specific data corruption
- **Action**: 
  1. Compare with working enrollments
  2. Check for manual database modifications
  3. Re-bind the affected enrollment

## Prevention Measures

1. **Add Health Checks**:
   - Monitor enrollment data integrity
   - Alert on signature validation failures

2. **Add Logging**:
   - Log device_public_key format on validation failure
   - Log encryption key status during validation

3. **Add Validation**:
   - Validate device_public_key format on enrollment creation
   - Periodic integrity checks for stored keys

4. **Add Monitoring**:
   - Track signature validation failure rate
   - Monitor encryption key rotation events

## Current Status

**Date**: 2025-12-26  
**Status**: ⚠️ **WORKAROUND APPLIED - ROOT CAUSE UNKNOWN**

### ⚠️ Critical Assessment

**This is a serious stability issue that is completely unacceptable:**

- **Context**: System in full development phase, **zero load**, **zero activity** for 3 days
- **Expected Behavior**: Complete stability with no degradation
- **Actual Behavior**: Signature validation failure after 3 days of idle uptime
- **Impact**: System became non-functional without any external trigger

**A restart is NOT a solution** - it is only a temporary workaround. The root cause must be identified and fixed.

### Workaround Applied

1. ✅ Executed diagnostic script (`diagnose-invalid-signature.sh`)
   - All data integrity checks passed
   - Enrollment state: VERIFIED
   - Device public key: Present and correctly formatted (EC P-256, X.509)
   - No data corruption detected

2. ✅ Full stack restart
   - `docker-compose down`
   - `docker-compose up -d`
   - All services restarted successfully

3. ✅ Postman login test with Demo Device
   - Authentication successful
   - Signature validation working correctly **after restart**

### What We Know

**Data Integrity**: ✅ All checks passed
- Enrollment data was intact
- Device public key was correctly formatted (EC P-256, X.509)
- Database schema was consistent
- No encryption key rotation impact on plaintext `device_public_key`

**Runtime State**: ❌ **Unknown failure mode**
- Error occurred after 3 days of idle uptime
- No load, no activity, no external triggers
- Restart resolved the issue (temporary workaround)
- **Root cause remains unidentified**

### What We Don't Know

1. **Why did signature validation fail after 3 days?**
   - Memory leak? Cache corruption? JVM state issue?
   - Database connection pool exhaustion?
   - Thread pool or resource exhaustion?
   - Subtle bug in signature validation logic?

2. **Will it happen again?**
   - At what uptime threshold?
   - Under what conditions?
   - Is it deterministic or random?

3. **What is the actual root cause?**
   - No evidence of memory leaks in diagnostic
   - No evidence of data corruption
   - No evidence of resource exhaustion
   - **Investigation required**

### Investigation Plan

#### Phase 1: Stability Testing (Immediate)

1. **Progressive Uptime Tests**:
   - Test 1: Leave system idle for 3 days (reproduce issue)
   - Test 2: Leave system idle for 5 days
   - Test 3: Leave system idle for 7 days
   - Monitor: JVM memory, thread counts, connection pools, signature validation success rate

2. **Gradual Load Testing**:
   - Test 4: Light load (1 request/hour) for 3 days
   - Test 5: Moderate load (1 request/minute) for 3 days
   - Test 6: Continuous light load for 7 days
   - Monitor: Same metrics as above

3. **Monitoring Setup** (Before Tests):
   - JVM heap usage over time
   - Thread pool utilization
   - Database connection pool status
   - Signature validation success/failure rate
   - Garbage collection frequency and duration
   - Service uptime tracking

#### Phase 2: Root Cause Analysis

1. **Code Review**:
   - Review `SignatureService.validateSignature()` for potential state issues
   - Review `AuthAttemptPendingService` for resource leaks
   - Review JPA entity caching behavior
   - Review connection pool configuration

2. **Runtime Analysis**:
   - Add detailed logging for signature validation (key format, validation steps)
   - Add memory snapshots before/after extended uptime
   - Add thread dump analysis capability
   - Monitor BouncyCastle crypto provider state

3. **Reproducibility**:
   - Attempt to reproduce in controlled environment
   - Identify exact conditions that trigger the issue
   - Create automated test that reproduces the failure

#### Phase 3: Fix Implementation

1. **Once root cause identified**:
   - Implement proper fix (not workaround)
   - Add regression tests
   - Update monitoring and alerting
   - Document the fix and prevention measures

### Immediate Actions Required

1. ⏳ **Set up monitoring** before next stability test
2. ⏳ **Plan progressive uptime tests** (3, 5, 7 days)
3. ⏳ **Add detailed logging** for signature validation
4. ⏳ **Review code** for potential state issues or resource leaks
5. ⏳ **Document all findings** in this investigation document

### Prevention Measures (To Be Implemented After Root Cause Identification)

1. **Health Checks**:
   - Periodic signature validation health checks
   - Automatic detection of validation degradation
   - Alert on signature validation failure rate increases

2. **Monitoring**:
   - Real-time JVM memory and thread monitoring
   - Database connection pool monitoring
   - Signature validation success/failure tracking
   - Service uptime and stability metrics

3. **Logging Enhancement**:
   - Detailed logging for signature validation failures
   - JVM memory state logging on validation failures
   - Thread dump capability on errors
   - Service uptime tracking in logs

### Lessons Learned

- **Diagnostic Scripts**: Essential for quickly ruling out data corruption
- **Systematic Approach**: Data integrity checks before restart was correct
- **Critical Issue**: A system that fails after 3 days of idle uptime is fundamentally broken
- **No Shortcuts**: Restart is not a solution - root cause must be found

### Next Steps

1. ⚠️ **This is NOT resolved** - workaround applied, investigation continues
2. ⏳ Set up monitoring infrastructure
3. ⏳ Execute progressive stability tests
4. ⏳ Identify root cause through systematic investigation
5. ⏳ Implement proper fix (not periodic restart)
6. ⏳ Add regression tests to prevent recurrence

