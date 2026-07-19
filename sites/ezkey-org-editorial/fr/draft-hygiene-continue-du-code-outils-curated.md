---
status: published
audience: "Développeurs et tech leads intéressés par la qualité de code à l'ère des agents IA ; essai personnel, voix en je, lane « Craft & engineering ». Ton sobre et concret, ancré dans le vécu Ezkey. Structure « essentiel d'abord » puis développement chronologique et conceptuel."
planned_slug_fr: "hygiene-continue-du-code-outils-curated.html"
planned_slug_en: "continuous-code-hygiene-curated-tooling.html"
planned_canonical: "https://ezkey.org/fr/hygiene-continue-du-code-outils-curated.html"
published_html_en: /continuous-code-hygiene-curated-tooling.html
published_html_fr: /fr/hygiene-continue-du-code-outils-curated.html
published_date: 2026-07-18
html_amended_post_publish: false
source_of_truth: html
---

<!-- ezkey-org:exclude-start
## Objectifs éditoriaux

- Raconter la mise en place progressive d'une discipline d'hygiène de code chez Ezkey : des
  séances ponctuelles informelles avec agents vers un outillage « curated » systématique,
  multi-stack, invocable à la demande.
- Faire ressortir les principes durables : valeur persistante de l'analyse statique
  déterministe à l'ère des agents ; orthogonalité des outils choisis ; réduction du bruit vers
  un rapport consolidé actionnable ; « prioriser les basses priorités » ; « quand ça fait mal,
  le faire plus souvent » ; parallèle statique/runtime avec tests unitaires/fonctionnels.
- Ton : pas un tutoriel, pas un changelog. Un récit de cheminement avec les raisonnements.

## Évidence Git et corpus (vérifiée le 2026-07-18)

Chronologie confirmée dans l'historique du dépôt :

- 2025-09-19 `d7c3c971` — introduction de Spotless (formateur) dans le build Maven.
- 2025-11-12 `bae0ca20` — module `checkstyle-config` : Checkstyle validé à chaque build.
- 2026-05-05 `a1aee029` — activation de Dependabot (`.github/dependabot.yml`).
- 2026-05-29 `73a597ea` — « feat: add React Doctor curated pass for Admin UI lint and polish »
  (`ezkey-admin-ui/scripts/doctor-curated.mjs`) : premier curateur, né du besoin Admin UI.
- 2026-06-16 → 06-18 — campagne d'hygiène mobile : React Native 0.86, gesture-handler 3.x,
  worklets, eslint, lint-staged ; memo eslint10/jest30 ; plusieurs itérations, tests sur
  téléphone physique ; `dependency-monitor.mjs` + « hygiene ceremony » (`7567a725`).
- 2026-06-18 → 06-20 — curateur qualité mobile : `code-quality-curator.mjs` (`bc020e64`),
  suppressions, puis `7927baae` « unify orthogonal quality signal pipeline »
  (Biome + Semgrep + Detekt → rapport consolidé unique).
- 2026-07-11 `18abf34d` — `java-doctor-curated` (SpotBugs + Semgrep pack épinglé + PMD étroit) ;
  évaluation `product-docs/global/java-doctor-curated-evaluation-2026-07-11.md` ; pass-1 HITL
  (brut 440 → curé 173 ; lot de 6 ; fix/suppress/skip ; « if it ain't broken, don't fix it »).
- 2026-07-11 `2b71104b` / `8cb4abd5` — Admin UI doctor-curated P1 (a11y, deep-links) et
  P2 quick wins + triage bas-signal.
- 2026-07-12 → 07-14 — security-pentest curated : évaluation d'outils
  (`security-pentest-tool-evaluation-2026-07-12.md`), runner `98cc2e1d`, durcissement `a00b6a51` ;
  charte `product-docs/global/hygiene/security-pentest/OPERATING-CHARTER.md`.
- 2026-07-15 `da6ae9f8` / `dbe505f3` — lane `dependabot-curated` formalisée (lots T1–T4),
  passes 1 et 2 fermées le même jour.
- 2026-07-17 — pentest pass-01 : trouvaille réelle (500 au lieu de 401 sur
  POST /api/v1/admin/enrollments/reset sans en-tête Authorization), promue au backlog
  (`I-2026-07-17-admin-enrollment-reset-missing-authorization-header-500`).

Références canon : `product-docs/global/hygiene/README.md` (les quatre lanes + mobile),
`AGENTS.md` racine (§ doctor-curated, java-doctor-curated, dependabot-curated),
`.cursor/skills/dependabot-curated/SKILL.md`, `ezkey_mobile/scripts/README.md`
(§ quality-pipeline, lane A/B triage).

