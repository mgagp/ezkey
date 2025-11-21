# Ezkey Functional Tests

Real-world security end-to-end functional tests for Ezkey using RestAssured against Docker stack.

## Overview

This module provides comprehensive security-focused end-to-end testing for Ezkey's Admin API, Auth API, and Crypto API. Tests run against a Docker stack that closely mirrors production, ensuring security features are validated in a real-world environment.

## Architecture

```
┌─────────────────────────────────────────────────┐
│        Docker Stack (Manually Started)          │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐     │
│  │ Admin API│  │ Auth API │  │Crypto API│     │
│  │ :9080    │  │ :8080    │  │ :9090    │     │
│  └──────────┘  └──────────┘  └──────────┘     │
│       ▲              ▲              ▲          │
│       └──────────────┼──────────────┘          │
│                      │                          │
│              ┌───────┴───────┐                 │
│              │  PostgreSQL   │                 │
│              │    :5432      │                 │
│              └───────────────┘                 │
└─────────────────────────────────────────────────┘
        ▲              ▲              ▲
        │              │              │
        │ HTTP         │ HTTP         │ HTTP
        │              │              │
┌───────┴──────────────┴──────────────┴──────────┐
│      ezkey-tests (Java Functional Tests)       │
│  ┌──────────────────────────────────────────┐  │
│  │ RestAssured Test Suites                  │  │
│  │  - Admin Authentication Tests            │  │
│  │  - API Key Authorization Tests           │  │
│  │  - Cryptographic Validation Tests      │  │
│  │  - Enrollment Flow Tests                │  │
│  │  - Auth Attempt Flow Tests              │  │
│  │  - Rate Limiting Tests                  │  │
│  └──────────────────────────────────────────┘  │
└──────────────────────────────────────────────────┘
```

## Key Principles

### Security-First Testing

- **No Security Bypass**: All security features remain enabled during testing
- **Real-World Validation**: Tests validate security in production-like environment
- **End-to-End Coverage**: Complete security flows from enrollment to authentication

### Docker Stack Integration

- **Manual Startup**: Docker stack started manually before tests
- **Production-Like**: Uses same Docker Compose configuration as production
- **Service Health Checks**: Tests verify services are healthy before execution

### Cryptographic Operations

- **Crypto API**: All cryptographic operations use Crypto API (port 9090)
- **No Core Dependencies**: Tests do not depend on ezkey-core crypto module
- **REST-Based**: Key generation, signing, and validation via REST endpoints

## Prerequisites

- **Java 21** or higher
- **Maven 3.6+**
- **Docker Desktop** (or Docker Engine + Docker Compose)
- **Docker Stack Running**: Admin API, Auth API, Crypto API, PostgreSQL

## Quick Start

### 1. Start Docker Stack

```bash
# Linux/Mac
./docker/start.sh

# Windows
docker\start.bat
```

Wait for all services to be healthy (check logs or health endpoints).

### 2. Extract Bootstrap Credentials (First Time Only)

After the first Docker stack startup, extract bootstrap credentials from logs:

```bash
# Extract credentials from Docker logs
mvn test -pl ezkey-tests -Dtest=BootstrapCredentialsExtractionTest
```

This will:
- Read Admin API container logs
- Extract enrollment credentials (ID, proof token, challenge code, recovery codes)
- Save to `.ezkey-test/bootstrap-credentials.json` for future use

**Note**: This only needs to be done once after initial Docker stack startup. The credentials file will be reused for subsequent test runs.

### 3. Set Admin Token (Optional)

For tests that require admin authentication, set the admin token:

```bash
# Linux/Mac
export EZKEY_ADMIN_TOKEN="your-admin-bearer-token"

# Windows PowerShell
$env:EZKEY_ADMIN_TOKEN="your-admin-bearer-token"
```

**Note**: Admin token can be obtained by:
- Logging in via Admin API (requires device approval using bootstrap credentials)
- Using a pre-configured token from Docker initialization

### 4. Run Tests

```bash
# From project root
mvn test -pl ezkey-tests

# With verbose output
mvn test -pl ezkey-tests -X

# Run specific test class
mvn test -pl ezkey-tests -Dtest=AdminAuthenticationSecurityTest
```

## Test Structure

### Test Organization

```
ezkey-tests/
├── pom.xml
├── README.md
└── src/test/java/org/ezkey/tests/
    ├── config/
    │   └── DockerStackConfig.java          # Docker stack connection config
    ├── util/
    │   ├── RestAssuredConfig.java          # RestAssured HTTP client config
    │   ├── CryptoApiClient.java            # Crypto API REST client
    │   ├── TestDataFactory.java            # Test data creation helpers
    │   └── AuthTokenManager.java           # Token management
    └── security/
        ├── AbstractSecurityTest.java        # Base test class
        ├── admin/
        │   └── AdminAuthenticationSecurityTest.java
        ├── apikey/
        │   └── ApiKeySecurityTest.java
        ├── crypto/
        │   └── CryptographicSecurityTest.java
        ├── enrollment/
        │   └── EnrollmentFlowSecurityTest.java
        ├── authentication/
        │   └── AuthenticationFlowSecurityTest.java
        └── ratelimit/
            └── RateLimitingSecurityTest.java
```

