# License Management Procedure

**SOC 2 Control**: CC7.1 - Risk Mitigation Activities  
**Last Updated**: 2025-12-11  
**Owner**: Ezkey Project Team

## Purpose

This procedure defines the process for managing third-party software licenses in the Ezkey project to ensure compliance with open source license obligations and support SOC 2 compliance requirements.

## Scope

This procedure applies to:
- All third-party dependencies (Maven, npm, Python, etc.)
- Docker base images and build tools
- Mobile platform dependencies (Gradle, CocoaPods)
- Development tools and CI/CD dependencies
- Any software component integrated into Ezkey

## Responsibilities

### Project Maintainers
- Review and approve new dependencies
- Ensure license compatibility
- Maintain license documentation
- Conduct regular license audits

### Developers
- Check license compatibility before adding dependencies
- Update license files when adding/updating dependencies
- Follow license review process

### Compliance Team
- Quarterly license audits
- Annual legal review
- SOC 2 evidence collection

## Process Overview

### 1. Adding a New Dependency

#### Step 1: License Compatibility Check

Before adding any dependency:

1. **Identify License Type**
   - Check dependency's license in package repository
   - Review license file if available
   - Verify license compatibility with MIT project license

2. **License Compatibility Matrix**
   - ✅ **Compatible**: MIT, Apache 2.0, BSD variants, EPL 1.0/2.0, LGPL (with linking exception)
   - ❌ **Incompatible**: GPL (without exception), AGPL
   - ⚠️ **Review Required**: Dual-licensed, unclear licenses

3. **Document Decision**
   - If compatible: Proceed to Step 2
   - If incompatible: Find alternative or document exception
   - If review required: Escalate to maintainers

#### Step 2: Add Dependency

1. **Add to Dependency File**
   - Maven: Add to appropriate `pom.xml`
   - npm: Add to `package.json`
   - Python: Add to `setup.py` or `requirements.txt`
   - Other: Follow ecosystem-specific process

2. **Update License Files**
   - Regenerate license files using appropriate tool:
     - Maven: `mvn license:aggregate-add-third-party`
     - npm: `yarn license:generate` or `npm run license:generate`
     - Python: `pip-licenses --format=plain > THIRD-PARTY-LICENSES.txt`
   - Review generated license file for accuracy

3. **Verify License Attribution**
   - Ensure dependency appears in license file
   - Verify license type is correctly identified
   - Check for any missing license information

#### Step 3: Update Documentation

1. **Update Inventory** (if significant dependency)
   - Update `docs/compliance/third-party-inventory.md`
   - Add to appropriate ecosystem section
   - Update dependency counts

2. **Update Risk Assessment** (if new license type)
   - Update `docs/compliance/license-risk-assessment.md`
   - Add risk assessment for new license type
   - Update compatibility matrix if needed

#### Step 4: Pull Request Review

1. **License Review Checklist**
   - [ ] License compatibility verified
   - [ ] License files updated
   - [ ] License attribution correct
   - [ ] Documentation updated (if needed)
   - [ ] No GPL dependencies (except with exception)

2. **Approval Required**
   - At least one maintainer approval
   - License compatibility confirmed
   - License files reviewed

### 2. Updating an Existing Dependency

#### Step 1: Check for License Changes

1. **Review License History**
   - Check if license changed in new version
   - Review changelog for license mentions
   - Verify license compatibility of new version

2. **License Change Detection**
   - Automated: CI/CD checks for license changes
   - Manual: Review dependency's license file
   - Alert: If license changed, follow review process

#### Step 2: Update Dependency

1. **Update Version**
   - Update version in dependency file
   - Test compatibility
   - Verify functionality

2. **Regenerate License Files**
   - Run license generation command
   - Review changes in license file
   - Verify license attribution still correct

#### Step 3: Document License Changes

1. **If License Changed**
   - Document change in PR description
   - Update risk assessment if needed
   - Verify compatibility with project license
   - Update license compatibility matrix if needed

2. **If License Unchanged**
   - No additional documentation required
   - License files automatically updated

### 3. Regular License Audits

#### Quarterly Audit Process

1. **Dependency Inventory Review**
   - Verify all dependencies documented
   - Check for missing license information
   - Review dependency counts and statistics

2. **License Compatibility Review**
   - Verify no incompatible licenses added
   - Review license compatibility matrix
   - Check for license changes in dependencies

3. **License File Verification**
   - Verify license files are up-to-date
   - Check for missing attributions
   - Verify license file accuracy

4. **Documentation Review**
   - Update inventory if needed
   - Update risk assessment if needed
   - Review and update procedures

#### Annual Legal Review

1. **Comprehensive Review**
   - Full dependency audit
   - Legal review of license compatibility
   - Review of license obligations
   - Verification of compliance

2. **Documentation**
   - Document review findings
   - Update procedures if needed
   - Archive audit reports

### 4. License File Generation

#### Maven Dependencies

**Command**: `mvn license:aggregate-add-third-party`

**Output**: `THIRD-PARTY.txt` (root directory)

**Frequency**: 
- On each dependency addition/update
- During build process (validate phase)
- Quarterly audit

**Verification**:
- Check file exists and is up-to-date
- Verify all dependencies listed
- Check license attribution accuracy

#### npm Dependencies (React Native)

**Command**: `yarn license:generate` or `npm run license:generate`

**Output**: 
- `ezkey_mobile/third-party-notices.txt`
- `ezkey_mobile/app/data/thirdPartyLicenses.json`

**Verification Command**:
- `yarn license:check`

**Frequency**:
- On each dependency addition/update
- Pre-build process
- Quarterly audit

