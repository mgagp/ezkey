# Ezkey API Endpoint Reference

## General Context

Ezkey separates its backend APIs into two applications:
- **admin-api** (internal): management of authentication requests (CRUD), accessible only to the organization.
- **auth-api** (external, mobile): consumption of authentication requests by the Ezkey mobile application.

The interaction model is **pull**: the mobile device fetches the request to validate (pending) by providing a cryptographic signature in the body, ensuring the authenticity of the request.

---

## 📱 User-Initiated Polling Model

### **Critical Design Principle: User Control**

The pending endpoint (`POST /api/v1/auth-attempts/pending/{enrollmentId}`) follows a **user-initiated polling model**, not an automated polling system.

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

**POST /api/v1/auth-attempts/pending/{enrollmentId}**

- **Description**: The mobile device queries the backend to check if there is a pending authentication request for its enrollmentId. The body contains a cryptographic signature proving the authenticity of the request.
- **Why POST?**: The crypto signature is transmitted in the body, which is not possible with GET.

**Request**
```http
POST /api/v1/auth-attempts/pending/{enrollmentId}
Content-Type: application/json

{
  "timestamp": 1712345678,
  "signature": "base64-encoded-signature",
  "publicKey": "base64-encoded-public-key"
}
```

**Response**
- 200 OK + pending request details (or 204 No Content if no request)
```json
{
  "authAttemptId": 123,
  "challenge": "...",
  "createdAt": "2024-06-01T12:34:56Z",
  ...
}
```

### b) Submit response to request

**POST /api/v1/auth-attempts/respond/{authAttemptId}**

- **Description**: The mobile device submits the user's response (approved, denied, signature, etc.) for the received authentication request.

**Request**
```http
POST /api/v1/auth-attempts/respond/{authAttemptId}
Content-Type: application/json

{
  "approved": true,
  "responseSignature": "base64-encoded-signature",
  "timestamp": 1712345699
}
```

**Response**
- 200 OK + validation result
```json
{
  "status": "APPROVED"
}
```

### c) Enrollment process (device binding)

**GET /api/v1/enrollments/bind/{enrollmentId}**

- **Description**: Initiates the process of binding an enrollment to a mobile device. The device retrieves the necessary information to start enrollment.

**Request**
```http
GET /api/v1/enrollments/bind/456
```

**Response**
- 200 OK + binding information
```json
{
  "enrollmentId": 456,
  "integrationPublicKey": "base64-encoded-integration-key",
  "enrollmentCode": "EZK-ABC123-DEF456",
  "enrollmentCodeSigned": "base64-encoded-signed-code"
}
```

### d) Enrollment verification

**POST /api/v1/enrollments/verify**

- **Description**: Finalizes the enrollment process by submitting the device's cryptographic keys and the enrollment code signature.

**Request**
```http
POST /api/v1/enrollments/verify
Content-Type: application/json

{
  "enrollmentId": 456,
  "challengeResponse": 987654,
  "devicePublicKey": "base64-encoded-device-public-key",
  "enrollmentCode": "EZK-ABC123-DEF456",
  "enrollmentCodeSigned": "base64-encoded-signed-enrollment-code"
}
```

**Response**
- 200 OK + verification confirmation
```json
{
  "verified": true,
  "enrollmentId": 456,
  "status": "CONFIRMED"
}
```

---

## 2. Admin API Endpoints (internal)

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
    "authAttemptRead": true,
    "authAttemptResponded": true,
    "authAttemptValid": true,
    "authAttemptAccepted": true,
    "createdAt": "2024-06-01T12:34:56Z"
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
```
   {
        "authAttemptId": 49,
        "enrollmentId": 61,
        "authAttemptRead": false,
        "authAttemptResponded": false,
        "authAttemptValid": false,
        "authAttemptAccepted": false,
        "authAttemptChallenge": null,
        "authAttemptProofToken": "KstrTWXbywp5Zi-ACI1kIzGrj9thTUkn_-lcOxQxYR0.1755796478548.1HGz9A4uEyYjqdxbYg9U7A",
        "deviceProofTokenValid": "false",
        "createdAt": "2025-08-21T13:14:38.548701"
    }
```

To display a status associated with a request, here are the rules in order of priority (#1 first)
-authAttemptRead null or false: PENDING
-authAttemptRead and authAttemptResponded null or false: READ
-authAttemptValid null or false: INVALID
-authAttemptAccepted null or false: REJECTED else ACCEPTED

**Creation example**
```http
POST /api/v1/auth-attempts
Content-Type: application/json

{
  "enrollmentId": "abc123",
  "requestedBy": "app-backend",
  "challenge": "...",
  ...
}
```

### b) Enrollment management (CRUD)

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
  "enrollmentCode": "EZK-ABC123-DEF456",
  "enrollmentChallenge": 987654,
  "createdAt": "2024-06-01T12:34:56Z"
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
  "enrollmentRead": false,
  "enrollmentConfirmed": false,
  "enrollmentActive": true,
  "enrollmentChallenge": 987654,
  "authAttemptChallengeRequired": true,
  "integrationPublicKey": "base64-encoded-key",
  "authAttemptPublicKey": null,
  "enrollmentCode": "EZK-ABC123-DEF456",
  "createdAt": "2024-06-01T12:34:56Z"
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
    "enrollmentRead": false,
    "enrollmentConfirmed": false,
    "enrollmentActive": true,
    "enrollmentChallenge": 987654,
    "authAttemptChallengeRequired": true,
    "integrationPublicKey": "base64-encoded-key",
    "authAttemptPublicKey": null,
    "enrollmentCode": "EZK-ABC123-DEF456",
    "createdAt": "2024-06-01T12:34:56Z"
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