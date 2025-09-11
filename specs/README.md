# Ezkey OpenAPI Specifications

This directory contains centralized OpenAPI specifications for all Ezkey APIs. This approach eliminates duplication and ensures consistency across all projects.

## Structure

```
specs/
├── admin-api/                   # Admin API specifications
│   ├── openapi-spec.json        # Current specification
│   ├── openapi-spec.json.backup # Automatic backup
│   └── README.md               # API-specific documentation
├── auth-api/                    # Auth API specifications
│   ├── openapi-spec.json        # Current specification
│   ├── openapi-spec.json.backup # Automatic backup
│   └── README.md               # API-specific documentation
└── README.md                   # This file
```

## Usage

### Projects Using These Specifications

- **ezkey-demo-app-acme** → `specs/admin-api/openapi-spec.json`
- **ezkey-demo-device** → `specs/auth-api/openapi-spec.json`
- **ezkey-sdk** → Both specifications for SDK generation

### Update Process

#### Automatic Update (Recommended)

Use the centralized update script:

```bash
# Update all specifications
./scripts/update-specs.sh

# Update only admin-api
./scripts/update-specs.sh --admin-only

# Update only auth-api
./scripts/update-specs.sh --auth-only
```

**Note**: The update script automatically formats JSON with proper indentation for better readability and AI analysis.

#### Manual Update

If you need to update manually:

```bash
# Admin API
curl http://localhost:9080/api-docs -o specs/admin-api/openapi-spec.json

# Auth API
curl http://localhost:8080/api-docs -o specs/auth-api/openapi-spec.json
```

## Prerequisites

### For Automatic Updates

- **APIs Running**: Both admin-api (port 9080) and auth-api (port 8080) must be running
- **curl**: Required for downloading specifications
- **jq**: Optional but recommended for JSON validation and formatting

### For Development

- **Maven**: For building demo projects
- **Node.js**: For building JavaScript SDK
- **Java**: For building Java SDK

## Workflow

### Daily Development

1. **Start APIs**:
   ```bash
   mvn spring-boot:run -pl ezkey-admin-api &
   mvn spring-boot:run -pl ezkey-auth-api &
   ```

2. **Update Specifications** (when APIs change):
   ```bash
   ./scripts/update-specs.sh
   ```

3. **Build Projects**:
   ```bash
   mvn clean compile -pl ezkey-demo-device,ezkey-demo-app-acme
   ```

### Before Commits

1. **Ensure Specifications are Up-to-Date**:
   ```bash
   ./scripts/update-specs.sh --all
   ```

2. **Test Builds**:
   ```bash
   mvn clean compile -pl ezkey-demo-device,ezkey-demo-app-acme
   mvn clean compile -pl ezkey-sdk/java
   cd ezkey-sdk/javascript && npm run build
   ```

## Benefits

### ✅ **Single Source of Truth**
- One specification file per API
- No duplication across projects
- Consistent versioning

### ✅ **Simplified Maintenance**
- One script updates all projects
- Automatic backup before changes
- Validation of downloaded specifications

### ✅ **Build Stability**
- No network dependency during builds
- Reproducible builds
- CI/CD friendly

### ✅ **Developer Experience**
- Clear update process
- Automatic project synchronization
- Cross-platform support (Windows/Linux/macOS)
- **Formatted JSON** for better readability and AI analysis

## JSON Formatting

### Automatic Formatting

The update scripts automatically format JSON specifications with proper indentation when `jq` is available. This provides several benefits:

- **Better Readability**: Properly indented JSON is easier to read and understand
- **AI Analysis**: Formatted JSON is much more efficient for AI tools to analyze
- **Section Grepping**: Easy to search for specific sections (e.g., `grep -A 10 "paths"`)
- **Cleaner Git Diffs**: Better version control with readable changes

### Manual Formatting

To format existing specifications manually:

```bash
# Format all specifications
./scripts/format-specs.sh

# Format a specific file
jq . specs/admin-api/openapi-spec.json > specs/admin-api/openapi-spec.json.tmp
mv specs/admin-api/openapi-spec.json.tmp specs/admin-api/openapi-spec.json
```

### Example: Before vs After

**Before (minified):**
```json
{"openapi":"3.0.1","info":{"title":"Ezkey Admin API","version":"1.0.0"},"paths":{"/api/v1/integrations":{"get":{"tags":["Integrations"],"summary":"Retrieve all integrations"}}}}
```

**After (formatted):**
```json
{
  "openapi": "3.0.1",
  "info": {
    "title": "Ezkey Admin API",
    "version": "1.0.0"
  },
  "paths": {
    "/api/v1/integrations": {
      "get": {
        "tags": ["Integrations"],
        "summary": "Retrieve all integrations"
      }
    }
  }
}
```

## Troubleshooting

### APIs Not Accessible

If the update script fails with "API not accessible":

1. **Check API Status**:
   ```bash
   curl http://localhost:9080/actuator/health  # Admin API
   curl http://localhost:8080/actuator/health  # Auth API
   ```

2. **Start APIs**:
   ```bash
   mvn spring-boot:run -pl ezkey-admin-api
   mvn spring-boot:run -pl ezkey-auth-api
   ```

### Invalid JSON Downloaded

If the script reports "invalid JSON":

1. **Check API Response**:
   ```bash
   curl http://localhost:9080/api-docs | jq .
   ```

2. **Restore Backup**:
   ```bash
   cp specs/admin-api/openapi-spec.json.backup specs/admin-api/openapi-spec.json
   ```

### Build Failures

If projects fail to build after specification update:

1. **Validate Specifications**:
   ```bash
   jq . specs/admin-api/openapi-spec.json
   jq . specs/auth-api/openapi-spec.json
   ```

2. **Check Project Links**:
   ```bash
   ls -la ezkey-demo-app-acme/openapi-spec.json
   ls -la ezkey-demo-device/openapi-spec.json
   ```

## Support

For issues with the centralized specification system:

1. Check this documentation
2. Review the update script logs
3. Verify API accessibility
4. Check project build logs

The centralized approach ensures that all Ezkey projects stay synchronized with the latest API specifications while maintaining build stability and developer productivity.
