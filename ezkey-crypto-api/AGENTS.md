# Ezkey Crypto API – Agent Notes

This file is UTF-8 without BOM.

## 🎯 Purpose and Role

**Crypto API is a testing and debugging tool** that provides cryptographic primitives as REST endpoints. It enables:

- **Testing**: Generate keys, sign data, and validate signatures for end-to-end testing flows
- **Debugging**: Decrypt encrypted database columns to investigate issues during development
- **Integration**: Support tools like Postman that don't have built-in cryptographic capabilities

**⚠️ CRITICAL**: This API is **NOT for production use**. It has no authentication, exposes sensitive operations, and should only be used in secure, isolated testing environments.

## 🐳 Docker Integration

Crypto API is **included in the Docker stack** and starts automatically with `./clean-start.sh`:

- **Port**: `9090` (not 8081 - that's auth-api)
- **Container**: `ezkey-crypto-api`
- **Base URL**: `http://localhost:9090`
- **Swagger UI**: `http://localhost:9090/swagger-ui.html`

**Quick Check**:
```bash
# Verify Crypto API is running
docker ps | grep crypto-api

# Check logs
docker logs ezkey-crypto-api --tail 50

# Test health
curl http://localhost:9090/actuator/health
```

## 🔑 Core Endpoints

### 1. Proof Token Generation
**GET** `/api/v1/crypto/prooftoken`

Generates cryptographically secure proof tokens for authentication flows.

**Use Case**: Generate tokens for enrollment or auth attempt flows when testing.

### 2. EC P-256 Key Pair Generation
**GET** `/api/v1/crypto/keypair`

Generates EC P-256 (secp256r1) key pairs for device simulation.

**Use Case**: Create device keys for enrollment testing without needing native crypto libraries.

### 3. Sign Data
**POST** `/api/v1/crypto/sign`

Signs data using EC P-256 ECDSA-SHA256 with a private key.

**Use Case**: Sign proof tokens or challenge data during enrollment/auth flows.

### 4. Validate Signature
**POST** `/api/v1/crypto/validate`

Validates EC P-256 ECDSA-SHA256 signatures.

**Use Case**: Verify signatures in test assertions or debugging scenarios.

### 5. Decrypt Encrypted Database Column
**POST** `/api/v1/crypto/decrypt`

**⚠️ DEBUGGING TOOL**: Decrypts encrypted database column values for investigation.

**Use Case**:
- Investigate encrypted fields: `enrollment_proof_token`, `auth_attempt_proof_token`, `device_proof_token`, `integration_private_key`
- Compare decrypted values with trace logs
- Troubleshoot encryption/decryption issues

**Request Format**:
```json
{
  "encryptedValue": "ENC:2865054995:AarFRRPHitKf32X1m/o8j0lJY71IGc1IN9dd65kd/..."
}
```

**Response Includes**:
- `plaintext`: Decrypted value (or null if failed)
- `isEncrypted`: Whether input had ENC: prefix
- `decryptionSuccessful`: Whether decryption succeeded
- `keyId`: Extracted key ID from encrypted value
- `encryptedFormat`: Detected format (ENC:keyID:Base64, ENC:INVALID, or PLAINTEXT)
- `errorMessage`: Error details if decryption failed
- `encryptionAvailable`: Whether EncryptionService is initialized

## 🔄 Keyset Management

### Keyset Loading Behavior

**Keyset is loaded at startup** from the configured keyset file (`/etc/ezkey/keysets/keyset.json.encrypted` in Docker).

### Key Rotation Impact

When key rotation occurs in the main application (admin-api or auth-api):

1. ✅ Keyset file is updated with new primary key
2. ⚠️ **Crypto API continues using keyset loaded at startup** (old keyset)
3. 🔄 **Restart Crypto API** to load the new keyset

**Why Restart is Required**:
- Simplicity: Crypto API is a debugging tool - restart ensures predictable behavior
- No database sync: Crypto API uses FILE storage mode only (no database access)
- Predictable state: Guarantees exact keyset from file at startup time

**Workflow After Key Rotation**:
```bash
# 1. Key rotation occurs in admin-api/auth-api
# 2. Keyset file is updated
# 3. Restart Crypto API
docker restart ezkey-crypto-api

# 4. Crypto API now uses new keyset
```

### Decryption Capabilities

Crypto API can decrypt values encrypted with:
- ✅ **Current keyset**: Keys loaded at startup
- ✅ **Previous keysets**: Tink supports multiple keys - old keys remain available until removed from keyset
- ❌ **Keys not in keyset**: Decryption fails if key was removed from keyset file

## 💡 Common Workflows

### Complete Enrollment Flow

1. **Generate device key pair**:
   ```bash
   curl http://localhost:9090/api/v1/crypto/keypair
   ```

2. **Generate proof token**:
   ```bash
   curl http://localhost:9090/api/v1/crypto/prooftoken
   ```

3. **Sign proof token**:
   ```bash
   curl -X POST http://localhost:9090/api/v1/crypto/sign \
     -H "Content-Type: application/json" \
     -d '{"data": "proof-token-here", "privateKey": "private-key-here"}'
   ```

4. **Use signature in enrollment request** to admin-api/auth-api

### Debugging Encrypted Columns

1. **Query database** for encrypted value:
   ```sql
   SELECT enrollment_proof_token FROM ezkey_enrollment WHERE enrollment_id = 123;
   ```

2. **Copy encrypted value** (format: `ENC:keyID:Base64...`)

3. **Decrypt via API**:
   ```bash
   curl -X POST http://localhost:9090/api/v1/crypto/decrypt \
     -H "Content-Type: application/json" \
     -d '{"encryptedValue": "ENC:2865054995:AarFRRPHitKf32X1m/..."}'
   ```

4. **Compare `plaintext`** with trace logs or expected values

5. **Use metadata fields** (`keyId`, `encryptedFormat`, `errorMessage`) to troubleshoot

## ⚠️ Watchouts

### Port Configuration
- ✅ **Crypto API**: Port `9090`
- ❌ **NOT Port 8081**: That's auth-api
- Always verify you're calling the correct port

### Security Warnings
- ⚠️ **No authentication**: API is open - only use in isolated test environments
- ⚠️ **Sensitive operations**: Decrypt endpoint returns plaintext values
- ⚠️ **Private keys in requests**: Keys are transmitted in API calls
- ⚠️ **Never expose in production**: This is a testing/debugging tool only

### Keyset Synchronization
- ⚠️ **Restart required after key rotation**: Crypto API doesn't auto-reload keyset
- ⚠️ **File-based only**: No database sync - uses keyset file only
- ⚠️ **Startup keyset**: Uses keyset loaded at startup, not current file state

### Error Handling
- **200 OK doesn't mean success**: Check `decryptionSuccessful` field in decrypt response
- **Plaintext can be null**: Even with 200 OK if decryption failed
- **Check `encryptionAvailable`**: Verify EncryptionService is initialized

## 🔍 Investigation Protocol

### When Decryption Fails

1. **Check `encryptionAvailable`**: Should be `true`
2. **Verify `encryptedFormat`**: Should be `ENC:keyID:Base64`, not `ENC:INVALID` or `PLAINTEXT`
3. **Check `keyId`**: Verify key exists in current keyset
4. **Review `errorMessage`**: Provides specific failure reason
5. **Verify keyset**: Check if Crypto API was restarted after key rotation
6. **Check logs**: `docker logs ezkey-crypto-api` for initialization errors

### When API is Unavailable

1. **Verify container is running**: `docker ps | grep crypto-api`
2. **Check health endpoint**: `curl http://localhost:9090/actuator/health`
3. **Review startup logs**: `docker logs ezkey-crypto-api --tail 100`
4. **Check port mapping**: Ensure port 9090 is accessible
5. **Verify volume mounts**: Encryption secrets volume should be mounted

## 📚 Documentation References

### Primary Documentation
- **[README.md](README.md)** - Complete API documentation with all endpoints, examples, and usage patterns
- **[docs/ENDPOINT.md](../docs/ENDPOINT.md)** - Main API endpoint documentation

### Related Documentation
- **[docs/analysis/CRYPTO_API_KEYSET_ROTATION_BEHAVIOR.md](../docs/analysis/CRYPTO_API_KEYSET_ROTATION_BEHAVIOR.md)** - Detailed keyset rotation behavior analysis
- **[ezkey-core/README.md](../ezkey-core/README.md)** - Core encryption service documentation

## 🎯 Best Practices

### For Testing
- Use Crypto API to generate test data (keys, tokens, signatures)
- Keep test flows independent - don't rely on shared state
- Use unique identifiers to avoid conflicts
- Prefer direct API calls over complex crypto libraries in tests

### For Debugging
- Always verify `decryptionSuccessful` field, not just HTTP status
- Use metadata fields (`keyId`, `encryptedFormat`) to understand encryption state
- Compare decrypted values with trace logs for verification
- Check `encryptionAvailable` before troubleshooting decryption failures

### For Integration
- Crypto API is stateless - each request is independent
- No session management - no need to maintain state between calls
- Fast operations - suitable for high-frequency test scenarios
- Docker integration - always available in test stack

## Next Steps

- Verify Crypto API is accessible on port 9090 before running tests
- Use decrypt endpoint for investigating encrypted column issues
- Remember to restart Crypto API after key rotations
- Keep security warnings in mind - never expose in production
