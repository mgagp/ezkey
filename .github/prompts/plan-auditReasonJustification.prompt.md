# Plan: Audit Justification & Gap Fixes

## Contexte et objectif

Ajouter un mécanisme de justification (`reason`) aux opérations sensibles d'EZKey pour répondre aux exigences normatives (SOC 2 CC6.3, CC8.1) et améliorer la qualité opérationnelle, tout en corrigeant les gaps d'audit critiques identifiés. Approche pragmatique : optionnelle, validée si fournie, pas de référentiel imposé.

---

## Analyse comparative

| Système | Champ reason natif | Notes |
|---|---|---|
| privacyIDEA | ✗ | Délégué à ITSM externe |
| Duo Security | ✗ | Hors-bande uniquement |
| Keycloak | ✗ | Événements riches, pas de reason |
| HashiCorp Vault | ✓ partiel | Optionnel sur révocation de lease |
| Okta | ✓ partiel | Sur suspend/deactivate uniquement |

**Conclusion** : EZKey sera en avance sur la majorité des comparables. C'est un différenciateur de qualité opérationnelle.

**SOC 2** : Les contrôles CC6.3 (déprovisionnement d'accès) et CC8.1 (changements autorisés) sont satisfaits par un champ optionnel capturé dans l'audit log — aucune obligation de le rendre obligatoire à ce stade.

---

## Décisions de conception

| Décision | Choix retenu | Rationale |
|---|---|---|
| Stockage | Colonne dédiée `reason VARCHAR(500)` dans `ezkey_audit_log` | Interrogeable, traçable SOC 2, lisible dans les rapports |
| Obligatoire? | Optionnelle — validée si fournie (min 10 chars, max 500) | Developer-first, pas de friction imposée, évolutif |
| Transport DELETE | `@RequestParam(required = false) String reason` | Convention HTTP — pas de body sur DELETE |
| Transport POST-actions | Champ JSON `reason` dans le body | Standard REST pour les actions avec body |
| Gaps d'audit | Inclus dans ce plan | Prérequis logique : on ne peut pas ajouter `reason` à une opération qui n'émet pas d'événement |
| Phasing | Pas de phases | Surface gérable : 8 services, 6 contrôleurs, 1 entité, 1 migration |

---

## Gaps d'audit découverts (à corriger dans ce plan)

| Opération | EventType existant? | Problème |
|---|---|---|
| `DELETE /api/v1/api-keys/{keyId}` | `API_KEY_REVOKED` ✓ | Jamais émis — entité mise à jour mais auditLogService jamais appelé |
| `POST /api/v1/api-keys` | `API_KEY_CREATED` ✓ | Jamais émis |
| `DELETE /api/v1/integrations/{id}` | ✗ | Aucun audit, aucun EventType |
| `POST /api/v1/integrations` | ✗ | Aucun audit, aucun EventType |
| `PUT /api/v1/integrations/{id}` | ✗ | Aucun audit, aucun EventType |
| `POST /api/v1/tenants/{id}/deactivate` | ✗ | Aucun audit, aucun EventType |
| `POST /api/v1/tenants` | ✗ | Aucun audit, aucun EventType |
| `PUT /api/v1/tenants/{id}` | ✗ | Aucun audit, aucun EventType |
| `POST /api/v1/encryption-keys/rotate` (success path) | `KEY_INTRODUCED` ✓ | Seulement loggué sur erreur — success path sans audit |
| `POST /api/v1/admin/enrollments/reset` | ✗ | Aucun audit call dans le contrôleur |

---

## Étape 1 — Migration Flyway : nouvelle colonne `reason`

**Module** : `ezkey-migration`

Créer un nouveau script Flyway (ex. `V{next}__add_reason_to_audit_log.sql`) :

```sql
-- Add optional reason/justification field to audit log
-- Supports SOC 2 CC6.3 (access deprovisioning) and CC8.1 (authorized changes)
ALTER TABLE ezkey_audit_log ADD COLUMN reason VARCHAR(500);
```

- Colonne nullable, applicable à toutes les partitions mensuelles existantes
- Inclure le champ `reason` dans le calcul HMAC (étape 2) pour garantir son intégrité

---

## Étape 2 — `AuditLog` entity + Builder

**Fichier** : `ezkey-core/src/main/java/org/ezkey/audit/domain/entity/AuditLog.java`

Modifications :
- Ajouter champ `@Column(name = "reason") @Size(max = 500) private String reason;`
- Ajouter méthode `reason(String reason)` au `Builder` interne
- Inclure `reason` dans la construction du `entryHmac` (après les champs existants dans la concaténation HMAC)
- Ajouter getter `getReason()`

---

## Étape 3 — `EventType` enum : types manquants

**Fichier** : `ezkey-core/src/main/java/org/ezkey/audit/domain/EventType.java`

Ajouter les valeurs suivantes dans les groupes appropriés :

```java
// Integration lifecycle
INTEGRATION_CREATED,
INTEGRATION_UPDATED,
INTEGRATION_DELETED,

// Tenant lifecycle
TENANT_CREATED,
TENANT_UPDATED,
TENANT_DEACTIVATED,
```

> Note : `API_KEY_CREATED`, `API_KEY_REVOKED` existent déjà dans l'enum — ils ne sont simplement pas émis (corrigé en étape 5).

---

## Étape 4 — Request DTOs : ajouter `reason`

### 4a. POST-actions avec body (champ JSON)

**`TenantDeactivateRequestDto`** (à créer — `POST /api/v1/tenants/{id}/deactivate` n'a pas encore de body) :
```java
public record TenantDeactivateRequestDto(
    @Size(min = 10, max = 500) String reason
) {}
```

**`EnrollmentResetRequestDto`** — ajouter champ :
```java
@Size(min = 10, max = 500)
private String reason; // optional
```

**`KeyRotationRequestDto`** — ajouter champ (ou créer le DTO si absent) :
```java
@Size(min = 10, max = 500)
private String reason; // optional
```

### 4b. DELETE avec query param

Pour les endpoints DELETE suivants, ajouter dans le contrôleur :
```java
@RequestParam(required = false) @Size(min = 10, max = 500) String reason
```
- `DELETE /api/v1/api-keys/{keyId}`
- `DELETE /api/v1/enrollments/{id}`
- `DELETE /api/v1/integrations/{id}`

### Règle de validation commune

Si `reason != null && !reason.isBlank()` → min 10 chars, max 500 chars. Si `reason` est `null` ou absent → accepté silencieusement.

---

## Étape 5 — Corriger les gaps d'audit dans les services

| Service | Opération | Correction |
|---|---|---|
| `ApiKeyService` | `revokeApiKey()` | Émettre `API_KEY_REVOKED` SUCCESS + passer `reason` |
| `ApiKeyService` | `createApiKey()` | Émettre `API_KEY_CREATED` SUCCESS/FAILURE |
| `IntegrationService` | `deleteIntegration()` | Émettre `INTEGRATION_DELETED` SUCCESS + `reason` |
| `IntegrationService` | `createIntegration()` | Émettre `INTEGRATION_CREATED` SUCCESS/FAILURE |
| `IntegrationService` | `updateIntegration()` | Émettre `INTEGRATION_UPDATED` SUCCESS/FAILURE |
| `TenantService` | `deactivateTenant()` | Émettre `TENANT_DEACTIVATED` SUCCESS + `reason` |
| `TenantService` | `createTenant()` | Émettre `TENANT_CREATED` SUCCESS/FAILURE |
| `TenantService` | `updateTenant()` | Émettre `TENANT_UPDATED` SUCCESS/FAILURE |
| `KeyRotationService` | `rotatePrimaryKey()` success path | Émettre `KEY_INTRODUCED` SUCCESS + `reason` |
| `AdminEnrollmentService` | `resetEnrollment()` | Vérifier et compléter audit call + `reason` |

Pattern standard à appliquer dans chaque service :
```java
auditLogService.log(AuditLog.builder()
    .eventType(EventType.API_KEY_REVOKED)
    .eventAction(AdminAuditConstants.API_KEY_REVOKED)
    .eventStatus(EventStatus.SUCCESS)
    .apiName(ApiName.ADMIN_API)
    .tenantId(tenantId)
    .adminId(adminId)
    .reason(reason)           // <-- nouveau
    // ... champs contextuels
    .build());
```

---

## Étape 6 — Contrôleurs : passer `reason` aux services

Contrôleurs à modifier :
- `ApiKeyController` — DELETE : ajouter `@RequestParam` + propager au service
- `EnrollmentController` — DELETE : ajouter `@RequestParam` + propager
- `IntegrationController` — DELETE : ajouter `@RequestParam` + propager
- `TenantController` — POST deactivate : ajouter body `TenantDeactivateRequestDto` (ou `@RequestParam`)
- `EncryptionKeyController` — POST rotate : ajouter champ `reason` dans `KeyRotationRequestDto`
- `AdminEnrollmentController` — POST reset : propager `reason` depuis `EnrollmentResetRequestDto`

---

## Étape 7 — Response DTO + Mapper

**`AuditLogResponseDto`** : ajouter champ `String reason` (nullable).

**`AuditLogMapper`** (MapStruct) : ajouter mapping `reason → reason`.

---

## Étape 8 — Tests

### Unit tests (services)
- `ApiKeyServiceTest` : vérifier que `reason` apparaît dans l'audit log lors d'une révocation
- `TenantServiceTest` : vérifier `reason` sur désactivation
- `IntegrationServiceTest` : vérifier `reason` sur suppression
- Tests pour chaque service nouvellement instrumenté (création, mise à jour)

### Integration tests (contrôleurs)
- `DELETE /api/v1/api-keys/{keyId}?reason=Clé+compromise+en+production` → `GET /api/v1/audit-logs` → champ `reason` présent
- `DELETE /api/v1/api-keys/{keyId}` (sans reason) → opération acceptée, `reason` null dans le log
- `POST /api/v1/tenants/{id}/deactivate` avec body `{"reason": "Tenant inactif depuis 6 mois"}` → audit log contient reason

### Tests de validation
- `reason` de 5 chars → `400 Bad Request`
- `reason` absent → `200/204` accepté
- `reason` de 500 chars exactement → accepté
- `reason` de 501 chars → `400 Bad Request`

### Intégrité HMAC
- Vérifier que `GET /api/v1/audit-logs/integrity-check` valide correctement les entrées avec `reason` non null

---

## Vérification finale

```bash
mvn checkstyle:check
mvn clean verify
```

Checklist :
- [ ] Tous les nouveaux `EventType` sont couverts dans les mappers MapStruct (méthode `eventTypeToString`)
- [ ] Migration Flyway s'applique proprement (test sur base vierge et base avec données existantes)
- [ ] HMAC recalculé après ajout de `reason` dans la concaténation — vérifier rétrocompatibilité des entrées existantes (reason null → chaîne vide ou "null" dans le hash ? À trancher)
- [ ] Postman collection mise à jour avec les nouveaux paramètres `reason`
- [ ] OpenAPI/Swagger documenté pour les nouveaux paramètres

---

## Fichiers impactés (résumé)

| Fichier | Type de changement |
|---|---|
| `ezkey-migration/src/main/resources/db/migration/V{n}__add_reason_to_audit_log.sql` | Nouveau |
| `ezkey-core/.../audit/domain/entity/AuditLog.java` | Modifier — champ + builder + HMAC |
| `ezkey-core/.../audit/domain/EventType.java` | Modifier — 6 nouvelles valeurs |
| `ezkey-admin-api/.../dto/request/TenantDeactivateRequestDto.java` | Nouveau |
| `ezkey-admin-api/.../dto/request/EnrollmentResetRequestDto.java` | Modifier — champ reason |
| `ezkey-admin-api/.../dto/request/KeyRotationRequestDto.java` | Modifier ou créer |
| `ezkey-admin-api/.../dto/response/AuditLogResponseDto.java` | Modifier — champ reason |
| `ezkey-admin-api/.../mapper/AuditLogMapper.java` | Modifier — mapping reason |
| `ezkey-admin-api/.../service/ApiKeyService.java` | Modifier — audit calls + reason |
| `ezkey-admin-api/.../service/IntegrationService.java` | Modifier — audit calls + reason |
| `ezkey-admin-api/.../service/TenantService.java` | Modifier — audit calls + reason |
| `ezkey-admin-api/.../service/KeyRotationService.java` | Modifier — audit success path + reason |
| `ezkey-admin-api/.../service/AdminEnrollmentService.java` | Modifier — audit call + reason |
| `ezkey-admin-api/.../controller/ApiKeyController.java` | Modifier — @RequestParam reason |
| `ezkey-admin-api/.../controller/EnrollmentController.java` | Modifier — @RequestParam reason |
| `ezkey-admin-api/.../controller/IntegrationController.java` | Modifier — @RequestParam reason |
| `ezkey-admin-api/.../controller/TenantController.java` | Modifier — body/param reason |
| `ezkey-admin-api/.../controller/EncryptionKeyController.java` | Modifier — reason dans DTO |
| `ezkey-admin-api/.../controller/AdminEnrollmentController.java` | Modifier — propager reason |
| Tests unitaires et d'intégration correspondants | Nouveau/Modifier |
