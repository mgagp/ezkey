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
# Usage: ./clean-start.sh [--native]
#   --native: Use native compiled images instead of JVM images (requires pre-built native images)
#
# Prerequisites:
#   - Docker and Docker Compose installed and running
#   - Maven installed
#   - Scripts must be run from ezkey-tests directory
#   - If using --native: Native images must be built separately before running

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
DOCKER_DIR="${PROJECT_ROOT}/docker"
TEST_STATE_DIR="${SCRIPT_DIR}/.ezkey-test"
NATIVE_MODE=""

# Parse flags
for arg in "$@"; do
    case "$arg" in
        --native)
            NATIVE_MODE="--native"
            ;;
        *)
            echo "Unknown option: $arg"
            echo "Usage: ./clean-start.sh [--native]"
            exit 1
            ;;
    esac
done

echo "=========================================="
echo "  Ezkey Tests - Clean Start"
echo "=========================================="
echo ""

# Step 1: Stop Docker Compose stack including volumes
if [ -n "$NATIVE_MODE" ]; then
    echo "Step 1/7: Stopping Docker Compose stack (including volumes) - Native mode..."
    COMPOSE_FILE="${DOCKER_DIR}/docker-compose.native.yml"
else
    echo "Step 1/7: Stopping Docker Compose stack (including volumes)..."
    COMPOSE_FILE="${DOCKER_DIR}/docker-compose.yml"
fi
cd "${PROJECT_ROOT}"

# Determine docker compose command
if docker compose version > /dev/null 2>&1; then
    DOCKER_COMPOSE="docker compose"
else
    DOCKER_COMPOSE="docker-compose"
fi

# Stop and remove containers, networks, and volumes
# Try both compose files to ensure cleanup
if [ -f "${COMPOSE_FILE}" ]; then
    echo "  Stopping containers..."
    ${DOCKER_COMPOSE} -f "${COMPOSE_FILE}" down -v 2>/dev/null || {
        echo "  ⚠️  Warning: Some containers may not have been running"
    }
    echo "  ✅ Docker stack stopped and volumes removed"
else
    echo "  ⚠️  Warning: ${COMPOSE_FILE} not found"
fi

# Also try to stop the other compose file if it exists (for cleanup)
OTHER_COMPOSE_FILE="${DOCKER_DIR}/docker-compose.yml"
if [ -n "$NATIVE_MODE" ]; then
    OTHER_COMPOSE_FILE="${DOCKER_DIR}/docker-compose.native.yml"
fi
if [ -f "${OTHER_COMPOSE_FILE}" ] && [ "${COMPOSE_FILE}" != "${OTHER_COMPOSE_FILE}" ]; then
    ${DOCKER_COMPOSE} -f "${OTHER_COMPOSE_FILE}" down -v 2>/dev/null || true
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
if [ -n "$NATIVE_MODE" ]; then
    echo "Step 3/7: Generating master encryption key (Native mode)..."
else
    echo "Step 3/7: Generating master encryption key..."
fi
cd "${PROJECT_ROOT}"

if [ -f "${DOCKER_DIR}/generate-encryption-keys.sh" ]; then
    if [ -n "$NATIVE_MODE" ]; then
        bash "${DOCKER_DIR}/generate-encryption-keys.sh" --native
    else
        bash "${DOCKER_DIR}/generate-encryption-keys.sh"
    fi
    echo "  ✅ Master key generated"
else
    echo "  ❌ Error: generate-encryption-keys.sh not found at ${DOCKER_DIR}/generate-encryption-keys.sh"
    exit 1
fi

echo ""

# Step 4: Start Docker Compose stack with test profiles
if [ -n "$NATIVE_MODE" ]; then
    echo "Step 4/7: Starting Docker Compose stack with test profiles (docker,docker-test) - Native mode..."
else
    echo "Step 4/7: Starting Docker Compose stack with test profiles (docker,docker-test)..."
fi
cd "${PROJECT_ROOT}"

