# Plan de Tests Multi-Tenancy - Phase 1

## Vue d'ensemble

Ce document définit un plan de tests complet pour valider la fonctionnalité multi-tenancy Phase 1. Les tests sont organisés par catégorie et peuvent être exécutés avec Postman et des validations directes dans la base de données.

**Objectifs:**
- Valider l'isolation complète entre tenants
- Vérifier les règles d'accès par type d'administrateur
- Confirmer l'application des limites (max/min admins)
- Tester les scénarios de déactivation
- Valider l'audit et la traçabilité

**Prérequis:**
- Stack Docker en cours d'exécution
- Token GlobalAdmin valide (bootstrap)
- Collections Postman configurées
- Accès à la base de données PostgreSQL

---

## 1. Tests de Non-Régression - Isolation Tenant

### 1.1 Test: TenantAdmin ne peut pas accéder aux ressources d'un autre tenant

**Objectif:** Vérifier que les TenantAdmins sont strictement isolés par tenant.

**Setup:**
1. Créer Tenant A (tenantId: 2)
2. Créer Tenant B (tenantId: 3)
3. Créer TenantAdmin A (tenantId: 2)
4. Créer TenantAdmin B (tenantId: 3)
5. Créer Integration A dans Tenant A (integrationId: X)
6. Créer Integration B dans Tenant B (integrationId: Y)

**Tests Postman:**

```http
# Test 1.1.1: TenantAdmin A ne peut pas lister les intégrations de Tenant B
GET /api/v1/integrations
Authorization: Bearer {tenantAdminA_token}
Expected: 200 OK, liste ne contient QUE les intégrations de Tenant A

# Test 1.1.2: TenantAdmin A ne peut pas accéder à Integration B
GET /api/v1/integrations/{integrationId_B}
Authorization: Bearer {tenantAdminA_token}
Expected: 403 Forbidden ou 404 Not Found

# Test 1.1.3: TenantAdmin A ne peut pas créer d'enrollment pour Integration B
POST /api/v1/enrollments
Authorization: Bearer {tenantAdminA_token}
Body: { "integrationId": integrationId_B, ... }
Expected: 403 Forbidden

# Test 1.1.4: TenantAdmin A ne peut pas créer d'API key pour Integration B
POST /api/v1/api-keys
Authorization: Bearer {tenantAdminA_token}
Body: { "integrationId": integrationId_B, ... }
Expected: 403 Forbidden
```

**Validation DB:**
```sql
-- Vérifier que les intégrations sont bien associées aux bons tenants
SELECT integration_id, integration_name, tenant_id 
FROM ezkey_integration 
WHERE tenant_id IN (2, 3)
ORDER BY tenant_id, integration_id;

-- Vérifier que les admins sont bien associés aux bons tenants
SELECT admin_id, username, admin_type, tenant_id, active
FROM ezkey_admin
WHERE admin_type = 'TENANT_ADMIN'
ORDER BY tenant_id, admin_id;
```

**Critères de succès:**
- ✅ TenantAdmin A ne voit QUE les ressources de Tenant A
- ✅ TenantAdmin A reçoit 403/404 pour les ressources de Tenant B
- ✅ Les requêtes de création échouent avec 403 pour les ressources d'autres tenants

---

### 1.2 Test: GlobalAdmin peut accéder à toutes les ressources

**Objectif:** Vérifier que GlobalAdmin a accès système-wide.

**Setup:**
- Utiliser le GlobalAdmin du bootstrap (tenantId: 1, "Ezkey System")

**Tests Postman:**

```http
# Test 1.2.1: GlobalAdmin peut lister toutes les intégrations
GET /api/v1/integrations
Authorization: Bearer {globalAdmin_token}
Expected: 200 OK, liste contient TOUTES les intégrations (tous tenants)

# Test 1.2.2: GlobalAdmin peut accéder à n'importe quelle intégration
GET /api/v1/integrations/{integrationId_A}
Authorization: Bearer {globalAdmin_token}
Expected: 200 OK

GET /api/v1/integrations/{integrationId_B}
Authorization: Bearer {globalAdmin_token}
Expected: 200 OK

# Test 1.2.3: GlobalAdmin peut lister tous les tenants
GET /api/v1/tenants
Authorization: Bearer {globalAdmin_token}
Expected: 200 OK, liste contient tous les tenants
```

