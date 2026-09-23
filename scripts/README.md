# Ezkey Scripts

## Overview

This directory contains utility scripts for Ezkey project management, including OpenAPI specification management and database migration tools.

**Portable Bash only:** use `./scripts/*.sh` from Git Bash on Windows, Linux, or macOS. Do not add `.bat` / `.ps1` / `.cmd` wrappers for repo tooling.

## Maven build (Java reactor)

Single entrypoint for the full validation baseline (Spotless, Checkstyle, clean, install, unit tests):

```bash
./scripts/build.sh
```

Diagnostics only: `./scripts/build.sh --diagnose-only`

On a fresh clone with an empty `~/.m2`, use this script rather than a bare `mvn checkstyle:check`.
The plugin depends on unpublished `org.ezkey:checkstyle-config`; `build.sh` installs that module first.
See [`docs/DEVELOPMENT.md`](../docs/DEVELOPMENT.md) § *First clone on a new workstation*.

Docker-only alternative (no host JDK/Maven): `./scripts/build-docker.sh`. Same DEVELOPMENT.md section.

## Git fast-forward helper

Deterministic helper to update the current branch from an upstream branch using fetch + ff-only.
It refuses dirty working trees and stops on divergence.

```bash
./scripts/git-ff-only.sh
```

Optional upstream argument:

```bash
./scripts/git-ff-only.sh origin/release/x.y
```

## Java doctor-curated (punctual hygiene)

Keyword: **`java-doctor-curated`**. Report-only shortlist (SpotBugs + Semgrep + narrow PMD); not a
build gate. See root [`AGENTS.md`](../AGENTS.md) § Java doctor-curated.

```bash
./scripts/java-doctor-curated.sh
```

Config: [`config/java-doctor/`](../config/java-doctor/). Outputs: `logs/java-doctor/` (gitignored).

## Security pentest curated (runtime hygiene)

Keyword: **`security-pentest-curated`**. Local-first bounded runtime campaign runner for
Schemathesis + OpenAPI-aware ZAP (`zap-api-scan.py`; `--zap-baseline` is opt-in) + first-party
Nuclei templates. Report-oriented, not a CI gate.
Nuclei prefers a local binary, then Docker (`NUCLEI_IMAGE` in `config/security-pentest/targets.env`).

```bash
./scripts/security-pentest-curated-preflight.sh
./scripts/security-pentest-curated.sh --dry-run
./scripts/security-pentest-curated.sh
# Deeper, more intrusive pass (pass-05+): all Schemathesis checks/phases, ZAP active scan,
# X-Forwarded-For rate-limit probe. Attack one API at a time with --only.
./scripts/security-pentest-curated.sh --deep --zap-active --probe-forwarded-ip
./scripts/security-pentest-curated.sh --deep --only admin
```

Profiles and flags: `--profile default|deep` (`--deep`), `--only admin|auth|integration`
(repeatable), `--zap-active` (drops `-S`), `--probe-forwarded-ip`. Deep knobs live in
`config/security-pentest/targets.env` (`SCHEMATHESIS_DEEP_*`, `SCHEMATHESIS_HEADERS` for a
bearer-authenticated tier, `FORWARDED_IP_PROBE_*`). Docker-run tools reach host ports through
`host.docker.internal` (`host-gateway`), and ZAP / Nuclei runs are wrapped in `timeout`.

Configuration: [`config/security-pentest/`](../config/security-pentest/)

Output (gitignored): `logs/security-pentest/` — `security-pentest.curated.{md,json,html}` plus
`raw/`. Schemathesis findings are one row per operation x check (Server error P1; auth ignored /
negative data accepted with a 2xx P2; contract drift P3).

Current health probe behavior:

- probes multiple health candidates in order (`/api/actuator/health`, `/actuator/health`, `/health`, `/`)
- classifies `200`, `401`, `403` as reachable/observable
- checks both proxy and direct URLs to surface Caddy vs Actuator routing drift

## JavaMelody curated (live-stack performance)

Keyword: **`javamelody-curated`**. Report-only extract from the opt-in JavaMelody collector after a
known workload (typically operational churn). Ranks HTTP/SQL/Spring by total time, drops Actuator
scrape noise, and proposes a small HITL lot. Not a CI gate. See root [`AGENTS.md`](../AGENTS.md)
§ JavaMelody curated.

```bash
./scripts/javamelody-curated.sh
./scripts/javamelody-curated.sh --offline
```

Config: [`config/javamelody/`](../config/javamelody/). Outputs: `logs/javamelody/` (gitignored).
Campaign notes: [`product-docs/global/hygiene/javamelody/`](../product-docs/global/hygiene/javamelody/).

## Cloudflare (ezkey.org static site)

- [`cloudflare/deploy-ezkey-org-preview.sh`](cloudflare/deploy-ezkey-org-preview.sh) — deploy [`sites/ezkey-org/`](../sites/ezkey-org/) to Cloudflare Pages as a **preview** (requires `CLOUDFLARE_API_TOKEN` and `CLOUDFLARE_ACCOUNT_ID`). See [`docs/cloudflare/ezkey-org-site.md`](../docs/cloudflare/ezkey-org-site.md).
- [`cloudflare/deploy-ezkey-org-production.sh`](cloudflare/deploy-ezkey-org-production.sh) — deploy the same folder to the Pages **production** branch (serves the custom domain, e.g. `ezkey.org`, when configured in Cloudflare). Same environment variables.
- [`cloudflare/upload-auth-api-schema-exp1.sh`](cloudflare/upload-auth-api-schema-exp1.sh) / [`cloudflare/upload-integration-api-schema-exp1.sh`](cloudflare/upload-integration-api-schema-exp1.sh) — API Shield schema list/upload/delete (`CLOUDFLARE_API_SHIELD_TOKEN`, not the Pages token).

