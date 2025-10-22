# Ezkey CLI Usage Guide

## Table of Contents
1. [Installation](#installation)
2. [Quick Start](#quick-start)
3. [Authentication](#authentication)
4. [Command Categories](#command-categories)
5. [Common Workflows](#common-workflows)
6. [Troubleshooting](#troubleshooting)

## Installation

### From Source (Development)
```bash
cd ezkey-cli-python
pip install -e .
```

### Verify Installation
```bash
ezkey --version
ezkey --help
```

## Quick Start

### 1. Configure the CLI
```bash
# Interactive configuration (recommended for first-time setup)
ezkey configure interactive

# Or set values directly
ezkey configure set --admin-url http://localhost:9080 --auth-url http://localhost:8080
```

### 2. View Current Configuration
```bash
# Show all configuration
ezkey configure get

# Get specific value
ezkey configure get --key adminUrl
```

### 3. Authenticate as Admin
```bash
# Passwordless login (single-call mode)
ezkey admin auth login --username admin

# Token is automatically saved for future commands
```

## Authentication

Ezkey CLI supports three authentication methods:

### 1. Bearer Token (Admin Users)

**Login:**
```bash
# Single-call mode (recommended)
ezkey admin auth login --username admin

# Two-call mode with challenge
ezkey admin auth login --username admin --challenge
# Then wait: ezkey admin auth passwordless-wait --auth-attempt-id <id> --challenge-code <code>
```

**Logout:**
```bash
ezkey admin auth logout
```

**Emergency Recovery:**
```bash
# When device is lost
ezkey admin auth recover --username admin --recovery-code "XXXX-XXXX-..."

# Reset enrollment
ezkey admin enrollment reset --id 1
```

### 2. API Key (Machine-to-Machine)

**Create API Key:**
```bash
ezkey admin api-key create \
  --integration-id 123 \
  --description "Production Server" \
  --expires-at "2025-12-31T23:59:59Z" \
  --save-key
```

**Use API Key:**
```bash
# Automatically used when saved to config
# Or configure manually:
# Edit ~/.ezkey/ezkey.json and add:
# {
#   "integrationKey": "ezkey_ikey_...",
#   "secretKey": "ezkey_skey_..."
# }
```

### 3. No Authentication

Some commands don't require authentication (public endpoints).

## Command Categories

### Admin Commands

#### Integrations
```bash
# List all integrations
ezkey admin integration list

# Get integration details
ezkey admin integration get --id 1

# Create integration
ezkey admin integration create --data @integration.json

# Delete integration
ezkey admin integration delete --id 1
```

#### Enrollments
```bash
# List all enrollments
ezkey admin enrollment list

# Filter by integration
ezkey admin enrollment list --integration-id 1

# Get enrollment details
ezkey admin enrollment get --id 1

# Create enrollment
ezkey admin enrollment create --integration-id 1 --data '{"name":"My Device"}'

# Reset enrollment (after recovery)
ezkey admin enrollment reset --id 1
```

#### Auth Attempts
```bash
# List auth attempts
ezkey admin auth-attempt list

# Filter by enrollment
ezkey admin auth-attempt list --enrollment-id 1

# Get auth attempt details
ezkey admin auth-attempt get --id 1

# Create auth attempt
ezkey admin auth-attempt create --enrollment-id 1

# Create with challenge
ezkey admin auth-attempt create --enrollment-id 1 --challenge-requested

# Wait for completion
ezkey admin auth-attempt wait --id 1 --timeout 30
```

#### Authentication
```bash
# Login (passwordless)
ezkey admin auth login --username admin

# Login with challenge (two-step)
ezkey admin auth login --username admin --challenge

# Wait for challenge approval
ezkey admin auth passwordless-wait --auth-attempt-id 123 --challenge-code 654321

# Emergency recovery
ezkey admin auth recover --username admin --recovery-code "XXXX-XXXX-..."

# Logout
ezkey admin auth logout
```

#### API Keys
```bash
# Create API key
ezkey admin api-key create \
  --integration-id 123 \
  --description "Production Server" \
  --expires-at "2025-12-31T23:59:59Z" \
  --ip-whitelist "192.168.1.0/24"

# List API keys for integration
ezkey admin api-key list --integration-id 123

# Get API key details
ezkey admin api-key get --id 42

# Revoke API key
ezkey admin api-key revoke --id 42
```

### Auth Commands (Device/Mobile)

```bash
# Bind enrollment to device
ezkey auth enrollment bind --id 456

# Verify enrollment
ezkey auth enrollment verify --id 456

# Check for pending auth attempts
ezkey auth pending

# Respond to auth attempt
ezkey auth respond --id 123
```

### Configuration Commands

```bash
# Interactive setup
ezkey configure interactive

# Set configuration values
ezkey configure set --admin-url http://localhost:9080 --auth-url http://localhost:8080

# Get all configuration
ezkey configure get

# Get specific value
ezkey configure get --key adminUrl

# Reset to defaults
ezkey configure reset
```

### Database Commands

```bash
# Run migrations
ezkey database migrate

# Show migration info
ezkey database info

# Alias
ezkey db migrate
```

### OpenAPI Commands

```bash
# Refresh OpenAPI specs
ezkey openapi refresh --all

# Generate client code
ezkey openapi generate
```

## Common Workflows

### Workflow 1: Admin Login and Create Integration

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
      "name": "My Application",
      "description": "Secure authentication for my app"
    }
  ]
}'

# 4. List integrations to verify
ezkey admin integration list
```

### Workflow 2: Create API Key for Production Server

```bash
# 1. Login as admin
ezkey admin auth login --username admin

# 2. Create API key with expiration and IP whitelist
ezkey admin api-key create \
  --integration-id 123 \
  --description "Production Server" \
  --expires-at "2026-12-31T23:59:59Z" \
  --ip-whitelist "10.0.0.0/8" \
  --save-key

# IMPORTANT: Save the secret key displayed - it's shown only once!

# 3. Test API key (automatically used from config)
ezkey admin integration get --id 123

# 4. List all keys for monitoring
ezkey admin api-key list --integration-id 123
```

### Workflow 3: Emergency Device Recovery

```bash
# 1. Use recovery code to login (when device is lost)
ezkey admin auth recover \
  --username admin \
  --recovery-code "1234-5678-9012-3456-7890-1234-5678-9012"

# 2. Reset enrollment to unbind lost device
ezkey admin enrollment reset --id 1

# Output will show new credentials:
# - enrollmentProofToken
# - enrollmentChallenge
# - integrationId

# 3. Use these new credentials to bind a new device
# (From mobile app or device simulator)
```

### Workflow 4: Create MFA Request and Wait for Response

```bash
# 1. Login as admin
ezkey admin auth login --username admin

# 2. Create auth attempt
ezkey admin auth-attempt create --enrollment-id 456

# Output: { "authAttemptId": 789 }

# 3. Wait for device response
ezkey admin auth-attempt wait --id 789 --timeout 60

# Device approves/rejects
# Output: status will be "APPROVED", "REJECTED", or timeout
```

### Workflow 5: API Key Rotation

```bash
# 1. Login as admin
ezkey admin auth login --username admin

# 2. Create new API key
ezkey admin api-key create \
  --integration-id 123 \
  --description "Production Server - New Key" \
  --expires-at "2026-12-31T23:59:59Z"

# 3. Update application configuration with new key
# (Deploy changes to production)

# 4. Monitor usage of new key
ezkey admin api-key get --id <new-key-id>
# Check lastUsedAt field

# 5. After migration is complete, revoke old key
ezkey admin api-key revoke --id <old-key-id>
```

## Troubleshooting

### Authentication Issues

**Problem: "Admin URL not configured"**
```bash
# Solution: Configure the admin URL
ezkey configure set --admin-url http://localhost:9080
```

**Problem: "No bearer token found"**
```bash
# Solution: Login first
ezkey admin auth login --username admin
```

**Problem: "Authentication failed: No device enrolled"**
```bash
# Solution: Enroll a device first or use recovery code
ezkey admin auth recover --username admin --recovery-code "..."
```

### API Key Issues

**Problem: "Invalid API key"**
```bash
# Solution: Check key status
ezkey admin api-key get --id <key-id>

# If expired, create new key
ezkey admin api-key create --integration-id <id> --description "New Key"
```

**Problem: "Lost secret key"**
```bash
# Solution: Secret keys cannot be recovered - create new key
ezkey admin api-key create --integration-id <id> --description "Replacement Key"

# After deploying new key, revoke old one
ezkey admin api-key revoke --id <old-key-id>
```

### Network Issues

**Problem: "Connection timeout"**
```bash
# Solution: Increase timeout
ezkey --timeout 60000 admin integration list

# Or set globally
ezkey configure set --timeout 60000
```

**Problem: "Connection refused"**
```bash
# Check if services are running
# Verify URLs in configuration
ezkey configure get --key adminUrl
ezkey configure get --key authUrl
```

### Configuration Issues

**Problem: Configuration not persisting**
```bash
# Save to global config (home directory)
ezkey configure set --admin-url http://localhost:9080 --global

# Verify it was saved
cat ~/.ezkey/ezkey.json
```

**Problem: Multiple configurations conflicting**
```bash
# Configuration precedence:
# 1. Command line options (highest)
# 2. Current directory (./ezkey.json)
# 3. Home directory (~/.ezkey/ezkey.json)
# 4. Default values (lowest)

# Check what's in each location
cat ./ezkey.json
cat ~/.ezkey/ezkey.json

# Reset to clean state
ezkey configure reset --global
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

### API Key Management
1. Create new API key with expiration date
2. Update application configuration with new key
3. Test application with new key
4. Monitor usage (check `lastUsedAt` field)
5. Revoke old key after migration complete

### Datetime Handling
All datetime fields are in UTC with Z suffix:
- Format: `yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'`
- Example: `"2025-10-16T11:55:55.925512Z"`
- Convert to local timezone in your application if needed

## Examples with JSON Data

### Create Integration with JSON File

**integration.json:**
```json
{
  "logo": "https://example.com/logo.png",
  "i18n": [
    {
      "language": "en",
      "name": "My Application",
      "description": "Secure authentication for my application"
    },
    {
      "language": "fr",
      "name": "Mon Application",
      "description": "Authentification sécurisée pour mon application"
    }
  ]
}
```

**Command:**
```bash
ezkey admin integration create --data @integration.json
```

### Create Enrollment with JSON Data

**enrollment.json:**
```json
{
  "name": "John's iPhone",
  "authAttemptChallengeRequired": true
}
```

**Command:**
```bash
ezkey admin enrollment create --integration-id 123 --data @enrollment.json
```

## Global Options

All commands support these global options:

- `--admin-url <url>` - Override admin API URL
- `--auth-url <url>` - Override auth API URL
- `--sim-url <url>` - Override sim API URL
- `--no-pretty` - Disable pretty printing of JSON output
- `--timeout <ms>` - Set request timeout in milliseconds
- `--verbose` - Enable verbose output

**Example:**
```bash
ezkey --verbose --timeout 60000 admin integration list
```

## Getting Help

### General Help
```bash
ezkey --help
```

### Command Group Help
```bash
ezkey admin --help
ezkey admin auth --help
ezkey admin api-key --help
```

### Specific Command Help
```bash
ezkey admin auth login --help
ezkey admin api-key create --help
ezkey admin integration create --help
```

## Version Information

```bash
ezkey --version
```

## License

MIT License - see LICENSE file in the project root.
