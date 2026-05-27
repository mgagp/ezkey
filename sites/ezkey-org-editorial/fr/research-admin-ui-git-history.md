---
status: research
audience: "Matériel de recherche pour le draft `draft-peindre-un-admin-ui-en-collaboration-avec-l-ia.md`. Non destiné à publication. Décision commit / no-commit reportée."
scope: "branche `main`, fenêtre 2026-01-01 → 2026-05-15"
sources:
  - git log --pretty=format:'%h|%ad|%s' --date=short -- ezkey-admin-ui/
  - git log -- specs/
  - git log -- ezkey-admin-api/
  - git log -- 'docs/ADMIN_UI*.md' 'docs/admin-ui-*.md'
  - git log -- plans/ .cursor/plans/
  - git log --all --grep='admin[- ]?ui|orval|dashboard|i18n|RFC ?9457|csrf|cookie|tooltip|popover|paginat|locale|timezone' -iE
unique_commits: 353
---

# Recherche Git — Évolution du Admin UI (janv. → début mai 2026)

Rapport condensé pour soutenir la rédaction de l'article essai sur l'élaboration de l'admin UI Ezkey. Les commits sont regroupés par catégorie thématique et ordonnés chronologiquement à l'intérieur de chaque catégorie. Un marqueur **🆕** signale les sujets que le brain dump verbatim n'évoque pas (ou évoque indirectement) mais qui ressortent des logs comme suffisamment significatifs pour mériter une discussion lors de la Phase 2 du plan.

## Stack exact (snapshot du `package.json` au moment de la recherche)

