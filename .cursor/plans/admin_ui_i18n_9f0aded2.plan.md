---
name: Admin UI i18n
overview: "Introduction de l’internationalisation (i18n) dans ezkey-admin-ui avec react-i18next : recommandations pour la stack Vite/React, choix d’approche (pilote puis généralisation), et cible français + anglais avec une structure évolutive."
todos: []
isProject: false
---

# Internationalisation (i18n) — ezkey-admin-ui

## Contexte

- **Stack actuelle** : Vite 7, React 19, TypeScript, React Router v7, Tailwind v4 ([AGENTS.md](ezkey-admin-ui/AGENTS.md)).
- **Aucune i18n en place** : tout le texte est en dur (sidebar, pages, formulaires, messages de validation Zod, erreurs API).
- **Règle actuelle** : « All content in English » dans AGENTS.md — à adapter une fois l’i18n en place.

---

## Recommandations pour la stack Vite + React

### Bibliothèque recommandée : **react-i18next** + **i18next**

- Standard de fait pour React : API `useTranslation()` / `t()`, support des namespaces, chargement lazy, pluralisation, interpolation.
- Compatible Vite sans plugin spécifique : les JSON de traduction peuvent être importés en dur ou chargés via `i18next-http-backend` (optionnel).
- Alternative plus récente (Intlayer) possible, mais moins de recul et d’exemples ; rester sur react-i18next est le choix le plus sûr et documenté.

### Bonnes pratiques à suivre

- **Ne pas tout charger au démarrage** : utiliser des namespaces (par ex. `common`, `login`, `dashboard`, `errors`) et charger les namespaces par page ou par zone si l’app grossit.
- **Fichiers de traduction** : JSON par langue et par namespace, ex. `src/locales/en/common.json`, `src/locales/fr/login.json`.
- `**escapeValue: false`** dans i18next car React échappe déjà le HTML.
- **Clés sémantiques** : préférer `login.submit` ou `common.buttons.save` plutôt que des clés génériques.
- **Validation (Zod)** : messages d’erreur via clés i18n (ex. `z.string().min(3, t('validation.minLength', { n: 3 }))`) en passant `t` dans le schéma ou en utilisant un schéma construit après init i18n.
- **Erreurs API** : continuer à afficher `detail` / `title` du backend ; pour les fallbacks côté UI (« Operation failed », etc.), utiliser `t('errors.operationFailed')`.

### Ce qui est attendu côté produit

- Au moins **français** et **anglais**.
- Langue par défaut : à définir (souvent anglais par défaut, français selon préférence navigateur ou choix explicite).
- Sélecteur de langue : dans le header (à côté du logout) ou dans un menu utilisateur, persistance en `localStorage` (ou session) pour la préférence.

---

## Approches possibles


| Approche                       | Description                                                         | Avantages                                                                                            | Inconvénients                                                                         |
| ------------------------------ | ------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------- |
| **One-shot**                   | Mise en place i18n + remplacement de toutes les chaînes en une fois | Tout cohérent, une seule PR                                                                          | PR volumineuse, risque de régressions, revue lourde                                   |
| **Incrémentale (recommandée)** | Infra + une « plaque pilote » puis page par page (ou zone par zone) | Revues plus petites, validation du pattern, possibilité de livrer FR/EN sur une partie tout de suite | Nécessite une discipline pour ne pas oublier de passer les nouvelles chaînes en `t()` |


Recommandation : **incrémentale**.
Étapes typiques :

1. **Phase 1** : Installer react-i18next / i18next, configurer i18n (fallback EN), créer la structure `src/locales/{en,fr}/*.json`, brancher un sélecteur de langue dans le header, et **internationaliser une seule zone** (ex. page Login + not-found, ou Sidebar + Header) comme démonstration.
2. **Phase 2** : Étendre aux autres pages et composants (dashboard, listes, formulaires, messages Zod, toasts/erreurs) par lots (ex. layout → dashboard → tenants → integrations → …).
3. **Phase 3** (optionnel) : Lazy-load des namespaces si le nombre de clés le justifie.

---

## Ordre de grandeur et difficulté

- **Setup initial** (config i18n, structure des dossiers, sélecteur de langue) : **faible** (quelques heures).
- **Extraction des chaînes** : **moyenne**. Environ 15+ pages, layout (sidebar, header, app-shell), composants partagés (boutons, labels, alertes), messages Zod et fallbacks d’erreur. Estimation grossière : **~150–300 clés** selon le niveau de découpage des textes.
- **Ajouter une nouvelle langue plus tard** : **faible** — ajout de fichiers `src/locales/{code}/...` et traduction, sans (ou peu de) changement de code si les clés restent stables.

Difficulté globale : **modérée**, surtout à cause du volume et de la régularité (remplacer chaque chaîne par `t('key')` et maintenir les JSON).

---

## Nombre de langues et évolutivité

