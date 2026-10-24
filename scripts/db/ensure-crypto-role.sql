-- Ensure ezkey_crypto LOGIN role exists (ADR-0016 / #961 Patrick B1).
-- Invoked from apply-grants.sh BEFORE apply-grants.sql on upgrade paths where
-- docker-entrypoint-initdb.d does not re-run (existing Postgres volume from main).
--
-- Password is supplied by the wrapper via:
--   SELECT set_config('ezkey.crypto_password', :<password>, false);
-- Never embed a default password in this file.

DO $$
DECLARE
  crypto_password text := current_setting('ezkey.crypto_password', true);
BEGIN
  IF crypto_password IS NULL OR length(crypto_password) = 0 THEN
    RAISE EXCEPTION
      'ezkey.crypto_password is not set — refuse to create ezkey_crypto without an explicit password (no default on grants/upgrade path)';
  END IF;

  IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'ezkey_crypto') THEN
    EXECUTE format('CREATE ROLE ezkey_crypto LOGIN PASSWORD %L', crypto_password);
    RAISE NOTICE 'Created LOGIN role ezkey_crypto (upgrade path)';
  ELSE
    RAISE NOTICE 'LOGIN role ezkey_crypto already present — leaving password unchanged';
  END IF;

  EXECUTE format(
    'GRANT CONNECT ON DATABASE %I TO ezkey_crypto',
    current_database());
END
$$;

GRANT USAGE ON SCHEMA public TO ezkey_crypto;
