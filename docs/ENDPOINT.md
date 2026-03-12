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
- This token is **signed** by the backend with the integration's private key
- The mobile device **verifies** the signature using the integration's public key
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

## 📅 Datetime Format - UTC Standard

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
  "authAttemptChallengeRequired": true,
  "contextTitle": "Payment Authorization",
  "contextMessage": "Authorize payment of $5,000 to Suppliers Ltd. for invoice INV-2025-042."
}
```

**Response Fields (context — all nullable):**
- `contextTitle`: Short heading displayed as the card header on the mobile device. Null when no context was attached.
- `contextMessage`: Descriptive body explaining what the approver is authorizing. Null when no context was attached.

### b) Submit response to request

**POST /api/v1/auth-attempts/respond**

- **Description**: The mobile device submits the user's response (approved, denied, signature, etc.) for the received authentication request.
- **Rate limiting**: When rate limiting is enabled (`ezkey.rate-limit.enabled=true`), this endpoint is limited **per auth attempt** (by `authAttemptId` from the request body). Default: 1 request per 5 minutes per `authAttemptId`. If the body is missing or invalid, the limit is applied per client IP. When exceeded, the API returns **429 Too Many Requests** with a `Retry-After` header.
- **One attempt per auth request**: The backend invalidates the authentication attempt on **first failed validation** (invalid signature, wrong challenge, or missing device key). There is no retry: after one failure the attempt is marked INVALID and the user must start a new authentication flow from the integrating application (e.g. log in again and receive a new pending request).

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
- **200 OK** + validation result
- **429 Too Many Requests** when rate limit is exceeded (same `authAttemptId` or same client IP when body cannot be parsed); response includes a `Retry-After` header (seconds).
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

**Error responses**
- **400 Bad Request**: Invalid verification data. For example, wrong `challengeResponse` returns message "Invalid challenge response". The enrollment is then invalidated; a subsequent verify call for the same enrollment will return 409.
- **409 Conflict**: Enrollment not in a state that allows verification. If the enrollment was invalidated due to a previous failed verification attempt (e.g. wrong challenge), the message states that the enrollment was invalidated due to a previous failed verification attempt.

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

---

### Audit log and chain checkpoint APIs

**Base path:** `http://localhost:9080/api/v1/audit-logs` (Admin API). Global Admin only for chain and integrity endpoints.

**GET /api/v1/audit-logs/chain-checkpoints** — Search audit chain checkpoints with pagination and optional filters. Use for operator visibility, SEAL range selection, and Declare Gap (anchor checkpoint) workflows.

**Query parameters (all optional):** `windowStartAfter`, `windowStartBefore` (ISO-8601), `entryCountMin`, `entryCountMax`, `checkpointType` (REGULAR, ARCHIVE_SEAL, GAP_DECLARATION), `createdAfter`, `createdBefore` (ISO-8601), plus `page`, `size`, `sort` (e.g. `sort=windowStart,asc`). Default: `size=20`, `sort=windowStart,asc`.

**Response (200 OK):** Paginated response with `content` (array of checkpoint objects: `checkpointId`, `windowStart`, `windowEnd`, `entryCount`, `firstEntryId`, `lastEntryId`, `entriesDigest`, `prevChainHmac`, `chainHmac`, `createdAt`, `checkpointType`, `notes`), `totalElements`, `totalPages`, etc.

See [AUDIT_LOG_INTEGRITY.md](AUDIT_LOG_INTEGRITY.md) for integrity verification, seal-archive, and declare-gap endpoints.

---

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

#### Search Integrations

**GET /api/v1/integrations**

Retrieves integrations with optional filters and pagination. All filter parameters are optional.

**Query Parameters:**
- `page` (optional): Page number (zero-based, default: 0)
- `size` (optional): Page size (default: 20)
- `sort` (optional): Sort field and direction (e.g., `createdAt,desc`). Sortable fields: `id`, `createdAt`, `active`
- `integrationName` (optional): Filter by integration name (partial match, case-insensitive)
- `active` (optional): Filter by active flag (true/false)
- `createdAfter` (optional): Filter integrations created after this timestamp (ISO-8601)
- `createdBefore` (optional): Filter integrations created before this timestamp (ISO-8601)
- `tenantId` (optional): Filter by tenant ID. **GlobalAdmin only**; when provided, limits results to that tenant. **Ignored for TenantAdmin** (they always see only their tenant).

**Multi-Tenancy:**
- **GlobalAdmin**: Sees all integrations by default; use `tenantId` to restrict to a specific tenant.
- **TenantAdmin**: Sees only integrations in their tenant; `tenantId` query parameter is ignored.

