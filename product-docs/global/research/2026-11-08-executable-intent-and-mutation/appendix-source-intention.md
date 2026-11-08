# Appendix — source of intention (quasi-verbatim)

- **Date:** 2026-11-08
- **Speaker:** Marc Gagnon
- **Nature:** dictated transcription kept as an upstream source, separate from the research note that interprets it
- **Transcription limit:** this is a quasi-verbatim conservation of the words. Non-breaking spaces and punctuation appear to have been normalized before this file was written. It is not a byte-for-byte reproduction of the dictation stream. Words are not corrected. In the research note, the project name is Ezkey. Spoken variants in the text below are left as dictated.
- **Unidentified references:** conferences and articles mentioned without a citation stay unidentified.
- **Precedence:** section 2 is a same-day clarification. On ablation, regrouping, and simplification, it prevails over section 1. Section 1 is kept intact. The two texts are not merged.

Discoverable from [`../../test-strategy.md`](../../test-strategy.md) and from the research note in this folder.

## 1. Initial remarks

Je suis l'évolution de la pratique de l'ingénierie logicielle à l'ère de l'agentic IA. Ces derniers temps j'ai vu des conférences où on met de l'avant une convergence d'opinions chez les leaders de pensée de l'industrie. Ça concerne le mutation testing et les spécifications exécutables.

Vous voulez en dire que l'on fusionne le test-driven development avec le behavior-driven development pour faire en sorte qu'on exprime l'intention portée par le code que l'on produit à travers des spécifications qui ne sont pas que des documents, mais qui deviennent éventuellement quelque chose qui se transforme, sous une forme ou sous une autre, en quelque chose d'exécutable et qui devient un gate qui échoue si le code a divergé de l'intention. En ce qui concerne deux sujets, mutation testing, j'aimerais que l'on fasse un ou deux GitHub issues avec une branche associée, probablement un pull request, et la documentation ou le corpus documentaire, selon la méthodologie que l'on a habituellement dans le projet ezkey.

L'objectif sera de faire d'abord une recherche pour ces deux sujets :
- voir l'état de l'industrie
- les nouvelles bonnes pratiques en ingénierie logicielle à l'ère des agents IA
- ce que ça implique pour ezkey

C'est un projet laboratoire, un projet qui concerne la sécurité et pour lequel les aspects de porter l'intention du produit sont critiques pour la sécurité justement.

Il faudra d'abord faire un constat sur les bonnes pratiques en émergence. Il faudra faire un constat à savoir la distance qu'il y a entre l'état actuel du projet ezkey dans sa pratique au niveau du corpus documentaire, du software development life cycle de façon générale, de la stratégie de documentation, ainsi que du lien qu'il y a avec la façon dont l'intention est portée en test au niveau de l'inventaire des tests qui sont actuellement implémentés.

Les tests, on le rappelle ici : on a d'abord une ligne de défense rapide sous la forme des tests unitaires. Il conviendra donc de s'assurer que, pour justement porter l'intention pour cet aspect, tous les sous-modules critiques sont révisés afin de statuer clairement si le niveau de détail et le lien avec l'intention sont raisonnablement portés.

On parle ici en particulier de trois aspects, je crois, en priorité :
- la surface d'API portée par le backend Java, qui est le cœur du système de façon générale
- l'application mobile, qui joue un rôle tout aussi critique
- l'application UI, qui permet d'administrer le système

Ensuite il y a la suite de tests fonctionnels qui portent surtout sur le noyau, donc le code Java. Il conviendra de la compléter par des tests qui sont peut-être moins évolués en ce qui concerne l'UI avec Playwright, puis l'application mobile avec Maestro, et de faire un constat, encore là, sur le niveau d'évolution, de maturité, de lien avec les nouvelles bonnes pratiques émergentes.

Il faudra finalement, fort de l'ensemble de ces constats, statuer sur là où se trouve EASYKey de façon générale face à ces pratiques émergentes. Le but, ce n'est pas de faire un beau rapport avec 300 pages et d'établir mille directions. Non. Le but, c'est de trouver des signaux forts qui sont de l'ingénierie logicielle, comme dans un des articles que j'ai déjà rédigés, « Le retour aux sources », et différents sujets sur lesquels j'ai déjà fait des rétrospectives.

Il faut se questionner sur quelles sont les bonnes pratiques qui portent l'intention produit. C'est un sujet que j'ai ramené à plusieurs reprises lorsqu'il y a eu un besoin de recadrage humain-agent. J'ai dû faire un zoom out et porter à l'attention des agents des éléments de la vie réelle qui sont des valeurs et des boussoles pour orienter les bonnes décisions en autonomie. Il faut aller le plus possible pour un alignement avec l'intention humaine du porté avec le produit.