**Validation DB:**
```sql
-- Compter les intégrations par tenant
SELECT tenant_id, COUNT(*) as integration_count
FROM ezkey_integration
GROUP BY tenant_id
ORDER BY tenant_id;

-- Vérifier que GlobalAdmin peut voir toutes les intégrations
-- (pas de validation DB directe, mais vérifier que le count correspond)
```

**Critères de succès:**
- ✅ GlobalAdmin voit toutes les ressources (tous tenants)
- ✅ GlobalAdmin peut accéder à n'importe quelle ressource
- ✅ GlobalAdmin peut lister tous les tenants

---

### 1.3 Test: Création d'intégration - Attribution automatique de tenant

**Objectif:** Vérifier que les intégrations sont automatiquement assignées au bon tenant selon le type d'admin.

**Setup:**
- GlobalAdmin token
- TenantAdmin A token (tenantId: 2)

**Tests Postman:**

```http
# Test 1.3.1: GlobalAdmin crée une intégration → assignée à "Ezkey System" (tenantId: 1)
POST /api/v1/integrations
Authorization: Bearer {globalAdmin_token}
Body: {
  "integrationName": "System Integration Test",
  "integrationDescription": "Test integration created by GlobalAdmin"
}
Expected: 201 Created
Response: { "integrationId": X, "tenantId": 1, ... }

# Test 1.3.2: TenantAdmin A crée une intégration → assignée à Tenant A (tenantId: 2)
POST /api/v1/integrations
Authorization: Bearer {tenantAdminA_token}
Body: {
  "integrationName": "Tenant A Integration Test",
  "integrationDescription": "Test integration created by TenantAdmin A"
}
Expected: 201 Created
Response: { "integrationId": Y, "tenantId": 2, ... }
```

**Validation DB:**
```sql
-- Vérifier l'attribution automatique de tenant
SELECT 
    i.integration_id,
    i.integration_name,
    i.tenant_id,
    t.tenant_name,
    a.username as created_by,
    a.admin_type
FROM ezkey_integration i
JOIN ezkey_tenant t ON i.tenant_id = t.tenant_id
LEFT JOIN ezkey_admin a ON i.created_by_admin_id = a.admin_id
WHERE i.integration_name LIKE '%Test%'
ORDER BY i.integration_id;

-- Vérifier que GlobalAdmin ne peut pas créer d'intégration pour un autre tenant
-- (pas de champ tenantId dans la requête, donc pas de test direct)
```

**Critères de succès:**
- ✅ GlobalAdmin crée des intégrations dans tenant "Ezkey System" (tenantId: 1)
- ✅ TenantAdmin crée des intégrations dans son propre tenant
- ✅ Pas d'impersonation possible (pas de champ tenantId dans la requête)

---

## 2. Tests de Sécurité - Contrôle d'Accès

### 2.1 Test: API Key - Accès limité à sa propre intégration

**Objectif:** Vérifier que les API keys ne peuvent accéder qu'aux auth attempts de leur intégration.

**Setup:**
1. Créer Integration A (tenantId: 2)
2. Créer Integration B (tenantId: 2)
3. Créer Enrollment A (integrationId: A)
4. Créer Enrollment B (integrationId: B)
5. Créer API Key A (integrationId: A)
6. Créer Auth Attempt A (enrollmentId: A)
7. Créer Auth Attempt B (enrollmentId: B)

**Tests Postman:**

```http
# Test 2.1.1: API Key A peut accéder à Auth Attempt A (même intégration)
GET /api/v1/auth-attempts/{authAttemptId_A}
Authorization: Bearer {apiKeyA_token}
Expected: 200 OK

# Test 2.1.2: API Key A ne peut PAS accéder à Auth Attempt B (autre intégration)
GET /api/v1/auth-attempts/{authAttemptId_B}
Authorization: Bearer {apiKeyA_token}
Expected: 403 Forbidden

# Test 2.1.3: API Key A peut créer un auth attempt pour Enrollment A
POST /api/v1/auth-attempts
Authorization: Bearer {apiKeyA_token}
Body: { "enrollmentId": enrollmentId_A, "challengeRequired": false }
Expected: 201 Created

# Test 2.1.4: API Key A ne peut PAS créer un auth attempt pour Enrollment B
POST /api/v1/auth-attempts
Authorization: Bearer {apiKeyA_token}
Body: { "enrollmentId": enrollmentId_B, "challengeRequired": false }
Expected: 403 Forbidden
```

