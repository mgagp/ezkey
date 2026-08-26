# Java transactional-boundary assessment (2026-08)

## Mandate

- **Surface:** Java service layer in `ezkey-core`, `ezkey-admin-api`, `ezkey-auth-api`, and
  `ezkey-integration-api` (`src/main`). Tests used for corroboration only.
- **Attention axes:** `@Transactional` placement (public entry points only); self-invocation /
  Spring AOP proxy bypass; nested same-class public-to-public transactional calls; whether
  `*TxHelper` / `TransactionTemplate` usage is a legitimate independent commit or a compensation
  for a service that owns too many responsibilities.
- **Non-goals:** live DAST; `java-doctor-curated` shortlist; rewriting every class-level
  annotation for style; inventing `I-*` / `TB-*` per finding; implementing remediations until HITL.

**Prior inventory:** [`plan/SERVICE_LAYER_SPRING_AUDIT_2026-04.md`](plan/SERVICE_LAYER_SPRING_AUDIT_2026-04.md)
(F-001 resolved; F-002 resolved 2026-08-25 as TX-004). Policy notes:
[`plan/JPA_TRANSACTION_DESIGN_NOTES.md`](plan/JPA_TRANSACTION_DESIGN_NOTES.md).

**HITL lane:**
[`product-docs/global/hygiene/java-transactional-boundaries/`](../product-docs/global/hygiene/java-transactional-boundaries/).

## Why this pass exists

Spring applies `@Transactional` only on calls that go through the proxy. A public transactional
method that calls another public transactional method on `this` does **not** start a second
boundary. The project already documents that rule and introduced `*TxHelper` beans (and one
programmatic `TransactionTemplate`) so a *new* transaction can be opened from inside a public
method without self-invocation.

That helper pattern is **intentional and functional**. The residual question is whether a helper
exists because an independent commit is genuinely required, or because one service still owns
too wide a transaction and would not need the helper if the work lived on another bean.

**One documented exception is out of that challenge by construction** — the auth-attempt
**wait** path (`AuthAttemptWaitService`, `NOT_SUPPORTED`). It is the exception that confirms
the rule. See § *Documented exception: auth-attempt wait* below. Do **not** treat
`AdminAuthAttemptTxHelper`, class-level `@Transactional` on `AdminAuthService`, or
`NOT_SUPPORTED` on the wait bean as a self-invocation smell or an SRP defect.

## Scope and method

- Repository-wide scan of `@Transactional`, `Propagation.REQUIRES_NEW`, `TransactionTemplate`,
  `*TxHelper`, `this::`, `@PostConstruct` + `@Transactional`, and class-level annotations.
- Manual review of the largest / historically risky services (`KeyRotationService`,
  `ReencryptionService`, `AdminAuthService`, `AdminBootstrapService`, enrollment / auth-attempt
  helpers).
- Integration API has **no** local `@Transactional` (delegates into core). That is expected.
- Static white-box only. No runtime transaction tracing in this pass.

## Executive summary

Most CRUD and protocol services are in good shape: method-level `@Transactional` on public
entries, private helpers without annotations, and cross-bean calls for isolated commits.

The **canonical self-invocation antipattern is concentrated in `KeyRotationService`**: several
public `@Transactional` methods call other public `@Transactional` methods on `this`. Current
propagation is `REQUIRED`, so today's behavior is still one outer transaction — the inner
annotations are **dead when those paths run internally**. The risk is future: a later
`REQUIRES_NEW` (or a reader who trusts the annotation) would not apply.

A second KeyRotation issue is the same class as the 2026-04 F-001 bootstrap defect:
`@PostConstruct` + `@Transactional` on `initializeKeysetSync()` is a Spring no-op (init runs on
the raw target, not the proxy). Startup keyset-to-database sync is therefore not guaranteed to
run in one transaction.

`*TxHelper` review:

| Bean | Verdict |
| --- | --- |
| `EnrollmentTxHelper` | **Keep.** Isolated INVALID / EXPIRED commit before the parent rolls back. Bind and verify are already separate services. |
| `AuthAttemptTxHelper` | **Keep.** Same isolated INVALID commit on respond. |
| `AdminAuthAttemptTxHelper` | **Keep — wait exception.** `REQUIRES_NEW` so the pending row is committed before `AuthAttemptWaitService` (`NOT_SUPPORTED`) polls for the device respond. Not an SRP compensation. |
| `AuditLogService` `TransactionTemplate` | **Keep.** Documented fail-open: programmatic `REQUIRES_NEW` so an inner rollback cannot escape as `UnexpectedRollbackException`. |
| Re-encryption creation / processing / row persistence | **Keep as collaborators.** Already split. The leftover smell is the **orchestrator** still declaring `@Transactional` on the scheduled entry. |

