# API Security Matrix - Ezkey Admin API

## Overview

This document provides a comprehensive security matrix for all endpoints in the Ezkey Admin API, detailing the required roles and permissions for each operation. This matrix serves as the source of truth for access control validation and testing.

## Security Roles

| Role | Description | Scope |
|------|-------------|-------|
| `ROLE_ADMIN` | Full administrative access | Global - all endpoints |
| `ROLE_API_KEY` | Machine-to-machine authentication | Integration-scoped - auth attempts only |

## Endpoint Security Matrix

### Authentication Endpoints

| Endpoint | Method | Required Role | Description | API Key Access |
|----------|--------|---------------|-------------|----------------|
| `/api/v1/admin/login` | POST | None | Admin login | ❌ No |
| `/api/v1/admin/logout` | POST | `ROLE_ADMIN` | Admin logout | ❌ No |

### Integration Management

| Endpoint | Method | Required Role | Description | API Key Access |
|----------|--------|---------------|-------------|----------------|
| `/api/v1/integrations` | GET | `ROLE_ADMIN` | List all integrations | ❌ No |
| `/api/v1/integrations/{id}` | GET | `ROLE_ADMIN` | Get integration by ID | ❌ No |
| `/api/v1/integrations` | POST | `ROLE_ADMIN` | Create new integration | ❌ No |
| `/api/v1/integrations/{id}` | DELETE | `ROLE_ADMIN` | Delete integration | ❌ No |

### Enrollment Management

| Endpoint | Method | Required Role | Description | API Key Access |
|----------|--------|---------------|-------------|----------------|
| `/api/v1/enrollments` | GET | `ROLE_ADMIN` | List all enrollments | ❌ No |
| `/api/v1/enrollments/{id}` | GET | `ROLE_ADMIN` | Get enrollment by ID | ❌ No |
| `/api/v1/enrollments` | POST | `ROLE_ADMIN` | Create new enrollment | ❌ No |
| `/api/v1/enrollments/{id}` | DELETE | `ROLE_ADMIN` | Delete enrollment | ❌ No |

### Authentication Attempt Management

| Endpoint | Method | Required Role | Description | API Key Access | Rate Limit |
|----------|--------|---------------|-------------|----------------|------------|
| `/api/v1/auth-attempts` | GET | `ROLE_ADMIN` | List auth attempts | ❌ No | N/A |
| `/api/v1/auth-attempts/{id}` | GET | `ROLE_ADMIN` or `ROLE_API_KEY` | Get auth attempt by ID | ✅ Yes (ownership check) | N/A |
| `/api/v1/auth-attempts` | POST | `ROLE_ADMIN` or `ROLE_API_KEY` | Create new auth attempt | ✅ Yes | ✅ 100/15min |
| `/api/v1/auth-attempts/{id}/wait` | GET | `ROLE_ADMIN` or `ROLE_API_KEY` | Wait for auth response | ✅ Yes (ownership check) | ✅ 200/15min |

### API Key Management

| Endpoint | Method | Required Role | Description | API Key Access |
|----------|--------|---------------|-------------|----------------|
| `/api/v1/api-keys` | POST | `ROLE_ADMIN` | Create new API key | ❌ No |
| `/api/v1/api-keys/integration/{integrationId}` | GET | `ROLE_ADMIN` | List API keys for integration | ❌ No |
| `/api/v1/api-keys/{keyId}` | GET | `ROLE_ADMIN` | Get API key details | ❌ No |
| `/api/v1/api-keys/{keyId}` | DELETE | `ROLE_ADMIN` | Revoke API key | ❌ No |

### Audit Log Management

| Endpoint | Method | Required Role | Description | API Key Access |
|----------|--------|---------------|-------------|----------------|
| `/api/v1/audit-logs` | GET | `ROLE_ADMIN` | Query audit logs | ❌ No |

## Access Control Rules

### For API Keys (`ROLE_API_KEY`)

**Allowed Operations:**
- ✅ Create authentication attempts (with rate limiting: 100/15min, **own integration only**)
- ✅ Read authentication attempts by ID (own integration only)
- ✅ Wait for authentication responses (own integration only, with rate limiting: 200/15min)

**Forbidden Operations:**
- ❌ List all authentication attempts (GET /auth-attempts)
- ❌ Delete authentication attempts
- ❌ All enrollment management
- ❌ All integration management
- ❌ All API key management
- ❌ All audit log access
- ❌ Admin authentication operations

**Ownership Rules:**
- API keys can only access auth attempts belonging to their associated integration
- **API keys can only CREATE auth attempts for enrollments belonging to their integration**
- Ownership is determined by: `auth_attempt.enrollment.integration_id == api_key.integration_id`

**Rate Limiting Rules:**
- **Create Auth Attempt:** 100 operations per 15-minute window
- **Wait Auth Attempt:** 200 operations per 15-minute window
- **Rate Limit Exceeded:** Returns HTTP 429 Too Many Requests
- **Rate Limit Reset:** Every 15 minutes (sliding window)

### For Admins (`ROLE_ADMIN`)

**Allowed Operations:**
- ✅ All operations on all endpoints
- ✅ No ownership restrictions
- ✅ Full administrative access

## Test Scenarios

### Scenario 1: API Key Access to Auth Attempts (Own Integration)

**Setup:**
- API Key associated with Integration ID: 2
- Auth Attempt ID: 10 belongs to Enrollment with Integration ID: 2

**Tests:**
```bash
# ✅ Should succeed
GET /api/v1/auth-attempts/10
POST /api/v1/auth-attempts
GET /api/v1/auth-attempts/10/wait
```

**Expected Results:** HTTP 200/201/204

### Scenario 2: API Key Access to Auth Attempts (Other Integration)

