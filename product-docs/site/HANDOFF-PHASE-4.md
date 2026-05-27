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

### 2. Intention de déploiement public

Publier le micro-site comme **vitrine méthodologique** sous le branding `ezkey.org`. Cible primaire : développeurs et opérateurs qui découvrent la méthodologie sans accès au repo. Objectif secondaire : alimenter un lien depuis le site marketing existant (`sites/ezkey-org/methodology.html`) vers la méthodologie navigable.

### 3. Contraintes verrouillées

- **Cible** : **Cloudflare Pages** (privilégié — preview branches, edge gratuit, intégration Wrangler propre).
- **Source-of-truth** : le corpus reste dans `product-docs/methodology/`, `product-docs/templates/`, `product-docs/glossary.md`. La Phase 4 ne déplace, ne renomme et ne réécrit **aucun** fichier du corpus.
- **Cohérence visuelle** : palette/typographie reprises de `sites/ezkey-org/` et de `product-docs/methodology/view/index.html`. Réutiliser le header/footer de marque d'ezkey-org si pertinent (à décider).
- **UI** : anglais uniquement (verrouillé en Phase 1).
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

### 5. Décisions à arbitrer (verrouille-les en ouverture de session)

- **URL publique** : sous-domaine (`methodology.ezkey.org`) vs sous-chemin (`ezkey.org/methodology/`) ? Recommandation par défaut : **sous-domaine**, plus simple côté Pages.
- **Branding** : header/footer ezkey.org repris tel quel ou allégé ? Le rail-gauche+rail-droit doit-il rester ou être condensé en menu ?
- **Mermaid** : rendu client actuel suffit-il, ou SSR via `@mermaid-js/mermaid-cli` (build plus lourd, mais SEO-friendly et JS-disabled-friendly) ? **Recommandation initiale : garder rendu client** (le site reste vivant et léger).
- **Analytics** : opt-in Cloudflare Web Analytics ? Par défaut : oui, désactivable.
- **Mode sombre** : conserver le toggle (déjà livré). À garder.
- **i18n FR/EN** : hors scope sauf décision contraire. Tout reste EN.
- **CI** : workflow GitHub Actions qui déclenche `wrangler pages deploy dist` sur push `main` modifiant `product-docs/methodology/**`, `product-docs/templates/**`, `product-docs/glossary.md`, ou `product-docs/site/**`.

### 6. Adaptations à prévoir (liste exhaustive)

- Extraire `BACKLINKS` / `SEARCH_INDEX` / `buildGlossary` de `server.js` dans `indices.js` partagé (refacto neutre côté local, prépare le build).
- Wrapper client `apiClient.js` qui résout `/api/...` vers JSON statiques en mode build.
- Réécriture des liens internes : actuellement, `rewriteLink()` cible `#/<urlPath>` — convertir en URLs propres (`/methodology/workflow-overview/`) avec ancres natives `#section-id`. **Garder le hash-routing en parallèle pour le local**.
- Pré-rendre le HTML du doc (pas seulement le shell) pour SEO + chargement sans JS, et hydrater côté client pour le router/glossaire/Mermaid.
- Sitemap.xml + robots.txt + meta tags OpenGraph par doc (titre + extrait).
- Gérer le fichier unique `glossary.md` mappé sur l'URL `/glossary` (cas spécial : `CORPUS` déclare `{ kind: 'file' }`, donc `toCorpusUrlPath` retourne juste `glossary` — la route statique doit être `dist/glossary/index.html`).

### 7. Critères de succès

- `node build.js` est **idempotent** et reproductible (output identique pour input identique).
- Parité fonctionnelle navigateur : arbre, doc, TOC, scroll-spy, breadcrumb, ribbon de phase, wizard tracks, glossaire, Mermaid, recherche Ctrl+K, mode présentation F, carte `/map`, dark mode.
- Permaliens d'étape `?track=&step=` restent fonctionnels.
- Lighthouse ≥ 90 sur Performance/Accessibility/Best Practices/SEO (objectif souhaitable, non bloquant).
- Déploiement Cloudflare Pages réussi sur preview branch puis sur production.
- Lien depuis `sites/ezkey-org/methodology.html` (ou ajout d'une carte/section) vers la nouvelle URL publique.
- Le mode local (`npm start` dans `product-docs/site/`) continue à fonctionner sans régression.

### 8. Alternatives écartées (à confirmer ou rouvrir)

- **Cloudflare Pages + Functions (Hono port d'Express)** : écarté par défaut car aucun besoin de dynamique edge. À rouvrir uniquement si un cas (commentaires, sondages, télémétrie active) émerge.
- **SSG mainstream (Next.js, Astro, VitePress)** : écarté parce que (a) on a déjà un rendu fonctionnel, (b) le couplage minimal au stack actuel évite une refonte coûteuse, (c) le `build.js` artisanal réutilise 100% du code existant. À rouvrir si la maintenance devient pénible.

### 9. Pointeurs à lire **en ouverture de session**

- `product-docs/site/server.js` — sections "Pure: …" et "Phase 3: backlinks + search index".
- `product-docs/site/public/app.js` — router, `loadDoc`, `renderDoc`.
- `product-docs/site/phases.json`, `product-docs/site/tracks.json`.
- `product-docs/methodology/view/index.html` — palette de référence.
- `sites/ezkey-org/index.html` et `sites/ezkey-org/AGENTS.md` — branding marketing + contraintes Cloudflare Pages du site marketing.
- `AGENTS.md` racine — guardrails repo (terminologie phase vs milestone, etc.).

### 10. Ordre d'exécution suggéré

1. Refactor neutre : extraire `BACKLINKS` / `SEARCH_INDEX` / `buildGlossary` dans `indices.js` ; tests : `npm start` doit fonctionner exactement comme avant.
2. Introduire `apiClient.js` côté public ; convertir tous les `fetch('/api/...')` à l'utiliser ; local doit toujours fonctionner.
3. Écrire `build.js` ; produire `dist/` ; valider visuellement avec `npx http-server dist`.
4. Aligner le branding sur `sites/ezkey-org/`.
5. Configurer Cloudflare Pages (Wrangler ou UI) ; déployer preview ; valider.
6. Ajouter le workflow GitHub Actions ; déployer production.
7. Lien depuis `sites/ezkey-org/`.

**Fin du prompt Phase 4. Bonne session.**
