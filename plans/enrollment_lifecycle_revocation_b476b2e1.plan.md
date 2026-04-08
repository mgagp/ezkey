---
name: Enrollment Lifecycle Revocation
overview: "Revise and extend the Enrollment Lifecycle Revocation plan to close blind spots: error handling specification, self-revocation guards, peer revocation policy, bearer token invalidation on revocation, and non-impersonation boundary clarification."
todos:
  - id: phase1-status-enum
    content: Ajouter REVOKED à EnrollmentStatus + event types ENROLLMENT_REVOKED/DEACTIVATED/REACTIVATED/AUTH_ATTEMPT_BLOCKED dans EventType
    status: completed
  - id: phase1-audit-fields
    content: Ajouter champs d'audit (deactivatedAt, deactivatedByAdmin, revokedAt, revokedByAdmin) sur Enrollment + migration Flyway
    status: completed
  - id: phase1-revocation-service
    content: Créer EnrollmentRevocationService avec méthodes revoke/deactivate/reactivate, self-revocation guard, peer revocation policy, et protection bulk contre system integrations
    status: completed
  - id: phase1-access-control
    content: Ajouter canRevokeEnrollment() dans AccessControlService — étend canAccessEnrollment avec la garde self-revocation
    status: completed
  - id: phase1-endpoints
    content: Ajouter POST /api/v1/enrollments/{id}/revoke, /deactivate, /reactivate + POST /api/v1/integrations/{id}/enrollments/revoke-all dans EnrollmentController
    status: completed
  - id: phase1-authattempt-gate
    content: "Corriger AuthAttemptService.create() + M2M resolveEnrollmentId() Path 1: vérifier active=true AND status=VERIFIED, lever EnrollmentInactiveException → 403, log WARN, audit ENROLLMENT_AUTH_ATTEMPT_BLOCKED"
    status: completed
  - id: phase1-bearer-token-invalidation
    content: Invalider les bearer tokens admin lors d'une révocation d'enrollment admin (AdminTokenRepository) + ajouter check enrollment status dans AdminTokenValidationService.validateTokenWithRelations()
    status: completed
  - id: phase1-admin-auth-coverage
    content: "Vérifier AdminAuthService: confirmer que le gate AuthAttemptService couvre le flux d'auth admin, sinon ajouter check explicite"
    status: completed
  - id: phase1-tests
    content: "Tests unitaires + intégration: revoke/deactivate/reactivate, self-revocation guard (403), peer revocation, enrollment inactif bloque auth attempt, bearer token invalidé"
    status: pending
isProject: false
---

# Enrollment Lifecycle Revocation — Plan révisé

## Résumé des angles morts identifiés dans l'analyse initiale

Le plan original est solide dans ses 4 phases, mais comporte **8 angles morts critiques** détaillés ci-dessous, suivis du plan de travail révisé.

---

## Angles morts

### 1. Gap AuthAttemptService — spécification incomplète (Phase 1, étape 6)

Le plan note qu'il faut "corriger le gap" mais ne précise pas le comportement attendu.

**Situation actuelle dans le code:**

- `resolveEnrollmentId()` dans `M2mAuthAttemptController` — chemin **Path 1** (`enrollmentId` direct) : retourne l'ID sans aucune validation du statut ou du flag `active`. Seul le chemin **Path 2** (`userIdentifier`) applique `status=VERIFIED AND active=true`.
- `AuthAttemptService.create()` ne vérifie ni `enrollment.getActive()` ni `enrollment.getStatus()` avant de créer l'auth attempt.

**Ce qui doit être précisé:**

- **HTTP Status à retourner**: `403 Forbidden` — le serveur comprend la requête mais refuse de l'exécuter car l'enrollment n'est plus autorisé à s'authentifier. (Pas `400` = format invalide; pas `422` = sémantique trop générique; `403` = refus d'accès, aligné avec Duo/Okta.)
- **Corps de la réponse**: `{"error": "enrollment_inactive", "message": "Enrollment is not active for authentication."}`
- **Log SLF4J**: Niveau `WARN` — c'est un événement de sécurité suspect (l'appelant connaît un enrollmentId révoqué), mais pas une erreur système. Un `ERROR` serait sur-réactif; un `INFO` serait insuffisant.
- **Audit log event**: Nouveau type `ENROLLMENT_AUTH_ATTEMPT_BLOCKED` dans `EventType.java` — pour traçabilité SOC 2 CC7.1.
- **Appliquer à deux endroits**: (a) `AuthAttemptService.create()` centralement, pour couvrir tous les appelants; (b) `resolveEnrollmentId()` dans `M2mAuthAttemptController` pour le Path 1.