**Response (200 OK):** Paginated response with `content` (array of integration objects), `totalElements`, `totalPages`, etc. System integrations are excluded from the listing.

---

## Tenant management

Tenant management endpoints allow GlobalAdmins to create, list, update, deactivate, and activate tenants. TenantAdmins can list and get only their own tenant.

**Lifecycle:** A tenant is either **active** or **inactive**. Deactivation sets `active = false`, revokes all admin tokens for that tenant, and blocks new integrations, enrollments, and API keys; data is preserved for audit. Activation sets `active = true` and restores full access; deactivation metadata (who/when) is preserved for traceability. The **system tenant** cannot be deactivated.

**Base path:** `http://localhost:9080/api/v1/tenants` (Admin API). All endpoints require Bearer token (GlobalAdmin for list, create, update, deactivate, activate; GlobalAdmin or TenantAdmin for get by ID).

### Create tenant

**POST /api/v1/tenants**

Creates a new tenant. GlobalAdmin only.

**Request body:** `tenantName` (required), `tenantDescription`, `organizationName`, `organizationDomain`, `countryCode`, `timezone`, `primaryContactName`, `primaryContactEmail` (all optional except tenantName).

**Response (201 Created):** Tenant object with `tenantId`, `tenantName`, `active`, `isSystemTenant`, etc.

### List tenants

**GET /api/v1/tenants**

Lists tenants with server-side pagination and optional filters. GlobalAdmin only (TenantAdmin receives 403).

**Query parameters:**
- `page` (optional, default 0): Zero-based page index.
- `size` (optional, default 20): Page size.
- `sort` (optional, default `createdAt,DESC`): Sort property and direction (e.g. `tenantId,asc`, `tenantName,desc`, `active,asc`, `createdAt,desc`).
- `tenantName` (optional): Filter by tenant name (partial match, case-insensitive).
- `active` (optional): Filter by active flag (`true` or `false`); omit to return all.

**Response (200 OK):** Paginated envelope with `content` (array of tenant objects) and `page` (metadata: `size`, `number`, `totalElements`, `totalPages`).

### Get tenant by ID

**GET /api/v1/tenants/{id}**

Returns a tenant by ID. GlobalAdmin can access any tenant; TenantAdmin only their own.

**Response (200 OK):** Tenant object. **404** if not found or not authorized.

### Update tenant

**PUT /api/v1/tenants/{id}**

Partial update of tenant (name, description, organization fields, contact). Only non-null fields are applied. GlobalAdmin only. Blocked if tenant is inactive (returns 400 with RFC 9457 ProblemDetail). **The system tenant cannot be updated** (returns 400 with RFC 9457 ProblemDetail, type `tenant-not-allowed`).

**Response (200 OK):** Updated tenant object.

### Deactivate tenant

**POST /api/v1/tenants/{id}/deactivate**

Deactivates a tenant: sets `active = false`, records `deactivatedAt` and `deactivatedByAdmin`, revokes all active admin tokens for that tenant. GlobalAdmin only. The system tenant cannot be deactivated (400).

**Request body (optional):** `{ "reason": "Optional audit reason (10–500 chars)" }`

**Response (204 No Content).**

### Activate tenant

**POST /api/v1/tenants/{id}/activate**

Activates a previously deactivated tenant: sets `active = true`. Idempotent if the tenant is already active. GlobalAdmin only. Deactivation metadata is preserved for audit.

**Request body (optional):** `{ "reason": "Optional audit reason (10–500 chars)" }`

**Response (204 No Content).**

---

## 👥 Administrator Provisioning

### **Overview**

Administrator provisioning endpoints allow GlobalAdmins and TenantAdmins to create peer administrators with proper limits enforcement and secure onboarding credential management.

**Security Pattern:**
- Creation endpoints return only basic admin information (no sensitive credentials)
- Sensitive credentials (enrollmentProofToken, enrollmentChallenge) are retrieved via separate GET endpoint
- Follows the same security pattern as the enrollment API for consistency

**Multi-Tenancy:**
- **GlobalAdmin**: Can create GlobalAdmins and TenantAdmins for any tenant
- **TenantAdmin**: Can only create TenantAdmins for their own tenant
- **System Tenant**: Cannot create TenantAdmins in System Tenant (tenant_id=1)

---

### a) Create Global Administrator

**POST /api/v1/admins/global**

Creates a new global administrator (peer admin). Only GlobalAdmin can create other GlobalAdmins.

**Request:**
```http
POST /api/v1/admins/global
Authorization: Bearer ezkey_admin_token...
Content-Type: application/json

{
  "username": "new.global.admin",
  "email": "newglobal@example.com",
  "firstName": "New",
  "lastName": "GlobalAdmin"
}
```

