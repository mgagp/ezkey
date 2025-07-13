# Ezkey Core

Le module core d'Ezkey contient la logique métier partagée, les entités, les services et les outils de migration de base de données.

## Vue d'ensemble

Ce module fournit :
- **Entités JPA** : Modèles de données pour l'authentification et les intégrations
- **Services métier** : Logique de gestion des tentatives d'authentification et des inscriptions
- **Mappers** : Conversion entre DTOs et entités
- **Outils de migration** : Gestion des migrations de base de données via Flyway

## Structure du projet

```
ezkey-core/
├── src/main/java/org/ezkey/
│   ├── authattempt/     # Gestion des tentatives d'authentification
│   ├── enrollment/       # Gestion des inscriptions
│   ├── integration/      # Gestion des intégrations
│   ├── signature/        # Services de signature
│   └── core/            # Application principale pour Flyway
├── src/main/resources/
│   ├── application.properties  # Configuration de base de données
│   └── db/migration/          # Scripts de migration Flyway
└── README.md
```

## Configuration

### Base de données

Le module est configuré pour utiliser PostgreSQL avec les paramètres suivants :

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/ezkey_db
spring.datasource.username=postgres
spring.datasource.password=ezkey
spring.datasource.driver-class-name=org.postgresql.Driver
```

### Flyway

Les migrations sont configurées pour :
- S'exécuter automatiquement au démarrage
- Utiliser le répertoire `classpath:db/migration`
- Baser les migrations à partir de la version 1

## ezkey-flyway

### Description

`ezkey-flyway` est un outil en ligne de commande dédié à la gestion des migrations de base de données. Il permet d'exécuter les commandes Flyway sans démarrer les APIs web, offrant une solution légère et sécurisée pour la gestion des schémas de base de données.

### Installation

Les scripts `ezkey-flyway` sont disponibles à la racine du projet :
- `ezkey-flyway.bat` (Windows)
- `ezkey-flyway.sh` (Linux/Mac)

Pour Linux/Mac, rendez le script exécutable :
```bash
chmod +x ezkey-flyway.sh
```

### Utilisation

#### Commandes disponibles

| Commande | Description |
|----------|-------------|
| (aucune) | Exécute les migrations en attente (comportement par défaut) |
| `--migrate` | Exécute les migrations en attente |
| `--info` | Affiche les informations sur l'état des migrations |
| `--repair` | Répare l'historique des migrations |

#### Exemples d'utilisation

**Migration par défaut :**
```bash
# Windows
ezkey-flyway.bat

# Linux/Mac
./ezkey-flyway.sh
```

**Afficher les informations de migration :**
```bash
# Windows
ezkey-flyway.bat --info

# Linux/Mac
./ezkey-flyway.sh --info
```

**Répare l'historique des migrations :**
```bash
# Windows
ezkey-flyway.bat --repair

# Linux/Mac
./ezkey-flyway.sh --repair
```

### Fonctionnement technique

1. **Compilation automatique** : Le script vérifie si le projet est compilé et le compile automatiquement si nécessaire
2. **Gestion des dépendances** : Utilise Maven pour récupérer le classpath complet avec toutes les dépendances
3. **Exécution directe** : Lance l'application Java directement sans passer par Maven pour de meilleures performances
4. **Gestion d'erreurs** : Affiche des messages d'erreur clairs en cas de problème

### Avantages

- **Performance** : Exécution directe en Java sans overhead Maven
- **Sécurité** : Pas d'accès aux APIs web, uniquement aux migrations
- **Simplicité** : Interface en ligne de commande intuitive
- **Flexibilité** : Support de toutes les commandes Flyway principales
- **Robustesse** : Gestion automatique de la compilation et des dépendances

### Dépannage

**Erreur de compilation :**
```bash
# Vérifiez que Java 21 est installé
java -version

# Nettoyez et recompilez manuellement
cd ezkey-core
mvn clean compile
```

**Erreur de connexion à la base de données :**
- Vérifiez que PostgreSQL est démarré
- Vérifiez les paramètres de connexion dans `application.properties`
- Assurez-vous que la base de données `ezkey_db` existe

**Erreur de permissions (Linux/Mac) :**
```bash
chmod +x ezkey-flyway.sh
```

### Intégration CI/CD

L'outil peut être intégré dans vos pipelines CI/CD :

```yaml
# Exemple GitHub Actions
- name: Run database migrations
  run: ./ezkey-flyway.sh --migrate
```

```bash
# Exemple script de déploiement
#!/bin/bash
echo "Running database migrations..."
./ezkey-flyway.sh --migrate
if [ $? -eq 0 ]; then
    echo "Migrations completed successfully"
else
    echo "Migration failed"
    exit 1
fi
```

### Exemples de sortie

#### Info :
```
=== Flyway Migration Info ===
Current version: 1.0.0
Pending migrations: 0
Applied migrations: 1

Applied migrations:
  - 1.0.0 : Initial schema (executed: 2025-07-13T13:45:00)
=== Info Completed ===
```

#### Repair :
```
=== Starting Flyway Repair ===
=== Flyway Repair Completed ===
``` 