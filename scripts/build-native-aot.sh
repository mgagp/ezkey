#!/bin/bash

# Native AOT Build Script for ezkey-auth-api
# 
# This script implements a specific build sequence to avoid intermittent
# MapStruct compilation issues with AOT processing.
#
# Problem: MapStruct generates mappers that reference classes from ezkey-core.
# If ezkey-core is not installed in the local Maven repository before
# ezkey-auth-api compilation, the generated mapper bytecode may contain
# "Unresolved compilation problems" that cause AOT processing to fail.
#
# Solution: Build in a specific order:
#   1. Clean everything
#   2. Install ezkey-core first (ensures it's in local repository)
#   3. Compile ezkey-auth-api with dependencies (MapStruct can resolve classes)
#   4. Execute AOT processing (Spring can introspect properly compiled mappers)
#
# Usage:
#   ./scripts/build-native-aot.sh [options]
#
# Options:
#   --skip-tests          Skip tests and AOT test processing (uses maven.test.skip)
#   --skip-aot            Skip AOT processing (only compile)
#   --verbose             Show detailed Maven output
#   --help                Show this help message

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" &> /dev/null && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"

# Default options
SKIP_TESTS=""
SKIP_AOT=false
VERBOSE=""
DEBUG_CLASSPATH=false

# Parse command line arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        --skip-tests)
            SKIP_TESTS="-Dmaven.test.skip=true"
            shift
            ;;
        --skip-aot)
            SKIP_AOT=true
            shift
            ;;
        --verbose)
            VERBOSE=""
            shift
            ;;
        --debug-classpath)
            DEBUG_CLASSPATH=true
            shift
            ;;
        --help)
            echo "Usage: $0 [options]"
            echo ""
            echo "Options:"
            echo "  --skip-tests          Skip tests during build"
            echo "  --skip-aot            Skip AOT processing (only compile)"
            echo "  --verbose             Show detailed Maven output"
            echo "  --debug-classpath     Show classpath before AOT processing"
            echo "  --help                Show this help message"
            echo ""
            echo "Example:"
            echo "  $0 --skip-tests       # Build without tests"
            echo "  $0 --skip-aot         # Compile only, skip AOT"
            echo "  $0 --debug-classpath # Show classpath analysis"
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

echo "=== Ezkey Auth API Native AOT Build ==="
echo "Project Directory: $PROJECT_DIR"
echo ""

# Step 1: Clean everything
echo "Step 1/4: Cleaning all modules..."
cd "$PROJECT_DIR"
mvn clean $MAVEN_QUIET
echo "✅ Clean complete"
echo ""

# Step 2: Install ezkey-core first (without dependencies to avoid reactor issues)
echo "Step 2/4: Installing ezkey-core module..."
echo "  This ensures ezkey-core classes are available in local Maven repository"
echo "  before MapStruct generates mappers in ezkey-auth-api"
mvn -pl ezkey-core clean install $SKIP_TESTS $MAVEN_QUIET
if [ $? -ne 0 ]; then
    echo "❌ Failed to install ezkey-core"
    exit 1
fi
echo "✅ ezkey-core installed successfully"
echo ""

# Step 3: Compile ezkey-auth-api with dependencies (without AOT)
echo "Step 3/4: Compiling ezkey-auth-api with dependencies..."
echo "  MapStruct will now generate mappers with all required classes available"
echo "  Using 'compile' instead of 'package' to avoid triggering AOT automatically"
echo "  (AOT will be executed explicitly in the next step)"
mvn -pl ezkey-auth-api -am compile $SKIP_TESTS $MAVEN_QUIET
if [ $? -ne 0 ]; then
    echo "❌ Failed to compile ezkey-auth-api"
    exit 1
fi

# Verify critical classes exist after compilation
echo "  Verifying compiled classes..."
MISSING_CLASSES=0
if [ ! -f "$PROJECT_DIR/ezkey-auth-api/target/classes/org/ezkey/authattempt/dto/AuthAttemptPendingRequestDto.class" ]; then
    echo "  ❌ AuthAttemptPendingRequestDto.class NOT found in target/classes"
    MISSING_CLASSES=1