`AdminBootstrapService.doBootstrapAdminMfa()` still carries a **private** `@Transactional`
(April F-002). The public entry is transactional, so behavior is correct; the inner annotation
is misleading.

## Findings register (HITL lot)

| ID | Title | Severity | Confidence | Quick win | Disposition |
| --- | --- | --- | --- | --- | --- |
| TX-001 | `KeyRotationService` public-to-public self-invocation | P2 | High | Yes — drop dead inner annotations **or** extract a collaborator | **implemented** (2026-08-25; honest annotations) |
| TX-002 | `@PostConstruct` + `@Transactional` on keyset startup sync | P2 | High | Yes — move to `ApplicationReadyEvent` public entry (F-001 pattern) | **implemented** (2026-08-25; ReadyEvent TX + slim test) |
| TX-003 | `ReencryptionService` scheduled orchestrator holds an outer `REQUIRED` TX | P2 | High | Yes — remove orchestrator annotation (siblings already have none) | **implemented** (2026-08-25; outer TX dropped) |
| TX-004 | `AdminBootstrapService` private `@Transactional` leftover (F-002) | P3 | High | Yes — delete the private annotation | **implemented** (2026-08-25; private annotation dropped) |

**Not a finding:** auth-attempt wait (`AuthAttemptWaitService` + `AdminAuthAttemptTxHelper` +
Admin/Integration wait callers). Documented exception; April F-005 “review AdminAuthService
class-level TX” is closed as **intentional**, not deferred.

## Documented exception: auth-attempt wait

**This is the exception that confirms the rule.** A later reader or agent must not re-open it
as “self-invocation,” “nested `@Transactional`,” or “TxHelper hiding a god-service.”

The wait use case is: a caller has created a **PENDING** auth attempt; the device will
`respond` on Auth API; the caller **polls the database** until that row is `ACCEPTED` /
`REJECTED` / `INVALID` / `EXPIRED` (or timeout). Holding a JPA transaction open across that
poll would pin a connection for the whole TTL. The designed answer is:

1. **Commit the pending row first** (so a wait with no surrounding transaction can see it).
2. **`AuthAttemptWaitService`** — class-level `@Transactional(propagation = NOT_SUPPORTED)`.
   Each poll is a short repository read; nothing is held between iterations.
3. **Device `respond`** runs in its own transaction on another bean
   (`AuthAttemptRespondService`). Wait observes that commit; it does not join it.

Call sites (all go through `AuthAttemptService.waitForResponse` → the wait bean):

| Caller | Role |
| --- | --- |
| Integration API `GET /api/v1/auth-attempts/{id}/wait` | Integrator wait |
| Admin API auth-attempt wait | Operator / test wait |
| `AdminAuthService` blocking login and `/passwordless-wait` | Admin passwordless |

`AdminAuthAttemptTxHelper.createAuthAttempt` (`REQUIRES_NEW`) is the **companion**, not a
workaround for a missing class split. `AdminAuthService` is class-level `@Transactional`.
Without an independent commit, `NOT_SUPPORTED` would suspend that parent TX and the wait
could not see the uncommitted pending row. The helper is a separate bean so Spring AOP
applies — the supported alternative to calling another public method on `this`.

April inventory F-005 already called this “intentional; not a defect.” This pass **confirms**
that verdict and takes the wait triad out of the HITL lot so it cannot raise a false signal.

**What this exception does *not* license:** copying `NOT_SUPPORTED` or a `*TxHelper` onto
unrelated facades (schedulers, CRUD, key rotation) “because wait does it.” Wait is
long-lived DB polling for a *foreign* commit. Other long methods should drop an unnecessary
outer `REQUIRED` or split work — they should not impersonate wait.

## Reviewed and sound (not in the HITL lot)

These were checked and do **not** need a finding this pass.

### Independent-commit helpers that earn their keep

