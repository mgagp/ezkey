# Ezkey API Endpoint Reference

## General Context

Ezkey exposes multiple backend surfaces with distinct responsibilities:
- **admin-api**: operator-facing and administrative workflows.
- **auth-api**: mobile enrollment and authentication workflows.
- **integration-api**: machine-to-machine workflows for integrated backends.

This API reference documents the Ezkey protocol surfaces as they exist today. Ezkey should be understood as its own cryptographic MFA approach, not as a FIDO2/WebAuthn compatibility layer.

The interaction model remains **pull-based** on the mobile side: the device fetches the request to validate (`pending`) by providing a cryptographic signature in the body, ensuring authenticity before a response is accepted.

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
- The mobile device **signs** the canonical payload `authAttemptProofToken|accepted` with its private key (not the raw token alone)
- The Auth API **returns** an integration-signed response: `proofToken|authAttemptId|result|message` — verify with the integration public key (see `docs/AUTH_ATTEMPT_SIGNATURE_PAYLOAD.md`)
- **Enhanced security**: impossible to replay an attempt without having the original token; outcome shown to the user is integrity-protected

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
- **Client display**: Admin UI may show timestamps in the browser local zone or the tenant IANA zone (`useDisplayTimezone`). Transport remains UTC.

**Why UTC?**
1. **Timezone independence**: Works globally without ambiguity
2. **API best practice**: Standard for RESTful APIs
3. **Consistency**: All timestamps use the same format
4. **Sortability**: Direct string comparison for chronological ordering

---

## 1. Auth API Endpoints (mobile)

### Error responses (RFC 9457)

HTTP **4xx** and **5xx** responses from the Auth API use **RFC 9457** Problem Details (`Content-Type: application/problem+json`). The JSON body includes `type` (URI identifying the problem category), `title`, `status`, `detail` (operator-safe text; do not rely on it for security-sensitive branching), and extension properties `path` and `timestamp`. RFC 9457 `instance` is the request path (a URI-reference, not an absolute URI). Clients should branch on **`type`** and HTTP status.

Unauthenticated public operations (`GET /api/v1/public/instance-info`) declare no HTTP security requirement in OpenAPI (`security: []`). Device POSTs authenticate with signatures in the JSON body, not a Bearer header.

Rate-limit **429** on `bind` / `verify` / enrolled `instance-info` / `pending` / `respond` is emitted by the servlet filter: **empty body**, `Retry-After` in seconds. It is not a Problem Details document.

Unsupported HTTP methods on a mapped path return **405** Problem Details (`type` `https://ezkey.io/problems/auth/method-not-allowed`) with an `Allow` header listing the supported methods. They are not mapped as **500**.

IP-keyed enrollment surfaces (`verify`, `bind`, enrolled `instance-info`) also have a **process-wide** rate-limit backstop (unkeyed Bucket4j, per Auth instance). A distributed rotation of client IPs can still receive **429** after that instance budget. The cap is not aggregated across replicas (ADR-0010).

**204 No Content** on `POST /api/v1/auth-attempts/pending` when there is no pending attempt is a **success** (no body), not an error.

**200 OK** on `POST /api/v1/auth-attempts/respond` returns a business-level `FAILED` result in the JSON body when validation or cryptographic checks fail in a way modeled as `AuthAttemptRequestFailedException` in the respond service (integration-signed; see `docs/AUTH_ATTEMPT_SIGNATURE_PAYLOAD.md`). A raw `IllegalArgumentException` or other uncaught exception from that flow is not converted to `FAILED` and is handled like other Auth API errors (typically Problem Details **400** for `IllegalArgumentException`). HTTP-level failures for state conflicts (e.g. superseded or expired attempt) use **409** Problem Details as above.

### Public instance metadata (unauthenticated)

**Base path:** `GET http://localhost:8080/api/v1/public/instance-info` (no `Authorization` header, no cryptographic signature).

**Posture:** This Auth GET is **not** the official mobile display path. Branding shown in the official app after enrollment comes only from **Enrolled instance-info** below (integration-signed POST). Do not treat this GET as a trust anchor, and do not use it as a fallback on the enrolled path.

| Surface | Role |
| ----- | ----- |
| `POST /api/v1/enrollments/instance-info` (Auth) | Enrolled branding. Official mobile verifies the integration Ed25519 signature before applying `instanceName` / `instanceDescription` / `aboutUrl`. |
| `GET /api/v1/public/instance-info` (Auth) | Unsigned. Operators, reachability probes, Bruno `g0`. Same JSON as Admin. Possible later retirement: [`I-2026-08-23-auth-unsigned-public-instance-info-retirement`](../product-docs/global/backlog/ideas/I-2026-08-23-auth-unsigned-public-instance-info-retirement.md). |
| `GET /api/v1/public/instance-info` (Admin) | Same JSON on the Admin host. Admin UI login / header. Out of Auth-surface retirement scope. |

Returns the same read-only JSON as the Admin API public instance-info (see **§2 Admin API — Public instance metadata**).

| Field | Source | Notes |
| ----- | ------ | ----- |
| `authApiPublicBaseUrl` | `ezkey.qr.auth-base-url` | Same value embedded as `authUrl` in enrollment QR JSON; `null` when unset |
| `instanceName` | `ezkey.organization.name` | Instance / organization display name |
| `instanceDescription` | `ezkey.organization.description` | Human-facing blurb for this **deployment** |
| `aboutUrl` | `ezkey.organization.about-url` | Optional link for “Learn more”; `null` when unset |

Configure these properties on the Auth API process (e.g. `EZKEY_QR_AUTH_BASE_URL`, `EZKEY_ORGANIZATION_ABOUT_URL` in Docker) so the response matches operator expectations and the Admin API when both are deployed.

### Enrolled instance-info (integration-signed)

**Base path:** `POST http://localhost:8080/api/v1/enrollments/instance-info`

**Posture:** Official mobile display path after enrollment (wizard post-verify and Home stale /
incomplete refresh). Verify the integration signature before applying branding; never fall back to
the unsigned public GET.

Returns the same branding fields as public instance-info, plus `enrollmentId` and an Ed25519
signature (`instanceInfoPayloadSignedByIntegration`) over the canonical `INSTANCE_INFO` payload
(see [ENROLLMENT_SIGNATURE_PAYLOAD.md](ENROLLMENT_SIGNATURE_PAYLOAD.md) § Enrolled instance-info).
The request authenticates with `enrollmentProofToken` only (hash lookup; anti-enumeration). Rate
limiting shares the bind endpoint client-IP budget.

**Request**
```http
POST /api/v1/enrollments/instance-info
Content-Type: application/json

{
  "enrollmentProofToken": "abc123-def456-ghi789"
}
```

**Response (200)**
```json
{
  "enrollmentId": 123,
  "authApiPublicBaseUrl": "https://auth.example.com:8080",
  "instanceName": "Acme Corporation",
  "instanceDescription": "Acme Corp Ezkey MFA",
  "aboutUrl": "https://www.example.com/about-ezkey",
  "instanceInfoPayloadSignedByIntegration": "Base64URL-Ed25519-signature"
}
```

Invalid or unknown proof tokens return RFC 9457 Problem Details **400**
(`enrollment-instance-info-failed`) with a generic detail (no enumeration hints).

### a) Retrieve pending request

**POST /api/v1/auth-attempts/pending**

- **Description**: The mobile device queries the backend to check if there is a pending authentication request for its enrollment. The body contains a cryptographic signature proving the authenticity of the request and an enrollment proof token for secure enrollment identification.
- **Why POST?**: The crypto signature is transmitted in the body, which is not possible with GET.
- **Security Enhancement**: This endpoint now uses enrollmentProofToken in the request body instead of enrollmentId in the URL path to prevent enumeration attacks.
- **Device proof token and 204 responses**: A successful cryptographic signature is still required on every request. When there is **no** pending attempt (**204 No Content**), the server does **not** store the `deviceProofToken`; the mobile client may send the **same** signed `deviceProofToken` on subsequent polls until a pending request exists and is claimed (**200 OK**). At that point the token is recorded and must not be reused for another claimed attempt. Volume abuse is mitigated by **rate limiting** (and infrastructure controls), not by requiring a fresh random on every empty poll.

**Request**
```http
POST /api/v1/auth-attempts/pending
Content-Type: application/json

{
  "enrollmentId": 456,
  "enrollmentProofToken": "EZK-ABC123-DEF456",
  "deviceProofToken": "abc123-def456-ghi789",
  "deviceProofTokenSigned": "<ECDSA-SHA256 DER, standard Base64 — use Crypto API POST /api/v1/crypto/sign; see docs/MOBILE_DEVELOPER_GUIDE.md>"
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
- **Deny vs challenge:** When `authAttemptAccepted` is `false`, challenge response is not required after a valid device signature — the attempt is stored as `REJECTED`. Wrong or missing challenge on **accept** still marks the attempt `INVALID`.

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
- **200 OK** + validation result (integration-signed; verify `authAttemptProofTokenResultSignedByIntegration` with the integration public key). That signature field is **null** when the server could not sign (for example the integration key is unavailable); treat a missing signature as untrusted.
- **429 Too Many Requests** when rate limit is exceeded (same `authAttemptId` or same client IP when body cannot be parsed); **empty body** plus a `Retry-After` header (seconds).
```json
{
  "authAttemptId": 123,
  "authAttemptResult": "APPROVED",
  "authAttemptMessage": "Auth attempt completed",
  "authAttemptProofTokenResultSignedByIntegration": "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
}
```

Canonical signed payload format: see `docs/AUTH_ATTEMPT_SIGNATURE_PAYLOAD.md` (Respond HTTP response).

### c) Enrollment process (device binding)

**POST /api/v1/enrollments/bind**

- **Description**: Initiates the process of binding an enrollment to a mobile device using secure enrollment proof token. The device retrieves the necessary information to start enrollment.

**Request**
```http
POST /api/v1/enrollments/bind
Content-Type: application/json

