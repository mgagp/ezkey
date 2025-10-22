# Ezkey CLI (Python)

Command line interface for Ezkey - Open Source MFA/Passkey Alternative

## Overview

The Ezkey CLI provides a unified command-line interface for interacting with all Ezkey APIs and managing the Ezkey system. It follows AWS CLI patterns with hierarchical commands and supports configuration management, JSON file input, and comprehensive error handling.

This is the Python implementation of the ezkey CLI, providing the same functionality as the TypeScript version but with improved performance.

## Installation

### From Source (Development)

```bash
cd ezkey-cli-python
pip install -e .
```

### Using the CLI

```bash
# After installation, the CLI is available as 'ezkey'
ezkey --help
```

## Quick Start

### 1. Configure the CLI

```bash
# Interactive configuration
ezkey configure interactive

# Or set individual values
ezkey configure set --admin-url http://localhost:9080 --auth-url http://localhost:8080
```

### 2. Test API connectivity

```bash
# List integrations
ezkey admin integration list

# Generate a test keypair
ezkey sim keypair --key-size 2048
```

## Command Structure

The CLI follows the pattern: `ezkey <api> <object> <action> [options]`

### APIs
- **admin** - Admin API commands (integrations, enrollments, auth attempts, authentication, API keys)
- **auth** - Auth API commands (enrollment binding/verification, auth responses)
- **sim** - Simulation API commands (cryptographic operations for testing)

### Utility Commands
- **configure** - Configuration management
- **database** - Database migration commands
- **openapi** - OpenAPI specification management

## New Features (Version 2.0)

### Admin Authentication (Passwordless)

Ezkey CLI now supports passwordless admin authentication using Ezkey's own cryptographic authentication system.

#### Single-Call Mode (Recommended)
```bash
# Login and wait for device approval in one command
ezkey admin auth login --username admin

# Token is automatically saved to config for future use
```

#### Two-Call Mode (With Challenge Code)
```bash
# Step 1: Request authentication with challenge
ezkey admin auth login --username admin --challenge

# Step 2: Enter challenge code on device, then wait
ezkey admin auth passwordless-wait --auth-attempt-id 123 --challenge-code 654321
```

#### Recovery Access (Emergency)
```bash
# Use recovery code when device is lost
ezkey admin auth recover --username admin --recovery-code "XXXX-XXXX-XXXX-XXXX-XXXX-XXXX-XXXX-XXXX"

# Reset enrollment to unbind lost device
ezkey admin enrollment reset --id 1

# Bind new device with new credentials
```

#### Logout
```bash
# Revoke current session token
ezkey admin auth logout
```

### API Keys (Machine-to-Machine)

API keys provide authentication for server-to-server integrations without requiring interactive login.

#### Create API Key
```bash
# Create an API key for an integration
ezkey admin api-key create \
  --integration-id 123 \
  --description "Production Server" \
  --expires-at "2025-12-31T23:59:59Z" \
  --ip-whitelist "192.168.1.0/24" \
  --save-key

# ⚠️ IMPORTANT: Save the secret key immediately - it's shown only once!
```

#### List API Keys
```bash
# List all API keys for an integration
ezkey admin api-key list --integration-id 123
```

#### Get API Key Details
```bash
# Get details of a specific API key
ezkey admin api-key get --id 42
```

#### Revoke API Key
```bash
# Revoke an API key (for security incidents or key rotation)
ezkey admin api-key revoke --id 42
```

### Authentication Methods

The CLI supports three authentication methods:

1. **Bearer Token** (for admin users)
   - Obtained via `ezkey admin auth login`
   - Stored in `~/.ezkey/ezkey.json` automatically
   - Valid for 24 hours (configurable)

2. **API Key** (for applications)
   - Obtained via `ezkey admin api-key create`
   - Uses HTTP Basic Auth (integration key:secret key)
   - Can be saved to config with `--save-key`

3. **No Authentication** (for public endpoints)
   - Some endpoints don't require authentication
   - Used for initial setup and testing

## Configuration Management

The CLI uses hierarchical configuration with the following precedence:

1. **Command line parameters** (highest priority)
2. **Current directory** (`./ezkey.json`)
3. **Home directory** (`~/.ezkey/ezkey.json`)
4. **Default values** (lowest priority)

### Configuration File Format

