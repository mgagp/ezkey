# Ezkey Crypto API

The Ezkey Crypto API provides cryptographic services to support testing tools like Postman that need to generate keys, sign data, and validate signatures but don't have built-in cryptographic capabilities. It also provides debugging tools for investigating encrypted database columns.

## Purpose

Postman and similar testing tools cannot easily:
- Generate RSA key pairs
- Sign data with private keys
- Validate digital signatures
- Encrypt plaintext values for testing
- Decrypt encrypted database column values for debugging

This crypto API exposes these crypto primitives as REST endpoints, enabling complete end-to-end testing of the Ezkey authentication flows and debugging of encrypted database columns.

## Endpoints

### 1. Generate Proof Token
**GET** `/api/v1/crypto/prooftoken`

Generates a cryptographically secure proof token for use in authentication flows.

**Response:**
```json
{
  "proofToken": "_kzdCf7M75wTfw1rvaPG1YdxlrQSq60LnP0o-S19zC4.GRWDB__P5BQ1TkTDpv_0tQ"
}
```

### 2. Generate RSA Key Pair
**GET** `/api/v1/crypto/keypair?keySize=2048`

Generates a new RSA key pair for device simulation.

**Parameters:**
- `keySize` (optional): Key size in bits (1024-4096, default: 2048)

**Response:**
```json
{
  "privateKey": "MIIEvAIBADANBgkqhkiG9w0BAQEFAASCBKYwggSi...",
  "publicKey": "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA...",
  "keySize": 2048
}
```

### 3. Sign Data
**POST** `/api/v1/crypto/sign`

Signs data using RSA-SHA256 with the provided private key.

**Request:**
```json
{
  "data": "Hello, World!",
  "privateKey": "MIIEvAIBADANBgkqhkiG9w0BAQEFAASCBKYwggSi..."
}
```

**Response:**
```json
{
  "signature": "kP0V+UzNJPfIfFqmO47Z/l/WcdTcoqyKb3MUeOqmNnbo...",
  "originalData": "Hello, World!",
  "algorithm": "SHA256withRSA"
}
```

### 4. Validate Signature
**POST** `/api/v1/crypto/validate`

Validates a signature against original data using the provided public key.

**Request:**
```json
{
  "data": "Hello, World!",
  "signature": "kP0V+UzNJPfIfFqmO47Z/l/WcdTcoqyKb3MUeOqmNnbo...",
  "publicKey": "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA..."
}
```

**Response:**
```json
{
  "valid": true,
  "message": "Signature is valid",
  "algorithm": "SHA256withRSA"
}
```

### 5. Encrypt Plaintext Value
**POST** `/api/v1/crypto/encrypt`

Encrypts a plaintext value and returns it in the standard encrypted format (`ENC:keyID:Base64(ciphertext)`). This endpoint is designed for testing and debugging purposes, enabling generation of encrypted test data and verification of encryption/decryption round-trips.

**Request:**
```json
{
  "plaintext": "my-secret-value"
}
```

**Response:**
```json
{
  "encryptedValue": "ENC:2865054995:AarFRRPHitKf32X1m/o8j0lJY71IGc1IN9dd65kd/...",
  "encryptionSuccessful": true,
  "keyId": "2865054995",
  "encryptedFormat": "ENC:keyID:Base64",
  "errorMessage": null,
  "encryptionAvailable": true
}
```

**Response Fields:**
- `encryptedValue`: Encrypted value in `ENC:keyID:Base64(ciphertext)` format, or original plaintext if encryption unavailable/failed
- `encryptionSuccessful`: Boolean indicating if the encryption operation succeeded
- `keyId`: Primary key ID used for encryption (null if encryption failed/unavailable)
- `encryptedFormat`: Format of the output ("ENC:keyID:Base64", or "PLAINTEXT" if encryption unavailable)
- `errorMessage`: Error message if encryption failed (null if successful)
- `encryptionAvailable`: Boolean indicating if EncryptionService is initialized and available

