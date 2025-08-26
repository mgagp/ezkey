#!/bin/bash
set -x
# Ezkey Flyway Migration Tool
echo "Ezkey Flyway Migration Tool"
echo "==========================="

# Get the directory where this script is located
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CORE_DIR="$SCRIPT_DIR/../ezkey-core"

# Check if ezkey-core directory exists
if [ ! -d "$CORE_DIR" ]; then
    echo "Error: ezkey-core directory not found at $CORE_DIR"
    exit 1
fi

# Change to the core directory
cd "$CORE_DIR"

# Check if target directory exists, if not build the project
if [ ! -d "target/classes" ]; then
    echo "Building ezkey-core project..."
    mvn clean compile
    if [ $? -ne 0 ]; then
        echo "Error: Failed to build ezkey-core project"
        exit 1
    fi
fi

# Build classpath with Maven dependencies
echo "Building classpath..."
mvn dependency:build-classpath -Dmdep.outputFile=classpath.txt -q
if [ $? -ne 0 ]; then
    echo "Error: Failed to build classpath"
    exit 1
fi

# Set Java classpath
CLASSPATH="target/classes"
if [ -f "classpath.txt" ]; then
    MAVEN_CLASSPATH=$(cat classpath.txt)
    CLASSPATH="$CLASSPATH:$MAVEN_CLASSPATH"
    rm classpath.txt
fi

# Run the application with the provided arguments
if [ -z "$1" ]; then
    echo "Running default migration..."
    java -cp "$CLASSPATH" org.ezkey.core.EzkeyCoreApp
else
    echo "Running Flyway command: $*"
    java -cp "$CLASSPATH" org.ezkey.core.EzkeyCoreApp "$@"
fi 