# Plan : Nouveau draft « Peindre un admin UI en collaboration avec l'IA »

## TL;DR
Créer un nouveau draft FR de type essai personnel (Craft & engineering) sous `sites/ezkey-org-editorial/fr/draft-peindre-un-admin-ui-en-collaboration-avec-l-ia.md`, avec comme fil conducteur l'analogie de la peinture (mère technicienne, père décalques, fils art naïf/abstrait → couches successives sur un canevas UI). Le plan commence par une **extraction Git exhaustive** (branche `main`, janvier → début mai 2026) ordonnée chronologiquement et archivée dans un fichier de recherche séparé non déployé, qui servira de base factuelle pour confronter/nuancer/compléter le récit verbatim du fondateur. Le draft conserve l'intégralité du brain dump verbatim dans un bloc `exclude-start` (trace historique, jamais publié) et structure le récit autour de l'inception (CLI Python → tenant admin UI), du choix de stack React, de l'approche horizontale-avant-verticale, des couches itératives (Dashboard, master/detail par entité, pagination + Orval + hooks, i18n, RFC 9457, time zone, aide contextuelle, navigation gauche/droite, mode démo, sécurité cookie + CSRF), et de l'effet amplificateur de la collaboration humain–IA.

## Phases

### Phase 1 — Extraction Git et tri chronologique (bloquante)
1. Créer `sites/ezkey-org-editorial/fr/research-admin-ui-git-history.md` (statut « research », non déployé, décision commit/no-commit à reporter).
2. Exécuter en parallèle plusieurs `git log` sur `main` avec fenêtre `--since=2026-01-01 --until=2026-05-15`, format `%h %ad %s` (ISO), filtres `--` :
   - `ezkey-admin-ui/`
   - `specs/`
   - `ezkey-admin-api/`
   - `docs/ADMIN_UI*.md docs/admin-ui-*.md`
   - `plans/ .cursor/plans/` (filtrer ensuite ce qui touche UI dans les titres)
3. Compléter par `git log --all --grep='admin[- ]?ui|admin-api|orval|dashboard|i18n|RFC ?9457|csrf|cookie' -iE --since=2026-01-01 --until=2026-05-15` pour rattraper les commits hors paths.
4. Structurer le rapport par **catégories thématiques + ordre chronologique** :
   - Inception & stack initial (React, Vite/CRA, TS, MUI/Tailwind/etc.)
   - Login & sécurité (token opaque, cookie, CSRF / nonce)
   - Dashboard (mock → widgets → service dédié → auto-refresh)
   - Master/detail par entité (génération, raffinements modal, pagination)
   - Orval + OpenAPI + hooks personnalisés
   - Internationalisation FR/EN + erreurs RFC 9457 paramétrées
   - Aide contextuelle (tooltips, popovers `?`, corpus documentaire par écran)
   - Navigation détail ↔ flèches G/D + stabilité visuelle des lignes
   - Time zone + format des timestamps (absolu vs relatif)
   - Identité opérateur dans le header + about/version
   - Mode démo (clic gauche sur logo, badges thématiques)
   - Foreign keys numériques → résolution lisible (revirement de design)
   - Gestion d'erreurs UI (toasts, palette, durée, slots backend)
   - Liens inter-entités (langage visuel)
