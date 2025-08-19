# OpenAPI Specification Management

## Vue d'ensemble

Ce dossier contient les scripts pour uniformiser la gestion des spécifications OpenAPI dans les projets demo d'Ezkey.

## Problème initial

Les projets demo utilisaient deux approches différentes pour les spécifications OpenAPI :

- **ezkey-demo-device** : URL live (`http://localhost:8080/v3/api-docs`)
- **ezkey-demo-app-acme** : Fichier local (`openapi-spec.json`)

## Solution adoptée : Fichier local uniformisé

### Pourquoi cette approche ?

**Avantages :**
- ✅ **Stabilité des builds** : Pas de dépendance réseau
- ✅ **Contrôle de version** : Gestion contrôlée des changements d'API
- ✅ **CI/CD simple** : Pas besoin de démarrer des services
- ✅ **Reproductibilité** : Builds identiques à chaque fois
- ✅ **Indépendance** : Développement possible sans API en cours

**Inconvénients :**
- ❌ **Maintenance manuelle** : Nécessite de mettre à jour le fichier
- ❌ **Risque de désynchronisation** : Possibilité d'avoir des DTOs obsolètes

## Scripts de synchronisation

### Script Bash (Linux/macOS/Git Bash)
```bash
./scripts/update-openapi-specs.sh [OPTIONS]
```

### Script Windows Batch
```cmd
scripts\update-openapi-specs.bat [OPTIONS]
```

### Options disponibles
- `--app` : Mettre à jour uniquement demo-app-acme
- `--device` : Mettre à jour uniquement demo-device
- `--all` : Mettre à jour les deux (par défaut)
- `--help` : Afficher l'aide

## Workflow recommandé

### 1. Développement quotidien
```bash
# Démarrer les APIs
mvn spring-boot:run -pl ezkey-auth-api
mvn spring-boot:run -pl ezkey-admin-api

# Mettre à jour les specs quand l'API change
./scripts/update-openapi-specs.sh

# Recompiler les projets demo
mvn clean compile -pl ezkey-demo-device
mvn clean compile -pl ezkey-demo-app-acme
```

### 2. Avant un commit
```bash
# Vérifier que les specs sont à jour
./scripts/update-openapi-specs.sh --all

# Tester que tout compile
mvn clean compile -pl ezkey-demo-device,ezkey-demo-app-acme
```

### 3. CI/CD
Les builds CI/CD utilisent directement les fichiers locaux, garantissant la stabilité.

## Structure des fichiers

```
ezkey-demo-device/
├── openapi-spec.json          # Spec Auth API (local)
└── pom.xml                    # Utilise le fichier local

ezkey-demo-app-acme/
├── openapi-spec.json          # Spec Admin API (local)
└── pom.xml                    # Utilise le fichier local

scripts/
├── update-openapi-specs.sh    # Script bash
├── update-openapi-specs.bat   # Script Windows
└── README.md                  # Cette documentation
```

## Configuration Maven

Les deux projets utilisent maintenant la même configuration :

```xml
<inputSpec>${project.basedir}/openapi-spec.json</inputSpec>
```

## Gestion des erreurs

### Si l'API n'est pas disponible
Le script affiche un message d'erreur et ne modifie pas les fichiers existants.

### Si le téléchargement échoue
Le script restaure automatiquement la sauvegarde précédente.

### Validation JSON
Le script valide automatiquement le JSON téléchargé (si `jq` est installé).

## Prérequis

- `curl` : Pour télécharger les spécifications
- `jq` (optionnel) : Pour la validation JSON
- APIs démarrées : Pour pouvoir télécharger les specs

## Exemples d'utilisation

### Mise à jour complète
```bash
./scripts/update-openapi-specs.sh
```

### Mise à jour sélective
```bash
./scripts/update-openapi-specs.sh --device
./scripts/update-openapi-specs.sh --app
```

### Vérification de l'aide
```bash
./scripts/update-openapi-specs.sh --help
```

## Intégration avec le workflow de développement

1. **Modification d'API** : Développer dans `ezkey-auth-api` ou `ezkey-admin-api`
2. **Test local** : Démarrer l'API et tester
3. **Synchronisation** : Exécuter le script de mise à jour
4. **Validation** : Recompiler les projets demo
5. **Commit** : Inclure les fichiers `openapi-spec.json` mis à jour

## Avantages de cette approche

- **Cohérence** : Les deux projets utilisent la même approche
- **Fiabilité** : Pas de builds cassés par des problèmes réseau
- **Traçabilité** : Les changements d'API sont visibles dans Git
- **Flexibilité** : Possibilité de revenir à une version précédente
- **Performance** : Génération plus rapide (pas de téléchargement à chaque build)