C'est cette piste de trouver les alignements qui importent : faire vivre à travers ces observations, en générant les signaux qui vont nous amener à nous positionner vers les bons outils et le bon moyen d'intégration et d'alignement entre l'intention portée par le corpus documentaire et l'intention humaine, jusqu'aux tests qui permettent de valider, sous forme d'exécution (build pass, build fail), des gates qui aident à bien porter l'intention avec la complexité essentielle nécessaire, en évitant trop de redondance pour ne pas perdre de temps et de token, et donc trouver le juste milieu pour bien porter l'intention. Un exemple de zoom-out : il y en a eu quelques-uns mais celui dont je me souviens instinctivement, c'est le moment où un agent a évalué l'application mobile pour dire qu'il y avait une lacune : lorsque l'on consomme une demande d'authentification, afficher le décompte.

Ça devenait une histoire compliquée avec une gestion de drift, de différence de temps avec le clock de l'application intégrante qui valide. Là j'ai dit : « Attention, on arrête ça, ce n'est pas pertinent. » Puis la façon de bien voir les choses, c'est d'ancrer l'intention dans le vécu quotidien d'un utilisateur du système. Il faut se mettre dans les souliers de notre utilisateur.

Cet utilisateur, il est devant l'écran d'une application protégée. Il entre son mot de passe, son username, il pèse sur « S'authentifier avec EasyKey » et il sait ce qu'il fait. Il se retourne alors vers son application mobile et demande à consommer la demande d'authentification. Le décompte, lui, ne vit qu'à un seul endroit : c'est à l'écran. Il n'y a pas lieu de le dupliquer.

Mais pour faire cette analyse et amener les bons tests et les bons jugements, la boussole de valeur, la boussole de décision, doit tenir compte de l'intention de l'humain qui utilise le système dans une situation réelle. Il faut travailler à formaliser et rendre visible à un agent ce genre d'intention produit. C'est la clé pour piloter les bonnes décisions pour les bonnes raisons pour l'évolution du produit et c'est ce que l'on recherche ici : trouver ces boussoles, trouver les façons de lier les niveaux d'abstraction entre eux pour porter le message de valeur produit de façon ordonnée, cohérente et bien alignée, sans complexité inutile. Il faut aussi demeurer alerte à ne pas constamment utiliser un mode purement additif. Il convient parfois de prendre un petit recul, d'analyser ce qui existe déjà et de rationaliser. Parfois il est nécessaire de faire une ablation, que ce soit au niveau de la documentation, de la méthodologie ou encore du code et des tests qui viennent appuyer la démarche. Savoir analyser quand il est temps de regrouper et supprimer pour mieux rebondir, c'est aussi une qualité que l'on recherche.

C'est un gros mandat d'analyse et il faudra commencer par un premier draft, porter peut-être de façon assez verbatim l'ensemble de ce propos et pouvoir y référer par la suite. J'apprécierais que ça fasse partie de ce que l'on met dans le billet ou surtout dans un corpus documentaire. Ici il y a quelques moments où j'ai demandé de conserver verbatim les choses et ce sera à considérer, quitte à le supprimer par la suite. Dans quelques situations où j'ai demandé de conserver un verbatim, je crois que ça l'a amené à un angle que je ne vois pas du tout de façon documentée dans la communauté sur des pratiques.

Étant donné que j'utilise un outil de dictée, j'ai l'impression qu'il y a une certaine valeur à faire cela pour aider à porter l'intention entre l'humain et l'agent. Donc j'amène beaucoup de mots, beaucoup de pensées. Parfois ça part dans tous les sens dans mon esprit, mais ultimement ça fait partie de l'aspect laboratoire du projet ezkey.

Encore une fois, c'est un projet qui se veut sérieux, mais où on peut expérimenter des choses, être à la frontière de l'ingénierie logicielle avec les agents. Ça fait partie aussi du rôle de ce projet de faire ce type d'exploration.

Donc prenons en considération qu'il pourrait être pertinent d'inclure à la méthodologie de travail le fait de non seulement spécifier tous les éléments que je viens d'apporter, mais aussi d'avoir, dans un coin, une façon de travailler qui inclut le fait d'attacher le verbatim à au moins un des éléments en amont du corpus documentaire et qu'il soit découvrable lorsque c'est pertinent afin de pouvoir nuancer les propos qui portent l'intention et clarifier, mettre à jour les contradictions, les lier et s'en servir pour établir de l'ordre et rétablir les valeurs de la boussole qui guide les décisions.

## 2. Later clarification (same day)

Je te dirais que l'aspect ablation regroupée simplifiée, je ne veux pas qu'on y accorde excessivement d'importance. Je trouve que c'est une zone potentiellement dangereuse de perte d'information. L'idée, c'est d'être alerte en mode consulter et être proactif à se poser des questions, mais de façon prudente et pondérée.
