## Plan: Audit de sécurité — Protocole Pending/Respond d'EZKey

### TL;DR

Audit exhaustif du protocole d'authentification mobile MFA d'EZKey (endpoints `/pending` et `/respond`). L'architecture cryptographique ECDSA P-256 est solide dans ses fondements, mais 17 vecteurs d'attaque ont été identifiés, classés par criticité et probabilité. Les remediations proposées capitalisent sur l'outillage existant (HMAC, Tink encryption, Bucket4j) sans dépendances externes nouvelles. Les principaux risques sont : le brute-force du challenge numérique via l'absence de rate limiting sur `/respond`, l'extraction d'IP spoofable dans le rate limiter, le contexte transactionnel non lié cryptographiquement au proof token, et les fuites de données sensibles dans les logs.

---

### Vecteurs d'attaque identifiés

#### CRITIQUE — Probabilité haute, impact direct sur l'intégrité du protocole

**V1. Brute-force du challenge numérique via `/respond` non rate-limité**

- **Méthodologie** : L'attaquant intercepte un `authAttemptId` (entier séquentiel prédictible) et un `authAttemptProofToken` lors d'un MITM ou compromise partielle du device. Le endpoint `/respond` n'a **aucun rate limiting** ([RateLimitFilter.java](ezkey-auth-api/src/main/java/org/ezkey/auth/config/RateLimitFilter.java) — seuls `/pending`, `/verify`, `/bind` sont couverts). Le challenge par défaut est **2 chiffres = 90 valeurs possibles** ([AuthAttemptService.java](ezkey-core/src/main/java/org/ezkey/authattempt/service/AuthAttemptService.java)). Un script peut épuiser l'espace en <2 secondes.
- **Prérequis** : Possession de la clé privée device (ou compromission du device). Sans signature ECDSA valide, la tentative échoue. **Probabilité réduite** par le prérequis, mais **impact maximal** si le device est compromis.
- **Remediation** : (a) Ajouter `/respond` au `RateLimitFilter` avec stratégie `auth-attempt-id`, max 3 tentatives/5min. (b) Augmenter le challenge minimum à 4 chiffres (9000 valeurs) ou 6 chiffres (900000 valeurs). (c) Invalider le `AuthAttempt` après N échecs de challenge (actuellement marqué INVALID mais sans compteur d'essais).
- **Vérification** : Test d'intégration envoyant >3 réponses avec mauvais challenge et vérifiant le 429 + le passage en INVALID.

**V2. Extraction d'IP spoofable — contournement du rate limiting**

- **Méthodologie** : Le [RateLimitFilter](ezkey-auth-api/src/main/java/org/ezkey/auth/config/RateLimitFilter.java) fait confiance aveuglément aux headers `X-Forwarded-For`, `X-Real-IP`, `CF-Connecting-IP` sans liste de proxies de confiance. Un attaquant envoie `X-Forwarded-For: <IP aléatoire>` à chaque requête pour obtenir un bucket neuf et contourner toute limite.
- **Probabilité** : **Haute** — trivial à exploiter, aucune connaissance spéciale requise.
- **Remediation** : (a) Implémenter une liste configurable de trusted proxies (seul `remoteAddr` est utilisé si la requête ne vient pas d'un proxy de confiance). (b) En option, utiliser Spring's `ForwardedHeaderFilter` avec `server.forward-headers-strategy=NATIVE` et laisser le conteneur/reverse proxy gérer.
- **Vérification** : Test envoyant des requêtes avec `X-Forwarded-For` variable et vérifiant que le rate limiting s'applique toujours sur la vraie IP source.

**V3. Extraction `enrollment-id` cassée dans le rate limiter**

- **Méthodologie** : La stratégie `enrollment-id` pour `/pending` utilise `extractEnrollmentIdFromPath()` qui extrait le **dernier segment de l'URL** ([RateLimitFilter.java](ezkey-auth-api/src/main/java/org/ezkey/auth/config/RateLimitFilter.java)). Pour `/api/v1/auth-attempts/pending`, cela retourne `"pending"` — pas l'enrollment ID. **Tous les appels `/pending` partagent le même bucket**, ce qui signifie : (a) un attaquant peut épuiser le budget de tous les enrollments d'un coup, ou (b) le rate limit est absurdement laxiste car il représente toutes les requêtes combinées.
- **Probabilité** : **Certaine** — c'est un bug, pas un risque théorique.
- **Remediation** : Extraire `enrollmentId` du body JSON de la requête POST (nécessite un `ContentCachingRequestWrapper` pour lire le body dans le filter sans le consommer). Ou passer à une stratégie `client-ip` pour `/pending` en attendant.
- **Vérification** : Test unitaire du `RateLimitFilter` validant que l'enrollment ID est correctement extrait du body.

#### ÉLEVÉ — Probabilité moyenne, impact significatif

**V4. Contexte transactionnel (`contextTitle`/`contextMessage`) non lié cryptographiquement**

- **Méthodologie** : Le contexte (ex: "Transfert de 5000€ vers compte X") est transmis dans la réponse `/pending` **à côté** du `authAttemptProofToken` signé, mais le contexte lui-même **n'est pas inclus dans les données signées** par l'intégration. Un MITM entre le serveur et le device pourrait modifier `contextTitle`/`contextMessage` sans invalider la signature. L'utilisateur approuverait une transaction qu'il croit être "Transfert de 50€" alors que le backend a envoyé "Transfert de 5000€".
- **Probabilité** : **Moyenne** — nécessite un MITM actif (atténué par TLS en production).
- **Remediation** : Inclure `contextTitle` et `contextMessage` dans le payload signé : `signature = ECDSA(authAttemptProofToken + "|" + contextTitle + "|" + contextMessage, integrationPrivateKey)`. Le device vérifie la signature sur le tuple complet. Ceci est **critique pour la conformité PSD2/SCA** mentionnée dans [CONTEXTUAL_AUTH.md](docs/CONTEXTUAL_AUTH.md).
- **Vérification** : Test modifiant le `contextMessage` dans la réponse interceptée et vérifiant que le device rejette la signature.

**V5. Race condition sur `/respond` — absence de `FOR UPDATE` lock**

- **Méthodologie** : Le [AuthAttemptRespondService](ezkey-core/src/main/java/org/ezkey/authattempt/service/AuthAttemptRespondService.java) utilise `findById()` sans verrou de ligne. Deux requêtes concurrentes sur le même `authAttemptId` pourraient passer simultanément la validation de status `READ`, et la dernière écriture gagnerait. Un attaquant pourrait rejeter un auth attempt que l'utilisateur légitime a approuvé.
- **Probabilité** : **Faible** — fenêtre de course très étroite, nécessite timing précis.
- **Remediation** : Utiliser `SELECT ... FOR UPDATE` dans le repository pour le `/respond`, similaire à `findAndLockMostRecentValidByEnrollmentIdAndStatus` déjà utilisé pour `/pending`.
- **Vérification** : Test de concurrence avec 2 threads répondant simultanément — vérifier qu'un seul réussit.

**V6. Code challenge loggé en clair dans les logs**

- **Méthodologie** : À [AuthAttemptRespondService.java](ezkey-core/src/main/java/org/ezkey/authattempt/service/AuthAttemptRespondService.java), le log `warn` inclut `expected: {}, got: {}` avec le challenge attendu en clair. Une compromission des logs (ELK, CloudWatch, fichier) expose le challenge.
- **Probabilité** : **Moyenne** — les logs sont une surface d'attaque fréquente.
- **Remediation** : Remplacer par `logger.warn("Challenge validation failed for authAttemptId: {}", authAttemptId)` sans les valeurs. Pour le debugging, les valeurs peuvent être loggées en `TRACE` uniquement.
- **Vérification** : Revue de code + grep sur `getAuthAttemptChallenge` dans tous les fichiers de log.

**V7. Fuite du status d'enrollment dans les messages d'exception**

- **Méthodologie** : [AuthAttemptService.java](ezkey-core/src/main/java/org/ezkey/authattempt/service/AuthAttemptService.java) — `"Enrollment is not in VERIFIED status. Current status: " + enrollment.getStatus()`. Si ces exceptions remontent via `@ControllerAdvice` jusqu'à la réponse HTTP, un attaquant peut énumérer les états d'enrollment.
- **Probabilité** : **Moyenne** — dépend de la configuration du `@ControllerAdvice`.
- **Remediation** : Messages d'exception génériques côté client : `"Authentication request failed"`. Le détail reste dans les logs serveur.
- **Vérification** : Test d'intégration envoyant une requête avec enrollment inactif et vérifiant que la réponse HTTP ne contient pas le status.

**V8. Absence de Bean Validation sur les DTOs de requête**

- **Méthodologie** : [AuthAttemptPendingRequestDto](ezkey-auth-api/src/main/java/org/ezkey/authattempt/dto/) et [AuthAttemptRespondRequestDto](ezkey-auth-api/src/main/java/org/ezkey/authattempt/dto/) utilisent `@Schema(requiredMode = REQUIRED)` qui n'est que de la documentation OpenAPI — **aucune annotation `@NotNull`, `@NotBlank`, `@Size`**. Le `@Valid` du controller ne valide rien. Un payload complètement null traverse jusqu'au service et cause des `NullPointerException`.
- **Probabilité** : **Haute** — trivial à exploiter, peut révéler des stack traces.
- **Remediation** : Ajouter `@NotNull`, `@NotBlank`, `@Size(max=...)` sur tous les champs des DTOs. Ajouter `@Min(1)` sur `authAttemptId`. Limiter `enrollmentProofToken`, `deviceProofToken`, `deviceProofTokenSigned` à `@Size(max=500)`.
- **Vérification** : Tests unitaires envoyant null/vide/trop grand et vérifiant 400 Bad Request avec erreur standardisée.

#### MODÉRÉ — Probabilité faible-moyenne, impact limité

**V9. IDs séquentiels auto-incrémentés pour `authAttemptId`**

- **Méthodologie** : [AuthAttempt.java](ezkey-core/src/main/java/org/ezkey/authattempt/domain/entity/AuthAttempt.java) utilise `GenerationType.IDENTITY` — entiers séquentiels. Un attaquant peut estimer le volume de trafic et tenter des réponses sur des IDs adjacents.
- **Impact** : Limité car la signature ECDSA du device est requise pour `/respond`. Sans la clé privée device, l'ID seul est inutile.
- **Remediation optionnelle** : Utiliser des UUID v7 (ordonnés chronologiquement mais non prédictibles) pour les identifiants d'auth attempt exposés dans l'API, tout en gardant l'entier comme PK interne.
- **Vérification** : Vérifier que l'API retourne des UUID au lieu d'entiers.

**V10. Fallback silencieux en cas d'échec de déchiffrement**

- **Méthodologie** : [AuthAttempt.java](ezkey-core/src/main/java/org/ezkey/authattempt/domain/entity/AuthAttempt.java) — `getAuthAttemptProofToken()` retourne le blob chiffré si le déchiffrement échoue. Ce blob serait alors utilisé dans des opérations de signature, produisant des résultats cryptographiquement invalides mais sans lever d'erreur.
- **Probabilité** : **Faible** — se produit uniquement en cas de corruption de clé ou rotation incomplète.
- **Remediation** : Lever une `EncryptionFailureException` au lieu de retourner le blob chiffré. Fail-fast plutôt que fail-silent.
- **Vérification** : Test unitaire simulant un échec de déchiffrement et vérifiant l'exception.

**V11. Accès au service de chiffrement par réflexion**

- **Méthodologie** : [AuthAttempt.java](ezkey-core/src/main/java/org/ezkey/authattempt/domain/entity/AuthAttempt.java) utilise `Class.forName().getDeclaredField().setAccessible(true)` pour accéder au `EncryptionService`. Si le classloader est modifié ou le champ renommé, la réflexion échoue silencieusement et retourne `null`, désactivant le chiffrement.
- **Probabilité** : **Faible** — risque d'ingénierie seulement.
- **Remediation** : Utiliser un pattern `ApplicationContextAware` ou un `BeanFactoryPostProcessor` pour injecter l'encryption service sans réflexion. Spring offre `@Configurable` ou `EntityListeners` avec injection.

**V12. Proof token timestamp en clair**

- **Méthodologie** : [SignatureService.java](ezkey-core/src/main/java/org/ezkey/signature/SignatureService.java) — le proof token est `randomPart.timestamp.saltPart`. Le timestamp révèle l'heure serveur exacte, utile pour des attaques de timing ou la reconnaissance.
- **Remediation** : Chiffrer ou hasher le timestamp dans le token, ou l'omettre (la protection anti-replay repose sur le hash unique, pas sur le timestamp).

**V13. TOCTOU entre validation d'enrollment et claim de l'auth attempt**

- **Méthodologie** : Dans [AuthAttemptPendingService](ezkey-core/src/main/java/org/ezkey/authattempt/service/AuthAttemptPendingService.java), l'enrollment est validé (étape 1-2) puis l'auth attempt est verrouillé et claimé (étape 3). Entre ces deux étapes, l'enrollment pourrait être désactivé par un admin.
- **Probabilité** : **Très faible** — fenêtre de millisecondes.
- **Remediation** : Re-valider le statut de l'enrollment dans la même transaction que le claim, ou utiliser un JOIN avec vérification dans la requête de lock.

#### FAIBLE — Mesures défensives recommandées

**V14. HMAC audit vérifié avec `.equals()` non constant-time**

- Dans [AuditHmacService.java](ezkey-core/src/main/java/org/ezkey/audit/integrity/AuditHmacService.java), la comparaison HMAC utilise `String.equals()` au lieu de `MessageDigest.isEqual()`. Risque de timing side-channel théorique.
- **Remediation** : Remplacer par `MessageDigest.isEqual(computed.getBytes(), stored.getBytes())`.

**V15. `DataIntegrityViolationException` non gérée pour le replay de device proof token**

- Dans [AuthAttemptPendingService](ezkey-core/src/main/java/org/ezkey/authattempt/service/AuthAttemptPendingService.java), le check `existsByDeviceProofTokenHash()` est suivi d'un `save()`. Si deux requêtes concurrentes passent le check, la contrainte UNIQUE de la BD lève une exception non catchée proprement.
- **Remediation** : Catcher `DataIntegrityViolationException` et la transformer en erreur applicative générique.

**V16. DNS lookup via `InetAddress.getByName()` sur header non fiable**

- Le rate limiter valide les IPv6 via `InetAddress.getByName()` qui déclenche un DNS lookup si l'input n'est pas une adresse IP. Un attaquant peut forcer des résolutions DNS via les headers HTTP.
- **Remediation** : Utiliser un regex de validation d'IP au lieu de `InetAddress.getByName()`.

**V17. Swagger UI/OpenAPI exposé en production**

- `anyRequest().permitAll()` expose `/api-docs` et `/swagger-ui.html`. Un attaquant obtient la documentation complète de l'API.
- **Remediation** : Restreindre ces endpoints en production via profil Spring.

---

### Steps

1. **[CRITIQUE] Ajouter rate limiting sur `/respond`** — Modifier [RateLimitFilter.java](ezkey-auth-api/src/main/java/org/ezkey/auth/config/RateLimitFilter.java) pour inclure le endpoint respond avec stratégie `auth-attempt-id` (extraire du body JSON), max 3 tentatives/5 min. Ajouter un compteur d'échecs sur `AuthAttempt` et invalider après N échecs.
2. **[CRITIQUE] Corriger l'extraction d'enrollment-id dans le rate limiter** — Refactorer `extractEnrollmentIdFromPath()` pour extraire l'ID du body JSON via `ContentCachingRequestWrapper`, ou basculer sur `client-ip` pour `/pending` en attendant.
3. **[CRITIQUE] Implémenter une liste de trusted proxies** — Ajouter une propriété `ezkey.rate-limit.trusted-proxies` (liste de CIDR). Ne faire confiance aux headers `X-Forwarded-For` que si `remoteAddr` est dans la liste.
4. **[ÉLEVÉ] Lier cryptographiquement le contexte au proof token** — Modifier `SignatureService.generateSignature()` pour inclure `contextTitle + "|" + contextMessage` dans le payload signé. Modifier le device pour vérifier la signature sur le tuple complet. C'est l'amélioration la plus structurante pour la crédibilité sécuritaire du projet.
5. **[ÉLEVÉ] Ajouter Bean Validation sur les DTOs** — Annoter `AuthAttemptPendingRequestDto` et `AuthAttemptRespondRequestDto` avec `@NotNull`, `@NotBlank`, `@Size`. Assurer que le `@ControllerAdvice` transforme les `MethodArgumentNotValidException` en réponses 400 standardisées.
6. **[ÉLEVÉ] Ajouter `FOR UPDATE` sur le respond** — Créer une méthode repository `findAndLockById()` avec `@Lock(PESSIMISTIC_WRITE)` et l'utiliser dans `AuthAttemptRespondService.validateAndGetAttempt()`.
7. **[ÉLEVÉ] Supprimer les valeurs de challenge des logs** — Modifier le `logger.warn` dans [AuthAttemptRespondService](ezkey-core/src/main/java/org/ezkey/authattempt/service/AuthAttemptRespondService.java) pour ne pas inclure `expected` / `got`. Auditer tous les usages de `getAuthAttemptChallenge()` et `toString()` sur `AuthAttempt`.
8. **[ÉLEVÉ] Génériciser les messages d'exception** — Remplacer les messages détaillés dans [AuthAttemptService](ezkey-core/src/main/java/org/ezkey/authattempt/service/AuthAttemptService.java) par des messages génériques. Garder le détail en `logger.debug()`.
9. **[MODÉRÉ] Augmenter le challenge minimum** — Passer la configuration par défaut de 2 → 4 chiffres minimum (ou 6 pour haute sécurité). Rendre configurable par intégration.
10. **[MODÉRÉ] Fail-fast sur échec de déchiffrement** — Modifier les getters décryptés dans [AuthAttempt.java](ezkey-core/src/main/java/org/ezkey/authattempt/domain/entity/AuthAttempt.java) pour lever `EncryptionFailureException` au lieu de retourner le blob chiffré.
11. **[MODÉRÉ] Comparaison HMAC constant-time** — Remplacer `.equals()` par `MessageDigest.isEqual()` dans [AuditHmacService.java](ezkey-core/src/main/java/org/ezkey/audit/integrity/AuditHmacService.java).
12. **[MODÉRÉ] Gérer `DataIntegrityViolationException` pour l'anti-replay** — Catcher dans [AuthAttemptPendingService](ezkey-core/src/main/java/org/ezkey/authattempt/service/AuthAttemptPendingService.java) et retourner une erreur applicative.
13. **[FAIBLE] Remplacer `InetAddress.getByName()` par regex IP** — Dans le rate limiter, valider les adresses IPv6 via pattern regex sans DNS lookup.
14. **[FAIBLE] Restreindre Swagger en production** — Conditionner l'accès Swagger/OpenAPI sur le profil Spring actif.
15. **[FAIBLE] Supprimer le timestamp du proof token** — Ou le chiffrer. La protection anti-replay repose déjà sur le hash unique.

---

### Verification

- **Tests de sécurité automatisés** : Créer une suite de tests dans `ezkey-tests/src/test/java/org/ezkey/tests/security/` couvrant :
  - Brute-force challenge (vérifier rate limiting + invalidation après N échecs)
  - IP spoofing (vérifier que les headers forgés ne contournent pas le rate limit)
  - Concurrent respond race condition (2 threads sur même auth attempt)
  - Null/empty payload validation (vérifier 400, pas 500)
  - Challenge non loggé en clair (grep sur les logs de test)
  - Contexte modifié → signature invalide (après implémentation V4)
- **Tests manuels via Postman** : Collection existante dans [postman/](postman/) — ajouter des scénarios d'attaque.
- **Build verification** : `mvn clean verify` — tous les tests passent après chaque changement.
- **Commande de validation** : `mvn checkstyle:check` — conformité style.

---

### Decisions

- **Challenge binding cryptographique du contexte (V4)** : C'est la recommandation la plus impactante pour la crédibilité du projet. Un système MFA dont le contexte affiché n'est pas lié cryptographiquement à l'approbation ne peut pas revendiquer la conformité PSD2/SCA.
- **UUID v7 vs entiers séquentiels (V9)** : Recommandé mais non bloquant — la signature ECDSA protège déjà contre l'exploitation des IDs prédictibles. À traiter en phase d'optimisation.
- **Pas de nouvelles dépendances** : Toutes les remédiations utilisent l'outillage existant (Bucket4j, BouncyCastle, Spring Validation, HMAC-SHA256 déjà en place).

