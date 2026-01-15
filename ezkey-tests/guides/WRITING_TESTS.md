# Writing Functional Tests - Ezkey Project

## Overview

This guide explains how to write functional tests for the Ezkey project, focusing on authentication, test independence, multi-tenant isolation, and REST API best practices.

## Authentication Mechanisms

### Single Login Endpoint for All Admins

Ezkey uses **one single login endpoint** for all admin types:

```
POST /admin/auth/login
```

The system automatically determines the admin type from the database and generates tokens with appropriate scope information via `AdminPrincipal`:

**Currently Implemented Admin Types:**
- `GLOBAL_ADMIN`: System-wide administrator with full access
- `TENANT_ADMIN`: Tenant-specific administrator with limited scope

**Future (Not Yet Implemented):**
- `INTEGRATION_ADMIN`: Integration-specific administrator (defined in schema but not activated in Phase 1)

**AdminPrincipal Fields:**
- `adminType`: GLOBAL_ADMIN or TENANT_ADMIN (currently)
- `tenantId`: null for GlobalAdmin, tenant ID for TenantAdmin
- `integrationId`: null (reserved for future INTEGRATION_ADMIN)

## Obtaining Admin Tokens

### GlobalAdmin Token

Use `AuthTokenManager.getAdminToken()` which follows a 3-tier strategy:

1. **Tier 1**: Environment variable `EZKEY_ADMIN_TOKEN` (highest priority)
2. **Tier 2**: Cached token from `.ezkey-test/admin-token.json` (validated before use)
3. **Tier 3**: Automatic bootstrap via `AdminBootstrapService` (if dependencies set)

**Example:**

```java
@BeforeEach
public void setUp() {
  super.setUp();

  try {
    String adminToken = authTokenManager.getAdminToken();
    // Use adminToken for API calls
  } catch (IllegalStateException e) {
    org.junit.jupiter.api.Assumptions.assumeTrue(
      false,
      "Admin token not available. Set EZKEY_ADMIN_TOKEN environment variable."
    );
  }
}
```

### TenantAdmin Token

Use `TenantAdminTestHelper.createAndLoginTenantAdmin()` which performs complete device simulation:

1. Creates TenantAdmin record via Admin API
2. Retrieves onboarding credentials (enrollmentId, enrollmentProofToken, enrollmentChallenge) via `GET /api/v1/admins/{id}/onboarding`
3. Generates device key pair via Crypto API
4. Binds device to enrollment via Auth API
5. Verifies enrollment with signature
6. Performs login and returns token

Note: the onboarding endpoint returns `recoveryCodes: null` by design (recovery codes cannot be retrieved in plain text after provisioning).

The helper also follows a 3-tier strategy:
1. **Tier 1**: Cached token from `.ezkey-test/tenant-admin-{tenantId}-token.json` (validated)
2. **Tier 2**: Cached device credentials from `.ezkey-test/tenant-admin-{tenantId}-device-credentials.json`
3. **Tier 3**: Full device simulation and enrollment

**Example:**

```java
@BeforeEach
public void setUp() {
  super.setUp();

  try {
    String globalAdminToken = authTokenManager.getAdminToken();

    TenantAdminTestHelper tenantAdminTestHelper =
      new TenantAdminTestHelper(dockerStackConfig, testDataFactory, cryptoApiClient);

    Integer tenantId = testDataFactory.createTenant("Test Tenant", globalAdminToken);

    String tenantAdminToken = tenantAdminTestHelper.createAndLoginTenantAdmin(
      "tenant.admin",
      tenantId,
      globalAdminToken
    );

    // Use tenantAdminToken for API calls
  } catch (IllegalStateException e) {
    org.junit.jupiter.api.Assumptions.assumeTrue(
      false,
      "Test setup failed: " + e.getMessage()
    );
  }
}
```

## Test Independence and Idempotence

### Principles

1. **Independence**: Each test can obtain its own admin token and run independently
2. **Idempotence**: Tests can be executed multiple times with the same result
3. **Efficiency**: Reuse cached tokens and device credentials when available

