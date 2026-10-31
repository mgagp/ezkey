# AI Software Factory : mandats des bots versionnés, portables et visibles des cloud agents — évaluation 2026-10-16

> **Statut :** analyse, PR en draft, aucune décision prise. Rien n'est créé pour les 13 bots.
> **Demandeur :** Marc. **Portée :** documentation seulement, aucun code.
> **Entrées :** `ezkey-bot-profiles.json` (13 profils exportés de Grok Bot), `skills.tgz` (2 skills),
> corpus du dépôt sur `main` @ `0e2b3cda`, recherche web du 2026-10-16 (sources en fin de document).
> **Convention :** **[Confirmé]** = appuyé sur une source citée ; **[Observé]** = constaté dans le dépôt,
> les pièces jointes ou l'environnement de ce cloud agent ; **[Supposé]** = interprétation ou
> spéculation, à vérifier.

## Résumé

Les mandats des 13 bots (environ 52 000 caractères) vivent uniquement dans Grok Bot. Le dépôt n'en garde
qu'une ligne par rôle (`team-roles.md`). Un cloud agent démarré sans contexte voit `AGENTS.md`,
`.cursor/rules` et `.cursor/skills`. Il ne voit ni les mandats, ni les anti-mandats, ni la mémoire des
bots : seulement le brief que le bot lui écrit. Ce n'est pas une limite d'Ezkey, c'est l'état du
marché. Tous les fournisseurs convergent vers des définitions d'agents en Markdown + YAML dans le dépôt,
des skills `SKILL.md` standardisées et `AGENTS.md` comme dénominateur commun. La mémoire des agents,
elle, reste propriétaire et non portable. La piste proportionnée tient en trois points. Un répertoire
versionné des mandats, en Markdown neutre, comme source de vérité. Des adaptateurs minces seulement pour
les rôles qui lancent des cloud agents. Une règle simple pour verser le steering durable dans le dépôt
au moment où Marc dit « retiens ça ». Cela revient à revenir sur une décision explicite de #787 (« pas
de copie des mandats ») : c'est la question n° 1 pour Marc.

## 1. État des lieux

### 1.1 Où vit chaque morceau

