# Plan: Admin UI — Analyse des Concepts, Lacunes et Recommandations

## Portraits des deux profils d'administrateurs

Avant de parler de navigation, il faut bien avoir en tête les workflows quotidiens réels.

**Global Admin — journée type :**
- Gérer les tenants (créer, activer/désactiver, voir les détails organisationnels)
- Provisionner des admins globaux et des admins tenant, gérer leurs credentials
- Surveiller l'ensemble des tenants : audit logs cross-tenant, auth attempts, intégrité de la chaîne
- Gérer les clés de chiffrement : surveiller l'état, déclencher une rotation, monitorer les re-encryption batches
- Opérations d'intégrité sur les audit logs : seal-archive, declare-gap, chain-integrity

**Tenant Admin — journée type :**
- Gérer les intégrations de son tenant (créer, consulter, actions de masse sur enrollments en cas d'incident)
- Gérer les enrollments : créer, distribuer les credentials (QR, token), suivre le statut, lifecycle (désactiver/réactiver/révoquer)
- Gérer les API Keys par intégration (créer, révoquer)
- Monitorage : auth attempts par enrollment/intégration, audit logs de son propre tenant
- Administrer les admins de son tenant (créer, onboarding credentials)

---

## Ce qui est bien en place — ne pas toucher

Les patterns suivants sont solides et cohérents :

- **DataTable + Pagination** — générique, réutilisable, avec sort, empty/loading states ✅
- **Modal Dialog pour les flows de création** avec deux étapes (form → credential reveal) ✅
- **Modal inline pour les détails en lecture seule** (auth attempts, audit logs) ✅
- **Role-gating dans la sidebar** via `roles?: AdminType[]` ✅
- **Danger zone inline** (confirmations destructives sans dialog séparé) ✅
- **Debounced search + filtres multiples** sur les pages liste ✅
- **ProtectedRoute** + session expiry + `AdminType` dans le contexte ✅

---

## Lacunes critiques — workflows non couverts

Ce sont des gaps entre les APIs disponibles et le UI actuel :

**1. Page Tenants — absente (GLOBAL_ADMIN uniquement)**
C'est le vide le plus important. Les 7 endpoints de `/api/v1/tenants` (list, create, get, update, activate, deactivate) n'ont aucune couverture UI. Pour un Global Admin, c'est le point de départ de tout : créer un tenant avant de pouvoir créer des intégrations et des admins tenant.

**2. Lifecycle des enrollments — absent sur la page détail**
La page `enrollment-detail.tsx` permet de voir les infos et de tester une auth, mais pas d'agir sur le cycle de vie. Les actions `revoke`, `deactivate`, `reactivate`, et `DELETE` sont toutes disponibles en API mais absentes de l'UI. C'est un workflow opérationnel quotidien pour un Tenant Admin.

**3. Danger Zone intégration — absente**
La page `integration-detail.tsx` n'a ni `DELETE integration`, ni les actions de masse : `revoke-all`, `deactivate-all`, `reactivate-all`. Ces dernières sont critiques en cas d'incident de sécurité.

**4. Création d'un Global Admin — absente**
Le formulaire de création dans `admins.tsx` ne poste que vers `/api/v1/admins/tenant`. L'endpoint `/api/v1/admins/global` n'a pas de chemin UI. Seul un GLOBAL_ADMIN peut faire cette action — elle doit être disponible dans le UI.

**5. Opérations d'intégrité des Audit Logs — absentes (GLOBAL_ADMIN)**
Les endpoints `integrity-check`, `chain-integrity`, `seal-archive`, et `declare-gap` sont entièrement absents. Ces opérations sont les plus sensibles du système (SOC 2, conformité) et méritent une section dédiée dans la page Audit Logs pour les Global Admins.

**6. Pagination sur API Keys — absente**
La page api-keys charge toutes les clés en une seule requête. À l'échelle, c'est un problème. Les autres collections API Keys supportent la pagination implicitement.

**7. Champs manquants dans les formulaires**
- API Key create : le champ `ipWhitelist` n'est pas exposé
- Auth Attempts : `contextTitle` / `contextMessage` ne sont pas affichés dans le détail
- Auth Attempts : pas de flow UI pour créer manuellement une tentative (utile pour les tests d'intégration depuis l'Admin UI)

---

## Recommandations UI — concepts à ajouter (80/20)

**Breadcrumbs — oui, mais ciblés**
La hiérarchie actuelle peut atteindre 3 niveaux : *Tenants → Tenant Detail → Admins de ce tenant*. Sur les pages `detail`, un breadcrumb simple (`← Intégrations / Mon Intégration`) offre une navigation contextuelle claire. L'implémentation est légère : une prop `breadcrumb?: { label: string; path: string }[]` passée à `AppShell`, rendue en quelques lignes. **C'est pertinent, faible coût, recommandé.**

**Tabs sur les pages détail — différer**
Les détail pages actuelles empilent des `Card` verticalement. Quand une page détail aura 4+ sections (ex: Integration Detail avec Info, Enrollments, API Keys, Danger Zone), les tabs simplifient la navigation. **Différer jusqu'à ce qu'une page détail dépasse 4 sections.** Un composant `Tabs` basique (3 variantes visuelles) suffit.

**Toasts / notifications de succès — recommandés**
Actuellement, le succès d'une création passe par l'état interne du dialog (step 2). Pour les actions qui ferment le dialog immédiatement (revoke, deactivate, delete), il n'y a aucun feedback visuel global. Un toast léger (une demi-douzaine de lignes, sans dépendance externe) renforcerait la confiance UX sans complexité. **Coût faible, valeur perçue élevée.**

**Filtre tenant sur les pages liste (GLOBAL_ADMIN) — ciblé**
Pour un Global Admin, les pages Integrations, Enrollments, Admins, et Auth Attempts retournent des données cross-tenant. Un `Select` tenant en haut des pages liste (similaire au filtre integration déjà présent) avec les données de `/api/v1/tenants` permettrait un scope contextuel explicit. **À implémenter sur les pages liste au moment où on implémente la page Tenants.**

**Inline edit sur Integrations et Tenants — à prévoir**
Les endpoints `PUT /api/v1/integrations/:id` et `PUT /api/v1/tenants/:id` existent. Un bouton "Edit" sur les pages détail ouvrant le Dialog de création pré-rempli suffit. **Pattern déjà établi — coût quasi nul à implémenter en même temps que les pages concernées.**

---

## Items actionnables par workflow futur

### Workflow Tenants (GLOBAL_ADMIN)
- Page `/tenants` : list (DataTable + Pagination + search + filtre active/inactive + tri)
- Page `/tenants/:id` : detail avec info organisationnelle, sous-liste des admins de ce tenant, actions activate/deactivate + breadcrumb
- Dialog Create Tenant (form complet : `tenantName`, `description`, `organizationName`, `organizationDomain`, `countryCode`, `timezone`, `primaryContactName`, `primaryContactEmail`)
- Dialog Edit Tenant (même form pré-rempli)
- Role-gate sidebar : `roles: ['GLOBAL_ADMIN']`
- Une fois la page Tenants disponible : ajouter le filtre tenant sur Integrations, Enrollments, Admins, Auth Attempts

### Workflow Admin Provisioning
- Sur `admins.tsx` : condition sur `adminType === GLOBAL_ADMIN` pour afficher un bouton séparé "New Global Admin" (ou un switch de type dans le dialog Create Admin existant) qui poste vers `/api/v1/admins/global`

### Workflow Enrollment Lifecycle
- Sur `enrollment-detail.tsx` : section Danger Zone avec Deactivate / Reactivate / Revoke / Delete
- Pattern : inline two-step confirm (déjà établi dans la codebase)
- Deactivate et Reactivate sont mutuellement exclusifs selon `enrollmentActive`
- Revoke est irréversible → confirm dialog obligatoire avec phrasing fort

### Workflow Integration Danger Zone
- Sur `integration-detail.tsx` : section Danger Zone avec Deactivate All / Revoke All / Reactivate All / Delete Integration
- Revoke All = action irréversible → phrasing fort dans la confirmation ("All enrollments will be permanently revoked")
- Ajouter breadcrumb `← Integrations / {name}`

### Workflow Audit Log Integrity (GLOBAL_ADMIN)
- Sur `audit-logs.tsx` : panneau conditionnel (GLOBAL_ADMIN only) avec :
  - Bouton "Check Chain Integrity" → résultat inline (shield icon + status)
  - Bouton "Verify Range Integrity" → date range picker + résultat
  - Action "Seal Archive" → Dialog form avec `periodStart` / `periodEnd` / `justification`
  - Action "Declare Gap" → Dialog form avec `gapStart` / `gapEnd` / `justification`
- Alternative : sous-page `/audit-logs/integrity` accessible uniquement aux GLOBAL_ADMIN (route gatée)

### Workflow Encryption Keys (GLOBAL_ADMIN)
- Implémenter la vraie page : list des clés (status PRIMARY/ENABLED, algorithm, ID) — DataTable sans pagination (peu de clés)
- Actions : Rotate Key (champ `reason` optionnel pour audit trail SOC 2), View re-encryption batches, Resume batch
- Badge de statut : `PRIMARY` → `success`, `ENABLED` → `default`, autres → `muted`

### Améliorations transversales
- Ajouter `breadcrumb?: { label: string; path: string }[]` prop à `AppShell` — detail pages de Integrations, Enrollments, Tenants
- Toast system léger (sans dépendance externe) pour confirmations de succès sur mutations destructives
- API Keys : ajouter pagination, ajouter champ `ipWhitelist` au dialog de création
- Auth Attempts : afficher `contextTitle` / `contextMessage` dans le detail dialog

---

## Récapitulatif de l'état de santé actuel

| Dimension | État | Note |
|---|---|---|
| Navigation role-aware | ✅ Bon | Seule Encryption Keys est gatée — Tenants manque |
| Patterns liste | ✅ Solide | DataTable + Pagination réutilisables |
| Patterns création | ✅ Solide | Two-step avec credential reveal établi |
| Patterns détail | ⚠️ Incomplet | Lifecycle actions manquantes sur Enrollments et Integrations |
| Pages manquantes critiques | ❌ Gap | Tenants absente — workflow GLOBAL_ADMIN bloqué |
| Audit log integrity | ❌ Gap | Opérations conformité absentes |
| Feedback visuel mutations | ⚠️ Acceptable | Toasts absents — suffisant aujourd'hui, à corriger |
| Breadcrumbs | ⚠️ Utile | Absent — faible coût à ajouter sur detail pages |
| Formulaires incomplets | ⚠️ Mineur | ipWhitelist, contextTitle/contextMessage |

Le socle technique est sain et bien pensé. Les patterns établis (DataTable, Dialog, Badge, Alert) sont cohérents et extensibles. Les investissements prioritaires sont la **page Tenants** et la **complétion des pages détail** (lifecycle Enrollment + Danger Zone Integration) — le reste relève de l'amélioration progressive.
