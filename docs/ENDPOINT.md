# Ezkey API Endpoint Reference

## General Context

Ezkey separates its backend APIs into two applications:
- **admin-api** (internal): management of authentication requests (CRUD), accessible only to the organization.
- **auth-api** (external, mobile): consumption of authentication requests by the Ezkey mobile application.

The interaction model is **pull**: the mobile device fetches the request to validate (pending) by providing a cryptographic signature in the body, ensuring the authenticity of the request.

---

## ðŸ“± User-Initiated Polling Model

### **Critical Design Principle: User Control**

The pending endpoint (`POST /api/v1/auth-attempts/pending`) follows a **user-initiated polling model**, not an automated polling system.

**Key Points:**
- **User Decision**: The mobile app polls only when the user explicitly requests to check for pending authentication attempts
- **No Auto-Polling**: The app does NOT automatically poll every 2-3 seconds in the background
- **Intentional Action**: Each pending request represents a deliberate user action to check for authentication requests
- **Rate Limiting Context**: Rate limiting on pending is designed for normal user behavior, not automated systems

**Why This Matters:**
- Prevents "push fatigue attacks" where systems bombard users with notifications
- Gives users control over when they want to authenticate
- Allows for natural user interaction patterns
- Simplifies the mobile app architecture by avoiding background polling complexity

---

## ðŸ” Token Security - Critical Design

### **Security Principle: One-Time Use Token**

Ezkey uses a **one-time proof token** system to ensure the integrity and security of the authentication process.

#### **ðŸ”‘ Two Types of Tokens**

1. **`enrollmentProofToken`**: Permanent enrollment token, used to bind the device
2. **`authAttemptProofToken`**: Unique token per authentication attempt, **CRITICAL for security**

#### **ðŸ›¡ï¸ Security Mechanism**

**Step 1 - PENDING**:
- The server generates a unique `authAttemptProofToken` for each attempt
- This token is **signed** by the backend with the integration's private key
- The mobile device **verifies** the signature using the integration's public key
- **The token can only be read once** (by the PENDING request)

**Step 2 - RESPOND**:
- The mobile device **signs** the `authAttemptProofToken` (in clear) with its private key
- This signature proves that the device has received the original token
- **Enhanced security**: impossible to replay an attempt without having the original token

#### **âš ï¸ Critical Points for Developers**

```java
// âŒ INCORRECT - Never sign enrollmentProofToken for RESPOND
String signature = signWithDeviceKey(enrollmentProofToken);

// âœ… CORRECT - Always sign authAttemptProofToken for RESPOND  
String signature = signWithDeviceKey(authAttemptProofToken);
```

#### **ðŸŽ¯ Why This Design?**

1. **Anti-replay**: Each attempt has a unique token
2. **Strong authentication**: Only the legitimate device can decrypt and sign
3. **Traceability**: Each token can be traced to a specific attempt
4. **Security by default**: Impossible to bypass without understanding the mechanism

---

## ðŸ• Datetime Format - UTC Standard

### **Standard: All Timestamps in UTC**

Ezkey APIs return all datetime fields in **UTC (Coordinated Universal Time) with Z suffix** for consistency and timezone independence.

**Format Pattern:**
```
yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'
```

**Examples:**
- `"createdAt": "2025-10-16T15:52:52.764912Z"`
- `"expiresAt": "2025-10-16T16:56:19.433745Z"`
- `"completedAt": "2025-10-16T11:55:55.925512Z"`

**Key Points:**
- **Z suffix**: Indicates UTC timezone (Zulu time)
- **Microsecond precision**: Six decimal places for timestamps
- **Database storage**: TIMESTAMPTZ preserves original timezone internally
- **API responses**: Always converted to UTC for consistency
- **Client responsibility**: Convert to local timezone for display if needed

**Why UTC?**
1. **Timezone independence**: Works globally without ambiguity
2. **API best practice**: Standard for RESTful APIs
3. **Consistency**: All timestamps use the same format
4. **Sortability**: Direct string comparison for chronological ordering

---

## 1. Auth API Endpoints (mobile)

