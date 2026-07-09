## Plan: Auth Attempt Challenge Protection Strategy

Recommended approach: prioritize data minimization over new cryptography for `auth_attempt_challenge` in the short term. In Ezkey’s current model, the challenge has very low entropy (default 2 digits) and a short lifetime (`ttl-seconds=120`). Crucially, the protocol itself neutralizes brute-force risks: the `PENDING`/`RESPOND` exchange consumes a unique cryptographic proof token upon a single verification attempt, making multi-guess brute-forcing natively impossible. Encrypting this field at rest gives limited real security gain but adds schema, re-encryption, and operational complexity. The approved direction for September release is: (1) aggressively reduce retention of challenge values after terminal states, (2) preserve historical semantics with a single enum source field (no persisted boolean), (3) defer column encryption as an optional Phase 2 if compliance/market perception requires it.

**Steps**
1. Phase A — Confirm current exposure and constraints (baseline).
2. Inventory where `auth_attempt_challenge` is persisted, read, logged, and exposed in APIs/admin views; confirm no sensitive logs contain challenge values and confirm `respond` rate-limiting posture in deploy profiles. This anchors the decision in measured exposure, not assumptions.
3. Validate business need for historical challenge visibility in admin/audit views. If there is no operational requirement for post-terminal challenge value access, classify it as minimizable transient data.
4. Phase B — Implement lifecycle minimization and normative historization (approved primary mitigation).
5. Add a terminal-state cleanup policy for `auth_attempt_challenge`: upon reaching a terminal state (`ACCEPTED`, `REJECTED`, `INVALID`, `EXPIRED`), clear the challenge (set it to null). To preserve immediate short-window debugging context, consider externalizing a configurable delay parameter (e.g., `ttl_post_terminal`) allowing deferred execution of this cleanup via a job, provided it avoids accidental complexity. *depends on step 3*
6. Add a persisted enum field `auth_attempt_challenge_requirement_source` as the single historical source of truth with values: `NONE`, `POLICY`, `AD_HOC`, `BOTH`.
7. Populate `auth_attempt_challenge_requirement_source` at attempt creation from effective business conditions (policy flag + ad hoc request), and keep it immutable after creation.
8. Ensure race-safe ordering: If cleanup is synchronous, the clearing strictly must occur in the exact same transaction as the terminal state transition commit to avoid checkout of out-of-sync data. If cleanup is asynchronous (deferred job), this risk is naturally mitigated, but the challenge must firmly remain available until `respond` validation finalizes. *depends on step 5*
9. Do not persist an additional boolean (greenfield context, no compatibility transition needed). If a boolean is needed in API/UI, derive it from enum (`enum != NONE`) at projection layer only.
10. Add/adjust retention docs and operator notes so this is explicit: challenge value is transient runtime material; enum source is durable normative metadata. *parallel with step 8*
11. Phase C — Optional defense-in-depth path (only if policy/marketing/compliance requires it).
12. If encryption is still desired, encrypt `auth_attempt_challenge` using existing entity-listener pattern and include column in `Reencryptable` mapping and re-encryption target query dispatch. Do not introduce custom salts or deterministic schemes for this field; use the existing randomized envelope model for consistency. *depends on step 5 decision outcome*
13. Add migration/backfill handling for existing rows and extend re-encryption batch targets carefully (query counting/fetch paths, admin operational visibility, docs). *depends on step 12*
14. Keep this as a feature flag or staged rollout candidate to avoid coupling September release success to low-yield crypto complexity. *parallel with step 13*
15. Phase D — Decision gate and prioritization.
16. Use a simple decision matrix before implementation: Real risk reduction, operational cost, complexity debt, and perception/commercial value. Recommend Go only if perception/compliance value is explicitly needed for launch messaging or customer due diligence.
17. If no strong external requirement exists: ship Phase B only now, log Phase C as deferred option with trigger conditions (enterprise asks, SOC2 evidence expectations, public scrutiny after open repo launch).

