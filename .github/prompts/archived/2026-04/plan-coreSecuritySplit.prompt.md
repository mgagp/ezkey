# Plan: Scission `ezkey-core` → `ezkey-core` + `ezkey-core-security`

## Statut

Plan complété et archivé en avril 2026. Le split `ezkey-core` / `ezkey-core-security` a été
implémenté et validé, y compris la validation Docker Clean Start et les tests fonctionnels.

**TL;DR** — Le split reste pertinent, mais le plan initial sous-estimait le couplage actuel.
Le verrou n'est pas seulement `EncryptionEntityListener` : `Enrollment` et `AuthAttempt`
dépendent aussi directement de `EncryptionService`, et `ezkey-auth-api` / `ezkey-integration-api`
scannent aujourd'hui `org.ezkey.security` ainsi que ses repositories/entities. Le bon ordre est
donc : **1. stabiliser un contrat léger dans `ezkey-core`, 2. déplacer l'implémentation lourde,
3. seulement ensuite nettoyer les consumers**.

---

## Réévaluation

Le plan initial doit être corrigé sur quatre points :

- Le vrai couplage runtime n'est pas limité au listener : les getters de `Enrollment` et
   `AuthAttempt` font aussi du decrypt via `EncryptionService`
- Garder `EncryptionEntityListener` dans `ezkey-core` tout en « déplaçant tout
   `org.ezkey.security.*` » est contradictoire
- `ezkey-auth-api` et `ezkey-integration-api` ne sont pas encore indépendants du sous-système
   sécurité : ils scannent `org.ezkey.security`, `org.ezkey.security.domain.entity` et
   `org.ezkey.security.domain.repository`
- `SensitiveDataHasher` est consommé hors admin/crypto aujourd'hui seulement de façon limitée,
   mais les entités `EncryptionKey` / `ReencryptionBatch` et leurs repositories restent dans le
   périmètre scanné par auth/integration

Conséquence : le premier slice exécutable n'est pas la création du nouveau module, mais
**l'introduction d'un contrat de chiffrement stable côté `core`**.

---

## Phase A — Découpler `core` de `EncryptionService` *(débloquant, à faire en premier)*

Le split direct échoue aujourd'hui pour trois raisons :

- `EncryptionEntityListener` dépend de `EncryptionService`
- `Enrollment` et `AuthAttempt` dépendent aussi de `EncryptionService` pour le decrypt lazy
- les entités et le listener sont tous dans `ezkey-core`, alors que l'implémentation lourde devra
   vivre dans le nouveau module

**Résolution** : introduire un contrat minimal et un point d'accès runtime côté `core`

1. Créer `org.ezkey.security.EncryptionOperations` dans `ezkey-core` avec la surface réellement
    utilisée par `core` : `isEncryptionAvailable`, `isEncrypted`, `encrypt`, `decrypt`
2. Créer un holder/runtime access léger dans `ezkey-core` pour exposer l'implémentation courante
    sans réflexion sur une classe concrète
3. Modifier `EncryptionEntityListener` pour dépendre de `EncryptionOperations` au lieu de
    `EncryptionService`
4. Modifier `Enrollment` et `AuthAttempt` pour récupérer `EncryptionOperations` via ce holder,
    et non plus via réflexion sur le champ statique du listener
5. Retirer `@DependsOn({"encryptionService", "tinkKeyManager"})` du listener : le lookup optionnel
    suffit déjà
6. Faire implémenter `EncryptionService` par `EncryptionOperations`

**Résultat attendu** : `ezkey-core` ne dépend plus à la compilation que d'un contrat léger. Le
listener et les entités continuent de fonctionner sans connaître le module futur.

---

## Phase B — Créer `ezkey-core-security` sans casser `core`

7. Ajouter `<module>ezkey-core-security</module>` dans le `pom.xml` racine (*dépend de A*)
8. Créer `ezkey-core-security/pom.xml` : parent = racine Ezkey, dépendance sur `ezkey-core`
9. Déplacer l'implémentation lourde vers `ezkey-core-security` :
    - `EncryptionService` (implémente `EncryptionOperations`)
    - `TinkKeyManager`
    - services de rotation / re-encryption
    - configuration ShedLock/re-encryption
10. Déplacer les dépendances lourdes de `ezkey-core/pom.xml` vers `ezkey-core-security/pom.xml` :
      `tink`, `shedlock-spring`, `shedlock-provider-jdbc-template`, `micrometer-core`, `ipaddress`

**Point d'attention** : ne pas déplacer en même temps tout ce qui est sous `org.ezkey.security`
par simple critère de package. Il faut d'abord séparer ce qui relève :

- du contrat léger partagé par `core`
- de l'implémentation lourde
- du modèle/repository qui impose encore un scan JPA dans certains modules