### a) Retrieve pending request

**POST /api/v1/auth-attempts/pending**

- **Description**: The mobile device queries the backend to check if there is a pending authentication request for its enrollment. The body contains a cryptographic signature proving the authenticity of the request and an enrollment proof token for secure enrollment identification.
- **Why POST?**: The crypto signature is transmitted in the body, which is not possible with GET.
- **Security Enhancement**: This endpoint now uses enrollmentProofToken in the request body instead of enrollmentId in the URL path to prevent enumeration attacks.

**Request**
```http
POST /api/v1/auth-attempts/pending
Content-Type: application/json

{
  "enrollmentId": 456,
  "enrollmentProofToken": "EZK-ABC123-DEF456",
  "deviceProofToken": "abc123-def456-ghi789",
  "deviceProofTokenSigned": "eyJhbGciOiJSUzI1NiJ9..."
}
```

**Response**
- 200 OK + pending request details (or 204 No Content if no request)
```json
{
  "authAttemptId": 123,
  "authAttemptProofToken": "abc123-def456-ghi789",
  "authAttemptProofTokenSignedByIntegration": "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
  "authAttemptChallengeRequired": true
}
```

### b) Submit response to request

**POST /api/v1/auth-attempts/respond**

- **Description**: The mobile device submits the user's response (approved, denied, signature, etc.) for the received authentication request.

**Request**
```http
POST /api/v1/auth-attempts/respond
Content-Type: application/json

{
  "authAttemptId": 123,
  "authAttemptAccepted": true,
  "authAttemptProofTokenSignedByDevice": "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
  "authAttemptChallengeResponse": 123456
}
```

**Response**
- 200 OK + validation result
```json
{
  "result": "APPROVED",
  "message": "Authentication approved"
}
```

### c) Enrollment process (device binding)

**POST /api/v1/enrollments/bind**

- **Description**: Initiates the process of binding an enrollment to a mobile device using secure enrollment proof token. The device retrieves the necessary information to start enrollment.

**Request**
```http
POST /api/v1/enrollments/bind
Content-Type: application/json

{
  "enrollmentId": 456,
  "enrollmentProofToken": "abc123-def456-ghi789",
  "language": "en"
}
```

**Response**
- 200 OK + binding information
```json
{
  "enrollmentId": 456,
  "enrollmentProofToken": "abc123-def456-ghi789",
  "integrationPublicKey": "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
  "integrationName": "Acme Bank",
  "integrationDescription": "Acme Bank provides secure online banking services.",
  "integrationLogo": "https://acme.com/logo.png",
  "enrollmentName": "John's iPhone"
}
```

### d) Enrollment verification

**POST /api/v1/enrollments/verify**

- **Description**: Finalizes the enrollment process by submitting the device's cryptographic keys and the enrollment proof token signature.

**Request**
```http
POST /api/v1/enrollments/verify
Content-Type: application/json

{
  "enrollmentId": 456,
  "challengeResponse": 987654,
  "devicePublicKey": "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
  "enrollmentProofTokenSigned": "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
}
```

**Response**
- 200 OK + verification confirmation
```json
{
  "active": true
}
```

---

## 2. Admin API Endpoints (internal)

### Admin Authentication (Passwordless-Only)

Base URL: `http://localhost:9080/api/v1/admin/auth`

**Architecture:** Ezkey Admin API uses **passwordless-only authentication** - no passwords stored or transmitted. Admins authenticate using Ezkey's cryptographic authentication system ("eating our own dogfood").

**Prerequisites:**
- Admin must have bound Ezkey enrollment (device with public key)
- Device must be available to approve authentication requests
- For emergency access, admins have 10 single-use recovery codes

---

#### POST /login (Passwordless)
Authenticate admin user using Ezkey cryptographic authentication.

**Two Authentication Modes:**
1. **Single-call (no challenge):** Blocking wait for device approval - convenient
2. **Two-call (with challenge):** Returns challenge code, requires separate wait - more secure

**Request (Single-Call Mode):**
```http
POST /api/v1/admin/auth/login
Content-Type: application/json

{
  "username": "admin",
  "challengeRequested": false
}
```

