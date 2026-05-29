---
status: published
audience: "Développeurs et architectes côté back-end qui n'osent pas toucher au front, opérateurs d'admin, lecteurs intéressés par la collaboration humain–IA appliquée au craft. Ton essai personnel, voix en je, lane « Craft & engineering »."
planned_slug_fr: "peindre-un-admin-ui-en-collaboration-avec-l-ia.html"
planned_canonical: "https://ezkey.org/fr/peindre-un-admin-ui-en-collaboration-avec-l-ia.html"
published_html_fr: /fr/peindre-un-admin-ui-en-collaboration-avec-l-ia.html
published_html_en: /painting-an-admin-ui-with-ai.html
published_date: 2026-05-26
html_amended_post_publish: false
source_of_truth: html
---

# Peindre un admin UI en collaboration avec l'IA

<!-- ezkey-org:exclude-start
Titres de travail alternatifs :
1. Le gars qui ne savait pas peindre
2. Couches successives sur un canevas d'admin
3. Comment j'ai construit un admin UI sans savoir dessiner
4. Backend, IA et toile blanche : récit d'un admin UI Ezkey
5. De la console Python à l'admin React : six mois en couches

Positionnement : essai personnel, lane « Craft & engineering ». Filigrane discret de la peinture (mère technicienne, père décalques, fils art naïf/abstrait → couches sur la toile). À utiliser au maximum à trois endroits visibles : ouverture (I), transition III→IV, clôture (IX). Le reste du texte parle technique, opérateur et collaboration humain–IA.

Liens internes à intégrer dans la version publiée sans alourdir le corps :
- /fr/du-code-a-l-intention-workflow-ia.html (from-code-to-intent-ai-workflow)
- /fr/retour-aux-sources-developpeur-ere-ia.html (ai-return-to-foundations-methodology)
- /fr/du-monolithe-au-contrat.html (from-monolith-to-contract)
- /fr/manifeste-codage-ia.html (ai-coding-manifesto)

Notes rédactionnelles :
- Ne pas céder à la tentation d'objectiver une comparaison à WebAuthn / FIDO2 ; l'article parle d'élaboration d'UI, pas de comparaison de protocoles.
- Pas de lien vers le dépôt GitHub (encore privé).
- Citer le stack réel uniquement (cf. research-admin-ui-git-history.md).
- Ton humble sur la sécurité : « j'ai découvert que ce que j'appelais nonce s'appelait en fait CSRF token, et que le bon couple était HttpOnly + SameSite + CSRF ».
- Pas de section « lecture en X minutes ».
- Le verbatim oral d'origine n'a pas survécu à la compaction du transcript. Il sera recollé ici dans la sous-section dédiée plus bas, dès qu'il sera repassé. La rédaction publiable du corps ne dépend pas de ce verbatim ; elle est ancrée sur le rapport Git.
ezkey-org:exclude-end -->

<!-- ezkey-org:exclude-start
Pointeur recherche :
La base factuelle qui sous-tend ce draft est consignée dans `sites/ezkey-org-editorial/fr/research-admin-ui-git-history.md` (353 commits uniques sur `main`, fenêtre 2026-01-01 → 2026-05-15, regroupés en 19 sections thématiques). Ce fichier n'est pas publié. Toute affirmation chronologique ou technique du corps publiable doit pouvoir être tracée vers ce rapport. Les huit zones marquées 🆕 ont été arbitrées en Phase 2 ; les décisions sont reportées telles quelles dans la structure du draft.
ezkey-org:exclude-end -->

<!-- ezkey-org:exclude-start
Brain dump verbatim (trace historique, jamais publié) :

