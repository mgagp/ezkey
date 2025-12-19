#!/bin/bash

# AOT Classpath Debugging Script
# 
# This script helps diagnose classpath issues during Spring Boot AOT processing
# by capturing and analyzing the classpath used by the AOT processor.
#
# Usage:
#   ./scripts/debug-aot-classpath.sh [options]
#
# Options:
#   --verbose             Show detailed Maven output
#   --save-classpath      Save classpath to file for analysis
#   --check-class CLASS   Check if a specific class is in the classpath
#   --help                Show this help message

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" &> /dev/null && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"

# Default options
VERBOSE=""
SAVE_CLASSPATH=false
CHECK_CLASS=""
SHOW_RAW=false
MAVEN_QUIET="-q"

# Parse command line arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        --verbose)
            VERBOSE=""
            MAVEN_QUIET=""
            shift
            ;;
        --save-classpath)
            SAVE_CLASSPATH=true
            shift
            ;;
        --check-class)
            CHECK_CLASS="$2"
            shift 2
            ;;
        --show-raw)
            SHOW_RAW=true
            shift
            ;;
        --help)
            echo "Usage: $0 [options]"
            echo ""
            echo "Options:"
            echo "  --verbose             Show detailed Maven output"
            echo "  --save-classpath      Save classpath to file for analysis"
            echo "  --check-class CLASS   Check if a specific class is in the classpath"
            echo "  --show-raw            Show raw classpath content for debugging"
            echo "  --help                Show this help message"
            echo ""
            echo "Examples:"
            echo "  $0 --save-classpath"
            echo "  $0 --check-class org.ezkey.authattempt.dto.AuthAttemptPendingResponse"
            exit 0
            ;;
        *)
            echo "Unknown option: $1"
            echo "Use --help for usage information"
            exit 1
            ;;
    esac
done

echo "=== AOT Classpath Debugging ==="
echo "Project Directory: $PROJECT_DIR"
echo ""

cd "$PROJECT_DIR"

# Step 1: Ensure ezkey-core is installed
echo "Step 1: Ensuring ezkey-core is installed..."
mvn -pl ezkey-core install -DskipTests $MAVEN_QUIET
echo "✅ ezkey-core installed"
echo ""

# Step 2: Compile ezkey-auth-api (without packaging to avoid triggering AOT)
echo "Step 2: Compiling ezkey-auth-api..."
echo "  Note: Using 'compile' instead of 'package' to avoid triggering AOT automatically"
mvn -pl ezkey-auth-api -am -Pnative clean compile -DskipTests $MAVEN_QUIET
if [ $? -ne 0 ]; then
    echo "❌ Failed to compile ezkey-auth-api"
    exit 1
fi
echo "✅ ezkey-auth-api compiled"
echo ""

# Step 3: Prepare classpath inspection
echo "Step 3: Preparing classpath inspection..."
echo ""

# Step 4: Capture classpath using Maven dependency plugin
echo "Step 4: Capturing classpath used by Spring Boot AOT..."
echo ""

CLASSPATH_FILE="$PROJECT_DIR/ezkey-auth-api/target/aot-classpath.txt"

# Use Maven dependency plugin to get compile classpath (what AOT would use)
echo "Getting compile classpath (dependencies only)..."
mvn -pl ezkey-auth-api -am -Pnative dependency:build-classpath -Dmdep.outputFile="$CLASSPATH_FILE" -Dmdep.includeScope=compile $MAVEN_QUIET

# Add target/classes to the classpath (AOT includes it)
TARGET_CLASSES="$PROJECT_DIR/ezkey-auth-api/target/classes"
if [ -d "$TARGET_CLASSES" ]; then
    # Read the current classpath (it's all on one line)
    CURRENT_CP=$(cat "$CLASSPATH_FILE" | tr -d '\r\n' | sed 's/[[:space:]]*$//')
    
    # Detect separator from content
    if [[ "$CURRENT_CP" == *";"* ]]; then
        SEP=";"
    else
        SEP=":"
    fi
    
    # Append target/classes with proper separator
    echo "${CURRENT_CP}${SEP}${TARGET_CLASSES}" > "$CLASSPATH_FILE"
    echo "  Added target/classes to classpath: $TARGET_CLASSES"
