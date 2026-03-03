# Audit Logs — Analyse de la stratégie et opportunité d'enrichissement

> **Statut :** Analyse initiale — base pour un plan de travail correctif structuré (Phase 2)

## 1. Structure actuelle — Confirmation de compréhension

La table d'audit est **flat par conception**. Chaque `AuditLog` contient des colonnes nullable séparées :

- `adminId` → censé identifier l'admin auteur de l'action
- `enrollmentId` → censé identifier l'enrollment concerné
- `integrationId` → censé identifier l'intégration concernée
- `authAttemptId` → censé identifier la tentative d'authentification concernée
- `tenantId` → censé identifier le tenant de contexte

La stratégie actuelle est une **projection singulière** : à tout instant, l'audit ne capture que l'entité directement impliquée, laissant les autres colonnes à `NULL`.

---

## 2. Le problème que ça soulève

Prenons un exemple concret avec le cycle de vie d'une autorisation :

```
POST /api/v1/authorizations             → authorizationId = X, enrollmentId = NULL
GET  /api/v1/authorizations/{id}        → authorizationId = X, enrollmentId = NULL
POST /api/v1/authorizations/{id}/verify → authorizationId = X, enrollmentId = NULL
```

Pour répondre à la question *"Quels enrollments ont généré des authentifications échouées ce mois ?"*, il faut aujourd'hui faire un JOIN applicatif ou une requête en deux passes. **L'information existe dans le domaine** (une `Authorization` est liée à un `Enrollment`), mais elle n'est pas matérialisée dans l'audit.

---

## 3. Est-ce une bonne pratique d'enrichir ?

**Oui, avec des nuances importantes.**

### Arguments pour l'enrichissement

| Bénéfice | Détail |
|---|---|
| **Drill-down implicite** | Sans JOIN, on peut filtrer `WHERE enrollmentId = X AND authorizationId IS NOT NULL` |
| **Cohérence temporelle** | L'audit capture l'état *au moment de l'événement*. Si l'enrollment est supprimé plus tard, la trace reste |
| **Requêtes analytiques simplifiées** | Les SIEM, dashboards et rapports de conformité sont beaucoup plus efficaces |
| **Standard industrie** | C'est exactement ce que font les systèmes comme AWS CloudTrail, Okta System Log, Duo Audit |

### Arguments contre / risques

| Risque | Mitigation |
|---|---|
| **Complexité d'injection** | Il faut que les services propagent plus de contexte | → Gérable via un objet `AuditContext` |
| **Couplage entre domaines** | Le service Authorization devrait connaître l'enrollmentId | → Il le connaît déjà, c'est une FK |
| **Fausse richesse** | Remplir des champs sans cas d'usage défini = bruit | → Enrichir seulement ce qui a du sens sémantique |

---

## 4. Comparaison avec des projets similaires

Les systèmes IAM/MFA sérieux (**Keycloak**, **Duo**, **Okta**, **Auth0**) utilisent tous une approche **enrichie contextuellement** :

- **Keycloak** : chaque événement porte `userId`, `clientId`, `sessionId`, `ipAddress` — même si l'événement ne concerne directement qu'un seul de ces objets
- **Okta System Log** : chaque `LogEvent` a `actor`, `target[]`, `client`, `authenticationContext` — un tableau de targets pour refléter les entités impactées
- **Duo** : chaque log entry inclut `user`, `integration`, `factor` même si l'action n'en concerne qu'un

La tendance de l'industrie est **event-centric enrichi**, pas flat par entité.

---

## 5. Recommandation concrète pour Ezkey

**Approche intermédiaire pragmatique :**

> Enrichir les audits avec les IDs de contexte connus au moment de l'événement, sans aller chercher des données supplémentaires.

### Règle de base

```
Si l'entité auditée possède une FK vers une autre entité trackée,
alors inclure cet ID dans le log — c'est du contexte gratuit.
```

### Exemple appliqué

