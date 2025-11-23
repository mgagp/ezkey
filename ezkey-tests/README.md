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

- **No Security Bypass**: All security features remain enabled during testing (in production mode)
- **Real-World Validation**: Tests validate security in production-like environment
- **End-to-End Coverage**: Complete security flows from enrollment to authentication
- **Two Testing Modes**: Production mode (with rate limits) and test mode (unrestricted)

### Docker Stack Integration

- **Manual Startup**: Docker stack started manually before tests
- **Production-Like**: Uses same Docker Compose configuration as production
- **Service Health Checks**: Tests verify services are healthy before execution
- **Profile-Based Configuration**: Choose between production mode (default) or test mode (permissive)

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

**Mode Production (Default)** - Rate limiting enabled with production values:
```bash
# Linux/Mac
./docker/start.sh

# Windows
docker\start.bat
```

**Mode Test Libre** - Rate limiting disabled for unrestricted testing:
```bash
# Linux/Mac
SPRING_PROFILES_ACTIVE=docker,docker-test ./docker/start.sh

# Windows PowerShell
$env:SPRING_PROFILES_ACTIVE="docker,docker-test"; .\docker\start.ps1
```

**Mode Selection:**
- **Production Mode (default)**: Tests run with production-like constraints. Rate limiting is enabled, requiring tests to handle rate limits using built-in synchronization and retry mechanisms.
- **Test Mode**: Rate limiting is disabled, allowing unrestricted testing in any order and frequency. Useful for development and debugging.

**Note**: The profile is set at stack startup and persists for the lifetime of the Docker stack. To change modes, restart the stack with the desired profile.

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

### 3. Create Admin Token (Optional but Recommended)

For tests that require admin authentication, create an admin token:

```bash
# Create admin token (will bootstrap if needed, reuse device credentials if available)
mvn test -pl ezkey-tests -Dtest=AdminTokenCreationTest
```

This will:
- Perform initial bootstrap (device enrollment) if needed (one-time)
- Create admin token using device credentials
- Save token to `.ezkey-test/admin-token.json` for reuse
- Save device credentials to `.ezkey-test/device-credentials.json` for future token creation

**Alternative**: Set admin token manually via environment variable:

```bash
# Linux/Mac
export EZKEY_ADMIN_TOKEN="your-admin-bearer-token"

# Windows PowerShell
$env:EZKEY_ADMIN_TOKEN="your-admin-bearer-token"
```

**Note**: The `AdminTokenCreationTest` is idempotent and independent - it can be run multiple times and will efficiently reuse cached tokens or device credentials.

### 4. Run Tests

```bash
# From project root
mvn test -pl ezkey-tests

# With verbose output
mvn test -pl ezkey-tests -X

# Run specific test class
mvn test -pl ezkey-tests -Dtest=AdminTokenCreationTest
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
    │   ├── RestAssuredTestConfig.java     # RestAssured HTTP client config
    │   ├── CryptoApiClient.java            # Crypto API REST client
    │   ├── TestDataFactory.java            # Test data creation helpers
    │   ├── AuthTokenManager.java           # Token management
    │   ├── AdminBootstrapService.java      # Admin bootstrap and token creation
    │   └── BootstrapCredentialsExtractor.java # Bootstrap credentials extraction
    └── security/
        ├── AbstractSecurityTest.java        # Base test class
        ├── admin/
        │   ├── AdminTokenCreationTest.java  # Building block: create admin token
        │   └── AdminAuthenticationSecurityTest.java
        ├── bootstrap/
        │   ├── BootstrapCredentialsExtractionTest.java # Extract bootstrap credentials
        │   └── AdminInitialBootstrapTest.java # Force initial bootstrap (optional)
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

#### 1. Admin Token Creation (`AdminTokenCreationTest`)

**Building Block Test** - Core capability for obtaining admin tokens:

- **Independent**: Can be run in any order, does not depend on other tests
- **Idempotent**: Can be run multiple times with the same result
- **Efficient**: Reuses cached tokens and device credentials when available
- **Three-tier strategy**:
  1. Reuse cached token from previous run (fastest)
  2. Reuse device credentials to create new token (fast)
  3. Perform initial bootstrap if needed (slower, one-time)

This test validates:
- Token creation (via bootstrap or reuse)
- Token validity by accessing protected endpoints

**Usage**: Use as a dependency for other tests that require an admin token.

#### 2. Admin Authentication Tests (`AdminAuthenticationSecurityTest`)

- Unauthorized access attempts (401)
- Invalid token handling
- Token invalidation (logout)
- Valid token access validation
- Automatic token recreation after logout (idempotent)

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

## Docker Stack Modes

### Production Mode (Default)

**Profile**: `docker` (default)

**Characteristics**:
- Rate limiting enabled with production values
- Tests must handle rate limits using built-in mechanisms:
  - **Synchronization**: `ReentrantLock` prevents parallel bootstrap attempts
  - **Retry with Backoff**: Exponential backoff (1s, 2s, 4s, 8s, 16s) for rate limit errors
- Validates production-like behavior
- Tests are resilient and handle constraints gracefully

**Use Cases**:
- Full test suite execution
- Production readiness validation
- Security testing with real constraints
- CI/CD pipeline testing

**Rate Limit Values** (Production):
- Bind: 3 requests / 5 minutes per IP
- Verify: 5 requests / 5 minutes per IP
- Pending: 10 requests / 1 minute per enrollment

### Test Mode (Permissive)

**Profile**: `docker,docker-test`

**Characteristics**:
- Rate limiting disabled or very permissive
- Allows unrestricted testing in any order and frequency
- No rate limit constraints
- Synchronization and retry mechanisms remain active (defense in depth)

**Use Cases**:
- Development and debugging
- Ad-hoc testing
- Rapid iteration
- Testing without rate limit concerns

**Activation**:
```bash
# Linux/Mac
SPRING_PROFILES_ACTIVE=docker,docker-test ./docker/start.sh

