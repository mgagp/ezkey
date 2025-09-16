#!/bin/bash
set -e

echo "Ezkey Flyway Migration Tool"
echo "==========================="

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

# Check if migration JAR exists, build if needed
# Use wildcard to find the JAR with version
MIGRATION_JAR=$(ls target/ezkey-migration-*.jar 2>/dev/null | head -1)
if [ -z "$MIGRATION_JAR" ]; then
    echo "Migration JAR not found. Building..."
    mvn clean package -Pmigration-jar -q
    # Re-find the JAR after building
    MIGRATION_JAR=$(ls target/ezkey-migration-*.jar 2>/dev/null | head -1)
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
