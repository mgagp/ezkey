---

## status: draft
audience: "Développeurs expérimentés, praticiens de méthodologie IA, lecteurs des essais précédents sur Ezkey ; essai personnel, voix en je, lane « Craft & engineering ». Registre philosophique et autocritique, ancré dans le vécu concret de la méthodologie Ezkey. Structure « essentiel d'abord » puis développement linéaire ; verdict explicite avant la conclusion."
planned_slug_fr: "ablation-methodologie-mode-plan.html"
planned_slug_en: "ablation-my-own-methodology.html"
planned_canonical: "[https://ezkey.org/fr/ablation-methodologie-mode-plan.html](https://ezkey.org/fr/ablation-methodologie-mode-plan.html)"

# Le principe d'ablation, ou ma méthodologie devant le miroir



## L'essentiel, d'abord

J'ai entendu récemment Boris Cherny, le créateur de Claude Code, raconter comment son équipe traite les nouvelles générations de modèles. À chaque sortie, ils suppriment l'entièreté du system prompt du produit, puis le remontent ligne par ligne pour voir ce qui reste réellement utile. Avec Opus 5, ils en ont retiré plus de 80 %. La plupart de ce qui restait compensait des défauts qu'un modèle plus intelligent ne fait simplement plus. Il appelle ça un principe d'ablation, et son conseil aux utilisateurs de son propre outil, ce n'est pas mineur : tous les six mois, supprimez votre fichier de directives, vos skills, vos automatisations, et regardez ce que le modèle fait sans eux.

C'est en écoutant ça que je me suis mis à retourner la question contre moi-même. Voilà quelques mois, j'ai construit pour Ezkey une méthodologie complète : des niveaux documentaires, des templates, des skills, des règles de nommage. Je l'ai présentée dans un article précédent comme un retour aux sources salutaire, en prenant soin de dire que je voulais éviter le piège des grandes méthodologies d'hier. Le principe d'ablation, pris au sérieux, m'oblige à me regarder dans le miroir. Est-ce que ma méthodologie mérite encore d'exister ? Est-ce qu'elle est vraiment aussi légère que je l'ai prétendu ? Est-ce que je ne devrais pas simplement revenir au mode plan, nu, et laisser les grands fournisseurs faire le travail à ma place ?

Le miroir donne un verdict en deux temps, et les deux m'ont surpris. D'abord : non, ma méthodologie n'est pas aussi légère que je l'ai affirmé. Elle a grossi, et je vais essayer de l'admettre sans me défendre — on verra que je n'y arrive qu'à moitié. Ensuite, le vrai renversement : ce constat n'est pas une raison de tout jeter. C'est une raison de recompartimenter, de garder ce que le mode plan ne porte pas et de laisser filer le reste vers l'évolution naturelle des modèles. Le reste de cet article raconte ce tri, jusqu'à une répartition explicite et jusqu'à un aveu que je garde pour la fin, parce qu'il concerne le miroir lui-même.

## Le principe d'ablation

Ce que je retiens de Cherny, c'est une idée simple qui devient inconfortable dès qu'on l'applique sérieusement : les directives qu'on donne à un modèle sont souvent des béquilles pour compenser les faiblesses d'une génération précédente. Quand le modèle change, la béquille devient parfois du bruit, parfois même une entrave. On continue à lui expliquer comment marcher alors qu'il court déjà. Je fais ici une interprétation libre de son propos : je n'étais pas dans la salle, je n'ai que la transcription et mon propre filtre. L'intuition me paraît pourtant juste au-delà du cas de Claude Code.

Ce qui me frappe, c'est que je tenais déjà, sans le nommer, un principe cousin. J'ai écrit qu'il fallait éviter de couper les ailes probabilistes du modèle : formuler trop de contraintes trop tôt ferme des chemins qui auraient pu servir le projet. L'ablation de Cherny, c'est la même logique appliquée dans le temps plutôt que dans l'instant. Ce qu'on fige aujourd'hui pour compenser un modèle limité devient plus tard un carcan. Personne ne l'a choisi ; il s'est installé sans bruit, ligne après ligne.

## Se regarder dans le miroir

Voici l'exercice inconfortable. J'ai écrit, dans ce même article, que je voulais éviter le réflexe RUP : ces grandes méthodologies des années deux mille qui prétendaient tout couvrir avec des monstres de templates, et qui se sont effondrées loin de la réalité des projets. Je voulais une méthodologie propre au projet, légère, choisie délibérément. C'était sincère. Ce n'était pas complètement vrai.