fi

if [ -f "$CLASSPATH_FILE" ]; then
    echo "✅ Classpath captured to: $CLASSPATH_FILE"
    echo ""
    
    # Read classpath content (handle Windows line endings)
    CLASS_PATH_CONTENT=$(cat "$CLASSPATH_FILE" | tr -d '\r' | tr '\n' ' ')
    
    # Remove trailing spaces
    CLASS_PATH_CONTENT=$(echo "$CLASS_PATH_CONTENT" | sed 's/[[:space:]]*$//')
    
    # Detect separator (Windows uses ;, Unix uses :)
    # Check if content contains semicolon (Windows) or colon (Unix)
    if [[ "$CLASS_PATH_CONTENT" == *";"* ]]; then
        SEPARATOR=';'
        echo "Detected Windows classpath separator (;)"
    elif [[ "$CLASS_PATH_CONTENT" == *":"* ]]; then
        SEPARATOR=':'
        echo "Detected Unix classpath separator (:)"
    else
        # Default to colon if no separator found (single entry)
        SEPARATOR=':'
        echo "No separator detected, assuming single entry (using :)"
    fi
    
    # Split classpath into array
    IFS="$SEPARATOR" read -ra ENTRIES <<< "$CLASS_PATH_CONTENT"
    ENTRY_COUNT=${#ENTRIES[@]}
    
    # Clean up entries (remove trailing separators and empty entries)
    CLEAN_ENTRIES=()
    for entry in "${ENTRIES[@]}"; do
        # Remove trailing separator if present
        entry=$(echo "$entry" | sed "s/[${SEPARATOR}]$//")
        # Skip empty entries
        if [ -n "$entry" ]; then
            CLEAN_ENTRIES+=("$entry")
        fi
    done
    ENTRIES=("${CLEAN_ENTRIES[@]}")
    ENTRY_COUNT=${#ENTRIES[@]}
    
    # Show raw content if requested
    if [ "$SHOW_RAW" = true ]; then
        echo "=== Raw Classpath Content (first 500 chars) ==="
        echo "$CLASS_PATH_CONTENT" | head -c 500
        echo "..."
        echo ""
        echo "Separator detected: '$SEPARATOR'"
        echo ""
    fi
    
    echo "=== Classpath Analysis ==="
    echo "Total classpath entries: $ENTRY_COUNT"
    echo ""
    
    # Check for key directories/JARs
    echo "=== Key Classpath Entries ==="
    echo "Looking for ezkey-auth-api/target/classes..."
    FOUND_AUTH_API=false
    TARGET_CLASSES_PATH="$PROJECT_DIR/ezkey-auth-api/target/classes"
    
    # Normalize target path for comparison
    TARGET_NORMALIZED=$(echo "$TARGET_CLASSES_PATH" | tr '\\' '/' | sed 's|^/c/|C:|' | sed 's|^/mnt/c/|C:|')
    
    for entry in "${ENTRIES[@]}"; do
        # Clean entry (remove trailing separators, etc.)
        CLEAN_ENTRY=$(echo "$entry" | sed "s/[${SEPARATOR}]$//" | sed 's/^[[:space:]]*//' | sed 's/[[:space:]]*$//')
        
        if [ -z "$CLEAN_ENTRY" ]; then
            continue
        fi
        
        # Normalize paths for comparison (handle Windows vs Unix paths)
        # Convert /c/ to C: and /mnt/c/ to C: for Git Bash paths
        ENTRY_NORMALIZED=$(echo "$CLEAN_ENTRY" | tr '\\' '/' | sed 's|^/c/|C:|' | sed 's|^/mnt/c/|C:|')
        
        # Check for exact match or contains target/classes
        if [[ "$ENTRY_NORMALIZED" == "$TARGET_NORMALIZED" ]] || 
           [[ "$ENTRY_NORMALIZED" == *"ezkey-auth-api"*"target/classes" ]] ||
           [[ "$ENTRY_NORMALIZED" == *"target/classes" ]] && [[ "$ENTRY_NORMALIZED" == *"auth-api"* ]]; then
            echo "✅ Found: $CLEAN_ENTRY"
            FOUND_AUTH_API=true
            break
        fi
    done
    
    if [ "$FOUND_AUTH_API" = false ]; then
        echo "❌ ezkey-auth-api/target/classes NOT found in classpath!"
        echo "   Expected: $TARGET_CLASSES_PATH"
        echo "   This is likely the root cause of the AOT failures!"
    fi
    
    echo ""
    echo "Looking for ezkey-core JAR..."
    FOUND_CORE=false
    for entry in "${ENTRIES[@]}"; do
        if [[ "$entry" == *"ezkey-core"* ]] && [[ "$entry" == *".jar"* ]]; then
            echo "✅ Found: $entry"
            FOUND_CORE=true
        fi
    done
    
    if [ "$FOUND_CORE" = false ]; then
        echo "❌ ezkey-core JAR NOT found in classpath!"
    fi
    
    echo ""
    
    # Check for specific class if requested
    if [ -n "$CHECK_CLASS" ]; then
        echo "=== Checking for class: $CHECK_CLASS ==="
        CLASS_FILE=$(echo "$CHECK_CLASS" | tr '.' '/')
        FOUND_CLASS=false
        FOUND_IN_TARGET=false
        
        # First check if class file exists in target/classes (ezkey-auth-api)
        if [ -f "$PROJECT_DIR/ezkey-auth-api/target/classes/$CLASS_FILE.class" ]; then
            echo "✅ Class file exists in ezkey-auth-api: $PROJECT_DIR/ezkey-auth-api/target/classes/$CLASS_FILE.class"
            FOUND_IN_TARGET=true
        fi
        
        # Also check in ezkey-core JAR (for domain classes)
        CORE_JAR=""
        for entry in "${ENTRIES[@]}"; do
            CLEAN_ENTRY=$(echo "$entry" | sed "s/[${SEPARATOR}]$//" | sed 's/^[[:space:]]*//' | sed 's/[[:space:]]*$//')
            if [[ "$CLEAN_ENTRY" == *"ezkey-core"* ]] && [[ "$CLEAN_ENTRY" == *".jar"* ]]; then
                CORE_JAR="$CLEAN_ENTRY"
                # Check if class exists in core JAR
                if command -v jar &> /dev/null; then
                    if jar -tf "$CORE_JAR" 2>/dev/null | grep -q "$CLASS_FILE.class"; then
                        echo "✅ Class file exists in ezkey-core JAR: $CORE_JAR"
                        FOUND_IN_TARGET=true
                    fi
                elif command -v unzip &> /dev/null; then
                    if unzip -l "$CORE_JAR" 2>/dev/null | grep -q "$CLASS_FILE.class"; then
                        echo "✅ Class file exists in ezkey-core JAR: $CORE_JAR"
                        FOUND_IN_TARGET=true
                    fi
                fi
                break
            fi
        done
        
        # Then check if it's in the classpath
        echo "Searching in classpath entries..."
        for entry in "${ENTRIES[@]}"; do
            # Clean entry
            CLEAN_ENTRY=$(echo "$entry" | sed "s/[${SEPARATOR}]$//" | sed 's/^[[:space:]]*//' | sed 's/[[:space:]]*$//')
            
            if [ -z "$CLEAN_ENTRY" ]; then
                continue
            fi
            
            if [ -d "$CLEAN_ENTRY" ]; then
                # Directory entry
                if [ -f "$CLEAN_ENTRY/$CLASS_FILE.class" ]; then
                    echo "✅ Class found in classpath directory: $CLEAN_ENTRY/$CLASS_FILE.class"
                    FOUND_CLASS=true
                fi
            elif [ -f "$CLEAN_ENTRY" ] && [[ "$CLEAN_ENTRY" == *.jar ]]; then
                # JAR entry - check if class exists (use jar or unzip if available)
                if command -v jar &> /dev/null; then
                    if jar -tf "$CLEAN_ENTRY" 2>/dev/null | grep -q "$CLASS_FILE.class"; then
                        echo "✅ Class found in JAR: $CLEAN_ENTRY"
                        FOUND_CLASS=true
                    fi
                elif command -v unzip &> /dev/null; then
                    if unzip -l "$CLEAN_ENTRY" 2>/dev/null | grep -q "$CLASS_FILE.class"; then
                        echo "✅ Class found in JAR: $CLEAN_ENTRY"
                        FOUND_CLASS=true
                    fi
                fi
            fi
        done
        
        echo ""
        
        # Try to actually load the class using Java
        echo "=== Testing Class Loading ==="
        echo "Attempting to load class: $CHECK_CLASS"
        
        # Build classpath string from entries (use detected separator)
        CLASSPATH_STRING=$(IFS="$SEPARATOR"; echo "${ENTRIES[*]}")
        
        # Create a simple Java test class
        TEST_CLASS="$PROJECT_DIR/ezkey-auth-api/target/classpath-test/TestClassLoader.java"
        mkdir -p "$(dirname "$TEST_CLASS")"
        cat > "$TEST_CLASS" << EOF
public class TestClassLoader {
    public static void main(String[] args) {
        String className = args[0];
        try {
            Class<?> clazz = Class.forName(className);
            System.out.println("SUCCESS: Class loaded: " + clazz.getName());
            if (clazz.getProtectionDomain() != null && 
                clazz.getProtectionDomain().getCodeSource() != null) {
                System.out.println("Location: " + 
                    clazz.getProtectionDomain().getCodeSource().getLocation());
            }
            System.exit(0);
        } catch (ClassNotFoundException e) {
            System.out.println("FAILED: ClassNotFoundException: " + className);
            System.exit(1);
        } catch (Exception e) {
            System.out.println("ERROR: " + e.getMessage());
            e.printStackTrace();
            System.exit(2);
        }
    }
}
EOF
        
        # Compile and run the test
        TEST_DIR=$(dirname "$TEST_CLASS")
        if javac -d "$TEST_DIR" "$TEST_CLASS" 2>&1; then
            # Use appropriate separator for Java classpath
            if [[ "$SEPARATOR" == ";" ]]; then
                # Windows: use ; but Java expects ; on Windows
                TEST_CLASSPATH="$CLASSPATH_STRING;$TEST_DIR"
            else
                # Unix: use :
                TEST_CLASSPATH="$CLASSPATH_STRING:$TEST_DIR"
            fi
            # Run from the test directory to ensure TestClassLoader can be found
            cd "$TEST_DIR"
            if java -cp "$TEST_CLASSPATH" TestClassLoader "$CHECK_CLASS" 2>&1; then
                echo "✅ Class can be loaded from classpath!"
            else
                echo "❌ Class CANNOT be loaded from classpath!"
                if [ "$FOUND_IN_TARGET" = true ]; then
                    echo ""
                    echo "🔍 ROOT CAUSE CONFIRMED:"
                    echo "   Class exists in target/classes but cannot be loaded."
                    echo "   This means target/classes is NOT in the classpath used by AOT."
                fi
            fi
            else
                echo "⚠️  Could not compile test class (Java may not be in PATH)"
                echo "   Compilation errors (if any):"
                javac -d "$TEST_DIR" "$TEST_CLASS" 2>&1 || true
            fi
            # Return to project directory
            cd "$PROJECT_DIR"
            echo ""
    fi
    
    # Show first 20 entries
    echo "=== First 20 Classpath Entries ==="
    if [[ "$SEPARATOR" == ";" ]]; then
        head -n 1 "$CLASSPATH_FILE" | tr ';' '\n' | head -n 20
    else
        head -n 1 "$CLASSPATH_FILE" | tr ':' '\n' | head -n 20
    fi
    echo "..."
    echo ""
    
    if [ "$SAVE_CLASSPATH" = true ]; then
        echo "✅ Classpath saved to: $CLASSPATH_FILE"
    else
        echo "💡 Tip: Use --save-classpath to save full classpath to file"
    fi
else
    echo "❌ Failed to capture classpath"
    exit 1
fi

echo ""
echo "=== Next Steps ==="
echo "1. Review the classpath entries above"
echo "2. Verify that ezkey-auth-api/target/classes is included"
echo "3. Verify that ezkey-core JAR is included"
echo "4. Check if missing classes are in the classpath"
echo ""
echo "To check a specific class:"
echo "  $0 --check-class org.ezkey.authattempt.dto.AuthAttemptPendingResponse"
echo ""
