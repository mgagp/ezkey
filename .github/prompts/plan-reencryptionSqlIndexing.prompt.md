## Plan: Indexation SQL pour la ré-encryption

Évaluer et sécuriser la performance SQL du repérage des lignes à ré-encrypter pour les colonnes chiffrées au format ENC:{keyId}:{ciphertext}, puis définir la stratégie d’indexation la plus adaptée à un volume de centaines de milliers de lignes. L’approche recommandée est en deux temps: d’abord mesurer les requêtes réelles actuelles, ensuite arbitrer entre une optimisation immédiate par index de recherche de préfixe et une optimisation plus robuste par extraction/indexation explicite du key id de chiffrement.

**Steps**
1. Phase 1 — Inventaire technique. Confirmer les 4 couples table/colonne réellement scannés par la ré-encryption, la forme exacte des requêtes COUNT/FETCH, la pagination par id, et l’impact du partitionnement sur ezkey_auth_attempt. Cette étape s’appuie sur ReencryptionTargetQueryService, EnrollmentRepository et AuthAttemptRepository.
2. Phase 1 — Inventaire des structures d’accès. Documenter les indexes existants, les colonnes auxiliaires déjà présentes, et les patterns PostgreSQL déjà acceptés dans le repo. Point clé: distinguer les colonnes hash existantes, utiles aux lookups déterministes métier, de ce qui est nécessaire pour retrouver les lignes encore chiffrées avec un old key donné.
3. Phase 2 — Profiling SQL de référence. Construire un jeu de mesures sur PostgreSQL avec volumétrie représentative, en séparant enrollment et auth_attempt. Capturer EXPLAIN / EXPLAIN ANALYZE pour les variantes COUNT et FETCH, avec et sans shard sur auth_attempt, et mesurer coût, type de scan, buffers lus et impact du ORDER BY id + LIMIT.
4. Phase 2 — Vérifier l’hypothèse de base. Confirmer si le bottleneck principal est bien le LIKE préfixé sans index, ou si le coût dominant vient du scan inter-partitions sur ezkey_auth_attempt, du prédicat mod(auth_attempt_id, shardCount), ou de l’ordre de pagination. Cette étape doit produire un diagnostic falsifiable avant toute proposition de migration.
5. Phase 3 — Comparer les options d’indexation. Option A: indexes de préfixe sur ciphertext pour supporter LIKE 'ENC:{keyId}:%' de manière directe. Option B: index d’expression ou colonne auxiliaire dérivée exposant explicitement le key id extrait du préfixe, puis requêtes réécrites en égalité sur key id. Option C: combinaison différente par table, par exemple solution simple pour enrollment et solution plus structurante pour auth_attempt.
6. Phase 3 — Critères de décision. Évaluer chaque option selon: sélectivité réelle, taille d’index, coût d’écriture, compatibilité avec le partitionnement, simplicité des migrations Flyway, stabilité du plan SQL, lisibilité opérationnelle et facilité de validation dans le temps.
7. Phase 4 — Recommandation ciblée. Produire une recommandation finale par table/colonne. Attendu probable: une stratégie plus simple peut suffire sur ezkey_enrollment, alors que ezkey_auth_attempt doit être évaluée plus strictement à cause du volume, du partitionnement mensuel et du traitement shardé.
8. Phase 4 — Plan d’implémentation. Détailler les migrations à prévoir, les adaptations éventuelles des requêtes repository si une stratégie key-id explicite est retenue, la stratégie de déploiement des indexes en environnement chargé, et les risques de verrouillage ou de régression.
9. Phase 5 — Validation post-changement. Définir les checks automatiques et manuels: comparaison de plans avant/après, temps de création de batchs, temps de lecture des slices, pression I/O observée, comportement avec plusieurs shards et absence de régression fonctionnelle sur la découverte des candidats.

