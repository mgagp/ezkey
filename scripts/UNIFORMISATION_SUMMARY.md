# Résumé de l'uniformisation des spécifications OpenAPI

## Problème initial

Les projets demo d'Ezkey utilisaient deux approches différentes pour les spécifications OpenAPI :

- **ezkey-demo-device** : URL live (`http://localhost:8080/v3/api-docs`)
- **ezkey-demo-app-acme** : Fichier local (`openapi-spec.json`)

Cette disparité causait :
- Des builds instables (dépendance réseau)
- Des difficultés en CI/CD
- Une maintenance incohérente

## Solution implémentée

### 1. Approche uniformisée : Fichier local

**Décision :** Utiliser des fichiers locaux pour les deux projets

**Avantages :**
- ✅ Stabilité des builds (pas de dépendance réseau)
- ✅ Contrôle de version (gestion Git des changements d'API)
- ✅ CI/CD simple (pas besoin de démarrer des services)
- ✅ Reproductibilité (builds identiques)
- ✅ Indépendance (développement possible sans API)

### 2. Scripts de synchronisation

**Scripts créés :**
- `scripts/update-openapi-specs.sh` (Bash - Linux/macOS/Git Bash)
- `scripts/update-openapi-specs.bat` (Windows Batch)

**Fonctionnalités :**
- Téléchargement automatique depuis les APIs en cours
- Sauvegarde automatique avant modification
- Validation JSON (si `jq` disponible)
- Restauration automatique en cas d'échec
- Options pour mise à jour sélective

### 3. Configuration Maven uniformisée

**Avant :**
```xml
<!-- demo-device -->
<inputSpec>http://localhost:8080/v3/api-docs</inputSpec>

<!-- demo-app-acme -->
<inputSpec>${project.basedir}/openapi-spec.json</inputSpec>
```

**Après :**
```xml
<!-- Les deux projets -->
<inputSpec>${project.basedir}/openapi-spec.json</inputSpec>
```

## Fichiers modifiés

### 1. Configuration Maven
- `ezkey-demo-device/pom.xml` : Changé de URL live vers fichier local

### 2. Scripts créés
- `scripts/update-openapi-specs.sh` : Script bash principal
- `scripts/update-openapi-specs.bat` : Script Windows
- `scripts/README.md` : Documentation complète
- `scripts/UNIFORMISATION_SUMMARY.md` : Ce résumé

### 3. Fichiers de spécification
- `ezkey-demo-device/openapi-spec.json` : Téléchargé depuis auth-api
- `ezkey-demo-app-acme/openapi-spec.json` : Déjà existant

## Workflow recommandé

### Développement quotidien
```bash
# 1. Démarrer les APIs
mvn spring-boot:run -pl ezkey-auth-api
mvn spring-boot:run -pl ezkey-admin-api

# 2. Modifier l'API selon les besoins

# 3. Mettre à jour les specs
./scripts/update-openapi-specs.sh

# 4. Recompiler les projets demo
mvn clean compile -pl ezkey-demo-device,ezkey-demo-app-acme
```

### Avant un commit
```bash
# Vérifier que les specs sont à jour
./scripts/update-openapi-specs.sh --all

# Tester la compilation
mvn clean compile -pl ezkey-demo-device,ezkey-demo-app-acme
```

## Utilisation des scripts

### Options disponibles
```bash
./scripts/update-openapi-specs.sh --help
```

- `--app` : Mettre à jour uniquement demo-app-acme
- `--device` : Mettre à jour uniquement demo-device  
- `--all` : Mettre à jour les deux (par défaut)
- `--help` : Afficher l'aide

### Exemples d'utilisation
```bash
# Mise à jour complète
./scripts/update-openapi-specs.sh

# Mise à jour sélective
./scripts/update-openapi-specs.sh --device
./scripts/update-openapi-specs.sh --app
```

## Avantages obtenus

### 1. Stabilité
- Plus de builds cassés par des problèmes réseau
- Compilation fiable et reproductible

### 2. Traçabilité
- Les changements d'API sont visibles dans Git
- Historique des évolutions de l'API

### 3. Flexibilité
- Possibilité de revenir à une version précédente
- Développement possible sans API en cours

### 4. Performance
- Génération plus rapide (pas de téléchargement à chaque build)
- Moins de dépendances externes

### 5. CI/CD
- Builds stables et prévisibles
- Pas besoin de démarrer des services

## Prérequis

- `curl` : Pour télécharger les spécifications
- `jq` (optionnel) : Pour la validation JSON
- APIs démarrées : Pour pouvoir télécharger les specs

## Validation

✅ **Tests effectués :**
- Téléchargement de la spec auth-api
- Compilation du projet demo-device
- Génération des DTOs
- Fonctionnement du script de mise à jour

✅ **Résultats :**
- BUILD SUCCESS sur demo-device
- Scripts fonctionnels sur Windows et Linux
- Documentation complète

## Conclusion

L'uniformisation est **terminée avec succès**. Les deux projets demo utilisent maintenant la même approche (fichier local) avec des scripts de synchronisation robustes pour maintenir les spécifications à jour.

Cette solution offre un bon équilibre entre :
- **Stabilité** (builds fiables)
- **Flexibilité** (contrôle de version)
- **Simplicité** (scripts automatisés)
- **Maintenabilité** (approche uniforme)
