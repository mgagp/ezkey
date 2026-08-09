## Plan: Script Dockerisé de tests fonctionnels

Objectif: ajouter un script dédié pour exécuter les tests fonctionnels dans Docker, en reprenant le pattern BuildKit/cache de [scripts/build-docker.sh](scripts/build-docker.sh), avec un scope optionnel.
Comportement attendu:
1. Sans paramètre: scope implicite ALL_TEST, mappé vers `mvn test -pl ezkey-tests -P all-tests`.
2. Avec paramètre `fast_tests`: mappé vers `mvn test -pl ezkey-tests`.

**Étapes**
1. Phase 1, contrat CLI et mapping des scopes.
2. Définir l’interface: `./scripts/test-docker.sh [scope] [options]`.
3. Normaliser le scope en insensible à la casse, avec alias tolérants (`ALL_TEST`, `all-tests`, `fast_tests`, `fast-tests`).
4. Poser le mapping v1 minimal:
5. ALL_TEST -> Maven avec profil all-tests.
6. FAST_TESTS -> Maven sans profil additionnel (profil par défaut).
7. Gérer les scopes invalides avec message explicite, liste des scopes, et code de sortie non nul.
8. Phase 2, implémentation du script Docker (dépend de la phase 1).
9. Créer [scripts/test-docker.sh](scripts/test-docker.sh) en reprenant la structure de [scripts/build-docker.sh](scripts/build-docker.sh): robustesse shell, logs clairs, BuildKit activé.
10. Exécuter Maven dans un conteneur `maven:3.9-eclipse-temurin-25`, avec montage du repo et cache Maven persistant via volume Docker.
11. Ajouter options techniques alignées: `--help` et `--no-cache`.
12. Afficher explicitement la précondition: stack clean-start déjà démarré (hypothèse assumée, sans vérification active).
13. Phase 3, wrapper Windows et doc (docs en parallèle, finalisation après phase 2).
14. Ajouter [scripts/test-docker-local.cmd](scripts/test-docker-local.cmd) sur le modèle de [scripts/build-local.cmd](scripts/build-local.cmd).
15. Documenter l’usage dans [README.md](README.md) (default ALL_TEST, fast_tests, exemples).
16. Phase 4, validation.
17. Vérifier parsing CLI: sans paramètre, `fast_tests`, casse mixte, scope invalide.
18. Vérifier exécution Docker: commande Maven correcte, cache, propagation du code de sortie.
19. Vérifier cohérence script/doc et aide utilisateur.

**Fichiers de référence**
1. [scripts/build-docker.sh](scripts/build-docker.sh): pattern BuildKit, cache, options.
2. [scripts/build-local.cmd](scripts/build-local.cmd): pattern wrapper Windows.
3. [ezkey-tests/pom.xml](ezkey-tests/pom.xml#L137): profils de tests disponibles.
4. [pom.xml](pom.xml#L439): profils globaux et cohérence.
5. [README.md](README.md): documentation d’usage.
6. [scripts/test-docker.sh](scripts/test-docker.sh): nouveau script.
7. [scripts/test-docker-local.cmd](scripts/test-docker-local.cmd): nouveau wrapper.

**Décisions de scope**
1. Inclus: exécution Dockerisée des tests fonctionnels `ezkey-tests` avec scope métier.
2. Exclu: gestion start/stop/health de la stack Docker (hors demande).
3. Exclu en v1: ajout de multiples scopes métier au-delà de ALL_TEST et fast_tests, mais design prévu pour extension simple.

**Further Considerations**
1. Option d’évolution rapide: ajouter dès v1 un tableau de mapping extensible pour `smoke_tests` et `slow_tests` sans changer la CLI publique.
2. Option ergonomie: ajouter un mode `--dry-run` qui affiche la commande Maven finale sans l’exécuter.
3. Option CI locale: permettre la surcharge d’image Maven via variable d’environnement (`MAVEN_IMAGE`) pour flexibilité infrastructure.