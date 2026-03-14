# Bearer Token Storage: Hash/HMAC vs Plaintext — Complete Analysis

## 1. Source of the recommendation in EZKey

The recommendation comes from the **Admin Auth Token Strategy Audit** ([.github/prompts/plan-adminAuthTokenStrategyAudit.prompt.md](.github/prompts/plan-adminAuthTokenStrategyAudit.prompt.md)), which evaluates the opaque bearer token approach. It explicitly lists:

- **Section 2.4 (What could be improved):**  
  *"Token hashing in DB (store SHA-256 hash instead of plaintext token) — defense against DB breach. Currently the token is stored as-is. This is a known improvement pattern."*

- **Section 8 (Priority improvements, optional):**  
  *"1. **Token hashing in DB** — Store `SHA-256(token)` instead of plaintext. Protects against DB breach. Low effort, high security value."*

The same document recommends **staying the course** on opaque tokens (no JWT) and treats hashing as an **optional, high-value hardening** step.

The [ENCRYPTION_KEY_ROTATION_IMPLEMENTATION.md](docs/ENCRYPTION_KEY_ROTATION_IMPLEMENTATION.md) doc mentions **Phase 3: Admin Token Encryption** (encrypting the bearer token at rest with Tink AEAD). That is a **different** control (encryption vs hashing). This analysis focuses only on the **hash/HMAC** approach (store a derived value, not the token itself).

---

## 2. Current EZKey state: tokens and prefixes

| Token type | Prefix | Storage | Lookup |
|------------|--------|---------|--------|
| **Admin bearer** | `ezkey_` + UUID (no hyphens) | `ezkey_admin_tokens.bearer_token` **plaintext** | `findByBearerTokenAndActiveTrue(token)` |
| **Recovery** | `ezkey_recovery_` | Same table, same column, plaintext | Same |
| **API key (integration)** | `ezkey_ikey_` | Plain in DB (public identifier) | By integration key |
| **API key (secret)** | `ezkey_skey_` | **BCrypt hash** in `ezkey_api_key.secret_key_hash` | Lookup by `integration_key`, then BCrypt.matches(secret, hash) |

So today:

- **API key secrets** are already stored as a one-way derivative (BCrypt), not plaintext.
- **Admin bearer tokens** (and recovery tokens in the same table) are stored in **plaintext** in [AdminToken](ezkey-core/src/main/java/org/ezkey/integration/domain/entity/AdminToken.java) (`bearer_token`), with lookup in [AdminTokenRepository](ezkey-core/src/main/java/org/ezkey/integration/domain/repository/AdminTokenRepository.java) and validation in [AdminAuthService](ezkey-admin-api/src/main/java/org/ezkey/admin/service/AdminAuthService.java) (`findByBearerTokenAndActiveTrue(bearerToken)`).

The **prefix** is used only to distinguish token type (e.g. [AdminEnrollmentController](ezkey-admin-api/src/main/java/org/ezkey/admin/controller/AdminEnrollmentController.java) checks `ezkey_recovery_` for recovery flow). It does not change how the value is stored; it is a small, useful operational hint.

---

## 3. Hash vs HMAC vs "signature" (clarification)

- **Hash (e.g. SHA-256):** One-way, deterministic. Store `hash(token)`. On each request: compute `hash(incomingToken)`, look up row by that value. No secret key. Protects DB breach: an attacker with DB dumps gets hashes, not usable tokens.
- **HMAC (e.g. HMAC-SHA-256):** Deterministic with a secret key. Store `HMAC(token, key)`. Lookup: compute `HMAC(incomingToken, key)`, find row. Same lookup pattern. Adds key management; if the key is compromised, an attacker could potentially compute HMACs for candidate tokens (still need to guess the high-entropy token). For "store derived value for lookup only", SHA-256 is simpler and sufficient.
- **Digital signature:** Typically asymmetric (sign with private key, verify with public). Used to prove origin/integrity of a message, not as a storage surrogate for lookup. The audit's "signature/HMAC/hash" is best read as: store a **cryptographic derivative** (hash or HMAC), not the raw token.

**Conclusion:** For EZKey's goal (no plaintext token in DB, lookup by derived value), **SHA-256(token)** is the right fit: no key to manage, no collision risk in practice, and industry-accepted for high-entropy session tokens.

---

## 4. Is it common and best practice?

