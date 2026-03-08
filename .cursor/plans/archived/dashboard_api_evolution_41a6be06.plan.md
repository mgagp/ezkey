---
name: Dashboard API Evolution
overview: Analyse du dashboard actuel du Tenant UI et recommandations d'évolution d'API pour réduire les 11 appels parallèles actuels à une architecture tiered efficace, respectant les vitesses de refresh différentes et les principes de pragmatisme d'EZKey.
todos:
  - id: backend-dto
    content: Créer DashboardOverviewDto avec les nested records (IntegrationStats, EnrollmentStats, Auth24hStats, RecentActivityItem)
    status: completed
  - id: backend-service
    content: Créer DashboardService avec requêtes parallèles CompletableFuture, tenant-scoped, fenêtre 24h côté serveur
    status: completed
  - id: backend-controller
    content: Créer DashboardController GET /api/v1/dashboard/overview avec @PreAuthorize ROLE_ADMIN
    status: completed
  - id: backend-pending-count
    content: Ajouter GET /api/v1/auth-attempts/pending-count dans AuthAttemptController (ou DashboardController)
    status: completed
  - id: frontend-types
    content: Ajouter type DashboardOverview dans ezkey-admin-ui/src/types/models.ts
    status: completed
  - id: frontend-dashboard
    content: "Refactorer dashboard.tsx : 11 useQueries → 2 useQueries, données présentées depuis overview + pending count"
    status: completed
isProject: false
status: implemented
archived: true
---

# Dashboard API — Analyse et Recommandations d'Evolution

**Implementation status:** Fully implemented and ready for archival. Backend (DTOs, DashboardService, DashboardController, pending-count), frontend (ezkey-admin-ui: types, 2 useQueries, Audit chain alerts widget for Global Admin), Postman collections, and elective test (DashboardOverviewElectiveTest with DB spot checks) are in place. Verified by maintainer.

## Diagnostic : Le problème actuel

Le dashboard (`ezkey-tenant-ui/src/pages/dashboard.tsx`) fait **11 appels parallèles** via `useQueries` à chaque montage de composant :

```
 Count queries (size=1 trick)  ──────── 10 appels
 Recent activity                ────────  1 appel
                                         ───────
                                Total : 11 appels
```

Le hack `size=1` : chaque call de comptage demande une page de 1 élément, puis lit uniquement `totalElements` depuis l'enveloppe Spring Data `Page<T>`. Le backend exécute quand même un `COUNT(*)` + fetch d'1 enregistrement + sérialise le wrapper complet (`pageable`, `sort`, `numberOfElements`, etc.). C'est fonctionnel mais incorrect semantiquement et inefficace.

### Décomposition des 11 appels par tier de refresh


| Tier   | Intervalle | Appels | Données                                                                      |
| ------ | ---------- | ------ | ---------------------------------------------------------------------------- |
| Lent   | 60s        | 6      | Intégrations (total, active) + Enrollments (total, VERIFIED, BOUND, CREATED) |
| Moyen  | 30s        | 4      | Auth attempts 24h (total, ACCEPTED, REJECTED) + Recent audit logs (5 items)  |
| Rapide | 10s        | 1      | Auth attempts PENDING (live)                                                 |


---

## Analyse : Patterns industrie pour admin dashboards

### Pattern 1 — BFF (Backend for Frontend)

Une couche d'agrégation dédiée (ex: Next.js API route, Spring Gateway) fait les appels internes et sert un payload unique au dashboard. Utilisé par de grandes plateformes (Stripe Dashboard). **Verdict EZKey : sur-complexité, exclure.**

### Pattern 2 — GraphQL avec field selection

Le client demande exactement les champs voulus. Élimine les over-fetches. **Verdict EZKey : coût architectural disproportionné, exclure.**

### Pattern 3 — Endpoint `/summary` monolithique

Un seul endpoint retourne toutes les stats dans une réponse JSON structurée. Simple, mais impose un seul TTL côté client — ne convient pas quand les données ont des vitesses de stalement différentes. Utilisé par Auth0 Management API (tenant stats), Keycloak Admin UI.

### Pattern 4 — Endpoints `/count` sémantiques + agrégat tiered (recommandé)

Combine deux idées :

1. **Endpoints de comptage** (`GET /resource/count`) plutôt que le hack `size=1`
2. **Agrégats par tier de fraîcheur** — le client poll chaque agrégat à sa propre cadence

C'est le pattern utilisé par GitHub API (`/repos/{owner}/{repo}/stats/commit_activity`), Stripe (`/v1/balance`), et des frameworks admin matures comme Forest Admin ou Retool.

---

## Recommandation : Architecture tiered en 2+1 endpoints

### Principe directeur

- **11 appels → 2 appels** pour les données lentes/moyennes (agrégation backend)
- **1 appel** pour la donnée live — conserver ou améliorer
- **Evolutivité** : le payload JSON est extensible sans breaking change (ajout de sections)
- **Pas de BFF** : les endpoints vivent directement dans `ezkey-admin-api`

---

### Endpoint 1 — `GET /api/v1/dashboard/overview` (60s refresh)

Consolide les 10 appels lents + moyens en un seul.

**Payload de réponse (`DashboardOverviewDto`)** :