{
  "enrollmentId": 456,
  "enrollmentProofToken": "abc123-def456-ghi789"
}
```

**Response**
- 200 OK + binding information
```json
{
  "enrollmentId": 456,
  "enrollmentProofToken": "abc123-def456-ghi789",
  "integrationPublicKey": "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
  "integrationKeyAlgorithm": "ed25519",
  "integrationName": "Acme Bank",
  "integrationDescription": "Acme Bank provides secure online banking services.",
  "enrollmentName": "John's iPhone",
  "tenantId": 1,
  "tenantName": "Acme Corporation",
  "tenantDescription": "Acme Corp primary tenant",
  "isSystemIntegration": false,
  "adminType": null,
  "enrollmentBindPayloadSignedByIntegration": "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
}
```

**429 Too Many Requests** when the bind (or shared instance-info) rate-limit or per-instance backstop is exceeded: empty body, `Retry-After` in seconds.

`tenantId`, `tenantName`, and `tenantDescription` come from the enrollment’s integration tenant (nullable if unset). Clients may persist them for multi-tenant enrollment lists (e.g. Demo Device groups by tenant on the home screen).

`isSystemIntegration` is `true` when the enrollment belongs to the system integration (admin MFA). `adminType` is `GLOBAL_ADMIN` or `TENANT_ADMIN` when the enrollment is linked to an `EzkeyAdmin`; omit or null for regular enrollments. Both segments are part of the signed bind payload. Mobile persists them and shows a localized Purpose / Role; it does not parse role out of `enrollmentName`.

`integrationPublicKey` is the **raw 32-byte** Ed25519 public key, **Base64URL without padding** (43 characters). `integrationKeyAlgorithm` is a **required** JSON field in the Auth API contract (OpenAPI); for phase 1 it is always the literal string `ed25519` (lowercase). **Clients should treat it as part of the cryptographic contract:** validate that the value is exactly `ed25519` before decoding `integrationPublicKey` or verifying `enrollmentBindPayloadSignedByIntegration`. If the field is missing or any other string is received, **fail closed** (abort enrollment)—do not assume Ed25519 wire format. `enrollmentBindPayloadSignedByIntegration` is an Ed25519 signature over the canonical bind payload; clients must verify it before trusting the integration key (see `docs/ENROLLMENT_SIGNATURE_PAYLOAD.md`). Device keys in verify requests remain **EC P-256** SPKI (standard Base64).

### d) Enrollment verification

**POST /api/v1/enrollments/verify**

- **Description**: Finalizes the enrollment process by submitting the device's cryptographic keys and an ECDSA signature over the canonical verify payload (`enrollmentProofToken|enrollmentId|challengeResponse|devicePublicKey`; see `docs/ENROLLMENT_SIGNATURE_PAYLOAD.md`).

**Request**
```http
POST /api/v1/enrollments/verify
Content-Type: application/json

{
  "enrollmentId": 456,
  "challengeResponse": 987654,
  "devicePublicKey": "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
  "enrollmentProofTokenSigned": "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
  "devicePrivateKeyStorageTier": "STANDARD"
}
```

`devicePrivateKeyStorageTier` is optional. When present, it must be one of `NONE`, `STANDARD`, or `STRONG`. It is **client-reported only** — the server does not independently verify hardware or Key Attestation; see trust model in `docs/MOBILE_DEVELOPER_GUIDE.md` (**Device private key storage tier — trust model and proof boundary**). Invalid values yield **400**.

**Response**
- 200 OK + verification confirmation (integration-signed outcome)
```json
{
  "active": true,
  "enrollmentVerifyMessage": "Enrollment verified successfully",
  "enrollmentVerifyPayloadSignedByIntegration": "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
}
```

Canonical verify-result format: `docs/ENROLLMENT_SIGNATURE_PAYLOAD.md`.

**Error responses**

See **Error responses (RFC 9457)** above. Verification failures return **400** or **409** with stable `type` URIs under `https://ezkey.io/problems/auth/`. Wrong `challengeResponse` invalidates the enrollment; a subsequent verify call for the same enrollment may return **409**. Rate-limit **429** is an empty body plus `Retry-After` (per client IP and per-instance backstop).

**Uniqueness:** Verify is rejected when **any** `VERIFIED` enrollment already exists for the same `(integrationId, enrollmentName)`, including an inactive one. Partial unique index + application checks; no automatic supersession — see `docs/LIFECYCLE_GOVERNANCE.md` §3.3.

---

## 2. Admin API Endpoints (internal)

**Error responses (RFC 9457):** HTTP **4xx** and **5xx** responses from the Admin API use **RFC 9457** Problem Details (`Content-Type: application/problem+json`) with `type`, `title`, `status`, `detail`, and extension property `path`. RFC 9457 `instance` is the request path (a URI-reference, not an absolute URI). Some problems include an optional extension property **`parameters`** (JSON object of scalar values, **camelCase** keys) for client-side localization when `detail` contains dynamic fragments; clients should still branch on **`type`** and HTTP status. Unsupported HTTP methods on a mapped path return **405** (`type` `https://ezkey.io/problems/admin/method-not-allowed`) with an `Allow` header; they are not mapped as **500**. Missing or invalid session credentials on protected operations return **401** with an empty body (Spring `HttpStatusEntryPoint`). Login-family **400 / 401 / 408 / 500** remain Problem Details. Login and passwordless-wait **429** from the rate-limit filter are an **empty body** plus `Retry-After`.

Public operations (`GET /api/v1/public/instance-info`, `POST /api/v1/public/evaluator-signup`) declare no HTTP security requirement in OpenAPI (`security: []`).

### Public instance metadata (unauthenticated)

**Base path:** `GET http://localhost:9080/api/v1/public/instance-info` (no `Authorization` header).

Returns read-only JSON for the Admin UI login shell and operators. This is the **Admin** host copy
of the same DTO; official mobile branding does **not** call this endpoint (see **§1** Auth public
vs enrolled instance-info).

| Field | Source | Notes |
| ----- | ------ | ----- |
| `authApiPublicBaseUrl` | `ezkey.qr.auth-base-url` | Same value embedded as `authUrl` in enrollment QR JSON; `null` when unset |
| `instanceName` | `ezkey.organization.name` | Instance / organization display name |
| `instanceDescription` | `ezkey.organization.description` | Human-facing blurb for this **deployment** (not the logged-in role); may appear on login / About |
| `aboutUrl` | `ezkey.organization.about-url` | Optional link for “Learn more”; `null` when unset |

**Example (200 OK):**

```json
{
  "authApiPublicBaseUrl": "https://auth.example.com:8080",
  "instanceName": "Acme Corporation",
  "instanceDescription": "Acme Corp Ezkey MFA Instance",
  "aboutUrl": "https://www.example.com/about-ezkey"
}
```

See also: enrollment QR JSON and `authUrl` (same property as `ezkey.qr.auth-base-url` on the Admin API); search this file for `ezkey.qr.auth-base-url`.

**Bruno:** `bruno/public-admin/get-public-instance-info.bru` (no Bearer token; uses `{{base_url_admin_api}}`). The `postman/` tree is a historical leftover — see `postman/README.md`.

### Anonymous evaluator self-registration (unauthenticated, installation-scoped)

**Base path:** `POST http://localhost:9080/api/v1/public/evaluator-signup` (no `Authorization` header).

Available only when `ezkey.evaluator.self-registration.enabled=true` (intended for EXP1 preview). When disabled, returns **404 Not Found** (no response body enumeration).

Creates an **empty tenant** and a **pending Tenant Admin** with **`ACTIVATION_CODE`** onboarding.
When the feature flag is on, also mints an opaque Admin UI **`BOOTSTRAP`** session (absolute **8
hours**, no sliding; purpose `BOOTSTRAP`; distinct from the activation code). Does **not** create
integrations, API keys, or enrollments — evaluators follow activation + device bind, then normal
passwordless login. Community / alpha only — not a clean-start or production default.

**Rate limits (defaults):** global **5 successful signups per UTC day**; **1 successful signup per client IP per 24h**. Failures return **429** with a generic capacity message. Activation redeem (`POST /api/v1/admin/auth/activate`) shares the admin login rate-limit bucket.

**Request body (optional JSON):**

```json
{
  "tenantLabel": "My preview workspace"
}
```

| Field | Required | Notes |
| ----- | -------- | ----- |
| `tenantLabel` | No | Max 40 chars; must not contain email or URL patterns |

**Response (201 Created):**

```json
{
  "activationCode": "ezkey_activation_…",
  "activationCodeExpiresAt": "2026-05-30T12:00:00Z",
  "adminUiUrl": "https://admin-ui.ezkey.online",
  "guidedTourUrl": "https://ezkey.org/community-guided-tour.html",
  "tenantLabel": "eval-a1b2c3d4",
  "sessionToken": "ezkey_bootstrap_…",
  "sessionExpiresAt": "2026-05-23T20:00:00Z",
  "username": "eval-admin-a1b2c3d4"
}
```

