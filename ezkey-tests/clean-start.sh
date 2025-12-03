#!/bin/bash
# Ezkey Tests - Clean Start Script
# 
# Performs a clean startup of the Docker stack for testing:
# 1. Stops Docker Compose stack including volumes
# 2. Cleans test state files in .ezkey-test directory
# 3. Generates master encryption key
# 4. Starts Docker Compose stack with test profiles (no rate limiting)
# 5. Extracts bootstrap credentials
# 6. Initializes admin token
#
# Usage: ./clean-start.sh
#
# Prerequisites:
#   - Docker and Docker Compose installed and running
#   - Maven installed
#   - Scripts must be run from ezkey-tests directory

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
DOCKER_DIR="${PROJECT_ROOT}/docker"
TEST_STATE_DIR="${SCRIPT_DIR}/.ezkey-test"

echo "=========================================="
echo "  Ezkey Tests - Clean Start"
echo "=========================================="
echo ""

# Step 1: Stop Docker Compose stack including volumes
echo "Step 1/7: Stopping Docker Compose stack (including volumes)..."
cd "${PROJECT_ROOT}"

# Determine docker compose command
if docker compose version > /dev/null 2>&1; then
    DOCKER_COMPOSE="docker compose"
else
    DOCKER_COMPOSE="docker-compose"
fi

COMPOSE_FILE="${DOCKER_DIR}/docker-compose.yml"

# Stop and remove containers, networks, and volumes
if [ -f "${COMPOSE_FILE}" ]; then
    echo "  Stopping containers..."
    ${DOCKER_COMPOSE} -f "${COMPOSE_FILE}" down -v 2>/dev/null || {
        echo "  ⚠️  Warning: Some containers may not have been running"
    }
    echo "  ✅ Docker stack stopped and volumes removed"
else
    echo "  ⚠️  Warning: docker-compose.yml not found at ${COMPOSE_FILE}"
fi

echo ""

# Step 2: Clean test state files
echo "Step 2/7: Cleaning test state files..."
cd "${SCRIPT_DIR}"

if [ -d "${TEST_STATE_DIR}" ]; then
    echo "  Removing files in ${TEST_STATE_DIR}..."
    rm -f "${TEST_STATE_DIR}/admin-token.json" 2>/dev/null || true
    rm -f "${TEST_STATE_DIR}/bootstrap-credentials.json" 2>/dev/null || true
    rm -f "${TEST_STATE_DIR}/device-credentials.json" 2>/dev/null || true
    echo "  ✅ Test state files cleaned"
else
    echo "  Creating test state directory..."
    mkdir -p "${TEST_STATE_DIR}"
    echo "  ✅ Test state directory created"
fi

echo ""

# Step 3: Generate master encryption key
echo "Step 3/7: Generating master encryption key..."
cd "${PROJECT_ROOT}"

if [ -f "${DOCKER_DIR}/generate-encryption-keys.sh" ]; then
    bash "${DOCKER_DIR}/generate-encryption-keys.sh"
    echo "  ✅ Master key generated"
else
    echo "  ❌ Error: generate-encryption-keys.sh not found at ${DOCKER_DIR}/generate-encryption-keys.sh"
    exit 1
fi

echo ""

# Step 4: Start Docker Compose stack with test profiles
echo "Step 4/7: Starting Docker Compose stack with test profiles (docker,docker-test)..."
cd "${PROJECT_ROOT}"

if [ -f "${DOCKER_DIR}/start.sh" ]; then
    echo "  Starting stack with SPRING_PROFILES_ACTIVE=docker,docker-test..."
    SPRING_PROFILES_ACTIVE=docker,docker-test bash "${DOCKER_DIR}/start.sh"
    echo "  ✅ Docker stack started"
else
    echo "  ❌ Error: start.sh not found at ${DOCKER_DIR}/start.sh"
    exit 1
fi

echo ""

# Step 5: Compile project and dependencies
echo "Step 5/7: Compiling project and dependencies..."
cd "${PROJECT_ROOT}"

echo "  Compiling parent project and ezkey-tests module..."
if mvn clean compile test-compile -pl ezkey-tests -am -q; then
    echo "  ✅ Project compiled successfully"
else
    echo "  ❌ Error: Failed to compile project"
    echo "  Check logs above for details"
    exit 1
fi

echo ""

# Step 6: Extract bootstrap credentials
echo "Step 6/7: Extracting bootstrap credentials..."
cd "${PROJECT_ROOT}"

echo "  Running BootstrapCredentialsExtractionTest..."
if mvn test -pl ezkey-tests -Dtest=BootstrapCredentialsExtractionTest -q; then
    echo "  ✅ Bootstrap credentials extracted"
else
    echo "  ❌ Error: Failed to extract bootstrap credentials"
    echo "  Check logs above for details"
    exit 1
fi

echo ""

# Step 7: Initialize admin token
echo "Step 7/7: Initializing admin token..."
cd "${PROJECT_ROOT}"

echo "  Running AdminTokenCreationTest..."
if mvn test -pl ezkey-tests -Dtest=AdminTokenCreationTest -q; then
    echo "  ✅ Admin token initialized"
else
    echo "  ❌ Error: Failed to initialize admin token"
    echo "  Check logs above for details"
    exit 1
fi

echo ""
echo "=========================================="
echo "  ✅ Clean Start Complete!"
echo "=========================================="
echo ""
echo "📋 Stack Status:"
echo "  - Docker stack: Running with test profiles"
echo "  - Bootstrap credentials: Extracted to .ezkey-test/bootstrap-credentials.json"
echo "  - Admin token: Created and saved to .ezkey-test/admin-token.json"
echo ""
echo "🧪 Next Steps:"
echo "  - Run tests: mvn test"
echo "  - Run specific test: mvn test -Dtest=TestClassName"
echo ""
echo "💡 Useful Commands:"
echo "  - View logs: cd ../docker && ./manage.sh logs"
echo "  - Stop stack: cd ../docker && ./manage.sh stop"
echo "  - View status: cd ../docker && ./manage.sh status"
echo ""

