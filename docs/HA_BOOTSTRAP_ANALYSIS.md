# Analyse des Problèmes de Bootstrap en Mode HA

## Date
2025-01-XX

## Contexte

Lors de l'exécution du stack Docker en mode High Availability (HA) avec 2 instances de chaque API, plusieurs problèmes critiques ont été identifiés concernant :

1. Le montage du volume MasterKey sur les instances admin-api
2. L'extraction des credentials bootstrap depuis les logs Docker
3. L'identification de l'instance qui a créé l'admin global initial
4. La validation que ShedLock protège correctement le processus de bootstrap

## Problèmes Identifiés

### 1. Génération du MasterKey pour le Stack HA

**Problème** : Le script `generate-encryption-keys.sh` ne supporte pas le mode HA.

**Détails** :
- Le script utilise un nom de volume hardcodé : `ezkey_encryption-secrets` (mode standard) ou `ezkey-native_encryption-secrets-native` (mode native)
- Le stack HA utilise le volume `encryption-secrets-ha` qui devient `ezkey-ha_encryption-secrets-ha` avec le préfixe Docker Compose
- Le script `clean-start.sh` appelle `generate-encryption-keys.sh` sans option `--ha`, donc le volume HA n'est jamais créé/initialisé

**Impact** :
- Les instances admin-api-1 et admin-api-2 ne peuvent pas accéder au MasterKey
- Les applications échouent au démarrage car elles ne peuvent pas charger les clés de chiffrement
- Le bootstrap ne peut pas s'exécuter

**Fichiers concernés** :
- `docker/generate-encryption-keys.sh` : Nécessite une option `--ha`
- `ezkey-tests/clean-start.sh` : Nécessite de passer l'option `--ha` au script de génération

### 2. Extraction des Credentials Bootstrap en Mode HA

**Problème** : `BootstrapCredentialsExtractor` utilise un nom de conteneur hardcodé qui n'existe pas en mode HA.

**Détails** :
- Le code utilise `DOCKER_CONTAINER_NAME = "ezkey-admin-api"` (ligne 63)
- En mode HA, les conteneurs s'appellent `ezkey-admin-api-1` et `ezkey-admin-api-2`
- Le test `BootstrapCredentialsExtractionTest.testLoadCredentialsFromFile()` échoue car il ne peut pas trouver le conteneur

**Impact** :
- Impossible d'extraire les credentials bootstrap depuis les logs
- Les tests fonctionnels ne peuvent pas s'exécuter car ils dépendent de ces credentials
- Le processus d'initialisation ne peut pas être validé

**Fichiers concernés** :
- `ezkey-tests/src/test/java/org/ezkey/tests/util/BootstrapCredentialsExtractor.java` : Nécessite de détecter le mode HA et de lire les logs de la bonne instance

**Question critique** : Comment identifier quelle instance (`admin-api-1` ou `admin-api-2`) a créé l'admin global initial ?

### 3. Identification de l'Instance qui a Créé l'Admin Global

**Problème** : En mode HA, il faut identifier quelle instance a exécuté le bootstrap pour lire ses logs.

**Analyse** :
- ShedLock garantit qu'une seule instance exécute le bootstrap (voir section 4)
- Les deux services de bootstrap (`InitialGlobalAdminService` et `AdminBootstrapService`) utilisent le même lock `ADMIN_STARTUP_BOOTSTRAP`
- Les logs de bootstrap sont écrits dans les logs de l'instance qui a acquis le lock
- Il faut soit :
  - Lire les logs des deux instances et trouver celle qui contient les credentials
  - Ou interroger la base de données ShedLock pour identifier l'instance qui détient le lock

**Solution proposée** :
1. Lire les logs des deux instances admin-api en mode HA
2. Chercher le pattern "GLOBAL ADMIN PASSWORDLESS ENROLLMENT" dans les deux
3. Utiliser les logs de l'instance qui contient ce pattern
4. Alternative : Interroger la table `ezkey_shedlock` pour identifier `locked_by` du lock `ADMIN_STARTUP_BOOTSTRAP`

### 4. Vérification de la Protection ShedLock

**Analyse du code** :

#### InitialGlobalAdminService
- **Ligne 80-88** : Utilise `@EventListener(ApplicationReadyEvent.class)` avec `@Order(ApplicationReadyStartupOrder.INITIAL_GLOBAL_ADMIN)` (1)
- **Ligne 87** : Appelle `lockingTaskExecutor.executeWithLock("ADMIN_STARTUP_BOOTSTRAP", Duration.ofMinutes(5), this::doInitializeGlobalAdmin)`
- ✅ **Protection ShedLock confirmée**

#### AdminBootstrapService
- **Ligne 136-148** : Utilise `@EventListener(ApplicationReadyEvent.class)` avec `@Order(ApplicationReadyStartupOrder.ADMIN_MFA_BOOTSTRAP)` (2)
- **Ligne 147** : Appelle `lockingTaskExecutor.executeWithLock("ADMIN_STARTUP_BOOTSTRAP", Duration.ofMinutes(5), this::doBootstrapAdminMfa)`
- ✅ **Protection ShedLock confirmée**

**Observation importante** :
- `KeyRotationService.initializeKeysetSync()` s'exécute d'abord (`@Order` 0) pour peupler `ezkey_encryption_key` avant l'insert d'enrollment
- Les deux services de bootstrap utilisent le **même nom de lock** (`ADMIN_STARTUP_BOOTSTRAP`)
- `InitialGlobalAdminService` s'exécute ensuite (`@Order` 1)
- Une fois `InitialGlobalAdminService` terminé, le lock est libéré
- `AdminBootstrapService` peut alors acquérir le lock et s'exécuter

