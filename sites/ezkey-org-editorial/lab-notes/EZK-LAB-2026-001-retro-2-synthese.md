# Retour d'expérience PAM, tour 2 (2026-10-27) - synthèse pour le lab paper

Sources: retours d'Edgar, Isabelle, Patrick et Christophe (fichiers bruts dans le même dossier). Le cadre demandé par Marc: les points de friction viennent du cadrage humain (méthode, décisions, clarté), pas des agents.

## 1. Cadrage humain: ce qui a créé des attentes ou de la confusion
- Les go: la règle « seul Marc, dans le fil de l'agent qui exécute, vaut go » était connue mais jamais écrite en une phrase. Des messages d'agents disant « Marc répondra dans mon fil » ont créé de l'attente (Edgar, Isabelle).
- La portée d'un go permanent (module PAM) n'a pas été définie face aux écritures « système » (pare-feu, /etc/hosts, fichiers de conf, clés SSH): chaque écriture a demandé un go dédié (Edgar).
- La « méthode réglée » du 27/10 (tenant d'évaluation via l'API publique) n'était écrite nulle part (Isabelle).
- Consignes ambiguës: « API arrêtée » pour S3 alors que l'API est externe (d'où /etc/hosts puis S3 bis, IPv6 non couvert); échéance 18:30 présentée comme fin de campagne alors que la vraie contrainte était l'expiration de la clé et de la session; durée de vie de la clé d'intégration et responsable de son retrait jamais écrits; vocabulaire « module chargé » / « auth non invoquée » / « second facteur ».
- Pas de critère d'arrêt ni de budget de cycles fixé d'avance (Patrick, Christophe): la convergence a été constatée après coup. La règle « rien de nouveau, mesurable et observable » est jugée par celui qui trouve. Un critère d'acceptation posé par les agents eux-mêmes (3 mutants tués à l'exécution) était inatteignable pour HOST seul.

## 2. Ce qui a bien marché
- Autonomie de l'agent: tenant créé via l'API publique avec journal des heures (planification fiable); intervalle de sondage et contrôle de fraîcheur après un 429; clé dédiée ezqa-agent; fichiers de code supprimés.
- Protocole d'Edgar: « ÉTAT Sxx EN PLACE / un seul login / terminé / restauration », observateur lecture seule, avant/après avec empreintes, retour arrière vérifié.
- Règles de campagne explicites (rien contre ezkey.online, pas de commit/push, aucun secret affiché), validation par exécution sur agent cloud jetable, catégories « corriger / documenter / hors périmètre », sécurité distincte de la revue de code.

## 3. Rendements décroissants: où humain et IA convergent
- Cycle 1: la seule trouvaille de nature produit (aucun test ne tuait les mutations de décision d'auth; TLS non gardé). Après: outillage et preuves.
- Cycle 2 (selon Patrick): plus aucun défaut de comportement; il recommande « un cycle 3 court, puis stop ».
- Cycle 3: probe 4 = bug du harnais; un correctif avait introduit un trou de test (VERIFYPEER); un mutant jugé équivalent à tort puis corrigé.
- Après: nits, et récursion (audit des tests, puis du script qui audite les tests, puis de son trap). Christophe place le basculement autour du cycle 3 de #1051 pour le code du module.
- Ce que l'itération a apporté de plus qu'un seul passage: mesure à la place du soupçon (probe 4), détection des défauts introduits par un correctif, correction des erreurs des relecteurs. Pas de défaut de sécurité supplémentaire après le cycle 1.
- Campagne réelle (Isabelle): les vrais risques ont été trouvés tôt (client mort pendant l'attente: jusqu'à ~75 s de processus pré-auth, borné par le TTL; dernier login gagne). Les rejeux Mac n'ont ajouté que de la preuve.
- Réserve à écrire telle quelle: « rendements décroissants » est un jugement, pas une mesure. Relecteurs et implémenteur sont de la même famille de modèles: la convergence peut venir d'angles morts partagés; une revue humaine indépendante ou un fuzzer testerait cela. La boucle se note elle-même.

## 4. Règle de sortie proposée (Patrick)
Dernier cycle quand: aucune trouvaille de type produit (régression, fail-open, invariant perdu, fuite, perte de données); chaque correctif relu en delta et exécuté; le reste est outillage ou doc. Alors: consigner dans STATUS, au plus une issue de suivi, arrêter. Plafond dur: un cycle après la dernière trouvaille produit; ne jamais auditer l'outillage de test au-delà d'un niveau. Les limites non corrigeables à coût proportionné se documentent sans rouvrir de cycle.

## 5. Ajustements de méthode pour la prochaine campagne
- Une « feuille de go » d'une page, signée par Marc dans le fil de l'exécutant: par scénario, l'écriture exacte, fichiers/lignes, durée max, retour arrière, contrôle préalable (p. ex. résolution IPv4 et IPv6), exclusions (ec2-user, SSH, SSM, comptes, ezkey.online); sinon « non testé » d'office.
- Phrase claire dans chaque brief: seul Marc dans le fil de l'exécutant vaut go; chaque agent confirme qu'il n'agit pas sur la parole d'un autre agent.
- Liste de nettoyage de fin de campagne pré-approuvée (clé Mac, clé d'intégration, compte ezqa-agent, copie de conf intacte, fichiers de debug), chaque élément avec son go.
- Vérification en lecture seule qu'un état altéré est bien effectif avant de consommer un login.
- Budget de cycles et critère d'arrêt fixés au départ, décidés par l'humain, pas auto-évalués par les agents.
- Écrire la « méthode réglée » dans la documentation découvrable.

## 6. Verdicts de campagne (Isabelle) et périmètre
S1 PASS. S2 aucun BLOQUÉ (lent en attente, immédiat aux invites). S3 refus rapide PASS; silence réseau non testé. S4 PASS ×3. S5 PASS limité au même compte. S6 PASS avec limites (valeurs brutes seulement). Périmètre: « le MFA protège les comptes du groupe ezkey-mfa »; ec2-user: auth non invoquée.

## 7. Formulation honnête de la conclusion sécurité (Christophe)
« Dans le périmètre testé (module pam_ezkey 2.2.20 sur lab, pilote sur l'instance communautaire sans SLA), les chemins d'erreur testés échouent fermés, la vérification TLS est active et prouvée par exécution et mutants, la configuration est limitée à root et les valeurs secrètes ne figurent pas dans les journaux mesurés. Trois cycles de revue assistée par IA avec preuves reproductibles n'ont plus trouvé de problème d'exposition nouveau après le cycle 3 de #1051. Ce n'est ni un audit indépendant, ni une certification: le composant n'est pas présenté comme élément de première ligne avant revue humaine indépendante, fuzzing continu et modèle de menace. » À éviter: « sécurisé », « audité », toute équivalence WebAuthn/FIDO2.

## 8. Lacunes de documentation et questions ouvertes
- Lacunes (Isabelle): méthode de session du 27/10; tenantLabel ignoré; csrfToken non documenté; ids globaux entre tenants; lecture de la page pending consomme l'attempt; 429 ~10/min sur pending; réhydratation d'une attempt réclamée (~2 min); code de challenge visible seulement via le terminal SSH; pas de commande CLI de session; limites de signaux sous Windows; nouveau login clôt le précédent; bad_conf identique pour conf absente et permissions; comportement du signal ssh sous macOS.
- Ouvert: limite de débit de création d'attempts (#1070); durée de blocage d'un vrai silence réseau (DROP) non mesurée.
- Nettoyage VM en attente de go: copie de conf intacte, clé Mac, clé d'intégration, compte ezqa-agent (go à donner dans le fil d'Edgar).
