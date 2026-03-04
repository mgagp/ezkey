-- Add EXPIRED to enrollment_status check constraint.
-- V41 introduced expires_at and the code marks expired enrollments as EXPIRED, but V1's
-- check constraint only allowed CREATED, BOUND, VERIFIED, INVALID, REVOKED.
-- This caused constraint violations when EnrollmentBindService/EnrollmentVerifyService
-- tried to mark expired enrollments via EnrollmentTxHelper.markExpiredAndEmitAudit().

ALTER TABLE ezkey_enrollment
    DROP CONSTRAINT ezkey_enrollment_enrollment_status_check;

ALTER TABLE ezkey_enrollment
    ADD CONSTRAINT ezkey_enrollment_enrollment_status_check
    CHECK (enrollment_status IN ('CREATED', 'BOUND', 'VERIFIED', 'INVALID', 'REVOKED', 'EXPIRED'));
