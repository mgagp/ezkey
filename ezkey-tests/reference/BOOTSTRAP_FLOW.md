# Admin Bootstrap and Token Creation Flow - Detailed Analysis

## Overview

This document explains step-by-step the complete flow for admin enrollment and token creation, including all API calls and when JSON files are created. The flow is separated into two phases:

1. **Initial Bootstrap** (one-time): Device enrollment (bind + verify)
2. **Token Creation** (reusable): Create admin token using enrolled device

This separation enables efficient, idempotent test execution.

## Flow Diagram

```
┌─────────────────────────────────────────────────────────────────┐
│              ADMIN BOOTSTRAP AND TOKEN CREATION                  │
└─────────────────────────────────────────────────────────────────┘

PHASE 1: INITIAL BOOTSTRAP (One-Time, Steps 1-4)
  ↓
Step 1: Load Bootstrap Credentials
  ↓
Step 2: Generate Device Key Pair (Crypto API)
  ↓
Step 3: Bind Device to Enrollment (Auth API)
  ↓
Step 4: Verify Enrollment (Auth API)
  ↓
  [Save Device Credentials to device-credentials.json]
  ↓
PHASE 2: TOKEN CREATION (Reusable, Steps 5-9)
  ↓
Step 5: Login Admin (Admin API) - Creates Auth Attempt
  ↓
Step 6: Get Pending Auth Attempt (Auth API)
  ↓
Step 7: Respond to Auth Attempt (Auth API)
  ↓
Step 8: Wait for Token (Admin API)
  ↓
Step 9: Save Token to File
```

### Three-Tier Strategy

The `AdminBootstrapService.ensureAdminToken()` method follows a three-tier strategy:

1. **Tier 1**: Reuse cached token from `admin-token.json` (fastest)
2. **Tier 2**: Reuse device credentials from `device-credentials.json` → Create token (fast)
3. **Tier 3**: Perform initial bootstrap (Steps 1-4) → Create token (slower, one-time)

## Detailed Step-by-Step Analysis

### Step 1: Load Bootstrap Credentials

**Method**: `BootstrapCredentialsExtractor.loadOrExtractCredentials()`

**What happens**:
1. Checks if `.ezkey-test/bootstrap-credentials.json` exists
2. If exists: Loads from file
3. If not exists: Extracts from Docker logs and saves to file

**File Created**: `.ezkey-test/bootstrap-credentials.json` (if not exists)

**File Content**:
```json
{
  "enrollmentId": 1,
  "enrollmentProofToken": "nNbHsGDI5xCFuKX_OcCxkgC1__ndyHYztAUcLAQtpgI.1763832282158.xZC44CaCF7lE38x4t_nKWg",
  "enrollmentChallengeCode": 284878,
  "recoveryCodes": []
}
```

**Note**: This file is created once and reused for all subsequent operations.

**API Calls**: None (file I/O only)

**RestAssured Configuration**: None needed

---

### Step 2: Generate Device Key Pair

**Method**: `CryptoApiClient.generateKeyPair()`

**API Call**:
- **Endpoint**: `GET http://localhost:9090/api/v1/crypto/keypair?keySize=2048`
- **Service**: Crypto API
- **Method**: GET
- **Query Parameters**: `keySize=2048` (default)

**Request**: None (GET request)

**Response**:
```json
{
  "privateKey": "MIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAgEAAoIBAQC...",
  "publicKey": "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA...",
  "keySize": 2048
}
```

**RestAssured Configuration**: Configured for Crypto API (`http://localhost:9090`, basePath `/api/v1/crypto`)

**File Created**: None

**Note**: This configures RestAssured for Crypto API. Must reconfigure for Auth API before next Auth API call.

---

### Step 3: Bind Device to Enrollment

**Method**: `AdminBootstrapService.bindDeviceOrSkip()`

**API Call**:
- **Endpoint**: `POST http://localhost:8080/api/v1/enrollments/bind`
- **Service**: Auth API
- **Method**: POST
- **Content-Type**: `application/json`

