# Service layer Spring audit — Ezkey backend

**Date:** 2026-04-14  
**Scope:** `ezkey-core`, `ezkey-admin-api`, `ezkey-auth-api`, `ezkey-integration-api` (`**/src/main/java/**`, focus on `**/service/**`).  
**Methodology:** Repository-wide `rg`/IDE-style grep for self-injection (`@Lazy`, self-typed constructor params), `this::` method references (executors, bootstrap), `ApplicationContext` / `getBean`, `@Transactional` usage; manual review of `*TxHelper` and bootstrap paths; **line-count outliers** for `**/service/**` (main sources only). **Cyclomatic complexity:** PMD is not bound in the root `pom.xml`; per-method complexity was **not** machine-generated—use IDE analysis or a one-off PMD run in a follow-up if numeric CC is required.

**Related plan:** Spring service anti-patterns and complexity inventory (Cursor plan `spring_service_anti-patterns_audit_f99c5953`).

---

## 1. Purpose, scope, and methodology

### Purpose

Baseline the codebase for Spring-specific service-layer risks: proxy bypass (`this::`, self-invocation), misleading `@Transactional`, unnecessary nested boundaries, service-locator patterns, and pragmatic review of `REQUIRES_NEW` helpers. Produce **sectioned findings** so follow-on work can target **Section 2**, **3**, or **4** independently.

### Scope

| Module | Included |
|--------|----------|
| ezkey-core | Yes |
| ezkey-admin-api | Yes |
| ezkey-auth-api | Yes |
| ezkey-integration-api | Yes |
| ezkey-demo-app-acme | Grep hits only (non-production) |
| ezkey-crypto-api | Not exhaustively scanned |

### Signals used

- `@Lazy`, constructor self-type, `this::` in services and `LockingTaskExecutor`
- `ApplicationContext`, `getBean`, `getBeanProvider`
- `@Transactional` distribution (class-level vs method-level)
- `Propagation.REQUIRES_NEW` and `*TxHelper` / `*RowPersistence` beans
- Line counts: PowerShell `Measure-Object -Line` on `**/service/**/*.java` under main sources

### What this document does not do

- No code changes were made as part of producing this audit.
- No mandatory PMD/Sonar baseline was added to CI.

---

## 2. Findings: proxy / self-invocation / transactional boundaries

Findings use ID **F-xxx** for cross-reference in Section 5. Each subsection below is one finding, written for linear reading.

### 2.1 F-001 — Private `@Transactional` + `this::` bypass

**ID:** F-001

**Pattern:** Private `@Transactional` method invoked via `this::` inside `LockingTaskExecutor`, while the public entry point does not declare a transaction.

**Location:** `InitialGlobalAdminService`: `initializeGlobalAdmin()` calls `LockingTaskExecutor.executeWithLock(..., this::doInitializeGlobalAdmin)`. The method `doInitializeGlobalAdmin()` is **private** and annotated with `@Transactional`.

**Severity:** High

**Suggested remediation:** Move `@Transactional` to the **public** entry (`initializeGlobalAdmin`) so the locked task runs in one Spring-managed transaction. Remove the redundant annotation from the private method, or extract the body into another bean if that clarifies boundaries.

**Effort:** M

**Notes:** Spring does not apply `@Transactional` to **private** methods in the usual proxy setup. The callback uses **self-invocation**, so the proxy is never involved. The **public** listener method has **no** `@Transactional`. Risk: fragmented persistence sessions and lack of a single transactional boundary for initial global admin setup (same class of issue as bootstrap notes in `JPA_TRANSACTION_DESIGN_NOTES.md`).

**Status:** Resolved (2026-04-14). `@Transactional` is on the public `initializeGlobalAdmin()` entry; the private initializer no longer carries a misleading annotation. Regression coverage: `InitialGlobalAdminServiceTransactionBoundaryTest` in `ezkey-admin-api`.

---

### 2.2 F-002 — Redundant / misleading `@Transactional` on callee

**ID:** F-002

**Pattern:** Redundant or misleading `@Transactional` on a private method that is only invoked through `this::` from a transactional public method.

**Location:** `AdminBootstrapService`: public `bootstrapAdminMfa()` is `@Transactional` and passes `this::doBootstrapAdminMfa` to the lock executor. The method `doBootstrapAdminMfa()` is **private** and also annotated `@Transactional`.

**Severity:** Low

**Suggested remediation:** **Keep** the outer transaction on `bootstrapAdminMfa()` (per `ezkey-admin-api/AGENTS.md`). Remove `@Transactional` from `doBootstrapAdminMfa`, **or** add a short comment that only the outer boundary applies; the inner annotation is misleading.

**Effort:** S