**Success Response (200 OK) - After Device Approval:**
```json
{
  "success": true,
  "token": "ezkey_abc123def456...",
  "adminType": "GLOBAL_ADMIN",
  "username": "admin",
  "expiresAt": "2025-10-04T10:00:00Z",
  "message": "Authentication successful"
}
```

**Request (Two-Call Mode with Challenge):**
```http
POST /api/v1/admin/auth/login
Content-Type: application/json

{
  "username": "admin",
  "challengeRequested": true
}
```

**Pending Response (200 OK) - Immediate:**
```json
{
  "success": false,
  "status": "pending",
  "authAttemptId": 123,
  "challengeCode": 654321,
  "username": "admin",
  "adminType": "GLOBAL_ADMIN",
  "expiresAt": "2025-10-04T10:05:00Z",
  "message": "Challenge verification required. Enter code 654321 on your device, then call /passwordless-wait."
}
```

**Failure Responses:**
```json
// 400 Bad Request - No enrollment
{
  "success": false,
  "message": "Authentication failed: No device enrolled for passwordless authentication"
}

// 400 Bad Request - Device rejected
{
  "success": false,
  "message": "Authentication failed: Authentication rejected by device"
}

// 400 Bad Request - Timeout
{
  "success": false,
  "message": "Authentication failed: Authentication timeout - no device response"
}
```

**Rate Limited Response (429 Too Many Requests):**
```http
HTTP/1.1 429 Too Many Requests
Retry-After: 300
Content-Type: text/plain

Too many login attempts. Please try again later.
```

**Rate Limiting:**
- 5 requests per minute per IP address
- Automatic IP blocking after 10 consecutive failures (30 minutes)
- HTTP 429 when limit exceeded
- `Retry-After` header indicates seconds until retry allowed

**Security Notes:**
- **No passwords** - phishing resistant, cannot be stolen
- Device-bound credentials (private key never leaves device)
- Optional challenge code for high-security scenarios
- Bearer token expires after 24 hours
- Cryptographic signatures prevent forgery
- IP-based rate limiting prevents brute force attacks

---

#### POST /passwordless-wait (Two-Step Flow)
Wait for device approval in challenge-based authentication.

**Usage:** After receiving `authAttemptId` and `challengeCode` from `/login` with `challengeRequested: true`, call this endpoint to wait for device approval.

**Request:**
```http
POST /api/v1/admin/auth/passwordless-wait
Content-Type: application/json

{
  "authAttemptId": 123,
  "challengeCode": 654321
}
```

**Success Response (200 OK) - After Device Approval:**
```json
{
  "success": true,
  "token": "ezkey_abc123def456...",
  "adminType": "GLOBAL_ADMIN",
  "username": "admin",
  "expiresAt": "2025-10-04T10:00:00Z",
  "message": "Authentication successful"
}
```

**Security Notes:**
- `challengeCode` prevents enumeration attacks (proof of legitimate login initiation)
- Blocking call (up to 5 minutes timeout)
- Device must enter matching challenge code before approval
- Invalid challenge on device marks attempt as INVALID

---

#### POST /recover (Emergency Access)
Authenticate using single-use recovery code when device is lost.

**Request:**
```http
POST /api/v1/admin/auth/recover
Content-Type: application/json

{
  "username": "admin",
  "recoveryCode": "1234-5678-9012-3456-7890-1234-5678-9012"
}
```

**Success Response (200 OK):**
```json
{
  "success": true,
  "recoveryToken": "ezkey_recovery_abc123...",
  "expiresAt": "2025-10-04T10:30:00Z",
  "codesRemaining": 9,
  "message": "Recovery successful. Token valid for 30 minutes. Re-bind enrollment immediately."
}
```

**Failure Response (403 Forbidden):**
```json
{
  "success": false,
  "message": "Recovery failed: Invalid recovery code"
}
```

**Recovery Code Format:**
- 32 digits in 8 groups of 4: `XXXX-XXXX-XXXX-XXXX-XXXX-XXXX-XXXX-XXXX`
- 106-bit entropy (10^32 combinations = paranoia-level security)
- Single-use only (removed from array after successful use)
- 10 codes per admin (generated at account creation)

