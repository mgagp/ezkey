## Plan: Extract Minimal Zero-Dependency Java SDK

SDK minimaliste, zéro dépendance, Java 17+, utilisant `java.net.http.HttpClient` et un micro-JSON interne pour les 3 opérations M2M (create, wait, cancel auth attempt). Builder pattern immutable inspiré de Stripe. Validation immédiate en refactorant `ezkey-demo-app-acme` pour utiliser le nouveau SDK.

### Steps

1. **Nettoyer `ezkey-sdk/java`** — Supprimer les 5 classes existantes (`EzkeyClient`, `EzkeyConfig`, `EzkeyException`, `EzkeyAdminAPI`, `EzkeyAuthAPI`) dans `ezkey-sdk/java/src/main/java/org/ezkey/sdk`, la config OpenAPI generator et toutes les dépendances (Jackson, jsr305, etc.) du `ezkey-sdk/java/pom.xml`. Réécrire le POM : `groupId=org.ezkey`, `artifactId=ezkey-sdk`, Java 17, **zéro dépendance compile-scope**, JUnit 5 en test-scope uniquement.

Intégrer le SDK comme module du parent POM (<module>ezkey-sdk/java</module>) pour que le reactor Maven le compile avant ezkey-demo-app-acme. Ceci est nécessaire car la suite de tests Docker (clean-start.sh) compile tout de façon autonome dans le conteneur — le SDK doit être résolu localement par le reactor, pas depuis un repo externe. L'intégration au parent n'empêche pas une publication indépendante future sur Maven Central.

2. **Créer `EzkeyConfig` (record immutable)** — Record Java 17 contenant `baseUrl` (String, défaut `http://localhost:9080`), `integrationKey` (String), `secretKey` (String), `connectTimeout` (Duration, défaut 10s), `readTimeout` (Duration, défaut 30s). Validation dans le constructeur compact (clés non nulles/non vides). L'appelant construit ce record comme il veut (Spring `@ConfigurationProperties`, properties, YAML, hardcodé…) — le SDK ne présume rien du format de stockage.

3. **Créer `EzkeyClient` avec Builder pattern** — Classe principale dans `org.ezkey.sdk` :
   - Constructeur simple : `new EzkeyClient(integrationKey, secretKey)` → défauts pour URL/timeouts
   - Builder : `EzkeyClient.builder().integrationKey("...").secretKey("...").baseUrl("...").connectTimeout(Duration.ofSeconds(10)).build()`
   - Factory : `EzkeyClient.fromEnvironment()` → lit `EZKEY_INTEGRATION_KEY`, `EZKEY_SECRET_KEY`, `EZKEY_BASE_URL`
   - Construit un `java.net.http.HttpClient` interne avec les timeouts configurés
   - Calcule le header `Authorization: Basic base64(integrationKey:secretKey)` à la construction
   - Immutable, thread-safe, réutilisable

4. **Implémenter les 3 opérations M2M** dans `EzkeyClient` :
   - `createAuthAttempt(int enrollmentId, boolean challengeRequested)` → `POST /api/v1/auth-attempts` → retourne `AuthAttemptCreateResponse`
   - `waitForAuthAttempt(int authAttemptId, int timeoutSeconds, int pollingSeconds)` → `GET /api/v1/auth-attempts/{id}/wait?timeout=X&polling=Y` → retourne `AuthAttemptWaitResponse`
   - `cancelAuthAttempt(int authAttemptId)` → `POST /api/v1/auth-attempts/{id}/cancel` → retourne `AuthAttemptCancelResponse`
   - Chaque méthode lève `EzkeyException` (checked) avec `statusCode`, `responseBody`, helpers `isClientError()`/`isServerError()`

5. **Créer les DTOs (records) et le micro-JSON** — Records : `AuthAttemptCreateResponse(int authAttemptId, Integer authAttemptChallenge, int timeoutSeconds, String expiresAt)`, `AuthAttemptWaitResponse(String status, boolean completed, boolean timeoutReached)`, `AuthAttemptCancelResponse(int authAttemptId, String status)`. Classe interne `JsonHelper` (~100-120 lignes) avec `toJson(Map)` et `parseObject(String) → Map<String, String>` — parsing simple clé/valeur pour des objets plats sans imbrication, suffisant pour les 3 DTOs. Pas de librairie JSON externe.

6. **Refactorer `ezkey-demo-app-acme`** — Ajouter dépendance Maven vers `ezkey-sdk`. Remplacer `EzkeyAuthService` + `EzkeyClientConfig` + `RestTemplate` + les 3 DTOs dupliqués par un `@Bean EzkeyClient` construit depuis `AcmeProperties` via le builder. `LoginController` appelle directement `ezkeyClient.createAuthAttempt(...)` / `ezkeyClient.waitForAuthAttempt(...)`. Supprimer les fichiers devenus obsolètes : `EzkeyAuthService.java`, `EzkeyClientConfig.java`, `HttpLoggingInterceptor.java`, `BufferingClientHttpResponseWrapper.java`, et les DTOs auth de la demo app. Retirer `spring-boot-starter-restclient` du `pom.xml`.

7. Mettre à jour le build Docker — Vérifier que clean-start.sh et les Dockerfiles associés copient bien les sources de java dans le contexte de build Docker. Puisque le SDK est maintenant un module du parent POM, le mvn clean install dans le conteneur le compilera automatiquement dans le bon ordre via le reactor. S'assurer que l'ordre des <module> dans le parent POM place java avant ezkey-demo-app-acme.

### Further Considerations

1. **Scope Auth API (device-side)?** — Ce plan couvre uniquement les opérations M2M côté Admin API (clé d'API). L'ancien SDK avait aussi des wrappers Auth API (bind, verify enrollment, pending, respond) destinés au device. On laisse ça hors scope — ce sera un plan de travail futur si nécessaire.

2. **Logging dans le SDK?** — Aucun framework de logging en dépendance. Recommandation : utiliser `System.Logger` (Java 9+, JEP 264), qui délègue automatiquement à SLF4J/JUL/Log4j si présent au runtime. Zéro dépendance ajoutée, et la demo app Spring Boot verra les logs SDK apparaître via SLF4J automatiquement.
