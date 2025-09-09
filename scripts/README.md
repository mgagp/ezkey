# Ezkey Scripts

## Overview

This directory contains utility scripts for Ezkey project management, including OpenAPI specification management and database migration tools.

## Initial Problem

Demo projects were using two different approaches for OpenAPI specifications:

- **ezkey-demo-device** : Live URL (`http://localhost:8080/v3/api-docs`)
- **ezkey-demo-app-acme** : Local file (`openapi-spec.json`)

## Adopted Solution: Standardized Local File

### Why This Approach?

**Advantages:**
- ✅ **Build stability** : No network dependency
- ✅ **Version control** : Controlled management of API changes
- ✅ **Simple CI/CD** : No need to start services
- ✅ **Reproducibility** : Identical builds every time
- ✅ **Independence** : Development possible without running APIs

**Disadvantages:**
- ❌ **Manual maintenance** : Requires updating the file
- ❌ **Desynchronization risk** : Possibility of having obsolete DTOs

## Database Migration Scripts

### Simple Scripts (Recommended)

#### Bash Script (Linux/macOS/Git Bash)
```bash
./scripts/ezkey-flyway-simple.sh [COMMAND]
```

#### Windows Batch Script
```cmd
scripts\ezkey-flyway-simple.bat [COMMAND]
```

#### Available Commands
- No argument : Run default migration
- `--info` : Show migration info
- `--repair` : Repair migration history
- `--migrate` : Run migrations explicitly

### Legacy Scripts (Complex)

#### Bash Script (Linux/macOS/Git Bash)
```bash
./scripts/ezkey-flyway.sh [COMMAND]
```

#### Windows Batch Script
```cmd
scripts\ezkey-flyway.bat [COMMAND]
```

**Note:** The legacy scripts build the project and construct classpath manually. Use the simple scripts instead.

## OpenAPI Specification Management

### Centralized Specification System

Ezkey now uses a centralized approach for managing OpenAPI specifications. All specifications are stored in the `specs/` directory and automatically synchronized across all projects.

### Synchronization Scripts

#### Bash Script (Linux/macOS/Git Bash)
```bash
./scripts/update-specs.sh [OPTIONS]
```

#### Windows Batch Script
```cmd
scripts\update-specs.bat [OPTIONS]
```

#### Available Options
- `--admin-only` : Update only admin-api specification
- `--auth-only` : Update only auth-api specification
- `--all` : Update all specifications (default)
- `--help` : Display help

### Legacy Scripts (Deprecated)

The old scripts are still available but deprecated:
- `update-openapi-specs.sh` - Use `update-specs.sh` instead
- `update-openapi-specs.bat` - Use `update-specs.bat` instead

### Additional Scripts

- `format-specs.sh` - Format existing JSON specifications for better readability

## Recommended Workflow

### 1. Daily Development
```bash
# Start the APIs
mvn spring-boot:run -pl ezkey-auth-api &
mvn spring-boot:run -pl ezkey-admin-api &

# Update specs when API changes
./scripts/update-specs.sh

# Recompile demo projects
mvn clean compile -pl ezkey-demo-device,ezkey-demo-app-acme
```

### 2. Before Commit
```bash
# Check that specs are up to date
./scripts/update-specs.sh --all

# Test that everything compiles
mvn clean compile -pl ezkey-demo-device,ezkey-demo-app-acme
```

### 3. CI/CD
CI/CD builds use local files directly, ensuring stability.

## File Structure

```
specs/                         # Centralized specifications
├── admin-api/
│   ├── openapi-spec.json      # Admin API specification
│   └── README.md              # API documentation
├── auth-api/
│   ├── openapi-spec.json      # Auth API specification
│   └── README.md              # API documentation
└── README.md                  # Centralized specs documentation

ezkey-demo-device/
├── openapi-spec.json          # Links to specs/auth-api/openapi-spec.json
└── pom.xml                    # Uses local file

ezkey-demo-app-acme/
├── openapi-spec.json          # Links to specs/admin-api/openapi-spec.json
└── pom.xml                    # Uses local file

ezkey-sdk/
├── admin-api-spec.json        # Links to specs/admin-api/openapi-spec.json
└── auth-api-spec.json         # Links to specs/auth-api/openapi-spec.json

scripts/
├── update-specs.sh            # Main bash script
├── update-specs.bat           # Windows script
├── setup-centralized-specs.sh # Initial setup script
└── README.md                  # This documentation
```

## Maven Configuration

Both projects now use the same configuration:

```xml
<inputSpec>${project.basedir}/openapi-spec.json</inputSpec>
```

## Error Handling

### If API is not available
The script displays an error message and does not modify existing files.

### If download fails
The script automatically restores the previous backup.

### JSON Validation
The script automatically validates downloaded JSON (if `jq` is installed).

## Prerequisites

- `curl` : For downloading specifications
- `jq` (optional) : For JSON validation
- APIs started : To be able to download specs

## Usage Examples

### Complete Update
```bash
./scripts/update-openapi-specs.sh
```

### Selective Update
```bash
./scripts/update-openapi-specs.sh --device
./scripts/update-openapi-specs.sh --app
```

### Help Verification
```bash
./scripts/update-openapi-specs.sh --help
```

## Integration with Development Workflow

1. **API Modification** : Develop in `ezkey-auth-api` or `ezkey-admin-api`
2. **Local Testing** : Start the API and test
3. **Synchronization** : Execute the update script
4. **Validation** : Recompile demo projects
5. **Commit** : Include updated `openapi-spec.json` files

## Advantages of This Approach

- **Consistency** : Both projects use the same approach
- **Reliability** : No broken builds due to network issues
- **Traceability** : API changes are visible in Git
- **Flexibility** : Ability to revert to a previous version
- **Performance** : Faster generation (no download at each build)

## Security Considerations

### API Access
- Scripts only access local development APIs
- No production API access through these scripts
- Secure error handling prevents information leakage

### File Management
- Automatic backup before updates
- Validation of downloaded specifications
- Safe fallback to previous versions

## Troubleshooting

### Common Issues

**API not responding:**
```bash
# Check if APIs are running
curl http://localhost:8080/v3/api-docs
curl http://localhost:9080/v3/api-docs
```

**Permission denied:**
```bash
# Make script executable (Linux/Mac)
chmod +x scripts/update-openapi-specs.sh
```

**JSON validation failed:**
```bash
# Install jq for JSON validation
# Ubuntu/Debian
sudo apt-get install jq

# macOS
brew install jq

# Windows
# Download from https://stedolan.github.io/jq/download/
```

### Script Debugging

Enable verbose output:
```bash
./scripts/update-openapi-specs.sh --verbose
```

Check script syntax:
```bash
# Bash
bash -n scripts/update-openapi-specs.sh

# Windows
# Check batch file syntax manually
```

## Best Practices

### Development
- Always update specs after API changes
- Test compilation before committing
- Keep backup files for safety

### CI/CD Integration
- Use local files in build pipelines
- Validate specs during build process
- Include spec updates in version control

### Maintenance
- Regular validation of spec files
- Monitor for API changes
- Update documentation when needed
