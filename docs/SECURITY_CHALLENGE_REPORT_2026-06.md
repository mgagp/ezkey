# Ezkey — Security Challenge Report
**Cryptographic & Java Backend Audit — June 2026**

> **Posture:** Pen-test pragmatique, chapeau rouge appliqué.
> **Scope:** Backend Java (tous modules), contrats cryptographiques (signatures, proof tokens, séquençage), et protocole d'authentification.
> **Cadence:** Une passe structurée sur 10 domaines.
> **Livrable:** Constatations classées + backlog priorisé avec mitigations.

---

## Résumé exécutif

La fondation cryptographique d'Ezkey est solide. L'architecture backend-first est cohérente avec les principes du projet. Les algorithmes choisis (EC P-256 / ECDSA-SHA256 pour les devices, Ed25519 pour les intégrations) sont corrects, la normalisation low-S est appliquée, les proof tokens sont générés avec `SecureRandom`, et les bearer tokens admin utilisent SHA-256 pour les comparaisons en base de données.

**Cependant**, ce challenge révèle plusieurs zones à risque réel qui méritent une attention proactive. La majorité des findings sont de gravité MOYENNE à HAUTE — pas de vulnérabilité critique permettant un compromis total sans conditions externes, mais des lacunes qui fragilisent la posture de sécurité perçue et réelle d'un produit MFA.

### Statut d'implémentation (juillet 2026)

| ID | Statut | Référence |
|----|--------|-----------|
| SEC-001 | **Fait** | PR #255 — challenge obligatoire sur `/passwordless-wait` |
| SEC-002 | **Fait** | PR #293 — `encryption.required` + health Actuator Tink |
| SEC-003 | **Fait** | PR #266 — Caffeine bounded sur maps rate limit admin |
| SEC-004 | **Fait** | PR #266 — rate limit `/passwordless-wait` |
| SEC-005 | **Adressé** | Rate limiting Auth API (`RateLimitFilter` pending/respond) — activer en prod via `ezkey.rate-limit.enabled=true` |
| SEC-006 | **Fait** | PR #308 — login admin 401 générique (anti-énumération) |
| SEC-007 | **Fait** | PR #297 — device proof token hash-only (V16) |
| SEC-008 | **Fait** | PR #293 — `audit.integrity.required` + health HMAC |
| SEC-009 | Ouvert | Contention `TinkKeyManager.getAeadPrimitive()` |
| SEC-010 | Ouvert | Chiffrement at-rest du hash API key |
| SEC-011 | En cours | `trusted-proxies.required` fail-fast au démarrage |
| SEC-012–016 | Ouvert | Durcissement crypto / défense en profondeur (voir backlog) |

Les trois risques du Top 3 initial (SEC-001, SEC-002, SEC-003) sont traités. Les phases 1–2 de la roadmap sont essentiellement closes ; la phase 3 (posture prod / SOC 2) est en cours.

### Top 3 risques à traiter en priorité

| # | Finding | Impact | Complexité de fix |
|---|---------|--------|------------------|
| 1 | **Challenge bypass sur `/passwordless-wait`** | Contourne le facteur challenge en cas de MITM | Faible (2 lignes) |
| 2 | **Dégradation silencieuse du chiffrement Tink** | Production sans chiffrement at-rest sans alerte | Modérée |
| 3 | **Memory leak sur les maps de rate limiting** | DoS via rotation d'IPs sous charge | Modérée |

---

## Ce qui fonctionne bien

Avant les constatations : ce qui a été correctement implémenté et qui renforce la crédibilité du projet.

- ✅ **Bearer tokens hashés (SHA-256)** — `AdminTokenValidationService` compare via hash, pas en clair.
- ✅ **Proof tokens par hash** — `SensitiveDataHasher.sha256Hex()` utilisé partout pour les lookups.
- ✅ **Low-S normalization** — `SignatureService.signEcdsaSha256()` normalise `s` pour la parité Conscrypt/JDK.
- ✅ **Read-once guarantee** — `findAndLockMostRecentValidByEnrollmentIdAndStatus()` + pessimistic lock sur la claim du pending attempt.
- ✅ **Supersession check** — `AuthAttemptRespondService` vérifie l'absence d'un attempt plus récent avant d'accepter.
- ✅ **Signature avant mutation de state** — Steps ordonnés : validation cryptographique → validation challenge → mutation DB.
- ✅ **HA bootstrap sécurisé** — `InitialGlobalAdminService` utilise `LockingTaskExecutor` avec distributed lock.
- ✅ **Recovery codes BCrypt + single-use** — 32 chiffres (106 bits), BCrypt, consommation immédiate après validation.
- ✅ **Enrollment state machine** — Transition BOUND → VERIFIED avec row-level locking et validation device key uniqueness.
- ✅ **ThreadLocal recursion guard** — `CHECKING_DATABASE` avec `finally` correctement implémenté dans `TinkKeyManager`.
- ✅ **IP resolver externalisé** — `ClientIpResolver` avec liste CIDR de proxies de confiance configurable.

