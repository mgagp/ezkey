# Guide de Test de Compilation Native - Étape par Étape

Ce guide vous accompagne dans les tests de validation de la compilation native pour ADMIN_API et AUTH_API.

## Objectifs des Tests

1. **Valider la compilation native** avec AdminNativeConfiguration.java et AuthNativeConfiguration.java
2. **Tester l'hypothèse de redondance** des fichiers JSON
3. **Valider le fonctionnement** des images natives compilées

## Prérequis

- Docker installé et en cours d'exécution
- Maven 3.6+ installé
- Java 21+ installé
- Accès réseau pour télécharger les dépendances Maven et les buildpacks

## Phase 1: Test de Compilation Native - Configuration Actuelle (Avec JSON)

### Étape 1.1: Préparation de l'environnement

Vérifiez que Docker fonctionne:
```bash
docker info
```

Vérifiez que Maven fonctionne:
```bash
mvn --version
```

### Étape 1.2: Compilation Native Auth API (Configuration Actuelle)

**Objectif**: Compiler auth-api avec la configuration actuelle (Java + JSON) pour établir une baseline.

```bash
cd c:\github\ezkey

# Nettoyer les builds précédents
mvn clean -pl ezkey-auth-api

# Compiler l'image native avec buildpack
mvn spring-boot:build-image -pl ezkey-auth-api -Pnative \
    -Dspring-boot.build-image.imageName=ezkey-auth-api-native \
    -DskipTests
```

**Temps estimé**: 10-30 minutes (première fois)

**Points à vérifier**:
- ✅ La compilation démarre sans erreur
- ✅ Le buildpack télécharge les dépendances
- ✅ La compilation GraalVM se termine avec succès
- ✅ L'image Docker `ezkey-auth-api-native:latest` est créée

**Résultat attendu**: Image Docker créée avec succès

**Feedback à donner**:
- Durée de compilation
- Erreurs éventuelles
- Taille de l'image créée (`docker images ezkey-auth-api-native`)

---

### Étape 1.3: Test d'Exécution Auth API (Configuration Actuelle)

**Objectif**: Valider que l'image native fonctionne correctement.

**Prérequis**: PostgreSQL doit être accessible. Vous pouvez utiliser Docker:

```bash
# Démarrer PostgreSQL si pas déjà démarré
docker run -d --name ezkey-postgres-test \
    -e POSTGRES_DB=ezkey_db \
    -e POSTGRES_USER=postgres \
    -e POSTGRES_PASSWORD=ezkey \
    -p 5433:5432 \
    postgres:18-alpine
```

**Attendre que PostgreSQL soit prêt**:
```bash
docker exec ezkey-postgres-test pg_isready -U postgres
```

**Exécuter l'image native**:
```bash
docker run -d --name ezkey-auth-api-test \
    -p 8080:8080 \
    -e SPRING_PROFILES_ACTIVE=native \
    -e SPRING_DATASOURCE_URL=jdbc:postgresql://host.docker.internal:5433/ezkey_db \
    -e SPRING_DATASOURCE_USERNAME=postgres \
    -e SPRING_DATASOURCE_PASSWORD=ezkey \
    --add-host=host.docker.internal:host-gateway \
    ezkey-auth-api-native:latest
```

**Vérifier les logs**:
```bash
docker logs -f ezkey-auth-api-test
```

**Points à vérifier**:
- ✅ L'application démarre sans erreur
- ✅ Pas d'erreurs de réflexion (`ClassNotFoundException`, `NoSuchMethodException`)
- ✅ Pas d'erreurs de sérialisation
- ✅ L'endpoint de santé répond

**Tester l'endpoint de santé**:
```bash
# Attendre quelques secondes pour le démarrage
sleep 10

# Tester l'endpoint
curl http://localhost:8080/actuator/health
```

**Résultat attendu**:
- Démarrage rapide (~2-5 secondes)
- Health endpoint retourne `{"status":"UP"}` ou similaire
- Pas d'erreurs dans les logs

**Feedback à donner**:
- Temps de démarrage observé
- Erreurs éventuelles dans les logs
- Résultat du health check

**Nettoyer après test**:
```bash
docker stop ezkey-auth-api-test
docker rm ezkey-auth-api-test
```

---

### Étape 1.4: Compilation Native Admin API (Configuration Actuelle)

**Objectif**: Compiler admin-api avec la configuration actuelle (Java + JSON).

