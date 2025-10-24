# API Security Testing Guide - Ezkey Admin API

## Overview

This guide provides comprehensive instructions for testing the security implementation of the Ezkey Admin API. It covers both automated and manual testing approaches to validate access control, authentication, and authorization.

## Prerequisites

### 1. Server Setup
- Ezkey Admin API server running on `http://localhost:8080` (or your configured port)
- Database properly configured and migrated
- At least one integration and enrollment created for testing

### 2. Test Credentials
- **API Key**: A valid API key associated with an integration
- **Admin Token**: A valid admin authentication token
- **Test Integration ID**: The ID of an integration to use for testing

### 3. Tools Required
- **PowerShell** (for Windows automated testing)
- **curl** (for manual testing)
- **Postman** (optional, for GUI testing)

## Automated Testing

### PowerShell Script Testing

The `scripts/test-api-security.ps1` script provides comprehensive automated testing.

#### Basic Usage

```powershell
# Check if server is running
.\scripts\test-api-security.ps1 -CheckServerOnly

# Run all tests with credentials
.\scripts\test-api-security.ps1 -ApiKey "your_api_key" -AdminToken "your_admin_token"

# Run tests with custom server URL
.\scripts\test-api-security.ps1 -BaseUrl "http://localhost:9080" -ApiKey "your_api_key" -AdminToken "your_admin_token"
```

#### Test Categories

The automated script tests the following scenarios:

1. **Unauthorized Access**: Tests endpoints without authentication
2. **API Key Forbidden Access**: Tests API key access to restricted endpoints
3. **API Key Own Auth Attempts**: Tests API key access to allowed endpoints
4. **Admin Access**: Tests admin access to all endpoints (baseline)

#### Expected Results

- **Total Tests**: ~20 tests
- **All tests should pass** if security is properly implemented
- **Failed tests** indicate security issues that need to be addressed

## Manual Testing

### 1. Test API Key Authentication

#### Create an API Key
```bash
# Login as admin first
curl -X POST http://localhost:8080/api/v1/admin/login \
  -H "Content-Type: application/json" \
  -d '{"username": "admin", "password": "admin"}'

# Create API key for integration
curl -X POST http://localhost:8080/api/v1/api-keys \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_ADMIN_TOKEN" \
  -d '{"integrationId": 2, "description": "Test API Key"}'
```

#### Test API Key Access to Auth Attempts
```bash
# Should succeed - API key can create auth attempts
curl -X POST http://localhost:8080/api/v1/auth-attempts \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_API_KEY" \
  -d '{"enrollmentId": 1, "challengeRequired": false}'

# Should succeed - API key can list auth attempts
curl -X GET http://localhost:8080/api/v1/auth-attempts \
  -H "Authorization: Bearer YOUR_API_KEY"
```

#### Test API Key Forbidden Access
```bash
# Should fail with 403 - API key cannot access enrollments
curl -X GET http://localhost:8080/api/v1/enrollments \
  -H "Authorization: Bearer YOUR_API_KEY"

# Should fail with 403 - API key cannot create enrollments
curl -X POST http://localhost:8080/api/v1/enrollments \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_API_KEY" \
  -d '{"integrationId": 1, "enrollmentName": "test"}'

# Should fail with 403 - API key cannot access integrations
curl -X GET http://localhost:8080/api/v1/integrations \
  -H "Authorization: Bearer YOUR_API_KEY"

# Should fail with 403 - API key cannot access API key management
curl -X GET http://localhost:8080/api/v1/api-keys/integration/2 \
  -H "Authorization: Bearer YOUR_API_KEY"
```

### 2. Test Admin Access (Baseline)

```bash
# Should succeed - Admin can access all endpoints
curl -X GET http://localhost:8080/api/v1/enrollments \
  -H "Authorization: Bearer YOUR_ADMIN_TOKEN"

curl -X GET http://localhost:8080/api/v1/integrations \
  -H "Authorization: Bearer YOUR_ADMIN_TOKEN"

curl -X GET http://localhost:8080/api/v1/auth-attempts \
  -H "Authorization: Bearer YOUR_ADMIN_TOKEN"

curl -X GET http://localhost:8080/api/v1/api-keys/integration/2 \
  -H "Authorization: Bearer YOUR_ADMIN_TOKEN"
```

### 3. Test Unauthorized Access

```bash
# Should fail with 401 - No authentication
curl -X GET http://localhost:8080/api/v1/enrollments

# Should fail with 401 - Invalid token
curl -X GET http://localhost:8080/api/v1/enrollments \
  -H "Authorization: Bearer invalid_token"
```

## Test Scenarios by Endpoint

### Enrollment Endpoints

| Endpoint | Method | API Key | Admin | Unauthorized |
|----------|--------|---------|-------|--------------|
| `/api/v1/enrollments` | GET | ❌ 403 | ✅ 200 | ❌ 401 |
| `/api/v1/enrollments` | POST | ❌ 403 | ✅ 201 | ❌ 401 |
| `/api/v1/enrollments/{id}` | GET | ❌ 403 | ✅ 200 | ❌ 401 |
| `/api/v1/enrollments/{id}` | DELETE | ❌ 403 | ✅ 204 | ❌ 401 |

### Integration Endpoints

| Endpoint | Method | API Key | Admin | Unauthorized |
|----------|--------|---------|-------|--------------|
| `/api/v1/integrations` | GET | ❌ 403 | ✅ 200 | ❌ 401 |
| `/api/v1/integrations` | POST | ❌ 403 | ✅ 201 | ❌ 401 |
| `/api/v1/integrations/{id}` | GET | ❌ 403 | ✅ 200 | ❌ 401 |
| `/api/v1/integrations/{id}` | DELETE | ❌ 403 | ✅ 204 | ❌ 401 |

