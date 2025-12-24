#!/bin/sh
# Bootstrap Init Script
# Performs automatic enrollment bind+verify and seeds demo-device for Docker demos

set -e

BOOTSTRAP_CREDS_FILE="/bootstrap/bootstrap-credentials.json"
DEVICE_CREDS_FILE="/bootstrap/device-credentials.json"
DEMO_DEVICE_ENROLLMENTS_DIR="/app/data/enrollments"

ADMIN_API_URL="${ADMIN_API_URL:-http://admin-api:9080}"
AUTH_API_URL="${AUTH_API_URL:-http://auth-api:8080}"
CRYPTO_API_URL="${CRYPTO_API_URL:-http://crypto-api:9090}"

echo "=========================================="
echo "  Ezkey Bootstrap Init"
echo "=========================================="
echo ""
echo "Configuration:"
echo "  ADMIN_API_URL: $ADMIN_API_URL"
echo "  AUTH_API_URL: $AUTH_API_URL"
echo "  CRYPTO_API_URL: $CRYPTO_API_URL"
echo "  BOOTSTRAP_CREDS_FILE: $BOOTSTRAP_CREDS_FILE"
echo "  DEVICE_CREDS_FILE: $DEVICE_CREDS_FILE"
echo "  DEMO_DEVICE_ENROLLMENTS_DIR: $DEMO_DEVICE_ENROLLMENTS_DIR"
echo ""

# Step 1: Wait for bootstrap credentials file
echo "Step 1: Waiting for bootstrap credentials file..."
echo "   Checking: $BOOTSTRAP_CREDS_FILE"
echo "   Listing /bootstrap directory:"
ls -la /bootstrap/ 2>&1 || echo "   (Directory may not exist yet)"
TIMEOUT=120
ELAPSED=0
while [ ! -f "$BOOTSTRAP_CREDS_FILE" ]; do
  if [ $ELAPSED -ge $TIMEOUT ]; then
    echo "❌ Error: Bootstrap credentials file not found after ${TIMEOUT} seconds"
    echo "   Expected: $BOOTSTRAP_CREDS_FILE"
    echo "   Listing /bootstrap directory contents:"
    ls -la /bootstrap/ 2>&1 || echo "   (Directory does not exist)"
    exit 1
  fi
  if [ $((ELAPSED % 10)) -eq 0 ]; then
    echo "   Still waiting... (${ELAPSED}s elapsed, checking /bootstrap/)"
    ls -la /bootstrap/ 2>&1 || echo "   (Directory may not exist yet)"
  fi
  sleep 2
  ELAPSED=$((ELAPSED + 2))
done
echo "✅ Bootstrap credentials file found"

# Step 2: Read bootstrap credentials
echo ""
echo "Step 2: Reading bootstrap credentials..."
ENROLLMENT_ID=$(jq -r '.enrollmentId' "$BOOTSTRAP_CREDS_FILE")
ENROLLMENT_PROOF_TOKEN=$(jq -r '.enrollmentProofToken' "$BOOTSTRAP_CREDS_FILE")
ENROLLMENT_CHALLENGE=$(jq -r '.enrollmentChallengeCode' "$BOOTSTRAP_CREDS_FILE")
USERNAME=$(jq -r '.username' "$BOOTSTRAP_CREDS_FILE")

if [ "$ENROLLMENT_ID" = "null" ] || [ -z "$ENROLLMENT_ID" ]; then
  echo "❌ Error: enrollmentId not found in bootstrap credentials"
  exit 1
fi

echo "✅ Credentials loaded:"
echo "   Enrollment ID: $ENROLLMENT_ID"
echo "   Username: $USERNAME"

# Step 3: Generate device keypair via Crypto API
echo ""
echo "Step 3: Generating device keypair..."
KEYPAIR_RESPONSE=$(curl -s -f "$CRYPTO_API_URL/api/v1/crypto/keypair")
DEVICE_PRIVATE_KEY=$(echo "$KEYPAIR_RESPONSE" | jq -r '.privateKey')
DEVICE_PUBLIC_KEY=$(echo "$KEYPAIR_RESPONSE" | jq -r '.publicKey')

if [ "$DEVICE_PRIVATE_KEY" = "null" ] || [ -z "$DEVICE_PRIVATE_KEY" ]; then
  echo "❌ Error: Failed to generate device keypair"
  exit 1
fi

echo "✅ Device keypair generated"

