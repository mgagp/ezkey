# Plan : Micro-site local pour le corpus méthodologique

## Décisions verrouillées
- **Corpus** : `product-docs/methodology/` + `product-docs/templates/` + `product-docs/glossary.md` (extensible plus tard).
- **Stack** : Node + npm. Express, `marked`, `gray-matter`, `highlight.js`. Mermaid côté client (CDN local vendored).
- **Tracks wizard** : 3 pistes — Discover / Apply / Present.
- **Itérations** : 3 phases (MVP, Pédagogique, Polish).
- **Emplacement** : `product-docs/site/` (sert plusieurs sous-dossiers, sibling de `methodology/`).
- **Palette** : reprise de `methodology/view/index.html` (`--blue #2563eb`, `--cyan #0891b2`, `--amber #d97706`, `--violet #7c3aed`, `--indigo #4f46e5`, `--teal #0d9488`, `--green #16a34a`, fond `#f8fafc`).

## Phases

### Phase 1 — MVP visuel (foundation)
But : valider visuellement la navigation 3-volets + rendu Markdown.
1. Scaffolding `product-docs/site/` : `package.json`, `server.js`, `public/`, `README.md` local.
2. Serveur Express : `/` (statique), `/api/tree` (arbre du corpus), `/api/doc?path=...` (rendu MD → HTML + TOC + frontmatter).
3. Indexation au démarrage : parcourt méthodologie + templates + glossary, construit l'arbre, mémorise mtimes pour rechargement.
4. Layout 3 volets dans `public/index.html` + `app.js` + `styles.css` :
   - Top bar : marque "Ezkey Méthode · Explorateur local" + 3 portes personae (boutons Discover/Apply/Present, désactivés P1).
   - Gauche : arbre repliable (dossiers/fichiers, état persisté via localStorage).
   - Centre : titre + breadcrumb + corps Markdown rendu (avec `highlight.js` pour code).
   - Droite : TOC du document courant (H2/H3 ancres cliquables, scroll-spy).
5. Routing client minimaliste (`#/path/to/file.md`), partageable.
6. Page d'accueil sobre : 3 cartes personae visibles mais inertes en P1, plus lien direct vers `methodology/README.md`.
7. Mermaid en P1 : laissé en bloc `code` (pas de rendu graphique encore — explicite via badge "diagramme — phase 2").

**Point de validation P1** : tu navigues, tu vois la palette, le rendu, l'ergonomie 3 volets. Feedback.

### Phase 2 — Couche pédagogique (le cœur de l'effort)
But : faire vivre la méthodologie via personae, phases et narration.
1. **Modèle de phases** : `product-docs/site/phases.json` mappant chaque fichier méthodologique à une phase (Vision, Capture/Triage, Challenge, Tracer Bullet, Design, Test, Quality/Closeout, Retrofit, AI Collaboration). Couleur par phase tirée de la palette.
2. **Ruban de phase** au-dessus du contenu : badge coloré + libellé + tooltip "étape du workflow".
3. **Wizard tracks** : `product-docs/site/tracks.json` déclarant les 3 pistes. Format par étape : `{ file, anchor?, narration, highlight? }`.
   - *Discover* : valeurs → workflow-overview → analysis-and-design-canon → tracer-bullet-method → quality-gates → ai-collaboration-model.
   - *Apply* : session-start-guide → workflow-overview (phases) → tracer-bullet → design canon → testing-strategy → quality-gates → closeout (via README quickstart).
   - *Present* : 6–8 étapes scénarisées avec talking points courts pour soutenir un discours live.
4. **Footer wizard persistant** quand track active : prev/next, breadcrumb des étapes (cliquable), narration courte, bouton "Quitter la piste".
5. **Rendu Mermaid** côté client (charge mermaid.min.js seulement si blocs `mermaid` détectés).
6. **Glossaire flottant** : tooltips/popovers sur les tokens `V-*`, `I-*`, `TB-*`, `R-*`, `ADR-*`, `phase`, `milestone`, lus depuis `glossary.md` + `nomenclature.md`. Détection regex au rendu.
7. **Indicateur de piste dans l'arbre** : petites pastilles colorées sur les fichiers couverts par la track active.

