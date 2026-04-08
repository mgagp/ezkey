## Plan: Gouvernance transverse des cycles de vie

Produire un document d’analyse principal qui unifie la logique des opérations de cycle de vie à l’échelle d’Ezkey. L’approche recommandée est: partir de l’état actuel documenté et implémenté, normaliser une taxonomie commune des actions (réversible vs irréversible, logique vs physique, justificatif requis ou non), cartographier les dépendances et blast radius entre entités, puis formuler un tableau de décisions cible utilisable ensuite pour des plans séparés backend, UI et documentation.

## Valeurs Directrices Du Projet Pour Cette Analyse

Cette analyse doit rester explicitement alignée sur l’ADN d’Ezkey, pas seulement sur l’état courant du code. Les règles de gouvernance qui en résulteront doivent être cohérentes avec un produit `developer first`, `backend first`, auto-hébergeable, pragmatique et volontairement opinionated.

1. **Backend first, developer first**. Ezkey assume un modèle de confiance où la source principale de sécurité et de cohérence se situe dans le backend, les API, les signatures, l’état persistant et les règles métier explicites. Le projet n’essaie pas d’imiter les approches centrées UI ou navigateur; il se positionne comme une alternative pragmatique et cryptographiquement solide, pensée pour une organisation qui héberge elle-même son instance et qui accepte des hypothèses plus contrôlées et plus lisibles opérationnellement.
2. **Alternative pragmatique aux approches UI-first**. Les choix conceptuels doivent rester compatibles avec ce positionnement: Ezkey n’a pas vocation à dépendre d’un écosystème externe complexe pour être pertinent. Il doit rester opérable comme système complet en lui-même, y compris dans des modes self-contained utiles pour validation interne, pré-pilotage ou auto-hébergement simple, même si cela implique des compromis assumés et clairement documentés.
3. **Portée significative à échelle humaine**. L’objectif n’est pas de modéliser tous les cas d’une plateforme IAM tentaculaire. Le système doit couvrir une portée réaliste et crédible pour des organisations de taille humaine, avec des concepts suffisamment puissants pour le marché visé, mais sans dériver vers une sophistication disproportionnée. La distinction Global Admin / Tenant Admin est un bon exemple de complexité acceptable car elle apporte une vraie valeur opératoire.
4. **80/20 et simplicité opératoire**. Entre deux options métier plausibles, privilégier celle qui résout l’essentiel du besoin avec le moins de structure conceptuelle supplémentaire. Les règles de cycle de vie doivent rester intuitives pour des opérateurs réels et suffisamment simples pour être comprises, documentées et maintenues sans lourdeur excessive.
5. **Auto-hébergement et autonomie fonctionnelle**. Les choix d’analyse doivent favoriser un système déployable et exploitable avec peu de dépendances externes obligatoires. Cela n’interdit pas des intégrations futures, mais la cohérence de base doit rester celle d’un produit capable d’exister de manière autonome, avec des compromis explicités plutôt que masqués.
6. **Complexité essentielle oui, complexité accidentelle non**. La complexité du domaine est acceptable lorsqu’elle sert l’opérabilité, la sécurité, l’intuition métier et la cohérence des concepts. En revanche, il faut minimiser la complexité accidentelle, notamment la prolifération de validations, d’exceptions et de branches de code qui rendraient l’implémentation finale disproportionnée par rapport au gain métier. Un bon résultat d’analyse est un cadre qui réduit la quantité de logique spéciale au lieu de la multiplier.
7. **Cohérence conceptuelle avant exhaustivité locale**. Les décisions de gouvernance doivent chercher l’intégrité conceptuelle à travers les entités, même si cela impose de simplifier certains cas particuliers. L’objectif n’est pas d’empiler des règles locales nées de tâtonnements historiques, mais de consolider ces apprentissages en un modèle transverse stable, compréhensible et implémentable.
8. **Réalisme opérationnel et implémentabilité**. Les scénarios retenus doivent être plausibles dans la vraie vie: investigation, compromission, suspension, retrait, reprise d’activité, administration quotidienne. Mais, entre plusieurs réponses réalistes possibles, il faut privilégier celle qui garde une implémentation finale aussi simple que possible. Le critère de choix n’est donc pas seulement “est-ce défendable en théorie ?”, mais aussi “est-ce cohérent avec l’ADN d’Ezkey et soutenable en code pour un projet porté principalement par un développeur principal aidé d’assistants de codage ?”.