**Relevant files**
- `ezkey-core/src/main/java/org/ezkey/authattempt/domain/entity/AuthAttempt.java` — current plaintext challenge field and encrypted-field mapping (`getEncryptedFields` excludes challenge today).
- `ezkey-core/src/main/java/org/ezkey/authattempt/service/AuthAttemptService.java` — challenge generation and create flow; insertion point for lifecycle minimization rules around final states.
- `ezkey-core/src/main/java/org/ezkey/authattempt/service/AuthAttemptRespondService.java` — challenge validation logic and terminal transitions where safe nullification can be coordinated.
- `ezkey-core/src/main/java/org/ezkey/authattempt/service/AuthAttemptExpiryScheduler.java` — expiry path (`EXPIRED`) that should align with challenge cleanup policy.
- `ezkey-core/src/main/java/org/ezkey/authattempt/domain/AuthAttemptCreateRequest.java` — source inputs (`challengeRequested`) required to compute enum source at creation.
- `ezkey-core/src/main/java/org/ezkey/security/EncryptionEntityListener.java` — existing encryption-at-rest pattern used by auth attempt proof token and enrollment secrets.
- `ezkey-core/src/main/java/org/ezkey/security/ReencryptionTargetQueryService.java` — where new encrypted-column support would expand batch count/fetch logic if Phase C is selected.
- `ezkey-core/src/main/java/org/ezkey/authattempt/domain/repository/AuthAttemptRepository.java` — native queries and re-encryption column handling, including shard-aware behavior.
- `ezkey-core/src/main/resources/db/migration/V4__partitioning_auth_audit_and_function.sql` — authoritative auth-attempt table structure and comments.
- `ezkey-auth-api/src/main/java/org/ezkey/auth/config/RateLimitProperties.java` — confirms online guessing controls assumptions (respond default key strategy per authAttemptId).
- `docs/LIFECYCLE_GOVERNANCE.md` — lifecycle semantics and reversible/irreversible strategy alignment for minimization policy.
- `docs/analysis/key-rotation-batch-strategy.md` — existing principle favoring TTL/purge for ephemeral artifacts.

**Verification**
1. Unit/integration tests: `AuthAttemptService` + `AuthAttemptRespondService` + expiry scheduler paths proving challenge is still validated while attempt is live and cleared only after terminalization.
2. Concurrency verification: simulate READ/respond race to ensure no premature null before challenge comparison.
3. API contract verification: confirm pending/respond behavior unchanged for active attempts; confirm admin list/detail behavior when terminal attempts have null challenge and expose enum source consistently.
4. Re-encryption verification (only if Phase C): count/fetch/reencrypt flows include new column without regressions in shard mode.
5. Security validation: ensure logs and audit payloads do not expose challenge values unnecessarily after policy changes.
6. Enum semantics verification: validate mapping matrix at creation time (`NONE`, `POLICY`, `AD_HOC`, `BOTH`) and ensure derived boolean behavior (`enum != NONE`) where needed.

**Decisions**
- Included now: objective security evaluation, lifecycle-first mitigation path, enum-based historical semantics (`auth_attempt_challenge_requirement_source`) as single source of truth, and explicit defer/go criteria for encryption.
- Included conditionally: schema-level challenge encryption only if external value (compliance/perception) is explicit.
- Excluded for now: adding hash+salt logic for challenge matching; because verification is online and challenge entropy is low, this adds complexity without proportionate benefit in this protocol.
- Excluded by design: persisting a redundant boolean for challenge requirement (greenfield project; no client compatibility bridge required).

**Further Considerations**
1. Launch posture option: Option A (recommended) = lifecycle minimization now, encryption deferred; Option B = implement encryption before September for signaling seriousness in public release narrative.
2. Challenge policy option: keep 2 digits for UX simplicity or raise to 3–4 digits for stronger brute-force margin if product UX tolerates it (lower priority given protocol's 1-attempt limitation).
3. Data retention option: immediate null on terminal transition vs parameterized delayed cleanup job (e.g., via config property) to support short-window debugging without adding undue architectural complexity.
4. Abandoned Attempts (Zombies): The `AuthAttemptExpiryScheduler` must be entirely robust to ensure all naturally expiring attempts (where user takes no action) transition to `EXPIRED` and correctly trigger the challenge cleanup policy, preventing infinite accumulations of plaintext values.