### 2. Absence totale de garde contre la self-révocation

`AccessControlService.canAccessEnrollment()` vérifie uniquement l'appartenance au tenant — aucune protection contre la révocation de son propre enrollment.

**Risque**: Un admin peut se verrouiller hors du système en révoquant son propre enrollment.

**Ce qui doit être ajouté** dans le service de révocation:

```java
// Pseudo-code de la garde
private void assertNotSelfRevocation(AdminPrincipal principal, Enrollment enrollment) {
    EzkeyAdmin admin = adminRepository.findById(principal.adminId())...;
    if (admin.getMfaEnrollment() != null
        && admin.getMfaEnrollment().getEnrollmentId().equals(enrollment.getEnrollmentId())) {
        throw new SelfRevocationNotAllowedException("Cannot revoke your own MFA enrollment.");
    }
}
```

- **HTTP status**: `403 Forbidden` avec `{"error": "self_revocation_not_allowed"}`
- S'applique à **Global Admin** et **Tenant Admin** sans exception.

### 3. Politique de révocation entre pairs (peer revocation) — non définie

**Scénario non traité**: Tenant Admin A veut révoquer l'enrollment de Tenant Admin B (même tenant).

**Ma recommandation: Autoriser, avec contraintes.**

Justification et référence marché:

- **Okta (référence)**: Un "Org Admin" peut réinitialiser les facteurs MFA de n'importe quel utilisateur y compris d'autres admins de même niveau.
- **Azure AD (référence)**: Le rôle "Authentication Administrator" peut réinitialiser les méthodes d'authentification des non-admins et des admins de même niveau.
- **Cohérence avec l'existant EZKey**: Le `DELETE /api/v1/enrollments/{id}` (hard delete) n'a actuellement **aucune garde** contre la suppression d'un enrollment de pair admin — ce comportement est donc déjà implicitement permis.
- **Raison opérationnelle**: Si le Tenant Admin B est compromis, le Tenant Admin A doit pouvoir agir immédiatement sans escalader vers un Global Admin (ce qui peut prendre du temps).

**Règles résultantes à implémenter:**


