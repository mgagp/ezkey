## Plan: Pré-pilotage expérimental EZKey

Préparer un pilote limité, auto-hébergé et pragmatique en restant au plus près du stack Docker existant, avec Cloudflare comme façade publique, AWS comme origine principale, et un petit chantier produit séparé pour un onboarding admin “recovery-codes-first”. L’approche recommandée est de démarrer avec une origine AWS publique mais strictement filtrée pour n’accepter que Cloudflare, puis de garder l’option “origine masquée” comme variante à comparer plus tard si la posture de sécurité ou l’exploitation l’exige.

**Steps**
1. Phase 0 — Débroussaillage et cadrage de référence.
   Valider le périmètre exact du pré-pilote: quelques dizaines d’utilisateurs, faible trafic, audience technique prête à installer un APK debug, objectif principal d’apprentissage DevOps/Cloudflare/AWS/MCP plutôt que disponibilité quasi-production. Formaliser les non-objectifs dès le départ: pas de scale-out complexe, pas de haute disponibilité multi-région, pas de distribution mobile grand public, pas de durcissement “enterprise complete” au premier passage.
2. Phase 0 — Choisir la topologie cible de départ.
   Recommandation: Cloudflare DNS/WAF/TLS en frontal, AWS comme origine, Caddy à l’origine pour centraliser le reverse proxy vers les APIs, Admin UI séparée côté Cloudflare, base de données gardée dans le stack Docker au tout premier laboratoire si la simplicité prime, avec un point de décision explicite entre PostgreSQL conteneurisé, RDS, ou Supabase pour l’étape suivante. Prévoir deux variantes documentées: option A “origine publique filtrée à Cloudflare seulement” comme chemin principal; option B “origine masquée via tunnel/proxy dédié” comme alternative à évaluer après le premier apprentissage.
3. Phase 1 — Établir le socle d’exposition sécurisé.
   Définir les sous-domaines publics, la stratégie TLS, le chemin réseau, et le point d’entrée unique. Vérifier que seul Cloudflare peut atteindre l’origine AWS. Définir le rôle de Caddy: terminaison/proxy interne, en-têtes de sécurité, routage vers admin-api, auth-api, integration-api. Cadrer aussi les flux de l’Admin UI déployée côté Cloudflare vers l’Admin API, y compris CORS, CSP, et URLs publiques utilisées dans les QR codes et les flows d’onboarding.
4. Phase 1 — Sécuriser les fondations minimales avant exposition.
   Activer et vérifier les trusted proxies, le rate limit applicatif, la restriction d’accès origine, la stratégie de certificats, la séparation des secrets, et la révision des rôles base de données. Prévoir un minimum de journalisation, santé applicative, sauvegarde, et restauration. Le but n’est pas la perfection mais d’éviter les oublis structurants: origine exposée directement, headers proxy mal fiables, secrets statiques, base sans sauvegarde, récupération impossible des clés.
5. Phase 2 — Définir le modèle d’accès expérimental au service.
   Formaliser le parcours opérateur: page apex easykey.org avec appel à contact via info@easykey.org, tri manuel des demandes, création du tenant, création du tenant admin, envoi d’un courriel de réponse avec URL Admin UI, URL APK, consignes de side-loading, et codes de récupération. Cette phase doit aussi définir les limites du programme: audience visée, prérequis Android, niveau de support attendu, politique de révocation et d’arrêt du programme.
6. Phase 2 — Planifier le mini-chantier produit pour l’onboarding “recovery-codes-first”.
   Puisque tu veux le traiter comme une mini-évolution produit, planifier un ajout explicite plutôt qu’un bricolage opérateur sur les flows actuels. Le point central à étudier est l’extension du provisioning admin pour permettre un mode “enrollment deferred” ou équivalent, tout en gardant la génération de recovery codes et l’audit. Cette phase doit trancher entre deux sous-options: option recommandée, ajout d’un flag de création d’admin avec enrollment différé; option plus légère, conservation de l’enrollment obligatoire mais clarification d’un flow de reset/activation contrôlé. Le plan d’exécution ultérieur devra comparer impact API, impact UI, impact audit, et cohérence produit avec la philosophie break-glass actuelle.
7. Phase 2 — Définir le paquet opérateur pilote.
   Préparer les artefacts nécessaires au pilote: gabarit de réponse email, mini-guide d’installation APK debug, avertissement sur l’absence de canal de mise à jour automatique, mini-guide d’enrôlement initial, procédure de support de premier niveau, procédure de reset/recovery, et message de posture de risque adapté à un public développeur niche.
