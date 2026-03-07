# Phase 2 — Migration vers les hooks Orval : inventaire et évaluation

**Contexte :** Plan [plan-adminUiOpenapiClientGeneration.prompt.md](../.github/prompts/plan-adminUiOpenapiClientGeneration.prompt.md). La Phase 1 (types générés, suppression de `src/types/`) est faite. La Phase 2 consiste à remplacer les appels manuels `api.get/post/...` et le hook custom `usePaginatedQuery` par les hooks TanStack Query générés par Orval.

**Objectif de ce document :** Inventaire actualisé des écrans et APIs concernés, vérification de la couverture, et identification des angles morts avant de lancer la génération / migration.

---

## 1. Résumé exécutif

| Métrique | Valeur |
|----------|--------|
| **Pages utilisant `usePaginatedQuery`** | **7** (listes paginées) |
| **Pages avec `api.*` manuel (queries/mutations)** | **12** (toutes les pages qui touchent l’API) |
| **Hook custom à supprimer après migration** | `use-paginated-query.ts` |
| **Hook custom à migrer / remplacer** | `use-integrations.ts` (liste + lookup) |
| **Endpoints paginés dans le spec** | 5 (integrations, enrollments, auth-attempts, audit-logs, admins) |
| **Compatibilité Orval** | Les hooks générés retournent `PagedModel*Dto` (content + page) ; il faudra un **adaptateur pagination** pour garder le même contrat que `<Pagination>` / `<DataTable>`.

**Estimation chantier :** ~1–1,5 jour (aligné avec le plan). Risque de régression modéré ; tests manuels page par page requis.

---

## 2. Inventaire par écran

### 2.1 Listes paginées (remplacer `usePaginatedQuery` + `api.get` par hook Orval `useGet*`)

| # | Page | Fichier | Endpoint | Filtres / paramètres | Hook Orval attendu |
|---|------|---------|----------|----------------------|--------------------|
| 1 | Integrations | `integrations.tsx` | `GET /api/v1/integrations` | page, size, sort, integrationName?, active? | `useSearchIntegrations` (opération `search`) |
| 2 | Enrollments | `enrollments.tsx` | `GET /api/v1/enrollments` | page, size, sort, enrollmentName?, status?, integrationId?, active? | `useSearch_1` (ou nom généré pour Enrollments) |
| 3 | Auth Attempts | `auth-attempts.tsx` | `GET /api/v1/auth-attempts` | page, size, sort, status?, enrollmentId?, integrationId?, createdAfter?, createdBefore? | `useSearch_2` (ou nom généré Auth Attempts) |
| 4 | Audit Logs | `audit-logs.tsx` | `GET /api/v1/audit-logs` | page, size, sort, eventType?, eventStatus?, apiName?, createdAfter?, createdBefore? | `useGetAuditLogs` |
| 5 | Admins | `admins.tsx` | `GET /api/v1/admins` | page, size, sort | `useListAdmins` |
| 6 | Tenant detail (liste admins) | `tenant-detail.tsx` | `GET /api/v1/admins` | tenantId?, page, size, sort | **Même** `useListAdmins` ; le paramètre optionnel `tenantId` est dans le spec (GlobalAdmin only). |
| 7 | Integration detail (liste enrollments) | `integration-detail.tsx` | `GET /api/v1/enrollments` | integrationId, page, size, sort | Même hook que Enrollments avec `integrationId` |

Les 7 usages ci-dessus passent par `usePaginatedQuery` + `api.get<PageResponse<T>>(...)`. Chacun devra être remplacé par le hook généré correspondant, avec un **wrapper ou helper** qui transforme le retour Orval (objet avec `content` + `page`) en le contrat actuel attendu par `<Pagination>` et `<DataTable>` (liste `data` + objet `pagination` avec page, size, sort, totalPages, totalElements, isFirst, isLast, setSort, setPageSize, prevPage, nextPage, goToPage).

---

### 2.2 Pages avec queries/mutations manuelles (sans pagination)