| Acteur       | Cible                                          | Deactivate | Revoke   | Condition                    |
| ------------ | ---------------------------------------------- | ---------- | -------- | ---------------------------- |
| Global Admin | Son propre enrollment                          | INTERDIT   | INTERDIT | Self-revocation guard        |
| Global Admin | Autre Global Admin enrollment                  | Autorisé   | Autorisé | Audité                       |
| Global Admin | Tenant Admin enrollment                        | Autorisé   | Autorisé | Audité                       |
| Global Admin | User enrollment (n'importe quelle intégration) | Autorisé   | Autorisé | Audité (voir angle mort #5)  |
| Tenant Admin | Son propre enrollment                          | INTERDIT   | INTERDIT | Self-revocation guard        |
| Tenant Admin | Peer Tenant Admin enrollment (même tenant)     | Autorisé   | Autorisé | `reason` obligatoire, audité |
| Tenant Admin | User enrollment (son tenant uniquement)        | Autorisé   | Autorisé | Audité                       |
| Tenant Admin | Enrollment hors de son tenant                  | INTERDIT   | INTERDIT | Scoping existant             |


**Implémentation**: Méthode dédiée `canRevokeEnrollment(auth, enrollmentId)` dans `AccessControlService`, distincte de `canAccessEnrollment()` (lecture seule). La logique de scoping existante s'applique; la garde self-revocation est ajoutée en surcouche.

### 4. Bearer tokens non invalidés lors de la révocation

`AdminTokenValidationService.validateTokenWithRelations()` vérifie si le **tenant** est actif, mais **pas si l'enrollment MFA de l'admin est actif**.

**Conséquence**: Révoquer l'enrollment d'un admin n'a aucun effet immédiat si cet admin possède un bearer token valide — il continue à fonctionner jusqu'à l'expiration du token.

**Deux corrections nécessaires:**

1. **Check enrollment status à chaque validation de token** (dans `AdminTokenValidationService`):

```java
// Ajouter après la vérification du tenant:
Enrollment mfaEnrollment = admin.getMfaEnrollment();
if (mfaEnrollment != null && !Boolean.TRUE.equals(mfaEnrollment.getActive())) {
    logger.warn("Token rejected: MFA enrollment inactive for admin: {}", admin.getUsername());
    return Optional.empty();
}
```

1. **Invalidation immédiate des tokens actifs** lors de la révocation: Dans le service de révocation, si l'enrollment cible appartient à un admin (`ezkey_admin.mfa_enrollment_id = enrollmentId`), désactiver tous les `AdminToken` actifs de cet admin. Référence: `AdminTokenCleanupService` ou `AdminTokenRepository.deactivateByAdminId(adminId)`.

### 5. Frontière du principe de non-impersonation — à clarifier explicitement

Le plan ne tranche pas la question: **un Global Admin peut-il révoquer l'enrollment d'un usager ordinaire** dans une intégration non-système?

**Ma réponse: OUI, et ce n'est pas de l'impersonation.**

Distinction fondamentale:

- **Impersonation** = utiliser l'enrollment d'autrui pour s'authentifier **en tant que cet utilisateur**. Interdit, protégé par l'architecture (chaque admin a son propre enrollment sur la system integration).
- **Révocation administrative** = désactiver l'accès d'un enrollment. C'est un acte d'administration, pas d'usurpation d'identité.

**Règle**: Un Global Admin PEUT révoquer n'importe quel enrollment (incluant les usagers ordinaires de toute intégration). Un Tenant Admin PEUT révoquer les enrollments dans les intégrations de son tenant.

Cela doit être **documenté explicitement** dans le Javadoc du service de révocation et dans `ENDPOINT.md`.

### 6. Absence de protection spécifique pour les enrollments système (system integrations)

Les enrollments admin sont tous liés à la **system integration** (`isSystemIntegration=true`). Il n'y a aucune garde au niveau du endpoint de révocation pour ce type d'intégration.

La self-revocation guard (angle mort #2) couvre partiellement ce cas, mais il faut aussi s'assurer que le **bulk revocation** (`POST /integrations/{id}/enrollments/revoke-all`) ne peut pas être appliqué à une system integration — ce serait catastrophique.

**Garde à ajouter**: Dans `EnrollmentRevocationService.revokeAll(integrationId)`, vérifier `integration.getIsSystemIntegration()` et rejeter avec `403` si vrai.

### 7. Comportement du flux d'authentification admin lors d'une révocation (AdminAuthService)

Quand un admin tente de se connecter (flux passwordless via `AdminAuthService`), le flux crée un auth attempt via le system integration. Si l'enrollment de cet admin est révoqué:

- Le check `AdminTokenValidationService` (angle mort #4) empêche l'utilisation d'un token existant ✓
- Mais `AdminAuthService` crée-t-il un auth attempt en vérifiant `active`? À vérifier — peut-être que le flux auth admin crée des auth attempts directement via `AuthAttemptService.create()` sans passer par le check M2M.

**Action**: Vérifier `AdminAuthService` et s'assurer que le check `active` dans `AuthAttemptService.create()` (angle mort #1) couvre bien ce flux.

### 8. Paramètre `reason` pour la révocation entre pairs — non défini dans le plan

Le plan mentionne un paramètre `reason` pour le `DELETE` endpoint (pattern existant). Mais pour `POST /revoke` et `POST /deactivate`, la politique sur `reason` n'est pas définie.

**Recommandation**:

- `POST /revoke`: `reason` **obligatoire** (min 10 char) — la révocation est irréversible, elle mérite une justification tracée.
- `POST /deactivate`: `reason` **optionnel** — désactivation réversible, moins critique.
- `POST /reactivate`: `reason` **optionnel**.
- Pour la révocation d'un **enrollment admin** (peer revocation): `reason` **obligatoire** quel que soit le type d'opération.

---

## Plan de travail Phase 1 révisé

### Étapes originales (conservées intactes)

1. Ajouter `REVOKED` à `EnrollmentStatus`
2. Ajouter event types d'audit: `ENROLLMENT_REVOKED`, `ENROLLMENT_DEACTIVATED`, `ENROLLMENT_REACTIVATED`
3. Créer `POST /api/v1/enrollments/{id}/revoke`
4. Créer `POST /api/v1/enrollments/{id}/deactivate` et `POST /api/v1/enrollments/{id}/reactivate`
5. Ajouter champs d'audit sur Enrollment: `deactivatedAt`, `deactivatedByAdmin`, `revokedAt`, `revokedByAdmin`
6. Corriger le gap dans `AuthAttemptService.create()` (actif check)
7. Ajouter endpoint bulk `POST /api/v1/integrations/{id}/enrollments/revoke-all`
8. Migration Flyway pour les nouveaux champs d'audit

### Nouvelles étapes ajoutées

**9. Spécifier la réponse d'erreur pour enrollment inactif** (`ezkey-core`, `ezkey-m2m-api`)

- `AuthAttemptService.create()`: lever une nouvelle exception `EnrollmentInactiveException` (ou `EnrollmentNotActiveException`) → mapée sur `403 Forbidden` dans le `GlobalExceptionHandler`
- M2M: Path 1 dans `resolveEnrollmentId()` doit charger l'enrollment et vérifier `active=true AND status=VERIFIED`
- Log SLF4J: `WARN` avec enrollmentId masqué
- Nouvel EventType: `ENROLLMENT_AUTH_ATTEMPT_BLOCKED`

**10. Implémenter la self-revocation guard** (`ezkey-admin-api`, `AccessControlService` ou nouveau `EnrollmentRevocationService`)

- Méthode `canRevokeEnrollment(auth, enrollmentId)` — extends la logique `canAccessEnrollment()` avec la garde self-revocation
- `assertNotSelfRevocation()` dans le service de révocation
- `403 Forbidden` + `{"error": "self_revocation_not_allowed"}`

**11. Implémenter la politique de peer revocation** (`EnrollmentRevocationController` ou `EnrollmentController`)

- Utiliser `canRevokeEnrollment()` pour tous les endpoints revoke/deactivate
- `reason` obligatoire sur `POST /revoke` et sur toute opération ciblant un enrollment admin

**12. Invalider les bearer tokens lors de la révocation d'un admin** (`ezkey-admin-api`)

- Dans `EnrollmentRevocationService`: si l'enrollment cible est un `mfa_enrollment_id` d'un admin, appeler `adminTokenRepository.deactivateAllByAdminId(adminId)` ou équivalent
- Dans `AdminTokenValidationService.validateTokenWithRelations()`: ajouter check enrollment status

**13. Protéger bulk revocation contre les system integrations** (`EnrollmentRevocationService`)

- `revokeAll(integrationId)`: vérifier `integration.getIsSystemIntegration()` → rejeter `403` si vrai

**14. Vérifier AdminAuthService** (`ezkey-admin-api`)

- Confirmer que le flux d'auth admin passe bien par `AuthAttemptService.create()` et bénéficiera du check ajouté à l'étape 9
- Sinon, ajouter le check explicitement dans `AdminAuthService`

---

## Fichiers clés impactés (Phase 1 révisée)

**ezkey-core:**

- `[EnrollmentStatus.java](ezkey-core/src/main/java/org/ezkey/enrollment/domain/EnrollmentStatus.java)` — ajouter `REVOKED`
- `[Enrollment.java](ezkey-core/src/main/java/org/ezkey/enrollment/domain/entity/Enrollment.java)` — champs d'audit
- `[AuthAttemptService.java](ezkey-core/src/main/java/org/ezkey/authattempt/service/AuthAttemptService.java)` — gate enrollment active + VERIFIED
- `EventType.java` — nouveaux types d'audit
- `EnrollmentRevocationService.java` (nouveau) — logique revoke/deactivate/reactivate

**ezkey-admin-api:**

- `[EnrollmentController.java](ezkey-admin-api/src/main/java/org/ezkey/admin/controller/EnrollmentController.java)` — nouveaux endpoints
- `[AccessControlService.java](ezkey-admin-api/src/main/java/org/ezkey/admin/security/AccessControlService.java)` — `canRevokeEnrollment()`
- `[AdminTokenValidationService.java](ezkey-admin-api/src/main/java/org/ezkey/admin/service/AdminTokenValidationService.java)` — check enrollment status
- `AdminAuthService.java` — vérification coverage

**ezkey-m2m-api:**

- `M2mAuthAttemptController.java` — Path 1 de `resolveEnrollmentId()` — valider active+VERIFIED

**ezkey-migration:**

- Nouvelle migration Flyway: champs `deactivated_at`, `deactivated_by_admin_id`, `revoked_at`, `revoked_by_admin_id` sur `ezkey_enrollment`

