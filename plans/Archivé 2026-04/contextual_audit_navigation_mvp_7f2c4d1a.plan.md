## Plan: RFC Audits contextualises

**Status**
- Archive 2026-04
- Completed
- Implemented
- Validated

Proposition: introduire un mecanisme de navigation d’investigation operationnelle depuis certaines fiches detail vers une vue des audits lies, sans melanger ce mecanisme avec la navigation structurelle existante par foreign keys. Le MVP vise a accelerer la comprehension d’une situation reelle avec un comportement simple, visible et maintenable: un bouton "Voir les audits lies" ouvre l’ecran audit avec le bon filtre d’entite et une fenetre temporelle glissante par defaut sur les 24 dernieres heures. Cette premiere phase doit aussi etablir un pattern d’interaction reutilisable et uniforme pour toutes les fiches detail qui offriront ensuite cette capacite. Le comportement plus proche de CloudWatch, elargir avant/apres autour d’un evenement audit precis, est volontairement reporte a une phase suivante depuis l’ecran audit lui-meme.

**Steps**
1. RFC produit - Problem statement. Poser le probleme comme un manque de continuite entre l’investigation structurelle et l’investigation operationnelle. Aujourd’hui, l’Admin UI permet deja de suivre les relations entre entites, mais pas de basculer directement vers l’histoire audit pertinente autour d’un sujet de support ou d’incident. L’operateur doit reconstruire manuellement cette recherche dans l’ecran audit.
2. RFC produit - Objective. Definir l’objectif principal comme une reduction du cout cognitif d’investigation: depuis une entite detaillee, l’operateur doit pouvoir obtenir rapidement une vue initiale d’audits lies, suffisamment pertinente pour commencer l’analyse sans sur-ingenierie ni logique opaque.
3. RFC produit - Non-goals. Exclure explicitement du MVP la narration automatique de causalite, les filtres multi-entites implicites, la recherche plein texte dans reason/error/details, la logique OR/expansion intelligente, et le vrai recentrage avant/apres autour d’un evenement de reference. Cela protege la lisibilite du produit et la maintenabilite.
4. RFC UX - Interaction model. Ajouter une action secondaire distincte du bouton existant de details lies. Le bouton recommande est "Voir les audits lies". Sa semantique est: ouvrir l’ecran audit dans un contexte prefiltre, puis laisser l’operateur elargir ou simplifier la recherche. Cette phase doit servir de reference d’interaction pour les autres entites, afin de conserver une experience uniforme autour des objets de detail. Cette etape depend d’un contrat URL stable sur l’ecran audit.
5. RFC UX - Default context policy. Quand l’utilisateur arrive depuis une entite, appliquer une fenetre temporelle par defaut sur les 24 dernieres heures jusqu’a maintenant. Cette fenetre est simple, credible depuis une entite detaillee, et alignee sur un usage support/operations. Elle evite de simuler artificiellement un modele CloudWatch sans evenement de reference.
6. RFC UX - Explainability. Ajouter un microcopy ou un indicateur de preset dans la vue audit pour rendre le contexte d’entree explicite: audits lies a cette entite sur les 24 dernieres heures. L’utilisateur doit comprendre qu’il s’agit d’un point de depart, pas d’une histoire exhaustive.
7. Design technique - Stabiliser le contrat URL de l’ecran audit dans c:\github\ezkey-worktree2\ezkey-admin-ui\src\pages\audit-logs.tsx. Les filtres doivent devenir deep-linkables via query params. Le contrat MVP doit couvrir les filtres deja exposes et les filtres contextuels d’entite: eventType, eventStatus, apiName, createdAfter, createdBefore, enrollmentId, integrationId, authAttemptId, plus eventuellement un petit marqueur de preset contextuel.
8. Design technique - Completer le contrat API de c:\github\ezkey-worktree2\ezkey-admin-api\src\main\java\org\ezkey\admin\controller\AuditLogController.java et c:\github\ezkey-worktree2\ezkey-core\src\main\java\org\ezkey\audit\service\AuditLogService.java. EnrollmentId existe deja. Le MVP a besoin d’ajouter integrationId et authAttemptId. Aucun enrichissement relationnel automatique ne doit etre encode cote backend au MVP.
9. Design technique - Ajouter les points d’entree UI dans c:\github\ezkey-worktree2\ezkey-admin-ui\src\pages\enrollment-detail.tsx, c:\github\ezkey-worktree2\ezkey-admin-ui\src\pages\integration-detail.tsx et c:\github\ezkey-worktree2\ezkey-admin-ui\src\pages\auth-attempts.tsx. La premiere implementation sur enrollment doit etre pensee comme un pattern de reference reutilisable pour les autres entites, avec la meme place dans l’UI, la meme logique de navigation et le meme contrat de filtre contextualise. Ces points d’entree doivent reutiliser les conventions deja presentes de query params et de navigation contextuelle dans l’Admin UI.
10. Design technique - Preparer le MVP2. Une fois le MVP valide, ajouter depuis un audit precis une action du type "Voir autour de cet evenement" avec un evenement ancre et un voisinage borne. Le mode recommande est: 10 evenements avant + evenement ancre + 10 evenements apres, avec un chargement incremental controle de 10 de plus avant ou apres. Cette phase doit reutiliser l’ecran audit existant, eviter toute correlation automatique multi-entites, et s’appuyer sur un endpoint dedie de voisinage autour d’une ancre plutot que sur une inflation du contrat de recherche existant. C’est la transposition la plus pragmatique de l’analogie CloudWatch.

