---
name: HA Docker Environment with Load Balancer
overview: Créer un environnement Docker Compose HA avec load balancer pour tester ShedLock avec 2 instances de chaque API, incluant comparaison des solutions de load balancing et tests fonctionnels pour valider l'exclusion mutuelle et les locks.
todos:
  - id: docker-compose-ha
    content: Créer docker-compose.ha.yml avec 2 instances de chaque API (admin-api-1, admin-api-2, auth-api-1, auth-api-2) et services HAProxy
    status: completed
  - id: haproxy-config
    content: Créer configurations HAProxy (haproxy-admin.cfg et haproxy-auth.cfg) avec health checks et round-robin
    status: completed
  - id: scripts-ha
    content: Créer scripts start-ha.sh/bat et manage-ha.sh/bat pour gérer le stack HA
    status: completed
  - id: test-exclusion
    content: "Implémenter test A: Exclusion mutuelle - vérifier qu'un job ne s'exécute qu'une fois avec 2 instances"
    status: completed
  - id: test-locks
    content: "Implémenter test B: Vérification locks DB - interroger table shedlock pour valider les locks"
    status: completed
  - id: test-failover
    content: "Implémenter test C (opportuniste): Failover - tester reprise si une instance crash"
    status: completed
  - id: test-helpers
    content: Créer ShedLockTestHelper avec méthodes pour interroger PostgreSQL et parser logs
    status: completed
  - id: documentation-ha
    content: Créer README-HA.md avec instructions complètes pour environnement HA et tests
    status: completed
---

# Environnement HA Docker avec Load Balancer pour Tests ShedLock

## Objectifs

1. Créer un environnement Docker Compose avec 2 instances de chaque API (admin-api, auth-api)
2. Configurer un load balancer pour distribuer le trafic
3. Valider le fonctionnement de ShedLock (exclusion mutuelle des jobs schedulés)
4. Préparer la base pour les tests de charge futurs

## Comparaison des Solutions de Load Balancing

### HAProxy

**Avantages:**

- Spécialisé dans le load balancing (L4/L7)
- Configuration simple et légère
- Excellent pour la terminaison HTTPS/TLS
- Health checks intégrés robustes
- Performance élevée (C)
- Supporte plusieurs algorithmes (round-robin, leastconn, etc.)
- Statistiques détaillées via interface web

**Inconvénients:**

- Moins polyvalent qu'Apache/Nginx (pas de serveur web complet)
- Configuration moins familière si on vient d'Apache/Nginx

**Recommandation:** Excellent choix pour ce cas d'usage (load balancing pur)

### Apache HTTPD

**Avantages:**

- Expérience existante dans l'équipe
- Très polyvalent (reverse proxy + serveur web)
- Modules mod_proxy_balancer intégré
- Configuration familière (.conf)
- Bon support de la terminaison HTTPS

**Inconvénients:**

- Plus lourd que HAProxy pour le load balancing pur
- Performance légèrement inférieure à HAProxy/Nginx
- Configuration plus verbeuse pour le load balancing
- Moins optimisé spécifiquement pour le load balancing

**Recommandation:** Choix valide si l'expérience de l'équipe prime, mais moins optimal que HAProxy

### Nginx

**Avantages:**

- Performance excellente (événementiel)
- Configuration moderne et concise
- Très léger en ressources
- Bon pour reverse proxy + load balancing
- Très populaire dans l'écosystème moderne

**Inconvénients:**

- Configuration différente d'Apache (courbe d'apprentissage si équipe Apache)
- Moins de modules que Apache

**Recommandation:** Excellent choix technique, mais nécessite adaptation si équipe Apache

### Combinaison HAProxy + Apache/Nginx

**Intérêt limité pour ce projet:**

- **HTTPS Termination:** Pas nécessaire pour tests locaux (pas de TLS requis)
- **Complexité ajoutée:** Deux composants à maintenir sans bénéfice immédiat
- **Cas d'usage:** Utile en production avec TLS, certificats, règles complexes

**Recommandation:** Pas nécessaire pour l'environnement de test HA. HAProxy seul suffit.

## Recommandation Finale

**HAProxy** est recommandé pour:

- Simplicité de configuration pour load balancing pur
- Performance optimale
- Health checks robustes pour détecter instances down
- Statistiques utiles pour monitoring
- Légèreté (moins de ressources)

**Alternative:** Apache HTTPD si l'expérience de l'équipe est un facteur important.

## Architecture Proposée

```javascript
┌─────────────────────────────────────────────────────────┐
│              Docker Compose HA Stack                     │
├─────────────────────────────────────────────────────────┤
│                                                          │
│  ┌──────────────┐                                       │
│  │  PostgreSQL  │                                       │
│  │   (shared)   │                                       │
│  └──────┬───────┘                                       │
│         │                                                │
│         ├──► ┌──────────────┐  ┌──────────────┐        │
│         │    │ Admin API #1 │  │ Admin API #2 │        │
│         │    │   (9081)     │  │   (9082)     │        │
│         │    └──────┬───────┘  └──────┬───────┘        │
│         │           │                 │                 │
│         │           └────────┬────────┘                 │
│         │                    │                          │
│         │              ┌─────▼──────┐                  │
│         │              │  HAProxy   │                  │
│         │              │  Admin LB  │                  │
│         │              │   (9080)   │                  │
│         │              └────────────┘                  │
│         │                                                │
│         ├──► ┌──────────────┐  ┌──────────────┐        │
│         │    │  Auth API #1 │  │  Auth API #2 │        │
│         │    │   (8081)     │  │   (8082)     │        │
│         │    └──────┬───────┘  └──────┬───────┘        │
│         │           │                 │                 │
│         │           └────────┬────────┘                 │
│         │                    │                          │
│         │              ┌─────▼──────┐                  │
│         │              │  HAProxy   │                  │
│         │              │  Auth LB   │                  │
│         │              │   (8080)   │                  │
│         │              └────────────┘                  │
│         │                                                │
│         └──► Migration (one-time)                       │
│                                                          │
└─────────────────────────────────────────────────────────┘
```



## Implémentation

### Phase 1: Configuration Docker Compose HA

**Fichier:** `docker/docker-compose.ha.yml`**Services à créer:**

