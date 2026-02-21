# Security Policy

## Reporting a Vulnerability

The Ezkey project takes security seriously. We appreciate your efforts to responsibly disclose your findings and will make every effort to acknowledge your contributions.

### Where to Report

**Please DO NOT report security vulnerabilities through public GitHub issues.**

Instead, please report security vulnerabilities to:
- **Email**: security@ezkey.org
- **Alternative**: Direct message to [@mgagp](https://github.com/mgagp)

### What to Include

To help us better understand and resolve the issue, please include as much of the following information as possible:

1. **Type of vulnerability** (e.g., authentication bypass, SQL injection, XSS, etc.)
2. **Full paths of source file(s)** related to the manifestation of the issue
3. **Location of the affected source code** (tag/branch/commit or direct URL)
4. **Step-by-step instructions to reproduce the issue**
5. **Proof-of-concept or exploit code** (if possible)
6. **Impact of the issue**, including how an attacker might exploit it
7. **Any potential mitigations** you've identified

### Response Timeline

- **Initial Response**: Within 48 hours of report submission
- **Status Update**: Within 7 days with assessment of the issue
- **Resolution Target**:
  - Critical vulnerabilities: 7 days
  - High vulnerabilities: 30 days
  - Medium vulnerabilities: 60 days
  - Low vulnerabilities: 90 days

### Disclosure Policy

- **Coordinated Disclosure**: We follow coordinated vulnerability disclosure
- **Embargo Period**: We request a 90-day embargo period for critical vulnerabilities
- **Public Disclosure**: After remediation, we will:
  1. Release a security patch
  2. Publish a security advisory
  3. Credit the reporter (unless anonymity is requested)
  4. Update this document with CVE information (if applicable)

### Security Researcher Recognition

We believe in recognizing security researchers who help make Ezkey more secure:

- Public acknowledgment on our security page (if desired)
- Detailed description of the issue and fix in release notes
- CVE assignment for qualifying vulnerabilities

### Out of Scope

The following are considered out of scope for security reports:

- **Social Engineering**: Attacks that require social engineering
- **Physical Attacks**: Attacks requiring physical access to user devices
- **Third-Party Services**: Issues in third-party services (report to the service provider)
- **Denial of Service**: Generic DoS attacks without demonstrating significant impact
- **Automated Scanners**: Reports from automated scanners without manual verification
- **Already Known Issues**: Issues already listed in our security advisories or issue tracker

### Secure Development Practices

Ezkey follows secure development practices including:

- **Code Review**: All code changes require peer review
- **Automated Security Scanning**:
  - Dependency vulnerability scanning (Dependabot)
  - Static Application Security Testing (SAST)
  - Secret scanning
- **Cryptographic Standards**:
  - EC P-256 (secp256r1) with ECDSA-SHA256 for all digital signatures
  - SHA-256 for hashing
  - Industry-standard cryptographic libraries
- **Regular Updates**: Dependencies are regularly updated for security patches
- **Security Testing**: Regular security assessments and penetration testing

### Supported Versions

| Version | Supported          |
| ------- | ------------------ |
| 1.x.x   | :white_check_mark: |
| < 1.0   | :x:                |

**Note**: Only the latest stable release receives security updates. We strongly recommend always running the latest version.

### Security Features

Ezkey implements multiple layers of security:

1. **Authentication Security**
   - Cryptographic key-based authentication
   - One-time proof tokens to prevent replay attacks
   - Signature validation on all authentication attempts

2. **API Security**
   - Rate limiting to prevent abuse
   - IP-based access controls
   - Secure session management
   - JWT token authentication with rotation

3. **Data Protection**
   - Encryption in transit (TLS 1.2+)
   - Encryption at rest for sensitive data
   - Secure key storage and management
   - PII data minimization

4. **Audit & Monitoring**
   - Comprehensive audit logging
   - Security event monitoring
   - Anomaly detection
   - Real-time alerting

5. **Infrastructure Security**
   - Regular security updates
   - Network segmentation
   - Database access controls
   - Secure configuration management

### Compliance

Ezkey is working towards SOC2 compliance. See [docs/SOC2_PREPARATION.md](docs/SOC2_PREPARATION.md) for details on our compliance roadmap.

### Security Advisories

Published security advisories can be found at:
- https://github.com/mgagp/ezkey/security/advisories

### Contact

For non-security-related questions, please use:
- GitHub Issues: https://github.com/mgagp/ezkey/issues
- GitHub Discussions: https://github.com/mgagp/ezkey/discussions

For security concerns, always use: **security@ezkey.org**

---

**Last Updated**: 2025-10-12
**Policy Version**: 1.0