#### Python Dependencies

**Command**: `pip-licenses --format=plain --with-urls --with-description > THIRD-PARTY-LICENSES.txt`

**Output**: `ezkey-cli-python/THIRD-PARTY-LICENSES.txt`

**Frequency**:
- On each dependency addition/update
- Quarterly audit

#### JavaScript SDK Dependencies

**Command**: `npm run license:generate`

**Output**: `ezkey-sdk/javascript/THIRD-PARTY-LICENSES.txt`

**Frequency**:
- On each dependency addition/update
- Quarterly audit

### 5. License Compatibility Verification

#### Automated Checks

1. **CI/CD Pipeline**
   - License file generation check
   - License change detection
   - GPL dependency detection
   - License file freshness check

2. **Pre-commit Hooks** (Optional)
   - License file update check
   - License compatibility check

#### Manual Checks

1. **Before Adding Dependency**
   - Check license type
   - Verify compatibility
   - Review license terms

2. **During PR Review**
   - Verify license compatibility
   - Check license files updated
   - Review license attribution

### 6. Handling License Issues

#### Incompatible License Detected

1. **Immediate Action**
   - Do not merge PR with incompatible license
   - Notify maintainers
   - Document issue

2. **Resolution Options**
   - Find alternative dependency with compatible license
   - Request license exception (rare, requires legal review)
   - Document decision if exception granted

#### Missing License Information

1. **Action**
   - Add to `src/license/THIRD-PARTY.properties` (Maven)
   - Manually document license
   - Contact dependency maintainers if needed

2. **Documentation**
   - Document manual license mapping
   - Include rationale for license determination
   - Update license file

#### License Change Detected

1. **Review Process**
   - Verify new license compatibility
   - Update risk assessment if needed
   - Update documentation

2. **If Incompatible**
   - Freeze dependency version if possible
   - Find alternative dependency
   - Plan migration strategy

### 7. Distribution Compliance

#### JAR Files (Maven)

1. **License Files**
   - Include `THIRD-PARTY.txt` in JAR
   - Place in `META-INF/licenses/` directory
   - Verify inclusion in build process

#### Mobile App Bundles

1. **License Attribution**
   - Include license files in app bundle
   - Add license screen in app UI (Settings > About > Licenses)
   - Comply with App Store requirements

#### Python Packages

1. **License Files**
   - Include `THIRD-PARTY-LICENSES.txt` in package
   - Verify inclusion in setup.py
   - Include in distribution

#### NPM Packages

1. **License Files**
   - Include `THIRD-PARTY-LICENSES.txt` in package
   - Verify inclusion in package.json
   - Include in NPM distribution

#### Docker Images

1. **License Metadata**
   - Add LABEL instructions with license information
   - Include license files in image
   - Document in image documentation

### 8. Emergency Procedures

#### License Violation Detected

1. **Immediate Actions**
   - Remove violating dependency if possible
   - Freeze dependency version
   - Notify project maintainers
   - Document violation

2. **Remediation**
   - Find alternative dependency
   - Update license files
   - Update documentation
   - Legal review if needed

#### Critical License Change

1. **Response**
   - Assess impact
   - Freeze dependency version
   - Plan migration
   - Update risk assessment

## Checklists

### Adding New Dependency Checklist

- [ ] License compatibility verified
- [ ] License type documented
- [ ] Dependency added to appropriate file
- [ ] License files regenerated
- [ ] License attribution verified
- [ ] Documentation updated (if needed)
- [ ] PR includes license review
- [ ] Maintainer approval obtained

### Updating Dependency Checklist

- [ ] License change checked
- [ ] Version updated
- [ ] License files regenerated
- [ ] Compatibility verified
- [ ] Documentation updated (if license changed)
- [ ] Tests passed

### Quarterly Audit Checklist

- [ ] All dependencies inventoried
- [ ] License files up-to-date
- [ ] No incompatible licenses
- [ ] License compatibility verified
- [ ] Documentation reviewed
- [ ] Audit report created

## Tools and Automation

### License Management Tools

1. **Maven**: License Maven Plugin
2. **npm**: license-checker, @rnx-kit/third-party-notices
3. **Python**: pip-licenses
4. **CI/CD**: Automated license checks

### Automation

1. **License File Generation**: Automated in build process
2. **License Change Detection**: CI/CD pipeline checks
3. **GPL Detection**: Automated alerts
4. **License File Freshness**: CI/CD validation

## Training and Awareness

### Developer Training

1. **License Basics**
   - Understanding open source licenses
   - License compatibility
   - Attribution requirements

2. **Process Training**
   - How to add dependencies
   - License review process
   - License file generation

### Documentation

1. **Quick Reference**: `docs/LICENSES.md`
2. **Detailed Procedures**: This document
3. **Compatibility Matrix**: `docs/compliance/license-risk-assessment.md`

## Related Documents

- `docs/compliance/third-party-inventory.md` - Dependency inventory
- `docs/compliance/license-risk-assessment.md` - Risk assessment
- `docs/LICENSES.md` - User-facing license documentation
- `THIRD-PARTY.txt` - Maven license file

## Evidence for SOC 2 Audit

This procedure serves as evidence for:

- **CC7.1**: Risk mitigation activities (license management process)
- **CC8.1**: Change management (license review in change process)
- **CC9.1**: Third-party management (dependency management process)
- **CC9.2**: Third-party risk assessment (license compatibility process)

## Maintenance

- **Last Updated**: 2025-12-11
- **Next Review**: 2026-01-11 (Monthly)
- **Owner**: Ezkey Project Team
- **Review Process**: Update based on process improvements, review quarterly
