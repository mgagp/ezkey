---
name: Enrollment uniqueness constraint
overview: Implement uniqueness constraint for enrollments to prevent duplicate VERIFIED enrollments with the same name for the same integration, while allowing multiple CREATED enrollments for retry scenarios. The constraint is based on enrollment_status='VERIFIED', not the active flag which is for administrative control. No automatic supersession - replacement must go through the existing recovery process.
todos:
  - id: db-constraint
    content: Create database migration with partial unique index for VERIFIED enrollments (integration_id, enrollment_name) WHERE enrollment_status='VERIFIED'
    status: completed
  - id: db-index
    content: Add performance index for validation queries (integration_id, enrollment_name, enrollment_status)
    status: completed
  - id: repository-method
    content: Add repository methods to find VERIFIED enrollments with same integration and name
    status: in_progress
  - id: service-creation-validation
    content: Add validation in EnrollmentService.create() to reject creation if active VERIFIED enrollment exists, but allow if inactive
    status: pending
  - id: audit-creation-rejection
    content: Enhance audit logging in EnrollmentController.create() to include existing enrollment ID when creation is rejected
    status: pending
  - id: audit-creation-replacement
    content: Enhance audit logging in EnrollmentController.create() to include inactive enrollment ID when replacing inactive VERIFIED enrollment
    status: pending
  - id: service-verification-validation
    content: Add validation in EnrollmentVerifyService to check for existing VERIFIED enrollments and reject verification with clear error message
    status: pending
  - id: audit-verification-rejection
    content: Enhance audit logging in EnrollmentController.verify() (auth-api) to include existing enrollment ID when verification is rejected
    status: pending
  - id: unit-tests
    content: Write unit tests for uniqueness validation and error handling
    status: completed
  - id: integration-tests
    content: Write integration tests for complete enrollment flow with constraint enforcement
    status: completed
isProject: false
---

# Enrollment Uniqueness Constraint and Supersession Implementation

## Problem Analysis

Currently, the system allows creating multiple enrollments with the same `enrollment_name` for the same `integration_id`. This creates confusion and potential security issues. The user has identified 5 enrollments with the same name, all in VERIFIED status but none active.

**Important Distinction**:

- **`enrollment_status = 'VERIFIED'`**: Indicates the enrollment process with the mobile app has been completed successfully
- **`enrollment_active`**: Administrative flag to selectively disable an otherwise valid VERIFIED enrollment

## Proposed Solution

**Security-First Approach**: Unlike authentication attempts which use supersession, enrollments require explicit replacement through the recovery process:

1. **Allow multiple CREATED enrollments** - Users can retry enrollment creation
2. **Enforce uniqueness for VERIFIED enrollments** - Only one VERIFIED enrollment per (integration_id, enrollment_name)
3. **Reject duplicate verification attempts** - If a VERIFIED enrollment already exists, reject the new verification with a clear error message
4. **Replacement through recovery process** - Users must use the existing recovery code process (`/api/v1/admin/auth/recover` + `/api/v1/admin/enrollments/reset`) to replace an enrollment

**Rationale**:

- **Security**: INVALID status means "verification failed or enrollment is compromised" - not appropriate for automatic replacement
- **Audit Trail**: Explicit replacement process provides clear audit trail
- **Existing Protection**: `EnrollmentTxHelper.markInvalidAndClear()` explicitly protects VERIFIED enrollments from being marked INVALID
- **Business Process**: Recovery process already exists for device replacement scenarios

## Implementation Details

### 1. Database Constraint

**File**: New migration file (e.g., `V28__enrollment_unique_verified_name.sql`)

Add a partial unique index to enforce uniqueness only for VERIFIED enrollments:

```sql
-- Partial unique index: only one VERIFIED enrollment per integration+name
CREATE UNIQUE INDEX idx_enrollment_unique_verified_name
ON ezkey_enrollment(integration_id, enrollment_name)
WHERE enrollment_status = 'VERIFIED';

COMMENT ON INDEX idx_enrollment_unique_verified_name IS
'Ensures only one VERIFIED enrollment exists per integration and enrollment name. Allows multiple CREATED enrollments for retry scenarios. The enrollment_active flag is separate and used for administrative control.';
```

**Rationale**:

- Uses partial index (WHERE clause) to allow multiple non-VERIFIED enrollments
- PostgreSQL partial unique indexes exclude rows that don't match the WHERE condition
- Allows multiple CREATED, BOUND, and INVALID enrollments while preventing duplicate VERIFIED ones
- Based on `enrollment_status`, not `enrollment_active` (which is for administrative control)

### 2. Repository Methods

**File**: `ezkey-core/src/main/java/org/ezkey/enrollment/domain/repository/EnrollmentRepository.java`

Add methods to find VERIFIED enrollments with same name:

```java
/**
 * Finds VERIFIED enrollments with the same integration and name.
 * Used for validation during enrollment creation.
 *
 * @param integrationId the integration ID
 * @param enrollmentName the enrollment name
 * @param status the enrollment status to search for
 * @return list of enrollments with same integration, name, and status
 */
List<Enrollment> findByIntegrationIdAndEnrollmentNameAndStatus(
    Integer integrationId,
    String enrollmentName,
    EnrollmentStatus status);

/**
 * Finds VERIFIED enrollments with the same integration and name.
 * Used for validation during enrollment verification.
 *
 * @param integrationId the integration ID
 * @param enrollmentName the enrollment name
 * @param excludeEnrollmentId enrollment ID to exclude from results (current enrollment)
 * @return list of VERIFIED enrollments with same integration and name
 */
List<Enrollment> findByIntegrationIdAndEnrollmentNameAndStatusAndEnrollmentIdNot(
    Integer integrationId,
    String enrollmentName,
    EnrollmentStatus status,
    Integer excludeEnrollmentId);
```

### 3. Service Layer - Enrollment Creation

**File**: `ezkey-core/src/main/java/org/ezkey/enrollment/service/EnrollmentService.java`

**Method**: `create(EnrollmentCreateRequest request)`

**Security validation** before creating enrollment:

- Check if a VERIFIED enrollment already exists with same (integration_id, enrollment_name)
- **If VERIFIED enrollment is ACTIVE**: Reject creation with clear error message directing to recovery process
- **If VERIFIED enrollment is INACTIVE**: Allow creation (admin has deactivated the old enrollment, replacement is allowed)
- **If no VERIFIED enrollment exists**: Allow creation (normal flow)

**Rationale**:

- **Security**: Prevents creating duplicate enrollments when an active one exists (must use recovery process)
- **Flexibility**: Allows replacement when admin has explicitly deactivated the old enrollment
- **Early Detection**: Catches the issue at creation time, not at verification time
- **Clear Guidance**: Error message directs users to the correct replacement process

**Implementation**:

```java
// Security validation: Check for existing VERIFIED enrollment
List<Enrollment> existingVerifiedEnrollments =
    enrollmentRepository.findByIntegrationIdAndEnrollmentNameAndStatus(
        request.getIntegrationId(),
        request.getName().trim(),
        EnrollmentStatus.VERIFIED);

if (!existingVerifiedEnrollments.isEmpty()) {
    Enrollment existing = existingVerifiedEnrollments.get(0);

    // If VERIFIED enrollment is active, reject creation
    if (Boolean.TRUE.equals(existing.getActive())) {
        logger.warn(
            "Enrollment creation rejected: Active VERIFIED enrollment {} (ID: {}) already exists for integration {} and name '{}'. "
            + "Use recovery process (/api/v1/admin/auth/recover + /api/v1/admin/enrollments/reset) to replace enrollment.",
            existing.getEnrollmentName(),
            existing.getEnrollmentId(),
            request.getIntegrationId(),
            request.getName());
        throw new IllegalArgumentException(
            "An active verified enrollment with the same name already exists for this integration. "
            + "To replace an enrollment, use the recovery process: POST /api/v1/admin/auth/recover with a recovery code, "
            + "then POST /api/v1/admin/enrollments/reset to reset the existing enrollment.");
    }

    // If VERIFIED enrollment is inactive, allow creation (admin has deactivated it)
    logger.info(
        "Enrollment creation allowed: Inactive VERIFIED enrollment {} (ID: {}) exists. "
        + "Creating new enrollment for replacement.",
        existing.getEnrollmentName(),
        existing.getEnrollmentId());
}
```

**Audit Logging**:

The validation exception will be caught by `EnrollmentController.create()` which already logs FAILURE events. However, we need to enhance the audit log to include the existing enrollment ID for complete traceability:

```java
// In EnrollmentController.create() catch block for IllegalArgumentException:
catch (IllegalArgumentException e) {
    // Check if error is about existing VERIFIED enrollment
    if (e.getMessage().contains("active verified enrollment")) {
        // Extract existing enrollment ID from service layer if possible
        // Or query to find it for audit purposes
        Enrollment existing = enrollmentRepository
            .findByIntegrationIdAndEnrollmentNameAndStatus(
                request.integrationId(),
                request.name(),
                EnrollmentStatus.VERIFIED)
            .stream()
            .filter(e -> Boolean.TRUE.equals(e.getActive()))
            .findFirst()
            .orElse(null);

        auditLogService.log(
            AuditHelper.createAdminAudit(
                    context,
                    EventType.ENROLLMENT_CREATED,
                    AdminAuditConstants.ENROLLMENT_CREATION_FAILED)
                .eventStatus(EventStatus.FAILURE)
                .integrationId(request.integrationId())
                .enrollmentId(existing != null ? existing.getEnrollmentId() : null)
                .errorMessage(e.getMessage())
                .eventDetails(
                    "Enrollment creation rejected: Active VERIFIED enrollment exists. "
                    + "Existing enrollment ID: " + (existing != null ? existing.getEnrollmentId() : "unknown") + ", "
                    + "Requested name: " + request.name())
                .build());
    } else {
        // Standard validation failure logging (existing code)
        auditLogService.log(...);
    }
    throw e;
}
```

**For successful creation when inactive VERIFIED exists**:

```java
// After successful enrollment creation, check if inactive VERIFIED exists
List<Enrollment> inactiveVerified = enrollmentRepository
    .findByIntegrationIdAndEnrollmentNameAndStatus(
        response.getEnrollmentId(), // Use integrationId from request
        request.name(),
        EnrollmentStatus.VERIFIED)
    .stream()
    .filter(e -> Boolean.FALSE.equals(e.getActive()))
    .toList();

if (!inactiveVerified.isEmpty()) {
    // Enhance success audit log with context
    auditLogService.log(
        AuditHelper.createAdminAudit(
                context, EventType.ENROLLMENT_CREATED, AdminAuditConstants.ENROLLMENT_CREATED)
            .eventStatus(EventStatus.SUCCESS)
            .enrollmentId(response.getEnrollmentId())
            .integrationId(request.integrationId())
            .eventDetails(
                "Enrollment name: " + request.name() + ". "
                + "Replacing inactive VERIFIED enrollment (ID: " + inactiveVerified.get(0).getEnrollmentId() + ")")
            .build());
} else {
    // Standard success logging (existing code)
    auditLogService.log(...);
}
```

**Note**: This validation provides early feedback and prevents unnecessary enrollment creation attempts. The database constraint will also prevent creating a second VERIFIED enrollment, but this catches the issue earlier with better error messages.

### 4. Service Layer - Enrollment Verification

**File**: `ezkey-core/src/main/java/org/ezkey/enrollment/service/EnrollmentVerifyService.java`

**Method**: `markAsVerified(Enrollment enrollment, EnrollmentVerifyRequest request)`

Add validation before marking as verified:

1. Find all VERIFIED enrollments with same (integration_id, enrollment_name) excluding current enrollment
2. If any found, throw `IllegalStateException` with clear error message directing user to recovery process
3. Log the rejection for audit trail
4. Only proceed with verification if no existing VERIFIED enrollment found

**Implementation**:

```java
// Validation: Check for existing VERIFIED enrollment with same name
List<Enrollment> existingVerifiedEnrollments =
    enrollmentRepository.findByIntegrationIdAndEnrollmentNameAndStatusAndEnrollmentIdNot(
        enrollment.getIntegrationId(),
        enrollment.getEnrollmentName(),
        EnrollmentStatus.VERIFIED,
        enrollment.getEnrollmentId());

if (!existingVerifiedEnrollments.isEmpty()) {
    Enrollment existing = existingVerifiedEnrollments.get(0);
    logger.warn(
        "Enrollment verification rejected: VERIFIED enrollment {} (ID: {}) already exists for integration {} and name '{}'. "
        + "Use recovery process (/api/v1/admin/auth/recover + /api/v1/admin/enrollments/reset) to replace enrollment.",
        existing.getEnrollmentName(),
        existing.getEnrollmentId(),
        enrollment.getIntegrationId(),
        enrollment.getEnrollmentName());
    throw new IllegalStateException(
        "A verified enrollment with the same name already exists for this integration. "
        + "To replace an enrollment, use the recovery process: POST /api/v1/admin/auth/recover with a recovery code, "
        + "then POST /api/v1/admin/enrollments/reset to reset the existing enrollment.");
}
```

