#!/bin/bash
# Diagnostic script for "Invalid signature for enrollment" error
# Usage: ./scripts/diagnose-invalid-signature.sh [enrollment_id]

ENROLLMENT_ID=${1:-1}
CONTAINER_NAME="ezkey-postgres"

echo "=========================================="
echo "Invalid Signature Diagnostic Tool"
echo "=========================================="
echo "Enrollment ID: $ENROLLMENT_ID"
echo ""

# Check if container exists
if ! docker ps --format '{{.Names}}' | grep -q "^${CONTAINER_NAME}$"; then
    echo "❌ Error: Container '$CONTAINER_NAME' not found or not running"
    echo "   Available containers:"
    docker ps --format '{{.Names}}' | grep -E "(postgres|ezkey)"
    exit 1
fi

echo "✅ Container '$CONTAINER_NAME' is running"
echo ""

# 1. Check Enrollment State
echo "1️⃣  Checking enrollment state..."
docker exec -it $CONTAINER_NAME psql -U postgres -d ezkey_db -c "
SELECT 
  e.enrollment_id, 
  e.enrollment_status as status, 
  e.device_public_key IS NULL as device_key_null, 
  LENGTH(e.device_public_key) as key_length, 
  i.tenant_id, 
  e.integration_id,
  e.created_at
FROM ezkey_enrollment e
LEFT JOIN ezkey_integration i ON e.integration_id = i.integration_id
WHERE e.enrollment_id = $ENROLLMENT_ID;
"

# 2. Check Device Public Key Format
echo ""
echo "2️⃣  Checking device public key format..."
docker exec -it $CONTAINER_NAME psql -U postgres -d ezkey_db -c "
SELECT 
  enrollment_id, 
  CASE 
    WHEN device_public_key IS NULL THEN 'NULL'
    WHEN device_public_key LIKE 'MIIBSzCCAQMGByqGSM49%' THEN 'X.509 format (EC P-256)'
    WHEN device_public_key LIKE 'MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCg%' THEN 'X.509 format (RSA)'
    WHEN device_public_key LIKE 'MFkw%' THEN 'X.509 format (EC)'
    WHEN device_public_key LIKE 'MII%' THEN 'X.509 format (unknown algorithm)'
    WHEN LENGTH(device_public_key) < 100 THEN 'TOO SHORT (possibly corrupted)'
    ELSE 'Unknown format'
  END as key_format,
  LENGTH(device_public_key) as key_length,
  SUBSTRING(device_public_key, 1, 100) as key_preview
FROM ezkey_enrollment 
WHERE enrollment_id = $ENROLLMENT_ID;
"

# 3. Check Integration and Tenant
echo ""
echo "3️⃣  Checking integration and tenant assignment..."
docker exec -it $CONTAINER_NAME psql -U postgres -d ezkey_db -c "
SELECT 
  e.enrollment_id,
  e.integration_id,
  i18n.integration_i18n_name as integration_name,
  i.tenant_id as integration_tenant_id,
  CASE 
    WHEN i.tenant_id IS NULL THEN '⚠️  Integration has no tenant assigned'
    ELSE '✅ Tenant assigned via integration'
  END as tenant_status
FROM ezkey_enrollment e
LEFT JOIN ezkey_integration i ON e.integration_id = i.integration_id
LEFT JOIN LATERAL (
  SELECT integration_i18n_name 
  FROM ezkey_integration_i18n 
  WHERE integration_id = i.integration_id 
  ORDER BY CASE WHEN integration_i18n_lang = 'en' THEN 1 ELSE 2 END 
  LIMIT 1
) i18n ON true
WHERE e.enrollment_id = $ENROLLMENT_ID;
"

# 4. Check Recent Auth Attempts
echo ""
echo "4️⃣  Checking recent auth attempts..."
docker exec -it $CONTAINER_NAME psql -U postgres -d ezkey_db -c "
SELECT 
  auth_attempt_id, 
  enrollment_id, 
  auth_attempt_status as status, 
  created_at,
  expires_at
FROM ezkey_auth_attempt 
WHERE enrollment_id = $ENROLLMENT_ID 
ORDER BY created_at DESC 
LIMIT 5;
"

# 5. Check Encryption Key Status
echo ""
echo "5️⃣  Checking encryption key status..."
docker exec -it $CONTAINER_NAME psql -U postgres -d ezkey_db -c "
SELECT 
  key_id, 
  key_status, 
  introduced_at, 
  promoted_primary_at,
  disabled_at
FROM ezkey_encryption_key 
ORDER BY introduced_at DESC 
LIMIT 5;
"

# 6. Check Re-encryption Batches
echo ""
echo "6️⃣  Checking re-encryption batches for enrollment table..."
docker exec -it $CONTAINER_NAME psql -U postgres -d ezkey_db -c "
SELECT 
  batch_id, 
  status, 
  target_table, 
  target_column, 
  old_key_id, 
  new_key_id,
  records_total,
  records_done,
  progress_pct
FROM ezkey_reencryption_batch
WHERE target_table = 'ezkey_enrollment' 
  AND (target_column = 'device_public_key' OR target_column LIKE '%device%')
ORDER BY batch_id DESC
LIMIT 5;
"

# 7. Check Audit Logs
echo ""
echo "7️⃣  Checking recent audit logs for enrollment..."
docker exec -it $CONTAINER_NAME psql -U postgres -d ezkey_db -c "
SELECT 
  audit_log_id,
  event_type,
  event_action,
  event_status,
  enrollment_id,
  SUBSTRING(event_details, 1, 100) as event_details_preview,
  created_at
FROM ezkey_audit_log
WHERE enrollment_id = $ENROLLMENT_ID
ORDER BY created_at DESC
LIMIT 10;
"

echo ""
echo "=========================================="
echo "Diagnostic complete"
echo "=========================================="
echo ""
echo "Next steps:"
echo "1. Review the output above"
echo "2. If device_public_key is NULL or corrupted, re-bind the enrollment"
echo "3. If tenant mismatch detected, check multi-tenancy migration"
echo "4. If encryption key rotation detected, check re-encryption status"
echo "5. Restart stack and retry: docker-compose restart"

