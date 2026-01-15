---
name: Native Compilation Review Admin Auth APIs
overview: Révision complète de la configuration native pour ADMIN_API et AUTH_API avec préférence pour la configuration Java explicite (RuntimeHintsRegistrar). Analyse de redondance entre fichiers JSON et configuration Java, tests pour valider les hypothèses, suppression proactive des configurations redondantes, et création d'AdminNativeConfiguration.java équivalent à AuthNativeConfiguration.
todos:
  - id: analyze-auth-native-config
    content: Analyser AuthNativeConfiguration.java pour comprendre sa couverture complète et identifier ce qui est déjà configuré explicitement.
    status: pending
  - id: list-admin-controllers-dtos
    content: Lister exhaustivement tous les contrôleurs admin-api et leurs DTOs utilisés (9 contrôleurs identifiés).
    status: pending
  - id: compare-admin-reflect-config
    content: Comparer la liste complète des DTOs admin-api avec le contenu actuel de reflect-config.json pour identifier les manquants.
    status: pending
  - id: test-auth-json-redundancy
    content: "Tester l'hypothèse de redondance: compiler auth-api sans reflect-config.json, serialization-config.json, resource-config.json pour valider si AuthNativeConfiguration.java suffit."
    status: pending
  - id: remove-auth-redundant-json
    content: Si les tests confirment la redondance, supprimer les fichiers JSON redondants de auth-api (reflect-config.json, serialization-config.json, resource-config.json si redondants).
    status: pending
  - id: create-admin-native-config
    content: Créer AdminNativeConfiguration.java avec RuntimeHintsRegistrar incluant tous les DTOs, entités JPA, ressources et sérialisation de manière explicite et exhaustive.
    status: pending
  - id: test-admin-native-compilation
    content: Compiler admin-api avec AdminNativeConfiguration.java et tester l'exécution de l'image native pour valider la configuration.
    status: pending
  - id: test-admin-json-redundancy
    content: "Tester l'hypothèse de redondance: compiler admin-api sans les fichiers JSON pour valider si AdminNativeConfiguration.java suffit."
    status: pending
  - id: remove-admin-redundant-json
    content: Si les tests confirment la redondance, supprimer les fichiers JSON redondants de admin-api (reflect-config.json, serialization-config.json, resource-config.json si redondants).
    status: pending
  - id: verify-jpa-entities-java-config
    content: Vérifier que toutes les entités JPA (AuthAttempt, Enrollment, Integration, ApiKey, EncryptionKey, EzkeyAdmin, AuditLog, etc.) sont dans les configurations Java pour les deux APIs.
    status: pending
  - id: verify-spring-config-classes-java
    content: Vérifier que les classes de configuration Spring et filtres de sécurité sont dans les configurations Java si nécessaire.
    status: pending
  - id: verify-exceptions-java-config
    content: Vérifier que les exceptions personnalisées (ResourceNotFoundException, RateLimitExceededException, etc.) sont dans les configurations Java.
    status: pending
  - id: verify-native-image-properties
    content: Vérifier native-image.properties pour les deux APIs (garder, pas redondant avec Java).
    status: pending
  - id: check-application-native-properties
    content: Vérifier si application.native.properties existe pour les deux APIs et son contenu (garder, configuration Spring Boot).
    status: pending
  - id: update-native-build-docs-admin
    content: Créer/mettre à jour NATIVE_BUILD.md pour admin-api avec approche de configuration Java explicite et résultats des tests de redondance.
    status: pending
  - id: update-native-build-docs-auth
    content: Mettre à jour NATIVE_BUILD.md auth-api avec approche de configuration Java explicite et résultats des tests de redondance.
    status: pending
  - id: document-native-approach
    content: Documenter l'approche de configuration Java explicite comme source unique de vérité et les raisons de cette préférence.
    status: pending
  - id: create-docker-native-profile
    content: Créer docker-compose.native.yml pour le profil de compilation native avec images natives compilées.
    status: pending
  - id: update-docker-start-scripts
    content: Mettre à jour docker/start.sh et docker/start.ps1 pour supporter le basculement entre profil classique et profil native.
    status: pending
  - id: update-docker-documentation
    content: Mettre à jour la documentation Docker (docker/README.md) avec instructions pour utiliser les deux profils (classique et native).
    status: pending
---

# Plan de Révision de la Compilation Native pour ADMIN_API et AUTH_API

## Contexte

La compilation native GraalVM nécessite une configuration exhaustive pour que toutes les classes utilisant la réflexion, la sérialisation et les ressources soient correctement déclarées. Une configuration incomplète peut causer des erreurs à l'exécution dans l'image native.

## Principe Directeur

