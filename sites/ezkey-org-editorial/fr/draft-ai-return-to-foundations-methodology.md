---
audience: "Développeurs expérimentés, architectes techniques, leads pragmatiques ; ton de retour d'expérience, sans détailler d'implémentation produit dans le corps principal."
planned_slug_fr: "retour-aux-sources-developpeur-ere-ia.html"
planned_canonical: "https://ezkey.org/fr/retour-aux-sources-developpeur-ere-ia.html"
status: published
published_html_en: /ai-return-to-foundations-methodology.html
published_html_fr: /fr/retour-aux-sources-developpeur-ere-ia.html
published_date: 2026-05-13
html_amended_post_publish: false
source_of_truth: html
---

# Le retour aux sources du développeur à l'ère de l'IA

<!-- ezkey-org:exclude-start
Titres de travail alternatifs :
1. Ce que l'IA m'a forcé à réapprendre
2. L'IA ne remplace pas l'analyse, elle la rend rentable
3. Quand le contexte redevient le vrai moteur de la vélocité
4. Le développeur augmenté revient aux fondamentaux

Positionnement : article de fond, voix en je, à mi-chemin entre manifeste calme et retour d'expérience structuré.

Liens internes à intégrer dans la version publiée sans alourdir le corps :
- du-monolithe-au-contrat.html
- from-code-to-intent-ai-workflow.html
- strategie-de-test.html
- ai-coding-manifesto.html
- generative-ai-nocode-lowcode-parallel.html

Note rédactionnelle : ne pas trop détailler ici Docker / clean room / OpenAPI / stratégie de test. Les résumer en quelques phrases et renvoyer aux autres billets.
ezkey-org:exclude-end -->

<!-- ezkey-org:exclude-start
Historique des révisions :
- v1 (initial) : parcours du vibe coding vers le context engineering, retour aux fondations spec-first, dette technique, humilité des seniors.
- v2 (mai 2026) : intégration du modèle à trois niveaux (vision/méthodologie → analyse/conception → programmation), du rôle de méthodologiste, de la révélation sur les templates + skills, de la ségrégation intellectuelle engendrée par l'évolution de l'IA. Fusion des sections répétitives ; enrichissement de la structure documentaire avec la famille templates/skills ; refonte de la conclusion.
ezkey-org:exclude-end -->

## Nous avons appris les bonnes méthodes. Puis nous avons appris à les contourner

Quand on se forme comme développeur, on nous enseigne des choses très saines. On nous parle de besoins utilisateurs, d'entrevues avec des experts de domaine, de spécifications, d'analyses fonctionnelles, de flux de données, de modèles de données, d'architecture, de diagrammes, de machines à états, de cas d'utilisation, de stratégies de test. Toute cette formation repose sur une idée simple : un bon système logiciel ne devrait pas sortir de nulle part. Il devrait naître d'une compréhension claire du réel.

Sur le plan intellectuel, tout cela est juste. Et pourtant, dans la vraie vie, ce n'est pas toujours ainsi que les choses se passent.

Dans beaucoup d'équipes, on démarre avec quelques échanges avec le client, quelques exemples concrets, une liste de besoins. Les spécifications deviennent parfois une simple liste d'épicerie. Les diagrammes existent, mais rarement de façon exhaustive. Les décisions d'architecture sont prises, mais pas toujours formalisées. Ce n'est pas nécessairement de la négligence. C'est souvent un compromis entre le temps, la pression, la vélocité et la confiance accordée à l'expérience des développeurs en place.

Avec les années, on finit par devenir très bon dans cette économie implicite. On fusionne les rôles. On devient programmeur-analyste, ou analyste-programmeur, selon le mot qu'on préfère. On développe un instinct. On sait où mettre une frontière. On sait quand découper une verticale, quand faire une concession pragmatique, quand serrer une API. Une part énorme de cette compétence cesse d'être visible parce qu'elle vit dans notre tête.

Cette invisibilité a longtemps été compatible avec une excellente productivité. Le code sortait. Les systèmes avançaient. Les équipes pouvaient même très bien fonctionner ainsi, tant qu'il existait suffisamment d'humains pour transporter ce contexte tacite.

