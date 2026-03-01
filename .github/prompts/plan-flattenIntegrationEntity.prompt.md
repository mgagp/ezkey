# Plan: Flatten Integration Entity — Remove Logo & I18N

Les deux changements partagent le même vecteur (simplification du modèle `Integration`) et peuvent être livrés en deux phases atomiques et validables indépendamment. Phase 1 est un échauffement à faible risque; Phase 2 est le vrai sujet.

---

## Phase 1 — Supprimer le logo (faible risque, haute confiance)

**TL;DR** : la colonne `integration_logo` n'est consommée nulle part de façon utile. On la supprime proprement de bout en bout en une passe. Chaque couche est modifiée, les tests servent de filet de sécurité.

### Steps

1. **Migration V30** — nouveau fichier dans `ezkey-core/src/main/resources/db/migration/` :
   `ALTER TABLE ezkey_integration DROP COLUMN integration_logo`

2. **Entité** — retirer le champ `logo` de `Integration.java`

3. **DTOs core** — retirer `logo` de `IntegrationCreateRequest.java` et `IntegrationResponse.java`

4. **DTOs admin-api** — retirer `logo` de `IntegrationCreateRequestDto.java` et `IntegrationResponseDto.java`

5. **Mappers** — retirer tout ignorer/mapping de `logo` dans `IntegrationServiceMapper.java` et `IntegrationControllerMapper.java`

6. **CLI** — retirer `--logo` de la commande `create` dans `ezkey-cli-python/ezkey_cli/commands/admin.py`

7. **TUI** — retirer le bloc `# Logo` (lignes 106–109) dans `ezkey-cli-python/ezkey_cli/tui/screens/integration_detail.py`

8. **Tests** — retirer `logo`/`integrationLogo` de `TestDataFactory.java`, `DemoDeviceEnrollmentWriter.java`, et tout test unitaire qui asserte `logo`

9. **Postman** — retirer le champ `logo` des request bodies dans `EZ Key Integrations admin.postman_collection.json`

### Vérification Phase 1

- `mvn clean verify` sans erreur de compilation
- `IntegrationManagementSecurityTest` vert
- CLI `ezkey admin integration create --code test --name "Test" --language en` retourne 201 sans champ logo

---

## Phase 2 — Aplatir l'i18n (impact structurel, deux tables → une)

**TL;DR** : `ezkey_integration_i18n` est supprimée. `integration_name` (NOT NULL sauf system integration) et `integration_description` (nullable) sont ajoutées directement sur `ezkey_integration`. La migration backfille à partir de la première entrée i18n existante et impose `"Ezkey System"` pour la system integration. Toutes les couches sont simplifiées.

### Decisions

- Convention colonnes : `integration_name` / `integration_description` (cohérence avec `integration_active`, `integration_code`)
- System integration : backfill `'Ezkey System'` via migration V31
- `description` : nullable partout (DB, Java, DTO)
- `integration_name` : nullable en DB (pas de NOT NULL en base pour la system integration), mais `@NotBlank` sur le DTO de création pour les cas non-système
- Pas de période de coexistence des deux modèles — migration directe, aucun système en production

### Steps

1. **Migration V31** — dans `ezkey-core/src/main/resources/db/migration/` :
   - `ADD COLUMN integration_name VARCHAR(255) NULL` et `ADD COLUMN integration_description VARCHAR(500) NULL` sur `ezkey_integration`
   - Backfill depuis i18n :
     ```sql
     UPDATE ezkey_integration ei
     SET
       integration_name = (
         SELECT integration_i18n_name
         FROM ezkey_integration_i18n
         WHERE integration_id = ei.integration_id
         LIMIT 1
       ),
       integration_description = (
         SELECT integration_i18n_description
         FROM ezkey_integration_i18n
         WHERE integration_id = ei.integration_id
         LIMIT 1
       );
     ```
   - Forcer le nom de la system integration :
     ```sql
     UPDATE ezkey_integration
     SET integration_name = 'Ezkey System'
     WHERE is_system_integration = TRUE AND integration_name IS NULL;
     ```
   - `DROP TABLE ezkey_integration_i18n`

2. **Supprimer les fichiers suivants** :
   - `ezkey-core/.../domain/entity/IntegrationI18n.java`
   - `ezkey-core/.../domain/repository/IntegrationI18nRepository.java`
   - `ezkey-core/.../domain/IntegrationI18nCreate.java`
   - `ezkey-core/.../domain/IntegrationI18nResponse.java`
   - `ezkey-admin-api/.../dto/IntegrationI18nCreateDto.java`
   - `ezkey-admin-api/.../dto/IntegrationI18nResponseDto.java`
   - `ezkey-admin-api/.../test/.../IntegrationI18nResponseDtoTest.java`
   - `ezkey-admin-api/.../test/.../IntegrationI18nCreateDtoTest.java`