| Field | Notes |
| ----- | ----- |
| `sessionToken` | Opaque BOOTSTRAP bearer; omitted from JSON when HttpOnly browser session cookies are enabled (Mode B). Never put in URL query or audit payloads. |
| `sessionExpiresAt` | Absolute expiry (~8h). |
| `username` | Generated evaluator Tenant Admin username. |

**BOOTSTRAP allowlist** (while using that session): `POST /admin/auth/activate`, `GET /admin/auth/me`, `GET /admins/{id}/onboarding` (+ `/qrcode`), `GET /enrollments/{id}`, `POST /admin/auth/logout`. Logout invalidates the session server-side; the incomplete tenant/admin persists.

**CORS:** cross-origin signup from `ezkey.org` requires `ezkey.admin.cors.allowed-origins` to include the static site origin(s). Auto-redirect target: Admin UI `/evaluator-bootstrap` (handoff via cookie or sessionStorage — not query string).

See `ezkey-admin-api/CONFIGURATION.md` (evaluator self-registration group) and vision `V-2026-09-26-evaluator-bootstrap-admin-session`.

### Resume incomplete evaluator onboarding (unauthenticated, same flag)

**Base path:** `POST http://localhost:9080/api/v1/public/evaluator-onboarding/reissue` (no `Authorization` header).

Same gate as signup (`ezkey.evaluator.self-registration.enabled`). When disabled → **404**. Bounded re-issue so logout / absolute BOOTSTRAP TTL before device bind is not a dead end (product option B). Does **not** block logout. No permanent password.

**Eligible only when enrollment is incomplete:**

| Phase | Condition | Material returned |
| ----- | --------- | ----------------- |
| `PENDING_ACTIVATION` | Lifecycle pending, no enrollment yet | New activation code + fresh BOOTSTRAP session |
| `DEVICE_BIND` | Lifecycle ACTIVE, enrollment status `CREATED` (no device bind yet) | Rotated enrollment proof / challenge + fresh BOOTSTRAP session |

After device bind (`BOUND` / `VERIFIED`) → **404** (use normal passwordless login). Non-evaluator usernames and inactive tenants → **404** (anti-enumeration).

**Rate limits (defaults, separate from signup):** **10** successful re-issues per client IP per hour; **5** per username per hour.

**Request body:**

```json
{
  "username": "eval-admin-a1b2c3d4"
}
```

**Response (200 OK)** — phase-specific fields omitted when null:

```json
{
  "phase": "DEVICE_BIND",
  "username": "eval-admin-a1b2c3d4",
  "enrollmentId": 42,
  "enrollmentProofToken": "ezkey_proof_…",
  "enrollmentChallenge": 123456,
  "sessionToken": "ezkey_bootstrap_…",
  "sessionExpiresAt": "2026-05-23T20:00:00Z",
  "adminUiUrl": "https://admin-ui.ezkey.online",
  "guidedTourUrl": "https://ezkey.org/community-guided-tour.html"
}
```

**Bruno:** `bruno/public-admin/evaluator-onboarding-reissue.bru`.

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

**Pending `expiresAt`:** Matches the stored authentication attempt’s expiry (`ezkey.core.auth-attempt.ttl-seconds`, default 120 seconds). Use it for countdown timers; it is not a fixed five-minute window.

**Audit (pending):** When `status` is `pending`, Ezkey records `event_action` `login_mfa_requested` with **SUCCESS** (MFA request created — not a full session yet). Legacy rows may show `login_pending`; see [AUDIT_ADMIN_LOGIN_ACTIONS.md](AUDIT_ADMIN_LOGIN_ACTIONS.md).

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
```

Empty body (servlet filter). `Retry-After` is seconds until the per-IP or per-instance backstop budget refills.

**Rate Limiting:**
- 5 requests per minute per IP address
- Automatic IP blocking after 10 consecutive failures (30 minutes)
- HTTP 429 when limit exceeded
- `Retry-After` header indicates seconds until retry allowed

---

### Dashboard overview

**Base path:** `http://localhost:9080/api/v1/dashboard` (Admin API).

**GET /api/v1/dashboard/overview** — Single aggregated payload for the Admin UI landing page: integration and enrollment stats, auth-attempt 24h totals (including pending), recent audit activity, and (Global Admin) optional open operator alerts such as audit-chain follow-ups. Tenant Admins receive tenant-scoped stats; Global Admins receive instance-wide stats. Prefer this over parallel `size=1` list/`count` storms. Related: `GET /api/v1/auth-attempts/pending-count` (Admin-only) when a live pending tally is needed outside the overview. Widget badge semantics: [`product-docs/components/admin-ui/dashboard-widget-signal-model.md`](../product-docs/components/admin-ui/dashboard-widget-signal-model.md).

### Audit log and chain checkpoint APIs

**Base path:** `http://localhost:9080/api/v1/audit-logs` (Admin API). Global Admin only for chain and integrity endpoints.

**GET /api/v1/audit-logs** — Paginated audit log list. Optional filters: `eventType` (a single event type enum value), `eventTypeFamily` (all types in a logical family: `ADMIN`, `ENROLLMENT`, `AUTH_ATTEMPT`, `API_KEY`, `SYSTEM`, `ENCRYPTION_KEY`, `REENCRYPTION`, `INTEGRATION`, `TENANT`, `AUDIT_CHAIN`). `eventType` and `eventTypeFamily` are mutually exclusive; sending both returns **400 Bad Request**. Also: `eventStatus`, `apiName`, `enrollmentId`, `authAttemptId`, `integrationId`, `adminId`, `targetAdminId`, `tenantId` (Global Admin scope), `createdAfter`, `createdBefore` (ISO-8601), plus standard `page`, `size`, `sort`. **Visibility:** Tenant Admins automatically see only rows where `tenant_id` matches their tenant (system events with `tenant_id` null are excluded); Global Admins see all logs and may optionally filter with `tenantId` (ignored for Tenant Admins). **Attribution:** Tenant Admin create (`ADMIN_CREATED`) audits the **target admin’s tenant**; Auth API bind/verify/pending/respond for **admin MFA** enrollments (single system integration) also use the admin’s tenant — not the system-integration tenant — so peer onboarding and MFA auth appear in that tenant’s audit view.

**GET /api/v1/audit-logs/{auditLogId}/context** — Bounded neighborhood around one anchor audit event (default 10 events before and after; max 50 each). Same tenant visibility as the list search. Query: `beforeCount`, `afterCount`, optional `tenantId` (Global Admin). Returns items plus `hasMoreBefore` / `hasMoreAfter`. This is investigation context, not a substitute for paginated search.

**GET /api/v1/audit-logs/chain-checkpoints** — Search audit chain checkpoints with pagination and optional filters. Use primarily for lifecycle observability, anomaly investigation, and archive-confirmation context. Exceptional maintenance workflows may still use this surface when needed.

**Query parameters (all optional):** `windowStartAfter`, `windowStartBefore` (ISO-8601), `entryCountMin`, `entryCountMax`, `checkpointType` (REGULAR, ARCHIVE_SEAL, GAP_DECLARATION), `createdAfter`, `createdBefore` (ISO-8601), plus `page`, `size`, `sort` (e.g. `sort=windowStart,asc`). Default: `size=20`, `sort=windowStart,asc`.

**Response (200 OK):** Paginated response with `content` (array of checkpoint objects: `checkpointId`, `windowStart`, `windowEnd`, `entryCount`, `firstEntryId`, `lastEntryId`, `entriesDigest`, `prevChainHmac`, `chainHmac`, `createdAt`, `checkpointType`, `lifecycleState`, `notes`), `totalElements`, `totalPages`, etc.

**GET /api/v1/audit-logs/integrity/bootstrap** — Thin Global Admin Integrity atelier bootstrap for Admin UI honesty chrome. Returns product `runtimeProfile` (`base`|`integrity`, from Spring `docker-base`) plus live `chainCheckpointsEnabled` / `nightlyValidationEnabled` config flags. Dual source: profile name is not derived from flags; flags are not derived from the profile name. Not a job matrix or second journal.

**POST /api/v1/audit-logs/integrity-validation/run** — Global Admin detective-layer validation over `[from, to)`. Same orchestration as the nightly batch (may raise/touch `AUDIT_INTEGRITY_RUPTURE`; emits `NIGHTLY_INTEGRITY_VALIDATION_COMPLETED` with `triggerSource=OPERATOR`). Distinct from GET `integrity-check` / `chain-integrity` (read-only verify). When `ezkey.audit.integrity.nightly.enabled=false` (typical on opt-in `--runtime=base`): **409 Conflict** RFC 9457 type `https://ezkey.io/problems/domain/integrity-validation-disabled`. HMAC inactive while the flag is on still returns 200 skipped. Full contract: [AUDIT_LOG_INTEGRITY.md](AUDIT_LOG_INTEGRITY.md).

**Integrity async jobs (preferred for long Ops):** Global Admin single global slot — `POST /api/v1/audit-logs/integrity/jobs` body `{ type, from, to, raiseAlert? }` → **202** `{ jobId }`; occupied → **409** with `currentJob` / `resumeOneLiner`. Types: `VERIFY_CHAIN_RANGE`, `VERIFY_ENTRY_HMAC_RANGE`, `RUN_VALIDATION`. `GET …/integrity/jobs/current` (200 or 204), `GET …/integrity/jobs/{jobId}`, `POST …/integrity/jobs/current/abandon` (only when status ∈ {EXPIRED, CANCELLED, INTERRUPTED}). TTL default 60m (`ezkey.audit.integrity.async-job.ttl`). Sync `GET …/{id}/integrity-check` (single entry) stays outside the slot. Does **not** use `ezkey_scheduled_job_last_run` for in-flight state.