## L'illusion du prompt suffisant, puis le plafond de verre

Puis l'IA de codage est arrivée dans nos IDE, et une première phase très excitante a commencé.

Au début, l'illusion était presque parfaite. On demandait une méthode, un service, un endpoint, un composant. L'outil complétait du code, proposait des tests, suggérait des refactorings. Et cela marchait vraiment. Pas parfaitement, mais suffisamment bien pour donner l'impression qu'une part non négligeable du développement venait de basculer.

Ce qui fonctionnait à ce moment-là, ce n'était pas seulement l'IA. C'était surtout le fait que le contexte restait dans la tête du développeur. L'architecture, l'historique produit, les contraintes métier, les décisions prises avec un client six mois plus tôt, les raisons pour lesquelles tel comportement devait rester ainsi : tout cela n'était pas dans le prompt. Tout cela vivait dans ma tête, et l'IA opérait dans l'espace que je bornais moi-même à chaque instant.

Quelques mois plus tard, j'ai commencé à vouloir déléguer davantage. Pas seulement des fragments de code, mais des ensembles plus consistants : un nouveau service, une évolution plus large, une séquence backend + UI, une série de tests. Et là, le plafond de verre est apparu.

L'IA se heurtait rapidement à ce qui n'était nulle part : les raisons derrière les décisions, les contraintes implicites, les choix d'architecture, l'historique des compromis. Je devais la recadrer, lui rappeler des règles, corriger des hypothèses. Pire encore, tout cela défilait dans les chats. Une conversation chasse l'autre. Un arbitrage important a existé, mais il s'est dissous dans le flot d'interactions.

À ce moment-là, j'ai compris quelque chose de simple : si je voulais que l'IA devienne meilleure, il fallait arrêter de lui demander de lire dans mes pensées.

## Des notes aux plans : le début du context engineering

Ma première réaction a été triviale, mais structurante : j'ai commencé à lui faire prendre des notes. Pas pour faire joli. Pas pour satisfaire un principe documentaire abstrait. Juste pour ne pas perdre les clarifications, les raisonnements et les décisions prises en cours de route.

Après quelques mois, cela a évolué vers des plans plus explicites. Les grands fournisseurs avaient observé ce que tout le monde observait : pour produire du meilleur code, les développeurs avaient besoin de préserver et structurer du contexte. On a commencé à parler de context engineering. Certains l'ont emballé dans des frameworks. D'autres l'ont intégré progressivement dans l'expérience IDE.

J'ai moi-même résisté à l'idée d'adopter trop vite une méthodologie lourde. J'ai préféré rester léger : faire des plans, les conserver dans le dépôt, les clore proprement, les archiver. Cette approche légère a bien fonctionné. Mais elle a aussi révélé une nouvelle exigence : faire des plans ne suffit pas si ces plans réinventent à chaque fois une analyse qui aurait dû exister de façon plus stable.

## 2026 : de meilleurs modèles exigent de meilleures spécifications

Le vrai tournant s'est produit au début de 2026.

Les modèles sont devenus plus autonomes, plus proactifs, plus capables d'enchaîner eux-mêmes des étapes de travail. Et un paradoxe est apparu : plus ils devenaient puissants, plus ils exigeaient une meilleure alimentation en contexte. Pour obtenir un meilleur code, il ne suffisait plus de mieux prompter. Il fallait mieux analyser, mieux découper, mieux concevoir, mieux documenter.

J'ai remarqué quelque chose de frappant : les modèles récents proposaient eux-mêmes d'ajouter des étapes de spécification. Ils demandaient davantage de précision, davantage de contraintes, davantage de structure. L'industrie IA elle-même semblait redécouvrir ce que le développement logiciel sait depuis des décennies : la qualité d'un système dépend aussi de la qualité des artefacts qui précèdent le code.