3. **Mettre à jour `Integration.java`** :
   - Retirer `List<IntegrationI18n> i18n` (avec `@OneToMany`, `CascadeType.ALL`, `orphanRemoval`)
   - Ajouter `String name` (`@Column(name = "integration_name")`)
   - Ajouter `String description` (`@Column(name = "integration_description")`)

4. **DTOs core** :
   - `IntegrationCreateRequest.java` : remplacer `List<IntegrationI18nCreate>` par `String name` + `String description`
   - `IntegrationResponse.java` : remplacer `List<IntegrationI18nResponse>` par `String name` + `String description`

5. **DTOs admin-api** :
   - `IntegrationCreateRequestDto.java` : remplacer `List<IntegrationI18nCreateDto>` par `@NotBlank String name` + `String description`
   - `IntegrationResponseDto.java` : remplacer `List<IntegrationI18nResponseDto>` par `String name` + `String description`

6. **Mappers** :
   - `IntegrationServiceMapper.java` : retirer `map(IntegrationI18nCreate)`, simplifier le mapping (`name`→`name`)
   - `IntegrationControllerMapper.java` : retirer tout ce qui est `I18n` (méthodes `toI18nResponse`, `toI18nEntity`, `toI18nResponseList`)

7. **Service `IntegrationService.java`** :
   - `createIntegration` : retirer la boucle de back-référencement i18n, retirer l'injection d'`IntegrationI18nRepository`
   - `findByFilters` : remplacer le JOIN sur i18n par `WHERE LOWER(i.integration_name) LIKE :query`, retirer `query.distinct(true)`

7b. **`IntegrationRepository.java`** ⚠️ *requis pour compilation* :
    - Supprimer la méthode `findByIdWithI18nAndTenant` — son JPQL contient `LEFT JOIN FETCH i.i18n` qui deviendra invalide dès que le champ `i18n` est retiré de l'entité
    - Supprimer l'annotation `@QueryHints(@QueryHint(...))` qui était sur cette méthode
    - Les imports `jakarta.persistence.QueryHint` et `org.springframework.data.jpa.repository.QueryHints` peuvent être retirés ici si plus aucune méthode ne les utilise (vérifier si `findSystemIntegrationReadOnly` est aussi supprimée dans cette phase)

7c. **`EnrollmentBindService.java`** ⚠️ *requis pour compilation* :
    - Un appel à `integrationRepository.findByIdWithI18nAndTenant(integrationId)` est présent — remplacer par `integrationRepository.findById(integrationId)` ; il n'y a plus de `PersistentBag` à protéger

8. **CLI** — `ezkey-cli-python/ezkey_cli/commands/admin.py` :
   - Commande `create` : retirer `--language`, conserver `--name`/`--description`, envoyer directement `{"code": ..., "name": ..., "description": ...}` sans encapsulation `i18n[]`
   - Commande `list` : lire directement `integration['name']`

9. **TUI** :
   - `integration_detail.py` : remplacer la boucle `# I18N` par lecture directe `data.get('name')` / `data.get('description')`
   - `integrations.py` : simplifier l'affichage du nom (lecture directe)
   - `integration_create.py` : retirer le champ language, simplifier le payload

10. **Tests** :
    - `TestDataFactory.java` : remplacer `i18n[]` par `"name": name, "description": description` dans les factory methods
    - `DemoDeviceEnrollmentWriter.java` : remplacer `i18nList.get(0).get("name")` par `integrationResponse.jsonPath().getString("name")`
    - Mettre à jour : `IntegrationManagementSecurityTest.java`, `IntegrationServiceTest.java`, `IntegrationRepositoryTest.java`, `IntegrationServiceMapperTest.java`, `IntegrationResponseDtoTest.java`

11. **Postman** — `EZ Key Integrations admin.postman_collection.json` :
    - Requêtes `create` : `{"code": "my-app", "name": "My App", "description": "..."}` au lieu de `{"code": "my-app", "i18n": [...]}`
    - Réponses attendues dans les tests Postman : adapter les assertions `i18n[0].name` → `name`

### Vérification Phase 2

