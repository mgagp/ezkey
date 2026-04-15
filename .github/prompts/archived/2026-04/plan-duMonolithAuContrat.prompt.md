# Plan v2 : Article — Du monolithe au contrat (OpenAPI, slow AI, backend-first) ✅ COMPLÉTÉ

## TL;DR
Nouvel article FR (~6–7 min, ~1 300 mots) dans `sites/ezkey-org/fr/`.
Arc d'évolution : monolithe → split progressif → discipline OpenAPI non-négociable → script update-specs comme geste de gatekeeper → workflow deux sessions.
Nouvelles idées v2 : philosophie "slow AI" (gatekeeper humain délibéré), backend-first comme fil rouge structurant, le CLI et les projets expérimentaux comme preuves supplémentaires.
Pretty-print downplacé (détail utile, pas un game-changer).
Titre à affiner — plusieurs alternatives proposées.

---

## Périmètre différenciateur (vs articles existants)

Unique dans cet article :
- Arc évolutif monolithe → 2 → 3 → 4 backends (nulle part ailleurs)
- Mécanique du script update-specs : extract live → pretty-print → dispatch (nulle part ailleurs)
- Philosophie "slow AI" / gatekeeper : choisir de NE PAS automatiser l'invocation (nulle part ailleurs)
- Workflow deux sessions déployé en complet (1 phrase dans `from-code-to-intent-ai-workflow` §09 seulement)
- Backend-first comme identité structurante du projet

Cross-links à la place de répéter :
- Bounded contexts → `ai-coding-manifesto.html` §04
- "Context engineering" comme concept → `generative-ai-nocode-lowcode-parallel.html`
- Workflow global intention→code→QA → `from-code-to-intent-ai-workflow.html`

---

## Fichiers concernés

Créer : `sites/ezkey-org/fr/[slug-à-définir].html`
Modifier : `sites/ezkey-org/fr/index.html` — ajout pub-card
Modifier : `sites/ezkey-org/AGENTS.md` — section méthodologie de rédaction
Template : `sites/ezkey-org/fr/from-code-to-intent-ai-workflow.html`

---

## Outline des sections (v2)

### 01 — L'application qui faisait tout (~120 mots)
- 2024/début 2025, ezKey démarre en backend unique
- Tout ensemble : admin, auth, intégration — pas de frontière parce qu'il n'y en a qu'une
- Phase d'exploration, pas de jugement rétrospectif
- Amorce du fil rouge : je suis développeur backend, je pense en API depuis le début

### 02 — La première fracture : suivre la logique de confiance (~150 mots)
- L'admin ne sera jamais exposé à l'extérieur (VPN, accès différents)
- Auth API = backend-for-frontend pour le mobile
- Integration API = contrat vers les intégrateurs tiers
- La séparation suit le modèle de confiance, pas arbitraire
- 2 backends → 3 → 4 (Crypto API en dev)
- Les consommateurs qui suivent : admin UI, mobile, demo device, CLI python, SDK, dart (expérimental), PAM (expérimental)

### 03 — Javadoc, OpenAPI : la discipline non-négociable (~140 mots)
- Non-négociable depuis le jour 1 : Javadoc + contrôleurs REST entièrement documentés
- SpringDoc expose l'OpenAPI dynamiquement — contrat vivant, toujours frais
- Quand les backends se multiplient, la discipline devient valeur concrète
- Un contrat n'a de valeur que s'il est accessible, frais et consommable → déclic du script

### 04 — Le script update-specs : extract, format, dispatch (~180 mots)
- "Si chaque backend expose son OpenAPI en live, pourquoi ne pas extraire et dispatcher automatiquement ?"
- Responsabilité : backend live → JSON validé → pretty-print → copié dans chaque sous-dossier consommateur
- Mapping :
  - admin-api → ezkey-admin-ui, ezkey-demo-app-acme, ezkey-sdk
  - auth-api → ezkey-mobile, ezkey-demo-device, ezkey-sdk, ezkey-cli (python)
- La spec est versionnée dans le sous-dossier du consommateur — artefact du dépôt
- Note calibrée sur le pretty-print : pas un game-changer, mais un détail sensé — JSON monoligne rend le grep de l'agent peu efficace ; `jq .` résout ça simplement

### 05 — Slow AI : je suis le gatekeeper (~200 mots)
- Note philosophique : le script pourrait être invoqué par l'agent — techniquement faisable
- Choix délibéré de ne pas l'automatiser → j'appelle ça "slow AI"
- Je ne cherche pas l'autonomie maximale ; je cherche la bonne interaction humain-AI
- Rôle du gatekeeper : c'est moi qui décide quand les API sont assez solides pour devenir le contrat d'un consommateur
- Processus : impl → tests unitaires → tests fonctionnels → validation Postman (collection sur spec) → jugement humain → update-specs → dispatch
- Ce moment de gating = lire la spec générée, valider que le contrat reflète l'intention
- Cohérent avec backend-first : l'UI, le mobile, le SDK méritent un contrat solide, pas un brouillon