Cela m'a obligé à regarder un biais très personnel en face. Comme beaucoup de développeurs expérimentés, j'avais pris l'habitude de capitaliser sur mon expérience pour compresser l'analyse dans ma tête. Je pouvais passer directement d'un besoin à une implémentation raisonnable parce que les user stories, les tableaux de décision, les flux fonctionnels, les états, les relations entité, l'architecture, tout cela vivait déjà mentalement chez moi. L'IA a mis ce raccourci en crise. Non pas parce qu'elle est incapable, mais parce qu'elle ne bénéficie pas de cette mémoire implicite tant qu'on ne l'externalise pas.

## Le vrai retour aux sources

À partir de là, le constat s'est imposé : le spec-first n'est pas un effet de mode. C'est un retour aux fondations.

Si je veux vraiment collaborer avec l'IA, je dois recommencer à faire explicitement ce que j'avais fini par faire mentalement. Définir l'intention produit. Clarifier la réalité opérationnelle. Établir les objectifs. Poser les contraintes. Choisir la stack en connaissance de cause. Décrire l'architecture. Nommer les frontières. Documenter les flux fonctionnels. Expliquer les décisions de conception. Prévoir la stratégie de test.

Rien de tout cela n'est nouveau. La nouveauté, c'est l'incitatif.

Pendant des décennies, beaucoup de documentation a fini par être perçue comme un vœu pieux. Quelque chose qu'on devrait maintenir, mais qui coûte cher, qui se dégrade vite, et qui freine la livraison si elle devient trop lourde. Avec l'IA, cette dynamique change profondément. La documentation bien faite cesse d'être seulement une dette morale de l'équipe. Elle devient un multiplicateur de vélocité. Elle devient vivante parce qu'elle nourrit directement un partenaire de travail qui sait lire, comparer, proposer, challenger, écrire, tester et naviguer entre plusieurs couches du système.

## Le parcours de l'IA a évolué aussi : naissance d'une ségrégation

Il y a quelque chose d'important qu'on n'énonce pas assez clairement : le parcours de l'IA elle-même a évolué. Et cette évolution engendre une ségrégation intellectuelle forte au sein de la profession.

D'un côté, il y a ceux qui ont adopté la discipline de l'analyse. Les diagrammes de séquence, les machines à états finis, les tables de décision, les flux fonctionnels. Ces gens sont devenus des analystes au sens fort du terme, et l'IA est devenue leur programmeur. Ils lui livrent des spécifications précises, structurées, actionnables — et elle produit du code cohérent, testé, aligné sur une vision. De l'autre côté, il y a ceux qui ont continué dans le mode prompt-et-codage, plus ou moins sophistiqué, mais sans rehausser leur discipline d'analyse. L'écart entre les deux groupes se creuse rapidement.

Un informaticien, au fond, fait quelque chose de simple : il découvre un monde, s'y intéresse, définit un produit, puis mobilise des outils d'analyse, de conception et d'architecture pour lui donner forme. Ce que l'IA a changé, c'est que la partie « donner forme » peut désormais être très largement déléguée. Ce qui reste irremplaçablement humain, c'est la partie « comprendre, définir, structurer, décider ». La profession revient à ses fondamentaux — mais elle les redécouvre dans un contexte qui rend enfin leur valeur concrète et mesurable.

## L'inconfort partagé : malaise, humilité et faux espoirs

Beaucoup d'équipes sentent intuitivement qu'un retour aux sources s'impose. Elles comprennent que l'analyse, la conception et l'architecture doivent revenir au premier plan. Mais cette intuition s'accompagne souvent d'un malaise. Les gens savent qu'il manque quelque chose, sans savoir exactement comment le remettre en place.

J'ai vu des équipes se demander quelle forme documentaire adopter, quel niveau de détail viser, comment éviter de produire des documents inutiles, et surtout comment ne pas commettre une erreur méthodologique. Cette peur de mal faire produit un effet paradoxal : au lieu de remettre les choses en mouvement, elle encourage l'immobilisme. On attend un signal fort venu des pairs, des grands fournisseurs, des cabinets de conseil. Ce signal n'arrive jamais de façon nette. Pendant ce temps-là, les équipes font du surplace en croyant être prudentes.