- **Cible immédiate** : **2 langues (français, anglais)** suffisant pour votre besoin.
- **Structure évolutive** : oui. Ajouter une langue = ajouter un dossier `src/locales/de/` (ou autre code) avec les mêmes fichiers JSON que `en`/`fr`. Aucun changement de code si les clés sont identiques. Difficulté d’ajout : **faible**.
- Pas besoin de « nombre fixe » de langues : l’architecture i18next gère un ensemble de locales ouvert.

---

## Un seul langage d’abord ?

- **Option A — Deux langues dès le début** : infra + EN + FR en même temps. Avantage : pas de moitié d’UI traduite ; inconvénient : il faut maintenir deux jeux de fichiers dès la phase pilote.
- **Option B — Une langue d’abord (ex. anglais)** : infra + toutes les clés en anglais uniquement (fichiers `en/*.json` remplis), pas de fichier `fr` (ou `fr` vide / fallback sur `en`). Ensuite, ajout du français en remplissant `fr/*.json`. Avantage : moins de travail initial ; inconvénient : pas de démo bilingue tout de suite.
- **Recommandation** : **Option A (FR + EN dès le début)** pour la plaque pilote. Pour la pilote (ex. login + not-found + sidebar), le volume est limité ; avoir EN et FR tout de suite valide le flux (sélecteur, chargement des locales) et évite d’oublier le français ensuite. Pour le reste des pages, vous pouvez remplir d’abord les clés en anglais puis ajouter le français par fichier si vous préférez étaler la traduction.

---

## Coût « maintenant » vs « plus tard »

- **Faire plus tard** : chaque nouvel écran ou composant risque d’ajouter encore du texte en dur ; il faudra un jour refaire un passage global sur tout le code pour introduire `t()`. Coût : une grosse vague de refactor + risque d’oublis.
- **Faire maintenant (ou bientôt)** : une fois l’infra et le pattern en place, chaque nouvelle fonctionnalité peut directement utiliser `t()`. Le « coût » restant est surtout la première extraction (phase 2) et la rédaction des traductions FR.
- **Recommandation** : introduire **dès maintenant** l’infra et la plaque pilote (phase 1), puis planifier un **passage dédié** (phase 2) pour le reste de l’UI, plutôt que d’attendre que l’app grandisse encore.

---

## Plan de travail proposé (ordonné, sans tout casser)

### Phase 1 — Infra et plaque pilote (priorité haute)

1. **Dépendances** : `react-i18next`, `i18next` ; optionnel : `i18next-browser-languagedetector` pour détecter la langue du navigateur.
2. **Configuration** : fichier `src/i18n.ts` (ou `i18n.tsx`) qui initialise i18next (langue par défaut, fallback `en`, `escapeValue: false`), avec `initReactI18next`.
3. **Structure des locales** :
  `src/locales/en/common.json`, `src/locales/fr/common.json` (pour boutons, labels génériques, layout).
   Pour la pilote : par ex. `src/locales/en/login.json`, `src/locales/fr/login.json`, et éventuellement `layout.json` pour sidebar/header.
4. **Bootstrap** : importer `./i18n` dans [main.tsx](ezkey-admin-ui/src/main.tsx) avant le rendu de l’app.
5. **Sélecteur de langue** : dans [header.tsx](ezkey-admin-ui/src/components/layout/header.tsx) (dropdown ou boutons FR | EN), avec persistance de la préférence (ex. `localStorage` + `i18n.changeLanguage()`).
6. **Pilote** : internationaliser **un sous-ensemble cohérent** :
  - [sidebar.tsx](ezkey-admin-ui/src/components/layout/sidebar.tsx) (labels de nav, « Global Admin », « Tenant Admin », « EZKey » si besoin),
  - [header.tsx](ezkey-admin-ui/src/components/layout/header.tsx) (« Signed in as », « Logout »),
  - [login.tsx](ezkey-admin-ui/src/pages/login.tsx) (titres, champs, boutons, états « waiting », « expired », erreurs),
  - [not-found.tsx](ezkey-admin-ui/src/pages/not-found.tsx) (titre, message, bouton).
7. **Règle AGENTS.md** : remplacer « All content in English » par une phrase du type « All user-facing text must use i18n keys (e.g. `t('key')` from `useTranslation()`); supported locales: en, fr. »

Livrable phase 1 : l’app tourne comme avant, avec une zone (login + not-found + layout) disponible en EN et FR, et un sélecteur fonctionnel.

### Phase 2 — Généralisation (par lots)

1. **Common** : compléter `common.json` (Cancel, Save, Delete, Confirm, Loading, etc.) et les réutiliser dans les composants UI partagés si nécessaire.
2. **Pages** : une par une (dashboard, tenants, integrations, enrollments, auth-attempts, audit-logs, admins, api-keys, encryption-keys) :
  - Créer ou étendre les namespaces (ex. `dashboard.json`, `tenants.json`, …),
  - Remplacer les chaînes en dur par `t('...')`,
  - Messages Zod : soit schémas qui acceptent `t` en paramètre, soit fichier `validation.json` et `t('validation.minLength', { n: 3 })`.
