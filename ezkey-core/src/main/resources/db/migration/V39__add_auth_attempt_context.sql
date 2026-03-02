-- V39: Add contextual authentication fields to ezkey_auth_attempt
--
-- Extends authentication attempts with two optional plain-text context fields so the approver's
-- mobile device can display exactly what they are being asked to authorize.
--
-- Both columns are nullable to preserve full backward compatibility — existing auth attempts
-- and integrations that omit context are completely unaffected.

ALTER TABLE ezkey_auth_attempt
    ADD COLUMN context_title   VARCHAR(200),
    ADD COLUMN context_message VARCHAR(2000);

COMMENT ON COLUMN ezkey_auth_attempt.context_title   IS 'Optional short heading displayed on the mobile approval card (max 200 chars, e.g. "Payment Authorization")';
COMMENT ON COLUMN ezkey_auth_attempt.context_message IS 'Optional descriptive body explaining what the approver is authorizing (max 2000 chars, e.g. "Authorize payment of $5,000 to Suppliers Ltd.")';
