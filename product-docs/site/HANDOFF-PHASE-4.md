# HANDOFF — Phase 4 (Cloudflare Pages publication)

> **Ce document est un prompt prêt-à-coller.** Ouvre une nouvelle session, copie tout ce qui suit la ligne `--- PROMPT ---`, et l'agent disposera de tout le contexte pour entreprendre la Phase 4 sans intervention verbale supplémentaire.
>
> Aucun autre markdown n'a été créé pendant les Phases 1–3 (par décision explicite). Ce fichier est l'unique livrable documentaire des Phases 1–3.

---

## PROMPT

Tu interviens dans le repository `ezkey-worktree2` sur le micro-site local situé dans `product-docs/site/`. Ta mission : **publier ce site comme vitrine méthodologique publique sur Cloudflare Pages**, alignée visuellement et éditorialement avec le site marketing `sites/ezkey-org/`. Les Phases 1–3 ont livré un explorateur local fonctionnel. La Phase 4 doit produire un **build statique idempotent** et un déploiement Pages, sans casser l'usage local.

### 1. État final atteint à la fin de la Phase 3

**Stack** : Node 24 + ES modules ; Express 4.21 comme couche de transport mince ; rendu Markdown via `marked` + `marked-gfm-heading-id` + `marked-highlight` + `highlight.js` ; frontmatter `gray-matter` ; recherche client `MiniSearch` (CDN ESM) ; diagrammes Mermaid v11 (CDN ESM, lazy).

**Arborescence** :
```
product-docs/site/
├── server.js                   # serveur + fonctions pures (export!)
├── phases.json                 # 12 phases du workflow + mapping fichier→phase
├── tracks.json                 # 3 wizard tracks (discover/apply/present)
├── package.json                # express, marked, marked-gfm-heading-id,
│                               # marked-highlight, highlight.js, gray-matter
├── README.md                   # `npm install && npm start` → :4321
├── .gitignore                  # node_modules/
└── public/
    ├── index.html              # coquille 3-volets, topbar (search/map/theme)
    ├── styles.css              # palette alignée methodology/view, dark mode
    ├── app.js                  # router hash, fetch tree/doc, intégration P2/P3
    ├── wizard.js               # state machine tracks + footer persistant
    ├── mermaid-loader.js       # lazy-load mermaid CDN ESM
    ├── glossary.js             # décoration regex + tooltips flottants
    ├── search.js               # Ctrl+K palette (MiniSearch CDN ESM)
    ├── presentation.js         # F = fullscreen + ←/→ navigation track
    └── map.js                  # SVG cognitif #/map
```

**Endpoints serveur actuels** (à reproduire en JSON statique au build) :
- `GET /api/tree` → arbre du corpus.
- `GET /api/doc?path=...` → `{ path, title, frontmatter, html, toc, phase, mtime, backlinks }`.
- `GET /api/phases` → contenu de `phases.json`.
- `GET /api/tracks` → contenu de `tracks.json`.
- `GET /api/glossary` → glossaire hand-curated (cf. `buildGlossary()`).
- `GET /api/search-index` → `{ docs: [{ path, title, headings, text }] }`.

**Fonctions pures exportées de `server.js` (à réutiliser tel quel par `build.js`)** :
- `buildTree(corpus?)` — arbre de navigation avec `mtime` sur les fichiers.
- `resolveCorpusPath(urlPath, corpus?)` — résout vers un absolu avec garde anti-traversal.
- `toCorpusUrlPath(absPath, corpus?)` — chemin URL canonique.
- `renderDoc(absPath)` — render complet : html, toc, phase, mtime, backlinks.
- `extractToc(html)` — H2/H3 → liste d'ancres.
- `phaseForPath(urlPath)` — id de phase.
- `CORPUS` — déclaration des racines indexées (methodology/, templates/, glossary.md).

**Routes client** :
- `#/` — accueil (3 cartes personae, raccourcis).
- `#/<path>?h=<anchor>&track=<id>&step=<idx>` — document avec ancre + permalien de step.
- `#/map` — carte cognitive SVG.

**Features livrées en Phase 3** : Ctrl+K recherche (MiniSearch fuzzy + boost titres/headings), mode présentation `F` (fullscreen + ←/→ pour avancer dans la track active), carte cognitive `/#/map`, permaliens d'étape avec bouton "Copy link" dans le footer wizard, toggle clair/sombre persistant, panel "Documents linking here" (backlinks calculés au boot), badge "updated Xd ago" dans le breadcrumb.

**Corpus indexé** : `product-docs/methodology/` (incl. `decisions/`) + `product-docs/templates/` + `product-docs/glossary.md`. ~41 documents, ~41 cibles de backlinks au moment du handoff.

### 2. Intention de déploiement public — positionnement verrouillé