**Notes:** Behavior is likely correct because the **public** method is transactional and `LockingTaskExecutor` runs the runnable **in the same thread** inside that call. The inner `@Transactional` does not apply via the proxy.

**Status:** Resolved (2026-08-25) as hygiene TX-004. Private `doBootstrapAdminMfa()` no longer carries `@Transactional`. Public `bootstrapAdminMfa()` remains the Spring transaction boundary.

---

### 2.3 F-003 — `this::` in controllers (mapping)

**ID:** F-003

**Pattern:** Method references `this::…` used in `map(...)` for DTO mapping.

**Location:** `ApiKeyController`, `EncryptionKeyController` — e.g. `map(this::mapToResponseDto)`.

**Severity:** Info

**Suggested remediation:** None required for transaction semantics.

**Effort:** —

**Notes:** Controllers are not the transactional boundary for these flows. No Spring proxy issue for business `@Transactional` on services.

---

### 2.4 F-004 — `this::` non-transactional (rate limit)

**ID:** F-004

**Pattern:** Method reference used for non-transactional setup.

**Location:** `RateLimitFilter` — e.g. `this::createBucket` for bucket creation.

**Severity:** Info

**Suggested remediation:** None.

**Effort:** —

**Notes:** Not a service-layer transaction concern.

---

### 2.5 F-005 — Class-level `@Transactional`

**ID:** F-005

**Pattern:** Class-level `@Transactional` on a service that calls other services and helpers.

**Location:** `AdminAuthService` — `@Transactional` on the class.

**Severity:** Medium (review)

**Suggested remediation:** Periodically review the call graph for nested service `@Transactional` and `REQUIRES_NEW` helpers (`AdminAuthAttemptTxHelper`). The passwordless flow is intentional; avoid adding more layers without need.

**Effort:** M

**Notes:** Documented design in `AdminAuthAttemptTxHelper`; not a defect by itself.

**Status:** Closed as intentional (2026-08 transactional-boundary assessment). The wait triad
(`AdminAuthService` class-level TX → helper `REQUIRES_NEW` → `AuthAttemptWaitService`
`NOT_SUPPORTED`) is the documented exception that confirms the proxy rule. Do not re-open as
an SRP / nested-transaction finding. Canon:
[`../java-transactional-boundaries-assessment-2026-08.md`](../java-transactional-boundaries-assessment-2026-08.md)
§ *Documented exception: auth-attempt wait*.

---

### 2.6 F-006 — No self-injection of proxied service

**ID:** F-006

**Pattern:** `@Lazy` injection of same bean type or constructor self-reference (anti-pattern inventory).

**Location:** Repository-wide search for `@Lazy` and self-typed constructor parameters.

**Severity:** Info

**Suggested remediation:** None.

**Effort:** —

**Notes:** **No** `@Lazy` self-injection found in this scan.

---

### 2.7 F-007 — `ApplicationContext` / `getBean` (infrastructure exception)

**ID:** F-007

**Pattern:** `ApplicationContextAware` and `getBeanProvider` to resolve a bean outside normal injection.

**Location:** `EncryptionEntityListener` — implements `ApplicationContextAware`, uses `getBeanProvider(EncryptionService.class)`.

**Severity:** Info (documented exception)

**Suggested remediation:** Keep as the established infrastructure pattern for JPA entity listeners.

**Effort:** —

**Notes:** Entity listeners are not standard Spring-managed beans in the same way; context lookup is a **known workaround**. Described in the class Javadoc.

---

### 2.8 F-008 — `getBean` in demo application

**ID:** F-008

**Pattern:** Direct `ApplicationContext.getBean` usage.

**Location:** `DemoAcmeApplication` (demo module).

**Severity:** Info

**Suggested remediation:** Out of scope for core Ezkey service hardening.

**Effort:** —

**Notes:** Demo-only code.

---


## 3. Findings: `*TxHelper` and `REQUIRES_NEW`-focused beans (critical lens)