### Patterns for Independence

#### Unique Naming

Use timestamps to ensure unique names:

```java
String uniqueSuffix = String.valueOf(System.currentTimeMillis());
Integer tenantId = testDataFactory.createTenant("Tenant " + uniqueSuffix, adminToken);
```

#### Graceful Degradation

Handle missing tokens gracefully using JUnit assumptions:

```java
@Test
public void testAdminCanListIntegrations() {
  try {
    String adminToken = authTokenManager.getAdminToken();

    Response response = given()
      .header("Authorization", "Bearer " + adminToken)
      .when()
      .get("/integrations")
      .then()
      .extract()
      .response();

    assertThat(response.getStatusCode()).isEqualTo(200);
  } catch (IllegalStateException e) {
    org.junit.jupiter.api.Assumptions.assumeTrue(
      false,
      "Admin token not available."
    );
  }
}
```

#### Opportunistic Reuse

Use `findOrCreate*` methods to reuse existing entities:

```java
// Reuses existing tenant with matching name if found
Integer tenantId = testDataFactory.findOrCreateTenant("My Tenant", adminToken);

// Reuses existing integration with matching name and tenant if found
Integer integrationId = testDataFactory.findOrCreateIntegration(
  "My Integration",
  tenantId,
  adminToken
);
```

### AbstractSecurityTest Base Class

All security tests extend `AbstractSecurityTest` which provides:

```java
@BeforeEach
public void setUp() {
  // 1. Configuration Docker stack
  dockerStackConfig = new DockerStackConfig();
  dockerStackConfig.verifyServicesHealthy();

  // 2. Initialization of utilities
  authTokenManager = new AuthTokenManager(dockerStackConfig);
  cryptoApiClient = new CryptoApiClient(dockerStackConfig);
  testDataFactory = new TestDataFactory(dockerStackConfig, authTokenManager);
  bootstrapCredentialsExtractor = new BootstrapCredentialsExtractor();

  // 3. Configuration bootstrap dependencies
  authTokenManager.setBootstrapDependencies(
    bootstrapCredentialsExtractor,
    cryptoApiClient
  );
}

@AfterEach
public void tearDown() {
  RestAssuredTestConfig.reset();
}
```

## REST API Best Practices

### Pagination

#### Make Pagination Explicit

Always specify pagination parameters explicitly to avoid reliance on defaults:

```java
given()
    .header("Authorization", "Bearer " + globalAdminToken)
    .queryParam("page", 0)
    .queryParam("size", 100)
    .queryParam("sort", "id,ASC")
    .get("/integrations");
```

**Benefits:**
- Removes reliance on API defaults
- Stable ordering improves reproducibility
- Better resilience under data accumulation

#### Validate Pagination Contract

Always validate pagination metadata to ensure the API contract is correct:

```java
Response response = given()
    .header("Authorization", "Bearer " + globalAdminToken)
    .queryParam("page", 0)
    .queryParam("size", 100)
    .queryParam("sort", "id,ASC")
    .get("/integrations")
    .then()
    .extract()
    .response();

// Validate pagination metadata
assertThat(response.jsonPath().getInt("totalElements")).isGreaterThanOrEqualTo(2);
assertThat(response.jsonPath().getInt("totalPages")).isGreaterThanOrEqualTo(1);
assertThat(response.jsonPath().getInt("size")).isEqualTo(100);
assertThat(response.jsonPath().getInt("number")).isEqualTo(0);
```

**Benefits:**
- Ensures API pagination contract remains correct under load
- Catches regressions earlier
- Validates response structure consistency

### Filtering

#### Use Filters to Scope to Test Data

When possible, use API filters to scope queries to test-specific data:

```java
String uniqueSuffix = String.valueOf(System.currentTimeMillis());

given()
    .header("Authorization", "Bearer " + globalAdminToken)
    .queryParam("integrationName", uniqueSuffix)
    .queryParam("size", 100)
    .get("/integrations");
```

**Benefits:**
- Improved independence without cleanup
- Faster and more deterministic assertions
- Reduces dependency on global database state