| Morceau | Où il vit aujourd'hui | Versionné ? | Visible d'un cloud agent sans contexte ? |
| --- | --- | --- | --- |
| Mandat, voix, boussole, anti-mandats (13 bots) | Profil Grok Bot (champ description) | Non | **Non**, sauf ce que le bot recopie dans son brief |
| Une ligne par rôle | [`team-roles.md`](team-roles.md) (#787) | Oui | Oui, s'il le lit (non chargé d'office) |
| Ajustements de mandat (ex. Julie « rétro #726, validé 2026-10-09 » ; Justin « approuvé 2026-10-14 ») | Texte du profil Grok Bot | Non | Non. Le dépôt n'a reçu que les correctifs de découvrabilité (#739), pas le texte du mandat |
| Steering accumulé (mémoire des bots) | Mémoire Grok Bot, propre à chaque bot | Non | **Non** |
| Skills Grok Bot (`ezkey-mac-test-host-refresh`, `Ezkey Admin UI demo login`) | Bibliothèque privée Grok Bot | Non | Non. Ici, elles sont arrivées seulement en pièce jointe [Observé : pas de `~/.cursor/skills` sur cette VM] |
| Skills du dépôt (6 : `critical-review`, `assessment-curated`…) | [`.cursor/skills/`](../../.cursor/skills/README.md) | Oui | **Oui** |
| Règles et contexte de démarrage | `AGENTS.md`, `.cursor/rules/*.mdc` | Oui | **Oui** (chargés d'office dans ce run) |
| Décisions | Doc propriétaire du sujet (« Repo is the memory », `AGENTS.md` § *Working conventions*, #787) | Oui | Oui, si l'agent trouve le bon doc |
| Méthodes (critical-review, `*-curated`, Walk Gate) | `product-docs/global/hygiene/`, `product-docs/templates/` | Oui | Oui |
| Brief envoyé au cloud agent | Prompt rédigé par le bot | Non (transcript seulement) | C'est le seul vecteur des mandats aujourd'hui |

[Observé] Ce run a aussi un montage `/cursor/stores/bot` (« store du bot »), vide. Je n'ai pas trouvé
de documentation publique sur ce mécanisme. Je ne le compte donc pas comme canal fiable.

### 1.2 La directive « la mémoire, c'est le dépôt » et ses limites

- [Observé] `AGENTS.md` § *Working conventions* : toute décision qui change un comportement, un
  contrat, une contrainte ou une façon de travailler va dans le doc propriétaire, dans la même PR,
  « Chat-only or mandate-only counts as unrecorded ».
- [Observé] [`methodology/README.md`](../methodology/README.md) § *Decisions made in discussion* :
  même règle pour les décisions de chat, d'équipe et de mandat, avec cette phrase : « Do not invent a
  decision log or copy mandates into the repo ». La PR #787 l'a écrit explicitement : « not added:
  … bot-mandate copies ».
- [Observé] Huit mandats sur treize recopient la clause « le dépôt est la mémoire » ; Mathieu,
  Clémentine, Carl, Edgar et Audrey ne l'ont pas.
- [Observé] La directive couvre les **décisions projet**. Elle ne couvre pas le **rôle lui-même** :
  qui fait quoi, les anti-mandats, la voix, le routage. C'est justement ce qui manque à un cloud agent
  qui démarre à froid.
- [Confirmé] La documentation Grok Bot dit la même chose côté outil : la mémoire « is not a
  substitute for an authoritative source ». Elle conseille de garder les faits changeants dans le
  système source [S3].

### 1.3 Signes de dérive constatés dans les 13 profils [Observé]

- **Rétro #739.** C'est la PR (fusionnée) qui applique la rétro de #726. Le changement du mandat de
  Julie (« passe de contenu » sur tout nouveau mode) n'existe que dans l'app ; le dépôt a reçu les
  règles générales (Walk Gate élargi, posture adoptant en Tier 0). Les deux peuvent diverger sans que
  rien ne le signale.
- **Critical-review.** Le canon [`hygiene/critical-review/README.md`](hygiene/critical-review/README.md)
  nomme Mathieu, Patrick et Christophe comme lanceurs. Aucun des 13 mandats ne contient le mot-clé
  `critical-review`.
- **Lignes périmées.** Isabelle dit encore « Fred — being created next ». Alex indique
  `operational-readiness-prioritization-2026-09.md` comme première lecture pour « what next », alors
  que `AGENTS.md` place désormais `V-2026-09-26-public-alpha-posture-closeout.md` en premier et classe
  ce fichier comme historique.
- **Hypothèses de poste.** Huit mandats codent en dur `C:\github\ezkey`, alors qu'un cloud agent
  travaille dans `/workspace`. « K » apparaît dans onze mandats et dans aucun doc du dépôt.
- **Hétérogénéité.** Trois mandats seulement ont une section « Cloud agents » (Christophe, Patrick,
  Alex). Le français et l'anglais se mélangent. Mathieu fait 1 500 caractères, Patrick 5 800.
- **Skills jointes.** `ezkey-mac-test-host-refresh` est une vraie procédure projet, cohérente avec
  `AGENTS.md` (`~/w/ezkey-main`). `Ezkey Admin UI demo login` a son corps encodé comme une chaîne JSON
  sur une seule ligne. Son contenu (ne pas cocher la case du code à deux chiffres, attendre le minuteur)
  recoupe [`docs/testing/AGENT_UI_VALIDATION.md`](../../docs/testing/AGENT_UI_VALIDATION.md).

## 2. Ce qui existe : Grok Bot et les équivalents

### 2.1 Grok Bot et le « Bot Development Kit »

**[Confirmé]**

- **Grok Bot** est documenté à la fois par Cursor (`cursor.com/docs/grok-bot`) et par xAI
  (`x.ai/bot/guides`). Un bot y est un « durable AI teammate » avec un nom, un rôle, sa conversation
  et une mémoire qui s'accumule [S1][S3].
- **Ce qui est réglable :** une leçon durable se règle en le disant au bot (« ask it to remember »).
  Un Team Bot a en plus une description, éditée sous *About* [S3].
- **Ce que retient la mémoire :** « stable preferences, important facts, and summaries ». Elle est
  séparée pour chaque bot [S1][S3].
- **Partage et duplication :**
  - Les skills sont partagées entre tous les bots d'un compte ; une routine appartient à un seul bot
    [S3].
  - Partager un bot (lien ou *template*) produit une **recette**, pas un clone : identité,
    description, skills, routines. Ni l'ordinateur, ni les logins, ni l'historique [S3][S4].
  - Dupliquer un bot copie le profil, les skills et les routines, mais **pas la mémoire apprise** [S3].
  - Les bots d'un même utilisateur partagent un seul ordinateur cloud. L'écran de chaque bot n'est pas
    une frontière de sécurité [S3].
- **Lien avec Cursor :** les bots savent lancer des cloud agents Cursor, lire leurs transcripts et
  relancer. Le schéma décrit est une boucle externe (le bot) qui passe « a clean prompt » à une boucle
  interne (le cloud agent) [S5][S6].
- **Le « kit » s'appelle Bot Development Kit (BDK).** C'est un produit Cursor (`@cursor/bdk`, CLI
  `bdk`) pour « define, test, and deploy agents as code » [S2] :
  - **Format :** un dossier `bot/` versionnable avec `agent.ts` (modèle, `grok-4.5` par défaut),
    `instructions.md` (prompt système), `skills/*/SKILL.md`, `tools/`, `subagents/<id>/`,
    `mcp-connections/`, `channels/`, `hooks/`, `schedules/`, plus `evals/` à la racine [S2][S7].
  - **Positionnement :** la page BDK compare explicitement Grok Bot (« instructions, skills, and
    connected tools in the app ») et BDK (« a versioned Markdown and TypeScript project ») [S2].
- **Passerelle Grok Bot → dépôt :** `bdk init --from-grokbot recipe.json [--dry-run --json]` [S8].
  - **Ce qui passe :** le profil (vers `agent.ts` et le début d'`instructions.md`), les skills, les
    routines, la mémoire (au choix : skill `knowledge`, seed KV ou rien) et les connecteurs.
  - **Ce qui ne passe pas :** les secrets, dont seuls les **noms** sont exportés ; les OAuth sont à
    refaire. Le transfert serveur des secrets « [is] not shipped ».
  - **Traçabilité :** chaque choix est consigné dans `grokbot-export.json`.

**[Non confirmé]**

- Le nom « Grok Bot Development Kit » : le nom officiel trouvé est **Bot Development Kit (BDK)**.
- Que la structure vue par Marc en présentation soit le dossier `bot/` du BDK : probable [Supposé],
  non vérifiable.
- Une méthode officielle pour obtenir le fichier `recipe.json` attendu par `--from-grokbot` : je ne
  l'ai pas trouvée dans les docs lues. Un outil communautaire le télécharge avec le jeton de session
  de l'app [S9]. Il est non officiel ; à éviter pour des raisons de sécurité.
- Un export officiel **complet** de la mémoire : non documenté [S10, tiers]. La recette contient des
  « memories » [S4], mais on ne sait pas si c'est toute la mémoire ou seulement celle jugée partageable.

[Observé] La version du BDK installée sur cette VM est `0.2.25` ; ses skills documentent une
migration « early alpha v1 → v2 ». C'est un outil jeune.

### 2.2 DeepSeek Harness (« Everything is a plugin »)

**[Confirmé]** Il ne s'agit pas d'une erreur de nom : `deepseek-ai/deepseek-harness` (`dsh`) existe
[S11][S12][S13].

- **Nature et licence :** runtime d'agent open source sous licence MIT, en Node.js. Publié le
  2026-08-13 en *developer preview*.
- **Architecture :** l'adaptateur de modèle, le registre d'outils, le journal de session et la boucle
  d'agent sont tous des plugins Cordis remplaçables par configuration. Les profils fournis sont `web`,
  `headless`, `sdk` et `acp`.
- **Fournisseurs :** l'outil n'est pas lié aux modèles DeepSeek (Anthropic, OpenAI, Bedrock, Azure,
  Gemini sont pris en charge).
- **Portabilité :** il charge par défaut `AGENTS.md` et `CLAUDE.md` (avec des surcharges
  `*.local.md`) de la racine du projet jusqu'au dossier courant, plus un `AGENTS.md` global
  (`$DSH_HOME`, `~/.agents`). Il lit des skills sur le système de fichiers, y compris `~/.agents`. Il
  comprend des passerelles vers les hooks de Claude Code et de Codex, ainsi qu'un paquet expérimental
  `agent-team`.
- **Limite :** Cordis reste un noyau obligatoire sous les plugins [S13, analyse tierce].

### 2.3 Équivalents : formats de définition d'agents

| Outil | Définition d'un rôle | Skills | Instructions projet | Ce qui est versionnable |
| --- | --- | --- | --- | --- |
| Claude Code (Anthropic) | `.claude/agents/*.md`, YAML (`name`, `description`, `tools`, `model`, `skills`, `memory`…) + prompt [S14] | `.claude/skills/*/SKILL.md` | `CLAUDE.md` ; peut lire `AGENTS.md` [S15] | Tout, sauf `~/.claude` (personnel) |
| Codex (OpenAI) | `.codex/agents/*.toml` [S16] | `.agents/skills/` [S17] | Chaîne d'`AGENTS.md`, plafond 32 Kio [S18] | Tout ; « Memories » à part [S19] |
| OpenAI Agents SDK | En code (`Agent(instructions, tools, handoffs)`) [S20] | — | — | Le code |
| Gemini CLI / ADK (Google) | `.gemini/agents/*.md`, YAML [S21] ; ADK : `agent.py` [S22] | Oui (ADK *Agents CLI* livre des skills) [S22] | `GEMINI.md`, nom configurable (dont `AGENTS.md`) [S23] | Tout |
| GitHub Copilot (Microsoft) | `.github/agents/*.agent.md`, aussi au niveau organisation [S24] | Annoncé client Agent Skills [S31] | Lit `AGENTS.md` [S30] | Tout |
| Microsoft Agent Framework | Agents déclaratifs YAML/JSON [S25] | Non vérifié | Non vérifié | Le YAML |
| Cursor | `.cursor/agents/*.md` ; lit aussi `.claude/agents/` et `.codex/agents/` [S26] | `.cursor/skills/`, `.agents/skills/` [S27] | `AGENTS.md` (imbriqué), `.cursor/rules/*.mdc` [S28] | Tout ; les skills utilisateur ne vont aux cloud agents que si « Sync Skills for Cloud Agents » est activé [S27] |
| Grok Bot / BDK | Profil dans l'app ; `bot/instructions.md` en BDK [S2] | Bibliothèque de l'app ; `bot/skills/` | — | Seulement via BDK |

## 3. Ce qui évolue chez les grands fournisseurs (équipes d'agents, packaging, portabilité)

### 3.1 Faits

1. **Définitions de rôles en Markdown + YAML dans le dépôt** chez presque tous : Anthropic, Google,
   GitHub/Microsoft, Cursor [S14][S21][S24][S26]. Exceptions : Codex utilise du TOML [S16] ; l'OpenAI
   Agents SDK et Google ADK définissent les agents en code [S20][S22] ; Microsoft Agent Framework en
   YAML déclaratif [S25].
2. **Lectures croisées.** Cursor lit les agents Claude et Codex [S26]. DeepSeek charge `AGENTS.md` et
   `CLAUDE.md` [S12]. Claude Code peut lire `AGENTS.md` [S15]. Gemini CLI se configure pour lire
   `AGENTS.md` [S23].
3. **`AGENTS.md` est devenu un standard neutre.** OpenAI l'a confié à l'Agentic AI Foundation (Linux
   Foundation), cofondée avec Anthropic et Block, avec le soutien de Google, Microsoft et AWS. Plus de
   60 000 projets l'ont adopté [S29][S30].
4. **Les skills `SKILL.md` sont un standard ouvert** (agentskills.io), annoncé dans plus de 40 clients
   dont Claude Code, Cursor, Copilot, Codex et Gemini CLI [S31, source tierce]. Le dossier
   `.agents/skills` émerge comme racine partagée entre outils (Codex, Cursor, DeepSeek)
   [S17][S27][S12].
5. **Packaging.** *Agent Plugins 1.0* (août 2026) est un manifeste `plugin.json` qui regroupe skills
   et serveurs MCP. Son comité technique inclut Amazon, Cursor, Microsoft, OpenAI et Vercel [S32][S33].
   Les plugins Claude Code et les plugins Cursor ajoutent agents, hooks et règles, mais dans des
   formats propres à chaque outil [S34][S35].
6. **Équipes d'agents.**
   - Claude Code « agent teams » : un lead, des coéquipiers, une liste de tâches partagée. C'est
     expérimental et désactivé par défaut ; un coéquipier peut réutiliser une définition de sous-agent
     [S36].
   - Gemini CLI fait tourner des sous-agents en parallèle [S21].
   - OpenAI propose les *handoffs* et les *agents-as-tools* [S20].
   - DeepSeek a un `agent-team` expérimental [S12].
   - Grok Bot propose des groupes de 2 à 6 bots et des passations asynchrones entre bots [S3].
   - Cursor permet de lancer des sous-agents cloud depuis une session locale [S26].
7. **Protocole inter-agents.** A2A (« Agent Card » JSON), créé par Google et hébergé par la Linux
   Foundation, vise des agents en réseau [S37].
8. **La mémoire reste propriétaire :** Grok Bot (pas d'export officiel documenté [S10]), BDK
   (`ctx.host.kv`) [S7], Codex « Memories » [S19]. Les fournisseurs eux-mêmes conseillent de ne pas
   en faire une source de vérité [S3].

### 3.2 Lecture pour Ezkey [Supposé]

- La couche **portable** se résume à trois pièces : du Markdown lisible (mandat), `AGENTS.md` et
  `SKILL.md`. Tout le reste est un adaptateur propre à chaque outil (frontmatter, TOML, `bot/`).
  Investir dans la couche portable vieillit bien ; investir dans un adaptateur devient vite obsolète.
- Les **formats d'équipe** (agent teams, `agent-team`) sont encore expérimentaux. Ezkey n'a pas besoin
  d'orchestration codée : Mathieu route, Marc décide. Il n'y a rien à adopter maintenant.
- La **mémoire** ne sera pas portable à court terme. Le seul transfert fiable est la **distillation**
  humaine (ou assistée) vers le dépôt.
- BDK et Agent Plugins sont des pistes **plus tard**. Elles deviennent intéressantes si un bot doit
  tourner hors de l'app (BDK) ou si les skills Ezkey doivent s'installer dans plusieurs outils
  (plugin).
- A2A est hors sujet à l'échelle d'Ezkey.

## 4. Pistes d'amélioration (proportionnées, phasées)

| # | Piste | Bénéfice | Coût | Risque |
| --- | --- | --- | --- | --- |
| **P0** | **Hygiène des mandats dans l'app**, sans nouvelle structure : retirer les lignes périmées, ajouter `critical-review` aux trois lanceurs, remplacer `C:\github\ezkey` par « racine du dépôt (ou le worktree nommé) », ajouter la clause « le dépôt est la mémoire » aux 5 mandats qui ne l'ont pas | Corrige la dérive constatée tout de suite | Faible (édition dans l'app) | Reste non versionné : la dérive reviendra |
| **P1** | **Répertoire versionné des mandats** (Markdown + petit frontmatter YAML neutre), source de vérité, l'app en étant une copie | Lisible par tout outil ; relu en PR ; historique Git ; survit à un changement d'outil | Moyen : un gabarit, puis 13 fichiers (environ 52 000 caractères à nettoyer) | Revient sur #787 et la méthode ; risque de double maintenance si le sens de synchro n'est pas fixé (Q2) ; contenu public si le dépôt devient open source (Q3) |
| **P2** | **Exposition aux cloud agents :** (a) une ligne dans la table de pointeurs d'`AGENTS.md` (« rôle nommé → `roles/<slug>.md` ») ; (b) une clause dans chaque mandat : « tout brief de cloud agent cite le chemin du fichier de rôle et recopie les anti-mandats pertinents » ; (c) des adaptateurs `.cursor/agents/<slug>.md` **uniquement** pour les rôles pilotes qui lancent des cloud agents | Un cloud agent froid retrouve le rôle de façon fiable ; (c) donne aussi des sous-agents utilisables | Faible pour (a) et (b), moyen pour (c) | (c) duplique le canon : il faut des adaptateurs minces qui pointent vers le canon, sans recopier |
| **P3** | **Verser le steering durable dans le dépôt.** *Quoi* : une règle de rôle (routage, frontière, anti-mandat) va dans `roles/<slug>.md` ; une décision projet va dans le doc propriétaire (règle existante) ; une préférence personnelle ou hors Ezkey reste dans l'app. *Qui* : le bot propose la ligne, un cloud agent ouvre la PR, Marc fusionne. *Quand* : à chaque « retiens ça » de Marc qui touche un rôle, et lors d'une rétro (comme #726) | Ferme la fuite principale (mandate-only = non consigné) ; même geste que la règle actuelle | Faible : une ligne et une PR par ajustement | Friction si chaque micro-préférence part en PR : ne verser que ce qui changerait le travail d'un autre rôle ou d'un cloud agent |
| **P4** | **Vérification d'écart app ↔ dépôt** : export des profils (le JSON joint ou un `bdk init --from-grokbot --dry-run --json`), puis comparaison avec `roles/*.md` (script Bash, plus tard) ; à chaque changement de mandat et une fois par mois | Rend la dérive visible au lieu de la deviner | Moyen ; dépend d'un export pratique (pas d'API de synchro officielle trouvée) | L'export de recette n'est pas documenté (§ 2.1) ; à manier sans jeton de session |
| **P5** | **Pilote BDK** (optionnel, plus tard) : exporter un seul bot (ex. Fred ou Christophe) en `bot/` hors du monorepo, `validate` + `dev`, puis évaluer | Teste le vrai « bot as code », evals comprises | Moyen à élevé ; outil en alpha | Lock-in Cursor/Grok ; ne doit pas devenir une migration implicite |

**Ne pas versionner (dans aucune phase) :**

- secrets, jetons, clés API, OAuth, keystores, certificats d'origine (déjà interdit dans le dépôt) ;
- données personnelles, ainsi que tout ce qui concerne K ou la vie quotidienne (« daily-life or
  non-Ezkey work ») ;
- dumps bruts de mémoire et transcripts, qui sont périmables, personnels et fausses sources de vérité
  [S3] ;
- chemins, hôtes et emplacements de clés propres à une machine, au-delà de ce que `AGENTS.md` assume
  déjà (Rootbeer, `~/w/ezkey-main`) ;
- avatars et noms de connecteurs liés à un compte.

Écrire les mandats comme des documents publics : le dépôt doit passer en open source sous MIT
(positionnement d'Audrey).

**Ordre recommandé (80/20) :** P0, puis P3 (dès la décision Q1), puis P1 et P2 (a)+(b), puis
P2 (c) sur 2 ou 3 rôles pilotes, puis P4. P5 seulement si un bot doit vivre hors de l'app.

## 5. Proposition de structure (sans créer encore les fichiers des 13 bots)

```text
product-docs/global/factory/
  README.md              # contrat : source de vérité, format, processus P3, liste "ne pas versionner",
                         #   sens de synchro app <-> dépôt ; lien depuis team-roles.md et AGENTS.md
  roles/
    _TEMPLATE.md         # gabarit unique ci-dessous
    <slug>.md            # plus tard : un fichier par rôle (christophe-security.md, ...)
.cursor/agents/<slug>.md # P2 (c) : adaptateur mince pour les rôles pilotes seulement ;
                         #   frontmatter outil + "lis product-docs/global/factory/roles/<slug>.md"
.cursor/skills/<nom>/    # skills Grok Bot à valeur projet, après tri (ex. ezkey-mac-test-host-refresh)
scripts/factory-drift-check.sh   # P4, plus tard
```

`team-roles.md` reste l'index d'une ligne par rôle. Les dossiers `.claude/agents/` et `.agents/skills/`
ne servent à rien tant que Cursor reste l'outil des cloud agents (Cursor les lit déjà [S26][S27]).
`factory/` reste hors du périmètre de publication méthodologique (frontière dans `AGENTS.md`).

Gabarit de `roles/_TEMPLATE.md` (format neutre, proche des frontmatters Claude, Gemini, Copilot et
Cursor) :

```markdown
---
name: <Prénom>
slug: <prenom-role>
title: <Titre court>
status: active            # active | paused | retired
launches_cloud_agents: false
last_reviewed: YYYY-MM-DD
---
# <Prénom> — <titre>
## Mission (une phrase)
## Voix
## Boussole de décision
## Livrables
## Règles cloud agent (si applicable)
## Collaboration et routage
## Anti-mandats
## Canon (pointeurs, pas un rituel de lecture)
## Ajustements (une ligne : date — changement — raison — #issue/PR)
```

La section « Ajustements » n'est pas un journal de décisions global. C'est la trace dans le doc
propriétaire, appliquée au rôle.

## 6. Questions ouvertes pour Marc

1. **Revenir sur #787 ?** La méthode dit « do not copy mandates into the repo ». Acceptes-tu que les
   mandats deviennent un canon versionné (P1) ? Ou préfères-tu rester sur P0 + P3 (seul le steering
   durable entre dans le dépôt, le mandat reste dans l'app) ?
2. **Sens de la vérité :** le dépôt vers l'app (on édite en PR, puis on recopie dans Grok Bot) ou
   l'app vers le dépôt (on exporte périodiquement) ? Je recommande le dépôt comme source, l'app comme
   copie.
3. **Visibilité publique :** le dépôt doit passer en open source. Les mandats peuvent-ils être publics,
   ou faut-il un dépôt compagnon privé ?
4. **Pilote :** quels rôles en premier pour P2 (c) ? Je propose Christophe, Patrick et Alex, les seuls
   qui lancent des cloud agents. Et les deux skills jointes doivent-elles entrer dans le dépôt ?
5. **K et la vérification d'écart :** qui est « K » (cité dans 11 mandats, absent du dépôt) et
   doit-il apparaître dans `team-roles.md` ? Qui porte P4 : Mathieu ou toi ?

## Sources (consultées le 2026-10-16)

- [S1] Cursor, *Grok Bot* — https://cursor.com/docs/grok-bot
- [S2] Cursor, *Bot Development Kit (BDK)* — https://cursor.com/docs/bdk
- [S3] Cursor, *Work with Grok Bot* — https://cursor.com/docs/grok-bot/work
- [S4] xAI, *Templates for Grok Bot* — https://x.ai/bot/guides/templates-for-grok-bot
- [S5] xAI, *Grok Bot for Engineering* — https://x.ai/bot/guides/grok-bot-for-engineering
- [S6] xAI, *Grok Bot 101* — https://x.ai/bot/guides/grok-bot-101
- [S7] Cursor, *BDK project layout* / *Agent config* — https://cursor.com/docs/bdk/reference/project-layout ,
  https://cursor.com/docs/bdk/reference/agent-config
- [S8] Cursor, *Export a Grok Bot to a BDK project* — https://cursor.com/docs/bdk/guides/export-grokbot
- [S9] gprot42/grok-bot-tool (communautaire, non officiel) — https://github.com/gprot42/grok-bot-tool
- [S10] METAGROK (tiers), *Back up Grok Bot memory when there is no export feature* —
  https://metagrok.io/how-to/export-grok-bot-memory-backup ; Cursor Forum, *Best way to export memory* —
  https://forum.cursor.com/t/best-way-to-export-memory/170714
- [S11] DeepSeek, *deepseek-harness* — https://github.com/deepseek-ai/deepseek-harness ; *architecture.md* —
  https://github.com/deepseek-ai/deepseek-harness/blob/master/docs/architecture.md
- [S12] DeepSeek, *config-catalog.md* et README `dsh-agent-instructions` —
  https://github.com/deepseek-ai/deepseek-harness/blob/master/docs/config-catalog.md ,
  https://github.com/deepseek-ai/deepseek-harness/tree/master/packages/context/agent-instructions
- [S13] The New Stack, 2026-08-13 — https://thenewstack.io/deepseek-harness-open-source-plugins/ ;
  DataCamp (analyse) — https://www.datacamp.com/blog/what-is-deepseek-harness
- [S14] Anthropic, *Create custom subagents* — https://code.claude.com/docs/en/sub-agents
- [S15] Anthropic, *Explore the .claude directory* — https://code.claude.com/docs/en/claude-directory
- [S16] OpenAI, *Subagents* (Codex) — https://learn.chatgpt.com/docs/agent-configuration/subagents
- [S17] OpenAI, *Build skills* — https://learn.chatgpt.com/docs/build-skills
- [S18] OpenAI, *Custom instructions with AGENTS.md* — https://learn.chatgpt.com/docs/agent-configuration/agents-md
- [S19] OpenAI, *Customization* — https://learn.chatgpt.com/docs/customization/overview
- [S20] OpenAI, *Agent orchestration* (Agents SDK) — https://openai.github.io/openai-agents-python/multi_agent/
- [S21] Google, *Subagents have arrived in Gemini CLI* — https://developers.googleblog.com/subagents-have-arrived-in-gemini-cli/
- [S22] Google, *Agents CLI — ADK* — https://adk.dev/get-started/agents-cli/
- [S23] Gemini CLI, *GEMINI.md* — https://geminicli.com/docs/cli/gemini-md/
- [S24] GitHub, *About custom agents* — https://docs.github.com/en/copilot/concepts/agents/copilot-cli/about-custom-agents
- [S25] Microsoft, *Declarative Agents* (Agent Framework) — https://learn.microsoft.com/en-us/agent-framework/agents/declarative
- [S26] Cursor, *Subagents* — https://cursor.com/docs/subagents ; *Cloud Agents* — https://cursor.com/docs/cloud-agent
- [S27] Cursor, *Agent Skills* — https://cursor.com/docs/skills
- [S28] Cursor, *Rules* — https://cursor.com/docs/rules
- [S29] OpenAI, *OpenAI co-founds Agentic AI Foundation, donates AGENTS.md* — https://openai.com/index/agentic-ai-foundation/
- [S30] Linux Foundation, *Formation of the Agentic AI Foundation* —
  https://www.linuxfoundation.org/press/linux-foundation-announces-the-formation-of-the-agentic-ai-foundation
- [S31] Anomity (tiers), *The Agent Skills Open Standard* — https://anomity.ai/blog/agent-skills-open-standard-adoption-governance/
- [S32] *Agent Plugins Specification 1.0.0* — https://agent-plugins.org/specification
- [S33] Vercel, *The Complete Guide to Agent Plugins* — https://vercel.com/kb/guide/agent-plugins
- [S34] Anthropic, *Plugins overview* — https://code.claude.com/docs/en/plugins/overview
- [S35] Cursor, *Plugins* — https://cursor.com/docs/plugins
- [S36] Anthropic, *Orchestrate teams of Claude Code sessions* — https://code.claude.com/docs/en/agent-teams
- [S37] Linux Foundation, *Agent2Agent Protocol Project* —
  https://www.linuxfoundation.org/press/linux-foundation-launches-the-agent2agent-protocol-project-to-enable-secure-intelligent-communication-between-ai-agents
