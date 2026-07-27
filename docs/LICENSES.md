# License Management Guide

**Last Updated**: 2025-12-11  
**Purpose**: Comprehensive guide to license management in the Ezkey project

## Overview

Ezkey is licensed under the **MIT License**. This document explains how we manage third-party dependencies and their licenses, ensuring compliance with open source license obligations.

## Quick Reference

### License Files Location

| Component | License File | Location |
|-----------|-------------|----------|
| Maven (Java) | `THIRD-PARTY.txt` | Root directory |
| React Native Mobile | `THIRD-PARTY-LICENSES.txt` | `ezkey_mobile/` |
| Python CLI | `THIRD-PARTY-LICENSES.txt` | `ezkey-cli-python/` |
| JavaScript SDK | `THIRD-PARTY-LICENSES.txt` | `ezkey-sdk/javascript/` |

### Regenerating License Files

```bash
# Maven dependencies
mvn license:aggregate-add-third-party

# React Native mobile app
cd ezkey_mobile
yarn license:generate

# Python CLI
cd ezkey-cli-python
pip-licenses --format=plain --with-urls --with-description > THIRD-PARTY-LICENSES.txt

# JavaScript SDK
cd ezkey-sdk/javascript
npm run license:generate
```

## Ezkey License

Ezkey is licensed under the **MIT License**. See the [LICENSE](../LICENSE) file in the project root for the full license text.

## Third-Party Dependencies

Ezkey uses third-party open source software. All dependencies and their licenses are documented in the license files listed above.

### License Distribution Summary

**Maven Dependencies** (~193 dependencies):
- Apache 2.0: ~85%
- MIT: ~5%
- EPL 1.0/2.0: ~5%
- BSD variants: ~3%
- Other: ~2%

**npm Dependencies** (~950 dependencies):
- MIT: ~85%
- Apache 2.0: ~10%
- ISC: ~3%
- BSD variants: ~2%

**Python Dependencies** (~14 dependencies):
- MIT: ~40%
- Apache 2.0: ~30%
- BSD variants: ~30%

### License Compatibility

All third-party dependencies use licenses compatible with Ezkey's MIT license:
- ✅ MIT License
- ✅ Apache 2.0 License
- ✅ BSD Licenses (2-Clause, 3-Clause)
- ✅ Eclipse Public License (EPL) 1.0/2.0
- ✅ LGPL 2.1 (development tools only, not distributed)

**No GPL dependencies** (except with Classpath Exception for runtime components like Eclipse Temurin).

## Adding New Dependencies

### Before Adding a Dependency

