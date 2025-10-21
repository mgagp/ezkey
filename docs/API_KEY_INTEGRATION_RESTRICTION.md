# API Key Integration Restrictions

## Overview

Ezkey API keys are designed for machine-to-machine (M2M) authentication and have specific restrictions to ensure proper security boundaries between automated systems and administrative operations.

## Key Principle

**API keys are only permitted to manage authentication attempts for their associated integration. API keys are not permitted to create, read, update, or delete enrollments. Use bearer tokens (admin) for enrollment management.**

## Authentication Methods

### API Keys (Machine-to-Machine)

- **Purpose**: Backend server integrations, automation, CI/CD pipelines
- **Authentication**: HTTP Basic Auth with integration key and secret key
- **Format**: `Authorization: Basic base64(ezkey_ikey_xxx:ezkey_skey_xxx)`
- **Principal**: `integration_{id}`
- **Role**: `ROLE_ADMIN` (with restricted access)

### Bearer Tokens (Human Administrators)

- **Purpose**: Admin console, interactive sessions, full administrative control
- **Authentication**: Bearer token from admin login
- **Format**: `Authorization: Bearer ezkey_admin_token...`
- **Principal**: Admin email address
- **Role**: `ROLE_ADMIN` (with full access)

## Access Control Matrix

| Operation | API Key | Bearer Token |
|-----------|---------|--------------|
| **Authentication Attempts** | | |
| Create auth attempt | ✅ Allowed | ✅ Allowed |
| Read auth attempt | ✅ Allowed | ✅ Allowed |
| Wait for auth attempt | ✅ Allowed | ✅ Allowed |
| Delete auth attempt | ✅ Allowed | ✅ Allowed |
| **Enrollments** | | |
| Create enrollment | ❌ Forbidden | ✅ Allowed |
| Read enrollment | ❌ Forbidden | ✅ Allowed |
| Update enrollment | ❌ Forbidden | ✅ Allowed |
| Delete enrollment | ❌ Forbidden | ✅ Allowed |
| **Integrations** | | |
| Create integration | ❌ Forbidden | ✅ Allowed |
| Read integration | ❌ Forbidden | ✅ Allowed |
| Update integration | ❌ Forbidden | ✅ Allowed |
| Delete integration | ❌ Forbidden | ✅ Allowed |
| **API Keys** | | |
| Create API key | ❌ Forbidden | ✅ Allowed |
| Read API key | ❌ Forbidden | ✅ Allowed |
| Revoke API key | ❌ Forbidden | ✅ Allowed |

## Rationale

### Why Restrict Enrollment Management?

1. **Security Principle**: Enrollments represent device-to-user bindings and should only be managed by human administrators who can verify user identity.

2. **Workflow Separation**: 
   - **Enrollment**: Human administrators provision user devices (bearer token required)
   - **Authentication**: Automated systems verify user authentication (API key allowed)

3. **Prevent Abuse**: API keys are designed for high-volume authentication operations. Allowing enrollment creation could enable automated account takeover scenarios.

4. **Clear Responsibility**: Human oversight is required for device enrollment to prevent unauthorized device registrations.

## Implementation Details

### Detection Mechanism

The `IntegrationAccessControl` utility class provides the `isApiKey(Authentication)` method to detect API key authentication:

```java
Authentication auth = SecurityContextHolder.getContext().getAuthentication();
if (accessControl.isApiKey(auth)) {
    throw new ResponseStatusException(HttpStatus.FORBIDDEN, 
        "API keys cannot manage enrollments");
}
```

### Detection Logic

1. Check if authentication exists and is authenticated
2. Verify principal starts with `integration_` prefix
3. Confirm `ROLE_ADMIN` authority is present

### HTTP Response

When an API key attempts to access an enrollment endpoint:

- **Status Code**: `403 Forbidden`
- **Error Message**: `"API keys cannot manage enrollments"`

## Usage Examples

### ✅ Correct: Using API Key for Auth Attempts

```bash
# Create authentication attempt (ALLOWED)
curl -X POST http://localhost:9080/api/v1/auth-attempts \
  -u "ezkey_ikey_xxx:ezkey_skey_xxx" \
  -H "Content-Type: application/json" \
  -d '{
    "enrollmentId": 123,
    "challengeRequired": true,
    "challengeText": "Login to Admin Portal"
  }'

# Wait for authentication completion (ALLOWED)
curl -X GET http://localhost:9080/api/v1/auth-attempts/456/wait \
  -u "ezkey_ikey_xxx:ezkey_skey_xxx"
```

### ❌ Incorrect: Using API Key for Enrollments

```bash
# Create enrollment (FORBIDDEN - 403)
curl -X POST http://localhost:9080/api/v1/enrollments \
  -u "ezkey_ikey_xxx:ezkey_skey_xxx" \
  -H "Content-Type: application/json" \
  -d '{
    "integrationId": 1,
    "name": "User Device",
    "challengeRequired": false
  }'

# Response: 403 Forbidden
# {"error": "API keys cannot manage enrollments"}
```

