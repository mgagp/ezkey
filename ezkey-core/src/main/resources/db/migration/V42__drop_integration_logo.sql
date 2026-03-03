-- Phase 1: Remove integration logo column (unused; flatten plan).
-- Logo is not consumed meaningfully; model is simplified for flat integration (name/description only).
ALTER TABLE ezkey_integration DROP COLUMN IF EXISTS integration_logo;