**GET /api/v1/audit-logs/lifecycle/archive-eligibility** — Read lifecycle archive observability state. Returns whether external archival is enabled, whether confirmation is required, and the sealed checkpoint tranche currently awaiting confirmation.

**POST /api/v1/audit-logs/lifecycle/confirm-archived** — Record successful external archival for a sealed tranche by marking it `EXPORTED`. This confirms the result of an external archival workflow; it does not perform the export itself.

**GET /api/v1/audit-logs/lifecycle/incidents** — Paginated operational heartbeat incidents (distinct from cryptographic gap declarations). Standard Spring Data `page`, `size`, `sort` (default newest first).

**POST /api/v1/audit-logs/lifecycle/incidents/{incidentId}/declare** — Supplies justification (10–500 chars) and classified `rootCause` enum when status is `RECOVERED_PENDING_DECLARATION`.

See [AUDIT_LOG_INTEGRITY.md](AUDIT_LOG_INTEGRITY.md) for integrity verification, lifecycle observability, archive confirmation, heartbeat supervision, and exceptional maintenance endpoints.

**Peripheral degraded mode — audit-chain heartbeat fail-closed (Auth API / Integration API):**

- **Scope intentionally narrow:** `AuditChainHeartbeatPeripheralInterceptor` only blocks **`POST /api/v1/auth-attempts/pending`** (Auth API — “new pending claim / polling that starts work”) and **`POST /api/v1/auth-attempts`** (Integration API — “create MFA attempt”).
- **`POST /api/v1/auth-attempts/respond`**, **enrollment binds/verify**, **wait**, **cancel**, and Integration API retrieval endpoints continue to behave normally even while supervision is degraded — this matches the product posture “finish in-flight work, stop starting new MFA” without accidentally freezing device responses attached to attempts already minted earlier.
- **HTTP contract:** impacted routes respond **503 Service Unavailable** with RFC 9457 type **`https://ezkey.io/problems/system/audit-chain-heartbeat-degraded`** and **`Retry-After: 60`** whenever `ezkey.audit.chain.heartbeat.enabled=true` **and** `required=true`, and checkpoints appear stalled relative to **`latest.window_end` + grace thresholds** documented in [**AUDIT_LOG_INTEGRITY.md**](AUDIT_LOG_INTEGRITY.md).

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
Wait for device approval in challenge-based or non-blocking authentication.

**Usage:** After receiving `authAttemptId`, optional `challengeCode`, and `waiterSecret` capability from `/login`, call this endpoint to wait for device approval.

**Request:**
```http
POST /api/v1/admin/auth/passwordless-wait
Content-Type: application/json

{
  "authAttemptId": 123,
  "challengeCode": 654321,
  "waiterSecret": "xK8_j9L2mNp..."
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

**Failure Responses (RFC 9457 Problem Details):**

Errors use `Content-Type: application/problem+json` (or JSON with the same fields). Common cases for `/passwordless-wait`:

| HTTP | Problem `type` (URI suffix) | Typical `title` |
|------|-----------------------------|-----------------|
| 400 | `.../authentication/auth-rejected` | Authentication Rejected |
| 400 | `.../authentication/auth-expired` | Authentication Expired |
| 400 | `.../authentication/invalid-signature` | Invalid Signature |
| 401 | `.../authentication/invalid-credentials` | Authentication failed (invalid/missing waiter secret, challenge mismatch, or attempt already consumed) |
| 408 | `.../authentication/auth-timeout` | Authentication Timeout |

**Example (device denied):**
```json
{
  "type": "https://ezkey.io/problems/authentication/auth-rejected",
  "title": "Authentication Rejected",
  "status": 400,
  "detail": "Device rejected the authentication request",
  "instance": "/api/v1/admin/auth/passwordless-wait"
}
```

The client should use `detail` (then `title`) for user-facing messages, not assume a transport failure.

**Audit:** Each call to `/passwordless-wait` that reaches a terminal outcome writes an `ADMIN_LOGIN` audit row with a distinct `event_action` (for example `login_mfa_session_issued`, `login_mfa_expired`, `login_mfa_rejected`). See [AUDIT_ADMIN_LOGIN_ACTIONS.md](AUDIT_ADMIN_LOGIN_ACTIONS.md) for the full list and SIEM notes.

**Security Notes:**
- `waiterSecret` capability token prevents replay and session hijacking by requiring the unguessable CSPRNG secret returned only to the initial `/login` caller.
- Atomic CAS guarantees a session is issued at most once per authentication attempt; replays after session issuance are rejected with generic 401 and cannot trigger token rotation or mint survivor tokens.
- `challengeCode` prevents enumeration attacks (proof of legitimate login initiation)
- Blocking HTTP call: the server waits up to the minimum of 300 seconds and (remaining attempt lifetime + small slack). If `ezkey.core.auth-attempt.ttl-seconds` is set above 300 (max 600), the wait may return HTTP 408 while the attempt row is still valid in the database.
- Device must enter matching challenge code before approval
- Invalid challenge on device marks attempt as INVALID

---

#### POST /activate (First-time activation code)

Unauthenticated consume of a one-time onboarding activation code (`onboardingMode = ACTIVATION_CODE`).
Creates the first enrollment and returns bind credentials. This is **not**
`POST /api/v1/admins/{id}/activate` (operator reactivation of a deactivated admin). It is also
**not** recovery (`POST /recover`).

**Request:**
```http
POST /api/v1/admin/auth/activate
Content-Type: application/json

{
  "activationCode": "ezkey_activation_550e8400e29b41d4a716446655440000"
}
```

**Success Response (200 OK):**
```json
{
  "success": true,
  "message": "Activation successful. Bind the first device now. Recovery codes remain deferred.",
  "username": "pending.admin",
  "enrollmentId": 123,
  "enrollmentProofToken": "ezkey_proof_…",
  "enrollmentChallenge": 123456
}
```

Recovery codes are generated server-side when the first enrollment exists but are **omitted** from
this unauthenticated response. Reveal or regenerate them later from an authenticated admin-management
flow (`POST /api/v1/admins/{id}/recovery-codes/regenerate`). See [LIFECYCLE_GOVERNANCE.md](LIFECYCLE_GOVERNANCE.md) §3.5.

**Failure:** **403** for invalid, expired, or unusable codes (neutral client-safe message). **400**
when activation cannot proceed in the current state.

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
  "enrollmentId": 123,
  "message": "Recovery successful. Token valid for 30 minutes. Re-bind enrollment immediately."
}
```

The `enrollmentId` is the administrator’s MFA enrollment to pass to `POST /api/v1/admin/enrollments/reset` (with the recovery token as bearer). See [ADMIN_UI_RECOVERY.md](ADMIN_UI_RECOVERY.md).

**Failure Response (403 Forbidden):**

All pre-authentication recovery failures return the same status and the same client-safe message
(unknown username, inactive account, no recovery codes, or wrong code). Distinct reasons are
recorded only in server logs and structured audit (`reason_code`).

```json
{
  "success": false,
  "message": "Invalid username or recovery code"
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
- Recovery tokens are stored with purpose `RECOVERY` and are rejected by ordinary Admin API
  session authentication (SEC-021); they may only be used for enrollment reset
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
- Requires recovery token (obtained via `/recover`); ordinary session tokens receive 403
- Unbinds old device (sets device_public_key to null)
- Generates new enrollment credentials
- Recovery token is deactivated after successful reset, or expires after 30 minutes

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

#### GET /me
Return non-secret metadata for the current Admin UI session.

**Request:**
```http
GET /api/v1/admin/auth/me
Cookie: EZKEY_ADMIN_SESSION=...
```

Bearer clients may also call this endpoint with `Authorization: Bearer ...`.

**Success Response (200 OK):**
```json
{
  "username": "admin",
  "adminType": "GLOBAL_ADMIN",
  "expiresAt": "2026-04-28T14:00:00Z",
  "adminId": 2,
  "tenantId": null,
  "csrfToken": "non-secret-csrf-token"
}
```

For tenant- or integration-scoped administrators, `tenantId` and `tenantName` are populated (display name from `ezkey_tenant.tenant_name`). Global administrators omit both (`null` / absent with `NON_NULL` serialization).

`csrfToken` is present in browser cookie mode and must be sent as `X-CSRF-TOKEN` on unsafe cookie-authenticated requests (`POST`, `PUT`, `PATCH`, `DELETE`). The opaque session token is never returned by `/me`.

**Unauthorized Response:**
```http
HTTP/1.1 401 Unauthorized
```

#### Using Bearer Token
All authenticated Admin API endpoints require the bearer token in the Authorization header:

```http
GET /api/v1/admin/integrations
Authorization: Bearer ezkey_abc123def456...
```

#### Create Integration

**POST /api/v1/integrations**

Creates an integration in the caller's tenant: Tenant Admin → that tenant; Global Admin → the
system tenant. There is no `tenantId` on the create body. `code` is required: 2–100 characters,
slug `^[a-zA-Z0-9_-]+$`, unique per tenant. `name` is required; `description` is optional. There is
no API to change `code` after create. Same `code` in a different tenant is allowed.

**Request:**
```http
POST /api/v1/integrations
Authorization: Bearer ezkey_admin_token...
Content-Type: application/json

