-- ============================================================================
-- Ezkey Migration V31: Enrollment Contact and Audit Columns (Phase 1 & 2)
-- ============================================================================
-- Description: Adds columns for normative (SOC 2), operational, and contact
--              purposes as per enrollment enhancement plan.
--
-- Phase 1 (Normative + Operational):
--   - verified_at: When enrollment transitioned to VERIFIED
--   - created_by_admin_id: Admin who created (null when via API key)
--   - last_used_at: Last successful authentication
--
-- Phase 2 (Contact, optional):
--   - contact_email: Optional end-user contact for incident response
--   - user_identifier: Optional app user reference (unique per integration lookup)
--
-- Author: Ezkey contributors
-- Date: 2026-02
-- ============================================================================

-- ============================================================================
-- STEP 1: Add Phase 1 Columns
-- ============================================================================

ALTER TABLE ezkey_enrollment
    ADD COLUMN verified_at TIMESTAMPTZ;

COMMENT ON COLUMN ezkey_enrollment.verified_at IS
'When the enrollment transitioned to VERIFIED status (device completed binding). Used for audit trail and lifecycle metrics. Set once when status becomes VERIFIED.';

ALTER TABLE ezkey_enrollment
    ADD COLUMN created_by_admin_id INT REFERENCES ezkey_admin(admin_id);

COMMENT ON COLUMN ezkey_enrollment.created_by_admin_id IS
'Admin who created this enrollment (SOC 2 CC6.1, CC7.2). Populated when created via Admin API; null when created via API key (M2M).';

ALTER TABLE ezkey_enrollment
    ADD COLUMN last_used_at TIMESTAMPTZ;

COMMENT ON COLUMN ezkey_enrollment.last_used_at IS
'When this enrollment was last used for successful authentication. Updated on each accepted auth attempt. Used for operational hygiene and stale enrollment detection.';

-- ============================================================================
-- STEP 2: Add Phase 2 Columns
-- ============================================================================

ALTER TABLE ezkey_enrollment
    ADD COLUMN contact_email VARCHAR(255);

COMMENT ON COLUMN ezkey_enrollment.contact_email IS
'Optional contact for the end-user (device owner). Used for incident response, revocation notices, support. Provided by integrating app at creation.';

ALTER TABLE ezkey_enrollment
    ADD COLUMN user_identifier VARCHAR(255);

COMMENT ON COLUMN ezkey_enrollment.user_identifier IS
'Optional reference to integrating app user (username, user_id). Unique per integration for lookup. Enables future auth attempt creation by userIdentifier.';

-- ============================================================================
-- STEP 3: Create Lookup Index for user_identifier
-- ============================================================================

CREATE INDEX idx_enrollment_integration_user_identifier
    ON ezkey_enrollment(integration_id, user_identifier)
    WHERE user_identifier IS NOT NULL;

COMMENT ON INDEX idx_enrollment_integration_user_identifier IS
'Non-unique index for lookup by (integration_id, user_identifier). Supports single- and multi-device.';

-- ============================================================================
-- Migration Complete
-- ============================================================================