Conséquence directe pour le cycle d’analyse à venir: toute recommandation devra être évaluée selon un double filtre explicite:

1. Est-ce que cette règle améliore réellement l’opérabilité, la sécurité et l’intuition du système pour les administrateurs Ezkey ?
2. Est-ce que cette règle préserve une implémentation suffisamment simple, lisible et maintenable pour éviter que le coût de validation et de code n’explose ?

Si une règle améliore marginalement le réalisme métier mais introduit une surcharge importante de validations, de statuts, de branches ou d’exceptions, elle devra être considérée avec suspicion et probablement écartée au profit d’une alternative plus simple.

## Heuristiques De Décision Pour La Suite

Pour garder l’analyse cohérente et actionnable, appliquer systématiquement les heuristiques suivantes quand plusieurs options semblent défendables:

1. **Privilégier la règle la plus simple qui reste crédible en exploitation réelle**. Si deux réponses métier paraissent raisonnables, retenir celle qui produit le moins de statuts spéciaux, le moins d’exceptions de parcours et le moins de validations croisées.
2. **Préférer le réversible avant l’irréversible** lorsqu’un scénario d’investigation ou de doute peut encore évoluer, sauf si la compromission confirmée ou le retrait définitif justifie clairement une coupure terminale.
3. **Préférer le blocage runtime à la cascade persistée** quand cela suffit à obtenir l’effet opératoire recherché, afin d’éviter de propager artificiellement des changements d’état complexes dans toute la hiérarchie.
4. **Préférer des concepts stables et réutilisables** plutôt que des règles ultra-locales propres à une seule entité, sauf si une asymétrie métier forte est réellement nécessaire.
5. **Ne pas introduire un nouveau type d’état ou de règle sans gain opératoire clair**. Chaque nouvel état, motif obligatoire, exception ou interaction parent/enfant doit payer son coût en lisibilité et en code.
6. **Arbitrer en faveur de l’intuition administrateur** quand deux modèles ont une sécurité comparable. Une règle facile à anticiper, à expliquer et à diagnostiquer sera généralement meilleure qu’une règle plus “pure” mais opaque.
7. **Considérer le coût complet de validation** avant de recommander une règle. Si une idée impose une explosion de garde-fous, de messages d’erreur, de cas limites et de tests, elle doit être fortement requestionnée.
8. **Préserver la capacité du projet à rester soutenable pour un développement principal solo**. Une bonne règle de gouvernance Ezkey doit rester implémentable et maintenable dans un cadre réaliste de développement assisté, sans exiger une machine bureaucratique de validations.

Ces heuristiques ne remplacent pas l’analyse métier; elles servent de garde-fous pour éviter que la consolidation conceptuelle dérive vers un modèle plus sophistiqué que nécessaire pour le marché et l’ADN du projet.

## Statut De Cette Session

Cette session doit maintenant être traitée comme une session de consolidation initiale, pas comme une session de recherche ouverte. Le corpus pertinent a déjà été suffisamment élargi pour démarrer la structuration du livrable principal. Le document de référence pour la suite reste ce fichier, et les sources amont désormais prioritaires incluent non seulement `docs/` et le code, mais aussi les artefacts de conception retenus dans `plans/` et `.github/prompts/`.

Le dossier racine `plans/` doit être compris comme un **corpus de travail temporaire**, créé explicitement pour soutenir cet effort d’analyse, puis les phases de conception et de code qui en découleront. Il ne doit pas être traité comme une archive exhaustive permanente. Son contenu peut donc être **fortement curé et réduit** pour ne conserver que les plans réellement utiles à ce chantier. Les originaux restent ailleurs; ici, l’objectif est le signal, pas l’exhaustivité.

