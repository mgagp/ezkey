# Ezkey CLI (Python)

**Command line interface for Ezkey - Open Source Cryptographic MFA Platform**

## Overview

The Ezkey CLI provides a unified interface for interacting with all Ezkey APIs. It follows AWS CLI patterns with hierarchical commands, supports both traditional CLI mode and an interactive TUI (Text User Interface) for **read-only investigation and audit**.

**Key Features:**
- Passwordless admin authentication
- API key management for machine-to-machine integrations
- **Device simulation** (`ezkey device`) — bind/verify plus pending/respond from the CLI for
  development and testing. Device private keys are stored in plaintext under `~/.ezkey/devices/`;
  this is not a production device. Prefer Demo Device or the mobile app for realistic UX.
- **TUI** (`ezkey --tui`) — read-only investigation/audit fallback (e.g. over SSH when the web Admin UI is unavailable)
- Configuration management
- JSON file input support
- Comprehensive error handling

**Primary admin interface:** Use the **Admin UI** (web) for day-to-day operations. It is the main human administration surface for both **Global Admin** and **Tenant Admin** workflows. The TUI is a narrow, read-only tool for audit logs and entity lookup. See [TUI_SCOPE.md](TUI_SCOPE.md).

The CLI is **not** started by `./ezkey-tests/clean-start.sh`. After the Docker stack is up, install it on the host and point at localhost APIs (`http://localhost:9080` Admin, `http://localhost:8080` Auth, `http://localhost:9090` Crypto).

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

### CLI Mode

```bash
# 1. Configure the CLI
ezkey configure set --admin-url http://localhost:9080 --auth-url http://localhost:8080

# 2. Authenticate as admin
ezkey admin auth login --username admin

# 3. List integrations
ezkey admin integration list

# Device simulation (dev/test — plaintext keys under ~/.ezkey/devices/)
ezkey device enroll --enrollment-id 456 --enrollment-proof-token EZK-ABC123 --challenge 123456
ezkey device auth --enrollment-id 456 --approve
```

### TUI Mode (Read-only investigation and audit)

```bash
# Launch read-only TUI (e.g. over SSH when web UI is unavailable)
ezkey --tui
```

**First run:** Interactive setup wizard (Admin URL, username, organization, passwordless auth). **Subsequent runs:** Session loads automatically. The TUI exposes **Audit logs**, **Auth attempts**, **Enrollments**, **Integrations**, and **Tenants** in read-only form (list, detail, filter). No create/update/delete. See [TUI_SCOPE.md](TUI_SCOPE.md).

## Command Structure

The CLI follows the pattern: `ezkey <api> <object> <action> [options]`

### APIs
- **admin** - Admin API commands (integrations, enrollments, auth attempts, authentication, API keys)
- **auth** - Auth API commands (enrollment binding/verification, auth responses)
- **crypto** - Crypto API commands (cryptographic operations for testing)
- **device** - Device simulation (enroll, auth, list, show, remove). Development/testing only;
  private keys are stored in plaintext. Not a production authenticator.

### Utility Commands
- **configure** - Configuration management
- **database** - Database migration commands
- **openapi** - OpenAPI specification management

## Authentication Methods

The CLI supports three authentication methods:

### 1. Bearer Token (Admin Users)

Passwordless authentication using Ezkey's own cryptographic authentication system.

```bash
# Single-call mode (recommended)
ezkey admin auth login --username admin

# Two-call mode with challenge code
ezkey admin auth login --username admin --challenge
ezkey admin auth passwordless-wait --auth-attempt-id 123 --challenge-code 654321

# Emergency recovery
ezkey admin auth recover --username admin --recovery-code "XXXX-XXXX-..."

# Logout
ezkey admin auth logout
```

### 2. API Keys (Machine-to-Machine)

API keys provide authentication for server-to-server integrations without interactive login.

```bash
# Create API key
ezkey admin api-key create \
  --integration-id 123 \
  --description "Production Server" \
  --expires-at "2025-12-31T23:59:59Z" \
  --save-key

# ⚠️ IMPORTANT: Save the secret key immediately - it's shown only once!

# List API keys
ezkey admin api-key list --integration-id 123

# Revoke API key
ezkey admin api-key revoke --id 42
```