8. Phase 3 — Construire le laboratoire DevOps/MCP.
   Organiser le futur travail de recherche et d’outillage pour permettre une gestion agentique de l’environnement. Le périmètre à préparer est: structure des environnements, variables et secrets, conventions de déploiement, journaux utiles, checklists d’exploitation, et commandes reproductibles. L’objectif n’est pas seulement de déployer EZKey, mais de rendre chaque opération suffisamment explicite pour être pilotable par agents plus tard.
9. Phase 3 — Définir les décisions d’infrastructure par incréments.
   Démarrer au plus simple avec le stack Docker existant comme laboratoire contrôlé. Ensuite, poser un point de décision formel: conserver PostgreSQL dans Docker pour le tout premier pilote fermé, ou migrer rapidement vers RDS/Supabase si l’exposition réelle, la sauvegarde, ou la gestion des secrets rendent la version purement conteneurisée trop fragile. Cette étape doit inclure un tableau de critères simple: effort, sécurité, restauration, portabilité, coût, et compatibilité avec une gestion agentique.
10. Phase 4 — Préparer la validation avant ouverture limitée.
   Vérifier end-to-end le parcours suivant: accès Cloudflare vers origine, Admin UI opérationnelle, login admin, création tenant, création tenant admin, réception du matériel d’onboarding, installation APK, enrôlement, puis premiers tests d’usage. Ajouter une vérification ciblée des protections: trusted proxy, impossibilité de joindre directement l’origine autrement que via Cloudflare, rate limiting, récupération admin, et cohérence des URLs publiques.
11. Phase 4 — Ouvrir progressivement et apprendre.
   Commencer avec un nombre très limité de personnes, observer les incidents réels, noter les blocages d’onboarding, ajuster la documentation opérateur, puis seulement ensuite envisager le passage à un mode plus stable de stockage de données, de signature applicative Android, et de déploiement plus durable. Le succès du pré-pilote n’est pas un volume d’usage élevé; c’est l’obtention d’apprentissages concrets sur le déploiement, la posture de sécurité, les flows d’administration, et l’usage agentique/MCP.

**Section spécifique — Mécanisme d’onboarding expérimental “recovery-codes-first”**

Cette section décrit le mécanisme d’onboarding envisagé pour le pré-pilote expérimental, afin qu’il puisse être repris tel quel comme base de travail dans une session ultérieure dédiée à la planification détaillée ou à la conception produit.

**Intention**
Permettre à un public restreint de développeurs intéressés par EZKey d’obtenir un accès expérimental sans exiger un enrôlement préalable au moment exact de la création du tenant admin. Le principe est de garder un parcours très manuel, très assumé, et compatible avec le positionnement Developer First / Pragmatic: demande par courriel, tri humain, provisioning manuel par le Global Admin, puis enrôlement du tenant admin à partir d’un matériel de récupération envoyé hors bande.

**Parcours cible à haut niveau**
1. Un intéressé consulte la page apex du projet sur easykey.org.
2. La page indique qu’un accès expérimental peut être demandé via info@easykey.org.
3. Le Global Admin reçoit la demande, effectue un tri manuel, puis décide d’accepter ou non l’accès expérimental.
4. Si la demande est acceptée, le Global Admin crée un tenant dédié pour cette personne ou cette équipe.
5. Le Global Admin crée ensuite un tenant admin dans un mode spécial d’onboarding expérimental.
6. Dans ce mode, l’objectif est que le tenant admin soit créé avec des recovery codes utilisables pour démarrer le parcours, sans dépendre d’un enrôlement déjà complété au moment du provisioning initial.
7. Le Global Admin envoie un courriel de réponse contenant au minimum: l’URL de l’Admin UI, le lien de téléchargement de l’APK expérimental, les recovery codes, et une courte procédure d’installation/enrôlement.
8. Le participant installe l’APK debug sur son téléphone Android, accède à l’Admin UI, puis utilise le mécanisme de récupération prévu pour enclencher le flux de reset / bind / verify / enrôlement de son appareil.
9. Une fois l’appareil enrôlé et lié, le participant peut utiliser EZKey normalement dans son tenant de test.

**Hypothèse produit recommandée**
Le plan de travail ultérieur devrait partir de l’hypothèse qu’un petit ajout produit est préférable à une procédure purement implicite. L’objectif n’est pas de détourner la logique existante de recovery, mais de formaliser un mode de provisioning expérimental compréhensible, auditable, et exploitable. Le cœur de la réflexion est d’introduire un mode “enrollment deferred” ou un équivalent fonctionnel, afin que la création du tenant admin et l’étape de liaison de l’appareil soient distinctes mais cohérentes.