**Request Body**:
```json
{
  "enrollmentId": 1,
  "enrollmentProofToken": "nNbHsGDI5xCFuKX_OcCxkgC1__ndyHYztAUcLAQtpgI.1763832282158.xZC44CaCF7lE38x4t_nKWg"
}
```

**Response (200 OK)**:
```json
{
  "enrollmentId": 1,
  "enrollmentProofToken": "eyJhbGciOiJSUzI1NiJ9...",
  "integrationPublicKey": "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA...",
  "integrationName": "Ezkey System Admin",
  "integrationDescription": "System integration for admin authentication",
  "integrationLogo": null,
  "enrollmentName": "John Doe"
}
```

**RestAssured Configuration**: Configured for Auth API (`http://localhost:8080`, basePath `/api/v1`)

**File Created**: None

**Important**: The `enrollmentProofToken` in the response is the **bind proof token** that must be signed for verification in Step 4.

**Note**: If enrollment is already bound (409 CONFLICT), the bootstrap cannot proceed without resetting Docker or the enrollment.

---

### Step 4: Verify Enrollment

**Method**: `AdminBootstrapService.verifyEnrollment()`

**Sub-step 4a: Sign Bind Proof Token**

**API Call**:
- **Endpoint**: `POST http://localhost:9090/api/v1/crypto/sign`
- **Service**: Crypto API
- **Method**: POST

**Request Body**:
```json
{
  "data": "eyJhbGciOiJSUzI1NiJ9...",
  "privateKey": "MIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAgEAAoIBAQC..."
}
```

**Response**:
```json
{
  "signature": "U3Q2V59GRCwdoLnn/wu2CS1Gy0EyPrwE1tur/hW7/L1B5nFRj7RWhZ2OMorxHu09RbSAhiixRUVqo1wXXT9apD9hE8wHPjbbKJHzfb29fcbYapDDd+uqP8z2TLa7JxKy1GEoxFMmOnext3zbEct+jcl8iKRBLx93Zz4zkcC/lbBppTwAevzTd3uV9y9MIX/50iplHwBeEtT8dMW4Xdr7bEsoZinyYY7nrUJA02osQLqb5G9XUoJPEsaMpJfLG9WmXKfWDruXrQo4wYUg7y5JExnyyH4pkkGKhs2EtVvL/4oa7UVRIkjkCB7SWLDghr6CuauDYnK7xTCGKA8Fkmy1Ew=="
}
```

**RestAssured Configuration**: Configured for Crypto API (after signData call)

**Sub-step 4b: Verify Enrollment**

**API Call**:
- **Endpoint**: `POST http://localhost:8080/api/v1/enrollments/verify`
- **Service**: Auth API
- **Method**: POST

**Request Body**:
```json
{
  "enrollmentId": 1,
  "challengeResponse": 284878,
  "devicePublicKey": "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA...",
  "enrollmentProofTokenSigned": "U3Q2V59GRCwdoLnn/wu2CS1Gy0EyPrwE1tur/hW7/L1B5nFRj7RWhZ2OMorxHu09RbSAhiixRUVqo1wXXT9apD9hE8wHPjbbKJHzfb29fcbYapDDd+uqP8z2TLa7JxKy1GEoxFMmOnext3zbEct+jcl8iKRBLx93Zz4zkcC/lbBppTwAevzTd3uV9y9MIX/50iplHwBeEtT8dMW4Xdr7bEsoZinyYY7nrUJA02osQLqb5G9XUoJPEsaMpJfLG9WmXKfWDruXrQo4wYUg7y5JExnyyH4pkkGKhs2EtVvL/4oa7UVRIkjkCB7SWLDghr6CuauDYnK7xTCGKA8Fkmy1Ew=="
}
```

**Response (200 OK)**:
```json
{
  "active": true
}
```

**RestAssured Configuration**: **MUST reconfigure for Auth API** after Crypto API call (done in code)

**File Created**: `.ezkey-test/device-credentials.json` (after Step 4 completes)