**Security Notes:**
- Recovery codes are BCrypt hashed (same security as passwords were)
- Single-use enforcement (code removed after validation)
- Recovery token expires after 30 minutes
- Limited permissions (can only reset enrollment)
- Rate limited: 3 attempts per 15 minutes per IP

---

#### POST /enrollments/reset (After Recovery)
Reset enrollment after using recovery code (unbind lost device).

**Request:**
```http
POST /api/v1/admin/enrollments/reset
Authorization: Bearer ezkey_recovery_abc123...
Content-Type: application/json

{
  "enrollmentId": 1
}
```

**Success Response (200 OK):**
```json
{
  "success": true,
  "enrollmentId": 1,
  "enrollmentProofToken": "new-token-xyz789...",
  "enrollmentChallenge": 654321,
  "integrationId": 1,
  "message": "Enrollment reset successfully. Old device unbound. Use these credentials to bind new device."
}
```

**Usage Flow:**
1. Use recovery code to get recovery token (30 min validity)
2. Reset enrollment (unbinds old device)
3. Bind new device with new credentials
4. Resume normal passwordless login

**Security Notes:**
- Requires recovery token (obtained via `/recover`)
- Unbinds old device (sets device_public_key to null)
- Generates new enrollment credentials
- Recovery token expires after use or 30 minutes

#### POST /logout
Revoke current bearer token and end session.

**Request:**
```http
POST /api/v1/admin/auth/logout
Authorization: Bearer ezkey_abc123def456...
```

**Success Response:**
```http
HTTP/1.1 200 OK
```

**Unauthorized Response:**
```http
HTTP/1.1 401 Unauthorized

Invalid or expired token
```

**Security Notes:**
- Token immediately invalidated in database
- Subsequent requests with same token will return 401
- Recommended to call on explicit logout or session timeout

#### Using Bearer Token
All authenticated Admin API endpoints require the bearer token in the Authorization header:

```http
GET /api/v1/admin/integrations
Authorization: Bearer ezkey_abc123def456...
```

---

## ðŸ”‘ API Keys Authentication (Machine-to-Machine)

### **Overview**

API Keys provide machine-to-machine (M2M) authentication for integrated applications, enabling server-to-server API calls without the login/logout overhead required for human administrators.

**Use Cases:**
- Backend servers calling Ezkey Admin API
- CI/CD pipelines automating MFA operations
- Scheduled jobs creating auth attempts
- Third-party integrations

**Authentication Methods:**
- **Bearer Tokens:** For human administrators (requires passwordless login)
- **API Keys:** For integrated applications (no login required)

---

### API Key Format

API keys follow a Duo-style dual key system:

```
Integration Key: ezkey_ikey_a1b2c3d4e5f6g7h8i9j0  (public, safe to log)
Secret Key:      ezkey_skey_a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6q7r8s9t0  (private, shown once)
```

**HTTP Basic Authentication:**
```http
Authorization: Basic base64(ezkey_ikey_xxx:ezkey_skey_xxx)
```

---

### a) Create API Key

**POST /api/v1/api-keys**

Creates a new API key pair for an integration. The secret key is shown ONLY ONCE and cannot be retrieved later.

**Request:**
```http
POST /api/v1/api-keys
Authorization: Bearer ezkey_admin_token...
Content-Type: application/json

{
  "integrationId": 123,
  "description": "Production Server API Key",
  "expiresAt": "2025-12-31T23:59:59Z",
  "ipWhitelist": ["192.168.1.0/24", "10.0.0.100"]
}
```

**Response (201 Created):**
```json
{
  "apiKeyId": 42,
  "integrationKey": "ezkey_ikey_a1b2c3d4e5f6g7h8i9j0",
  "secretKey": "ezkey_skey_a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6q7r8s9t0",
  "description": "Production Server API Key",
  "createdAt": "2025-10-17T12:00:00Z",
  "expiresAt": "2025-12-31T23:59:59Z",
  "ipWhitelist": ["192.168.1.0/24", "10.0.0.100"],
  "warning": "IMPORTANT: Save the secret key now. It will not be shown again."
}
```