```bash
cd c:\github\ezkey

# Nettoyer les builds précédents
mvn clean -pl ezkey-admin-api

# Compiler l'image native avec buildpack
mvn spring-boot:build-image -pl ezkey-admin-api -Pnative \
    -Dspring-boot.build-image.imageName=ezkey-admin-api-native \
    -DskipTests
```

**Temps estimé**: 15-40 minutes (première fois, plus complexe que auth-api)

**Points à vérifier**:
- ✅ La compilation démarre sans erreur
- ✅ Le buildpack télécharge les dépendances
- ✅ La compilation GraalVM se termine avec succès
- ✅ L'image Docker `ezkey-admin-api-native:latest` est créée

**Résultat attendu**: Image Docker créée avec succès

**Feedback à donner**:
- Durée de compilation
- Erreurs éventuelles
- Taille de l'image créée (`docker images ezkey-admin-api-native`)

---

### Étape 1.5: Test d'Exécution Admin API (Configuration Actuelle)

**Objectif**: Valider que l'image native admin-api fonctionne correctement.

**Exécuter l'image native**:
```bash
docker run -d --name ezkey-admin-api-test \
    -p 9080:9080 \
    -e SPRING_PROFILES_ACTIVE=native \
    -e SPRING_DATASOURCE_URL=jdbc:postgresql://host.docker.internal:5433/ezkey_db \
    -e SPRING_DATASOURCE_USERNAME=postgres \
    -e SPRING_DATASOURCE_PASSWORD=ezkey \
    --add-host=host.docker.internal:host-gateway \
    ezkey-admin-api-native:latest
```

**Vérifier les logs**:
```bash
docker logs -f ezkey-admin-api-test
```

**Points à vérifier**:
- ✅ L'application démarre sans erreur
- ✅ Pas d'erreurs de réflexion
- ✅ Pas d'erreurs de sérialisation
- ✅ L'endpoint de santé répond

**Tester l'endpoint de santé**:
```bash
# Attendre quelques secondes pour le démarrage
sleep 15

# Tester l'endpoint
curl http://localhost:9080/actuator/health
```

**Résultat attendu**:
- Démarrage rapide (~3-5 secondes)
- Health endpoint retourne `{"status":"UP"}` ou similaire
- Pas d'erreurs dans les logs

**Feedback à donner**:
- Temps de démarrage observé
- Erreurs éventuelles dans les logs
- Résultat du health check

**Nettoyer après test**:
```bash
docker stop ezkey-admin-api-test
docker rm ezkey-admin-api-test
```

---

## Phase 2: Test de Redondance - Sans Fichiers JSON

### Étape 2.1: Sauvegarde des Fichiers JSON

**Objectif**: Sauvegarder les fichiers JSON avant de les supprimer pour pouvoir les restaurer si nécessaire.

```bash
cd c:\github\ezkey

# Créer un dossier de sauvegarde
mkdir -p backup-native-config

# Sauvegarder les fichiers JSON de auth-api
cp ezkey-auth-api/src/main/resources/META-INF/native-image/org.ezkey/ezkey-auth-api/reflect-config.json backup-native-config/auth-reflect-config.json
cp ezkey-auth-api/src/main/resources/META-INF/native-image/org.ezkey/ezkey-auth-api/serialization-config.json backup-native-config/auth-serialization-config.json
cp ezkey-auth-api/src/main/resources/META-INF/native-image/org.ezkey/ezkey-auth-api/resource-config.json backup-native-config/auth-resource-config.json

# Sauvegarder les fichiers JSON de admin-api
cp ezkey-admin-api/src/main/resources/META-INF/native-image/org.ezkey/ezkey-admin-api/reflect-config.json backup-native-config/admin-reflect-config.json
cp ezkey-admin-api/src/main/resources/META-INF/native-image/org.ezkey/ezkey-admin-api/serialization-config.json backup-native-config/admin-serialization-config.json
cp ezkey-admin-api/src/main/resources/META-INF/native-image/org.ezkey/ezkey-admin-api/resource-config.json backup-native-config/admin-resource-config.json
```

**Vérifier la sauvegarde**:
```bash
ls backup-native-config/
```

**Feedback à donner**: Confirmation que les fichiers sont sauvegardés

---

### Étape 2.2: Suppression des Fichiers JSON Auth API

**Objectif**: Supprimer les fichiers JSON pour tester si AuthNativeConfiguration.java suffit.