**Available Filters:**
- `integrationName` (partial match, case-insensitive)
- `active` (boolean)
- `createdAfter` / `createdBefore` (date filters)

### Response Structure Validation

Validate the complete response structure, not just presence of expected data:

```java
Response response = given()
    .header("Authorization", "Bearer " + adminToken)
    .get("/integrations")
    .then()
    .extract()
    .response();

// Validate pagination structure
assertThat(response.jsonPath().getInt("totalElements")).isGreaterThanOrEqualTo(0);
assertThat(response.jsonPath().getInt("totalPages")).isGreaterThanOrEqualTo(0);
assertThat(response.jsonPath().getList("content")).isNotNull();

// Validate content coherence
List<Map<String, Object>> integrations = response.jsonPath().getList("content");
assertThat(integrations.size()).isLessThanOrEqualTo(response.jsonPath().getInt("size"));
```

## Data Accumulation Strategy

### Intentional Feature, Not a Bug

The multi-tenant test strategy is **intentional**: create new data as needed **without cleanup** to simulate a production-like environment. This is a **feature, not a bug**, and it improves the overall value of the functional test suite.

**Benefits:**
- Tests are more **production-like**
- Better signal on **scalability** issues (pagination, queries)
- Better signal on **performance** as data grows
- Simpler test flows (less cleanup orchestration)

### How to Keep Tests Independent Under Accumulation

Independence is preserved via:
- **Unique identifiers** for test-created entities (timestamps/UUIDs)
- **Filters** that scope to test-run data
- **Explicit pagination** to avoid "first page only" assumptions
- Avoiding reliance on ordering and pre-existing data

**Example:**

```java
@Test
@DisplayName("GlobalAdmin can list all integrations")
void testGlobalAdminCanListAllIntegrations() {
    String uniqueSuffix = String.valueOf(System.currentTimeMillis());
    
    // Create test data with unique suffix
    Integer integrationAId = testDataFactory.createIntegrationForTenant(
        "Integration A " + uniqueSuffix, tenantAId, globalAdminToken);
    Integer integrationBId = testDataFactory.createIntegrationForTenant(
        "Integration B " + uniqueSuffix, tenantBId, globalAdminToken);
    
    // Use filter to scope to test data
    Response response = given()
        .header("Authorization", "Bearer " + globalAdminToken)
        .queryParam("integrationName", uniqueSuffix)
        .queryParam("page", 0)
        .queryParam("size", 100)
        .queryParam("sort", "id,ASC")
        .get("/integrations")
        .then()
        .statusCode(200)
        .extract()
        .response();
    
    // Validate pagination contract
    assertThat(response.jsonPath().getInt("totalElements")).isGreaterThanOrEqualTo(2);
    assertThat(response.jsonPath().getInt("totalPages")).isGreaterThanOrEqualTo(1);
    
    // Validate test data is present
    List<Integer> integrationIds = response.jsonPath().getList("content.id");
    assertThat(integrationIds).contains(integrationAId, integrationBId);
}
```

## Using DatabaseHelper

### When to Use DatabaseHelper

`DatabaseHelper` is a utility for direct database access in tests. Use it for:

**Good Uses:**
- Fast existence/status checks
- Safe recovery from partially-completed bootstrap
- Optional cleanup when a test *must* reset state for correctness
- Diagnostics when API responses are ambiguous
- Cross-validation of API vs DB state

**Bad Uses:**
- Replacing API assertions with DB checks for the core behavior being validated
- Bypassing the API layer to test business logic

**Principle:** The API should remain the primary validation surface; DB access is a supporting tool.

### Examples

#### Pre-test State Checks

```java
@Test
public void testIntegrationCreation() {
    // Check initial state
    int initialCount = databaseHelper.countIntegrations();
    
    // Create integration via API
    Integer integrationId = testDataFactory.createIntegration("Test Integration", tenantId, adminToken);
    
    // Verify via API (primary validation)
    Response response = given()
        .header("Authorization", "Bearer " + adminToken)
        .get("/integrations/" + integrationId)
        .then()
        .statusCode(200)
        .extract()
        .response();
    
    // Optional: Cross-validate with DB for diagnostics
    assertThat(databaseHelper.countIntegrations()).isEqualTo(initialCount + 1);
}
```