### Test Categories

#### 1. Admin Authentication Tests (`AdminAuthenticationSecurityTest`)

- Unauthorized access attempts (401)
- Invalid token handling
- Token invalidation (logout)
- Valid token access validation

#### 2. API Key Authorization Tests (`ApiKeySecurityTest`)

- API key can create auth attempts (own integration)
- API key cannot access admin endpoints (403)
- API key cannot access enrollment/integration endpoints (403)
- API key ownership validation

#### 3. Cryptographic Validation Tests (`CryptographicSecurityTest`)

- RSA key pair generation via Crypto API
- Proof token generation
- Data signing with private keys
- Signature validation
- Invalid signature rejection

#### 4. Enrollment Flow Tests (`EnrollmentFlowSecurityTest`)

- Complete enrollment with signature validation
- Enrollment binding security (proof token)
- Enrollment verification with cryptographic proof
- Invalid enrollment token handling

#### 5. Authentication Flow Tests (`AuthenticationFlowSecurityTest`)

- Complete passwordless authentication flow
- Pending auth attempt retrieval
- Auth attempt response with signature
- Wait API validation

#### 6. Rate Limiting Tests (`RateLimitingSecurityTest`)

- Rate limit enforcement on auth attempt creation
- Rate limit enforcement on wait API
- Rate limit exceeded handling (429)

## Configuration

### Environment Variables

Service URLs can be configured via environment variables:

```bash
# Admin API URL (default: http://localhost:9080)
export EZKEY_ADMIN_API_URL="http://localhost:9080"

# Auth API URL (default: http://localhost:8080)
export EZKEY_AUTH_API_URL="http://localhost:8080"

# Crypto API URL (default: http://localhost:9090)
export EZKEY_CRYPTO_API_URL="http://localhost:9090"

# Admin bearer token (optional, for authenticated tests)
export EZKEY_ADMIN_TOKEN="your-admin-token"
```

### Rate Limiting Configuration

Rate limiting is **enabled** in tests to validate security features. For automated testing, configure higher limits in Docker environment:

```properties
# In application-docker.properties or Docker environment
ezkey.admin.ratelimit.auth-attempt.create.limit=1000
ezkey.admin.ratelimit.auth-attempt.create.window-minutes=15
ezkey.admin.ratelimit.auth-attempt.wait.limit=2000
ezkey.admin.ratelimit.auth-attempt.wait.window-minutes=15
```

## Test Execution Model

### Workflow

1. **Developer starts Docker stack**: `./docker/start.sh` (or `docker\start.bat` on Windows)
2. **Developer verifies stack health** (optional but recommended)
3. **Developer sets admin token** (optional, for authenticated tests)
4. **Developer runs tests**: `mvn test -pl ezkey-tests`
5. **Tests connect to Docker stack** via HTTP (localhost)
6. **Tests validate security features** end-to-end
7. **Developer stops Docker stack** when done (manual)

### Benefits

- ✅ Real production-like environment
- ✅ Security features fully enabled
- ✅ Can inspect database (DBeaver, etc.)
- ✅ Can use Postman/curl for ad-hoc testing
- ✅ Simple, straightforward workflow

## Writing Tests

### Base Test Class

All security tests extend `AbstractSecurityTest`:

```java
public class MySecurityTest extends AbstractSecurityTest {
  
  @Test
  public void testSomething() {
    // dockerStackConfig, authTokenManager, cryptoApiClient, 
    // and testDataFactory are available
  }
}
```

### Example: Creating Test Data

```java
// Create integration
Integer integrationId = testDataFactory.createIntegration();

// Create enrollment
Integer enrollmentId = testDataFactory.createEnrollment(integrationId);

// Create auth attempt
Integer authAttemptId = testDataFactory.createAuthAttempt(enrollmentId);
```

### Example: Using Crypto API

```java
// Generate key pair
RsaKeyPair keyPair = cryptoApiClient.generateKeyPair(2048);

// Generate proof token
String proofToken = cryptoApiClient.generateProofToken();

// Sign data
String signature = cryptoApiClient.signData(data, keyPair.privateKey());

// Validate signature
boolean isValid = cryptoApiClient.validateSignature(data, signature, keyPair.publicKey());
```

### Example: Making API Calls