- **OWASP Session Management:** The session repository must protect session IDs against "local or remote accidental disclosure or unauthorized access." Storing only a hash of the token aligns with that.
- **NIST 800-63-4:** Session secrets should be "protected appropriately"; reducing exposure of the raw token in storage supports that.
- **Important nuance:** For **passwords**, OWASP recommends **slow** hashes (Argon2, BCrypt) because we look up by username then verify. For **session tokens** we have no separate identifier—we only have the token. So we must **look up by the derived value**. That requires a **deterministic** function (same token → same stored value). Hence a **fast** hash (SHA-256) or HMAC is appropriate; BCrypt (random salt) is not suitable for token lookup.
- **Community (e.g. Security Stack Exchange, session libraries):** The pattern "store hash of token, look up by hash" is standard. SHA-256 is considered acceptable for high-entropy secrets (e.g. UUID ≈ 122 bits) because brute-forcing is infeasible.

So: **yes**, storing a hash of the bearer token (instead of plaintext) is a **common, recommended** hardening for DB breach defense, and SHA-256 is the usual choice for this use case.

---

## 5. Similar projects and EZKey's position

The audit already compared EZKey to GitHub, GitLab, Stripe, Grafana, Vault, privacyIDEA, etc.: they use **opaque, server-side** tokens; the audit did not claim they all hash those tokens in DB. So "similar projects" often use the same **architecture** (opaque tokens + DB), but the **storage form** (plain vs hash) is an implementation detail many don't document. EZKey would be in good company by adding token hashing as a **defense-in-depth** measure without changing the overall model.

---

## 6. Real added value

- **Gain:** If the database is exfiltrated (backup leak, SQL injection, compromised DB host), the attacker obtains **hashes**, not bearer tokens. They cannot use those hashes in `Authorization: Bearer <token>` without inverting the hash, which is not feasible for 122-bit entropy tokens.
- **Scope:** Applies to all tokens in `ezkey_admin_tokens` (normal admin and recovery). One change protects both.
- **No change** to: token format, lifetime, revocation, cleanup, or client behavior. Only the **stored value** and the **lookup key** change.

---

## 7. Schema and column strategy (single column only)

**Only one column:** Store **only** `bearer_token_hash` (e.g. VARCHAR(64) for SHA-256 hex). There is no reason to keep both a plaintext column and a hash column in production:

- Keeping both would expose the risk of a misconfiguration (e.g. a "dev mode" that writes plaintext) ending up in production, and would weaken the security goal.
- The target schema is therefore: **replace** `bearer_token` by `bearer_token_hash`; no dual-column phase, no optional mode.

Implementation: at **issue** time, compute SHA-256(token), persist only the hash, return the **plain** token to the client (unchanged). At **validation** time, compute SHA-256(incoming token), then `findByBearerTokenHashAndActiveTrue(hash)`. A few call sites in [AdminAuthService](ezkey-admin-api/src/main/java/org/ezkey/admin/service/AdminAuthService.java), [AdminTokenValidationService](ezkey-admin-api/src/main/java/org/ezkey/admin/service/AdminTokenValidationService.java), [AdminRecoveryService](ezkey-admin-api/src/main/java/org/ezkey/admin/service/AdminRecoveryService.java), and the filter that calls the validation service.

**Accidental complexity:** Low. No new protocols, no key distribution, no change to clients or to token semantics.

---

## 8. Migration (full development, no existing installations)

Context: **full development mode, no production or existing installations.** There is **no migration of existing data** to consider.

- **Positioning:** Execute the plan as an **adjustment to the appropriate Flyway migration** so that the schema is correct from the start where possible.
- **Approach:** Do **not** edit existing migrations (e.g. [V2__add_multi_tenant_security.sql](ezkey-core/src/main/resources/db/migration/V2__add_multi_tenant_security.sql)) to avoid Flyway checksum and ordering issues. Add a **new** migration (e.g. next available version number) that:
  - Adds column `bearer_token_hash` (e.g. VARCHAR(64) NOT NULL UNIQUE).
  - Drops column `bearer_token`.
  - Updates indexes (e.g. `idx_admin_tokens_active` to use `bearer_token_hash` instead of `bearer_token`).
- Because there are no existing installations, this migration runs on a clean or dev database and simply defines the target schema (hash-only); no data backfill step is required.

---

## 9. Operability: what we lose and what we gain