Le prochain pas logique dans cette session est de figer un premier cadre de synthèse exploitable: source set priorisé, taxonomie transverse v1, structure du futur document principal, et critères explicites de décision. L’objectif n’est pas encore d’écrire tout le document final de gouvernance, mais de rendre la suite évidente, bornée et amorçable sans réouvrir une phase de fouille large.

## Portée De Consolidation Pour Cette Session

Cette session devrait aller jusqu’aux livrables intermédiaires suivants:

1. Stabiliser le corpus prioritaire qui servira de base au document principal, avec distinction entre sources canoniques, sources d’implémentation et sources d’intention/conception.
	Ce corpus doit être volontairement restreint. Pour les plans de conception temporaires, privilégier le dossier racine `plans/` comme corpus curé de référence pour ce chantier, et éviter d’entretenir inutilement un miroir large des plans historiques.
2. Produire une taxonomie transverse v1 des opérations de cycle de vie, avec définitions provisoires mais suffisamment claires pour éviter les faux synonymes entre activation, désactivation, révocation, retrait, suppression logique, suppression physique, expiration et actions purement runtime.
3. Fixer un squelette du document d’analyse principal avec les sections minimales obligatoires: état actuel, écarts, recommandations, matrice de décision, blast radius, scénarios opératoires, suites de travail.
4. Dresser une première liste explicite des tensions conceptuelles majeures à arbitrer dans la session suivante, plutôt que de les laisser implicites dans les notes de recherche.

Cette session n’a pas besoin d’aller jusqu’à une rédaction exhaustive du document final si cela dilue la qualité de la consolidation. Le bon seuil d’arrêt est atteint dès que la suite du travail n’a plus besoin d’une redécouverte des sources, mais seulement d’un travail d’écriture, de hiérarchisation et de décision.

## Critères De Clôture De Cette Session

La session sera logiquement close quand les conditions suivantes seront réunies:

1. Les sources amont prioritaires seront explicitement listées et hiérarchisées.
	Pour les plans temporaires de conception, la liste doit idéalement converger vers un sous-ensemble maîtrisé dans `plans/`, afin de réduire le bruit documentaire pour les sessions suivantes.
2. La taxonomie transverse v1 sera assez stable pour servir de base au document principal.
3. Le futur document d’analyse aura un plan détaillé de niveau section/sous-section, avec un ordre de production clair.
4. Les principales questions ouvertes seront formulées comme décisions à traiter, et non comme simples observations dispersées.
5. Le point de reprise pour la session suivante sera écrit noir sur blanc dans ce plan.

Quand ces cinq conditions sont remplies, poursuivre davantage dans cette même session devient moins rentable: on passe d’un travail de consolidation à un travail de rédaction lourde. C’est précisément le moment opportun pour clore cette session spécifique et en ouvrir une autre dédiée à la production du livrable principal.

## Point D’Amorçage Pour La Session Suivante

La session suivante devra démarrer à partir d’un corpus désormais considéré comme suffisamment exploré. Son point d’entrée ne devra plus être une recherche ouverte, mais l’assemblage du document principal `état actuel + écarts + recommandations` à partir des éléments déjà consolidés ici.

Point de départ recommandé pour la session suivante:

1. Reprendre ce fichier comme source de vérité pour le cadrage.
2. Utiliser la liste priorisée des sources et la taxonomie transverse v1 comme base de rédaction.
3. Produire d’abord la structure du document principal et la matrice de décision inter-entités avant d’entrer dans les scénarios détaillés.
4. N’ouvrir de nouvelles recherches que si une question bloquante apparaît, et non par réflexe exploratoire.

Statut attendu à inscrire implicitement à la fin de cette session: `recherche large terminée`, `consolidation initiale terminée`, `session suivante orientée rédaction/synthèse décisionnelle`.

