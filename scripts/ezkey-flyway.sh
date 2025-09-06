#!/bin/bash
set -e

echo "Ezkey Flyway Migration Tool (Simple)"
echo "===================================="

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

# Use Maven exec plugin to run the application
if [ -z "$1" ]; then
    echo "Running default migration..."
    mvn exec:java -Dexec.mainClass="org.ezkey.core.EzkeyCoreApp" -q
else
    echo "Running Flyway command: $*"
    mvn exec:java -Dexec.mainClass="org.ezkey.core.EzkeyCoreApp" -Dexec.args="$*" -q
fi

echo "Done!"
