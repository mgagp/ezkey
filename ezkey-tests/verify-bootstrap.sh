#!/bin/bash

# Ezkey Bootstrap Verification Script
# Verifies that Clean Start and bootstrap process works correctly
# Usage: ./verify-bootstrap.sh

set -e

DOCKER_COMPOSE="docker-compose -f docker/docker-compose.yml"

echo "╔════════════════════════════════════════════════════╗"
echo "║   Ezkey Bootstrap Verification Script              ║"
echo "╚════════════════════════════════════════════════════╝"
echo ""

# Color codes
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

check() {
    echo -e "${BLUE}[Check]${NC} $1"
}

pass() {
    echo -e "${GREEN}✅${NC} $1"
}

fail() {
    echo -e "${RED}❌${NC} $1"
    exit 1
}

warn() {
    echo -e "${YELLOW}⚠️${NC} $1"
}

echo ""
echo "Step 1: Verify Bootstrap Credentials File Exists"
echo "───────────────────────────────────────────────"
check "Bootstrap credentials file in Admin API"

CRED_FILE=$($DOCKER_COMPOSE exec admin-api cat /var/lib/ezkey/bootstrap/bootstrap-credentials.json 2>/dev/null || echo "")

if [ -z "$CRED_FILE" ]; then
    fail "Bootstrap credentials file not found in Admin API"
else
    pass "Bootstrap credentials file exists"

    # Extract and verify fields
    ENROLLMENT_ID=$(echo "$CRED_FILE" | grep -o '"enrollmentId" : [0-9]*' | grep -o '[0-9]*')
    ENROLLMENT_TOKEN=$(echo "$CRED_FILE" | grep -o '"enrollmentProofToken" : "[^"]*"')
    CHALLENGE=$(echo "$CRED_FILE" | grep -o '"enrollmentChallengeCode" : [0-9]*' | grep -o '[0-9]*$')
    USERNAME=$(echo "$CRED_FILE" | grep -o '"username" : "[^"]*"')

    if [ -z "$ENROLLMENT_ID" ]; then
        fail "enrollmentId not found in bootstrap credentials"
    fi
    pass "✓ enrollmentId: $ENROLLMENT_ID"

    if [ -z "$ENROLLMENT_TOKEN" ]; then
        fail "enrollmentProofToken not found in bootstrap credentials"
    fi
    pass "✓ enrollmentProofToken present"

    if [ -z "$CHALLENGE" ]; then
        fail "enrollmentChallengeCode not found in bootstrap credentials"
    fi
    pass "✓ enrollmentChallengeCode: $CHALLENGE"

    if [ -z "$USERNAME" ]; then
        fail "username not found in bootstrap credentials"
    fi
    pass "✓ username: $USERNAME"
fi

echo ""
echo "Step 2: Verify Device Credentials File Exists"
echo "─────────────────────────────────────────────"
check "Device credentials file in Bootstrap Init"

DEVICE_FILE=$($DOCKER_COMPOSE exec admin-api cat /var/lib/ezkey/bootstrap/device-credentials.json 2>/dev/null || echo "")

if [ -z "$DEVICE_FILE" ]; then
    fail "Device credentials file not found"
else
    pass "Device credentials file exists"
fi

echo ""
echo "Step 3: Verify Spring Profile is Active"
echo "────────────────────────────────────────"
check "Docker profile active in Admin API"

PROFILE_LOG=$($DOCKER_COMPOSE logs admin-api 2>&1 | grep -i "profile is active" | head -1 || echo "")

if [ -z "$PROFILE_LOG" ]; then
    fail "Could not find profile activation log in Admin API"
else
    if echo "$PROFILE_LOG" | grep -q "docker"; then
        pass "Docker profile is active"
    else
        fail "Docker profile is NOT active in Admin API"
    fi
fi

echo ""
echo "Step 4: Verify Bootstrap Export Message"
echo "───────────────────────────────────────"
check "Bootstrap export confirmation in Admin API logs"

EXPORT_LOG=$($DOCKER_COMPOSE logs admin-api 2>&1 | grep -i "bootstrap credentials exported" | head -1 || echo "")

if [ -z "$EXPORT_LOG" ]; then
    fail "Bootstrap export confirmation not found in Admin API logs"
else
    pass "Bootstrap export confirmed"
    echo "   Message: $(echo $EXPORT_LOG | sed 's/.*Bootstrap/Bootstrap/')"
fi

echo ""
echo "Step 5: Verify Bootstrap Init Success"
echo "─────────────────────────────────────"
check "Bootstrap Init completion"

BOOTSTRAP_COMPLETE=$($DOCKER_COMPOSE logs bootstrap-init 2>&1 | grep -i "Bootstrap Init Complete" | head -1 || echo "")

if [ -z "$BOOTSTRAP_COMPLETE" ]; then
    warn "Bootstrap Init completion message not found (may still be running)"
else
    pass "Bootstrap Init completed successfully"
fi

echo ""
echo "Step 6: Verify Container Health Status"
echo "──────────────────────────────────────"

HEALTH=$($DOCKER_COMPOSE ps --format "{{.Service}}: {{.Status}}")
echo "$HEALTH" | while read -r line; do
    SERVICE=$(echo "$line" | cut -d: -f1)
    STATUS=$(echo "$line" | cut -d: -f2- | xargs)

    if echo "$STATUS" | grep -q "healthy"; then
        pass "$SERVICE is healthy"
    elif echo "$STATUS" | grep -q "starting"; then
        warn "$SERVICE is starting"
    elif echo "$STATUS" | grep -q "Exited.*0"; then
        pass "$SERVICE completed (exit code 0)"
    else
        fail "$SERVICE status is: $STATUS"
    fi
done

echo ""
echo "╔════════════════════════════════════════════════════╗"
echo "║   ✅ Bootstrap Verification Complete!             ║"
echo "╚════════════════════════════════════════════════════╝"
echo ""
echo "Summary:"
echo "  • Bootstrap credentials exported by Admin API"
echo "  • Device credentials created by Bootstrap Init"
echo "  • Docker profile active in Admin API"
echo "  • All required fields present in credentials"
echo "  • Services running and healthy"
echo ""
echo "Clean Start is ready for use!"