#### Diagnostics

```java
@Test
public void testIntegrationList() {
    // Create test data
    Integer integrationId = testDataFactory.createIntegration("Test Integration", tenantId, adminToken);
    
    // Test via API
    Response response = given()
        .header("Authorization", "Bearer " + adminToken)
        .get("/integrations")
        .then()
        .extract()
        .response();
    
    // If test fails, use DB for diagnostics
    if (response.getStatusCode() != 200) {
        // Check if integration exists in DB
        boolean exists = databaseHelper.integrationExists(integrationId);
        logger.debug("Integration exists in DB: {}", exists);
    }
    
    assertThat(response.getStatusCode()).isEqualTo(200);
}
```

## Multi-Tenant Testing

### Test Structure

Multi-tenant tests should verify:

1. **Cross-tenant isolation**: TenantAdmin A cannot access Tenant B resources
2. **Boundary permissions**: TenantAdmin cannot perform GlobalAdmin operations
3. **Scope filtering**: Listings only return resources within admin's scope

### Template for Cross-Tenant Isolation Tests

```java
@Tag(TestTags.MULTI_TENANT)
@Tag(TestTags.SECURITY)
@DisplayName("Cross-Tenant Isolation Tests")
public class MyIsolationTest extends AbstractSecurityTest {

  private TenantAdminTestHelper tenantAdminTestHelper;
  private String globalAdminToken;
  private Integer tenantAId;
  private Integer tenantBId;
  private String tenantAdminAToken;
  private String tenantAdminBToken;

  @Override
  @BeforeEach
  public void setUp() {
    super.setUp();

    try {
      globalAdminToken = authTokenManager.getAdminToken();

      tenantAdminTestHelper = new TenantAdminTestHelper(
        dockerStackConfig,
        testDataFactory,
        cryptoApiClient
      );

      String suffix = String.valueOf(System.currentTimeMillis());
      tenantAId = testDataFactory.findOrCreateTenant("Tenant A " + suffix, globalAdminToken);
      tenantBId = testDataFactory.findOrCreateTenant("Tenant B " + suffix, globalAdminToken);

      tenantAdminAToken = tenantAdminTestHelper.createAndLoginTenantAdmin(
        "admin-a-" + suffix,
        tenantAId,
        globalAdminToken
      );

      tenantAdminBToken = tenantAdminTestHelper.createAndLoginTenantAdmin(
        "admin-b-" + suffix,
        tenantBId,
        globalAdminToken
      );
    } catch (IllegalStateException e) {
      org.junit.jupiter.api.Assumptions.assumeTrue(
        false,
        "Test setup failed: " + e.getMessage()
      );
    }
  }

  @Test
  @DisplayName("TenantAdmin A cannot access Tenant B resources (403/404)")
  public void testCrossTenantIsolation() {
    configureForAdminApi(dockerStackConfig);

    // Create resource in Tenant B
    Integer integrationB = testDataFactory.createIntegrationForTenant(
      "Integration B",
      tenantBId,
      globalAdminToken
    );

    // Try to access with Tenant A token
    Response response = given()
      .contentType(ContentType.JSON)
      .header("Authorization", "Bearer " + tenantAdminAToken)
      .when()
      .get("/integrations/" + integrationB)
      .then()
      .extract()
      .response();

    assertThat(response.getStatusCode()).isIn(403, 404);
  }
}
```

### Template for Boundary Permissions Tests