**MVP2 Framing**
1. Objectif produit. Permettre a un operateur d’ouvrir le contexte immediat d’un log ambigu sans quitter l’ecran audit ni reconstruire manuellement une investigation. Le MVP2 ne cherche pas a "raconter l’incident", mais a reduire le cout cognitif de lecture locale autour d’un evenement ancre.
2. Point d’entree UX. Ajouter une action secondaire dans le detail d’un log audit: "Voir autour de cet evenement". Cette action n’est exposee que depuis le detail d’un log, pas depuis les fiches entites ni depuis d’autres surfaces.
3. Mode de lecture. Reutiliser c:\github\ezkey-worktree2\ezkey-admin-ui\src\pages\audit-logs.tsx avec un mode contextualise. Le log ancre est surligne; le bandeau de contexte explique que l’utilisateur regarde un voisinage d’investigation autour du log #ID; un bouton permet de quitter ce contexte.
4. Definition opinionated du voisinage. Par defaut: 10 evenements avant, l’evenement ancre, 10 evenements apres. Le modele en nombre d’evenements est prefere a une fenetre temporelle au MVP2 car il est plus simple a expliquer et plus directement utile en enquete.
5. Elargissement controle. Autoriser au maximum deux actions: "Charger 10 de plus avant" et "Charger 10 de plus apres". Ne pas reintroduire ici la pagination generale, ni une logique de filtres secondaires complexes.
6. Contrat API recommande. Ajouter un endpoint dedie de type voisinage autour d’une ancre dans c:\github\ezkey-worktree2\ezkey-admin-api\src\main\java\org\ezkey\admin\controller\AuditLogController.java, porte par une methode dediee dans c:\github\ezkey-worktree2\ezkey-core\src\main\java\org\ezkey\audit\service\AuditLogService.java. Entree minimale: anchorAuditLogId, beforeCount, afterCount. Sortie recommandee: items, anchorAuditLogId, hasMoreBefore, hasMoreAfter. Le scope tenant/global admin reste applique exactement comme pour la recherche standard.
7. Regles de tri et stabilite. Definir un ordre stable et explicable: createdAt puis auditLogId comme tie-break. Le voisinage doit etre coherent meme en presence de timestamps identiques.
8. Non-goals explicites. Pas de multi-ancre, pas de correlation automatique entre entites, pas de "logs similaires", pas de graphe, pas de DSL de recherche, pas de fusion intelligente avec tous les filtres existants, pas de promesse d’exhaustivite d’incident.
9. Valeur attendue. Le MVP2 est considere utile s’il accelere les enquetes courtes sur MFA, erreurs et sequences ambigues, sans obliger l’operateur a apprendre un nouveau modele mental. S’il faut expliquer des regles de voisinage trop complexes, la phase a depasse sa bonne limite.
10. Note de decision. Le choix retenu pour la phase suivante est volontairement opinionated: un outil d’inspection locale autour d’un evenement, pas une console d’investigation generale. Cette limite est explicite pour rester dans le 80-20, conserver la complexite fondamentale utile pour l’avenir, et eviter la derive vers de la complexite accidentelle a faible rendement.

