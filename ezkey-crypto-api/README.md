# Ezkey Crypto API

The Ezkey Crypto API provides cryptographic services to support testing tools like Postman that need to generate keys, sign data, and validate signatures but don't have built-in cryptographic capabilities.

## Purpose

Postman and similar testing tools cannot easily:
- Generate RSA key pairs
- Sign data with private keys  
- Validate digital signatures

This crypto API exposes these crypto primitives as REST endpoints, enabling complete end-to-end testing of the Ezkey authentication flows.

## Endpoints

### 1. Generate Proof Token
**GET** `/api/v1/crypto/prooftoken`

Generates a cryptographically secure proof token for use in authentication flows.

**Response:**
```json
{
  "proofToken": "_kzdCf7M75wTfw1rvaPG1YdxlrQSq60LnP0o-S19zC4.1755647562970.GRWDB__P5BQ1TkTDpv_0tQ"
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

Swagger UI is available at: `http://localhost:8080/swagger-ui.html`

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

## Security Notes

⚠️ **WARNING**: This API is intended for testing and simulation purposes only. 

- Private keys are transmitted in API requests
- No authentication/authorization is implemented
- Should never be used in production environments
- Only use in secure, isolated testing environments

## Testing

Run unit tests:
```bash
mvn test
```

The test suite includes validation of all endpoints and error handling scenarios.