**Success Response (201 Created):**
```json
{
  "adminId": 2,
  "username": "new.global.admin",
  "email": "newglobal@example.com",
  "firstName": "New",
  "lastName": "GlobalAdmin",
  "adminType": "GLOBAL_ADMIN",
  "tenantId": null,
  "enrollmentId": 123,
  "createdAt": "2025-12-26T14:30:00Z"
}
```

**Note:** Sensitive onboarding credentials are NOT returned in this response. Use GET /api/v1/admins/{id}/onboarding to retrieve them.

**Status Codes:**
- 201: Global administrator created successfully
- 400: Invalid data, limit exceeded, username exists, or email exists
- 401: Unauthorized - admin token required
- 403: Forbidden - not a GlobalAdmin
- 500: Internal server error

---

### b) Create Tenant Administrator

**POST /api/v1/admins/tenant**

Creates a new tenant administrator (peer admin). GlobalAdmin can create TenantAdmins for any tenant. TenantAdmin can create TenantAdmins for their own tenant only.

**Request:**
```http
POST /api/v1/admins/tenant
Authorization: Bearer ezkey_admin_token...
Content-Type: application/json

{
  "username": "new.tenant.admin",
  "email": "newtenant@example.com",
  "firstName": "New",
  "lastName": "TenantAdmin",
  "tenantId": 2
}
```

**Success Response (201 Created):**
```json
{
  "adminId": 3,
  "username": "new.tenant.admin",
  "email": "newtenant@example.com",
  "firstName": "New",
  "lastName": "TenantAdmin",
  "adminType": "TENANT_ADMIN",
  "tenantId": 2,
  "enrollmentId": 124,
  "createdAt": "2025-12-26T14:35:00Z"
}
```

**Note:** Sensitive onboarding credentials are NOT returned in this response. Use GET /api/v1/admins/{id}/onboarding to retrieve them.

**Status Codes:**
- 201: Tenant administrator created successfully
- 400: Invalid data, limit exceeded, username exists, email exists, tenant not found, or System Tenant
- 401: Unauthorized - admin token required
- 403: Forbidden - TenantAdmin trying to create for different tenant
- 500: Internal server error

---

### c) Retrieve Onboarding Credentials

**GET /api/v1/admins/{id}/onboarding**

Retrieves sensitive onboarding credentials for an administrator. These credentials are separated from the creation response for security reasons.

**Permissions:**
- **GlobalAdmin**: Can retrieve onboarding credentials for any admin
- **TenantAdmin**: Can only retrieve onboarding credentials for admins in their tenant

**Request:**
```http
GET /api/v1/admins/2/onboarding
Authorization: Bearer ezkey_admin_token...
```

**Success Response (200 OK):**
```json
{
  "enrollmentId": 123,
  "enrollmentProofToken": "EZK-ABC123-DEF456-GHI789-JKL012-MNO345-PQR678-STU901-VWX234",
  "enrollmentChallenge": 654321,
  "recoveryCodes": null
}
```

**Note on Recovery Codes:**
- Recovery codes are stored as BCrypt hashes and cannot be retrieved in plain text after initial provisioning
- Recovery codes must be saved immediately during admin creation (they are returned in ProvisioningResult at service layer)
- This endpoint returns `recoveryCodes: null` because they cannot be retrieved after initial provisioning

**Status Codes:**
- 200: Onboarding credentials retrieved successfully
- 400: Bad request (e.g., admin does not have an enrollment)
- 401: Unauthorized - admin token required
- 403: Forbidden - not authorized to access this admin's credentials
- 404: Administrator or enrollment not found
- 500: Internal server error

---

### d) Generate Onboarding QR Code

**GET /api/v1/admins/{id}/onboarding/qrcode**

Generates a PNG QR code image containing enrollment credentials for administrator onboarding. The QR code can be scanned by the mobile application for passwordless enrollment binding.

**Permissions:**
- **GlobalAdmin**: Can generate QR codes for any admin
- **TenantAdmin**: Can only generate QR codes for admins in their tenant

**Request:**
```http
GET /api/v1/admins/2/onboarding/qrcode
Authorization: Bearer ezkey_admin_token...
```

**Success Response (200 OK):**
- Content-Type: `image/png`
- Body: PNG image bytes (300x300 pixels)
- Content-Disposition: `inline; filename=admin-2-onboarding-qrcode.png`

**QR Code Format:**
The QR code contains enrollment credentials as a JSON payload:

```json
{"enrollmentId":"123","enrollmentProofToken":"abc...","authUrl":"https://ezkey.acme.com:8080"}
```

The `authUrl` field is included only when `ezkey.qr.auth-base-url` is configured. When absent, the mobile app falls back to its default base URL.

This allows the mobile app to automatically populate enrollment credentials and connect to the correct auth-api instance when scanning the QR code, eliminating manual entry and reducing errors.

**Status Codes:**
- 200: QR code generated successfully
- 400: Bad request (e.g., enrollment missing proof token)
- 401: Unauthorized - admin token required
- 403: Forbidden - not authorized to access this admin's credentials
- 404: Administrator or enrollment not found
- 500: Internal server error

---

### e) List Administrators

**GET /api/v1/admins**

Lists administrators with tenant-based filtering. GlobalAdmin sees all administrators across all tenants. TenantAdmin sees only administrators from their tenant.

**Request:**
```http
GET /api/v1/admins?page=0&size=20&sort=createdAt,DESC
Authorization: Bearer ezkey_admin_token...
```

**Query Parameters:**
- `page` (optional, default: 0): Page number (zero-based)
- `size` (optional, default: 20): Number of items per page
- `sort` (optional, default: createdAt,DESC): Sort field and direction
- `active` (optional): Filter by active status
- `adminType` (optional): Filter by admin type (GLOBAL_ADMIN, TENANT_ADMIN)

**Success Response (200 OK):**
```json
{
  "content": [
    {
      "adminId": 1,
      "username": "admin.docker",
      "email": "admin@example.com",
      "firstName": "Admin",
      "lastName": "Docker",
      "adminType": "GLOBAL_ADMIN",
      "tenantId": null,
      "active": true,
      "createdAt": "2025-12-26T10:00:00Z",
      "lastLoginAt": "2025-12-28T09:15:00Z"
    }
  ],
  "totalElements": 1,
  "totalPages": 1,
  "size": 20,
  "number": 0,
  "first": true,
  "last": true
}
```

**Response fields:** `lastLoginAt` is the timestamp of the administrator's last successful login (null if never logged in). Useful for access reviews and SOC 2 procedures.

**Status Codes:**
- 200: List of administrators retrieved successfully
- 401: Unauthorized - admin token required
- 403: Forbidden - not an administrator
- 500: Internal server error

---

### e2) Get Administrator by ID

**GET /api/v1/admins/{id}**

Returns a single administrator by ID. Same shape as a list item (including `lastLoginAt`).

**Permissions:** GlobalAdmin can access any admin. TenantAdmin can only access admins in their own tenant.

**Request:**
```http
GET /api/v1/admins/2
Authorization: Bearer ezkey_admin_token...
```

**Success Response (200 OK):** Same fields as a list item: `adminId`, `username`, `email`, `firstName`, `lastName`, `adminType`, `tenantId`, `active`, `createdAt`, `lastLoginAt`.

**Status Codes:**
- 200: Administrator retrieved successfully
- 401: Unauthorized - admin token required
- 403: Forbidden - not authorized for this admin
- 404: Administrator not found
- 500: Internal server error

---

### e3) Deactivate Administrator

**POST /api/v1/admins/{id}/deactivate**

Deactivates an administrator account and revokes all active tokens. GlobalAdmin only. Enforces minimum global admin count; self-deactivation is not allowed. Idempotent if the admin is already inactive.

**Query Parameters:**
- `reason` (optional, 10–500 characters): Audit justification for the deactivation.

**Request:**
```http
POST /api/v1/admins/2/deactivate?reason=Offboarding%20per%20HR%20request
Authorization: Bearer ezkey_admin_token...
```

**Success Response:** 204 No Content.

**Status Codes:**
- 204: Administrator deactivated successfully
- 400: Self-deactivation attempted or would violate minimum global admin limit (RFC 9457 Problem Detail)
- 401: Unauthorized - admin token required
- 403: Forbidden - caller is not a global administrator
- 404: Administrator not found
- 500: Internal server error

---

### e4) Activate Administrator

**POST /api/v1/admins/{id}/activate**

Reactivates a previously deactivated administrator. GlobalAdmin only. Sets `active = true`. Idempotent if the admin is already active. The admin must log in again to obtain a new bearer token.

**Request:**
```http
POST /api/v1/admins/2/activate
Authorization: Bearer ezkey_admin_token...
```

**Success Response:** 204 No Content.

**Status Codes:**
- 204: Administrator activated successfully
- 401: Unauthorized - admin token required
- 403: Forbidden - caller is not a global administrator
- 404: Administrator not found
- 500: Internal server error

---

### f) Important: When to Use Admin Onboarding API vs Enrollment API

