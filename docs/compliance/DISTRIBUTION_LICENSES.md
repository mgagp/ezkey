# License Files in Distribution Artifacts

**Last Updated**: 2026-11-02  
**Purpose**: Documentation of how license files are included in distribution artifacts

## Overview

All distribution artifacts include third-party license attribution files to ensure compliance with open source license obligations.

## Maven JAR Files

### Configuration

Shipped server/backend modules (Admin API, Auth API, Integration API, Crypto API, Migration, Demo Device, Demo App ACME) copy the **committed** root `THIRD-PARTY.txt` into `META-INF/licenses/` via `maven-resources-plugin` (`copy-resources` at `process-resources`). Opt-in is per module (`ezkey.skip.third-party.licenses.copy=false`). Packaging reads the checked-in root file only — it does **not** regenerate licenses during the default build. `maven-enforcer-plugin` fails the build if that file is missing after the copy (so a Docker context that forgot `COPY THIRD-PARTY.txt` cannot silently ship empty licenses).

Docker image builds (`docker/Dockerfile`) must `COPY THIRD-PARTY.txt` into the build stage before `mvn package` so the opt-in modules can package it.

Regenerate the root file separately with `mvn -Plicenses generate-resources` (enforced by CI job `licenses-drift`).

### Location in JAR

- `META-INF/licenses/THIRD-PARTY.txt` (JAR root; present in both the thin `-original` JAR and the Spring Boot executable JAR)

### Verification

To verify license files are included in a JAR:

```bash
jar tf ezkey-admin-api/target/ezkey-admin-api-*.jar | grep -i license
```

Expected output:
```
META-INF/licenses/
META-INF/licenses/THIRD-PARTY.txt
```

## Python Packages

### Configuration

License files are included via `MANIFEST.in` and `setup.py` `package_data` configuration.

### Files Included

- `THIRD-PARTY-LICENSES.txt` - Python dependencies with licenses
- `LICENSE` - Ezkey MIT license
- `README.md` - Package documentation

### Verification

After installing the package:

```bash
pip show -f ezkey-cli | grep THIRD-PARTY
```

## NPM Packages (JavaScript SDK)

### Configuration

License files are included via `package.json` `files` field.

### Files Included

- `THIRD-PARTY-LICENSES.txt` - JavaScript dependencies with licenses
- `LICENSE` - Ezkey MIT license
- `README.md` - Package documentation

### Verification

After publishing to NPM:

```bash
npm pack ezkey-javascript-sdk
tar -tzf ezkey-javascript-sdk-*.tgz | grep THIRD-PARTY
```

## Mobile App Bundles

### Configuration

License files are included in mobile app bundles and should be accessible in the app UI.

### Files Included

- `ezkey_mobile/THIRD-PARTY-LICENSES.txt` - All npm dependencies with licenses
- `ezkey_mobile/third-party-notices.txt` - Formatted third-party notices

### App Store Requirements

- **Google Play**: License attribution required in app
- **Apple App Store**: License attribution required in app

### Implementation Status

- ✅ License files generated
- ⏳ License screen in app UI (planned for next release)

## Docker Images

### Configuration

License metadata should be added via LABEL instructions in Dockerfile.

### Recommended Labels

```dockerfile
LABEL org.opencontainers.image.licenses="MIT"
LABEL org.ezkey.third-party-licenses="See THIRD-PARTY.txt in image"
```

### Status

- ⏳ Docker license labels (planned for next Docker build)

## Verification Checklist

### Pre-Release Checklist

- [ ] Maven JARs include `META-INF/licenses/THIRD-PARTY.txt`
- [ ] Python packages include `THIRD-PARTY-LICENSES.txt`
- [ ] NPM packages include `THIRD-PARTY-LICENSES.txt`
- [ ] Mobile app bundles include license files
- [ ] Docker images include license metadata (if applicable)
- [ ] All license files are up-to-date
- [ ] License attribution verified in all distributions

## Related Documents

- [License Management Guide](../LICENSES.md) - Complete license management documentation
- [Third-Party Inventory](third-party-inventory.md) - Dependency inventory
- [License Risk Assessment](license-risk-assessment.md) - Risk analysis