**Relevant files**
- c:\github\ezkey-worktree3\ezkey-core\src\main\java\org\ezkey\security\ReencryptionTargetQueryService.java — point central de construction des préfixes ENC:{keyId}:% et de dispatch des requêtes COUNT/FETCH.
- c:\github\ezkey-worktree3\ezkey-core\src\main\java\org\ezkey\enrollment\domain\repository\EnrollmentRepository.java — requêtes natives COUNT/FETCH sur integration_private_key et enrollment_proof_token.
- c:\github\ezkey-worktree3\ezkey-core\src\main\java\org\ezkey\authattempt\domain\repository\AuthAttemptRepository.java — requêtes natives COUNT/FETCH sur auth_attempt_proof_token et device_proof_token, avec shardCount/shardIndex.
- c:\github\ezkey-worktree3\ezkey-core\src\main\resources\db\migration\V1__core_domain_and_multi_tenant.sql — définition initiale des colonnes chiffrées concernées.
- c:\github\ezkey-worktree3\ezkey-core\src\main\resources\db\migration\V2__audit_api_keys_proof_tokens_and_admin_identity.sql — ajout des colonnes hash déjà existantes, à distinguer du besoin de filtrage par old key id.
- c:\github\ezkey-worktree3\ezkey-core\src\main\resources\db\migration\V4__partitioning_auth_audit_and_function.sql — partitionnement mensuel de ezkey_auth_attempt et indexes déjà présents sur la table partitionnée.
- c:\github\ezkey-worktree3\ezkey-core\src\main\resources\db\migration\V3__encryption_keyset_and_audit_jsonb_indexes.sql — tables de suivi de clé et de batch de ré-encryption, utiles pour comprendre le flux opérationnel existant.
- c:\github\ezkey-worktree3\docs\REENCRYPTION_OPERATIONS.md — comportement opérationnel, batch-size, sharding auth_attempt, exécution parallèle et contraintes de reprise.
- c:\github\ezkey-worktree3\docs\ENDPOINT.md — contrat opératoire exposé côté Admin API pour les opérations de ré-encryption.

**Verification**
1. Exécuter EXPLAIN ANALYZE sur les 8 requêtes critiques: 4 COUNT et 4 FETCH paginées, avec préfixes correspondant à des old keys présentes et absentes.
2. Mesurer séparément ezkey_enrollment et ezkey_auth_attempt avec un volume de référence réaliste, dont plusieurs partitions mensuelles pour auth_attempt.
3. Comparer avant/après pour chaque option candidate: Seq Scan vs Index Scan/Bitmap Scan, buffers, coût total, latence, et stabilité du plan avec différentes sélectivités de keyId.
4. Vérifier l’effet du prédicat shardé mod(auth_attempt_id, N) sur la capacité du planner à exploiter l’index retenu.
5. Vérifier le temps de création des batchs et le temps de fetch d’un slice typique de batch-size 500 et de tailles supérieures utilisées pour les essais de charge.
6. Si une option implique une réécriture de requête, vérifier que la découverte des candidats reste strictement équivalente au LIKE de référence sur un jeu de données de contrôle.
7. Prévoir une validation de migration sur PostgreSQL réel, pas seulement via tests unitaires, car la décision dépend du planificateur et du comportement des indexes sur données volumineuses.

**Decisions**
- Inclus: analyse de performance SQL, opportunités d’indexation, arbitrage entre index de préfixe et support explicite du key id, impact du partitionnement et du sharding sur ezkey_auth_attempt.
- Inclus: recommandations de migration et de validation nécessaires pour supporter des campagnes de ré-encryption à grande volumétrie.
- Exclu: refonte complète du moteur de batch, modification du format de ciphertext ENC:{keyId}:{ciphertext}, ou optimisation générale de toutes les requêtes métier non liées à la ré-encryption.
- Exclu sauf si le diagnostic l’impose: changements fonctionnels du comportement de re-encryption, au-delà de ce qui est nécessaire pour permettre un meilleur accès indexé.
- Constat important: les colonnes *_hash déjà présentes ne répondent pas directement au besoin de retrouver les lignes encore chiffrées avec un old key donné, car elles adressent des lookups déterministes métier, pas le key id Tink embarqué dans le préfixe du ciphertext.

**Further Considerations**
1. Recommandation de design: traiter ezkey_enrollment et ezkey_auth_attempt séparément dans l’analyse finale, car leurs profils de charge et contraintes SQL sont différents.
2. Recommandation de risque: pour un système destiné à monter en volumétrie, préférer une décision appuyée par des plans PostgreSQL mesurés plutôt qu’un choix théorique d’index basé seulement sur la forme du LIKE.
3. Recommandation d’architecture: si l’option key-id explicite est retenue, comparer soigneusement index d’expression versus colonne dérivée persistée selon la facilité de migration, la clarté des requêtes et la maintenabilité à long terme.