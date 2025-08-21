# API Separation and Security Plan - Ezkey Project

## 1. Problem Statement

Currently, Ezkey's REST APIs are monolithic:
- Integration, enrollment, and authentication endpoints are accessible to all (organization and mobile).
- Risk: Mobile applications could access endpoints reserved for the organization (e.g., integration creation, enrollment creation, etc.).

**Objective:** Separate and secure API flows for:
- Organization applications (admin, integration, enrollment/auth attempt creation)
- Mobile applications (enrollment consumption/completion, authentication validation)

---

## 2. Possible Approaches

### A. Logical Separation (within the same Spring Boot project)
- **Principle:**
  - Create distinct controllers:
    - `AdminIntegrationController`, `AdminEnrollmentController`, `AdminAuthAttemptController` (for organization)
    - `MobileEnrollmentController`, `MobileAuthAttemptController` (for mobile)
  - Use URL prefixes (`/api/v1/admin/*` vs `/api/v1/mobile/*`)
  - Secure each API group with Spring filters/security (JWT, roles, etc.)

- **Advantages:**
  - Single deployment, easier to maintain initially
  - Shared business logic and database
  - Easy to refactor if extraction is needed later

- **Disadvantages:**
  - Larger attack surface (a config bug can expose an admin endpoint)
  - Less scalable long-term (difficulty separating lifecycles, logs, monitoring)
  - Risk of confusion in dependencies and access management

---

### B. Physical Separation (multiple Spring Boot apps)
- **Principle:**
  - Create two distinct Spring Boot applications:
    - `ezkey-admin-api`: endpoints for organization (integration, enrollment creation, auth attempt creation)
    - `ezkey-authentication-api`: endpoints for mobile (enrollment consumption/completion, authentication validation)
  - Each app has its own security config, controllers, and potentially its own database (or shared schema)

- **Advantages:**
  - Strong isolation: impossible for mobile to access admin endpoints
  - Enhanced security (reduced attack surface, possible network isolation)
  - Independent deployment, scaling, monitoring
  - Aligned with modern architectures (microservices, hexagonal)
  - Easier to open to community (contributors can work on a subset)

- **Disadvantages:**
  - More maintenance (2 projects, 2 configs, 2 CI/CD pipelines)
  - Requires inter-app communication management if needed (e.g., events, messages)
  - Longer initial migration

---

### C. Hybrid Separation (mono-repo, multi-app)
- **Principle:**
  - Single repository (e.g., `ezkey/ezkey`), but multiple Spring Boot modules/applications (`admin-api`, `authentication-api`)
  - Shared business logic in shared modules (`ezkey-core`)

- **Advantages:**
  - Benefits from physical separation while maintaining mono-repo simplicity
  - Shared business logic, DTOs, mappers, etc.
  - Easy to test and version

- **Disadvantages:**
  - Build complexity (multi-module Maven/Gradle)
  - Can become a heavy mono-repo with many modules

---

## 3. API Security

- **Admin API:**
  - Strong authentication (JWT, OAuth2, API Key, mutual TLS)
  - Role and permission management (Spring Security, RBAC)
  - IP or VPN access limitation (optional)

- **Mobile API:**
  - Device ID, JWT, or OAuth2 authentication
  - Strict limitation of accessible endpoints
  - Server-side token validation

- **Best practices:**
  - Always validate inputs server-side
  - Log sensitive access
  - Version APIs (`/api/v1/`)
  - Document authentication and authorization flows

---

## 4. Authentication Attempt Security Evolution

### Current Implementation
- **Method:** `SELECT ... FOR NO KEY UPDATE` with row-level locking
- **Security Level:** Basic protection against race conditions
- **Vulnerabilities:** Limited audit trail, no comprehensive monitoring

### Recommended Security Enhancements

#### Phase 1: Immediate Improvements (Current Implementation)
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