Il y a aussi une dimension plus inconfortable, mais essentielle. Pour des développeurs avec dix, quinze ou vingt ans d'expérience, il est difficile d'accepter qu'ils ne sont peut-être pas aussi solides qu'ils le pensaient sur certains fondamentaux. J'ai osé le dire brutalement dans un échange d'équipe : « la gang, je ne veux insulter personne, même si je m'apprête un peu à le faire, mais on n'est pas si hot que ça ». Le fond est sérieux. Quand un senior constate que l'IA ne fonctionne pas très bien dans son contexte, il est souvent plus confortable de conclure que le problème vient surtout du brownfield, de la taille de la base de code, du mauvais moment. Pendant ce temps-là, on continue surtout à faire des prompts locaux, à tester à petite échelle, à temporiser.

Je le dis aussi contre moi-même. Dans mon propre projet, j'ai créé de la dette très vite. La première ébauche avait été produite dans une phase de vibe coding pur et dur : des niveaux mélangés, des services et des accès DAO dans les contrôleurs, des séparations floues. C'était objectivement laid. Ce moment m'a forcé à un constat désagréable : je n'étais pas en train d'appliquer proprement l'analyse et la conception que je pensais pourtant maîtriser.

Dans cet état d'hésitation, un autre réflexe apparaît souvent : l'espoir qu'un framework, un outil ou un mode du moment va résoudre le problème à notre place. Hier on était en mode Copilot. Aujourd'hui tout le monde parle de Claude Code. Demain ce sera autre chose. Je ne crois pas à cette magie. Changer d'outil peut améliorer certaines choses, mais rien de cela ne corrige le problème fondamental si l'équipe n'a pas reconnecté avec son rôle de fond : analyste, concepteur, architecte, testeur, expert de domaine, et pas seulement producteur de code.

## Ne pas choisir une méthode légère, c'est choisir l'immobilisme

La bonne nouvelle, c'est qu'il n'est pas nécessaire de revenir aux lourdeurs méthodologiques d'hier.

Le parallèle avec l'architecture logicielle me paraît éclairant. Pendant quelques années, les microservices ont été vendus comme une panacée. On a vu la suite : explosion de complexité opérationnelle, de maintenance et d'interfaces fragiles. Le marché n'est pas revenu au monolithe par nostalgie. Il est revenu au monolithe modulaire, au monorepo pragmatique, parce qu'il cherchait un meilleur point d'équilibre. Il faut faire exactement la même chose avec la méthodologie IA.

Il faut aussi éviter le réflexe RUP. Les grandes méthodologies qui prétendent tout couvrir — Rational Unified Process et ses monstres de templates — se sont effondrées sur elles-mêmes : trop lourd, trop rigide, trop éloigné de la réalité des projets. Ce type d'approche universelle ne devrait pas revenir. Ce qui devrait exister à la place, c'est une méthodologie propre au projet : légère, pragmatique, choisie délibérément.

À mes yeux, le choix le plus structurant est simple : décider qu'une part de la méthode doit vivre à même la base de code, dans des formats simples, versionnés, lisibles par les humains et par l'IA.

## De l'analyste qui choisit une méthode au méthodologiste qui la conçoit

C'est ici que quelque chose de fondamental m'a été révélé, il y a seulement quelques jours.

J'avais une approche d'analyste-programmeur. J'avais à cœur l'architecture du système, j'avais une bonne expérience, j'utilisais l'IA en mode plan. J'interagissais avec elle pour créer des analyses, des concepts, des tables de décision, tout ce qu'il faut pour générer du code de qualité. Mais il me manquait une véritable méthodologie. Ce que je faisais était bon, mais c'était artisanal. Chaque plan, chaque artefact, chaque forme documentaire était réinventé en chemin.

Un ami m'a présenté sa façon de travailler, qui avait consisté très tôt dans son projet à rédiger des templates, une méthodologie pour les utiliser, et des skills — au sens moderne de l'IA : des instructions spécialisées, des agents à rôle défini, des séquences d'analyse formalisées. Ces templates, ce guide méthodologique et ces skills forment un système. Pas simplement le mode plan d'un IDE. Une approche méthodologique qui lui appartient, taillée sur mesure pour les réalités de son projet.

