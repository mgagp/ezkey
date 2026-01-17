# Analyse et Documentation du Mécanisme de Synchronisation Keyset

## Contexte

Le système Ezkey utilise une stratégie hybride de stockage du keyset (collection de clés de chiffrement Tink) pour permettre la synchronisation entre plusieurs instances dans un environnement HA. Cette analyse vise à clarifier:

1. Pourquoi utiliser `ezkey_keyset_blob` (BD) vs fichier sur filesystem
2. Le mécanisme de zone tampon temporelle (sync window) 
3. Les implications pour la haute disponibilité
4. La couverture des tests fonctionnels
5. Les angles morts potentiels

## Architecture Actuelle

### Stockage du Keyset

Le système supporte 3 modes de stockage (voir `TinkProperties.Keyset.StorageMode`):

- **FILE**: Keyset uniquement dans un fichier (`keyset.json.encrypted`)
- **DATABASE**: Keyset uniquement dans la BD (`ezkey_keyset_blob`)
- **HYBRID**: Keyset dans les deux, BD comme source de vérité, fichier comme cache

**Mode actuel configuré**: `DATABASE` (voir `ezkey-admin-api/config/application.properties:86`)

### Pourquoi la Table `ezkey_keyset_blob`?

**Problème résolu**: Synchronisation distribuée en environnement HA

Dans un déploiement avec plusieurs back-ends (ex: mode HA avec load balancer):
- Si le keyset est uniquement dans un fichier, chaque instance a sa propre copie locale
- Lors d'une rotation, une instance met à jour le fichier, mais les autres instances ne sont pas notifiées
- Résultat: **des erreurs de chiffrement/déchiffrement** car les instances utilisent des keysets différents

**Solution avec `ezkey_keyset_blob`**:
- La BD sert de **source de vérité centralisée** pour le keyset
- Toutes les instances partagent la même BD, donc le même keyset
- Les instances vérifient périodiquement la version du keyset dans la BD (via `checkAndReloadKeysetIfNeeded()`)
- Changement détecté: la version en BD est supérieure à la version en cache → rechargement du keyset

### Mécanisme de Zone Tampon Temporelle (Sync Window)

**Objectif**: Garantir que **toutes les instances** ont synchronisé la nouvelle clé avant qu'elle devienne active pour le chiffrement.

**Workflow** (voir `KeyRotationService.introduceNewKey()`):

1. **T=0 - Introduction de la nouvelle clé**:
   - `KeyRotationService.introduceNewKey()` est appelé (manuellement ou via job planifié)
   - `TinkKeyManager.addKeyWithoutPromotion()` ajoute la nouvelle clé au keyset **SANS la promouvoir en PRIMARY**
   - La nouvelle clé est en statut **PENDING** dans la BD (`ezkey_encryption_key`)
   - Le keyset est sauvegardé dans la BD (`ezkey_keyset_blob`) et le fichier
   - `effective_at = maintenant + sync_window_seconds` (défaut: 10s en docker-test)