**Security Notes:**
- Secret key shown ONLY ONCE - save immediately
- Lost secret keys cannot be recovered - create new key instead
- Expiration date optional but recommended (enforces rotation)
- IP whitelist optional but recommended for production

---

### b) List API Keys for Integration

**GET /api/v1/api-keys/integration/{integrationId}**

Lists all active API keys for a specific integration. Secret keys are never included.

**Request:**
```http
GET /api/v1/api-keys/integration/123
Authorization: Bearer ezkey_admin_token...
```

**Response (200 OK):**
```json
[
  {
    "apiKeyId": 42,
    "integrationId": 123,
    "integrationKey": "ezkey_ikey_a1b2c3d4e5f6g7h8i9j0",
    "description": "Production Server API Key",
    "active": true,
    "createdAt": "2025-10-17T12:00:00Z",
    "expiresAt": "2025-12-31T23:59:59Z",
    "lastUsedAt": "2025-10-17T15:30:00Z",
    "ipWhitelist": ["192.168.1.0/24"]
  }
]
```

---

### c) Get API Key Details

**GET /api/v1/api-keys/{keyId}**

Retrieves details of a specific API key. Secret key is never included.

**Request:**
```http
GET /api/v1/api-keys/42
Authorization: Bearer ezkey_admin_token...
```

**Response (200 OK):**
```json
{
  "apiKeyId": 42,
  "integrationId": 123,
  "integrationKey": "ezkey_ikey_a1b2c3d4e5f6g7h8i9j0",
  "description": "Production Server API Key",
  "active": true,
  "createdAt": "2025-10-17T12:00:00Z",
  "expiresAt": "2025-12-31T23:59:59Z",
  "lastUsedAt": "2025-10-17T15:30:00Z",
  "ipWhitelist": ["192.168.1.0/24"]
}
```

---

### d) Revoke API Key

**DELETE /api/v1/api-keys/{keyId}**

Immediately revokes an API key, making it unusable. Preserved for audit.

**Request:**
```http
DELETE /api/v1/api-keys/42
Authorization: Bearer ezkey_admin_token...
```

**Response (204 No Content):**
```http
HTTP/1.1 204 No Content
```

**Use Cases:**
- Compromised key security incident
- Key rotation cleanup after deploying new key
- Decommissioning an application

---

### e) Using API Keys for Authentication

Once created, use API keys with HTTP Basic Auth for all admin API calls:

**Example: Create Auth Attempt with API Key**
```http
POST /api/v1/auth-attempts
Authorization: Basic base64(ezkey_ikey_xxx:ezkey_skey_xxx)
Content-Type: application/json

{
  "enrollmentId": 456,
  "challengeRequested": false
}
```

**Rate Limiting:**
- 1000 requests per hour per integration key
- Higher than admin login (designed for server usage)
- Returns 429 Too Many Requests when limit exceeded

---

### f) API Key Rotation Best Practices

**Recommended Rotation Process:**

1. **Create new API key** (keep old one active)
2. **Update application config** with new keys
3. **Deploy and test** application with new keys
4. **Monitor** that new key is being used (check lastUsedAt)
5. **Revoke old key** once migration complete

**Transition Period Support:**
- Up to 5 active API keys per integration
- Allows zero-downtime key rotation
- Both old and new keys work during migration

**Automatic Expiration:**
- Set `expiresAt` when creating keys
- System sends warnings before expiration (30/7/1 days)
- Expired keys automatically deactivated
- Forces regular rotation for enhanced security

---

### a) Authentication request management

**GET    /api/v1/auth-attempts**          // List all authentication requests
**POST   /api/v1/auth-attempts**         // Create an authentication request
**GET    /api/v1/auth-attempts/{id}**    // Read a request
**GET    /api/v1/auth-attempts/{id}/wait** // Wait for authentication response (POLLING)
**DELETE /api/v1/auth-attempts/{id}**    // Delete a request

#### **New Endpoint: Authentication Response Waiting**