---

## Findings par domaine

---

### Domaine 1 — Signature & Vérification

#### F-01-A — ECDSA : Malléabilité de signature (LOW)

**Fichier :** `SignatureService.validateSignature()`
**OWASP :** A02 — Cryptographic Failures

**Constat :** `signEcdsaSha256()` normalise la valeur `s` au low-S lors de la signature pour assurer la cohérence Conscrypt/Android. Mais `validateSignature()` (JDK `SHA256withECDSA`) accepte les deux formes (low-S et high-S), car le JDK ne force pas la normalisation à la vérification.

Un attaquant qui intercepte une signature DER valide peut produire une signature alternative mathématiquement valide en calculant `s' = n - s` (même message, même clé publique). Les deux signatures passeront la vérification.

**Impact :** Dans le contexte Ezkey, la malléabilité permet de transformer une signature valide en une autre sans connaître la clé privée. Le payload signé reste identique (`proofToken|accepted`), donc le contenu de la décision n'est pas modifiable. Le vecteur d'exploitation est limité à des attaques de journalisation (le serveur ne pourra pas distinguer si la signature est "originale" ou "malléée"). Pas d'impact direct sur l'authentification.

**Mitigation suggérée :** Ajouter une validation low-S explicite dans `validateSignature()` avant de passer au JCA :

```java
// Après Base64.getDecoder().decode(signatureBase64) :
BigInteger[] rs = EcdsaDerCodec.decodeSignature(sigBytes);
if (rs != null) {
  BigInteger n = ((ECPublicKey) publicKey).getParams().getOrder();
  BigInteger halfN = n.shiftRight(1);
  if (rs[1].compareTo(halfN) > 0) {
    return false; // Reject high-S — non-canonical form
  }
}
```

**Ratio impact/complexité :** 10 lignes de code. Recommandé comme mesure de durcissement.

---

#### F-01-B — Décodage Base64 permissif pour les signatures Ed25519 (LOW)

**Fichier :** `SignatureService.verifyIntegrationSignature()` → `decodeFlexibleBase64ToBytes()`
**OWASP :** A02 — Cryptographic Failures

**Constat :** La méthode `decodeFlexibleBase64ToBytes()` accepte à la fois le Base64URL (sans padding) et le Base64 standard. Les vérifications de longueur après décodage (`pubRaw.length != 32` et `sigRaw.length != 64`) constituent des garde-fous. Mais l'acceptation des deux formats introduit une ambiguïté lors de la normalisation.

**Impact :** Risque théorique de confusion de format si un attaquant envoie un payload encodé différemment que prévu. En pratique, les vérifications de longueur protègent contre les attaques les plus directes.

**Mitigation suggérée :** Documenter explicitement que le décodage permissif est intentionnel (rétrocompatibilité), et ajouter un log de niveau DEBUG indiquant quelle voie de décodage a été utilisée.

---

### Domaine 2 — Proof Token Lifecycle

#### F-02-A — Device proof token stocké en clair dans `auth_attempts` (MEDIUM)

**Fichier :** `AuthAttemptPendingService.claimPendingAttempt()`
**Code :** `authAttempt.setDeviceProofToken(request.getDeviceProofToken())`
**OWASP :** A02 — Cryptographic Failures

**Constat :** Le `deviceProofToken` est stocké en clair dans la colonne `auth_attempts.device_proof_token`. L'unicité est vérifiée via un hash SHA-256 (`existsByDeviceProofTokenHash()`), mais la valeur en clair est persistée simultanément.

L'enrollment proof token, en comparaison, est stocké **uniquement par son hash** (`findByEnrollmentProofTokenHashAndActive`). Cette asymétrie crée un traitement inégal pour deux types de secrets similaires.

**Impact :** Si la table `auth_attempts` est exfiltrée (dump DB, accès compromis, backup exposé), les device proof tokens récents pourraient être réutilisés. Le TTL des auth attempts limite la fenêtre d'exploitation, mais les rows restent en base après traitement (jusqu'au nettoyage périodique).

**Mitigation suggérée :**
1. Stocker uniquement `deviceProofTokenHash` sur l'entité `AuthAttempt`.
2. Retirer la colonne `device_proof_token` (ou la migrer vers NULL après hash).
3. La logique d'unicité `existsByDeviceProofTokenHash()` reste inchangée.