- **React 19.2** + **react-dom 19.2** + TypeScript ~6.0
- **Vite 8** (bundler) + `@vitejs/plugin-react`
- **TanStack Query 5** (cache serveur + invalidation)
- **react-router-dom 7**
- **react-hook-form 7** + **zod 4** + `@hookform/resolvers`
- **i18next 26** + **react-i18next 17** + `i18next-browser-languagedetector`
- **Tailwind CSS 4** + `tailwind-merge` + `clsx`
- **lucide-react** (icônes) + `qrcode`
- **Orval 8** (génération du client typé depuis l'OpenAPI Admin API)
- **Playwright** (tests browser) + **Vitest** (tests unitaires)
- ESLint 10 + typescript-eslint

Aucune lib UI lourde (pas de MUI, pas de Chakra, pas d'Ant Design) : la signature visuelle repose sur Tailwind + composants maison + lucide.

---

## 1. Inception & ancêtres (CLI Python TUI → tenant-ui → admin-ui)

La généalogie réelle observée dans les logs : **TUI Python d'abord, tenant-ui React ensuite, puis bascule rapide vers un admin-ui unifié**.

- `07754ba9` 2026-01-31 — feat: Add interactive TUI for Ezkey admin console
- `865e7cfd` 2026-01-31 — Add authentication attempts management screens and functionality (TUI)
- `83efcee4` 2026-01-31 — feat: Add filter modal for searching integrations (TUI)
- `c03a543d` 2026-01-31 — feat: Add tenant management functionality to the TUI
- `defc4b7b` 2026-02-01 — Update usage guide and CLI controller review; enhance TUI features
- `befa6452` 2026-01-30 — feat: add pagination utilities for handling paginated API responses (TUI)
- `01ab9613` 2026-02-11 — fix: Ensure minimum page size of 10 in TenantsScreen for pagination stability (TUI)
- `03db86ff` 2026-03-15 — feat(tui): enhance TUI documentation and functionality for read-only operations

Puis le passage React :

- `fa1a7060` 2026-02-28 — feat: **Initialize ezkey-tenant-ui project structure** with essential files
- `40f3e1a8` 2026-02-28 — Update third-party dependencies and enhance tenant UI with new Docker setup
- `36b17a9e` 2026-02-28 — Enhance EZKey Tenant UI with new pages and components
- `42ee1c0e` 2026-02-28 — Introduce Test Authentication feature in Enrollment Detail page
- `dd3bf192` 2026-02-28 — Enhance data table and pagination components for improved sorting and page size selection
- `e8375381` 2026-03-01 — Merge branch 'Branch_tenant_ui'
- `356693bf` 2026-03-06 — chore(plans): **remove obsolete plans for ezkey-tenant-ui** and related scope evolution *(pivot vers admin-ui unifié)*
- `97669603` 2026-03-06 — **Refactor admin UI to use generated DTOs and update API integration** *(introduction d'Orval — point d'inflexion majeur)*
- `71e27831` 2026-03-06 — feat: add integrations management page with create integration functionality *(premier écran maître/détail de l'admin-ui)*

---

## 2. La couche de fond — première passe horizontale (mars 2026)

Tout se condense sur quelques jours début mars : pose rapide de l'ossature, entité par entité, avec mocks puis branchement API.

- `97669603` 2026-03-06 — Refactor admin UI to use generated DTOs (point de bascule Orval)
- `71e27831` 2026-03-06 — Integrations management page
- `8511e764` 2026-03-08 — feat(dashboard): implement dashboard overview and pending auth attempts count *(première version du Dashboard)*
- `21a1047b` 2026-03-08 — feat(ui): improve Create Admin dialog with reset functionality
- `1246679c` 2026-03-08 — feat(ui): enhance Tenant Admin creation flow with tenant selection
- `9a80bbad` 2026-03-08 — feat(ui): enhance API keys and enrollments pages with integration alerts
- `ec66eaad` 2026-03-08 — feat(ui): update button placement in integrations and tenants pages for improved UX
- `e2ada3c8` 2026-03-08 — feat(ui): enhance tenant list synchronization and query key management

À cette date, la palette d'écrans entité-par-entité est posée (tenants, admins, integrations, api-keys, enrollments, audit-logs, encryption-keys, auth-attempts).

---

## 3. Orval, hooks personnalisés, pagination uniformisée

- `97669603` 2026-03-06 — Bascule vers DTO générés par Orval
- `a84844a8` 2026-03-08 — feat(docker): update Dockerfile to include OpenAPI spec and **Orval API client generation**
- `12c98faa` 2026-03-08 — feat(audit-logs, encryption-keys, login): **migrate to Orval-generated API hooks** and enhance error handling
- `a9df028f` 2026-03-08 — feat(pagination): **migrate to Orval-generated pagination hooks** and remove legacy implementation
- `26144911` 2026-03-08 — docs(migration): update inventory document for Orval hooks migration status
- `f47f1853` 2026-03-07 — feat(sort): standardize `sort` query parameter representation across API and client
- `91557f1b` 2026-03-08 — feat(date-range-presets): implement shared date range presets for admin UI
- `cd2823f9` 2026-03-10 — feat(data-table): enhance sorting functionality for paginated lists
- `2f897729` 2026-03-10 — feat(encryption-keys): paginated listing and filtering
- `23eb2b36` 2026-03-10 — feat(encryption-keys): server-side pagination and filtering
- `bb4fa03a` 2026-03-10 — feat(tenants): server-side pagination
- `8fad560b` 2026-03-10 — feat(tenant): paginated listing with filtering
- `1cd86699` 2026-03-10 — feat(api-keys): server-side pagination
- `405b8f41` 2026-03-10 — feat(api-keys): pagination and filtering
- `37ec8b12` 2026-03-11 — feat(pagination): add first and last page navigation buttons
- `45a76f7a` 2026-03-27` — feat(pagination): document and **archive Admin API pagination uniformity plan** *(revirement assumé : généralisation après hésitations initiales)*
- `f6d976f4` 2026-03-27 — feat(pagination): server-side pagination for re-encryption batches
- `64780f4f` 2026-05-01 — chore: **update orval to version 8.9.0** and regenerate API models

---

## 4. Dashboard — du squelette aux pastilles cliquables

- `95e3a792` 2026-01-24 — feat(dashboard): update dashboard template *(ancêtre TUI/template)*
- `8511e764` 2026-03-08 — Dashboard overview + pending auth attempts count
- `8df433e1` 2026-03-08 — feat(dashboard): **add detailed dashboard API evolution plan** and OpenAPI specifications *(plan : passer d'agrégations multi-requêtes à un endpoint dédié)*
- `894b6914` 2026-03-14 — Dashboard refresh functionality + localization
- `1dc718f9` 2026-03-14 — Event status badge + dashboard localization
- `57919d93` 2026-03-17 — DashboardService gap handling + checkpoint repository
- `267fa207` 2026-03-26 — **Auth health widget** + dashboard statistics
- `0601422b` 2026-03-26 — fix(openapi): dashboard overview and statistics descriptions
- `1cd100b3` 2026-03-28 — Auth attempts widget coherence + localization
- `4e68ab16` 2026-04-14 — **Enrollment dashboard widget with aggregated stats**
- `6d635574` 2026-04-14 — **Clickable dashboard badge drill-downs with audit trail links** *(les « pastilles cliquables » du verbatim)*
- `4732efc0` 2026-04-27 — chore: outline dashboard integration fix plan
- `185b44cc` 2026-04-27 — fix(admin-dashboard): align integration retired stats
- `2ef4e33c` 2026-04-27 — refactor(admin-dashboard): remove inactive backward compat alias
- `a4669bbc` 2026-05-01 — fix: update `anchorCheckpoint` format in dashboard localization files

---

## 5. Internationalisation FR/EN

Vague concentrée le 13 mars (rampe d'i18n), puis raffinements continus.

- `2f93ada7` 2026-03-13 — feat(i18n): **implement internationalization support for the Admin UI** *(mise en place du mécanisme i18next)*
- `f30c89a4` 2026-03-13 — Tenant localization (EN/FR)
- `d251ce70` 2026-03-13 — Admins localization
- `4b132e89` 2026-03-13 — API keys localization
- `9204f9c1` 2026-03-13 — Encryption keys localization
- `5ab69bc6` 2026-03-13 — Integration localization
- `f49fc063` 2026-03-13 — Auth attempts localization
- `43f294c6` 2026-03-13 — Audit logs localization
- `d8ffcecf` 2026-03-13 — Enrollment localization
- `b0cd7b92` 2026-03-13 — i18n pagination UX
- `34c95511` 2026-03-13 — i18n pagination + dashboard
- `da93d6ec` 2026-03-14 — Admin UI + audit logs localization
- `cbb6e766` 2026-03-15 — Help-text localization cleanup
- `86f0cb42` 2026-03-15 — Audit-logs date formatting + period labels localization
- `06e80e20` 2026-03-15 — Audit-logs integrity panel localization
- `34fdc526` 2026-03-31 — fix(locales): primary contact name placeholder EN/FR
- `2e5cdecd` 2026-04-16 — French translations for integrations and tenants + operational warnings
- `bd8cd663` 2026-05-01 — feat(i18n): consolidate i18next setup for EN/FR
- `555e477e` 2026-05-01 — bump i18next 26.0.8 + react-i18next 17.0.6

---

## 6. RFC 9457 (Problem Details) — du backend au rendu UI paramétré

- `9f98fb35` 2026-02-12 — refactor: Enhance error message extraction in EzkeyClient with **RFC 9457 support**
- `195c525b` 2026-02-12 — Exception handler ordering for improved error management
- `fc8af0a1` 2026-02-12 — ValidationExceptionHandler
- `3ed30d85` 2026-02-12 — fix: ProblemDetail type for tenant-not-allowed
- `a80f25ca` 2026-03-07 — feat(enrollment): **RFC 9457 Problem Detail responses** for enrollment deletion and update
- `22cc5087` 2026-03-08 — Exception handling for integration deletion with enrollments
- `eb2fbacd` 2026-03-27 — Enhance error handling and documentation for Auth API
- `a3d3e45f` 2026-04-03 — feat(exception): enhance error handling with ProblemDetail and **introduce AdminApiProblemCatalog**
- `799cba23` 2026-04-03 — Introduce new validation exceptions
- `550573cd` 2026-04-03 — Auth-attempt cancellation error handling
- `b3f18001` 2026-04-03 — AuthAttemptWaitValidationException
- `418f5eb7` 2026-04-03 — AuthAttemptCreateValidationException
- `6974c2b3` 2026-04-04 — refactor(exception): **replace ErrorResponseDto with ProblemDetail** in error handling
- `2df4f5e5` 2026-04-04 — ProblemDetail for Crypto API
- `5add5c17` 2026-04-04 — **Migrate from ErrorResponseDto to ProblemDetail** for standardized responses
- `b60e2da1` 2026-04-06 — GlobalAdminLimitException
- `93adc578` 2026-04-06 — feat(i18n): **Admin UI API error internationalization plan**
- `8c8290c5` 2026-04-06 — feat(i18n): **error message internationalization strategy** *(jonction RFC 9457 ↔ i18n)*
- `8fca8b26` 2026-04-07 — fix(api-error-i18n): type safety in error parameter parsing
- `9c4e26e2` 2026-04-03 — TenantInactiveException

> **Note pour le draft** : la migration ErrorResponseDto → ProblemDetail (avril 2026) et la stratégie d'i18n des erreurs paramétrées (8c8290c5) confirment le verbatim — « ce n'est pas entièrement complété », c'est exact, les derniers commits suggèrent que le slot paramétré est posé mais sa diffusion à tous les écrans reste incrémentale.

---

## 7. Aide contextuelle (tooltips, popovers `?`, corpus documentaire)

- `eb3567f4` 2026-03-09 — feat(audit): audit chain checkpoints endpoint **+ context help component** *(première brique d'aide contextuelle)*
- `6d028ce2` 2026-03-09 — feat(tooltip): **implement tooltip component** and enhance UI with contextual help
- `cbb6e766` 2026-03-15 — Help-text localization cleanup
- `6ef081b6` 2026-03-25 — feat(help): **implement contextual help system in Admin UI** *(le corpus d'aide par écran)*
- `30ca6f92` 2026-04-03 — feat(docs): add Admin UI documentation and update references

---

## 8. Navigation détail ↔ flèches G/D + stabilité visuelle des lignes

- `0debd702` 2026-03-15 — feat(expandable-details): add `useExpandableRelatedDetails` hook + localization
- `29005e24` 2026-03-15 — feat(expandable-details): integrate `useExpandableRelatedDetails` across multiple pages
- `08a8e669` 2026-03-29 — feat(navigation): **implement Prev/Next detail navigation for admin UI** *(les flèches G/D du verbatim)*
- `b48c517b` 2026-04-11 — Contextual navigation for related audits
- `1a286e4d` 2026-05-02 — Enhance enrollment detail and pending auth workflows with inline feedback and improved navigation

> **Note** : les logs ne mentionnent pas explicitement le passage « stabilité visuelle des lignes / saut de pagination » que le verbatim décrit (ajustement de la taille des fenêtres détail pour éviter le saut horizontal). C'est sans doute matérialisé dans `08a8e669` ou un commit de raffinement non explicite — **🆕 à confirmer en Phase 2** : était-ce un commit nommé autrement (hauteur de modale, layout, sticky), ou une série de micro-ajustements non isolés ?

---

## 9. Time zone et format des timestamps

- `ef5e538b` 2026-04-13 — feat(timezone): **implement tenant timezone strategy in Admin UI and API**
- `86f0cb42` 2026-03-15 — Audit-logs date formatting + period labels localization
- `f0e4c568` 2026-05-01 — Audit log timestamps for improved incident correlation
- `42da0bbe` 2026-05-02 — Enrollment last activity tracking + UI for verification timestamps

---

## 10. Identité opérateur, branding, about / version

- `11570d4b` 2026-03-16 — feat(branding): **update brand assets and enhance sidebar with new logo and about dialog**
- `6962e550` 2026-03-16 — Branding (doublon / merge)
- `98ba87ee` 2026-03-31 — feat(docker): **public instance metadata endpoint** and configuration *(endpoint about/version)*
- `ec633519` 2026-04-14 — feat(instance-info): implement public instance-info endpoint for Auth API

---

## 11. Mode démo (clic sur logo, badges thématiques)

- `7b6f86c8` 2026-03-08 — feat(demo): **introduce demo mode functionality for development**
- `2f456397` 2026-03-08 — Placeholders « Garage du coin »
- `19117b66` 2026-03-08 — « Garage du coin » theme (doublon)
- `4869418c` 2026-03-15 — feat(demo-mode): **add DemoReasonBadges component** and integrate into pages
- `4d89e9c6` 2026-03-20 — Demo-mode: optional context fields for auth requests
- `30c9cf4e` 2026-03-27 — Demo: add new demo presets for **InterCube**
- `3b4a90b6` 2026-03-31 — Demo: locale-specific presets and integration support

---

## 12. Foreign keys numériques → résolution lisible (revirement assumé)

Trace indirecte (le revirement n'est jamais nommé tel quel mais affleure dans plusieurs commits) :

- `1eadef32` 2026-03-02 — feat(integration): add tenant ID filtering for integration retrieval *(API qui rendait les FK numériques)*
- `bdf41f80` 2026-03-07 — Admin listing with tenant ID filtering
- `1246679c` 2026-03-08 — feat(ui): **enhance Tenant Admin creation flow with tenant selection** *(début de la résolution lisible côté formulaire)*
- `9a80bbad` 2026-03-08 — API keys / enrollments pages with **integration alerts** (jointures contextuelles)
- `e2ada3c8` 2026-03-08 — Tenant list synchronization + query key management
- `e458d0e4` 2026-04-10 — feat(admin): **expose enrollmentId in AdminResponseDto** and update related APIs
- `aaa0c5e2` 2026-05-03 — feat: **enhance admin management with tenant context and performance improvements** *(matérialisation tardive du revirement)*

> **🆕 à confirmer en Phase 2** : le revirement « FK numériques → noms lisibles » n'a pas de commit unique signature. Le verbatim suggère un « moment dogmatique » suivi d'un revirement assumé — confirmer si c'est diffusé sur plusieurs petites passes (mars-mai) plutôt qu'un chantier explicite.

---

## 13. Gestion d'erreurs UI (toasts, palette, durée, slots backend)

- `12c98faa` 2026-03-08 — Orval-generated hooks + **enhanced error handling**
- `14865304` 2026-03-08 — Error handling for deletion of enrollments linked as admin MFA
- `c6551001` 2026-03-28 — feat(login): **error mapping for passwordless wait failures**
- `aa357320` 2026-04-02 — feat(bulk-operations): uniform no-op pattern for integration lifecycle actions
- `5add5c17` 2026-04-04 — Migration ErrorResponseDto → ProblemDetail
- `8c8290c5` 2026-04-06 — Error message i18n strategy
- `9e5c0a44` 2026-04-22 — feat(audit-logs): **simplify gap declaration UX and enhance operator workflow** *(exemple concret de raffinement UX d'un workflow opérateur)*

---

## 14. Liens inter-entités (langage visuel)

- `0debd702` / `29005e24` 2026-03-15 — `useExpandableRelatedDetails` *(le mécanisme générique de cross-link inline)*
- `b48c517b` 2026-04-11 — Audit-logs contextual navigation for related audits
- `a929a30d` 2026-04-12 — Endpoint to retrieve audit log context around an anchor event
- `6d635574` 2026-04-14 — Clickable dashboard badge drill-downs **with audit trail links**

---

## 15. Sécurité du UI — eat your own dog food, token → cookie HttpOnly + CSRF

Évolution clé pour le récit (couche de fond sécurité → durcissement) :

- `7cd82557` 2026-03-14 — feat(token-storage): **bearer token hashing** for enhanced security *(côté backend, soutient le UI)*
- `4a849585` 2026-03-25 — feat(security): **add Admin UI token security plan documentation** *(point de bascule analytique)*
- `24494721` 2026-03-27 — feat(security): **document Admin UI token security implementation and validation**
- `bab4e47e` 2026-03-28 — feat(login): « remember username » avec pin toggle
- `9a2d6d16` 2026-03-28 — Align admin login expiry with auth attempt TTL
- `c6551001` 2026-03-28 — Error mapping for passwordless wait failures
- `e99ab35f` 2026-03-28 — feat(audit): **MFA audit logging for admin login flows**
- `64544fad` 2026-04-20 — chore: **update environment configuration for HttpOnly session cookie support**
- `0e56f7f0` 2026-04-21 — Add endpoint to issue initial recovery codes for administrators
- `312be331` 2026-04-21 — Update activation endpoint + OpenAPI spec for **session cookie support**
- `394643c8` 2026-04-24 — feat: **Enhance session management and security features**
- `31c4cb7a` 2026-04-28 — feat(security): **implement Admin UI session hardening with CSRF protection** *(le « truc CSRF / nonce » du verbatim — terme exact côté code : `CSRF protection`)*
- `4d0a3b26` 2026-04-29 — feat(auth): **enhance session management with HttpOnly cookie support** *(localStorage → cookie, confirmé)*

> **Précision terminologique pour le draft** : le verbatim parle de « jeton NUNS » et de « replay / man-in-the-middle ». Les commits réels disent **CSRF protection** (cross-site request forgery) et **HttpOnly session cookie**. Pas de mention de « nonce » au sens replay-attack dans l'admin-ui (le terme nonce existe ailleurs pour les protocoles crypto, mais ici la sécurité UI tient à : cookie HttpOnly + token CSRF + SameSite vraisemblablement). **À valider précisément lors de la Phase 3** quand on rédigera le passage sécurité.

---

## 16. Onboarding / activation / récupération admin (chantier connexe au UI)

- `34f90518` 2026-04-21 — feat: **admin activation flow and onboarding process**
- `1c89b633` 2026-04-21 — Admin operational checks
- `b730e109` 2026-05-04 — feat: **activation code reissue for pending administrators**
- `a7ce633c` 2026-03-29 — feat(login-recovery): enrollment ID copy functionality
- `aac86ffe` 2026-03-29 — feat(plans): **admin UI recovery codes plan**
- `8c41183f` 2026-03-29 — feat(docs): **Admin UI recovery funnel documentation**
- `2507b064` 2026-03-29 — Structured admin recovery audit trail + enrollment reset logging
- `f773576f` 2026-04-03 — Recovery code regeneration and lifecycle analysis
- `2977d527` 2026-04-13 — docs: archive **Admin UI 401 redirect plan**
- `9940e67a` 2026-04-10 — Recovery-first bootstrap mode

> **🆕 Sujet non couvert par le verbatim** : tout le chantier d'activation / récupération / 401 redirect est important pour l'admin-ui (premier login, fallback, recovery codes). Le verbatim ne l'évoque pas. **À discuter en Phase 2** : faut-il y consacrer un paragraphe ou laisser pour un article companion ?

---

## 17. Tests & qualité du UI

- `404f127d` 2026-04-03 — feat(tests): **introduce Playwright browser testing suite for Admin UI**
- `31b9e22b` 2026-04-13 — feat(tests): add unit tests for API client functionality
- `6ac22624` 2026-05-01 — feat: **implement Admin UI lint cleanup plan** and refactor context usage
- `9417b8f2` 2026-03-10 — feat(docker): enhance Docker setup for Admin UI with test and production builds
- `a84844a8` 2026-03-08 — Dockerfile includes OpenAPI spec + Orval generation

> **🆕 Sujet non couvert** : l'introduction de Playwright (avril 2026) est un signal de maturation qualité. Probablement à mentionner brièvement en Phase 3 dans la section « couches de finition ».

---

## 18. Itérations UX raffinées tardives (post-stabilisation)

- `2b0164ca` 2026-05-02 — Refine PendingAuthScreen UX (reduce redundancy, improve title hierarchy)
- `1a286e4d` 2026-05-02 — Enhance enrollment detail + pending auth workflows with inline feedback and improved navigation
- `9771532c` 2026-05-02 — **Recent authentication result summary** in EnrollmentDetail and PendingAuth
- `42da0bbe` 2026-05-02 — Enrollment last activity tracking + verification timestamps
- `bd29c210` 2026-05-03 — feat: **enhance accessibility and user experience in authentication flows**
- `aaa0c5e2` 2026-05-03 — Admin management with tenant context + performance
- `1ee041f3` 2026-04-17 — CORS configuration for Admin API
- `d76a1748` 2026-04-18 — Quiet 404 handling for missing routes

---

## 19. Allers-retours assumés (essais reverted, plans archivés)

Matériel pour la section « V. Les retours en arrière assumés » du draft :

- `4ec8a0a7` 2026-04-29 — feat: Add EnrollmentPolicyBadge component and integrate
- `7ee6e46e` 2026-04-30 — **Revert** "feat: Add EnrollmentPolicyBadge component…"
- `8f088641` 2026-04-29 — Update OpenAPI specification for Ezkey Auth API
- `30118392` 2026-04-30 — **Revert** "Update OpenAPI specification…"
- `be4e1a86` 2026-04-29 — feat: **remove Diagnostics screen and related navigation** *(retrait assumé d'un écran)*
- `45a76f7a` 2026-03-27 — Pagination uniformity plan archivé (matérialisation du revirement « ne pas paginer → paginer partout »)
- `356693bf` 2026-03-06 — Removal of obsolete tenant-ui plans *(bascule conceptuelle tenant-ui → admin-ui)*

---

## Lacunes et 🆕 à porter en Phase 2 (récapitulatif)

1. **🆕 Stabilité visuelle des lignes en navigation G/D** — pas de commit nommé explicitement. À chercher dans les diffs autour de `08a8e669` ou dans des raffinements CSS/layout non isolés.
2. **🆕 FK numériques → libellés lisibles** — diffusé sur plusieurs commits (mars → mai), jamais isolé comme chantier. Le verbatim suggère un revirement plus explicite à confronter.
3. **🆕 Recovery codes / 401 redirect / activation** — gros chantier (mars-mai) totalement absent du verbatim. À arbitrer : paragraphe dédié, simple mention, ou exclusion.
4. **🆕 Playwright (avril 2026)** — saut de maturité qualité, à mentionner en Phase 3 (couches de finition) ou laisser hors scope.
5. **🆕 Terminologie sécurité** — le verbatim dit « NUNS / replay / man-in-the-middle ». Les commits disent **CSRF protection + HttpOnly cookie**. À aligner précisément en Phase 3.
6. **🆕 « Garage du coin » et InterCube** — exemples concrets de presets démo. Bons matériaux pour illustrer le mode démo si l'on veut un exemple nommé.
7. **🆕 Suppression de l'écran Diagnostics** (`be4e1a86`, avr. 2026) — exemple de retrait assumé à éventuellement citer dans la section « retours en arrière ».
8. **🆕 i18n des erreurs paramétrées** — confirmé incomplet (8c8290c5 pose la stratégie, mais déploiement par vagues). Cadrage du verbatim correct.

---

## Annexe — Trois exemples concrets candidats pour le draft (Phase 2)

Le plan demande d'identifier 2–3 exemples concrets pour illustrer le propos. Candidats issus des logs :

1. **Migration du Dashboard d'agrégation multi-requêtes → endpoint dédié** :
   - Plan : `8df433e1` 2026-03-08 (Dashboard API evolution plan)
   - Matérialisation : `267fa207` 2026-03-26 (auth health widget), `4e68ab16` 2026-04-14 (enrollment widget)
   - Drill-down : `6d635574` 2026-04-14 (pastilles cliquables)

2. **Bascule localStorage → cookie HttpOnly + CSRF** :
   - Audit : `4a849585` 2026-03-25 + `24494721` 2026-03-27
   - Cookie : `64544fad` 2026-04-20 + `4d0a3b26` 2026-04-29
   - CSRF : `31c4cb7a` 2026-04-28

3. **Pagination uniformisée via hooks Orval** :
   - Bascule Orval : `97669603` 2026-03-06
   - Hooks pagination : `a9df028f` 2026-03-08 + `12c98faa` 2026-03-08
   - Généralisation : `2f897729` / `bb4fa03a` / `1cd86699` / `405b8f41` 2026-03-10
   - Plan d'uniformité archivé : `45a76f7a` 2026-03-27
