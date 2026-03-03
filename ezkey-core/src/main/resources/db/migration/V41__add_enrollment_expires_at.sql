-- V41: Add optional expiration for pending enrollments
--
-- Optional expires_at on ezkey_enrollment. When set, bind/verify must complete before this time;
-- after that the enrollment is treated as expired (status EXPIRED). Null means no expiration.
-- Set at creation when policy is enabled (e.g. created_at + 30 days). Aligns with Duo/Okta
-- enrollment link validity and NIST lifecycle "expiration" traceability.

ALTER TABLE ezkey_enrollment
    ADD COLUMN expires_at TIMESTAMPTZ;

COMMENT ON COLUMN ezkey_enrollment.expires_at IS
    'Optional expiration for pending enrollment (CREATED/BOUND). When set, bind/verify must complete before this time; null means no expiration.';

-- Index for scheduled job: find CREATED enrollments with expires_at < now
CREATE INDEX idx_enrollment_expires_at_created
    ON ezkey_enrollment(expires_at)
    WHERE enrollment_status = 'CREATED' AND expires_at IS NOT NULL;
