-- Flatten integration i18n: add integration_name and integration_description to ezkey_integration,
-- backfill from ezkey_integration_i18n, then drop the i18n table.

-- Add new columns (nullable for backfill)
ALTER TABLE ezkey_integration
  ADD COLUMN integration_name VARCHAR(255) NULL,
  ADD COLUMN integration_description VARCHAR(500) NULL;

-- Backfill from first i18n entry per integration (clean start, simple approach)
UPDATE ezkey_integration ei
SET
  integration_name = (
    SELECT integration_i18n_name
    FROM ezkey_integration_i18n
    WHERE integration_id = ei.integration_id
    LIMIT 1
  ),
  integration_description = (
    SELECT integration_i18n_description
    FROM ezkey_integration_i18n
    WHERE integration_id = ei.integration_id
    LIMIT 1
  )
WHERE EXISTS (
  SELECT 1 FROM ezkey_integration_i18n WHERE integration_id = ei.integration_id
);

-- Force system integration name when null
UPDATE ezkey_integration
SET integration_name = 'Ezkey System'
WHERE is_system_integration = TRUE AND integration_name IS NULL;

-- Drop the i18n table
DROP TABLE ezkey_integration_i18n;
