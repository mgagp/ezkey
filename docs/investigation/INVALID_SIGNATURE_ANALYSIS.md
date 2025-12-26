# Invalid Signature Error - Analysis of Diagnostic Results

**Date**: 2025-12-26  
**Error Time**: 19:30:39  
**Enrollment ID**: 1  
**System Uptime**: ~3 days

## Diagnostic Results Summary

### ✅ Enrollment State
- **Status**: VERIFIED (enrollment is active and verified)
- **Device Public Key**: Present (448 characters)
- **Key Format**: X.509 format (EC P-256, not RSA as initially detected)
- **Tenant ID**: 1 (via integration)
- **Integration ID**: 1
- **Created**: 2025-12-24 01:30:12

### ⚠️ Key Observations

1. **Encryption Key Rotation Detected**:
   - New PRIMARY key introduced: 2025-12-26 01:50:00
   - Key ID: 1401555173
   - Multiple ENABLED keys from previous days (rotation history visible)

2. **Auth Attempt Timeline**:
   - Auth attempt #61 created: 2025-12-26 19:30:27
   - Error occurred: 2025-12-26 19:30:39 (12 seconds later)
   - Status: PENDING (never completed)

3. **Previous Successful Auth Attempts**:
   - Multiple ACCEPTED attempts on 2025-12-24
   - Last successful: 2025-12-24 01:40:54
   - No successful attempts after key rotation (2025-12-26 01:50:00)

## Hypothesis: Encryption Key Rotation Impact

### Theory
The error "Invalid signature for enrollment: 1" occurring after encryption key rotation suggests that **`device_public_key` might be encrypted** and cannot be decrypted with the new primary key.

However, based on code analysis:
- `device_public_key` is stored in **plaintext** (not encrypted)
- Only `integration_private_key` and `enrollment_proof_token` are encrypted

### Alternative Theory: Key Format Mismatch

The device public key is stored in X.509 format (EC P-256), but:
- The key was created on 2025-12-24 (before system ran for 3 days)
- System uses EC P-256 (migration V16)
- Signature validation uses `SignatureService.validateSignature()` which expects EC P-256

**Possible Issue**: If the stored `device_public_key` format doesn't match what `SignatureService` expects, validation will fail.

## Next Investigation Steps

### 1. Verify Key Format Compatibility
```sql
-- Check if device_public_key can be parsed as EC P-256
SELECT 
  enrollment_id,
  device_public_key,
  SUBSTRING(device_public_key, 1, 50) as key_start,
  CASE 
    WHEN device_public_key LIKE 'MIIBSzCCAQMGByqGSM49%' THEN 'EC P-256 (correct)'
    WHEN device_public_key LIKE 'MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCg%' THEN 'RSA (wrong format)'
    ELSE 'Unknown format'
  END as detected_format
FROM ezkey_enrollment 
WHERE enrollment_id = 1;
```

### 2. Check Re-encryption Status
```sql
-- Check if device_public_key was affected by re-encryption
SELECT 
  batch_id,
  status,
  target_table,
  target_column,
  old_key_id,
  new_key_id,
  records_total,
  records_done,
  progress_pct,
  error_message
FROM ezkey_reencryption_batch
WHERE target_table = 'ezkey_enrollment'
  AND target_column = 'device_public_key'
ORDER BY batch_id DESC
LIMIT 5;
```

### 3. Compare with Working Enrollment
```sql
-- Find another VERIFIED enrollment to compare
SELECT 
  enrollment_id,
  enrollment_status,
  LENGTH(device_public_key) as key_length,
  SUBSTRING(device_public_key, 1, 50) as key_start,
  created_at
FROM ezkey_enrollment
WHERE enrollment_status = 'VERIFIED'
  AND enrollment_id != 1
ORDER BY created_at DESC
LIMIT 3;
```

### 4. Test Signature Validation Manually
- Extract the `device_public_key` from enrollment 1
- Extract the `device_proof_token` and `device_proof_token_signed` from auth attempt 61
- Test signature validation manually to see exact error

## Recommended Actions

### Immediate (Before Restart)
1. ✅ Run corrected diagnostic script to get complete information
2. ⏳ Check re-encryption batches for `device_public_key`
3. ⏳ Compare enrollment 1 with other working enrollments

### After Restart
1. **If error persists**: 
   - Likely data corruption or format issue
   - Re-bind enrollment 1 with new device credentials
   
2. **If error resolves**:
   - Likely memory/cache issue after 3 days
   - Implement monitoring for long-running systems
   - Add periodic health checks

### Prevention
1. Add validation for `device_public_key` format on enrollment creation
2. Add logging for signature validation failures with key format details
3. Monitor encryption key rotation events and their impact
4. Add health checks for enrollment data integrity

## Key Findings from Diagnostic

### ✅ Working Correctly
- Enrollment exists and is VERIFIED
- Device public key is present (not NULL)
- Key length is reasonable (448 chars)
- Tenant assignment is correct (tenant_id: 1)
- Recent successful auth attempts exist (from 2025-12-24)

### ⚠️ Potential Issues
- **Encryption key rotation** occurred 18 hours before error
- **No successful auth attempts** after key rotation
- **Error timing**: 12 seconds after auth attempt creation suggests immediate validation failure

### 🔍 Critical Questions
1. Is `device_public_key` actually encrypted despite documentation saying it's plaintext?
2. Did re-encryption batch process `device_public_key` column incorrectly?
3. Is there a format mismatch between stored key and expected format?
4. Did the 3-day uptime cause memory corruption affecting key parsing?

## Conclusion

The most likely cause is **encryption key rotation impact**, even though `device_public_key` should be plaintext. The timing correlation (error after rotation, no successful attempts after rotation) is strong evidence.

**Next step**: Run corrected diagnostic script, then restart stack to test if issue persists.