| Bean / service | Role | Triage | Rationale |
|------------------|------|--------|-----------|
| `EnrollmentTxHelper` | `REQUIRES_NEW` for invalid/expired enrollment updates + audit | **Keep as-is** | Cohesive, small, well-named; aligns with enrollment bind/verify flows. |
| `AuthAttemptTxHelper` | `REQUIRES_NEW` to persist INVALID (or related) status before outer failure | **Keep as-is** | Clear single concern; mirrors enrollment helper pattern. |
| `AdminAuthAttemptTxHelper` | `REQUIRES_NEW` around `AuthAttemptService.create` so attempt is committed before `NOT_SUPPORTED` wait | **Keep as-is** | Thin; Javadoc explains interaction with `AuthAttemptWaitService` — intentional design, not a dumping ground. |
| `ReencryptionRowPersistenceService` | Per-row `REQUIRES_NEW` flush isolation | **Keep as-is** | Infrastructure boundary for re-encryption safety. |
| `ReencryptionBatchProcessingService` | Batch step in `REQUIRES_NEW` | **Keep as-is** | Matches batch lifecycle. |
| `ReencryptionBatchCreationService` | Batch creation in `REQUIRES_NEW` | **Keep as-is** | Matches batch lifecycle. |
| `AuditLogService` | Programmatic `TransactionTemplate` with `REQUIRES_NEW` (not declarative) | **Keep as-is** | Documented **intentional** deviation: avoids `UnexpectedRollbackException` escaping after internal catch; high cohesion for audit safety. |

**Optional follow-up (low priority):** If `AdminAuthAttemptTxHelper` ever grows beyond delegating `create`, revisit whether a **named use-case service** would read clearer than “helper + `AuthAttemptService`”.

---

## 4. Findings: complexity and size outliers

**Metric:** Physical line count (`Measure-Object -Line`) on `**/service/**` under `src/main/java` only (tests excluded).

### Largest main-source service files (approximate)

| Lines (approx.) | File |
|-----------------|------|
| 1098 | `ezkey-core/.../security/KeyRotationService.java` |
| 901 | `ezkey-admin-api/.../admin/service/AdminProvisioningService.java` |
| 666 | `ezkey-admin-api/.../admin/service/EnrollmentRevocationService.java` |
| 620 | `ezkey-core/.../authattempt/service/AuthAttemptService.java` |
| 603 | `ezkey-core/.../integration/service/ApiKeyService.java` |
| 570 | `ezkey-admin-api/.../admin/service/AdminAuthService.java` |
| 534 | `ezkey-admin-api/.../admin/service/AdminBootstrapService.java` |
| 509 | `ezkey-core/.../enrollment/service/EnrollmentService.java` |
| 442 | `ezkey-core/.../enrollment/service/EnrollmentVerifyService.java` |
| 384 | `ezkey-core/.../audit/service/AuditLogService.java` |
| 366 | `ezkey-core/.../authattempt/service/AuthAttemptPendingService.java` |
| 355 | `ezkey-core/.../authattempt/service/AuthAttemptRespondService.java` |

**Prior art:** `docs/plan/SPLIT_AUTH_ATTEMPT_SERVICE.md` documents historical complexity drivers for `AuthAttempt*` before splitting.

### Cyclomatic complexity

- **Not automated** in this repo’s Maven build for this audit.
- **Recommendation:** Run IDE “Cyclomatic Complexity” or a **one-off** `maven-pmd-plugin` with `CyclomaticComplexity` on `**/service/**` when a follow-on session needs numeric thresholds.

---

## 5. Prioritized backlog (cross-reference)

| Priority | ID(s) | Summary | Suggested follow-on session |
|----------|-------|---------|-----------------------------|
| — | **F-001 (§2.1) — done** | ~~Fix `InitialGlobalAdminService` transactional boundary~~ **Resolved 2026-04-14** (see §2.1 Status) | — |
| — | **F-002 (§2.2) — done as TX-004** | ~~Remove private `@Transactional` on `doBootstrapAdminMfa`~~ **Resolved 2026-08-25** (see §2.2 Status) | — |
| — | **F-005 (§2.5) — closed 2026-08** | Wait triad is the documented exception | [`../java-transactional-boundaries-assessment-2026-08.md`](../java-transactional-boundaries-assessment-2026-08.md) |
| — | — | Size/complexity outliers (Section 4) | Schedule refactors by module need; not blocking this audit |

---

## 6. Appendix: raw grep / command notes

### Search commands (examples)

```text
rg "@Lazy" --glob "*.java"
rg "this::" --glob "*.java"
rg "ApplicationContext|getBean\\(" --glob "*.java"
rg "LockingTaskExecutor" --glob "*.java"
rg "TxHelper|REQUIRES_NEW" --glob "*.java"
```

### `this::` hits (full list, Java)

- `InitialGlobalAdminService` — `this::doInitializeGlobalAdmin` (see F-001)
- `AdminBootstrapService` — `this::doBootstrapAdminMfa` (see F-002)
- `ApiKeyController` / `EncryptionKeyController` — `map(this::...)` (F-003)
- `RateLimitFilter` — `this::createBucket` (F-004)

### Line-count script

PowerShell: `Get-ChildItem -Recurse ... | Where-Object { $_.FullName -match '\\service\\' } | ForEach-Object { Lines = (Get-Content ... | Measure-Object -Line).Lines }`

---

*End of audit document.*
