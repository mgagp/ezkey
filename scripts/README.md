# Ezkey Scripts

## Overview

This directory contains utility scripts for Ezkey project management, including OpenAPI specification management and database migration tools.

**Portable Bash first:** prefer `./scripts/*.sh` from Git Bash on Windows, Linux, or macOS. Legacy `.bat` siblings may still exist for some tools; they are not the canonical path for agents or cross-platform workflows.

## Maven build (Java reactor)

Single entrypoint for the full validation baseline (Spotless, Checkstyle, clean, install, unit tests):

```bash
./scripts/build.sh
```

Diagnostics only: `./scripts/build.sh --diagnose-only`

Docker-only alternative (no host JDK/Maven): `./scripts/build-docker.sh`. See [`docs/DEVELOPMENT.md`](../docs/DEVELOPMENT.md).

## Java doctor-curated (punctual hygiene)

Keyword: **`java-doctor-curated`**. Report-only shortlist (SpotBugs + Semgrep + narrow PMD); not a
build gate. See root [`AGENTS.md`](../AGENTS.md) § Java doctor-curated.

```bash
./scripts/java-doctor-curated.sh
```

Config: [`config/java-doctor/`](../config/java-doctor/). Outputs: `logs/java-doctor/` (gitignored).

## Cloudflare (ezkey.org static site)

- [`cloudflare/deploy-ezkey-org-preview.sh`](cloudflare/deploy-ezkey-org-preview.sh) — deploy [`sites/ezkey-org/`](../sites/ezkey-org/) to Cloudflare Pages as a **preview** (requires `CLOUDFLARE_API_TOKEN` and `CLOUDFLARE_ACCOUNT_ID`). See [`docs/cloudflare/ezkey-org-site.md`](../docs/cloudflare/ezkey-org-site.md).
- [`cloudflare/deploy-ezkey-org-production.sh`](cloudflare/deploy-ezkey-org-production.sh) — deploy the same folder to the Pages **production** branch (serves the custom domain, e.g. `ezkey.org`, when configured in Cloudflare). Same environment variables.

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

### Spring Boot Mode Scripts (Recommended)

These scripts use the modern Spring Boot Maven plugin approach with `mvn spring-boot:run`.

#### Bash Script (Linux/macOS/Git Bash)
```bash
./scripts/ezkey-flyway.sh [COMMAND]
```

#### Windows Batch Script
```cmd
scripts\ezkey-flyway.bat [COMMAND]
```

#### Available Commands
- No argument : Run default migration
- `--info` : Show migration info
- `--repair` : Repair migration history
- `--migrate` : Run migrations explicitly

### JAR Mode Scripts (Production-Ready)

These scripts build and run the executable JAR directly, suitable for production deployments.

#### Bash Script (Linux/macOS/Git Bash)
```bash
./scripts/ezkey-flyway-jar.sh [COMMAND]
```

#### Windows Batch Script
```cmd
scripts\ezkey-flyway-jar.bat [COMMAND]
```

#### Available Commands
- No argument : Run default migration
- `--info` : Show migration info
- `--repair` : Repair migration history
- `--migrate` : Run migrations explicitly

### Migration Tool Features

Both script modes provide:
- **Automatic JAR building** : Builds migration JAR if not present
- **Spring Boot integration** : Uses the new `EzkeyCoreApp` application
- **Command-line interface** : Supports all Flyway commands
- **Cross-platform** : Works on Windows, Linux, and macOS
- **Error handling** : Proper error messages and exit codes

## OpenAPI Specification Management

### Centralized Specification System

Ezkey now uses a centralized approach for managing OpenAPI specifications. All specifications are stored in the `specs/` directory and automatically synchronized across all projects.

### Synchronization Scripts

#### Bash script (canonical — Windows Git Bash, Linux, macOS)
```bash
./scripts/update-specs.sh [OPTIONS]
```

#### Windows batch script (legacy)
```cmd
scripts\update-specs.bat [OPTIONS]
```

Prefer `update-specs.sh` from Git Bash on Windows for parity with Linux and macOS.

#### Available Options
- `--admin-only` : Update only admin-api specification
- `--auth-only` : Update only auth-api specification
- `--all` : Update all specifications (default)
- `--help` : Display help

### Legacy Scripts (Deprecated)

The old scripts are still available but deprecated:
- `update-openapi-specs.sh` - Use `update-specs.sh` instead
- `update-openapi-specs.bat` - Use `update-specs.bat` instead

### Native Build Scripts

### Native AOT Build Script

A specialized build script to avoid intermittent MapStruct compilation issues with Spring Boot AOT processing.

#### Problem
MapStruct generates mapper implementations that reference classes from `ezkey-core`. If `ezkey-core` is not installed in the local Maven repository before `ezkey-auth-api` compilation, the generated mapper bytecode may contain "Unresolved compilation problems" that cause AOT processing to fail.