**File Content**:
```json
{
  "enrollmentId": 1,
  "privateKey": "MIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAgEAAoIBAQC...",
  "publicKey": "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA...",
  "keySize": 2048
}
```

**Note**: After Step 4 completes successfully, the enrollment is verified and active. Device credentials are saved to `device-credentials.json` for reuse in future token creation (Tier 2 strategy). This enables efficient token creation without redoing the bootstrap flow.

---

### Step 5: Login Admin (Create Auth Attempt)

**Method**: `AdminBootstrapService.loginAdmin()`

**API Call**:
- **Endpoint**: `POST http://localhost:9080/api/v1/admin/auth/login`
- **Service**: Admin API
- **Method**: POST

**Request Body**:
```json
{
  "username": "admin.docker",
  "challengeRequested": true
}
```

**Response (200 OK)**:
```json
{
  "success": false,
  "status": "pending",
  "authAttemptId": 1,
  "challengeCode": 85,
  "username": "admin.docker",
  "adminType": "GLOBAL_ADMIN",
  "expiresAt": "2025-11-22T17:30:55Z",
  "message": "Challenge verification required. Enter code 85 on your device, then call /passwordless-wait."
}
```

**RestAssured Configuration**: Configured for Admin API (`http://localhost:9080`, basePath `/api/v1`)

**File Created**: None

**Important**: This creates an auth attempt in the database. The device must now respond to this attempt.

---

### Step 6: Get Pending Auth Attempt

**Method**: `AdminBootstrapService.respondToAuthAttempt()` (first part)

**Sub-step 6a: Generate Device Proof Token**

**API Call**:
- **Endpoint**: `GET http://localhost:9090/api/v1/crypto/prooftoken`
- **Service**: Crypto API
- **Method**: GET

**Request**: None (GET request)

**Response**:
```json
{
  "proofToken": "eyJhbGciOiJSUzI1NiJ9..."
}
```

**RestAssured Configuration**: Configured for Crypto API

**Sub-step 6b: Sign Device Proof Token**

**API Call**:
- **Endpoint**: `POST http://localhost:9090/api/v1/crypto/sign`
- **Service**: Crypto API
- **Method**: POST

**Request Body**:
```json
{
  "data": "eyJhbGciOiJSUzI1NiJ9...",
  "privateKey": "MIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAgEAAoIBAQC..."
}
```

**Response**:
```json
{
  "signature": "abc123..."
}
```

**RestAssured Configuration**: Configured for Crypto API

**Sub-step 6c: Get Pending Auth Attempt**

**API Call**:
- **Endpoint**: `POST http://localhost:8080/api/v1/auth-attempts/pending`
- **Service**: Auth API
- **Method**: POST

**Request Body**:
```json
{
  "enrollmentId": 1,
  "enrollmentProofToken": "nNbHsGDI5xCFuKX_OcCxkgC1__ndyHYztAUcLAQtpgI.1763832282158.xZC44CaCF7lE38x4t_nKWg",
  "deviceProofToken": "eyJhbGciOiJSUzI1NiJ9...",
  "deviceProofTokenSigned": "abc123..."
}
```

**Response (200 OK)**:
```json
{
  "authAttemptId": 1,
  "authAttemptProofToken": "eyJhbGciOiJSUzI1NiJ9...",
  "authAttemptProofTokenSignedByIntegration": "xyz789...",
  "authAttemptChallengeRequired": true
}
```

**RestAssured Configuration**: **MUST reconfigure for Auth API** after Crypto API calls (done in code)

**File Created**: None

**Important**: The `authAttemptProofToken` must be signed with the device private key for the respond call.

---

### Step 7: Respond to Auth Attempt

**Method**: `AdminBootstrapService.respondToAuthAttempt()` (second part)

**Sub-step 7a: Sign Auth Attempt Proof Token**

**API Call**:
- **Endpoint**: `POST http://localhost:9090/api/v1/crypto/sign`
- **Service**: Crypto API
- **Method**: POST

**Request Body**:
```json
{
  "data": "eyJhbGciOiJSUzI1NiJ9...",
  "privateKey": "MIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAgEAAoIBAQC..."
}
```

