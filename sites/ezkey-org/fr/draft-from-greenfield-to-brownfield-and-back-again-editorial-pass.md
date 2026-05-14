---
status: draft
audience: "Développeurs expérimentés, architectes pragmatiques, leads techniques ; retour d'expérience personnel sur la découvrabilité documentaire et la collaboration avec l'IA."
planned_slug_fr: "de-greenfield-a-brownfield-puis-retour-au-vert.html"
planned_canonical: "https://ezkey.org/fr/de-greenfield-a-brownfield-puis-retour-au-vert.html"
---

# De greenfield à brownfield, puis retour au vert

<!-- ezkey-org:exclude-start
Copie de travail pour passe éditoriale.
Le but est de garder le même squelette que le draft source afin de rendre le diff facile à lire.

Source de comparaison :
- draft-from-greenfield-to-brownfield-and-back-again.md

Principes de cette passe :
- garder la voix personnelle ;
- conserver les mêmes sections ;
- augmenter le punch par reformulation locale ;
- éviter les ajouts structurels majeurs.
ezkey-org:exclude-end -->

## Le départ : vitesse maximale, structure minimale

Quand j'ai commencé Ezkey, j'étais dans une posture que j'assume très bien avec le recul : greenfield total, trunk-based massif, recherche de vélocité maximale, structure ultra légère, et confiance élevée dans ma capacité à porter l'ensemble conceptuel du projet à bout de bras.

Cette approche n'était pas absurde. Elle capitalisait sur mon expérience, sur des réflexes d'architecture que je croyais assez solides, sur la dictée pour verbaliser vite, et sur le fait qu'en solo je pouvais encore maintenir une intégrité conceptuelle implicite. L'IA m'aidait énormément. Je jouais l'orchestrateur, l'assembleur, le directeur. Elle cherchait, résumait, proposait, complétait, exécutait vite.

Au début, cette formule fonctionnait très bien. C'est précisément ce qui la rendait dangereuse.

Une approche légère qui fonctionne assez bien peut cacher le moment exact où elle cesse d'être suffisante.

## L'agent suivait. Mais il fallait toujours le recadrer

À mesure que les modèles progressaient, j'ai pu élever le jeu. J'en ai déjà parlé ailleurs : on passe de fragments de code à de vraies séquences de pair architecture, pair design, pair programming, pair testing. L'agent ne fait plus seulement du code. Il aide à penser.

Mais un symptôme très concret est apparu : je devais lui répéter les mêmes principes de conception, encore et encore.

Je devais rappeler des frontières. Réexpliquer des conventions. Réinjecter des valeurs de design. Réinsister sur des choix d'architecture. Redonner des contraintes qui, à mes yeux, auraient déjà dû être visibles. L'agent s'en sortait étonnamment bien malgré cela. Il grepait, cherchait, retrouvait des bribes d'information, naviguait dans les plans accumulés. Mais il lui manquait une suite conceptuelle.

Et ce manque n'était pas vraiment un problème de modèle. C'était un problème de découvrabilité.

## Mon vrai problème n'était pas la traçabilité. C'était la découvrabilité

Pendant un bon moment, j'ai accumulé des plans. Certains étaient à jour. D'autres étaient déjà partiellement désuets. Certains étaient archivés proprement. D'autres vivaient dans plusieurs répertoires. Le tout formait une masse d'information encore exploitable pour moi, parce que j'en portais mentalement le sens global.

Mon agent de codage arrivait souvent à s'y retrouver. C'est d'ailleurs l'un des aspects impressionnants des outils actuels : ils savent fouiller un dépôt désordonné et en tirer des éléments utiles. Mais cette capacité a une limite. Elle ne crée pas magiquement la chaîne de compréhension.

Ce qui me manquait, ce n'était pas seulement la possibilité de retrouver un document. Ce qui me manquait, c'était une structure qui aide l'agent, et l'humain avec lui, à savoir quel fil tirer selon le besoin.

En d'autres mots : il me manquait un fil d'Ariane.

## La convergence conceptuelle a besoin d'un support

Le constat qui s'est imposé à moi est simple : la méthodologie qu'on met en place ne doit pas seulement produire des artefacts. Elle doit servir un objectif devenu central avec l'IA : permettre une convergence conceptuelle entre l'humain et l'agent de codage.

Si je demande de l'aide sur une stratégie produit, un découpage d'architecture, la conception d'un module, une contrainte de performance, une décision de mapping ou un flux fonctionnel, l'agent devrait pouvoir partir de la racine du projet, suivre des références explicites, comprendre les niveaux de décision, puis descendre progressivement vers le détail utile.

Cela veut dire que le chaînage doit être pensé. Avoir un PRD, un README et un document d'endpoints ne suffit pas si rien n'indique clairement où vivent les décisions plus spécifiques, les contraintes locales, les exceptions, les relations entre modules, les choix de conception ou les conventions de qualité.

À partir d'un certain seuil de complexité, si la documentation ne supporte pas cette convergence des idées, alors l'humain et l'IA restent dans un dialogue incomplet. On peut avancer, oui. Mais on réexplique trop. On corrige trop. On affine en deux ou trois passes là où une meilleure structure aurait dû permettre une compréhension plus directe.

## Une méthodologie ne doit pas vivre dans les nuages

J'ai aussi compris autre chose : une méthodologie qui vit à part, comme une abstraction suspendue au-dessus du projet, finit presque toujours par échouer.

On a déjà vu cela sous plusieurs formes. Des structures méthodologiques pensées comme des univers autonomes. Des arborescences externes. Des systèmes qui prétendent avoir tout prévu. Des modèles tellement génériques qu'ils deviennent vite étrangers au vrai produit. Tout cela peut rassurer sur le papier, mais ne règle pas rapidement la question de l'intégrité conceptuelle dans un dépôt vivant.