**Steps**
1. Phase 1 — Inventaire transverse de l’existant. Consolider dans une seule matrice les opérations réellement présentes ou implicites pour chaque entité du noyau et des cas voisins: Global Admin, Tenant Admin, Tenant, Integration, Enrollment, Authentication Attempt, API Key, clés cryptographiques, recovery codes. Croiser la documentation existante avec les règles de service déjà implémentées pour distinguer clairement: ce qui est documenté, ce qui est appliqué en code, ce qui est incohérent, ce qui est encore conceptuel.
2. Phase 1 — Établir une taxonomie commune des opérations. Définir des catégories stables à réutiliser partout: création, activation, désactivation, réactivation, suspension temporaire, révocation, retrait conceptuel, suppression logique, suppression physique, expiration automatique. Pour chaque catégorie, préciser l’intention opératoire, la réversibilité, le besoin de justification, l’audit attendu, et les préconditions/postconditions minimales.
3. Phase 2 — Modéliser la hiérarchie et les dépendances. Construire une vue domaine montrant les relations Global Admin/Tenant Admin → Tenant → Integration → Enrollment → Authentication Attempt, avec les branches API Key, clés cryptographiques et recovery codes. Identifier, par type d’opération, le blast radius attendu sur les entités filles, les effets de blocage, les effets immédiats sur l’authentification, les tokens, les sessions, les créations futures et les opérations déjà en cours.
4. Phase 2 — Produire une matrice de décisions par entité et par opération. Pour chaque croisement entité/opération: définir si l’action est autorisée, interdite, conditionnelle, idempotente, réversible ou terminale; préciser l’impact descendant et remontant; distinguer les conséquences runtime des changements d’état persistés; noter les motifs de sécurité et d’opérabilité. Cette matrice doit mettre en évidence les asymétries actuelles, par exemple tenant désactivé sans cascade persistée, intégration avec état INACTIVE incomplet, API key révoquée sans raison, enrollment avec double sémantique status/active.
5. Phase 3 — Ancrer les décisions dans des scénarios opératoires réalistes. Formaliser quelques scénarios de référence qui servent de test conceptuel: investigation de compromission d’un enrollment, fuite probable d’une API key, suspension d’une intégration suspecte, désactivation d’un tenant en situation de crise, retrait d’un administrateur, rotation ou retrait de clé cryptographique. Pour chaque scénario, expliciter l’action recommandée, pourquoi elle est réversible ou non, et le niveau de risque accepté.
6. Phase 3 — Définir la cible harmonisée. À partir des écarts observés, proposer un cadre cible simple et intuitif: quand utiliser désactivation vs révocation vs suppression; quand imposer un motif; quelles actions doivent être possibles en bulk; quels effets doivent être purement runtime vs matérialisés; quels états doivent être exposés dans l’UI; quelles opérations doivent rester impossibles pour préserver l’intégrité conceptuelle.
7. Phase 4 — Structurer le livrable d’analyse principal. Prévoir un document de synthèse avec: résumé exécutif, taxonomie commune, diagramme Mermaid des entités et dépendances, tableaux de décision, matrice blast radius, scénarios opératoires, écarts entre existant et cible, et liste ordonnée des décisions à transformer plus tard en plans backend/API/UI/documentation. Cette phase dépend des phases 1 à 3.
8. Phase 4 — Préparer le chaînage vers les travaux ultérieurs. Extraire du document principal une liste de suites de travail sans les détailler à l’excès: harmonisation des règles backend, alignement des RFC 9457 et erreurs métier, modèle d’audit/justification, guidage UI (tooltips, libellés, disable states, confirmations), tests et documentation opératoire. Cette étape dépend du document principal et ne traite pas l’implémentation.