**Two APIs can retrieve enrollment onboarding credentials. Here's when to use each:**

#### **GET /api/v1/admins/{id}/onboarding** (Recommended for Admin Onboarding)

**Use this API when:**
- ✅ You have an **admin ID** (from `POST /api/v1/admins/global` or `/api/v1/admins/tenant`)
- ✅ You want to retrieve onboarding credentials for a **specific administrator**
- ✅ You're working in the **admin provisioning workflow**
- ✅ You're a **TenantAdmin** accessing your own or peer admin credentials

**Why this API:**
- Validates access at the **admin tenant level** (correct for admin management)
- Returns only onboarding credentials (minimal information)
- Works correctly for **TenantAdmin** accessing their own enrollment
- Semantic clarity: "Get onboarding credentials for admin X"

**Example:**
```http
GET /api/v1/admins/2/onboarding
Authorization: Bearer ezkey_admin_token...
```

#### **GET /api/v1/enrollments/{id}** (For General Enrollment Management)

**Use this API when:**
- ✅ You have an **enrollment ID** (from enrollment search or creation)
- ✅ You want **comprehensive enrollment details** (not just onboarding credentials)
- ✅ You're working in the **enrollment management workflow**
- ✅ You're a **GlobalAdmin** (TenantAdmin may not have access to enrollments via this API)

**Why this API:**
- Validates access at the **integration tenant level** (correct for enrollment management)
- Returns full enrollment details (status, keys, integration info, etc.)
- Suitable for **enrollment monitoring and management**
- Semantic clarity: "Get details of enrollment Y"

**Important Note for TenantAdmin:**
- ⚠️ **TenantAdmin cannot access their own enrollment via this API** (validation checks integration tenant, which is System Tenant)
- ✅ **Use `/api/v1/admins/{id}/onboarding` instead** for TenantAdmin to access their own credentials

**Example:**
```http
GET /api/v1/enrollments/123
Authorization: Bearer ezkey_admin_token...
```

#### **PATCH /api/v1/enrollments/{id}** (Partial Update of Enrollment Metadata)

Partially updates enrollment metadata. Only non-null fields in the request body are applied. Supports `enrollmentName`, `contactEmail`, `expiresAt`, `authAttemptChallengeRequired`. Only active, non-revoked VERIFIED enrollments can be updated. Include `version` from the GET response for optimistic locking.

**Request:**
```http
PATCH /api/v1/enrollments/123
Authorization: Bearer ezkey_admin_token...
Content-Type: application/json

{
  "version": 0,
  "enrollmentName": "John's iPhone",
  "contactEmail": "john@example.com",
  "expiresAt": "2026-12-31T23:59:59Z",
  "authAttemptChallengeRequired": false
}
```

**Response (200 OK):** Updated enrollment object (same shape as GET response, including new `version`).

**Status Codes:**
- 200: Enrollment updated successfully
- 400: Invalid data, enrollment not updatable (revoked/inactive), or validation failed (e.g. duplicate name, expiresAt in past)
- 403: Access denied
- 404: Enrollment not found
- 409: Optimistic lock conflict — resource was modified, re-fetch and retry

**Constraints:**
- `enrollmentName`: must be unique per integration for VERIFIED status
- `expiresAt`: must be in the future if provided; null = no expiration
- Only VERIFIED and active enrollments can be updated

#### **DELETE /api/v1/enrollments/{id}** (Delete Enrollment)

Removes an enrollment from the system. Optional query parameter `reason` (min 10, max 500 characters) is recorded in the audit log for SOC 2 compliance.

**Request:**
```http
DELETE /api/v1/enrollments/42?reason=Device%20decommissioned
Authorization: Bearer ezkey_admin_token...
```

**Response (204 No Content):** Enrollment deleted successfully.

**Status Codes:**
- 204: Enrollment deleted successfully
- 403: Cannot delete your own MFA enrollment (RFC 9457 Problem Detail). Use the recovery flow to reset it.
- 404: Enrollment not found (or no access; existence is not revealed for cross-tenant)
- 409: Enrollment cannot be deleted (RFC 9457 Problem Detail). Either it has authentication history (revoke instead) or it is linked as an administrator's MFA (use the recovery flow to reset that admin's MFA first).
- 500: Internal server error

#### **Summary Table**

| Scenario | Use This API | Why |
|----------|--------------|-----|
| Admin provisioning workflow | `/api/v1/admins/{id}/onboarding` | Validates admin tenant, returns minimal credentials |
| TenantAdmin accessing own credentials | `/api/v1/admins/{id}/onboarding` | Only API that works for TenantAdmin |
| Enrollment management workflow | `/api/v1/enrollments/{id}` | Validates integration tenant, returns full details |
| GlobalAdmin monitoring enrollments | `/api/v1/enrollments/{id}` | Works for GlobalAdmin, comprehensive information |