La méthode Dependabot légère mentionnée par le mainteneur a bien laissé une trace : skill
`dependabot-curated` + notes de campagne sous `product-docs/global/hygiene/dependabot/`.

## Notes de rédaction

- Ne pas surcharger de dates dans le corps ; la chronologie précise reste ici.
- Ne pas présenter le pentest comme une certification : rester dans la posture « évidence
  exécutable locale, campagnes bornées, pas de gate CI » (charte).
- SonarQube : mentionné comme choix différé assumé, sans dénigrement.
ezkey-org:exclude-end -->

# L'hygiène de code en continu : des séances avec des agents aux outils « curated »

## L'essentiel, d'abord

Travailler avec des agents IA sur la qualité du code est une pratique salutaire — je le fais
depuis les débuts d'Ezkey. Mais j'ai fini par me rendre à l'évidence : les outils d'analyse
statique qui existent depuis des années, parfois des décennies, apportent encore une valeur que
l'agent seul ne remplace pas. Ils encodent le savoir collectif de communautés d'experts sous
forme de règles déterministes, reproductibles, sans humeur et sans oubli.

La réponse d'Ezkey a pris la forme d'un patron que j'appelle **curated** : pour chaque stack du
projet — React/TypeScript pour l'Admin UI, Java pour les API, Kotlin et React Native pour le
mobile — un script pilote des outils open source complémentaires, puis ramène leurs observations
vers un rapport filtré et priorisé. Le but n'est pas de tout corriger. Il est de réduire le bruit
jusqu'à ce que l'humain puisse examiner les constats qui méritent réellement son jugement.

Le reste de cet article raconte comment cette discipline s'est mise en place, morceau par
morceau, puis comment elle a débordé du code pour atteindre les dépendances et la sécurité en
exécution.

## Des séances ponctuelles, salutaires mais informelles

Depuis le début du projet, je fais avec différents agents des séances de vérification de la
qualité du code — ponctuelles, plus ou moins informelles. Le raisonnement de départ était
simple : faire ce travail avec un agent a tout son sens. L'agent lit vite, couvre
large, et ne se lasse pas. Au fur et à mesure de l'évolution des modèles et de ma propre
capacité à bien interagir avec eux, ces exercices ont été, somme toute, salutaires : ils ont
fait progresser de façon constante la qualité du code source.

Mais une séance avec un agent reste une conversation. Deux sessions ne regardent pas exactement
les mêmes choses ; ce qui est signalé un jour peut passer inaperçu le lendemain. C'est
précieux comme regard, insuffisant comme discipline.

Une preuve que le déterministe garde sa place existait déjà dans le projet : dès les premiers
mois, j'avais introduit un formateur et surtout Checkstyle pour valider, à chaque build, la
conformité à des règles de standardisation du code. Pas de discussion, pas d'humeur : le build
passe ou ne passe pas. Pendant des semaines, voire des mois, c'est cette mécanique — plus les
séances ponctuelles — qui a porté seule l'hygiène du code.

## Le déclencheur : un développeur back-end devant du TypeScript

L'idée d'aller plus loin est venue d'un manque, pas d'une théorie. Je suis, comme je l'ai
mentionné dans d'autres articles, un développeur back-end avant tout. Les technologies Web ne
sont pas mon terrain d'origine ; les meilleures pratiques fines du TypeScript et des paradigmes
UI ne font pas partie de mes réflexes acquis.

C'est précisément pour pallier ce manque que je me suis tourné vers les outils de type lint
disponibles en open source : des outils portés par des communautés actives de développeurs
qui sont des experts dans leur domaine, et qui apportent un regard piloté par des règles
déterministes sur la qualité du code. Ce savoir collectif, cette expertise distillée en règles,
c'est exactement ce que je voulais intégrer dans la discipline Ezkey — malgré, ou plutôt à cause
de, mon inexpérience sur ce stack en particulier.

La démarche s'est faite en collaboration avec l'agent : une recherche des outils open source
pertinents pour le stack de l'Admin UI, avec un inventaire et une évaluation critique. Une des
observations initiales de cette analyse de marché : **ces outils ne sont pas tous égaux et ne
portent pas tous sur les mêmes qualités**. Certains se concentrent sur le formatage ; d'autres sur
la bonne utilisation des patrons propres au stack ; d'autres encore sur l'accessibilité
ou la robustesse. Si on retient plusieurs candidats, il faut les choisir pour obtenir une
certaine **orthogonalité des concepts validés** — éviter que trois outils creusent avec
redondance dans une seule direction au détriment d'angles conceptuels qui ont aussi leur
importance.