# Windows PowerShell
$env:SPRING_PROFILES_ACTIVE="docker,docker-test"; .\docker\start.ps1
```

**Note**: The profile is set at stack startup and persists for the lifetime of the Docker stack. To change modes, restart the stack.

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

## Bootstrap and Token Management

### Bootstrap Credentials

When Admin API starts for the first time on an empty database, it automatically creates:
- Initial global administrator
- System integration for admin MFA
- Global admin enrollment with proof token

These credentials are logged to the Admin API container logs and are required to:
- Bind the admin enrollment to a device
- Complete the admin passwordless login setup

### Extracting Bootstrap Credentials

The `BootstrapCredentialsExtractor` utility reads Docker container logs and extracts:
- **Enrollment ID**: ID of the admin enrollment
- **Enrollment Proof Token**: Token required for enrollment binding
- **Enrollment Challenge Code**: 6-digit code for enrollment verification
- **Recovery Codes**: Emergency access codes (single-use)

**File**: `.ezkey-test/bootstrap-credentials.json`
- Automatically created on first extraction
- Reused for subsequent test runs
- Can be manually edited if needed
- Should be added to `.gitignore` (contains sensitive data)

### Device Credentials (After Initial Bootstrap)

After the initial bootstrap (device enrollment), device credentials are saved:
- **Enrollment ID**: ID of the enrolled device
- **Private Key**: Device private key (Base64-encoded)
- **Public Key**: Device public key (Base64-encoded)
- **Key Size**: RSA key size in bits

**File**: `.ezkey-test/device-credentials.json`
- Created after initial bootstrap enrollment
- Reused for creating new admin tokens (much faster than full bootstrap)
- Enables idempotent token creation across test runs
- Should be added to `.gitignore` (contains sensitive data)

### Admin Token

The admin bearer token is saved after successful authentication:
- **Token**: Admin bearer token for API authentication

**File**: `.ezkey-test/admin-token.json`
- Created after successful token creation
- Reused for subsequent test runs (fastest path)
- Automatically recreated if invalidated (e.g., after logout)
- Should be added to `.gitignore` (contains sensitive data)

### Three-Tier Token Strategy

The `AdminBootstrapService` follows a three-tier strategy for efficiency:

1. **Tier 1: Cached Token** (fastest)
   - Loads token from `.ezkey-test/admin-token.json`
   - No API calls needed
   - Used if token file exists and is valid

2. **Tier 2: Reuse Device Credentials** (fast)
   - Loads device credentials from `.ezkey-test/device-credentials.json`
   - Creates new token using existing device enrollment
   - Skips bootstrap steps (bind + verify)
   - Only performs: login → respond → wait

3. **Tier 3: Initial Bootstrap** (slower, one-time)
   - Performs complete bootstrap flow
   - Extracts bootstrap credentials
   - Generates device key pair
   - Binds and verifies enrollment
   - Creates token
   - Saves device credentials for future reuse

### Using Credentials in Tests

```java
// In your test - get admin token (handles all tiers automatically)
String adminToken = authTokenManager.getAdminToken();

// Or use AdminBootstrapService directly
AdminBootstrapService bootstrapService = new AdminBootstrapService(
    dockerStackConfig, bootstrapCredentialsExtractor, cryptoApiClient);
String adminToken = bootstrapService.ensureAdminToken();
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
1. Run `AdminTokenCreationTest` to create a token: `mvn test -pl ezkey-tests -Dtest=AdminTokenCreationTest`
2. Or set `EZKEY_ADMIN_TOKEN` environment variable
3. Check token is valid: `curl -H "Authorization: Bearer $TOKEN" http://localhost:9080/api/v1/integrations`
4. If token is invalidated (e.g., after logout), `AdminTokenCreationTest` will automatically recreate it

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
