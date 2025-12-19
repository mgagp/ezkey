#!/bin/bash

# Native Build Script for ezkey-admin-api
# 
# This script builds a native image for admin-api WITHOUT AOT processing.
# Admin API uses limited native compilation (native image only) for memory
# footprint reduction, without the complexity of AOT configuration.
#
# Strategy: Native compilation without AOT provides ~50% memory reduction
# while maintaining acceptable startup time for batch operations.
#
# Usage:
#   ./scripts/build-native-admin.sh [options]
#
# Options:
#   --skip-tests          Skip tests during build
#   --verbose             Show detailed Maven output
#   --help                Show this help message

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" &> /dev/null && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"

# Default options
SKIP_TESTS=""
VERBOSE=""

# Parse command line arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        --skip-tests)
            SKIP_TESTS="-DskipTests"
            shift
            ;;
        --verbose)
            VERBOSE=""
            shift
            ;;
        --help)
            echo "Usage: $0 [options]"
            echo ""
            echo "Options:"
            echo "  --skip-tests          Skip tests during build"
            echo "  --verbose             Show detailed Maven output"
            echo "  --help                Show this help message"
            echo ""
            echo "Example:"
            echo "  $0 --skip-tests       # Build without tests"
            exit 0
            ;;
        *)
            echo "Unknown option: $1"
            echo "Use --help for usage information"
            exit 1
            ;;
    esac
done

# Set Maven quiet flag if not verbose
if [ -z "$VERBOSE" ]; then
    MAVEN_QUIET="-q"
else
    MAVEN_QUIET=""
fi

echo "=== Ezkey Admin API Native Build (Limited - No AOT) ==="
echo "Project Directory: $PROJECT_DIR"
echo ""
echo "Strategy: Native compilation without AOT for memory reduction"
echo "  - Memory: ~50% reduction (200-300MB → 100-150MB)"
echo "  - Startup: Similar to JVM (~15-20s) - acceptable for batch operations"
echo "  - Build: Simpler (no AOT configuration required)"
echo ""

# Step 1: Clean and compile
echo "Step 1/2: Compiling ezkey-admin-api..."
echo "  Building standard JAR (no AOT processing)"
mvn -pl ezkey-admin-api -am clean compile $SKIP_TESTS $MAVEN_QUIET
if [ $? -ne 0 ]; then
    echo "❌ Failed to compile ezkey-admin-api"
    exit 1
fi
echo "✅ ezkey-admin-api compiled successfully"
echo ""

# Step 2: Build native image
echo "Step 2/2: Building native image..."
echo "  Using Spring Boot buildpacks (no AOT required)"
mvn -pl ezkey-admin-api -Pnative spring-boot:build-image \
    -Dspring-boot.build-image.imageName=ezkey-admin-api-native \
    $SKIP_TESTS \
    -Dspring-boot.build-image.skip=false \
    $MAVEN_QUIET

if [ $? -ne 0 ]; then
    echo "❌ Native image build failed"
    echo ""
    echo "Troubleshooting:"
    echo "  1. Ensure Docker is running"
    echo "  2. Check network connectivity (buildpacks need to download)"
    echo "  3. Try running with --verbose to see detailed error messages"
    exit 1
fi

echo "✅ Native image built successfully: ezkey-admin-api-native"
echo ""

echo "=== Build Complete ==="
echo ""
echo "Native image: ezkey-admin-api-native:latest"
echo ""
echo "To run the native image:"
echo "  docker run -p 9080:9080 \\"
echo "    -e SPRING_PROFILES_ACTIVE=native \\"
echo "    -e SPRING_DATASOURCE_URL=jdbc:postgresql://host.docker.internal:5432/ezkey_db \\"
echo "    ezkey-admin-api-native:latest"
echo ""
echo "Or use docker-compose:"
echo "  docker-compose -f docker/docker-compose.native.yml up admin-api"
echo ""