# Step 4: Bind device to enrollment
echo ""
echo "Step 4: Binding device to enrollment..."
BIND_REQUEST=$(jq -n \
  --arg enrollmentId "$ENROLLMENT_ID" \
  --arg enrollmentProofToken "$ENROLLMENT_PROOF_TOKEN" \
  '{enrollmentId: ($enrollmentId | tonumber), enrollmentProofToken: $enrollmentProofToken, language: "en"}')

BIND_RESPONSE=$(curl -s -w "\n%{http_code}" -X POST \
  -H "Content-Type: application/json" \
  -d "$BIND_REQUEST" \
  "$AUTH_API_URL/api/v1/enrollments/bind")

HTTP_CODE=$(echo "$BIND_RESPONSE" | tail -n1)
BIND_BODY=$(echo "$BIND_RESPONSE" | sed '$d')

if [ "$HTTP_CODE" != "200" ]; then
  if [ "$HTTP_CODE" = "409" ]; then
    echo "⚠️  Enrollment already bound (409) - Checking if demo-device enrollment exists..."
    # Check if demo-device enrollment file already exists (idempotent)
    if [ -f "$DEMO_DEVICE_ENROLLMENTS_DIR/${ENROLLMENT_ID}.json" ]; then
      echo "   ✅ Demo-device enrollment file already exists - Bootstrap already complete"
      echo "   Bootstrap init complete (enrollment already configured)"
      exit 0
    else
      echo "   ❌ Error: Enrollment is already bound to a different device"
      echo "   Demo-device enrollment file is missing and cannot be created automatically"
      echo "   Please reset Docker stack (docker-compose down -v) for a fresh start"
      exit 1
    fi
  else
    echo "❌ Error: Bind failed with HTTP $HTTP_CODE"
    echo "   Response: $BIND_BODY"
    exit 1
  fi
else
  BIND_PROOF_TOKEN=$(echo "$BIND_BODY" | jq -r '.enrollmentProofToken')
  INTEGRATION_NAME=$(echo "$BIND_BODY" | jq -r '.integrationName // ""')
  INTEGRATION_DESCRIPTION=$(echo "$BIND_BODY" | jq -r '.integrationDescription // ""')
  INTEGRATION_LOGO=$(echo "$BIND_BODY" | jq -r '.integrationLogo // ""')
  INTEGRATION_PUBLIC_KEY=$(echo "$BIND_BODY" | jq -r '.integrationPublicKey')
  ENROLLMENT_NAME=$(echo "$BIND_BODY" | jq -r '.enrollmentName')
  echo "✅ Device bound to enrollment"
fi

# Step 5: Verify enrollment (if not already verified)
if [ "$BIND_PROOF_TOKEN" != "SKIPPED_ALREADY_BOUND" ]; then
  echo ""
  echo "Step 5: Verifying enrollment..."
  
  # Sign bind proof token with device private key
  SIGN_REQUEST=$(jq -n \
    --arg data "$BIND_PROOF_TOKEN" \
    --arg privateKey "$DEVICE_PRIVATE_KEY" \
    '{data: $data, privateKey: $privateKey}')
  
  SIGN_RESPONSE=$(curl -s -f -X POST \
    -H "Content-Type: application/json" \
    -d "$SIGN_REQUEST" \
    "$CRYPTO_API_URL/api/v1/crypto/sign")
  
  SIGNATURE=$(echo "$SIGN_RESPONSE" | jq -r '.signature')
  
  if [ "$SIGNATURE" = "null" ] || [ -z "$SIGNATURE" ]; then
    echo "❌ Error: Failed to sign bind proof token"
    exit 1
  fi
  
  # Verify enrollment
  VERIFY_REQUEST=$(jq -n \
    --arg enrollmentId "$ENROLLMENT_ID" \
    --arg challengeResponse "$ENROLLMENT_CHALLENGE" \
    --arg devicePublicKey "$DEVICE_PUBLIC_KEY" \
    --arg enrollmentProofTokenSigned "$SIGNATURE" \
    '{enrollmentId: ($enrollmentId | tonumber), challengeResponse: ($challengeResponse | tonumber), devicePublicKey: $devicePublicKey, enrollmentProofTokenSigned: $enrollmentProofTokenSigned}')
  
  VERIFY_RESPONSE=$(curl -s -w "\n%{http_code}" -X POST \
    -H "Content-Type: application/json" \
    -d "$VERIFY_REQUEST" \
    "$AUTH_API_URL/api/v1/enrollments/verify")
  
  VERIFY_HTTP_CODE=$(echo "$VERIFY_RESPONSE" | tail -n1)
  VERIFY_BODY=$(echo "$VERIFY_RESPONSE" | sed '$d')
  
  if [ "$VERIFY_HTTP_CODE" != "200" ]; then
    echo "❌ Error: Verify failed with HTTP $VERIFY_HTTP_CODE"
    echo "   Response: $VERIFY_BODY"
    exit 1
  fi
  
  echo "✅ Enrollment verified"