Avec le recul, le constat s'impose : la méthodologie d'Ezkey compte aujourd'hui des dizaines de documents de cadrage, une vingtaine de templates, une quinzaine de skills, et un corpus de notes de vision, d'idées et de plans de conception qui a largement dépassé le stade anecdotique. Est-ce aussi lourd qu'un RUP ? Non, mais pas pour la raison que je croyais. Le RUP ne s'est pas effondré sous son volume ; il s'est effondré sous ses obligations. Chez moi, rien n'impose un chemin unique : chaque niveau, chaque gabarit reste optionnel, à prendre à la pièce. C'est un choix assumé, opinionated comme le reste du projet. Reste que léger, ça ne l'est pas non plus, et je préfère le nommer plutôt que de répéter la version flatteuse.

Le plus étonnant, c'est que ce constat était en partie déjà écrit dans mon propre corpus. J'y ai retrouvé, dans une note de décision, l'avertissement explicite de ne pas ajouter une nouvelle couche de cérémonie ; une autre note distinguait le travail d'hygiène courant d'un vrai programme, précisément pour éviter de matérialiser un dossier complet pour une mise à jour de routine. Ces garde-fous vivent au fond du corpus, dans des décisions datées qu'un agent qui démarre à froid n'a aucune raison de consulter en premier.

J'ai d'abord classé ça comme un problème de rangement : les bonnes règles existent, elles sont juste enfouies. La lecture inconfortable est ailleurs. Un corpus assez épais pour perdre ses propres meilleures règles est un corpus trop épais — et une règle que personne ne lit est le candidat idéal à l'ablation. Je n'ai pas encore tranché ce qui devra partir ; je sais maintenant où chercher.

Quant à ce que cette épaisseur me coûte, je n'ai pas d'anecdote spectaculaire à offrir : pas de session ruinée, pas de document inutile produit sous la contrainte. Le coût est plus sournois, c'est le doute. Chaque fois que j'envisage de retrancher, j'hésite, de peur de couper quelque chose qui ne devrait pas l'être, et cette hésitation se paie en temps, session après session. C'est un mauvais réflexe que j'ai encore. Autrement dit, je n'ai pas fini de digérer ce que Cherny est venu me dire.

## Ce que le mode plan fait mieux

Il y a une asymétrie que je n'avais pas pleinement mesurée avant cet exercice : ma méthodologie est statique, alors que le mode plan des grands fournisseurs évolue à chaque version, nourri par le retour d'expérience de milliers de développeurs, de designers, de gestionnaires de produit. Chaque release améliore la capacité du modèle à chercher en profondeur avant d'agir et à remettre en question ce qu'on lui soumet. Cette amélioration-là, je ne peux pas la reproduire moi-même : elle vient d'une échelle de rétroaction que je n'ai pas.

Je l'ai vérifié à mes dépens. J'ai déjà utilisé ma méthodologie de bout en bout pour implémenter une fonctionnalité complète, de l'idée au design puis au code, sans passer par une session de plan ouverte. Ça a fonctionné, mais je m'étais privé de quelque chose : en sautant le mode plan, je restais en mode agent, sans la phase de recherche large qu'il est justement conçu pour imposer. Ce n'est pas gagner en discipline. C'est se couper d'une amélioration continue que je ne finance ni ne contrôle.

Concurrencer le mode plan n'est la vocation ni de ma méthodologie, ni d'aucun projet de cette taille. Le bon réflexe, c'est l'inverse : le mode plan d'abord, la méthodologie en support.

## Ce que le mode plan ne fait pas

Une fois cette humilité admise, il reste une vraie question : qu'est-ce que ma méthodologie apporte que le mode plan ne fait pas aujourd'hui, et n'a pas vocation à faire ?

Le mode plan n'a pas de niveau prédéterminé. On peut aussi bien s'en servir pour brainstormer un positionnement produit que pour transformer une idée en design, ou pour tirer d'un design ses conséquences sur le code. Dans tous les cas, ça reste une session ouverte, sans hiérarchie imposée entre ces usages, sans lien systématique entre ce qu'on a fait la semaine dernière et ce qu'on fait aujourd'hui. Ma méthodologie, elle, a introduit des niveaux explicites : une vision produit qui se distingue d'une idée concrète, qui se distingue à son tour d'un design borné prêt à être implémenté. Chacun de ces niveaux a sa forme, ses métadonnées, son statut, et surtout ses liens vers les autres : une découvrabilité bidirectionnelle qui permet de remonter d'une ligne de code jusqu'à l'intention qui l'a motivée, et inversement.