| # | Page | Fichier | Usages `api.*` / hooks manuels | Hook Orval cible |
|---|------|---------|--------------------------------|------------------|
| 8 | Enrollment detail | `enrollment-detail.tsx` | GET enrollment, GET auth-attempt (poll), POST create/cancel auth-attempt, POST deactivate/reactivate/revoke, DELETE enrollment | `useGetEnrollment`, `useGetAuthAttempt`, `useCreateAuthAttempt`, `useCancelAuthAttempt`, mutations enrollment |
| 9 | Integration detail | `integration-detail.tsx` | GET integration, POST deactivate-all/reactivate-all/revoke-all, DELETE integration | `useGetIntegration`, mutations bulk + DELETE |
| 10 | Tenant detail | `tenant-detail.tsx` | GET tenant, PUT tenant, POST activate/deactivate tenant | `useGetTenant`, `useUpdateTenant`, mutations lifecycle |
| 11 | API Keys | `api-keys.tsx` | GET list (non paginé), POST create, DELETE revoke | `useGetApiKeys` (ou équivalent list), `useCreateApiKey`, `useRevokeApiKey` |
| 12 | API Key detail | `api-key-detail.tsx` | GET api-key, PATCH api-key | `useGetApiKey`, `useUpdateApiKey` (PATCH) |
| 13 | Admins | `admins.tsx` | GET onboarding, GET admin detail (dialog), POST activate/deactivate, POST provision (global/tenant) | hooks get + mutations |
| 14 | Encryption Keys | `encryption-keys.tsx` | GET key, GET batches, POST trigger/create-batches/resume, etc. | Hooks Encryption Keys tag |
| 15 | Tenants | `tenants.tsx` | GET /api/v1/tenants (liste complète), POST create | Liste non paginée → hook généré list |
| 16 | Dashboard | `dashboard.tsx` | Plusieurs `api.get` pour stats (integrations, enrollments, auth-attempts, audit-logs avec size=1 ou 5) | Remplacer par hooks générés avec params fixes (size=1, etc.) |

---

### 2.3 Hook `use-integrations.ts`

- **Rôle actuel :** Charge toutes les intégrations (size=100), expose `list` + `lookup` (Map id → nom). Utilisé par : enrollments, integration-detail, api-keys, enrollment-detail (pour affichage noms).
- **Migration :** Remplacer par un hook généré du type `useSearchIntegrations` ou équivalent avec `size: 100`, puis dériver `list` et `lookup` dans le composant ou un petit hook wrapper qui utilise ce hook généré. La fonction **`getIntegrationName`** peut rester dans un util ou être réexportée depuis un module partagé.

---

## 3. Composants impactés (pagination)

- **`src/components/data-table/pagination.tsx`** — Reçoit aujourd’hui l’objet `pagination` retourné par `usePaginatedQuery`. Après migration, cet objet devra être produit soit par un **hook wrapper** autour du hook Orval (ex. `usePaginatedAdmins` qui appelle `useListAdmins` et renvoie `{ data, pagination }` au même format), soit par un helper qui construit `pagination` à partir de `data?.page` et d’un state local (page, size, sort).
- **`src/components/data-table/data-table.tsx`** — Utilise `currentSort` et `onSort` ; inchangé tant que le contrat `pagination.sort` / `pagination.setSort` est respecté.

Aucun changement de contrat des composants UI n’est nécessaire si on introduit une couche d’adaptation (wrapper ou helper) entre les hooks Orval et les pages.

---

## 4. Angles morts et vérifications

### 4.1 GET /api/v1/admins et paramètre `tenantId` — résolu

- **État actuel :** Dans `tenant-detail.tsx`, l’UI envoie `tenantId=${tenantId}` en query. Dans le **spec OpenAPI** actuel, l’opération `listAdmins` ne déclare **pas** de paramètre `tenantId`. Dans le backend (`AdminProvisioningController.listAdmins`), seul `Pageable` est pris en compte ; le `tenantId` est dérivé de l’auth (`extractTenantId(auth)`), pas de la requête.
- **Conséquence :** Aujourd’hui, le paramètre `tenantId` envoyé par l’UI est **ignoré** par le serveur. Un Global Admin sur la page « Tenant detail » voit donc tous les admins, pas seulement ceux du tenant affiché.
- **Recommandation :**
  - **Option A (court terme, Phase 2) :** Migrer quand même vers le hook généré `useListAdmins(page, size, sort)` sans `tenantId`. Comportement identique à aujourd’hui (pas de régression). Traiter l’ajout de `tenantId` côté backend + spec comme une évolution séparée.
  - **Option B (avant Phase 2) :** Faire évoluer le backend pour accepter un `tenantId` optionnel (Global Admin only), mettre à jour le spec via `update-specs.sh`, puis utiliser le hook généré avec ce paramètre sur la page tenant-detail.

### 4.2 Spec et build (openapi-spec.json versionné)