3. **Erreurs et toasts** : centraliser les messages de fallback (ex. `getApiErrorMessage(..., t('errors.operationFailed'))`) et les libellés de toasts dans un namespace `errors` ou `common`.
4. **Tests manuels** : vérifier FR/EN sur chaque écran, et que le changement de langue met à jour toute la page (déjà le cas avec react-i18next).

### Phase 3 (optionnel) — Optimisation

1. Si le nombre de clés devient important : charger certains namespaces en lazy (par route ou par section) pour réduire le bundle initial.
2. Typage TypeScript des clés : possibilité d’utiliser `i18next-typescript` ou des types générés pour éviter les typos dans les clés (optionnel, à évaluer selon la taille de l’équipe).

---

## Résumé des choix recommandés


| Sujet                        | Recommandation                                                                                                       |
| ---------------------------- | -------------------------------------------------------------------------------------------------------------------- |
| Bibliothèque                 | react-i18next + i18next                                                                                              |
| Approche                     | Incrémentale : infra + pilote (layout + login + not-found), puis généralisation par pages                            |
| Langues                      | Français + anglais dès la pilote ; structure prête pour d’autres langues plus tard                                   |
| Difficulté                   | Setup : faible ; extraction complète : moyenne (~150–300 clés)                                                       |
| Un seul langage au début ?   | Non : viser FR + EN dès la pilote pour valider le flux ; le reste des pages peut être rempli en EN puis FR si besoin |
| Coût maintenant vs plus tard | Faire l’infra + pilote maintenant, puis un passage dédié pour le reste, pour éviter d’accumuler du texte en dur      |


En suivant ce plan, vous introduisez l’i18n de façon ordonnée, sans tout casser, avec une première démo bilingue sur la connexion et le layout, puis une généralisation progressive au reste de l’admin UI.

---

## État après mise en œuvre (observations pour action future)

*Dernière revue : après complétion des lots (layout, login, common, dashboard, tenants, integrations, enrollments, auth-attempts, audit-logs, api-keys, encryption-keys, admins).*

### Ce qui est couvert

- **Pages** : toutes utilisent `useTranslation` et des clés (Dashboard, Login, Tenants, Integrations, Enrollments, Auth Attempts, Audit Logs, API Keys, Encryption Keys, Admins, NotFound).
- **Pages détail** : Tenant, Integration, Enrollment, API Key — namespaces dédiés pour titres, formulaires, boutons, messages.
- **Layout** : Sidebar et Header (nav, « Signed in as », sélecteur EN/FR) via `layout` et `common`.
- **Composants partagés** : Pagination (`common.pagination`), DateRangeFilter (`common.dateRange`), badges (enrollment-status, auth-attempt-status) utilisent les locales.
- **Namespaces** : 12 namespaces en/fr enregistrés dans `i18n.ts`.

### Angles morts restants (à traiter si besoin)

1. **Contenu des bulles d’aide (ContextHelp)**  
   - **Encryption Keys** : le contenu du popover (explications PRIMARY / ENABLED) vient de `ENCRYPTION_KEYS_SECTION_HELP.content` dans `help-text.tsx` (JSX en anglais). Le titre est déjà en i18n.  
   - **Audit Logs** : les 4 popovers (Integrity & Lifecycle, Seal Archive, Declare Gap, Checkpoint timeline) utilisent `AUDIT_CONTEXT_HELP` dans `help-text.tsx` — titres et contenu en JSX anglais. Un `ariaLabel` utilise déjà `t()`, les trois autres sont en dur.  
   - **Action possible** : ajouter des clés dédiées (ex. `audit-logs.help.*`, `encryption-keys.help.section`) et utiliser `Trans` (react-i18next) pour le contenu riche, ou accepter l’anglais pour ces aides.

2. **Optionnel**  
   - **NotFound** : les boutons EN/FR ont `aria-label="English"` et `aria-label="Français"` en dur. Pour l’a11y : ajouter par ex. `common.languageEn` / `common.languageFr` si souhaité.  
   - **Mode démo** : libellés des presets (ex. « Garage du coin », « ACME Corp ») dans `demo-mode.ts` ; visibles uniquement en dev. Traduire seulement si on souhaite une démo bilingue en dev.

### Conclusion

- Le dossier i18n peut être **considéré clos** pour tout le contenu des pages, listes, formulaires, boutons, messages et layout.
- **Évolution** : toute nouvelle page ou nouveau texte visible doit suivre la règle dans [AGENTS.md](ezkey-admin-ui/AGENTS.md) : `useTranslation('namespace')` + clés dans `src/locales/en/*.json` et `src/locales/fr/*.json`.