Résultat : une fois cette méthodologie rodée, tout est devenu uniforme, systématique, répétable. Les patterns appropriés au projet s'enchaînent rapidement, efficacement, avec une reproductibilité quasiment sans faille. Le travail s'est accéléré. Il est devenu rigoureux, fiable. C'est l'effet concret de l'application d'une méthodologie.

Ce qui m'a frappé, c'est la différence de niveau. Il y a l'analyste qui UTILISE une méthode. Et il y a le méthodologiste qui CONÇOIT la méthode adaptée au projet. Le méthodologiste prend comme objet de premier plan non pas le produit, non pas une architecture ou un concept, mais les guides qui orientent eux-mêmes ces activités : la palette d'outils d'analyse, les templates de représentation, les patterns de prise de décision, les cycles de travail. Ce sont ces métadécisions qui forment le cadre dans lequel l'analyste, le concepteur et l'IA travaillent ensuite.

Interagir avec un agent IA en tant que partenaire de définition méthodologique, c'est une expérience différente du mode plan ordinaire. On lui présente les besoins du projet, la nature des décisions à prendre, les types d'artefacts à produire, les cycles à rendre systématiques. Ensemble, on conçoit la méthodologie et les skills qui la mettent en œuvre. Et tout d'un coup, cela élève le jeu comme on ne l'aurait pas cru possible. Ça a été une révélation.

Cette découverte m'a aussi propulsé dans un grand chantier de retrofitting : reprendre la matière produite jusqu'ici — à format variable, à profondeur variable, à représentation variable — et la réinjecter vers un format méthodologisé et uniforme. Des templates, des patterns, des conventions de nommage, des cycles, des phases, des métadonnées de statut qui forment un flux de travail complet, cohérent, bien ordonné. Il y aura du travail, mais c'est la bonne direction.

La grande leçon à tirer de tout ceci, je l'ai visualisée en trois niveaux :

- **Vision, méthodologie**
- **Analyse, conception, architecture**
- **Programmation, test, débogage**

Avant l'IA, l'analyste-programmeur opérait aux deux niveaux du bas. L'objectif de l'analyse était de produire du code. Le niveau du haut — vision et méthodologie — était réservé à des architectes séniors ou à des méthodologistes que la plupart des projets n'avaient pas vraiment les moyens de s'offrir.

Maintenant, l'IA occupe le niveau du bas. Programmer, tester, déboguer : elle s'en acquitte remarquablement bien, et de mieux en mieux. Ce qui reste irremplaçablement humain, c'est ce qui se passe aux deux niveaux supérieurs.

L'humain doit désormais être un visionnaire-méthodologiste capable de définir des visions, de concevoir des méthodologies appropriées, d'orchestrer des analyses, de poser des architectures, d'écrire des concepts — et l'IA produit le code. Ce n'est pas un affaiblissement du rôle du développeur. C'est une élévation. Et c'est ça la grande conclusion de cette période.

## Les outils qui incarnent cette approche : exemples, pas finalités

L'écosystème ne s'est pas arrêté à la théorie. Des outils ont commencé à matérialiser cette élévation méthodologique de façon concrète.

Claude Code, qui s'est imposé comme un des environnements les plus populaires du moment, en est un exemple frappant. Avec des extensions comme Super Powers, il dépasse largement le mode plan ordinaire pour offrir une structure méthodologique directement intégrée à l'expérience de travail : des workflows interactifs, une organisation standardisée des artefacts, un ton qui allège sans banaliser, et une façon de guider l'agent qui rappelle davantage l'interaction avec un partenaire d'analyse qu'avec un simple générateur de code. C'est le juste milieu que beaucoup cherchaient sans pouvoir le nommer : assez structuré pour être systématique, assez léger pour rester vivant.

Mais c'est là qu'il faut résister à un réflexe familier : celui de voir dans une solution populaire la solution définitive.

