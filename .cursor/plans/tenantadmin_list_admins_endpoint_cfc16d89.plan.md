---
name: TenantAdmin List Admins Endpoint
overview: Implement endpoint to allow TenantAdmin to list administrators of their tenant. Three design options are analyzed with detailed pros/cons based on project values (simplicity, uniformity, security). Option 1 is recommended as it aligns with existing patterns in IntegrationController.
todos:
  - id: create-admin-response-dto
    content: "Create AdminResponseDto record in ezkey-admin-api/src/main/java/org/ezkey/admin/dto/response/AdminResponseDto.java with fields: adminId, username, email, firstName, lastName, adminType, tenantId, active, createdAt"
    status: completed
  - id: add-service-method
    content: Add listAdmins(Integer tenantId, Pageable pageable) method to AdminProvisioningService that filters by tenantId (null = all for GlobalAdmin, specific tenantId for TenantAdmin)
    status: completed
    dependencies:
      - create-admin-response-dto
  - id: add-controller-endpoint
    content: Add GET /api/v1/admins endpoint to AdminProvisioningController with @PreAuthorize('hasRole(ADMIN)'), extract tenantId from Authentication, and call service method
    status: completed
    dependencies:
      - add-service-method
  - id: implement-mapper
    content: Create mapper method to convert EzkeyAdmin entity to AdminResponseDto, handling null tenant case for GlobalAdmin
    status: completed
    dependencies:
      - create-admin-response-dto
  - id: add-unit-tests
    content: Add unit tests for AdminProvisioningService.listAdmins() testing tenant filtering logic for both GlobalAdmin and TenantAdmin cases
    status: completed
    dependencies:
      - add-service-method
  - id: add-integration-tests
    content: Add integration tests for GET /api/v1/admins endpoint verifying tenant isolation (TenantAdmin sees only their tenant, GlobalAdmin sees all)
    status: completed
    dependencies:
      - add-controller-endpoint
  - id: update-documentation
    content: Update OpenAPI annotations and add endpoint to Postman collection
    status: completed
    dependencies:
      - add-controller-endpoint
---

# Implementation Plan: TenantAdmin List Admins Endpoint

## Context

Currently, TenantAdmins cannot list other TenantAdmins in their tenant. The `GET /api/v1/admins` endpoint does not exist. The backend repository method `EzkeyAdminRepository.findByTenantTenantId()` exists but there's no REST endpoint exposing it.

## Design Options Analysis

### Option 1: `GET /api/v1/admins` with Automatic Tenant Filtering

**Pattern:** Same as `IntegrationController.search()` - single endpoint with automatic tenant scoping based on authenticated user.**Implementation:**

- GlobalAdmin: Returns all admins (all tenants)
- TenantAdmin: Returns only admins from their tenant (automatic filtering)
- Uses `@PreAuthorize("hasRole('ADMIN')")` to allow both roles
- Controller extracts tenantId from Authentication context
- Service layer filters based on tenantId (null for GlobalAdmin = all tenants)

**Code Structure:**

```java
@GetMapping
@PreAuthorize("hasRole('ADMIN')")
public ResponseEntity<List<AdminResponseDto>> listAdmins(
    @RequestParam(required = false) Boolean active,
    Pageable pageable) {
  Integer tenantId = extractTenantId(auth); // null for GlobalAdmin
  // Service filters by tenantId automatically
}
```

**Advantages:**

- ✅ **Uniformity**: Matches existing pattern in `IntegrationController`, `EnrollmentController`, `AuthAttemptController`
- ✅ **Simplicity**: Single endpoint, no path parameters needed for TenantAdmin
- ✅ **Developer-friendly**: Same pattern developers already know from other endpoints
- ✅ **Security**: Automatic tenant scoping at service layer (proven pattern)
- ✅ **Flexibility**: Can add filters later (active, adminType) without breaking changes
- ✅ **Consistency**: Aligns with RESTful design - resource collection at `/api/v1/admins`

**Disadvantages:**

- ⚠️ Slightly less explicit than Option 2 (tenantId in path)
- ⚠️ GlobalAdmin gets all admins by default (may need filtering if many tenants)

**Alignment with Project Values:**

- **Simplicity**: ✅ Single endpoint, intuitive usage
- **Uniformity**: ✅ ✅ Matches existing codebase patterns exactly
- **Security**: ✅ Uses proven AccessControlService pattern
- **Developer-friendly**: ✅ Consistent with what developers already know

---

### Option 2: `GET /api/v1/admins/tenant/{tenantId}`

**Pattern:** Tenant-specific sub-resource endpoint with explicit tenantId in path.**Implementation:**

- GlobalAdmin: Can specify any tenantId in path
- TenantAdmin: Must specify their own tenantId (controller validates)
- Returns 403 if TenantAdmin tries to access different tenant
- Uses `@PreAuthorize("hasRole('ADMIN')")` 
- Controller validates tenantId matches authenticated user's tenant (for TenantAdmin)

**Code Structure:**

```java
@GetMapping("/tenant/{tenantId}")
@PreAuthorize("hasRole('ADMIN')")
public ResponseEntity<List<AdminResponseDto>> listAdminsByTenant(
    @PathVariable Integer tenantId) {
  // Validate: TenantAdmin can only access their own tenantId
  if (principal.isTenantAdmin() && !principal.tenantId().equals(tenantId)) {
    return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
  }
  // Use repository.findByTenantTenantId(tenantId)
}
```

**Advantages:**

