# Ezkey Multi-Tenant Philosophy & Testing Strategy

**Document Status:** Specification Reference
**Last Updated:** January 12, 2026
**UTF-8 without BOM**

## Table of Contents

1. [Executive Summary](#executive-summary)
2. [Fundamental Rule: No Impersonation](#fundamental-rule-no-impersonation)
3. [Core Distinction: Viewing vs Creating](#core-distinction-viewing-vs-creating)
4. [Permission Matrices](#permission-matrices)
5. [Implementation Examples](#implementation-examples)
6. [Testing Strategy](#testing-strategy)
7. [References](#references)

---

## Executive Summary

Ezkey implements a pragmatic multi-tenant architecture with two key admin types:
- **GlobalAdmin**: System-wide visibility, creates resources in "Ezkey System" tenant
- **TenantAdmin**: Tenant-scoped visibility and resource creation

**Critical Design Principle:**
```
GlobalAdmin VIEWS all resources across all tenants
BUT
GlobalAdmin CREATES resources ONLY in "Ezkey System" tenant (tenantId: 1)
```

This document defines the philosophy, rationale, and testing approach for this architecture.

---

## Fundamental Rule: No Impersonation

**Source:** `.cursor/plans/phase_1_multi-tenant_6ba5f673.plan.md` (line 193)

> **"No impersonation: Administrators cannot create resources (integrations, enrollments, API keys, auth attempts) for other tenants. If a Global Admin needs to create resources for a specific tenant, they must become a Tenant Admin for that tenant."**

### What This Means

1. **GlobalAdmin creates integration** → Assigned to **"Ezkey System"** tenant (tenantId: 1)
2. **TenantAdmin creates integration** → Assigned to **their own tenant**
3. **No cross-tenant creation** → A GlobalAdmin cannot create resources "on behalf of" a TenantAdmin

### Rationale

- **Simplicity**: No complex delegation/impersonation logic
- **Auditability**: Clear ownership - who created what
- **Security**: Explicit boundaries prevent accidental cross-tenant operations
- **Pragmatism**: Covers 90% of use cases without enterprise over-engineering

### Implementation Pattern

```java
// Integration creation - automatic tenant assignment
if (admin.getAdminType() == GLOBAL_ADMIN) {
    integration.setTenant(systemTenant); // tenantId: 1
} else if (admin.getAdminType() == TENANT_ADMIN) {
    integration.setTenant(admin.getTenant()); // admin's tenant
}
```

---

## Core Distinction: Viewing vs Creating

The multi-tenant architecture makes a **critical distinction** between read and write operations:

### 📖 VIEWING (Read Operations)

**GlobalAdmin Behavior:**
- ✅ **Sees ALL resources across ALL tenants**
- No tenant filtering applied
- Full system-wide visibility for administration, monitoring, compliance

**Implementation:**
```java
private Integer extractTenantId(Authentication auth) {
    if (principal instanceof AdminPrincipal adminPrincipal) {
        return adminPrincipal.tenantId();
        // Returns null for GlobalAdmin → no filtering
        // Returns tenantId for TenantAdmin → filtered
    }
    return null;
}
```

**Examples:**
| Endpoint | GlobalAdmin | TenantAdmin A | TenantAdmin B |
|----------|-------------|---------------|---------------|
| `GET /api/v1/integrations` | All integrations (tenants 1, 2, 3, ...) | Only tenant A integrations | Only tenant B integrations |
| `GET /api/v1/api-keys` | All API keys (all tenants) | Only tenant A keys | Only tenant B keys |
| `GET /api/v1/admins` | All admins (all tenants) | Only tenant A admins | Only tenant B admins |

**TenantAdmin Behavior:**
- ✅ **Sees ONLY resources in their tenant**
- Automatic tenant filtering via `tenantId` in `AdminPrincipal`
- Cross-tenant access returns 403 Forbidden or 404 Not Found

### ✏️ CREATING (Write Operations)

**GlobalAdmin Behavior:**
- ❌ **Cannot create resources for other tenants**
- ✅ **Creates resources in "Ezkey System" tenant ONLY**
- To create for a specific tenant → must become TenantAdmin for that tenant

**Examples:**
| Endpoint | GlobalAdmin | TenantAdmin A |
|----------|-------------|---------------|
| `POST /api/v1/integrations` | Created in System Tenant (ID: 1) | Created in Tenant A |
| `POST /api/v1/api-keys` | For System Tenant integration | For Tenant A integration |
| `POST /api/v1/enrollments` | For System Tenant integration | For Tenant A integration |

**TenantAdmin Behavior:**
- ✅ **Creates resources in their tenant ONLY**
- ❌ **Cannot create resources for other tenants**
- Automatic tenant assignment via `admin.getTenant()`

### Why This Distinction?

1. **Operational Need**: GlobalAdmin needs visibility for monitoring, compliance, troubleshooting
2. **Security Boundary**: Cross-tenant creation is blocked to prevent accidents/abuse
3. **Auditability**: Clear creation trail - resources belong to the admin who created them
4. **Flexibility**: GlobalAdmin can become TenantAdmin when needed (dual roles)

---

## Permission Matrices

### Matrix 1: API Endpoint Permissions

| Controller | Endpoint | GlobalAdmin | TenantAdmin (Own) | TenantAdmin (Other) |
|------------|----------|-------------|-------------------|---------------------|
| **ApiKeyController** |
| | `POST /api-keys` | ✅ System Tenant | ✅ Own tenant | ❌ 403 |
| | `GET /api-keys` | ✅ All tenants | ✅ Own tenant | ❌ (filtered) |
| | `GET /api-keys/{id}` | ✅ Any tenant | ✅ Own tenant | ❌ 403/404 |
| | `DELETE /api-keys/{id}` | ✅ Any tenant | ✅ Own tenant | ❌ 403 |
| **IntegrationController** |
| | `POST /integrations` | ✅ System Tenant | ✅ Own tenant | ❌ 403 |
| | `GET /integrations` | ✅ All tenants | ✅ Own tenant | ❌ (filtered) |
| | `GET /integrations/{id}` | ✅ Any tenant | ✅ Own tenant | ❌ 403/404 |
| | `DELETE /integrations/{id}` | ✅ Any tenant | ✅ Own tenant | ❌ 403 |
| **AdminProvisioningController** |
| | `POST /admins/tenant` | ✅ Any tenant | ✅ Own tenant peers | ❌ 403 |
| | `GET /admins` | ✅ All tenants | ✅ Own tenant | ❌ (filtered) |
| | `GET /admins/{id}` | ✅ Any admin | ✅ Own tenant admin | ❌ 403/404 |
| **TenantController** |
| | `POST /tenants` | ✅ Create | ❌ 403 | ❌ 403 |
| | `GET /tenants` | ✅ All tenants | ✅ Own tenant | ❌ (filtered) |
| | `GET /tenants/{id}` | ✅ Any tenant | ✅ Own tenant | ❌ 403/404 |

**Legend:**
- ✅ = Allowed (200/201)
- ❌ 403 = Forbidden (explicit denial)
- ❌ 404 = Not Found (resource not accessible)
- ❌ (filtered) = Not shown in list (filtered out automatically)

### Matrix 2: Test Cases - Tenant Isolation

| Test Case | Actor | Target Resource | Target Tenant | Expected Result | Priority |
|-----------|-------|-----------------|---------------|-----------------|----------|
| **Positive Cases** |
| Case 1 | TenantAdmin A | Integration A | Tenant A | ✅ 200 OK | P0 |
| Case 2 | TenantAdmin A | API Key A | Tenant A | ✅ 200 OK | P0 |
| Case 3 | GlobalAdmin | Integration A | Tenant A | ✅ 200 OK | P0 |
| Case 4 | GlobalAdmin | Integration B | Tenant B | ✅ 200 OK | P0 |
| **Cross-Tenant Isolation** |
| Case 5 | TenantAdmin A | Integration B | Tenant B | ❌ 403/404 | **P0 CRITICAL** |
| Case 6 | TenantAdmin A | API Key B | Tenant B | ❌ 403/404 | **P0 CRITICAL** |
| Case 7 | TenantAdmin A | Enrollment B | Tenant B | ❌ 403 | **P0 CRITICAL** |
| **List Filtering** |
| Case 8 | TenantAdmin A | List integrations | All tenants | ✅ Only Tenant A | P0 |
| Case 9 | TenantAdmin A | List API keys | All tenants | ✅ Only Tenant A | P0 |
| Case 10 | GlobalAdmin | List integrations | All tenants | ✅ All tenants | P0 |
| Case 11 | GlobalAdmin | List API keys | All tenants | ✅ All tenants | P0 |

---

## Implementation Examples

### Example 1: API Key Listing with Tenant Filtering

**File:** `ezkey-admin-api/src/main/java/org/ezkey/admin/controller/ApiKeyController.java`

```java
@PreAuthorize("hasRole('ADMIN')")
@GetMapping
public ResponseEntity<List<ApiKeyResponseDto>> listAllApiKeys() {
    EzkeyAdmin currentAdmin = getCurrentAdmin();

    List<ApiKey> apiKeys;

    if (currentAdmin.getAdminType() == EzkeyAdmin.AdminType.GLOBAL_ADMIN) {
        // GlobalAdmin can see all API keys across all tenants
        logger.debug("GlobalAdmin - fetching all API keys");
        apiKeys = apiKeyService.findAll();
    } else {
        // TenantAdmin sees only keys for their tenant's integrations
        Integer tenantId = currentAdmin.getTenant().getTenantId();
        logger.debug("TenantAdmin - fetching API keys for tenant: {}", tenantId);
        apiKeys = apiKeyService.listApiKeysByTenant(tenantId);
    }

    List<ApiKeyResponseDto> response =
        apiKeys.stream()
               .map(this::mapToResponseDto)
               .collect(Collectors.toList());

    return ResponseEntity.ok(response);
}
```

**Key Points:**
1. ✅ GlobalAdmin path: `findAll()` - no filtering
2. ✅ TenantAdmin path: `listApiKeysByTenant(tenantId)` - automatic filtering
3. ✅ No cross-tenant access possible for TenantAdmin

### Example 2: Integration Search with Tenant Scoping

**File:** `ezkey-admin-api/src/main/java/org/ezkey/admin/controller/IntegrationController.java`

```java
@PreAuthorize("hasRole('ADMIN')")
@GetMapping
public ResponseEntity<Page<IntegrationResponseDto>> search(
    @RequestParam(required = false) String integrationName,
    @RequestParam(required = false) Boolean active,
    Pageable pageable) {

    // Extract tenant ID from authentication for tenant scoping
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    Integer tenantId = extractTenantId(auth);
    // Returns null for GlobalAdmin, tenantId for TenantAdmin

    Page<IntegrationResponseDto> integrations =
        service.findByFilters(integrationName, active, null, null, tenantId, pageable)
               .map(mapper::toResponse);

    return ResponseEntity.ok(integrations);
}

private Integer extractTenantId(Authentication auth) {
    if (auth == null || auth.getPrincipal() == null) {
        return null;
    }

    Object principal = auth.getPrincipal();
    if (principal instanceof AdminPrincipal adminPrincipal) {
        return adminPrincipal.tenantId();
        // null for GlobalAdmin → sees all
        // tenantId for TenantAdmin → filtered
    }

    return null;
}
```

**Key Points:**
1. ✅ Single method handles both admin types
2. ✅ `tenantId = null` → no filtering (GlobalAdmin)
3. ✅ `tenantId = X` → automatic filtering (TenantAdmin)
4. ✅ Service layer applies filtering transparently

---

## Testing Strategy

### Test Organization

**Test Packages:**
```
ezkey-tests/src/test/java/org/ezkey/tests/security/
├── multitenant/
│   ├── TenantCrossIsolationSecurityTest.java
│   └── TenantBoundaryPermissionsSecurityTest.java
└── tenant/
    ├── MultiTenantGlobalAdminTest.java
    └── TenantBasicOperationsTest.java
```

### Test Naming Convention

**Format:** `test{AdminType}{Action}{Scope}_{ExpectedResult}`

**Examples:**
- ✅ `testTenantAdminACanListOnlyOwnApiKeys()` - Positive case
- ✅ `testTenantAdminACannotAccessOtherTenantIntegration()` - Cross-tenant denial
- ✅ `testGlobalAdminCanListAllApiKeys()` - GlobalAdmin visibility
- ✅ `testGlobalAdminCreatesIntegrationInSystemTenant()` - Creation behavior

### Test Priorities

**P0 - Critical (Must Pass):**
1. ✅ Cross-tenant isolation (TenantAdmin cannot access other tenant resources)
2. ✅ List filtering (TenantAdmin sees only own tenant, GlobalAdmin sees all)
3. ✅ Creation tenant assignment (GlobalAdmin → System, TenantAdmin → own)

**P1 - High (Should Pass):**
1. ✅ Permission boundaries (TenantAdmin cannot perform GlobalAdmin operations)
2. ✅ API key tenant scoping validation
3. ✅ Admin listing filtered by tenant

**P2 - Medium (Nice to Have):**
1. ⚠️ Edge cases (deactivation, complex queries)
2. ⚠️ Performance with large tenant counts

### Test Data Strategy

**Unique Data Creation (Idempotent):**
```java
@BeforeEach
void setUp() {
    String uniqueSuffix = String.valueOf(System.currentTimeMillis());

    // Create unique tenants for this test run
    tenantAId = testDataFactory.createTenant("Tenant A " + uniqueSuffix, globalAdminToken);
    tenantBId = testDataFactory.createTenant("Tenant B " + uniqueSuffix, globalAdminToken);

    // Create tenant admins with unique usernames
    tenantAdminAToken = testDataFactory.createTenantAdmin(
        "tenant.admin.a." + uniqueSuffix, tenantAId, globalAdminToken);

    // No cleanup needed - unique data ensures idempotence
}
```

**Why Unique Data:**
1. ✅ Tests can run multiple times without conflicts
2. ✅ No cleanup required between runs
3. ✅ Independent test execution (can run in parallel)
4. ✅ Fresh state for each test

### Critical Test Cases

#### Test 1: TenantAdmin List Filtering

```java
@Test
@DisplayName("TenantAdmin A can list only own API keys")
void testTenantAdminACanListOnlyOwnApiKeys() {
    // Given: API keys exist in both tenants
    String apiKeyA = testDataFactory.createApiKeyForIntegration(integrationAId, tenantAdminAToken);
    String apiKeyB = testDataFactory.createApiKeyForIntegration(integrationBId, tenantAdminBToken);

    // When: TenantAdmin A lists API keys
    Response response = given()
        .auth().oauth2(tenantAdminAToken)
        .when().get("/api/v1/api-keys")
        .then().statusCode(200)
        .extract().response();

    List<Map<String, Object>> apiKeys = response.jsonPath().getList("$");

    // Then: Only Tenant A keys are returned
    assertThat(apiKeys).isNotEmpty();
    assertThat(apiKeys).allMatch(key ->
        key.get("integrationId").equals(integrationAId));

    // And: Tenant B keys are NOT visible
    assertThat(apiKeys).noneMatch(key ->
        key.get("integrationId").equals(integrationBId));
}
```

#### Test 2: GlobalAdmin Sees All

```java
@Test
@DisplayName("GlobalAdmin can list all API keys across all tenants")
void testGlobalAdminCanListAllApiKeys() {
    // Given: API keys exist in multiple tenants
    String apiKeyA = testDataFactory.createApiKeyForIntegration(integrationAId, tenantAdminAToken);
    String apiKeyB = testDataFactory.createApiKeyForIntegration(integrationBId, tenantAdminBToken);

    // When: GlobalAdmin lists API keys
    Response response = given()
        .auth().oauth2(globalAdminToken)
        .when().get("/api/v1/api-keys")
        .then().statusCode(200)
        .extract().response();

    List<Map<String, Object>> apiKeys = response.jsonPath().getList("$");

    // Then: Keys from ALL tenants are returned
    List<Integer> integrationIds = apiKeys.stream()
        .map(key -> (Integer) key.get("integrationId"))
        .toList();

    assertThat(integrationIds).contains(integrationAId, integrationBId);
}
```

#### Test 3: Cross-Tenant Access Denied

```java
@Test
@DisplayName("TenantAdmin A cannot access Tenant B's integration")
void testTenantAdminACannotAccessOtherTenantIntegration() {
    // Given: Integration B exists in Tenant B
    Integer integrationBId = testDataFactory.createIntegrationForTenant(
        "Integration B", tenantBId, tenantAdminBToken);

    // When: TenantAdmin A attempts to access Integration B
    Response response = given()
        .auth().oauth2(tenantAdminAToken)
        .when().get("/api/v1/integrations/" + integrationBId)
        .then().extract().response();

    // Then: Access is denied (403 Forbidden or 404 Not Found)
    assertThat(response.statusCode()).isIn(403, 404);
}
```

---

## References

### Specification Documents

1. **Phase 1 Multi-Tenant Plan**
   - File: `.cursor/plans/phase_1_multi-tenant_6ba5f673.plan.md`
   - Lines 190-270: "No Impersonation" rule, tenant assignment logic

2. **Multi-Tenant Test Plan**
   - File: `docs/testing/MULTI_TENANT_TEST_PLAN.md`
   - Sections 1.1-1.3: Tenant isolation test scenarios

3. **Tenant Permissions Test Strategy**
   - File: `docs/testing/TENANT_PERMISSIONS_TEST_STRATEGY.md`
   - Matrices 1-3: Comprehensive permission matrices

### Implementation Files

1. **ApiKeyController.java**
   - File: `ezkey-admin-api/src/main/java/org/ezkey/admin/controller/ApiKeyController.java`
   - Lines 241-290: List all API keys with tenant filtering

2. **IntegrationController.java**
   - File: `ezkey-admin-api/src/main/java/org/ezkey/admin/controller/IntegrationController.java`
   - Method `extractTenantId()`: Tenant ID extraction logic

3. **AdminProvisioningService.java**
   - File: `ezkey-admin-api/src/main/java/org/ezkey/admin/service/AdminProvisioningService.java`
   - Lines 135-165: Tenant creation logic
   - Lines 284-320: TenantAdmin creation with validation

### Test Files

1. **TenantCrossIsolationSecurityTest.java**
   - File: `ezkey-tests/src/test/java/org/ezkey/tests/security/multitenant/TenantCrossIsolationSecurityTest.java`
   - Cross-tenant isolation validation

2. **MultiTenantGlobalAdminTest.java**
   - File: `ezkey-tests/src/test/java/org/ezkey/tests/security/tenant/MultiTenantGlobalAdminTest.java`
   - GlobalAdmin visibility tests

---

## Conclusion

The Ezkey multi-tenant architecture implements a **clear and pragmatic separation**:

1. ✅ **Viewing**: GlobalAdmin sees all, TenantAdmin sees own tenant
2. ✅ **Creating**: GlobalAdmin creates in System Tenant, TenantAdmin creates in own tenant
3. ✅ **No Impersonation**: No cross-tenant creation, explicit tenant membership required
4. ✅ **Auditability**: Clear ownership and responsibility tracking

This design balances operational needs (GlobalAdmin visibility) with security boundaries (no cross-tenant creation), providing a simple yet robust multi-tenant foundation.

---

**Document Maintenance:**
- Update this document when adding new multi-tenant features
- Reference this document in test Javadoc and AGENTS.md
- Review alignment with specs when modifying tenant logic
