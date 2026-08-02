# Third-Party Dependency Inventory

**SOC 2 Control**: CC9.1 - Third-Party Service Provider Management  
**Last Updated**: 2025-12-11  
**Owner**: Ezkey Project Team

## Overview

This document provides a comprehensive inventory of all third-party dependencies used across the Ezkey project. This inventory supports SOC 2 compliance by documenting all third-party service providers (software dependencies) that are integrated into the Ezkey system.

## Inventory Summary

| Ecosystem | Direct Dependencies | Transitive Dependencies | Total | License Files |
|-----------|---------------------|------------------------|-------|---------------|
| Maven (Java) | ~50 | ~143 | 193 | `THIRD-PARTY.txt` |
| npm (React Native) | 13 | ~900+ | ~950 | `ezkey_mobile/third-party-notices.txt` |
| Python (CLI) | 4 | ~10 | ~14 | `ezkey-cli-python/THIRD-PARTY-LICENSES.txt` |
| JavaScript SDK | 1 | ~50 | ~51 | `ezkey-sdk/javascript/THIRD-PARTY-LICENSES.txt` |
| Docker Base Images | 3 | N/A | 3 | Documented below |
| Gradle (Android) | 3 | N/A | 3 | Documented below |
| CocoaPods (iOS) | 1 | N/A | 1 | Documented below |
| Ruby Gems (Dev) | 2 | N/A | 2 | Documented below |

**Total Estimated Dependencies**: ~1,200+ (including transitive)

## Maven Dependencies (Java Backend)

**License File**: `THIRD-PARTY.txt` (root directory)  
**Generation**: `mvn license:aggregate-add-third-party`

### Primary Dependencies

- **Spring Boot 3.5.6** (Apache 2.0) - Application framework
- **Spring Framework** (Apache 2.0) - Core framework
- **Spring Data JPA** (Apache 2.0) - Data persistence
- **Spring Security** (Apache 2.0) - Security framework
- **PostgreSQL Driver** (BSD-2-Clause) - Database connectivity
- **MapStruct 1.6.3** (Apache 2.0) - Object mapping
- **SpringDoc OpenAPI 2.8.13** (Apache 2.0) - API documentation
- **ZXing 3.5.3** (Apache 2.0) - QR code generation
- **Bucket4j 8.15.0** (Apache 2.0) - Rate limiting
- **Caffeine 3.2.2** (Apache 2.0) - Caching
- **Google Tink 1.23.0** (Apache 2.0) - Cryptography and encrypted keyset handling
- **BouncyCastle** (Bouncy Castle License) - Cryptography

### License Distribution (Maven)

- **Apache 2.0**: ~85% of dependencies
- **MIT**: ~5% of dependencies
- **EPL 1.0/2.0**: ~5% of dependencies
- **LGPL 2.1**: ~2% (Checkstyle - dev tool)
- **BSD variants**: ~3% of dependencies
- **Other**: <1%

### Critical Notes

- **No GPL dependencies detected** - Ensures license compatibility
- **LGPL dependencies** (Checkstyle) are development tools only, not distributed
- **Eclipse Temurin** (GPLv2 + Classpath Exception) - Compatible with proprietary use

## npm Dependencies (React Native Mobile)

**License Files**: `ezkey_mobile/third-party-notices.txt`, `ezkey_mobile/app/data/thirdPartyLicenses.json`  
**Generation**: `yarn license:generate` and `yarn license:check`

### Primary Dependencies

- **React Native 0.76.0** (MIT)
- **React 18.3.1** (MIT)
- **@react-navigation/native 7.1.19** (MIT)
- **@tanstack/react-query 5.90.7** (MIT)
- **axios 1.13.2** (MIT)
- **react-native-vision-camera 4.2.3** (MIT)
- **react-native-keychain 10.0.0** (MIT)
- **zustand 5.0.8** (MIT)

### License Distribution (npm)

- **MIT**: ~85% of dependencies
- **Apache-2.0**: ~10% of dependencies
- **ISC**: ~3% of dependencies
- **BSD variants**: ~2% of dependencies

## Python Dependencies (CLI)

**License File**: `ezkey-cli-python/THIRD-PARTY-LICENSES.txt`  
**Generation**: `pip-licenses --format=plain --with-urls --with-description`

### Primary Dependencies

- **click 8.3.0** (BSD-3-Clause)
- **requests 2.32.5** (Apache 2.0)
- **colorama 0.4.6** (BSD)
- **pyyaml 6.0.3** (MIT)

## JavaScript SDK Dependencies

**License File**: `ezkey-sdk/javascript/THIRD-PARTY-LICENSES.txt`  
**Generation**: `npm run license:generate`

### Primary Dependencies

- **node-fetch 2.7.0** (MIT)

## Container and Build Infrastructure

### Docker Base Images

1. **eclipse-temurin:21-jre-alpine**
   - License: GPLv2 + Classpath Exception
   - Purpose: Runtime JRE for Java applications
   - Distribution: Included in Docker images