C'est précisément ce que le mode plan, laissé à lui-même, ne structure pas : une mémoire de produit. Il n'a pas vocation à jouer le rôle d'un backlog, à porter mes visions à travers les mois, à relier une décision de conception prise en mai à une contrainte de sécurité posée en février. Quand je reprends une session après avoir bouclé plusieurs dossiers, la question qui monte naturellement, c'est : quelles devraient être nos prochaines priorités ? Cette question-là, je peux la poser à un agent froid sans lui rappeler le contexte, parce que le corpus le porte déjà. Je peux aussi lui verser des idées en vrac, à la voix si j'en ai envie, avant de les faire migrer vers une forme plus structurée quand elles le méritent. Je reviendrai sur cette notion de mémoire de produit, parce qu'une évolution récente chez les fournisseurs semble, à première vue, la contredire.

## Ce que je prenais pour un défaut

J'ai utilisé des skills et des règles d'agent pour renforcer la proactivité autour de ma méthodologie, et malgré ça, l'application est restée à géométrie variable : parfois plusieurs éléments pris en charge d'un coup, parfois une section oubliée. Pendant longtemps, j'ai vécu ça comme un défaut personnel. Je me sentais coupable de ne pas savoir forcer systématiquement le bon chemin avec des règles plus strictes.

J'ai résisté à cette tentation de tout contrôler, et je crois aujourd'hui que c'est ce qui m'a sauvé. Si je m'étais entêté à imposer un chemin unique, figé dans le temps, j'aurais fait exactement l'inverse du principe d'ablation : j'aurais coupé les ailes du modèle au moment même où il devenait suffisamment intelligent pour choisir lui-même, dans un corpus riche, ce qui méritait d'être lu. Ne pas avoir tout verrouillé a laissé la place à l'évolution des modèles pour combler les trous que j'avais laissés ouverts, sans le savoir, par maladresse.

Je vois ce que je viens de faire : convertir un aveu en vertu en deux paragraphes. Le procès annoncé tourne à l'acquittement, et plutôt vite. Je le nomme parce que c'est exactement le réflexe qui rend la vraie ablation nécessaire, et j'y reviendrai à la fin.

La même souplesse se retrouve dans un choix fait dès le départ : garder la méthodologie autonome de tout système de billetterie. La gestion de produit vit entièrement dans le dépôt Git ; chaque artefact porte une date et un identifiant unique, ce qui permet de mener des chantiers parallèles et de ne consolider la documentation qu'au moment où les branches se rejoignent. Branche avant ou après, pull request ou merge direct, billet ou pas : rien n'est imposé, et quelqu'un pourrait forker le projet et le maintenir avec Jira, Git restant le seul socle commun. Cette portabilité mériterait un article à elle seule ; je m'arrête avant de le commencer.

Cette souplesse n'en est pas une pour tout le monde. Ma géométrie variable laisse passer de petits oublis qu'il faut rattraper par une relance ; pour une équipe qui a besoin que chacun suive le même chemin de la même façon, ce serait un manque d'uniformité nuisible plutôt qu'une force.

## Recevoir plutôt que construire

Ma lenteur à adopter certaines technologies récentes relève du même mouvement. Les sous-agents parallèles, par exemple : je ne m'en croyais tout simplement pas capable, trop de complexité à apprendre d'un coup alors que j'avançais déjà sur une application mobile et un admin UI. J'ai continué à faire des plans, et les modèles frontières ont fini par intégrer ces pratiques d'eux-mêmes. J'en profite maintenant sans effort de rattrapage.

Une évolution récente illustre où ce mouvement s'en va. Anthropic a présenté un mode qu'ils appellent dreaming : un job qui relit en lot des dizaines de sessions passées d'un agent et sa mémoire existante, pour en extraire les motifs récurrents et reconstruire une mémoire plus propre, sans doublons ni contradictions. L'analogie qu'ils assument, c'est le cerveau qui consolide ses souvenirs pendant le sommeil. Je n'ai pas l'intention de construire un équivalent pour Ezkey ; ce n'est ni ma place ni mon échelle. C'est une capacité que je recevrai probablement sans effort, comme le mode plan avant elle.

On pourrait m'objecter que ce mode contredit ce que j'ai écrit plus haut : si les fournisseurs consolident la mémoire à travers les sessions, à quoi bon un corpus qui la porte à travers les mois ? La distinction est réelle. Dreaming consolide la mémoire d'un agent : préférences de travail, erreurs qui reviennent, styles d'interaction. Ma méthodologie porte la mémoire d'un produit : une intention structurée en niveaux, reliée par des liens qu'on peut suivre dans les deux sens. L'un apprend comment je travaille ; l'autre retient ce que je construis, et pourquoi. Les deux couches ne se concurrencent pas ; je m'attends à ce qu'elles finissent par se compléter.

## Ce qui n'a pas à vivre dans le corpus