**Decision Note — UX refinement after first implementation pass**
1. Observation. Le point d’entree "Voir autour de cet evenement" place uniquement dans le detail d’un log s’est revele trop peu decouvrable. Dans le flux reel, l’operateur est deja entre dans une intention d’investigation lorsqu’il arrive sur l’ecran audit via "Voir les audits lies". L’action d’extension de contexte doit donc vivre d’abord au niveau de la page contextualisee, pas etre cachee dans un deuxieme niveau de detail.
2. Clarification. Le vrai focus initial du MVP n’est pas seulement un type d’evenement, mais surtout un filtre contextuel d’entite (enrollmentId, integrationId, authAttemptId) combine a une fenetre de temps. Le risque operationnel n’est donc pas uniquement de manquer un evenement du meme type, mais aussi de passer a cote d’evenements voisins d’une autre nature qui peuvent etre pertinents dans la meme tranche temporelle.
3. Ajustement recommande. Le MVP2 doit privilegier une action de page du type "Etendre le contexte" a cote d’"Actualiser" lorsque l’ecran audit est ouvert dans un contexte provenant d’une entite. Cette action est un zoom-out controle depuis l’intention initiale de recherche.
4. Semantique recommandee. "Etendre le contexte" doit faire deux choses en meme temps, de maniere explicable: elargir la fenetre temporelle d’un cran et desserrer le focus contextuel d’entite pour laisser apparaitre une plus grande variete d’evenements dans la meme zone temporelle. Le but n’est pas de corriger la recherche initiale, mais d’ouvrir un second cercle d’investigation.
5. Garde-fou principal. Ne pas transformer cette extension en recherche generale libre ni en suppression silencieuse de tous les reperes. Le bon compromis est: garder le scope tenant implicite, garder un cadre temporel borne, annoncer clairement dans le bandeau que l’on regarde maintenant un contexte elargi, et fournir une sortie explicite pour revenir au focus initial.
6. Compromis produit propose. Au lieu de faire du detail-log l’entree principale, utiliser le detail-log au mieux comme option secondaire. L’action primaire du MVP2 devient un zoom-out de page contextualisee: depuis les audits lies d’un enrollment, d’une integration ou d’une auth attempt, l’operateur peut demander a voir "plus large" dans le meme voisinage temporel, y compris des evenements d’une autre nature.
7. Non-goals confirmes. Pas de correlation automatique multi-entites, pas d’heuristique causale, pas de graphe, pas de moteur d’investigation generique. L’extension reste un zoom-out borne et reversible, pas une console d’exploration universelle.
8. Note de design. Cette evolution corrige un defaut de decouvrabilite et rapproche mieux le produit de la pratique operatoire reelle: l’operateur commence avec une histoire ciblee, puis demande a voir le voisinage plus large sans perdre son cadre mental initial.

**Relevant files**
- c:\github\ezkey-worktree2\docs\ADMIN_UI.md — base de positionnement: l’Admin UI est une surface operatoire pragmatique orientee taches, pas une console decorative.
- c:\github\ezkey-worktree2\ezkey-admin-ui\src\pages\audit-logs.tsx — ecran central a rendre deep-linkable et destination unique des entrees contextuelles.
- c:\github\ezkey-worktree2\ezkey-admin-api\src\main\java\org\ezkey\admin\controller\AuditLogController.java — contrat HTTP de GET /api/v1/audit-logs a etendre.
- c:\github\ezkey-worktree2\ezkey-core\src\main\java\org\ezkey\audit\service\AuditLogService.java — logique de filtrage a etendre avec integrationId et authAttemptId.
- c:\github\ezkey-worktree2\ezkey-admin-ui\src\pages\enrollment-detail.tsx — point d’entree MVP le plus rentable.
- c:\github\ezkey-worktree2\ezkey-admin-ui\src\pages\integration-detail.tsx — point d’entree MVP cote integration.
- c:\github\ezkey-worktree2\ezkey-admin-ui\src\pages\auth-attempts.tsx — point d’entree MVP cote tentative d’authentification.
- c:\github\ezkey-worktree2\ezkey-admin-ui\src\components\feature\related-details-button.tsx — repere conceptuel pour bien distinguer navigation structurelle et navigation d’investigation.
- c:\github\ezkey-worktree2\ezkey-admin-ui\src\pages\enrollments.tsx et c:\github\ezkey-worktree2\ezkey-admin-ui\src\pages\admins.tsx — exemples existants d’ouverture contextuelle via query params.

**Verification**
1. Ajouter des tests backend sur GET /api/v1/audit-logs pour integrationId et authAttemptId, en confirmant que le scope tenant/global admin reste correct.
2. Verifier que l’ecran audit hydrate correctement ses filtres depuis l’URL et supporte refresh, navigation avant/arriere et partage de lien.
3. Executer un scenario navigateur cible au moins sur enrollment detail -> audit logs pre-filtres, puis elargissement manuel de la fenetre ou retrait d’un filtre.
4. Valider la comprehension UX avec une revue rapide: le bouton doit etre interprete comme "ouvrir des audits pertinents" et non comme "expliquer automatiquement toute l’histoire".

**Decisions**
- Inclus dans le MVP: enrollment, integration, auth attempt.
- Exclus du MVP: tenant, admin, API key, encryption key.
- Inclus dans le MVP: filtre centre sur l’entite elle-meme uniquement.
- Inclus dans le MVP: fenetre glissante de 24 heures jusqu’a maintenant.
- Exclus du MVP: expansion automatique aux relations voisines, logique OR, recherche plein texte, raisonnement causal implicite.
- La premiere implementation sur enrollment sert a etablir un pattern uniforme et reutilisable pour les autres vues detail qui exposeront ensuite "Voir les audits lies".
- Positionnement: le bouton "Voir les audits lies" complete la navigation structurelle existante; il ne la remplace pas.

