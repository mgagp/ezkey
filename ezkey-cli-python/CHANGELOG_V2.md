# Ezkey CLI Version 2.0 - Changelog

## Release Date: October 2025

## Overview

Major update to the Ezkey CLI tool adding support for passwordless admin authentication, API key management, and comprehensive documentation. This release aligns the CLI with the latest Ezkey system features documented in PRD.md, ENDPOINT.md, and related documentation.

## New Features

### 1. Admin Passwordless Authentication

Complete implementation of Ezkey's cryptographic authentication for admin users.

#### Commands Added:
- `ezkey admin auth login` - Passwordless login with two modes:
  - Single-call mode (default): Blocks until device approves/rejects
  - Two-call mode (--challenge): Returns challenge code for enhanced security
- `ezkey admin auth passwordless-wait` - Wait for device approval with challenge
- `ezkey admin auth recover` - Emergency recovery using recovery codes
- `ezkey admin auth logout` - Revoke current session token

#### Features:
- ✅ No passwords stored or transmitted
- ✅ Cryptographic device-bound authentication
- ✅ Challenge code support (6-digit codes)
- ✅ Recovery codes (32-digit format)
- ✅ Automatic token persistence
- ✅ Session management

#### Examples:
```bash
# Single-call login
ezkey admin auth login --username admin

# Two-call login with challenge
ezkey admin auth login --username admin --challenge
ezkey admin auth passwordless-wait --auth-attempt-id 123 --challenge-code 654321

# Emergency recovery
ezkey admin auth recover --username admin --recovery-code "XXXX-XXXX-..."
```

### 2. API Keys Management

Complete CRUD operations for machine-to-machine authentication.

#### Commands Added:
- `ezkey admin api-key create` - Create new API key with optional:
  - Expiration date
  - IP whitelist (CIDR ranges)
  - Auto-save to config
- `ezkey admin api-key list` - List all keys for an integration
- `ezkey admin api-key get` - Get key details (lastUsedAt, status, etc.)
- `ezkey admin api-key revoke` - Revoke key immediately

#### Features:
- ✅ Duo-style dual key system (integration key + secret key)
- ✅ HTTP Basic Authentication support
- ✅ Secret key shown only once (security best practice)
- ✅ Optional expiration dates
- ✅ IP whitelist support
- ✅ Usage tracking (lastUsedAt)
- ✅ Secure revocation

#### Examples:
```bash
# Create API key
ezkey admin api-key create \
  --integration-id 123 \
  --description "Production Server" \
  --expires-at "2025-12-31T23:59:59Z" \
  --ip-whitelist "192.168.1.0/24" \
  --save-key

# List keys
ezkey admin api-key list --integration-id 123

# Revoke key
ezkey admin api-key revoke --id 42
```

### 3. Admin Enrollment Management

#### Commands Added:
- `ezkey admin enrollment reset` - Reset enrollment after recovery
  - Unbinds lost device
  - Generates new credentials
  - Requires recovery token

#### Features:
- ✅ Emergency device recovery
- ✅ New credential generation
- ✅ Secure token validation

#### Examples:
```bash
# After recovery, reset enrollment
ezkey admin enrollment reset --id 1
```

### 4. Authentication Support

Enhanced HTTP client with authentication capabilities.

#### Features:
- ✅ Bearer token authentication (admin users)
- ✅ API key authentication (Basic Auth for M2M)
- ✅ Automatic authentication header setup
- ✅ Token persistence in config files
- ✅ Config-based authentication switching

#### Implementation:
- HTTP Client automatically detects and applies authentication
- Bearer token takes precedence over API key
- Tokens stored in `~/.ezkey/ezkey.json` or `./ezkey.json`

### 5. Documentation

Comprehensive documentation for all features.

#### Documents Added:
- **USAGE_GUIDE.md** - Complete usage guide with:
  - Quick start instructions
  - Authentication methods
  - Command categories
  - Common workflows (5 complete examples)
  - Troubleshooting guide
  - Best practices
  - Examples with JSON data

- **README.md** - Updated with:
  - New features section
  - Authentication methods
  - Command reference
  - Examples for each command
  - Troubleshooting section
  - Best practices
  - UTC datetime format documentation
  - How to update the CLI

#### Help Text Improvements:
- ✅ All commands have comprehensive help
- ✅ Context-aware descriptions
- ✅ Usage examples
- ✅ Clear option descriptions
- ✅ Related command suggestions

## Enhanced Commands

### Help Text Improvements

All existing commands now have enhanced help text:

- **Integration commands**: Added context about what integrations are
- **Enrollment commands**: Added context about device binding
- **Auth attempt commands**: Added status descriptions and workflow info

## Technical Improvements

### Config Manager
- Added `set_bearer_token()` and `clear_bearer_token()` methods
- Added `set_api_key()` and `clear_api_key()` methods
- Token and key persistence in config files

### HTTP Client
- Added `_setup_auth()` method for automatic authentication setup
- Bearer token authentication support
- API key (Basic Auth) authentication support
- Automatic header configuration

### Output Utils
- Added `output_json()` helper method for consistent JSON output

## Breaking Changes

None. This is a backward-compatible release.

## Deprecations

None.

## Migration Guide

No migration needed. New features are additive.

### To Start Using New Features:

1. **Update CLI**:
   ```bash
   cd ezkey-cli-python
   git pull
   pip install -e . --upgrade
   ```

2. **Login with Passwordless**:
   ```bash
   ezkey admin auth login --username admin
   ```

3. **Create API Key**:
   ```bash
   ezkey admin api-key create --integration-id <id> --description "..." --save-key
   ```

## UTC Datetime Format

All datetime fields now documented as UTC with Z suffix:
- Format: `yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'`
- Example: `"2025-10-16T11:55:55.925512Z"`
- Convert to local timezone in your application if needed

## Security Improvements

- ✅ Passwordless authentication eliminates password vulnerabilities
- ✅ API keys support expiration and IP whitelisting
- ✅ Recovery codes provide emergency access
- ✅ Tokens stored securely in config files
- ✅ Secret keys shown only once

## Best Practices Documented

### Authentication
- Use passwordless login for interactive sessions
- Use API keys for automated scripts
- Store tokens in home directory for global access

### API Key Management
- Set expiration dates for automatic rotation
- Use IP whitelist in production
- Create new key before revoking old (zero-downtime rotation)

### Security
- Never commit tokens or API keys to version control
- Use environment variables for sensitive data
- Save recovery codes securely
- Rotate API keys regularly

## Testing

- ✅ CLI compiles without errors
- ✅ All commands accessible
- ✅ Help text displays correctly
- ✅ Config manager tested
- ✅ HTTP client tested
- ✅ Authentication flows verified

## Known Issues

None.

## Future Enhancements

Potential future features:
- Batch operations for bulk management
- Interactive prompts for complex JSON inputs
- Output format options (JSON, YAML, table)
- Shell completion scripts
- Configuration profiles for multiple environments

## Contributors

- GitHub Copilot
- Ezkey Team

## References

- PRD.md - Product requirements
- ENDPOINT.md - API endpoint documentation
- ADMIN_PASSWORDLESS_LOGIN.md - Passwordless authentication
- DATETIME_TIMEZONE_DECISION.md - UTC datetime format
- API_KEYS_GUIDE.md - API key management

## Links

- Usage Guide: [USAGE_GUIDE.md](USAGE_GUIDE.md)
- README: [README.md](README.md)
- License: [MIT License](../LICENSE)

---

**Version**: 2.0  
**Release Date**: October 2025  
**Status**: Production Ready
