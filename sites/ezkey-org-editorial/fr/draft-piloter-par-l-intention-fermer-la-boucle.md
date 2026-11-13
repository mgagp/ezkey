---
audience: "Développeurs et leads qui suivent l’évolution des agents IA ; retour d’expérience personnel, voix en je, lane « Craft & engineering ». Ton sobre, court (lecture ~3–4 min), ancré dans le vécu Ezkey. Outils nommés comme faits situés (Cursor, Grok Bot, Docker), sans pitch produit. Voix québécoise naturelle (registre de Marc en conversation), sans joual ni caricature."
planned_slug_fr: "piloter-par-l-intention-fermer-la-boucle.html"
planned_slug_en: "steering-by-intention-closing-the-loop.html"
planned_canonical: "https://ezkey.org/fr/steering-by-intention-closing-the-loop.html"
published_html_en: /steering-by-intention-closing-the-loop.html
published_html_fr: /fr/steering-by-intention-closing-the-loop.html
published_date: 2026-09-20
html_amended_post_publish: true
source_of_truth: html
status: published
---

<!-- ezkey-org:exclude-start
Editorial context (not publishable):
- Lane: Craft & engineering. Short field note grounded in Ezkey, not a market essay and not a tutorial.
- New thesis: the meaningful shift is not wonder at collaborators, but a real elevation of capability through delegation plus the discovery that a multi-agent workflow only becomes reliable when validation and definition of done are explicit.
- Core lived sequence: specialized bots with memory + separate execution environments, concretely Docker containers + phone/desktop pilotage by intention while travelling + late real functional check on the workstation.
- Central incident: the main task was the intentional introduction of Ezkey's opt-in base mode, which removes some non-core cryptographic guardrails; validation stalled at a vague "ça a l'air correct" because completion criteria were not observable enough.
- Main lesson: knowing tests matter is not enough. Prior published ideas on tests, intention, project values, and ablation still had to be made operational inside the workflow itself.
- Constraint on tone: sober, self-critical, no "I saw the future" register, no software-history detour, no industry grand theory.
- Naming: Cursor / Grok Bot / Docker are situated facts. Claude delegation can be named only as a brief enabling signal for separate virtual environments, not as product analysis.
- Keep Docker brief. The important point is not ports; it is that separated execution contexts made higher-level delegation credible.
- Cross-links now materialized in body: l-intention-prochaine-frontiere, hygiene-continue-du-code, retour-aux-sources-developpeur-ere-ia, ablation-methodologie.
- Length target: ~700–900 words FR.
ezkey-org:exclude-end -->

# Piloter par l’intention, fermer la boucle

## L’essentiel, d’abord

Sous Ezkey, j’ai récemment vécu une vraie élévation de capacité. Pas seulement parce que les bots sont meilleurs, mais parce que la délégation est devenue concrète : plusieurs collaborateurs spécialisés, chacun avec sa mémoire, son contexte et son propre environnement de travail. Pour la première fois, j’ai pu piloter l’essentiel d’une tâche surtout par l’intention, même en déplacement, depuis le téléphone plutôt que devant mes worktrees habituels.

Mais cette montée d’un cran a montré son point faible tout de suite. Un système multi-agents peut très bien analyser, répartir, exécuter et même faire une passe d’assurance qualité sans pour autant savoir conclure correctement. Tant que ce que veut dire « complété » n’est pas explicite, observable et imposé dans le cycle, le travail peut avoir l’air bon sans être vraiment fermé.

## Une élévation réelle, pas juste une impression

Mon mode normal, jusqu’ici, restait très lié au poste de travail : un ou plusieurs worktrees, l’IDE ouvert, des allers-retours constants, puis un rôle d’orchestrateur encore assez proche du pair programming classique. Ce qui a changé récemment, c’est que j’ai décidé d’aller au bout de l’expérience avec mon équipe de bots.

L’un des éléments qui a rendu ça crédible, c’est le fait que chaque bot disposait de son propre contexte et de son propre environnement d’exécution, concrètement des conteneurs Docker séparés. Là, la délégation cesse d’être une métaphore. Ce n’est plus simplement une longue conversation bien organisée. Le travail peut vraiment se séparer, avancer en parallèle, puis revenir vers un coordonnateur qui garde le cap général.

J’étais en déplacement quand j’ai lancé cette expérience. Une bonne partie du suivi s’est faite depuis l’application mobile, avec Grok Bot ouvert aussi sur mon poste. J’ai piloté le développement à très haute dose par l’intention, beaucoup plus que d’habitude. Le vrai test fonctionnel sur mon PC, je ne l’ai fait qu’à la toute fin. Pour moi, c’était un vrai changement de niveau dans la manière d’interagir avec le travail logiciel.

## La limite s’est révélée exactement où il fallait