```java
// Au lieu de :
authAttempt.setDeviceProofToken(request.getDeviceProofToken());
// Utiliser :
// (le hash est déjà calculé dans validateDeviceSignature)
authAttempt.setDeviceProofTokenHash(deviceProofTokenHash);
authAttempt.setDeviceProofToken(null); // ou supprimer le champ
```

**Ratio impact/complexité :** Migration Flyway mineure + refactor de l'entité. Recommandé.

---

### Domaine 3 — Admin Auth & Bearer Token

#### F-03-A — Challenge bypass sur `/passwordless-wait` (HIGH)

**Fichier :** `AdminAuthService.waitForPasswordlessAuth()`
**Code :**
```java
if (challengeCode != null) {
  if (!challengeCode.equals(authAttempt.getAuthAttemptChallenge())) {
    throw new AdminAuthenticationException("Invalid challenge code");
  }
}
```
**OWASP :** A07 — Identification and Authentication Failures

**Constat :** Pour les admins avec `challengeRequired = true`, le flot de login génère un challenge code qui doit être confirmé côté admin sur `/passwordless-wait`. La validation du challenge est conditionnelle à ce que le **client envoie** le `challengeCode`. Si le client appelle `/passwordless-wait` sans fournir de `challengeCode` (ou avec `challengeCode = null`), la vérification côté serveur est **silencieusement sautée**.

Le challenge est validé indépendamment par le device via `AuthAttemptRespondService.validateChallenge()` — ce qui fournit une protection. Mais la validation admin-side est spécifiquement conçue pour résister à une attaque MITM : si un attaquant intercepte la réponse de login (obtenant `authAttemptId`), il peut appeler `/passwordless-wait` sans le challenge code et obtenir un bearer token si le device approuve (légitime ou trompé).

**Scénario d'attaque :**
1. Attaquant en position MITM intercepte la réponse au `POST /admin/auth/login`.
2. Obtient `authAttemptId` dans la réponse.
3. Le challenge code est conçu pour que le vrai admin le voie et le confirme sur son device. L'attaquant ne le connaît pas (il a intercepté après le device push).
4. L'attaquant appelle `POST /passwordless-wait` avec `authAttemptId` + **sans challengeCode**.
5. Si le device approuve (légitime, ou par social engineering), l'attaquant obtient un bearer token — le challenge n'a PAS été validé côté admin.

**Impact :** Contournement de la protection MITM fournie par le challenge. La valeur du `challengeRequired` flag est partiellement annulée.

**Mitigation (2 lignes) :** Vérifier si l'auth attempt *a* un challenge stocké, et si oui, exiger que le client le fournisse :

```java
Integer storedChallenge = authAttempt.getAuthAttemptChallenge();
if (storedChallenge != null) {
  // Challenge was required for this attempt — MUST be provided by the client
  if (challengeCode == null) {
    throw new AdminAuthenticationException("Challenge code required for this authentication attempt");
  }
  if (!challengeCode.equals(storedChallenge)) {
    logger.warn("Invalid challenge code for authAttemptId: {} (enumeration attack?)", authAttemptId);
    throw new AdminAuthenticationException("Invalid challenge code — authentication failed");
  }
}
```

**Ratio impact/complexité :** Fix de 6 lignes, risque de régression nul. **À implémenter immédiatement.**

---

#### F-03-B — Énumération de usernames via réponses d'erreur différenciées (MEDIUM)

**Fichier :** `AdminAuthService.authenticatePasswordless()`
**OWASP :** A07 — Identification and Authentication Failures

**Constat :** La méthode lève des exceptions distinctes selon l'état du compte :
- `AdminAuthenticationException` (401) → username invalide
- `AdminAccountInactiveException` (403) → compte désactivé
- `AdminNoEnrollmentException` (403) → compte actif mais pas d'enrollment
- `TenantInactiveException` (403) → tenant désactivé

Un attaquant qui énumère des usernames peut distinguer :
- Username inexistant → 401
- Username existant avec différents états → 403

**Impact :** Énumération de usernames valides et de l'état de leurs comptes. Permet de cibler des comptes spécifiques (accounts sans enrollment = potentiellement récents, donc plus faciles à social-engineer).

**Mitigation :** Retourner systématiquement HTTP 401 pour toute condition de pré-authentification sans bearer token. Les détails (compte inactif, pas d'enrollment) vont dans l'audit log uniquement.

```java
// Dans le catch du GlobalExceptionHandler:
// AdminAccountInactiveException, AdminNoEnrollmentException, TenantInactiveException
// → mapper tous vers HTTP 401 avec message générique
// → log interne au niveau WARN avec détails complets
```

**Ratio impact/complexité :** Changement dans `@ControllerAdvice`. Impact potentiel sur l'expérience utilisateur (messages moins informatifs pour l'admin en erreur). Recommandé pour la production.