Publier le micro-site comme **outil de référence méthodologique** sur un **sous-domaine séparé** : **`methodology.ezkey.org`**.

**Raison du sous-domaine plutôt qu'un sous-chemin sur l'apex marketing** :

- L'apex (`ezkey.org`) reste un site marketing/éditorial : témoignages, essais, et le **rich view méthodologique FR/EN** (slice narrative, déjà publié). Intent : convaincre, raconter.
- Le **method explorer** (objet de cette Phase 4) est un **outil de travail** : navigation 3-volets, Ctrl+K, mode présentation, permaliens d'étape, carte cognitive, backlinks. Intent : référence consultable à répétition.
- Forme suit fonction : l'UX outillée ne se mélange pas proprement avec l'UX narrative. Cadence aussi découplée (corpus méthodologique évolue plus souvent que le marketing).
- Comparables : `docs.stripe.com`, `docs.github.com`, `handbook.gitlab.com` — pattern "navigation outillée = sous-domaine". À l'inverse, le rich view marketing joue le rôle du "livre Shape Up" sur `basecamp.com/shapeup/` (narration sur apex).

**Pont éditorial** : le rich view marketing (sur apex, FR/EN) garde son rôle de vitrine et **renvoie vers `methodology.ezkey.org`** via un CTA sobre en fin de page ("Browse the full methodology explorer →"). L'explorateur renvoie vers l'apex via un wordmark discret "ezkey · methodology" en topbar + un lien footer minimal.

**Cible primaire** : l'auteur, ses collaborateurs proches, et les pairs qui évaluent la méthode en profondeur. Cible secondaire : développeurs/opérateurs qui veulent appliquer la méthode à leurs propres projets.

### 3. Contraintes verrouillées