```json
{
  "integrations": {
    "total": 12,
    "active": 10,
    "inactive": 2
  },
  "enrollments": {
    "total": 87,
    "verified": 75,
    "bound": 8,
    "created": 4
  },
  "auth24h": {
    "total": 134,
    "accepted": 120,
    "rejected": 14,
    "failureRatePct": 10
  },
  "recentActivity": [
    {
      "auditLogId": "...",
      "eventType": "AUTH_ATTEMPT",
      "eventStatus": "SUCCESS",
      "eventAction": "login",
      "apiName": null,
      "adminId": null,
      "createdAt": "2026-02-28T10:00:00Z"
    }
  ]
}
```

**Implémentation backend** :

- Nouveau `DashboardController` dans `ezkey-admin-api`
- `DashboardService` qui exécute les requêtes de comptage en parallèle (4 threads via `CompletableFuture` ou requêtes SQL directes)
- Tenant-scoped via `AdminPrincipal` (identique aux autres controllers)
- Fenêtre 24h calculée **côté serveur** (élimine le `since24h = useMemo` du client)
- Le `failureRatePct` calculé côté serveur (logique métier au bon endroit)

```java
// DashboardController.java
@GetMapping("/api/v1/dashboard/overview")
@PreAuthorize("hasRole('ROLE_ADMIN')")
public DashboardOverviewDto getOverview(AdminPrincipal principal) {
    return dashboardService.buildOverview(principal.getTenantId());
}
```

**Fichiers à créer :**

- `[ezkey-admin-api/src/main/java/org/ezkey/admin/controller/DashboardController.java](ezkey-admin-api/src/main/java/org/ezkey/admin/controller/DashboardController.java)`
- `[ezkey-admin-api/src/main/java/org/ezkey/admin/service/DashboardService.java](ezkey-admin-api/src/main/java/org/ezkey/admin/service/DashboardService.java)`
- `[ezkey-admin-api/src/main/java/org/ezkey/admin/dto/response/DashboardOverviewDto.java](ezkey-admin-api/src/main/java/org/ezkey/admin/dto/response/DashboardOverviewDto.java)`

---

### Endpoint 2 — `GET /api/v1/auth-attempts/pending-count` (10s refresh)

Remplace le `size=1&status=PENDING` hack pour la donnée live.

**Payload** :

```json
{ "count": 3 }
```

**Pourquoi un endpoint dédié et non le hack existant ?**

- Semantique correcte : une requête de count ne devrait pas retourner une page
- Côté DB : simple `SELECT COUNT(*) WHERE status='PENDING' AND tenantId=?` — pas de LIMIT 1 inutile
- Transfert réseau minimal : 15 octets vs ~500 octets (enveloppe Spring `Page<T>`)

**Alternative acceptable (maintient API surface minimale)** : Ajouter un paramètre `countOnly=true` aux endpoints de liste existants qui retourne `{"totalElements": N}`. Moins pur mais évite de créer un endpoint `count` par resource.

```java
// Dans AuthAttemptController.java — ajout minimal
@GetMapping("/api/v1/auth-attempts/pending-count")
@PreAuthorize("hasAnyRole('ROLE_ADMIN', 'ROLE_API_KEY')")
public Map<String, Long> getPendingCount(AdminPrincipal principal) {
    long count = authAttemptService.countByStatusAndTenant("PENDING", principal.getTenantId());
    return Map.of("count", count);
}
```

---

## Impact côté frontend

Le dashboard passe de 11 queries à **2 queries** :

```typescript
// dashboard.tsx — nouvelle version
const results = useQueries({
  queries: [
    {
      queryKey: ['dashboard', 'overview'],
      queryFn: () => api.get<DashboardOverview>('/api/v1/dashboard/overview'),
      staleTime: 60_000,
      refetchInterval: 60_000,
    },
    {
      queryKey: ['dashboard', 'pending'],
      queryFn: () => api.get<{ count: number }>('/api/v1/auth-attempts/pending-count'),
      staleTime: 10_000,
      refetchInterval: 10_000,
    },
  ],
});
```

Le type `DashboardOverview` mappe exactement le `DashboardOverviewDto` backend. Toutes les valeurs calculées (`failureRatePct`, `inactive`) viennent du serveur — le dashboard devient purement présentationnel.

---

## Evolutivité — Comment le dashboard peut grandir

La structure de `DashboardOverviewDto` est extensible par **ajout de sections** :

- **Section `admins`** : `{ total, active }` — si on veut compter les admins
- **Section `apiKeys`** : `{ total, expiringIn30Days }` — alertes de clés expirantes
- **Section `encryptionHealth`** : `{ primaryKeyAge, reencryptionInProgress }` — état du chiffrement
- **Section `alerts`** : liste de messages d'alertes système prioritaires

Chaque nouvelle section est un champ JSON optionnel — le client existant l'ignore s'il ne le connaît pas. Pas de versioning d'API requis pour les ajouts.

Pour des widgets avec des vitesses très différentes (ex: alertes critiques à 5s, rapports hebdomadaires), l'approche reste la même : endpoint dédié avec son propre polling interval.

---

## Résumé des décisions clés

- **Nouvelle surface API** : 2 endpoints (`/dashboard/overview` + `/auth-attempts/pending-count`)
- **Réduction des appels** : 11 → 2 (−82%)
- **Logique métier** : calculs (`failureRatePct`, fenêtre 24h) migrent vers le serveur
- **Polling tiered** : conservé côté client via TanStack Query (60s + 10s)
- **Pas de BFF** : endpoints natifs dans `ezkey-admin-api`, scoped au tenant
- **Evolutivité** : le DTO `overview` est extensible par ajout de sections JSON optionnelles
