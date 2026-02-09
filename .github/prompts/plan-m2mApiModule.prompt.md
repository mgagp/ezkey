# Plan: Extract M2M API Key Auth into Dedicated Sub-Module (`ezkey-m2m-api`)

## Overview

Créer un module Spring Boot dédié aux 3 opérations M2M (create/wait/cancel auth attempt) authentifiées par clé d'API. Le module dépend d'`ezkey-core` pour toute la logique métier. Les classes de sécurité (`ApiKeyAuthenticationFilter`, `AccessControlService`, `RateLimitService`) sont copiées dans le nouveau module — refactoring dans core remis à plus tard. L'admin-api conserve ses endpoints M2M avec un flag `@ConditionalOnProperty` pour les désactiver si souhaité. Pas de compilation native pour l'instant.

## Steps

### Step 1 — Créer le module Maven `ezkey-m2m-api`

Nouveau dossier avec `pom.xml` enfant du parent `ezkey-parent`. Dépendances réduites : `ezkey-core`, `spring-boot-starter-webmvc`, `spring-boot-starter-security`, `bucket4j-caffeine`, `caffeine`, `springdoc-openapi`, `spring-boot-starter-actuator`, `micrometer-registry-prometheus`. Pas de `spring-security-oauth2-jose`, pas de ZXing, pas de `@EnableScheduling`. Ajouter `<module>ezkey-m2m-api</module>` dans le `pom.xml` racine et le `spring-boot-maven-plugin` avec `combine.self="override"` comme dans les autres modules.

### Step 2 — Créer `M2mApplication` et `application.properties`

Classe principale avec `@SpringBootApplication(scanBasePackages = {"org.ezkey.m2m", "org.ezkey.authattempt", "org.ezkey.enrollment", "org.ezkey.integration", "org.ezkey.audit", "org.ezkey.config", "org.ezkey.security", "org.ezkey.exception", "org.ezkey.signature"})`. Port **7080** (management 7081). Propriétés identiques à l'admin-api pour datasource, JPA, rate limiting API key, encryption Tink — sans les sections admin-auth, batch jobs, password policy, MFA bootstrap.

### Step 3 — Créer le contrôleur `M2mAuthAttemptController`

Exposer uniquement les 3 endpoints issus de `AuthAttemptController.java` (admin-api) : `POST /api/v1/auth-attempts` (create), `GET /api/v1/auth-attempts/{id}/wait` (long-poll), `DELETE /api/v1/auth-attempts/{id}` (cancel). Copier les helpers `extractApiKeyId()`, `extractIntegrationId()`, `validateApiKeyIntegrationAccess()`. L'authentification est toujours `ROLE_API_KEY` — pas de branche bearer/admin. Rate limiting inline identique via `RateLimitService`. Audit logging identique via `AuditService`.

### Step 4 — Copier et adapter les classes de sécurité

Copier dans le package `org.ezkey.m2m.security` : `ApiKeyAuthenticationFilter` (depuis l'admin-api), `AccessControlService` (simplifié : uniquement la logique API key, pas admin/tenant), `RateLimitService` (depuis l'admin-api). Créer un `M2mSecurityConfig` minimaliste : uniquement `ApiKeyAuthenticationFilter` dans la chaîne, tous les endpoints requièrent `ROLE_API_KEY`, CSRF/form/basic désactivés, endpoints actuator publics. Pas de `AdminTokenAuthenticationFilter`, pas de `AdminRateLimitFilter`.

### Step 5 — Ajouter le flag de désactivation dans l'admin-api

Propriété `ezkey.m2m.auth-attempts.enabled=true` (défaut `true`, donc aucun changement de comportement). Appliquer `@ConditionalOnProperty(name = "ezkey.m2m.auth-attempts.enabled", havingValue = "true", matchIfMissing = true)` sur les 3 méthodes M2M du `AuthAttemptController` existant — en suivant le pattern déjà utilisé dans `AdminRateLimitConfig`. Documenter dans `application.properties` avec un commentaire explicatif.

### Step 6 — Intégrer à Docker

Ajouter le stage `m2m-api` dans le `Dockerfile` (copie du pattern `auth-api`). Ajouter le service `m2m-api` dans `docker-compose.yml` : ports `7080:7080` / `7081:7081`, mêmes env vars datasource, volumes `encryption-keys` et `bootstrap-artifacts`, healthcheck sur `/actuator/health`, `depends_on: [postgres, migration]`. Mettre à jour `docker-compose.docker-dev.yml`. Ajouter le service dans les health checks de `manage.ps1` / `manage.sh` et dans les builds de `start.ps1` / `start.sh`. Ajouter `m2m-api` aux `depends_on` de `bootstrap-init` et `cli-test`.

### Step 7 — Mettre à jour Postman

Ajouter `base_url_m2m_api` = `http://localhost:7080` dans l'environnement Postman. Créer une collection `EZ Key Auth Attempts m2m.postman_collection.json` avec les 3 requêtes pointant vers `{{base_url_m2m_api}}`, authentification Basic (API key). Les collections admin-api existantes restent inchangées.

## Constraints & Notes

### Duplication de sécurité assumée

`ApiKeyAuthenticationFilter`, `AccessControlService` et `RateLimitService` sont dupliqués entre admin-api et m2m-api. L'API est stable ; le risque de divergence est faible. Prévoir une extraction vers `ezkey-core` ou un module `ezkey-security-common` dans une phase future, quand les tests core seront mieux isolés des changements de dépendance.

### Rate limiting en mémoire (non distribué)

Chaque instance (admin-api, m2m-api) maintient ses propres buckets Caffeine. Un même API key a donc des quotas indépendants par service. Acceptable pour le MVP ; envisager un backend partagé (Redis) quand le volume le justifiera.

### Pas de compilation native

Le module est structurellement simple et bien positionné pour une future compilation native (peu de dépendances, stateless). La configuration GraalVM sera ajoutée dans une phase ultérieure après résolution des problèmes JPA/Hibernate connus.
