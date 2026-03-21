# M2M API → Integration API — Renommage complet et systématique

## Objectif

Renommer entièrement le module M2M en **Integration API** : vocation (intégration ↔ Ezkey), cohérence avec Admin API / Auth API, intuition pour les développeurs. **Renommage complet** incluant la **table partitionnée** `ezkey_audit_log` et l’axe de sous-partitions (`_m2m` / `M2M_API` → `_integration` / `INTEGRATION_API`).

---

## 1. Table partitionnée et axe de sous-partitions (BD)

**Contexte :** Mode full développement, aucune installation en production. Les tests à court terme = clean start + BD flambant neuf. On peut donc **réviser les migrations existantes** (V6, V24, V25) : le seul impact est le nom interne de la partition ; pas de migration de données ni nouvelle migration Flyway.

La table **ezkey_audit_log** est partitionnée en deux niveaux :

- **Niveau 1 :** `RANGE (created_at)` — une partition par mois (`ezkey_audit_log_YYYY_MM`).
- **Niveau 2 :** `LIST (api_name)` — pour chaque mois, trois sous-partitions selon la source (à renommer) :
  - `ezkey_audit_log_YYYY_MM_admin`       → `api_name = 'ADMIN_API'`
  - `ezkey_audit_log_YYYY_MM_auth`       → `api_name = 'AUTH_API'`
  - `ezkey_audit_log_YYYY_MM_integration` → `api_name = 'INTEGRATION_API'` (au lieu de _m2m / M2M_API)

**À faire : révision directe des migrations existantes**

| Fichier | Modifications |
|---------|----------------|
| [V6__create_audit_log.sql](ezkey-core/src/main/resources/db/migration/V6__create_audit_log.sql) | CHECK `api_name IN ('ADMIN_API', 'AUTH_API', 'INTEGRATION_API')` ; commentaires sur `api_name` (port 7080 = Integration API). |
| [V24__partition_audit_log_by_month.sql](ezkey-core/src/main/resources/db/migration/V24__partition_audit_log_by_month.sql) | Remplacer partout `_m2m` par `_integration`, `M2M_API` par `INTEGRATION_API` (création des sous-partitions, commentaires, blocs DO $$). |
| [V25__create_partition_management_function.sql](ezkey-core/src/main/resources/db/migration/V25__create_partition_management_function.sql) | Dans `create_monthly_partition` : sous-partition `_integration` pour `VALUES IN ('INTEGRATION_API')` ; RAISE NOTICE et COMMENT sur la fonction (_admin, _auth, _integration). |

- **Java (core) :** enum [ApiName](ezkey-core/src/main/java/org/ezkey/audit/domain/ApiName.java) — remplacer `M2M_API` par `INTEGRATION_API` ; tous les usages (audit, DTOs, filtres UI, etc.) passent à `INTEGRATION_API`.

- **Tests :** [PartitionSchedulerServiceTest](ezkey-core/src/test/java/org/ezkey/database/service/PartitionSchedulerServiceTest.java) et toute doc/test qui mentionne `_m2m` / `M2M_API` → `_integration` / `INTEGRATION_API`.

---

## 2. Maven et module

- Renommer le répertoire **ezkey-m2m-api** → **ezkey-integration-api**.
- [pom.xml](pom.xml) racine : `<module>ezkey-integration-api</module>`.
- `ezkey-integration-api/pom.xml` : `artifactId` **ezkey-integration-api**, `name` / `description` (Integration API pour create/wait/cancel auth attempts, authentification par clé d’API).

---

## 3. Java (packages et classes)

- Package **org.ezkey.m2m** → **org.ezkey.integration.api** (éviter le conflit avec le domaine `org.ezkey.integration` dans core).
- Renommer classes/config : `M2mApplication` → `IntegrationApiApplication`, `M2mSecurityConfig` → `IntegrationApiSecurityConfig`, `M2mAuthAttemptController` → `IntegrationApiAuthAttemptController`, `M2mAuditConstants` → `IntegrationApiAuditConstants`, `M2mJpaConfig` → `IntegrationApiJpaConfig`, etc.
- Toutes les références à `ApiName.M2M_API` et constantes "M2M" dans ce module et dans ezkey-core (audit, DTOs) → `INTEGRATION_API` / "Integration API".

---

## 4. Docker et scripts

- **docker-compose** (tous les fichiers) : service **m2m-api** → **integration-api** ; build context / image alignés sur le nouveau nom de module.
- **Dockerfile** : stage et chemins pour **ezkey-integration-api**.
- **manage.ps1 / manage.sh** : health checks et noms de services **integration-api**.
- **Caddyfile** : upstream **integration-api** au lieu de m2m-api.

---

## 5. Configuration

- Propriétés **ezkey.m2m.*** → **ezkey.integration-api.*** (ou schéma équivalent) dans `application.properties` et `application-docker.properties` du module integration-api.

---

## 6. Documentation

- **README.md** : architecture (arbre des modules), toute mention "M2M" → "Integration API" / ezkey-integration-api.
- **docs/ENDPOINT.md**, **docs/API_KEYS_*.md**, **docs/AUDIT_LOG_*.md**, **docs/DATABASE_PARTITIONING_*.md**, **docs/CONTEXTUAL_AUTH.md** : remplacer M2M / m2m / M2M_API par Integration API / integration-api / INTEGRATION_API ; garder "machine-to-machine" ou "M2M" uniquement pour décrire le mécanisme d’auth (clé API).
- Ne pas modifier à la main les specs OpenAPI sous `specs/` (générées) ; le renommage Java (enum, DTOs) sera reflété à la prochaine génération.

---

## 7. Admin UI et Postman

- **Locales** (en/fr) : `filterApiM2m` → `filterApiIntegration`, "M2M API" / "API M2M" → "Integration API", "M2M credentials" / "accès M2M" → libellés "Integration API" ou "API key".
- **Code** (ex. [audit-logs.tsx](ezkey-admin-ui/src/pages/audit-logs.tsx)) : filtre par `M2M_API` → `INTEGRATION_API`.
- **Postman** : variables `base_url_m2m_api` → `base_url_integration_api` ; nom et description de la collection "Auth Attempts m2m" → "Auth Attempts (Integration API)".

---

## 8. Tests et prompts

- **ezkey-tests** : URLs, noms de services, enum `M2M_API` → Integration API / `INTEGRATION_API`.
- **.github/prompts**, **.cursor/plans** : mettre à jour les prompts/plans qui citent m2m-api / M2M API pour utiliser integration-api / Integration API (archives optionnel).

---

## 9. Récapitulatif des changements BD (axe sous-partitions)

| Élément | Avant | Après |
|--------|--------|--------|
| Valeur `api_name` (audit) | `M2M_API` | `INTEGRATION_API` |
| Sous-partition par mois | `ezkey_audit_log_YYYY_MM_m2m` | `ezkey_audit_log_YYYY_MM_integration` |
| CHECK `api_name` | `('ADMIN_API','AUTH_API','M2M_API')` | `('ADMIN_API','AUTH_API','INTEGRATION_API')` |
| Fonction `create_monthly_partition` | crée _admin, _auth, _m2m | crée _admin, _auth, _integration |

**Stratégie :** Révision directe de V6, V24, V25 (mode dev uniquement, clean start, BD neuve). Aucune nouvelle migration ni migration de données.
