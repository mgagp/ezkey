#!/bin/bash
# Ezkey Master Key Generator
# 
# Generates a cryptographically secure master key for encrypting Tink keysets.
# This master key is used to encrypt the DEK (Data Encryption Key) keyset file.
#
# Usage: sudo ./scripts/generate-master-key.sh
#
# Security: Master key file is stored with 600 permissions (owner read/write only)

set -e

echo "🔑 Ezkey Master Key Generator"
echo "================================"
echo ""

# Check if running as root (for proper file permissions)
if [ "$EUID" -ne 0 ]; then 
    echo "❌ Please run as root (needed for secure file creation)"
    echo "   sudo ./scripts/generate-master-key.sh"
    exit 1
fi

# Generate 32 bytes (256 bits) of cryptographically secure random data
MASTER_KEY=$(openssl rand -base64 32)

# Create directory structure
SECRETS_DIR="/etc/ezkey/secrets"
KEYSETS_DIR="/etc/ezkey/keysets"

mkdir -p "$SECRETS_DIR"
mkdir -p "$KEYSETS_DIR"

# Save master key
MASTER_KEY_FILE="$SECRETS_DIR/master.key"
echo "$MASTER_KEY" > "$MASTER_KEY_FILE"

# Secure permissions
chmod 600 "$MASTER_KEY_FILE"
chown ezkey:ezkey "$MASTER_KEY_FILE" 2>/dev/null || {
    # If ezkey user doesn't exist, use current user
    CURRENT_USER=$(whoami)
    chown "$CURRENT_USER:$CURRENT_USER" "$MASTER_KEY_FILE"
    echo "⚠️  Note: Using current user ($CURRENT_USER) for ownership (ezkey user not found)"
}

# Secure directories
chmod 700 "$SECRETS_DIR"
chmod 755 "$KEYSETS_DIR"
chown -R ezkey:ezkey /etc/ezkey 2>/dev/null || {
    chown -R "$CURRENT_USER:$CURRENT_USER" /etc/ezkey
}

echo "✅ Master key generated and saved to: $MASTER_KEY_FILE"
echo ""
echo "⚠️  IMPORTANT: Backup this file securely!"
echo ""
echo "Backup commands:"
echo "  1. Encrypted backup:"
echo "     tar czf - /etc/ezkey/secrets | gpg --encrypt --recipient admin@example.com > ezkey-master-key-backup.tar.gz.gpg"
echo ""
echo "  2. Password manager:"
echo "     cat $MASTER_KEY_FILE"
echo ""
echo "  3. Offline storage:"
echo "     Print this key and store in a physical safe"
echo ""

# Verify permissions
echo "🔒 Security verification:"
ls -la "$MASTER_KEY_FILE"
ls -la "$SECRETS_DIR"

echo ""
echo "✅ Setup complete! Application can now start automatically at boot."
echo ""
echo "Configuration: Add to application.properties:"
echo "  ezkey.encryption.master-key-file=$MASTER_KEY_FILE"
echo "  ezkey.encryption.keyset-file=$KEYSETS_DIR/keyset.json.encrypted"