- **Loss:** You can no longer **read the actual token** from the DB (you only see the hash). In practice, tokens are used from the client (e.g. sessionStorage) or from logs (which should already mask tokens).
- **Operability recovery — Crypto API endpoint:** Add an endpoint to the **Crypto API** that accepts a token in plaintext in the request body and returns the corresponding SHA-256 hash (e.g. hex). This fits the Crypto API’s role as a **testing and debugging** tool with full transparency: developers can paste a bearer token (e.g. from client or logs), get the hash, and use it to look up or verify the corresponding row in `ezkey_admin_tokens` during development or troubleshooting.
- **Postman:** Update the **Crypto API** Postman collection ([postman/collections/v2.1/EZ Key crypto.postman_collection.json](postman/collections/v2.1/EZ Key crypto.postman_collection.json)) with a new request for this "hash token" endpoint so it is easy to use from the existing testing workflow.
- **Unchanged:** Revocation (set `active = false`), expiration, cleanup job, audit fields (ip_address, user_agent, last_used_at), and all existing Admin API behaviors remain the same.

---

## 10. Cryptographic cost

- **SHA-256** of a short string (e.g. 40-byte `ezkey_` + 32 hex chars): **microseconds** per call. One hash per login and one per authenticated request. Cost is negligible compared to the DB round-trip and the rest of the request.
- **HMAC-SHA-256:** Same order of magnitude. No practical reason to prefer HMAC over SHA-256 for this use case; SHA-256 is simpler (no key).

---

## 11. Normative and compliance value

- **SOC2 / NIST:** "Protect sensitive data at rest" and "reduce impact of credential/session disclosure" are standard expectations. Storing only a hash of the bearer token is a clear, auditable control that reduces the sensitivity of the data at rest.
- **Audit narrative:** "Admin bearer tokens are not stored in plaintext; only a one-way hash is stored, so a database breach does not expose usable session tokens." This is easy to explain and aligns with common compliance language.

---

## 12. Summary and recommendation

| Question | Answer |
|----------|--------|
| Is hashing the bearer token (SHA-256) common / best practice? | **Yes** for session/token storage when lookup by derived value is feasible. |
| Do similar projects do it? | Many use opaque tokens; hashing in DB is a common hardening, not always visible. |
| Real added value? | **DB breach:** attacker gets hashes, not usable tokens. |
| Complexity worth it? | **Yes:** small, localized change; no client or protocol change. |
| Cryptographic cost? | **Negligible** (one fast hash per issue and per validation). |
| Operability loss? | **Minimal:** cannot read token from DB; mitigated by Crypto API hash endpoint + Postman. |
| Normative value? | **Yes:** supports "sensitive data at rest" and "reduce session disclosure" for SOC2/NIST. |

**Recommendation:** Implementing **SHA-256(token)** storage for the admin bearer token (and thus recovery tokens in the same table) is **worth doing**: low complexity, clear breach mitigation, and better alignment with normative expectations. Use a **single column** (`bearer_token_hash` only), a **new Flyway migration** (no edit of V2), and add a **Crypto API endpoint** plus **Postman** update for operability.

---

## 13. Implementation outline (for execution)

- **Schema (new Flyway migration):** Add `bearer_token_hash` VARCHAR(64) NOT NULL UNIQUE; drop `bearer_token`; update indexes to use `bearer_token_hash`. No data migration.
- **Entity & repository:** [AdminToken](ezkey-core/src/main/java/org/ezkey/integration/domain/entity/AdminToken.java) uses `bearerTokenHash`; [AdminTokenRepository](ezkey-core/src/main/java/org/ezkey/integration/domain/repository/AdminTokenRepository.java) exposes `findByBearerTokenHashAndActiveTrue(String hash)` (and with-relations variant).
- **Issue (AdminAuthService, AdminRecoveryService):** After generating the token, compute SHA-256 hex, persist only the hash, return plain token to client.
- **Lookup:** Validation service and auth service: hash the incoming token, then call `findByBearerTokenHashAndActiveTrue(hash)`.
- **Crypto API:** New endpoint (e.g. `POST /api/v1/crypto/hash-token`) with request body `{ "token": "ezkey_..." }` and response `{ "tokenHash": "64 hex chars" }`. Implement in [CryptoController](ezkey-crypto-api/src/main/java/org/ezkey/crypto/controller/CryptoController.java); add DTOs; document in Crypto API AGENTS.md/README if needed.
- **Postman:** Add a request to [EZ Key crypto.postman_collection.json](postman/collections/v2.1/EZ Key crypto.postman_collection.json) for the new hash-token endpoint.
- **Tests:** Unit tests for hash generation and lookup; existing integration tests that use bearer tokens should still pass (client sends plain token; server hashes and looks up).

No change to OpenAPI for Admin API, to token format, or to API keys (already BCrypt); only the internal storage and lookup of admin/recovery tokens change, plus the additive Crypto API and Postman updates.
