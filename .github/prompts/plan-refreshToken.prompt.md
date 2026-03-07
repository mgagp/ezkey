## Plan: Refresh Token pour Admin API & CLI (v2)

**TL;DR** — Implémenter le pattern standard OAuth 2.0 Access Token + Refresh Token pour l'Admin API et le CLI (commandes et TUI). Le login MFA produit un **Refresh Token** (8h, long-lived) et un **Access Token** (15 min, short-lived). Le CLI/TUI utilise automatiquement le Refresh Token pour renouveler l'Access Token de manière transparente. QUIT invalide l'Access Token seulement ; LOGOUT invalide tout. Pas de Refresh Token Rotation pour le MVP. Durées configurables via `application.yml`. Documentation OpenAPI complète et mise à jour de la collection Postman.

### Clarification terminologique

| Ta description | Terme standard OAuth 2.0 | Rôle |
|---|---|---|
| "Token principal" | **Refresh Token** | Long-lived (8h), obtenu au login, sert à obtenir de nouveaux Access Tokens |
| "Token de travail" / "Refresh token" | **Access Token** | Short-lived (15 min), utilisé pour chaque appel API, renouvelé via le Refresh Token |

---

**Steps**

### 1. Migration Flyway — Schéma BD

Créer une nouvelle migration dans `ezkey-core/src/main/resources/db/migration/` :

- Ajouter colonne `token_type VARCHAR(20) NOT NULL DEFAULT 'ACCESS'` à `ezkey_admin_tokens` — valeurs : `'ACCESS'`, `'REFRESH'`, `'RECOVERY'`
- Ajouter colonne `parent_token_id INT NULL` avec FK vers `ezkey_admin_tokens(token_id)` — lie chaque Access Token à son Refresh Token parent
- Ajouter index `idx_admin_tokens_type` sur `(token_type, active) WHERE active = TRUE`
- Migrer les tokens existants : tous les tokens actuels deviennent `token_type = 'ACCESS'` (rétro-compatible)
- **Recovery tokens** : les tokens existants avec préfixe `ezkey_recovery_` doivent être migrés vers `token_type = 'RECOVERY'` via un `UPDATE` dans la même migration. Ceci assure la cohérence du modèle puisque les recovery tokens (émis par `AdminRecoveryService`, durée 30 min) partagent la même table `ezkey_admin_tokens`

### 2. Entity JPA — `AdminToken`

Modifier `AdminToken.java` dans `ezkey-core` :

