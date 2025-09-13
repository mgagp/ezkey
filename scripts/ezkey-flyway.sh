#!/bin/bash
set -e

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

# Check if migration JAR exists, build if needed
MIGRATION_JAR="target/ezkey-migration.jar"
if [ ! -f "$MIGRATION_JAR" ]; then
    echo "Migration JAR not found. Building..."
    mvn clean package -Pmigration-jar -q
fi

# Run the migration application
if [ -z "$1" ]; then
    echo "Running default migration..."
    mvn spring-boot:run -q
else
    echo "Running Flyway command: $*"
    mvn spring-boot:run -Dspring-boot.run.arguments="$*" -q
fi

echo "Done!"