**Validation DB:**
```sql
-- Vérifier les associations API Key → Integration
SELECT 
    ak.api_key_id,
    ak.api_key_name,
    ak.integration_id,
    i.integration_name,
    i.tenant_id
FROM ezkey_api_key ak
JOIN ezkey_integration i ON ak.integration_id = i.integration_id
ORDER BY ak.integration_id, ak.api_key_id;

-- Vérifier les associations Auth Attempt → Enrollment → Integration
SELECT 
    aa.auth_attempt_id,
    aa.enrollment_id,
    e.integration_id,
    i.integration_name,
    i.tenant_id
FROM ezkey_auth_attempt aa
JOIN ezkey_enrollment e ON aa.enrollment_id = e.enrollment_id
JOIN ezkey_integration i ON e.integration_id = i.integration_id
ORDER BY e.integration_id, aa.auth_attempt_id;
```

**Critères de succès:**
- ✅ API Key accède uniquement aux auth attempts de sa propre intégration
- ✅ Les tentatives d'accès à d'autres intégrations retournent 403
- ✅ Les créations d'auth attempts sont validées par intégration

---

### 2.2 Test: TenantAdmin - Accès aux enrollments de son tenant

**Objectif:** Vérifier que TenantAdmin ne peut accéder qu'aux enrollments de son tenant.

**Setup:**
1. Tenant A avec Integration A
2. Tenant B avec Integration B
3. Enrollment A (integrationId: A, tenantId: 2)
4. Enrollment B (integrationId: B, tenantId: 3)
5. TenantAdmin A (tenantId: 2)

**Tests Postman:**

```http
# Test 2.2.1: TenantAdmin A peut accéder à Enrollment A (même tenant)
GET /api/v1/enrollments/{enrollmentId_A}
Authorization: Bearer {tenantAdminA_token}
Expected: 200 OK

# Test 2.2.2: TenantAdmin A ne peut PAS accéder à Enrollment B (autre tenant)
GET /api/v1/enrollments/{enrollmentId_B}
Authorization: Bearer {tenantAdminA_token}
Expected: 403 Forbidden ou 404 Not Found

# Test 2.2.3: TenantAdmin A peut lister les enrollments de son tenant
GET /api/v1/enrollments?integrationId={integrationId_A}
Authorization: Bearer {tenantAdminA_token}
Expected: 200 OK, liste ne contient QUE les enrollments de Tenant A
```

**Validation DB:**
```sql
-- Vérifier les associations Enrollment → Integration → Tenant
SELECT 
    e.enrollment_id,
    e.enrollment_name,
    e.integration_id,
    i.integration_name,
    i.tenant_id,
    t.tenant_name
FROM ezkey_enrollment e
JOIN ezkey_integration i ON e.integration_id = i.integration_id
JOIN ezkey_tenant t ON i.tenant_id = t.tenant_id
ORDER BY i.tenant_id, e.enrollment_id;
```

**Critères de succès:**
- ✅ TenantAdmin accède uniquement aux enrollments de son tenant
- ✅ Les tentatives d'accès à d'autres tenants retournent 403/404
- ✅ Les listes sont filtrées par tenant automatiquement

---

## 3. Tests de Limites - Max/Min Admins

### 3.1 Test: Limite maximale de GlobalAdmins

**Objectif:** Vérifier que la limite maximale de GlobalAdmins est respectée.

**Configuration:**
- `ezkey.security.admin.max-global-admins=3` (par défaut)

**Setup:**
- Compter les GlobalAdmins actifs existants
- Tenter de créer des GlobalAdmins jusqu'à la limite

**Tests Postman:**