**Security Note:**
Both APIs use different validation logic (admin tenant vs integration tenant), which is intentional and provides complementary security checks. The different validation approaches ensure proper access control for different use cases.

---

## 🔑 API Keys Authentication (Machine-to-Machine)

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

### b) List all API keys

**GET /api/v1/api-keys**

Lists API keys visible to the current admin with server-side pagination and optional filters. GlobalAdmin sees all keys; TenantAdmin sees only keys for their tenant's integrations. Secret keys are never included.

**Query parameters:**
- `page` (optional, default 0): Zero-based page index.
- `size` (optional, default 20): Page size.
- `sort` (optional, default `createdAt,DESC`): Sort property and direction (e.g. `apiKeyId,asc`, `integrationKey,desc`, `description`, `active`, `createdAt`, `expiresAt`, `lastUsedAt`, `revokedAt`).
- `integrationId` (optional): Filter by integration ID.
- `active` (optional): Filter by active flag (`true` or `false`); omit to return all.
- `description` (optional): Filter by description (partial match, case-insensitive).

**Request:**
```http
GET /api/v1/api-keys?page=0&size=20&sort=createdAt,desc
Authorization: Bearer ezkey_admin_token...
```

**Response (200 OK):** Paginated envelope with `content` (array of API key objects) and `page` (metadata: `size`, `number`, `totalElements`, `totalPages`).

---

### c) List API Keys for Integration

**GET /api/v1/api-keys/integration/{integrationId}**

Lists API keys for a specific integration with server-side pagination. When `active` is omitted, only active keys are returned. When `active` is provided, results are filtered by that value. Secret keys are never included.

**Query parameters:**
- `page` (optional, default 0): Zero-based page index.
- `size` (optional, default 20): Page size.
- `sort` (optional, default `createdAt,DESC`): Sort property and direction.
- `active` (optional): Filter by active flag; omit for active-only (default).

**Request:**
```http
GET /api/v1/api-keys/integration/123?page=0&size=20&sort=createdAt,desc
Authorization: Bearer ezkey_admin_token...
```

**Response (200 OK):** Paginated envelope with `content` (array of API key objects) and `page` (metadata: `size`, `number`, `totalElements`, `totalPages`).

---

### d) Get API Key Details

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

**Response fields:** `version` is included for optimistic locking. Include it in PATCH requests.

---

### e) Partially Update API Key Configuration

**PATCH /api/v1/api-keys/{keyId}**

Partially updates API key configuration (ipWhitelist, description). Only active (non-revoked) keys can be updated. Include `version` from the GET response for optimistic locking.

**Request:**
```http
PATCH /api/v1/api-keys/42
Authorization: Bearer ezkey_admin_token...
Content-Type: application/json

{
  "version": 0,
  "description": "Updated Production Server",
  "ipWhitelist": ["192.168.1.0/24", "10.0.0.100"]
}
```

**Response (200 OK):** Updated API key object (same shape as GET response, including new `version`).

**Status Codes:**
- 200: API key updated successfully
- 400: Invalid data, key revoked, or ipWhitelist validation failed (invalid CIDR)
- 403: Access denied
- 404: API key not found
- 409: Optimistic lock conflict — resource was modified, re-fetch and retry

**Constraints:**
- `ipWhitelist`: each entry must be valid IP or CIDR; null or empty array = removes restrictions
- Only active keys can be updated

---

### f) Revoke API Key

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

### g) Using API Keys for Authentication

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
- Create auth attempt: 10 requests per minute per API key (Admin API); M2M API uses higher defaults (e.g. 100/min)
- Wait and cancel: 20 requests per minute per API key (Admin API); M2M API uses higher defaults (e.g. 200/min)
- Limits are per instance (no distributed coordination); higher than admin login (designed for server usage)
- Returns 429 Too Many Requests when limit exceeded

---

### h) API Key Rotation Best Practices

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

## Authentication Attempts (Admin API)

### a) Create Authentication Attempt

**POST /api/v1/auth-attempts**

Creates a new authentication attempt for MFA validation. This endpoint is used by integrating applications to initiate authentication requests.

Optional context fields (`contextTitle`, `contextMessage`) allow attaching business context to the attempt, which is forwarded to the mobile device and displayed to the approver. This transforms a generic "Approve login?" prompt into a meaningful approval request (e.g. "Authorize payment of $5,000 to Suppliers Ltd.").

