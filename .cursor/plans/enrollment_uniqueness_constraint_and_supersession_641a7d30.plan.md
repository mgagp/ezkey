---
name: Enrollment uniqueness constraint and supersession
overview: Implement uniqueness constraint and supersession logic for enrollments to prevent duplicate active enrollments with the same name for the same integration, while allowing multiple CREATED enrollments for retry scenarios.
todos:
  - id: db-constraint
    content: Create database migration with partial unique index for active enrollments (integration_id, enrollment_name) WHERE active=true
    status: completed
  - id: db-index
    content: Add performance index for supersession queries (integration_id, enrollment_name, active)
    status: completed
  - id: repository-method
    content: Add repository method to find active enrollments with same integration and name
    status: in_progress
  - id: service-validation
    content: Add validation in EnrollmentService.create() to check for existing active enrollments
    status: pending
  - id: verify-supersession
    content: Implement supersession logic in EnrollmentVerifyService.markAsVerified() to deactivate older active enrollments
    status: pending
  - id: unit-tests
    content: Write unit tests for uniqueness validation and supersession logic
    status: completed
  - id: integration-tests
    content: Write integration tests for complete enrollment flow with supersession
    status: completed
isProject: false
---

# Enrollment Uniqueness Constraint and Supersession Implementation

## Problem Analysis

Currently, the system allows creating multiple enrollments with the same `enrollment_name` for the same `integration_id`. This creates confusion and potential security issues. The user has identified 5 enrollments with the same name, all in VERIFIED status but none active.

## Proposed Solution

Following the pragmatic approach similar to the **Supersede Session** pattern used for authentication attempts, we will:

1. **Allow multiple CREATED enrollments** - Users can retry enrollment creation
2. **Enforce uniqueness for active enrollments** - Only one active enrollment per (integration_id, enrollment_name)
3. **Implement supersession on verification** - When a new enrollment is verified, automatically deactivate older active enrollments with the same name

## Implementation Details

### 1. Database Constraint

**File**: New migration file (e.g., `V28__enrollment_unique_active_name.sql`)

Add a partial unique index to enforce uniqueness only for active enrollments:

```sql
-- Partial unique index: only one active enrollment per integration+name
CREATE UNIQUE INDEX idx_enrollment_unique_active_name
ON ezkey_enrollment(integration_id, enrollment_name)
WHERE enrollment_active = TRUE;

COMMENT ON INDEX idx_enrollment_unique_active_name IS
'Ensures only one active enrollment exists per integration and enrollment name. Allows multiple CREATED enrollments for retry scenarios.';
```

**Rationale**:

- Uses partial index (WHERE clause) to allow multiple inactive enrollments
- PostgreSQL partial unique indexes exclude NULL and FALSE values from uniqueness constraint
- Allows multiple CREATED enrollments while preventing duplicate active ones

### 2. Repository Method

**File**: `ezkey-core/src/main/java/org/ezkey/enrollment/domain/repository/EnrollmentRepository.java`

Add method to find active enrollments with same name:

```java
/**
 * Finds active enrollments with the same integration and name.
 * Used for supersession logic during enrollment verification.
 *
 * @param integrationId the integration ID
 * @param enrollmentName the enrollment name
 * @param excludeEnrollmentId enrollment ID to exclude from results (current enrollment)
 * @return list of active enrollments with same integration and name
 */
List<Enrollment> findByIntegrationIdAndEnrollmentNameAndActiveTrueAndEnrollmentIdNot(
    Integer integrationId, String enrollmentName, Integer excludeEnrollmentId);
```

### 3. Service Layer - Enrollment Creation

**File**: `ezkey-core/src/main/java/org/ezkey/enrollment/service/EnrollmentService.java`

**Method**: `create(EnrollmentCreateRequest request)`

Add validation before creating enrollment:

- Check if an active enrollment already exists with same (integration_id, enrollment_name)
- If found, throw `IllegalArgumentException` with clear message
- This provides early feedback to users

**Note**: The database constraint will also catch this, but service-level validation provides better error messages.

### 4. Service Layer - Enrollment Verification

**File**: `ezkey-core/src/main/java/org/ezkey/enrollment/service/EnrollmentVerifyService.java`

**Method**: `markAsVerified(Enrollment enrollment, EnrollmentVerifyRequest request)`

Add supersession logic before marking as verified:

1. Find all active enrollments with same (integration_id, enrollment_name) excluding current enrollment
2. If any found, deactivate them (set `active = false`)
3. Log supersession events for audit trail
4. Then proceed with normal verification (set status VERIFIED, active = true)

**Implementation pattern** (similar to AuthAttemptService):

```java
// Supersession: Deactivate existing active enrollments with same name
List<Enrollment> existingActiveEnrollments =
    enrollmentRepository.findByIntegrationIdAndEnrollmentNameAndActiveTrueAndEnrollmentIdNot(
        enrollment.getIntegrationId(),
        enrollment.getEnrollmentName(),
        enrollment.getEnrollmentId());

if (!existingActiveEnrollments.isEmpty()) {
    for (Enrollment existing : existingActiveEnrollments) {
        existing.setActive(false);
        enrollmentRepository.save(existing);
        logger.info(
            "Supersession: Deactivated enrollment {} (ID: {}) - superseded by enrollment {}",
            existing.getEnrollmentName(),
            existing.getEnrollmentId(),
            enrollment.getEnrollmentId());
    }
}
```

### 5. Database Index for Performance

**File**: Same migration file as constraint

Add index to optimize the supersession query:

```sql
-- Index to optimize queries for finding active enrollments by integration and name
CREATE INDEX idx_enrollment_integration_name_active
ON ezkey_enrollment(integration_id, enrollment_name, enrollment_active)
WHERE enrollment_active = TRUE;
```

### 6. Testing

**Unit Tests**:

- Test enrollment creation with duplicate active enrollment (should fail)
- Test enrollment creation with duplicate inactive enrollment (should succeed)
- Test verification supersession (older active enrollment deactivated)
- Test multiple CREATED enrollments allowed

**Integration Tests**:

- Test complete flow: create → verify → create another → verify (supersession)
- Test database constraint enforcement

## Business Rules Summary

1. **Multiple CREATED enrollments allowed** - Users can retry enrollment creation
2. **Only one active enrollment per (integration_id, enrollment_name)** - Enforced by database constraint
3. **Automatic supersession on verification** - Newer enrollment automatically deactivates older ones
4. **Based on created_at** - Similar to AuthAttempt supersession pattern

## Migration Strategy

1. **No data migration required** - Existing data remains valid
2. **Handle existing duplicates** - Before adding constraint, may need to deactivate duplicates (optional cleanup script)
3. **Backward compatible** - All existing APIs continue to work

## Files to Modify

1. `ezkey-core/src/main/resources/db/migration/V28__enrollment_unique_active_name.sql` (new)
2. `ezkey-core/src/main/java/org/ezkey/enrollment/domain/repository/EnrollmentRepository.java`
3. `ezkey-core/src/main/java/org/ezkey/enrollment/service/EnrollmentService.java`
4. `ezkey-core/src/main/java/org/ezkey/enrollment/service/EnrollmentVerifyService.java`
5. Test files (unit and integration tests)

## Considerations

- **Audit Trail**: Supersession events should be logged for monitoring
- **Error Messages**: Clear messages when creation fails due to existing active enrollment
- **Performance**: Index ensures efficient queries for supersession logic
- **Simplicity**: Solution is pragmatic and follows existing patterns (Supersede Session)