if [ -f "${DOCKER_DIR}/start.sh" ]; then
    if [ -n "$NATIVE_MODE" ]; then
        echo "  Starting stack with SPRING_PROFILES_ACTIVE=docker,docker-test,native..."
        echo "  Using native compiled images..."
        SPRING_PROFILES_ACTIVE=docker,docker-test,native bash "${DOCKER_DIR}/start.sh" ${NATIVE_MODE}
    else
        echo "  Starting stack with SPRING_PROFILES_ACTIVE=docker,docker-test..."
        SPRING_PROFILES_ACTIVE=docker,docker-test bash "${DOCKER_DIR}/start.sh"
    fi
    echo "  ✅ Docker stack started"
else
    echo "  ❌ Error: start.sh not found at ${DOCKER_DIR}/start.sh"
    exit 1
fi

echo ""

# Step 5: Install project and dependencies
echo "Step 5/7: Installing project and dependencies..."
cd "${PROJECT_ROOT}"

echo "  Installing ezkey-admin-api and ezkey-auth-api (required for original classifier JARs)..."
if mvn clean install -pl ezkey-admin-api,ezkey-auth-api -am -DskipTests -q; then
    echo "  ✅ Admin API and Auth API installed successfully"
else
    echo "  ❌ Error: Failed to install Admin API and Auth API"
    echo "  Check logs above for details"
    exit 1
fi

echo "  Compiling ezkey-tests module..."
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
if [ -n "$NATIVE_MODE" ]; then
    echo "  - Docker stack: Running with test profiles (NATIVE mode)"
    echo "  - Images: Using native compiled images (ezkey-admin-api-native, ezkey-auth-api-native)"
else
    echo "  - Docker stack: Running with test profiles"
fi
echo "  - Bootstrap credentials: Extracted to .ezkey-test/bootstrap-credentials.json"
echo "  - Admin token: Created and saved to .ezkey-test/admin-token.json"
echo ""
echo "🧪 Next Steps - Running Tests:"
echo ""
echo "  Default (fast tests only - excludes slow, time-dependent):"
echo "    mvn test -pl ezkey-tests"
echo "    Use case: CI on every commit, quick local validation"
echo ""
echo "  All tests (full validation before release):"
echo "    mvn test -pl ezkey-tests -P all-tests"
echo "    Use case: Complete test suite, pre-release validation"
echo ""
echo "  Slow tests only (nightly builds):"
echo "    mvn test -pl ezkey-tests -P slow-tests"
echo "    Use case: Nightly builds, comprehensive validation"
echo ""
echo "  Smoke tests only (quick sanity check):"
echo "    mvn test -pl ezkey-tests -P smoke-tests"
echo "    Use case: Quick validation, post-deployment check"
echo ""
echo "  Specific test class:"
echo "    mvn test -pl ezkey-tests -Dtest=TestClassName"
echo ""
echo "  Ad-hoc filtering (by test groups):"
echo "    mvn test -pl ezkey-tests -Dgroups=encryption"
echo "    mvn test -pl ezkey-tests -DexcludedGroups=time-dependent"
echo ""
if [ -n "$NATIVE_MODE" ]; then
    echo "📦 Native Mode Information:"
    echo "  - Using native compiled images (ezkey-admin-api-native, ezkey-auth-api-native)"
    echo "  - Faster startup time (~2-3 seconds vs ~15-20 seconds)"
    echo "  - Lower memory usage (~50-100MB vs ~200-300MB)"
    echo "  - To rebuild native images:"
    echo "    mvn spring-boot:build-image -pl ezkey-admin-api -Pnative -Dspring-boot.build-image.imageName=ezkey-admin-api-native -DskipTests"
    echo "    mvn spring-boot:build-image -pl ezkey-auth-api -Pnative -Dspring-boot.build-image.imageName=ezkey-auth-api-native -DskipTests"
    echo ""
fi
echo "💡 Useful Commands:"
echo "  - View logs: cd ../docker && ./manage.sh logs"
echo "  - Stop stack: cd ../docker && ./manage.sh stop"
echo "  - View status: cd ../docker && ./manage.sh status"
if [ -z "$NATIVE_MODE" ]; then
    echo "  - Start with native images: ./clean-start.sh --native"
fi
echo ""