```http
# Test 3.1.1: Créer GlobalAdmin jusqu'à la limite
POST /api/v1/admins/global
Authorization: Bearer {globalAdmin_token}
Body: {
  "username": "global_admin_1",
  "email": "global1@example.com",
  "firstName": "Global",
  "lastName": "Admin 1"
}
Expected: 201 Created (si sous la limite)

# Répéter jusqu'à atteindre la limite...

# Test 3.1.2: Tentative de création au-delà de la limite
POST /api/v1/admins/global
Authorization: Bearer {globalAdmin_token}
Body: {
  "username": "global_admin_4",
  "email": "global4@example.com",
  "firstName": "Global",
  "lastName": "Admin 4"
}
Expected: 400 Bad Request avec message d'erreur indiquant la limite
```

**Validation DB:**
```sql
-- Compter les GlobalAdmins actifs
SELECT 
    admin_type,
    COUNT(*) as active_count
FROM ezkey_admin
WHERE admin_type = 'GLOBAL_ADMIN' 
  AND active = true
GROUP BY admin_type;

-- Vérifier que le count ne dépasse pas la limite (3)
-- Si le count = 3, la création suivante doit échouer
```

**Critères de succès:**
- ✅ Les créations réussissent jusqu'à la limite (max-global-admins)
- ✅ La création au-delà de la limite retourne 400 Bad Request
- ✅ Le message d'erreur indique clairement la limite atteinte

---

### 3.2 Test: Limite maximale de TenantAdmins par tenant

**Objectif:** Vérifier que la limite maximale de TenantAdmins par tenant est respectée.

**Configuration:**
- `ezkey.security.admin.max-tenant-admins-per-tenant=3` (par défaut)

**Setup:**
- Tenant A (tenantId: 2)
- Compter les TenantAdmins actifs pour Tenant A
- Tenter de créer des TenantAdmins jusqu'à la limite

**Tests Postman:**

```http
# Test 3.2.1: Créer TenantAdmin pour Tenant A jusqu'à la limite
POST /api/v1/admins/tenant
Authorization: Bearer {globalAdmin_token}
Body: {
  "username": "tenant_admin_1",
  "email": "tenant1@example.com",
  "firstName": "Tenant",
  "lastName": "Admin 1",
  "tenantId": 2
}
Expected: 201 Created (si sous la limite)

# Répéter jusqu'à atteindre la limite...

# Test 3.2.2: Tentative de création au-delà de la limite
POST /api/v1/admins/tenant
Authorization: Bearer {globalAdmin_token}
Body: {
  "username": "tenant_admin_4",
  "email": "tenant4@example.com",
  "firstName": "Tenant",
  "lastName": "Admin 4",
  "tenantId": 2
}
Expected: 400 Bad Request avec message d'erreur indiquant la limite
```

**Validation DB:**
```sql
-- Compter les TenantAdmins actifs par tenant
SELECT 
    tenant_id,
    COUNT(*) as active_tenant_admin_count
FROM ezkey_admin
WHERE admin_type = 'TENANT_ADMIN' 
  AND active = true
  AND tenant_id = 2
GROUP BY tenant_id;

-- Vérifier que le count ne dépasse pas la limite (3)
```