### ✅ Correct: Using Bearer Token for Enrollments

```bash
# Create enrollment (ALLOWED)
curl -X POST http://localhost:9080/api/v1/enrollments \
  -H "Authorization: Bearer ezkey_admin_token..." \
  -H "Content-Type: application/json" \
  -d '{
    "integrationId": 1,
    "name": "User Device",
    "challengeRequired": false
  }'

# Response: 201 Created
```

## Integration Workflow

### Recommended Pattern

1. **Setup (Bearer Token)**:
   - Admin creates integration via admin console
   - Admin creates enrollments for users
   - Admin generates API keys for backend integration

2. **Operation (API Key)**:
   - Backend server creates auth attempts using API key
   - Backend server waits for auth completion using API key
   - Users respond via mobile app

3. **Management (Bearer Token)**:
   - Admin manages enrollments (reset, revoke, view)
   - Admin rotates API keys
   - Admin monitors system via admin console

## Error Handling

### Client-Side Detection

Applications should handle 403 responses appropriately:

```javascript
try {
    const response = await fetch('/api/v1/enrollments', {
        method: 'POST',
        headers: {
            'Authorization': 'Basic ' + btoa(integrationKey + ':' + secretKey),
            'Content-Type': 'application/json'
        },
        body: JSON.stringify(enrollmentData)
    });
    
    if (response.status === 403) {
        console.error('API keys cannot manage enrollments. Use bearer token.');
        // Switch to bearer token authentication or notify admin
    }
} catch (error) {
    console.error('Request failed:', error);
}
```

### Server-Side Logging

The server logs API key restriction attempts for security monitoring:

```
WARN - API key authentication failed for enrollment operation
    Integration ID: 123
    Client IP: 192.168.1.100
    Endpoint: POST /api/v1/enrollments
    Reason: API keys cannot manage enrollments
```

## Migration Guide

### Existing Integrations

If your integration currently uses API keys for enrollment management:

1. **Identify Enrollment Operations**: Review your codebase for calls to enrollment endpoints
2. **Switch to Bearer Tokens**: Use admin bearer token authentication for enrollment operations
3. **Keep API Keys for Auth**: Continue using API keys for authentication attempt operations
4. **Test Changes**: Verify 403 errors are handled appropriately

### Code Update Example

Before:
```python
# Using API key for everything (OLD - will fail for enrollments)
auth = (integration_key, secret_key)
requests.post(f"{base_url}/enrollments", auth=auth, json=enrollment_data)
requests.post(f"{base_url}/auth-attempts", auth=auth, json=attempt_data)
```

After:
```python
# Use bearer token for enrollments, API key for auth attempts (NEW)
bearer_token = get_admin_bearer_token()
headers = {"Authorization": f"Bearer {bearer_token}"}
requests.post(f"{base_url}/enrollments", headers=headers, json=enrollment_data)

# API key for auth attempts (unchanged)
auth = (integration_key, secret_key)
requests.post(f"{base_url}/auth-attempts", auth=auth, json=attempt_data)
```

## Security Considerations

### Defense in Depth

This restriction provides multiple security benefits:

1. **Principle of Least Privilege**: API keys only have access to authentication operations
2. **Audit Trail**: Clear separation between automated (API key) and manual (bearer token) operations
3. **Rate Limiting**: Different rate limits can be applied to API keys vs bearer tokens
4. **Blast Radius**: Compromised API key cannot manipulate enrollment database

### Key Rotation

When rotating API keys:

1. ✅ No impact on authentication attempt operations
2. ✅ No need to update enrollment management flows (uses bearer tokens)
3. ✅ Reduced risk surface area

## Testing

### Unit Tests

The restriction is validated through comprehensive unit tests:

```java
@Test
void enrollmentEndpoint_WithApiKey_ShouldReturn403() {
    // Setup API key authentication
    setupApiKeyAuth();
    
    // Attempt to access enrollment endpoint
    mockMvc.perform(post("/api/v1/enrollments")
            .contentType(MediaType.APPLICATION_JSON)
            .content(enrollmentJson))
            .andExpect(status().isForbidden());
}
```

### Integration Tests

Verify the restriction in integration tests:

```bash
# Test script
API_KEY="ezkey_ikey_xxx:ezkey_skey_xxx"

# Should succeed (auth attempts)
curl -u "$API_KEY" -X POST localhost:9080/api/v1/auth-attempts -d {...}
echo "Expected: 201 Created"

# Should fail (enrollments)
curl -u "$API_KEY" -X POST localhost:9080/api/v1/enrollments -d {...}
echo "Expected: 403 Forbidden"
```

## Support

For questions or issues related to API key restrictions:

1. Review this documentation
2. Check the [API Keys Guide](API_KEYS_GUIDE.md)
3. Review [API Keys - How It Works](API_KEYS_HOW_IT_WORKS.md)
4. Open an issue on GitHub

## Version History

- **2025-10-21**: Initial documentation for API key enrollment restrictions
- Implemented as part of enhanced security controls for machine-to-machine authentication