- Ajouter champ `tokenType` (enum `TokenType`) mappé à `token_type`
- Ajouter relation `@ManyToOne` self-referencing `parentToken` vers `parent_token_id`
- Ajouter relation `@OneToMany` `childTokens` (Access Tokens enfants d'un Refresh Token)
- Créer enum `TokenType` avec valeurs `ACCESS`, `REFRESH`, `RECOVERY`
- **Coexistence avec les Recovery Tokens** : le `AdminRecoveryService` crée des tokens avec préfixe `ezkey_recovery_` dans la même table. Le nouveau champ `tokenType` doit être positionné à `RECOVERY` lors de leur création. Modifier `AdminRecoveryService` pour utiliser le nouveau champ au lieu de se fier uniquement au préfixe du bearer token. Le `parentToken` sera `NULL` pour les Recovery Tokens (ils ne font pas partie de la hiérarchie Access/Refresh)

### 3. Repository — `AdminTokenRepository`

Modifier `AdminTokenRepository.java` dans `ezkey-core` :

- Ajouter `findByBearerTokenAndActiveTrueAndTokenType(String bearerToken, String tokenType)`
- Ajouter `deactivateChildTokens(Integer parentTokenId)` — invalider les Access Tokens enfants
- Ajouter `deactivateTokenAndChildren(Integer tokenId)` — invalider Refresh Token + tous ses enfants (LOGOUT)

### 4. Configuration — Durées configurables

Modifier ou créer une classe de configuration dans `ezkey-admin-api` :

- Nouvelles propriétés Spring Boot :
  - `ezkey.admin.token.access-token-duration-minutes=15` (défaut : 15 min)
  - `ezkey.admin.token.refresh-token-duration-hours=8` (défaut : 8h)
- Modifier `AdminAuthService.java` — remplacer le `plusHours(24)` hardcodé par les propriétés injectées

### 5. Service Auth — Login modifié

Modifier `AdminAuthService.java` dans `ezkey-admin-api` :

- Refactorer `generateAndPersistToken()` en deux méthodes :
  - `generateRefreshToken(admin, request)` — `tokenType=REFRESH`, durée 8h
  - `generateAccessToken(admin, refreshToken, request)` — `tokenType=ACCESS`, durée 15 min, `parentTokenId` = ID du Refresh Token
- Le flow de login produit les deux tokens dans la réponse
- La rotation existante (`rotateTokensIfEnabled()`) invalide les anciens REFRESH tokens en cascade

### 6. Nouveau endpoint — Token Refresh

Ajouter dans `AdminAuthController.java` :

- `POST /api/v1/admin/auth/token/refresh` — corps : `{ "refreshToken": "ezkey_..." }`
  - Valide le Refresh Token (actif + non expiré)
  - Invalide l'Access Token précédent
  - Génère un nouveau Access Token enfant
  - Retourne le nouveau Access Token + `expiresAt`
  - **Exclu du filtre d'authentification** (le Refresh Token est dans le body, pas en header Bearer)

### 7. Documentation OpenAPI/Swagger — Nouveau endpoint

Annoter le nouveau endpoint `POST /api/v1/admin/auth/token/refresh` dans `AdminAuthController.java` en suivant le pattern existant des autres méthodes du même contrôleur :

- `@Operation` avec `summary` et `description` détaillée — expliquer le flow Access/Refresh, quand l'appeler, et le comportement en cas de Refresh Token expiré
- `@ApiResponses` couvrant :
  - `200` — Nouveau Access Token émis avec succès
  - `400` — Bad Request (body invalide, Refresh Token manquant)
  - `401` — Unauthorized (Refresh Token invalide ou expiré)
  - `429` — Rate limit
  - `500` — Internal Server Error
- `@io.swagger.v3.oas.annotations.parameters.RequestBody` avec `@Schema` pour le DTO de requête
- Aussi : **ajouter les annotations `@Operation`/`@ApiResponses` manquantes sur le `/logout`** existant (il n'en a pas actuellement, seulement du Javadoc)

Mettre à jour les annotations `@Operation` du endpoint `/login` pour documenter les **nouveaux champs de réponse** (`refreshToken`, `refreshTokenExpiresAt`) et expliquer que le `token` retourné est maintenant un Access Token short-lived.

Note importante sur la config OpenAPI dans `OpenApiConfig.java` : le `securitySchemes` s'applique globalement. Le nouveau endpoint `/token/refresh` devra utiliser `@SecurityRequirements({})` (vide) pour indiquer qu'il n'utilise **pas** le Bearer auth standard, puisque le Refresh Token est dans le body.

### 8. Mise à jour OpenAPI Spec

Après implémentation et démarrage de l'Admin API localement :

- **L'utilisateur** exécute `scripts/update-specs.sh --admin-only` (ou `scripts/update-specs.bat`) pour extraire le nouveau `specs/admin-api/openapi-spec.json`
- Le script crée automatiquement un backup, télécharge la spec live depuis `http://localhost:9080/api-docs`, la formate, et la distribue vers `ezkey-demo-app-acme/` et `ezkey-sdk/`

### 9. Mise à jour Collection Postman

Ajouter une nouvelle requête dans `postman/collections/v2.1/EZ Key Authentication Login admin.postman_collection.json` :

- **Nom** : `token refresh`
- **Méthode** : `POST`
- **URL** : `{{base_url_admin_api}}/api/v1/admin/auth/token/refresh`
- **Body** : `{ "refreshToken": "{{refreshToken}}" }`
- **Auth** : `noauth` (pas de Bearer, le Refresh Token est dans le body)
- **Test script** (`event.script.exec`) : extraire `token` (Access Token) et `expiresAt` dans les variables d'environnement Postman
- **Description** : documentation Markdown décrivant le flow, les cas d'erreur, exemples de réponse
- Positionner la requête **après** `passwordless-wait` et **avant** `logout` dans l'ordre de la collection (flow logique)

Mettre à jour la requête `login` existante :

- Ajouter dans le test script l'extraction des nouveaux champs `refreshToken` et `refreshTokenExpiresAt` en variables d'environnement
- Mettre à jour la description Markdown pour documenter les nouveaux champs de réponse

Mettre à jour `postman/environments/local.postman_environment.json` :

- Ajouter variable `refreshToken` (valeur vide, alimentée par le script de la requête login)

### 10. Service Auth — Logout modifié

Modifier le `logout()` dans `AdminAuthService` :

- Le LOGOUT invalide le Refresh Token + tous ses Access Tokens enfants via `deactivateTokenAndChildren()`

### 11. Filtre d'authentification — Distinction Access/Refresh

Modifier `AdminTokenAuthenticationFilter.java` dans `ezkey-admin-api` :

- Ne doit accepter que les **Access Tokens** en Bearer header
- Rejeter les Refresh Tokens utilisés comme Bearer (401)
- Exclure `/api/v1/admin/auth/token/refresh` du filtre

Modifier `AdminTokenValidationService.java` :

- `validateTokenWithRelations()` doit vérifier que le token est de type `ACCESS` — rejeter les REFRESH et RECOVERY tokens utilisés en Bearer header
- Ajouter méthode `validateRefreshToken(String bearerToken)` — utilisée exclusivement par le endpoint `/token/refresh`, vérifie type `REFRESH` + actif + non expiré

Modifier `SecurityConfig.java` :

- Ajouter `/api/v1/admin/auth/token/refresh` à la liste des endpoints publics (`.permitAll()`)

### 12. DTOs — Réponse login enrichie

Modifier `AdminLoginResponseDto.java` dans `ezkey-admin-api` :

- Ajouter : `refreshToken`, `refreshTokenExpiresAt`
- Le champ `token` existant reste l'Access Token (rétro-compatible)
- Créer `TokenRefreshRequestDto` (record avec `refreshToken`)
- Créer `TokenRefreshResponseDto` (record avec `accessToken`, `expiresAt`)
- Annoter les DTOs avec `@Schema` (description, example) pour le rendu OpenAPI

### 13. CLI Python — Structure `ezkey.json` mise à jour

Modifier `config_manager.py` dans `ezkey-cli-python` :

- Nouveaux champs : `refreshToken`, `refreshTokenExpiresAt`
- Méthodes : `set_refresh_token()`, `clear_refresh_token()`, `set_refresh_token_expires_at()`, `is_refresh_token_expired()`

### 14. CLI Python — Unification des clients HTTP

**Angle mort identifié** : le CLI Python possède **deux clients HTTP distincts** qui construisent chacun le header `Authorization: Bearer` indépendamment :
- `http_client.py` (`ezkey-cli-python/ezkey_cli/utils/`) — utilisé par les commandes CLI (mode sans TUI)
- `api_client.py` (`ezkey-cli-python/ezkey_cli/tui/`) — utilisé par le TUI

La logique de refresh doit être implémentée **une seule fois** dans un module commun :

- Créer un module `token_refresh.py` dans `ezkey-cli-python/ezkey_cli/auth/` avec la fonction `ensure_valid_token(config_manager)` :
  1. Access Token valide → retourner le token
  2. Access Token expiré + Refresh Token valide → appeler `POST /token/refresh`, stocker nouveau Access Token dans config, retourner le nouveau token
  3. Refresh Token expiré → lever une exception (le caller décide : re-auth en TUI, erreur en CLI)
- Modifier `http_client.py` — appeler `ensure_valid_token()` avant chaque requête (ou sur réception d'un 401)
- Modifier `api_client.py` — appeler `ensure_valid_token()` avant chaque requête (ou sur réception d'un 401)
- Les deux clients doivent **déléguer** à ce module commun plutôt que dupliquer la logique

### 15. CLI Python — Login modifié

Modifier `login_wizard.py` dans `ezkey-cli-python` :

- Stocker les deux tokens + expirations après login réussi

### 16. CLI Python — QUIT vs LOGOUT

Modifier `home.py` dans `ezkey-cli-python` :

- **LOGOUT** : appel API `/logout` → clear tous les tokens locaux → serveur invalide tout en cascade
- **QUIT** : clear seulement `bearerToken` + `tokenExpiresAt` localement, conserver `refreshToken` → au prochain lancement, auto-génération d'un nouveau Access Token (0-click)

### 17. TUI — Gestion expiration en session active

Modifier `app.py` dans `ezkey-cli-python` :

- Intercepter 401 pendant session active → appeler `ensure_valid_token()` → reprendre l'opération
- Si Refresh Token expiré → `QuickReAuthScreen`

### 18. Cleanup Service — Adaptation

Modifier `AdminTokenCleanupService.java` dans `ezkey-admin-api` :

- Nettoyer Access Tokens puis Refresh Tokens (ordre FK)
- Les Recovery Tokens (type `RECOVERY`) suivent le même cycle de nettoyage que les Access Tokens (pas de parent)

### 19. Opérations bulk existantes — Validation

Vérifier la compatibilité des opérations de révocation en masse qui existent déjà dans le code :

- `TenantService.java` — appelle `tokenRepository.deactivateAllTokensForTenant(tenantId)` lors de la désactivation d'un tenant. Cette query `UPDATE` met `active=false` pour TOUS les tokens du tenant, indépendamment du type. **Pas de changement requis** mais vérifier que le test `TenantServiceTest.java` couvre bien le cas avec des tokens de types mixtes.
- `AdminProvisioningService.java` — appelle `tokenRepository.deactivateAllTokensForAdmin(adminId)` lors de la désactivation d'un admin. Même logique. **Pas de changement requis** mais vérifier les tests `AdminProvisioningServiceTest.java`.
- `AdminTokenRotationProperties.java` — la rotation sur login (`rotateTokensIfEnabled()`) doit être vérifiée : elle appelle `deactivateAllTokensForAdmin()` qui désactive tout. S'assurer qu'elle est appelée **avant** la création du nouveau Refresh Token + Access Token.

### 20. Tests d'intégration (`ezkey-tests`) — Mise à jour

**Angle mort identifié** : les utilitaires de tests d'intégration extraient le token de la réponse de login et vont casser avec la nouvelle structure de réponse.

Fichiers à modifier :

- `AuthTokenManager.java` (`ezkey-tests/src/test/java/org/ezkey/tests/util/`) — `getAdminToken()` retourne un `String` (bearer token). Doit extraire l'**Access Token** de la nouvelle réponse. Ajouter gestion du Refresh Token pour auto-renouveler si les tests durent > 15 min.
- `AdminBootstrapService.java` (`ezkey-tests/src/test/java/org/ezkey/tests/util/`) — `createAdminToken()` parse la réponse de login. Doit extraire `token` (Access Token) et optionnellement `refreshToken`.
- `AdminTokenCreationTest.java` (`ezkey-tests/src/test/java/org/ezkey/tests/security/admin/`) — assertions sur la réponse de login. Ajouter assertions pour la présence de `refreshToken` et `refreshTokenExpiresAt`.

Fichiers à vérifier (probablement aucun changement) :

- `TestDataFactory.java`, `TenantAdminTestHelper.java`, `DemoDeviceEnrollmentWriter.java` — ces utilitaires utilisent le token comme un `String` opaque dans le header `Authorization: Bearer`. Tant que `AuthTokenManager` retourne un Access Token valide, ils fonctionnent sans modification.

### 21. Constantes et préfixes

Modifier `AdminAuditConstants.java` dans `ezkey-admin-api` :

- Ajouter constantes pour les types de token si nécessaire (bien que l'enum `TokenType` soit le mécanisme principal)
- Vérifier que `RECOVERY_TOKEN_PREFIX = "ezkey_recovery_"` reste cohérent — le préfixe est utilisé dans `AdminEnrollmentController` pour les validations côté endpoint. Ce mécanisme de préfixe **coexiste** avec le nouveau champ `token_type` (défense en profondeur)

---

**Verification**

1. **Tests unitaires** : `AdminAuthServiceTest` — login produit les 2 tokens, refresh endpoint fonctionne, logout cascade, recovery tokens utilisent le nouveau champ `tokenType`
2. **Tests d'intégration** : `AdminAuthControllerTest` — endpoints `/login`, `/token/refresh`, `/logout`
3. **Tests d'intégration (ezkey-tests)** : vérifier que `AuthTokenManager`, `AdminBootstrapService` et `AdminTokenCreationTest` fonctionnent avec la nouvelle structure de réponse
4. **Tests de sécurité** : vérifier qu'un Refresh Token dans le header `Authorization: Bearer` est rejeté (401). Vérifier qu'un Recovery Token ne peut pas être utilisé au endpoint `/token/refresh`.
5. **Tests bulk** : vérifier que la désactivation d'un tenant/admin invalide tous les types de tokens (ACCESS + REFRESH + RECOVERY)
6. **OpenAPI** : Démarrer l'Admin API → vérifier `/api-docs` contient le nouveau endpoint documenté → exécuter `scripts/update-specs.sh --admin-only`
7. **Postman** : Importer la collection mise à jour → exécuter le flow login → token refresh → logout
8. **Test CLI** : login → opérations → attendre 15+ min → vérifier auto-refresh → QUIT → re-lancement → accès direct → LOGOUT → plus d'accès. Tester via les **deux clients** (commande CLI directe + TUI)
9. **Build** : `mvn clean verify` + `mvn checkstyle:check`
10. **Migration** : vérifier que les tokens existants avec préfixe `ezkey_recovery_` sont correctement migrés vers `token_type = 'RECOVERY'`

**Decisions**

- **Terminologie OAuth 2.0 standard** : Refresh Token (long-lived) + Access Token (short-lived)
- **Durées par défaut** : Refresh Token = 8h, Access Token = 15 min — configurables
- **Même pattern CLI + TUI** : un seul mécanisme
- **Pas de Refresh Token Rotation au MVP**
- **QUIT** conserve le Refresh Token, invalide/clear l'Access Token
- **Endpoint refresh hors filtre auth** : Refresh Token dans le body, pas en Bearer header
- **OpenAPI** : annotations complètes sur le nouveau endpoint + corrections `/logout`, spec extraite par l'utilisateur via `update-specs.sh`
- **Postman** : nouvelle requête `token refresh` + mise à jour du login pour extraire le Refresh Token

**Impact Analysis — Fichiers touchés**

| Couche | Fichiers à modifier | Risque |
|---|---|---|
| Core Domain | `AdminToken.java`, `AdminTokenRepository.java`, nouvelle migration Flyway | Moyen — schéma BD |
| Admin API Services | `AdminAuthService.java`, `AdminTokenValidationService.java`, `AdminTokenCleanupService.java`, `AdminRecoveryService.java` | Moyen — logique auth |
| Admin API Security | `AdminTokenAuthenticationFilter.java`, `SecurityConfig.java` | Moyen — filtre auth |
| Admin API Config | `AdminTokenRotationProperties.java` (vérif), nouvelle config durées | Faible |
| Admin API DTOs | `AdminLoginResponseDto.java`, 2 nouveaux DTOs | Faible |
| Admin API Controller | `AdminAuthController.java` | Moyen — nouveau endpoint |
| Admin API Constants | `AdminAuditConstants.java` | Faible |
| CLI Python | `config_manager.py`, `http_client.py`, `api_client.py`, `app.py`, `login_wizard.py`, `home.py`, nouveau `token_refresh.py` | Moyen — deux clients à harmoniser |
| Tests intégration | `AuthTokenManager.java`, `AdminBootstrapService.java`, `AdminTokenCreationTest.java` | Moyen — casseront sans mise à jour |
| Tests unitaires | `TenantServiceTest.java`, `AdminProvisioningServiceTest.java` (vérif) | Faible |
| Postman | Collection auth + environnement | Faible |
| Auto-régénéré | OpenAPI spec (`update-specs.sh`), SDK JavaScript | Aucun — auto |
| Pas de changement | `AdminPrincipal.java`, `AdminEnrollmentController.java`, API Keys, `TestDataFactory.java`, `TenantAdminTestHelper.java`, `DemoDeviceEnrollmentWriter.java`, Requestly configs | Aucun |

**Total : ~22 fichiers à modifier, ~6 à vérifier, ~2 à créer**

---

## Addendum — Analyse stratégique croisée (mars 2026)

> Cette section confronte le plan Refresh Token aux conclusions de l'analyse stratégique du système de jetons (`plan-adminAuthTokenStrategyAudit.prompt.md`), qui a évalué l'ensemble de l'approche d'authentification admin d'ezkey.

### Contexte : ce que l'audit stratégique a conclu

L'audit a statué que le système actuel (token opaque unique, 24h TTL, DB-backed) est :

- **Moderne** — utilisé par GitHub, GitLab, Grafana, Vault, Bitwarden
- **Aligné avec les best practices** — OWASP et NIST recommandent les sessions server-side pour les interfaces admin
- **Suffisant** — sécurisé, testé, couvre tous les flux
- **Pas un cul-de-sac** — chemin d'évolution clair (OIDC, cache, JWT optionnel)
- **Pas de l'escalation of commitment** — décision pragmatique documentée

La recommandation était : **stay the course**, avec trois améliorations optionnelles de priorité supérieure (token hashing en DB, OIDC admin login, cache de validation).

### Le Refresh Token est-il une bonification substantielle ?

#### Ce qu'il apporte réellement

| Bénéfice | Poids | Commentaire |
|----------|-------|-------------|
| **Réduction de la fenêtre d'exposition** (15 min vs 24h) | **Fort** | Si un Access Token est volé, l'impact est limité à 15 min. C'est le seul argument de sécurité solide. |
| **UX CLI : QUIT préserve la session** | **Moyen** | Relancer le CLI sans re-MFA est un vrai gain d'ergonomie pour les utilisateurs fréquents. |
| **Alignement terminologique OAuth 2.0** | **Faible** | Les développeurs reconnaissent le pattern, mais ezkey n'est pas un serveur OAuth. C'est cosmétique. |
| **Fondation pour OIDC futur** | **Faible** | L'OIDC peut émettre un token opaque interne sans pré-requis de refresh token. L'un n'implique pas l'autre. |

#### Ce qu'il coûte

| Coût | Poids | Commentaire |
|------|-------|-------------|
| **22 fichiers modifiés, 2 créés** | **Élevé** | Surface de changement très large pour un projet en développement actif. |
| **Complexité du filtre de sécurité** | **Moyen** | Le filtre doit distinguer ACCESS/REFRESH/RECOVERY. Trois chemins de validation au lieu d'un. |
| **Deux tokens dans la réponse de login** | **Moyen** | Breaking change pour le Tenant UI, le CLI, les tests d'intégration, Postman, le SDK. |
| **Endpoint `/token/refresh` hors filtre auth** | **Moyen** | Surface d'attaque additionnelle (endpoint public qui accepte un token dans le body). |
| **Parent-child token en DB** | **Faible** | Self-referencing FK + cascade logique = modèle de données plus complexe. |
| **Tests : régression transversale** | **Élevé** | Les tests d'intégration (`ezkey-tests`) casseront. Effort de mise à jour non trivial. |

### La fenêtre d'exposition de 15 min peut-elle être obtenue plus simplement ?

**Oui.** Il existe une alternative beaucoup plus légère qui produit 80% du bénéfice sécuritaire :

| Approche | Effort | Fenêtre d'exposition | Fichiers touchés |
|----------|--------|---------------------|-----------------|
| **Statu quo** (token 24h) | Aucun | 24 heures | 0 |
| **Réduire le TTL à 1-2h + sliding expiration** | Faible (~4 fichiers) | 1-2 heures d'inactivité | AdminAuthService, AdminTokenValidationService, config, tests |
| **Plan Refresh Token complet** | Élevé (~24 fichiers) | 15 minutes | 22 modifiés + 2 créés |

La **sliding expiration** (prolonger le TTL à chaque requête validée) élimine le problème "session expire pendant que je travaille" sans nécessiter un deuxième token. C'est le pattern utilisé par la majorité des admin consoles comparables (Grafana, GitLab, Jenkins).

### Le QUIT vs LOGOUT du CLI peut-il être découplé ?

**Oui.** Le cas d'usage CLI (QUIT conserve la session, LOGOUT invalide tout) ne requiert pas de Refresh Token. Il suffit de :

1. QUIT : ne pas appeler `/logout` côté serveur, conserver le token en config locale
2. LOGOUT : appeler `/logout` + effacer la config locale
3. Relance : vérifier si le token local est encore valide (non expiré) → accès direct

Avec un TTL de 2h + sliding expiration, un développeur qui fait QUIT et revient dans l'heure reprend sans re-MFA. C'est exactement l'UX souhaitée, sans la machinerie Refresh Token.

### Comparaison avec les projets comparables

L'audit a identifié que les projets comparables à ezkey (Grafana, GitLab, privacyIDEA, Vault, Bitwarden) **n'utilisent PAS le pattern Access/Refresh pour leurs sessions admin**. Ce pattern est caractéristique de :

- **Serveurs OAuth 2.0 / OIDC** (Keycloak, Auth0) — ezkey n'en est pas un
- **APIs publiques avec des millions de clients** — ezkey sert des dizaines d'admins
- **Mobile apps avec des sessions de plusieurs semaines** — pas le cas d'usage d'ezkey

Implémenter Access/Refresh pour une console admin et un CLI est **techniquement correct mais disproportionné** par rapport au profil du produit.

### Risque de sur-ingénierie

L'audit identifie spécifiquement que JWT + refresh tokens serait "over-engineering for ezkey's current and near-term needs". Le plan Refresh Token, même avec des tokens opaques (pas JWT), hérite de la même critique :

- Il ajoute un mécanisme OAuth 2.0 à un produit dont la proposition de valeur est la **simplicité**
- Le PRD d'ezkey dit : "solves 90% of the problem with 10% of the effort" — le Refresh Token fait l'inverse ici (10% de gain pour 90% d'effort supplémentaire)
- Les développeurs évaluant ezkey ne seront pas impressionnés par un Refresh Token ; ils seront impressionnés par la simplicité du login passwordless

### Timing : faut-il le faire avant la sortie initiale ?

**Non.** Arguments :

1. **Le système actuel est jugé suffisant** par l'audit stratégique
2. **Aucun comparable** ne l'exige à ce stade
3. **22 fichiers modifiés** = risque de régression élevé juste avant une release
4. **Le sliding expiration** (4 fichiers) donne 80% du bénéfice pour 15% de l'effort
5. **Le Refresh Token ne sera pas un critère d'adoption** — la qualité du MFA passwordless et la simplicité d'intégration le seront

### Verdict

| Question | Réponse |
|----------|---------|
| Le plan est-il bien conçu ? | **Oui** — exhaustif, couvre les edge cases, terminologie correcte |
| Apporte-t-il une valeur substantielle ? | **Marginale** — le gain sécuritaire est réel mais atteignable plus simplement |
| Est-il pragmatique ? | **Non** — 22 fichiers pour un bénéfice marginal viole le principe de pragmatisme d'ezkey |
| Est-il suffisamment simple ? | **Non** — il complexifie le modèle de données, le filtre de sécurité, et les tests |
| Faut-il le faire avant la release initiale ? | **Non** — risque de régression disproportionné |
| Faut-il le faire rapidement après ? | **Non** — prioriser token hashing en DB et sliding expiration |
| Faut-il le faire un jour ? | **Peut-être** — si le CLI devient un outil quotidien à grande échelle, le pattern pourrait se justifier |
| Est-ce de l'escalation of commitment de l'abandonner ? | **Non** — c'est un plan, pas du code livré. L'abandonner est pragmatique |

### Recommandation

**Différer le plan Refresh Token.** À la place, pour la prochaine itération :

1. **Sliding expiration** (~4 fichiers, 1-2 jours d'effort) — prolonger le TTL du token à chaque requête validée. Résout le problème "session expire pendant que je travaille" sans token additionnel.
2. **TTL configurable réduit** (déjà supporté) — passer le défaut de 24h à 2-4h. Réduit la fenêtre d'exposition de 85-92% sans aucun changement d'architecture.
3. **CLI QUIT/LOGOUT découplé** (~2 fichiers CLI) — QUIT conserve le token, LOGOUT appelle `/logout`. Fonctionne avec le token unique actuel.
4. **Token hashing en DB** (~3 fichiers) — stocker `SHA-256(token)` au lieu du token en clair. Priorité sécuritaire supérieure au Refresh Token selon l'audit.

Ces quatre actions combinées coûtent ~10 fichiers (vs 24) et produisent un résultat équivalent ou supérieur en termes de sécurité et d'UX.

**Le plan Refresh Token reste un document de référence valide** si les besoins évoluent (adoption massive du CLI, intégration OIDC, exigences de conformité spécifiques). Il n'est pas invalidé — il est différé.

---

## Implementation — Sliding expiration (March 2026)

The recommended **sliding expiration** and **configurable TTL** have been implemented:

- **Config:** `ezkey.admin.token.expiration-hours=2` (default 2h). Used for initial token TTL at login and for the sliding window on each validated request.
- **AdminTokenRotationProperties:** New `expirationHours` (default 2); `AdminAuthService` uses it in `generateAndPersistToken()`.
- **AdminTokenValidationService:** `updateTokenLastUsed()` now extends `expiresAt` to `now + expirationHours` for normal admin tokens; recovery tokens (prefix `ezkey_recovery_`) are not extended.
- **Application config:** Property added in `application.properties`, `application-docker.properties`, `application-windows.properties`, `application-native.properties`.
- **Tests:** `AdminTokenValidationServiceTest` updated (constructor + sliding tests: normal token extended, recovery token not extended).
