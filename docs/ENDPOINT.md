# Ezkey API Endpoint Reference

## General Context

Ezkey separates its backend APIs into two applications:
- **admin-api** (internal): management of authentication requests (CRUD), accessible only to the organization.
- **auth-api** (external, mobile): consumption of authentication requests by the Ezkey mobile application.

The interaction model is **pull**: the mobile device fetches the request to validate (pending) by providing a cryptographic signature in the body, ensuring the authenticity of the request.

---

## 📱 User-Initiated Polling Model

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

## 🔐 Token Security - Critical Design

### **Security Principle: One-Time Use Token**

Ezkey uses a **one-time proof token** system to ensure the integrity and security of the authentication process.

#### **🔑 Two Types of Tokens**

1. **`enrollmentProofToken`**: Permanent enrollment token, used to bind the device
2. **`authAttemptProofToken`**: Unique token per authentication attempt, **CRITICAL for security**

#### **🛡️ Security Mechanism**

**Step 1 - PENDING**:
- The server generates a unique `authAttemptProofToken` for each attempt
- This token is **encrypted** with the integration's public key when sent
- The mobile device **decrypts** the token with its private key
- **The token can only be read once** (by the PENDING request)

**Step 2 - RESPOND**:
- The mobile device **signs** the `authAttemptProofToken` (in clear) with its private key
- This signature proves that the device has received the original token
- **Enhanced security**: impossible to replay an attempt without having the original token

#### **⚠️ Critical Points for Developers**

```java
// ❌ INCORRECT - Never sign enrollmentProofToken for RESPOND
String signature = signWithDeviceKey(enrollmentProofToken);

// ✅ CORRECT - Always sign authAttemptProofToken for RESPOND  
String signature = signWithDeviceKey(authAttemptProofToken);
```

#### **🎯 Why This Design?**

1. **Anti-replay**: Each attempt has a unique token
2. **Strong authentication**: Only the legitimate device can decrypt and sign
3. **Traceability**: Each token can be traced to a specific attempt
4. **Security by default**: Impossible to bypass without understanding the mechanism

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
  "deviceProofToken": "eyJhbGciOiJSUzI1NiJ9...",
  "deviceProofTokenSigned": "eyJhbGciOiJSUzI1NiJ9..."
}
```

**Response**
- 200 OK + pending request details (or 204 No Content if no request)
```json
{
  "authAttemptId": 123,
  "authAttemptProofToken": "eyJhbGciOiJSUzI1NiJ9...",
  "authAttemptProofTokenSignedByIntegration": "eyJhbGciOiJSUzI1NiJ9...",
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
  "authAttemptProofTokenSignedByDevice": "eyJhbGciOiJSUzI1NiJ9...",
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
  "enrollmentProofToken": "eyJhbGciOiJSUzI1NiJ9...",
  "integrationPublicKey": "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA...",
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
  "devicePublicKey": "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA...",
  "enrollmentProofTokenSigned": "eyJhbGciOiJSUzI1NiJ9..."
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

### Admin Authentication

Base URL: `http://localhost:9080/api/v1/admin/auth`

#### POST /login
Authenticate admin user and receive bearer token for API access.

**Request:**
```http
POST /api/v1/admin/auth/login
Content-Type: application/json

{
  "username": "admin",
  "password": "secure-password-here"
}
```

**Success Response (200 OK):**
```json
{
  "success": true,
  "bearerToken": "ezkey_abc123def456...",
  "adminType": "GLOBAL_ADMIN",
  "username": "admin",
  "expiresAt": "2025-10-04T10:00:00Z",
  "passwordChangeRequired": false,
  "message": "Authentication successful"
}
```

**Failure Response (400 Bad Request):**
```json
{
  "success": false,
  "bearerToken": null,
  "adminType": null,
  "username": null,
  "expiresAt": null,
  "passwordChangeRequired": false,
  "message": "Invalid credentials"
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
- 5 requests per 5 minutes per IP address
- Automatic IP blocking after 10 consecutive failures (30 minutes)
- HTTP 429 when limit exceeded
- `Retry-After` header indicates seconds until retry allowed

**Security Notes:**
- Password transmitted over HTTPS only
- Bearer token expires after 24 hours
- Failed attempts logged for security monitoring
- IP-based rate limiting prevents brute force attacks

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

**⚠️ Supersession Rule:**
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
      "description": "Système d'authentification sécurisé pour les applications ACME"
    }
  ]
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
  "integrationPublicKey": "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA...",
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
    "integrationPublicKey": "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA...",
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
- Specify the expected format for signature and public keys.
- The `/wait` endpoint enables synchronous behavior in the asynchronous MFA flow.

---

**This file serves as a reference for the design and future documentation of Ezkey endpoints.**

---

## 5. Authentication Attempt Supersession

### **Business Rule: One Active Attempt Per Person**

Ezkey implements a **supersession rule** to ensure that only the most recent authentication attempt for a person is valid. This prevents confusion and ensures a clear authentication flow.

#### **🔄 How It Works**

1. **New Attempt Creation**: When a new authentication attempt is created for an enrollment
2. **Older Attempts Expired**: All previous attempts for the same enrollment are considered **EXPIRED**
3. **Conceptual Expiration**: This happens regardless of the actual timeout of older attempts

#### **📋 Implementation Details**

**APIs Affected:**
- **RESPOND**: Returns `EXPIRED` status if a newer attempt exists
- **WAIT**: Returns `EXPIRED` status if a newer attempt exists

**Database Impact:**
- No data modification (older attempts remain in database)
- Index on `(enrollment_id, created_at DESC)` for performance
- Query checks for newer attempts by `created_at` timestamp

#### **🎯 Use Cases**

1. **User Double-Click**: User accidentally creates multiple requests
2. **Network Issues**: User retries authentication after timeout
3. **Mobile App Refresh**: App creates new request after state loss
4. **Security**: Ensures only the latest request is processed

#### **📊 Example Scenario**

```
10:00:00 - User creates AuthAttempt A
10:00:30 - User creates AuthAttempt B (A becomes expired)
10:01:00 - Device tries to respond to A → EXPIRED
10:01:30 - Device responds to B → ACCEPTED
```

#### **🔧 Technical Implementation**

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