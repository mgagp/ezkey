# Résumé des Contrôleurs Pageable - Ezkey Admin API

Ce fichier sera en UTF-8 sans BOM.

## Vue d'Ensemble

Ce document résume les caractéristiques de pagination et tri pour chaque contrôleur Admin API supportant `Pageable`.

---

## 1. AuditLogController

**Endpoint:** `GET /api/v1/audit-logs`

### Paramètres de Pagination
- **Page par défaut:** 0
- **Size par défaut:** 20
- **Sort par défaut:** `createdAt,DESC`

### Champs Triables
| Champ | Type | Description |
|-------|------|-------------|
| `auditLogId` | Long | ID unique du log d'audit |
| `createdAt` | OffsetDateTime | Date de création |
| `eventType` | Enum | Type d'événement (ex: ENROLLMENT_CREATE) |
| `eventStatus` | Enum | Statut (SUCCESS, FAILURE) |
| `apiName` | Enum | Nom de l'API appelée |

### Filtres Disponibles
- `eventType` (EventType enum)
- `eventStatus` (EventStatus enum)
- `apiName` (ApiName enum)
- `enrollmentId` (Integer)
- `adminId` (Integer)

### Exemple d'Appel
```bash
# API
GET /api/v1/audit-logs?eventType=ENROLLMENT_CREATE&page=0&size=10&sort=createdAt,desc

# Futur CLI
ezkey admin audit-log list --event-type ENROLLMENT_CREATE --page 0 --size 10 --sort createdAt,desc
```

### Status CLI
❌ Pagination/tri non implémentés (filtres partiellement implémentés)

---

## 2. AuthAttemptController

**Endpoint:** `GET /api/v1/auth-attempts`

### Paramètres de Pagination
- **Page par défaut:** 0
- **Size par défaut:** 20
- **Sort par défaut:** `createdAt,DESC`

### Champs Triables
| Champ | Type | Description |
|-------|------|-------------|
| `authAttemptId` | Integer | ID unique de la tentative |
| `createdAt` | OffsetDateTime | Date de création |
| `expiresAt` | OffsetDateTime | Date d'expiration |
| `enrollmentId` | Integer | ID de l'enrollment associé |

### Filtres Disponibles
- `status` (AuthAttemptStatus enum: PENDING, READ, ACCEPTED, REJECTED, INVALID, EXPIRED)
- `enrollmentId` (Integer)
- `integrationId` (Integer)
- `createdAfter` (OffsetDateTime, ISO-8601)
- `createdBefore` (OffsetDateTime, ISO-8601)

### Exemple d'Appel
```bash
# API
GET /api/v1/auth-attempts?status=PENDING&enrollmentId=123&page=1&size=50&sort=createdAt,asc

# Futur CLI
ezkey admin auth-attempt list --status PENDING --enrollment-id 123 --page 1 --size 50 --sort createdAt,asc
```

### Status CLI
❌ Pagination/tri non implémentés, filtres incomplets (seulement --enrollment-id)

---

## 3. EnrollmentController

**Endpoint:** `GET /api/v1/enrollments`

### Paramètres de Pagination
- **Page par défaut:** 0
- **Size par défaut:** 20
- **Sort par défaut:** `createdAt,DESC`

### Champs Triables
| Champ | Type | Description |
|-------|------|-------------|
| `enrollmentId` | Integer | ID unique de l'enrollment |
| `enrollmentName` | String | Nom de l'enrollment |
| `createdAt` | OffsetDateTime | Date de création |
| `integrationId` | Integer | ID de l'integration associée |
| `status` | Enum | Statut (CREATED, BOUND, VERIFIED, INVALID) |

### Filtres Disponibles
- `status` (EnrollmentStatus enum: CREATED, BOUND, VERIFIED, INVALID)
- `integrationId` (Integer)
- `enrollmentName` (String, partial match, case-insensitive)
- `active` (Boolean)
- `createdAfter` (OffsetDateTime, ISO-8601)
- `createdBefore` (OffsetDateTime, ISO-8601)

### Exemple d'Appel
```bash
# API
GET /api/v1/enrollments?integrationId=5&status=BOUND&active=true&page=0&size=20&sort=enrollmentName,asc

# Futur CLI
ezkey admin enrollment list --integration-id 5 --status BOUND --active --page 0 --size 20 --sort enrollmentName,asc
```

### Status CLI
⚠️ Filtres partiels (seulement --integration-id), ❌ pagination/tri non implémentés

**Recommandation:** Commande pilote pour l'implémentation (bon cas d'usage, 5 champs triables)

---

## 4. IntegrationController

**Endpoint:** `GET /api/v1/integrations`

### Paramètres de Pagination
- **Page par défaut:** 0
- **Size par défaut:** 20
- **Sort par défaut:** `createdAt,DESC`

### Champs Triables
| Champ | Type | Description |
|-------|------|-------------|
| `id` | Integer | ID unique de l'integration |
| `createdAt` | OffsetDateTime | Date de création |
| `active` | Boolean | Statut actif/inactif |

