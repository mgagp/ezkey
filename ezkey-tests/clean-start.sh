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
# Usage: ./clean-start.sh [--native] [--ha] [--mvn-bootstrap] [--with-proxy] [--jmx] [--prod-safe]
#   --native: Use native compiled images instead of JVM images (requires pre-built native images)
#   --ha: Use HA stack with 2 instances of each API behind HAProxy load balancers
#   --mvn-bootstrap: Enable Maven-based bootstrap steps (default: disabled, Docker bootstrap-init handles this)
#   --with-proxy: Add Caddy reverse proxy; EZKEY_TRUSTED_PROXIES set so tests can use ports 19080/18080/17080 (trusted proxy path)
#   --jmx: Enable JMX port publishing for VisualVM (DEV ONLY; unauthenticated, non-SSL)
#   --prod-safe: Start using production-safe Spring profile only (docker). Disables docker-dev and docker-test.
#
# Optional environment (passed to Docker Compose for auth-api):
#   EZKEY_DEMO_MITM_SIGNATURE_ENABLED  Maps to ezkey.demo.mitm-signature-enabled. When true, Auth API may
#     tamper Pending JSON after signing for attempts flagged at creation (Admin UI demo checkbox).
#     Default for this script: true (demo-friendly). With --prod-safe: default false unless you set this
#     explicitly before running. See docs/DEMO_MITM_SIGNATURE.md.
#
# Prerequisites:
#   - Docker and Docker Compose installed and running
#   - Maven installed (only if --mvn-bootstrap is used)
#   - Scripts must be run from ezkey-tests directory
#   - If using --native: Native images must be built separately before running
#   - If using --ha: HA stack will be started (for testing ShedLock distributed locking)

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
DOCKER_DIR="${PROJECT_ROOT}/docker"
TEST_STATE_DIR="${SCRIPT_DIR}/.ezkey-test"
NATIVE_MODE=""
HA_MODE=""
MVN_BOOTSTRAP=""
WITH_PROXY=""
ENABLE_JMX=""
PROD_SAFE=""
SPRING_PROFILES=""

# Parse flags
for arg in "$@"; do
    case "$arg" in
        --native)
            NATIVE_MODE="--native"
            ;;
        --ha)
            HA_MODE="--ha"
            ;;
        --mvn-bootstrap)
            MVN_BOOTSTRAP="true"
            ;;
        --with-proxy)
            WITH_PROXY="--with-proxy"
            ;;
        --jmx)
            ENABLE_JMX="true"
            ;;
        --prod-safe)
            PROD_SAFE="true"
            ;;
        *)
            echo "Unknown option: $arg"
            echo "Usage: ./clean-start.sh [--native] [--ha] [--mvn-bootstrap] [--with-proxy] [--jmx] [--prod-safe]"
            exit 1
            ;;
    esac
done

# Validate incompatible options
if [ -n "$NATIVE_MODE" ] && [ -n "$HA_MODE" ]; then
    echo "❌ Error: --native and --ha options are incompatible"
    echo "   HA mode currently only supports regular Spring Boot builds"
    exit 1
fi

# Compute Spring profiles for Docker startup.
# Defaults:
# - docker: base production-like docker profile (required for bootstrap export configuration)
# - docker-dev: local diagnostics (Actuator exposed on management ports)
# - docker-test: permissive test mode (rate limiting disabled)
# - native: native image profile (only when --native is used)
if [ -n "$PROD_SAFE" ]; then
    SPRING_PROFILES="docker"
elif [ -n "$NATIVE_MODE" ]; then
    SPRING_PROFILES="docker,docker-dev,docker-test,native"
else
    SPRING_PROFILES="docker,docker-dev,docker-test"
fi

# Export JMX flag so docker/start.sh and docker/start.ps1 can include JMX override.
if [ -n "$ENABLE_JMX" ]; then
    export EZKEY_ENABLE_JMX=true
fi

