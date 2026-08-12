# Plan: Couverture fonctionnelle M2M vs Admin API

**TL;DR** — La couverture actuelle de `create` sur l'Admin API est solide (4 tests). Cependant, `wait` n'est couvert nulle part, et l'Integration API (port 7080; formerly called M2M API) n'a aucun test automatisé — seulement des collections Postman. Le plan ajoute la configuration d'infrastructure pour Integration API dans le harness de tests, crée un test Admin API pour `wait` + `userIdentifier`, et crée un test Integration API parallèle couvrant `create` + `wait` + `userIdentifier`. Admin API reste le défaut, Integration API est configuré en supplément. Pas de duplication des scénarios déjà couverts. Keep “M2M” only when referring to API-key auth as a mechanism.

---

## Décisions actées

- Admin API (port 9080) : reste le défaut dans tous les tests existants et futurs
- Integration API (port 7080) : configuré en parallèle, couverture ciblée uniquement
- Scope Admin à ajouter : `wait` + création par `userIdentifier`
- Scope M2M à créer : `create` + `wait` + création par `userIdentifier`
- `cancel` : hors scope pour ce cycle

---

## État des lieux — couverture actuelle

| Endpoint | Admin API (9080) | M2M API (7080) |
|---|---|---|
| `POST /auth-attempts` (create) | ✅ 4 tests | ❌ 0 test |
| `GET /auth-attempts/{id}/wait` | ❌ 0 test | ❌ 0 test |
| `POST /auth-attempts/{id}/cancel` | ❌ 0 test | ❌ 0 test |
| `GET /auth-attempts/{id}` | ❌ 0 test | N/A (Admin only) |
| `GET /auth-attempts` (list) | ❌ 0 test | N/A (Admin only) |
| `DELETE /auth-attempts/{id}` | ❌ 0 test | N/A (Admin only) |

La couverture existante autour du `create` Admin API est portée par :
- `AuthenticationFlowSecurityTest` — E2E complet avec réponse device
- `AuthAttemptSupersessionTest` — doublon auto-expiré
- `RateLimitingSecurityTest` — rate limiting via Admin API
- `ApiKeySecurityTest` — API key access control

---

## Étape 1 — Infrastructure : ajouter le M2M API au harness

**Fichier :** `ezkey-tests/src/test/java/org/ezkey/tests/config/DockerStackConfig.java`
- Ajouter la constante `M2M_API_URL` pointant sur `http://localhost:7080`
- Mirror du pattern `ADMIN_API_URL` / `AUTH_API_URL` déjà présent

**Fichier :** `ezkey-tests/src/test/java/org/ezkey/tests/config/RestAssuredTestConfig.java`
- Ajouter `configureForM2mApi()` — base URL port 7080
- L'authentification M2M est HTTP Basic (`integrationKey:secretKey`), différente du Bearer token admin : documenter cette différence dans la méthode

---

## Étape 2 — Nouveau test Admin API : `AuthAttemptWaitFlowTest`

**Nouveau fichier :** `ezkey-tests/src/test/java/org/ezkey/tests/security/authentication/AuthAttemptWaitFlowTest.java`

Ce test ne duplique pas `AuthenticationFlowSecurityTest` (qui couvre déjà `create → device respond → ACCEPTED`). Il cible spécifiquement les chemins **non couverts** :

| Scénario | Description |
|---|---|
| `wait - timeout PENDING` | Crée un attempt, appelle `wait` avec timeout court (ex. 1s) → vérifie retour avec statut PENDING (personne ne répond) |
| `create by userIdentifier` | Crée un attempt en passant `userIdentifier` au lieu de `enrollmentId` → 201 avec `enrollmentId` résolu |
| `wait - ACCEPTED after device response` | Flux E2E : create → device répond via Auth API → `wait` → retourne ACCEPTED (exercice complet du wait happy path) |

Setup : utilise `TestDataFactory` existant + `DemoDeviceEnrollmentWriter` pour le scénario device response. Auth : Bearer token admin.

---

## Étape 3 — Nouveau test M2M : `M2mAuthAttemptFlowTest`

**Nouveau fichier :** `ezkey-tests/src/test/java/org/ezkey/tests/security/m2m/M2mAuthAttemptFlowTest.java`

Objectif : démontrer que les **mêmes flux principaux** fonctionnent bout-en-bout via le M2M API avec son mécanisme d'auth propre (API Key / Basic Auth).

| Scénario | Description |
|---|---|
| `create by enrollmentId` | POST `/api/v1/auth-attempts` sur port 7080, auth Basic Key → 201 |
| `create by userIdentifier` | Même via `userIdentifier` → 201, `enrollmentId` résolu |
| `wait - timeout PENDING` | `GET /{id}/wait?timeout=1` → retour PENDING (path non-happy, rapide) |
| `wait - ACCEPTED after response` | Flux E2E complet : create M2M → device répond Auth API → wait M2M → ACCEPTED |

Ce test valide aussi implicitement : authentification API Key, rate limiting (premier appel), isolation tenant.

---

## Étape 4 — Helper d'authentification M2M (si nécessaire)

**Fichier existant :** `ezkey-tests/src/test/java/org/ezkey/tests/util/AuthTokenManager.java`

Vérifier si `AuthTokenManager` gère déjà la récupération de `integrationKey`/`secretKey` pour l'auth Basic M2M. Si non, ajouter une méthode utilitaire `buildM2mBasicAuthHeader(integrationKey, secretKey)` dans `TestDataFactory` ou dans un nouveau `M2mApiClient`.

---

## Vue d'ensemble de la couverture cible

| Endpoint | Admin API (9080) | M2M API (7080) |
|---|---|---|
| `POST /auth-attempts` (by enrollmentId) | ✅ existant | ✅ à créer |
| `POST /auth-attempts` (by userIdentifier) | ✅ à créer | ✅ à créer |
| `GET /auth-attempts/{id}/wait` (timeout) | ✅ à créer | ✅ à créer |
| `GET /auth-attempts/{id}/wait` (ACCEPTED) | ✅ à créer | ✅ à créer |
| `POST /auth-attempts/{id}/cancel` | hors scope | hors scope |
| `GET /auth-attempts`, `GET /{id}`, `DELETE` | hors scope | N/A |

---

## Vérification

```bash
# Lancer uniquement les tests authentication
mvn -pl ezkey-tests test -Dtest="AuthAttemptWaitFlowTest,M2mAuthAttemptFlowTest"

# Build complet avec toute la suite
mvn clean verify -pl ezkey-tests
```

Valider manuellement : les 4 scénarios de chaque test passent en vert avec la stack Docker complète démarrée (Admin API + Auth API + M2M API + DB).

---

**Résumé net :** 2 nouveaux fichiers de test + 2 modifications infrastructure (`DockerStackConfig`, `RestAssuredTestConfig`) + potentiellement 1 méthode helper. Aucune modification aux tests existants. La couverture passe de "create-only sur Admin API" à "create + wait + userIdentifier sur les deux APIs".