**Relevant files**
- c:/github/ezkey-worktree3/PRD.md — cadrage produit, rôles opérateurs, principes de simplicité, pragmatisme, contrôle opérationnel.
- c:/github/ezkey-worktree3/README.md — synthèse du produit et des surfaces Admin UI/Admin API/Auth API/Integration API.
- c:/github/ezkey-worktree3/docs/ENDPOINT.md — source transversale des endpoints et des opérations de cycle de vie déjà exposées.
- c:/github/ezkey-worktree3/docs/ADMIN_UI.md — surface opératoire UI et périmètre d’administration humaine.
- c:/github/ezkey-worktree3/docs/ADMIN_PROVISIONING_DEPROVISIONING_PROCEDURE.md — modèle le plus abouti de justification, audit et idempotence côté administrateurs.
- c:/github/ezkey-worktree3/docs/API_KEYS_IMPLEMENTATION.md — règles métier et contraintes de révocation des API keys.
- c:/github/ezkey-worktree3/docs/API_KEYS_HOW_IT_WORKS.md — scénarios de rotation/compromission des API keys.
- c:/github/ezkey-worktree3/docs/RECOVERY_CODES_LIFECYCLE_ANALYSIS.md — exemple de cycle de vie avec irréversibilité et remplacement complet.
- c:/github/ezkey-worktree3/docs/SPEC_ENCRYPTION_KEY_LIFECYCLE_STRATEGY.md — modèle à deux couches pour distinguer état technique et état opérateur.
- c:/github/ezkey-worktree3/.github/prompts/plan-ezkeyEntityLifecycleGovernance.prompt.md — source de vérité de cadrage pour ce chantier.
- c:/github/ezkey-worktree3/.github/prompts/plan-adminUiLifecycleAndDangerZones.prompt.md — patterns UX de danger zones, réversibilité et guidage opérateur.
- c:/github/ezkey-worktree3/.github/prompts/plan-adminUiConceptsGapsAndRecommendations.prompt.md — écarts conceptuels UI/Admin API déjà identifiés autour des statuts et actions.
- c:/github/ezkey-worktree3/plans/tenant_lifecycle_revision_plan_b3e5bdea.plan.md — source amont prioritaire sur la symétrie Active ↔ Inactive et les limites du deactivate-only pour les tenants.
- c:/github/ezkey-worktree3/plans/enrollment_lifecycle_revocation_b476b2e1.plan.md — source amont prioritaire sur la séparation revoke / deactivate / reactivate et les angles morts de sécurité.
- c:/github/ezkey-worktree3/plans/api_key_lifecycle_review_40070814.plan.md — source amont prioritaire sur l’asymétrie du modèle API key (revoke-only, pas de réactivation).
- c:/github/ezkey-worktree3/plans/key_lifecycle_strategy_e4debaae.plan.md — source amont prioritaire sur la dissociation entre statut technique, rôle cryptographique et état opérateur.
- c:/github/ezkey-worktree3/plans/reason_ux_review_53ddefc2.plan.md — source amont prioritaire sur la sémantique des justifications et leur rôle dans les actions de cycle de vie.
- c:/github/ezkey-worktree3/plans/admin_management_gaps_and_soc2_36506da5.plan.md — source complémentaire utile sur le cycle de vie des administrateurs, les garde-fous et l’alignement audit.
- c:/github/ezkey-worktree3/plans/system_tenant_semantic_rules_ce3ff3d1.plan.md — source complémentaire utile sur les contraintes sémantiques du tenant système et les opérations interdites.
- c:/github/ezkey-worktree3/plans/bulk_deactivate_reactivate_enrollments_8b203bfc.plan.md — source complémentaire utile sur les actions bulk et leur blast radius opératoire.
- c:/github/ezkey-worktree3/plans/integration_delete_constraint_ux_658880a2.plan.md — source complémentaire utile sur les contraintes de suppression d’intégration et la frontière entre règle métier et erreur technique.
- c:/github/ezkey-worktree3/ezkey-admin-api/src/main/java/org/ezkey/admin/service/TenantService.java — activation/désactivation tenant et effets de sécurité immédiats.
- c:/github/ezkey-worktree3/ezkey-admin-api/src/main/java/org/ezkey/admin/service/AdminProvisioningService.java — garde-fous admin, raisons obligatoires, idempotence, minima globaux.
- c:/github/ezkey-worktree3/ezkey-admin-api/src/main/java/org/ezkey/admin/service/EnrollmentRevocationService.java — séparation révocation irréversible vs désactivation réversible sur les enrollments.
- c:/github/ezkey-worktree3/ezkey-admin-api/src/main/java/org/ezkey/admin/service/EnrollmentUpdateService.java — restrictions de modification selon le statut et l’activité.
- c:/github/ezkey-worktree3/ezkey-core/src/main/java/org/ezkey/integration/service/IntegrationService.java — retraite, suppression et lacunes autour de INACTIVE.
- c:/github/ezkey-worktree3/ezkey-core/src/main/java/org/ezkey/integration/service/ApiKeyService.java — révocation, expiration automatique, limites d’usage des API keys.
- c:/github/ezkey-worktree3/ezkey-core/src/main/java/org/ezkey/authattempt/service/AuthAttemptService.java — dépendance directe aux statuts d’enrollment.
- c:/github/ezkey-worktree3/ezkey-admin-ui/src/pages/tenant-detail.tsx — pattern UI de désactivation/réactivation tenant.
- c:/github/ezkey-worktree3/ezkey-admin-ui/src/pages/integration-detail.tsx — bulk actions, retire/delete, danger zone intégration.
- c:/github/ezkey-worktree3/ezkey-admin-ui/src/pages/enrollment-detail.tsx — coexistence deactivate/reactivate/revoke/delete.
- c:/github/ezkey-worktree3/ezkey-admin-ui/src/pages/api-key-detail.tsx — révocation API key et limites du guidage UX.