**Request (minimal — backward-compatible):**
```http
POST /api/v1/auth-attempts
Authorization: Bearer ezkey_admin_token...
Content-Type: application/json

{
  "enrollmentId": 456,
  "challengeRequested": false
}
```

**Request (with contextual authentication):**
```http
POST /api/v1/auth-attempts
Authorization: Basic base64(ezkey_ikey_xxx:ezkey_skey_xxx)
Content-Type: application/json

{
  "enrollmentId": 456,
  "challengeRequested": false,
  "contextTitle": "Payment Authorization",
  "contextMessage": "Authorize payment of $5,000 to Suppliers Ltd. for invoice INV-2025-042."
}
```

**Request Fields:**
- `enrollmentId`: Enrollment to authenticate (use this OR `userIdentifier`)
- `userIdentifier`: User identifier for lookup within integration scope (use this OR `enrollmentId`)
- `integrationId`: Required when `userIdentifier` is used with admin token; ignored with API key
- `challengeRequested` (**required**): Whether a challenge code is requested
- `contextTitle` (optional): Short heading displayed as card header on mobile, max 200 chars
- `contextMessage` (optional): Descriptive body explaining what the approver is authorizing, max 2 000 chars

**Response (201 Created):**
```json
{
  "authAttemptId": 123,
  "authAttemptChallenge": 42,
  "timeoutSeconds": 120,
  "expiresAt": "2025-01-20T15:34:00.123456Z",
  "contextTitle": "Payment Authorization",
  "contextMessage": "Authorize payment of $5,000 to Suppliers Ltd. for invoice INV-2025-042."
}
```

**Response Fields:**
- `authAttemptId`: Unique identifier of the created authentication attempt
- `authAttemptChallenge`: Optional challenge code (2 digits) if challenge was requested. Null if no challenge was requested.
- `timeoutSeconds`: Maximum time in seconds the user has to respond to the authentication request (currently 120 seconds)
- `expiresAt`: Absolute expiration timestamp when the authentication attempt will expire (UTC format)
- `contextTitle`, `contextMessage`: Context fields echoed back for confirmation (null if not provided)

**Status Codes:**
- 201: Authentication attempt created successfully
- 400: Invalid data (enrollment not found, etc.)
- 401: Unauthorized - authentication required
- 403: Forbidden - access denied to enrollment
- 500: Internal server error

---

### b) Cancel Authentication Attempt

**POST /api/v1/auth-attempts/{id}/cancel**

Cancels a pending or read authentication attempt by marking it as expired. This endpoint allows client applications to proactively abort authentication requests that are still waiting for user response.

**Use Case:** When a user decides to abort the authentication flow (e.g., clicks "Cancel" or navigates away), the client application can call this endpoint to immediately mark the attempt as expired, allowing any waiting threads to terminate promptly.

**Request:**
```http
POST /api/v1/auth-attempts/123/cancel
Authorization: Bearer ezkey_admin_token...
```

**Response (200 OK):**
```json
{
  "authAttemptId": 123,
  "enrollmentId": 456,
  "authAttemptStatus": "EXPIRED",
  "createdAt": "2025-01-20T15:32:00.123456Z",
  "expiresAt": "2025-01-20T15:34:00.123456Z"
}
```

**Status Codes:**
- 200: Authentication attempt cancelled successfully
- 400: Bad Request - authentication attempt already in final state (cannot be cancelled)
- 404: Not Found - authentication attempt not found
- 401: Unauthorized - authentication required
- 403: Forbidden - access denied to authentication attempt
- 500: Internal server error

**Notes:**
- Only attempts in `PENDING` or `READ` status can be cancelled
- Attempts that are already in a final state (`ACCEPTED`, `REJECTED`, `INVALID`, `EXPIRED`) cannot be cancelled
- Supports both Bearer token (admin) and API key (M2M) authentication
- All cancellation operations are audited for security monitoring

---

### c) Wait for Authentication Response

**GET /api/v1/auth-attempts/{id}/wait**

Waits for authentication response completion with configurable timeout and polling. This endpoint enables synchronous-like behavior in the asynchronous MFA authentication flow.

**Request:**
```http
GET /api/v1/auth-attempts/123/wait?timeout=30&polling=2
Authorization: Bearer ezkey_admin_token...
```

**Query Parameters:**
- `timeout` (optional, default: 30): Maximum wait duration in seconds (1-300)
- `polling` (optional, default: 2): Polling interval in seconds (1-60)