**Préférence pour la configuration explicite en Java**: La configuration via `RuntimeHintsRegistrar` en Java est préférée car elle est:

- **Explicite**: Si ça ne fonctionne pas, on sait exactement où faire la mise à jour
- **Type-safe**: Vérifiée à la compilation
- **Maintenable**: Plus facile à suivre et comprendre
- **Testable**: Peut être testée unitairement

Les fichiers JSON générés automatiquement sont moins préférés car ils reposent sur des hypothèses de génération implicite.

## État Actuel

### AUTH_API

- ✅ Classe `AuthNativeConfiguration.java` présente avec `RuntimeHintsRegistrar`
- ✅ Fichiers de configuration native présents dans `META-INF/native-image/`
- ✅ 2 contrôleurs (EnrollmentController, AuthAttemptController)
- ✅ DTOs configurés dans reflect-config.json et AuthNativeConfiguration

### ADMIN_API

- ❌ Pas de classe équivalente à `AuthNativeConfiguration.java`
- ✅ Fichiers de configuration native présents dans `META-INF/native-image/`
- ⚠️ 9 contrôleurs (beaucoup plus que auth-api)
- ⚠️ reflect-config.json incomplet (manque plusieurs DTOs)

## Analyse Requise

### 1. Évaluation de AuthNativeConfiguration.java

**Hypothèse à tester**: Les fichiers JSON (`reflect-config.json`, `serialization-config.json`, `resource-config.json`) sont redondants avec `AuthNativeConfiguration.java` et peuvent être supprimés si la configuration Java couvre tout.

**Approche de test**:

1. Créer une version de test sans les fichiers JSON
2. Compiler et tester l'image native
3. Si ça fonctionne: les JSON sont redondants → les supprimer
4. Si ça ne fonctionne pas: identifier ce qui manque et l'ajouter à la config Java

### 2. Révision Exhaustive des Contrôleurs et DTOs

#### Contrôleurs ADMIN_API (9 total):

1. `IntegrationController` - DTOs: IntegrationCreateRequestDto, IntegrationCreateResponseDto, IntegrationResponseDto
2. `AuthAttemptController` - DTOs: AuthAttemptDto, AuthAttemptCreateRequestDto, AuthAttemptCreateResponseDto, AuthAttemptWaitRequestDto, AuthAttemptWaitResponseDto
3. `EnrollmentController` - DTOs: EnrollmentCreateRequestDto, EnrollmentCreateResponseDto, EnrollmentResponseDto
4. `AdminEnrollmentController` - DTOs: EnrollmentResetRequestDto, EnrollmentResetResponseDto
5. `AdminAuthController` - DTOs: AdminLoginRequestDto, AdminLoginResponseDto, AdminPasswordlessWaitRequestDto, AdminRecoveryRequestDto, AdminRecoveryResponseDto
6. `ApiKeyController` - DTOs: ApiKeyCreateRequestDto, ApiKeyCreateResponseDto, ApiKeyResponseDto
7. `EncryptionKeyController` - DTOs: record classes définis dans le contrôleur (EncryptionKeyResponse, KeyRotationResponse, ReencryptionBatchResponse, etc.)
8. `AuditLogController` - DTOs: AuditLogResponseDto
9. `GlobalExceptionHandler` - DTOs: ErrorResponseDto

#### Contrôleurs AUTH_API (2 total):

1. `EnrollmentController` - DTOs: EnrollmentBindRequestDto, EnrollmentBindResponseDto, EnrollmentVerifyRequestDto, EnrollmentVerifyResponseDto
2. `AuthAttemptController` - DTOs: AuthAttemptPendingRequestDto, AuthAttemptPendingResponseDto, AuthAttemptRespondRequestDto, AuthAttemptRespondResponseDto

### 3. Révision des Fichiers de Configuration

#### reflect-config.json

- **AUTH_API**: Tester si redondant avec AuthNativeConfiguration
- **ADMIN_API**: Créer AdminNativeConfiguration.java équivalent, puis tester redondance

#### resource-config.json

- Vérifier si les patterns de ressources peuvent être migrés vers Java
- Tester redondance avec configuration Java

#### serialization-config.json

- Vérifier si la sérialisation peut être entièrement gérée en Java
- Tester redondance avec configuration Java

#### native-image.properties

- Garder (arguments GraalVM spécifiques, pas de redondance avec Java)

#### application.native.properties

- Garder (configuration Spring Boot, pas de redondance)

## Plan d'Action

### Phase 1: Analyse et Documentation

1. Lister exhaustivement tous les DTOs utilisés dans chaque contrôleur admin-api
2. Comparer avec le contenu actuel de reflect-config.json admin-api
3. Identifier tous les DTOs manquants
4. Analyser AuthNativeConfiguration.java pour comprendre sa couverture complète

