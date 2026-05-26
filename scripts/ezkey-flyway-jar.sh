#!/bin/bash
set -e

echo "Ezkey Flyway Migration Tool (JAR Mode)"
echo "======================================="

# Get the directory where this script is located
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
MIGRATION_DIR="$SCRIPT_DIR/../ezkey-migration"

# Check if ezkey-migration directory exists
if [ ! -d "$MIGRATION_DIR" ]; then
    echo "Error: ezkey-migration directory not found at $MIGRATION_DIR"
    exit 1
fi

# Change to the migration directory
cd "$MIGRATION_DIR"

# Build the migration JAR if it doesn't exist
MIGRATION_JAR="target/ezkey-migration.jar"
if [ ! -f "$MIGRATION_JAR" ]; then
    echo "Building migration JAR..."
    mvn clean package -Pmigration-jar -q
fi

# Run the migration JAR directly
if [ -z "$1" ]; then
    echo "Running default migration..."
    java -jar "$MIGRATION_JAR"
else
    echo "Running Flyway command: $*"
    java -jar "$MIGRATION_JAR" "$@"
fi

echo "Done!"
