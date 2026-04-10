# Ezkey Java SDK

The Ezkey Java SDK provides easy integration with Ezkey Admin and Auth APIs for Java applications.

## Features

- **Zero External Dependencies**: Uses only Java standard libraries and minimal Jackson for JSON
- **Configurable Endpoints**: Support for custom Admin and Auth API URLs
- **Complete API Coverage**: All Admin and Auth API operations
- **Type-Safe**: Generated from OpenAPI specifications with strong typing
- **Simple Interface**: Easy-to-use wrapper classes over generated clients
- **Java 8+ Compatible**: Works with Java 8 and higher

## Quick Start

### 1. Build the SDK

```bash
# Linux/Mac
./build.sh

# Windows
build.cmd
```

### 2. Add to Your Project

Add the generated JAR to your classpath:

```bash
java -cp "ezkey-java-sdk-1.0.0.jar:your-app.jar" com.yourcompany.App
```

### 3. Basic Usage

```java
import org.ezkey.sdk.*;

// Initialize client with default localhost URLs
EzkeyClient client = EzkeyClient.create();

// Or with custom URLs
EzkeyClient client = EzkeyClient.create(
    "https://admin.yourcompany.com", 
    "https://auth.yourcompany.com"
);

// Use Admin API
IntegrationCreateResponseDto integration = client.admin().createIntegration(
    "https://company.com/logo.png",
    "My App", 
    "My application description"
);

// Use Auth API
EnrollmentBindResponseDto binding = client.auth().bindEnrollment(enrollmentId);
```

## Complete Integration Example

The SDK includes a comprehensive demo application that showcases the complete Ezkey integration workflow:

```bash
# Run the demo
java -cp "target/ezkey-java-sdk-1.0.0.jar:target/demo-classes" org.ezkey.sdk.demo.EzkeyDemoApplication
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

```java
// Create with default config (localhost)
EzkeyClient client = EzkeyClient.create();

// Create with custom URLs
EzkeyClient client = EzkeyClient.create(adminUrl, authUrl);

// Access APIs
EzkeyAdminAPI adminAPI = client.admin();
EzkeyAuthAPI authAPI = client.auth();
```

### Admin API Operations

```java
// Integration management
IntegrationCreateResponseDto integration = adminAPI.createIntegration(logo, name, description);
List<IntegrationResponseDto> integrations = adminAPI.getAllIntegrations();
IntegrationResponseDto integration = adminAPI.getIntegration(integrationId);
adminAPI.deleteIntegration(integrationId);

// Enrollment management
EnrollmentCreateResponseDto enrollment = adminAPI.createEnrollment(integrationId, name, challengeRequired);
List<EnrollmentResponseDto> enrollments = adminAPI.getAllEnrollments();
EnrollmentResponseDto enrollment = adminAPI.getEnrollment(enrollmentId);
adminAPI.deleteEnrollment(enrollmentId);

// Auth attempt management
AuthAttemptCreateResponseDto authAttempt = adminAPI.createAuthAttempt(enrollmentId, challengeRequested);
List<AuthAttemptDto> authAttempts = adminAPI.getAllAuthAttempts();
AuthAttemptDto authAttempt = adminAPI.getAuthAttempt(authAttemptId);

// Wait for authentication completion
AuthAttemptWaitResponseDto result = adminAPI.waitForResponse(authAttemptId);
AuthAttemptWaitResponseDto result = adminAPI.waitForResponse(authAttemptId, "60", "5"); // Custom timeout/polling

adminAPI.deleteAuthAttempt(authAttemptId);
```

### Auth API Operations

```java
// Enrollment operations
EnrollmentBindResponseDto binding = authAPI.bindEnrollment(enrollmentId);

EnrollmentVerifyResponseDto verification = authAPI.verifyEnrollment(
    enrollmentId, challengeResponse, devicePublicKey, enrollmentProofTokenSigned
);

// Authentication operations
AuthAttemptPendingResponseDto pending = authAPI.checkPendingAuth(
    enrollmentId, deviceProofToken, deviceProofTokenSigned
);

AuthAttemptRespondResponseDto response = authAPI.respondToAuth(
    authAttemptId, authAttemptProofTokenSigned, accepted
);

AuthAttemptRespondResponseDto response = authAPI.respondToAuth(
    authAttemptId, authAttemptProofTokenSigned, accepted, challengeResponse
);
```

## Configuration

### Default Configuration

By default, the SDK connects to:
- Admin API: `http://localhost:9080`
- Auth API: `http://localhost:8080`

### Custom Configuration

```java
EzkeyConfig config = new EzkeyConfig(
    "https://admin-api.yourcompany.com",
    "https://auth-api.yourcompany.com"
);
EzkeyClient client = new EzkeyClient(config);
```

## Error Handling

All SDK operations can throw `EzkeyException`:

```java
try {
    IntegrationCreateResponseDto integration = client.admin().createIntegration(logo, name, description);
} catch (EzkeyException e) {
    System.err.println("Error: " + e.getMessage());
    System.err.println("Status code: " + e.getStatusCode());
    System.err.println("Response body: " + e.getResponseBody());
    
    if (e.isClientError()) {
        // Handle 4xx errors (bad request, etc.)
    } else if (e.isServerError()) {
        // Handle 5xx errors (server issues)
    }
}
```

## Security Considerations

### Cryptographic Operations

The demo application includes examples of:
- RSA key pair generation
- Digital signature creation and verification
- Base64 encoding for key transport

```java
// Generate device key pair
KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
keyGen.initialize(2048);
KeyPair deviceKeys = keyGen.generateKeyPair();

// Sign data
Signature signature = Signature.getInstance("SHA256withRSA");
signature.initSign(privateKey);
signature.update(data.getBytes("UTF-8"));
byte[] signatureBytes = signature.sign();
String signedData = Base64.getEncoder().encodeToString(signatureBytes);
```

### Production Recommendations

- Use HTTPS endpoints in production
- Store private keys securely
- Implement proper key rotation
- Validate all signatures server-side
- Use secure random number generation

## Dependencies

The SDK uses minimal dependencies:
- Jackson 2.15.2 for JSON processing
- Java 8+ standard libraries

## Maven Integration

To use in a Maven project, add the JAR as a system dependency:

```xml
<dependency>
    <groupId>org.ezkey</groupId>
    <artifactId>ezkey-java-sdk</artifactId>
    <version>1.0.0</version>
    <scope>system</scope>
    <systemPath>${project.basedir}/lib/ezkey-java-sdk-1.0.0.jar</systemPath>
</dependency>
```

## Requirements

- Java 8+
- Maven 3.6+ (for building)

## License

MIT License - see LICENSE file in the project root.