- **Cible plateforme** : **Cloudflare Pages**, projet dédié **`methodology-ezkey-org`** (parallèle au projet `ezkey-org` du marketing).
- **Sous-domaine** : `methodology.ezkey.org`, attaché au projet Pages via un CNAME dans le DNS Cloudflare.
- **Source-of-truth** : le corpus reste dans `product-docs/methodology/`, `product-docs/templates/`, `product-docs/glossary.md`. La Phase 4 ne déplace, ne renomme et ne réécrit **aucun** fichier du corpus.
- **Cohérence visuelle** : palette/typographie reprises de `sites/ezkey-org/` et de `product-docs/methodology/view/index.html` ; **chrome distinct** (l'explorer garde son layout 3-volets et sa topbar à icônes ; ne pas importer le header marketing).
- **UI** : anglais uniquement (verrouillé en Phase 1, confirmé pour P4). Si FR plus tard : `/en/` + `/fr/` sous le même sous-domaine, pas deux sous-domaines.
- **Aucune dépendance Java/Maven n'est concernée**. Aucun changement aux artefacts backend.

### 4. Voie technique recommandée — pré-build statique

Créer `product-docs/site/build.js` qui :

1. Importe les fonctions pures de `server.js` (`buildTree`, `renderDoc`, `CORPUS`, `phaseForPath`, `toCorpusUrlPath`).
2. Reproduit la logique d'indexation actuelle (BACKLINKS + SEARCH_INDEX — la **extraire** de `server.js` dans un module partagé `indices.js` avant la Phase 4 pour éviter la duplication).
3. Itère sur tous les fichiers du corpus, appelle `renderDoc(abs)`, et écrit :
   - `dist/<path>/index.html` — page complète (shell HTML identique à `public/index.html`, doc rendu côté serveur inline).
   - `dist/api/tree.json`, `dist/api/phases.json`, `dist/api/tracks.json`, `dist/api/glossary.json`, `dist/api/search-index.json` — snapshots statiques.
   - `dist/api/doc/<path>.json` — un fichier JSON par doc (pour navigation client cohérente avec le mode local).
   - `dist/index.html` — accueil.
   - `dist/map/index.html` — carte.
   - Copie de `public/styles.css`, `public/*.js` (sauf `server.js`).
4. Réécrit les `fetch('/api/...')` du client en chemins statiques `*.json` (option A : un petit wrapper `apiClient.js` qui choisit entre `/api/...` en local et `/api/.../*.json` en build). Option B : laisser `fetch('/api/tree')` et configurer Cloudflare Pages pour servir `dist/api/tree.json` via une règle de redirection / `_headers`. **Option A est plus propre et 100% statique**.
5. URLs propres : envisager `dist/methodology/workflow-overview/index.html` au lieu de `dist/methodology/workflow-overview.md/index.html`. Si tu rebases les URLs, **mets à jour le router client** pour accepter les deux formats (rétrocompat local) ou exposer un mode `BUILD=static`.

### 5. Setup Cloudflare — intervention manuelle one-off (préalable au premier deploy)

Avant que les scripts de déploiement fonctionnent, **une intervention manuelle one-off** est nécessaire côté Cloudflare et DNS. Le token API et l'account ID sont déjà configurés dans le `.env` racine du repo (mêmes variables que pour `deploy-ezkey-org-preview.sh` : `CLOUDFLARE_API_TOKEN`, `CLOUDFLARE_ACCOUNT_ID`). À faire :

1. **Créer le projet Cloudflare Pages** `methodology-ezkey-org` (dashboard Cloudflare → Workers & Pages → Create application → Pages → Direct Upload, ou première invocation de `wrangler pages project create methodology-ezkey-org --production-branch=main`).
2. **Créer le CNAME DNS** `methodology` dans la zone `ezkey.org` pointant vers le `*.pages.dev` du projet (instructions affichées par Cloudflare Pages après attachement du custom domain).
3. **Attacher le custom domain** `methodology.ezkey.org` au projet dans l'UI Pages. Cloudflare gère le certificat automatiquement.
4. Vérifier que `wrangler whoami` (avec le `.env` sourcé) reconnaît l'account ; tester un premier preview deploy (étape automatisée par le script ci-dessous).

Le `.env` racine contient déjà les variables nécessaires ; il est gitignored. Ne pas le commiter, ne pas l'imprimer dans les logs.

### 6. Scripts de déploiement — **dans le scope P4**

Mirroir exact du pattern marketing apex (`scripts/cloudflare/deploy-ezkey-org-preview.sh` + `deploy-ezkey-org-production.sh`). Créer :

- **`scripts/cloudflare/deploy-methodology-preview.sh`** — build le site (`node product-docs/site/build.js`) puis `wrangler pages deploy product-docs/site/dist --project-name=methodology-ezkey-org --branch=<preview-branch>`. Source le `.env` racine, valide la présence de `CLOUDFLARE_API_TOKEN` et `CLOUDFLARE_ACCOUNT_ID`, expose `PREVIEW_BRANCH` et `CLOUDFLARE_PAGES_PROJECT` comme overrides.
- **`scripts/cloudflare/deploy-methodology-production.sh`** — même chose mais `--branch=main` (production branch du projet Pages).
- Optionnel à inclure si simple : **`scripts/cloudflare/cleanup-methodology-previews.sh`** (équivalent du cleanup existant pour le marketing).

Bash de préférence (cohérent avec l'existant), même style de header (usage en commentaire, `set -euo pipefail`, source du `.env` racine).

### 7. Décisions à arbitrer (verrouille-les en ouverture de session)

- **Branding** : wordmark sobre "ezkey · methodology" en topbar (lien vers apex) + lien footer "← ezkey.org". Confirmer avant d'implémenter.
- **Mermaid** : rendu client actuel suffit-il, ou SSR via `@mermaid-js/mermaid-cli` (build plus lourd, mais SEO-friendly et JS-disabled-friendly) ? **Recommandation initiale : garder rendu client** (le site reste vivant et léger).
- **Analytics** : opt-in Cloudflare Web Analytics ? Par défaut : oui, désactivable.
- **Mode sombre** : conserver le toggle (déjà livré). À garder.
- **i18n FR/EN** : hors scope P4. Tout reste EN. Si FR ajouté plus tard : `/en/`+`/fr/` sous le même sous-domaine.
- **Visibilité des décisions et retrofits** : `methodology/decisions/` et futurs `R-*` retrofits sont indexés et navigables. Assumer cette transparence publiquement (cohérent avec posture open d'ezkey) — à confirmer.

### 8. Auto-déploiement — **hors scope P4, à décider plus tard**

Un workflow GitHub Actions qui déclenche `deploy-methodology-production.sh` sur push `main` modifiant `product-docs/methodology/**`, `product-docs/templates/**`, `product-docs/glossary.md`, ou `product-docs/site/**` est **techniquement trivial** une fois les scripts livrés. Mais ce n'est **pas l'objectif primaire de P4**.

L'auteur préfère **ne pas se commettre trop vite** à une discipline de publication. Deux pistes restent ouvertes :

1. Mise à jour ponctuelle, manuelle, à la demande (invocation directe du script de prod).
2. Incorporation dans un skill de "processus de révision méthodologique" (publication = étape explicite d'une revue, pas un side-effect automatique d'un commit).

P4 livre l'**outillage** (scripts + build idempotent) qui rend les deux options possibles. La décision sur le mécanisme se prend après P4.

### 9. Adaptations à prévoir (liste exhaustive)

- Extraire `BACKLINKS` / `SEARCH_INDEX` / `buildGlossary` de `server.js` dans `indices.js` partagé (refacto neutre côté local, prépare le build).
- Wrapper client `apiClient.js` qui résout `/api/...` vers JSON statiques en mode build.
- Réécriture des liens internes : actuellement, `rewriteLink()` cible `#/<urlPath>` — convertir en URLs propres (`/methodology/workflow-overview/`) avec ancres natives `#section-id`. **Garder le hash-routing en parallèle pour le local**.
- Pré-rendre le HTML du doc (pas seulement le shell) pour SEO + chargement sans JS, et hydrater côté client pour le router/glossaire/Mermaid.
- Sitemap.xml + robots.txt + meta tags OpenGraph par doc (titre + extrait).
- Gérer le fichier unique `glossary.md` mappé sur l'URL `/glossary` (cas spécial : `CORPUS` déclare `{ kind: 'file' }`, donc `toCorpusUrlPath` retourne juste `glossary` — la route statique doit être `dist/glossary/index.html`).

### 10. Critères de succès

- `node build.js` est **idempotent** et reproductible (output identique pour input identique).
- Parité fonctionnelle navigateur : arbre, doc, TOC, scroll-spy, breadcrumb, ribbon de phase, wizard tracks, glossaire, Mermaid, recherche Ctrl+K, mode présentation F, carte `/map`, dark mode.
- Permaliens d'étape `?track=&step=` restent fonctionnels.
- Lighthouse ≥ 90 sur Performance/Accessibility/Best Practices/SEO (objectif souhaitable, non bloquant).
- Scripts `deploy-methodology-preview.sh` et `deploy-methodology-production.sh` livrés et exécutables, alignés sur le pattern marketing.
- Preview deploy réussi sur `*.pages.dev`, puis production sur `methodology.ezkey.org`.
- Pont éditorial : rich view sur apex pointe vers `methodology.ezkey.org` ; explorer pointe vers apex via wordmark + footer.
- Le mode local (`npm start` dans `product-docs/site/`) continue à fonctionner sans régression.

### 11. Alternatives écartées (à confirmer ou rouvrir)

- **Cloudflare Pages + Functions (Hono port d'Express)** : écarté par défaut car aucun besoin de dynamique edge. À rouvrir uniquement si un cas (commentaires, sondages, télémétrie active) émerge.
- **SSG mainstream (Next.js, Astro, VitePress)** : écarté parce que (a) on a déjà un rendu fonctionnel, (b) le couplage minimal au stack actuel évite une refonte coûteuse, (c) le `build.js` artisanal réutilise 100% du code existant. À rouvrir si la maintenance devient pénible.

### 12. Pointeurs à lire **en ouverture de session**

- `product-docs/site/server.js` — sections "Pure: …" et "Phase 3: backlinks + search index".
- `product-docs/site/public/app.js` — router, `loadDoc`, `renderDoc`.
- `product-docs/site/phases.json`, `product-docs/site/tracks.json`.
- `product-docs/methodology/view/index.html` — palette de référence (rich view marketing actuel).
- `sites/ezkey-org/index.html` et `sites/ezkey-org/AGENTS.md` — branding marketing + posture Cloudflare Pages du site marketing.
- `scripts/cloudflare/deploy-ezkey-org-preview.sh` et `deploy-ezkey-org-production.sh` — **patron exact** à mirrorer pour les nouveaux scripts methodology.
- `.env` racine (gitignored) — `CLOUDFLARE_API_TOKEN`, `CLOUDFLARE_ACCOUNT_ID` déjà en place.
- `AGENTS.md` racine — guardrails repo (terminologie phase vs milestone, etc.).

### 13. Ordre d'exécution suggéré

1. **Refactor neutre** : extraire `BACKLINKS` / `SEARCH_INDEX` / `buildGlossary` de `server.js` dans `indices.js` ; tests : `npm start` doit fonctionner exactement comme avant.
2. **Wrapper API client** : introduire `apiClient.js` côté public ; convertir tous les `fetch('/api/...')` à l'utiliser ; local doit toujours fonctionner.
3. **Build statique** : écrire `build.js` ; produire `dist/` ; valider visuellement avec `npx http-server product-docs/site/dist`.
4. **Branding minimal** : wordmark "ezkey · methodology" topbar + footer, aligné palette avec `sites/ezkey-org/`.
5. **Intervention one-off Cloudflare** (l'auteur exécute) : créer projet `methodology-ezkey-org`, créer CNAME DNS, attacher custom domain.
6. **Scripts de déploiement** : livrer `deploy-methodology-preview.sh` et `deploy-methodology-production.sh` sous `scripts/cloudflare/`.
7. **Premier preview deploy** : exécuter le script preview, valider sur `*.pages.dev`.
8. **Production deploy** : exécuter le script production, valider sur `methodology.ezkey.org`.
9. **Pont éditorial** : CTA "Browse the full methodology explorer →" en fin du rich view marketing → `methodology.ezkey.org`.
10. **Auto-deploy** : *non livré en P4*. Décision reportée (manuel à la demande vs intégration au skill de revue méthodologique).

**Fin du prompt Phase 4. Bonne session.**
