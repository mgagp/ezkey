#!/bin/bash
# Ezkey Encryption Keys Generator for Docker
#
# Generates master key file in Docker volume for Tink encryption.
# This script should be run before first startup or when master key is missing.
#
# Usage: ./docker/generate-encryption-keys.sh [--native] [--ha]
#   --native: Generate keys for native stack (uses ezkey-native_encryption-secrets-native volume)
#   --ha: Generate keys for HA stack (uses ezkey-ha_encryption-secrets-ha volume)
#
# Security: Master key file is stored in persistent Docker volume with 600 permissions

set -e

NATIVE_MODE=""
HA_MODE=""
FORCE_MODE=""
VOLUME_NAME="ezkey_encryption-secrets"

# Parse flags
for arg in "$@"; do
    case "$arg" in
        --native)
            NATIVE_MODE="1"
            VOLUME_NAME="ezkey-native_encryption-secrets-native"
            ;;
        --ha)
            HA_MODE="1"
            VOLUME_NAME="ezkey-ha_encryption-secrets-ha"
            ;;
        --force)
            FORCE_MODE="1"
            ;;
        *)
            echo "Unknown option: $arg"
            echo "Usage: ./generate-encryption-keys.sh [--native] [--ha] [--force]"
            exit 1
            ;;
    esac
done

# Validate incompatible options
if [ -n "$NATIVE_MODE" ] && [ -n "$HA_MODE" ]; then
    echo "❌ Error: --native and --ha options are incompatible"
    exit 1
fi

echo "🔑 Ezkey Encryption Keys Generator (Docker)"
if [ -n "$HA_MODE" ]; then
    echo "   Mode: High Availability (HA)"
elif [ -n "$NATIVE_MODE" ]; then
    echo "   Mode: Native"
fi
echo "=========================================="
echo ""

# Check if Docker is running
if ! docker info > /dev/null 2>&1; then
    echo "❌ Error: Docker is not running. Please start Docker and try again."
    exit 1
fi

# Check if volume exists, create if not
# Note: Docker Compose prefixes volume names with project name (from docker-compose.yml "name: ezkey")
# So the actual volume name is "ezkey_encryption-secrets" (project prefix + volume name)
# For native mode: "ezkey-native_encryption-secrets-native"
# The volume will be created automatically by docker-compose if it doesn't exist,
# but we check/create it here to ensure it exists before generating keys
if ! docker volume inspect "$VOLUME_NAME" > /dev/null 2>&1; then
    echo "📦 Creating Docker volume: $VOLUME_NAME"
    echo "   Note: This volume will also be created by docker-compose if it doesn't exist"
    docker volume create "$VOLUME_NAME"
    echo "✅ Volume created"
else
    echo "✅ Volume already exists: $VOLUME_NAME"
fi

# Create temporary container to generate master key
CONTAINER_NAME="ezkey-keygen-$(date +%s)"
TEMP_IMAGE="alpine:latest"

echo "🔐 Generating master key in Docker volume..."

# Generate master key using temporary container
# Note: Check if master key already exists to avoid overwriting
if docker run --rm -v "$VOLUME_NAME:/etc/ezkey" "$TEMP_IMAGE" test -f /etc/ezkey/secrets/master.key 2>/dev/null; then
    if [ -z "$FORCE_MODE" ]; then
        echo "⚠️  Warning: Master key already exists in volume"
        read -p "   Do you want to overwrite it? (y/N): " -n 1 -r
        echo
        if [[ ! $REPLY =~ ^[Yy]$ ]]; then
            echo "   Skipping master key generation (using existing key)"
            exit 0
        fi
    else
        echo "ℹ️  Master key exists - overwriting (--force mode)"
    fi
fi

# Generate master key using temporary container
# Note: We create spring user (UID 100, GID 101) to match the runtime user in containers
if ! docker run --rm \
    --name "$CONTAINER_NAME" \
    -v "$VOLUME_NAME:/etc/ezkey" \
    "$TEMP_IMAGE" \
    sh -c "
        set -e
        # Install openssl (required for key generation)
        apk add --no-cache openssl > /dev/null 2>&1

        # Create spring user/group to match runtime container (UID 100, GID 101)
        addgroup -g 101 -S spring 2>/dev/null || true
        adduser -u 100 -G spring -S spring 2>/dev/null || true

        # Create directory structure
        mkdir -p /etc/ezkey/secrets
        mkdir -p /etc/ezkey/keysets

        # Generate 32 bytes (256 bits) of cryptographically secure random data
        MASTER_KEY=\$(openssl rand -base64 32)

        # Verify key was generated (should be 44 characters for base64-encoded 32 bytes)
        if [ -z \"\$MASTER_KEY\" ] || [ \${#MASTER_KEY} -lt 40 ]; then
            echo \"❌ Error: Failed to generate master key\" >&2
            exit 1
        fi

        # Save master key
        echo \"\$MASTER_KEY\" > /etc/ezkey/secrets/master.key

        # Verify file was created and has content
        if [ ! -f /etc/ezkey/secrets/master.key ] || [ ! -s /etc/ezkey/secrets/master.key ]; then
            echo \"❌ Error: Failed to save master key\" >&2
            exit 1
        fi

        # Set secure permissions (600 = owner read/write only)
        chmod 600 /etc/ezkey/secrets/master.key

        # Change ownership to spring user (matches runtime container user)
        chown spring:spring /etc/ezkey/secrets/master.key

        # Set directory permissions and ownership
        chmod 700 /etc/ezkey/secrets
        chmod 755 /etc/ezkey/keysets
        chown spring:spring /etc/ezkey/secrets
        chown spring:spring /etc/ezkey/keysets

        echo \"✅ Master key generated successfully\"
        echo \"📁 Location: /etc/ezkey/secrets/master.key\"
        echo \"👤 Owner: spring:spring (UID 100, GID 101)\"
        echo \"🔒 Permissions: 600 (owner read/write only)\"
    "; then
    echo ""
    echo "❌ Error: Failed to generate master key. Please check the error messages above."
    exit 1
fi

echo ""
echo "✅ Master key generation complete!"
echo ""
echo "📋 Next steps:"
echo "   1. Start Docker stack: ./docker/start.sh"
echo "   2. The keyset will be automatically generated on first startup"
echo "   3. Both admin-api and auth-api will use the same encryption keys"
echo ""
echo "⚠️  IMPORTANT: Backup the master key securely!"
echo "   To backup: docker run --rm -v $VOLUME_NAME:/data alpine tar czf - /data/secrets/master.key | gzip > master-key-backup.tar.gz"
echo ""
echo "📝 Note: Volume name in docker-compose.yml is 'encryption-secrets'"
echo "         Docker Compose creates it as '$VOLUME_NAME' (with project prefix)"
echo ""