**GET /api/v1/auth-attempts/{id}/wait**

- **Description**: Allows applications to wait for the mobile device's response to an authentication request. Implements a polling mechanism with configurable timeout for synchronous behavior in the asynchronous MFA flow.

**Query parameters:**
- `timeout` (optional, default: 30s): Maximum wait duration in seconds (1-300)
- `polling` (optional, default: 2s): Polling interval in seconds (1-60)

**Request**
```http
GET /api/v1/auth-attempts/123/wait?timeout=30&polling=2
```

**Response**
- 200 OK: Authentication completed
```json
{
  "authAttempt": {
    "authAttemptId": 123,
    "enrollmentId": 456,
    "authAttemptStatus": "ACCEPTED",
    "authAttemptChallenge": 789012,
    "authAttemptProofToken": "EZK-XYZ789-ABC123",
    "createdAt": "2024-06-01T12:34:56Z",
    "expiresAt": "2024-06-01T12:39:56Z"
  },
  "status": "ACCEPTED",
  "completed": true,
  "timeoutReached": false,
  "waitDuration": 15,
  "completedAt": "2024-06-01T12:35:11Z"
}
```

- 408 Request Timeout: Timeout reached, authentication still pending
- 404 Not Found: Auth attempt not found
- 400 Bad Request: Invalid parameters

**Status calculation rules (according to ENDPOINT.md):**
1. `authAttemptRead` null or false: **PENDING**
2. `authAttemptRead` true and `authAttemptResponded` null or false: **READ**
3. `authAttemptValid` null or false: **INVALID**
4. `authAttemptAccepted` null or false: **REJECTED** else **ACCEPTED**

**âš ï¸ Supersession Rule:**
When a newer authentication attempt is created for the same enrollment, older attempts are considered **EXPIRED** even if they haven't reached their timeout. This ensures that only the most recent authentication request for a person is valid.

**GET Example**
```json
{
  "authAttemptId": 49,
  "enrollmentId": 61,
  "authAttemptStatus": "PENDING",
  "authAttemptChallenge": null,
  "authAttemptProofToken": "KstrTWXbywp5Zi-ACI1kIzGrj9thTUkn_-lcOxQxYR0.1755796478548.1HGz9A4uEyYjqdxbYg9U7A",
  "createdAt": "2025-08-21T13:14:38.548701",
  "expiresAt": "2025-08-21T13:19:38.548701"
}
```

To display a status associated with a request, the `authAttemptStatus` field provides the calculated status:
- **PENDING**: Authentication request created, waiting for device response
- **READ**: Device has read the request but not yet responded
- **INVALID**: Authentication failed validation
- **REJECTED**: User denied the authentication request
- **ACCEPTED**: User approved the authentication request
- **EXPIRED**: Superseded by a newer authentication attempt

**Creation example**
```http
POST /api/v1/auth-attempts
Content-Type: application/json

{
  "enrollmentId": 123,
  "challengeRequested": false
}
```

**Response**
- 201 Created + created auth attempt details
```json
{
  "authAttemptId": 11
}
```

### b) Integration management (CRUD)

**GET    /api/v1/integrations**           // Retrieve all integrations
**GET    /api/v1/integrations/{id}**      // Retrieve an integration by ID
**POST   /api/v1/integrations**          // Create a new integration
**DELETE /api/v1/integrations/{id}**     // Delete an integration

**Creating an integration**

**Note**: Both `logo` and `i18n` fields are **optional**. The System Integration (used for global admin authentication) is created without logo or i18n data. Regular integrations typically include i18n for multi-language support in client applications.

**Example with i18n (recommended for regular integrations):**
```http
POST /api/v1/integrations
Content-Type: application/json

{
  "logo": "https://example.com/logo.png",
  "i18n": [
    {
      "language": "en",
      "name": "ACME Corporation",
      "description": "Secure authentication system for ACME applications"
    },
    {
      "language": "fr",
      "name": "Corporation ACME",
      "description": "SystÃ¨me d'authentification sÃ©curisÃ© pour les applications ACME"
    }
  ]
}
```

