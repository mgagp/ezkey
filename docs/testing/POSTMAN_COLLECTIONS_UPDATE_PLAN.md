# Plan de Mise à Jour des Collections Postman - Multi-Tenancy

## Vue d'ensemble

Ce document décrit les mises à jour nécessaires aux collections Postman pour refléter les changements du mode multi-tenancy Phase 1.

## Objectifs

1. **Mettre à jour les collections existantes** pour documenter les changements multi-tenancy
2. **Créer de nouvelles collections** pour les nouveaux endpoints (Tenants, Admin Provisioning)
3. **Assurer la cohérence** des variables, documentation et tests entre toutes les collections

---

## 1. Collections Existantes à Mettre à Jour

### 1.1 EZ Key Integrations admin
**Changements:**
- ✅ Attribution automatique de tenant lors de la création
  - GlobalAdmin → "Ezkey System" tenant (tenantId: 1)
  - TenantAdmin → admin's tenant
- ✅ Filtrage automatique par tenant dans les listes
  - TenantAdmin voit uniquement les intégrations de son tenant
  - GlobalAdmin voit toutes les intégrations
- ✅ Validation d'accès par tenant dans GET by ID

**Actions:**
- Mettre à jour la description de la collection pour mentionner le multi-tenancy
- Ajouter des notes dans la documentation de chaque endpoint
- Mettre à jour les tests pour valider le tenantId dans les réponses

### 1.2 EZ Key Enrollments admin
**Changements:**
- ✅ Filtrage automatique par tenant dans les listes
- ✅ Validation d'accès par tenant dans GET by ID
- ✅ Validation d'accès à l'intégration lors de la création

**Actions:**
- Mettre à jour la description de la collection
- Ajouter des notes sur le tenant scoping
- Mettre à jour les tests

### 1.3 EZ Key API Keys admin
**Changements:**
- ✅ Validation d'accès à l'intégration lors de la création
- ✅ Filtrage automatique par tenant dans les listes

**Actions:**
- Mettre à jour la description de la collection
- Ajouter des notes sur le tenant scoping
- Mettre à jour les tests

### 1.4 EZ Key Auth Attempts admin
**Changements:**
- ✅ Validation d'accès par tenant/enrollment
- ✅ Filtrage automatique par tenant dans les listes

**Actions:**
- Mettre à jour la description de la collection
- Ajouter des notes sur le tenant scoping
- Mettre à jour les tests

### 1.5 EZ Key Authentication Login admin
**Changements:**
- ✅ Réponse inclut maintenant `adminType` et `tenantId`
- ✅ Support pour GlobalAdmin et TenantAdmin

**Actions:**
- Mettre à jour les tests pour extraire et sauvegarder `adminType` et `tenantId`
- Documenter les différents types d'admins

---

## 2. Nouvelles Collections à Créer

### 2.1 EZ Key Tenants admin
**Endpoints:**
- `POST /api/v1/tenants` - Créer un tenant (GlobalAdmin only)
- `GET /api/v1/tenants` - Lister tous les tenants (GlobalAdmin only)
- `GET /api/v1/tenants/{id}` - Obtenir un tenant par ID (GlobalAdmin only)
- `POST /api/v1/tenants/{id}/deactivate` - Désactiver un tenant (GlobalAdmin only)

**Variables à utiliser:**
- `tenantId` - ID du tenant créé/consulté
- `tenantName` - Nom du tenant
- `tenantDescription` - Description du tenant

**Tests:**
- Valider les codes de statut
- Extraire et sauvegarder `tenantId` dans les variables d'environnement
- Valider la structure des réponses

### 2.2 EZ Key Admin Provisioning admin
**Endpoints:**
- `POST /api/v1/admins/global` - Créer un GlobalAdmin (GlobalAdmin only)
- `POST /api/v1/admins/tenant` - Créer un TenantAdmin (GlobalAdmin ou TenantAdmin pour même tenant)
- `POST /api/v1/admins/{id}/deactivate` - Désactiver un admin (GlobalAdmin only, TODO)

**Variables à utiliser:**
- `adminId` - ID de l'admin créé
- `enrollmentId` - ID de l'enrollment créé
- `enrollmentProofToken` - Token d'enrollment (shown once)
- `enrollmentChallenge` - Code challenge (shown once)
- `recoveryCodes` - Codes de récupération (shown once)
- `tenantId` - ID du tenant (pour TenantAdmin)