**Final Note — Closure 2026-04-12**
1. Statut final. Le plan est complete, implemente et valide. Les slices MVP1 couvrent enrollment, integration et auth attempt. Le raffinement MVP2 retenu et implemente reste volontairement borne a un zoom-out de page, sans transformer l’ecran audit en console d’investigation generique.
2. Dernier arbitrage sur l’extension de contexte. Le point d’equilibre retenu est une extension de fenetre de 24h a 48h, et non 72h. La justification est pragmatique: 72h produisait trop vite du bruit operationnel dans un environnement deja charge, alors que 48h conserve un second cercle utile d’investigation sans diluer excessivement le signal.
3. Repere visuel retenu. Lors de l’extension de contexte, les lignes deja presentes dans le contexte cible initial restent reperables via un surlignage et un badge dedie. La justification est de conserver un ancrage cognitif clair apres le zoom-out, avec un cout de complexite UI faible et local.
4. Explainability finale. L’action d’extension et l’action de retour disposent d’une aide contextuelle legere. La justification est d’expliciter le comportement sans surcharger l’interface ni introduire un modele mental supplementaire.
5. Limite produit preservee. Le dossier est clos avec un resultat aligné sur le principe 80-20: navigation contextuelle utile, extension reversible, scope tenant preserve, pas de correlation automatique ni de moteur d’investigation generique.

**Further Considerations**
1. Si l’equipe veut une phase 0 encore plus simple, enrollment seul est le meilleur point de depart car l’API supporte deja enrollmentId et le besoin support y est frequent. Cette phase 0 a aussi l’avantage de valider le pattern complet avant extension aux autres entites.
2. Si la fenetre de 24 heures s’avere trop bruyante, il faut ajuster le preset global plutot que rajouter trop vite une logique differenciee par entite.
3. Si l’usage confirme le besoin, la phase 2 doit etre pensee comme un outil d’enquete depuis un audit precis, pas comme un enrichissement invisible du MVP.

**Entity Matrix**
- Enrollment
Contexte operatoire typique: un utilisateur n’arrive pas a se connecter, un enrolllement semble incoherent, ou un support veut comprendre ce qui s’est passe autour d’un device lie.
Filtre MVP recommande: enrollmentId uniquement.
Fenetre MVP: 24 dernieres heures.
Pourquoi inclure: ROI tres fort, deja aligne avec les workflows support et l’API actuelle.
Risque si on enrichit trop tot: bruit en ajoutant integration ou auth attempts sans controle explicite.

- Integration
Contexte operatoire typique: une application cliente rencontre des echecs ou un comportement anormal et l’operateur veut comprendre l’activite recente liee a cette integration.
Filtre MVP recommande: integrationId uniquement.
Fenetre MVP: 24 dernieres heures.
Pourquoi inclure: utile pour remonter du service vers ses evenements lies, sans forcer la navigation via enrollments.
Risque si on enrichit trop tot: derive vers des vues quasi tenant-wide si on inclut automatiquement toutes les entites enfants.

- Auth attempt
Contexte operatoire typique: une tentative precise parait suspecte, rejetee ou incomprise, et l’operateur veut retrouver les audits lies a cette tentative.
Filtre MVP recommande: authAttemptId uniquement.
Fenetre MVP: 24 dernieres heures, meme si l’identifiant suffit souvent a reduire le bruit.
Pourquoi inclure: c’est le point d’ancrage le plus proche d’un evenement reel cote authentification.
Risque si on enrichit trop tot: confusion entre analyse d’une tentative precise et analyse plus large de l’enrollment.

- Tenant
Decision MVP: exclu.
Raison: utile pour supervision large, mais moins focalise sur l’investigation support de proximite et plus susceptible de produire du bruit massif.
Phase ulterieure possible: preset plus large ou approches dediees Global Admin.

- Admin
Decision MVP: exclu.
Raison: cas reel mais plus ambigu, car il faut distinguer actions par un admin et actions ciblees sur un admin. La matrice est pertinente mais pas assez simple pour le premier jet.
Phase ulterieure possible: deux entrees distinctes ou un choix explicite.

- API key
Decision MVP: exclu.
Raison: cas d’usage plausible mais depend souvent d’une bonne correlation avec integration et erreurs operationnelles; le signal de depart est moins universel que pour enrollment.
Phase ulterieure possible: analyse dediee si les cas support le justifient.

- Encryption key
Decision MVP: exclu.
Raison: surface plutot Global Admin, plus rare, plus sensible, et avec une semantique d’investigation differente du support quotidien.
Phase ulterieure possible: workflow specifique plateforme/operations.