Le questionnement d'ablation m'a aussi servi plus concrètement : pour départager ce qui mérite de vivre dans un document de vision, d'idée ou de conception, et ce qui devrait rester dans le code lui-même, proche de ce qu'il décrit.

Au tout début du projet, j'ai vécu un moratoire de plusieurs semaines pendant lequel j'ai coupé volontairement l'usage de l'IA pour remettre à niveau la qualité du code, parce que la première génération avait mélangé les couches et les conventions. Depuis, je tiens à ce standard, et j'ai fini par accepter une idée qui allait contre mon intuition initiale : tout élément de conception ne mérite pas un document canonique. Les itérations récentes autour du chiffrement des données au repos en sont un bon exemple. L'intention de ces choix se lit dans la structure du code, pourvu que les commentaires soient bien placés. Ce qui mérite de vivre dans le corpus, c'est le pourquoi stratégique : pourquoi telle colonne est chiffrée, pourquoi telle décision de sécurité a été prise à ce niveau. Le comment se comprend en lisant un code correctement structuré.

Cette frontière, je ne l'ai pas tracée seul. J'ai posé la question directement à l'IA : est-ce que ça vaut la peine d'ouvrir un couloir documentaire pour ces micro-décisions d'hygiène ? La réponse a été non, et je l'ai suivie. J'ai déjà écrit que l'IA est une façon très sophistiquée de se parler à soi-même, et le risque de chambre d'écho est réel. Mais à mesure que les modèles gagnent en capacité critique, ce dialogue rétrospectif sur la méthode elle-même prend de la valeur : il s'élève, il challenge, et il en ressort une boucle d'amélioration continue de la méthodologie. Assumer cette boucle fait partie, elle aussi, du caractère opinionated du projet.

Je serais tenté d'écrire que l'ablation a joué son rôle ici. Ce serait tricher. Je n'ai rien retranché ; j'ai seulement évité d'ajouter. Ne pas ajouter n'est pas retrancher. Au mieux, c'est du contrôle des dégâts, de petits arbitrages au fil de l'eau pour éviter de faire enfler un corpus existant. L'exercice que Cherny décrit — supprimer, puis regarder — reste devant moi.

## La boussole

S'il y a un élément que je ne retrouve ni dans le mode plan des grands fournisseurs, ni dans les méthodologies open source qui circulent en ce moment, c'est celui-ci : des valeurs de projet et des valeurs méthodologiques qui servent de boussole, plutôt que de contrainte.

Le mode plan, à mesure qu'il s'améliore, apprend à juger de ce qui est raisonnable dans l'absolu, en s'appuyant sur des milliers d'interactions issues de contextes très différents. Ma méthodologie porte quelque chose de plus étroit et de plus intime : les priorités propres à ce projet précis. La règle du quatre-vingt-vingt, le pragmatisme plutôt que l'élégance abstraite, la distinction entre complexité essentielle et complexité accidentelle. Ce ne sont pas des règles qu'on applique mécaniquement ; elles servent de point de repère quand aucune règle précise ne s'applique clairement. C'est exactement ce qu'une boussole fait : elle n'indique pas le chemin exact, elle indique une direction cohérente.

Ces valeurs se transposent à la méthode elle-même : la rigueur doit rester proportionnée à l'enjeu, pas maximisée par principe. Une anecdote illustre le genre de principe qu'on découvre sur le tas. En revenant à froid sur des plans déjà bien travaillés, j'ai remarqué que reprendre une session ancienne pousse à douter à nouveau, à rouvrir des zones d'ombre déjà couvertes, à produire résumé sur résumé par bonne intention d'alignement. Passé un certain point, cette boucle ne clarifie plus rien. J'ai fini par inscrire dans la méthodologie qu'une incertitude, une fois adressée et consignée, est close : pas de réouverture sans signal nouveau. Un mécanisme de coupure qui rejoint ce que les modèles apprennent eux-mêmes : reconnaître quand c'est suffisant.

## Ce que je garde, ce que je laisse filer

Le verdict annoncé en ouverture, je peux maintenant le livrer sans détour. Le mode plan devient le chemin par défaut pour tout ce qui relève de la recherche, de la remise en question et de la comparaison d'alternatives ; il continuera de s'améliorer sans moi. La méthodologie est recompartimentée sur trois choses qu'il ne porte pas : les niveaux documentaires qui distinguent une vision d'une idée et d'un design, la découvrabilité bidirectionnelle qui relie chaque artefact à son intention, et la boussole des valeurs propre au projet. Tout le reste, directives de proactivité comme consolidation de mémoire, je le laisse filer vers l'évolution naturelle des modèles, quitte à le retrancher explicitement à mesure qu'ils le rendent superflu.