Sur le chantier principal, tout semblait pourtant bien se dérouler. Il s’agissait d’introduire de façon intentionnelle le mode base d’Ezkey, une option opt-in où l’on n’introduit pas systématiquement de clés cryptographiques, où le heartbeat est désactivé et où l’intégrité cryptographique des checkpoints est coupée. L’analyse s’est faite, la répartition du travail s’est faite, les bots concernés ont produit ce qu’ils avaient à produire, et une passe d’assurance qualité a même été faite.

C’est précisément là que le vrai problème est apparu.

Au moment de valider, le contrôle s’arrêtait à peu près à : ça a l’air correct. Or une fonctionnalité qui retire des garde-fous cryptographiques fait partie des cas où cette impression devient traîtresse. Le manque était plus profond : je n’avais pas suffisamment défini ce qu’il fallait observer pour pouvoir déclarer, de manière crédible, que le travail était réellement terminé.

Autrement dit, le système savait avancer. Il ne savait pas encore fermer la boucle.

## Savoir ne suffit pas

Ce qui rend cette expérience intéressante à mes yeux, c’est que je ne pars pas de zéro sur ces sujets-là. J’ai déjà écrit sur [l’importance des tests](/fr/strategie-de-test.html). J’ai déjà insisté, dans [mon retour aux sources](/fr/retour-aux-sources-developpeur-ere-ia.html), sur le fait qu’il ne suffit pas d’avoir « des tests », mais qu’il faut savoir lesquels exécuter, à quel niveau, et ce qu’ils doivent vraiment vérifier. J’ai déjà écrit aussi sur [le passage du code aux spécifications, puis des spécifications à l’intention](/fr/from-code-to-intent-ai-workflow.html), justement pour donner aux modèles en évolution une boussole plus haute que le simple code immédiat.

J’ai même déjà soutenu, dans [Rendre ses ailes à l’IA](/fr/l-intention-prochaine-frontiere-partenariat-humain-ia.html), qu’il ne fallait pas couper les ailes probabilistes du moteur. Autrement dit : éviter le micromanagement, garder des voies ouvertes, et fournir plutôt des valeurs de projet qui aident à décider intelligemment.

Tout ça, je le savais déjà. Et pourtant, dans ce nouveau mode de travail distribué, ce savoir ne s’est pas traduit tout seul en comportement fiable.

La leçon est là. Connaître les principes n’est pas la même chose que les avoir rendus opérables dans le système lui-même. Une boussole aide à choisir une direction. Elle ne remplace pas des critères observables qui permettent de conclure que la destination a bien été atteinte.

## La correction n’était pas plus de contrôle, mais un meilleur cadre

La bonne lecture, c’était plutôt : qu’est-ce qui manque dans le système pour qu’un travail distribué puisse bien se terminer sans mes relances successives ?

J’ai donc fait une petite rétrospective avec mon agent coordonnateur, mon bras droit générique. Ensemble, on a regardé ce qui avait manqué : une définition de « terminé » plus explicite, des attentes de validation plus observables, et une manière plus ferme d’imposer ces garde-fous dans le cycle normal du travail. La correction a ensuite été réinjectée à deux endroits : dans le corpus documentaire, pour poser la règle, et dans la description de tâche des bots concernés, pour en faire un réflexe de fonctionnement plutôt qu’un rappel improvisé.

Cette première correction touchait la profondeur : pouvoir dire que c’est terminé parce qu’on l’a observé, pas parce que ça avait l’air correct. Le même chantier, une fois testé pour de vrai, a montré l’autre facette. Le mode base coupe des traitements. Une rotation de clés qui ne peut plus aboutir doit alors être refusée par l’API, et l’interface ne doit pas continuer à la proposer. Tant que le processus, la surface d’API et l’écran ne tiennent pas ensemble, le travail n’est pas complet. Une software factory devient plus autonome quand elle porte ce genre de cohérence, le rayon d’impact d’un choix, jusque dans ses propres règles.

Ça, pour moi, c’est peut-être le point le plus intéressant de toute l’expérience. La software factory agentique n’est pas seulement une équipe de bots qui se parlent. Elle commence vraiment à devenir sérieuse quand le système de travail apprend sur lui-même et se corrige à même ses propres règles.

## Encore préliminaire, mais déjà instructif

Je ne veux pas sur-vendre cette expérience. C’est encore récent. Je ne prétends pas avoir trouvé une formule achevée. Mais il y a déjà quelque chose de très clair pour moi.

Oui, j’ai vécu une élévation réelle de capacité : plus de délégation, plus de séparation du travail, plus de pilotage par l’intention, et même un suivi crédible à distance depuis le téléphone. Mais cette montée rend aussi les faiblesses du système plus visibles. Plus on monte en abstraction, plus la fermeture de boucle doit être explicite.

Si je devais résumer la leçon en une ligne, ce ne serait donc pas : les bots collaborent maintenant. Ce serait plutôt : un travail piloté par l’intention ne devient fiable qu’à partir du moment où l’on formalise ce que veut dire terminé.