#### Solution
The script implements a specific build sequence:
1. Clean everything
2. Install `ezkey-core` first (ensures it's in local repository)
3. Compile `ezkey-auth-api` with dependencies (MapStruct can resolve classes)
4. Execute AOT processing (Spring can introspect properly compiled mappers)

#### Usage

**Bash Script (Linux/macOS/Git Bash):**
```bash
./scripts/build-native-aot.sh [options]
```

#### Available Options
- `--skip-tests` : Skip tests during build
- `--skip-aot` : Skip AOT processing (only compile)
- `--verbose` : Show detailed Maven output
- `--help` : Show help message

#### Examples
```bash
# Standard build with tests
./scripts/build-native-aot.sh

# Build without tests
./scripts/build-native-aot.sh --skip-tests

# Compile only, skip AOT
./scripts/build-native-aot.sh --skip-aot

# Verbose output for debugging
./scripts/build-native-aot.sh --verbose
```

#### Troubleshooting
If AOT processing fails:
1. Verify that `ezkey-core` is installed: `mvn -pl ezkey-core install`
2. Check for MapStruct compilation errors in `target/generated-sources`
3. Ensure Eclipse/IDE is closed (can interfere with compilation)
4. Try running with `--verbose` to see detailed error messages
5. Use `--debug-classpath` to analyze the classpath used by AOT

#### Debugging Classpath Issues

If you suspect classpath issues, use the debugging options:

```bash
# Show classpath analysis during build
./scripts/build-native-aot.sh --skip-tests --debug-classpath

# Or use the dedicated classpath debugging script
./scripts/debug-aot-classpath.sh --check-class org.ezkey.authattempt.dto.AuthAttemptPendingResponse
```

The `--debug-classpath` option will:
- Capture the compile classpath used by AOT
- Check if `ezkey-auth-api/target/classes` is included
- Check if `ezkey-core` JAR is included
- Save the full classpath to a file for analysis

#### Debugging Execution Order

If you suspect timing issues (classes not generated before AOT runs), use:

```bash
./scripts/debug-aot-execution-order.sh --check-classes
```

This script will:
- Check if MapStruct classes exist before/after compilation
- Verify the Maven phase where AOT runs
- List all classes in `target/classes`
- Attempt AOT processing and show the actual error

This is particularly useful for diagnosing intermittent `NoClassDefFoundError` issues that work sometimes but not others.

### Native API Build Scripts

The native-image workflow is scoped to the Auth API and Integration API. Admin API remains JVM-only.

#### Usage

**Bash Script (Linux/macOS/Git Bash):**
```bash
./scripts/build-native-aot.sh [options]
```

#### Available Options
- `--skip-tests` : Skip tests during build
- `--skip-aot` : Stop after compilation, before Spring AOT
- `--verbose` : Show detailed Maven output
- `--debug-classpath` : Dump classpath details before AOT
- `--help` : Show help message

#### Examples
```bash
# Standard auth-api build without tests
./scripts/build-native-aot.sh --skip-tests

# Verbose output for debugging
./scripts/build-native-aot.sh --verbose
```

#### Native Image Builds
```bash
# Auth API
mvn spring-boot:build-image -pl ezkey-auth-api -Pnative \
	-Dspring-boot.build-image.imageName=ezkey-auth-api-native -Dmaven.test.skip=true

# Integration API
mvn spring-boot:build-image -pl ezkey-integration-api -Pnative \
	-Dspring-boot.build-image.imageName=ezkey-integration-api-native -Dmaven.test.skip=true
```

See `docs/NATIVE_COMPILATION_STRATEGY.md` for complete strategy details.

## Additional Scripts

- `format-specs.sh` - Format existing JSON specifications for better readability
- `build-native-aot.sh` - Native AOT build script for auth-api (see Native Build Scripts section above)
- `debug-aot-classpath.sh` - Debug classpath used by AOT processor
- `debug-aot-runtime-classpath.sh` - Debug runtime classpath during AOT execution
- `debug-aot-execution-order.sh` - Debug execution order and timing of AOT processing

## Recommended Workflow

### 1. Daily Development
```bash
# Run database migrations
./scripts/ezkey-flyway.sh

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
# Check migration status
./scripts/ezkey-flyway.sh --info

# Check that specs are up to date
./scripts/update-specs.sh --all

# Test that everything compiles
mvn clean compile -pl ezkey-demo-device,ezkey-demo-app-acme
```

### 3. Production Deployment
```bash
# Build migration JAR for production
cd ezkey-core
mvn clean package -Pmigration-jar

# Run migrations in production
java -jar target/ezkey-migration.jar --info
java -jar target/ezkey-migration.jar
```

### 4. CI/CD
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
├── ezkey-flyway.sh            # Migration script (Spring Boot mode)
├── ezkey-flyway.bat           # Migration script Windows (Spring Boot mode)
├── ezkey-flyway-jar.sh        # Migration script (JAR mode)
├── ezkey-flyway-jar.bat       # Migration script Windows (JAR mode)
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
