# Ezkey JavaScript/TypeScript SDK

The Ezkey JavaScript/TypeScript SDK provides easy integration with Ezkey Admin and Auth APIs for Node.js and browser applications.

## Features

- **TypeScript Support**: Full TypeScript definitions and type safety
- **Zero External Dependencies**: Uses only Node.js built-ins and minimal fetch polyfill
- **Configurable Endpoints**: Support for custom Admin and Auth API URLs
- **Complete API Coverage**: All Admin and Auth API operations
- **Promise-Based**: Modern async/await API
- **Browser Compatible**: Works in both Node.js and browser environments
- **Node.js 14+ Compatible**: Works with Node.js 14 and higher

## Quick Start

### 1. Build the SDK

```bash
npm install
npm run build
```

### 2. Use in Your Project

#### Node.js/TypeScript

```typescript
import { EzkeyClient, EzkeyException } from './dist';

// Initialize client with default localhost URLs
const client = EzkeyClient.create();

// Or with custom URLs
const client = EzkeyClient.createWithUrls(
  'https://admin.yourcompany.com',
  'https://auth.yourcompany.com'
);

try {
  // Use Admin API
  const integration = await client.admin().createIntegration(
    'my-app',
    'My App',
    'My application description'
  );

  // Use Auth API
  const binding = await client.auth().bindEnrollment(enrollmentId, enrollmentProofToken);
} catch (error) {
  if (error instanceof EzkeyException) {
    console.error(`API Error: ${error.message}`);
    console.error(`Status: ${error.statusCode}`);
  }
}
```

#### JavaScript (Node.js)

```javascript
const { EzkeyClient, EzkeyException } = require('./dist');

const client = EzkeyClient.create();

client.admin().createIntegration(
  'my-app',
  'My App',
  'My application description'
).then(integration => {
  console.log('Created integration:', integration.id);
}).catch(error => {
  console.error('Error:', error.message);
});
```

## Complete Integration Example

The SDK includes a comprehensive demo application that showcases the complete Ezkey integration workflow:

```bash
# Build first
./build.sh

# Run the demo
node dist/demo/demo.js
```

The demo demonstrates:

1. **Creating an Integration** - Set up a new application integration
2. **Creating an Enrollment** - Generate enrollment for a user device
3. **Binding & Verifying** - Complete device enrollment process
4. **Creating Auth Attempt** - Request user authentication
5. **Checking Pending** - Poll for pending authentication requests
6. **Accepting/Denying** - Handle authentication responses

## API Reference

### EzkeyClient

Main entry point for the SDK:

```typescript
// Create with default config (localhost)
const client = EzkeyClient.create();

// Create with custom URLs
const client = EzkeyClient.createWithUrls(adminUrl, authUrl);

// Access APIs
const adminAPI = client.admin();
const authAPI = client.auth();
```

### Admin API Operations

```typescript
// Integration management
const integration = await adminAPI.createIntegration(code, name, description);
const integrations = await adminAPI.getAllIntegrations();
const integration = await adminAPI.getIntegration(integrationId);
await adminAPI.deleteIntegration(integrationId);

// Enrollment management
const enrollment = await adminAPI.createEnrollment(integrationId, name, challengeRequired);
const enrollments = await adminAPI.getAllEnrollments();
const enrollment = await adminAPI.getEnrollment(enrollmentId);
await adminAPI.deleteEnrollment(enrollmentId);

// Auth attempt management
const authAttempt = await adminAPI.createAuthAttempt(enrollmentId, challengeRequested);
const authAttempts = await adminAPI.getAllAuthAttempts();
const authAttempt = await adminAPI.getAuthAttempt(authAttemptId);

// Wait for authentication completion
const result = await adminAPI.waitForResponse(authAttemptId);
const result = await adminAPI.waitForResponseWithOptions(authAttemptId, '60', '5'); // Custom timeout/polling

await adminAPI.deleteAuthAttempt(authAttemptId);
```

### Auth API Operations

```typescript
// Enrollment operations
const binding = await authAPI.bindEnrollment(enrollmentId, enrollmentProofToken);

const verification = await authAPI.verifyEnrollment(
  enrollmentId, challengeResponse, devicePublicKey, enrollmentProofTokenSigned
);

// Authentication operations
const pending = await authAPI.checkPendingAuth(
  enrollmentId, enrollmentProofToken, deviceProofToken, deviceProofTokenSigned
);

const response = await authAPI.respondToAuth(
  authAttemptId, authAttemptProofTokenSigned, accepted
);

const response = await authAPI.respondToAuth(
  authAttemptId, authAttemptProofTokenSigned, accepted, challengeResponse
);
```

## Configuration

### Default Configuration

By default, the SDK connects to:
- Admin API: `http://localhost:9080`
- Auth API: `http://localhost:8080`

### Custom Configuration

```typescript
import { EzkeyConfig } from './dist';

const config: EzkeyConfig = {
  adminApiUrl: 'https://admin-api.yourcompany.com',
  authApiUrl: 'https://auth-api.yourcompany.com'
};

const client = new EzkeyClient(config);
```

## Error Handling

All SDK operations can throw `EzkeyException`:

```typescript
try {
  const integration = await client.admin().createIntegration(code, name, description);
} catch (error) {
  if (error instanceof EzkeyException) {
    console.error(`Error: ${error.message}`);
    console.error(`Status code: ${error.statusCode}`);
    console.error(`Response body: ${error.responseBody}`);
    
    if (error.isClientError()) {
      // Handle 4xx errors (bad request, etc.)
    } else if (error.isServerError()) {
      // Handle 5xx errors (server issues)
    }
  }
}
```

## Security Considerations

### Cryptographic Operations

The demo application includes examples of:
- RSA key pair generation using Node.js crypto module
- Digital signature creation and verification
- Base64 encoding for key transport

```typescript
import * as crypto from 'crypto';

// Generate device key pair
const deviceKeys = crypto.generateKeyPairSync('rsa', {
  modulusLength: 2048,
  publicKeyEncoding: { type: 'spki', format: 'pem' },
  privateKeyEncoding: { type: 'pkcs8', format: 'pem' }
});

// Sign data
const sign = crypto.createSign('SHA256');
sign.update(data, 'utf8');
const signature = sign.sign(privateKey, 'base64');
```

### Production Recommendations

- Use HTTPS endpoints in production
- Store private keys securely
- Implement proper key rotation
- Validate all signatures server-side
- Use secure random number generation

## Browser Support

To use in browsers, you may need to polyfill Node.js APIs:

```html
<!-- Include crypto polyfill for browsers -->
<script src="https://cdn.jsdelivr.net/npm/crypto-browserify@3.12.0/index.js"></script>
```

For modern bundlers (webpack, vite, etc.), configure Node.js polyfills as needed.

## Dependencies

The SDK uses minimal dependencies:
- `node-fetch@2.7.0` for HTTP requests
- `@types/node` for TypeScript definitions
- TypeScript compiler for build process

## Package.json Integration

Add to your package.json dependencies:

```json
{
  "dependencies": {
    "ezkey-javascript-sdk": "file:./path/to/ezkey-sdk/javascript"
  }
}
```

## Requirements

- Node.js 14+
- npm 6+
- TypeScript 5.3+ (for development)

## License

MIT License - see LICENSE file in the project root.