```java
// Configure for Admin API
configureForAdminApi(dockerStackConfig);

// Make authenticated request
Response response = given()
    .contentType(ContentType.JSON)
    .header("Authorization", "Bearer " + authTokenManager.getAdminToken())
    .when()
    .get("/integrations")
    .then()
    .statusCode(200)
    .extract()
    .response();
```

## Bootstrap Credentials

### What Are Bootstrap Credentials?

When Admin API starts for the first time on an empty database, it automatically creates:
- Initial global administrator
- System integration for admin MFA
- Global admin enrollment with proof token

These credentials are logged to the Admin API container logs and are required to:
- Bind the admin enrollment to a device
- Complete the admin passwordless login setup

### Extracting Credentials

The `BootstrapCredentialsExtractor` utility reads Docker container logs and extracts:
- **Enrollment ID**: ID of the admin enrollment
- **Enrollment Proof Token**: Token required for enrollment binding
- **Enrollment Challenge Code**: 6-digit code for enrollment verification
- **Recovery Codes**: Emergency access codes (single-use)

### Credentials File

Credentials are saved to `.ezkey-test/bootstrap-credentials.json`:
- Automatically created on first extraction
- Reused for subsequent test runs
- Can be manually edited if needed
- Should be added to `.gitignore` (contains sensitive data)

### Using Credentials in Tests

```java
// In your test
BootstrapCredentials credentials = bootstrapCredentialsExtractor.loadOrExtractCredentials();

// Use credentials for enrollment binding
Integer enrollmentId = credentials.enrollmentId();
String enrollmentProofToken = credentials.enrollmentProofToken();
Integer challengeCode = credentials.enrollmentChallengeCode();
```

## Troubleshooting

### Tests Fail: Bootstrap Credentials Not Found

**Problem**: `BootstrapCredentialsExtractor` cannot find credentials in logs

**Solution**:
1. Ensure Docker stack has completed startup
2. Check Admin API logs: `docker logs ezkey-admin-api | grep "GLOBAL ADMIN"`
3. Verify bootstrap completed: Look for "✅ Global Admin Enrollment created"
4. Run extraction test manually: `mvn test -pl ezkey-tests -Dtest=BootstrapCredentialsExtractionTest`

### Tests Fail: Services Not Healthy

**Problem**: `DockerStackConfig.verifyServicesHealthy()` throws exception

**Solution**: 
1. Ensure Docker stack is running: `docker ps`
2. Check service health: `curl http://localhost:9080/actuator/health`
3. Review Docker logs: `./docker/manage.sh logs`

### Tests Fail: 401 Unauthorized

**Problem**: Tests requiring admin token fail with 401

**Solution**:
1. Set `EZKEY_ADMIN_TOKEN` environment variable
2. Or implement login flow (requires device approval)
3. Check token is valid: `curl -H "Authorization: Bearer $TOKEN" http://localhost:9080/api/v1/integrations`

### Tests Fail: 403 Forbidden

**Problem**: API key tests fail with 403

**Solution**:
1. Verify API key is created correctly
2. Check API key belongs to correct integration
3. Verify HTTP Basic Auth header format: `Basic base64(integrationKey:secretKey)`

### Tests Fail: Connection Refused

**Problem**: Cannot connect to Docker services

**Solution**:
1. Verify Docker stack is running
2. Check ports are not blocked: `netstat -an | grep 9080`
3. Verify service URLs in `DockerStackConfig`

## CI/CD Considerations

**Note**: These tests are designed for **manual execution** after Docker stack startup. They are not currently integrated into CI/CD pipelines.

For CI/CD integration:
1. Start Docker stack in CI environment
2. Wait for services to be healthy
3. Run tests
4. Stop Docker stack

## Benefits Over Previous Approach

### vs Karate Tests

- ✅ **Security Enabled**: Tests validate security, don't bypass it
- ✅ **Real Environment**: Uses Docker stack, not embedded servers
- ✅ **Java-Based**: Better IDE support, easier debugging
- ✅ **Type Safety**: Java types vs JavaScript in Karate

### vs Unit Tests

- ✅ **End-to-End**: Validates complete flows across services
- ✅ **Integration**: Tests service interactions
- ✅ **Security**: Validates security features in real environment

## Future Enhancements

- [ ] Add performance benchmarking tests
- [ ] Implement chaos engineering scenarios
- [ ] Add security penetration tests
- [ ] Create load testing scenarios
- [ ] Add API contract testing
- [ ] Implement smoke test suite
- [ ] CI/CD pipeline integration

## References

- [RestAssured Documentation](https://rest-assured.io/)
- [Docker Documentation](docker/README.md)
- [API Endpoints](docs/ENDPOINT.md)
- [Security Guide](docs/API_SECURITY_TESTING_GUIDE.md)

## License

This module is part of the Ezkey project and is licensed under the MIT License.