**Response**:
```json
{
  "signature": "h1PSlQeQgDP6+IHtuO5WFusiF4Eq0bi7R6aMakAFFw/zsrALwhcqYqa0zR5elwzVZBiZMAfi/tcf2cd99yod+Ohh4PkKx+EPbKbuVt5+LihojGb4PNzy/D0K0myrIRKw5p/bEZdsx4VZigVt6Xlff9w9GY5k7xO7MlZpl8Z36VCdgpUgTlMeojPWOdWzregZWFlw+vF/njpmSVdT9Z8IX42K/nhtAR6ccMb4SN04fzcDVvIzNs78hkgwnkQzECS0UNlJZY00SOSsDyyihsDbLf6Smv60tmiJtXqRLREmZjcgUEuzjuPklcVZiQ6FMvP9szCxblB6LUkvpqlA9djcZA=="
}
```

**RestAssured Configuration**: Configured for Crypto API

**Sub-step 7b: Respond to Auth Attempt**

**API Call**:
- **Endpoint**: `POST http://localhost:8080/api/v1/auth-attempts/respond`
- **Service**: Auth API
- **Method**: POST

**Request Body**:
```json
{
  "authAttemptId": 1,
  "authAttemptAccepted": true,
  "authAttemptProofTokenSignedByDevice": "h1PSlQeQgDP6+IHtuO5WFusiF4Eq0bi7R6aMakAFFw/zsrALwhcqYqa0zR5elwzVZBiZMAfi/tcf2cd99yod+Ohh4PkKx+EPbKbuVt5+LihojGb4PNzy/D0K0myrIRKw5p/bEZdsx4VZigVt6Xlff9w9GY5k7xO7MlZpl8Z36VCdgpUgTlMeojPWOdWzregZWFlw+vF/njpmSVdT9Z8IX42K/nhtAR6ccMb4SN04fzcDVvIzNs78hkgwnkQzECS0UNlJZY00SOSsDyyihsDbLf6Smv60tmiJtXqRLREmZjcgUEuzjuPklcVZiQ6FMvP9szCxblB6LUkvpqlA9djcZA==",
  "authAttemptChallengeResponse": 85
}
```

**Response (200 OK)**:
```json
{
  "result": "APPROVED",
  "message": "Authentication approved"
}
```

**RestAssured Configuration**: **MUST reconfigure for Auth API** after Crypto API call (done in code)

**File Created**: None

---

### Step 8: Wait for Token

**Method**: `AdminBootstrapService.waitForPasswordlessAuth()`

**API Call**:
- **Endpoint**: `POST http://localhost:9080/api/v1/admin/auth/passwordless-wait`
- **Service**: Admin API
- **Method**: POST

**Request Body**:
```json
{
  "authAttemptId": 1,
  "challengeCode": 85
}
```

**Response (200 OK)**:
```json
{
  "success": true,
  "token": "ezkey_abc123def456...",
  "adminType": "GLOBAL_ADMIN",
  "username": "admin.docker",
  "expiresAt": "2025-11-23T17:25:55Z",
  "message": "Authentication successful"
}
```

**RestAssured Configuration**: Configured for Admin API (`http://localhost:9080`, basePath `/api/v1`)

**File Created**: None (yet)

---

### Step 9: Save Token to File

**Method**: `AdminBootstrapService.saveTokenToFile()`

**What happens**:
1. Creates `.ezkey-test/` directory if it doesn't exist
2. Creates/overwrites `.ezkey-test/admin-token.json` with the token

**File Created**: `.ezkey-test/admin-token.json`

**File Content**:
```json
{
  "token": "ezkey_abc123def456..."
}
```

**API Calls**: None (file I/O only)

**RestAssured Configuration**: None needed

---

## Critical RestAssured Configuration Points

### When RestAssured is Configured for Crypto API:
- `CryptoApiClient.generateKeyPair()` - Configures for Crypto API
- `CryptoApiClient.generateProofToken()` - Configures for Crypto API
- `CryptoApiClient.signData()` - Configures for Crypto API