## Le patron « curated » : réduire le bruit, garder le signal

De cette première sélection est né un script, et avec lui le patron qui allait se répéter. Sa
responsabilité : invoquer chacun des outils retenus avec le paramétrage qui lui est propre,
adapté aux besoins d'Ezkey ; produire un rapport individuel par outil ; puis un rapport
**consolidé, filtré, et trié par ordre d'importance**. C'est ce que nous avons appelé un
*curated report* : un rapport dont la fonction est de faire ressortir ce qui est le plus
pertinent sous une forme actionnable.

Car le problème de tous ces outils n'est pas d'en dire trop peu — c'est d'en dire trop. Une
première passe sur le code Java a produit 440 constats bruts. Le filtrage en a retenu 173, puis
la revue en a tiré un premier lot de six éléments réellement dignes d'attention. Ces étages sont
essentiels : le curateur ne remplace pas le jugement, il lui rend le volume praticable.

L'autre moitié du patron est humaine. Une passe « curated » ne se termine pas par un commit
automatique : elle se termine par une revue, constat par constat, où chaque élément reçoit une
décision explicite : **corriger**, **retirer du rapport avec une justification durable**, ou
**passer faute de signal assez clair**. Deux garde-fous se sont imposés à l'usage. D'abord, si un
constat ne peut pas être relié clairement au code source, on ne lui invente pas une explication.
Ensuite, un diagnostic clair n'oblige pas une réécriture : quand le code signalé est une
sur-défense locale inoffensive, le « perfectionner » coûterait des tests et des changements pour
un gain produit nul. Si ce n'est pas cassé, on ne le répare pas. L'hygiène de code n'est pas une
chasse aux sorcières.

## Le signal comme occasion d'apprendre

Les signaux forts ont fini par remplir une seconde fonction. Ce sont des points de décision sur
des éléments potentiellement actionnables, mais aussi des occasions de formation continue.
Avant de décider, je demande à l'agent de m'expliquer ce que l'outil a vu : la règle concernée,
la pratique sous-jacente, le standard dont le code s'écarte, et l'importance réelle du concept.
La discussion ne sert donc pas seulement à choisir entre corriger, supprimer ou passer. Elle
sert à comprendre ce que cette décision engage.

Ce détour est d'autant plus important que, dans Ezkey, la quasi-totalité du code est produite par
l'agent. Il m'arrive encore d'effectuer directement quelques modifications, mais elles restent
l'exception. Dans cette réalité, il devient facile de rester proche de l'intention générale tout
en s'éloignant du mécanisme précis qui l'implémente. Un rapport qui fait ressortir une dérive par
rapport à une pratique établie devient alors un point de reconnexion : avant de modifier le code,
je reviens au concept, à sa traduction dans l'implémentation et aux raisons pour lesquelles la
règle existe.

Du code généré par un agent ne cesse pas pour autant d'être du code source. Il demeure une
ressource durable et précieuse, qu'il faut entretenir avec soin. Déléguer son écriture ne revient
pas à déléguer sa maîtrise ni la responsabilité de le comprendre. La passe
« curated » devient ainsi à la fois une boucle d'hygiène et une boucle d'apprentissage : elle
améliore le code tout en maintenant ma capacité à raisonner sur les concepts qu'il matérialise.

## Prioriser les basses priorités

Il y a un principe que j'ai introduit dans cette discipline et qui peut sembler contradictoire :
on ne doit pas chercher à traiter uniquement les priorités les plus élevées.

Traiter d'abord les signaux les plus importants est une évidence. Mais j'aime la pondérer avec un
principe complémentaire que j'appellerais **prioriser les basses priorités**. Les petits éléments
améliorables constituent un désordre que l'on tolère dans la base de code. Si on ne leur fait
jamais de place, ils s'accumulent jusqu'au point où le retard devient difficile à rattraper.

En pratique, cela veut dire que pour chaque période — jours, semaines, sprints, peu importe la
mesure — on cherche à identifier les éléments jugés peu prioritaires mais à faible risque : des
*quick wins* qu'on peut insérer avec un effort modeste, en dose raisonnable, après les éléments
à haut signal. Ne faire que de l'urgent est une forme subtile de « je ne fais qu'éteindre des
feux ». Certains petits signaux sont des canaris : ignorés assez longtemps, ils deviennent des
problèmes réels. Il faut leur réserver une place sans tomber dans un perfectionnisme qui dépense
trop d'énergie pour peu de valeur.