fi
if [ ! -f "$PROJECT_DIR/ezkey-auth-api/target/classes/org/ezkey/authattempt/mapper/AuthAttemptMapperImpl.class" ]; then
    echo "  ❌ AuthAttemptMapperImpl.class NOT found in target/classes"
    MISSING_CLASSES=1
fi

if [ $MISSING_CLASSES -eq 0 ]; then
    echo "  ✅ Critical classes verified in target/classes"
else
    echo "  ⚠️  Some classes are missing - this may cause AOT to fail"
fi

echo "✅ ezkey-auth-api compiled successfully (all classes in target/classes)"
echo ""

# Step 4: Execute AOT processing
if [ "$SKIP_AOT" = false ]; then
    echo "Step 4/4: Executing AOT processing..."
    echo "  Spring AOT will introspect properly compiled mapper classes"
    
    # Debug classpath if requested
    if [ "$DEBUG_CLASSPATH" = true ]; then
        echo ""
        echo "=== Debugging Classpath ==="
        echo "Getting compile classpath used by AOT..."
        CLASSPATH_FILE="$PROJECT_DIR/ezkey-auth-api/target/aot-classpath-debug.txt"
        mvn -pl ezkey-auth-api -am -Pnative dependency:build-classpath \
            -Dmdep.outputFile="$CLASSPATH_FILE" -Dmdep.includeScope=compile $MAVEN_QUIET
        
        if [ -f "$CLASSPATH_FILE" ]; then
            echo "Classpath saved to: $CLASSPATH_FILE"
            echo ""
            echo "Checking for key entries..."
            CLASS_PATH_CONTENT=$(cat "$CLASSPATH_FILE")
            IFS=':' read -ra ENTRIES <<< "$CLASS_PATH_CONTENT"
            
            FOUND_AUTH_API=false
            FOUND_CORE=false
            for entry in "${ENTRIES[@]}"; do
                if [[ "$entry" == *"ezkey-auth-api"* ]] && [[ "$entry" == *"target/classes"* ]]; then
                    echo "✅ ezkey-auth-api/target/classes: $entry"
                    FOUND_AUTH_API=true
                fi
                if [[ "$entry" == *"ezkey-core"* ]] && [[ "$entry" == *".jar"* ]]; then
                    echo "✅ ezkey-core JAR: $entry"
                    FOUND_CORE=true
                fi
            done
            
            if [ "$FOUND_AUTH_API" = false ]; then
                echo "❌ ezkey-auth-api/target/classes NOT in classpath!"
            fi
            if [ "$FOUND_CORE" = false ]; then
                echo "❌ ezkey-core JAR NOT in classpath!"
            fi
            echo ""
        fi
    fi
    
    mvn -pl ezkey-auth-api -Pnative spring-boot:process-aot $SKIP_TESTS $MAVEN_QUIET
    if [ $? -ne 0 ]; then
        echo "❌ AOT processing failed"
        echo ""
        echo "Troubleshooting:"
        echo "  1. Verify that ezkey-core is installed: mvn -pl ezkey-core install"
        echo "  2. Check for MapStruct compilation errors in target/generated-sources"
        echo "  3. Ensure Eclipse/IDE is closed (can interfere with compilation)"
        echo "  4. Try running with --verbose to see detailed error messages"
        exit 1
    fi
    echo "✅ AOT processing completed successfully"
    echo ""
    
    # Verify AOT output
    if [ -f "$PROJECT_DIR/ezkey-auth-api/target/spring-aot/main/sources/org/ezkey/auth/AuthApplication__ApplicationContextInitializer.java" ]; then
        echo "✅ AOT output verified - ApplicationContextInitializer generated"
    else
        echo "⚠️  Warning: AOT output file not found (may be normal if processing was skipped)"
    fi
else
    echo "Step 4/4: Skipping AOT processing (--skip-aot flag)"
fi

echo ""
echo "=== Build Complete ==="
echo ""
echo "Next steps:"
echo "  - To build native image: mvn -pl ezkey-auth-api -Pnative spring-boot:build-image -Dmaven.test.skip=true -Dspring-boot.build-image.skip=false"
echo "  - To run tests: mvn -pl ezkey-auth-api test"
echo ""
