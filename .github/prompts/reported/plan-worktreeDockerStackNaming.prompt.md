## Plan: Nommage Docker par worktree

Faire de [.mvn/maven.config](c:/github/ezkey-worktree2/.mvn/maven.config#L1) la source de vérité du suffixe Docker, dériver un nom Compose du type ezkey / ezkey-wt2 / ezkey-native-wt2 / ezkey-ha-wt2, puis l’injecter via -p dans les scripts Bash. Pour que le basculement entre worktrees marche vraiment dans Docker Desktop, retirer ou paramétrer les container_name fixes dans les Compose files.

**Étapes**
1. Ajouter un petit résolveur Bash partagé qui lit [.mvn/maven.config](c:/github/ezkey-worktree2/.mvn/maven.config#L1), extrait -DbuildQualifier=..., enlève le tiret initial, normalise en minuscules et construit le nom Compose final.
2. Brancher ce résolveur dans [ezkey-tests/clean-start.sh](c:/github/ezkey-worktree2/ezkey-tests/clean-start.sh#L1) pour tous les appels Compose down et pour le démarrage.
3. Brancher la même logique dans [docker/start.sh](c:/github/ezkey-worktree2/docker/start.sh#L1) pour build, up, exec, logs et les messages d’aide affichés en fin de script.
4. Mettre à jour [docker/generate-encryption-keys.sh](c:/github/ezkey-worktree2/docker/generate-encryption-keys.sh#L1) pour calculer les vrais noms de volumes à partir du project name Compose au lieu des valeurs fixes ezkey_encryption-secrets, ezkey-ha_..., etc.
5. Aligner aussi [docker/manage.sh](c:/github/ezkey-worktree2/docker/manage.sh#L1), sinon stop, logs et status viseront encore le stack par défaut.
6. Décider explicitement du traitement des container_name dans [docker/docker-compose.yml](c:/github/ezkey-worktree2/docker/docker-compose.yml#L2), [docker/docker-compose.native.yml](c:/github/ezkey-worktree2/docker/docker-compose.native.yml#L2), [docker/docker-compose.ha.yml](c:/github/ezkey-worktree2/docker/docker-compose.ha.yml#L2) et [docker/docker-compose.with-proxy.yml](c:/github/ezkey-worktree2/docker/docker-compose.with-proxy.yml#L1). Ma recommandation est de les retirer pour permettre la coexistence réelle des stacks.
7. Mettre à jour [docker/README.md](c:/github/ezkey-worktree2/docker/README.md#L193) et [docker/README.md](c:/github/ezkey-worktree2/docker/README.md#L500) pour documenter le comportement par défaut et le comportement worktree.

**Vérification**
1. Vérifier que le résolveur retourne ezkey sur le checkout principal et ezkey-wt2 sur le worktree courant.
2. Vérifier que docker compose -p ... config rend correctement les stacks standard, native et HA.
3. Lancer le clean-start Bash depuis le worktree et confirmer que Docker Desktop affiche le bon nom de projet.
4. Vérifier que les volumes bootstrap et encryption portent bien le bon préfixe de projet.
5. Si les container_name sont retirés, matérialiser deux worktrees distincts et confirmer qu’ils peuvent coexister à l’état stopped/running sans conflit de noms.

**Décisions**
- Source de vérité confirmée: [.mvn/maven.config](c:/github/ezkey-worktree2/.mvn/maven.config#L1).
- Premier passage confirmé: Bash d’abord.
- Mécanisme recommandé: option Compose -p plutôt que dépendre du name: YAML.
- UI Docker: à garder hors du premier passage, sauf besoin QA explicite.

Ma recommandation finale est donc: oui pour un stack name dérivé du buildQualifier, mais seulement si tu traites le sujet comme un problème d’identité Compose complet, pas comme un simple renommage visuel du stack. Le minimum viable robuste pour ton workflow solo est: résolveur partagé, injection via -p, alignement start/clean-start/manage/keygen, puis suppression des container_name fixes si tu veux vraiment basculer d’un worktree à l’autre dans Docker Desktop sans reconstruction.