{
  "code": "web-portal",
  "name": "Web Portal",
  "description": "Customer-facing portal"
}
```

**Response (201 Created):** `Location: /api/v1/integrations/{id}` plus the created integration
(including `code`). Duplicate `code` in the same tenant returns **409** Problem Detail
(`IntegrationCodeAlreadyExistsException`). Invalid slug/blank fields return **400**. Creating under
an inactive tenant returns **403**.

Bruno: `bruno/integrations-admin/create.bru`.

#### Search Integrations

**GET /api/v1/integrations**

Retrieves integrations with optional filters and pagination. All filter parameters are optional.
Retired integrations are excluded by default from day-to-day listings.

**Query Parameters:**
- `page` (optional): Page number (zero-based, default: 0)
- `size` (optional): Page size (default: 20)
- `sort` (optional): Sort field and direction (e.g., `createdAt,desc`). Sortable fields: `id`, `createdAt`, `lifecycleStatus`
- `integrationName` (optional): Filter by integration name (partial match, case-insensitive)
- `active` (optional): Compatibility filter mapped onto lifecycle (`true` = `ACTIVE`, `false` = `RETIRED`). Prefer `lifecycleStatus` for new callers.
- `lifecycleStatus` (optional): Exact lifecycle filter (`ACTIVE`, `RETIRED`)
- `includeRetired` (optional): When `true`, include retired integrations in results if no exact `lifecycleStatus` is requested
- `createdAfter` (optional): Filter integrations created after this timestamp (ISO-8601)
- `createdBefore` (optional): Filter integrations created before this timestamp (ISO-8601)
- `tenantId` (optional): Filter by tenant ID. **GlobalAdmin only**; when provided, limits results to that tenant. **Ignored for TenantAdmin** (they always see only their tenant).

**Multi-Tenancy:**
- **GlobalAdmin**: Sees all integrations by default; use `tenantId` to restrict to a specific tenant.
- **TenantAdmin**: Sees only integrations in their tenant; `tenantId` query parameter is ignored.

**Response (200 OK):** Paginated response with `content` (array of integration objects), `totalElements`, `totalPages`, etc. Each integration includes `lifecycleStatus` (`ACTIVE`, `RETIRED`) as the source of truth and `operational` (`ACTIVE` lifecycle **and** parent tenant active). There is no `active` boolean on the response. System integrations are excluded from the listing.

#### Bulk enrollment lifecycle for an integration

These endpoints execute integration-scoped bulk lifecycle actions against the current backend state.
They are **successful no-ops** when no eligible enrollment matches at execution time. This keeps the
backend authoritative and concurrency-safe while allowing the UI to present an exact result summary.

**Shared response fields (`200 OK`):**
- `affectedCount`: number of enrollments whose state changed
- `skippedCount`: number of enrollments skipped by defensive guards (for example self-guard)
- `noOp`: `true` when the request succeeded but changed no enrollment state

**POST /api/v1/integrations/{id}/enrollments/deactivate-all**

Reversibly deactivates all active VERIFIED enrollments for the integration. Cannot be applied to a
system integration.

**Request query parameter (optional):** `reason`

**Response (200 OK):**
```json
{
  "affectedCount": 3,
  "skippedCount": 0,
  "noOp": false
}
```

**POST /api/v1/integrations/{id}/enrollments/reactivate-all**

Reactivates all inactive VERIFIED enrollments for the integration.

**Request query parameter (optional):** `reason`

**Response (200 OK):**
```json
{
  "affectedCount": 0,
  "skippedCount": 0,
  "noOp": true
}
```

**POST /api/v1/integrations/{id}/enrollments/revoke-all**

Permanently revokes all revocable enrollments for the integration. This includes `CREATED`,
`BOUND`, and `VERIFIED` enrollments, whether currently active or already deactivated. Cannot be
applied to a system integration.

**Request query parameter (optional):** `reason`

**Response (200 OK):**
```json
{
  "affectedCount": 5,
  "skippedCount": 1,
  "noOp": false
}
```

**Audit behavior:** For these routine admin lifecycle bulk actions, Ezkey audits **effective
changes**. A successful request that affects zero enrollments does not emit a success audit row.

#### Integration retirement and deletion

**POST /api/v1/integrations/{id}/retire**

Retires an integration from day-to-day operations while preserving historical data. As part of the
retirement flow, Ezkey first bulk-revokes revocable enrollments under that integration, then marks
the integration lifecycle as `RETIRED`. System integrations cannot be retired.

**Request query parameter (optional):** `reason`

**Response:** `204 No Content`

**DELETE /api/v1/integrations/{id}**

Permanent deletion is exceptional. It is only allowed after the integration has already been
retired and only when no enrollments remain linked to it. System integrations cannot be deleted.

**Request query parameter (optional):** `reason`

**Response:** `204 No Content`

---

## Tenant management

Tenant management endpoints allow GlobalAdmins to create, list, update, deactivate, and activate tenants. TenantAdmins cannot list tenants (`GET /tenants` → 403); they may get their own tenant by ID only.

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
- Creation endpoints accept an optional `onboardingMode` field. `IMMEDIATE` is the default and
  preserves the existing behavior. `ACTIVATION_CODE` creates a pending administrator and returns a
  one-time activation code instead of immediate enrollment bootstrap data.
- In `IMMEDIATE`, the creation response returns basic admin information plus the first
  `enrollmentId`. Enrollment proof token and challenge are **not** in this response; they are
  retrieved via GET `/api/v1/admins/{id}/onboarding`. Recovery codes are generated server-side but
  intentionally remain deferred from bootstrap responses.
- In `ACTIVATION_CODE`, the creation response returns `lifecycleStatus`, `onboardingMode`,
  `activationCode`, and `activationCodeExpiresAt`; `enrollmentId` and `recoveryCodes` stay null
  until the activation code is consumed and the first enrollment exists.
- Follows the same split as the enrollment API: bind credentials via a dedicated retrieval path;
  recovery codes are no longer revealed during unauthenticated/bootstrap steps and should be
  revealed later through an authenticated admin-management flow.
- The default recovery-code set size is **5** and remains configurable via `ezkey.admin.recovery.codes-count`.

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
  "lastName": "GlobalAdmin",
  "onboardingMode": "IMMEDIATE"
}
```

**Success Response (201 Created, `onboardingMode = IMMEDIATE`):**
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

**Alternative Success Response (201 Created, `onboardingMode = ACTIVATION_CODE`):**
```json
{
  "adminId": 2,
  "username": "new.global.admin",
  "email": "newglobal@example.com",
  "firstName": "New",
  "lastName": "GlobalAdmin",
  "adminType": "GLOBAL_ADMIN",
  "tenantId": null,
  "enrollmentId": null,
  "lifecycleStatus": "PENDING_ACTIVATION",
  "onboardingMode": "ACTIVATION_CODE",
  "activationCode": "ezkey_activation_550e8400-e29b-41d4-a716-446655440000",
  "activationCodeExpiresAt": "2026-01-02T14:30:00Z",
  "createdAt": "2025-12-26T14:30:00Z",
  "recoveryCodes": null
}
```

