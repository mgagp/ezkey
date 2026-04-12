# Plan: Actualisation ezkey-pam micro-SDK

Le module PAM est un module C standalone (Rocky Linux, libcurl, cJSON) qui n'a pas suivi l'évolution des APIs sécurisés. L'objectif est de le rendre fonctionnel avec le M2M API (port 7080) via Basic Auth, en utilisant `userIdentifier` pour résoudre l'enrollment depuis le username Linux, et de compléter les fichiers manquants pour permettre un test E2E: SSH → PAM → M2M API → demo-device (approve) → SSH session OK.

## Steps

### 1. Actualiser le code C — `ezkey-pam/src/pam_ezkey.c`

- Remplacer l'appel `POST http://host.docker.internal:9080/api/v1/auth-attempts` par le M2M API: `POST http://{M2M_URL}/api/v1/auth-attempts`
- Ajouter le header `Authorization: Basic base64(integrationKey:secretKey)` via `curl_slist_append` — même pattern que le Java SDK dans `EzkeyClient.buildAuthorizationHeader()`
- Remplacer le body `{"enrollmentId": 1}` par `{"userIdentifier": "<username>"}` — le username Linux obtenu via `pam_get_user()` sert de `userIdentifier`
- Actualiser l'appel `GET .../wait` pour ajouter les query params `?timeout=30&polling=2` et le header Basic Auth
- Supprimer la fonction `ezkey_poc1_prooftoken()` et son appel (endpoint `sim/prooftoken` sur port 8085 ne sert plus)
- Supprimer les fonctions mock (`ezkey_authenticate_user()` avec `sleep()` et faux logs) — garder uniquement le flow réel
- Lire les credentials depuis des **variables d'environnement**: `EZKEY_M2M_API_URL`, `EZKEY_INTEGRATION_KEY`, `EZKEY_SECRET_KEY` via `getenv()` en C, avec fallback sur les constantes de `ezkey_config.h`

### 2. Actualiser les constantes — `ezkey-pam/src/ezkey_config.h`

- Renommer `EZKEY_ADMIN_API_URL` → `EZKEY_M2M_API_URL` avec default `http://localhost:7080`
- Supprimer `EZKEY_AUTH_API_URL` (pas utilisé par le PAM)
- Mettre à jour `EZKEY_VERSION` de `1.0.0-mock` à `1.1.0`
- Ajouter les constantes env var names: `EZKEY_ENV_M2M_URL`, `EZKEY_ENV_INTEGRATION_KEY`, `EZKEY_ENV_SECRET_KEY`

### 3. Créer les fichiers manquants référencés par le Dockerfile

- **`docker-entrypoint.sh`** — Script qui: démarre `rsyslog`, génère les clés SSH host si absentes, lance `sshd -D`. Simple, 10-15 lignes.
- **`install.sh`** — Script d'installation automatisé: `make all`, copie `pam_ezkey.so` vers `/lib64/security/`, copie `pam_ezkey.conf` vers `/etc/security/`, affiche les instructions PAM. ~20 lignes.
- **`test/test_pam.sh`** — Script de test basique: vérifie que `pam_ezkey.so` existe dans `/lib64/security/`, vérifie la config PAM dans `/etc/pam.d/sshd`, tente un `pamtester` (si disponible) ou vérifie la connectivité API avec `curl`. ~30 lignes.

### 4. Actualiser le Dockerfile — `ezkey-pam/Dockerfile`

- Ajouter les `ENV` pour les credentials M2M: `EZKEY_M2M_API_URL`, `EZKEY_INTEGRATION_KEY`, `EZKEY_SECRET_KEY` (avec valeurs par défaut vides, injectées par docker-compose)
- Remplacer `host.docker.internal` par les variables d'environnement dans le contexte Docker
- Consolider les `dnf install -y` en une seule commande (réduire les layers)
- S'assurer que les fichiers `docker-entrypoint.sh`, `install.sh`, `test/` existent avant le `COPY`

### 5. Actualiser le docker-compose PAM — `ezkey-pam/docker-compose.yml`

- Ajouter le réseau externe `ezkey-network` pour communiquer avec le stack principal (`m2m-api`, `auth-api`, etc.)
- Ajouter les variables d'environnement: `EZKEY_M2M_API_URL=http://m2m-api:7080`, `EZKEY_INTEGRATION_KEY`, `EZKEY_SECRET_KEY`
- Ajouter un `depends_on` conditionnel si lancé avec le stack, ou documenter l'ordre de démarrage
- Supprimer le mapping de port `2223:2222` inutile, garder `2222:22`
- Réseau doit être déclaré comme `external: true` (créé par le stack principal)