## Le miroir convoqué, pas encore regardé

Reste l'aveu promis. Cherny donne une consigne concrète : supprimez, puis regardez. Or, dans tout ce que je viens de raconter, je n'ai rien supprimé. Les moments où j'ai cru appliquer l'ablation étaient des abstentions : ne pas ajouter une contrainte, ne pas ouvrir un dossier, ne pas verrouiller un chemin. C'est une hygiène utile, mais ce n'est pas l'exercice. Et j'ai plaidé en chemin plus que je ne l'avais promis : le défaut devenu qualité, la lenteur devenue stratégie. Le miroir, je l'ai convoqué ; je ne l'ai pas encore regardé.

La vraie ablation documentaire d'Ezkey — retirer des morceaux du corpus et observer ce qu'un agent froid fait sans eux — n'a pas commencé. Cet article en est le préambule, pas le compte rendu.

Il y aura du travail. Le corpus ressemble encore à une pieuvre : des plans pas tous intégrés, des documents qui se prétendent canoniques mais ne font que référencer d'autres éléments, des tentacules un peu partout dans le projet. La convergence vers une vérité canonique unique demande un travail intentionnel qui n'est jamais terminé, et je continue d'y faire des erreurs. Je sais aussi que d'autres méthodologies open source gagnent en maturité. Un jour, l'une d'elles couvrira peut-être mieux que la mienne ce que j'essaie de faire ; le bon réflexe sera alors de m'en approcher plutôt que de m'accrocher — l'ablation appliquée, cette fois, à une méthodologie entière.

En attendant, je me retrouve avec un sentiment que je n'ai pas envie de résoudre trop vite : une fierté d'avoir construit quelque chose qui tient, et un inconfort réel à l'idée d'avoir peut-être créé un monstre un peu prétentieux. Je ne suis pas méthodologiste de métier. Je suis un concepteur backend qui apprend lentement à s'organiser, et qui a fini, chemin faisant, par écrire une méthodologie qu'il n'avait pas prévu d'écrire. Le principe d'ablation ne me donne pas de réponse définitive à cette tension. Il me donne une discipline pour continuer à me poser la question, session après session : est-ce que ce que je garde mérite encore d'être gardé, ou est-ce que je le garde simplement parce que je l'ai déjà écrit ? Je préfère rester avec cette gêne plutôt que de la faire taire. Ni passif, à laisser le mode plan tout faire à ma place ; ni trop directif, à imposer une structure figée à un partenaire qui évolue plus vite que moi. Et, la prochaine fois que le miroir se présentera, prêt à le regarder pour vrai.

## **Fin de l'article - Début de la critique**

J'ai lu le draft en entier. Il est bon — la voix est tenue, l'autocritique est sincère, et la thèse centrale (recompartimenter plutôt que raser) est juste. Mais deux problèmes structurels affaiblissent sa valeur d'opinion, et le texte est nettement trop long pour ce qu'il démontre.

Le diagnostic principal, d'abord
L'essai pose le test d'ablation et ne le passe jamais. Cherny donne une consigne concrète : supprime ton fichier de directives, tes skills, tes hooks, et regarde. Tu reprends le principe, tu le tournes contre toi — puis tu ne supprimes rien. Aucun passage ne dit « j'ai retiré X et rien n'a manqué », ni même « voici ce que je vais retirer ». Les trois moments où tu écris « l'ablation a joué son rôle » (§ géométrie variable, § corpus, § conclusion) décrivent en fait des abstentions : je n'ai pas ajouté de contrainte, je n'ai pas ouvert de dossier, je n'ai pas verrouillé. Ne pas ajouter n'est pas retrancher. Un lecteur attentif verra que le miroir a été convoqué mais pas regardé.

C'est aussi ce qui explique que le verdict annoncé en ouverture — « recompartimenter » — ne soit jamais livré. À la fin, le lecteur ne peut pas énoncer la nouvelle répartition. Elle existe pourtant dans le matériau : mode plan par défaut, et la méthodologie réduite à trois choses que le mode plan ne porte pas (les niveaux hiérarchiques, la découvrabilité bidirectionnelle, la boussole des valeurs). Il manque quatre lignes explicites, avant la dernière section : voici ce que je garde, voici ce que je laisse filer. Ce seul ajout ferait passer le texte de « réflexion » à « position ».