## Un patron qui se généralise : Java, puis mobile

Après quelques passes sur l'Admin UI, le constat s'imposait : rien de tout cela n'est
spécifique au stack Web. Le même cycle — recherche d'outils, sélection orthogonale, scripting,
rapport consolidé, triage, priorisation — s'applique à n'importe quelle composante du projet.

Pour le code Java, la même évaluation critique du marché a retenu une combinaison volontairement
orthogonale : SpotBugs pour les bogues visibles dans le bytecode, Semgrep avec un pack épinglé
orienté sécurité, et PMD avec un jeu étroit de règles de conception et de maintenabilité. Trois
angles différents, un seul rapport consolidé. Fait révélateur : SonarQube,
qui est la référence de la catégorie, a été sérieusement considéré — et délibérément écarté pour
le moment. SonarQube est un monde en soi : un déployable, avec ses API, son UI, sa base de
données. C'est un outil important qui viendra peut-être un jour. Mais pour des invocations
ponctuelles qui identifient dès maintenant des candidats d'amélioration, sans infrastructure à
opérer, la combinaison légère l'a emporté.

Pour l'application mobile, même principe, adapté au terrain : un curateur qui normalise les
signaux de trois sources complémentaires — Biome pour le TypeScript React Native, Semgrep pour
les motifs sensibles du mobile comme le stockage, la journalisation et la cryptographie, et
Detekt pour le Kotlin natif Android — vers un modèle unique et un rapport priorisé.
Le commit qui a scellé cette étape porte un nom qui résume bien l'intention : *unify orthogonal
quality signal pipeline*.

Trois stacks, trois niveaux de familiarité personnelle — le Java que je maîtrise, le TypeScript
que j'apprivoise, le Kotlin que je connais peu — et une seule discipline. C'est peut-être le
point le plus important : ce patron fonctionne indépendamment de mon niveau d'expertise sur le
stack, précisément parce qu'il fait porter l'expertise par les règles des communautés et
l'arbitrage par le contexte du projet.

## Dependabot : quand ça fait mal, le faire plus souvent

Une pratique voisine a renforcé cette réflexion : l'activation de Dependabot. Il ne s'agit pas
d'un pipeline d'analyse statique, mais la posture est la même — réduire un flux bruyant en unités
de décision praticables. J'ai rapidement vu que cela générait une activité essentiellement
hebdomadaire : tous les projets dont
on dépend sont en mouvement, et les pull requests de mise à jour arrivent par vagues.

Il y a un principe en informatique que j'aime bien répéter : **quand ça fait mal, il faut le
faire plus souvent**. Les activités qu'on fait rarement, on se sent moins sûr de les faire. Ce
qu'on ne pratique pas, on a tendance à moins se faire confiance pour le faire — et si la
procédure n'est pas formelle, chaque application risque ses petits oublis. C'est de l'insécurité
et du risque accumulés. La bonne réponse n'est pas de remettre à plus tard en craignant l'erreur :
c'est de le faire plus souvent, de le faire mieux, de le maîtriser, et de se doter des outils
nécessaires pour y arriver.

Au début, je traitais les pull requests de Dependabot une par une. La gymnastique — vérifier,
fusionner, valider — répétée pour chaque groupe de deux, trois ou dix PR, était réellement
chronophage. L'évolution de méthode a été de donner à l'agent des instructions pour
répertorier les mises à jour en attente, les **regrouper en lots par affinité de composante et
par niveau de risque**, et proposer un traitement par lot. Trois mises à jour de correctif
compatibles SemVer sur des bibliothèques à faible risque de casse ? On les examine comme un même
lot, on fusionne chaque PR individuellement, puis on mène une mini-campagne de tests
**d'ampleur proportionnelle au risque**. Les mises à jour majeures ou perturbatrices, elles, sont
différées explicitement, avec une trace du pourquoi.

Cette mécanique supporte exactement le souhait d'amélioration continue : ne pas accumuler de
retard sur les dépendances, tout en équilibrant l'effort d'une tâche routinière qui revient
systématiquement. Le mois dernier, l'application mobile en a fourni la démonstration a
contrario : une remise à niveau attendue trop longtemps — React Native, les packages de vision,
les alignements de versions obligatoires entre composantes — a demandé plusieurs itérations,
avec les tests unitaires et sur téléphone physique que cela impose. C'était le signal clair
qu'il fallait encadrer la pratique. Maintenant que c'est fait, il suffit de garder le rythme.
Garder le rythme : tout est là.