else
  echo ""
  echo "Step 5: Skipping verify (enrollment already bound/verified)"
fi

# Step 6: Save device credentials
echo ""
echo "Step 6: Saving device credentials..."
DEVICE_CREDS_JSON=$(jq -n \
  --arg enrollmentId "$ENROLLMENT_ID" \
  --arg privateKey "$DEVICE_PRIVATE_KEY" \
  --arg publicKey "$DEVICE_PUBLIC_KEY" \
  '{enrollmentId: ($enrollmentId | tonumber), privateKey: $privateKey, publicKey: $publicKey, keySize: 256}')

echo "$DEVICE_CREDS_JSON" > "$DEVICE_CREDS_FILE"
echo "✅ Device credentials saved to $DEVICE_CREDS_FILE"

# Step 7: Seed demo-device enrollment file
echo ""
echo "Step 7: Seeding demo-device enrollment file..."
# Ensure directory exists with correct permissions (matching demo-device entrypoint)
# demo-device runs as spring:spring, so we need to ensure the directory is writable
mkdir -p "$DEMO_DEVICE_ENROLLMENTS_DIR"
if [ ! -d "$DEMO_DEVICE_ENROLLMENTS_DIR" ]; then
  echo "❌ Error: Failed to create demo-device enrollments directory"
  exit 1
fi
# Ensure directory is writable (demo-device entrypoint does chown spring:spring, but we run as root)
# Set permissions to 755 (rwxr-xr-x) so spring user can read/write
chmod 755 "$DEMO_DEVICE_ENROLLMENTS_DIR" 2>/dev/null || true

# Create enrollment file matching EnrollmentStoreService.Record format
ENROLLMENT_FILE="$DEMO_DEVICE_ENROLLMENTS_DIR/${ENROLLMENT_ID}.json"

# Ensure integrationPublicKey and enrollmentName are set
if [ -z "$INTEGRATION_PUBLIC_KEY" ]; then
  echo "❌ Error: Integration public key is required but missing"
  exit 1
fi
if [ -z "$ENROLLMENT_NAME" ]; then
  ENROLLMENT_NAME="Global Admin MFA"
fi

ENROLLMENT_JSON=$(jq -n \
  --arg enrollmentId "$ENROLLMENT_ID" \
  --arg enrollmentName "$ENROLLMENT_NAME" \
  --arg integrationPublicKey "$INTEGRATION_PUBLIC_KEY" \
  --arg enrollmentProofToken "$ENROLLMENT_PROOF_TOKEN" \
  --arg devicePublicKey "$DEVICE_PUBLIC_KEY" \
  --arg devicePrivateKey "$DEVICE_PRIVATE_KEY" \
  --arg integrationName "$INTEGRATION_NAME" \
  --arg integrationDescription "$INTEGRATION_DESCRIPTION" \
  --arg integrationLogo "$INTEGRATION_LOGO" \
  '{enrollmentId: ($enrollmentId | tonumber), integrationId: null, enrollmentName: $enrollmentName, enrollmentUrl: null, integrationPublicKey: $integrationPublicKey, enrollmentProofToken: $enrollmentProofToken, devicePublicKey: $devicePublicKey, devicePrivateKey: $devicePrivateKey, authAttemptChallengeRequired: false, deviceLabel: "Device", createdAt: (now | todateiso8601), integrationName: (if $integrationName == "" then null else $integrationName end), integrationDescription: (if $integrationDescription == "" then null else $integrationDescription end), integrationLogo: (if $integrationLogo == "" then null else $integrationLogo end)}')

echo "$ENROLLMENT_JSON" > "$ENROLLMENT_FILE"
echo "✅ Demo-device enrollment file created: $ENROLLMENT_FILE"

echo ""
echo "=========================================="
echo "  ✅ Bootstrap Init Complete!"
echo "=========================================="
echo ""
echo "📋 Summary:"
echo "   Enrollment ID: $ENROLLMENT_ID"
echo "   Device credentials: $DEVICE_CREDS_FILE"
echo "   Demo-device enrollment: $ENROLLMENT_FILE"
echo ""
echo "🎉 Demo-device is ready for use!"
echo "   Next: Login via POST /api/v1/admin/auth/login and approve on demo-device"