**Tests:**
- Valider les codes de statut
- Extraire et sauvegarder toutes les credentials d'onboarding
- Valider les limites (max/min admins)
- Valider les permissions (TenantAdmin ne peut pas créer pour autre tenant)

---

## 3. Variables d'Environnement à Ajouter

### Variables existantes (à conserver)
- `base_url_admin_api`
- `token`
- `integrationId`
- `enrollmentId`
- `apiKeyId`
- etc.

### Nouvelles variables à ajouter
- `tenantId` - ID du tenant actuel
- `tenantName` - Nom du tenant
- `adminId` - ID de l'admin créé
- `adminType` - Type d'admin (GLOBAL_ADMIN, TENANT_ADMIN)
- `enrollmentProofToken` - Token d'enrollment (pour nouveaux admins)
- `enrollmentChallenge` - Code challenge (pour nouveaux admins)
- `recoveryCodes` - Codes de récupération (pour nouveaux admins)

---

## 4. Structure et Pattern des Collections

### 4.1 Structure Standard
Chaque collection doit suivre ce pattern:
```json
{
  "info": {
    "name": "Collection Name",
    "description": "Description complète avec notes multi-tenancy"
  },
  "item": [
    {
      "name": "endpoint name",
      "event": [
        {
          "listen": "test",
          "script": {
            "exec": [
              "// Tests avec validation des codes de statut",
              "// Extraction des variables d'environnement",
              "// Validation de la structure des réponses"
            ]
          }
        }
      ],
      "request": {
        "method": "HTTP_METHOD",
        "header": [],
        "body": { /* ... */ },
        "url": { /* ... */ },
        "description": "Description détaillée avec notes multi-tenancy"
      }
    }
  ],
  "auth": {
    "type": "bearer",
    "bearer": [
      {
        "key": "token",
        "value": "{{token}}"
      }
    ]
  }
}
```

### 4.2 Documentation Standard
Chaque endpoint doit inclure:
- Description de l'endpoint
- Notes sur les permissions (GlobalAdmin vs TenantAdmin)
- Notes sur le tenant scoping (si applicable)
- Structure de la requête
- Structure de la réponse
- Codes de statut possibles
- Exemples d'utilisation

### 4.3 Tests Standard
Chaque endpoint doit inclure:
- Validation du code de statut
- Extraction des variables d'environnement pertinentes
- Validation de la structure des réponses
- Validation des champs requis

---

## 5. Ordre d'Exécution

1. ✅ Mettre à jour l'environnement Postman avec les nouvelles variables
2. ✅ Mettre à jour les collections existantes (ajout de documentation multi-tenancy)
3. ✅ Créer la collection "EZ Key Tenants admin"
4. ✅ Créer la collection "EZ Key Admin Provisioning admin"
5. ✅ Vérifier la cohérence entre toutes les collections
6. ✅ Tester toutes les collections avec le stack Docker

---

## 6. Notes Importantes

### 6.1 Tenant Scoping
- **TenantAdmin**: Voit uniquement les ressources de son tenant
- **GlobalAdmin**: Voit toutes les ressources (tous tenants)
- **Filtrage automatique**: Les listes sont automatiquement filtrées par tenant
- **Validation d'accès**: Les accès individuels sont validés par `AccessControlService`

### 6.2 Attribution Automatique de Tenant
- **GlobalAdmin crée une intégration**: Assignée automatiquement à "Ezkey System" (tenantId: 1)
- **TenantAdmin crée une intégration**: Assignée automatiquement à son tenant
- **Pas d'impersonation**: Impossible de créer des ressources pour un autre tenant

### 6.3 Credentials d'Onboarding
- **Enrollment Proof Token**: Shown once, doit être sauvegardé
- **Enrollment Challenge**: Shown once, doit être sauvegardé
- **Recovery Codes**: Shown once, doivent être sauvegardés
- **Utilisation**: Ces credentials sont nécessaires pour le login initial de l'admin

---

**Dernière mise à jour:** 2025-12-24  
**Version:** 1.0  
**Statut:** Plan d'action pour mise à jour des collections Postman