- ✅ **Explicit**: Clear intent - listing admins for a specific tenant
- ✅ **Flexibility for GlobalAdmin**: Can easily query any tenant's admins
- ✅ **Self-documenting**: URL shows exactly what resource is being accessed

**Disadvantages:**

- ❌ **Inconsistency**: Different pattern from other list endpoints in codebase
- ❌ **Complexity for TenantAdmin**: Must know their tenantId to call endpoint
- ❌ **Less uniform**: Doesn't match `GET /api/v1/integrations`, `GET /api/v1/enrollments` patterns
- ❌ **Validation overhead**: Requires controller-level validation that Option 1 doesn't need
- ❌ **Breaking change potential**: If we later add Option 1, we'd have duplicate endpoints

**Alignment with Project Values:**

- **Simplicity**: ⚠️ Requires TenantAdmin to know tenantId (extra step)
- **Uniformity**: ❌ Different pattern from existing endpoints
- **Security**: ✅ Explicit validation, but more code to maintain
- **Developer-friendly**: ⚠️ Inconsistent with what developers expect

---

### Option 3: `GET /api/v1/admins/me/peers`

**Pattern:** Self-referential endpoint using "me" convention, specific to peer admin listing.**Implementation:**

- TenantAdmin only: Returns admins from same tenant as authenticated user
- GlobalAdmin: Returns empty list or error (endpoint not applicable)
- Uses `@PreAuthorize("hasRole('TENANT_ADMIN')")` 
- No parameters needed - automatically uses authenticated user's tenant

**Code Structure:**

```java
@GetMapping("/me/peers")
@PreAuthorize("hasRole('TENANT_ADMIN')")
public ResponseEntity<List<AdminResponseDto>> listPeerAdmins() {
  Integer tenantId = extractTenantId(auth); // Must exist for TenantAdmin
  // Use repository.findByTenantTenantId(tenantId)
}
```

**Advantages:**

- ✅ **Simple for TenantAdmin**: No parameters needed
- ✅ **Self-documenting**: "me/peers" clearly indicates listing peer admins
- ✅ **Security**: No tenantId parameter to validate or manipulate

**Disadvantages:**

- ❌ **Limited scope**: Only for TenantAdmin, not extensible to GlobalAdmin use case
- ❌ **Non-standard pattern**: "me/peers" is not a common REST pattern
- ❌ **Inconsistency**: Different from other list endpoints
- ❌ **Future limitation**: Cannot be extended if GlobalAdmin needs similar functionality
- ❌ **Less flexible**: Cannot add filters or pagination easily in intuitive way

**Alignment with Project Values:**

- **Simplicity**: ✅ Simple for TenantAdmin, but ⚠️ non-standard pattern
- **Uniformity**: ❌ Different pattern from existing endpoints
- **Security**: ✅ Secure but limited in scope
- **Developer-friendly**: ⚠️ Non-standard REST pattern may confuse developers

---

## Recommendation: Option 1

**Option 1 is the clear winner** based on:

1. **Perfect alignment with existing codebase patterns** - `IntegrationController`, `EnrollmentController`, and `AuthAttemptController` all use this exact pattern
2. **Uniformity** - Developers already understand this pattern
3. **Simplicity** - Single endpoint, automatic filtering, no path parameters needed for TenantAdmin
4. **Future-proof** - Can easily extend with filters (active, adminType, etc.) without breaking changes
5. **Security** - Uses proven service-layer filtering pattern already validated in production

The only minor concern (GlobalAdmin seeing all admins) is already handled in other endpoints and is the expected behavior for GlobalAdmin.

## Implementation Details

### Files to Modify/Create

1. **DTO Creation**:

- Create `AdminResponseDto` in `ezkey-admin-api/src/main/java/org/ezkey/admin/dto/response/AdminResponseDto.java`
- Include: adminId, username, email, firstName, lastName, adminType, tenantId, active, createdAt

2. **Controller**:

- Add `GET /api/v1/admins` endpoint to `AdminProvisioningController.java`
- Use `@PreAuthorize("hasRole('ADMIN')")`
- Extract tenantId from Authentication (null for GlobalAdmin)
- Call service method with tenantId

3. **Service**:

- Add `listAdmins(Integer tenantId, Pageable pageable)` to `AdminProvisioningService.java`
- Filter: if tenantId is null (GlobalAdmin), return all admins; else filter by tenantId
- Use `EzkeyAdminRepository.findByTenantTenantId()` for TenantAdmin
- Use `EzkeyAdminRepository.findAll()` for GlobalAdmin

4. **Repository**:

- Already exists: `EzkeyAdminRepository.findByTenantTenantId(Integer tenantId)`
- May need pagination variant if using Pageable

5. **Mapper**:

- Create mapper method `EzkeyAdmin -> AdminResponseDto`
- Handle null tenant case for GlobalAdmin

6. **Tests**:

- Unit tests for service layer filtering logic
- Integration tests for endpoint with both GlobalAdmin and TenantAdmin
- Test tenant isolation (TenantAdmin cannot see other tenants' admins)

7. **Documentation**:

- Update OpenAPI annotations
- Add to Postman collection
- Update endpoint documentation

## Implementation Steps

1. Create `AdminResponseDto` record with all necessary fields
2. Add `listAdmins()` method to `AdminProvisioningService` with tenant filtering
3. Add `GET /api/v1/admins` endpoint to `AdminProvisioningController`
4. Implement mapper for `EzkeyAdmin` to `AdminResponseDto`
5. Add unit tests for service layer
6. Add integration tests for endpoint
7. Update OpenAPI documentation