# Auth API demo MITM (simulated Pending tamper): default ON for local demo / clean start.
# --prod-safe defaults OFF so the stack behaves closer to production unless overridden.
if [ -n "$PROD_SAFE" ]; then
    export EZKEY_DEMO_MITM_SIGNATURE_ENABLED="${EZKEY_DEMO_MITM_SIGNATURE_ENABLED:-false}"
else
    export EZKEY_DEMO_MITM_SIGNATURE_ENABLED="${EZKEY_DEMO_MITM_SIGNATURE_ENABLED:-true}"
fi

echo "=========================================="
echo "  Ezkey Tests - Clean Start"
echo "=========================================="
echo ""

# Step 1: Stop Docker Compose stack including volumes
if [ -n "$HA_MODE" ]; then
    echo "Step 1/7: Stopping Docker Compose HA stack (including volumes)..."
    COMPOSE_FILE="${DOCKER_DIR}/docker-compose.ha.yml"
    COMPOSE_EXTRA=""
elif [ -n "$NATIVE_MODE" ]; then
    echo "Step 1/7: Stopping Docker Compose stack (including volumes) - Native mode..."
    COMPOSE_FILE="${DOCKER_DIR}/docker-compose.native.yml"
    COMPOSE_EXTRA=""
else
    echo "Step 1/7: Stopping Docker Compose stack (including volumes)..."
    COMPOSE_FILE="${DOCKER_DIR}/docker-compose.yml"
    # Include with-proxy override so Caddy is torn down if it was running
    COMPOSE_EXTRA="-f ${DOCKER_DIR}/docker-compose.with-proxy.yml"
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
    ${DOCKER_COMPOSE} -f "${COMPOSE_FILE}" ${COMPOSE_EXTRA} down -v 2>/dev/null || {
        echo "  ⚠️  Warning: Some containers may not have been running"
    }
    echo "  ✅ Docker stack stopped and volumes removed"
else
    echo "  ⚠️  Warning: ${COMPOSE_FILE} not found"
fi

# Also try to stop the other compose files if they exist (for cleanup)
if [ -n "$HA_MODE" ]; then
    # Stop standard (and with-proxy) and native stacks if running
    if [ -f "${DOCKER_DIR}/docker-compose.yml" ]; then
        ${DOCKER_COMPOSE} -f "${DOCKER_DIR}/docker-compose.yml" -f "${DOCKER_DIR}/docker-compose.with-proxy.yml" down -v 2>/dev/null || true
    fi
    if [ -f "${DOCKER_DIR}/docker-compose.native.yml" ] && [ "${COMPOSE_FILE}" != "${DOCKER_DIR}/docker-compose.native.yml" ]; then
        ${DOCKER_COMPOSE} -f "${DOCKER_DIR}/docker-compose.native.yml" down -v 2>/dev/null || true
    fi
else
    # Stop HA stack if running
    HA_COMPOSE_FILE="${DOCKER_DIR}/docker-compose.ha.yml"
    if [ -f "${HA_COMPOSE_FILE}" ] && [ "${COMPOSE_FILE}" != "${HA_COMPOSE_FILE}" ]; then
        ${DOCKER_COMPOSE} -f "${HA_COMPOSE_FILE}" down -v 2>/dev/null || true
    fi
    # Stop other mode if running (and with-proxy for standard stack)
    if [ -n "$NATIVE_MODE" ]; then
        if [ -f "${DOCKER_DIR}/docker-compose.yml" ]; then
            ${DOCKER_COMPOSE} -f "${DOCKER_DIR}/docker-compose.yml" -f "${DOCKER_DIR}/docker-compose.with-proxy.yml" down -v 2>/dev/null || true
        fi
    else
        if [ -f "${DOCKER_DIR}/docker-compose.native.yml" ]; then
            ${DOCKER_COMPOSE} -f "${DOCKER_DIR}/docker-compose.native.yml" down -v 2>/dev/null || true
        fi
    fi
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
    rm -f "${TEST_STATE_DIR}"/tenant-admin-*-device-credentials.json 2>/dev/null || true
    rm -f "${TEST_STATE_DIR}"/tenant-admin-*-token.json 2>/dev/null || true
    echo "  ✅ Test state files cleaned (including tenant admin credentials)"