### Filtres Disponibles
- `integrationName` (String, partial match, case-insensitive)
- `active` (Boolean)
- `createdAfter` (OffsetDateTime, ISO-8601)
- `createdBefore` (OffsetDateTime, ISO-8601)

### Exemple d'Appel
```bash
# API
GET /api/v1/integrations?active=true&page=0&size=50&sort=id,asc

# Futur CLI
ezkey admin integration list --active --page 0 --size 50 --sort id,asc
```

### Status CLI
❌ Aucun filtre, ❌ pagination/tri non implémentés

---

## 5. AdminProvisioningController

**Endpoint:** `GET /api/v1/admins`

### Paramètres de Pagination
- **Page par défaut:** 0
- **Size par défaut:** 20
- **Sort par défaut:** `createdAt,DESC` *(à confirmer)*

### Champs Triables
| Champ | Type | Description |
|-------|------|-------------|
| *(À documenter)* | | |

### Filtres Disponibles
*(À documenter en examinant le contrôleur)*

### Exemple d'Appel
```bash
# API
GET /api/v1/admins?page=0&size=20&sort=createdAt,desc

# Futur CLI (à créer)
ezkey admin provisioning list --page 0 --size 20 --sort createdAt,desc
```

### Status CLI
❌ Commande non implémentée (contrôleur existe mais pas exposé dans CLI)

---

## Synthèse des Patterns Communs

### Conventions Spring Pageable

Tous les contrôleurs suivent les mêmes conventions :

1. **Paramètres de requête**
   - `page` : Numéro de page (0-based)
   - `size` : Nombre de résultats par page
   - `sort` : Format `field,direction` (ex: `createdAt,desc`)

2. **Valeurs par défaut**
   - Page: 0 (première page)
   - Size: 20
   - Sort: `createdAt,DESC` (plus récent en premier)

3. **Structure de réponse**
   ```json
   {
     "content": [...],
     "pageable": { "pageNumber": 0, "pageSize": 20, ... },
     "totalPages": 10,
     "totalElements": 187,
     "first": true,
     "last": false,
     "number": 0,
     "size": 20,
     "numberOfElements": 20,
     "empty": false
   }
   ```

### Directions de Tri Supportées

Toutes les APIs supportent :
- `asc` : Ordre croissant (A→Z, 0→9, ancien→récent)
- `desc` : Ordre décroissant (Z→A, 9→0, récent→ancien)

### Filtres Temporels

Les contrôleurs supportant des filtres temporels utilisent :
- `createdAfter` : Inclut les enregistrements créés après cette date
- `createdBefore` : Inclut les enregistrements créés avant cette date
- Format : ISO-8601 (ex: `2026-01-30T10:00:00Z`)

---

## Matrice de Compatibilité CLI

| Contrôleur | Filtres | Pagination | Tri | Status Global |
|------------|---------|------------|-----|---------------|
| AuditLog | ⚠️ Partiel | ❌ | ❌ | ⚠️ Incomplet |
| AuthAttempt | ⚠️ Minimal | ❌ | ❌ | ⚠️ Incomplet |
| Enrollment | ⚠️ Minimal | ❌ | ❌ | ⚠️ Incomplet |
| Integration | ❌ Aucun | ❌ | ❌ | ❌ Non implémenté |
| AdminProvisioning | ❌ N/A | ❌ | ❌ | ❌ Commande absente |

**Légende:**
- ✅ Complet et fonctionnel
- ⚠️ Partiellement implémenté
- ❌ Non implémenté

---

## Priorités d'Implémentation

### Phase 1 : MVP (8-10h)
1. **EnrollmentController** - Commande pilote (5 champs triables, cas d'usage fréquent)
2. **AuthAttemptController** - Priorité sécurité/audit
3. **AuditLogController** - Priorité compliance

### Phase 2 : Complétion (3-4h)
4. **IntegrationController** - Plus simple (3 champs triables)
5. **AdminProvisioningController** - Créer la commande d'abord

---

## Actions Recommandées

### Immédiat
1. ✅ Créer `pagination_utils.py` avec fonctions réutilisables
2. ✅ Implémenter sur `enrollment list` comme pilote
3. ✅ Tester et valider le pattern

### Court Terme
4. Déployer sur `auth-attempt list` et `audit-log list`
5. Ajouter tests d'intégration pour pagination/tri
6. Documenter dans README

### Moyen Terme
7. Compléter `integration list`
8. Créer et implémenter `admin provisioning list`
9. Ajouter formats d'affichage alternatifs (table, CSV)

---

**Dernière mise à jour :** 2026-01-30
**Source :** Analyse des contrôleurs ezkey-admin-api
**Fichiers analysés :**
- AuditLogController.java (lignes 70-144)
- AuthAttemptController.java (lignes 150-210)
- EnrollmentController.java (lignes 120-210)
- IntegrationController.java (lignes 80-180)
- AdminProvisioningController.java (à compléter)