**Note:** In `IMMEDIATE`, the first enrollment is created now; retrieve bind material via
`GET /api/v1/admins/{id}/onboarding`. Plaintext `recoveryCodes` are deferred from bootstrap —
reveal or regenerate them from an authenticated management flow. In `ACTIVATION_CODE`, no enrollment
exists yet: the new admin consumes `POST /api/v1/admin/auth/activate`, then binds a device. Do not
call onboarding retrieval until that consume has produced the first enrollment.

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
  "tenantId": 2,
  "onboardingMode": "IMMEDIATE"
}
```

**Success Response (201 Created, `onboardingMode = IMMEDIATE`):**
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

**Alternative Success Response (201 Created, `onboardingMode = ACTIVATION_CODE`):**
```json
{
  "adminId": 3,
  "username": "new.tenant.admin",
  "email": "newtenant@example.com",
  "firstName": "New",
  "lastName": "TenantAdmin",
  "adminType": "TENANT_ADMIN",
  "tenantId": 2,
  "enrollmentId": null,
  "lifecycleStatus": "PENDING_ACTIVATION",
  "onboardingMode": "ACTIVATION_CODE",
  "activationCode": "ezkey_activation_550e8400-e29b-41d4-a716-446655440111",
  "activationCodeExpiresAt": "2026-01-02T14:35:00Z",
  "createdAt": "2025-12-26T14:35:00Z",
  "recoveryCodes": null
}
```

**Note:** Same rule as global admin: `IMMEDIATE` creates the first enrollment now (onboarding retrieval applies); plaintext recovery codes stay deferred from bootstrap. `ACTIVATION_CODE` defers enrollment until `POST /api/v1/admin/auth/activate`.

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

This endpoint only applies after the administrator already has a first enrollment. Administrators
created with `onboardingMode = ACTIVATION_CODE` remain in `PENDING_ACTIVATION` with no
`enrollmentId`, so this endpoint is not applicable until the activation code has been consumed.

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
- Recovery codes are stored as BCrypt hashes at rest and cannot be retrieved in plain text after initial provisioning
- Plain codes are returned **once** in the **create** response (`POST /api/v1/admins/global` or `/tenant`); operators must save them then
- This GET returns `recoveryCodes: null` because they cannot be listed again after provisioning

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

### e) Regenerate Recovery Codes

**POST /api/v1/admins/{id}/recovery-codes/regenerate**

Generates a fresh one-time set of recovery codes for the target administrator and immediately
invalidates any previous unused recovery codes.

**Permissions:**
- **GlobalAdmin**: Can regenerate recovery codes for any administrator
- **TenantAdmin**: Can regenerate recovery codes for administrators in their tenant

**Request:**
```http
POST /api/v1/admins/2/recovery-codes/regenerate
Authorization: Bearer ezkey_admin_token...
```

**Success Response (200 OK):**
```json
{
  "adminId": 2,
  "username": "tenant.admin",
  "recoveryCodes": [
    "1111-2222-3333-4444-5555-6666-7777-8888",
    "..."
  ],
  "codesCount": 5,
  "invalidatedPreviousCodes": true,
  "message": "New recovery codes generated. Previous unused codes are no longer valid."
}
```

**Notes:**
- `recoveryCodes` are plain text and are shown **only in this response**.
- The previous remaining recovery codes are revoked immediately.
- Inactive administrators cannot receive regenerated recovery codes.

**Status Codes:**
- 200: Recovery codes regenerated successfully
- 400: Target administrator is inactive
- 401: Unauthorized - admin token required
- 403: Forbidden - not authorized for this administrator
- 404: Administrator not found
- 500: Internal server error

---

### e1) Re-issue activation code (pending administrators)

**POST /api/v1/admins/{id}/activation-code/regenerate**

Re-issues a **new** one-time deferred onboarding activation code for an administrator who is still
in `PENDING_ACTIVATION` and has **no** first enrollment yet. Any previously issued **unused**
activation issuance rows for that administrator are **deactivated** first, so only the latest
code remains valid until it is consumed or expires.

**Permissions:**
- **GlobalAdmin** only (TenantAdmin receives 403).

**Eligibility (400 when not met):**
- Target administrator must be `active = true`
- `lifecycleStatus` must be `PENDING_ACTIVATION`
- `enrollment` must not exist yet (no first enrollment)
- When the admin is tenant-scoped, the tenant must be active

**Request:**
```http
POST /api/v1/admins/42/activation-code/regenerate
Authorization: Bearer ezkey_admin_token...
```

**Success Response (200 OK):**
```json
{
  "adminId": 42,
  "username": "tenant.admin",
  "activationCode": "ezkey-activation-…",
  "activationCodeExpiresAt": "2026-05-10T15:00:00Z",
  "invalidatedPreviousTokens": true,
  "deactivatedActiveTokenCount": 1,
  "message": "New activation code generated. Previous unused activation codes no longer work."
}
```

**Notes:**
- `activationCode` is plain text and is shown **only in this response**.
- The administrator remains **pending** until the new code is consumed through the normal
  activation flow.
- This is a **recovery** operation for operators who lost the original code; it does not reset
  broader onboarding state beyond invalidating prior unused activation issuances.

**Status Codes:**
- 200: Activation code re-issued successfully
- 400: Administrator inactive, tenant inactive, not pending activation, or enrollment already exists
- 401: Unauthorized - admin token required
- 403: Forbidden - not a Global Admin
- 404: Administrator not found
- 500: Internal server error

---

### e2) List Administrators

**GET /api/v1/admins**

Lists administrators with tenant-based filtering. GlobalAdmin sees all administrators across all tenants. TenantAdmin sees only administrators from their tenant.

**Request:**
```http
GET /api/v1/admins?page=0&size=20&sort=createdAt,DESC
Authorization: Bearer ezkey_admin_token...
```

**Query Parameters:**
- `tenantId` (optional, **GlobalAdmin only**): When provided, limits results to that tenant. Ignored for TenantAdmin (tenant scope comes from the authenticated principal).
- `page` (optional, default: 0): Page number (zero-based)
- `size` (optional, default: 20): Number of items per page
- `sort` (optional, default: createdAt,DESC): Sort field and direction (see Spring Data `Pageable`). Commonly used fields include `adminId`, `username`, `adminType`, `tenantId`, `active`, and `createdAt`.

**Success Response (200 OK):** Spring Data page JSON with a `content` array and **nested** pagination metadata under `page` (the Admin UI consumes this “pattern B” shape):

```json
{
  "content": [
    {
      "adminId": 1,
      "version": 0,
      "username": "admin.docker",
      "email": "admin@example.com",
      "phoneNumber": "+15145551234",
      "firstName": "Admin",
      "lastName": "Docker",
      "adminType": "GLOBAL_ADMIN",
      "tenantId": null,
      "tenantName": null,
      "enrollmentId": 42,
      "active": true,
      "lifecycleStatus": "ACTIVE",
      "createdAt": "2025-12-26T10:00:00Z",
      "lastLoginAt": "2025-12-28T09:15:00Z",
      "hasRecoveryCodes": true,
      "operational": true
    }
  ],
  "page": {
    "size": 20,
    "number": 0,
    "totalElements": 1,
    "totalPages": 1
  }
}
```

**Response fields:** `tenantName` is the display name of the tenant (`ezkey_tenant.tenant_name`) when `tenantId` is set; it is `null` for global administrators. `enrollmentId` is the MFA enrollment linked to this administrator (passwordless identity); null if not linked. `lastLoginAt` is the timestamp of the administrator's last successful login (null if never logged in). `operational` summarizes whether the admin account and (for tenant-scoped admins) the tenant are in an operable state for login. Useful for access reviews.

**Status Codes:**
- 200: List of administrators retrieved successfully
- 401: Unauthorized - admin token required
- 403: Forbidden - not an administrator
- 500: Internal server error

---

### e3) Get Administrator by ID

**GET /api/v1/admins/{id}**

Returns a single administrator by ID. Same shape as a list item (including `lastLoginAt`).

**Permissions:** GlobalAdmin can access any admin. TenantAdmin can only access admins in their own tenant.

**Request:**
```http
GET /api/v1/admins/2
Authorization: Bearer ezkey_admin_token...
```

**Success Response (200 OK):** Same fields as a list item: `adminId`, `version`, `username`, `email`, `phoneNumber`, `firstName`, `lastName`, `adminType`, `tenantId`, `tenantName`, `enrollmentId`, `active`, `lifecycleStatus`, `createdAt`, `lastLoginAt`, `hasRecoveryCodes`, `operational`.

**Status Codes:**
- 200: Administrator retrieved successfully
- 401: Unauthorized - admin token required
- 403: Forbidden - not authorized for this admin
- 404: Administrator not found
- 500: Internal server error

---

### e4) Deactivate Administrator

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

### e5) Activate Administrator

**POST /api/v1/admins/{id}/activate**

Reactivates a previously deactivated administrator. GlobalAdmin only. Sets `active = true`. Idempotent if the admin is already active. The admin must log in again to obtain a new bearer token. This is **not** first-time onboarding (`POST /api/v1/admin/auth/activate`).

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

#### **POST /api/v1/enrollments** (Create Enrollment)

Creates a pending enrollment (`CREATED`) for an integration. Returns identifiers needed for mobile bind/verify.

**Uniqueness (`name`):** At most one `VERIFIED` enrollment per `(integrationId, name)` (see `docs/LIFECYCLE_GOVERNANCE.md` §3.3). Create is rejected when an **active** `VERIFIED` enrollment already uses that name (use recovery/reset or deactivate/re-enroll rather than supersession). Create is allowed when a same-name `VERIFIED` enrollment exists but is **inactive**, or when only non-`VERIFIED` rows exist.

**Request body:**

| Field | Required | Notes |
| ----- | -------- | ----- |
| `integrationId` | yes | Target integration |
| `name` | yes | Enrollment display name |
| `authAttemptChallengeRequired` | no | Default false |
| `contactEmail`, `contactPhoneNumber`, `userIdentifier` | no | Optional metadata |
| `expiresAt` | no | ISO-8601 UTC instant; invitation expires after this time during the **pending** bind/verify phase only. Must be strictly in the future when provided. When omitted, the server sets expiry using `ezkey.enrollment.pending-expiration-days` (**default 7**). Use instance property `0` to disable default pending expiry (explicit `expiresAt` still honored). |

**Example:**

```http
POST /api/v1/enrollments
Authorization: Bearer ezkey_admin_token...
Content-Type: application/json