**Point de validation P2** : tu testes les 3 personae, tu vois si la narration est utile pour présenter. Feedback ciblé sur tracks/glossaire/phases.

### Phase 3 — Polish & accessibilité avancée
But : transformer en outil de présentation et de découverte fluide.
1. **Recherche client** : index Lunr/MiniSearch construit côté serveur, palette `Ctrl+K` minimaliste (titre, ancre, extrait).
2. **Mode présentation** : `F` plein écran, typo agrandie, ←/→ navigent dans la track active, masque les rails, garde le ruban de phase + le talking point.
3. **Carte cognitive** : page `/#/map` en SVG une-page du workflow (phases reliées, parallèles : retrofit, plan-incubation, multi-branch). Clic = saute au document. Sert de seconde porte d'entrée.
4. **Permaliens d'étape** : `?track=present&step=3` + bouton "Copier le lien d'étape".
5. **Toggle clair/sombre** (option, si temps).
6. **Petits raffinements** : breadcrumb cliquable, "documents liés" basé sur les liens entrants détectés à l'indexation, badge "modifié il y a X jours".

**Point de validation P3** : usage réel en simulation de présentation + navigation libre. Closeout.

## Fichiers à créer (Phase 1)
- `product-docs/site/package.json` — deps : express, marked, gray-matter, highlight.js, marked-gfm-heading-id (pour TOC stable).
- `product-docs/site/server.js` — serveur Express + indexeur de corpus + endpoint rendu.
- `product-docs/site/public/index.html` — coquille 3 volets.
- `product-docs/site/public/styles.css` — palette + layout.
- `product-docs/site/public/app.js` — routing client, fetch tree/doc, gestion arbre + TOC + scroll-spy.
- `product-docs/site/README.md` — `npm install && npm start` → `http://localhost:4321`.
- `product-docs/site/.gitignore` — `node_modules/`.

## Fichiers à créer (Phase 2, en plus)
- `product-docs/site/phases.json`
- `product-docs/site/tracks.json`
- `product-docs/site/public/wizard.js`, `public/mermaid-loader.js`, `public/glossary.js`

## Architecture de référence à réutiliser
- `product-docs/methodology/view/index.html` : palette, conventions de typo, sobriété visuelle (tone-setter direct).
- `sites/ezkey-org/index.html` : cohérence de marque (logo, ton).
- `product-docs/methodology/README.md` (section "Reading order") : sert de squelette pour la track *Discover*.
- `product-docs/methodology/workflow-overview.md` : sert de squelette pour la track *Apply* (correspond aux phases du workflow).
- `product-docs/glossary.md` + `methodology/nomenclature.md` : source du tooltip glossaire.

## Vérification
- **P1** : `npm start` dans `product-docs/site/`, ouvrir `http://localhost:4321`, valider :
  - arbre liste tous les fichiers du corpus retenu (methodology + templates + glossary) ;
  - cliquer sur 5 documents variés (README, workflow-overview, un template, glossary) → rendu correct, TOC à droite peuplé, scroll-spy fonctionnel ;
  - palette cohérente avec `methodology/view/index.html` ;
  - aucun crash sur fichiers avec frontmatter ou liens relatifs.
- **P2** : activer chaque track depuis la home → footer wizard apparaît, prev/next traversent les étapes, le doc s'ouvre à la bonne ancre, ruban de phase correct ; ouvrir un fichier contenant ```mermaid → diagramme rendu ; passer la souris sur "TB-2025-..." → tooltip glossaire.
- **P3** : `Ctrl+K` → recherche retourne résultats pertinents ; `F` → mode présentation, ←/→ avance dans la track ; `/#/map` → carte cliquable.
- Aucune dépendance Maven/Java touchée ; aucun changement aux artefacts existants du corpus.

## Note d'architecture préventive (à appliquer dès P1, coût zéro)
Pour faciliter la future Phase 4 (déploiement Cloudflare), structurer `server.js` autour de
fonctions pures exportables : `renderDoc(absPath) → { html, toc, frontmatter }`,
`buildTree(roots) → tree`, `extractToc(html) → toc`. Express devient une fine couche par-dessus.
Aucun travail supplémentaire en P1, mais ça rend la Phase 4 quasi-triviale (un `build.js` qui
réutilise ces fonctions).