**Verification**
1. Vérifier que chaque entité retenue apparaît dans la matrice finale avec ses opérations autorisées, interdites et conditionnelles.
2. Vérifier que chaque opération du vocabulaire cible a une définition unique et non ambiguë, applicable de façon cohérente ou explicitement exclue selon l’entité.
3. Vérifier que chaque relation parent/enfant dispose d’une note de blast radius pour les actions réversibles et irréversibles.
4. Vérifier que les écarts entre documentation, implémentation backend et comportements UI sont explicitement listés et priorisés.
5. Vérifier que les scénarios opératoires couvrent au minimum: suspicion de compromission enrollment, compromission API key, suspension intégration, désactivation tenant, retrait administrateur, gestion des clés cryptographiques.
6. Vérifier que le livrable se termine par une liste ordonnée de décisions et de suites de travail séparées, sans dériver vers l’implémentation.

**Decisions**
- Inclure le noyau métier et les cas voisins: clés cryptographiques et recovery codes.
- Produire un livrable principal de type document d’analyse avec tableaux de décision et diagrammes Mermaid.
- Positionnement attendu: état actuel + écarts + recommandations.
- Le plan reste volontairement conceptuel et transverse; il ne descend pas au niveau attribut par attribut ni au niveau patch API/UI.
- Les suppressions logique, conceptuelle et physique doivent être traitées comme catégories distinctes, mais seulement là où elles ont un sens métier et opérationnel.
- Les décisions doivent être justifiées par l’opérabilité réelle d’un système de sécurité, pas uniquement par les contraintes techniques existantes.

**Further Considerations**
1. Recommander d’ajouter dans le document final une distinction explicite entre état persistant, capacité opérationnelle effective et visibilité UI, afin d’éviter les faux équivalents entre “inactive”, “blocked”, “revoked” et “deleted”.
2. Recommander de traiter séparément les effets sur les opérations futures et les effets sur les sessions/tokens/opérations déjà engagées, car ce sont deux sources majeures de confusion actuelle.
3. Recommander de faire émerger un principe directeur simple: en contexte d’investigation, privilégier d’abord l’action réversible à blast radius maîtrisé, et réserver l’irréversible aux cas de compromission confirmée ou de retrait définitif.
