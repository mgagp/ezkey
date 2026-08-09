---
status: draft
audience: "Développeurs expérimentés, praticiens de méthodologie IA, lecteurs des essais précédents sur Ezkey ; essai personnel, voix en je, lane « Craft & engineering ». Registre sobre et factuel : le récit d'une ablation réellement exécutée, pas une réflexion en suspens. L'essentiel d'abord, puis le chemin qui y mène."
planned_slug_fr: "ablation-methodologie-mode-plan.html"
planned_slug_en: "ablation-my-own-methodology.html"
planned_canonical: "https://ezkey.org/fr/ablation-methodologie-mode-plan.html"
---

<!-- ezkey-org:exclude-start
Editorial context (not publishable):
- Replaces rejected-draft-ablation-methodologie-mode-plan.md (rejected draft + two critique rounds preserved in that file).
- Factual sources: commit a9d65cc4 on chore/methodology-radical-ablation (105 files changed, 9,761 deletions),
  product-docs/methodology/release-notes/2026-08-08-ablation-record.md, condensed product-docs/methodology/README.md,
  working plan "Ablation radicale méthodologie" (ee459493).
- Editorial mandate: ~50% shorter than the rejected draft; single thread = the lived experiment; values assumed;
  honesty shown by facts, never declared repeatedly; few em-dashes; varied paragraph lengths.
ezkey-org:exclude-end -->

# Le principe d'ablation, ou ma méthodologie devant le miroir

## L'essentiel, d'abord

Le 8 août 2026, j'ai supprimé 93 % de la méthodologie que j'avais construite pour Ezkey. Un seul commit : 105 fichiers, près de 9 800 lignes retranchées. Vingt et un documents de méthode réduits à un seul. Trente-deux notes de décision : zéro. Vingt gabarits : quatre. Quatorze skills d'agent : aucun. Ce qui pesait environ 10 500 lignes tient désormais en 650.

Ce n'est pas un accès de rage. C'est l'application, tardive et littérale, d'un principe que je trouvais brillant tant qu'il s'appliquait aux autres. Cet article raconte le chemin : un principe entendu, un article raté que vous ne lirez jamais, une critique qui ne m'a rien laissé passer, puis le geste.

## Le principe d'ablation

J'ai entendu Boris Cherny, le créateur de Claude Code, raconter comment son équipe traite chaque nouvelle génération de modèles : ils suppriment l'entièreté du system prompt du produit, puis le remontent ligne par ligne pour voir ce qui reste réellement utile. Avec Opus 5, plus de 80 % n'est jamais revenu. La plupart de ce qui restait compensait des défauts qu'un modèle plus intelligent ne fait simplement plus. Son conseil aux utilisateurs de son propre outil, et je le rapporte tel que je l'ai compris : tous les six mois, supprimez vos directives, vos skills, vos automatisations, et regardez ce que le modèle fait sans eux.

L'idée est simple et devient inconfortable dès qu'on la prend au sérieux. Les directives qu'on donne à un modèle sont souvent des béquilles posées pour une génération précédente. Quand le modèle change, la béquille devient du bruit, parfois une entrave. On continue de lui expliquer comment marcher alors qu'il court déjà.