- **`EnrollmentTxHelper`** / **`AuthAttemptTxHelper`:** `REQUIRES_NEW` so INVALID / EXPIRED
  persists even when the parent bind/verify/respond transaction rolls back. That is a real
  isolation need, not a missing class split. Bind, verify, pending, and respond are already
  separate beans.
- **`AuditLogService.log()`:** programmatic `TransactionTemplate` (`PROPAGATION_REQUIRES_NEW`)
  with internal catch. Declarative `@Transactional(REQUIRES_NEW)` would let
  `UnexpectedRollbackException` escape after the method body. Fail-open for the business path;
  audit failure stays observable in logs.
- **Re-encryption row / batch beans:** `ReencryptionBatchCreationService`,
  `ReencryptionBatchProcessingService`, `ReencryptionRowPersistenceService` are the intended
  cross-bean `REQUIRES_NEW` split (see `docs/REENCRYPTION_OPERATIONS.md`).
- **`ScheduledJobLastRunService`:** `REQUIRES_NEW` last-run rows. Narrow, named, appropriate.
- **Auth-attempt wait triad:** see § *Documented exception* (not a finding).

### Placement that matches the policy

- **F-001 is still closed.** `InitialGlobalAdminService.initializeGlobalAdmin()` is the public
  transactional entry; `this::doInitializeGlobalAdmin` runs inside that boundary. Regression:
  `InitialGlobalAdminServiceTransactionBoundaryTest`.
- **No other private `@Transactional`** besides TX-004.
- **No `@Lazy` self-injection** of a proxied service (still true since April).
- **Method-level `@Transactional`** on the large CRUD facades (`AdminProvisioningService`,
  `TenantService`, `ApiKeyService`, `IntegrationService`, `EnrollmentRevocationService`,
  `AlertService`, `AuditLifecycleService`, `AuthAttemptService` create/update/cancel). Public
  methods call **private** helpers, not other public transactional methods.
- **Class-level `@Transactional` on protocol services** (`EnrollmentBindService`,
  `EnrollmentVerifyService`, `AuthAttemptPendingService`, `AuthAttemptRespondService`) is
  coherent: one use-case per class, public API is the transaction.
- **`this::` in controllers / rate-limit / dashboard `CompletableFuture`** is mapping or
  non-transactional setup, not a service proxy issue.
- **`DashboardService` `this::buildAlerts` via `CompletableFuture`:** not a `@Transactional`
  self-invocation. Out of scope unless a later pass audits async transaction context.

### Class-level annotations that are broad but not defective

| Type | Notes |
| --- | --- |
| `AdminAuthService` | Broad **by design** with the wait exception (F-005 closed). |
| `AdminRecoveryService` | Class-level plus a redundant method-level `deactivateRecoveryToken`. `generateRecoveryCodes()` is public and CPU-only — opens a needless TX when called through the proxy. Hygiene only; not a correctness bug. |
| Protocol bind / verify / pending / respond | Appropriate (one use case). |

## Finding details

### TX-001 — `KeyRotationService` public-to-public self-invocation

**Verdict:** Several public `@Transactional` methods call other public `@Transactional` methods
on the same instance. Spring AOP does not apply the inner annotation.

**Evidence:**

| Caller (public, `@Transactional`) | Callee (public, `@Transactional`) |
| --- | --- |
| `introduceNewKey(String)` | `introduceNewKey(String, boolean)` |
| `checkAndPromotePendingKeys()` | `promotePendingToPrimary(...)` |
| `checkAndRotate()` | `introduceNewKey(String)` and `cleanupOldKeys()` |

`isRotationDue()` is also public and called from `checkAndRotate()`, but it is **not**
annotated — no dead boundary there.

**Why it matters here:** All of these methods use default `REQUIRED`. Self-invocation therefore
still runs in the **outer** transaction. Today's rotation/promotion/cleanup atomicity is
probably what the authors intended. The annotations on the callees only apply when **another
bean** calls them (Admin API manual rotate, tests calling `introduceNewKey` on the Spring
proxy). A later edit that adds `REQUIRES_NEW` to `promotePendingToPrimary` (per-key isolation)
or that assumes `cleanupOldKeys` commits independently of a failed `introduceNewKey` would be
silently wrong on the scheduled path.