{
  "integrationId": 1,
  "name": "John's iPhone",
  "authAttemptChallengeRequired": false,
  "expiresAt": "2026-12-31T23:59:59Z"
}
```

**Response (201 Created):**

| Field | Notes |
| ----- | ----- |
| `enrollmentId` | New enrollment id |
| `enrollmentChallenge` | Challenge shown during verify |
| `expiresAt` | Pending invitation expiry when set on the stored enrollment (typically present when default or explicit expiry applies) |

#### **GET /api/v1/enrollments/{id}** (For General Enrollment Management)

**Use this API when:**
- ✅ You have an **enrollment ID** (from enrollment search or creation)
- ✅ You want **comprehensive enrollment details** (not just onboarding credentials)
- ✅ You're working in the **enrollment management workflow**
- ✅ You're a **GlobalAdmin**, or a **TenantAdmin** reading an enrollment on **your tenant’s integration** (not admin MFA)

**Why this API:**
- Validates access at the **integration tenant level** (correct for enrollment management)
- Returns full enrollment details (status, keys, integration info, etc.)
- Suitable for **enrollment monitoring and management**
- Semantic clarity: "Get details of enrollment Y"

**Important Note for TenantAdmin:**
- ⚠️ **Admin MFA enrollments** sit on the **system integration**. `GET /enrollments/{id}` checks integration tenant, so a Tenant Admin cannot load their own (or a peer’s) admin MFA enrollment this way.
- ✅ Use `GET /api/v1/admins/{id}/onboarding` (and `/onboarding/qrcode`) for admin MFA credentials.
- ✅ `GET /enrollments/{id}` is still the right API for enrollments on integrations that belong to the Tenant Admin’s tenant.

**Example:**
```http
GET /api/v1/enrollments/123
Authorization: Bearer ezkey_admin_token...
```

**Response (high level):** Returns the full enrollment record including integration display fields where applicable (`integrationName`, `isSystemIntegration`), cryptographic material references (`integrationPublicKey`, `devicePublicKey`), client-reported `devicePrivateKeyStorageTier` when set at verify (`NONE`, `STANDARD`, `STRONG`; not independently verified by the server — see `docs/MOBILE_DEVELOPER_GUIDE.md`), and lifecycle audit timestamps such as `createdAt`, `createdByAdminId`, `deactivatedAt`, `deactivatedByAdminId`, `revokedAt`, and `revokedByAdminId` when set.

#### **GET /api/v1/enrollments/{id}/qrcode** (Enrollment bind-handle QR)

Returns a PNG QR encoding the bind handle (`enrollmentId` + `enrollmentProofToken`, plus optional `authUrl`). Same tenant gate as JSON GET (`canAccessEnrollment`). Cross-tenant and unknown ids return **404** (hide-existence), not 403. Auth API bind stays token-gated and is not tenant-filtered.

**Request:**
```http
GET /api/v1/enrollments/123/qrcode
Authorization: Bearer ezkey_admin_token...
```

**Status Codes:**
- 200: PNG QR generated
- 400: Enrollment missing proof token
- 404: Enrollment not found or not visible to this admin
- 500: Internal server error

#### **PATCH /api/v1/enrollments/{id}** (Partial Update of Enrollment Metadata)

Partially updates enrollment metadata. Only non-null fields in the request body are applied. Supports `enrollmentName`, `contactEmail`, `expiresAt`, `authAttemptChallengeRequired`, `userIdentifier`, `clearContactEmail`, and `clearExpiresAt`. Only active, non-revoked VERIFIED enrollments can be updated. Include `version` from the GET response for optimistic locking.

Omitted fields are left unchanged. Sending `null` for optional fields does **not** clear them. To clear optional `contactEmail` or `expiresAt`, set **`clearContactEmail`: true** or **`clearExpiresAt`: true** respectively (or send an empty `contactEmail` after trim to clear email). When `clearContactEmail` is true, any `contactEmail` value in the same request is ignored for the stored value. If `expiresAt` is non-null in the same request, it wins and **`clearExpiresAt` is ignored**.

**Semantics — `expiresAt`:** This field governs the **pending invitation window** (creation → bind → verify). It is **not** a post-verification “MFA valid until” lifetime for the whole enrollment; after `VERIFIED`, ongoing authentication does not use this field as an automatic cutoff (a separate product concept may be introduced later).

**Audit:** On success, `ENROLLMENT_UPDATED` stores structured JSON in `event_details` with `enrollment_id`, `integration_id`, and a `changes` array listing each modified field with `previous` and `new` values.

**Request:**
```http
PATCH /api/v1/enrollments/123
Authorization: Bearer ezkey_admin_token...
Content-Type: application/json