-- Enhanced locking with audit
WITH claimed_attempt AS (
    UPDATE auth_attempt 
    SET auth_attempt_read = true,
        claimed_at = NOW(),
        claimed_by = ?
    WHERE auth_attempt_id = (
        SELECT auth_attempt_id 
        FROM auth_attempt 
        WHERE enrollment_id = ? 
          AND auth_attempt_read = false 
          AND (claimed_at IS NULL OR claimed_at < NOW() - INTERVAL '5 minutes')
        ORDER BY auth_attempt_id DESC 
        LIMIT 1
    ) AND auth_attempt_read = false
    RETURNING *
)
SELECT * FROM claimed_attempt;
```

#### Phase 3: Advanced Security Features (Medium-term)
```sql
-- Timeout mechanism for abandoned claims
ALTER TABLE auth_attempt ADD COLUMN claimed_at TIMESTAMP;
ALTER TABLE auth_attempt ADD COLUMN claimed_by VARCHAR(100);
ALTER TABLE auth_attempt ADD COLUMN claim_timeout_minutes INTEGER DEFAULT 5;

-- Index for performance
CREATE INDEX idx_auth_attempt_unread_claimed 
ON auth_attempt(enrollment_id, auth_attempt_read, claimed_at) 
WHERE auth_attempt_read = false;

-- Cleanup job for abandoned claims
DELETE FROM auth_attempt 
WHERE auth_attempt_read = false 
  AND claimed_at < NOW() - INTERVAL '5 minutes';
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

-- Rate limiting and suspicious activity detection
CREATE TABLE auth_attempt_rate_limit (
    id SERIAL PRIMARY KEY,
    enrollment_id INTEGER NOT NULL,
    attempt_count INTEGER DEFAULT 0,
    window_start TIMESTAMP DEFAULT NOW(),
    blocked_until TIMESTAMP
);
```

### Security Recommendations

#### 1. Immediate Actions (Current Sprint)
- ✅ Implement `SELECT ... FOR NO KEY UPDATE` (COMPLETED)
- ✅ Add double-check validation in service layer
- ✅ Ensure proper transaction boundaries

#### 2. Short-term Enhancements (Next 2-4 weeks)
- 🔄 Add comprehensive audit logging
- 🔄 Implement claim timeout mechanism
- 🔄 Add performance monitoring for lock contention
- 🔄 Create security metrics dashboard

#### 3. Medium-term Improvements (Next 1-2 months)
- 📋 Implement device fingerprinting
- 📋 Add rate limiting per enrollment
- 📋 Create anomaly detection system
- 📋 Implement automatic cleanup of abandoned claims

#### 4. Long-term Security Vision (Next 3-6 months)
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
- **High Priority:** Multiple failed attempts from same enrollment
- **Medium Priority:** Unusual IP patterns or device fingerprints
- **Low Priority:** Performance degradation in lock acquisition

---

## 5. Open Source Community Alignment

- **What's most expected in the community:**
  - **Physical separation** (option B or C):
    - This is the norm in modern open source projects (Keycloak, Argo, Supabase, etc.)
    - Facilitates contribution, security, and scalability
    - Allows independent lifecycles
  - **Expected naming:**
    - `ezkey-api-admin` or `ezkey-admin-api` for admin
    - `ezkey-api-authentication` or `ezkey-authentication-api` for mobile
    - `ezkey-core` for shared logic

---

## 6. Summary

| Approach         | Security | Simplicity | Scalability | Community | Recommended |
|------------------|----------|------------|-------------|-----------|-------------|
| Logical (A)      | Medium   | Easy       | Limited     | Medium    | No          |
| Physical (B)     | Strong   | Medium     | Excellent   | Excellent | Yes         |
| Hybrid (C)       | Strong   | Good       | Excellent   | Excellent | Yes         |

**Conclusion:**
- For a modern open source project, physical separation (B) or hybrid (C) is most expected and secure.
- Starting with a mono-repo multi-app (C) is often the best compromise: easy to migrate, test, and open to community.
- Always apply strong authentication and strict access controls on admin APIs.
- Implement comprehensive audit and monitoring for authentication attempts.

---

**Next Steps:**
- Split existing controllers according to flows (admin vs mobile)
- Create two Spring Boot modules/applications
- Implement security appropriate for each flow
- Document endpoints and authentication flows
- Implement audit and monitoring for authentication attempts
