---
name: Add Decrypt Endpoint to Crypto API
overview: "Add a generic decrypt endpoint to the crypto-api module to enable debugging of encrypted database columns. The endpoint will accept any encrypted value (format: ENC:keyID:Base64(ciphertext)) and return a detailed JSON response with decrypted plaintext, validation information, and metadata."
todos: []
---

# Add Decrypt Endpoint to Crypto API

## Overview

Add a generic decryption endpoint to the crypto-api module (`POST /api/v1/crypto/decrypt`) that enables debugging of encrypted database columns. The endpoint will accept any encrypted value and return a comprehensive JSON response with decrypted plaintext, validation metadata, and debugging information.

## Context

- **Encrypted fields**: `enrollment_proof_token`, `auth_attempt_proof_token`, `device_proof_token`, `integration_private_key`

- **Encryption format**: `ENC:keyID:Base64(ciphertext)` where keyID is the Tink primary key ID

- **Encryption service**: `EncryptionService` in `ezkey-core` handles all encryption/decryption

- **Current state**: crypto-api scans `org.ezkey.config` and `org.ezkey.signature` but not `org.ezkey.security`

- **Postman collection**: `EZ Key Crypto.postman_collection.json` contains endpoints for proof token, key pair, sign, and validate

## Implementation Plan

### 1. Update Component Scanning

**File**: [ezkey-crypto-api/src/main/java/org/ezkey/crypto/CryptoApplication.java](ezkey-crypto-api/src/main/java/org/ezkey/crypto/CryptoApplication.java)

- Add `"org.ezkey.security"` to `scanBasePackages` array

- This enables Spring to discover `EncryptionService` and `TinkKeyManager` from ezkey-core

### 2. Create Request DTO

**File**: [ezkey-crypto-api/src/main/java/org/ezkey/crypto/dto/DecryptRequestDto.java](ezkey-crypto-api/src/main/java/org/ezkey/crypto/dto/DecryptRequestDto.java) (new file)

- Field: `encryptedValue` (String, @NotBlank)
- Validation: must not be blank

- OpenAPI schema annotations with example

### 3. Create Response DTO

**File**: [ezkey-crypto-api/src/main/java/org/ezkey/crypto/dto/DecryptResponseDto.java](ezkey-crypto-api/src/main/java/org/ezkey/crypto/dto/DecryptResponseDto.java) (new file)

Response structure with comprehensive debugging information:

- `plaintext` (String): decrypted value or null if decryption failed/not encrypted

- `isEncrypted` (boolean): indicates if input had ENC: prefix

- `decryptionSuccessful` (boolean): indicates if decryption operation succeeded

- `keyId` (String): extracted key ID from encrypted value (null if not encrypted)

- `encryptedFormat` (String): detected format (e.g., "ENC:keyID:Base64" or "PLAINTEXT")

- `errorMessage` (String): error message if decryption failed (null if successful)

- `encryptionAvailable` (boolean): indicates if EncryptionService is initialized and available

### 4. Update CryptoController

**File**: [ezkey-crypto-api/src/main/java/org/ezkey/crypto/controller/CryptoController.java](ezkey-crypto-api/src/main/java/org/ezkey/crypto/controller/CryptoController.java)

- Add `EncryptionService` as constructor dependency (alongside `SignatureService`)

- Add `POST /api/v1/crypto/decrypt` endpoint method

- Endpoint logic:

1. Validate request

2. Check if encryption service is available

3. Check if value is encrypted (using `encryptionService.isEncrypted()`)

4. Extract key ID if encrypted (using `encryptionService.parseKeyIdFromPrefix()`)

5. Attempt decryption (using `encryptionService.decrypt()`)

6. Build comprehensive response DTO with all metadata

7. Handle exceptions gracefully (invalid format, decryption failures, etc.)

- OpenAPI annotations with operation description

### 5. Update Tests

**File**: [ezkey-crypto-api/src/test/java/org/ezkey/crypto/controller/CryptoControllerTest.java](ezkey-crypto-api/src/test/java/org/ezkey/crypto/controller/CryptoControllerTest.java)

- Add `EncryptionService` mock to `TestConfig`

- Test cases:
- Successful decryption of valid encrypted value

- Plaintext value (not encrypted)

- Invalid encrypted format