Ce que j'expérimente aujourd'hui va dans la direction inverse. La base de code doit faire vivre aussi la base documentaire. La structure documentaire doit devenir un parallèle raisonnable de la structure du produit.

Si j'ai un Admin UI, il devrait exister un endroit clair où vivent les décisions qui concernent cette composante, ses contraintes, ses relations avec le backend correspondant, ses conventions, ses mappings, ses features importantes. Si j'ai une application mobile, ses interactions avec les APIs, ses contraintes propres, ses choix de design et ses flux principaux devraient pouvoir être suivis dans une structure compréhensible.

L'idée n'est pas d'alourdir. L'idée est d'accrocher la méthode à quelque chose de concret.

## Deux formes sont possibles. Le principe est plus important que la forme

À ce stade, je vois au moins deux manières d'exprimer cette structure.

La première consiste à rapprocher la documentation des composants eux-mêmes. Par exemple, un sous-dossier `doc` dans chaque composante majeure, avec des sections architecture, conception, contraintes, features ou décisions.

La seconde consiste à garder un point d'entrée documentaire top-level, avec une racine unique, puis une hiérarchie qui reflète les grandes parties du projet : UI, backend, mobile, contrats, etc.

Les deux approches se défendent. Le vrai principe n'est pas là. Le vrai principe, c'est que depuis la racine du repo, la découvrabilité doit être pensée pour l'humain et pour l'agent. Il faut que l'agent puisse commencer par les documents généraux, puis suivre des liens ou des chemins explicites vers les éléments plus spécialisés.

Autrement dit, la structure doit aider la compréhension à se déployer progressivement.

## Pourquoi je me méfie d'une multiplication d'outils externes

Évidemment, on peut imaginer connecter Confluence, Jira, un système de billetterie, d'autres dépôts documentaires, ou des outils spécialisés. Je ne dis pas que c'est inutile. Je dis que cela ajoute de la complexité, et que cette complexité ne se justifie pas toujours.

Pour certaines grosses équipes et certains grands programmes, ces outils ont leur place. Mais pour beaucoup de projets, surtout quand on cherche encore le bon point d'équilibre, l'approche à même le monorepo me paraît être un meilleur point de départ.

Elle a un avantage décisif : elle réduit le risque de paralysis by analysis. On n'a pas besoin de résoudre immédiatement tout l'écosystème d'outillage pour commencer à structurer la pensée. On peut adopter une base simple, la faire vivre, puis la faire évoluer au besoin.

Et l'IA aide beaucoup sur ce terrain. Elle sait résumer, redistribuer, reformuler, reclasser, détecter des patterns, proposer des réorganisations. Cela veut dire qu'adopter une première structure imparfaite n'est pas une condamnation. C'est un point de départ évolutif.

## Le moment où mon greenfield a commencé à brunir

Le vrai déclic a été presque ironique.

J'avais commencé Ezkey comme un projet greenfield, et cette situation m'avait donné un maximum de vélocité. J'ai pu expérimenter rapidement, suivre l'évolution des agents de codage, monter progressivement en puissance, tester différentes façons de travailler. C'était stimulant, excitant, vivant.

Puis j'ai fini par comprendre quelque chose d'un peu dérangeant : à force d'avancer avec une structure encore trop peu organisée, mon greenfield était tranquillement en train de devenir un brownfield sous mes yeux.

Pas un brownfield au sens d'un produit vieux de vingt ans. Un brownfield au sens le plus utile ici : un projet où la compréhension d'ensemble commence à coûter plus cher, où la découvrabilité n'est plus naturelle, où l'agent peut encore aider mais plus au niveau optimal, et où l'on commence à compenser par de la mémoire personnelle ce qui devrait être soutenu par la structure.

Et pour moi, ce n'était pas bon signe.

## Retour au vert

Dans cette nouvelle phase, l'objectif n'est pas de faire semblant de revenir au vrai greenfield. Il ne s'agit pas de nier ce qui existe. Il s'agit de recréer quelque chose qui ressemble à un état plus vert : une capacité à avancer avec de meilleurs principes explicites, une meilleure découvrabilité, une structure qui redonne de la puissance à la collaboration avec l'IA.

Vu sous cet angle, la documentation structurée n'est pas une lourdeur administrative. C'est une tentative de redonner au projet une qualité de démarrage qu'il est en train de perdre en vieillissant trop vite.

Je ne suis qu'au début de cette expérimentation, et je ne prétends pas avoir la formule parfaite. Mais le constat est déjà clair : si l'on veut garder une IA vraiment performante comme partenaire de conception et d'implémentation, il faut lui offrir un terrain où les concepts se découvrent, s'enchaînent et se répondent.

## Conclusion

Ce que j'apprends ici est finalement assez simple.

Un projet peut commencer très vert, très rapide, très libre. Cette phase a de la valeur. Elle permet d'explorer, de tester, d'oser. Mais si la structure documentaire ne grandit pas avec lui, la friction finit par apparaître. On se retrouve à porter trop de choses dans sa tête. L'agent compense tant bien que mal. La vitesse reste réelle, mais l'intégrité conceptuelle commence à coûter plus cher.

Le vrai geste méthodologique, dans ce contexte, n'est pas de plaquer un grand système théorique sur le projet. C'est de construire une documentation vivante qui suit la logique du produit, qui reste légère, et qui améliore la découvrabilité au point de rendre possible une compréhension partagée plus profonde entre l'humain et l'IA.

Si je devais résumer cet article en une image, ce serait celle-ci : Ezkey a commencé en greenfield, a commencé à brunir, et la meilleure chose que je puisse faire maintenant est de lui redonner, graduellement, un peu de vert.
