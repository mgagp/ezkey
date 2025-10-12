-- Migration V2: Ajout du support multi-tenant et sécurité
-- Cette migration ajoute les nouvelles tables sans modifier les existantes

-- Table des tenants
CREATE TABLE ezkey_tenant (
    tenant_id INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_name VARCHAR(100) NOT NULL UNIQUE,
    tenant_description TEXT,
    created_by_admin_id INT, -- Sera ajouté après création de ezkey_admin
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP NOT NULL,
    active BOOLEAN DEFAULT TRUE NOT NULL
);

-- Table des administrateurs
CREATE TABLE ezkey_admin (
    admin_id INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    admin_type VARCHAR(20) NOT NULL CHECK (admin_type IN ('GLOBAL_ADMIN', 'TENANT_ADMIN', 'INTEGRATION_ADMIN')),
    tenant_id INT REFERENCES ezkey_tenant(tenant_id),
    integration_id INT REFERENCES ezkey_integration(integration_id),
    mfa_enabled BOOLEAN DEFAULT TRUE NOT NULL,
    mfa_required BOOLEAN DEFAULT TRUE NOT NULL,
    mfa_enrollment_id INT REFERENCES ezkey_enrollment(enrollment_id),
    password_change_required BOOLEAN DEFAULT FALSE NOT NULL,
    created_by_admin_id INT REFERENCES ezkey_admin(admin_id),
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP NOT NULL,
    last_login_at TIMESTAMPTZ,
    last_password_change TIMESTAMPTZ,
    active BOOLEAN DEFAULT TRUE NOT NULL,
    
    -- Contraintes de hiérarchie
    CONSTRAINT check_admin_hierarchy CHECK (
        (admin_type = 'GLOBAL_ADMIN' AND tenant_id IS NULL AND integration_id IS NULL) OR
        (admin_type = 'TENANT_ADMIN' AND tenant_id IS NOT NULL AND integration_id IS NULL) OR
        (admin_type = 'INTEGRATION_ADMIN' AND tenant_id IS NOT NULL AND integration_id IS NOT NULL)
    )
);

-- Table des tokens d'administration
CREATE TABLE ezkey_admin_tokens (
    token_id INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    bearer_token VARCHAR(255) NOT NULL UNIQUE,
    admin_id INT NOT NULL REFERENCES ezkey_admin(admin_id),
    admin_type VARCHAR(20) NOT NULL,
    tenant_id INT REFERENCES ezkey_tenant(tenant_id),
    integration_id INT REFERENCES ezkey_integration(integration_id),
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    last_used_at TIMESTAMPTZ,
    ip_address VARCHAR(45),
    user_agent TEXT,
    active BOOLEAN DEFAULT TRUE NOT NULL
);

-- Table des tokens temporaires
CREATE TABLE ezkey_admin_temp_tokens (
    temp_token_id INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    temp_token VARCHAR(255) NOT NULL UNIQUE,
    admin_id INT NOT NULL REFERENCES ezkey_admin(admin_id),
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    mfa_required BOOLEAN DEFAULT TRUE NOT NULL,
    active BOOLEAN DEFAULT TRUE NOT NULL
);

-- Ajouter les colonnes tenant aux tables existantes
ALTER TABLE ezkey_integration ADD COLUMN tenant_id INT REFERENCES ezkey_tenant(tenant_id);
ALTER TABLE ezkey_integration ADD COLUMN is_system_integration BOOLEAN DEFAULT FALSE;
ALTER TABLE ezkey_integration ADD COLUMN created_by_admin_id INT REFERENCES ezkey_admin(admin_id);

-- Index pour la performance
CREATE INDEX idx_admin_tokens_active ON ezkey_admin_tokens(bearer_token, active) WHERE active = TRUE;
CREATE INDEX idx_admin_tokens_expired ON ezkey_admin_tokens(expires_at) WHERE active = TRUE;
CREATE INDEX idx_admin_temp_tokens_expired ON ezkey_admin_temp_tokens(expires_at) WHERE active = TRUE;
CREATE INDEX idx_admin_username ON ezkey_admin(username) WHERE active = TRUE;
CREATE INDEX idx_admin_type ON ezkey_admin(admin_type) WHERE active = TRUE;
CREATE INDEX idx_tenant_name ON ezkey_tenant(tenant_name) WHERE active = TRUE;
