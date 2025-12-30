#!/bin/bash
# Diagnostic script to identify which test breaks admin authentication
# Usage: ./diagnose-auth-issue.sh

set -e

echo "=========================================="
echo "Admin Authentication Diagnostic Script"
echo "=========================================="
echo ""
echo "This script will:"
echo "1. Check admin enrollment state before tests"
echo "2. Run tests one by one"
echo "3. After each test, verify if admin login still works"
echo "4. Report which test breaks authentication"
echo ""
echo "Press Enter to continue or Ctrl+C to cancel..."
read

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Configuration
ADMIN_API_URL="http://localhost:9080"
AUTH_API_URL="http://localhost:8080"
ADMIN_USERNAME="admin"

# Function to check admin enrollment state
check_admin_enrollment() {
    echo -e "${YELLOW}Checking admin enrollment state...${NC}"
    
    # Query database for admin enrollment
    docker exec -it ezkey-postgres psql -U postgres -d ezkey_db -c "
        SELECT 
            a.admin_id,
            a.username,
            e.enrollment_id,
            e.enrollment_status,
            COUNT(aa.auth_attempt_id) as pending_auth_attempts
        FROM ezkey_admin a
        LEFT JOIN ezkey_enrollment e ON a.mfa_enrollment_id = e.enrollment_id
        LEFT JOIN ezkey_auth_attempt aa ON e.enrollment_id = aa.enrollment_id 
            AND aa.auth_attempt_status = 'PENDING'
        WHERE a.username = 'admin' OR a.username = 'admin.docker'
        GROUP BY a.admin_id, a.username, e.enrollment_id, e.enrollment_status;
    " || echo "Could not query database"
    echo ""
}

# Function to test admin login
test_admin_login() {
    echo -e "${YELLOW}Testing admin login...${NC}"
    
    # Create login request
    RESPONSE=$(curl -s -X POST "${ADMIN_API_URL}/api/v1/admin/auth/login" \
        -H "Content-Type: application/json" \
        -d "{\"username\": \"${ADMIN_USERNAME}\"}" \
        -w "\n%{http_code}")
    
    HTTP_CODE=$(echo "$RESPONSE" | tail -n1)
    BODY=$(echo "$RESPONSE" | head -n-1)
    
    if [ "$HTTP_CODE" = "200" ]; then
        # Check if response contains authAttemptId (pending) or token (success)
        if echo "$BODY" | grep -q "authAttemptId"; then
            AUTH_ATTEMPT_ID=$(echo "$BODY" | grep -o '"authAttemptId":[0-9]*' | grep -o '[0-9]*')
            echo -e "${GREEN}✓ Login request successful (authAttemptId: ${AUTH_ATTEMPT_ID})${NC}"
            return 0
        elif echo "$BODY" | grep -q "token"; then
            echo -e "${GREEN}✓ Login successful (token received)${NC}"
            return 0
        else
            echo -e "${RED}✗ Unexpected response format${NC}"
            echo "$BODY"
            return 1
        fi
    else
        echo -e "${RED}✗ Login failed (HTTP ${HTTP_CODE})${NC}"
        echo "$BODY"
        return 1
    fi
}

# Function to get list of test classes
get_test_classes() {
    mvn test -Dtest=*Test -DfailIfNoTests=false 2>&1 | \
        grep -E "Running org\.ezkey\.tests" | \
        sed 's/.*Running //' | \
        sed 's/\.\.\..*//' | \
        sort -u
}

# Main diagnostic flow
echo "Step 1: Checking initial admin enrollment state..."
check_admin_enrollment

echo "Step 2: Testing admin login before tests..."
if test_admin_login; then
    echo -e "${GREEN}✓ Admin login works before tests${NC}"
else
    echo -e "${RED}✗ Admin login already broken before tests!${NC}"
    exit 1
fi

echo ""
echo "Step 3: Getting list of test classes..."
TEST_CLASSES=$(get_test_classes)
echo "Found $(echo "$TEST_CLASSES" | wc -l) test classes"
echo ""

echo "Step 4: Running tests one by one..."
echo "=========================================="
echo ""

FAILED_TEST=""
TEST_COUNT=0

for TEST_CLASS in $TEST_CLASSES; do
    TEST_COUNT=$((TEST_COUNT + 1))
    echo "[$TEST_COUNT] Running: $TEST_CLASS"
    
    # Run the test
    if mvn test -Dtest="$TEST_CLASS" -DfailIfNoTests=false > /tmp/test-output.log 2>&1; then
        echo -e "  ${GREEN}✓ Test passed${NC}"
    else
        echo -e "  ${YELLOW}⚠ Test failed (but continuing)${NC}"
    fi
    
    # Check if admin login still works
    echo "  Checking admin login after test..."
    if test_admin_login; then
        echo -e "  ${GREEN}✓ Admin login still works${NC}"
    else
        echo -e "  ${RED}✗ Admin login BROKEN after this test!${NC}"
        FAILED_TEST="$TEST_CLASS"
        break
    fi
    
    echo ""
done

echo "=========================================="
echo "Diagnostic Complete"
echo "=========================================="
if [ -n "$FAILED_TEST" ]; then
    echo -e "${RED}✗ Authentication broken by: $FAILED_TEST${NC}"
    echo ""
    echo "Last test output:"
    tail -50 /tmp/test-output.log
    exit 1
else
    echo -e "${GREEN}✓ All tests completed, authentication still works${NC}"
    exit 0
fi