5. Pour chaque catégorie : extraire `sha + date + sujet` + courte glose (1 ligne) + lien éventuel vers un plan/doc. Identifier les **lacunes vs verbatim** (sujets que le fondateur n'a pas évoqués mais que les logs révèlent) → marqueur 🆕 à discuter en Phase 2.
6. Capturer le **stack technique exact** depuis `ezkey-admin-ui/package.json` + `vite.config.*` + `AGENTS.md` pour citation précise dans le draft.

### Phase 2 — Discussion de validation (point de synchronisation utilisateur)
1. Présenter à l'utilisateur le rapport Git condensé + la liste des 🆕 (sujets non couverts par le verbatim mais significatifs).
2. Recueillir : confirmations, nuances, omissions volontaires, ordre chronologique à corriger.
3. Identifier 2–3 exemples concrets pour illustrer (ex. boîte de recherche dans un select, refactor du widget Dashboard vers endpoint dédié, migration localStorage → cookie HttpOnly).

### Phase 3 — Rédaction du draft (après validation Phase 2)
1. Créer `sites/ezkey-org-editorial/fr/draft-peindre-un-admin-ui-en-collaboration-avec-l-ia.md` avec YAML front matter complet (status `draft`, `planned_slug_fr`, `planned_canonical`, audience).
2. Bloc `exclude-start` initial : titres de travail alternatifs + notes éditoriales + **brain dump verbatim intégral** dans une sous-section dédiée (référence historique de la collaboration humain–IA).
3. Bloc `exclude-start` séparé : pointeur vers `research-admin-ui-git-history.md`.
4. Corps publiable structuré (ordre proposé, à raffiner) :
   - **I. Le gars qui ne savait pas peindre** (préambule personnel, peinture de la mère, décalques du père, mon goût pour l'art naïf/abstrait — filigrane discret, pas envahissant)
   - **II. D'un TUI Python à un admin UI React** (inception rapide, choix de stack avec citation des libs réelles)
   - **III. La couche de fond : largeur avant profondeur** (passe horizontale entité par entité, mocks, Dashboard squelette ; séparation conceptuelle largeur/profondeur ; tracer bullet appliqué au UI)
   - **IV. Les couches de finition** (récit chronologique condensé issu de Phase 1 : Dashboard → service dédié ; Orval + hooks ; pagination uniformisée ; i18n + RFC 9457 ; aide contextuelle ; navigation G/D + stabilité visuelle ; time zone ; mode démo ; identité opérateur ; about/version)
   - **V. Les retours en arrière assumés** (FK numériques → résolution ; non-pagination → pagination généralisée ; localStorage → cookie)
   - **VI. La sécurité comme couche transversale** (eat your own dog food, token opaque Spring Security, audit, cookie HttpOnly, CSRF/nonce anti-replay — avec ton humble sur la terminologie)
   - **VII. L'amplificateur** (collaboration humain–IA comme outil qui comble les lacunes ; ce qui aurait été impossible six mois plus tôt ; ce qui reste à raffiner)
5. Liens internes (à intégrer sans alourdir) : `from-code-to-intent-ai-workflow.html`, `ai-return-to-foundations-methodology.html`, `from-monolith-to-contract.html`, `ai-coding-manifesto.html`.
6. Pas de section « lecture en X minutes ». Tonalité essai. Filigrane peinture en ouverture, rappel léger en transition III→IV et en clôture VII — pas plus.

### Phase 4 — Itérations d'ajustement de ton
1. Première relecture utilisateur du draft complet.
2. Passes ciblées : équilibre filigrane peinture (pas d'exagération), précision technique (stack, hooks Orval, RFC 9457, CSRF), humilité non défensive (cf. principe éditorial), dates visibles.
3. Stop avant publication. La conversion HTML EN/FR + indexation `articles.html` + sitemap sont **hors scope** de ce plan (relèveront d'un plan ultérieur déclenché par l'utilisateur).

## Fichiers concernés (à créer / lire)

- À créer : `sites/ezkey-org-editorial/fr/research-admin-ui-git-history.md` — rapport d'extraction Git condensé, non déployé.
- À créer : `sites/ezkey-org-editorial/fr/draft-peindre-un-admin-ui-en-collaboration-avec-l-ia.md` — draft de l'article.
- À lire : `ezkey-admin-ui/package.json`, `ezkey-admin-ui/AGENTS.md`, `ezkey-admin-ui/README.md` — stack exact.
- À lire (référence stylistique) : `sites/ezkey-org-editorial/fr/draft-ai-return-to-foundations-methodology.md` — structure YAML + blocs exclude + tonalité essai.
- À lire (cross-link) : `sites/ezkey-org/from-code-to-intent-ai-workflow.html`, `sites/ezkey-org/ai-coding-manifesto.html`.

## Vérifications

1. `research-admin-ui-git-history.md` contient tous les commits pertinents avec sha + date ISO + sujet, regroupés par catégorie thématique, dans l'ordre chronologique au sein de chaque catégorie.
2. Le draft compile mentalement comme essai cohérent sans le brain dump (le verbatim est en `exclude-start`, donc invisible à la publication).
3. Le filigrane peinture apparaît au max à 3 endroits (ouverture, transition centrale, clôture) — vérifier par recherche `peinture|couche|calque|toile`.
4. Le stack React cité correspond exactement à `package.json` (pas d'invention de libs).
5. Aucune référence à WebAuthn/FIDO2 en termes objectifs de comparaison (cf. principes éditoriaux `AGENTS.md`).
6. Aucun lien GitHub public dans le draft (repo encore privé).
7. YAML front matter complet (`status: draft`, `audience`, `planned_slug_fr`, `planned_canonical`).
8. Aucun fichier draft créé sous `sites/ezkey-org/` (règle critique : `ezkey-org-editorial/` uniquement).

## Décisions actées (Phase 0 — questions utilisateur)

- **Langue** : FR d'abord. HTML EN/FR à publication, hors scope de ce plan.
- **Slug planifié** : `peindre-un-admin-ui-en-collaboration-avec-l-ia.html`.
- **Brain dump verbatim** : dans le draft .md à l'intérieur d'un bloc `<!-- ezkey-org:exclude-start ... ezkey-org:exclude-end -->`, jamais publié.
- **Rapport Git** : fichier séparé `sites/ezkey-org-editorial/fr/research-admin-ui-git-history.md`, décision commit/no-commit reportée.
- **Paths Git** : `ezkey-admin-ui/`, `specs/`, `ezkey-admin-api/`, `docs/ADMIN_UI*.md`, `docs/admin-ui-*.md`, `plans/`, `.cursor/plans/` + filtre grep complémentaire.
- **Lane de publication** : Craft & engineering.

## Considérations supplémentaires

1. **Point de synchronisation entre Phase 1 et Phase 3** : l'utilisateur a explicitement demandé une discussion après l'extraction Git pour confronter le verbatim aux faits. Phase 2 est non négociable.
2. **Profondeur sécurité** : le verbatim mentionne CSRF/nonce avec terminologie « boiteuse ». Recommandation : faire valider la terminologie exacte (CSRF token vs nonce vs same-site cookie) par l'extraction Git et la documenter précisément dans le draft sans rendre le passage trop technique.
3. **Risque de longueur** : le sujet est dense. Si le draft dépasse ~2500 mots utiles, proposer en Phase 4 de scinder une partie en article companion (ex. « La pagination, Orval et les hooks personnalisés ») ou d'externaliser certains détails techniques vers un article existant.
