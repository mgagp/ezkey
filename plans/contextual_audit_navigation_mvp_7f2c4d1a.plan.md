## Plan: RFC Audits contextualises

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
10. Design technique - Preparer la phase 2. Une fois le MVP valide, ajouter depuis un audit precis une action du type "Elargir autour de cet evenement" avec une petite fenetre avant/apres, par exemple plus ou moins 15 minutes, et une possible simplification de certains filtres. C’est la bonne transposition de l’analogie CloudWatch.

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