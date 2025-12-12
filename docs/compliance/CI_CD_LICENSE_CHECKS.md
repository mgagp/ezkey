# CI/CD License Validation

**Last Updated**: 2025-12-11  
**Purpose**: Guide for integrating license checks into CI/CD pipeline

## Overview

License validation should be integrated into the CI/CD pipeline to ensure:
- License files are always up-to-date
- No incompatible licenses are added
- License changes are detected
- SOC 2 evidence is collected

## Recommended CI/CD Checks

### 1. License File Generation Check

**Purpose**: Verify license files are regenerated when dependencies change

**Maven**:
```bash
# Generate license file
mvn license:aggregate-add-third-party

# Check if file changed
git diff --exit-code THIRD-PARTY.txt
```

**npm (React Native)**:
```bash
cd ezkey_mobile
yarn license:generate
git diff --exit-code THIRD-PARTY-LICENSES.txt third-party-notices.txt
```

**Python**:
```bash
cd ezkey-cli-python
pip-licenses --format=plain --with-urls --with-description > THIRD-PARTY-LICENSES.txt
git diff --exit-code THIRD-PARTY-LICENSES.txt
```

**JavaScript SDK**:
```bash
cd ezkey-sdk/javascript
npm run license:generate
git diff --exit-code THIRD-PARTY-LICENSES.txt
```

### 2. GPL Dependency Detection

**Purpose**: Prevent incompatible GPL dependencies

**Maven**:
```bash
# Check for GPL dependencies (excluding Classpath Exception)
grep -i "GNU General Public License" THIRD-PARTY.txt | grep -v "Classpath Exception" && exit 1 || exit 0
```

**npm**:
```bash
license-checker --onlyAllow "MIT;Apache-2.0;BSD;ISC;EPL-1.0;EPL-2.0;LGPL-2.1"
```

### 3. License Change Detection

**Purpose**: Alert on license changes in dependency updates

**Implementation**: Compare license files between commits or branches

```bash
# Example: Check if licenses changed in PR
git diff origin/main...HEAD -- THIRD-PARTY.txt | grep -i "license" && echo "License change detected"
```

### 4. License File Freshness Check

**Purpose**: Ensure license files are not stale

**Check**: Verify license file modification time vs dependency file modification time

## GitHub Actions Workflow Example

Create `.github/workflows/license-check.yml`:

```yaml
name: License Validation

on:
  pull_request:
    paths:
      - '**/pom.xml'
      - '**/package.json'
      - '**/setup.py'
      - '**/requirements.txt'
  push:
    branches:
      - main

jobs:
  maven-licenses:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'
      - name: Generate Maven License File
        run: mvn license:aggregate-add-third-party -DskipTests
      - name: Check License File Up-to-Date
        run: |
          if [ -n "$(git diff THIRD-PARTY.txt)" ]; then
            echo "License file is out of date. Please run: mvn license:aggregate-add-third-party"
            exit 1
          fi
      - name: Check for GPL Dependencies
        run: |
          if grep -i "GNU General Public License" THIRD-PARTY.txt | grep -qv "Classpath Exception"; then
            echo "GPL dependencies detected (without Classpath Exception). This is not allowed."
            exit 1
          fi

  npm-licenses:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-node@v4
        with:
          node-version: '18'
      - name: Generate npm License Files
        working-directory: ezkey_mobile
        run: |
          yarn install
          yarn license:generate
      - name: Check License Files Up-to-Date
        working-directory: ezkey_mobile
        run: |
          if [ -n "$(git diff THIRD-PARTY-LICENSES.txt third-party-notices.txt)" ]; then
            echo "License files are out of date. Please run: yarn license:generate"
            exit 1
          fi
      - name: Check License Compatibility
        working-directory: ezkey_mobile
        run: |
          yarn license:check

  python-licenses:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-python@v5
        with:
          python-version: '3.11'
      - name: Install pip-licenses
        run: pip install pip-licenses
      - name: Generate Python License File
        working-directory: ezkey-cli-python
        run: |
          pip install -e .
          pip-licenses --format=plain --with-urls --with-description > THIRD-PARTY-LICENSES.txt
      - name: Check License File Up-to-Date
        working-directory: ezkey-cli-python
        run: |
          if [ -n "$(git diff THIRD-PARTY-LICENSES.txt)" ]; then
            echo "License file is out of date. Please regenerate."
            exit 1
          fi

  javascript-sdk-licenses:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-node@v4
        with:
          node-version: '18'
      - name: Generate JavaScript SDK License File
        working-directory: ezkey-sdk/javascript
        run: |
          npm install
          npm run license:generate
      - name: Check License File Up-to-Date
        working-directory: ezkey-sdk/javascript
        run: |
          if [ -n "$(git diff THIRD-PARTY-LICENSES.txt)" ]; then
            echo "License file is out of date. Please run: npm run license:generate"
            exit 1
          fi
```

## Pre-commit Hooks (Optional)

Create `.git/hooks/pre-commit` or use husky:

```bash
#!/bin/bash
# Check if dependency files changed
if git diff --cached --name-only | grep -E "(pom.xml|package.json|setup.py|requirements.txt)"; then
  echo "Dependency files changed. Regenerating license files..."
  mvn license:aggregate-add-third-party -DskipTests
  git add THIRD-PARTY.txt
fi
```

## SOC 2 Evidence Collection

### Automated Evidence Collection

1. **License File Generation**: Evidence that license files are maintained
2. **GPL Detection**: Evidence that incompatible licenses are prevented
3. **License Change Detection**: Evidence that license changes are monitored
4. **CI/CD Logs**: Evidence of license validation process

### Evidence Storage

- Store CI/CD logs as evidence
- Archive license files with each release
- Document license validation in release notes

## Integration with Existing CI/CD

### If Using GitHub Actions

Add license checks to existing workflows or create dedicated workflow.

### If Using Jenkins

Add license validation steps to build pipeline.

### If Using GitLab CI

Add license validation jobs to `.gitlab-ci.yml`.

## Monitoring and Alerts

### License Change Alerts

- Alert on license changes in dependency updates
- Notify maintainers of incompatible licenses
- Track license change history

### Regular Audits

- Quarterly automated license audits
- Annual comprehensive review
- License compliance reports

## Related Documents

- [License Management Procedure](procedures/license-management.md) - Detailed procedures
- [License Risk Assessment](license-risk-assessment.md) - Risk analysis
- [Distribution Licenses](DISTRIBUTION_LICENSES.md) - Distribution compliance