## Lightsail (community / ezkey.online host)

AWS CLI helpers under [`lightsail/`](lightsail/) to create and bootstrap a **NEW** all-in-one Lightsail VM parallel to EXP1 (`exp1-ezkey`). **VM lifecycle only** (create / ports / bootstrap / status / delete). Dry-run by default for create / delete / ports. Does **not** change DNS or certificates. Self-contained tree — may later extract to a private ops repo after e2e validation (see runbook). Runbook: [`docs/lightsail/community-host.md`](../docs/lightsail/community-host.md).

```bash
export AWS_PROFILE=ezkey-lightsail
./scripts/lightsail/create-instance.sh
./scripts/lightsail/open-ports.sh
./scripts/lightsail/bootstrap-host.sh --dry-run
./scripts/lightsail/status.sh --help
./scripts/lightsail/delete-instance.sh --help
```

App layer remains [`experimental-hybrid/lightsail/`](../experimental-hybrid/lightsail/); image push reuses [`experimental-hybrid/scripts/export-backend-images-to-lightsail.sh`](../experimental-hybrid/scripts/export-backend-images-to-lightsail.sh) with `LIGHTSAIL_SSH_HOST=ezkey-online`. Do not put community-only secrets or ezkey.online-only DNS checklists into the public compose tree.

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

Forced Auth-first keyset readiness (ADR-0012 / TB-2026-08-28), not a lucky clean-start:

```bash
./scripts/repro-auth-keyset-readiness.sh
./scripts/repro-auth-keyset-readiness.sh negative
./scripts/repro-auth-keyset-readiness.sh positive
```

`ezkey-flyway.sh` and `ezkey-flyway-jar.sh` run Flyway only. They intentionally do not create
LOGIN roles or apply runtime grants. This keeps schema evolution separate from credential
provisioning and authorization policy.

### Spring Boot Mode Scripts (Recommended)

These scripts use the modern Spring Boot Maven plugin approach with `mvn spring-boot:run`.

#### Bash (Linux/macOS/Git Bash)
```bash
./scripts/ezkey-flyway.sh [COMMAND]
```

#### Available Commands
- No argument : Run default migration
- `--info` : Show migration info
- `--repair` : Repair migration history
- `--migrate` : Run migrations explicitly

### JAR Mode Scripts (Production-Ready)

These scripts build and run the executable JAR directly, suitable for production deployments.

#### Bash (Linux/macOS/Git Bash)
```bash
./scripts/ezkey-flyway-jar.sh [COMMAND]
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
- **Cross-platform** : Works on Windows (Git Bash), Linux, and macOS
- **Error handling** : Proper error messages and exit codes

## OpenAPI Specification Management

### Centralized Specification System

Ezkey now uses a centralized approach for managing OpenAPI specifications. All specifications are stored in the `specs/` directory and automatically synchronized across all projects.

### Synchronization Scripts

#### Bash script (Windows Git Bash, Linux, macOS)
```bash
./scripts/update-specs.sh [OPTIONS]
```

#### Available Options
- `--admin-only` : Update only admin-api specification
- `--auth-only` : Update only auth-api specification
- `--integration-only` : Update only integration-api specification
- `--all` : Update all specifications (default)
- `--help` : Display help

Auth API and Integration API canonical output is host-neutral: `update-specs.sh --auth-only`
and `--integration-only` strip top-level `servers` after fetch. Do not put EXP1 or localhost
hosts back into Java `@Server` annotations. To build Cloudflare upload artifacts:

```bash
# EXP1 (default)
./scripts/package-auth-api-cloudflare-schema.sh
./scripts/cloudflare/upload-auth-api-schema-exp1.sh --list
./scripts/package-integration-api-cloudflare-schema.sh
./scripts/cloudflare/upload-integration-api-schema-exp1.sh --list

# Community (ezkey.online)
./scripts/package-auth-api-cloudflare-schema.sh --community
./scripts/package-integration-api-cloudflare-schema.sh --community
```

See [`docs/cloudflare/auth-api-schema-validation.md`](../docs/cloudflare/auth-api-schema-validation.md)
and [`docs/cloudflare/integration-api-schema-validation.md`](../docs/cloudflare/integration-api-schema-validation.md).

## Additional Scripts

- `format-specs.sh` - Format existing JSON specifications for better readability
- `package-auth-api-cloudflare-schema.sh` - Auth API Cloudflare schema (EXP1 default; `--community` / `--server`)
- `cloudflare/upload-auth-api-schema-exp1.sh` - list or upload EXP1 Auth artifact (`CLOUDFLARE_API_SHIELD_TOKEN`)
- `cloudflare/upload-auth-api-schema-community.sh` - thin community defaults for Auth (`ezkey.online`, None mitigation)
- `package-integration-api-cloudflare-schema.sh` - Integration API Cloudflare schema (EXP1 default; `--community` / `--server`)
- `cloudflare/upload-integration-api-schema-exp1.sh` - list or upload EXP1 Integration artifact (same shield token; does not delete Auth)
- `cloudflare/upload-integration-api-schema-community.sh` - thin community defaults for Integration

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
├── ezkey-flyway-jar.sh        # Migration script (JAR mode)
├── update-specs.sh            # OpenAPI sync from live APIs
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