2. **T=0 à T=sync_window - Fenêtre de synchronisation**:
   - Toutes les instances continuent d'utiliser l'**ancienne clé PRIMARY** pour le chiffrement
   - Chaque appel à `getAeadPrimitive()` peut déclencher `checkAndReloadKeysetIfNeeded()`
   - Cette méthode vérifie la version du keyset en BD (toutes les 5s max, throttled)
   - Si la version a changé, le keyset est rechargé → **toutes les instances ont maintenant la nouvelle clé** (mais elle n'est pas encore PRIMARY)
   - La nouvelle clé peut être utilisée pour **déchiffrer** (Tink peut déchiffrer avec n'importe quelle clé ENABLED dans le keyset)

3. **T=sync_window - Promotion**:
   - Le job planifié `checkAndPromotePendingKeys()` s'exécute (toutes les 5s par défaut)
   - Il trouve la clé PENDING dont `effective_at <= maintenant`
   - `promotePendingToPrimary()` promeut la clé en PRIMARY dans le Tink keyset
   - Le keyset mis à jour est sauvegardé dans la BD et le fichier
   - **Maintenant toutes les instances utilisent la nouvelle clé pour le chiffrement**

**Avantages**:
- Aucune perte de données: les instances ont toutes la nouvelle clé avant qu'elle devienne active
- Zéro downtime: le changement de clé est transparent
- Résilience: même si une instance rate la synchronisation initiale, elle peut récupérer au prochain `getAeadPrimitive()`

### Vérification Périodique

**Implémentation**: `TinkKeyManager.checkAndReloadKeysetIfNeeded()`

- Appelé depuis `getAeadPrimitive()` (qui est appelé à chaque opération de chiffrement/déchiffrement)
- **Throttled**: Vérification max toutes les 5 secondes (`DATABASE_CHECK_INTERVAL_MS`)
- **Mode DATABASE**: Vérifie `keysetBlobRepository.findVersion()` pour détecter les changements
- **Mode FILE**: Vérifie `file.lastModified()` pour détecter les changements
- **Mode HYBRID**: Vérifie d'abord la BD, puis le fichier

**Protection contre la récursion**: `CHECKING_DATABASE` ThreadLocal flag pour éviter les boucles infinies quand les queries JPA déclenchent des entity listeners qui utilisent le chiffrement.

## Comportement dans le Stack Docker

### Configuration Docker

Dans le stack Docker, la configuration utilise le mode **DATABASE** pour le stockage du keyset:

- **Admin API**: `ezkey-admin-api/config/application-docker.properties:141` → `ezkey.encryption.keyset.storage-mode=DATABASE`
- **Auth API**: `ezkey-auth-api/config/application-docker.properties:87` → `ezkey.encryption.keyset.storage-mode=DATABASE`

### Volume Docker Partagé

**Important**: Bien que le mode configuré soit `DATABASE`, le comportement dans Docker est **de facto HYBRID** grâce au volume Docker partagé:

- **Volume**: `encryption-secrets` monté sur `/etc/ezkey` (voir `docker/docker-compose.yml:70, 103, 159`)
- **Fichier keyset**: `/etc/ezkey/keysets/keyset.json.encrypted` (partagé entre toutes les instances)
- **Master key**: `/etc/ezkey/secrets/master.key` (partagé entre toutes les instances)

**Conséquence**: Le fichier keyset est **physiquement partagé** entre toutes les instances Docker (admin-api, auth-api, crypto-api) via le volume Docker. Chaque instance voit la même copie du fichier.

### Stratégie Hybride de Fait

**Pourquoi c'est hybride malgré le mode DATABASE**:

1. **Au démarrage** (`TinkKeyManager.initialize()`, ligne 148-162):
   - Le code tente de charger depuis la BD en premier (mode DATABASE)
   - Si la BD échoue ou n'a pas de keyset, fallback au fichier
   - Si le fichier existe, il est chargé et **synchronisé vers la BD** (ligne 193)

2. **Lors de la sauvegarde** (`TinkKeyManager.saveKeyset()`, ligne 1064-1091):
   - En mode DATABASE ou HYBRID, le keyset est **toujours sauvegardé dans le fichier** d'abord
   - **Puis** sauvegardé dans la BD (ligne 812, 815)
   - Si la sauvegarde BD échoue, le fichier est quand même à jour (ligne 816: `// Don't throw - file save succeeded`)

3. **Lors de la rotation** (`TinkKeyManager.rotateKey()`, ligne 807-818):
   - Le keyset est sauvegardé dans le fichier (ligne 797)
   - **Et** dans la BD (ligne 812)
   - Les deux sont synchronisés

### Comportement dans Docker (Mode HA)

**Dans `docker-compose.ha.yml`** (mode haute disponibilité avec plusieurs instances):

1. **Toutes les instances partagent**:
   - La même BD PostgreSQL (source de vérité pour le keyset blob)
   - Le même volume Docker `encryption-secrets` (fichier keyset partagé)

2. **Lors d'une rotation**:
   - L'instance qui exécute le job (via `@SchedulerLock`) crée la nouvelle clé PENDING
   - Le keyset est sauvegardé dans **la BD ET le fichier** (volume partagé)
   - Toutes les autres instances voient **immédiatement** le changement du fichier (volume partagé)
   - Mais la vérification périodique dans `checkAndReloadKeysetIfNeeded()` utilise la **BD comme source de vérité** (ligne 325-334)

3. **Synchronisation**:
   - **BD**: Source de vérité via `keysetBlobRepository.findVersion()` (vérification toutes les 5s)
   - **Fichier**: Cache/backup partagé via volume Docker (visible immédiatement par toutes les instances)
   - En cas de problème BD, le fichier partagé permet une récupération rapide

### Avantages du Volume Partagé en Docker

1. **Récupération rapide**: Si une instance redémarre, elle peut charger depuis le fichier partagé sans attendre la BD
2. **Cohérence**: Toutes les instances voient la même version du fichier (volume Docker partagé)
3. **Backup**: Le fichier dans le volume Docker peut servir de backup en cas de problème BD
4. **Performance**: Chargement depuis fichier plus rapide que depuis la BD

### Résumé du Comportement Docker

**Configuration**: Mode `DATABASE` (BD comme source de vérité)

**Comportement réel**: **HYBRID de facto** grâce au volume Docker partagé:
- BD = Source de vérité pour la synchronisation distribuée
- Fichier = Cache/backup partagé via volume Docker pour récupération rapide
- Les deux sont synchronisés lors de chaque rotation

**Avantage clé**: En cas d'indisponibilité temporaire de la BD, le fichier partagé permet aux instances de continuer à fonctionner avec la dernière version connue du keyset.

## Tests Fonctionnels Existants

### Tests Identifiés

1. **`KeyRotationSyncWindowTest`** (`ezkey-tests/src/test/java/org/ezkey/tests/security/crypto/KeyRotationSyncWindowTest.java`):
   - Teste le workflow PENDING → PRIMARY
   - Vérifie que l'ancienne clé est utilisée pendant le sync window
   - Vérifie que la nouvelle clé est utilisée après promotion
   - Teste le déchiffrement cross-API

2. **`KeyRotationServiceIntegrationTest`** (`ezkey-core/src/test/java/org/ezkey/security/KeyRotationServiceIntegrationTest.java`):
   - Teste la logique de rotation (mocked `TinkKeyManager`)
   - Vérifie les statuts en BD

### Couverture Manquante

**Aucun test ne vérifie explicitement**:
- La synchronisation entre le fichier et la BD (mode HYBRID)
- Le comportement quand la BD et le fichier sont désynchronisés
- La récupération après une perte de synchronisation
- Le comportement en cas d'échec de sauvegarde dans la BD (mais succès dans le fichier)

## Angles Morts Potentiels

### 1. Désynchronisation BD ↔ Fichier (Mode HYBRID)

**Scénario problématique**:
- Rotation réussit dans le fichier mais échoue dans la BD (réseau, timeout, erreur transaction)
- Certaines instances chargent depuis la BD (ancienne version)
- D'autres chargent depuis le fichier (nouvelle version)
- **Résultat**: Erreurs de chiffrement/déchiffrement

**Protection actuelle**:
- `TinkKeyManager.saveKeysetToDatabase()` log les erreurs mais ne throw pas (ligne 816): `// Don't throw - file save succeeded, database sync can be retried`
- Mode DATABASE (actuel) évite ce problème car seule la BD est utilisée

**Recommandation**: En mode HYBRID, s'assurer que la sauvegarde dans la BD est critique (throw exception) ou implémenter un mécanisme de réconciliation.

### 2. Instance Lente/Offline Pendant le Sync Window

**Scénario**:
- Rotation crée clé PENDING avec `effective_at = T + 10s`
- Une instance est offline ou lente
- À T + 10s, promotion se produit
- L'instance revient en ligne et charge le keyset → nouvelle clé est PRIMARY
- **Problème**: L'instance n'a jamais eu la chance de synchroniser avant la promotion

**Protection actuelle**:
- `checkAndReloadKeysetIfNeeded()` s'exécute à chaque `getAeadPrimitive()` (même après la promotion)
- Tink peut déchiffrer avec l'ancienne clé (ENABLED) même si la nouvelle est PRIMARY
- Le chiffrement utilisera la nouvelle clé PRIMARY

**Impact**: Acceptable - pas de perte de données car l'ancienne clé reste ENABLED.

### 3. Échec de Rechargement depuis la BD

**Scénario**:
- BD temporairement inaccessible pendant une rotation
- `checkAndReloadKeysetIfNeeded()` échoue silencieusement (catch exception, ligne 336)
- Instance continue avec l'ancienne version du keyset

**Protection actuelle**:
- Les erreurs sont loggées mais l'instance continue avec le keyset en cache
- Au prochain appel, la vérification est retentée

**Recommandation**: Implémenter un mécanisme d'alerting si la vérification échoue plusieurs fois de suite.

### 4. Race Condition: Multiple Instances Font la Rotation

**Scénario**:
- Deux instances exécutent le job de rotation en même temps
- Les deux créent une clé PENDING avec des `effective_at` différents

**Protection actuelle**:
- `@SchedulerLock` sur `checkAndRotate()` garantit qu'une seule instance exécute le job (ligne 338)
- Si une clé PENDING existe déjà, `introduceNewKey()` throw `IllegalStateException` (ligne 484)

**Impact**: Bien protégé.

### 5. Version Optimistic Locking non Utilisée

**Observation**:
- `KeysetBlob.version` existe (pour optimistic locking) mais n'est pas utilisé dans `saveKeysetToDatabase()`
- Si deux instances sauvegardent en même temps, il peut y avoir overwrite

**Impact**: Faible - le `@SchedulerLock` sur les jobs de rotation empêche les concurrent writes.

## Limitations Actuelles et Évolution vers Multiples Keysets

### Architecture Actuelle: Un Seul Keyset

**Implémentation actuelle**: Le système Ezkey utilise un seul MasterKey et un seul Keyset pour toutes les opérations de chiffrement.

**Blocages architecturaux**:

1. **Table `ezkey_keyset_blob`**:
   - Conçue comme table single-row avec `CHECK (id = 1)` (voir `V22__create_keyset_blob_table.sql`)
   - `KeysetBlob.SINGLETON_ID = 1` hardcodé dans l'entité
   - `KeysetBlobRepository.findKeyset()` utilise `findById(SINGLETON_ID)`

2. **`TinkKeyManager`**:
   - Un seul champ `keysetHandle` (`private volatile KeysetHandle keysetHandle`)
   - Méthodes `getAeadPrimitive()` retourne un seul primitive pour tout le système
   - Pas de mécanisme pour identifier ou sélectionner un keyset spécifique

3. **Configuration**:
   - `TinkProperties` a une seule configuration de keyset (`keyset-file`, `keyset.storage-mode`)
   - Pas de support pour plusieurs chemins de keyset ou plusieurs modes de stockage

4. **Services de chiffrement**:
   - `EncryptionService` utilise directement `TinkKeyManager.getAeadPrimitive()` sans spécifier de keyset
   - `EncryptionEntityListener` utilise le même primitive pour toutes les entités

### Cas d'Usage Futur: Keyset Séparé pour Audit Logs

**Objectif**: Utiliser un keyset séparé pour l'encryption et la signature digitale des audit logs, pour:
- **Séparation des préoccupations**: Isolation des clés d'audit des clés de données applicatives
- **Conformité SOC2**: Exigences d'intégrité et de traçabilité des logs d'audit
- **Rotation indépendante**: Possibilité de faire tourner les clés d'audit sans affecter les données applicatives

**Analyse préliminaire**: Cette idée a été mentionnée dans une analyse préliminaire non implémentée.

### Bloquants Techniques pour Multiples Keysets

#### Bloquants Majeurs

1. **Table Database**: 
   - Nécessite migration pour supprimer la contrainte `CHECK (id = 1)`
   - Ajout d'un champ `keyset_type` ou `keyset_id` pour identifier les keysets
   - Modification de `KeysetBlobRepository` pour supporter `findByKeysetType(String type)`

2. **`TinkKeyManager`**:
   - Refactoring majeur: passage d'un seul `keysetHandle` à une `Map<String, KeysetHandle>`
   - Méthodes `getAeadPrimitive()` doivent accepter un paramètre `keysetType`
   - Méthodes de rotation doivent cibler un keyset spécifique
   - Gestion de la synchronisation pour chaque keyset

3. **Configuration**:
   - Extension de `TinkProperties` pour supporter plusieurs configurations de keyset
   - Exemple: `ezkey.encryption.keysets.application.storage-mode`, `ezkey.encryption.keysets.audit.storage-mode`

4. **Services de chiffrement**:
   - `EncryptionService` doit spécifier le keyset à utiliser par contexte
   - `EncryptionEntityListener` doit déterminer le keyset approprié par type d'entité
   - Ajout d'annotations ou de configuration pour mapper entités → keyset

#### Bloquants Mineurs

1. **Rotation**:
   - `KeyRotationService` doit gérer plusieurs keysets avec des cycles de rotation indépendants
   - Jobs planifiés doivent cibler un keyset spécifique

2. **Tests**:
   - Tests existants supposent un seul keyset
   - Nécessité de tests pour la séparation des keysets

### Faisabilité

**Conclusion**: **Pas de bloquants conceptuels** - Tink supporte nativement plusieurs keysets indépendants. Les bloquants sont **purement architecturaux** et peuvent être résolus par refactoring.

**Complexité estimée**: **Moyenne à Élevée**
- Refactoring du `TinkKeyManager`: **Élevé** (cœur de l'architecture)
- Migration de la BD et repository: **Moyen** (changements relativement simples)
- Mise à jour des services: **Moyen** (nécessite propagation du keysetType)
- Tests: **Moyen** (couvre plusieurs keysets)

**Recommandation**: 
- Si besoin immédiat pour audit logs → refactoring progressif:
  1. Phase 1: Ajouter support pour 2 keysets fixes (application, audit)
  2. Phase 2: Généraliser pour N keysets dynamiques
- Si besoin futur → documenter l'architecture cible et planifier le refactoring lors de la prochaine grande refonte

**Note**: Le MasterKey peut rester unique (il chiffre tous les keysets), seuls les DEK (Data Encryption Keys) dans les keysets sont séparés.

## Plan d'Action

### 1. Documentation

Créer/actualiser `docs/KEYSET_SYNCHRONIZATION_MECHANISM.md` avec:
- Explication du pourquoi de `ezkey_keyset_blob`
- Workflow détaillé du sync window
- Diagrammes de séquence (mermaid)
- Guide pour les opérateurs (que faire en cas de problème)
- Section spécifique sur le comportement Docker (stratégie hybride de facto)

### 2. Tests Fonctionnels

Créer `KeysetBlobFileSyncTest` pour tester:
- Synchronisation fichier ↔ BD en mode HYBRID
- Récupération après désynchronisation
- Comportement quand la BD est inaccessible (fallback fichier)
- Comportement avec volume Docker partagé (simulation)

### 3. Monitoring/Alerting

Ajouter des métriques pour:
- Fréquence des rechargements de keyset depuis la BD
- Échecs de synchronisation BD
- Temps de synchronisation moyen

### 4. Amélioration de la Robustesse

Envisager:
- Utiliser `version` (optimistic locking) dans `saveKeysetToDatabase()` pour éviter les overwrites
- Retry logic avec backoff exponentiel pour les échecs de sauvegarde BD
- Health check endpoint pour vérifier la synchronisation keyset