```json
{
  "adminUrl": "http://localhost:9080",
  "authUrl": "http://localhost:8080",
  "simUrl": "http://localhost:8080",
  "javaPath": "java",
  "ezkeyCorePath": "/path/to/ezkey-core.jar",
  "prettyPrint": true,
  "timeout": 30000
}
```

## JSON Input Support

The CLI supports JSON input in two ways:

### 1. File Input (recommended)
```bash
# Use @filename to load JSON from file
ezkey admin integration create --data @integration.json
ezkey sim sign --data "text" --private-key @private.pem
```

### 2. Inline JSON
```bash
# Pass JSON directly
ezkey admin integration create --data '{"logo":"logo.png","i18n":[{"language":"en","name":"Test"}]}'
```

## Global Options

All commands support these global options:

- `--admin-url <url>` - Override admin API URL
- `--auth-url <url>` - Override auth API URL  
- `--sim-url <url>` - Override sim API URL
- `--no-pretty` - Disable pretty printing of JSON output
- `--timeout <ms>` - Set request timeout in milliseconds
- `--verbose` - Enable verbose output

## Error Handling

The CLI provides detailed error messages and appropriate exit codes:

- **0** - Success
- **1** - General error (network, API, validation)

Error responses include:
- HTTP status codes
- Error messages from APIs
- Request/response context when available

## Datetime Format

All Ezkey APIs return datetime fields in **UTC with Z suffix** for consistency and timezone independence:

```json
{
  "createdAt": "2025-10-16T11:55:55.925512Z",
  "expiresAt": "2025-10-16T15:56:19.433745Z"
}
```

**Format:** `yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'`

**Key Points:**
- **Z suffix**: Indicates UTC timezone (Zulu time)
- **Microsecond precision**: Six decimal places for timestamps
- **Timezone independence**: Works globally without regional assumptions
- **Client responsibility**: Convert to local timezone for display if needed

**Example: Convert to Local Time (Python)**
```python
from datetime import datetime

# Parse UTC datetime from API
created_at = datetime.fromisoformat("2025-10-16T11:55:55.925512Z")

# Convert to local timezone
local_time = created_at.astimezone()
print(local_time)  # Displays in your local timezone
```

## Updating the CLI

### From Source (Development)

```bash
cd ezkey-cli-python
git pull origin main
pip install -e . --upgrade
```

### Verify Version

```bash
ezkey --version
```

### Configuration After Update

After updating, check your configuration is still valid:

```bash
# View current configuration
ezkey configure show

# Update if needed
ezkey configure set --admin-url http://localhost:9080 --auth-url http://localhost:8080
```

## Command Reference

### Admin Commands

#### Integrations
- `ezkey admin integration list` - List all integrations
- `ezkey admin integration get --id <id>` - Get integration details
- `ezkey admin integration create --data @file.json` - Create integration
- `ezkey admin integration delete --id <id>` - Delete integration

#### Enrollments
- `ezkey admin enrollment list` - List all enrollments
- `ezkey admin enrollment get --id <id>` - Get enrollment details
- `ezkey admin enrollment create --integration-id <id> --data @file.json` - Create enrollment
- `ezkey admin enrollment reset --id <id>` - Reset enrollment (after recovery)

#### Auth Attempts
- `ezkey admin auth-attempt list` - List auth attempts
- `ezkey admin auth-attempt get --id <id>` - Get auth attempt details
- `ezkey admin auth-attempt create --enrollment-id <id>` - Create auth attempt
- `ezkey admin auth-attempt wait --id <id> --timeout 30` - Wait for completion

#### Authentication
- `ezkey admin auth login --username <user>` - Passwordless login
- `ezkey admin auth login --username <user> --challenge` - Login with challenge code
- `ezkey admin auth passwordless-wait --auth-attempt-id <id> --challenge-code <code>` - Wait for challenge approval
- `ezkey admin auth recover --username <user> --recovery-code <code>` - Emergency recovery
- `ezkey admin auth logout` - Logout and revoke token

#### API Keys
- `ezkey admin api-key create --integration-id <id> --description "..."` - Create API key
- `ezkey admin api-key list --integration-id <id>` - List API keys
- `ezkey admin api-key get --id <id>` - Get API key details
- `ezkey admin api-key revoke --id <id>` - Revoke API key

### Auth Commands