### Phase 2: Tests de Redondance AUTH_API

1. **Hypothèse**: Les fichiers JSON sont redondants avec AuthNativeConfiguration.java
2. Créer une branche de test sans reflect-config.json, serialization-config.json, resource-config.json
3. Compiler l'image native auth-api sans les fichiers JSON
4. Tester l'exécution de l'image native
5. **Si succès**: Supprimer les fichiers JSON redondants
6. **Si échec**: Identifier ce qui manque, l'ajouter à AuthNativeConfiguration.java, puis supprimer les JSON

### Phase 3: Création AdminNativeConfiguration.java

1. Créer `AdminNativeConfiguration.java` avec `RuntimeHintsRegistrar`
2. Inclure tous les DTOs identifiés dans Phase 1
3. Inclure toutes les entités JPA nécessaires
4. Inclure les patterns de ressources
5. Inclure la configuration de sérialisation Jackson
6. S'assurer que la configuration est exhaustive et explicite

### Phase 4: Tests de Redondance ADMIN_API

1. **Hypothèse**: Les fichiers JSON sont redondants avec AdminNativeConfiguration.java
2. Compiler l'image native admin-api avec AdminNativeConfiguration.java
3. Tester l'exécution de l'image native
4. Créer une version de test sans les fichiers JSON
5. Compiler et tester sans les fichiers JSON
6. **Si succès**: Supprimer les fichiers JSON redondants
7. **Si échec**: Identifier ce qui manque, l'ajouter à AdminNativeConfiguration.java, puis supprimer les JSON

### Phase 5: Mise à Jour ADMIN_API

1. S'assurer que AdminNativeConfiguration.java couvre tous les besoins
2. Mettre à jour reflect-config.json si nécessaire (temporairement, avant suppression)
3. Mettre à jour serialization-config.json si nécessaire (temporairement, avant suppression)
4. Vérifier native-image.properties (garder, pas redondant)
5. Vérifier application.native.properties si existe

### Phase 6: Révision AUTH_API

1. Vérifier que AuthNativeConfiguration.java couvre tous les besoins après tests
2. Supprimer les fichiers JSON redondants identifiés en Phase 2
3. Vérifier tous les autres fichiers de configuration (native-image.properties, etc.)

### Phase 7: Vérifications Finales

1. Vérifier les entités JPA dans les deux APIs (dans les configs Java)
2. Vérifier les classes de configuration Spring (dans les configs Java)
3. Vérifier les exceptions personnalisées (dans les configs Java)
4. Vérifier les autres éléments critiques identifiés

### Phase 8: Documentation et Docker

1. Mettre à jour la documentation existante sur la compilation native
2. Créer/mettre à jour NATIVE_BUILD.md pour admin-api (équivalent à auth-api)
3. Documenter l'approche de configuration Java explicite
4. Documenter les tests de redondance effectués
5. Créer un profil Docker pour la compilation native
6. Permettre le basculement facile entre Spring Boot classique et images natives

## Profil Docker pour Compilation Native (Phase 2)

### Objectif

Créer un système permettant de lancer facilement le stack Docker soit en mode Spring Boot classique (par défaut), soit avec des images compilées nativement.

### Approche

1. **Profil par défaut**: Spring Boot classique (comportement actuel)
2. **Profil native**: Utiliser des images Docker compilées nativement

### Implémentation

- Créer un fichier `docker-compose.native.yml` ou utiliser des variables d'environnement
- Modifier les scripts de démarrage (`start.sh`, `start.ps1`) pour supporter les deux modes
- Documenter l'utilisation des deux profils
- S'assurer que les deux modes utilisent les mêmes configurations de base (ports, volumes, etc.)

### Fichiers à Modifier/Créer

- `docker-compose.yml` (profil par défaut - Spring Boot classique)
- `docker-compose.native.yml` (profil native - images compilées)
- `docker/start.sh` et `docker/start.ps1` (ajouter support pour profil native)
- Documentation Docker mise à jour

## Livrables

1. Liste exhaustive de tous les DTOs manquants dans admin-api reflect-config.json
2. AdminNativeConfiguration.java créé avec configuration explicite complète
3. Tests de redondance effectués pour auth-api et admin-api
4. Fichiers JSON redondants supprimés (si validé par tests)
5. Configuration Java explicite comme source unique de vérité
6. Documentation mise à jour sur la compilation native (NATIVE_BUILD.md pour admin-api)
7. Documentation de l'approche de configuration Java explicite
8. Profil Docker pour compilation native fonctionnel
9. Scripts de démarrage mis à jour pour supporter les deux modes
10. Documentation Docker mise à jour avec instructions pour les deux profils