**Example without i18n (valid, used for System Integration):**
```http
POST /api/v1/integrations
Content-Type: application/json

{
  "logo": "https://example.com/logo.png"
}
```

**Response**
- 201 Created + created integration details
```json
{
  "id": 42
}
```

**Retrieving an integration**
```http
GET /api/v1/integrations/42
```

**Response**
- 200 OK + complete integration details
```json
{
  "id": 42,
  "logo": "https://example.com/logo.png",
  "active": true,
  "createdAt": "2024-06-01T12:34:56Z",
  "i18n": [
    {
      "id": 1,
      "language": "en",
      "name": "ACME Corporation",
      "description": "Secure authentication system for ACME applications"
    }
  ]
}
```

### c) Enrollment management (CRUD)

**GET    /api/v1/enrollments**           // Retrieve all enrollments
**GET    /api/v1/enrollments/{id}**      // Retrieve an enrollment by ID
**POST   /api/v1/enrollments**          // Create a new enrollment
**DELETE /api/v1/enrollments/{id}**     // Delete an enrollment

**Creating an enrollment**
```http
POST /api/v1/enrollments
Content-Type: application/json

{
  "integrationId": 123,
  "name": "My Mobile Device",
  "authAttemptChallengeRequired": true
}
```

**Response**
- 201 Created + created enrollment details
```json
{
  "enrollmentId": 456,
  "enrollmentChallenge": 987654
}
```

**Retrieving an enrollment**
```http
GET /api/v1/enrollments/456
```

**Response**
- 200 OK + complete enrollment details
```json
{
  "enrollmentId": 456,
  "integrationId": 123,
  "enrollmentName": "My Mobile Device",
  "enrollmentStatus": "VERIFIED",
  "enrollmentActive": true,
  "enrollmentChallenge": 987654,
  "enrollmentProofToken": "EZK-ABC123-DEF456",
  "authAttemptChallengeRequired": true,
  "integrationPublicKey": "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
  "devicePublicKey": "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA..."
}
```

**Retrieving all enrollments**
```http
GET /api/v1/enrollments
```

**Response**
- 200 OK + list of all enrollments
```json
[
  {
    "enrollmentId": 456,
    "integrationId": 123,
    "enrollmentName": "My Mobile Device",
    "enrollmentStatus": "VERIFIED",
    "enrollmentActive": true,
    "enrollmentChallenge": 987654,
    "enrollmentProofToken": "EZK-ABC123-DEF456",
    "authAttemptChallengeRequired": true,
    "integrationPublicKey": "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
    "devicePublicKey": "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA..."
  }
]
```

**Deleting an enrollment**
```http
DELETE /api/v1/enrollments/456
```

**Response**
- 204 No Content (successful deletion)
- 404 Not Found (enrollment not found)

---

## 3. Design choices summary

- **pending**: clearly expresses the pending request for a given enrollment.
- **respond**: standard, explicit for response submission.
- **wait**: clearly indicates the intention to wait for authentication response.
- **POST for pending**: allows transmission of crypto signature in the body, strengthening security.
- **GET for wait**: read operation without modification, with query parameters for configuration.
- **Clear separation** between management (admin-api) and consumption (auth-api).
- **Enrollment process**: clear separation between binding (GET) and verification (POST) for security.
- **Enrollment CRUD**: classic administrative enrollment management operations.

---

## 4. Additional notes

- Always document return codes (200, 204, 400, 401, 403, 408, etc.).
- Explain in the documentation that the model is pull (no push notification).
## ðŸ” Cryptographic Key and Signature Formats

### EC P-256 Key Format

All cryptographic keys in Ezkey use **EC P-256 (secp256r1)** with **ECDSA-SHA256** for digital signatures.

#### **Public Keys**
- **Format**: X.509 SubjectPublicKeyInfo (ASN.1 DER encoded)
- **Curve**: secp256r1 (NIST P-256)
- **Encoding**: Base64 (standard encoding for REST APIs)
- **Example**: `"MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE..."`
- **Size**: ~91 bytes Base64 encoded
- **Storage**: TEXT column in database
- **Transmission**: Base64 string in JSON