---

#### F-03-C — `/passwordless-wait` non soumis au rate limiting (MEDIUM)

**Fichier :** `AdminRateLimitFilter.shouldApplyRateLimit()`
**Code :** `return requestUri.contains("/api/v1/admin/auth/login")`
**OWASP :** A07 — Identification and Authentication Failures

**Constat :** Le rate limiter couvre uniquement `POST /api/v1/admin/auth/login`. L'endpoint `POST /api/v1/admin/auth/passwordless-wait` n'est pas couvert.

Pour un admin avec `challengeRequired = true`, le challenge code (4 à 6 chiffres) est brute-forceable via `/passwordless-wait` sans rate limiting :
- 4 chiffres : 9 000 combinaisons
- 6 chiffres : 900 000 combinaisons
- TTL typique : 5 minutes → à 10 req/s = 3 000 tentatives → couvre 33% d'un espace 4-chiffres par TTL

**Pré-condition d'exploitation :** L'attaquant doit avoir l'`authAttemptId` (requires MITM ou accès préalable à la réponse de login).

**Impact :** Combiné avec F-03-A, ceci réduit davantage l'efficacité du challenge. En isolation, nécessite un `authAttemptId` valide et une fenêtre temporelle active.

**Mitigation :** Étendre `AdminRateLimitFilter.shouldApplyRateLimit()` pour couvrir `/passwordless-wait` :

```java
private static final List<String> RATE_LIMITED_PATHS = List.of(
  "/api/v1/admin/auth/login",
  "/api/v1/admin/auth/passwordless-wait"
);

private boolean shouldApplyRateLimit(String requestUri, String requestMethod) {
  if (!"POST".equals(requestMethod)) return false;
  return RATE_LIMITED_PATHS.stream().anyMatch(requestUri::contains);
}
```

**Ratio impact/complexité :** 5 lignes. Recommandé.

---

### Domaine 4 — API Key Handling

#### F-04-A — Secret key hash non chiffré at-rest (MEDIUM)

**Fichier :** `ApiKeyService.createApiKey()` → `apiKey.setSecretKeyHash(secretKeyHash)`
**OWASP :** A02 — Cryptographic Failures

**Constat :** Le secret key est BCrypt-hashé avant stockage. Cependant, contrairement à d'autres champs sensibles (proof tokens, device public keys), le `secretKeyHash` n'est pas chiffré par Tink avant d'être persisté en base.