Deuxième problème, plus dangereux parce qu'un lecteur sceptique le repérera : une contradiction non traitée. La section « Ce que le mode plan ne fait pas » affirme qu'il n'a pas vocation à porter la mémoire à travers les mois, à relier une décision de mai à une contrainte de février. Deux sections plus loin, tu décris le mode dreaming : un job qui relit cent sessions passées et reconstruit une mémoire consolidée. Les deux sections sont adjacentes et ne se parlent pas. La distinction qui sauverait l'argument est réelle et tu ne l'écris pas : dreaming consolide la mémoire d'un agent, pas la mémoire d'un produit. L'un accumule des préférences de travail, l'autre porte une intention structurée avec des niveaux et des liens. Dis-le, et l'argument devient beaucoup plus solide qu'aujourd'hui.

Dans la même veine, « et pour toujours » dans un titre de section, et les deux « ne fera jamais », sont les affirmations les plus fortes du texte et les moins défendues. Elles décrivent un périmètre produit actuel, pas une loi. « Ne fait pas aujourd'hui, et n'a pas vocation à » coûte peu et te protège.

Longueur
Environ 4 800 mots dans le corps publiable, soit près de vingt minutes de lecture. C'est long pour le site, et surtout long par rapport au nombre d'idées distinctes : j'en compte cinq ou six, étalées sur neuf sections. Une réduction de 20 à 25 % ne coûterait aucun argument. Trois coupes évidentes :

La section « géométrie variable » fait trois métiers à la fois : le défaut devenu qualité, l'adoption lente des sous-agents, l'autonomie vis-à-vis des systèmes de billetterie avec la souplesse branche/PR. Le troisième bloc (trois paragraphes, de « Un autre exemple de cette même souplesse » jusqu'à la fin sur le fork avec Jira) est intéressant mais c'est un autre sujet — la portabilité de la méthode — et c'est précisément là que le fil de l'ablation se perd. Un paragraphe suffirait, ou un autre article.

L'adoption lente et le mode dreaming défendent exactement la même thèse avec deux exemples : recevoir plutôt que construire. Les fusionner en une seule section supprimerait une redite complète.

Enfin, le motif « c'était déjà écrit dans mon corpus » apparaît trois fois, avec la même conclusion à chaque fois (« le bon réflexe était déjà là, simplement pas assez visible »). La troisième occurrence se lit comme de l'autosatisfaction.

Répétitions
Au-delà de celle-là, quatre tics reviennent assez pour être visibles :

« Laisser le marché / les modèles faire le travail à ma place » revient quatre fois (ouverture, § mode plan fait mieux, § adoption lente, § dreaming). C'est ta bonne idée, mais elle s'use.

L'annonce d'honnêteté — « l'honnêteté m'oblige », « je dois faire un autre aveu », « je dois quand même nuancer, honnêtement » — revient quatre fois. Quand un essai déclare sa propre honnêteté à répétition, l'effet s'inverse : ça devient défensif. L'honnêteté se démontre par le contenu, pas par l'étiquette.

« Ce serait prétentieux / ridicule / un monstre prétentieux » : trois occurrences, dont la dernière est la chute. Les deux premières désamorcent la troisième.

Et le rythme ternaire — « trop lourdes, trop rigides, trop loin » ; « plus strictes, plus explicites, plus contraignantes » ; « sans Jira, sans billets, sans aucun outil » ; « ni comité, ni rôle, ni phase » — revient une dizaine de fois. C'est ta signature, mais à cette densité c'est un automatisme perceptible.

Style
Le registre est juste et tenu, c'est la vraie force du texte. Trois réserves.

Les paragraphes sont tous longs, entre cinq et neuf phrases, sans variation sur tout l'article. Le seul paragraphe court (« Cela dit, une fois cette humilité admise… ») respire nettement mieux que ses voisins. Place-en trois ou quatre aux moments de bascule.

Le tiret cadratin apparaît une vingtaine de fois. Beaucoup de ces incises diluent la phrase au lieu de l'accentuer. En supprimer la moitié — certaines deviennent des phrases autonomes, d'autres de simples virgules — rendrait de la force aux endroits où le tiret compte vraiment.

Deux phrases plient sous leur propre poids : celle du § ablation sur « le carcan même que je viens de décrire », qui empile trois relatives, et la dernière du § « ce que le mode plan fait mieux », qui enchaîne trois subordonnées après « ce n'est pas gagner en discipline ». Les couper en deux suffit.

Détails de titres : « et pour toujours » sonne comme une surenchère et jure avec la sobriété de « Ce que le mode plan ne fait pas » juste après. Et « La géométrie variable était une qualité » divulgue la révélation que la section met deux paragraphes à construire — « Ce que je prenais pour un défaut » garderait la tension. Enfin, trois repères temporels relatifs s'empilent (« récemment », « voilà quelques mois », « il y a quelques mois », « il y a moins de huit mois ») ; sur une page datée, un ou deux suffisent.

Ce qui devrait être renforcé, par ordre de gain
Le coût manquant. Nulle part on ne lit ce que la méthodologie te coûte réellement : du temps par session, de la charge mentale, des documents à maintenir, ou un moment précis où elle t'a ralenti ou t'a fait produire quelque chose d'inutile. Tu donnes des volumes (« des dizaines de documents ») mais pas une gêne vécue. Sans cela, « elle a grossi » reste une abstraction et le lecteur ne peut pas calibrer le verdict. Une seule anecdote de la méthodologie qui se met en travers vaudrait plus que toute la section sur la géométrie variable.

Le constat « pas assez visible » est sous-exploité. Tu le traites comme un problème de classement — les bons garde-fous existent, ils sont juste enfouis. C'est en réalité la preuve directe de ta thèse : si le corpus est assez épais pour que ses propres meilleures règles y disparaissent, c'est le corpus qui est trop épais. Et une règle que personne ne lit est exactement le candidat idéal à l'ablation. Cette connexion est à un centimètre du texte et n'est pas faite. C'est la meilleure amélioration disponible, parce qu'elle est plus inconfortable et donc plus crédible.

La promesse « sans me défendre » n'est pas tout à fait tenue. L'ouverture annonce un miroir sévère ; les sections « géométrie variable » et « boussole » trouvent surtout des raisons d'être rassuré (« c'est ce qui m'a sauvé », « j'ai résisté », « le bon réflexe était déjà là »). L'aveu de la section 6 est converti en vertu dès le paragraphe suivant. Ce n'est pas malhonnête, mais un lecteur exigeant sentira que le procès s'est terminé par un acquittement rapide. Soit tu assumes ce basculement en le nommant, soit tu laisses une charge sans réponse.

