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

## Security pentest curated (runtime hygiene)

Keyword: **`security-pentest-curated`**. Local-first bounded runtime campaign runner for
Schemathesis + ZAP baseline + first-party Nuclei templates. Report-oriented, not a CI gate.

```bash
./scripts/security-pentest-curated-preflight.sh
./scripts/security-pentest-curated.sh --dry-run
./scripts/security-pentest-curated.sh
```

Configuration: [`config/security-pentest/`](../config/security-pentest/)

Output (gitignored): `logs/security-pentest/`

Current health probe behavior:

- probes multiple health candidates in order (`/api/actuator/health`, `/actuator/health`, `/health`, `/`)
- classifies `200`, `401`, `403` as reachable/observable
- checks both proxy and direct URLs to surface Caddy vs Actuator routing drift

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

### Role-separated migration contract

The migration tool is aligned with the PostgreSQL role split documented in
[`docs/DATABASE_ROLE_PERMISSIONS_MATRIX.md`](../docs/DATABASE_ROLE_PERMISSIONS_MATRIX.md):

- Flyway connects as `ezkey_migrate`; it does not use an application role or the `postgres`
  superuser.
- `ezkey_migrate` owns schema objects and performs DDL.
- Runtime DML grants are deliberately outside Flyway and are applied after a successful migration
  by [`db/apply-grants.sh`](db/apply-grants.sh).
- Docker Compose enforces this sequence automatically:
  `postgres init → migration → db-grants → APIs`.
- For a new local non-Docker database, run:

  ```bash
  ./scripts/db/create-roles.sh
  ./scripts/ezkey-flyway.sh
  ./scripts/db/apply-grants.sh
  ./scripts/db/verify-grants.sh  # optional privilege smoke check
  ```

`ezkey-flyway.sh` and `ezkey-flyway-jar.sh` run Flyway only. They intentionally do not create
LOGIN roles or apply runtime grants. This keeps schema evolution separate from credential
provisioning and authorization policy.

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

## Additional Scripts

- `format-specs.sh` - Format existing JSON specifications for better readability

## Recommended Workflow

### 1. Daily Development
```bash
# Run database migrations as ezkey_migrate
./scripts/ezkey-flyway.sh

# Re-apply runtime DML grants after schema changes (not needed for --info/--validate)
./scripts/db/apply-grants.sh

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
# Build the dedicated migration JAR
cd ezkey-migration
mvn package -Pmigration-jar

# Supply the ezkey_migrate datasource credentials through the environment,
# then inspect and run migrations.
java -jar target/ezkey-migration.jar --info
java -jar target/ezkey-migration.jar

# Apply the reviewed runtime role matrix after Flyway succeeds.
cd ..
./scripts/db/apply-grants.sh
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