Si la base de données est exfiltrée, un attaquant obtient les BCrypt hashes de tous les secrets API. BCrypt avec un coût approprié rend le crackage difficile (mais pas impossible pour des mots de passe faibles, or ici les secrets sont générés par SecureRandom sur 40 hex chars = 160 bits d'entropie → crackage impossible).

**Impact :** Dans le contexte Ezkey, l'entropie des secrets générés rend le crackage des hashes infaisable. L'impact réel est **limité**. Cependant, du point de vue de la posture et de la perception (SOC 2, etc.), le traitement asymétrique des secrets API vs. d'autres champs sensibles est notable.

**Mitigation :** Appliquer le chiffrement Tink via `@Convert(converter = EncryptedStringConverter.class)` sur la colonne `secret_key_hash`, identique à d'autres champs sensibles. Impact sur les performances : minimal (déchiffrement uniquement lors de la validation).

**Ratio impact/complexité :** Modérée. Cohérence avec les autres champs chiffrés. Recommandé pour Milestone 3+.

---

#### F-04-B — Aucun rate limiting sur l'authentification API key (LOW-MEDIUM)

**Fichier :** `ApiKeyService.validateApiKey()`
**OWASP :** A07 — Identification and Authentication Failures

**Constat :** L'endpoint d'authentification par API key (Integration API) n'a pas de rate limiting au niveau de l'auth elle-même, au-delà des limites sur la création d'auth attempts (100/15min). Un attaquant avec un `integrationKey` valide (la partie publique) pourrait tenter d'énumérer le `secretKey`.

**Impact :** Avec des secrets de 160 bits générés par SecureRandom, la brute-force est mathématiquement infaisable (10^48 combinaisons). L'impact réel est quasi-nul pour les clés correctement générées. Cependant, un rate limit défensif est attendu sur tout endpoint d'authentification.

**Mitigation :** Ajouter un rate limiter basé sur l'`integrationKey` (10-20 tentatives/min) pour limiter la vitesse d'énumération et fournir une détection via métriques.

---

### Domaine 5 — Multi-Tenancy & Authorization

#### F-05-A — `ApiKeyService.findAll()` sans scope tenant (LOW)

**Fichier :** `ApiKeyService.findAll()`
**Code :** `return apiKeyRepository.findAll()`
**OWASP :** A01 — Broken Access Control

**Constat :** Cette méthode retourne toutes les API keys du système, sans filtrage par tenant. Elle est déclarée pour usage GlobalAdmin, mais l'absence de filtre dans la requête JPA signifie qu'une misconfiguration de l'autorisation pourrait exposer toutes les clés cross-tenant.

**Impact :** Conditionnel à une erreur d'autorisation. Actuellement, seul `ROLE_ADMIN` (GlobalAdmin) atteint cette méthode. En défense-en-profondeur, la requête elle-même devrait être scoped.

**Mitigation :** Documenter explicitement l'intention GlobalAdmin-only. Optionnellement, ajouter un check `@PreAuthorize("hasRole('GLOBAL_ADMIN')")` au niveau du contrôleur si ce rôle est distingué programmatiquement.

---

### Domaine 6 — Enrollment Binding & Verification

#### F-06-A — Enrollment state machine robuste (NOTE POSITIVE)

**Constat :** `EnrollmentVerifyService.verify()` implémente une state machine rigoureuse :
- Rejet si `status != BOUND` (impossible de sauter l'étape bind)
- Row-level locking via `findByIdWithLock()` avant mutation
- Validation de l'unicité de la device public key (anti-replay)
- Validation du challenge si présent
- Validation de signature cryptographique avant toute mutation DB

Aucun finding de sécurité majeur dans ce domaine. ✅

#### F-06-B — `markInvalidAndClear()` dans un contexte de transaction imbriquée (LOW)

**Fichier :** `EnrollmentVerifyService.validateEnrollmentState()` → `enrollmentTxHelper.markInvalidAndClear()`
**OWASP :** A04 — Insecure Design

**Constat :** `enrollmentTxHelper.markInvalidAndClear()` utilise `@Transactional(REQUIRES_NEW)` pour committer le statut INVALID indépendamment de la transaction parente. C'est correct sur le principe. Cependant, si le processus échoue entre le commit du statut INVALID et la propagation de l'exception, l'enrollment restera en état INVALID sans que l'opération parente ait pu le savoir.

**Impact :** Risque de `EnrollmentVerifyStateConflictException` "orpheline" lors d'une tentative ultérieure sur le même enrollment. Impact limité : l'enrollment sera dans un état clairement invalide plutôt qu'un état ambigu.

**Mitigation :** Aucune action immédiate nécessaire. Documenter le comportement dans le Javadoc de `TxHelper`.

---

### Domaine 7 — Encryption Key Rotation & Re-encryption

#### F-07-A — Dégradation silencieuse du chiffrement (HIGH)

**Fichier :** `TinkKeyManager.initialize()`
**Code :**
```java
} catch (FileNotFoundException e) {
  logger.warn("Master key file not found. Encryption will be disabled...");
} catch (Exception e) {
  logger.error("Failed to initialize Tink encryption. Application will continue without encryption...");
}
```
**OWASP :** A05 — Security Misconfiguration

**Constat :** Si le fichier de master key est absent ou illisible, Tink ne s'initialise pas et `keysetHandle` reste `null`. L'application démarre et fonctionne normalement, mais sans chiffrement at-rest. Les champs sensibles (proof tokens, clés publiques device, secrets d'intégration) sont stockés en clair.

Il n'y a pas de :
- Mécanisme de fail-fast configurable (`require-encryption: true`)
- Health check exposant l'état du chiffrement
- Alerte opérationnelle distincte d'un simple log
- Validation au démarrage si le chiffrement est obligatoire

**Impact :** En production, une misconfiguration de chemin de fichier (Docker mount absent, permissions incorrectes) peut provoquer un démarrage sans chiffrement, totalement invisible pour un opérateur qui ne surveille pas les logs de démarrage.

**Mitigation :**
1. Ajouter une propriété `ezkey.encryption.required: false` (default) configurable à `true` pour les déploiements production. Quand `true`, échec au démarrage si Tink ne s'initialise pas.
2. Exposer `TinkKeyManager.isInitialized()` comme indicateur dans le Spring Actuator health endpoint.
3. Considérer un `@ConditionalOnProperty` ou un `@EventListener(ApplicationReadyEvent)` qui log un avertissement visible en dashboard.

```yaml
# application-production.yml
ezkey:
  encryption:
    required: true  # Fail startup if encryption unavailable
```

**Ratio impact/complexité :** Modérée. **Critique pour les déploiements production.**

---

#### F-07-B — `getAeadPrimitive()` synchronisé avec vérification DB = contention (MEDIUM)

**Fichier :** `TinkKeyManager.getAeadPrimitive()`
**OWASP :** A04 — Insecure Design

**Constat :** La méthode `getAeadPrimitive()` est `synchronized`. Elle est appelée sur chaque opération de chiffrement/déchiffrement (via `EncryptionEntityListener`). Toutes les 5 secondes, à l'intérieur du verrou, une requête DB est exécutée pour vérifier la version du keyset.

Sous charge élevée (>100 threads concurrents accédant à des entités chiffrées), tous les threads contendent sur ce verrou. La requête DB de 5 secondes peut bloquer tous les threads pendant ~10-50ms.

**Impact :** Dégradation des performances sous charge. Dans un scénario de ré-encryption + activité auth concurrente, les temps de réponse augmentent significativement.

**Mitigation :**
1. Sortir la vérification DB de la section synchronisée (vérification asynchrone ou dans un thread séparé).
2. Utiliser un `ReadWriteLock` : lecture non-exclusive pour `getAeadPrimitive()`, exclusive uniquement pour le reload.

```java
private final ReadWriteLock keysetLock = new ReentrantReadWriteLock();

public Aead getAeadPrimitive() {
  // Trigger async check periodically, outside the read lock
  triggerDatabaseCheckIfNeeded();
  keysetLock.readLock().lock();
  try {
    // ... return primitive
  } finally {
    keysetLock.readLock().unlock();
  }
}
```

**Ratio impact/complexité :** Modérée. Recommandé si charge élevée prévue (Milestone 3+).

---

#### F-07-C — Reencryption batch : auto-invocation Spring AOP (MEDIUM)

**Fichier :** `ReencryptionService.processBatch()` → `batchProcessingService.processBatchInternal()`
**OWASP :** A04 — Insecure Design

**Constat :** `ReencryptionService` est `@Transactional` et délègue à `ReencryptionBatchProcessingService.processBatchInternal()`. Ce design est correct car il utilise un bean distinct (pas d'auto-invocation). Cependant, `triggerFullReencryption()` n'est pas `@Transactional` mais appelle des méthodes transactionnelles. Le comportement sous exception n'est pas atomique au niveau du trigger complet.

**Impact :** Si `triggerFullReencryption()` échoue en cours de route, certains batches peuvent être créés/démarrés sans que d'autres le soient, laissant un état intermédiaire. La récupération dépend de la logique de reprise (`findBatchesEligibleForResume()`).

**Mitigation :** Documenter explicitement que `triggerFullReencryption()` n'est pas atomique et que la reprise est assurée par le scheduler. Ajouter un test d'intégration couvrant la reprise après échec partiel.

---

### Domaine 8 — Rate Limiting & Brute Force

#### F-08-A — Memory leak sur `failureCountMap` et `blockedUntilMap` (MEDIUM)

**Fichier :** `AdminRateLimitFilter` (constructeur)
**Code :**
```java
this.failureCountMap = new ConcurrentHashMap<>();
this.blockedUntilMap = new ConcurrentHashMap<>();
```
**OWASP :** A05 — Security Misconfiguration

**Constat :** Le `bucketCache` utilise Caffeine avec max 1000 entrées et expiration après 1h. Mais les deux maps `failureCountMap` et `blockedUntilMap` sont des `ConcurrentHashMap` sans politique d'éviction.

Sous une attaque distribuée avec rotation d'IPs (chaque requête vient d'une IP distincte), ces maps accumulent des entrées indéfiniment. Chaque entrée est petite (~100 octets), mais avec 10 000 IPs uniques/heure, la croissance peut devenir significative sur une longue période.

**Impact :** Vulnérabilité DoS par épuisement mémoire JVM. L'attaquant n'a pas besoin de passer la limite de rate — il suffit de générer beaucoup d'IPs uniques.

**Mitigation :** Remplacer par des caches Caffeine avec expiration identique au `bucketCache` :

```java
this.failureCountMap = Caffeine.newBuilder()
    .maximumSize(10_000)
    .expireAfterWrite(Duration.ofHours(1))
    .<String, AtomicInteger>build()
    .asMap();

this.blockedUntilMap = Caffeine.newBuilder()
    .maximumSize(10_000)
    .expireAfterWrite(Duration.ofHours(2))
    .<String, Long>build()
    .asMap();
```

**Ratio impact/complexité :** Faible. **Recommandé.**

---

#### F-08-B — Auth API endpoints sans rate limiting documenté (MEDIUM)

**Fichier :** `ezkey-auth-api` — endpoints `/api/v1/auth-attempts/pending` et `/api/v1/auth-attempts/respond`
**OWASP :** A07 — Identification and Authentication Failures

**Constat :** `AdminRateLimitFilter` protège le login admin. L'Integration API a un rate limit sur la création d'auth attempts (100/15min). Mais les endpoints de l'Auth API (utilisés par le mobile) ne semblent pas avoir de rate limiting documenté ou implémenté dans le code source visible.

`/pending` : accepte un `enrollmentProofToken`. Sans rate limiting, un attaquant peut faire du polling agressif.
`/respond` : accepte une réponse device. Sans rate limiting, peut être ciblé pour le brute-force de challenge codes sur le côté device (même si l'entropie du challenge + la validation de signature rendent ça difficile).

**Impact :** MEDIUM — la validation cryptographique rend l'exploitation directe difficile, mais l'absence de rate limiting signifie qu'il n'y a pas de détection d'activité anormale.

**Mitigation :** Ajouter un rate limit basé sur `enrollmentId` (ou l'IP) sur `/pending` et `/respond` dans l'Auth API. Valeur suggérée : 30 req/min par enrollment.

---

### Domaine 9 — Audit Logging & Integrity

#### F-09-A — Dégradation silencieuse du HMAC d'intégrité des logs (MEDIUM)

**Fichier :** `AuditHmacService.init()`
**Code :**
```java
if (keyFilePath == null || keyFilePath.isBlank()) {
  logger.warn("Audit HMAC key file not configured... HMAC signing will be disabled.");
  return;
}
```
**OWASP :** A09 — Security Logging and Monitoring Failures

**Constat :** Si le fichier de clé HMAC n'est pas configuré ou n'est pas trouvé, la signature HMAC est silencieusement désactivée. L'audit log continue de fonctionner, mais sans garantie d'intégrité. Les entrées de logs pourraient être modifiées sans détection.

Identique à F-07-A (dégradation Tink) mais pour la couche intégrité des logs.

**Impact :** Compromet la piste d'audit en cas d'incident. Particulièrement problématique pour la posture SOC 2 (CC7.2 — audit trail integrity).

**Mitigation :**
1. Ajouter `isActive()` dans le health endpoint Actuator.
2. Optionnel : propriété `ezkey.audit.integrity.required: false` configurable à `true`.
3. Log de démarrage WARN visible distinguant "disabled by config" (normal) vs. "failed to load key" (anomalie).

---

#### F-09-B — IP source dans les audit logs spoofable (LOW-MEDIUM)

**Fichier :** `ClientIpResolver.resolve()`
**OWASP :** A09 — Security Logging and Monitoring Failures

**Constat :** L'IP source dans les audit logs est extraite de `X-Forwarded-For` si la liste des proxies de confiance est configurée. Si la liste est vide ou incorrecte, toute IP peut être spoofée dans les logs via manipulation du header `X-Forwarded-For`.

**Impact :** Attribution incorrecte dans les logs d'audit. Un attaquant peut faire apparaître ses actions comme venant d'une IP interne ou d'un autre utilisateur.

**Mitigation :** Valider que `TrustedProxyProperties.getCidrs()` n'est pas vide en production. Propriété `ezkey.trusted-proxies.required=true` (fail-fast au démarrage, SEC-011). Documenter explicitement que `trusted-proxies.cidrs` doit être configuré dans tous les déploiements derrière un reverse proxy.

---

### Domaine 10 — Concurrency & Race Conditions

#### F-10-A — Bootstrap HA sécurisé (NOTE POSITIVE)

`InitialGlobalAdminService` utilise `LockingTaskExecutor` avec un distributed lock (`ADMIN_STARTUP_BOOTSTRAP`, TTL 5 minutes). Les instances concurrentes skipent l'initialisation si le lock est détenu. ✅

#### F-10-B — Contention sur `getAeadPrimitive()` pendant la re-encryption (MEDIUM)

Voir F-07-B. Applicable aussi en Domaine 10 : sous re-encryption concurrente, tous les threads qui accèdent à des entités chiffrées contendent sur le même verrou dans `TinkKeyManager`. La re-encryption peut prolonger la durée de contention.

---

## Backlog priorisé

### Critique — À traiter immédiatement

| ID | Finding | Fichier | Fix |
|----|---------|---------|-----|
| SEC-001 | Challenge bypass via `challengeCode=null` sur `/passwordless-wait` | `AdminAuthService.java` | Vérifier `storedChallenge != null` → requiert `challengeCode` du client |

### Haute priorité — À planifier (Milestone 3)

| ID | Finding | Fichier | Fix |
|----|---------|---------|-----|
| SEC-002 | Dégradation silencieuse Tink sans chiffrement | `TinkKeyManager.java` | Propriété `encryption.required`, health check Actuator |
| SEC-003 | Memory leak `failureCountMap` / `blockedUntilMap` | `AdminRateLimitFilter.java` | Caffeine avec expiration |
| SEC-004 | `/passwordless-wait` non rate-limité | `AdminRateLimitFilter.java` | Étendre `RATE_LIMITED_PATHS` |
| SEC-005 | Auth API (`/pending`, `/respond`) sans rate limiting | `ezkey-auth-api` | Rate limit par enrollmentId |
| SEC-006 | Énumération de usernames via erreurs différenciées | `AdminAuthService.java` + `@ControllerAdvice` | Normaliser vers HTTP 401 générique |

### Priorité moyenne — Amélioration continue

| ID | Finding | Fichier | Fix |
|----|---------|---------|-----|
| SEC-007 | Device proof token stocké en clair | `AuthAttemptPendingService.java`, `AuthAttempt.java` | Stocker hash uniquement |
| SEC-008 | Dégradation silencieuse HMAC audit | `AuditHmacService.java` | Health check + propriété `required` |
| SEC-009 | Contention `getAeadPrimitive()` synchronisé | `TinkKeyManager.java` | `ReadWriteLock` + check DB asynchrone |
| SEC-010 | Secret API key hash non chiffré at-rest | `ApiKeyService.java`, `ApiKey.java` | Appliquer `@Convert(EncryptedStringConverter)` |
| SEC-011 | IP source spoofable dans audit logs | `ClientIpResolver.java` | Validation config au démarrage |

### Faible priorité / Durcissement

| ID | Finding | Fichier | Fix |
|----|---------|---------|-----|
| SEC-012 | ECDSA malléabilité (high-S non rejetée) | `SignatureService.java` | Rejet low-S dans `validateSignature()` |
| SEC-013 | Décodage Base64 trop permissif (Ed25519) | `SignatureService.java` | Log de voie utilisée, documenter intentionnel |
| SEC-014 | Absence de validation longueur signature avant décodage | `SignatureService.java` | Pre-check longueur string |
| SEC-015 | `ApiKeyService.findAll()` sans scope tenant | `ApiKeyService.java` | Documenter + annotation GlobalAdmin-only |
| SEC-016 | Pas de rate limiting sur auth API key (Integration API) | `ApiKeyService.java` | Rate limit par integrationKey |

---

## Synthèse OWASP Top 10

| Catégorie | Findings | Gravité max |
|-----------|---------|------------|
| A01 — Broken Access Control | SEC-015 | LOW |
| A02 — Cryptographic Failures | SEC-007, SEC-010, SEC-012, SEC-013, SEC-014 | MEDIUM |
| A04 — Insecure Design | SEC-001, SEC-004, SEC-009 | HIGH |
| A05 — Security Misconfiguration | SEC-002, SEC-003, SEC-008 | HIGH |
| A07 — Identification & Authentication Failures | SEC-001, SEC-004, SEC-005, SEC-006 | HIGH |
| A09 — Security Logging Failures | SEC-008, SEC-011 | MEDIUM |

---

## Roadmap de durcissement suggérée

### Phase 1 — Immédiat (sprint en cours)
- SEC-001 : Challenge bypass (6 lignes)
- SEC-003 : Memory leak rate limiter (Caffeine)
- SEC-004 : Rate limit `/passwordless-wait` (5 lignes)

### Phase 2 — Milestone 3
- SEC-002 : Propriété `encryption.required` + health check Tink
- SEC-006 : Normalisation des erreurs auth (énumération usernames)
- SEC-007 : Device proof token hash-only
- SEC-008 : Health check HMAC audit

### Phase 3 — Milestone 4 / SOC 2 prep
- SEC-005 : Rate limiting Auth API
- SEC-009 : `ReadWriteLock` Tink
- SEC-010 : Chiffrement hash API key
- SEC-011 : Validation config IP audit
- SEC-012 : Rejet high-S ECDSA

---

## Tests recommandés à ajouter

```text
// SEC-001 — Challenge bypass
AuthAttemptChallengeBypassTest:
  - Challenge-required admin + /passwordless-wait sans challengeCode → expect 401
  - Challenge-required admin + /passwordless-wait avec mauvais code → expect 401
  - Challenge-required admin + /passwordless-wait avec bon code + device approved → expect 200

// SEC-003 + SEC-004 — Rate limiting
RateLimitCoverageTest:
  - POST /passwordless-wait > 10/min par IP → expect 429
  - Memory usage après 10k IPs uniques → expect stable heap

// SEC-007 — Device proof token
DeviceProofTokenStorageTest:
  - Après claim d'un pending attempt, device_proof_token en DB est null
  - device_proof_token_hash en DB n'est pas null
```

---

*Rapport généré dans le cadre du Security Challenge — Ezkey Backend Cryptographic Audit, juin 2026.*
*Posture : pen-test pragmatique. Findings basés sur revue de code statique. Aucune exploitation active réalisée.*