La comparaison RUP mérite d'être mieux tranchée. Tu la poses puis tu la congédies par une liste d'absences. L'échec du RUP n'était pas le volume, c'était le caractère obligatoire. Tu le frôles (« pas de phase obligatoire ») sans le formuler. Optionnel contre obligatoire, c'est ta meilleure défense et elle tient en une phrase.

Deux détails mineurs. La décision méthodologique déléguée à l'IA elle-même (« je lui ai posé la question directement : est-ce que ça vaut la peine de créer un couloir pour ça ? ») est l'une des choses les plus intéressantes de l'article et elle est expédiée en une phrase — développe ou coupe. Et la mise en garde opinionated de la section 6 est excellente et arrive tard ; elle gagnerait à être promue plus haut, elle achète de la crédibilité.

Dernier point de forme : le front matter annonce « huit mouvements », le corps en compte neuf après l'introduction.

J'ai tâché de tenir compte des commentaires très pertinents que tu as émis. J'aimerais que tu refasses maintenant une lecture et voir où ça nous amène. Révise donc les observations que tu avais faites à la lumière de ce que l'article contient maintenant et vois ce qui est maintenant plus honnête (pun intended) et ce qui mériterait encore une amélioration.

Measured: the publishable body is now 3,360 words, about a fourteen-minute read. (My earlier "≈4,800" was an eyeball estimate that ran high — judge length off this figure, not that one.)

Où ça nous amène
Les deux problèmes structurels sont réglés, et l'un des deux est devenu la colonne vertébrale du texte. L'article ne se contente plus de convoquer le miroir : il constate lui-même qu'il ne l'a pas regardé, et il en fait son aveu final. C'est un vrai gain, parce que la faiblesse est convertie en la chose la plus crédible de l'essai.

Le dispositif fonctionne sur trois appuis, ce qui n'est pas un hasard : l'annonce en ouverture (« un aveu que je garde pour la fin, parce qu'il concerne le miroir lui-même »), le refus au § corpus (« Je serais tenté d'écrire que l'ablation a joué son rôle ici. Ce serait tricher »), puis la chute (« Cet article en est le préambule, pas le compte rendu »). « Ne pas ajouter n'est pas retrancher » est la meilleure phrase de l'article — quatre mots qui font le travail que quatre paragraphes ne faisaient pas avant.

La contradiction dreaming est mieux traitée que ce que j'avais suggéré. Tu ne te contentes pas de distinguer mémoire d'agent et mémoire de produit : tu plantes l'objection au § précédent (« une évolution récente semble, à première vue, la contredire »), tu la fais porter par le lecteur (« On pourrait m'objecter »), et tu refuses la victoire facile en concluant sur la complémentarité. « L'un apprend comment je travaille ; l'autre retient ce que je construis, et pourquoi » est la formulation la plus nette du texte.

