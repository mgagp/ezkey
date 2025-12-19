#!/bin/bash

# AOT Execution Order Debugging Script
# 
# This script helps diagnose timing issues with Spring Boot AOT processing
# by checking when classes are generated and when AOT runs.
#
# Usage:
#   ./scripts/debug-aot-execution-order.sh [options]
#
# Options:
#   --verbose             Show detailed Maven output
#   --check-classes       Check if specific classes exist before/after AOT
#   --help                Show this help message

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" &> /dev/null && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"

# Default options
VERBOSE=""
CHECK_CLASSES=false
MAVEN_QUIET="-q"

# Parse command line arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        --verbose)
            VERBOSE=""
            MAVEN_QUIET=""
            shift
            ;;
        --check-classes)
            CHECK_CLASSES=true
            shift
            ;;
        --help)
            echo "Usage: $0 [options]"
            echo ""
            echo "Options:"
            echo "  --verbose             Show detailed Maven output"
            echo "  --check-classes       Check if specific classes exist before/after AOT"
            echo "  --help                Show this help message"
            exit 0
            ;;
        *)
            echo "Unknown option: $1"
            echo "Use --help for usage information"
            exit 1
            ;;
    esac
done

echo "=== AOT Execution Order Debugging ==="
echo "Project Directory: $PROJECT_DIR"
echo ""

cd "$PROJECT_DIR"

# Function to check if a class exists
check_class() {
    local CLASS_NAME="$1"
    local CLASS_FILE=$(echo "$CLASS_NAME" | tr '.' '/')
    local FOUND=false
    
    # Check in target/classes
    if [ -f "$PROJECT_DIR/ezkey-auth-api/target/classes/$CLASS_FILE.class" ]; then
        echo "  ✅ Found in target/classes: $CLASS_FILE.class"
        FOUND=true
    fi
    
    # Check in generated-sources
    if [ -f "$PROJECT_DIR/ezkey-auth-api/target/generated-sources/annotations/$CLASS_FILE.class" ]; then
        echo "  ✅ Found in generated-sources: $CLASS_FILE.class"
        FOUND=true
    fi
    
    # Check in ezkey-core JAR
    CORE_JAR="$PROJECT_DIR/ezkey-core/target/ezkey-core-0.0.1-SNAPSHOT.jar"
    if [ -f "$CORE_JAR" ]; then
        if command -v jar &> /dev/null; then
            if jar -tf "$CORE_JAR" 2>/dev/null | grep -q "$CLASS_FILE.class"; then
                echo "  ✅ Found in ezkey-core JAR: $CLASS_FILE.class"
                FOUND=true
            fi
        fi
    fi
    
    if [ "$FOUND" = false ]; then
        echo "  ❌ NOT FOUND: $CLASS_FILE.class"
    fi
}

# Step 1: Clean everything
echo "Step 1: Cleaning project..."
mvn clean $MAVEN_QUIET
echo "✅ Cleaned"
echo ""

# Step 2: Install ezkey-core
echo "Step 2: Installing ezkey-core..."
mvn -pl ezkey-core install -DskipTests $MAVEN_QUIET
echo "✅ ezkey-core installed"
echo ""

# Step 3: Check classes BEFORE compilation
if [ "$CHECK_CLASSES" = true ]; then
    echo "Step 3: Checking classes BEFORE compilation..."
    echo "  Checking for MapStruct generated classes..."
    check_class "org.ezkey.authattempt.mapper.AuthAttemptMapperImpl"
    echo ""
fi

# Step 4: Compile (but don't run AOT yet)
echo "Step 4: Compiling ezkey-auth-api (without AOT)..."
mvn -pl ezkey-auth-api -am compile -DskipTests $MAVEN_QUIET
echo "✅ Compiled"
echo ""

# Step 5: Check classes AFTER compilation
if [ "$CHECK_CLASSES" = true ]; then
    echo "Step 5: Checking classes AFTER compilation..."
    echo "  Checking for MapStruct generated classes..."
    check_class "org.ezkey.authattempt.mapper.AuthAttemptMapperImpl"
    echo ""
    echo "  Checking for DTOs..."
    check_class "org.ezkey.authattempt.dto.AuthAttemptPendingResponseDto"
    check_class "org.ezkey.authattempt.dto.AuthAttemptRespondRequestDto"
    echo ""
    echo "  Checking for domain classes (should be in ezkey-core)..."
    check_class "org.ezkey.authattempt.domain.AuthAttemptPendingResponse"
    check_class "org.ezkey.authattempt.domain.AuthAttemptRespondResponse"
    echo ""
fi

# Step 6: Check what phase process-aot runs in
echo "Step 6: Checking Maven phase for process-aot..."
echo "  Running Maven help to see plugin configuration..."
mvn -pl ezkey-auth-api -Pnative help:effective-pom $MAVEN_QUIET | grep -A 20 "process-aot" | head -n 30 || echo "  (Could not extract phase information)"
echo ""

# Step 7: List all classes in target/classes
echo "Step 7: Listing classes in target/classes..."
echo "  Total .class files:"
find "$PROJECT_DIR/ezkey-auth-api/target/classes" -name "*.class" 2>/dev/null | wc -l || echo "  0 (directory may not exist)"
echo ""
echo "  MapStruct generated classes:"
find "$PROJECT_DIR/ezkey-auth-api/target/classes" -name "*MapperImpl.class" 2>/dev/null | head -n 10 || echo "  None found"
echo ""

# Step 8: Check generated-sources
echo "Step 8: Checking generated-sources directory..."
if [ -d "$PROJECT_DIR/ezkey-auth-api/target/generated-sources" ]; then
    echo "  ✅ generated-sources directory exists"
    find "$PROJECT_DIR/ezkey-auth-api/target/generated-sources" -name "*.java" 2>/dev/null | wc -l | xargs echo "  Java files:"
    find "$PROJECT_DIR/ezkey-auth-api/target/generated-sources" -name "*MapperImpl.java" 2>/dev/null | head -n 5 || echo "  No MapperImpl.java found"
else
    echo "  ❌ generated-sources directory does not exist"
fi
echo ""

# Step 9: Try to run AOT and see what happens
echo "Step 9: Attempting AOT processing..."
echo "  This will show the actual error if classes are missing..."
echo ""

if mvn -pl ezkey-auth-api -Pnative spring-boot:process-aot -DskipTests $MAVEN_QUIET 2>&1 | tee /tmp/aot-output.txt; then
    echo ""
    echo "✅ AOT processing succeeded!"
else
    echo ""
    echo "❌ AOT processing failed"
    echo ""
    echo "=== Error Analysis ==="
    grep -i "NoClassDefFoundError\|ClassNotFoundException\|Failed to introspect" /tmp/aot-output.txt | head -n 10 || echo "  (Could not extract error details)"
    echo ""
    echo "Full error output saved to: /tmp/aot-output.txt"
fi

echo ""
echo "=== Summary ==="
echo "1. Check if MapStruct classes are generated before AOT runs"
echo "2. Verify that target/classes contains all necessary classes"
echo "3. Check if the error is consistent or intermittent"
echo ""