else
    echo "  Creating test state directory..."
    mkdir -p "${TEST_STATE_DIR}"
    echo "  ✅ Test state directory created"
fi

echo ""

# Step 3: Generate master encryption key
if [ -n "$HA_MODE" ]; then
    echo "Step 3/7: Generating master encryption key (HA mode)..."
elif [ -n "$NATIVE_MODE" ]; then
    echo "Step 3/7: Generating master encryption key (Native mode)..."
else
    echo "Step 3/7: Generating master encryption key..."
fi
cd "${PROJECT_ROOT}"

if [ -f "${DOCKER_DIR}/generate-encryption-keys.sh" ]; then
    if [ -n "$HA_MODE" ]; then
        # For HA mode, use HA volume name (shared between instances)
        bash "${DOCKER_DIR}/generate-encryption-keys.sh" --ha --force
    elif [ -n "$NATIVE_MODE" ]; then
        bash "${DOCKER_DIR}/generate-encryption-keys.sh" --native --force
    else
        bash "${DOCKER_DIR}/generate-encryption-keys.sh" --force
    fi
    echo "  ✅ Master key generated"
else
    echo "  ❌ Error: generate-encryption-keys.sh not found at ${DOCKER_DIR}/generate-encryption-keys.sh"
    exit 1
fi

echo ""

# Step 4: Start Docker Compose stack with test profiles
if [ -n "$HA_MODE" ]; then
    echo "Step 4/7: Starting Docker Compose HA stack with profiles (${SPRING_PROFILES})..."
    echo "  HA mode: 2 instances of each API behind HAProxy load balancers"
elif [ -n "$NATIVE_MODE" ]; then
    echo "Step 4/7: Starting Docker Compose stack with profiles (${SPRING_PROFILES}) - Native mode..."
elif [ -n "$WITH_PROXY" ]; then
    echo "Step 4/7: Starting Docker Compose stack with profiles (${SPRING_PROFILES}) - Caddy reverse proxy..."
else
    echo "Step 4/7: Starting Docker Compose stack with profiles (${SPRING_PROFILES})..."
fi
cd "${PROJECT_ROOT}"

if [ -n "$HA_MODE" ]; then
    # Use HA start script
    if [ -f "${DOCKER_DIR}/start-ha.sh" ]; then
        echo "  Starting HA stack with SPRING_PROFILES_ACTIVE=${SPRING_PROFILES}..."
        SPRING_PROFILES_ACTIVE="${SPRING_PROFILES}" bash "${DOCKER_DIR}/start-ha.sh"
        echo "  ✅ Docker HA stack started"
    else
        echo "  ❌ Error: start-ha.sh not found at ${DOCKER_DIR}/start-ha.sh"
        exit 1
    fi
elif [ -f "${DOCKER_DIR}/start.sh" ]; then
    if [ -n "$NATIVE_MODE" ]; then
        echo "  Starting stack with SPRING_PROFILES_ACTIVE=${SPRING_PROFILES}..."
        echo "  Using native compiled images..."
        SPRING_PROFILES_ACTIVE="${SPRING_PROFILES}" bash "${DOCKER_DIR}/start.sh" ${NATIVE_MODE} ${WITH_PROXY}
    else
        echo "  Starting stack with SPRING_PROFILES_ACTIVE=${SPRING_PROFILES}..."
        SPRING_PROFILES_ACTIVE="${SPRING_PROFILES}" bash "${DOCKER_DIR}/start.sh" ${WITH_PROXY}
    fi
    echo "  ✅ Docker stack started"
else
    echo "  ❌ Error: start.sh not found at ${DOCKER_DIR}/start.sh"
    exit 1