Le reste des correctifs a pris : le verdict existe maintenant comme section autonome ; le retournement du « pas assez visible » en preuve de la thèse est en place et c'est l'un des passages les plus forts (« un corpus assez épais pour perdre ses propres meilleures règles est un corpus trop épais ») ; le RUP est tranché sur l'obligation plutôt que le volume, ce qui est ta meilleure défense ; l'acquittement rapide est nommé dans le texte et repayé à la fin ; la fusion adoption lente + dreaming supprime une redite complète ; les tirets cadratins sont passés d'une vingtaine à une demi-douzaine ; « prétentieux » ne survit qu'une fois, à la chute, où il porte enfin.

Ce qui mérite encore du travail
1. Le coût reste un sentiment, et c'est maintenant le point faible le plus visible — précisément parce que tu l'as promis. Le paragraphe fait la chose honnête (« je n'ai pas d'anecdote spectaculaire à offrir ») puis substitue le doute, l'hésitation, « ça se paie en temps, session après session ». Aucune scène, aucun ordre de grandeur. Dans un article dont le sujet est mesurer par soustraction, ne rien mesurer de ce qu'on pourrait soustraire se remarque. Or le matériau existe : tes notes de travail portaient des chiffres réels, et le corps se contente de « des dizaines », « une vingtaine », « une quinzaine », « largement dépassé le stade anecdotique ». Deux nombres réels au § miroir feraient plus pour la crédibilité du verdict que tout le paragraphe sur le doute. Et une scène — quel document tu as failli couper, pourquoi tu t'es arrêté — vaudrait mieux qu'une description de l'hésitation en général.

2. Le préambule n'a pas de date, et c'est ce qui l'empêche d'être un engagement. Cherny donne une cadence : tous les six mois. Tu la cites en ouverture et tu ne l'adoptes jamais. Le verdict dit « quitte à le retrancher explicitement à mesure qu'ils le rendent superflu » — un conditionnel suivi d'un différé indéfini. La conclusion dit que l'ablation « n'a pas commencé », ce qui est honnête, mais un préambule sans premier candidat, sans moment et sans protocole d'observation est un préambule renouvelable à l'infini. Tu as déjà le candidat sous la main : les garde-fous enfouis que le § miroir vient d'identifier. Une phrase suffirait — quoi, quand, et comment tu regardes ce qui manque. C'est l'amélioration la plus rentable qui reste, et c'est ce qui donnerait un appui à « prêt à le regarder pour vrai » ; sinon, cette dernière ligne a la forme exacte d'une résolution qu'on ne tient pas.

3. Deux coutures structurelles. Le paragraphe agent-memory / product-memory est probablement le plus analytique de l'article, et il est logé dans une section intitulée « Recevoir plutôt que construire » — un titre qui annonce une posture passive alors que ce paragraphe fait un travail actif, au bénéfice du verdict. La promesse est posée au § « Ce que le mode plan ne fait pas » et honorée deux sections plus loin : le report est long. Vérifie s'il ne finit pas mieux là où il est annoncé.

Et le mouvement final compte maintenant quatre temps, dont un plus faible : la pieuvre. C'est un reliquat de l'ancienne section « le vrai chantier », et il s'intercale entre les deux beats les plus forts (l'aveu, puis la gêne) pour faire une liste de tâches générale. Sa place naturelle est en haut, près de l'argument du « corpus trop épais », où il servirait de preuve au lieu de diluer la confession.

4. Longueur. 3 360 mots, c'est tenable pour cette lane, mais trois endroits restent mous. Les deux paragraphes du § « Ce que le mode plan ne fait pas » sont les plus longs de l'article et défendent un point chacun ; le premier énumère trois fois ce que le mode plan permet de faire. Au § boussole, la définition de la boussole est donnée deux fois de suite. Et le rappel du moratoire tient en deux phrases, puisqu'un article lié le développe.

5. Détails. « J'ai écrit, dans ce même article » se lit d'abord comme cet article-ci ; il s'agit du précédent — à désambiguïser. Les apartés métanarratifs sont devenus un tic léger : cinq occurrences où le narrateur sort du texte pour commenter son écriture ; deux les méritent (l'acquittement, le « ce serait tricher »), et « je m'arrête avant de le commencer » est la plus dispensable. « Le questionnement d'ablation » est une périphrase gauche née du souci de ne pas surclamer. Enfin, dreaming lit « jusqu'à une centaine » de transcriptions selon tes propres faits vérifiés ; « des dizaines » sous-vend gratuitement.

Le verdict global : l'article est passé de réflexion à position, et la position est maintenant honnête sur sa propre incomplétude. Ce qui manque n'est plus de la lucidité — c'en est plutôt le prolongement : un chiffre, une scène, et une date.