**Audit Logging**:

The validation exception will be caught by `EnrollmentController.verify()` (auth-api) which already logs FAILURE events. We need to enhance the audit log to include the existing enrollment ID:

```java
// In EnrollmentController.verify() catch block:
catch (IllegalStateException e) {
    // Check if error is about existing VERIFIED enrollment
    if (e.getMessage().contains("verified enrollment with the same name")) {
        // Query to find existing enrollment for audit purposes
        Enrollment existing = enrollmentRepository
            .findByIntegrationIdAndEnrollmentNameAndStatusAndEnrollmentIdNot(
                enrollment.getIntegrationId(),
                enrollment.getEnrollmentName(),
                EnrollmentStatus.VERIFIED,
                req.enrollmentId())
            .stream()
            .findFirst()
            .orElse(null);

        auditLogService.log(
            AuditLog.builder()
                .eventType(EventType.ENROLLMENT_VERIFY)
                .eventAction("enrollment_verify_failed")
                .eventStatus(EventStatus.FAILURE)
                .apiName(ApiName.AUTH_API)
                .ipAddress(clientIp)
                .userAgent(userAgent)
                .enrollmentId(req.enrollmentId())
                .errorMessage(e.getMessage())
                .eventDetails(
                    "Verification rejected: VERIFIED enrollment already exists. "
                    + "Attempted enrollment ID: " + req.enrollmentId() + ", "
                    + "Existing enrollment ID: " + (existing != null ? existing.getEnrollmentId() : "unknown") + ", "
                    + "Enrollment name: " + enrollment.getEnrollmentName())
                .build());
    } else {
        // Standard failure logging (existing code)
        auditLogService.log(...);
    }
    throw e;
}
```

**Rationale**:

- **Security**: INVALID status means "verification failed or enrollment is compromised" - not appropriate for automatic replacement
- **Explicit Process**: Replacement must go through recovery process with recovery codes (security requirement)
- **Audit Trail**: Clear rejection message provides audit trail
- **Consistency**: Aligns with `EnrollmentTxHelper.markInvalidAndClear()` which protects VERIFIED enrollments
- **User Guidance**: Error message directs users to the correct replacement process

### 5. Database Index for Performance

**File**: Same migration file as constraint

Add index to optimize the validation query:

```sql
-- Index to optimize queries for finding VERIFIED enrollments by integration and name
CREATE INDEX idx_enrollment_integration_name_status
ON ezkey_enrollment(integration_id, enrollment_name, enrollment_status)
WHERE enrollment_status = 'VERIFIED';
```

### 6. Testing

**Unit Tests**:

- Test enrollment creation with active VERIFIED enrollment (should fail at creation with clear error)
- Test enrollment creation with inactive VERIFIED enrollment (should succeed - replacement allowed)
- Test enrollment creation with duplicate CREATED enrollment (should succeed)
- Test verification rejection when VERIFIED enrollment exists (should throw IllegalStateException with clear message)
- Test multiple CREATED enrollments allowed
- Test that existing VERIFIED enrollment is not modified (preserved as-is)

**Integration Tests**:

- Test complete flow: create → verify → create another → verify (second verification rejected)
- Test database constraint enforcement (cannot create second VERIFIED enrollment)
- Test that inactive VERIFIED enrollments also prevent new verification (active flag doesn't affect uniqueness)
- Test error message directs user to recovery process

## Business Rules Summary

1. **Multiple CREATED enrollments allowed** - Users can retry enrollment creation
2. **Only one VERIFIED enrollment per (integration_id, enrollment_name)** - Enforced by database constraint
3. **Creation validation** - Cannot create enrollment if active VERIFIED enrollment exists (must use recovery process)
4. **Replacement when inactive** - Can create enrollment if VERIFIED enrollment exists but is inactive (admin has deactivated it)
5. **Verification rejection** - If a VERIFIED enrollment exists, new verification is rejected with clear error message
6. **Replacement through recovery process** - Users must use recovery codes (`/auth/recover` + `/enrollments/reset`) to replace active enrollments
7. **Based on enrollment_status, not enrollment_active** - The active flag is for administrative control, not lifecycle uniqueness
8. **No automatic modification of VERIFIED enrollments** - Existing VERIFIED enrollments are preserved (aligned with `EnrollmentTxHelper` protection)

## Migration Strategy

1. **No data cleanup required** - Project is in active development with no production deployment
2. **Backward compatible** - All existing APIs continue to work
3. **Constraint added directly** - Migration simply adds the unique index (existing duplicates will be resolved naturally through recovery process)

## Files to Modify

1. `ezkey-core/src/main/resources/db/migration/V28__enrollment_unique_verified_name.sql` (new)

                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                - Partial unique index on (integration_id, enrollment_name) WHERE enrollment_status = 'VERIFIED'
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                - Performance index for validation queries

2. `ezkey-core/src/main/java/org/ezkey/enrollment/domain/repository/EnrollmentRepository.java`

                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                - Add method to find VERIFIED enrollments by integration and name

3. `ezkey-core/src/main/java/org/ezkey/enrollment/service/EnrollmentVerifyService.java`

                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                - Add validation to reject verification if VERIFIED enrollment already exists
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                - Provide clear error message directing to recovery process

4. Test files (unit and integration tests)

## Audit Logging Requirements (SOC2 Compliance)

All decision points must be logged with complete context for forensic analysis:

### 1. Enrollment Creation - Rejected (Active VERIFIED exists)

- **Event Type**: `ENROLLMENT_CREATED`
- **Event Status**: `FAILURE`
- **Event Action**: `enrollment_creation_failed`
- **Required Fields**:
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                - `integrationId`: Integration ID from request
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                - `enrollmentId`: ID of existing VERIFIED enrollment (if found)
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                - `errorMessage`: Full error message explaining rejection
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                - `eventDetails`: Context including existing enrollment ID, requested name, reason for rejection

### 2. Enrollment Creation - Allowed (Inactive VERIFIED exists)

- **Event Type**: `ENROLLMENT_CREATED`
- **Event Status**: `SUCCESS`
- **Event Action**: `enrollment_created`
- **Required Fields**:
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                - `enrollmentId`: ID of newly created enrollment
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                - `integrationId`: Integration ID
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                - `eventDetails`: Must include context that inactive VERIFIED enrollment exists and is being replaced (include existing enrollment ID)

### 3. Enrollment Creation - Normal (No VERIFIED exists)

- **Event Type**: `ENROLLMENT_CREATED`
- **Event Status**: `SUCCESS`
- **Event Action**: `enrollment_created`
- **Required Fields**: Standard logging (already implemented)

### 4. Enrollment Verification - Rejected (VERIFIED exists)

- **Event Type**: `ENROLLMENT_VERIFY`
- **Event Status**: `FAILURE`
- **Event Action**: `enrollment_verify_failed`
- **Required Fields**:
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                - `enrollmentId`: ID of enrollment attempting verification
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                - `errorMessage`: Full error message explaining rejection
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                - `eventDetails`: Context including existing VERIFIED enrollment ID, enrollment name, reason for rejection

### 5. Database Constraint Violation

- **Event Type**: `ENROLLMENT_CREATED` or `ENROLLMENT_VERIFY` (depending on operation)
- **Event Status**: `FAILURE`
- **Event Action**: `enrollment_creation_failed` or `enrollment_verify_failed`
- **Required Fields**: Standard constraint violation logging (already implemented)

**Audit Log Quality Standards**:

- **Precise**: Each log entry clearly identifies the decision point and reason
- **Concise**: Essential information only, no redundant data
- **Complete**: All relevant entity IDs included for traceability
- **Actionable**: Error messages direct users to correct resolution process
- **Immutable**: Audit logs are never modified after creation

## Considerations

- **Security First**: No automatic modification of VERIFIED enrollments (aligned with `EnrollmentTxHelper` protection)
- **Early Detection**: Validation at creation time prevents unnecessary enrollment creation attempts
- **Explicit Process**: Replacement of active enrollments must go through recovery process with recovery codes
- **Admin Flexibility**: Inactive enrollments can be replaced directly (admin has explicitly deactivated them)
- **Error Messages**: Clear messages when creation/verification fails, directing users to recovery process
- **Performance**: Index ensures efficient queries for validation logic
- **Audit Trail**: All decision points logged with complete context for SOC2 compliance
- **Administrative Control**: The `enrollment_active` flag is used to determine if replacement is allowed
- **INVALID Status**: Reserved for actual security issues (verification failed or compromised), not for replacement scenarios