Claude Code et Super Powers sont des exemples à étudier, des points de départ à adapter, des incarnations d'une direction juste — pas une norme à copier tel quel. Ce qui fait la force de cette approche n'est pas liée à un outil particulier. Elle tient à un principe : un agent IA est capable d'interagir à un niveau méthodologique avec quiconque veut bien organiser son travail à ce niveau. L'approche ne devrait pas être la chasse gardée d'un groupe d'initiés qui maîtrisent les bons plugins au bon moment. Elle appartient à quiconque est prêt à réfléchir à ce niveau d'abstraction.

La customisation méthodologique doit être prudente. On ne restructure pas son workflow à chaque nouvelle mode. Mais elle apporte des avantages réels quand elle est faite avec discernement : elle ajuste les cycles de travail au style d'une équipe, à la nature d'un produit, aux types de décisions récurrentes dans un domaine. Une équipe qui développe une plateforme de sécurité n'a pas la même palette d'outils d'analyse qu'une équipe qui construit un produit SaaS de gestion de contenu. La méthodologie doit refléter cette réalité.

Ce qui compte, c'est donc moins l'outil que la posture. Rester ouvert à ce que les meilleurs outils du moment ont à enseigner. Ne pas s'y enfermer. Comprendre ce qui les rend efficaces. Et construire, à partir de cette compréhension, quelque chose d'adapté au projet et à l'équipe.

## La dette qu'on ne veut pas regarder en face

Cet exercice d'humilité ne concerne pas seulement les individus. Il concerne aussi les équipes et les directions.

Dans beaucoup d'organisations, ce qui est valorisé, c'est d'avancer la business. C'est normal jusqu'à un certain point. Mais cette logique a un angle mort : le reverse engineering, la reconstruction documentaire, l'hygiène de code ne paraissent presque jamais de façon flatteuse dans un état d'avancement. Quand on rattrape des mois de laisser-aller technique, on ne donne pas l'impression d'inventer une nouvelle fonctionnalité spectaculaire. Et pourtant, sans cette étape, les bénéfices de l'IA ne se multiplient pas correctement.

Dans mon cas, j'ai fini par faire un moratoire de plusieurs semaines pour remettre le code comme du monde. Ajouter de la Javadoc. Nettoyer les warnings. Introduire des garde-fous de qualité. Durcir les conventions. C'est après cette remise en état que les bénéfices multiplicateurs de l'IA ont commencé à mieux se cumuler.

La même logique s'est imposée dans mon rapport à Docker. J'avais gardé en tête le Docker d'il y a plusieurs années. Le Docker que j'ai redécouvert était meilleur, plus mature, plus holistique — et beaucoup plus utile que le souvenir que j'en avais gardé. L'humilité dont je parle n'est pas seulement morale. C'est une posture pratique qui permet de revisiter des outils qu'on croyait déjà connaître, d'abandonner des pistes séduisantes mais mal adaptées, et d'accepter que le bon équilibre soit parfois plus simple et plus concret que la solution théoriquement la plus sophistiquée.

Dans un brownfield, ce rattrapage peut exiger de ralentir franchement la livraison de nouvelles choses pour reconstituer les conditions qui rendront ensuite la vélocité durable. Il faut accepter que ce travail ressemble parfois à un repli stratégique : on s'arrête, on se regarde honnêtement, on nomme la dette, on rétablit des bases plus saines, puis on repart avec une autre qualité d'élan.

## Documents, templates, skills : une structure qui vit dans le dépôt

Pour se remettre en mouvement, il faut assumer trois familles d'artefacts directement dans le dépôt.

La première famille, ce sont les plans de travail. Des plans avec une structure minimale, des garde-fous, un statut, un historique, et la possibilité d'être relus, critiqués, amendés, puis archivés. Ces plans deviennent un espace d'interaction entre analystes, concepteurs, développeurs et IA. Si l'on veut une véritable vélocité collective, il faut commencer à versionner les plans de travail au lieu de les laisser se dissoudre dans des chats ou des réunions.

