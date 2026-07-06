# Plan: Ezkey Security Challenge — Cryptographic & Backend Auditi

## TL;DR

Campagne de sécurité proactive sur le backend Java + protocole cryptographique d'Ezkey, pour identifier et classer les risques réels (pas de chasse aux sorcières), et proposer des mitigations pragmatiques alignées avec les principes du projet (simplicité + pragmatisme + crédibilité de sécurité).

Livrables:
- Rapport de constatations structuré (impact + gravité + preuve conceptuelle pour chaque trouvaille)
- Backlog de risques priorisé (CRITIQUE, HAUT, MOYEN) avec recommandations d'action

**Profondeur:** Java backend + contrats cryptographiques (signatures, tokens, séquençage des preuves)  
**Cadence:** Une passe structurée sur dix domaines identifiés  
**Posture:** Pen-test actif mais pragmatique (ne pas refuser complexité si gain de sécurité justifié)

---

## Contexte

### Enjeux du projet
- **Crédibilité en alpha:** Ezkey est un protocole propriétaire distinct de FIDO2/WebAuthn, en cours d'établissement de sa réputation.
- **Fondation cryptographique:** EC P-256 (device) + Ed25519 (integration), preuves ponctuelles (proof tokens), signa signatures.
- **Attaque du groupe:** MFA = cible de haut risque; compromission d'une part serait miner la confiance établie.
- **Principes du projet:** Pragmatisme + simplicité — complexité acceptable si elle élimine un risque réel.

### Documents de référence critiques (à lire en parallèle)
- `PRD.md` — fondations du protocole distinct, trust model backend-first
- `docs/ARCHITECTURE.md` — composants, frontières de confiance
- `docs/CRYPTO.md` — algorithmes, formats de clés, signatures
- `docs/LIFECYCLE_GOVERNANCE.md` — état des entités, chaînes d'éligibilité
- `docs/ADMIN_API_SECURITY_GUIDE.md` — flux d'auth passwordless, gestion des tokens
- `docs/API_SECURITY_MATRIX.md` — matrice RBAC
- `docs/API_SECURITY_DESIGN_CHALLENGES.md` — décisions ouvertes et ratios


---

## Domaines à auditer (dans l'ordre)

### **Domaine 1: Signature & Vérification (CRITIQUE)**
**Fichiers:** `SignatureService.java`, `Ed25519SpkiBytes.java`, `EcdsaDerCodec.java`, `AdminAuthService.java`

**Risques à explorer:**
- DER/SPKI parsing robustesse (malformed bytes, edge cases)
- Normalisation Base64/Base64URL (padding, décoding permissif)
- Normalisation UTF-8 + NFC sur payloads signés (canonical form pour le device et backend)
- Low-S enforcement pour ECDSA (cohérence Conscrypt/JDK)
- Entropie RNG pour génération de keys
- **Replay:** la preuve poncuelle (proof token) crée-t-elle une liaison authentique au contexte? (auth attempt, enrollment, challenge)

**Vecteur d'attaque type:** Réutilisation de signature valide sur un second auth attempt, usurpation d'identité d'admin via faux signature.

**Dépendances:** Vérifier la cohérence signature device (mobile, Conscrypt) ↔ backend (JDK)

---

### **Domaine 2: Proof Token Lifecycle (CRITIQUE)**
**Fichiers:** `AuthAttemptPendingService.java`, `AuthAttemptRespondService.java`, `ProofTokenService.java`, token-related repos

**Risques à explorer:**
- Génération (SecureRandom, entropie, format URL-safe Base64)
- Stockage (encrypted, timestamped, indexed)
- Consommation (read-once guarantee, validation du contexte)
- Supersession (plus ancien token invalidé si nouveau créé?)
- TTL/expiry (fenêtre de réponse, clock skew)
- Cleanup (tokens expirés supprimés, scheduled ou à la demande?)
- **Concurrence:** deux requests simultanées qui font avancer le même enrollment — race condition?

**Vecteur d'attaque type:** Replay de proof token ancien, réutilisation après expiry, token brute-force.

---

### **Domaine 3: Admin Auth & Bearer Token (CRITIQUE)**
**Fichiers:** `AdminAuthController.java`, `AdminAuthService.java`, `AdminTokenValidationService.java`, `AdminRecoveryService.java`, `InitialGlobalAdminService.java`

**Risques à explorer:**
- Bootstrap initial: `InitialGlobalAdminService` lance-t-elle une fois ou peut-elle créer duplicatas en HA?
- Device signature validation (ordre: cryptographique avant state mutation?)
- Challenge code validation (mandatory, cannot be skipped?)
- Bearer token (plaintext compare — à remplacer par hash selon le RFC 9457 plan?)
- Token rotation (ancien révoqué immédiatement?)
- Deactivation of admin (tokens révoqués atomiquement?)
- Recovery token (single-use guarantee, consume-before-reissue?)
- Rate limiting (5/min, par IP — X-Forwarded-For peut être spoofé?)

**Vecteur d'attaque type:** Brute-force passwordless login, session hijacking via plaintext token, account takeover via recovery token replay.

