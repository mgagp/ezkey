# Ezkey CLI

Command line interface for Ezkey - Open Source MFA/Passkey Alternative

## Overview

The Ezkey CLI provides a unified command-line interface for interacting with all Ezkey APIs and managing the Ezkey system. It follows AWS CLI patterns with hierarchical commands and supports configuration management, JSON file input, and comprehensive error handling.

## Installation

### From Source (Development)

```bash
cd ezkey-cli
npm install
npm run build
```

### Using the CLI

```bash
# Development mode (with ts-node)
./bin/ezkey --help

# Production mode (compiled)
npm run build
./bin/ezkey --help
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

## Command Reference

### Admin API Commands

#### Integrations
```bash
# List all integrations
ezkey admin integration list

# Get specific integration
ezkey admin integration get --id 1

# Create integration with JSON file
ezkey admin integration create --data @integration.json

# Delete integration
ezkey admin integration delete --id 1
```

#### Enrollments
```bash
# List all enrollments
ezkey admin enrollment list

# Create enrollment
ezkey admin enrollment create --integration-id 1 --name "John's Phone" --challenge-required

# Get enrollment details
ezkey admin enrollment get --id 123

# Delete enrollment
ezkey admin enrollment delete --id 123
```

#### Auth Attempts
```bash
# List all auth attempts
ezkey admin auth-attempt list

# Create auth attempt
ezkey admin auth-attempt create --enrollment-id 123

# Wait for completion (synchronous)
ezkey admin auth-attempt wait --id 456 --timeout 60 --polling 3

# Get auth attempt status
ezkey admin auth-attempt get --id 456
```

### Auth API Commands

#### Enrollment Operations
```bash
# Bind device to enrollment
ezkey auth enrollment bind --id 123 --language en

# Verify enrollment
ezkey auth enrollment verify \
  --enrollment-id 123 \
  --challenge-response 654321 \
  --public-key "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA..." \
  --token-signed "eyJhbGciOiJSUzI1NiJ9..."
```

#### Authentication Operations
```bash
# Check for pending auth requests
ezkey auth auth-attempt pending \
  --enrollment-id 123 \
  --device-token "token" \
  --device-token-signed "signature"

# Respond to auth attempt
ezkey auth auth-attempt respond \
  --id 456 \
  --accepted true \
  --token-signed "signature" \
  --challenge-response 123456
```

### Simulation API Commands

```bash
# Generate proof token
ezkey sim prooftoken

# Generate RSA key pair
ezkey sim keypair --key-size 2048

# Sign data
ezkey sim sign --data "Hello, World!" --private-key @private.pem

# Validate signature
ezkey sim validate \
  --data "Hello, World!" \
  --signature @signature.txt \
  --public-key @public.pem
```

### Database Commands

```bash
# Run migrations (auto-detect method)
ezkey database migrate

# Show migration info
ezkey database info

# Repair migration metadata
ezkey database repair

# Use specific Java/JAR
ezkey database migrate --java-path /usr/bin/java --ezkey-core-jar /path/to/ezkey-core.jar
```

### OpenAPI Commands

```bash
# Refresh all demo app specifications
ezkey openapi refresh --all

# Refresh only demo-app-acme (admin API)
ezkey openapi refresh --app

# Refresh only demo-device (auth API)
ezkey openapi refresh --device
```

### Configuration Commands

```bash
# Interactive configuration
ezkey configure interactive

# Set specific values
ezkey configure set --admin-url http://localhost:9080 --timeout 30000

# Get all configuration
ezkey configure get

# Get specific value
ezkey configure get --key adminUrl

# Reset configuration
ezkey configure reset
```

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
# Pass JSON directly (escape quotes as needed)
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

## Examples

### Complete Integration Workflow

```bash
# 1. Create integration
ezkey admin integration create --data '{
  "logo": "https://acme.com/logo.png",
  "i18n": [
    {"language": "en", "name": "ACME Corp", "description": "ACME secure login"}
  ]
}'

# 2. Create enrollment
ezkey admin enrollment create --integration-id 1 --name "John's iPhone" --challenge-required

# 3. Bind device (simulate mobile app)
ezkey auth enrollment bind --id 1

# 4. Verify enrollment with keys
ezkey sim keypair --key-size 2048 > keys.json
ezkey auth enrollment verify --enrollment-id 1 --challenge-response 123456 --public-key "..." --token-signed "..."

# 5. Create auth attempt
ezkey admin auth-attempt create --enrollment-id 1

# 6. Check for pending requests (simulate mobile app)
ezkey auth auth-attempt pending --enrollment-id 1 --device-token "..." --device-token-signed "..."

# 7. Respond to auth attempt
ezkey auth auth-attempt respond --id 1 --accepted true --token-signed "..."

# 8. Wait for completion (simulate backend)
ezkey admin auth-attempt wait --id 1 --timeout 30
```

### Cryptographic Testing Workflow

```bash
# Generate test keys
ezkey sim keypair > keypair.json

# Extract keys (using jq or manually)
cat keypair.json | jq -r .privateKey > private.pem
cat keypair.json | jq -r .publicKey > public.pem

# Sign some data
ezkey sim sign --data "test message" --private-key @private.pem > signature.json

# Validate the signature
cat signature.json | jq -r .signature > signature.txt
ezkey sim validate --data "test message" --signature @signature.txt --public-key @public.pem
```

## Development

### Project Structure

```
ezkey-cli/
├── package.json          # Dependencies and scripts
├── tsconfig.json         # TypeScript configuration
├── bin/ezkey            # Executable entry point
├── src/
│   ├── index.ts         # Main CLI application
│   ├── commands/        # Command implementations
│   │   ├── admin.ts     # Admin API commands
│   │   ├── auth.ts      # Auth API commands
│   │   ├── sim.ts       # Sim API commands
│   │   ├── database.ts  # Database migration commands
│   │   ├── openapi.ts   # OpenAPI management commands
│   │   └── configure.ts # Configuration commands
│   ├── config/          # Configuration management
│   │   └── config-manager.ts
│   ├── utils/           # Utility modules
│   │   ├── http-client.ts
│   │   └── json-utils.ts
│   └── types/           # TypeScript type definitions
└── dist/                # Compiled JavaScript (after build)
```

### Building

```bash
# Install dependencies
npm install

# Build TypeScript
npm run build

# Development mode (with auto-compilation)
npm run dev

# Clean build artifacts
npm run clean
```

### Adding New Commands

1. Create command file in `src/commands/`
2. Implement command class with `getCommand()` method
3. Register in `src/index.ts`
4. Update this README with command documentation

## Troubleshooting

### Common Issues

**Command not found**
```bash
# Make sure the binary is executable
chmod +x bin/ezkey

# Check if in development mode
npm run build
```

**Connection refused errors**
```bash
# Check if APIs are running
curl http://localhost:9080/actuator/health  # Admin API
curl http://localhost:8080/actuator/health  # Auth API

# Update configuration
ezkey configure set --admin-url http://your-server:9080
```

**Migration failures**
```bash
# Check Java version
java -version

# Verify project structure
ezkey database migrate --java-path /usr/bin/java

# Use specific JAR
ezkey database migrate --ezkey-core-jar /path/to/ezkey-core.jar
```

### Debug Mode

Enable verbose output for debugging:

```bash
ezkey --verbose admin integration list
```

## Contributing

1. Follow the existing code style and patterns
2. Add comprehensive error handling
3. Update documentation for new commands
4. Test all command paths manually
5. Ensure TypeScript compilation without errors

## License

MIT License - see LICENSE file in the project root.