## Au-delà du code : la sécurité en évidence exécutable

Plus récemment, je me suis dit que le même principe pouvait s'appliquer à la sécurisation de
l'application. Ezkey étant une plateforme MFA, la sécurité est au premier plan. J'ai donc
entamé la même démarche — sélection d'outils, scripting, rapport consolidé et priorisé — pour un
volet *pen test*. Ce n'est plus exactement de l'hygiène de code, mais une extension de la même
discipline de curation vers l'application en mouvement. Cette fois, les outils retenus travaillent
sur le **runtime**. La cible n'est pas le texte du code, mais une instance Docker complète,
lancée par un *clean start* dans une posture proche de la production : le code réel, avec ses
en-têtes, ses contrats d'API, ses logs, attaqué pour en faire sortir des lacunes fondées sur une
évidence exécutable.

Le point de comparaison qui m'est venu à l'esprit : les scripts d'analyse statique sont un peu
comme des tests unitaires — un regard sur le code à froid, hors exécution. Les campagnes de
sécurité runtime, elles, sont l'analogue des tests fonctionnels qui exercent la pile complète
après un clean start. Deux régimes complémentaires, comme dans toute stratégie de test : l'un ne
remplace pas l'autre.

La sélection d'outils a suivi la même logique d'orthogonalité : un fuzzer piloté par les
spécifications OpenAPI, qui excelle à trouver les classes d'erreurs que les scanners génériques
manquent ; un scanner DAST établi pour les en-têtes et les configurations, en mode passif
d'abord ; et un moteur de templates utilisé à contre-courant de son usage habituel — pas de
larges packs communautaires de CVE, mais des templates maison, étroits et déterministes, qui
capturent les invariants de sécurité propres au projet. Et une règle d'acceptation stricte :
aucune conclusion de sécurité n'est retenue si elle n'est pas reproduite par l'outillage
déterministe ou par une reproduction manuelle documentée. Les hypothèses restent des hypothèses.

La première passe a immédiatement rapporté : un endpoint d'administration qui répondait 500 au
lieu de 401 quand l'en-tête d'autorisation manquait — sémantiquement faux, opérationnellement
bruyant, reproduit par rejeu HTTP direct avant d'être promu au backlog avec sa trace complète.
Exactement le genre de signal qu'on attend de cette lane : petit, précis, exécutable, corrigible.

## Un choix assumé : ponctuel, pas gate CI

Il s'agit d'un choix intentionnel de ne pas intégrer ces outils à un système de build continu.
La posture actuelle du projet reste largement centrée sur les validations lancées depuis un poste
de développement, même si une GitHub Action ciblée exécute déjà les validations JavaScript et
Android du mobile. Il n'existe pas encore de système de release formel, et les outils « curated »
ne sont pas des gates CI. Ils se prêtent à l'évaluation ponctuelle : invocables à la demande,
sans infrastructure permanente à opérer.

Cette posture n'est pas une position de principe contre l'automatisation — le formateur et
Checkstyle tournent, eux, à chaque build, parce que la conformité aux règles de base doit être
non négociable. C'est une position d'adéquation : chaque outil au niveau d'intégration qui
correspond à sa fonction et à la réalité du projet, aujourd'hui. Le jour où une chaîne de
release formelle existera, la question se reposera — et certains de ces scripts y trouveront
naturellement leur place.

## Ce que tout cela forme ensemble

Rétrospectivement, le fil est cohérent : des séances d'agents informelles ; un formateur et
Checkstyle à chaque build ; un premier curateur né d'un manque d'expertise assumé sur le stack
Web ; le même patron généralisé au Java puis au mobile ; une méthode légère pour absorber le
flux hebdomadaire des dépendances ; et l'extension du principe au runtime pour la sécurité.

Ce qui compte n'est pas de multiplier les outils ni d'éliminer chaque avertissement. C'est de
construire une boucle assez légère pour revenir régulièrement, assez stricte pour ne pas inventer
des problèmes, assez formatrice pour entretenir la compréhension du code, et assez humaine pour
distinguer une amélioration réelle d'une chasse à la perfection.

Cette discipline fonctionne autant sur le Java que je maîtrise que sur le Kotlin ou le TypeScript
où j'ai davantage à apprendre. L'expertise vit dans les règles des communautés ; la discipline
vit dans la méthode ; le jugement reste humain.
