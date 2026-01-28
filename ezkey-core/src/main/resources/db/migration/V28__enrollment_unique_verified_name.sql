-- Ezkey Migration V28: Enrollment Uniqueness Constraint for VERIFIED Status
-- Purpose: Enforce uniqueness constraint on (integration_id, enrollment_name) for VERIFIED enrollments
--          while allowing multiple CREATED enrollments for retry scenarios.
--          The enrollment_active flag is separate and used for administrative control.

-- ============================================================================
-- STEP 1: Create Partial Unique Index
-- ============================================================================

-- Partial unique index: only one VERIFIED enrollment per integration+name
-- This allows multiple CREATED, BOUND, and INVALID enrollments while preventing duplicate VERIFIED ones
CREATE UNIQUE INDEX idx_enrollment_unique_verified_name
ON ezkey_enrollment(integration_id, enrollment_name)
WHERE enrollment_status = 'VERIFIED';

COMMENT ON INDEX idx_enrollment_unique_verified_name IS
'Ensures only one VERIFIED enrollment exists per integration and enrollment name. Allows multiple CREATED enrollments for retry scenarios. The enrollment_active flag is separate and used for administrative control.';

-- ============================================================================
-- STEP 2: Create Performance Index
-- ============================================================================

-- Index to optimize queries for finding VERIFIED enrollments by integration and name
-- Used for validation logic during enrollment creation and verification
CREATE INDEX idx_enrollment_integration_name_status
ON ezkey_enrollment(integration_id, enrollment_name, enrollment_status)
WHERE enrollment_status = 'VERIFIED';

COMMENT ON INDEX idx_enrollment_integration_name_status IS
'Performance index for validation queries that check for existing VERIFIED enrollments by integration and name. Used during enrollment creation and verification to enforce uniqueness constraint.';
