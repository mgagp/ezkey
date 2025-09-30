-- Migration V3: Création de l'administrateur initial
-- Cette migration crée l'administrateur "zero" avec un mot de passe placeholder
-- Le mot de passe sera mis à jour au premier démarrage de l'application

-- Créer le tenant par défaut pour l'administrateur global
INSERT INTO ezkey_tenant (tenant_name, tenant_description, created_at, active) 
VALUES ('Ezkey System', 'Default system tenant for global administrators', CURRENT_TIMESTAMP, true);

-- Créer l'administrateur global initial avec mot de passe placeholder
-- Mot de passe: "defined at first execution" (hash BCrypt: $2a$10$placeholder...)
-- IMPORTANT: GLOBAL_ADMIN ne doit PAS avoir de tenant_id (contrainte check_admin_hierarchy)
INSERT INTO ezkey_admin (
    username, 
    password_hash, 
    admin_type, 
    tenant_id, 
    integration_id,
    mfa_enabled, 
    mfa_required, 
    password_change_required, 
    created_at, 
    active
) VALUES (
    'admin', 
    '$2a$10$placeholder.defined.at.first.execution', 
    'GLOBAL_ADMIN', 
    NULL,  -- GLOBAL_ADMIN ne doit pas avoir de tenant_id
    NULL,  -- GLOBAL_ADMIN ne doit pas avoir d'integration_id
    true, 
    true, 
    true, 
    CURRENT_TIMESTAMP, 
    true
);

-- Mettre à jour le tenant avec l'admin créateur
UPDATE ezkey_tenant 
SET created_by_admin_id = (SELECT admin_id FROM ezkey_admin WHERE username = 'admin')
WHERE tenant_name = 'Ezkey System';