**Comportement fonctionnel recherché**
- Le Global Admin doit pouvoir créer un tenant admin dans un mode explicitement identifié comme onboarding expérimental.
- Ce mode doit conserver la génération de recovery codes et leur affichage one-shot.
- L’admin créé doit disposer d’un chemin clair pour amorcer lui-même son enrôlement à partir du matériel fourni, sans intervention supplémentaire du Global Admin dans le cas nominal.
- Le parcours doit rester aligné avec la posture actuelle d’EZKey: recovery codes comme mécanisme contraint, audit explicite, et enrôlement final sur appareil mobile comme condition d’usage normal.
- Le parcours doit être suffisamment simple pour être expliqué dans un email court à un public technique, sans runbook lourd ni support synchrone systématique.

**Deux options à comparer dans la planification détaillée**
Option A, recommandée: ajouter un mode explicite de création d’admin avec enrôlement différé. Dans cette variante, le système distingue clairement “admin créé” et “appareil pas encore lié”, avec un état ou un comportement dédié côté API/UI/audit.

Option B, plus légère: conserver le couplage actuel avec création d’un enrollment immédiat, mais formaliser un parcours supporté où les recovery codes servent à déclencher proprement un reset ou une activation contrôlée du parcours d’enrôlement. Cette option réduit le scope technique, mais risque d’être moins lisible fonctionnellement et moins propre comme modèle produit.

**Considérations à inclure dès maintenant dans la future planification**
- Clarifier si le système doit permettre un admin sans enrollment lié au moment de la création, ou seulement un enrollment créé mais non encore activé par l’utilisateur final.
- Définir précisément ce que reçoit le Global Admin lors du provisioning et ce qui est ensuite transmissible par email au participant sans exposer inutilement des secrets supplémentaires.
- Vérifier l’impact sur l’audit: il faut pouvoir distinguer la création d’un admin standard, la création d’un admin en onboarding expérimental, l’activation ultérieure de l’enrollment, et les éventuelles régénérations de recovery codes.
- Vérifier l’impact UI: case à cocher, libellé explicite, état visible “enrollment pending” ou équivalent, et surfaces minimales nécessaires pour éviter une UX ambiguë.
- Vérifier l’impact API: contrat de création d’admin, éventuel flag de mode expérimental, éventuel endpoint d’activation ou de reprise du parcours, et compatibilité avec la logique actuelle de recovery.
- Vérifier l’impact sécurité: les recovery codes ne doivent pas devenir un pseudo-mode de login normal; ils doivent rester un mécanisme de démarrage ou de secours vers l’enrôlement, pas un substitut durable à la liaison de l’appareil.
- Vérifier la posture support: définir ce que l’on fait si le participant perd les recovery codes avant d’avoir terminé son premier enrôlement, ou s’il échoue plusieurs fois au parcours initial.
- Prévoir la révocation simple du tenant expérimental et du tenant admin si le pilote ne doit pas être poursuivi.

**Artefacts opérateur à prévoir pour ce mécanisme**
- Un gabarit d’email d’acceptation au programme expérimental.
- Un mini-guide d’installation de l’APK debug Android.
- Un mini-guide “première connexion et premier enrôlement”.
- Une consigne explicite sur le caractère expérimental, le risque lié au side-loading, et l’absence possible de mécanisme de mise à jour fluide.
- Une procédure de support court pour recovery / reset / réémission si nécessaire.

**Questions ouvertes à garder pour la session de planification dédiée**
1. Le mode expérimental doit-il être réservé aux Global Admins, ou aussi utilisable par un Tenant Admin qui crée un pair dans son propre tenant?
2. Faut-il introduire un vrai état fonctionnel d’“enrollment deferred”, ou un simple comportement spécial sur la logique actuelle?
3. Quel est le minimum d’informations à envoyer par email pour rester pragmatique sans dégrader la posture de sécurité?
4. Comment rendre ce flux suffisamment explicite dans l’Admin UI pour éviter toute confusion entre recovery, onboarding, et reset?
5. Quelle est la plus petite évolution produit qui rende ce parcours clair, durable, et compatible avec l’audit?