```bash
cd c:\github\ezkey

# Supprimer les fichiers JSON de auth-api
rm ezkey-auth-api/src/main/resources/META-INF/native-image/org.ezkey/ezkey-auth-api/reflect-config.json
rm ezkey-auth-api/src/main/resources/META-INF/native-image/org.ezkey/ezkey-auth-api/serialization-config.json
rm ezkey-auth-api/src/main/resources/META-INF/native-image/org.ezkey/ezkey-auth-api/resource-config.json
```

**Vérifier la suppression**:
```bash
ls ezkey-auth-api/src/main/resources/META-INF/native-image/org.ezkey/ezkey-auth-api/
```

**Résultat attendu**: Seul `native-image.properties` doit rester

**Feedback à donner**: Confirmation que les fichiers sont supprimés

---

### Étape 2.3: Compilation Native Auth API (Sans JSON)

**Objectif**: Compiler auth-api sans les fichiers JSON pour tester l'hypothèse de redondance.

```bash
cd c:\github\ezkey

# Nettoyer les builds précédents
mvn clean -pl ezkey-auth-api

# Compiler l'image native avec buildpack (sans JSON)
mvn spring-boot:build-image -pl ezkey-auth-api -Pnative \
    -Dspring-boot.build-image.imageName=ezkey-auth-api-native-nojson \
    -DskipTests
```

**Temps estimé**: 10-30 minutes

**Points à vérifier**:
- ✅ La compilation démarre sans erreur
- ✅ Le buildpack télécharge les dépendances
- ✅ La compilation GraalVM se termine avec succès
- ✅ L'image Docker `ezkey-auth-api-native-nojson:latest` est créée

**Résultat attendu**:
- **Si succès**: Les fichiers JSON sont redondants ✅
- **Si échec**: Noter les erreurs pour identifier ce qui manque

