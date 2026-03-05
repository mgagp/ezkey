# Ezkey API Keys - Complete Guide

## Table of Contents
1. [Overview](#overview)
2. [When to Use API Keys](#when-to-use-api-keys)
3. [Getting Started](#getting-started)
4. [Security Best Practices](#security-best-practices)
5. [Key Rotation Procedures](#key-rotation-procedures)
6. [Troubleshooting](#troubleshooting)
7. [API Reference](#api-reference)

---

## Overview

Ezkey API Keys provide machine-to-machine (M2M) authentication for integrated applications, enabling server-to-server API calls without the login/logout overhead required for human administrators.

### Key Concepts

**Dual Key System (Duo-style):**
- **Integration Key** (`ezkey_ikey_xxx`): Public identifier, safe to log and display
- **Secret Key** (`ezkey_skey_xxx`): Private secret, shown once, BCrypt hashed for storage

**Authentication Method:**
- HTTP Basic Authentication with integration key as username and secret key as password
- No login/logout required - use keys directly in API calls
- Same permissions as admin users for the associated integration

---

## When to Use API Keys

### Use API Keys For:

✅ **Backend Servers**
- Web application servers calling Ezkey Admin API
- Microservices requiring MFA functionality
- Server-to-server integrations

✅ **Automation**
- CI/CD pipelines automating MFA operations
- Scheduled jobs creating auth attempts
- Batch processing systems

✅ **Third-Party Integrations**
- External systems integrating with Ezkey
- SaaS platforms requiring MFA
- API gateways and middleware

### Use Bearer Tokens For:

👤 **Human Administrators**
- Admin console web interfaces
- Manual administrative operations
- Interactive sessions with login/logout

---

## Getting Started

### Step 1: Create an API Key

**Prerequisites:**
- Admin account with bearer token
- Integration ID you want to create a key for
- Secure location to store the secret key

**Request:**
```bash
curl -X POST http://localhost:9080/api/v1/api-keys \
  -H "Authorization: Bearer ezkey_admin_token..." \
  -H "Content-Type: application/json" \
  -d '{
    "integrationId": 123,
    "description": "Production Server",
    "expiresAt": "2025-12-31T23:59:59Z",
    "ipWhitelist": ["192.168.1.0/24"]
  }'
```

**Response:**
```json
{
  "apiKeyId": 42,
  "integrationKey": "ezkey_ikey_a1b2c3d4e5f6g7h8i9j0",
  "secretKey": "ezkey_skey_a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6q7r8s9t0",
  "description": "Production Server",
  "createdAt": "2025-10-17T12:00:00Z",
  "expiresAt": "2025-12-31T23:59:59Z",
  "ipWhitelist": ["192.168.1.0/24"],
  "warning": "IMPORTANT: Save the secret key now. It will not be shown again."
}
```

**⚠️ CRITICAL:** Save the `secretKey` immediately! It will never be shown again.

### Step 2: Store Keys Securely

**Environment Variables (Recommended):**
```bash
export EZKEY_INTEGRATION_KEY="ezkey_ikey_a1b2c3d4e5f6g7h8i9j0"
export EZKEY_SECRET_KEY="ezkey_skey_a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6q7r8s9t0"
```

**Configuration File (Encrypted):**
```yaml
# config.yml (encrypted at rest)
ezkey:
  integration_key: ezkey_ikey_a1b2c3d4e5f6g7h8i9j0
  secret_key: ezkey_skey_a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6q7r8s9t0
```

**Secret Management (Production):**
- AWS Secrets Manager
- HashiCorp Vault
- Azure Key Vault
- Google Secret Manager

### Step 3: Use API Keys in Your Application

**Java Example:**
```java
import org.apache.commons.codec.binary.Base64;

public class EzkeyClient {
    private final String integrationKey;
    private final String secretKey;
    
    public EzkeyClient(String integrationKey, String secretKey) {
        this.integrationKey = integrationKey;
        this.secretKey = secretKey;
    }
    
    private String getAuthHeader() {
        String credentials = integrationKey + ":" + secretKey;
        String encodedCredentials = Base64.encodeBase64String(
            credentials.getBytes(StandardCharsets.UTF_8)
        );
        return "Basic " + encodedCredentials;
    }
    
    public AuthAttempt createAuthAttempt(int enrollmentId) {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:9080/api/v1/auth-attempts"))
            .header("Authorization", getAuthHeader())
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(
                "{\"enrollmentId\":" + enrollmentId + ",\"challengeRequested\":false}"
            ))
            .build();
            
        HttpResponse<String> response = client.send(request, 
            HttpResponse.BodyHandlers.ofString());
            
        return parseResponse(response.body());
    }
}
```

**Python Example:**
```python
import requests
from requests.auth import HTTPBasicAuth

class EzkeyClient:
    def __init__(self, integration_key, secret_key):
        self.integration_key = integration_key
        self.secret_key = secret_key
        self.base_url = "http://localhost:9080/api/v1"
    
    def create_auth_attempt(self, enrollment_id):
        response = requests.post(
            f"{self.base_url}/auth-attempts",
            auth=HTTPBasicAuth(self.integration_key, self.secret_key),
            json={
                "enrollmentId": enrollment_id,
                "challengeRequested": False
            }
        )
        return response.json()
```

**Node.js Example:**
```javascript
const axios = require('axios');

class EzkeyClient {
    constructor(integrationKey, secretKey) {
        this.integrationKey = integrationKey;
        this.secretKey = secretKey;
        this.baseUrl = 'http://localhost:9080/api/v1';
    }
    
    async createAuthAttempt(enrollmentId) {
        const credentials = Buffer.from(
            `${this.integrationKey}:${this.secretKey}`
        ).toString('base64');
        
        const response = await axios.post(
            `${this.baseUrl}/auth-attempts`,
            {
                enrollmentId: enrollmentId,
                challengeRequested: false
            },
            {
                headers: {
                    'Authorization': `Basic ${credentials}`
                }
            }
        );
        
        return response.data;
    }
}
```

---

## Security Best Practices

### 1. Secret Key Management

**DO:**
- ✅ Store secret keys in environment variables or secret managers
- ✅ Encrypt configuration files containing secret keys
- ✅ Use different keys for dev/staging/production
- ✅ Rotate keys regularly (every 90 days recommended)
- ✅ Revoke keys immediately if compromised

**DON'T:**
- ❌ Hardcode secret keys in source code
- ❌ Commit secret keys to version control
- ❌ Share secret keys via email or chat
- ❌ Reuse keys across multiple environments
- ❌ Log secret keys in application logs

### 2. IP Whitelisting

**Recommended for Production:**
```json
{
  "integrationId": 123,
  "description": "Production Server",
  "ipWhitelist": [
    "192.168.1.0/24",      // Internal network
    "203.0.113.50",        // Specific server IP
    "198.51.100.0/24"      // Office network
  ]
}
```

**Benefits:**
- Prevents key usage from unauthorized locations
- Additional layer of security if keys are leaked
- Compliance with security policies

### 3. Expiration Dates

**Recommended Expiration Periods:**
- **Development:** 30 days
- **Staging:** 60 days
- **Production:** 90 days

**Benefits:**
- Enforces regular key rotation
- Limits exposure window if keys compromised
- Automatic cleanup of forgotten keys

### 4. Monitoring

**Key Metrics to Monitor:**
- `lastUsedAt`: Detect unused or forgotten keys
- Expiration warnings: Plan rotation before expiration
- Failed authentication attempts: Detect brute force
- IP address patterns: Detect suspicious usage

### 5. Principle of Least Privilege

**One Key Per Application:**
```
Production Server    → API Key #1
Staging Server       → API Key #2
CI/CD Pipeline       → API Key #3
```

**Benefits:**
- Easier to revoke specific access
- Better audit trail
- Isolated security incidents

---

## Key Rotation Procedures

### Manual Rotation (Zero Downtime)

**Scenario:** Proactive key rotation every 90 days

**Step 1: Create New Key**
```bash
curl -X POST http://localhost:9080/api/v1/api-keys \
  -H "Authorization: Bearer ezkey_admin_token..." \
  -H "Content-Type: application/json" \
  -d '{
    "integrationId": 123,
    "description": "Production Server - Q1 2025",
    "expiresAt": "2025-12-31T23:59:59Z"
  }'
```

Save the response:
```json
{
  "integrationKey": "ezkey_ikey_NEW_KEY_HERE",
  "secretKey": "ezkey_skey_NEW_SECRET_HERE"
}
```

**Step 2: Update Application Configuration**

Deploy new configuration to staging first:
```bash
# staging environment
export EZKEY_INTEGRATION_KEY="ezkey_ikey_NEW_KEY_HERE"
export EZKEY_SECRET_KEY="ezkey_skey_NEW_SECRET_HERE"
```

**Step 3: Test New Key**
```bash
# Verify new key works
curl -X GET http://localhost:9080/api/v1/integrations/123 \
  -u "ezkey_ikey_NEW_KEY_HERE:ezkey_skey_NEW_SECRET_HERE"
```

**Step 4: Deploy to Production**
```bash
# Blue/green deployment or rolling update
# Both old and new keys work during transition
```

**Step 5: Monitor Usage**

Check that new key is being used:
```bash
curl -X GET http://localhost:9080/api/v1/api-keys/NEW_KEY_ID \
  -H "Authorization: Bearer ezkey_admin_token..."
```

Look for updated `lastUsedAt` timestamp.

**Step 6: Revoke Old Key**
```bash
curl -X DELETE http://localhost:9080/api/v1/api-keys/OLD_KEY_ID \
  -H "Authorization: Bearer ezkey_admin_token..."
```

**Step 7: Verify Revocation**
```bash
# Old key should now return 401
curl -X GET http://localhost:9080/api/v1/integrations/123 \
  -u "ezkey_ikey_OLD_KEY:ezkey_skey_OLD_KEY"
```

### Emergency Rotation (Compromised Key)

**Scenario:** Key leaked in logs or code repository

**Immediate Actions:**

1. **Revoke Compromised Key Immediately**
```bash
curl -X DELETE http://localhost:9080/api/v1/api-keys/COMPROMISED_KEY_ID \
  -H "Authorization: Bearer ezkey_admin_token..."
```

2. **Create New Key**
```bash
curl -X POST http://localhost:9080/api/v1/api-keys \
  -H "Authorization: Bearer ezkey_admin_token..." \
  -H "Content-Type: application/json" \
  -d '{
    "integrationId": 123,
    "description": "Emergency Rotation - ' + $(date) + '"
  }'
```

3. **Emergency Deploy**
- Update configuration with new keys
- Deploy immediately to all environments
- Monitor for unauthorized usage

4. **Audit Review**
- Check audit logs for suspicious activity
- Review all API calls made with compromised key
- Assess security impact

### Automatic Expiration

**Setup:**
```json
{
  "integrationId": 123,
  "description": "Production Server Q4 2025",
  "expiresAt": "2025-12-31T23:59:59Z"
}
```

**Warning System:**
- 30 days before: Email warning to admins
- 7 days before: Daily email warnings
- 1 day before: Hourly warnings
- On expiration: Key automatically deactivated

**Response to Expiration:**
1. Application receives 401 Unauthorized
2. Check error message: "API key expired"
3. Create new key via admin interface
4. Update application configuration
5. Deploy updated configuration

---

## Security Best Practices

### Production Deployment Checklist

**Before Going Live:**
- [ ] API keys stored in secret manager (not environment variables)
- [ ] IP whitelist configured for production servers
- [ ] Expiration date set (90 days maximum)
- [ ] Separate keys for each environment (dev/staging/prod)
- [ ] Monitoring and alerting configured
- [ ] Key rotation procedure documented
- [ ] Incident response plan prepared

### Secure Storage Options

**1. AWS Secrets Manager**
```bash
# Store keys
aws secretsmanager create-secret \
  --name ezkey/prod/api-keys \
  --secret-string '{
    "integration_key":"ezkey_ikey_xxx",
    "secret_key":"ezkey_skey_xxx"
  }'

# Retrieve in application
aws secretsmanager get-secret-value \
  --secret-id ezkey/prod/api-keys
```

**2. HashiCorp Vault**
```bash
# Store keys
vault kv put secret/ezkey/prod \
  integration_key="ezkey_ikey_xxx" \
  secret_key="ezkey_skey_xxx"

# Retrieve in application
vault kv get secret/ezkey/prod
```

**3. Kubernetes Secrets**
```yaml
apiVersion: v1
kind: Secret
metadata:
  name: ezkey-api-keys
type: Opaque
stringData:
  integration-key: ezkey_ikey_xxx
  secret-key: ezkey_skey_xxx
```

### Rate Limiting

**Current Limits (per API key, per instance):**
- **Admin API:** Create auth attempt: 10 requests/minute; wait and cancel: 20 requests/minute
- **M2M API:** Higher defaults (e.g. 100 create/min, 200 wait/min) for dedicated M2M workloads
- Limits are per instance (no distributed coordination); see [ENDPOINT.md](ENDPOINT.md) for details
- Returns 429 Too Many Requests when exceeded

**Tuning for M2M:** If your server or CI/CD needs higher throughput, you can raise the Admin API limits in configuration (e.g. `ezkey.api-key.rate-limit.create-auth-attempt.requests=30` or 60 per minute). For dedicated M2M traffic, prefer the M2M API (port 7080), which uses higher defaults.

**Monitoring:**
```bash
# Check recent usage
curl -X GET http://localhost:9080/api/v1/api-keys/42 \
  -H "Authorization: Bearer ezkey_admin_token..."
  
# Look at lastUsedAt to monitor usage patterns
```

### Audit Logging

**API Key Events Logged:**
- `API_KEY_CREATED`: New key generated
- `API_KEY_REVOKED`: Key manually revoked
- `API_KEY_EXPIRED`: Key auto-expired
- `API_KEY_AUTH_SUCCESS`: Successful authentication
- `API_KEY_AUTH_FAILED`: Failed authentication attempt
- `API_KEY_IP_BLOCKED`: IP not whitelisted

---

## Key Rotation Procedures

### Scenario 1: Scheduled Rotation (Every 90 Days)

**Timeline:**
```
Day 0:   Create new API key
Day 1:   Deploy to staging, test
Day 2:   Deploy to production (both keys active)
Day 7:   Verify new key usage, revoke old key
```

**Detailed Steps:**

**Week Before Rotation:**
1. Review current API key usage
2. Identify all applications using current key
3. Prepare deployment plan
4. Schedule maintenance window

**Day of Rotation:**
1. Create new key with expiration +90 days
2. Update CI/CD secrets with new key
3. Deploy to staging environment
4. Run integration tests
5. Monitor for 24 hours
6. Deploy to production with rolling update
7. Both keys active during transition

**Week After Rotation:**
1. Monitor `lastUsedAt` for old key (should stop updating)
2. Verify new key `lastUsedAt` is recent
3. Revoke old key
4. Update documentation with new key ID

### Scenario 2: Emergency Rotation (Compromised Key)

**Immediate Response (Within 1 Hour):**

1. **Revoke compromised key** (no testing, immediate)
```bash
curl -X DELETE http://localhost:9080/api/v1/api-keys/COMPROMISED_ID \
  -H "Authorization: Bearer ezkey_admin_token..."
```

2. **Create replacement key**
```bash
curl -X POST http://localhost:9080/api/v1/api-keys \
  -H "Authorization: Bearer ezkey_admin_token..." \
  -d '{"integrationId":123,"description":"Emergency Replacement"}'
```

3. **Emergency deployment**
- Update production config immediately
- Deploy with expedited process
- Accept brief downtime if necessary

**Post-Incident (Within 24 Hours):**

1. **Audit Review**
- Check all API calls made with compromised key
- Review audit logs for unauthorized access
- Assess data exposure

2. **Incident Report**
- Document how key was compromised
- List actions taken
- Update security procedures

3. **Prevention Measures**
- Update logging to catch similar issues
- Review access controls
- Security training if needed

### Scenario 3: Planned Migration (Environment Changes)

**Use Case:** Moving from staging to production infrastructure

1. Create production-specific key with IP whitelist
2. Deploy to new environment with new key
3. Test thoroughly
4. Switch traffic to new environment
5. Revoke staging key

---

## Troubleshooting

### Issue: 401 Unauthorized

**Symptoms:**
```
HTTP/1.1 401 Unauthorized
```

**Possible Causes:**

1. **Invalid Secret Key**
```bash
# Verify you're using correct secret key
# Check for typos or truncation
```

2. **Revoked Key**
```bash
# Check key status
curl -X GET http://localhost:9080/api/v1/api-keys/42 \
  -H "Authorization: Bearer admin_token..."
  
# Look for: "active": false
```

3. **Expired Key**
```bash
# Check expiresAt field
# If expired, create new key
```

4. **Wrong Authentication Method**
```bash
# ❌ Wrong: Bearer token
Authorization: Bearer ezkey_ikey_xxx

# ✅ Correct: HTTP Basic Auth
Authorization: Basic base64(ezkey_ikey_xxx:ezkey_skey_xxx)
```

### Issue: 403 Forbidden (IP Blocked)

**Symptoms:**
```
API key authentication failed - IP not whitelisted
```

**Solutions:**

1. **Check Client IP**
```bash
# Your actual IP might be different (proxy, NAT)
curl http://ifconfig.me
```

2. **Update IP Whitelist**
```bash
# Revoke old key and create new one with correct IP
curl -X POST http://localhost:9080/api/v1/api-keys \
  -d '{"integrationId":123,"ipWhitelist":["YOUR_ACTUAL_IP"]}'
```

3. **Use CIDR Range**
```bash
# More flexible for dynamic IPs
"ipWhitelist": ["192.168.0.0/16"]
```

### Issue: 429 Too Many Requests

**Symptoms:**
```
HTTP/1.1 429 Too Many Requests
Retry-After: 3600
```

**Cause:** Exceeded 1000 requests per hour limit

**Solutions:**

1. **Wait for Rate Limit Reset**
```bash
# Check Retry-After header (seconds)
# Wait before retrying
```

2. **Optimize API Usage**
- Implement caching
- Batch operations
- Reduce polling frequency

3. **Create Additional Key**
- Maximum 5 active keys per integration
- Distribute load across multiple keys

### Issue: Secret Key Lost

**Symptoms:**
```
Cannot find secret key in configuration
```

**Solution:**

Lost secret keys **CANNOT be recovered**. Create a new key:

1. **Create New Key**
```bash
curl -X POST http://localhost:9080/api/v1/api-keys \
  -H "Authorization: Bearer admin_token..." \
  -d '{"integrationId":123,"description":"Replacement Key"}'
```

2. **Save New Secret Immediately**
3. **Update Application Configuration**
4. **Revoke Old Key** (if you know its ID)

---

## API Reference

### POST /api/v1/api-keys

Create new API key pair.

**Request Body:**
```json
{
  "integrationId": 123,           // Required
  "description": "string",         // Optional, max 255 chars
  "expiresAt": "ISO-8601",        // Optional
  "ipWhitelist": ["string"]       // Optional, IP or CIDR
}
```

**Response (201 Created):**
```json
{
  "apiKeyId": 42,
  "integrationKey": "ezkey_ikey_xxx",
  "secretKey": "ezkey_skey_xxx",  // SHOWN ONCE
  "description": "string",
  "createdAt": "ISO-8601",
  "expiresAt": "ISO-8601",
  "ipWhitelist": ["string"],
  "warning": "IMPORTANT: Save the secret key now..."
}
```

**Errors:**
- 400: Invalid integration ID or data
- 401: Admin authentication required
- 404: Integration not found
- 409: Maximum active keys limit reached (5 per integration)

---

### GET /api/v1/api-keys/integration/{integrationId}

List all active API keys for an integration.

**Response (200 OK):**
```json
[
  {
    "apiKeyId": 42,
    "integrationId": 123,
    "integrationKey": "ezkey_ikey_xxx",  // Public
    "description": "string",
    "active": true,
    "createdAt": "ISO-8601",
    "expiresAt": "ISO-8601",
    "lastUsedAt": "ISO-8601",
    "ipWhitelist": ["string"]
    // Note: secretKey never included
  }
]
```

---

### GET /api/v1/api-keys/{keyId}

Get specific API key details.

**Response (200 OK):**
Same as list item above.

**Errors:**
- 401: Admin authentication required
- 404: API key not found

---

### DELETE /api/v1/api-keys/{keyId}

Revoke API key immediately.

**Response (204 No Content):**
```http
HTTP/1.1 204 No Content
```

**Errors:**
- 401: Admin authentication required
- 404: API key not found

---

## Advanced Topics

### Multiple Active Keys (Rotation Support)

Each integration supports up to 5 active API keys simultaneously:

**Use Cases:**
- Zero-downtime key rotation
- Multiple application instances
- Gradual migration between keys

**Example:**
```
Integration #123:
  ✅ Key #1: Production Server (expires 2025-12-31)
  ✅ Key #2: Staging Server (expires 2025-11-30)
  ✅ Key #3: CI/CD Pipeline (expires 2026-01-15)
  ✅ Key #4: Migration Key (new, being tested)
  ❌ Key #5: Old Production (revoked)
```

### Cleanup Scheduled Tasks

**Automatic Cleanup:**
- Runs hourly
- Deactivates expired keys
- Preserves for audit (active=false)
- Logs all cleanup operations

**Manual Cleanup:**
```bash
# Find unused keys (never used)
curl -X GET http://localhost:9080/api/v1/api-keys/integration/123 \
  -H "Authorization: Bearer admin_token..."

# Look for keys with lastUsedAt = null
# Revoke if no longer needed
```

---

## Quick Reference

### Create & Use API Key (Complete Flow)

```bash
# 1. Create key as admin
RESPONSE=$(curl -X POST http://localhost:9080/api/v1/api-keys \
  -H "Authorization: Bearer ezkey_admin_token..." \
  -H "Content-Type: application/json" \
  -d '{"integrationId":123,"description":"My App"}')

# 2. Extract keys
IKEY=$(echo $RESPONSE | jq -r '.integrationKey')
SKEY=$(echo $RESPONSE | jq -r '.secretKey')

# 3. Save to environment
echo "export EZKEY_IKEY=$IKEY" >> ~/.bashrc
echo "export EZKEY_SKEY=$SKEY" >> ~/.bashrc

# 4. Use in application
curl -X POST http://localhost:9080/api/v1/auth-attempts \
  -u "$EZKEY_IKEY:$EZKEY_SKEY" \
  -H "Content-Type: application/json" \
  -d '{"enrollmentId":456,"challengeRequested":false}'
```

### Common Commands

```bash
# List all keys for integration
curl -X GET http://localhost:9080/api/v1/api-keys/integration/123 \
  -H "Authorization: Bearer admin_token..."

# Check specific key
curl -X GET http://localhost:9080/api/v1/api-keys/42 \
  -H "Authorization: Bearer admin_token..."

# Revoke key
curl -X DELETE http://localhost:9080/api/v1/api-keys/42 \
  -H "Authorization: Bearer admin_token..."
```

---

## Comparison: API Keys vs Bearer Tokens

| Feature | API Keys | Bearer Tokens |
|---------|----------|---------------|
| **Use Case** | Machine-to-machine | Human admins |
| **Authentication** | HTTP Basic Auth | Passwordless MFA |
| **Lifetime** | Long-lived (days/months) | 24 hours |
| **Login Required** | No | Yes |
| **Rotation** | Manual or auto-expiration | Automatic on login |
| **Rate Limit** | 10 create/min, 20 wait/min (Admin API); higher on M2M API | 5/minute (login) |
| **Ideal For** | Server applications | Interactive sessions |

---

## Support

### Resources
- **Main Documentation:** [ENDPOINT.md](ENDPOINT.md)
- **Security Guide:** [ARCHITECTURE.md](ARCHITECTURE.md)
- **Development Guide:** [DEVELOPMENT.md](DEVELOPMENT.md)

### Getting Help
- GitHub Issues: Report bugs or feature requests
- Security Issues: security@ezkey.org (for vulnerabilities)

---

**Document Version:** 1.0  
**Last Updated:** October 2025  
**Status:** Active

---

*This guide provides comprehensive information for using Ezkey API Keys in production environments. Follow security best practices and rotation procedures to maintain a secure authentication infrastructure.*