1. **PostgreSQL** (identique à l'existant)
2. **Migration** (identique à l'existant)
3. **Admin API Instance 1** (port 9081 interne, pas d'exposition directe)
4. **Admin API Instance 2** (port 9082 interne, pas d'exposition directe)
5. **Auth API Instance 1** (port 8081 interne, pas d'exposition directe)
6. **Auth API Instance 2** (port 8082 interne, pas d'exposition directe)
7. **HAProxy Admin** (port 9080 exposé, load balance vers admin-api-1 et admin-api-2)
8. **HAProxy Auth** (port 8080 exposé, load balance vers auth-api-1 et auth-api-2)

**Points clés:**

- Chaque instance API partage le même volume `encryption-secrets` (clés partagées)
- Chaque instance utilise la même base PostgreSQL (ShedLock table partagée)
- Health checks sur chaque instance pour HAProxy
- Configuration HAProxy avec round-robin et health checks

### Phase 2: Configuration HAProxy

**Fichiers de configuration:**

- `docker/haproxy/haproxy-admin.cfg` - Configuration pour Admin API
- `docker/haproxy/haproxy-auth.cfg` - Configuration pour Auth API

**Fonctionnalités:**

- Backend avec 2 serveurs (admin-api-1:9081, admin-api-2:9082)
- Health checks HTTP sur `/actuator/health`
- Mode round-robin pour distribution équitable
- Statistiques sur port dédié (optionnel, pour monitoring)
- Logging configuré

**Exemple de configuration:**

```haproxy
global
    log stdout format raw local0

defaults
    mode http
    timeout connect 5s
    timeout client 30s
    timeout server 30s

frontend admin_frontend
    bind *:9080
    default_backend admin_backend

backend admin_backend
    balance roundrobin
    option httpchk GET /actuator/health
    http-check expect status 200
    server admin-api-1 admin-api-1:9081 check
    server admin-api-2 admin-api-2:9082 check
```



### Phase 3: Tests Fonctionnels ShedLock

**Fichier:** `ezkey-tests/src/test/java/org/ezkey/tests/security/scheduler/ShedLockDistributedTest.java`**Tests à implémenter:**

#### Test A: Exclusion Mutuelle (Essentiel)

- **Objectif:** Vérifier qu'un job schedulé ne s'exécute qu'une seule fois même avec 2 instances
- **Approche:**

1. Démarrer stack HA avec 2 instances admin-api
2. Attendre l'exécution d'un job (ex: `KEY_PROMOTION` toutes les 5 secondes)
3. Vérifier dans les logs qu'une seule instance exécute le job
4. Vérifier dans la table `shedlock` qu'un seul lock existe pour le job
5. Vérifier que le lock change d'instance entre exécutions (pas d'affinité)

#### Test B: Vérification Locks dans DB (Essentiel)

- **Objectif:** Valider que les locks sont correctement créés/mis à jour dans PostgreSQL
- **Approche:**

1. Exécuter un job schedulé
2. Interroger directement la table `shedlock` via JDBC
3. Vérifier les colonnes: `name`, `locked_by`, `locked_at`, `lock_until`
4. Vérifier que `lock_until > NOW()` pendant l'exécution
5. Vérifier que le lock expire après `lockAtMostFor`

#### Test C: Failover (Opportuniste si simple)

- **Objectif:** Tester qu'une instance peut prendre le relais si l'autre crash
- **Approche:**

1. Démarrer stack HA avec 2 instances
2. Attendre qu'une instance acquière un lock
3. Arrêter brutalement cette instance (`docker kill`)
4. Vérifier que l'autre instance acquiert le lock après expiration
5. Vérifier que les jobs continuent de s'exécuter

**Infrastructure de test:**

- Utiliser `DockerStackConfig` existant mais adapter pour HA (URLs via load balancer)
- Ajouter helper pour interroger PostgreSQL directement (via JDBC)
- Ajouter helper pour parser les logs des instances
- Utiliser `@TestContainers` ou connexion directe au stack Docker

### Phase 4: Documentation et Scripts

**Fichiers à créer/modifier:**

- `docker/README-HA.md` - Documentation de l'environnement HA
- `docker/start-ha.sh` / `docker/start-ha.bat` - Scripts de démarrage HA
- `docker/manage-ha.sh` / `docker/manage-ha.bat` - Scripts de gestion HA

**Documentation à inclure:**

- Instructions de démarrage
- Comment vérifier que les 2 instances sont actives
- Comment consulter les logs de chaque instance
- Comment exécuter les tests fonctionnels
- Comment monitorer les locks ShedLock dans la DB

## Structure des Fichiers

```javascript
docker/
├── docker-compose.ha.yml          # Nouveau: Stack HA
├── haproxy/
│   ├── haproxy-admin.cfg          # Nouveau: Config HAProxy Admin API
│   └── haproxy-auth.cfg           # Nouveau: Config HAProxy Auth API
├── README-HA.md                   # Nouveau: Documentation HA
├── start-ha.sh                    # Nouveau: Script démarrage HA
├── start-ha.bat                   # Nouveau: Script démarrage HA Windows
└── manage-ha.sh                   # Nouveau: Script gestion HA

ezkey-tests/
└── src/test/java/org/ezkey/tests/security/scheduler/
    ├── ShedLockDistributedTest.java           # Nouveau: Tests ShedLock
    └── ShedLockTestHelper.java                # Nouveau: Helpers pour tests
```



## Points d'Attention

1. **Ports:** S'assurer qu'aucun conflit avec le stack standard (9080, 8080 utilisés par load balancers)
2. **Volumes:** Partager `encryption-secrets` entre instances (même clés)
3. **Health Checks:** Configurer correctement pour que HAProxy détecte les instances down
4. **Logs:** Identifier clairement quelle instance génère quel log (ajouter instance ID dans logs)
5. **Base de données:** S'assurer que les migrations incluent la table `shedlock` (V26)

## Validation

**Critères de succès:**

1. Stack HA démarre avec 2 instances de chaque API
2. HAProxy distribue le trafic entre les instances
3. Tests A et B passent (exclusion mutuelle + vérification locks)
4. Test C passe si implémenté (failover)
5. Documentation complète et scripts fonctionnels

## Prochaines Étapes (Futures)

- Tests de charge avec plusieurs instances