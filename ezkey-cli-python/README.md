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
- **admin** - Admin API commands (integrations, enrollments, auth attempts)
- **auth** - Auth API commands (enrollment binding/verification, auth responses)
- **sim** - Simulation API commands (cryptographic operations for testing)

### Utility Commands
- **configure** - Configuration management
- **database** - Database migration commands
- **openapi** - OpenAPI specification management

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

## License

MIT License - see LICENSE file in the project root.