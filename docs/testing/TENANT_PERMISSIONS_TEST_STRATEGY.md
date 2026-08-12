# Tenant Permissions Testing Strategy

## Table of Contents

1. [Executive Summary](#executive-summary)
2. [Current State Assessment](#current-state-assessment)
3. [Conceptual Framework](#conceptual-framework)
4. [Validation Matrices](#validation-matrices)
5. [Test Organization & Naming](#test-organization--naming)
6. [Test Prioritization](#test-prioritization)
7. [Development Phases](#development-phases)
8. [Controller-by-Controller Test Plan](#controller-by-controller-test-plan)
9. [API Key Validation Considerations](#api-key-validation-considerations)
10. [Implementation Guidelines](#implementation-guidelines)

---

## Executive Summary

This document defines a comprehensive test strategy for validating tenant isolation and permissions in Ezkey. The strategy focuses on **automating existing manual test scenarios** and establishing a **regression test suite** to prevent future issues.

**Context:**
- Multi-tenancy development is **fully implemented** and has been tested through **manual exploratory testing**
- Several issues have already been identified, analyzed, and fixed
- The goal is to **automate these validations** to prevent regression and ensure fixes remain valid

**Key Principles:**
- **Tenant Isolation is Critical**: The most important security requirement - Tenant A must NEVER access Tenant B's data
- **Strategic Testing**: Focus on high-impact scenarios, not exhaustive edge cases
- **Automation for Regression**: Automate existing validations to catch regressions automatically
- **Validation of Existing Fixes**: Ensure previously fixed issues remain resolved without needing to specify which ones
- **Formal Organization**: Structured, maintainable test suite with clear naming and hierarchy

**Risk-Based Prioritization:**
- **Critical (P0)**: Tenant isolation violations (cross-tenant data access) - **Automate existing validations**
- **High (P1)**: Permission boundary validation (admin type restrictions) - **Automate existing validations**
- **Medium (P2)**: API key tenant scope validation - **Automate existing validations + edge cases if needed**
- **Low (P3)**: Non-critical operations (deactivation, simple validations) - **Manual testing acceptable**

**Testing Approach:**
- **Not TDD**: Multi-tenancy is already implemented and manually tested
- **Automation Focus**: Convert manual test scenarios into automated regression tests
- **Regression Prevention**: Catch regressions automatically without manual re-testing
- **Existing Fixes Validation**: Ensure previously fixed issues remain resolved
- **Edge Cases**: Cover additional edge cases based on priorities when necessary

---

## Current State Assessment

### Existing Test Infrastructure

**Test Module**: `ezkey-tests`
- Location: `ezkey-tests/src/test/java/org/ezkey/tests/`
- Base class: `AbstractSecurityTest.java` - All security tests extend this
- Utilities:
  - `TestDataFactory.java` - Creates test entities via REST API (needs extension for tenants)
  - `AuthTokenManager.java` - Manages admin tokens and API keys
  - `RestAssuredTestConfig.java` - Configures RestAssured for Admin/Auth APIs
  - `DockerStackConfig.java` - Docker stack health checks
  - `CryptoApiClient.java` - **Opportunistic**: Crypto API client for cryptographic operations (key generation, signing, token generation) - not part of functional domain but used to facilitate tests
  - `DatabaseHelper.java` - **Opportunistic**: Direct database access via `docker exec psql` for white-box testing (state verification, cleanup, detailed validation)
- Tags: `TestTags.java` - Test categorization constants
- Test organization: Tests organized by domain under `security/` (e.g., `security/integration/`, `security/apikey/`)

**Test Execution Model:**
- ✅ **Docker stack is pre-launched**: Tests assume Docker stack is already running
- ✅ **Health checks only**: `DockerStackConfig.verifyServicesHealthy()` verifies services are accessible (does NOT start Docker)
- ✅ **Independent tests**: Each test creates its own test data via REST API calls
- ✅ **Idempotent tests**: Tests can be executed multiple times safely by creating **unique data each time**
- ✅ **Unique data creation strategy**: Use unique identifiers (timestamps, UUIDs) for test data names/IDs to ensure idempotence
- ✅ **No cleanup required**: Tests don't clean up data - idempotence achieved through unique data creation, not deletion

**Implications for Tenant Tests:**
- Tests will create tenants, admins, integrations, etc. via REST API in `@BeforeEach` or test methods
- No need to manage Docker lifecycle in tests
- **Idempotence Strategy**: Create unique data each time (using timestamps, UUIDs, or unique suffixes) - **NOT through cleanup/deletion**
- **Independence Strategy**: Each test creates its own unique test data to avoid conflicts with other tests
- **No cleanup focus**: The goal is idempotence and independence through unique data creation, not data cleanup

**Opportunistic Testing Approaches:**
- ✅ **Crypto API**: Use `CryptoApiClient` for cryptographic operations (key generation, signing, token generation) when needed for test setup or validation, even though Crypto API is not part of the functional domain
- ✅ **Direct Database Access**: Use `DatabaseHelper` for white-box testing when beneficial:
  - **State Verification**: Verify tenant isolation directly in database (faster than API calls)
  - **Detailed Validation**: Check tenant_id assignments, foreign key relationships, constraint violations
  - **Cleanup Operations**: Reset state for idempotence or cleanup test data
  - **Finding Entities**: Query database to find existing entities before creating new ones
  - **Cross-Tenant Validation**: Verify that tenant_id constraints are properly enforced at database level

**Current Test Coverage:**
- ✅ Admin authentication security tests (`security/admin/`)
- ✅ API key security tests (`security/apikey/`)
- ✅ Enrollment flow security tests (`security/enrollment/`)
- ✅ Integration management security tests (`security/integration/`)
- ✅ Bootstrap and initialization tests (`security/bootstrap/`)
- ✅ Authentication flow tests (`security/authentication/`)
- ✅ Cryptographic security tests (`security/crypto/`)
- ✅ Rate limiting tests (`security/ratelimit/`)

**Gap Analysis:**
- ❌ **No automated tenant isolation tests** - Manual testing done, but no automated regression suite
- ❌ **No automated cross-tenant access validation tests** - Critical security gap in automation
- ❌ **No automated tenant admin permission boundary tests** - Permission boundaries manually tested but not automated
- ❌ **Limited automated API key tenant scope validation** - API key tests don't validate tenant scope automatically
- ❌ **No systematic controller-by-controller automated tenant permission tests** - Need comprehensive automated coverage
- ⚠️ **TestDataFactory lacks tenant methods** - Need to extend for tenant creation
- ✅ **Manual testing completed** - Issues identified and fixed through exploratory testing

**Test Patterns Observed:**
- All tests extend `AbstractSecurityTest`
- Use `@Tag(TestTags.FAST)` or `@Tag(TestTags.SLOW)` for speed categorization
- Use `@Tag(TestTags.{FEATURE})` for feature categorization (INTEGRATION, API_KEY, etc.)
- Use `@DisplayName` for descriptive test names
- Use `@TestMethodOrder(MethodOrderer.OrderAnnotation.class)` with `@Order()` for execution order
- Use try-catch with `Assumptions.assumeTrue()` for token availability checks
- Use `RestAssuredTestConfig.configureForAdminApi()` before API calls
- Use AssertJ assertions (`assertThat().isEqualTo()`, `isIn()`, etc.)
- Store test data as IDs (not entity objects) since we interact via REST API

### Multi-Tenant Architecture

**Tenant Types:**
1. **System Tenant** (tenant_id = 1): Hosts global admins and system integrations
2. **Application Tenants**: Organizations/departments created by GlobalAdmins

**Administrator Types:**
1. **GlobalAdmin**: System-wide access, can create tenants and other GlobalAdmins
2. **TenantAdmin**: Restricted to their tenant's data and operations
3. **IntegrationAdmin**: Not yet implemented (future scope)

**Access Control Model:**
- `AccessControlService` validates permissions using `@PreAuthorize` annotations
- Validation checks tenant_id matching for TenantAdmins
- API keys are scoped to integrations (which belong to tenants)

---

## Conceptual Framework

### Security Boundaries

```
┌─────────────────────────────────────────────────────────┐
│                    GlobalAdmin                          │
│            (Access: All Tenants)                        │
└─────────────────────────────────────────────────────────┘
                            │
        ┌───────────────────┴───────────────────┐
        │                                       │
┌───────▼────────┐                    ┌────────▼────────┐
│   Tenant A     │                    │   Tenant B      │
│  TenantAdmin   │                    │  TenantAdmin    │
│ (Access: A)    │                    │  (Access: B)    │
└────────────────┘                    └─────────────────┘
        │                                       │
        │                                       │
┌───────▼────────┐                    ┌────────▼────────┐
│ Integration A1 │                    │ Integration B1  │
│ Integration A2 │                    │ Integration B2  │
└────────────────┘                    └─────────────────┘
        │                                       │
        │                                       │
┌───────▼────────┐                    ┌────────▼────────┐
│  API Key A1    │                    │  API Key B1     │
│  (Scope: A1)   │                    │  (Scope: B1)    │
└────────────────┘                    └─────────────────┘
```

### Test Scenarios Matrix

Each test scenario must validate:

1. **Authorization Check**: Does the requester have permission?
2. **Tenant Isolation**: Can Tenant A access Tenant B's resources?
3. **Scope Validation**: Are API keys scoped correctly to their integration's tenant?
4. **Permission Boundary**: Can TenantAdmin perform GlobalAdmin operations?

---

## Validation Matrices

### Matrix 1: Controller Endpoint × Admin Type × Tenant Scope

| Controller | Endpoint | GlobalAdmin | TenantAdmin (Own Tenant) | TenantAdmin (Other Tenant) | API Key (Own Integration) | API Key (Other Integration) |
|------------|----------|-------------|--------------------------|----------------------------|---------------------------|----------------------------|
| **TenantController** |
| | `POST /tenants` | ✅ Create | ❌ 403 | ❌ 403 | ❌ 401 | ❌ 401 |
| | `GET /tenants` | ✅ All tenants | ✅ Own tenant only | ❌ 403 | ❌ 401 | ❌ 401 |
| | `GET /tenants/{id}` | ✅ Any tenant | ✅ Own tenant only | ❌ 403/404 | ❌ 401 | ❌ 401 |
| | `PUT /tenants/{id}` | ✅ Any tenant | ⚠️ Own tenant only | ❌ 403 | ❌ 401 | ❌ 401 |
| **AdminProvisioningController** |
| | `POST /admins/global` | ✅ Create | ❌ 403 | ❌ 403 | ❌ 401 | ❌ 401 |
| | `POST /admins/tenant` | ✅ Any tenant | ✅ Own tenant only | ❌ 403 | ❌ 401 | ❌ 401 |
| | `GET /admins` | ✅ All admins | ✅ Own tenant admins | ❌ 403 | ❌ 401 | ❌ 401 |
| | `GET /admins/{id}` | ✅ Any admin | ✅ Own tenant admin | ❌ 403/404 | ❌ 401 | ❌ 401 |
| | `GET /admins/{id}/onboarding` | ✅ Any admin | ✅ Own tenant admin | ❌ 403 | ❌ 401 | ❌ 401 |
| **IntegrationController** |
| | `POST /integrations` | ✅ Any tenant | ✅ Own tenant only | ❌ 403 | ❌ 401 | ❌ 401 |
| | `GET /integrations` | ✅ All integrations | ✅ Own tenant integrations | ❌ 403 | ❌ 401 | ❌ 401 |
| | `GET /integrations/{id}` | ✅ Any integration | ✅ Own tenant integration | ❌ 403/404 | ❌ 401 | ✅ Own integration only | ❌ 403/404 |
| | `PUT /integrations/{id}` | ✅ Any integration | ✅ Own tenant integration | ❌ 403 | ❌ 401 | ❌ 401 | ❌ 403 |
| | `DELETE /integrations/{id}` | ✅ Any integration | ✅ Own tenant integration | ❌ 403 | ❌ 401 | ❌ 401 | ❌ 403 |
| **EnrollmentController** |
| | `POST /enrollments` | ✅ Any integration | ✅ Own tenant integration | ❌ 403 | ❌ 401 | ❌ 401 |
| | `GET /enrollments` | ✅ All enrollments | ✅ Own tenant enrollments | ❌ 403 | ❌ 401 | ❌ 401 |
| | `GET /enrollments/{id}` | ✅ Any enrollment | ✅ Own tenant enrollment | ❌ 403/404 | ❌ 401 | ❌ 401 | ❌ 403/404 |
| | `DELETE /enrollments/{id}` | ✅ Any enrollment | ✅ Own tenant enrollment | ❌ 403 | ❌ 401 | ❌ 401 | ❌ 403 |
| **AuthAttemptController** |
| | `POST /auth-attempts` | ✅ Any enrollment | ✅ Own tenant enrollment | ❌ 403 | ❌ 401 | ✅ Own integration enrollment | ❌ 403 |
| | `GET /auth-attempts` | ✅ All attempts | ✅ Own tenant attempts | ❌ 403 | ❌ 401 | ✅ Own integration attempts | ❌ 403 |
| | `GET /auth-attempts/{id}` | ✅ Any attempt | ✅ Own tenant attempt | ❌ 403/404 | ❌ 401 | ✅ Own integration attempt | ❌ 403/404 |
| | `GET /auth-attempts/{id}/wait` | ✅ Any attempt | ✅ Own tenant attempt | ❌ 403 | ❌ 401 | ✅ Own integration attempt | ❌ 403 |
| **ApiKeyController** |
| | `POST /api-keys` | ✅ Any integration | ✅ Own tenant integration | ❌ 403 | ❌ 401 | ❌ 401 | ❌ 403 |
| | `GET /api-keys/integration/{id}` | ✅ Any integration | ✅ Own tenant integration | ❌ 403/404 | ❌ 401 | ✅ Own integration only | ❌ 403/404 |
| | `GET /api-keys/{id}` | ✅ Any API key | ✅ Own tenant API key | ❌ 403/404 | ❌ 401 | ✅ Own integration API key | ❌ 403/404 |
| | `DELETE /api-keys/{id}` | ✅ Any API key | ✅ Own tenant API key | ❌ 403 | ❌ 401 | ❌ 401 | ❌ 403 |

**Legend:**
- ✅ = Allowed (200/201)
- ❌ 403 = Forbidden (access denied)
- ❌ 404 = Not Found (resource not accessible, may return 404 instead of 403)
- ❌ 401 = Unauthorized (authentication required, admin-only endpoints)
- ⚠️ = Special case (may be restricted or have limitations)

### Matrix 2: Resource Ownership × Access Pattern

| Resource Type | Owner Tenant | Accessor | Expected Result | Critical Test Case |
|---------------|--------------|----------|-----------------|-------------------|
| **Tenant** | System | GlobalAdmin | ✅ Access | Baseline |
| | Tenant A | TenantAdmin A | ✅ Access | Positive case |
| | Tenant A | TenantAdmin B | ❌ Deny | **CRITICAL: Cross-tenant isolation** |
| | Tenant A | API Key (A's integration) | ❌ Deny | Admin-only endpoint |
| **Integration** | Tenant A | GlobalAdmin | ✅ Access | Baseline |
| | Tenant A | TenantAdmin A | ✅ Access | Positive case |
| | Tenant A | TenantAdmin B | ❌ Deny | **CRITICAL: Cross-tenant isolation** |
| | Tenant A | API Key (A's integration) | ✅ Access (own only) | API key scope |
| | Tenant A | API Key (B's integration) | ❌ Deny | **CRITICAL: Cross-tenant API key** |
| **Enrollment** | Tenant A (via Integration) | GlobalAdmin | ✅ Access | Baseline |
| | Tenant A | TenantAdmin A | ✅ Access | Positive case |
| | Tenant A | TenantAdmin B | ❌ Deny | **CRITICAL: Cross-tenant isolation** |
| | Tenant A | API Key (A's integration) | ❌ Deny | Enrollment admin-only |
| **Auth Attempt** | Tenant A (via Enrollment) | GlobalAdmin | ✅ Access | Baseline |
| | Tenant A | TenantAdmin A | ✅ Access | Positive case |
| | Tenant A | TenantAdmin B | ❌ Deny | **CRITICAL: Cross-tenant isolation** |
| | Tenant A | API Key (A's integration) | ✅ Access | API key allowed |
| | Tenant A | API Key (B's integration) | ❌ Deny | **CRITICAL: Cross-tenant API key** |
| **API Key** | Tenant A (via Integration) | GlobalAdmin | ✅ Access | Baseline |
| | Tenant A | TenantAdmin A | ✅ Access | Positive case |
| | Tenant A | TenantAdmin B | ❌ Deny | **CRITICAL: Cross-tenant isolation** |
| | Tenant A | API Key (A's integration) | ✅ Access (own only) | API key can view own keys |
| | Tenant A | API Key (B's integration) | ❌ Deny | **CRITICAL: Cross-tenant API key** |

### Matrix 3: API Key Tenant Scope Validation

**Critical Scenario**: API keys must be validated against their integration's tenant, not just the integration ID.

| API Key Integration | API Key Tenant | Target Resource | Target Tenant | Expected Result | Test Priority |
|---------------------|----------------|-----------------|---------------|-----------------|---------------|
| Integration A1 | Tenant A | Auth Attempt (Enrollment A1) | Tenant A | ✅ Access | P0 - Baseline |
| Integration A1 | Tenant A | Auth Attempt (Enrollment A2) | Tenant A | ✅ Access (same tenant) | P1 - Same tenant |
| Integration A1 | Tenant A | Auth Attempt (Enrollment B1) | Tenant B | ❌ Deny | **P0 - CRITICAL: Cross-tenant** |
| Integration A1 | Tenant A | Integration A1 (GET) | Tenant A | ✅ Access | P0 - Baseline |
| Integration A1 | Tenant A | Integration B1 (GET) | Tenant B | ❌ Deny | **P0 - CRITICAL: Cross-tenant** |
| Integration A1 | Tenant A | API Key A1 (GET) | Tenant A | ✅ Access | P1 - Own keys |
| Integration A1 | Tenant A | API Key B1 (GET) | Tenant B | ❌ Deny | **P0 - CRITICAL: Cross-tenant** |

**Known Issue**: User suspects API key tenant validation may be incorrect. This matrix helps identify the specific test cases to validate.

---

## Test Organization & Naming

### Package Structure

**Following existing test organization pattern:**

```
ezkey-tests/src/test/java/org/ezkey/tests/
└── security/
    ├── tenant/
    │   ├── TenantIsolationIntegrationTest.java
    │   ├── TenantIsolationEnrollmentTest.java
    │   ├── TenantIsolationAuthAttemptTest.java
    │   ├── TenantIsolationApiKeyTest.java
    │   ├── TenantIsolationAdminTest.java
    │   ├── TenantIsolationTenantTest.java
    │   └── ApiKeyTenantScopeTest.java
    └── ... (existing tests)
```

**Note:** The structure follows the existing pattern where tests are organized by domain under `security/`. All tenant-related tests are grouped in the `tenant/` subdirectory to maintain consistency with existing organization (e.g., `security/integration/`, `security/enrollment/`, `security/apikey/`).

### Test Naming Convention

**Format**: `{entity}_{action}_{actorType}_{scope}_{expectedResult}`

**Components:**
- `{entity}`: Resource being accessed (Tenant, Integration, Enrollment, AuthAttempt, ApiKey, Admin)
- `{action}`: Operation (Create, Read, Update, Delete, List)
- `{actorType}`: Who is performing (GlobalAdmin, TenantAdmin, ApiKey)
- `{scope}`: Scope context (OwnTenant, OtherTenant, OwnIntegration, OtherIntegration)
- `{expectedResult}`: Expected outcome (Success, Denied, NotFound)

**Examples:**
- `integration_read_tenantAdmin_ownTenant_success()`
- `integration_read_tenantAdmin_otherTenant_denied()`
- `authAttempt_create_apiKey_ownIntegration_success()`
- `authAttempt_create_apiKey_otherIntegration_denied()`
- `apiKey_read_apiKey_ownIntegration_success()`
- `apiKey_read_apiKey_otherIntegration_denied()`

**Alternative Shorter Format** (for simple cases):
- `{entity}_{actorType}_canAccess_{scope}()`
- `{entity}_{actorType}_cannotAccess_{scope}()`

**Examples:**
- `integration_tenantAdmin_canAccess_ownTenant()`
- `integration_tenantAdmin_cannotAccess_otherTenant()`
- `authAttempt_apiKey_canAccess_ownIntegration()`
- `authAttempt_apiKey_cannotAccess_otherIntegration()`

### Test Class Structure

**Following existing test patterns from `IntegrationManagementSecurityTest` and `ApiKeySecurityTest`:**

```java
/**
 * Tests tenant isolation for {Resource} operations.
 *
 * <p>Validates that TenantAdmins and API keys can only access resources
 * within their tenant scope, and that GlobalAdmins have full access.
 *
 * <p>Test organization follows existing security test patterns:
 * <ul>
 *   <li>Extends AbstractSecurityTest for common setup
 *   <li>Uses TestDataFactory for test data creation
 *   <li>Uses AuthTokenManager for authentication
 *   <li>Uses RestAssuredTestConfig for API configuration
 * </ul>
 */
@Tag(TestTags.FAST)
@Tag(TestTags.SECURITY)
@DisplayName("Tenant Isolation - {Resource} Tests")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class TenantIsolation{Resource}Test extends AbstractSecurityTest {

  // Test data IDs (stored as IDs since we interact via REST API)
  private Integer tenantAId;
  private Integer tenantBId;
  private String globalAdminToken;
  private String tenantAdminAToken;
  private String tenantAdminBToken;
  private Integer integrationA1Id;
  private Integer integrationB1Id;
  private String apiKeyA1Credentials; // "integrationKey:secretKey"

  @BeforeEach
  void setUp() {
    try {
      // Get global admin token for setup
      globalAdminToken = authTokenManager.getAdminToken();
      
      // Create tenants (via REST API using TestDataFactory)
      // Strategy: Create unique data each time for idempotence (NOT cleanup-based)
      // Using unique suffix ensures tests can run multiple times without conflicts
      String uniqueSuffix = String.valueOf(System.currentTimeMillis());
      tenantAId = testDataFactory.createTenant("Tenant A " + uniqueSuffix, globalAdminToken);
      tenantBId = testDataFactory.createTenant("Tenant B " + uniqueSuffix, globalAdminToken);
      
      // Create tenant admins (via REST API)
      // Each test execution creates new unique admins - no cleanup needed
      tenantAdminAToken = testDataFactory.createTenantAdmin("tenant.admin.a." + uniqueSuffix, tenantAId, globalAdminToken);
      tenantAdminBToken = testDataFactory.createTenantAdmin("tenant.admin.b." + uniqueSuffix, tenantBId, globalAdminToken);
      
      // Create integrations in each tenant
      // Unique names ensure no conflicts with previous test runs
      integrationA1Id = testDataFactory.createIntegrationForTenant("Integration A1 " + uniqueSuffix, tenantAId, tenantAdminAToken);
      integrationB1Id = testDataFactory.createIntegrationForTenant("Integration B1 " + uniqueSuffix, tenantBId, tenantAdminBToken);
      
      // Create API keys
      apiKeyA1Credentials = testDataFactory.createApiKeyForIntegration(integrationA1Id, tenantAdminAToken);
      
      // Note: No cleanup needed - idempotence achieved through unique data creation
    } catch (IllegalStateException e) {
      org.junit.jupiter.api.Assumptions.assumeTrue(
          false, "Admin token not available. Set EZKEY_ADMIN_TOKEN environment variable.");
    }
  }

  // P0: Critical tenant isolation tests
  @Test
  @Order(1)
  @DisplayName("TenantAdmin cannot access other tenant's integration (403/404)")
  void integration_read_tenantAdmin_crossTenant_denied() {
    configureForAdminApi(dockerStackConfig);
    
    // TenantAdmin A attempts to access Integration B1
    Response response =
        given()
            .contentType(ContentType.JSON)
            .header("Authorization", "Bearer " + tenantAdminAToken)
            .when()
            .get("/integrations/" + integrationB1Id)
            .then()
            .extract()
            .response();
    
    // Should be denied (403 Forbidden or 404 Not Found)
    assertThat(response.getStatusCode()).isIn(403, 404);
  }

  // P1: Same-tenant access tests
  @Test
  @Order(2)
  @DisplayName("TenantAdmin can access own tenant's integration (200)")
  void integration_read_tenantAdmin_ownTenant_success() {
    configureForAdminApi(dockerStackConfig);
    
    // TenantAdmin A accesses Integration A1 (own tenant)
    Response response =
        given()
            .contentType(ContentType.JSON)
            .header("Authorization", "Bearer " + tenantAdminAToken)
            .when()
            .get("/integrations/" + integrationA1Id)
            .then()
            .extract()
            .response();
    
    assertThat(response.getStatusCode()).isEqualTo(200);
    assertThat(response.jsonPath().getInt("id")).isEqualTo(integrationA1Id);
  }

  // P0: API key tenant scope tests
  @Test
  @Order(3)
  @DisplayName("API key cannot create auth attempt for other tenant's enrollment (403)")
  void authAttempt_create_apiKey_crossTenant_denied() {
    configureForAdminApi(dockerStackConfig);
    
    // Create enrollment in Tenant B
    Integer enrollmentB1Id = testDataFactory.createEnrollment(integrationB1Id, tenantAdminBToken);
    
    // API Key A1 attempts to create auth attempt for Enrollment B1
    String[] apiKeyParts = apiKeyA1Credentials.split(":");
    Map<String, Object> request = new HashMap<>();
    request.put("enrollmentId", enrollmentB1Id);
    request.put("challengeRequested", false);
    
    Response response =
        given()
            .contentType(ContentType.JSON)
            .header("Authorization", createApiKeyAuthHeader(apiKeyParts[0], apiKeyParts[1]))
            .body(request)
            .when()
            .post("/auth-attempts")
            .then()
            .extract()
            .response();
    
    assertThat(response.getStatusCode()).isEqualTo(403);
  }
  
  // P0: White-box validation - Verify tenant isolation at database level
  @Test
  @Order(4)
  @DisplayName("Database-level validation: Integration belongs to correct tenant")
  void integration_tenantAssignment_databaseValidation() {
    DatabaseHelper databaseHelper = new DatabaseHelper();
    
    // Verify integration A1 belongs to Tenant A
    String sql = String.format(
        "SELECT tenant_id FROM ezkey_integration WHERE integration_id = %d;",
        integrationA1Id);
    String tenantIdStr = databaseHelper.executeQuerySingleValue(sql);
    assertThat(tenantIdStr).isNotNull();
    Integer tenantId = Integer.parseInt(tenantIdStr.trim());
    assertThat(tenantId).isEqualTo(tenantAId);
    
    // Verify integration B1 belongs to Tenant B
    sql = String.format(
        "SELECT tenant_id FROM ezkey_integration WHERE integration_id = %d;",
        integrationB1Id);
    tenantIdStr = databaseHelper.executeQuerySingleValue(sql);
    assertThat(tenantIdStr).isNotNull();
    tenantId = Integer.parseInt(tenantIdStr.trim());
    assertThat(tenantId).isEqualTo(tenantBId);
  }
  
  private String createApiKeyAuthHeader(String integrationKey, String secretKey) {
    String credentials = integrationKey + ":" + secretKey;
    String encoded = Base64.getEncoder().encodeToString(credentials.getBytes());
    return "Basic " + encoded;
  }
}
```

**Key Alignment with Existing Infrastructure:**
- ✅ Uses existing `AbstractSecurityTest` base class (not `@SpringBootTest`)
- ✅ Uses existing `TestDataFactory` pattern (IDs instead of entity objects)
- ✅ Uses existing `AuthTokenManager` for token management
- ✅ Uses existing `RestAssuredTestConfig.configureForAdminApi()` pattern
- ✅ Follows existing test annotation patterns (`@Tag`, `@DisplayName`, `@Order`)
- ✅ Uses existing exception handling pattern with `Assumptions.assumeTrue()`
- ✅ Uses existing RestAssured patterns (`given().contentType().header().when().then().extract()`)
- ✅ Uses existing AssertJ assertion patterns
- ✅ Follows existing package organization (`security/{domain}/`)

**New Requirements:**
- ⚠️ Need to extend `TestDataFactory` with tenant-related methods
- ⚠️ Consider adding `TestTags.TENANT_ISOLATION` tag (optional, can use existing tags)
- ⚠️ May need helper methods for API key authentication header creation (pattern exists in `ApiKeySecurityTest`)

---

## Test Prioritization

### Priority Levels

**P0 - Critical (Must Have)**
- Tenant isolation violations (cross-tenant access)
- API key tenant scope validation
- Permission boundary enforcement (TenantAdmin cannot perform GlobalAdmin operations)

**Rationale**: These are security vulnerabilities that could lead to data breaches or unauthorized access.

**P1 - High (Should Have)**
- Same-tenant access validation (positive cases)
- Admin type permission boundaries
- API key integration scope validation

**Rationale**: Validates correct functionality within allowed boundaries.

**P2 - Medium (Nice to Have)**
- Edge cases (inactive tenants, deleted resources)
- List/filter operations with tenant scoping
- Pagination with tenant filtering

**Rationale**: Important for correctness but less critical than P0/P1.

**P3 - Low (Manual Testing Acceptable)**
- Simple validation errors (bad input format)
- Non-security-related operations (deactivation, metadata updates)
- UI-related validations

**Rationale**: Easy to verify manually, low security impact.

---

## Development Phases

### Phase 1: Foundation & Critical Path (Week 1-2)

**Goal**: Establish test infrastructure and automate critical tenant isolation validations that were previously tested manually.

**Deliverables:**
1. Test infrastructure setup
   - Base test classes for tenant isolation tests
   - Test data factory methods for multi-tenant scenarios (extend `TestDataFactory`)
   - Helper methods for tenant boundary validation

2. Critical tenant isolation tests (P0) - **Automate existing manual validations**
   - IntegrationController: Cross-tenant access denial (automate manual test scenarios)
   - EnrollmentController: Cross-tenant access denial (automate manual test scenarios)
   - AuthAttemptController: Cross-tenant access denial (automate manual test scenarios)
   - ApiKeyController: Cross-tenant access denial (automate manual test scenarios)

3. API key tenant scope validation (P0) - **Automate existing manual validations**
   - API key cannot access other tenant's resources (automate known scenarios)
   - API key can access own integration's resources (automate known scenarios)
   - API key tenant validation logic (automate existing validations)

**Success Criteria:**
- All P0 tenant isolation tests pass (validating existing fixes remain valid)
- Test infrastructure is reusable and maintainable
- Clear documentation of test patterns
- Regression tests catch issues automatically without manual re-testing

### Phase 2: Permission Boundaries (Week 3)

**Goal**: Automate admin type permission boundary validations that were previously tested manually.

**Deliverables:**
1. GlobalAdmin permission tests (P1) - **Automate existing manual validations**
   - Can create tenants (automate known scenario)
   - Can create GlobalAdmins (automate known scenario)
   - Can access all tenants' resources (automate known scenario)

2. TenantAdmin permission tests (P1) - **Automate existing manual validations**
   - Can create TenantAdmins for own tenant (automate known scenario)
   - Cannot create GlobalAdmins (automate known scenario - regression test)
   - Cannot create TenantAdmins for other tenants (automate known scenario - regression test)
   - Can access own tenant's resources only (automate known scenario)

3. AdminProvisioningController tests (P1) - **Automate existing manual validations**
   - TenantAdmin cannot create GlobalAdmin (automate existing fix validation)
   - TenantAdmin can create TenantAdmin for own tenant (automate existing fix validation)
   - TenantAdmin cannot create TenantAdmin for other tenant (automate existing fix validation)

**Success Criteria:**
- All P1 permission boundary tests pass (validating existing fixes remain valid)
- Clear separation between GlobalAdmin and TenantAdmin permissions
- Regression tests prevent permission boundary regressions

### Phase 3: API Key Scope Validation (Week 4)

**Goal**: Automate API key tenant scope validations and cover edge cases if needed.

**Deliverables:**
1. API key integration scope tests (P1) - **Automate existing manual validations**
   - API key can access own integration's auth attempts (automate known scenario)
   - API key cannot access other integrations' auth attempts (same tenant) - **if this was tested manually**
   - API key cannot access other tenants' integrations (automate known scenario - regression test)

2. API key tenant scope edge cases (P2) - **Cover if needed based on priorities**
   - API key with integration moved to different tenant (edge case - add if needed)
   - API key validation with inactive integration (edge case - add if needed)
   - API key validation with deleted integration (edge case - add if needed)

**Success Criteria:**
- API key tenant scope validation is automated (existing validations)
- Edge cases are covered if priorities allow
- Regression tests prevent API key scope violations

### Phase 4: Comprehensive Coverage (Week 5-6)

**Goal**: Complete automated test coverage for all controllers and endpoints, prioritizing existing manual test scenarios.

**Deliverables:**
1. TenantController tests (P1) - **Automate existing manual validations**
   - TenantAdmin cannot list tenants (`GET /api/v1/tenants` → 403; GlobalAdmin-only)
   - TenantAdmin can get own tenant by ID (`GET /api/v1/tenants/{id}`)
   - TenantAdmin cannot get other tenants by ID (403/404)
   - GlobalAdmin can list and view all tenants

2. Remaining controller tests (P1/P2) - **Automate existing manual validations + edge cases if needed**
   - List operations with tenant filtering (automate known scenarios)
   - Update/delete operations with tenant validation (automate known scenarios)
   - Pagination with tenant scoping (automate if tested manually, or add as edge case)

3. Integration tests (P2) - **Add if priorities allow**
   - End-to-end scenarios with multiple tenants (edge cases if needed)
   - Complex permission scenarios (edge cases if needed)
   - Audit trail validation (edge cases if needed)

**Success Criteria:**
- All P1 tests complete (existing validations automated)
- P2 tests where appropriate (edge cases based on priorities)
- Test suite is maintainable and well-documented
- Regression tests prevent tenant isolation and permission violations

---

## Controller-by-Controller Test Plan

### 1. TenantController

**Test Class**: `TenantIsolationTenantTest`

**Critical Tests (P0):**
- ❌ TenantAdmin cannot access other tenant's details
- ❌ TenantAdmin cannot update other tenant
- ❌ TenantAdmin cannot create tenants

**Positive Tests (P1):**
- ✅ TenantAdmin can get own tenant by ID
- ✅ GlobalAdmin can list and view all tenants
- ✅ GlobalAdmin can create tenants
- ✅ GlobalAdmin can update any tenant

**Negative / list (P0–P1):**
- ❌ TenantAdmin list tenants → 403 (`GET /api/v1/tenants` is GlobalAdmin-only per `ENDPOINT.md`)

**Edge Cases (P2):**
- GlobalAdmin list returns all tenants
- Inactive tenant filtering

### 2. AdminProvisioningController

**Test Class**: `TenantIsolationAdminTest`

**Critical Tests (P0):**
- ❌ TenantAdmin cannot create GlobalAdmin
- ❌ TenantAdmin cannot create TenantAdmin for other tenant
- ❌ TenantAdmin cannot access other tenant's admin onboarding credentials

**Positive Tests (P1):**
- ✅ TenantAdmin can create TenantAdmin for own tenant
- ✅ TenantAdmin can list own tenant's admins
- ✅ TenantAdmin can access own tenant's admin onboarding credentials
- ✅ GlobalAdmin can create GlobalAdmin
- ✅ GlobalAdmin can create TenantAdmin for any tenant
- ✅ GlobalAdmin can access any admin's onboarding credentials

**Edge Cases (P2):**
- Admin list filtering by tenant
- Admin list pagination with tenant scope
- Inactive admin filtering

### 3. IntegrationController

**Test Class**: `TenantIsolationIntegrationTest`

**Critical Tests (P0):**
- ❌ TenantAdmin cannot create integration for other tenant
- ❌ TenantAdmin cannot read other tenant's integration
- ❌ TenantAdmin cannot update other tenant's integration
- ❌ TenantAdmin cannot delete other tenant's integration
- ❌ API key cannot access other tenant's integration

**Positive Tests (P1):**
- ✅ TenantAdmin can create integration for own tenant
- ✅ TenantAdmin can read own tenant's integrations
- ✅ TenantAdmin can update own tenant's integration
- ✅ TenantAdmin can delete own tenant's integration
- ✅ API key can read own integration
- ✅ GlobalAdmin can perform all operations on any integration

**Edge Cases (P2):**
- Integration list filtering by tenant
- Integration pagination with tenant scope
- Inactive integration filtering

### 4. EnrollmentController

**Test Class**: `TenantIsolationEnrollmentTest`

**Critical Tests (P0):**
- ❌ TenantAdmin cannot create enrollment for other tenant's integration
- ❌ TenantAdmin cannot read other tenant's enrollment
- ❌ TenantAdmin cannot delete other tenant's enrollment
- ❌ API key cannot access enrollments (admin-only)

**Positive Tests (P1):**
- ✅ TenantAdmin can create enrollment for own tenant's integration
- ✅ TenantAdmin can read own tenant's enrollments
- ✅ TenantAdmin can delete own tenant's enrollment
- ✅ GlobalAdmin can perform all operations on any enrollment

**Edge Cases (P2):**
- Enrollment list filtering by tenant (via integration)
- Enrollment pagination with tenant scope
- Inactive enrollment filtering

### 5. AuthAttemptController

**Test Class**: `TenantIsolationAuthAttemptTest`

**Critical Tests (P0):**
- ❌ TenantAdmin cannot create auth attempt for other tenant's enrollment
- ❌ TenantAdmin cannot read other tenant's auth attempt
- ❌ TenantAdmin cannot wait for other tenant's auth attempt
- ❌ API key cannot create auth attempt for other tenant's enrollment
- ❌ API key cannot read other tenant's auth attempt
- ❌ API key cannot wait for other tenant's auth attempt

**Positive Tests (P1):**
- ✅ TenantAdmin can create auth attempt for own tenant's enrollment
- ✅ TenantAdmin can read own tenant's auth attempts
- ✅ TenantAdmin can wait for own tenant's auth attempt
- ✅ API key can create auth attempt for own integration's enrollment
- ✅ API key can read own integration's auth attempts
- ✅ API key can wait for own integration's auth attempt
- ✅ GlobalAdmin can perform all operations on any auth attempt

**Edge Cases (P2):**
- Auth attempt list filtering by tenant (via enrollment → integration)
- Auth attempt pagination with tenant scope
- Status filtering with tenant scope

### 6. ApiKeyController

**Test Class**: `TenantIsolationApiKeyTest` + `ApiKeyTenantScopeTest`

**Critical Tests (P0):**
- ❌ TenantAdmin cannot create API key for other tenant's integration
- ❌ TenantAdmin cannot read other tenant's API key
- ❌ TenantAdmin cannot delete other tenant's API key
- ❌ API key cannot read other tenant's API key
- ❌ API key cannot delete API keys (admin-only operation)

**Positive Tests (P1):**
- ✅ TenantAdmin can create API key for own tenant's integration
- ✅ TenantAdmin can read own tenant's API keys
- ✅ TenantAdmin can delete own tenant's API key
- ✅ API key can read own integration's API keys
- ✅ GlobalAdmin can perform all operations on any API key

**Edge Cases (P2):**
- API key list filtering by tenant (via integration)
- API key pagination with tenant scope
- API key expiration with tenant validation

---

## API Key Validation Considerations

### Known Issue: API Key Tenant Scope Validation

**User Observation**: "Je crois avoir déjà observé un cas où la validation n'est peut-être pas correcte."

**Potential Issues:**

1. **API Key Access to Integration**
   - Current validation: Checks if API key's integration ID matches requested integration ID
   - **Missing**: Validation that API key's integration belongs to the same tenant as the target resource

2. **API Key Access to Auth Attempt**
   - Current validation: Checks if auth attempt's enrollment belongs to API key's integration
   - **Should also check**: That the integration's tenant matches (redundant but explicit)

3. **API Key Access to API Keys**
   - Current validation: May only check integration ID
   - **Should check**: That API key belongs to same tenant as the target API key's integration

### Test Cases to Validate API Key Tenant Scope

**Test Class**: `ApiKeyTenantScopeTest`

```java
@Test
void apiKey_cannotAccess_otherTenantIntegration() {
  // API Key A1 (Integration A1, Tenant A)
  // Attempts to GET Integration B1 (Tenant B)
  // Expected: 403 Forbidden or 404 Not Found
}

@Test
void apiKey_cannotCreateAuthAttempt_otherTenantEnrollment() {
  // API Key A1 (Integration A1, Tenant A)
  // Attempts to POST auth-attempt for Enrollment B1 (Integration B1, Tenant B)
  // Expected: 403 Forbidden
}

@Test
void apiKey_cannotAccess_otherTenantApiKey() {
  // API Key A1 (Integration A1, Tenant A)
  // Attempts to GET API Key B1 (Integration B1, Tenant B)
  // Expected: 403 Forbidden or 404 Not Found
}

@Test
void apiKey_canAccess_sameTenantDifferentIntegration() {
  // This might be an edge case - should API keys be able to access
  // other integrations in the same tenant?
  // Expected behavior: ❌ Deny (API keys are scoped to their integration only)
}
```

### Validation Logic Review

**Areas to Review:**

1. `AccessControlService.canAccessIntegration()`
   - Current: Checks integration ID match for API keys
   - **Should verify**: Tenant validation is implicit (correct if integration belongs to tenant)

2. `AccessControlService.canAccessAuthAttempt()`
   - Current: Checks enrollment → integration chain
   - **Should verify**: Tenant validation happens at integration level

3. `ApiKeyController` endpoints
   - Review all endpoints for proper tenant validation
   - Ensure API keys cannot access other tenants' API keys

---

## Implementation Guidelines

### Opportunistic Testing Utilities

**CryptoApiClient Usage:**
- Available in `AbstractSecurityTest` as `cryptoApiClient`
- Use for cryptographic operations when needed (key generation, signing, token generation)
- Example: Generate device key pairs for enrollment simulation, sign enrollment proof tokens
- Not part of functional domain but facilitates test setup

**DatabaseHelper Usage:**
- Create instance: `DatabaseHelper databaseHelper = new DatabaseHelper();`
- Use for white-box testing when beneficial:
  - Verify tenant_id assignments directly in database
  - Check foreign key relationships (integration → tenant, admin → tenant)
  - Validate constraint enforcement (unique constraints, foreign keys)
  - Query existing entities before creating new ones (if needed for test setup)
  - **Note**: Cleanup/reset is NOT the primary strategy - use unique data creation instead for idempotence
- Example queries for tenant tests:
  ```java
  // Verify integration belongs to correct tenant
  String sql = "SELECT tenant_id FROM ezkey_integration WHERE integration_id = " + integrationId;
  Integer tenantId = Integer.parseInt(databaseHelper.executeQuerySingleValue(sql));
  assertThat(tenantId).isEqualTo(expectedTenantId);
  
  // Verify admin belongs to correct tenant
  sql = "SELECT tenant_id FROM ezkey_admin WHERE admin_id = " + adminId;
  tenantId = Integer.parseInt(databaseHelper.executeQuerySingleValue(sql));
  assertThat(tenantId).isEqualTo(expectedTenantId);
  
  // Verify API key's integration belongs to correct tenant
  sql = "SELECT i.tenant_id FROM ezkey_integration i " +
        "JOIN ezkey_api_key ak ON i.integration_id = ak.integration_id " +
        "WHERE ak.api_key_id = " + apiKeyId;
  tenantId = Integer.parseInt(databaseHelper.executeQuerySingleValue(sql));
  assertThat(tenantId).isEqualTo(expectedTenantId);
  ```

### Extending TestDataFactory

Before implementing tenant isolation tests, `TestDataFactory` needs to be extended with tenant-related methods. The existing factory focuses on integrations, enrollments, and auth attempts. New methods are needed for:

1. **Tenant creation**: `createTenant(String tenantName, String adminToken)`
2. **Tenant admin creation**: `createTenantAdmin(String username, Integer tenantId, String adminToken)` 
3. **Integration creation with tenant**: `createIntegrationForTenant(String name, Integer tenantId, String adminToken)`
4. **API key creation with tenant awareness**: May reuse existing `createApiKeyForIntegration()` if integration already has tenant

**Implementation Notes:**
- Follow existing patterns: return IDs (not entity objects) since we interact via REST API
- Use `RestAssuredTestConfig.configureForAdminApi()` before API calls
- Use existing `authTokenManager` or accept token as parameter
- Handle exceptions consistently (let them propagate to test methods)
- Use logging pattern: `log.debug("Creating...")` and `log.debug("Created... with ID: {}")`

### Test Data Setup Pattern

**Following existing TestDataFactory patterns:**

The `TestDataFactory` class needs to be extended with tenant-related methods. Current implementation focuses on integrations, enrollments, and auth attempts. We need to add:

```java
// New methods to add to TestDataFactory.java

/**
 * Creates a tenant via Admin API.
 *
 * @param tenantName Tenant name
 * @param adminToken Admin bearer token (must be GlobalAdmin)
 * @return Tenant ID
 */
public Integer createTenant(String tenantName, String adminToken) {
  log.debug("Creating tenant: {}", tenantName);
  
  RestAssuredTestConfig.configureForAdminApi(dockerStackConfig);
  
  Map<String, Object> request = new HashMap<>();
  request.put("tenantName", tenantName);
  request.put("tenantDescription", "Test tenant: " + tenantName);
  
  Response response =
      given()
          .contentType(ContentType.JSON)
          .header("Authorization", "Bearer " + adminToken)
          .body(request)
          .when()
          .post("/tenants")
          .then()
          .statusCode(201)
          .extract()
          .response();
  
  Integer tenantId = response.jsonPath().getInt("tenantId");
  log.debug("Created tenant with ID: {}", tenantId);
  
  return tenantId;
}

/**
 * Creates a tenant admin via Admin API.
 *
 * @param username Admin username
 * @param tenantId Tenant ID
 * @param adminToken Admin bearer token (must be GlobalAdmin or TenantAdmin of same tenant)
 * @return Tenant admin bearer token (obtained via login)
 */
public String createTenantAdmin(String username, Integer tenantId, String adminToken) {
  log.debug("Creating tenant admin: {} for tenant: {}", username, tenantId);
  
  RestAssuredTestConfig.configureForAdminApi(dockerStackConfig);
  
  Map<String, Object> request = new HashMap<>();
  request.put("username", username);
  request.put("email", username + "@example.com");
  request.put("firstName", "Test");
  request.put("lastName", "Admin");
  request.put("tenantId", tenantId);
  
  Response createResponse =
      given()
          .contentType(ContentType.JSON)
          .header("Authorization", "Bearer " + adminToken)
          .body(request)
          .when()
          .post("/admins/tenant")
          .then()
          .statusCode(201)
          .extract()
          .response();
  
  Integer adminId = createResponse.jsonPath().getInt("adminId");
  Integer enrollmentId = createResponse.jsonPath().getInt("enrollmentId");
  
  // Get onboarding credentials
  Response onboardingResponse =
      given()
          .contentType(ContentType.JSON)
          .header("Authorization", "Bearer " + adminToken)
          .when()
          .get("/admins/" + adminId + "/onboarding")
          .then()
          .statusCode(200)
          .extract()
          .response();
  
  String enrollmentProofToken = onboardingResponse.jsonPath().getString("enrollmentProofToken");
  Integer enrollmentChallenge = onboardingResponse.jsonPath().getInt("enrollmentChallenge");
  
  // Complete enrollment binding (simplified - actual implementation may require device simulation)
  // For now, return the enrollment credentials - actual login would require device approval
  // This is a placeholder - full implementation would require enrollment completion
  
  log.debug("Created tenant admin with ID: {}", adminId);
  // Note: Actual token retrieval requires enrollment completion via Auth API
  // For test setup, we may need to use a different approach or mock this
  throw new UnsupportedOperationException("Tenant admin enrollment completion not yet implemented in TestDataFactory");
}

/**
 * Creates an integration for a specific tenant.
 *
 * @param name Integration name
 * @param tenantId Tenant ID
 * @param adminToken Admin bearer token (must have access to tenant)
 * @return Integration ID
 */
public Integer createIntegrationForTenant(String name, Integer tenantId, String adminToken) {
  // Similar to existing createIntegration, but with tenant assignment
  // Implementation depends on API endpoint structure
  // This may require tenantId in request or tenant assignment after creation
}
```

**Note:** These methods need to be implemented in `TestDataFactory` following the existing patterns. The actual implementation will depend on the API endpoints available for tenant and admin management.

### Test Method Pattern

**Following existing patterns from `IntegrationManagementSecurityTest`:**

```java
@Test
@Order(1)
@DisplayName("TenantAdmin cannot access other tenant's integration (403/404)")
void integration_read_tenantAdmin_otherTenant_denied() {
  try {
    configureForAdminApi(dockerStackConfig);
    
    // Act: TenantAdmin A attempts to access Integration B1 (Tenant B)
    Response response =
        given()
            .contentType(ContentType.JSON)
            .header("Authorization", "Bearer " + tenantAdminAToken)
            .when()
            .get("/integrations/" + integrationB1Id)
            .then()
            .extract()
            .response();
    
    // Assert: Access denied
    assertThat(response.getStatusCode()).isIn(403, 404);
    
    // Optional: Verify no data leakage (if 403 might return body with error details)
    if (response.getStatusCode() == 403) {
      String body = response.getBody().asString();
      // Verify we don't leak tenant B's integration name
      assertThat(body).doesNotContain("Integration B1");
    }
  } catch (IllegalStateException e) {
    org.junit.jupiter.api.Assumptions.assumeTrue(
        false, "Admin token not available. Set EZKEY_ADMIN_TOKEN environment variable.");
  }
}
```

**Key patterns from existing tests:**
- Uses `configureForAdminApi(dockerStackConfig)` before API calls
- Uses `given().contentType().header().when().then().extract()` RestAssured pattern
- Uses try-catch with `Assumptions.assumeTrue()` for token availability
- Uses `@Order` annotation for test execution order
- Uses `@DisplayName` for descriptive test names
- Uses AssertJ assertions (`assertThat().isIn()`, etc.)

### Negative Test Pattern (Cross-Tenant Access)

**Following existing patterns from `ApiKeySecurityTest`:**

```java
/**
 * Pattern for testing that cross-tenant access is denied.
 * 
 * This is the MOST CRITICAL test pattern - validates tenant isolation.
 * 
 * Follows existing test patterns from ApiKeySecurityTest.testApiKeyCannotAccessAdminEndpoints()
 */
@Test
@Order(1)
@DisplayName("{ActorType} cannot access other tenant's {resource} (403/404)")
void {resource}_{action}_{actorType}_crossTenant_denied() {
  try {
    configureForAdminApi(dockerStackConfig);
    
    // Arrange: Create resource in Tenant B
    Integer resourceIdFromTenantB = testDataFactory.create{Resource}ForTenant(tenantBId, tenantAdminBToken);
    
    // Act: Actor from Tenant A attempts to access resource from Tenant B
    Response response =
        given()
            .contentType(ContentType.JSON)
            .header("Authorization", "Bearer " + {actorToken}) // tenantAdminAToken or apiKeyAuthHeader
            .when()
            .{httpMethod}("/{resourcePath}/" + resourceIdFromTenantB)
            .then()
            .extract()
            .response();
    
    // Assert: Access denied
    assertThat(response.getStatusCode()).isIn(403, 404);
  } catch (IllegalStateException e) {
    org.junit.jupiter.api.Assumptions.assumeTrue(
        false, "Admin token not available. Set EZKEY_ADMIN_TOKEN environment variable.");
  }
}
```

### Positive Test Pattern (Same-Tenant Access)

**Following existing patterns from `IntegrationManagementSecurityTest.testAdminCanGetIntegrationById()`:**

```java
/**
 * Pattern for testing that same-tenant access is allowed.
 * 
 * Validates correct functionality within allowed boundaries.
 */
@Test
@Order(2)
@DisplayName("{ActorType} can access own tenant's {resource} (200)")
void {resource}_{action}_{actorType}_ownTenant_success() {
  try {
    configureForAdminApi(dockerStackConfig);
    
    // Arrange: Create resource in Tenant A
    Integer resourceIdFromTenantA = testDataFactory.create{Resource}ForTenant(tenantAId, tenantAdminAToken);
    
    // Act: Actor from Tenant A accesses resource from Tenant A
    Response response =
        given()
            .contentType(ContentType.JSON)
            .header("Authorization", "Bearer " + {actorToken})
            .when()
            .{httpMethod}("/{resourcePath}/" + resourceIdFromTenantA)
            .then()
            .extract()
            .response();
    
    // Assert: Access granted
    assertThat(response.getStatusCode()).isEqualTo(200);
    
    // Verify correct data returned
    assertThat(response.jsonPath().getInt("id")).isEqualTo(resourceIdFromTenantA);
  } catch (IllegalStateException e) {
    org.junit.jupiter.api.Assumptions.assumeTrue(
        false, "Admin token not available. Set EZKEY_ADMIN_TOKEN environment variable.");
  }
}
```

### API Key Test Pattern

**Following existing patterns from `ApiKeySecurityTest.testApiKeyCanCreateAuthAttemptsForOwnIntegration()`:**

```java
@Test
@Order(3)
@DisplayName("API key cannot create auth attempt for other tenant's enrollment (403)")
void authAttempt_create_apiKey_crossTenant_denied() {
  try {
    configureForAdminApi(dockerStackConfig);
    
    // Arrange: Create enrollment in Tenant B
    Integer enrollmentB1Id = testDataFactory.createEnrollment(integrationB1Id, tenantAdminBToken);
    
    // API Key A1 (from Tenant A) attempts to create auth attempt for Enrollment B1 (Tenant B)
    String[] apiKeyParts = apiKeyA1Credentials.split(":");
    Map<String, Object> request = new HashMap<>();
    request.put("enrollmentId", enrollmentB1Id);
    request.put("challengeRequested", false);
    
    // Act: Attempt to create auth attempt
    Response response =
        given()
            .contentType(ContentType.JSON)
            .header("Authorization", createApiKeyAuthHeader(apiKeyParts[0], apiKeyParts[1]))
            .body(request)
            .when()
            .post("/auth-attempts")
            .then()
            .extract()
            .response();
    
    // Assert: Access denied
    assertThat(response.getStatusCode()).isEqualTo(403);
  } catch (IllegalStateException e) {
    org.junit.jupiter.api.Assumptions.assumeTrue(
        false, "Admin token not available. Set EZKEY_ADMIN_TOKEN environment variable.");
  }
}

// Helper method following existing pattern from ApiKeySecurityTest
private String createApiKeyAuthHeader(String integrationKey, String secretKey) {
  String credentials = integrationKey + ":" + secretKey;
  String encoded = Base64.getEncoder().encodeToString(credentials.getBytes());
  return "Basic " + encoded;
}
```

### Test Organization Best Practices

**Following existing patterns from security tests:**

1. **Extend AbstractSecurityTest**: All tenant tests should extend `AbstractSecurityTest` (not `@SpringBootTest`)
2. **Use Test Tags**: Follow existing tag pattern:
   - `@Tag(TestTags.FAST)` or `@Tag(TestTags.SLOW)` for speed
   - `@Tag(TestTags.SECURITY)` for security tests
   - Consider adding `@Tag(TestTags.TENANT_ISOLATION)` if new tag is created
   - Use feature tags: `@Tag(TestTags.INTEGRATION)`, `@Tag(TestTags.API_KEY)`, etc.
3. **Test Ordering**: Use `@TestMethodOrder(MethodOrderer.OrderAnnotation.class)` and `@Order()` for execution order
4. **Display Names**: Use `@DisplayName` for descriptive test names shown in test reports
5. **Independent Tests**: Each test should be independent and not rely on other tests
6. **Idempotence Strategy**: Create unique data each time (timestamps, UUIDs, unique suffixes) - **NOT cleanup-based**
   - Use unique identifiers in test data names/IDs
   - Each test execution creates fresh, unique data
   - No cleanup/deletion needed - idempotence achieved through uniqueness
7. **Clean Setup**: Use `@BeforeEach` for common setup, create unique test data in each test or in setup
8. **Token Management**: Use `authTokenManager.getAdminToken()` with try-catch and `Assumptions.assumeTrue()` pattern
9. **API Configuration**: Always use `RestAssuredTestConfig.configureForAdminApi(dockerStackConfig)` before API calls
10. **Assertion Clarity**: Use AssertJ assertions (`assertThat().isIn()`, `isEqualTo()`, etc.)
11. **Documentation**: Add JavaDoc comments explaining the security scenario being tested (following existing pattern)

**Existing Test Infrastructure to Leverage:**
- `AbstractSecurityTest`: Base class with `authTokenManager`, `testDataFactory`, `dockerStackConfig`
- `TestDataFactory`: Factory for creating test entities (needs extension for tenants)
- `AuthTokenManager`: Token management (supports admin tokens, API keys)
- `RestAssuredTestConfig`: Configuration helper for RestAssured
- `TestTags`: Constants for test categorization
- `DockerStackConfig`: Docker stack health checks and configuration

---

## Next Steps

1. **Review and Approve Strategy**: Review this document and approve the approach
2. **Create Test Infrastructure**: Set up base test classes and utilities
3. **Implement Phase 1**: Start with critical tenant isolation tests
4. **Iterate and Refine**: Adjust strategy based on findings during implementation
5. **Document Findings**: Document any issues found during testing
6. **Fix and Validate**: Fix issues found and validate with tests

---

## References

### Existing Test Infrastructure
- `ezkey-tests/src/test/java/org/ezkey/tests/security/AbstractSecurityTest.java` - Base test class
- `ezkey-tests/src/test/java/org/ezkey/tests/util/TestDataFactory.java` - Test data factory (needs extension)
- `ezkey-tests/src/test/java/org/ezkey/tests/util/AuthTokenManager.java` - Token management
- `ezkey-tests/src/test/java/org/ezkey/tests/util/RestAssuredTestConfig.java` - RestAssured configuration
- `ezkey-tests/src/test/java/org/ezkey/tests/tags/TestTags.java` - Test categorization tags
- `ezkey-tests/src/test/java/org/ezkey/tests/security/integration/IntegrationManagementSecurityTest.java` - Example test patterns
- `ezkey-tests/src/test/java/org/ezkey/tests/security/apikey/ApiKeySecurityTest.java` - API key test patterns

### Implementation Code
- `ezkey-admin-api/src/main/java/org/ezkey/admin/security/AccessControlService.java` - Access control logic
- `ezkey-core/src/main/java/org/ezkey/integration/domain/entity/EzkeyAdmin.java` - Admin entity
- `ezkey-core/src/main/java/org/ezkey/integration/domain/entity/Tenant.java` - Tenant entity
- `ezkey-core/src/main/java/org/ezkey/integration/domain/entity/Integration.java` - Integration entity

### Documentation
- `docs/ENDPOINT.md` - API endpoint reference
- `docs/analysis/ADMIN_ONBOARDING_VS_ENROLLMENT_API_ANALYSIS.md` - Admin/enrollment API analysis
- `docs/testing/MULTI_TENANT_OBSERVATIONS.md` - Multi-tenant observations and issues

---

**Document Version**: 1.0  
**Last Updated**: 2025-01-XX  
**Status**: Draft - Pending Review