- **`ezkey-admin-ui/openapi-spec.json`** est **versionné** (non gitignored). Il est mis à jour après chaque redémarrage Docker quand les API ont changé. On peut **présumer qu'il est toujours disponible** pour un build et pour la régénération Orval (DTO + hooks). Seul `src/generated/` reste gitignored.

### 4.3 Format du paramètre `sort` (array vs string)

- Le spec définit souvent `sort` comme **array** de strings (ex. `["createdAt,DESC"]`), alors que l’UI et `usePaginatedQuery` utilisent une **chaîne unique** (ex. `"createdAt,DESC"`). Vérifier comment Orval sérialise le paramètre (array → `sort=createdAt,DESC` ou `sort=createdAt&sort=DESC`) et adapter soit le hook, soit les paramètres passés depuis les pages, pour rester compatible avec le backend.

### 4.3 Fichiers générés absents au check-out

- `src/generated/admin-api/` est **gitignored**. Pour que le build et la Phase 2 soient reproductibles, il faut que le spec soit disponible avant `npm run build` (ex. via `scripts/update-specs.sh --admin-only` ou copie du spec dans `ezkey-admin-ui/openapi-spec.json`). Documenter ou automatiser cette étape en CI (comme indiqué dans le plan).

### 4.4 Noms d’opérations générés (search vs list)

- Les opérations du spec ont des noms comme `search`, `search_1`, `search_2`, `listAdmins`, `getAuditLogs`. Orval génère les noms de hooks à partir de ces `operationId`. Vérifier après la première génération les noms exacts (`useSearch`, `useSearch_1`, etc.) et prévoir des alias ou un mapping clair dans la doc interne pour éviter la confusion.

---

## 5. Plan d’action recommandé avant génération

1. **Lancer** `npm run generate:api` (le spec `openapi-spec.json` est versionné et présent). Noter les noms exacts des hooks pour les 5 endpoints paginés + les mutations utilisées.
2. **Concevoir** l’adaptateur pagination : soit un hook générique `usePaginatedFromOrval(useGetXxx, params)` qui retourne `{ data, pagination }` au format actuel, soit un hook par ressource (ex. `usePaginatedIntegrations`, `usePaginatedEnrollments`, …) qui encapsule le hook Orval et le state page/size/sort.
3. **Migrer** les 7 pages listées en § 2.1 une par une, puis les autres pages (§ 2.2), en remplaçant `use-integrations.ts` par le hook généré + getIntegrationName.
4. **Supprimer** `use-paginated-query.ts` et mettre à jour les imports.
5. **Valider** : `npm run build`, `npm run dev`, test manuel de chaque écran (listes, tris, pagination, filtres, mutations).

---

## 6. Tableau de bord des écrans et APIs (synthèse)

| Écran | usePaginatedQuery | api.get/post/put/delete/patch | Hook(s) Orval à utiliser |
|-------|-------------------|-------------------------------|---------------------------|
| integrations | ✅ | ✅ (create) | useSearch + useCreateIntegration |
| enrollments | ✅ | ✅ (create) | useSearch_1 + useCreateEnrollment |
| auth-attempts | ✅ | — | useSearch_2 |
| audit-logs | ✅ | ✅ (seal, gap, integrity) | useGetAuditLogs + mutations lifecycle |
| admins | ✅ | ✅ (onboarding, detail, activate, deactivate, provision) | useListAdmins + get/mutations |
| tenant-detail | ✅ (admins) | ✅ (tenant, update, activate/deactivate) | useListAdmins(tenantId?, page, size, sort), useGetTenant, mutations |
| integration-detail | ✅ (enrollments) | ✅ (integration, bulk, delete) | useSearch_1(integrationId), useGetIntegration, mutations |
| enrollment-detail | — | ✅ (enrollment, auth-attempts, lifecycle) | useGet + mutations |
| api-keys | — | ✅ (list, create, revoke) | useGet* + useCreate + useRevoke |
| api-key-detail | — | ✅ (get, patch) | useGet + usePatch |
| tenants | — | ✅ (list, create) | useGetTenants + useCreateTenant |
| dashboard | — | ✅ (plusieurs stats) | Hooks GET avec params size=1 / size=5 |
| encryption-keys | — | ✅ (multiples) | Hooks tag Encryption Keys |
| login | — | (auth flow dédié) | Hors scope Phase 2 (auth) |

Ce document peut servir de référence pour la Phase 2 et pour les revues de code (vérification qu’aucun appel manuel ne reste sur les endpoints couverts par Orval).