### 06 — Deux sessions, deux contextes (~220 mots)
- **Session backend** : design → impl → tests → Postman → validation humaine → update-specs → fin de session. Contrat "gelé", versionné, dispatché
- **Session consumer (admin UI)** : session fraîche, lit le plan de la session précédente + openapi-spec.json dans ezkey-admin-ui. Agent travaille entièrement dans sa verticale
- **Session mobile** : même principe, openapi-spec.json dans ezkey-mobile / ezkey-cli / ezkey-dart
- Plan précédent = contexte compressé en peu de tokens : décisions, intentions, contraintes — sans bruit
- Ce qui rend le contexte "performant" : la frontière est définie par la spec, pas à négocier en cours de session

### 07 — L'API d'abord, vraiment (~160 mots)
- Réflexion personnelle : je ne commence pas par une maquette, je commence par une vision qui se réalise en API + schéma BD
- Ezkey est developer-first, backend-first — positionnement, pas juste détail d'implémentation
- La spec comme acte fondateur de chaque verticale : tant que la spec n'est pas validée, rien d'autre ne commence
- OpenAPI, spec-first, bounded contexts : recommandés depuis des années, rarement vraiment suivis. Ici ils cessent d'être optionnels par construction
- Mention brève des expérimentaux (ezkey-dart, PAM) : même philosophie, même point de départ — la spec précède tout
- "Ce n'est plus une bonne intention. C'est la contrainte qui structure tout le reste."

### 08 — Ce que ça m'a appris pour de vrai (~140 mots)
- "Context engineering" : belle expression. Ma traduction : le contexte est un choix architectural, pas de la configuration
- Sessions fraîches + sujets bien bornés > framework de compression
- Slow AI n'est pas une faiblesse — c'est un positionnement qui préserve la qualité des décisions
- Closing : "Le meilleur contexte n'est pas le plus complet. C'est celui qui sait s'arrêter."
- Cross-links : `from-code-to-intent-ai-workflow.html`, `ai-coding-manifesto.html`, `generative-ai-nocode-lowcode-parallel.html`

---

## Titres candidats (à choisir)

1. **Du monolithe au contrat** — *Comment l'OpenAPI est devenu le squelette structurant d'un workflow AI.*
2. **L'API d'abord — et tout le reste suit** — *Un an à apprendre à découper, dispatcher et laisser la spec faire le travail.*
3. **Le gatekeeper pragmatique** — *OpenAPI, slow AI, et l'art de savoir quand un contrat est vraiment prêt.*
4. **Spec-first, pour de vrai** — *Du monolithe aux contextes délimités : le chemin concret.*
5. **Du monolithe au multi-backends** — *Comment la discipline OpenAPI est devenue la colonne vertébrale du projet.*

---

## Métadonnées article (à confirmer après choix du titre)

| Champ | Valeur |
|---|---|
| Tagline | Comment la spécification OpenAPI est devenue le contrat structurant de mon workflow AI. |
| Kicker | Un an d'évolution progressive — et un script de 30 lignes qui a tout changé. |
| Label | `Retour d'expérience` |
| Temps de lecture | ~6–7 min |

---

## Livrable 2 — AGENTS.md : méthodologie de rédaction

Section à ajouter dans `sites/ezkey-org/AGENTS.md` :
- HTML en français généré en premier
- Temps de lecture 3–8 min, audience développeurs intermédiaires-sénior
- Deux tons : descriptif/projet (formel mesuré) vs expérientiel (voix personnelle, détendu mais sérieux)
- Lire les articles existants avant de rédiger — éviter la duplication, tisser des liens croisés
- Nommage : slug en français (kebab-case)
- Checklist publication : pub-card dans `fr/index.html`, `hreflang` dans `<head>`
- Template de référence : `from-code-to-intent-ai-workflow.html`

---

## Vérification

1. HTML rendu navigateur — mise en page, nav, `hreflang`
2. Trois liens croisés fonctionnels : `from-code-to-intent-ai-workflow.html`, `ai-coding-manifesto.html`, `generative-ai-nocode-lowcode-parallel.html`
3. `pub-card` visible dans `fr/index.html`
4. Compte de mots ~1 300 → label 6-7 min cohérent
5. `AGENTS.md` — section méthodologie présente
6. Aucune duplication avec §09 de `from-code-to-intent-ai-workflow.html`

---

## Décisions

- Angle : récit d'évolution, pas tutoriel, pas manifeste
- Ton : expérientiel, voix praticien solo
- Pretty-print : détail utile, pas titré en "game-changer"
- Slow AI : concept propre à l'auteur, à présenter avec légèreté mais conviction
- Scope exclu : pas de code snippets, pas d'explication SpringDoc, pas de reproduction du workflow global