- `ezkey auth enrollment bind --id <id>` - Bind enrollment to device
- `ezkey auth enrollment verify --id <id>` - Verify enrollment
- `ezkey auth pending` - Check for pending auth attempts
- `ezkey auth respond --id <id>` - Respond to auth attempt

### Configuration Commands

- `ezkey configure interactive` - Interactive configuration
- `ezkey configure show` - Show current configuration
- `ezkey configure set --admin-url <url> --auth-url <url>` - Set configuration values

### Database Commands

- `ezkey database migrate` - Run database migrations
- `ezkey database info` - Show migration info
- `ezkey db migrate` - Alias for database migrate

### OpenAPI Commands

- `ezkey openapi refresh --all` - Refresh OpenAPI specs
- `ezkey openapi generate` - Generate client code

## Troubleshooting

### Authentication Issues

**Problem:** "Admin URL not configured"
```bash
# Solution: Configure the admin URL
ezkey configure set --admin-url http://localhost:9080
```

**Problem:** "No bearer token found"
```bash
# Solution: Login first
ezkey admin auth login --username admin
```

**Problem:** "Authentication failed: No device enrolled"
```bash
# Solution: Enroll a device first or use recovery code
ezkey admin auth recover --username admin --recovery-code "..."
```

### API Key Issues

**Problem:** "Invalid API key"
```bash
# Solution: Check that the key is active and not expired
ezkey admin api-key get --id <key-id>

# If expired, create a new key
ezkey admin api-key create --integration-id <id> --description "New Key"
```

**Problem:** "Lost secret key"
```bash
# Solution: Secret keys cannot be recovered - create a new key
ezkey admin api-key create --integration-id <id> --description "Replacement Key"

# After deploying the new key, revoke the old one
ezkey admin api-key revoke --id <old-key-id>
```

### Network Issues

**Problem:** "Connection timeout"
```bash
# Solution: Increase timeout
ezkey --timeout 60000 admin integration list

# Or set globally
ezkey configure set --timeout 60000
```

## Best Practices

### Security
1. **Never commit tokens or API keys** to version control
2. **Use environment variables** for sensitive data in scripts
3. **Rotate API keys regularly** using key expiration
4. **Use recovery tokens only in emergencies** - they expire in 30 minutes
5. **Save recovery codes securely** when first generated

### Authentication
1. **Use passwordless login** for interactive sessions
2. **Use API keys** for automated scripts and server integrations
3. **Store tokens in home directory** (`~/.ezkey/`) for global access
4. **Store tokens in project directory** (`./ezkey.json`) for project-specific configs

### Key Rotation
1. Create new API key with expiration date
2. Update application configuration with new key
3. Test application with new key
4. Monitor usage (check `lastUsedAt` field)
5. Revoke old key after migration complete

## Examples

### Complete Workflow: Admin Login and Create Integration

```bash
# 1. Configure CLI
ezkey configure set --admin-url http://localhost:9080 --auth-url http://localhost:8080

# 2. Login as admin
ezkey admin auth login --username admin

# 3. Create integration
ezkey admin integration create --data '{
  "logo": "https://example.com/logo.png",
  "i18n": [
    {
      "language": "en",
      "name": "My App",
      "description": "My application"
    }
  ]
}'

# 4. List integrations
ezkey admin integration list
```

### Complete Workflow: Create API Key for Production Server

```bash
# 1. Login as admin
ezkey admin auth login --username admin

# 2. Create API key
ezkey admin api-key create \
  --integration-id 123 \
  --description "Production Server" \
  --expires-at "2026-12-31T23:59:59Z" \
  --ip-whitelist "10.0.0.0/8" \
  --save-key

# 3. Test API key (saved to config automatically)
ezkey admin integration get --id 123

# 4. List all keys for the integration
ezkey admin api-key list --integration-id 123
```

### Complete Workflow: Emergency Recovery

```bash
# 1. Use recovery code to login
ezkey admin auth recover \
  --username admin \
  --recovery-code "1234-5678-9012-3456-7890-1234-5678-9012"

# 2. Reset enrollment to unbind lost device
ezkey admin enrollment reset --id 1

# 3. Note: Use the new credentials to bind a new device
# The output will show: enrollmentProofToken, enrollmentChallenge
```

## License

MIT License - see LICENSE file in the project root.