**Observation scenario:** Scheduled promotion (`checkAndPromotePendingKeys`) loops
`promotePendingToPrimary`. A reader sees two transactional methods and expects one TX per key.
The loop shares the scheduler transaction.

**Options:**

| Option | What it means |
| --- | --- |
| **fix — extract collaborator** | Move `promotePendingToPrimary` / `introduceNewKey` / `cleanupOldKeys` onto a narrow `KeyRotationCommandService` (or similar). Scheduler and HTTP both call through the proxy. Matches the re-encryption split. |
| **fix — honest annotations** | Keep one class. Leave `@Transactional` only on true entry points (`checkAndPromotePendingKeys`, `checkAndRotate`, `introduceNewKey(String, boolean)` as the HTTP entry). Make the one-arg overload and `cleanupOldKeys` package-private / unannotated if they are only internal, **or** document that scheduled paths share the outer TX. |
| **defer** | Behavior is correct today; revisit when KeyRotation is next touched. |
| **suppress** | Accept dead inner annotations as documentation of “this method is also an HTTP entry.” |

**Proportionate tests if fix:**

- Keep existing `KeyRotationServiceIntegrationTest` (proxy calls to `introduceNewKey`).
- If extracting a collaborator: one slim Spring test (same shape as
  `InitialGlobalAdminServiceTransactionBoundaryTest`) that a **scheduler-style** caller sees an
  active transaction inside `promotePendingToPrimary` / `introduceNewKey` when invoked from
  another bean — not a second full rotation suite.
- Do **not** add a test that only reflects on annotations.

### TX-002 — `@PostConstruct` + `@Transactional` on keyset startup sync

**Verdict:** `initializeKeysetSync()` is annotated `@PostConstruct` and `@Transactional`.
Spring invokes `@PostConstruct` on the raw target **before** the transactional proxy is
applied. The annotation does not run for startup.

**Evidence:** `KeyRotationService` lines 116–146. On empty `ezkey_encryption_key` with a loaded
keyset, it calls private `syncAllKeysFromKeyset`, which demotes existing PRIMARY rows and
`save`s each keyset id, then verifies “exactly one PRIMARY.”

**Why it matters here:** Same defect class as F-001 (private / non-proxied transactional
boundary). Without a wrapping transaction, each `save` auto-commits. A failure mid-loop can
leave a partial keyset metadata copy. The method already logs a CRITICAL inconsistency and
swallows `DataAccessException` so the app still starts — fail-open for boot, fail-closed for
*atomicity of the sync itself*.

This path is **rare** (keyset file present, table empty: reset / migration). It is still the
wrong boundary for encryption-key metadata.

**Observation scenario:** Operator resets the database but keeps the keyset volume. Startup
sync writes N keys. Process dies after k saves. Next boot sees a non-empty table and **skips**
sync (`keyCount == 0` gate), leaving a partial catalog.

**Options:**

| Option | What it means |
| --- | --- |
| **fix** | Move sync to a public `@EventListener(ApplicationReadyEvent)` (or equivalent) that is `@Transactional`, same pattern as `InitialGlobalAdminService`. Keep `@PostConstruct` off the transactional method. |
| **defer** | Rare path; accept until the next KeyRotation touch (pair with TX-001). |
| **suppress** | Accept auto-commit-per-row for a one-time empty-table sync. |

**Proportionate tests if fix:**

- Mirror `InitialGlobalAdminServiceTransactionBoundaryTest`: assert
  `TransactionSynchronizationManager.isActualTransactionActive()` inside the sync work when
  the **public** entry runs.
- Do not re-test Tink keyset loading.

### TX-003 — `ReencryptionService` scheduled orchestrator holds an outer `REQUIRED` TX

**Verdict:** `processReencryptionBatches()` is `@Transactional` and then loops / parallelizes
work that already runs in **other beans** with `REQUIRES_NEW`. Sibling public methods on the
same class (`enqueueFullReencryption`, `createBatchesForOldKeys`, deprecated
`triggerFullReencryption`) have **no** class or method annotation. The scheduled entry is the
inconsistent one.

**Why it matters here:** An outer `REQUIRED` transaction around a scheduled run that can last
up to `maxDurationMinutes` / many batches can hold a persistence context (and possibly a
connection) for the whole orchestration, even though each batch already commits in
`ReencryptionBatchProcessingService`. `createBatchesForOldKeys()` is `REQUIRES_NEW` **after**
the orchestrator already loaded `batchesToProcess` — new batches are intentionally not
processed in the same tick, but they are created while the outer TX is still open.