Or, voilà quelques mois, j'avais construit pour Ezkey une méthodologie complète : des niveaux documentaires, des templates, des skills, des règles de nommage. Je l'avais présentée au moment où [j'endossais le chapeau du méthodologiste](/fr/du-savoir-au-vecu-le-moment-methodologiste.html), en prenant soin de dire que je voulais éviter le piège des grandes méthodologies d'hier : légère, optionnelle, choisie délibérément. Le principe d'ablation, retourné contre moi, posait une question que je n'avais pas envie d'entendre.

## L'article que je n'ai pas publié

Ma première réponse a été d'écrire un article. Celui que vous lisez porte son nom ; ce n'est pas le même texte.

La version d'origine faisait tout ce qu'un essai d'autocritique sait faire pour éviter l'autocritique. Elle convoquait le miroir avec application, annonçait un examen sévère, puis chaque aveu se retournait en vertu au paragraphe suivant. Ma lenteur à adopter les nouvelles pratiques y devenait une stratégie. Mon incapacité à faire respecter mes propres règles y devenait une souplesse salvatrice. Et surtout, dans un texte entièrement consacré au principe « supprimer, puis regarder », je ne supprimais rien. Les moments où je croyais appliquer l'ablation étaient des abstentions : ne pas ajouter une contrainte, ne pas ouvrir un dossier. Ne pas ajouter n'est pas retrancher.

J'ai soumis ce texte à la critique, et la critique venait de l'IA elle-même. J'ai déjà écrit que travailler avec un modèle est une façon très sophistiquée de se parler à soi-même, et que le risque de chambre d'écho est réel. Cette fois, l'écho n'est pas revenu. La critique a nommé, noir sur blanc, ce qu'une petite voix me disait depuis un moment sans que je l'écoute : pas un chiffre, pas une scène, pas une date. Un verdict annoncé et jamais livré. Un procès conclu par un acquittement rapide. Un préambule renouvelable à l'infini.

Une fois la défense tombée, le constat était simple. J'avais présenté comme légère une méthodologie qui comptait des dizaines de documents de cadrage, une vingtaine de templates, une quinzaine de skills. J'avais prôné de laisser les modèles évoluer librement, et j'avais figé autour d'eux des workflows entiers que je ne pratiquais moi-même qu'à moitié. Mes meilleurs garde-fous, ceux qui mettaient en garde contre la cérémonie, dormaient au fond du corpus dans des notes de décision qu'aucun agent partant à froid n'avait de raison de lire. Un corpus assez épais pour perdre ses propres meilleures règles est un corpus trop épais. Je n'avais pas été honnête avec moi-même, et l'article raté en était la démonstration écrite.

## Supprimer, puis regarder

Alors je l'ai fait, littéralement. Un plan court, une session, un commit.

Sont partis : les trente-deux notes de décision datées, dont l'historique Git reste l'archive ; vingt des vingt et un documents de méthode, du guide de démarrage de session aux séquences d'états par workflow ; les quatorze skills qui orchestraient la méthode ; seize des vingt gabarits ; la vue HTML riche de près de 1 500 lignes. Rien n'a été déplacé vers un dossier d'archives. Un dossier d'archives, c'est une façon de supprimer sans regarder.

Ce qui a été jugé réellement porteur n'a pas été déplacé, il a été condensé : un document unique d'environ 230 lignes. La taxonomie des lanes, les diagrammes de séquence, les checklists d'entrée et de sortie n'ont été repliés nulle part. C'est exactement la classe de structure qu'une bonne session de mode plan fournit déjà, mieux que moi, et en s'améliorant à chaque version.

Le site public de la méthodologie suit le même mouvement : il est refondu et drastiquement simplifié lui aussi, conséquence directe de l'ablation. Les parcours guidés construits pour l'ancien corpus n'ont plus de matière à parcourir ; ce qui les remplace tient, comme le reste, en très peu de pages. La suppression est venue d'abord, la republication suit de près — dans l'ordre inverse, l'ablation serait devenue une réorganisation, et une réorganisation est encore une façon de ne rien supprimer.

## Ce qui a survécu

L'ablation n'était pas un verdict global ; c'était un tri. Il tient en une phrase : le mode plan d'abord, la méthodologie en support, réduite à ce qu'il ne porte pas.

Le mode plan des grands fournisseurs s'améliore à chaque version, nourri par une échelle de rétroaction qu'aucun projet de cette taille ne peut financer. Le concurrencer avec des workflows maison, c'est se couper d'une amélioration continue gratuite. Ce qu'il ne porte pas, en revanche, c'est la continuité : une session ouverte à froid n'a aucun moyen de savoir ce qu'une session d'il y a trois mois a décidé, sauf si le corpus le porte.

Trois choses ont donc survécu. Les niveaux documentaires, qui distinguent une vision produit d'une idée concrète et d'un design borné prêt à implémenter. La découvrabilité bidirectionnelle, qui permet de remonter d'une ligne de code jusqu'à l'intention qui l'a motivée, et inversement. Et la boussole des valeurs propre au projet : la règle du quatre-vingt-vingt, la complexité essentielle distinguée de l'accidentelle, la rigueur proportionnée à l'enjeu. Une boussole n'indique pas le chemin exact ; elle donne une direction quand aucune règle précise ne s'applique.

S'y ajoutent trois règles repêchées des profondeurs, précisément celles que le corpus avait enterrées : distinguer l'hygiène courante d'un vrai programme avant de matérialiser des artefacts ; traiter un plan de travail comme un échafaudage éphémère, sauf s'il porte encore quelque chose que le canon ne doit pas aplatir ; tenir pour close une incertitude déjà adressée et consignée, sans réouverture sans signal nouveau. Ces règles existaient déjà. Il a fallu tout supprimer autour d'elles pour qu'elles redeviennent lisibles.

Reste la question de la mémoire, puisque les fournisseurs commencent à consolider celle de leurs agents à travers les sessions. La distinction que je maintiens : cette mémoire-là apprend comment je travaille ; le corpus retient ce que je construis, et pourquoi. Une intention structurée en niveaux, reliée dans les deux sens, n'est pas un ensemble de préférences de travail. Je m'attends à ce que les deux couches finissent par se compléter, et le jour où la première absorbera la seconde, elle passera au même tamis que le reste.

## Le test du temps

Le protocole d'observation est en place, celui-là même qui manquait à l'article raté : travailler normalement, noter ce qu'un agent à froid rate visiblement de ce que le corpus supprimé portait, et ne réintégrer que ce qui manque pour vrai. Une ligne dans le document unique, jamais un nouveau fichier.

Je ne sais pas ce que l'observation donnera. Des morceaux manqueront peut-être et reviendront. Une méthodologie open source plus mûre couvrira peut-être un jour mieux que la mienne ce que j'essaie de faire, et le bon réflexe sera alors de m'en approcher plutôt que de m'accrocher : l'ablation appliquée, cette fois, à une méthodologie entière. Les modèles frontières continueront d'évoluer plus vite que mes habitudes, et il faudra recommencer. Je ferai des bons coups et des erreurs ; c'est un projet en évolution, et c'est l'avenir qui tranchera.

J'ai défendu ailleurs [la place de l'esprit critique](/fr/pensee-critique-chambres-echo-et-alignement-idees-ia.html) dans le travail avec l'IA, contre le confort des chambres d'écho. J'en tire aujourd'hui la version que cette expérience m'a coûtée : l'esprit critique, ce n'est pas convoquer le miroir. C'est s'y regarder, et agir sur ce qu'on y voit. Il m'aura fallu un article raté, une critique sans complaisance et 9 800 lignes supprimées pour apprendre la différence entre les deux.