fi

echo ""

# Step 5-7: Maven-based bootstrap (optional, disabled by default)
if [ -n "$MVN_BOOTSTRAP" ]; then
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
else
    echo "Step 5/7: Skipping Maven-based bootstrap (Docker bootstrap-init handles this)"
    echo "  ✅ Bootstrap handled by Docker bootstrap-init container"
    echo ""
fi

echo ""
echo "=========================================="
echo "  ✅ Clean Start Complete!"
echo "=========================================="
echo ""
echo "📋 Stack Status:"
if [ -n "$HA_MODE" ]; then
    echo "  - Docker stack: Running HA mode with profiles (${SPRING_PROFILES})"
    echo "  - Instances: 2x admin-api, 2x auth-api behind HAProxy load balancers"
    echo "  - Admin API: http://localhost:9080 (via HAProxy)"
    echo "  - Auth API: http://localhost:8080 (via HAProxy)"
    echo "  - HAProxy Stats: http://localhost:9081/stats (Admin), http://localhost:8085/stats (Auth)"
elif [ -n "$NATIVE_MODE" ]; then
    echo "  - Docker stack: Running with profiles (${SPRING_PROFILES}) (NATIVE mode)"
    echo "  - Images: Using native compiled images (ezkey-admin-api-native, ezkey-auth-api-native)"
elif [ -n "$WITH_PROXY" ]; then
    echo "  - Docker stack: Running with profiles (${SPRING_PROFILES}) (Caddy reverse proxy)"
    echo "  - Admin API (via Caddy): http://localhost:19080"
    echo "  - Auth API (via Caddy):  http://localhost:18080"
    echo "  - M2M API (via Caddy):   http://localhost:17080"
    echo "  - Direct ports still available: Admin 9080, Auth 8080, M2M 7080"
else
    echo "  - Docker stack: Running with profiles (${SPRING_PROFILES})"
fi
if [ -z "$PROD_SAFE" ]; then
    echo "  - Auth API demo MITM: EZKEY_DEMO_MITM_SIGNATURE_ENABLED=${EZKEY_DEMO_MITM_SIGNATURE_ENABLED} (Pending tamper when attempt is flagged)"
else
    echo "  - Auth API demo MITM: EZKEY_DEMO_MITM_SIGNATURE_ENABLED=${EZKEY_DEMO_MITM_SIGNATURE_ENABLED} (use false for prod-like; override before running if needed)"
fi
if [ -n "$MVN_BOOTSTRAP" ]; then
    echo "  - Bootstrap credentials: Extracted to .ezkey-test/bootstrap-credentials.json"
    echo "  - Admin token: Created and saved to .ezkey-test/admin-token.json"
else
    echo "  - Bootstrap: Handled automatically by Docker bootstrap-init container"
    echo "  - Demo-device: Pre-seeded and ready for use"
fi
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
if [ -n "$HA_MODE" ]; then
    echo "  - View logs: cd ../docker && ./manage-ha.sh logs"
    echo "  - View instance logs: cd ../docker && ./manage-ha.sh logs admin-api-1"
    echo "  - Stop stack: cd ../docker && ./manage-ha.sh stop"
    echo "  - View status: cd ../docker && ./manage-ha.sh status"
    echo "  - Check HAProxy stats: curl http://localhost:9081/stats"
    echo "  - Start standard stack: ./clean-start.sh"
else
    echo "  - View logs: cd ../docker && ./manage.sh logs"
    echo "  - Stop stack: cd ../docker && ./manage.sh stop"
    echo "  - View status: cd ../docker && ./manage.sh status"
    if [ -z "$NATIVE_MODE" ]; then
        echo "  - Start with native images: ./clean-start.sh --native"
        echo "  - Start with HA stack: ./clean-start.sh --ha"
        echo "  - Start with Caddy proxy (trusted proxy tests): ./clean-start.sh --with-proxy"
    fi
fi
echo ""