- `mvn clean verify` vert complet (test critique : la suppression de `LEFT JOIN FETCH i.i18n` ne laisse aucune référence JPQL invalide)
- `IntegrationManagementSecurityTest` vert
- CLI `ezkey admin integration create --code test --name "Test"` → 201 avec `{"id": ..., "code": "test", "name": "Test", "description": null}`
- TUI : écran détail integration affiche directement le nom sans itération i18n

---

## Inventaire d'impact complet

| Composant | Éléments touchés |
|---|---|
| **DB** | V30: drop `integration_logo`; V31: add `integration_name`/`integration_description`, backfill, drop `ezkey_integration_i18n` |
| **ezkey-core** | `Integration.java`, supprimer `IntegrationI18n.java`, supprimer `IntegrationI18nRepository.java`, `IntegrationCreateRequest.java`, `IntegrationResponse.java`, supprimer `IntegrationI18nCreate/Response.java`, `IntegrationServiceMapper.java`, `IntegrationService.java` |
| **ezkey-admin-api** | `IntegrationCreateRequestDto.java`, `IntegrationResponseDto.java`, supprimer `IntegrationI18nCreateDto.java` et `IntegrationI18nResponseDto.java`, `IntegrationControllerMapper.java` |
| **ezkey-cli-python** | `commands/admin.py`, `tui/screens/integrations.py`, `tui/screens/integration_detail.py`, `tui/screens/integration_create.py`, `tui/api_client.py` |
| **Postman** | `EZ Key Integrations admin.postman_collection.json` |
| **Tests** | `TestDataFactory.java`, `DemoDeviceEnrollmentWriter.java`, `IntegrationManagementSecurityTest.java`, `IntegrationServiceTest.java`, `IntegrationRepositoryTest.java`, `IntegrationServiceMapperTest.java`, `IntegrationResponseDtoTest.java` — supprimer `IntegrationI18nResponseDtoTest.java` et `IntegrationI18nCreateDtoTest.java` |

---

## Angles morts et considérations

- **Bug de contrainte unique existant** : la contrainte UNIQUE sur `ezkey_integration_i18n` est `(integration_i18n_id, lang)` au lieu de `(integration_id, lang)` — elle ne protège rien. La migration V31 supprime entièrement cette table donc ce bug disparaît sans action spécifique.
- **Concurrence sur la system integration** : le `PersistentBag` partagé et les deux requêtes read-only custom (`findByIdWithI18nAndTenant`, `findSystemIntegrationReadOnly`) deviennent inutiles après Phase 2 — les méthodes de repository peuvent être simplifiées ou supprimées.
- **Filtre `integrationName`** : le nom du paramètre query API reste `integrationName` pour ne pas changer le contrat client.
- **auth-api / m2m-api** : aucun impact — ces modules utilisent `integration_id` comme FK de scope mais ne lisent jamais les champs i18n.
- **ezkey-demo-app-acme** : pas de gestion directe de l'entité Integration — impact nul.
- **`integration_name` nullable en base** : intentionnel pour la system integration. La contrainte NOT NULL est portée par le DTO (`@NotBlank`) côté création admin, pas par la DDL.

---

## Phase 3 — Révision documentaire (post-implémentation)

**TL;DR** : Une fois les Phases 1 et 2 commitées et les tests verts, passer en revue la documentation du projet pour éliminer toute référence orpheline à `integration_logo`, `integration_i18n`, `IntegrationI18n` et au modèle i18n. Cette phase est entièrement éditoriale — aucun code n'est modifié.

### Fichiers à réviser

1. **`docs/API_DESIGN_NOTES.md`**
   - Item 6 (`Integration.logo is in the model but unused`) : devenu sans objet — supprimer ou marquer `[RESOLVED by Phase 1]`
   - Item 8 (`Integration i18n adds complexity`) : devenu sans objet — supprimer ou marquer `[RESOLVED by Phase 2]`

2. **`docs/ENDPOINT.md`**
   - Ligne ~196 : exemple de payload auth contient `"integrationLogo": "https://acme.com/logo.png"` — retirer ce champ du payload d'exemple
   - Vérifier les autres exemples JSON de réponse pour tout `i18n[]` résiduel

3. **`docs/ENCRYPTION_KEY_ROTATION_IMPLEMENTATION.md`**
   - Ligne ~1975 : snippet SQL d'exemple contient `INSERT INTO ezkey_integration (integration_logo, ...)` — mettre à jour pour refléter le nouveau schéma sans `integration_logo`