La deuxième famille, ce sont les documents qui décrivent la vérité actuelle du système. Pas en prose abstraite. Une structure parallèle au code, qui en reflète l'organisation et les frontières principales. Des documents d'architecture, de features, de frontières et de mappings. Des flux fonctionnels, des décisions de conception, des contrats d'interface. Quand cette structure existe, elle devient lisible par un humain sans replonger immédiatement dans le code, et exploitable par l'IA comme contexte de génération et de vérification.

La troisième famille, ce sont les templates et les skills. Des templates qui donnent une forme attendue aux artefacts d'analyse et de prise de décision. Des skills IA qui implémentent des patterns spécifiques : un skill pour produire une analyse de fonctionnalité, un skill pour modéliser une machine à états, un skill pour conduire une revue d'architecture, un skill pour le retrofitting de l'existant. Ces templates et skills forment le cœur vivant de la méthodologie. Ils rendent le travail systématique et reproductible, pas seulement documenté.

Il y a aussi un accélérateur sous-évalué dans ce dispositif : la dictée. Pour produire du contexte de qualité, il faut pouvoir communiquer massivement — verbaliser des nuances, des raisons, des contraintes, des arbitrages. En 2026, je ne crois plus qu'on puisse soutenir ce volume d'élaboration uniquement au clavier. Dès qu'on veut vraiment penser à haute voix, la dictée devient un accélérateur majeur. La parole fluide libère une pensée plus riche, moins autocensurée, plus rapide à déposer. On n'économise plus les mots. On laisse sortir le raisonnement. Ensuite, l'IA aide à trier, condenser, reformuler.

## Greenfield ou brownfield, la logique est la même

Dans un projet greenfield, tout cela est relativement simple. On peut mettre en place la structure documentaire, les templates et les skills dès le départ et les faire grandir en parallèle du code.

Dans un brownfield, c'est plus difficile. Il faut une phase de reverse engineering. Il faut choisir les modules les plus critiques, ramener dans les documents l'essentiel des frontières, des mappings, des interactions et des décisions implicites. C'est un transvasement progressif de l'information qui vit encore dans la tête des développeurs vers une structure documentaire plus stable. Mais ce transvasement ne fonctionne pas très bien si l'équipe refuse de regarder sa dette en face. Se remettre en mouvement commence justement par le choix d'une structure assez simple pour être adoptée et assez utile pour devenir vivante.

## L'IA à tous les niveaux, jusqu'au comportement d'ingénieur

Le changement le plus important, à mes yeux, est là. L'IA n'est pas seulement un outil de génération de code. Elle devient utile à tous les étages.

Elle peut participer au brainstorming produit. Elle peut challenger un positionnement. Elle peut aider à raffiner une architecture. Elle peut reformuler des exigences. Elle peut pointer les zones floues d'un workflow. Elle peut proposer une stratégie de test raisonnable. Elle peut aider à structurer la documentation de conception. Elle peut ensuite faire du pair programming très concret sur le code lui-même.

Ce qui m'a le plus frappé récemment, c'est un glissement subtil mais décisif. Avec les mêmes scripts, les mêmes documents, les mêmes conventions, un modèle plus récent s'est mis à prendre des initiatives que je n'avais pas explicitement demandées. Pour valider une évolution touchant à la fois l'interface et la base de données, l'agent a constaté qu'il disposait des scripts, des repères de test, des accès à l'Admin UI et d'un environnement de démonstration déjà documenté dans le dépôt. Il a compris qu'il devait ouvrir un navigateur, se connecter, utiliser le Demo Device prévu par le bootstrap, générer les bonnes données et vérifier le scénario de bout en bout.

Je ne lui avais pas enseigné cette séquence de manière impérative. Ce comportement a émergé parce que le contexte était devenu suffisamment bon, suffisamment cohérent, suffisamment exploitable. L'autonomie de l'IA ne vient pas seulement de ses progrès internes. Elle vient aussi de la qualité du monde documentaire et opérationnel dans lequel on l'insère.

## Le développeur n'est plus celui qui garde tout dans sa tête

Je crois que c'est là le vrai déplacement du métier.

