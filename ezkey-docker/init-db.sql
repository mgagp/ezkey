-- Ezkey Database Initialization Script
-- This script is executed when the PostgreSQL container starts for the first time

-- Ensure the database exists (though it should be created by POSTGRES_DB env var)
SELECT 'CREATE DATABASE ezkey_db'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'ezkey_db');

-- Create a dedicated user for the application (optional, but good practice)
DO $$
BEGIN
    IF NOT EXISTS (SELECT FROM pg_catalog.pg_user WHERE usename = 'ezkey_app') THEN
        CREATE USER ezkey_app WITH PASSWORD 'ezkey_app_pwd';
    END IF;
END
$$;

-- Grant necessary permissions
GRANT CONNECT ON DATABASE ezkey_db TO ezkey_app;
GRANT USAGE ON SCHEMA public TO ezkey_app;
GRANT CREATE ON SCHEMA public TO ezkey_app;

-- Note: Flyway migrations will handle the actual schema creation
-- This script only ensures the database and basic permissions are set up