[À recoller : le brain dump oral original d'environ 3000 mots décrivant — la peinture de la mère, les décalques du père, le rapport personnel à l'art naïf et abstrait ; l'inception à partir d'un TUI Python ; le passage à tenant-ui puis admin-ui ; la « couche de fond » horizontale entité par entité ; les itérations dashboard, pagination, i18n, aide contextuelle, navigation détail, time zone, mode démo, identité opérateur ; le revirement sur la résolution des FK numériques ; la sécurité (terminologie « boiteuse » côté nonce/replay) ; et l'effet amplificateur de la collaboration humain–IA. À paster intégralement dans ce bloc.]
ezkey-org:exclude-end -->

## I. Le gars qui ne savait pas peindre

Ma mère peignait. Vraiment peindre, au sens technique : couleur, perspective, lumière, geste sûr. Mon père, lui, fait des décalques — un papier translucide posé sur une image, un crayon qui reproduit le trait. Le décalque, c'est une discipline honnête : on n'invente rien, on copie bien. J'ai grandi entre les deux et je n'ai hérité ni de la main de l'une, ni de la patience de l'autre. Ce que j'ai gardé, c'est un goût pour l'art naïf et abstrait — les œuvres qui n'essaient pas de tromper l'œil, qui assument la maladresse, qui posent des aplats et avancent par couches.

Je suis développeur back-end. Pendant des années, j'ai construit des services, des modèles de données, des APIs, des moteurs. Les interfaces graphiques, je les laissais à d'autres. Je savais lire un CSS, brancher un formulaire, faire tenir une page. Mais peindre un vrai admin UI, avec ses listes, ses filtres, ses pagination, ses détails croisés, ses messages d'erreur, ses tooltips, ses traductions — je n'avais ni la main, ni l'instinct, ni l'envie.

Et pourtant, sur Ezkey, j'ai dû le faire. Il fallait une console d'administration pour piloter les tenants, les intégrations, les administrateurs, les utilisateurs, les clés API, les enrôlements et les tentatives d'authentification. Pas de designer, pas d'équipe front. Juste moi, l'IA dans l'IDE, et une toile blanche.

Cet article raconte comment cet admin UI est apparu, en couches, sur environ quatre mois. Ce n'est pas un article de méthode pure. C'est un récit honnête d'un back-end qui n'aurait pas su peindre seul, et qui a quand même fini par poser quelque chose de cohérent sur la toile grâce à un outil qui a comblé exactement les lacunes qu'il fallait.

## II. D'un TUI Python à un admin UI React

À la fin janvier 2026, je n'étais pas encore prêt à attaquer une interface graphique. Ezkey avait besoin d'une façon d'inspecter ses entités sans passer par la base de données et sans s'inventer un client Postman pour chaque écran. <!-- ezkey-org:exclude-start
Note pour la génération EN (hand-off ultérieur) :
Dans la phrase qui suit, la formulation correcte du source oral est « écran, liste, sélection au clavier » (singulier), et non « écrits, listes ». La version FR publiée a été corrigée en ce sens (validation utilisateur, 2026-05-26). La traduction EN doit refléter cette logique : il s'agit de l'écran TUI, de la liste affichée, et de la sélection au clavier — pas de « writings/lists ».
ezkey-org:exclude-end -->
La première itération a donc été une console en texte : un TUI Python interactif, écran, liste, sélection au clavier, accès direct aux endpoints d'administration. Quelques jours pour le rendre utile, quelques semaines pour le rendre confortable.

Le TUI a joué son rôle. Il a rendu visible la structure du back-end. Il a forcé les premières règles d'opération : qu'est-ce qu'on liste, qu'est-ce qu'on filtre, qu'est-ce qu'on peut faire évoluer côté lifecycle. Mais il avait deux limites évidentes. La première : un opérateur non-développeur ne s'en servirait jamais. La seconde : il s'arrêtait au texte. Pas de tableau triable, pas de pastilles d'état, pas de drill-down, pas de tooltips, pas de mode démo. Toute la richesse d'une vraie console d'opération restait hors d'atteinte.

Fin février, j'ai donc démarré une première interface graphique sous le nom de `tenant-ui`. L'idée initiale était plus modeste qu'un admin complet : exposer ce qu'un administrateur de tenant aurait à voir. Au bout d'une semaine, deux choses sont apparues. D'abord, la frontière tenant / global allait être plus poreuse qu'imaginé : un Global Admin et un Tenant Admin partagent une grosse moitié de leur surface, et dupliquer l'effort serait absurde. Ensuite, la stack que je posais sous `tenant-ui` pouvait servir l'admin global sans modification.

Début mars, j'ai assumé le pivot : `tenant-ui` est abandonné comme produit autonome, son code est replié dans un seul `ezkey-admin-ui`, et le récit redémarre sur cette nouvelle base. Les plans de l'ancienne ligne ont été archivés proprement plutôt que supprimés — la trace est plus utile que le silence.

Le stack technique qui a porté tout ce qui a suivi est volontairement orthodoxe et minimal : React 19 et React DOM 19, TypeScript, Vite comme bundler, TanStack Query pour le cache serveur et l'invalidation, React Router pour la navigation, React Hook Form et Zod pour les formulaires et leur validation, i18next et react-i18next pour la localisation, Tailwind CSS pour la mise en forme, lucide-react pour les icônes, et Orval pour générer le client typé à partir du contrat OpenAPI exposé par l'Admin API. Pas de bibliothèque de composants prêt-à-monter type Material UI ou Chakra. Pas de framework UI maison. Rien de propriétaire. Chaque brique est ce qu'un développeur React 2026 trouverait familier dès la première lecture du `package.json`.

Je dois faire un aveu, à ce stade. De cette soupe aux lettres, la plupart, je n'en connaissais même pas le nom six mois plus tôt. À la vue de tout ceci, pour un modeste développeur back-end, pourrait-on sérieusement envisager que toute cette richesse de construction UI eût été possible sans l'apport absolument considérable et déterminant de la collaboration humain–IA ?

Cette discipline du stack n'est pas de la coquetterie : c'est ce qui m'a permis, à moi qui ne suis pas un développeur front, de m'appuyer sur l'IA sans lui demander d'inventer des conventions. Tout ce qu'elle proposait s'accrochait à des bibliothèques qu'elle connaissait par cœur. La part de risque restait dans le métier — le lifecycle d'Ezkey, les contraintes opérateur, les règles d'éligibilité — pas dans le rendu. Restait à savoir par où commencer.

## III. La couche de fond : largeur avant profondeur

Sur la toile, on commence par couvrir. On ne dessine pas un œil avant d'avoir étendu un fond. Si on attaque un détail trop tôt, il tombe dans le vide et on doit le refaire quand on remplit ce qui l'entoure. C'est, je crois, le seul vrai héritage technique que j'ai pris à la peinture sans avoir su peindre : on respecte l'ordre des couches.

J'ai appliqué la même règle à l'admin UI. Avant d'aller dans la finesse d'un seul écran, j'ai voulu poser toutes les entités de bout en bout. Tenants, administrateurs, intégrations, utilisateurs, clés API, enrôlements, tentatives d'authentification, audits, paramètres : chacun devait avoir une page de liste, une page de détail, un bouton qui marche, un état visible. Pas pour qu'elles soient belles. Pour qu'elles existent.

Cette passe horizontale a tenu sur quelques jours intenses début mars. Le déclic technique a été d'introduire Orval dès cette première passe : le contrat OpenAPI publié par l'Admin API devient automatiquement un client TypeScript typé, avec des hooks TanStack Query générés pour chaque endpoint. Plus de DTOs écrits à la main, plus de couches de service à inventer. Le mapping entre une opération métier et un appel HTTP redevient direct, et je peux dépenser mon attention sur ce qui est vraiment de l'UI : l'agencement, le feedback, l'enchaînement.

Cette couche de fond avait des trous évidents. Beaucoup d'écrans n'affichaient pas encore d'erreur propre. La pagination était parfois locale, parfois inexistante. Les FK étaient des nombres bruts. Les tooltips n'existaient pas. C'était voulu : mieux vaut une toile entièrement couverte d'aplats provisoires qu'une zone très détaillée à côté d'une zone vide. Une fois la toile couverte, on peut commencer à peindre.

## IV. Les couches de finition

À partir du moment où toutes les entités existaient, le travail est devenu un enchaînement de passes ciblées. Chacune touche plusieurs écrans à la fois, parce que chacune est portée par un mécanisme transversal — un hook, un composant, une convention. Ce n'est jamais « refaire un écran ». C'est toujours « poser une couche » qui se dépose en même temps sur la moitié de la console.

### Le Dashboard, du squelette au vrai outil

Le Dashboard est l'écran qui résume le mieux le passage de l'aplat à la finition. Le 8 mars, c'est une page squelette qui affiche un compteur de tentatives d'authentification en attente. Une semaine plus tard, il sait s'auto-rafraîchir et s'afficher en français. Le 14 avril, il publie un véritable widget de santé d'authentification et un agrégat d'enrôlements ; le même jour, les pastilles deviennent cliquables et chaque chiffre du tableau ouvre la sous-liste filtrée dans l'écran d'audit ou de tentatives. Le Dashboard cesse alors d'être un panneau d'affichage et devient un point d'entrée opérationnel. Plus tard encore, certaines incohérences entre statistiques côté backend et états réels d'une intégration retirée ont dû être réalignées — un rappel utile que le Dashboard est dépendant du sérieux des compteurs qu'on lui sert.

### Pagination, tri, dates : la base devient uniforme

La première version de l'admin UI utilisait des modes de pagination hétérogènes : certains écrans paginaient côté client, d'autres pas du tout, d'autres avec une API serveur dont la pagination ne suivait pas le même contrat. Au tout début de mars, le simple alignement du paramètre de tri sur toutes les listes a déjà créé l'effet d'unification. Dans la foulée, la migration vers les hooks Orval a uniformisé la façon de demander une page : même signature, même mode de cache, même invalidation. Puis, par vagues, chaque entité a basculé en pagination serveur, avec navigation première / dernière page, sélection de taille, et tri activable colonne par colonne. À la fin mars, l'archivage du plan d'« uniformité de pagination » côté Admin API a signalé que la couche était posée : la cohérence ne dépendait plus d'un effort par écran, mais d'un contrat partagé.

### Internationalisation et erreurs lisibles

Le 13 mars, la couche d'internationalisation est passée d'un coup : toutes les pages principales — administrateurs, utilisateurs, intégrations, clés API, enrôlements, tentatives, audits — ont reçu leurs clés de traduction le même jour. C'est l'une des journées les plus visibles dans l'historique Git : un blitz d'une douzaine de commits qui transforme un admin uniquement anglais en console proprement bilingue, avec sélection de langue persistée.

La couche complémentaire est arrivée début avril, du côté des erreurs. L'Admin API avait migré ses réponses d'erreur vers le format RFC 9457 (ProblemDetail) : un objet structuré, avec un `type`, un `title`, un `status`, un `detail` et des champs d'extension. Le client UI savait désormais que toute réponse d'erreur suivait la même forme. À partir de là, un catalogue d'erreurs lisibles en français a pu être construit, paramétré, et présenté à l'opérateur sans exposer de stack trace ni de message brut. La précision de cette correspondance n'est pas encore complète à la date de cet article — certains messages restent génériques — mais le rail est posé.

### Aide contextuelle et navigation détail

Une console d'administration vit par ses petites aides. À la mi-mars, un composant de tooltip et de popover a été introduit. Dans la même séquence, un système d'aide contextuelle a complété chaque écran principal avec un petit bouton `?` qui ouvre un texte court expliquant ce que la page représente et ce qu'on peut y faire. Ce n'est pas de la documentation longue ; c'est un repère, posé à l'endroit où l'opérateur regarde quand il hésite.

La même logique vaut pour la navigation. Fin mars, les pages de détail ont reçu une navigation précédente / suivante qui respecte le tri courant de la liste : on lit un tenant, on passe au suivant, on revient au précédent sans repasser par la liste. Mi-avril, les audits liés à une entité ont gagné leur propre navigation contextuelle. Début mai, plusieurs écrans de détail ont ajouté du feedback inline et une meilleure mise en évidence des actions récentes — l'opérateur a une idée plus claire de ce qui vient de se passer sur l'entité qu'il regarde.

### Time zone et timestamps qui veulent dire quelque chose

Mi-avril, la stratégie de fuseau horaire au niveau du tenant a été câblée jusque dans l'admin UI. Une date affichée dans la console est explicitement liée à un fuseau connu, et les libellés relatifs (« il y a quelques minutes ») cohabitent avec les libellés absolus là où l'opérateur a besoin de précision. Début mai, les timestamps d'audit ont été enrichis pour mieux servir une corrélation d'incident, et le suivi d'« activité récente » d'un enrôlement a reçu une vraie surface UI.

### Mode démo : une console qu'on peut montrer

Dès le 8 mars, un mode démo a été introduit. Quand il est actif, la console montre des données synthétiques cohérentes, et certains scénarios pré-écrits servent d'exemple. Un premier thème, « Garage du coin », a illustré un cas PME. Un deuxième, « InterCube », a illustré un cas plus institutionnel. Plus tard, des badges « raison de la démo » et des présets localisés ont rendu le mode plus crédible pour montrer à quelqu'un sans risquer de salir une vraie base.

C'est une petite couche, dans l'absolu. Mais c'est l'une des plus rentables : elle a permis de montrer Ezkey à voix haute en réunion sans devoir s'excuser de la pauvreté des données réelles, et elle a obligé l'UI à être robuste face à des jeux d'entités complets, pas seulement face aux trois lignes du back-end local.

### Identité de l'instance et de l'opérateur

Mi-mars, l'en-tête a reçu un logo et une boîte « À propos » qui dit clairement à quelle instance on est connecté et à quelle version. Fin mars, un endpoint public d'information d'instance a été publié, et l'écran de connexion ainsi que l'Auth API ont commencé à l'utiliser. Ce sont des couches très discrètes — un opérateur ne les remarque pas s'il les voit la première fois — mais leur absence se sent : sans elles, l'opérateur ne sait jamais sur quelle instance il agit.

Ces couches-là se sont posées à peu près dans l'ordre. D'autres ont demandé d'être reprises.

## V. Les retours en arrière assumés

Il y a deux genres de couches qu'on pose mal : celles qu'on ne voit pas tout de suite, et celles qu'on défend par principe. Trois revirements méritent d'être rappelés ici, parce qu'ils résument une posture qui a beaucoup compté dans la suite.

Premier revirement : la pagination. La première vague d'écrans n'a pas paginé. C'était un raccourci honnête : la moitié des entités avaient cinq lignes en local, et l'effort de pagination uniforme paraissait disproportionné. La réalité a tranché vite. Dès qu'une démo a chargé deux mille audits ou cinq cents tentatives d'authentification, la console est devenue inutilisable. La pagination serveur uniforme n'est pas devenue une option : elle est devenue le contrat. Le plan d'uniformité a été écrit, exécuté, archivé.

Deuxième revirement : les foreign keys numériques. Il y a eu un moment, très dogmatique, où je trouvais légitime de montrer à l'opérateur un identifiant brut — « ce champ référence l'entité X dont l'ID est 42 ». L'argument était mince mais tenace : c'est honnête, c'est sans ambiguïté, c'est ce que le back-end stocke. La réalité d'usage a tranché aussi vite que pour la pagination : un opérateur ne lit pas des nombres, il lit des noms, des libellés, des descriptions courtes. Par vagues, les FK ont commencé à se résoudre en libellés lisibles, parfois avec un petit lien vers la fiche correspondante. Ce travail n'est pas terminé. Il avance par diffusion plutôt que par décret, et c'est sain : chaque écran reçoit la résolution quand son contexte d'usage la rend prioritaire.

Troisième revirement : le stockage du jeton d'authentification de l'UI. La première implémentation utilisait du `localStorage`, comme à peu près toute application React qui démarre vite. Ce choix marche, jusqu'à ce qu'on s'assoie sérieusement devant le modèle de menace d'une console d'administration. Il a été remplacé. La couche est devenue transversale, et elle mérite sa propre section.

## VI. La sécurité comme couche transversale

Je dois être honnête sur un point : pendant longtemps, ma terminologie de sécurité côté web était approximative. Je parlais de « nonce », je parlais de « replay », je mélangeais des concepts qui se côtoient sans se confondre. Travailler la sécurité de la console d'administration m'a forcé à apprendre les bons mots, parce que le code, lui, ne tolère pas l'approximation.

Ce que la console fait aujourd'hui repose sur trois briques, mises bout à bout sur la fin avril 2026. D'abord, l'authentification de session n'est plus portée par un jeton stocké en `localStorage` lisible par n'importe quel script de la page. Elle est portée par un cookie de session marqué `HttpOnly`, donc inaccessible au JavaScript du navigateur, et marqué `SameSite`, donc protégé contre la majorité des contextes de requête croisée. Ensuite, et c'est la partie que j'appelais maladroitement « anti-replay », un mécanisme de protection CSRF a été câblé : chaque opération mutante de la console s'accompagne d'un jeton CSRF qu'un attaquant tiers ne peut pas connaître ; le serveur refuse silencieusement l'opération si le jeton est absent ou ne correspond pas. Enfin, la durée d'expiration de la session de l'admin a été alignée sur le cycle d'authentification — le couplage est explicite, plutôt que laissé à deux configurations désynchronisées.

À cela s'ajoute une discipline d'auditing : chaque action significative d'un admin laisse une trace, et ces traces sont elles-mêmes navigables depuis la console. C'est aussi de la sécurité, au sens où elle déplace une partie du contrat de confiance de la mémoire individuelle vers une couche persistée et inspectable.

Je ne prétends pas que cette pile soit l'état de l'art absolu. Je prétends deux choses. D'abord, qu'elle utilise les bons mots et les bons mécanismes pour le couple « console d'administration + back-end Spring », et que ces mots — `HttpOnly`, `SameSite`, CSRF token — sont ceux que tout développeur web sérieux peut reconnaître et critiquer. Ensuite, qu'elle est arrivée en couche tardive, après que la console était déjà utilisable, et c'est probablement la séquence correcte : durcir une surface qu'on connaît est moins risqué que durcir une surface qu'on est en train de dessiner.

## VII. Activer un admin, le récupérer, fermer la porte

La sécurité de la session n'est pourtant que la moitié du contrat d'accès. Une console d'administration a aussi un cycle de vie d'accès qui se joue à côté du back-end : créer un administrateur, l'activer, lui permettre de revenir s'il s'est perdu, lui interdire l'entrée si son enrôlement est compromis. À partir de la fin mars, ce cycle est devenu un chantier en soi.

L'activation initiale d'un administrateur a été câblée comme un parcours séparé du login normal : l'administrateur reçoit un code à usage unique, le présente à la console, et n'entre dans le cycle MFA standard qu'une fois cette étape franchie. Quelques semaines plus tard, la possibilité de réémettre un code d'activation pour un administrateur encore en attente a été ajoutée — sans elle, la moindre faute de frappe dans une adresse mail aurait condamné un compte.

La récupération a suivi le même soin. Un mécanisme de codes de récupération a été pensé, écrit, documenté, puis intégré : un administrateur qui perd son appareil n'est plus dans une impasse. Le « copier l'identifiant d'enrôlement » a été ajouté à l'écran de récupération login, parce qu'un opérateur en panique a besoin d'un geste matériel, pas d'une procédure abstraite.

Enfin, la fermeture de porte : un plan dédié à la redirection vers la page de login lorsque le serveur répond `401` a été matérialisé, exécuté, archivé. La console ne reste plus jamais ouverte sur une vue de données quand la session a expiré. C'est un petit détail, mais c'est exactement le genre de détail qui distingue un outil qu'on utilise tous les jours d'un outil qu'on tolère.

## VIII. La qualité comme couche distincte

Pendant longtemps, la qualité de la console a été portée uniquement par des tests unitaires côté backend, par les tests de contrat OpenAPI, et par mon propre regard d'opérateur. C'était suffisant tant qu'il n'y avait qu'un opérateur et une seule machine en jeu. Cela ne suffit plus dès qu'on veut une UI qu'on peut faire évoluer sans tout casser à chaque passe.

Début avril, une suite Playwright a été introduite. Pas pour couvrir chaque pixel de la console, mais pour ancrer quelques scénarios critiques : login, navigation entre les entités principales, parcours sensibles. La logique a été assumée : les tests UI ne sont pas un automatisme — ils sont un choix éditorial. On en ajoute quand un parcours est critique, sensible, ou suffisamment complexe pour valoir la peine. On ne les multiplie pas pour gonfler un compteur. Cette posture est documentée dans les conventions de projet.

Sur la même fenêtre, un grand passage de propre a eu lieu : nettoyage du lint, normalisation des conventions de typage, harmonisation des composants partagés. Une console qui doit vivre demande une discipline de propreté que ses premières passes ne réclamaient pas.

## IX. L'amplificateur

Reviens un instant à la toile. Sur la toile, ce qui change tout entre un débutant et quelqu'un qui sait peindre, ce n'est pas le pinceau. C'est la capacité à anticiper les couches, à savoir laquelle poser avant l'autre, à se permettre des revirements quand une couche refuse de tenir. Le geste est un amplificateur. Sans la pensée des couches, il ne sert à rien. Avec elle, il devient le bras qui fait exister ce qu'on a déjà compris.

C'est exactement le rapport que j'ai eu avec l'IA dans l'IDE pendant ces quatre mois. Elle n'a pas peint l'admin UI à ma place. Elle n'a pas eu d'idée sur le mode démo, sur l'ordre des couches, sur le revirement FK, sur la stratégie de pagination, sur la pile de sécurité, sur la séparation entre l'activation et le login. Ces décisions sont le tracé du fond. Elles sont venues de l'usage opérateur, du modèle métier d'Ezkey, et de mon refus de m'écouter quand je voulais défendre un raccourci.

Ce que l'IA a fait, c'est rendre rentable mon manque de virtuosité côté front. Elle a posé des composants Tailwind cohérents quand je décrivais une intention. Elle a câblé un hook Orval correctement quand je décrivais un endpoint. Elle a raboté trois itérations d'un formulaire à ma place quand je n'avais pas l'énergie de relire le code de validation. Elle m'a soufflé la bonne signature d'un test Playwright quand je m'aventurais hors de ma zone. Et surtout, elle a pris des notes : chaque plan, chaque décision, chaque revirement assumé est resté écrit, parce qu'à chaque pas, j'ai pu lui demander de matérialiser ce qu'on venait de décider.

Six mois plus tôt, sans cette amplification, j'aurais probablement renoncé à écrire moi-même l'admin UI. J'aurais pris une console générique, accepté une UI étrangère au reste du produit, ou ralenti tout le reste du projet en attendant un développeur front. La collaboration a déplacé la frontière de ce que je pouvais raisonnablement me permettre.

Ce que je retiens, finalement, n'est pas un point sur la peinture. C'est un point sur l'honnêteté : un développeur back-end peut aujourd'hui poser une vraie console d'administration sans prétendre être ce qu'il n'est pas. Il lui faut un stack honnête, un contrat OpenAPI propre, une discipline des couches, le courage des revirements, et un amplificateur qui rend rentable son manque d'instinct. Pour le reste, il suffit de couvrir la toile, et de revenir, et de revenir encore.