**Feedback à donner**:
- Durée de compilation
- Erreurs éventuelles (noter les messages d'erreur précis)
- Taille de l'image créée

---

### Étape 2.4: Test d'Exécution Auth API (Sans JSON)

**Objectif**: Valider que l'image native compilée sans JSON fonctionne correctement.

**Exécuter l'image native**:
```bash
docker run -d --name ezkey-auth-api-test-nojson \
    -p 8081:8080 \
    -e SPRING_PROFILES_ACTIVE=native \
    -e SPRING_DATASOURCE_URL=jdbc:postgresql://host.docker.internal:5433/ezkey_db \
    -e SPRING_DATASOURCE_USERNAME=postgres \
    -e SPRING_DATASOURCE_PASSWORD=ezkey \
    --add-host=host.docker.internal:host-gateway \
    ezkey-auth-api-native-nojson:latest
```

**Vérifier les logs**:
```bash
docker logs -f ezkey-auth-api-test-nojson
```

**Points à vérifier**:
- ✅ L'application démarre sans erreur
- ✅ Pas d'erreurs de réflexion (`ClassNotFoundException`, `NoSuchMethodException`)
- ✅ Pas d'erreurs de sérialisation
- ✅ L'endpoint de santé répond

**Tester l'endpoint de santé**:
```bash
# Attendre quelques secondes pour le démarrage
sleep 10

# Tester l'endpoint
curl http://localhost:8081/actuator/health
```

**Tester un endpoint fonctionnel** (si health check OK):
```bash
# Tester l'endpoint d'enrollment bind (devrait retourner 400 ou 404, mais pas d'erreur de réflexion)
curl -X POST http://localhost:8081/api/v1/enrollments/bind \
    -H "Content-Type: application/json" \
    -d '{"enrollmentId":1,"enrollmentProofToken":"test"}'
```

**Résultat attendu**:
- **Si succès**: Les fichiers JSON sont redondants, on peut les supprimer définitivement ✅
- **Si échec**: Noter les erreurs précises pour identifier ce qui manque dans AuthNativeConfiguration.java

**Feedback à donner**:
- Temps de démarrage observé
- Erreurs éventuelles dans les logs (copier les messages d'erreur complets)
- Résultat du health check
- Résultat du test d'endpoint

**Nettoyer après test**:
```bash
docker stop ezkey-auth-api-test-nojson
docker rm ezkey-auth-api-test-nojson
```

---

### Étape 2.5: Suppression des Fichiers JSON Admin API

**Objectif**: Supprimer les fichiers JSON pour tester si AdminNativeConfiguration.java suffit.

```bash
cd c:\github\ezkey

# Supprimer les fichiers JSON de admin-api
rm ezkey-admin-api/src/main/resources/META-INF/native-image/org.ezkey/ezkey-admin-api/reflect-config.json
rm ezkey-admin-api/src/main/resources/META-INF/native-image/org.ezkey/ezkey-admin-api/serialization-config.json
rm ezkey-admin-api/src/main/resources/META-INF/native-image/org.ezkey/ezkey-admin-api/resource-config.json
```

**Vérifier la suppression**:
```bash
ls ezkey-admin-api/src/main/resources/META-INF/native-image/org.ezkey/ezkey-admin-api/
```

**Résultat attendu**: Seul `native-image.properties` doit rester

**Feedback à donner**: Confirmation que les fichiers sont supprimés

---

### Étape 2.6: Compilation Native Admin API (Sans JSON)

**Objectif**: Compiler admin-api sans les fichiers JSON pour tester l'hypothèse de redondance.

```bash
cd c:\github\ezkey

# Nettoyer les builds précédents
mvn clean -pl ezkey-admin-api

# Compiler l'image native avec buildpack (sans JSON)
mvn spring-boot:build-image -pl ezkey-admin-api -Pnative \
    -Dspring-boot.build-image.imageName=ezkey-admin-api-native-nojson \
    -DskipTests
```

**Temps estimé**: 15-40 minutes

**Points à vérifier**:
- ✅ La compilation démarre sans erreur
- ✅ Le buildpack télécharge les dépendances
- ✅ La compilation GraalVM se termine avec succès
- ✅ L'image Docker `ezkey-admin-api-native-nojson:latest` est créée

**Résultat attendu**:
- **Si succès**: Les fichiers JSON sont redondants ✅
- **Si échec**: Noter les erreurs pour identifier ce qui manque

**Feedback à donner**:
- Durée de compilation
- Erreurs éventuelles (noter les messages d'erreur précis)
- Taille de l'image créée

---

### Étape 2.7: Test d'Exécution Admin API (Sans JSON)

**Objectif**: Valider que l'image native admin-api compilée sans JSON fonctionne correctement.

**Exécuter l'image native**:
```bash
docker run -d --name ezkey-admin-api-test-nojson \
    -p 9081:9080 \
    -e SPRING_PROFILES_ACTIVE=native \
    -e SPRING_DATASOURCE_URL=jdbc:postgresql://host.docker.internal:5433/ezkey_db \
    -e SPRING_DATASOURCE_USERNAME=postgres \
    -e SPRING_DATASOURCE_PASSWORD=ezkey \
    --add-host=host.docker.internal:host-gateway \
    ezkey-admin-api-native-nojson:latest
```

**Vérifier les logs**:
```bash
docker logs -f ezkey-admin-api-test-nojson
```

**Points à vérifier**:
- ✅ L'application démarre sans erreur
- ✅ Pas d'erreurs de réflexion
- ✅ Pas d'erreurs de sérialisation
- ✅ L'endpoint de santé répond

**Tester l'endpoint de santé**:
```bash
# Attendre quelques secondes pour le démarrage
sleep 15

# Tester l'endpoint
curl http://localhost:9081/actuator/health
```

**Tester un endpoint fonctionnel** (si health check OK):
```bash
# Tester l'endpoint d'intégrations (devrait retourner 401, mais pas d'erreur de réflexion)
curl http://localhost:9081/api/v1/integrations
```

**Résultat attendu**:
- **Si succès**: Les fichiers JSON sont redondants, on peut les supprimer définitivement ✅
- **Si échec**: Noter les erreurs précises pour identifier ce qui manque dans AdminNativeConfiguration.java

**Feedback à donner**:
- Temps de démarrage observé
- Erreurs éventuelles dans les logs (copier les messages d'erreur complets)
- Résultat du health check
- Résultat du test d'endpoint

**Nettoyer après test**:
```bash
docker stop ezkey-admin-api-test-nojson
docker rm ezkey-admin-api-test-nojson
```

---

## Phase 3: Actions Basées sur les Résultats

### Scénario A: Tests Réussis (JSON Redondants)

Si tous les tests sont réussis sans les fichiers JSON:

1. **Supprimer définitivement les fichiers JSON**:
```bash
# Les fichiers sont déjà supprimés, on peut supprimer les sauvegardes aussi
rm -rf backup-native-config/
```

2. **Mettre à jour la documentation** pour indiquer que les JSON sont redondants

3. **Valider que la compilation fonctionne toujours**:
```bash
# Recompiler pour confirmer
mvn spring-boot:build-image -pl ezkey-auth-api -Pnative \
    -Dspring-boot.build-image.imageName=ezkey-auth-api-native -DskipTests
```

### Scénario B: Tests Échoués (JSON Nécessaires)

Si des erreurs surviennent sans les fichiers JSON:

1. **Restaurer les fichiers JSON**:
```bash
cp backup-native-config/auth-reflect-config.json ezkey-auth-api/src/main/resources/META-INF/native-image/org.ezkey/ezkey-auth-api/reflect-config.json
cp backup-native-config/auth-serialization-config.json ezkey-auth-api/src/main/resources/META-INF/native-image/org.ezkey/ezkey-auth-api/serialization-config.json
cp backup-native-config/auth-resource-config.json ezkey-auth-api/src/main/resources/META-INF/native-image/org.ezkey/ezkey-auth-api/resource-config.json

cp backup-native-config/admin-reflect-config.json ezkey-admin-api/src/main/resources/META-INF/native-image/org.ezkey/ezkey-admin-api/reflect-config.json
cp backup-native-config/admin-serialization-config.json ezkey-admin-api/src/main/resources/META-INF/native-image/org.ezkey/ezkey-admin-api/serialization-config.json
cp backup-native-config/admin-resource-config.json ezkey-admin-api/src/main/resources/META-INF/native-image/org.ezkey/ezkey-admin-api/resource-config.json
```

2. **Analyser les erreurs** et identifier ce qui manque dans les configurations Java

3. **Ajouter la configuration manquante** aux classes AdminNativeConfiguration.java ou AuthNativeConfiguration.java

4. **Réessayer les tests** après avoir ajouté la configuration manquante

---

## Checklist de Validation

### Auth API
- [ ] Compilation native réussie avec JSON
- [ ] Exécution native réussie avec JSON
- [ ] Compilation native réussie sans JSON
- [ ] Exécution native réussie sans JSON
- [ ] Health check fonctionne
- [ ] Endpoints fonctionnent (pas d'erreurs de réflexion)

### Admin API
- [ ] Compilation native réussie avec JSON
- [ ] Exécution native réussie avec JSON
- [ ] Compilation native réussie sans JSON
- [ ] Exécution native réussie sans JSON
- [ ] Health check fonctionne
- [ ] Endpoints fonctionnent (pas d'erreurs de réflexion)

---

## Commandes Utiles pour le Debugging

### Vérifier les images Docker
```bash
docker images | grep ezkey
```

### Vérifier les conteneurs en cours d'exécution
```bash
docker ps -a | grep ezkey
```

### Nettoyer les conteneurs de test
```bash
docker stop $(docker ps -aq --filter "name=ezkey-.*-test")
docker rm $(docker ps -aq --filter "name=ezkey-.*-test")
```

### Nettoyer les images de test
```bash
docker rmi ezkey-auth-api-native-nojson ezkey-admin-api-native-nojson
```

### Vérifier les logs d'un conteneur
```bash
docker logs ezkey-auth-api-test-nojson
docker logs ezkey-admin-api-test-nojson
```

### Entrer dans un conteneur pour inspection
```bash
docker exec -it ezkey-auth-api-test-nojson sh
```

---

## Notes Importantes

1. **Temps de compilation**: Les compilations natives prennent beaucoup de temps (10-40 minutes). C'est normal.

2. **Ressources**: La compilation native nécessite beaucoup de RAM et CPU. Fermez les applications inutiles.

3. **Première compilation**: La première compilation peut être plus lente car les buildpacks doivent télécharger les dépendances.

4. **Erreurs de compilation**: Si la compilation échoue, notez les messages d'erreur complets pour analyse.

5. **Erreurs d'exécution**: Si l'exécution échoue, vérifiez les logs Docker pour identifier les erreurs de réflexion ou de sérialisation.

6. **PostgreSQL**: Assurez-vous que PostgreSQL est accessible depuis les conteneurs Docker. Utilisez `host.docker.internal` sur Windows/Mac ou l'IP du conteneur PostgreSQL sur Linux.

---

## Prochaines Étapes Après Validation

Une fois les tests validés:

1. **Si JSON redondants**: Supprimer définitivement les fichiers JSON et mettre à jour la documentation
2. **Si JSON nécessaires**: Analyser ce qui manque et compléter les configurations Java
3. **Documenter les résultats**: Mettre à jour NATIVE_BUILD.md avec les résultats des tests
4. **Mettre à jour le plan**: Marquer les todos comme complétés dans le plan