**Risque potentiel** :
- Si les deux services tentent d'acquérir le lock simultanément, seul `InitialGlobalAdminService` réussira (car il s'exécute en premier)
- `AdminBootstrapService` attendra que le lock soit libéré
- Cela devrait fonctionner correctement car les événements Spring sont séquentiels par défaut

**Recommandation** :
- ✅ La protection ShedLock est correctement implémentée
- ⚠️ Considérer utiliser deux locks différents pour plus de clarté :
  - `ADMIN_INITIAL_GLOBAL_ADMIN_BOOTSTRAP` pour `InitialGlobalAdminService`
  - `ADMIN_MFA_BOOTSTRAP` pour `AdminBootstrapService`
- Mais ce n'est pas critique car l'ordre d'exécution est garanti par `@Order(1)`

## Solutions Proposées

### Solution 1 : Support du Mode HA dans generate-encryption-keys.sh

**Modifications requises** :

1. **Ajouter l'option `--ha` au script** :
   ```bash
   # Dans generate-encryption-keys.sh
   --ha)
       HA_MODE="1"
       VOLUME_NAME="ezkey-ha_encryption-secrets-ha"
       ;;
   ```

2. **Mettre à jour clean-start.sh** :
   ```bash
   # Dans clean-start.sh, Step 3
   if [ -n "$HA_MODE" ]; then
       bash "${DOCKER_DIR}/generate-encryption-keys.sh" --ha
   ```

### Solution 2 : Détection Automatique du Mode HA dans BootstrapCredentialsExtractor

**Modifications requises** :

1. **Détecter le mode HA** :
   - Vérifier si les conteneurs `ezkey-admin-api-1` et `ezkey-admin-api-2` existent
   - Si oui, mode HA détecté

2. **Identifier l'instance qui a créé l'admin** :
   - Option A : Lire les logs des deux instances et trouver celle qui contient "GLOBAL ADMIN PASSWORDLESS ENROLLMENT"
   - Option B : Interroger la table `ezkey_shedlock` pour identifier `locked_by` du lock `ADMIN_STARTUP_BOOTSTRAP`

3. **Lire les logs de la bonne instance** :
   - Utiliser le nom du conteneur identifié pour lire les logs

**Code proposé** :
```java
private String detectAdminApiContainer() {
    // Try HA mode first
    if (containerExists("ezkey-admin-api-1") && containerExists("ezkey-admin-api-2")) {
        // Find which instance created the admin by checking logs or ShedLock table
        return findInstanceWithBootstrapLogs();
    }
    // Fallback to standard mode
    return "ezkey-admin-api";
}

private String findInstanceWithBootstrapLogs() {
    // Option 1: Check logs of both instances
    for (String container : Arrays.asList("ezkey-admin-api-1", "ezkey-admin-api-2")) {
        String logs = readDockerLogs(container);
        if (logs.contains("GLOBAL ADMIN PASSWORDLESS ENROLLMENT")) {
            return container;
        }
    }
    // Option 2: Query ShedLock table (more reliable)
    return queryShedLockForBootstrapInstance();
}
```

### Solution 3 : Amélioration de la Traçabilité

**Recommandations** :

1. **Ajouter des logs explicites** :
   - Logger l'instance ID (`EZKEY_INSTANCE_ID`) dans les logs de bootstrap
   - Logger explicitement "Instance X is creating global admin" et "Instance X created global admin"

2. **Utiliser la table ShedLock pour l'identification** :
   - La colonne `locked_by` contient l'identifiant de l'instance
   - Interroger cette table pour identifier l'instance qui a créé l'admin

## Plan d'Action Recommandé

### Phase 1 : Corrections Critiques (Priorité Haute)

1. ✅ Ajouter le support `--ha` à `generate-encryption-keys.sh`
2. ✅ Mettre à jour `clean-start.sh` pour passer `--ha` au script de génération
3. ✅ Modifier `BootstrapCredentialsExtractor` pour détecter le mode HA
4. ✅ Implémenter la logique pour identifier l'instance qui a créé l'admin

### Phase 2 : Améliorations (Priorité Moyenne)

1. Améliorer les logs de bootstrap pour inclure l'instance ID
2. Ajouter une méthode utilitaire pour interroger ShedLock et identifier l'instance
3. Documenter le processus de bootstrap en mode HA

### Phase 3 : Optimisations (Priorité Basse)

1. Considérer séparer les locks ShedLock pour plus de clarté
2. Ajouter des tests fonctionnels spécifiques pour le bootstrap HA
3. Créer un guide de dépannage pour les problèmes de bootstrap HA

## Conclusion

Les problèmes identifiés sont tous liés au fait que le système a été conçu initialement pour un seul conteneur, et le mode HA nécessite des adaptations. Les solutions proposées sont simples à implémenter et résolvent tous les problèmes identifiés.

**Points critiques à résoudre** :
1. ✅ Génération du MasterKey pour le volume HA
2. ✅ Extraction des credentials depuis la bonne instance
3. ✅ Identification de l'instance qui a créé l'admin global

**Protection ShedLock** : ✅ Confirmée - Le bootstrap est correctement protégé contre les exécutions simultanées.