```java
@Tag(TestTags.MULTI_TENANT)
@Tag(TestTags.SECURITY)
@DisplayName("Tenant Boundary Permissions Tests")
public class MyBoundaryTest extends AbstractSecurityTest {

  private TenantAdminTestHelper tenantAdminTestHelper;
  private String globalAdminToken;
  private Integer tenantId;
  private String tenantAdminToken;

  @Override
  @BeforeEach
  public void setUp() {
    super.setUp();

    try {
      globalAdminToken = authTokenManager.getAdminToken();

      tenantAdminTestHelper = new TenantAdminTestHelper(
        dockerStackConfig,
        testDataFactory,
        cryptoApiClient
      );

      String suffix = String.valueOf(System.currentTimeMillis());
      tenantId = testDataFactory.findOrCreateTenant("Tenant " + suffix, globalAdminToken);

      tenantAdminToken = tenantAdminTestHelper.createAndLoginTenantAdmin(
        "tenant.admin." + suffix,
        tenantId,
        globalAdminToken
      );
    } catch (IllegalStateException e) {
      org.junit.jupiter.api.Assumptions.assumeTrue(
        false,
        "Test setup failed: " + e.getMessage()
      );
    }
  }

  @Test
  @DisplayName("TenantAdmin cannot create tenant (403)")
  public void testTenantAdminCannotCreateTenant() {
    configureForAdminApi(dockerStackConfig);

    Map<String, Object> request = new HashMap<>();
    request.put("tenantName", "Malicious Tenant");
    request.put("tenantDescription", "Should not be created");

    Response response = given()
      .contentType(ContentType.JSON)
      .header("Authorization", "Bearer " + tenantAdminToken)
      .body(request)
      .when()
      .post("/tenants")
      .then()
      .extract()
      .response();

    assertThat(response.getStatusCode()).isEqualTo(403);
  }
}
```

## Best Practices

### 1. Always Use Try-Catch for Token Acquisition

```java
try {
  String token = authTokenManager.getAdminToken();
  // Test logic
} catch (IllegalStateException e) {
  org.junit.jupiter.api.Assumptions.assumeTrue(false, "Token not available");
}
```

### 2. Use Unique Suffixes for Test Data

```java
String suffix = String.valueOf(System.currentTimeMillis());
String name = "Test Entity " + suffix;
```

### 3. Prefer findOrCreate Methods

```java
// Good - reuses existing entities
Integer tenantId = testDataFactory.findOrCreateTenant("My Tenant", token);

// Avoid - always creates new entity
Integer tenantId = testDataFactory.createTenant("My Tenant", token);
```

### 4. Test Both Success and Failure Cases

```java
// Success: Admin can access own resources
@Test
public void testAdminCanAccessOwnResources() { /* ... */ }

// Failure: Admin cannot access others' resources
@Test
public void testAdminCannotAccessOthersResources() { /* ... */ }
```

### 5. Use Descriptive Test Names

```java
@DisplayName("TenantAdmin A cannot get Tenant B integration by ID (403)")
public void testTenantAdminACannotGetTenantBIntegration() { /* ... */ }
```

### 6. Verify Isolation Bidirectionally

```java
@Test
public void testBidirectionalIsolation() {
  // A cannot access B
  assertCannotAccess(tenantAdminAToken, resourceB);

  // B cannot access A
  assertCannotAccess(tenantAdminBToken, resourceA);
}
```

### 7. Make Pagination Explicit

Always specify `page`, `size`, and `sort` parameters explicitly:

```java
given()
    .queryParam("page", 0)
    .queryParam("size", 100)
    .queryParam("sort", "id,ASC")
    .get("/integrations");
```

### 8. Validate Pagination Metadata

Always validate pagination contract:

```java
assertThat(response.jsonPath().getInt("totalElements")).isGreaterThanOrEqualTo(expectedCount);
assertThat(response.jsonPath().getInt("totalPages")).isGreaterThanOrEqualTo(1);
assertThat(response.jsonPath().getInt("size")).isEqualTo(100);
```

### 9. Use Filters When Available

Scope queries to test data using filters:

```java
given()
    .queryParam("integrationName", uniqueSuffix)
    .get("/integrations");
```

## Cache Files

The test framework uses several cache files in `.ezkey-test/`:

- `bootstrap-credentials.json`: Bootstrap credentials from Docker logs (one-time)
- `device-credentials.json`: GlobalAdmin device keys (reused across runs)
- `admin-token.json`: GlobalAdmin bearer token (validated before reuse)
- `tenant-admin-{tenantId}-device-credentials.json`: TenantAdmin device keys per tenant
- `tenant-admin-{tenantId}-token.json`: TenantAdmin bearer token per tenant

These files enable efficient test execution by avoiding repetitive bootstrap and authentication flows.

## Docker bootstrap-init and demo-device volume / permissions pitfalls

Functional tests interact with the `demo-device` container data volume (notably enrollments stored under `data/enrollments` inside the container, which resolves to `/app/data/enrollments`).

### Key facts

- **Same Docker volume, different mount paths still share the same files**: if `demo-device-data` is mounted as `/app/data` in one container and as `/demo-device-data` in another, they still see the same underlying volume content; only the *container path* differs.
- **The real failure mode is usually permissions**:
  - `bootstrap-init` often writes as `root`.
  - `demo-device` runs as `spring:spring` and expects writable directories under `/app/data`.
  - Test utilities that write files via `docker exec` as `spring:spring` can fail with **"Permission denied"** if the directory (or existing files) were created by `root` without compatible permissions.

### Practical guidance (to keep tests idempotent)

- Prefer a **single canonical mount point** across containers for the shared volume (typically `/app/data`) to avoid confusion and reduce accidental path drift.
- When a test utility writes to the shared volume, it should be **idempotent**:
  - if the target file already exists (created by bootstrap-init), skip writing or overwrite safely with correct ownership/permissions (depending on the intent of the test).
- If you are diagnosing a failure, check both:
  - **path consistency** (are we writing/reading the same logical location?), and
  - **ownership/permissions** on the volume directories and files.

### Current stack behavior (implementation note)

- `bootstrap-init` mounts the shared demo-device volume at `/app/data` and writes enrollment files under `/app/data/enrollments`.
- Some tests may also write enrollment files; the test utilities should tolerate the file already existing (bootstrap-init may have created it).
- If you have old Docker volumes from before path/permission fixes, prefer a clean start (`docker compose down -v` / `docker-compose down -v`) to avoid confusing mixed states.

For the full step-by-step bootstrap/token flow and the RestAssured reconfiguration points, see `reference/BOOTSTRAP_FLOW.md`.

## Troubleshooting

### Token Not Available

**Error**: `Admin token not available`

**Solution**: Set environment variable:
```bash
export EZKEY_ADMIN_TOKEN=your-token-here
```

Or run the bootstrap service to generate tokens automatically.

### Token Invalid After Logout

**Problem**: Cached token returns 401 after logout test

**Solution**: The framework automatically detects invalid tokens (Tier 1 validation) and regenerates them using device credentials (Tier 2) or full bootstrap (Tier 3).

### Device Simulation Fails

**Error**: Failed to create TenantAdmin token

**Solution**: Verify that:
1. Docker stack is healthy (`dockerStackConfig.verifyServicesHealthy()`)
2. Crypto API is accessible
3. Auth API is accessible
4. Admin API is accessible

Check container logs for errors.

### Pagination Issues

**Problem**: Test fails intermittently, especially after multiple runs

**Solution**:
1. Make pagination explicit (specify `page`, `size`, `sort`)
2. Use filters to scope to test data
3. Validate pagination metadata
4. Use unique suffixes for test data

### Data Accumulation Issues

**Problem**: Test fails because expected data is not on first page

**Solution**:
1. Use filters to scope queries to test data
2. Increase `size` parameter to ensure all test data is visible
3. Use stable sort order (e.g., `id,ASC` instead of `createdAt,DESC`)
4. Consider using `DatabaseHelper` for diagnostics

## Examples

See existing test files for complete examples:

- `EnrollmentManagementSecurityTest.java`: CRUD security tests with GlobalAdmin
- `TenantCrossIsolationSecurityTest.java`: Cross-tenant isolation tests
- `TenantBoundaryPermissionsSecurityTest.java`: Boundary permissions tests
- `AdminAuthenticationSecurityTest.java`: Authentication and token lifecycle tests
