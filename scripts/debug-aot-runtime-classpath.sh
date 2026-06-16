#!/bin/bash

# AOT Runtime Classpath Debugging Script
# 
# This script captures the actual classpath used by Spring Boot AOT processor
# at runtime by using JVM options to dump classpath information.
#
# Usage:
#   ./scripts/debug-aot-runtime-classpath.sh [options]
#
# Options:
#   --verbose             Show detailed Maven output
#   --save-output         Save AOT output to file
#   --help                Show this help message

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" &> /dev/null && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"

# Default options
VERBOSE=""
SAVE_OUTPUT=false
MAVEN_QUIET="-q"

# Parse command line arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        --verbose)
            VERBOSE=""
            MAVEN_QUIET=""
            shift
            ;;
        --save-output)
            SAVE_OUTPUT=true
            shift
            ;;
        --help)
            echo "Usage: $0 [options]"
            echo ""
            echo "Options:"
            echo "  --verbose             Show detailed Maven output"
            echo "  --save-output         Save AOT output to file"
            echo "  --help                Show this help message"
            exit 0
            ;;
        *)
            echo "Unknown option: $1"
            echo "Use --help for usage information"
            exit 1
            ;;
    esac
    shift
done

echo "=== AOT Runtime Classpath Debugging ==="
echo "Project Directory: $PROJECT_DIR"
echo ""

cd "$PROJECT_DIR"

# Step 1: Ensure ezkey-core is installed
echo "Step 1: Ensuring ezkey-core is installed..."
mvn -pl ezkey-core install -DskipTests $MAVEN_QUIET
echo "✅ ezkey-core installed"
echo ""

# Step 2: Package ezkey-auth-api
echo "Step 2: Packaging ezkey-auth-api..."
mvn -pl ezkey-auth-api -am -Pnative clean package -Dmaven.test.skip=true $MAVEN_QUIET
echo "✅ ezkey-auth-api packaged"
echo ""

# Step 3: Create a Java class that prints classpath
echo "Step 3: Creating classpath inspector class..."
mkdir -p "$PROJECT_DIR/ezkey-auth-api/target/classpath-debug"
cat > "$PROJECT_DIR/ezkey-auth-api/target/classpath-debug/ClasspathInspector.java" << 'EOF'
import java.net.URL;
import java.net.URLClassLoader;
import java.lang.module.ModuleLayer;
import java.util.Optional;

public class ClasspathInspector {
    public static void main(String[] args) {
        System.out.println("=== Runtime Classpath Inspection ===");
        System.out.println("");
        
        // Method 1: java.class.path system property
        String classpath = System.getProperty("java.class.path");
        if (classpath != null && !classpath.isEmpty()) {
            System.out.println("=== java.class.path ===");
            String[] entries = classpath.split(System.getProperty("path.separator"));
            System.out.println("Total entries: " + entries.length);
            System.out.println("");
            for (String entry : entries) {
                System.out.println(entry);
            }
            System.out.println("");
        }
        
        // Method 2: ClassLoader URLs (Java 8 and earlier)
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        if (cl instanceof URLClassLoader) {
            URLClassLoader ucl = (URLClassLoader) cl;
            URL[] urls = ucl.getURLs();
            System.out.println("=== ClassLoader URLs ===");
            System.out.println("Total URLs: " + urls.length);
            System.out.println("");
            for (URL url : urls) {
                System.out.println(url.toString());
            }
            System.out.println("");
        }
        
        // Method 3: Try to load a test class to verify classpath
        String[] testClasses = {
            "org.ezkey.authattempt.dto.AuthAttemptPendingResponseDto",
            "org.ezkey.authattempt.dto.AuthAttemptRespondRequestDto",
            "org.ezkey.authattempt.domain.AuthAttemptPendingResponse",
            "org.ezkey.authattempt.mapper.AuthAttemptMapperImpl"
        };
        
        System.out.println("=== Class Loading Test ===");
        for (String className : testClasses) {
            try {
                Class<?> clazz = Class.forName(className);
                System.out.println("✅ " + className);
                if (clazz.getProtectionDomain() != null && 
                    clazz.getProtectionDomain().getCodeSource() != null) {
                    System.out.println("   Location: " + 
                        clazz.getProtectionDomain().getCodeSource().getLocation());
                }
            } catch (ClassNotFoundException e) {
                System.out.println("❌ " + className + " - NOT FOUND");
            } catch (Exception e) {
                System.out.println("⚠️  " + className + " - Error: " + e.getMessage());
            }
        }
    }
}
EOF

# Compile the inspector
echo "Compiling classpath inspector..."
cd "$PROJECT_DIR/ezkey-auth-api/target/classpath-debug"
javac ClasspathInspector.java
echo "✅ Classpath inspector compiled"
echo ""

# Step 4: Run AOT with classpath inspection
echo "Step 4: Running AOT with classpath inspection..."
echo "  Using JVM options to capture classpath information"
echo ""

OUTPUT_FILE="$PROJECT_DIR/ezkey-auth-api/target/aot-runtime-debug.txt"

cd "$PROJECT_DIR"

# Run AOT with verbose class loading and classpath output
if [ "$SAVE_OUTPUT" = true ]; then
    mvn -pl ezkey-auth-api -Pnative spring-boot:process-aot -Dmaven.test.skip=true \
        -Dspring-boot.run.jvmArguments="-verbose:class -XshowSettings:properties -Djava.class.path.print=true" \
        $MAVEN_QUIET 2>&1 | tee "$OUTPUT_FILE"
else
    mvn -pl ezkey-auth-api -Pnative spring-boot:process-aot -Dmaven.test.skip=true \
        -Dspring-boot.run.jvmArguments="-verbose:class" \
        $MAVEN_QUIET 2>&1 | grep -E "(classpath|Classpath|Loading|AuthAttempt)" | head -n 50
fi

echo ""
echo "=== Analysis Complete ==="
if [ "$SAVE_OUTPUT" = true ]; then
    echo "✅ Full output saved to: $OUTPUT_FILE"
    echo ""
    echo "To analyze the output:"
    echo "  grep -i 'classpath' $OUTPUT_FILE"
    echo "  grep -i 'AuthAttempt' $OUTPUT_FILE"
fi