2. **maven:3.9-eclipse-temurin-21**
   - License: Apache 2.0 (Maven) + GPLv2 + CP Exception (Temurin)
   - Purpose: Build environment
   - Distribution: Build-time only, not distributed

3. **alpine** (base for temurin images)
   - License: Apache 2.0
   - Purpose: Minimal Linux distribution
   - Distribution: Included in Docker images

### Buildpacks

- **paketobuildpacks/builder-jammy-tiny**
  - License: Apache 2.0 (Cloud Native Buildpacks)
  - Purpose: Native image builds
  - Distribution: Build-time only

## Mobile Platform Dependencies

### Android Build Tools (Gradle)

1. **Android Gradle Plugin 8.13.0**
   - License: Apache 2.0
   - Purpose: Android build system
   - Distribution: Build-time only

2. **Kotlin Gradle Plugin 1.9.24**
   - License: Apache 2.0
   - Purpose: Kotlin compilation
   - Distribution: Build-time only

3. **React Native Gradle Plugin**
   - License: MIT
   - Purpose: React Native Android integration
   - Distribution: Build-time only

### iOS Dependencies (CocoaPods)

1. **GoogleMLKit/BarcodeScanning**
   - License: Apache 2.0
   - Purpose: QR code scanning on iOS
   - Distribution: Included in iOS app bundle

### Ruby Dependencies (Development Tools)

1. **cocoapods >= 1.13**
   - License: MIT
   - Purpose: iOS dependency management
   - Distribution: Dev tool only

2. **activesupport >= 6.1.7.5**
   - License: MIT
   - Purpose: CocoaPods dependency
   - Distribution: Dev tool only

## Development Tools (Not Distributed)

### Maven Plugins

- **Spotless Maven Plugin** (Apache 2.0) - Code formatting
- **Checkstyle Plugin** (LGPL 2.1) - Code quality
- **JaCoCo Plugin** (EPL 1.0) - Code coverage
- **License Maven Plugin** (Apache 2.0) - License management

### CI/CD Tools

- **GitHub Actions** - Various actions with their own licenses
- **Dependabot** - GitHub service

## License Compatibility Analysis

### Compatible Combinations

- ✅ MIT + Apache 2.0
- ✅ Apache 2.0 + EPL 1.0/2.0
- ✅ MIT + BSD variants
- ✅ GPLv2 + Classpath Exception + Proprietary (Eclipse Temurin)

### Incompatible Combinations (Avoided)

- ❌ GPL (without exception) + Proprietary
- ❌ AGPL + Proprietary

### Current Status

✅ **No incompatible license combinations detected**  
✅ **All licenses compatible with MIT project license**  
✅ **No GPL dependencies (except with Classpath Exception)**

## Risk Classification

### Low Risk Dependencies

- MIT licensed dependencies
- Apache 2.0 licensed dependencies
- Well-maintained projects with active communities
- Dependencies with clear license attribution

### Medium Risk Dependencies

- Dependencies with dual licenses (requires choice documentation)
- Dependencies with license changes in recent versions
- Dependencies with unclear license attribution

### High Risk Dependencies

- **None currently identified**

## Version Tracking

All dependencies are tracked in their respective dependency management files:

- **Maven**: `pom.xml` files (version properties in parent POM)
- **npm**: `package.json` files
- **Python**: `setup.py` and `requirements.txt`
- **Docker**: `Dockerfile` files
- **Gradle**: `build.gradle` files
- **CocoaPods**: `Podfile`

## Update Frequency

- **Maven dependencies**: Updated via Maven dependency management
- **npm dependencies**: Updated via `yarn upgrade` or `npm update`
- **Python dependencies**: Updated via `pip install --upgrade`
- **Docker images**: Updated via Dockerfile base image tags
- **License files**: Regenerated on each dependency update

## Audit Schedule

- **Quarterly**: Full dependency audit and license review
- **Monthly**: Review of new dependencies added
- **On Update**: License compatibility check before merging dependency updates
- **Annually**: Comprehensive legal review

## Related Documents

- `docs/compliance/license-risk-assessment.md` - Detailed risk assessment
- `docs/compliance/procedures/license-management.md` - Management procedures
- `docs/LICENSES.md` - User-facing license documentation
- `THIRD-PARTY.txt` - Complete Maven dependency list
- `ezkey_mobile/THIRD-PARTY-LICENSES.txt` - Mobile app dependencies
- `ezkey-cli-python/THIRD-PARTY-LICENSES.txt` - Python CLI dependencies
- `ezkey-sdk/javascript/THIRD-PARTY-LICENSES.txt` - JavaScript SDK dependencies

## Evidence for SOC 2 Audit

This document serves as evidence for:

- **CC9.1**: Third-party service provider identification and management
- **CC9.2**: Third-party risk assessment (license compatibility)
- **CC2.1**: Information quality (complete and accurate dependency inventory)

## Maintenance

- **Last Updated**: 2025-12-11
- **Next Review**: 2026-01-11 (Monthly)
- **Owner**: Ezkey Project Team
- **Review Process**: Update on each dependency change, full review quarterly