**Status Codes:**
- 200: Encryption operation completed (check `encryptionSuccessful` field for actual result)
- 400: Invalid request data (empty plaintext value)
- 500: Internal server error

**Usage Example - Testing Encryption/Decryption Round-trip:**

1. **Encrypt a test value:**
   ```bash
   curl -X POST http://localhost:9090/api/v1/crypto/encrypt \
     -H "Content-Type: application/json" \
     -d '{
       "plaintext": "test-secret-value"
     }'
   ```

2. **Copy the `encryptedValue` from the response**

3. **Decrypt to verify round-trip:**
   ```bash
   curl -X POST http://localhost:9090/api/v1/crypto/decrypt \
     -H "Content-Type: application/json" \
     -d '{
       "encryptedValue": "ENC:2865054995:AarFRRPHitKf32X1m/..."
     }'
   ```

4. **Verify the decrypted `plaintext` matches the original value**

**Note**: If a value is already encrypted (has `ENC:` prefix), the endpoint will detect this and return an error message indicating the value is already encrypted, preventing double encryption.

### 6. Decrypt Encrypted Database Column Value
**POST** `/api/v1/crypto/decrypt`

Decrypts an encrypted database column value for debugging purposes. This endpoint enables investigation of encrypted fields such as `enrollment_proof_token`, `auth_attempt_proof_token`, `device_proof_token`, and `integration_private_key`.

**Request:**
```json
{
  "encryptedValue": "ENC:1234567890:YWJjZGVmZ2hpams="
}
```

**Response:**
```json
{
  "plaintext": "decrypted-plaintext-value",
  "isEncrypted": true,
  "decryptionSuccessful": true,
  "keyId": "1234567890",
  "encryptedFormat": "ENC:keyID:Base64",
  "errorMessage": null,
  "encryptionAvailable": true
}
```

**Response Fields:**
- `plaintext`: Decrypted plaintext value, original input if not encrypted, or null if decryption failed
- `isEncrypted`: Boolean indicating if the input value had the ENC: prefix (was encrypted)
- `decryptionSuccessful`: Boolean indicating if the decryption operation succeeded
- `keyId`: Extracted key ID from encrypted value (null if not encrypted)
- `encryptedFormat`: Detected format of the input value ("ENC:keyID:Base64", "ENC:INVALID", or "PLAINTEXT")
- `errorMessage`: Error message if decryption failed (null if successful)
- `encryptionAvailable`: Boolean indicating if EncryptionService is initialized and available

**Status Codes:**
- 200: Decryption operation completed (check `decryptionSuccessful` field for actual result)
- 400: Invalid request data (empty encrypted value)
- 500: Internal server error

**Usage Example - Debugging Encrypted Columns:**

1. **Query database to get encrypted value:**
   ```sql
   SELECT enrollment_proof_token FROM ezkey_enrollment WHERE enrollment_id = 123;
   ```

2. **Copy the encrypted value** (format: `ENC:1234567890:YWJjZGVmZ2hpams=`)

3. **Decrypt using the API:**
   ```bash
   curl -X POST http://localhost:9090/api/v1/crypto/decrypt \
     -H "Content-Type: application/json" \
     -d '{
       "encryptedValue": "ENC:1234567890:YWJjZGVmZ2hpams="
     }'
   ```

4. **Compare decrypted plaintext** with trace logs or expected values to verify token matching

**Debugging Workflow:**
- Copy encrypted value from database column
- Use this endpoint to decrypt
- Compare `plaintext` result with trace logs
- Use metadata fields (`keyId`, `encryptedFormat`, `errorMessage`) to troubleshoot encryption issues

## Usage Example: Complete Crypto Flow

1. **Generate keys for your simulated device:**
   ```bash
   curl http://localhost:8080/api/v1/crypto/keypair
   ```

2. **Generate a proof token:**
   ```bash
   curl http://localhost:8080/api/v1/crypto/prooftoken
   ```

