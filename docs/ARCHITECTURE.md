# Ezkey Architecture and Security Documentation

## Table of Contents
1. [Project Organization](#project-organization)
2. [Cryptographic Architecture](#cryptographic-architecture)
3. [API Security and Separation](#api-security-and-separation)
4. [Authentication Security Evolution](#authentication-security-evolution)
5. [Security Recommendations](#security-recommendations)

---

## Project Organization

### Current Structure
```
ezkey/
├── ezkey-core              # Core API business logic
├── ezkey-admin-api         # Admin API for organizations
├── ezkey-auth-api          # Authentication API for mobile devices
├── ezkey-mobile            # Mobile application
├── ezkey-demo-app-acme     # Demo web application (Acme)
├── ezkey-demo-device-sim   # Demo device simulator
├── ezkey-docs              # Centralized documentation
├── ezkey-sdk               # Software Development Kits
├── ezkey-cli               # Command Line Interface
└── ezkey-pam               # PAM module for Linux
```

### Module Responsibilities

#### Core Components
- **ezkey-core**: Shared business logic, entities, repositories, and services
- **ezkey-admin-api**: Organization-facing API for integration and enrollment management
- **ezkey-auth-api**: Mobile device API for authentication operations

#### Demo and Tools
- **ezkey-demo-app-acme**: Web application demonstrating Ezkey integration
- **ezkey-demo-device-sim**: Device simulator for testing
- **ezkey-sdk**: Multi-language SDKs (Java, Python, JavaScript, .NET)
- **ezkey-cli**: Command-line interface for system administration
- **ezkey-pam**: Linux PAM module for system authentication

---

## Cryptographic Architecture

### SignatureService Implementation

#### Current Features
- **Digital Signature**: RSA signature generation with SHA-256 (`SHA256withRSA`)
- **Signature Verification**: RSA signature validation with SHA-256
- **Key Formats**:
  - Private Key: PKCS#8, Base64 encoded
  - Public Key: X.509, Base64 encoded
- **Implementation**: Java standard library (`java.security`), no exotic dependencies

#### Security Analysis

| Criterion | Current Implementation | Best Practice | Status |
|-----------|----------------------|---------------|---------|
| Algorithm | RSA + SHA-256 | RSA-PSS or ECDSA | ✅ Good |
| Key Length | Configurable | ≥2048 bits | ⚠️ Document minimum |
| Private Key Format | PKCS#8 | PKCS#8 | ✅ Perfect |
| Public Key Format | X.509 | X.509 | ✅ Perfect |
| Encoding | Base64 | Base64 | ✅ Perfect |
| Padding | PKCS#1 v1.5 | PSS | ⚠️ PSS recommended |
| Hash | SHA-256 | SHA-256/SHA-512 | ✅ Good |
| Private Key Storage | Not handled | Vault/HSM/KMS | ❌ Externalize |
| Key Rotation | Not handled | Regular rotation | ❌ Plan needed |

#### Advantages
- **Simplicity**: Robust, standard, and interoperable
- **Compatibility**: Works with Java, Python, Go, Node.js, C#, etc.
- **Export Compliance**: No restrictions in most countries
- **Cloud Ready**: Compatible with most cloud infrastructures

#### Disadvantages
- **Performance**: RSA is slower than ECDSA for mobile/embedded devices
- **Security**: PKCS#1 v1.5 is less secure than PSS (but still acceptable)
- **Key Management**: No native support for secure storage or rotation
- **Modern Algorithms**: No support for elliptic curves

### Cryptographic Recommendations

#### Phase 1: Security and Documentation
- Document minimum key length (2048+ bits)
- Recommend secure key storage (Vault, HSM, KMS)
- Add error logging for critical failures
- Enforce UTF-8 encoding everywhere

#### Phase 2: Cryptographic Improvements
- Add `RSASSA-PSS` support (modern, more secure padding)
- Allow configurable signature algorithms (RSA-PSS, ECDSA)
- Add interoperability tests with other languages

#### Phase 3: Modernization
- Propose ECDSA (P-256, P-384) as alternative
- Provide multi-language SDK examples
- Implement automatic key rotation

---

## Authentication Security Evolution

### Current Implementation
- **Method**: `SELECT ... FOR NO KEY UPDATE` with row-level locking
- **Security Level**: Basic protection against race conditions
- **Status**: ✅ Implemented

### Security Enhancement Phases

#### Phase 1: Immediate Improvements (Current)
```sql
-- Current approach with SELECT FOR NO KEY UPDATE
SELECT * FROM auth_attempt 
WHERE enrollment_id = ? AND auth_attempt_read = false 
ORDER BY auth_attempt_id DESC 
LIMIT 1 
FOR NO KEY UPDATE
```

**Security Benefits:**
- ✅ Prevents race conditions
- ✅ Ensures exclusive access to auth attempts
- ✅ Row-level locking minimizes contention

#### Phase 2: Audit and Monitoring (Short-term)
```sql
-- Audit table for comprehensive tracking
CREATE TABLE auth_attempt_audit (
    id SERIAL PRIMARY KEY,
    auth_attempt_id INTEGER NOT NULL,
    action VARCHAR(50) NOT NULL, -- 'CLAIMED', 'READ', 'RESPONDED'
    device_id VARCHAR(100),
    ip_address INET,
    user_agent TEXT,
    claimed_at TIMESTAMP DEFAULT NOW(),
    created_at TIMESTAMP DEFAULT NOW()
);
```

#### Phase 3: Advanced Security Features (Medium-term)
```sql
-- Timeout mechanism for abandoned claims
ALTER TABLE auth_attempt ADD COLUMN claimed_at TIMESTAMP;
ALTER TABLE auth_attempt ADD COLUMN claimed_by VARCHAR(100);
ALTER TABLE auth_attempt ADD COLUMN claim_timeout_minutes INTEGER DEFAULT 5;

-- Performance index
CREATE INDEX idx_auth_attempt_unread_claimed 
ON auth_attempt(enrollment_id, auth_attempt_read, claimed_at) 
WHERE auth_attempt_read = false;
```

#### Phase 4: Zero-Trust Architecture (Long-term)
```sql
-- Device fingerprinting and anomaly detection
CREATE TABLE device_fingerprint (
    id SERIAL PRIMARY KEY,
    enrollment_id INTEGER NOT NULL,
    device_hash VARCHAR(255) NOT NULL,
    ip_range CIDR,
    user_agent_pattern TEXT,
    risk_score INTEGER DEFAULT 0,
    created_at TIMESTAMP DEFAULT NOW()
);
```

---

## Security Recommendations

### Immediate Actions (Current Sprint)
- ✅ Implement `SELECT ... FOR NO KEY UPDATE` (COMPLETED)
- ✅ Add double-check validation in service layer
- ✅ Ensure proper transaction boundaries

### Short-term Enhancements (Next 2-4 weeks)
- 🔄 Add comprehensive audit logging
- 🔄 Implement claim timeout mechanism
- 🔄 Add performance monitoring for lock contention
- 🔄 Create security metrics dashboard

### Medium-term Improvements (Next 1-2 months)
- 📋 Implement device fingerprinting
- 📋 Add rate limiting per enrollment
- 📋 Create anomaly detection system
- 📋 Implement automatic cleanup of abandoned claims

### Long-term Security Vision (Next 3-6 months)
- 🎯 Zero-trust architecture implementation
- 🎯 Advanced threat detection
- 🎯 Machine learning-based anomaly detection
- 🎯 Comprehensive security testing framework

### Security Metrics and Monitoring

#### Key Performance Indicators (KPIs)
```sql
-- Lock contention monitoring
SELECT 
    COUNT(*) as total_attempts,
    COUNT(CASE WHEN auth_attempt_read = true THEN 1 END) as read_attempts,
    COUNT(CASE WHEN claimed_at IS NOT NULL THEN 1 END) as claimed_attempts,
    AVG(EXTRACT(EPOCH FROM (claimed_at - created_at))) as avg_claim_time_seconds
FROM auth_attempt 
WHERE created_at > NOW() - INTERVAL '1 hour';

-- Security anomaly detection
SELECT 
    enrollment_id,
    COUNT(*) as attempts_last_hour,
    COUNT(DISTINCT ip_address) as unique_ips
FROM auth_attempt_audit 
WHERE claimed_at > NOW() - INTERVAL '1 hour'
GROUP BY enrollment_id 
HAVING COUNT(*) > 10 OR COUNT(DISTINCT ip_address) > 3;
```

#### Alerting Rules
- **High Priority**: Multiple failed attempts from same enrollment
- **Medium Priority**: Unusual IP patterns or device fingerprints
- **Low Priority**: Performance degradation in lock acquisition

---

## Conclusion

### Architecture Summary
- **Current State**: Robust, standard, and interoperable cryptographic implementation
- **Security Level**: Good foundation with room for enhancement
- **Scalability**: Physical API separation enables independent scaling
- **Community Alignment**: Modern open source architecture patterns

### Key Principles
1. **Security First**: Always prioritize security over convenience
2. **Interoperability**: Maintain compatibility across languages and platforms
3. **Simplicity**: Keep the architecture understandable and maintainable
4. **Community**: Design for open source contribution and adoption

### Next Steps
1. Complete API separation implementation
2. Implement comprehensive audit logging
3. Add advanced security features
4. Create security monitoring dashboard
5. Plan for zero-trust architecture evolution

---

## 📖 Related Documentation

- **[Development Guide](DEVELOPMENT.md)** - Development workflow and testing strategy
- **[API Endpoints](ENDPOINT.md)** - Complete API documentation
- **[Cryptographic Implementation](CRYPTO.md)** - Detailed crypto specifications
- **[Main Project README](../README.md)** - Project overview and quick start
- **[Monitoring Setup](monitoring/README.md)** - Production monitoring guide

---

*This document consolidates the cryptographic analysis, security planning, and project organization for the Ezkey project. It serves as the definitive guide for architecture decisions and security implementations.*