### 6. Actualiser le fichier PAM sshd — `ezkey-pam/sshd`

- Retirer les arguments mock (`mock_delay=3`) de la ligne `auth required pam_ezkey.so debug mock_delay=3`
- Garder `auth required pam_ezkey.so debug` seulement

### 7. Nettoyer la config — `ezkey-pam/config/pam_ezkey.conf`

- Simplifier: garder seulement les paramètres pertinents (`m2m_api_url`, `wait_timeout`, `debug`)
- Retirer les paramètres mock (`mock_success`, `mock_delay`)
- Mettre à jour les URLs (remplacer `admin_api_url` par `m2m_api_url`)
- Note: le parsing du fichier reste hors scope pour cette itération — les valeurs sont documentatives

### 8. Actualiser le README — `ezkey-pam/README.md`

- Documenter le scénario de test E2E: stack principal → bootstrap → PAM container → SSH → demo-device approve
- Mettre à jour les instructions pour les API keys et le M2M API
- Retirer toute référence au mode mock
- Documenter les variables d'environnement nécessaires

## Scénario de test E2E (procédure)

```
1. cd docker && .\manage.ps1 clean          # Clean start
2. cd docker && .\manage.ps1 start          # Démarre le stack complet (postgres, migration, admin, auth, m2m, demo-device, bootstrap-init)
3. Attendre que bootstrap-init termine       # Crée tenant, integration, enrollment, API keys
4. Récupérer les credentials API key         # Depuis bootstrap-credentials.json ou les logs
5. cd ezkey-pam
6. docker-compose up --build                 # Build et lance le conteneur PAM avec réseau ezkey-network
7. ssh -p 2222 testuser@localhost            # SSH déclenche le PAM → POST /api/v1/auth-attempts
8. Ouvrir http://localhost:8083/phone/ezkey  # Demo-device UI
9. Check pending → Approve                  # demo-device approuve l'auth attempt
10. La session SSH se complète               # PAM reçoit ACCEPTED du wait API
```

**Pré-requis pour le test**: Le bootstrap-init doit créer un enrollment avec un `userIdentifier` correspondant au username Linux `testuser`. Vérifier dans `docker/bootstrap-init/bootstrap-init.sh` si le champ `userIdentifier` est renseigné lors de la création de l'enrollment. Si non, il faudra soit ajouter une étape au bootstrap, soit créer l'enrollment manuellement via l'Admin API avec `userIdentifier=testuser`.

## Verification

- **Build**: `cd ezkey-pam && docker-compose build` — doit compiler `pam_ezkey.so` sans erreur
- **Connectivité API**: Depuis le conteneur PAM, `curl http://m2m-api:7080/actuator/health` doit répondre (réseau partagé)
- **Auth flow**: `ssh -p 2222 testuser@localhost` doit déclencher une auth attempt visible dans les logs du conteneur PAM (`/tmp/pam_ezkey.out`) et en attente dans le demo-device UI
- **Approve**: Approuver dans le demo-device → la session SSH se complète (pas d'erreur `PAM_AUTH_ERR`)
- **Reject**: Rejeter dans le demo-device → la session SSH est refusée
- **Logs**: `docker exec ezkey-pam-test cat /tmp/pam_ezkey.out` montre le flow complet

## Decisions

- **M2M API over Admin API**: Le M2M API est conçu pour le machine-to-machine, supporte API key only, a du rate limiting et des virtual threads — c'est la cible appropriée pour un module PAM
- **userIdentifier over enrollmentId**: Évite le hardcoding, le M2M API résout l'enrollment automatiquement depuis le username Linux
- **Variables d'environnement over config file parsing**: Scope minimal — pas de parsing de `pam_ezkey.conf` en C. Les credentials passent par env vars (injectées par docker-compose), avec fallback sur les constantes de `ezkey_config.h`
- **docker-compose séparé avec réseau partagé**: Maintient l'isolation du module tout en permettant la communication avec le stack via `ezkey-network` déclaré `external: true`
- **Suppression du mock**: Le code mock n'a plus de raison d'être puisque le stack Docker fournit un environnement réel complet