**Critères de succès:**
- ✅ Les créations réussissent jusqu'à la limite (max-tenant-admins-per-tenant)
- ✅ La création au-delà de la limite retourne 400 Bad Request
- ✅ La limite est appliquée par tenant (chaque tenant peut avoir jusqu'à 3 TenantAdmins)

---

### 3.3 Test: Limite minimale de GlobalAdmins (safety floor)

**Objectif:** Vérifier que la limite minimale empêche le lockout.

**Configuration:**
- `ezkey.security.admin.min-global-admins=1` (par défaut)

**Setup:**
- S'assurer qu'il y a exactement 1 GlobalAdmin actif
- Tenter de désactiver ce dernier GlobalAdmin

**Tests Postman:**

```http
# Test 3.3.1: Tentative de désactivation du dernier GlobalAdmin
POST /api/v1/admins/{lastGlobalAdminId}/deactivate
Authorization: Bearer {globalAdmin_token}
Expected: 400 Bad Request avec message indiquant la limite minimale
```

**Validation DB:**
```sql
-- Compter les GlobalAdmins actifs
SELECT 
    admin_type,
    COUNT(*) as active_count
FROM ezkey_admin
WHERE admin_type = 'GLOBAL_ADMIN' 
  AND active = true
GROUP BY admin_type;

-- Si count = 1, la désactivation doit échouer
```

**Critères de succès:**
- ✅ La désactivation du dernier GlobalAdmin retourne 400 Bad Request
- ✅ Le message d'erreur indique clairement la limite minimale
- ✅ Le GlobalAdmin reste actif (pas de lockout)

---

### 3.4 Test: Limite minimale de TenantAdmins par tenant

**Objectif:** Vérifier que la limite minimale empêche le lockout par tenant.

**Configuration:**
- `ezkey.security.admin.min-tenant-admins-per-tenant=1` (par défaut)

**Setup:**
- Tenant A avec exactement 1 TenantAdmin actif
- Tenter de désactiver ce dernier TenantAdmin

**Tests Postman:**

```http
# Test 3.4.1: Tentative de désactivation du dernier TenantAdmin de Tenant A
POST /api/v1/admins/{lastTenantAdminId}/deactivate
Authorization: Bearer {globalAdmin_token}
Expected: 400 Bad Request avec message indiquant la limite minimale
```

**Validation DB:**
```sql
-- Compter les TenantAdmins actifs pour Tenant A
SELECT 
    tenant_id,
    COUNT(*) as active_count
FROM ezkey_admin
WHERE admin_type = 'TENANT_ADMIN' 
  AND active = true
  AND tenant_id = 2
GROUP BY tenant_id;

-- Si count = 1, la désactivation doit échouer
```

**Critères de succès:**
- ✅ La désactivation du dernier TenantAdmin retourne 400 Bad Request
- ✅ Le message d'erreur indique clairement la limite minimale
- ✅ Le TenantAdmin reste actif (pas de lockout)

---

## 4. Tests de Provisioning - Création d'Admins

### 4.1 Test: Création de GlobalAdmin par GlobalAdmin

**Objectif:** Vérifier que seul un GlobalAdmin peut créer un autre GlobalAdmin.

**Tests Postman:**

```http
# Test 4.1.1: GlobalAdmin crée un GlobalAdmin
POST /api/v1/admins/global
Authorization: Bearer {globalAdmin_token}
Body: {
  "username": "new_global_admin",
  "email": "newglobal@example.com",
  "firstName": "New",
  "lastName": "GlobalAdmin"
}
Expected: 201 Created
Response doit contenir:
- adminId
- enrollmentId
- enrollmentProofToken
- enrollmentChallengeCode
- recoveryCodes (liste de codes)

# Test 4.1.2: TenantAdmin ne peut PAS créer un GlobalAdmin
POST /api/v1/admins/global
Authorization: Bearer {tenantAdminA_token}
Body: { ... }
Expected: 403 Forbidden
```

**Validation DB:**
```sql
-- Vérifier la création du GlobalAdmin
SELECT 
    admin_id,
    username,
    email,
    admin_type,
    tenant_id,
    active,
    mfa_enrollment_id
FROM ezkey_admin
WHERE username = 'new_global_admin';

-- Vérifier que l'enrollment a été créé
SELECT 
    enrollment_id,
    enrollment_name,
    status,
    enrollment_proof_token IS NOT NULL as has_proof_token,
    enrollment_challenge IS NOT NULL as has_challenge
FROM ezkey_enrollment
WHERE enrollment_id = {enrollmentId_from_response};
```

**Critères de succès:**
- ✅ GlobalAdmin peut créer un GlobalAdmin
- ✅ TenantAdmin ne peut pas créer un GlobalAdmin (403)
- ✅ La réponse contient les credentials d'onboarding (proof token, challenge, recovery codes)
- ✅ L'enrollment est créé avec status CREATED

---

### 4.2 Test: Création de TenantAdmin

**Objectif:** Vérifier que GlobalAdmin et TenantAdmin peuvent créer des TenantAdmins (avec restrictions).

**Tests Postman:**

```http
# Test 4.2.1: GlobalAdmin crée un TenantAdmin pour Tenant A
POST /api/v1/admins/tenant
Authorization: Bearer {globalAdmin_token}
Body: {
  "username": "new_tenant_admin",
  "email": "newtenant@example.com",
  "firstName": "New",
  "lastName": "TenantAdmin",
  "tenantId": 2
}
Expected: 201 Created
Response doit contenir les credentials d'onboarding

# Test 4.2.2: TenantAdmin A crée un TenantAdmin pour son propre tenant (Tenant A)
POST /api/v1/admins/tenant
Authorization: Bearer {tenantAdminA_token}
Body: {
  "username": "peer_tenant_admin",
  "email": "peer@example.com",
  "firstName": "Peer",
  "lastName": "TenantAdmin",
  "tenantId": 2
}
Expected: 201 Created

# Test 4.2.3: TenantAdmin A ne peut PAS créer un TenantAdmin pour Tenant B
POST /api/v1/admins/tenant
Authorization: Bearer {tenantAdminA_token}
Body: {
  "username": "unauthorized_tenant_admin",
  "email": "unauthorized@example.com",
  "firstName": "Unauthorized",
  "lastName": "TenantAdmin",
  "tenantId": 3
}
Expected: 403 Forbidden
```

**Validation DB:**
```sql
-- Vérifier les créations de TenantAdmins
SELECT 
    admin_id,
    username,
    email,
    admin_type,
    tenant_id,
    active,
    mfa_enrollment_id
FROM ezkey_admin
WHERE admin_type = 'TENANT_ADMIN'
  AND username IN ('new_tenant_admin', 'peer_tenant_admin')
ORDER BY tenant_id, username;

-- Vérifier que les enrollments sont créés
SELECT 
    e.enrollment_id,
    e.enrollment_name,
    e.status,
    a.username,
    a.tenant_id
FROM ezkey_enrollment e
JOIN ezkey_admin a ON e.enrollment_id = a.mfa_enrollment_id
WHERE a.username IN ('new_tenant_admin', 'peer_tenant_admin');
```

**Critères de succès:**
- ✅ GlobalAdmin peut créer un TenantAdmin pour n'importe quel tenant
- ✅ TenantAdmin peut créer un TenantAdmin pour son propre tenant
- ✅ TenantAdmin ne peut pas créer un TenantAdmin pour un autre tenant (403)
- ✅ Les credentials d'onboarding sont retournés

---

## 5. Tests de Déactivation (si implémenté)

### 5.1 Test: Déactivation de Tenant

**Objectif:** Vérifier que la déactivation d'un tenant fonctionne correctement.

**Tests Postman:**

```http
# Test 5.1.1: GlobalAdmin désactive un tenant
POST /api/v1/tenants/{tenantId}/deactivate
Authorization: Bearer {globalAdmin_token}
Expected: 204 No Content

# Test 5.1.2: Vérifier que le tenant est désactivé
GET /api/v1/tenants/{tenantId}
Authorization: Bearer {globalAdmin_token}
Expected: 200 OK, { "active": false }

# Test 5.1.3: TenantAdmin ne peut plus créer de ressources pour un tenant désactivé
POST /api/v1/integrations
Authorization: Bearer {tenantAdminA_token}  # tenant désactivé
Expected: 403 Forbidden ou 400 Bad Request
```

**Validation DB:**
```sql
-- Vérifier que le tenant est désactivé
SELECT 
    tenant_id,
    tenant_name,
    active
FROM ezkey_tenant
WHERE tenant_id = {tenantId};

-- Vérifier que les tokens des admins du tenant sont révoqués
SELECT 
    at.token_id,
    at.admin_id,
    at.active,
    a.username,
    a.tenant_id
FROM ezkey_admin_token at
JOIN ezkey_admin a ON at.admin_id = a.admin_id
WHERE a.tenant_id = {tenantId}
  AND at.active = true;
-- Doit retourner 0 lignes (tous les tokens révoqués)
```

**Critères de succès:**
- ✅ Le tenant est marqué comme inactive (active = false)
- ✅ Les tokens des admins du tenant sont révoqués
- ✅ Les opérations sur le tenant désactivé sont bloquées

---

## 6. Tests d'Audit

### 6.1 Test: Audit des opérations de provisioning

**Objectif:** Vérifier que les opérations de création d'admins sont auditées.

**Tests Postman:**

```http
# Test 6.1.1: Créer un GlobalAdmin
POST /api/v1/admins/global
Authorization: Bearer {globalAdmin_token}
Body: { ... }
Expected: 201 Created

# Test 6.1.2: Créer un TenantAdmin
POST /api/v1/admins/tenant
Authorization: Bearer {globalAdmin_token}
Body: { ... }
Expected: 201 Created
```

**Validation DB:**
```sql
-- Vérifier les entrées d'audit pour les créations d'admins
SELECT 
    audit_log_id,
    event_type,
    admin_id,
    tenant_id,
    resource_type,
    resource_id,
    action,
    created_at
FROM ezkey_audit_log
WHERE event_type LIKE '%ADMIN%'
  AND action = 'CREATE'
ORDER BY created_at DESC
LIMIT 10;

-- Vérifier que tenant_id est correctement renseigné
SELECT 
    event_type,
    tenant_id,
    COUNT(*) as event_count
FROM ezkey_audit_log
WHERE event_type LIKE '%ADMIN%'
GROUP BY event_type, tenant_id
ORDER BY tenant_id, event_type;
```

**Critères de succès:**
- ✅ Chaque création d'admin génère une entrée d'audit
- ✅ Le tenant_id est correctement renseigné dans l'audit
- ✅ L'admin_id du créateur est enregistré
- ✅ Le resource_type et resource_id sont corrects

---

### 6.2 Test: Audit des opérations de création de ressources

**Objectif:** Vérifier que les créations de ressources (integrations, enrollments, API keys) sont auditées.

**Tests Postman:**

```http
# Test 6.2.1: Créer une intégration
POST /api/v1/integrations
Authorization: Bearer {tenantAdminA_token}
Body: { ... }
Expected: 201 Created

# Test 6.2.2: Créer un enrollment
POST /api/v1/enrollments
Authorization: Bearer {tenantAdminA_token}
Body: { ... }
Expected: 201 Created

# Test 6.2.3: Créer une API key
POST /api/v1/api-keys
Authorization: Bearer {tenantAdminA_token}
Body: { ... }
Expected: 201 Created
```

**Validation DB:**
```sql
-- Vérifier les entrées d'audit pour les créations de ressources
SELECT 
    audit_log_id,
    event_type,
    admin_id,
    tenant_id,
    resource_type,
    resource_id,
    action,
    created_at
FROM ezkey_audit_log
WHERE action = 'CREATE'
  AND resource_type IN ('INTEGRATION', 'ENROLLMENT', 'API_KEY')
ORDER BY created_at DESC
LIMIT 20;

-- Vérifier que tenant_id est correct pour chaque ressource
SELECT 
    resource_type,
    tenant_id,
    COUNT(*) as create_count
FROM ezkey_audit_log
WHERE action = 'CREATE'
  AND resource_type IN ('INTEGRATION', 'ENROLLMENT', 'API_KEY')
GROUP BY resource_type, tenant_id
ORDER BY tenant_id, resource_type;
```

**Critères de succès:**
- ✅ Chaque création de ressource génère une entrée d'audit
- ✅ Le tenant_id est correctement renseigné
- ✅ Le resource_type et resource_id correspondent à la ressource créée

---

## 7. Tests d'Intégration - Scénarios Complets

### 7.1 Test: Scénario complet - Création d'un tenant avec ressources

**Objectif:** Valider un scénario complet de création de tenant et de ressources.

**Étapes:**

1. **Créer un tenant**
```http
POST /api/v1/tenants
Authorization: Bearer {globalAdmin_token}
Body: {
  "tenantName": "ACME Corp",
  "tenantDescription": "Test tenant for ACME Corp"
}
Expected: 201 Created, tenantId: X
```

2. **Créer un TenantAdmin pour ce tenant**
```http
POST /api/v1/admins/tenant
Authorization: Bearer {globalAdmin_token}
Body: {
  "username": "acme_admin",
  "email": "acme@example.com",
  "firstName": "ACME",
  "lastName": "Admin",
  "tenantId": X
}
Expected: 201 Created
```

3. **Login avec le TenantAdmin** (utiliser les credentials d'onboarding)
```http
POST /api/v1/auth/login
Body: {
  "enrollmentId": {enrollmentId},
  "enrollmentProofToken": {enrollmentProofToken},
  "enrollmentChallengeCode": {enrollmentChallengeCode}
}
Expected: 200 OK, token reçu
```

4. **Créer une intégration**
```http
POST /api/v1/integrations
Authorization: Bearer {acmeAdmin_token}
Body: {
  "integrationName": "ACME Integration",
  "integrationDescription": "Main integration for ACME"
}
Expected: 201 Created, tenantId doit être X
```

5. **Créer un enrollment**
```http
POST /api/v1/enrollments
Authorization: Bearer {acmeAdmin_token}
Body: {
  "integrationId": {integrationId},
  "enrollmentName": "ACME User Enrollment",
  "challengeRequired": false
}
Expected: 201 Created
```

6. **Créer une API key**
```http
POST /api/v1/api-keys
Authorization: Bearer {acmeAdmin_token}
Body: {
  "integrationId": {integrationId},
  "apiKeyName": "ACME API Key"
}
Expected: 201 Created
```

**Validation DB:**
```sql
-- Vérifier la chaîne complète: Tenant → Admin → Integration → Enrollment → API Key
SELECT 
    t.tenant_id,
    t.tenant_name,
    a.admin_id,
    a.username,
    i.integration_id,
    i.integration_name,
    e.enrollment_id,
    e.enrollment_name,
    ak.api_key_id,
    ak.api_key_name
FROM ezkey_tenant t
LEFT JOIN ezkey_admin a ON t.tenant_id = a.tenant_id AND a.admin_type = 'TENANT_ADMIN'
LEFT JOIN ezkey_integration i ON t.tenant_id = i.tenant_id
LEFT JOIN ezkey_enrollment e ON i.integration_id = e.integration_id
LEFT JOIN ezkey_api_key ak ON i.integration_id = ak.integration_id
WHERE t.tenant_name = 'ACME Corp'
ORDER BY t.tenant_id, i.integration_id, e.enrollment_id, ak.api_key_id;
```

**Critères de succès:**
- ✅ Toutes les étapes réussissent
- ✅ Le tenantId est correctement propagé à toutes les ressources
- ✅ Le TenantAdmin ne peut accéder qu'aux ressources de son tenant
- ✅ Les entrées d'audit sont créées pour chaque opération

---

## 8. Checklist de Validation

### 8.1 Checklist Pré-Tests

- [ ] Stack Docker en cours d'exécution
- [ ] Token GlobalAdmin valide (bootstrap)
- [ ] Collections Postman configurées avec les tokens
- [ ] Accès à la base de données PostgreSQL
- [ ] Configuration des limites vérifiée (max/min admins)

### 8.2 Checklist Post-Tests

- [ ] Tous les tests de non-régression passent
- [ ] Tous les tests de sécurité passent
- [ ] Tous les tests de limites passent
- [ ] Tous les tests de provisioning passent
- [ ] Tous les tests d'audit passent
- [ ] Les validations DB confirment les résultats
- [ ] Aucune régression détectée

---

## 9. Notes et Observations

### 9.1 Points d'attention

- **Isolation stricte:** Vérifier que les filtres par tenant sont appliqués à tous les endpoints
- **Limites:** S'assurer que les limites sont vérifiées AVANT la création (pas après)
- **Audit:** Vérifier que tous les événements critiques sont audités
- **Tokens:** Vérifier que les tokens sont correctement révoqués lors de la désactivation

### 9.2 Tests à ajouter (Phase 2)

- Tests de déactivation d'admin (quand implémenté)
- Tests de révocation de tokens
- Tests de performance avec plusieurs tenants
- Tests de charge avec de nombreux admins

---

**Dernière mise à jour:** 2025-12-24  
**Version:** 1.0  
**Statut:** Plan de tests initial pour Phase 1 Multi-Tenancy