### Auth Attempt Endpoints

| Endpoint | Method | API Key | Admin | Unauthorized |
|----------|--------|---------|-------|--------------|
| `/api/v1/auth-attempts` | GET | ✅ 200 | ✅ 200 | ❌ 401 |
| `/api/v1/auth-attempts` | POST | ✅ 201 | ✅ 201 | ❌ 401 |
| `/api/v1/auth-attempts/{id}` | GET | ✅ 200* | ✅ 200 | ❌ 401 |
| `/api/v1/auth-attempts/{id}` | DELETE | ✅ 204* | ✅ 204 | ❌ 401 |
| `/api/v1/auth-attempts/{id}/wait` | GET | ✅ 200* | ✅ 200 | ❌ 401 |

*API key access requires ownership check (auth attempt must belong to API key's integration)

### API Key Management Endpoints

| Endpoint | Method | API Key | Admin | Unauthorized |
|----------|--------|---------|-------|--------------|
| `/api/v1/api-keys` | POST | ❌ 403 | ✅ 201 | ❌ 401 |
| `/api/v1/api-keys/integration/{id}` | GET | ❌ 403 | ✅ 200 | ❌ 401 |
| `/api/v1/api-keys/{id}` | GET | ❌ 403 | ✅ 200 | ❌ 401 |
| `/api/v1/api-keys/{id}` | DELETE | ❌ 403 | ✅ 204 | ❌ 401 |

### Audit Log Endpoints

| Endpoint | Method | API Key | Admin | Unauthorized |
|----------|--------|---------|-------|--------------|
| `/api/v1/audit-logs` | GET | ❌ 403 | ✅ 200 | ❌ 401 |

## Ownership Testing

### Test API Key Ownership Validation

1. **Create two integrations** with different IDs
2. **Create enrollments** for each integration
3. **Create auth attempts** for each enrollment
4. **Test API key access** to auth attempts from different integrations

```bash
# API key for integration 2 trying to access auth attempt from integration 3
# Should fail with 403
curl -X GET http://localhost:8080/api/v1/auth-attempts/20 \
  -H "Authorization: Bearer API_KEY_FOR_INTEGRATION_2"

# API key for integration 2 accessing auth attempt from integration 2
# Should succeed with 200
curl -X GET http://localhost:8080/api/v1/auth-attempts/10 \
  -H "Authorization: Bearer API_KEY_FOR_INTEGRATION_2"
```

## Error Response Validation

### Expected Error Responses

#### 401 Unauthorized
```json
{
  "timestamp": "2025-10-23T13:00:00Z",
  "status": 401,
  "error": "Unauthorized",
  "message": "Full authentication is required to access this resource",
  "path": "/api/v1/enrollments"
}
```

#### 403 Forbidden
```json
{
  "timestamp": "2025-10-23T13:00:00Z",
  "status": 403,
  "error": "Forbidden",
  "message": "Access denied: insufficient permissions",
  "path": "/api/v1/enrollments"
}
```

## Troubleshooting

### Common Issues

#### 1. Server Not Running
**Error**: Connection refused or timeout
**Solution**: Start the Ezkey Admin API server
```bash
mvn spring-boot:run
```

#### 2. Invalid Credentials
**Error**: 401 Unauthorized with valid-looking tokens
**Solution**: 
- Verify API key is active and not expired
- Check admin token is valid and not expired
- Ensure credentials are properly formatted

#### 3. Database Issues
**Error**: 500 Internal Server Error
**Solution**:
- Check database connection
- Verify database migrations are applied
- Check database logs for errors

#### 4. Port Conflicts
**Error**: Connection refused on expected port
**Solution**:
- Check if another service is using the port
- Verify the server is running on the expected port
- Use `-BaseUrl` parameter to specify correct URL

### Debug Mode

Enable debug logging for detailed security information:

```yaml
# application.yml
logging:
  level:
    org.ezkey.admin.security: DEBUG
    org.springframework.security: DEBUG
```

## Continuous Integration

### GitHub Actions Example

```yaml
name: API Security Tests
on: [push, pull_request]
jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v2
      - name: Set up JDK 21
        uses: actions/setup-java@v2
        with:
          java-version: '21'
      - name: Start server
        run: mvn spring-boot:run &
      - name: Wait for server
        run: sleep 30
      - name: Run security tests
        run: |
          # Get API key and admin token
          API_KEY=$(curl -s -X POST http://localhost:8080/api/v1/api-keys -H "Content-Type: application/json" -H "Authorization: Bearer $ADMIN_TOKEN" -d '{"integrationId": 1}' | jq -r '.secretKey')
          # Run tests
          ./scripts/test-api-security.sh -k "$API_KEY" -t "$ADMIN_TOKEN"
```

## Performance Testing

### Load Testing with API Keys

```bash
# Test API key rate limiting (if implemented)
for i in {1..100}; do
  curl -X GET http://localhost:8080/api/v1/auth-attempts \
    -H "Authorization: Bearer YOUR_API_KEY" &
done
wait
```

## Security Audit Checklist

- [ ] All endpoints properly protected with authentication
- [ ] API keys cannot access admin-only endpoints
- [ ] API keys can only access their own integration's resources
- [ ] Admin users have full access to all endpoints
- [ ] Unauthorized access returns proper 401 responses
- [ ] Forbidden access returns proper 403 responses
- [ ] Error messages don't leak sensitive information
- [ ] All security headers are properly set
- [ ] Rate limiting is implemented (if required)
- [ ] Audit logging captures security events

---

**Last Updated**: 2025-10-23  
**Version**: 1.0  
**Status**: Ready for Testing