**Setup:**
- API Key associated with Integration ID: 2
- Auth Attempt ID: 20 belongs to Enrollment with Integration ID: 3

**Tests:**
```bash
# ❌ Should fail with 403
GET /api/v1/auth-attempts/20
GET /api/v1/auth-attempts/20/wait
```

**Expected Results:** HTTP 403 Forbidden

### Scenario 2.5: API Key Create Auth Attempt (Other Integration)

**Setup:**
- API Key associated with Integration ID: 2
- Enrollment ID: 100 belongs to Integration ID: 3

**Tests:**
```bash
# ❌ Should fail with 403 - API key cannot create auth attempts for other integrations
POST /api/v1/auth-attempts
Authorization: Bearer API_KEY_FOR_INTEGRATION_2
Content-Type: application/json
{
  "enrollmentId": 100,
  "challengeRequested": false
}
```

**Expected Results:** HTTP 403 Forbidden

### Scenario 3: API Key Access to Forbidden Endpoints

**Setup:**
- API Key with `ROLE_API_KEY`

**Tests:**
```bash
# ❌ Should fail with 403
GET /api/v1/enrollments
POST /api/v1/enrollments
GET /api/v1/integrations
POST /api/v1/integrations
GET /api/v1/api-keys/integration/2
POST /api/v1/api-keys
GET /api/v1/audit-logs
```

**Expected Results:** HTTP 403 Forbidden

### Scenario 4: Admin Access (Baseline)

**Setup:**
- Admin with `ROLE_ADMIN`

**Tests:**
```bash
# ✅ Should succeed on all endpoints
GET /api/v1/enrollments
POST /api/v1/enrollments
GET /api/v1/integrations
POST /api/v1/integrations
GET /api/v1/auth-attempts
POST /api/v1/auth-attempts
GET /api/v1/api-keys/integration/2
POST /api/v1/api-keys
GET /api/v1/audit-logs
```

**Expected Results:** HTTP 200/201/204 (success)

## Design Questions & Challenges

### 1. Auth Attempt List Endpoint (`GET /api/v1/auth-attempts`)

**Current Design:** API keys can access with integration filtering
**Question:** Should API keys see ALL auth attempts for their integration, or only their own created attempts?

**Options:**
- **Option A:** API keys see all auth attempts for their integration (current)
- **Option B:** API keys only see auth attempts they created
- **Option C:** API keys see all auth attempts for their integration, but with filtering

**Recommendation:** Option A - API keys see all auth attempts for their integration for operational visibility.

### 2. Auth Attempt Creation (`POST /api/v1/auth-attempts`)

**Current Design:** API keys can create auth attempts
**Question:** Should there be any restrictions on auth attempt creation?

**Options:**
- **Option A:** No restrictions (current)
- **Option B:** Rate limiting per API key
- **Option C:** Require enrollment to be active/verified

**Recommendation:** Option A with future consideration for rate limiting.

### 3. Audit Trail for API Key Operations

**Current Design:** No audit logging for API key operations
**Question:** Should API key operations be audited?

**Options:**
- **Option A:** No audit logging (current)
- **Option B:** Basic audit logging (operation + timestamp)
- **Option C:** Full audit logging (operation + details + IP)

**Recommendation:** Option B - Basic audit logging for security monitoring.

### 4. Error Messages for API Keys

**Current Design:** Generic "Access denied" messages
**Question:** Should API keys get more specific error messages?

**Options:**
- **Option A:** Generic messages (current - security)
- **Option B:** Specific messages (better UX)
- **Option C:** Contextual messages (best UX, security risk)

**Recommendation:** Option A - Generic messages for security.

## Implementation Status

| Component | Status | Notes |
|-----------|--------|-------|
| Role Assignment | ✅ Complete | `ROLE_API_KEY` implemented |
| Method Security | ✅ Complete | `@EnableMethodSecurity` active |
| Access Control Service | ✅ Complete | Ownership checks implemented |
| Enrollment Controller | ✅ Complete | Admin-only access |
| Auth Attempt Controller | ✅ Complete | API key access with ownership |
| Integration Controller | ✅ Complete | Admin-only access |
| API Key Controller | ✅ Complete | Admin-only access |
| Audit Log Controller | ✅ Complete | Admin-only access |
| Exception Handling | ✅ Complete | `AuthorizationDeniedException` handled |
| Unit Tests | ✅ Complete | `AccessControlServiceTest` implemented |
| Integration Tests | ⚠️ Partial | Basic tests, need comprehensive scenarios |

## Next Steps

1. **Comprehensive Testing:** Implement all test scenarios above
2. **Documentation Updates:** Update API documentation with security requirements
3. **Monitoring:** Add audit logging for API key operations
4. **Rate Limiting:** Consider implementing rate limits for API keys
5. **Error Handling:** Review and standardize error responses

## Testing Commands

### Manual Testing with curl

```bash
# Test API key authentication
curl -H "Authorization: Bearer YOUR_API_KEY" \
     -H "Content-Type: application/json" \
     http://localhost:8080/api/v1/auth-attempts

# Test admin authentication
curl -H "Authorization: Bearer YOUR_ADMIN_TOKEN" \
     -H "Content-Type: application/json" \
     http://localhost:8080/api/v1/enrollments

# Test forbidden access
curl -H "Authorization: Bearer YOUR_API_KEY" \
     -H "Content-Type: application/json" \
     http://localhost:8080/api/v1/enrollments
```

### Automated Testing

```bash
# Run all tests
mvn test

# Run specific test classes
mvn test -Dtest=AccessControlServiceTest
mvn test -Dtest=ApiKeyControllerTest
```

---

**Last Updated:** 2025-10-23
**Version:** 1.0
**Status:** Implementation Complete, Testing In Progress