**Response (200 OK):**
```json
{
  "authAttempt": {
    "authAttemptId": 123,
    "enrollmentId": 456,
    "authAttemptStatus": "ACCEPTED",
    "createdAt": "2025-01-20T15:32:00.123456Z",
    "expiresAt": "2025-01-20T15:34:00.123456Z"
  },
  "status": "ACCEPTED",
  "completed": true,
  "timeoutReached": false,
  "waitDuration": 5,
  "completedAt": "2025-01-20T15:32:05.123456Z"
}
```

**Status Codes:**
- 200: Authentication completed (or timeout reached)
- 404: Not Found - authentication attempt not found
- 400: Bad Request - invalid parameters (timeout, polling)
- 401: Unauthorized - authentication required
- 403: Forbidden - access denied to authentication attempt
- 500: Internal server error

---

## Encryption Key Management and Re-encryption

Ezkey uses encryption at rest with Tink cryptographic library. Encryption keys are automatically rotated on a schedule, and data encrypted with old keys can be re-encrypted with new keys.

### Encryption Key Endpoints

**GET    /api/v1/encryption-keys**                    // List encryption keys (paginated)
**GET    /api/v1/encryption-keys/primary**           // Get current primary key
**GET    /api/v1/encryption-keys/{keyId}**           // Get key details
**POST   /api/v1/encryption-keys/rotate**            // Manually trigger key rotation
**GET    /api/v1/encryption-keys/reencryption-batches** // List re-encryption batches
**POST   /api/v1/encryption-keys/reencryption-batches/{batchId}/resume** // Resume failed batch

#### List encryption keys

**GET /api/v1/encryption-keys**

Lists encryption keys with server-side pagination and optional filter by status.

**Query parameters:**
- `page` (optional, default 0): Zero-based page index.
- `size` (optional, default 20): Page size.
- `sort` (optional, default `introducedAt,DESC`): Sort property and direction (e.g. `keyId,asc`, `keyStatus,desc`, `introducedAt`, `recordsEncrypted`, `createdBy`, `createdAt`).
- `keyStatus` (optional): Filter by key status. Values: `PRIMARY`, `ENABLED`, `DISABLED`, `PENDING`. Omit for all keys.

**Response (200 OK):** Paginated. Body has `content` (array of encryption key objects) and `page` (object with `size`, `number`, `totalElements`, `totalPages`).

### Re-encryption Trigger Endpoints

These endpoints allow manual triggering of re-encryption operations, useful for testing and emergency operations.

#### a) Trigger Full Re-encryption

**POST /api/v1/encryption-keys/reencrypt/trigger**

Manually triggers the full re-encryption process. Creates batches for all old keys and processes them immediately.

**Use Cases:**
- Testing re-encryption functionality
- Emergency re-encryption after security incident
- Completing re-encryption faster than scheduled job

**Request:**
```http
POST /api/v1/encryption-keys/reencrypt/trigger
Authorization: Bearer ezkey_admin_token...
```

**Response (200 OK):**
```json
{
  "batchesCreated": 8,
  "batchesProcessed": 8,
  "batchesFailed": 0,
  "message": "Full re-encryption completed successfully"
}
```

**Error Responses:**
- **400 Bad Request**: Encryption not available or re-encryption disabled
- **500 Internal Server Error**: Re-encryption processing failed

**Security Considerations:**
- Requires ADMIN role
- All operations are audited
- May process large amounts of data - use with caution in production
- Batches are processed immediately (no throttling limits)

---

#### b) Trigger Re-encryption for Specific Key

**POST /api/v1/encryption-keys/{keyId}/reencrypt**

Creates and processes re-encryption batches for a specific old key. The key must not be PRIMARY.

**Use Cases:**
- Targeted re-encryption for a specific key
- Testing re-encryption for a particular key
- Re-encrypting data after key compromise

**Request:**
```http
POST /api/v1/encryption-keys/12345/reencrypt
Authorization: Bearer ezkey_admin_token...
```

**Path Parameters:**
- `keyId` (Long): The old encryption key ID to re-encrypt (must be ENABLED, not PRIMARY)

**Response (200 OK):**
```json
{
  "keyId": 12345,
  "batchesCreated": 4,
  "batchesProcessed": 4,
  "batchesFailed": 0,
  "message": "Re-encryption for key 12345 completed successfully"
}
```

**Error Responses:**
- **400 Bad Request**: Key is PRIMARY (cannot re-encrypt PRIMARY key) or encryption not available
- **404 Not Found**: Key not found
- **500 Internal Server Error**: Re-encryption processing failed

**Security Considerations:**
- Requires ADMIN role
- All operations are audited
- May process large amounts of data - use with caution in production
- Batches are processed immediately (no throttling limits)

---