- Decryption failure scenario

- Empty/null encrypted value validation

- Encryption service unavailable scenario

### 6. Update Postman Collection

**File**: [postman/collections/v2.1/EZ Key Crypto.postman_collection.json](postman/collections/v2.1/EZ Key Crypto.postman_collection.json)

Add new request item following the existing pattern:

- **Request Name**: `"decrypt"`
- **Method**: `POST`
- **URL**: `{{base_url_crypto_api}}/api/v1/crypto/decrypt`
- **Body** (raw JSON):
  ```json
  {
      "encryptedValue": "{{encryptedValue}}"
  }
  ```

- **Test Script** (following existing pattern):
  - Check status code is 200 (or 400 for validation errors)
  - Validate response structure:
    - `plaintext` (string or null)
    - `isEncrypted` (boolean)
    - `decryptionSuccessful` (boolean)
    - `keyId` (string or null)
    - `encryptedFormat` (string)
    - `errorMessage` (string or null)
    - `encryptionAvailable` (boolean)
  - Save `plaintext` to environment variable `decryptedValue` if successful
  - Validate types and required fields
- **Description**: Comprehensive documentation including:
  - Purpose: Decrypt encrypted database column values for debugging
  - Request body fields explanation
  - Response fields explanation (all metadata fields)
  - Usage context: debugging encrypted columns (enrollment_proof_token, auth_attempt_proof_token, device_proof_token, integration_private_key)
  - Security notes: debugging tool only, returns sensitive plaintext
  - Example usage scenarios

**Pattern Reference**: Follow the structure of the existing `"sign"` and `"validate"` endpoints:

- Same test script structure (status check + response validation)
- Same description format (detailed markdown with sections)
- Use environment variables for request body (`{{encryptedValue}}`)
- Save useful response values to environment variables for subsequent requests

### 7. Update Documentation

**File**: [ezkey-crypto-api/README.md](ezkey-crypto-api/README.md)

- Add new endpoint documentation section
- Include request/response examples

- Add usage example for debugging encrypted database columns

- Note: This API is for debugging purposes only

## Technical Details

### EncryptionService Integration

- `EncryptionService` requires `TinkKeyManager` to be initialized

- `TinkKeyManager` can work in FILE mode (without database) for decryption operations

- The crypto-api must have access to Tink configuration (master key file, keyset file)

- Configuration properties should be shared via `application.properties` or environment variables

### Error Handling

- Invalid format: return response with `isEncrypted=false`, `decryptionSuccessful=false`

- Decryption failure: return response with `decryptionSuccessful=false`, `errorMessage` populated

- Service unavailable: return response with `encryptionAvailable=false`

- Always return HTTP 200 with detailed response (never fail on decryption errors - return metadata instead)

### Security Considerations

- This endpoint exposes decryption capabilities - acceptable for debugging tool

- No authentication required (consistent with other crypto-api endpoints)

- Should never be exposed in production (documented warning)

- Returns sensitive plaintext values - use with caution

## Files to Modify

1. `ezkey-crypto-api/src/main/java/org/ezkey/crypto/CryptoApplication.java` - Add security package scan

2. `ezkey-crypto-api/src/main/java/org/ezkey/crypto/dto/DecryptRequestDto.java` - New file

3. `ezkey-crypto-api/src/main/java/org/ezkey/crypto/dto/DecryptResponseDto.java` - New file

4. `ezkey-crypto-api/src/main/java/org/ezkey/crypto/controller/CryptoController.java` - Add decrypt endpoint

5. `ezkey-crypto-api/src/test/java/org/ezkey/crypto/controller/CryptoControllerTest.java` - Add tests

6. `postman/collections/v2.1/EZ Key Crypto.postman_collection.json` - Add decrypt endpoint request

7. `ezkey-crypto-api/README.md` - Update documentation

## Testing Strategy

- Unit tests with mocked EncryptionService

- Test all response scenarios (success, failure, plaintext, invalid format)

- Test edge cases (null, empty, malformed encrypted values)

- Manual testing with real encrypted values from database
- Integration testing via Postman collection:
  - Test with real encrypted values from database
  - Verify response structure matches expected format
  - Test various encrypted column types (enrollment_proof_token, auth_attempt_proof_token, etc.)