#### **Private Keys**
- **Format**: PKCS#8 (ASN.1 DER encoded)
- **Curve**: secp256r1 (NIST P-256)
- **Encoding**: Base64 (standard encoding for REST APIs)
- **Example**: `"MIGHAgEAMBMGByqGSM49AgEGCCqGSM49AwEHBG0wawIBAQQg..."`
- **Size**: ~138 bytes Base64 encoded
- **Storage**: Encrypted at rest in database
- **Transmission**: Base64 string in JSON (when needed)

**Note**: EC P-256 keys use standard PKCS#8 (private) and X.509 (public) formats for compatibility with native mobile hardware-backed keystores (Android Keystore, iOS Secure Enclave).

### EC P-256 Signature Format (ECDSA-SHA256)

#### **Signatures**
- **Algorithm**: ECDSA with SHA-256
- **Format**: ASN.1 DER encoded (variable length)
- **Encoding**: Base64 (standard encoding for REST APIs)
- **Size**: ~70 bytes Base64 encoded (variable due to ASN.1 DER encoding)
- **Example**: `"MEQCI..."`
- **Transmission**: Base64 string in JSON

**Note**: ECDSA signatures are non-deterministic (different signatures for same data) but both are valid. The signature format is ASN.1 DER encoded (r, s) components.

### Mutual Cryptographic Authentication

Ezkey implements mutual cryptographic authentication:

- **Backend → Mobile**: Backend signs `authAttemptProofToken` with `integration_private_key` (EC P-256, PKCS#8), mobile verifies with `integration_public_key` (EC P-256, X.509)
- **Mobile → Backend**: Mobile signs responses with `device_private_key` (EC P-256, hardware-backed), backend verifies with `device_public_key` (EC P-256, X.509)

This ensures both parties can cryptographically verify each other's authenticity.
- The `/wait` endpoint enables synchronous behavior in the asynchronous MFA flow.

---

**This file serves as a reference for the design and future documentation of Ezkey endpoints.**

---

## 5. Authentication Attempt Supersession

### **Business Rule: One Active Attempt Per Person**

Ezkey implements a **supersession rule** to ensure that only the most recent authentication attempt for a person is valid. This prevents confusion and ensures a clear authentication flow.

#### **ðŸ”„ How It Works**

1. **New Attempt Creation**: When a new authentication attempt is created for an enrollment
2. **Older Attempts Expired**: All previous attempts for the same enrollment are considered **EXPIRED**
3. **Conceptual Expiration**: This happens regardless of the actual timeout of older attempts

#### **ðŸ“‹ Implementation Details**

**APIs Affected:**
- **RESPOND**: Returns `EXPIRED` status if a newer attempt exists
- **WAIT**: Returns `EXPIRED` status if a newer attempt exists

**Database Impact:**
- No data modification (older attempts remain in database)
- Index on `(enrollment_id, created_at DESC)` for performance
- Query checks for newer attempts by `created_at` timestamp

#### **ðŸŽ¯ Use Cases**

1. **User Double-Click**: User accidentally creates multiple requests
2. **Network Issues**: User retries authentication after timeout
3. **Mobile App Refresh**: App creates new request after state loss
4. **Security**: Ensures only the latest request is processed

#### **ðŸ“Š Example Scenario**

```
10:00:00 - User creates AuthAttempt A
10:00:30 - User creates AuthAttempt B (A becomes expired)
10:01:00 - Device tries to respond to A â†’ EXPIRED
10:01:30 - Device responds to B â†’ ACCEPTED
```

#### **ðŸ”§ Technical Implementation**

```sql
-- Check for newer attempts
SELECT * FROM ezkey_auth_attempt 
WHERE enrollment_id = ? AND created_at > ? 
ORDER BY created_at DESC
```

---

## 6. Development rules

### Testing and validation
- **DO NOT perform tests with curl or other validation tools** during development
- **The user will perform the tests** for endpoint validation themselves
- **Focus on implementation** and documentation rather than manual testing
- **Use unit tests and integration tests** for automatic code validation 