---

## Phase C — Rebasculer les consumers module par module *(dépend de B)*

11. `ezkey-admin-api/pom.xml` : ajouter `ezkey-core-security` comme dépendance
12. `ezkey-crypto-api/pom.xml` : ajouter `ezkey-core-security` comme dépendance
13. `ezkey-auth-api` : garder temporairement l'accès aux entités/repositories sécurité tant qu'ils
      n'ont pas été relocalisés ou extraits proprement
14. `ezkey-integration-api` : même stratégie que `auth-api`
15. Mettre à jour `scanBasePackages`, `@EntityScan` et `@EnableJpaRepositories` **après** la
      relocalisation effective des beans / entités / repositories, pas avant

**Correction importante** : `auth-api` et `integration-api` ne peuvent pas être marqués “aucun
changement” à ce stade. Il faudra soit leur ajouter `ezkey-core-security`, soit sortir de leur
classpath les entités/repositories sécurité dans une phase dédiée.

---

## Phase D — Nettoyage structurel optionnel mais probable

16. Réévaluer le placement de `SensitiveDataHasher` :
      - le laisser dans `core` si on le considère comme utilitaire léger partagé
      - le déplacer seulement si tous ses consumers finaux suivent `core-security`
17. Réévaluer le placement des entités/repositories `EncryptionKey`, `KeysetBlob`,
      `ReencryptionBatch` : tant que `auth-api` et `integration-api` les scannent, ils empêchent un
      désengagement total du sous-système sécurité
18. Mettre à jour la documentation architecture/configuration si le split aboutit

---

## Fichiers clés

- `ezkey-core/src/main/java/org/ezkey/security/EncryptionEntityListener.java`
   → modifier pour utiliser `EncryptionOperations`, retirer `@DependsOn`
- `ezkey-core/src/main/java/org/ezkey/security/EncryptionOperations.java`
   → à créer dans `core`
- `ezkey-core/src/main/java/org/ezkey/security/EncryptionOperationsHolder.java`
   → à créer dans `core`
- `ezkey-core/src/main/java/org/ezkey/authattempt/domain/entity/AuthAttempt.java`
   → remplacer l'accès direct à `EncryptionService` par `EncryptionOperations`
- `ezkey-core/src/main/java/org/ezkey/enrollment/domain/entity/Enrollment.java`
   → idem
- `ezkey-core-security/pom.xml` — à créer
- `pom.xml` (racine) — ajouter le module
- `ezkey-admin-api/pom.xml` — ajouter dépendance `ezkey-core-security`
- `ezkey-crypto-api/pom.xml` — ajouter dépendance `ezkey-core-security`
- `ezkey-auth-api/src/main/java/org/ezkey/auth/AuthApplication.java`
   → sera à réviser quand le package sécurité sera réellement déplacé
- `ezkey-integration-api/src/main/java/org/ezkey/integration/api/IntegrationApiApplication.java`
   → idem

---

## Vérification

1. Slice A uniquement : `mvn -pl checkstyle-config,ezkey-core -am test -Dtest=EncryptionEntityListenerTest`
2. Slice A uniquement : `mvn -pl checkstyle-config,ezkey-core -am test-compile`
3. Après création du module : `mvn spotless:apply` depuis la racine
4. `mvn checkstyle:check`
5. `mvn clean install -DskipTests`
6. `mvn test -pl ezkey-core,ezkey-core-security,ezkey-admin-api,ezkey-auth-api,ezkey-integration-api`
7. `mvn dependency:tree -pl ezkey-auth-api`
8. `mvn dependency:tree -pl ezkey-integration-api`
9. Tests fonctionnels : `./ezkey-tests/clean-start.sh` puis la suite fonctionnelle pertinente
    pour confirmer que le chiffrement à la persistance et le decrypt lazy n'ont pas régressé

---

## Décisions

- Quarkus : hors scope, fermé
- `signature` (`Ed25519`, `ECDSA`) : reste dans `ezkey-core` — trop léger pour justifier un troisième module
- `TinkProperties` (dans `config/`) : à déplacer avec `TinkKeyManager` si aucun consumer léger ne
   subsiste dans `core`
- Le split ne doit pas changer l'API publique ni la sémantique des entités ; en revanche il touche
   leur mécanisme interne de decrypt/chiffrement lazy, donc les tests ne sont pas optionnels

---

## Considération

La Phase A introduit une petite abstraction (`EncryptionOperations` + un holder léger). C'est de la
complexité **essentielle** : elle remplace un couplage caché par réflexion vers `EncryptionService`
et rend le split testable sans big bang. Le bon critère de réussite n'est pas seulement “ça
compile”, mais “les entités et le listener continuent de chiffrer/déchiffrer sans connaître
l'implémentation concrète”.