4. **`docs/NATIVE_COMPILATION_REVIEW_SUMMARY.md`**
   - Ligne ~18 : liste des entités JPA mentionne `IntegrationI18n` — retirer
   - Lignes ~141–142 : liste des DTOs mentionne `IntegrationI18nCreateDto` et `IntegrationI18nResponseDto` — retirer
   - Ligne ~200 : liste mentionne `IntegrationI18n` — retirer

5. **`docs/PAGINATION_AUDIT_REPORT.md`**
   - Ligne ~126 : description du filtre `integrationName` dit *(partial match, case-insensitive via i18n join)* — changer en *(partial match, case-insensitive on `integration_name`)* maintenant que c'est une colonne directe

6. **`docs/features/SECURITY_MULTI_TENANT.md`**
   - Ligne ~953 : snippet de code d'entité contient `@Column(name = "integration_logo")` — mettre à jour le snippet pour refléter l'entité simplifiée

7. **`docs/testing/MULTI_TENANT_OBSERVATIONS.md`**
   - Ligne ~662 : observation qui prend `integrationLogo` dans `ezkey_integration_i18n` en exemple de pattern — retirer ou remplacer par un exemple pertinent

8. **`docs/monitoring/index.md`**
   - Ligne ~55 : liste des tables de monitoring inclut `ezkey_integration_i18n - Internationalization data` — retirer cette entrée

### Note sur les archives

`docs/plan/archives/plan.md` mentionne `ezkey_integration_i18n` (ligne ~657). Étant un document d'archive, il peut être laissé tel quel ou annoté avec une note *(superseded by Phase 2 migration)* — pas de correction active requise.

### Vérification Phase 3

- Grep global sur tout `docs/` pour `integration_logo`, `integration_i18n`, `IntegrationI18n`, `i18n join` : zéro résultat dans des fichiers actifs (hors archives)
- Relecture des exemples JSON dans `ENDPOINT.md` : aucun champ `logo` ni tableau `i18n[]` dans les réponses integration

---

## Phase 4 — Décontamination finale de l'infrastructure repository

**TL;DR** : après les Phases 1–3, `IntegrationRepository` conserve encore deux artéfacts qui existaient uniquement à cause de la contrainte i18n. `findSystemIntegrationReadOnly` est du **code mort** (jamais appelé), et les Javadocs des méthodes conservées portent encore le contexte PersistentBag/read-only qui n'a plus de sens. Cette phase finalise le nettoyage pour que le fichier reflète exactement ce qu'il aurait été si la table avait été conçue correctement dès le départ.

### Steps

1. **`IntegrationRepository.java`** — supprimer la méthode `findSystemIntegrationReadOnly` :
   - La méthode est définie avec `@Query(... LEFT JOIN FETCH i.tenant ...)` + `@QueryHints(readOnly=true)` mais **n'est jamais appelée** dans aucun service — c'est du code mort créé en anticipation d'un problème de concurrence sur le `PersistentBag` i18n qui disparaît avec Phase 2
   - Retirer les imports `jakarta.persistence.QueryHint` et `org.springframework.data.jpa.repository.QueryHints` (plus aucune méthode ne les utilise après suppression des deux méthodes `@QueryHints`)

2. **`IntegrationRepository.java`** — nettoyer le Javadoc de `findByIsSystemIntegrationAndActiveTrue` :
   - Le Javadoc actuel contient un avertissement *"use `findSystemIntegrationReadOnly` instead to avoid Hibernate dirty-checking and PersistentBag shared-reference hazard"* — cette mise en garde est désormais fausse et trompeuse
   - Remplacer par un Javadoc simple décrivant le rôle de la méthode sans référence aux anciennes contraintes

3. **Vérification de cohérence** — grep sur `IntegrationRepository.java` pour toute occurrence de `i18n`, `PersistentBag`, `readOnly`, `QueryHint`, `FETCH.*i18n` : aucun résultat attendu

### Résultat attendu

`IntegrationRepository` contiendra uniquement des méthodes qui auraient existé si le modèle avait été plat dès le départ :
- `existsByCodeAndTenant` — validation unicité du code
- `findByIsSystemIntegrationAndActiveTrue` — lookup de la system integration
- `findTenantIdByIntegrationId` — projection scalaire (optimisation légitime)
- Les méthodes héritées de `JpaRepository` (findById, save, delete…)

### Vérification Phase 4

- `mvn clean verify` vert
- Grep `IntegrationRepository.java` : aucune occurrence de `i18n`, `PersistentBag`, `QueryHint`, `FETCH`
- Le fichier est lisible et explicable sans connaissance de l'historique i18n