1. **Check License Compatibility**
   - Verify the dependency's license is compatible with MIT
   - Review the [License Compatibility Matrix](#license-compatibility-matrix)
   - Avoid GPL licenses (unless with Classpath Exception)

2. **Review License Terms**
   - Read the license file
   - Understand attribution requirements
   - Check for any special conditions

### Adding the Dependency

1. **Add to Dependency File**
   - Maven: Add to appropriate `pom.xml`
   - npm: Add to `package.json`
   - Python: Add to `setup.py` or `requirements.txt`

2. **Regenerate License Files**
   - Run the appropriate license generation command (see [Quick Reference](#quick-reference))
   - Verify the dependency appears in the license file
   - Check license attribution is correct

3. **Update Documentation** (if significant)
   - Update `docs/compliance/third-party-inventory.md` if adding major dependency
   - Update risk assessment if new license type

### Pull Request Checklist

When adding or updating dependencies:

- [ ] License compatibility verified
- [ ] License files regenerated
- [ ] License attribution verified
- [ ] Documentation updated (if needed)
- [ ] No GPL dependencies (except with exception)

## License Compatibility Matrix

### Compatible Licenses ✅

| License | Compatible | Notes |
|---------|------------|-------|
| MIT | ✅ Yes | Most permissive, fully compatible |
| Apache 2.0 | ✅ Yes | Permissive, requires NOTICE file |
| BSD (2-Clause, 3-Clause) | ✅ Yes | Permissive, simple attribution |
| EPL 1.0/2.0 | ✅ Yes | Permissive, compatible with MIT |
| LGPL 2.1 | ✅ Yes | Allows linking, compatible via exception |
| ISC | ✅ Yes | Similar to MIT, very permissive |

### Incompatible Licenses ❌

| License | Compatible | Notes |
|---------|------------|-------|
| GPL (no exception) | ❌ No | Copyleft, would require GPL distribution |
| AGPL | ❌ No | Strong copyleft, incompatible |
| Proprietary | ❌ No | Not open source |

### Special Cases ⚠️

| License | Compatible | Notes |
|---------|------------|-------|
| GPLv2 + Classpath Exception | ✅ Yes | Exception allows use (Eclipse Temurin) |
| Dual-Licensed | ⚠️ Review | Document which license option is used |

## Attribution Requirements

### MIT License

**Requirement**: Include copyright notice and license text

**Status**: ✅ Compliant - Copyright notices included in license files

### Apache 2.0 License

**Requirement**: 
- Include Apache 2.0 license text
- Include NOTICE file with attribution

**Status**: ✅ Compliant - NOTICE file created, license text included

### BSD Licenses

**Requirement**: Include copyright notice

**Status**: ✅ Compliant - Copyright notices included in license files

### EPL 1.0/2.0

**Requirement**: Include license text

**Status**: ✅ Compliant - License text included in license files

## Distribution Compliance

### JAR Files (Maven)

License files are included in JAR distributions:
- `THIRD-PARTY.txt` in `META-INF/licenses/`
- NOTICE file for Apache 2.0 dependencies

### Mobile App Bundles

License attribution included in mobile app:
- License files in app bundle
- License screen in app UI (Settings > About > Licenses)
- App Store compliance

### Python Packages

License file included in package distribution:
- `THIRD-PARTY-LICENSES.txt` in package

### NPM Packages

License file included in NPM package:
- `THIRD-PARTY-LICENSES.txt` in package

### Docker Images

License metadata included in Docker images:
- LABEL instructions with license information
- License files in image

## Container and Build Infrastructure

### Docker Base Images

- **eclipse-temurin:21-jre-alpine**: GPLv2 + Classpath Exception (compatible)
- **maven:3.9-eclipse-temurin-21**: Apache 2.0 + GPLv2 + CP Exception (build-time only)
- **alpine**: Apache 2.0

### Build Tools

- **Buildpacks**: Apache 2.0 (build-time only)

### Mobile Platform Tools

- **Gradle Plugins**: Apache 2.0 / MIT (build-time only)
- **CocoaPods**: MIT (build-time only)
- **GoogleMLKit**: Apache 2.0 (included in iOS app)

## License Change Tracking

### Monitoring License Changes

- **Automated**: CI/CD pipeline detects license changes
- **Manual**: Review dependency changelogs before updates
- **Documentation**: License changes tracked in version control

### Handling License Changes

1. **Detect Change**: Automated or manual detection
2. **Verify Compatibility**: Check new license compatibility
3. **Update Documentation**: Update risk assessment if needed
4. **Plan Migration**: If incompatible, plan dependency replacement

## Regular Audits

### Quarterly Audits

- Complete dependency inventory review
- License compatibility verification
- License file accuracy check
- Documentation updates

### Annual Legal Review

- Comprehensive license review
- Legal compliance verification
- Risk assessment update
- Procedure review

## SOC 2 Compliance

License management supports SOC 2 compliance:

- **CC9.1**: Third-party service provider management (dependency inventory)
- **CC9.2**: Third-party risk assessment (license compatibility)
- **CC7.1**: Risk mitigation activities (license management process)
- **CC8.1**: Change management (license review in changes)
- **CC2.1**: Information quality (accurate license information)

See `docs/compliance/` for detailed SOC 2 documentation.

## Troubleshooting

### Missing License Information

If a dependency doesn't have license information:

1. **Maven**: Add to `src/license/THIRD-PARTY.properties`
2. **npm**: Manually document in license file
3. **Python**: Document in license file
4. **Contact**: Reach out to dependency maintainers

### License File Out of Date

If license files are out of date:

1. Regenerate using appropriate command
2. Review changes
3. Commit updated files
4. Verify in CI/CD

### Incompatible License Detected

If an incompatible license is detected:

1. **Do not merge** PR with incompatible license
2. **Find alternative** dependency with compatible license
3. **Document decision** if exception needed (rare, requires legal review)

## Related Documentation

- [Third-Party Dependency Inventory](compliance/third-party-inventory.md) - Complete dependency list
- [License Risk Assessment](compliance/license-risk-assessment.md) - Detailed risk analysis
- [License Management Procedure](compliance/procedures/license-management.md) - Detailed procedures
- [SOC 2 Preparation](../docs/SOC2_PREPARATION.md) - SOC 2 compliance roadmap

## FAQ

### Q: Can I add a GPL-licensed dependency?

**A**: No, GPL licenses are incompatible with our MIT license. The only exception is GPL with Classpath Exception (like Eclipse Temurin), which is compatible.

### Q: How often are license files updated?

**A**: License files are regenerated:
- On each dependency addition/update
- During build process (Maven)
- Quarterly during audits

### Q: What if a dependency changes its license?

**A**: Our CI/CD pipeline detects license changes. If a license becomes incompatible, we freeze the dependency version and plan migration to an alternative.

### Q: Are development tools included in license files?

**A**: Development tools (like Checkstyle, Spotless) are documented separately as they're not distributed. They're included in the compliance inventory but marked as "dev-only".

### Q: How do I verify license compatibility?

**A**: Check the [License Compatibility Matrix](#license-compatibility-matrix) above. If unsure, consult with project maintainers before adding the dependency.

## Contact

For questions about license management:
- **Issues**: Open a GitHub issue
- **Documentation**: See `docs/compliance/` for detailed procedures
- **Maintainers**: Contact project maintainers for urgent license questions

## Maintenance

- **Last Updated**: 2025-12-11
- **Next Review**: 2026-01-11 (Monthly)
- **Owner**: Ezkey Project Team