## Hors scope P1–P3 (explicite)
- Pas d'authentification, pas de déploiement web public (objectif local).
- Pas d'édition Markdown depuis l'UI (lecture seule).
- Pas de traduction FR/EN automatique (le corpus est en anglais, l'UI sera bilingue light : labels FR courts car contexte personnel).
- Pas de modification des documents existants du corpus.
- Pas de génération statique pré-build pendant P1–P3 (réservé à la Phase 4 ci-dessous).

## Phase charnière (fin de P3) — Génération du prompt de cadrage Phase 4
Après validation de P3 et avant de clore le chantier local, produire un **prompt de cadrage**
auto-suffisant pour qu'une session future puisse entreprendre la Phase 4 sans contexte verbal.
Le prompt devra inclure :
- état final atteint (résumé du site local, structure de fichiers, dépendances) ;
- intention déploiement public (où, pourquoi : vitrine méthodologique alignée sur le site marketing `sites/ezkey-org/`) ;
- contraintes : Cloudflare Pages cible préférée ; cohérence visuelle avec ezkey.org ; corpus reste le source-of-truth dans `product-docs/` ;
- voie technique recommandée (pré-build statique via `build.js`) + alternatives écartées avec rationale ;
- liste précise des adaptations à prévoir (liens relatifs, index de recherche statique, sérialisation tracks/phases/glossaire, gestion Mermaid SSR vs client, i18n potentiel) ;
- critères de succès (build idempotent, parité fonctionnelle avec le local, déploiement Pages réussi, lien depuis le site marketing) ;
- pointeurs vers les fonctions pures exportées de `server.js` (`renderDoc`, `buildTree`, `extractToc`) à réutiliser ;
- décisions ouvertes à reprendre (URL publique, branding/footer, analytics opt-in, FR/EN).

Livrable : un seul fichier `product-docs/site/HANDOFF-PHASE-4.md` (le seul markdown créé,
explicitement demandé par l'utilisateur dans ce contexte). Sert de prompt prêt-à-coller pour
ouvrir la session future.

## Phase 4 (FUTURE — hors scope actuel) — Exposition publique Cloudflare
But : publier le micro-site comme vitrine méthodologique alignée avec `sites/ezkey-org/`.
Approche pressentie :
1. Voie recommandée : **pré-build statique** → Cloudflare Pages.
   - `build.js` itère sur le corpus, appelle les fonctions pures de P1, écrit `dist/*.html`.
   - `tracks.json`, `phases.json`, glossaire et index de recherche : JSON statiques.
   - Réécriture des liens relatifs en URLs propres (`/methodology/workflow-overview/`).
   - Mermaid : rendu client (déjà le cas en P2) ou SSR via `@mermaid-js/mermaid-cli` si pertinent.
2. Alternative : Cloudflare Pages + Functions (port Express → Hono) — seulement si rendu dynamique edge devient nécessaire. Probablement pas requis.
3. Intégration avec `sites/ezkey-org/` : sous-domaine ou sous-chemin (à décider), lien depuis `methodology.html`.
4. CI : déclenchement sur push `main` modifiant `product-docs/**` ou `product-docs/site/**`.

Mon avis initial : **très déployable, faible risque**. Le choix npm/Express en P1 n'est pas un
piège : marked + gray-matter + highlight.js tournent identiquement en build-time. Le portage = un
seul fichier `build.js`, à condition de respecter la note d'architecture préventive ci-dessus.

## Considérations supplémentaires (à trancher si besoin)
1. **Langue UI** : labels en FR (cohérent avec ton usage) ou EN (cohérent avec le corpus). Recommandé : **FR**, car outil personnel ; corpus reste EN.
2. **Port** : `4321` (mnémo, libre). Si conflit, override via env `PORT=`.
3. **Recharge live** : SSE simple sur changement de fichier (P2 si trivial, sinon P3). Recommandé : **non-prioritaire**, F5 suffit pour un outil local.