### When RestAssured Must be Reconfigured:
1. **After Step 2** (generateKeyPair) → Before Step 3 (bindDevice) → Reconfigure for Auth API
2. **After Step 4a** (signData for verify) → Before Step 4b (verifyEnrollment) → Reconfigure for Auth API
3. **After Step 6a/6b** (generateProofToken + signData for pending) → Before Step 6c (pending) → Reconfigure for Auth API
4. **After Step 7a** (signData for respond) → Before Step 7b (respond) → Reconfigure for Auth API

### Current Code Status:
- ✅ Step 2 → Step 3: Reconfiguration done in `bindDeviceOrSkip()` (line 159)
- ✅ Step 4a → Step 4b: Reconfiguration done in `verifyEnrollment()` (line 208)
- ✅ Step 6a/6b → Step 6c: Reconfiguration done in `respondToAuthAttempt()` (line 292)
- ✅ Step 7a → Step 7b: Reconfiguration done in `respondToAuthAttempt()` (line 318) - **FIXED**

---

## Summary of Files Created

1. **`.ezkey-test/bootstrap-credentials.json`** (Step 1)
   - Created by: `BootstrapCredentialsExtractor.extractCredentials()`
   - Contains: enrollmentId, enrollmentProofToken, enrollmentChallengeCode, recoveryCodes
   - **Reused**: For all bootstrap and token creation operations

2. **`.ezkey-test/device-credentials.json`** (After Step 4 - Initial Bootstrap Only)
   - Created by: `AdminBootstrapService.saveDeviceCredentials()`
   - Contains: enrollmentId, privateKey, publicKey, keySize
   - **Reused**: For creating new tokens without redoing bootstrap (Tier 2)
   - **Created once**: After initial bootstrap enrollment completes

3. **`.ezkey-test/admin-token.json`** (Step 9)
   - Created by: `AdminBootstrapService.saveTokenToFile()`
   - Contains: token (admin bearer token)
   - **Reused**: For subsequent test runs (Tier 1 - fastest path)
   - **Recreated**: Automatically if invalidated (e.g., after logout)

---

## Common Issues and Solutions

### Issue 1: "No static resource api/v1/crypto/auth-attempts/respond"
**Cause**: RestAssured not reconfigured for Auth API after Crypto API call
**Solution**: Add `RestAssuredTestConfig.configureForAuthApi(dockerStackConfig)` after `cryptoApiClient.signData()` call

### Issue 2: "Enrollment already bound"
**Cause**: Previous test run bound enrollment but didn't complete verification
**Solution**: Reset Docker stack (`docker-compose down -v`) or reset enrollment manually

### Issue 3: "No device enrolled for passwordless authentication"
**Cause**: Enrollment bound but not verified (status = BOUND, not VERIFIED)
**Solution**: Complete enrollment verification (Step 4)

---

## Testing Checklist

### Initial Bootstrap (One-Time)
- [ ] Step 1: Bootstrap credentials file exists or can be extracted
- [ ] Step 2: Device key pair generated successfully
- [ ] Step 3: Enrollment bind successful (200 OK)
- [ ] Step 4: Enrollment verify successful (200 OK, active: true)
- [ ] Device credentials file created (`device-credentials.json`)

### Token Creation (Reusable)
- [ ] Step 5: Admin login successful (200 OK, status: pending)
- [ ] Step 6: Pending auth attempt retrieved (200 OK)
- [ ] Step 7: Auth attempt response successful (200 OK, result: APPROVED)
- [ ] Step 8: Token obtained successfully (200 OK, success: true)
- [ ] Step 9: Token file created successfully (`admin-token.json`)

### Idempotence Validation
- [ ] Running `AdminTokenCreationTest` multiple times reuses cached token (Tier 1)
- [ ] Deleting `admin-token.json` recreates token using device credentials (Tier 2)
- [ ] Deleting both files performs full bootstrap (Tier 3)
- [ ] After logout, token is automatically recreated for subsequent tests