---

### **Domaine 4: API Key Handling & Isolation (HAUT)**
**Fichiers:** `ApiKeyService.java`, `ApiKeyController.java`, integration API authentication filters

**Risques à explorer:**
- Secret generation & hashing (BCrypt, HMAC — comparable à best practice?)
- Expiry enforcement (expired keys rejet immédiat ou delayed propagation?)
- IP whitelist validation (CIDR parsing permissif? order-sensitive bugs?)
- Cross-integration isolation (peut-on accéder aux auth attempts d'une autre intégration via même API key?)
- Revocation immediate ou delayed (race window?)
- Audit trail de l'accès au secret non-hashé (who saw the key once?)
- Database encryption (secret hash encrypted at rest ou plaintext en BD?)

**Vecteur d'attaque type:** API key compromise, escalation to peer integration.

---

### **Domaine 5: Multi-Tenancy & Authorization (HAUT)**
**Fichiers:** `EntityEligibilityService.java`, controllers, JPA Specifications

**Risques à explorer:**
- Role checking symmetry (`@PreAuthorize` correctement utilisé pour GlobalAdmin vs TenantAdmin?)
- Tenant filtering in all queries (aucun `findAll()` sans tenant context?)
- Eligibility chain runtime evaluation (parent chain correctly checked?)
- Specification builders (JPA `.and()` / `.or()` — precedence bugs?)
- Enrollment/API key visibility (can TenantAdmin A enumerate TenantAdmin B's resources?)

**Vecteur d'attaque type:** Cross-tenant data leakage, privilege escalation via misconfigured authorization.

---

### **Domaine 6: Enrollment Binding & Verification (HAUT)**
**Fichiers:** `EnrollmentVerifyService.java`, `EnrollmentBindService.java`, enrollment controllers

**Risques à explorer:**
- State machine (PENDING → VERIFIED → ACTIVE — can state be skipped?)
- Proof token binding (tied to enrollment ID + challenge?)
- Device public key replacement (already-verified enrollment — can key be updated without re-verify?)
- Challenge code validation (optional ou mandatory?)
- Atomicity of bind + verify (committed before return ou risk of partial state?)

**Vecteur d'attaque type:** Device hijacking, enrollment bypass, disabled MFA.

---

### **Domaine 7: Encryption Key Rotation & Re-encryption (HAUT)**
**Fichiers:** `TinkKeyManager.java`, `EncryptionService.java`, `KeyRotationService.java`, `ReencryptionService.java`

**Risques à explorer:**
- Master key file permissions (600? readable by wrong process?)
- ThreadLocal recursion guard (if fails, uncontrolled stack?)
- Synchronized methods contention (deadlock risk under high load?)
- Degraded mode fallback (silent fallback to weak key sans alerting?)
- Reencryption batch processing (optimistic locking conflicts, large persistence context, self-invocation AOP bypass?)
- Batch atomicity (all-or-nothing, or partial success risk?)

**Vecteur d'attaque type:** Key exposure via file permissions, DoS via lock contention, data loss via reencryption failure.

---

### **Domaine 8: Rate Limiting & Brute Force Prevention (HAUT)**
**Fichiers:** `AdminRateLimitFilter.java`, `EvaluatorSelfRegistrationRateLimiter.java`, Auth API

**Risques à explorer:**
- Admin login rate limiting (per IP — X-Forwarded-For spoofing? trusted proxy list empty or wrong?)
- Auth API endpoints (challenge code, proof token — rate limited? documented?)
- Bucket4j cache eviction (1h TTL — memory exhaustion via 1000s of source IPs?)
- Synchronized blocks (contention under concurrency?)
- Different IP targeting (if one IP exhausted, attacker tries different IPs — is quota separate or shared?)

**Vecteur d'attaque type:** Brute-force challenge code, credential stuffing via rotating IPs.

---

### **Domaine 9: Audit Logging & Integrity (MOYEN)**
**Fichiers:** `AuditLogService.java`, `AuditHmacService.java`, `ClientIpResolver.java`, audit controllers

**Risques à explorer:**
- HMAC chain implementation (is integrity checking mandatory or optional? chain corruption recovery?)
- Client IP extraction (X-Forwarded-For spoofing — same as rate limiting)
- Sensitive data logging (PII in audit table, unauthorized access?)
- Partition pruning (retention policy short enough for forensics?)
- Audit-log export/archive integrity (boundary crossing?)

**Vecteur d'attaque type:** Audit tampering, forensic gap, compliance failure.

---

### **Domaine 10: Concurrency & Race Conditions (MOYEN)**
**Fichiers:** `ReencryptionBatchParallelRunner.java`, bootstrap services, test helpers

**Risques à explorer:**
- Self-invocation of @Transactional (Spring AOP bypass, isolation incorrect)
- Lock contention (mutex per batch, other batches delayed)
- Optimistic locking conflicts (high churn, no auto-retry)
- HA bootstrap race (InitialGlobalAdminService, AdminBootstrapService both on startup event, no distributed lock)

**Vecteur d'attaque type:** Deadlock, race condition, duplicate state.

---

## Étapes du challenge

### Phase 1: Setup & Préparation (1-2h)
- [ ] Clone latest main, checkout clean worktree
- [ ] Build all modules (`mvn spotless:apply && mvn clean install`)
- [ ] Review PRD.md, ARCHITECTURE.md, LIFECYCLE_GOVERNANCE.md, CRYPTO.md in parallel
- [ ] Open tests referenced in each hotspot for use as reference implementations

### Phase 2: Deep Review — Domaines 1–5 (crypto + auth + API) (4–6h)
- [ ] Read Domaine 1 code end-to-end: `SignatureService` → `AdminAuthService` → verification order
- [ ] Trace auth flow: login request → device signature → token issuance
- [ ] Check proof token generation, validation, supersession, cleanup
- [ ] Verify admin bootstrap atomicity (distributed lock or single-instance only?)
- [ ] Verify API key ownership checks placed before data access
- [ ] Verify tenant filtering in all major queries
- [ ] Run existing security tests to baseline
- [ ] **Formulate Domaines 1–5 findings** (gratuites CRITIQUE + HAUT)

### Phase 3: Deep Review — Domaines 6–10 (infrastructure) (3–4h)
- [ ] Trace enrollment state machine: PENDING → VERIFIED → ACTIVE
- [ ] Verify device key replacement preconditions
- [ ] Review Tink key rotation & reencryption flow (master key perms, deadlock risk)
- [ ] Check rate limiting coverage (Admin API + Auth API)
- [ ] Check audit HMAC chain mandatory vs optional
- [ ] Review concurrency patterns (synchronized, locks, AOP)
- [ ] **Formulate Domaines 6–10 findings** (HAUT + MOYEN)

### Phase 4: Integration & Impact (1–2h)
- [ ] Cross-check findings: does Domaine X finding impact Domaine Y?
- [ ] Example: rate limiting gap + replay token = escalated risk
- [ ] Propose mitigations (prioritize by impact/complexity ratio)
- [ ] Verify mitigations don't break existing UX or tests

### Phase 5: Deliverables (2–3h)
- [ ] **Rapport (markdown):** one-page findings per domain, impact statement, proof sketch
- [ ] **Backlog priorisé:** CRITIQUE → HAUT → MOYEN, avec recommandations concises
- [ ] **Summary (1 page):** what's working well + top 3 areas to fix first

---

## Vérification & Tests

### Baseline Automated
```bash
mvn checkstyle:check
mvn spotless:check
mvn test -pl 'ezkey-tests' -Dtest='*SecurityTest'
```

### Manual Verification (per domain)
- **Domain 1 (Signature):** Forge a malformed ECDSA signature, verify rejection; check UTF-8 + NFC normalization
- **Domain 2 (Proof Token):** Replay old proof token, verify TTL check; create concurrent pending requests
- **Domain 3 (Admin Auth):** Rate-limit test via X-Forwarded-For spoofing; recovery token double-use
- **Domain 4 (API Key):** Cross-tenant key access attempt; expired key usage
- **Domain 5 (Multi-Tenancy):** TenantAdmin listing peer-tenant integrations
- **Domain 6 (Enrollment):** State machine skip attempt; public key replacement
- **Domain 7 (Encryption):** Reencryption under concurrent churn; master key permission audit
- **Domain 8 (Rate Limiting):** Challenge code brute-force per IP; aggregate exhaustion
- **Domain 9 (Audit):** HMAC chain corruption detection; sensitive data in logs
- **Domain 10 (Concurrency):** HA bootstrap duplicates; optimistic locking conflicts

---

## Décisions du plan

| Décision | Rationale |
|----------|-----------|
| **Java backend + protocole** (pas mobile) | Mobile crypto est référencé mais principal focus = backend vérification + protocol contract |
| **Une passe structurée** (pas vagues) | Couverture large permet priorisation finale, plus efficace qu'itérer |
| **Backlog priorisé** (pas rapport long) | Actionnable pour dev team, lié à capacité de fix |
| **Pen-test pragmatique** | Pas chasse aux sorcières — chaque trouvaille = impact réel + mitigation raisonnable |
| **Mitigations inclues** | Pas juste dire "c'est cassé" — proposer solution avec ratio impact/complexité |

---

## Livrables finaux attendus

1. **Rapport par domaine (markdown):** Constat + Impact + Preuve + Mitigation suggérée (1–2p par domaine)
2. **Backlog GitHub:** Issues priorisées (CRITIQUE, HAUT, MOYEN) avec labels `type:security`, `lang:java`, `component:*`
3. **Summary sheet (1p):** Top 3 risques critiques + quick wins + long-term hardening roadmap
4. **Test additions (optional):** New negative tests si gaps identifiés

---

## Dépendances & Blockers

- **Pas de blockers connus.** Codebase compile, tests passent (baseline).
- **Dépendances:** Accès à PRD.md, CRYPTO.md, docs/*. Tous accessibles dans le repo.
- **Audience:** Developpeurs Java backend, security-aware. Plan suppose familiarité avec Spring/JPA/crypto basics.