**Relevant files**
- `c:\github\ezkey-worktree3\PRD.md` — cadrage produit, audience, non-objectifs, posture backend-first.
- `c:\github\ezkey-worktree3\README.md` — surface produit actuelle et point d’entrée du stack local.
- `c:\github\ezkey-worktree3\docs\PROJECT_POSITIONING.md` — philosophie développeur-first et séparation Global Admin / Tenant Admin.
- `c:\github\ezkey-worktree3\docs\ENDPOINT.md` — contrats API existants, surtout onboarding, auth, enrollment et rate-limit côté Auth API.
- `c:\github\ezkey-worktree3\docs\OPERATIONAL.md` — trusted proxies, rate limiting, bonnes pratiques Cloudflare et recommandations ops.
- `c:\github\ezkey-worktree3\docs\RECOVERY_CODES_LIFECYCLE_ANALYSIS.md` — position produit actuelle sur les recovery codes et limites du modèle existant.
- `c:\github\ezkey-worktree3\docs\ADMIN_UI.md` — responsabilités opérateur et workflows UI à préserver.
- `c:\github\ezkey-worktree3\docker\docker-compose.yml` — stack de base à réutiliser pour le laboratoire d’exposition.
- `c:\github\ezkey-worktree3\docker\docker-compose.with-proxy.yml` — overlay Caddy existant à étudier comme base de reverse proxy d’origine.
- `c:\github\ezkey-worktree3\docker\caddy\Caddyfile` — proxy headers, sécurité HTTP, futur point d’ancrage TLS/routage.
- `c:\github\ezkey-worktree3\ezkey-admin-ui\docker\Caddyfile` — contraintes CSP/Admin UI si l’UI reste séparée du backend.
- `c:\github\ezkey-worktree3\ezkey-core\src\main\java\org\ezkey\audit\util\ClientIpResolver.java` — logique critique de confiance sur les IP clientes derrière proxy.
- `c:\github\ezkey-worktree3\ezkey-auth-api\src\main\java\org\ezkey\auth\config\RateLimitFilter.java` — comportement du rate limit dépendant des IP résolues.
- `c:\github\ezkey-worktree3\ezkey-admin-api\src\main\java\org\ezkey\admin\service\AdminProvisioningService.java` — point central du provisioning admin et du couplage actuel avec l’enrollment.
- `c:\github\ezkey-worktree3\ezkey-admin-api\src\main\java\org\ezkey\admin\service\AdminRecoveryService.java` — génération et rotation des recovery codes.
- `c:\github\ezkey-worktree3\ezkey-admin-ui\src\pages\admins.tsx` — UX actuelle de création d’admin, affichage des recovery codes et accès onboarding.
- `c:\github\ezkey-worktree3\ezkey_mobile_app\android\app\build.gradle` — posture actuelle de build/signing Android et limite du debug APK.
- `c:\github\ezkey-worktree3\ezkey_mobile\docs\MOBILE_PLAY_PUBLISHING.md` — jalon futur si le pilote évolue vers une distribution plus propre.

**Verification**
1. Produire un document d’architecture légère avec la topologie retenue, les sous-domaines, les flux réseau, et la chaîne de confiance proxy/TLS.
2. Produire une checklist de sécurité minimale de l’origine: Cloudflare only, trusted proxies, rate limits, secrets, certificats, sauvegarde, restauration.
3. Produire une note de décision sur la base de données du pré-pilote: Docker PostgreSQL vs RDS vs Supabase, avec recommandation circonstanciée.
4. Produire une note de décision sur l’onboarding expérimental: mini-évolution produit recommandée, portée exacte, et variante provisoire si tu veux aller plus vite.
5. Produire un runbook opérateur court couvrant: demande d’accès, création tenant, création admin, envoi des artefacts, support initial, recovery/reset.
6. Valider un scénario de bout en bout en environnement pilote avant toute ouverture externe.

**Decisions**
- Inclus: plan de pré-pilotage expérimental, exposition contrôlée, architecture Cloudflare/AWS/Caddy à haut niveau, posture sécurité minimale, flux d’accès expérimental, et cadrage du mini-chantier produit d’onboarding.
- Inclus: comparaison de deux options d’exposition origine, avec recommandation initiale pour l’origine publique strictement filtrée à Cloudflare afin de rester pragmatique et proche du stack actuel.
- Inclus: prise en compte explicite de l’objectif MCP/gestion agentique comme critère d’architecture et de documentation opérationnelle.
- Exclu pour cette première passe: implémentation détaillée AWS, Terraform, manifests complets, tuning fin des règles Cloudflare, signature release Android, haute disponibilité avancée, conformité formelle, et déploiement mobile grand public.
- Recommandation de posture: traiter ce pré-pilote comme un laboratoire contrôlé à faible blast radius, mais ne pas accepter de raccourcis sur les points qui casseraient la chaîne de confiance ou empêcheraient une restauration propre.

**Further Considerations**
1. Base de données du tout premier pilote: conserver PostgreSQL dans Docker accélère fortement l’apprentissage initial, mais RDS devient rapidement préférable dès que la disponibilité, la sauvegarde, et la sérénité opératoire comptent plus que la pure proximité avec clean-start.
2. APK debug: acceptable pour un public développeur très restreint si le risque est explicitement assumé; à moyen terme, prévoir au minimum un canal de build signé plus stable même hors Play Store.
3. Origine masquée: utile si tu veux pousser plus loin la posture de sécurité réseau, mais ce n’est probablement pas le meilleur premier incrément si ton objectif principal est d’apprendre vite avec une pile encore lisible et proche de l’existant.
