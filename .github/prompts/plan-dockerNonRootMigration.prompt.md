# Plan : Migration non-root des conteneurs Docker EZKey

**TL;DR** — Bonne nouvelle : le processus JVM (Spring Boot) tourne *déjà* en tant qu'utilisateur `spring` grâce au pattern `su-exec`. Le problème réel est que le conteneur *démarre* en root pour faire des `chown`/`chmod` sur les volumes montés, puis drop vers `spring`. L'objectif est d'éliminer cette phase root en préparant les répertoires et permissions au moment du *build* plutôt qu'au *runtime*, et d'ajouter `USER spring:spring` à tous les stages du Dockerfile.

---

## État actuel — ce qui existe déjà

- `migration` : déjà ✅ non-root (`USER spring:spring`)
- Services (`admin-api`, `auth-api`, `m2m-api`, `crypto-api`, `demo-device`, `demo-app-acme`) : root → drop vers `spring` via `su-exec`
- `bootstrap-init` : root permanent
- `cli-test` : root permanent (utilise `/root/.ezkey`)
- `postgres` : géré par l'image officielle (déjà non-root)

---

## Étapes

1. **Modifier le `docker/Dockerfile` — stages services Spring Boot**
   - Pour chaque stage (`admin-api`, `auth-api`, `m2m-api`, `crypto-api`, `demo-device`, `demo-app-acme`) : créer l'utilisateur `spring` (déjà fait au build pour `migration`, à dupliquer), pré-créer tous les répertoires nécessaires avec `mkdir -p` + `chown spring:spring` + `chmod` approprié depuis root *avant* de switcher
   - Ajouter `USER spring:spring` en fin de chaque stage, avant `ENTRYPOINT`
   - Supprimer l'installation de `su-exec` de ces stages (plus besoin)
   - Désinstaller `shadow` / `su-exec` des couches finales

2. **Réécrire les scripts `entrypoint.sh` embarqués** (générés inline via `RUN echo '...'` dans le Dockerfile)
   - Retirer tous les `chown`/`chmod` (ils seront faits au build)
   - Retirer le wrapper `exec su-exec spring:spring ...`
   - Remplacer par un simple `exec java -jar app.jar "$@"` avec les options JVM existantes
   - Garder les vérifications passives (wait-for-file, lecture de config)

3. **Modifier le stage `bootstrap-init`** (`docker/bootstrap-init/Dockerfile`)
   - Ajouter un utilisateur système `ezkey` (ou réutiliser `spring` comme convention)
   - Pré-créer `/bootstrap` et `/app/data/enrollments` avec le même UID que `demo-device` (`spring`, UID 100)
   - Remplacer `chmod 755` root par une création de répertoire owned dès le build
   - Ajouter `USER spring:spring`

4. **Modifier le stage `cli-test`** (`docker/cli-test/Dockerfile`)
   - Ajouter un utilisateur `ezkey` non-root
   - Migrer `/root/.ezkey` → `/home/ezkey/.ezkey` et `/work` avec le bon ownership
   - Mettre à jour les scripts de test CLI qui référencent `/root`
   - Ajouter `USER ezkey:ezkey`

5. **Valider le mécanisme de volumes** (le point le plus délicat)
   - Sur Linux, quand Docker monte un volume *nommé vide*, il copie le contenu de l'image dans le volume à la première création, **préservant le ownership**. Donc si `/etc/ezkey/secrets` est `chown spring:spring` dans l'image, un nouveau volume sera initialisé avec ce ownership. Ce comportement doit être documenté et vérifié.
   - Le script `docker/generate-encryption-keys.sh` fait déjà un pre-seeding du volume `encryption-secrets` avec UID 100/GID 101 — il reste pertinent et inchangé.

6. **Mettre à jour les docker-compose files**
   - Aucun `user:` explicite n'est nécessaire dans les compose si le Dockerfile a `USER`; toutefois ajouter `user: "100:101"` dans `docker/docker-compose.yml` comme pratique défensive explicite est optionnel mais recommandé pour les services critiques
   - Vérifier que les volumes `encryption-secrets`, `bootstrap-artifacts`, `demo-device-data`, `demo-app-acme-data`, `demo-app-acme-config` sont correctement initialisés

7. **Ajouter `--no-new-privileges` (optionnel — niveau hardening)**
   - Dans les compose files, ajouter `security_opt: ["no-new-privileges:true"]` pour empêcher toute escalade de privilège post-démarrage
   - Compatible avec l'approche non-root pure

8. **Tester le clean start sous Linux** (critère de validation final)
   - Vérifier que `docker compose up` depuis zéro sur Linux crée les volumes avec les bons droits
   - Tester la rotation de clés (`generate-encryption-keys.sh`) en mode non-root
   - Tester le flow bootstrap complet (enrollment, bind, verify)

---

## Vérification

- `docker compose up` complet depuis une image fraîche sur un système Linux (ou WSL2 en mode Linux strict)
- `docker inspect <container> | grep User` → doit retourner `100` ou `spring` pour tous les services
- `docker exec <container> id` → doit retourner `uid=100(spring) gid=101(spring)`
- Les logs ne doivent pas contenir d'erreur de permission sur `/etc/ezkey`
- Le flow 5-minutes EZKey doit fonctionner end-to-end

---

## Décisions

- **`su-exec` vs `USER`** : on passe à `USER` au build time — plus propre, plus simple, plus idiomatique. `su-exec` était un workaround pour un problème qui se résout mieux au build.
- **UID/GID fixes à 100/101** : on garde la convention existante (cohérente avec `generate-encryption-keys.sh`).
- **`bootstrap-init` et `cli-test` en root** : clairement dans le scope, même si moins critiques que les services exposés.
- **Scope hors-plan** : les images tierces (`postgres`, `haproxy`) gèrent leurs propres users — pas de changement nécessaire.

---

## Amplitude et risque

| Aspect | Évaluation |
|---|---|
| Ampleur | Modérée — 1 Dockerfile principal + 2 Dockerfiles secondaires + scripts entrypoint inline |
| Risque principal | Volumes Linux : comportement de copy-on-first-use à valider |
| Risque JVM | Faible — le process tournait déjà en `spring`, comportement identique |
| Risque `cli-test` | Faible-moyen — changement de chemin `/root/.ezkey` |
| Compatibilité Windows | Aucun impact — Docker Desktop abstrait les UIDs |
| Compatibilité AWS/Rocky Linux | Amélioration significative — plus d'exposition root |