### 3. No Authentication

Some commands don't require authentication (public endpoints).

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
  "cryptoUrl": "http://localhost:9090",
  "bearerToken": "eyJhbG...",
  "integrationKey": "ezkey_ikey_...",
  "secretKey": "ezkey_skey_...",
  "prettyPrint": true,
  "timeout": 30000
}
```

## Global Options

All commands support these global options:

- `--admin-url <url>` - Override admin API URL
- `--auth-url <url>` - Override auth API URL
- `--crypto-url <url>` - Override crypto API URL
- `--no-pretty` - Disable pretty printing of JSON output
- `--timeout <ms>` - Set request timeout in milliseconds
- `--verbose` - Enable verbose output
- `--tui` - Start interactive admin console

## JSON Input Support

The CLI supports JSON input in two ways:

### 1. File Input (recommended)
```bash
# Use @filename to load JSON from file
ezkey admin integration create --data @integration.json
ezkey crypto sign --data "text" --private-key @private.pem
```

### 2. Inline JSON
```bash
# Pass JSON directly
ezkey admin integration create --data '{"name":"Test Integration","description":"Demo app"}'
```

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

## Command Reference

For detailed command reference, see [USAGE_GUIDE.md](USAGE_GUIDE.md)

### Quick Reference

**Admin Commands:**
- `ezkey admin integration list|get|create|delete`
- `ezkey admin enrollment list|get|create|reset`
- `ezkey admin auth-attempt list|get|create|wait`
- `ezkey admin audit-log list`
- `ezkey admin auth login|logout|recover|passwordless-wait`
- `ezkey admin api-key create|list|get|revoke`

**Auth Commands:**
- `ezkey auth enrollment bind|verify`
- `ezkey auth auth-attempt pending|respond`

**Crypto Commands:**
- `ezkey crypto prooftoken|keypair|sign|validate`

**Configuration:**
- `ezkey configure interactive|show|set|get|reset`

**Database:**
- `ezkey database migrate|info`

## Error Handling

The CLI provides detailed error messages and appropriate exit codes:

- **0** - Success
- **1** - General error (network, API, validation)

Error responses include:
- HTTP status codes
- Error messages from APIs
- Request/response context when available

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

## Architecture Notes

### RFC 7807 Problem Details Support

The CLI TUI now supports **RFC 7807 Problem Details** for structured API error responses. This enables:
- **Readable error messages** from the Admin API (e.g., "Cannot deactivate your own account")
- **Backward compatibility** with legacy error formats
- **Automatic extensibility** - new endpoints using RFC 7807 work without code changes

See [TUI_GUIDE.md - API Client & Error Handling](TUI_GUIDE.md#api-client--error-handling) for details.

---

## Documentation

- **[USAGE_GUIDE.md](USAGE_GUIDE.md)** - Complete CLI usage guide with workflows and examples
- **[TUI_SCOPE.md](TUI_SCOPE.md)** - TUI scope and positioning (read-only investigation/audit fallback)
- **[TUI_GUIDE.md](TUI_GUIDE.md)** - TUI architecture and development (for contributors)
- **[TESTING_GUIDE.md](TESTING_GUIDE.md)** - Testing strategy and implementation

## Updating the CLI

```bash
cd ezkey-cli-python
git pull origin main
pip install -e . --upgrade

# Verify version
ezkey --version

# Check configuration
ezkey configure get
```

## Troubleshooting

### Authentication Issues

**"Admin URL not configured"**
```bash
ezkey configure set --admin-url http://localhost:9080
```

**"No bearer token found"**
```bash
ezkey admin auth login --username admin
```

**"Authentication failed: No device enrolled"**
```bash
ezkey admin auth recover --username admin --recovery-code "..."
```

### Network Issues

**Connection timeout**
```bash
# Increase timeout
ezkey --timeout 60000 admin integration list

# Or set globally
ezkey configure set --timeout 60000
```

## License

MIT License - see LICENSE file in the project root.