{
  "version": 0,
  "enrollmentName": "John's iPhone",
  "contactEmail": "john@example.com",
  "clearContactEmail": false,
  "expiresAt": "2026-12-31T23:59:59Z",
  "clearExpiresAt": false,
  "authAttemptChallengeRequired": false,
  "userIdentifier": "app-user-12345"
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
- `expiresAt`: must be in the future if provided; semantics are invitation/bind/verify only (see above)
- `userIdentifier`: optional; trimmed; empty string after trim clears to null
- Only VERIFIED and active enrollments can be updated

#### **DELETE /api/v1/enrollments/{id}** (Delete Enrollment)

Removes an enrollment from the system. Optional query parameter `reason` (min 10, max 500 characters) is recorded in the audit log for identifiable operator identity.

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

#### Single enrollment lifecycle (deactivate / reactivate / revoke)

Semantics: [`docs/LIFECYCLE_GOVERNANCE.md`](LIFECYCLE_GOVERNANCE.md) §3.3. Deactivate is the reversible
on/off switch (`VERIFIED` + `active=false`). Revoke is irreversible (`REVOKED`). Do not collapse
those into delete. Bulk variants live under the integration (`…/enrollments/deactivate-all`,
`reactivate-all`, `revoke-all`).

**POST /api/v1/enrollments/{id}/deactivate**

Reversibly deactivates a `VERIFIED` enrollment. Optional query parameter `reason` (max 500).
Self-deactivation of the caller's own MFA is forbidden. Admin MFA deactivation invalidates that
admin's bearer tokens.

**Response:** `204 No Content`. Typical errors: `400` (not eligible), `403` (self-guard / no
access), `404`.

**POST /api/v1/enrollments/{id}/reactivate**

Restores a deactivated `VERIFIED` enrollment. Optional `reason` (max 500). Cannot reactivate
`REVOKED` enrollments.

**Response:** `204 No Content`. Typical errors: `400` (already active, revoked, or not `VERIFIED`),
`403`, `404`.

**POST /api/v1/enrollments/{id}/revoke**

Permanently revokes the enrollment. Query parameter `reason` is **required** (min 10, max 500).
Self-revocation of the caller's own MFA is forbidden. Admin MFA revocation invalidates that admin's
bearer tokens.

**Response:** `204 No Content`. Typical errors: `400`, `403` (self-guard / system integration),
`404`.

Bruno: `bruno/enrollments-admin/deactivate.bru`, `reactivate.bru`, `revoke.bru`.

#### **Summary Table**

| Scenario | Use This API | Why |
|----------|--------------|-----|
| Admin provisioning workflow | `/api/v1/admins/{id}/onboarding` | Validates admin tenant, returns minimal credentials |
| TenantAdmin accessing own credentials | `/api/v1/admins/{id}/onboarding` | Only API that works for TenantAdmin |
| Enrollment management workflow | `/api/v1/enrollments/{id}` | Validates integration tenant, returns full details |
| Enrollment bind-handle QR | `/api/v1/enrollments/{id}/qrcode` | Same tenant gate as JSON GET; 404 hide-existence |
| GlobalAdmin monitoring enrollments | `/api/v1/enrollments/{id}` | Works for GlobalAdmin, comprehensive information |

**Security Note:**
Both APIs use different validation logic (admin tenant vs integration tenant), which is intentional and provides complementary security checks. The different validation approaches ensure proper access control for different use cases.

---

## 🔑 API Keys Authentication (Machine-to-Machine)

### **Overview**

API Keys provide machine-to-machine authentication for integrated applications (Integration API credentials), enabling server-to-server API calls without the login/logout overhead required for human administrators.

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

Lists API keys for a specific integration with server-side pagination. When `active` is omitted, only active keys are returned. When `active` is provided, results are filtered by that value. Secret keys are never included. TenantAdmin may access only integrations in their tenant; cross-tenant requests return **403**.

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

**Status Codes:**
- 200: Paginated list
- 401: Unauthorized
- 403: Access denied (caller cannot access the integration)

---

### d) Get API Key Details

**GET /api/v1/api-keys/{keyId}**

Retrieves details of a specific API key. Secret key is never included. TenantAdmin may access only keys for integrations in their tenant; cross-tenant requests return **403**.

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

**Status Codes:**
- 200: API key details
- 401: Unauthorized
- 403: Access denied (caller cannot access the key's integration)
- 404: API key not found

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

Immediately revokes an API key, making it unusable. Preserved for audit. TenantAdmin may revoke only keys for integrations in their tenant; cross-tenant requests return **403** and the key remains active (SEC-022).

**Request:**
```http
DELETE /api/v1/api-keys/42
Authorization: Bearer ezkey_admin_token...
```

**Response (204 No Content):**
```http
HTTP/1.1 204 No Content
```

**Status Codes:**
- 204: API key revoked
- 401: Unauthorized
- 403: Access denied (caller cannot access the key's integration)
- 404: API key not found
- 429: Rate limit exceeded

**Use Cases:**
- Compromised key security incident
- Key rotation cleanup after deploying new key
- Decommissioning an application

---

### g) Using API Keys for Authentication

Once created, use API keys with HTTP Basic Auth against **Integration API** (port 7080). Admin API does not authenticate API keys (HTTP Basic there returns **401**).

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

**Rate Limiting (Integration API):**
- Create auth attempt: 100 requests per minute per API key (default)
- Wait: 200 requests per minute per API key (default)
- Limits are per instance (no distributed coordination)
- Returns 429 Too Many Requests when limit exceeded (Problem Details from the Integration API rate-limit service). Missing API-key credentials return **401** with an empty body (`HttpStatusEntryPoint`).

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
- `integrationId`: Required when `userIdentifier` is used on Admin API; Integration API ignores it (scope is the API key's integration)
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
- 409: Conflict - authentication attempt already in final state (cannot be cancelled)
- 404: Not Found - authentication attempt not found
- 401: Unauthorized - authentication required
- 403: Forbidden - access denied to authentication attempt
- 500: Internal server error

**Notes:**
- Only attempts in `PENDING` or `READ` status can be cancelled
- Attempts that are already in a final state (`ACCEPTED`, `REJECTED`, `INVALID`, `EXPIRED`) cannot be cancelled
- Admin API: Bearer token or browser session. Integration API: API-key HTTP Basic.
- All cancellation operations are audited for security monitoring
- **Automatic TTL expiry (no HTTP call):** When `expires_at` passes without a response, a scheduled job in the Admin API process (default interval `ezkey.auth-attempt.expiry-scheduler.fixed-delay-ms`, often 60s) persists `EXPIRED` for eligible `PENDING`/`READ` rows and writes an `AUTH_ATTEMPT_EXPIRED` audit row with `event_action` `auth_attempt_expired_scheduler`. This is distinct from explicit cancel (`AUTH_ATTEMPT_CANCELLED`).

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

**Notes:**
- **Wait `status` vs clock expiry:** The wait `status` field reports a calculated outcome. Persisted final statuses (`ACCEPTED`, `REJECTED`, `INVALID`) take precedence over `expiresAt` — a deny (or accept / invalid) after the TTL clock has passed still returns that final status, not `EXPIRED`. Clock expiry applies only to `PENDING`/`READ`. Wait may also report `EXPIRED` for explicit cancel or supersession. Implementation: `AuthAttemptWaitService`.

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

**Authorization:** All `/api/v1/encryption-keys/**` routes are **Global Admin only**. Tenant Admins and API-key callers receive **403 Forbidden**. Manual mutations are audited with the acting `adminId`, client IP, and user-agent. Scheduled rotation / re-encryption jobs remain distinct system actors in core services (`triggeredBy` / `createdBy` = `SYSTEM`).

### Encryption Key Endpoints

**GET    /api/v1/encryption-keys**                    // List encryption keys (paginated) — Global Admin
**GET    /api/v1/encryption-keys/lifecycle-config**  // Rotation / re-encryption enable flags — Global Admin
**GET    /api/v1/encryption-keys/primary**           // Get current primary key — Global Admin
**GET    /api/v1/encryption-keys/{keyId}**           // Get key details — Global Admin
**POST   /api/v1/encryption-keys/rotate**            // Manually trigger key rotation — Global Admin
**GET    /api/v1/encryption-keys/reencryption-batches** // List re-encryption batches (paginated, optional filters) — Global Admin
**POST   /api/v1/encryption-keys/reencryption-batches/{batchId}/resume** // Resume failed batch — Global Admin
**POST   /api/v1/encryption-keys/reencrypt/create-batches** // Create re-encryption batches without processing — Global Admin

#### Lifecycle enable flags

**GET /api/v1/encryption-keys/lifecycle-config**

Thin honesty chrome for the Admin UI. Returns whether manual (and scheduled) key rotation and re-encryption are enabled.

**Response (200 OK):**
```json
{
  "rotationEnabled": false,
  "reencryptionEnabled": false
}
```

Flags come from `ezkey.encryption.rotation.enabled` and `ezkey.encryption.reencryption.enabled`, not from the product runtime profile name. Typical on opt-in `--runtime=base`: both `false`. Encryption at rest stays on.

#### Manual key rotation

**POST /api/v1/encryption-keys/rotate**

Optional query parameter: `reason` (10–500 characters) for audit.

**Responses:**
- **200 OK:** Body includes `newPrimaryKeyId` (the newly introduced key's ID) and a message describing the current state. The key is `PENDING`, not yet primary; a scheduled job promotes it to `PRIMARY` automatically once the sync window elapses. Re-encrypting existing records to the new key is a separate step (scheduled job or manual trigger).
- **409 Conflict:** RFC 9457 `ProblemDetail` (`type`, `title`, `status`, `detail`, `path`) when a `PENDING` key already exists and another introduction is not allowed yet, or when key rotation is inactive (`ezkey.encryption.rotation.enabled=false`; typical on opt-in `--runtime=base`). Problem types: `https://ezkey.io/problems/domain/pending-encryption-key-exists` or `https://ezkey.io/problems/domain/encryption-rotation-disabled`. The gate uses the config flag, not the product runtime profile name.
- **500 Internal Server Error:** Unexpected failure; body may include a legacy error payload for this endpoint.

#### List encryption keys

**GET /api/v1/encryption-keys**

Lists encryption keys with server-side pagination and optional filter by status.

**Query parameters:**
- `page` (optional, default 0): Zero-based page index.
- `size` (optional, default 20): Page size.
- `sort` (optional, default `introducedAt,DESC`): Sort property and direction (e.g. `keyId,asc`, `keyStatus,desc`, `introducedAt`, `recordsEncrypted`, `createdBy`, `createdAt`).
- `keyStatus` (optional): Filter by key status. Values: `PRIMARY`, `ENABLED`, `DISABLED`, `PENDING`. Omit for all keys.

**Response (200 OK):** Paginated. Body has `content` (array of encryption key objects) and `page` (object with `size`, `number`, `totalElements`, `totalPages`).

**Encryption key object (non-exhaustive):** `recordsEncrypted` is the **migration baseline** (ciphertext units when the key became `ENABLED`); `recordsReencrypted` is **cumulative** units migrated in completed batches (reset when demoted). `remainingRecords` is a **derived** sum on the same tracked targets as batch discovery, using an indexed equality lookup on each target's `*_encryption_key_id` companion column (not a `LIKE 'ENC:{keyId}:%'` scan; see `docs/REENCRYPTION_OPERATIONS.md` §1.2): for `ENABLED` keys it is the **migration backlog**; for `PRIMARY` it is **current live volume** on that key (operator observability). It is omitted (`null`) for `PENDING` / `DISABLED`. `verificationState` includes `PRIMARY_USAGE` when the snapshot is for the primary key. `lifecycleStage` and related fields come from the same verification model; auto-disable of old keys uses **drained** verification (zero remaining + batch state), not counters alone.

#### List re-encryption batches

**GET /api/v1/encryption-keys/reencryption-batches**

Lists re-encryption batch rows with server-side pagination and optional filters (aligned with other admin list endpoints).

**Query parameters:**
- `page` (optional, default 0): Zero-based page index.
- `size` (optional, default 20): Page size.
- `sort` (optional, default `createdAt,DESC`): Sort property and direction (e.g. `batchId,asc`, `status,desc`, `targetTable`, `targetColumn`, `createdAt`, `startedAt`, `completedAt`, `progressPct`, `recordsTotal`, `recordsDone`, `oldKey.keyId`, `newKey.keyId`).
- `status` (optional): Filter by batch status (`PENDING`, `IN_PROGRESS`, `COMPLETED`, `FAILED`, `PAUSED`). Omit for all. Invalid values are ignored (same behavior as `keyStatus` on list keys).
- `targetTable` (optional): Exact match on target table name (trimmed).
- `targetColumn` (optional): Exact match on target column name (trimmed).
- `oldKeyId` (optional): Filter by old encryption key id (`oldKey.keyId`).
- `newKeyId` (optional): Filter by new encryption key id (`newKey.keyId`).
- `createdAfter` (optional): Inclusive lower bound on `createdAt` (ISO-8601 instant).
- `createdBefore` (optional): Inclusive upper bound on `createdAt` (ISO-8601 instant).

**Response (200 OK):** Paginated. Body has `content` (array of batch objects) and `page` (object with `size`, `number`, `totalElements`, `totalPages`).

Example:

```json
{
  "content": [
    {
      "batchId": 1,
      "status": "COMPLETED",
      "targetTable": "ezkey_enrollment",
      "targetColumn": "device_public_key",
      "oldKeyId": 2,
      "newKeyId": 3,
      "progressPct": 100,
      "recordsTotal": 10,
      "recordsDone": 10,
      "recordsFailed": 0
    }
  ],
  "page": {
    "size": 20,
    "number": 0,
    "totalElements": 1,
    "totalPages": 1
  }
}
```

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

**Semantics note (QA):** `batchesCreated` is the **size of the batch set processed in this request** (pending and resumable batches after `createBatchesForOldKeys()` runs), not necessarily “only rows inserted in this call.” See `ReencryptionService.triggerFullReencryption()` and [REENCRYPTION_OPERATIONS.md](./REENCRYPTION_OPERATIONS.md).

**Error Responses:**
- **400 Bad Request**: Encryption not available or other rejected state
- **409 Conflict**: Re-encryption is inactive (`ezkey.encryption.reencryption.enabled=false`; typical on opt-in `--runtime=base`). Problem type: `https://ezkey.io/problems/domain/encryption-reencryption-disabled`. Same 409 on create-batches, per-key re-encrypt, and batch resume. The gate uses the config flag, not the product runtime profile name.
- **500 Internal Server Error**: Re-encryption processing failed

**Security Considerations:**
- Global Admin only (Tenant Admin / API key → 403)
- All operations are audited with acting adminId and client context
- May process large amounts of data - use with caution in production
- Batches are enqueued for background processing (progress via batches list)

---

#### b) Create re-encryption batches (no processing)

**POST /api/v1/encryption-keys/reencrypt/create-batches**

Creates re-encryption batch rows for all applicable old keys and targets (same discovery logic as the scheduled job’s `createBatchesForOldKeys`) but **does not** run cryptographic re-encryption. Use to prepare work for the next scheduler run or to inspect the queue without load.

**Request:**
```http
POST /api/v1/encryption-keys/reencrypt/create-batches
Authorization: Bearer ezkey_admin_token...
```

**Response (200 OK):**
```json
{
  "batchesCreated": 3,
  "message": "Created 3 re-encryption batches"
}
```

The `batchesCreated` field is the **net new batch rows** created in this call (count after minus count before).

**Error Responses:**
- **500 Internal Server Error**: Batch creation failed

**Security Considerations:**
- Global Admin only (Tenant Admin / API key → 403)
- Audited as manual batch creation with acting adminId and client context

---

#### c) Trigger Re-encryption for Specific Key

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
- Global Admin only (Tenant Admin / API key → 403)
- All operations are audited with acting adminId and client context
- May process large amounts of data - use with caution in production
- Batches are enqueued for background processing (progress via batches list)

---