| Événement | authorizationId | enrollmentId | adminId |
|---|---|---|---|
| `AUTHORIZATION_REQUESTED` | ✅ X | ✅ depuis `Authorization.enrollmentId` | ❌ |
| `AUTHORIZATION_VERIFIED` | ✅ X | ✅ depuis `Authorization.enrollmentId` | ❌ |
| `AUTHORIZATION_FAILED` | ✅ X | ✅ depuis `Authorization.enrollmentId` | ❌ |
| `ENROLLMENT_CREATED` | ❌ | ✅ Y | ✅ si action admin |
| `ENROLLMENT_DELETED` | ❌ | ✅ Y | ✅ toujours admin |

---

## 6. Est-ce que ça dénature la stratégie actuelle ?

**Non.** Ça l'**élève**.

La stratégie flat actuelle est une étape d'implémentation initiale raisonnable pour un MVP (Phase 1). Pour la Phase 2 (qualité & bonnes pratiques), enrichir les audits est exactement le type d'amélioration qui apporte de la valeur sans changer d'architecture.

Ce n'est pas de la complexité inutile — c'est de la **valeur analytique différée** qu'on ne peut pas reconstruire après coup si les données ne sont pas capturées à la source.

---

## Résumé

| Question | Réponse |
|---|---|
| Compréhension de la stratégie actuelle | ✅ Confirmée — flat, projection singulière |
| Bonne pratique d'enrichir ? | ✅ Oui, c'est le standard industrie |
| Projets similaires font-ils ça ? | ✅ Keycloak, Okta, Duo — tous enrichis |
| Complexité inutile ? | ❌ Non — contexte gratuit disponible via FK existantes |
| Ça dénature la stratégie ? | ❌ Non — ça matérialise la Phase 2 |

---

## 7. Implementation status and enrichment rules (completed)

The phased implementation from the **Audit Log Enrichment Phases** plan has been completed. The following rules are now applied at audit emission points.

### 7.1 Enrichment rules

| Rule | Scope | Behaviour |
|------|--------|-----------|
| **adminId** | Admin API, Bearer auth | When the caller is authenticated with a Bearer token, `AdminPrincipal` is in `SecurityContext`; `adminId` is set on the audit log. When the caller uses an API key, `adminId` remains null (no admin actor). |
| **adminId** | Admin API, login/logout | For `ADMIN_LOGIN` and `ADMIN_LOGOUT`, `adminId` is set when the admin is known (from login response or from token resolution before logout). |
| **integrationId** | Auth API | For enrollment bind/verify and auth attempt pending/respond, `integrationId` is set from the enrollment (or auth attempt → enrollment) already in context. |
| **integrationId** | M2M API | For auth attempt create/wait/cancel, `integrationId` is set from the enrollment or API key context. |
| **integrationId** | Admin API AuthAttemptController | For `AUTH_ATTEMPT_CREATED` and `AUTH_ATTEMPT_CANCELLED`, `integrationId` is derived from the enrollment (via `resolveIntegrationIdFromEnrollment` / `resolveIntegrationIdFromAuthAttempt`). |
| **tenantId** | All | Unchanged: set where already resolved for visibility and filtering. |
| **enrollmentId** | All | Set when the audited entity or action involves an enrollment (already in place; reinforced where needed for consistency). |

### 7.2 Phase completion status

| Phase | Description | Status |
|-------|--------------|--------|
| Phase 1a | adminId on IntegrationController, EnrollmentController, TenantController | ✅ Done |
| Phase 1b | adminId on AuthAttemptController, ApiKeyController, AdminProvisioningController | ✅ Done |
| Phase 1c | adminId on AdminAuthController login/logout | ✅ Done |
| Phase 2 | integrationId on Auth API and M2M API audit events | ✅ Done |
| Phase 2b | integrationId on Admin API AuthAttemptController | ✅ Done |
| Phase 3 | Verification: AuditLogTenantVisibilityTest, EnrollmentControllerAuditTest (adminId assertions) | ✅ Done |
| Phase 4 | Documentation (this section) | ✅ Done |

### 7.3 Tests

- **EnrollmentControllerAuditTest**: Success-path tests now mock `AdminPrincipal` and assert `auditLog.getAdminId()` (e.g. 42).
- **AuditLogTenantVisibilityTest**: Functional tests for `tenant_id` population and tenant-scoped visibility; no regression after enrichment.
