# Plan: Scission `ezkey-core` → `ezkey-core` + `ezkey-core-security`

**TL;DR** — Extraire le sous-système de chiffrement lourd (Tink, re-encryption, ShedLock) de `ezkey-core`
vers un nouveau module `ezkey-core-security`. Bénéfice direct : `ezkey-auth-api` et
`ezkey-integration-api` n'embarquent plus Tink ni ShedLock dans leur classpath, ce qui allège leur
surface pour une future native compilation. Un seul vrai défi technique à résoudre :
`EncryptionEntityListener`.

---

## Phase A — Résolution du couplage `EncryptionEntityListener` *(débloquant, à faire en premier)*

`EncryptionEntityListener` crée une dépendance circulaire qui interdit le split direct :
- Les entités `AuthAttempt` et `Enrollment` (dans `ezkey-core`) ont `@EntityListeners(EncryptionEntityListener.class)`
- `EncryptionEntityListener` importe ces mêmes entités via `instanceof`, ET dépend de `EncryptionService`
- Si `EncryptionService` déménage dans `ezkey-core-security`, et `ezkey-core-security` dépend de `ezkey-core` pour voir les entités → **cycle**

**Résolution** : introduire l'interface `EncryptionOperations` dans `ezkey-core`

1. Créer `org.ezkey.security.EncryptionOperations` (interface) dans `ezkey-core` avec la signature
   minimale utilisée par le listener (méthodes `encryptEnrollment`, `encryptAuthAttempt` ou un seul
   `encrypt(Object)`)
2. Modifier `EncryptionEntityListener` pour qu'il dépende de `EncryptionOperations` (interface) au
   lieu de `EncryptionService` (classe concrète) — le champ statique et le `getBeanProvider` utilisent
   l'interface
3. Retirer l'annotation `@DependsOn({"encryptionService", "tinkKeyManager"})` du listener — déjà
   inutile puisque le `@Autowired(required = false)` + `ObjectProvider` gèrent déjà l'aspect optionnel
4. `EncryptionService` dans `ezkey-core-security` implémente `EncryptionOperations` — aucun
   changement comportemental

**Résultat** : `ezkey-core` ne référence plus `EncryptionService` à la compilation → le cycle est brisé.

---

## Phase B — Création du module `ezkey-core-security`

5. Ajouter `<module>ezkey-core-security</module>` dans le `pom.xml` racine (*dépend de A*)
6. Créer `ezkey-core-security/pom.xml` : parent = racine Ezkey, dépendance sur `ezkey-core`
7. Déplacer dans `ezkey-core-security` le package `org.ezkey.security.*` **en entier** :
   - `EncryptionService` (maintenant implémente `EncryptionOperations`)
   - `TinkKeyManager`, `KeysetBlob`, `EncryptionKey`, `EncryptionKeyRepository`
   - `KeyRotationService`, `KeyUsageVerificationService`, `KeysetBlobRepository`
   - `EncryptionKeyMigrationScopeService`
   - `ReencryptionService`, `ReencryptionBatch*`, `ReencryptionBatchRepository`,
     `ReencryptionRecordCipher`, `ReencryptionRowPersistenceService`, `ReencryptionTargetQueryService`
   - `SensitiveDataHasher`
   - `PendingEncryptionKeyExistsException`
   - `config/ReencryptionExecutorConfiguration`
8. Déplacer les dépendances lourdes de `ezkey-core/pom.xml` vers `ezkey-core-security/pom.xml` :
   `tink`, `shedlock-spring`, `shedlock-provider-jdbc-template`, `micrometer-core`, `ipaddress`

---

## Phase C — Mise à jour des dépendances dans les modules consommateurs *(dépend de B)*

9. `ezkey-admin-api/pom.xml` : ajouter `ezkey-core-security` comme dépendance
10. `ezkey-crypto-api/pom.xml` : ajouter `ezkey-core-security` comme dépendance
11. `ezkey-auth-api/pom.xml` : **aucun changement** (ne consomme pas `security`)
12. `ezkey-integration-api/pom.xml` : **aucun changement** (ne consomme pas `security`)
13. Vérifier les imports dans `admin-api` et `crypto-api` ne référencent rien de manquant

---

## Fichiers clés

- `ezkey-core/src/main/java/org/ezkey/security/EncryptionEntityListener.java`
  → modifier pour utiliser l'interface, retirer `@DependsOn`
- `ezkey-core/src/main/java/org/ezkey/authattempt/domain/entity/AuthAttempt.java`
  → `@EntityListeners` pointe toujours `EncryptionEntityListener` (qui reste dans `core`) — **pas de changement**
- `ezkey-core/src/main/java/org/ezkey/enrollment/domain/entity/Enrollment.java`
  → idem
- `ezkey-core-security/pom.xml` — à créer
- `pom.xml` (racine) — ajouter le module
- `ezkey-admin-api/pom.xml` — ajouter dépendance `ezkey-core-security`
- `ezkey-crypto-api/pom.xml` — ajouter dépendance `ezkey-core-security`

---

## Vérification

1. `mvn spotless:apply` depuis la racine
2. `mvn checkstyle:check` — valider que le nouveau module respecte les conventions
3. `mvn clean install -DskipTests` — confirmer que tout compile sans erreur circulaire
4. `mvn test -pl ezkey-core,ezkey-core-security,ezkey-admin-api,ezkey-auth-api,ezkey-integration-api`
5. `mvn dependency:tree -pl ezkey-auth-api` — vérifier que `tink`, `shedlock`, `micrometer-core`
   n'apparaissent plus dans le classpath transitif

---

## Décisions

- Quarkus : hors scope, fermé
- `signature` (`Ed25519`, `ECDSA`) : reste dans `ezkey-core` — trop léger pour justifier un troisième module
- `TinkProperties` (dans `config/`) : déplacée dans `ezkey-core-security` car uniquement consommée
  par `TinkKeyManager`
- Le split ne touche ni l'API publique, ni les entités, ni les tests comportementaux existants

---

## Considération

La Phase A (interface `EncryptionOperations`) introduit une abstraction qui n'existait pas. C'est de
la complexité **essentielle** ici (elle débloque le split propre) — mais elle est petite : une
interface d'une ou deux méthodes. À documenter clairement dans le Javadoc pour que le « pourquoi »
soit évident.