Pendant très longtemps, la figure implicite du développeur héroïque a dominé : celui qui comprend tout, qui se souvient de tout, qui porte l'architecture, la logique métier, la dette historique, les exceptions et les règles non écrites dans sa propre tête. Le développeur comme demi-dieu de l'équipe, dernier garant du fait qu'un produit sortira malgré tout.

Cette figure n'a pas disparu, mais elle perd son avantage central. Ce qui devient stratégique, ce n'est plus de garder l'information en soi. C'est de savoir la structurer, l'écrire, l'articuler, la rendre exploitable par d'autres humains et par l'IA.

Le développeur que cette période favorise est celui qui sait comprendre le produit, comprendre le client, comprendre les contraintes, comprendre l'architecture, faire des choix de conception, documenter ces choix, et ensuite faire travailler l'IA dans ce cadre. Pas seulement pour coder plus vite. Pour concevoir plus juste, tester plus intelligemment, et conserver un système plus explicable.

Et cela demande souvent un geste plus exigeant qu'on ne l'admet volontiers : accepter de se déplacer vers des niveaux de pensée plus abstraits, reconnaître qu'on a laissé rouiller certaines parties de notre métier, et remettre délibérément de la rigueur là où l'habitude et la vitesse avaient fini par tout compresser dans notre tête. Ou, pour les plus avancés d'entre nous, accepter de monter encore d'un cran : endosser le chapeau de méthodologiste, non pas comme une posture, mais comme un investissement concret. Le temps de définir la palette d'outils, les templates, les skills. Puis poser ce chapeau, et reprendre le travail avec un élan tout autre.

## Conclusion

L'IA n'est pas en train de nous libérer de l'analyse et de la conception. Elle est en train de nous punir quand nous les gardons implicites, et de nous récompenser quand nous les rendons vivantes.

En ce sens, elle force un retour aux sources. Non pas un retour à la lourdeur documentaire des grandes méthodologies d'hier, mais un retour à la logique qui les justifiait : comprendre avant de construire, structurer avant de déléguer, documenter pour accélérer, et non pour se rassurer.

Le domaine de l'informatique est en métamorphose profonde. Ce qui s'est dessiné, c'est un réagencement des niveaux : vision et méthodologie au sommet, analyse et conception au milieu, programmation et test en bas. Pendant longtemps, l'analyste-programmeur opérait aux deux niveaux inférieurs. Aujourd'hui, l'IA occupe le niveau du bas avec une compétence croissante. Ce qui reste irremplaçablement humain, c'est ce qui se passe au-dessus : comprendre, choisir, architecturer, décider, et — pour ceux qui vont plus loin — définir le cadre méthodologique dans lequel tout cela se déroule.

Le paradoxe de cette époque est peut-être celui-ci : plus les machines écrivent du code, plus les humains doivent redevenir analystes, concepteurs, architectes — et parfois méthodologistes.

Les équipes qui tireront vraiment parti de cette période ne seront pas forcément celles qui se croient déjà prêtes. Ce seront souvent celles qui auront eu l'humilité de reconnaître leur dette, le courage de faire des choix difficiles, et la discipline de reconstruire graduellement des fondations plus propres, plus explicites et plus vivantes.

Au fond, ce texte parle peut-être d'une aventure plus humaine que technique. Beaucoup d'entre nous sont entrés dans ce métier attirés par quelque chose de grand : comprendre, concevoir, construire, relier des idées, donner une forme à des systèmes cohérents. Avec le temps, une partie de cette richesse s'est comprimée en réflexes, en connaissances stockées dans la tête, en vitesse d'exécution à travers un clavier.

L'IA rend ce paradoxe impossible à ignorer. Nous avons accumulé énormément de savoir, et en même temps beaucoup d'inconfort. La sortie ne passe ni par la nostalgie, ni par la fuite en avant. Elle passe par un arrêt volontaire, un recadrage, un retour humble vers les fondations que nous n'aurions jamais dû abandonner — et, pour les plus curieux d'entre nous, un saut vers un niveau qu'on n'avait peut-être pas encore osé nommer.
