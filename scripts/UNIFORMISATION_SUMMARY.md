# OpenAPI Specification Uniformization Summary

## Initial Problem

Ezkey demo projects were using two different approaches for OpenAPI specifications:

- **ezkey-demo-device** : Live URL (`http://localhost:8080/v3/api-docs`)
- **ezkey-demo-app-acme** : Local file (`openapi-spec.json`)

This disparity caused:
- Unstable builds (network dependency)
- CI/CD difficulties
- Inconsistent maintenance

## Implemented Solution

### 1. Uniformized Approach: Local File

**Decision:** Use local files for both projects

**Advantages:**
- ✅ Build stability (no network dependency)
- ✅ Version control (Git management of API changes)
- ✅ Simple CI/CD (no need to start services)
- ✅ Reproducibility (identical builds)
- ✅ Independence (development possible without APIs)

### 2. Synchronization Scripts

**Created Scripts:**
- `scripts/update-openapi-specs.sh` (Bash - Linux/macOS/Git Bash)
- `scripts/update-openapi-specs.bat` (Windows Batch)

**Features:**
- Automatic download from running APIs
- Automatic backup before modification
- JSON validation (if `jq` available)
- Automatic restoration on failure
- Options for selective updates

### 3. Uniformized Maven Configuration

**Before:**
```xml
<!-- demo-device -->
<inputSpec>http://localhost:8080/v3/api-docs</inputSpec>

<!-- demo-app-acme -->
<inputSpec>${project.basedir}/openapi-spec.json</inputSpec>
```

**After:**
```xml
<!-- Both projects -->
<inputSpec>${project.basedir}/openapi-spec.json</inputSpec>
```

## Modified Files

### 1. Maven Configuration
- `ezkey-demo-device/pom.xml` : Changed from live URL to local file

### 2. Created Scripts
- `scripts/update-openapi-specs.sh` : Main bash script
- `scripts/update-openapi-specs.bat` : Windows script
- `scripts/README.md` : Complete documentation
- `scripts/UNIFORMISATION_SUMMARY.md` : This summary

### 3. Specification Files
- `ezkey-demo-device/openapi-spec.json` : Downloaded from auth-api
- `ezkey-demo-app-acme/openapi-spec.json` : Already existing

## Recommended Workflow

### Daily Development
```bash
# 1. Start the APIs
mvn spring-boot:run -pl ezkey-auth-api
mvn spring-boot:run -pl ezkey-admin-api

# 2. Modify the API as needed

# 3. Update the specs
./scripts/update-openapi-specs.sh

# 4. Recompile demo projects
mvn clean compile -pl ezkey-demo-device,ezkey-demo-app-acme
```

### Before Commit
```bash
# Check that specs are up to date
./scripts/update-openapi-specs.sh --all

# Test compilation
mvn clean compile -pl ezkey-demo-device,ezkey-demo-app-acme
```

## Script Usage

### Available Options
```bash
./scripts/update-openapi-specs.sh --help
```

- `--app` : Update only demo-app-acme
- `--device` : Update only demo-device  
- `--all` : Update both (default)
- `--help` : Display help

### Usage Examples
```bash
# Complete update
./scripts/update-openapi-specs.sh

# Selective update
./scripts/update-openapi-specs.sh --device
./scripts/update-openapi-specs.sh --app
```

## Achieved Advantages

### 1. Stability
- No more broken builds due to network issues
- Reliable and reproducible compilation

### 2. Traceability
- API changes are visible in Git
- History of API evolution

### 3. Flexibility
- Ability to revert to a previous version
- Development possible without running APIs

### 4. Performance
- Faster generation (no download at each build)
- Fewer external dependencies

### 5. CI/CD
- Stable and predictable builds
- No need to start services

## Prerequisites

- `curl` : For downloading specifications
- `jq` (optional) : For JSON validation
- APIs started : To be able to download specs

## Validation

✅ **Tests performed:**
- Auth-api spec download
- Demo-device project compilation
- DTO generation
- Update script functionality

✅ **Results:**
- BUILD SUCCESS on demo-device
- Scripts functional on Windows and Linux
- Complete documentation

## Conclusion

The uniformization is **successfully completed**. Both demo projects now use the same approach (local file) with robust synchronization scripts to keep specifications up to date.

This solution offers a good balance between:
- **Stability** (reliable builds)
- **Flexibility** (version control)
- **Simplicity** (automated scripts)
- **Maintainability** (uniform approach)

## Security Considerations

### API Access
- Scripts only access local development APIs
- No production API access through these scripts
- Secure error handling prevents information leakage

### File Management
- Automatic backup before updates
- Validation of downloaded specifications
- Safe fallback to previous versions

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