3. **Sign the proof token with your private key:**
   ```bash
   curl -X POST http://localhost:8080/api/v1/crypto/sign \
     -H "Content-Type: application/json" \
     -d '{
       "data": "your-proof-token-here",
       "privateKey": "your-private-key-here"
     }'
   ```

4. **Use the signature in your enrollment/auth requests to the main Ezkey APIs**

5. **Optionally validate signatures for testing:**
   ```bash
   curl -X POST http://localhost:8080/api/v1/crypto/validate \
     -H "Content-Type: application/json" \
     -d '{
       "data": "your-data-here",
       "signature": "your-signature-here",
       "publicKey": "your-public-key-here"
     }'
   ```

## API Documentation

Swagger UI is available at: `http://localhost:8080/swagger-ui/index.html`

## Running the Application

```bash
cd ezkey-crypto-api
mvn spring-boot:run
```

The application will start on port 8080.

## Error Handling

All endpoints return standardized error responses:

```json
{
  "code": "ERROR_CODE",
  "message": "Human readable error message",
  "timestamp": "2025-08-19T23:52:49.897042608",
  "path": "/api/v1/crypto/endpoint"
}
```

Common error codes:
- `INVALID_PARAMETER`: Invalid request parameters
- `VALIDATION_ERROR`: Request validation failed
- `INTERNAL_ERROR`: Internal server error
- `UNKNOWN_ERROR`: Unexpected error

## Keyset Management and Key Rotation

### Keyset Loading Behavior

The Crypto API loads the encryption keyset from the configured keyset file (`ezkey.encryption.keyset-file`) **at application startup**. The keyset is loaded once during initialization and remains in memory for the duration of the application's lifecycle.

### Key Rotation Impact

When a key rotation occurs in the main application (admin-api or auth-api):

1. **The keyset file is updated** with the new primary key
2. **Crypto API continues using the keyset loaded at startup** (the old keyset)
3. **To use the new keyset, Crypto API must be restarted**

### Why Restart is Required

- **Simplicity**: Crypto API is a debugging tool. Requiring a restart ensures predictable behavior and avoids complexity
- **No Database Sync**: Crypto API does not have database access, so it cannot detect keyset changes via database synchronization
- **File-based Only**: Crypto API uses FILE storage mode exclusively (no DATABASE or HYBRID mode support)
- **Predictable State**: Restart guarantees that Crypto API uses the exact keyset that exists in the file at startup time

**Note**: While `TinkKeyManager` has a mechanism to check for keyset file changes during runtime (`checkAndReloadKeysetIfNeeded()`), this automatic reload is not guaranteed. It only occurs when encryption operations are performed, is throttled (checks max every 5 seconds), and may fail silently. For a debugging tool, requiring a restart after key rotation is simpler, more predictable, and more reliable than depending on automatic reload behavior.

### Workflow After Key Rotation

1. Key rotation occurs in the main application (admin-api or auth-api)
2. The keyset file is updated with the new primary key
3. **Restart Crypto API** to load the new keyset:
   ```bash
   # Stop Crypto API
   # Start Crypto API (it will load the updated keyset file)
   ```
4. Crypto API can now decrypt values encrypted with the new key

### Decryption Capabilities

Crypto API can decrypt values encrypted with:
- **Current keyset**: Values encrypted with keys in the keyset loaded at startup
- **Previous keysets**: Tink supports multiple keys in a keyset, so old keys remain available for decryption until they are removed from the keyset

**Note**: If a value was encrypted with a key that is no longer in the keyset file, decryption will fail. In this case, ensure Crypto API is using a keyset file that contains the required key.

## Security Notes

⚠️ **WARNING**: This API is intended for testing and simulation purposes only.

- Private keys are transmitted in API requests
- No authentication/authorization is implemented
- Should never be used in production environments
- Only use in secure, isolated testing environments
- **Decrypt endpoint**: Returns sensitive plaintext values - use with extreme caution
- **Debugging tool**: The decrypt endpoint is specifically for debugging encrypted database columns and should never be exposed in production

## Testing

Run unit tests:
```bash
mvn test
```

The test suite includes validation of all endpoints and error handling scenarios.