This is not self-invocation (children are other beans). It is an **unnecessary outer
boundary** on a facade that should orchestrate, not own a unit of work.

**Observation scenario:** Nightly `REENCRYPTION` job. Outer TX starts; eligible batches loaded;
`createBatchesForOldKeys` commits in a nested TX; `processBatchInternal` commits per batch;
outer TX still open until the scheduled method returns.

**Options:**

| Option | What it means |
| --- | --- |
| **fix** | Remove `@Transactional` from `processReencryptionBatches`. Align with `enqueue*` methods. Do **not** copy wait `NOT_SUPPORTED` here — this is not DB polling for a foreign commit. |
| **defer** | Children already isolate row work; outer TX is wasteful, not a known data bug. |
| **suppress** | Keep if a specific read in the scheduled method must be transactional (none identified). |

**Proportionate tests if fix:**

- Existing `ReencryptionServiceIntegrationTest` / elective concurrent tests.
- No new transaction-proxy test unless a regression once held an outer TX for visibility
  reasons (none documented).

### TX-004 — `AdminBootstrapService` private `@Transactional` leftover (F-002)

**Verdict:** Public `bootstrapAdminMfa()` is `@Transactional` and passes
`this::doBootstrapAdminMfa` to `LockingTaskExecutor`. The private method is also
`@Transactional`. Spring never applies the inner annotation. Behavior is correct because the
**public** entry owns the TX (same fix direction as F-001). The private annotation is
misleading.

**Options:** **fix** (delete private annotation + one-line Javadoc that only the public entry
applies) / **defer** / **suppress** (comment that the annotation is decorative).

**Proportionate tests if fix:** None required. Do not clone
`InitialGlobalAdminServiceTransactionBoundaryTest` unless bootstrap is being reworked for
another reason.

## Inventory appendix

### `REQUIRES_NEW` / programmatic new-TX (2026-08)

| Location | Mechanism | Role |
| --- | --- | --- |
| `EnrollmentTxHelper` | `@Transactional(REQUIRES_NEW)` | Isolated enrollment INVALID / EXPIRED |
| `AuthAttemptTxHelper` | `@Transactional(REQUIRES_NEW)` | Isolated auth-attempt INVALID / REJECTED |
| `AdminAuthAttemptTxHelper` | `@Transactional(REQUIRES_NEW)` | Commit pending attempt before wait (documented exception) |
| `AuditLogService` | `TransactionTemplate` | Fail-open audit insert + HMAC seal |
| `ReencryptionBatchCreationService` | `@Transactional(REQUIRES_NEW)` | Commit batch rows before processing |
| `ReencryptionBatchProcessingService` | `@Transactional(REQUIRES_NEW)` | One batch per TX |
| `ReencryptionRowPersistenceService` | `@Transactional(REQUIRES_NEW)` | Per-row flush isolation |
| `ScheduledJobLastRunService` | `@Transactional(REQUIRES_NEW)` | Job last-run row |

### Class-level `@Transactional` (main sources)

`AdminAuthService`, `AdminRecoveryService`, `EnrollmentBindService`,
`EnrollmentVerifyService`, `AuthAttemptPendingService`, `AuthAttemptRespondService`,
`AuthAttemptWaitService` (`NOT_SUPPORTED`).

### Self-invocation / `this::` that matter for transactions

| Location | Transactional? |
| --- | --- |
| `InitialGlobalAdminService` → `this::doInitializeGlobalAdmin` | Yes — public entry (F-001 closed) |
| `AdminBootstrapService` → `this::doBootstrapAdminMfa` | Yes — public entry only (TX-004 implemented) |
| `KeyRotationService` public-to-public calls | Yes — inner annotations dropped (TX-001 implemented) |
| Controllers `map(this::…)` | No |
| `RateLimitFilter` `this::createBucket` | No |
| `DashboardService` `this::buildAlerts` | Async; not a TX finding this pass |

## Hygiene vs program

This is a **hygiene** pass. TX-001 through TX-004 shipped on
`hygiene/java-transactional-boundaries-2026-08`. Do **not** promote the wait exception to
`I-*` / `TB-*`. April F-005 stays closed.
