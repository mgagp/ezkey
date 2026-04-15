# SQL injection posture audit (Ezkey)

**Scope:** Java backend modules that persist to PostgreSQL via JPA/Hibernate (`ezkey-core`, `ezkey-admin-api` JDBC helpers).  
**Method:** Repository-wide grep inventory, manual review of every `*Repository.java` in `ezkey-core`, `Specification`/Criteria services used by Admin list APIs, and programmatic SQL (`EntityManager.createNativeQuery`, `JdbcTemplate`).  
**Date:** 2026-04-15

## Principles (confirmed)

| Layer | Role |
|-------|------|
| **Parameterized queries** | Primary control: bind all variable fragments (`:name` in Spring Data / Hibernate, JDBC prepared parameters). |
| **Input validation** (Jakarta Validation, enums, tenant scoping) | Defense in depth; does **not** replace binding. |

Ezkey does not use MyBatis XML with `${}` substitution. Static literals in JPQL (e.g. `k.keyStatus = 'PENDING'`) refer to fixed enum/status values, not request strings.

---

## Phase 1 — Machine inventory (production `src/main/java`)

| Pattern | Result |
|---------|--------|
| `@NativeQuery` | [`AuthAttemptRepository`](../../ezkey-core/src/main/java/org/ezkey/authattempt/domain/repository/AuthAttemptRepository.java), [`EnrollmentRepository`](../../ezkey-core/src/main/java/org/ezkey/enrollment/domain/repository/EnrollmentRepository.java) |
| `@Query(… nativeQuery = true)` | [`IntegrationRepository`](../../ezkey-core/src/main/java/org/ezkey/integration/domain/repository/IntegrationRepository.java), [`EzkeyAdminRepository`](../../ezkey-core/src/main/java/org/ezkey/integration/domain/repository/EzkeyAdminRepository.java) |
| `createNativeQuery` | [`PartitionSchedulerService`](../../ezkey-core/src/main/java/org/ezkey/database/service/PartitionSchedulerService.java) only |
| `JdbcTemplate` | [`ShedLockHealthIndicator`](../../ezkey-admin-api/src/main/java/org/ezkey/admin/config/ShedLockHealthIndicator.java) (constant SQL), [`ShedLockConfiguration`](../../ezkey-admin-api/src/main/java/org/ezkey/admin/config/ShedLockConfiguration.java) (library lock provider + fixed table name `ezkey_shedlock`) |
| String-built `SELECT` / `String.format` SQL in `src/main/java` | **No matches** |

`ezkey-auth-api` and `ezkey-integration-api` main sources: **no** `createNativeQuery`, `@NativeQuery`, or `JdbcTemplate` (persistence lives in `ezkey-core`).

---

## Phase 2 — Repository spot-check (all 12 `ezkey-core` repositories)

| Repository | Custom SQL | Binding / notes |
|------------|------------|-----------------|
| `AuthAttemptRepository` | JPQL + `@NativeQuery` (locking, re-encryption `LIKE :prefix`, `IN (:… )`) | All dynamic fragments use named parameters. `String status` in native locking queries is bound; callers pass attempt status (controlled values). |
| `EnrollmentRepository` | JPQL + `@NativeQuery` (locking, re-encryption `LIKE :prefix`) | Same: `:enrollmentId`, `:prefix`, `:lastId`, `:limit` bound. |
| `IntegrationRepository` | JPQL + one native scalar for tenant info | `:id` only. |
| `EzkeyAdminRepository` | JPQL + one native scalar for tenant info | `:enrollmentId` only. |
| `ApiKeyRepository` | JPQL | Bound parameters only. |
| `AdminTokenRepository` | JPQL | Bound parameters only. |
| `TenantRepository` | Derived query methods only | No custom `@Query`. |
| `AuditLogRepository` | JPQL `DELETE … :cutoffDate` | Bound. |
| `AuditChainCheckpointRepository` | JPQL | Bound (`:from`, `:to`, `:before`, etc.) or parameterless fixed queries. |
| `EncryptionKeyRepository` | JPQL | Literals are fixed key-status strings; dynamic parts use `:cutoffDate`, `:now`, `:status`. |
| `KeysetBlobRepository` | JPQL fixed `id = 1` | No external input. |
| `ReencryptionBatchRepository` | JPQL | `:targetTable`, `:targetColumn`, key/shard params bound. Status literals in JPQL are fixed. |

**IN lists:** `IN (:statuses)` / `IN (:authAttemptIds)` are expanded by Spring Data with bound parameters, not string-concatenated id lists.

---

## Phase 3 — Services: `Specification`, Criteria, `Pageable`

| Service | Mechanism | Finding |
|---------|-----------|---------|
| `EnrollmentService#findByFilters` | JPA `Specification` | Predicates via `cb.equal`, `cb.like` with pattern `"%" + enrollmentName.toLowerCase() + "%"`. Value is a **single bound parameter** in generated SQL — not classic SQLi. Optional **LIKE wildcard** behavior (`%`, `_`) in search text is a separate product concern (see risk register). |
| `IntegrationService#findByFilters` | Same pattern for `integrationName` | Same as above. |
| `AuthAttemptService#findByFilters` | `Specification` + default `orderBy` when sort unsorted | Predicate building uses typed fields only. |
| `AuditLogService#findByFilters` | `Specification` + explicit `orderBy` when `pageable.getSort().isUnsorted()` | Tenant and filter predicates use bound fields. Context slices use fixed `Sort.by(...)` on entity properties. |
| Admin REST `Pageable` | `@PageableDefault` on controllers | Sort fields are **property names** resolved by Spring Data JPA to mapped attributes — not raw `ORDER BY` concatenation from request text. |

---

## Phase 4 — Edge programmatic SQL

### `PartitionSchedulerService`

- SQL string is fixed: `SELECT create_monthly_partition(:tableName, :partitionName, :startDate, :endDate)`.
- **Call site:** `tableName` is always `"ezkey_auth_attempt"` or `"ezkey_audit_log"`; `partitionName` is `ezkey_<table>_<yyyy_MM>` from `DateTimeFormatter`. No HTTP or user input.
- Parameters passed via `setParameter` — **safe**.

### `ShedLockHealthIndicator`

- Single constant `LOCKS_QUERY` — **safe**.

### `JdbcTemplateLockProvider` (ShedLock)

- Table name configured as literal `ezkey_shedlock`. Library uses parameterized inserts/updates for lock rows — **no custom dynamic SQL** in application code.

### `ReencryptionRowPersistenceService`

- Uses `EntityManager.flush()` after repository `save` — **no** ad hoc SQL.

---

## Risk register

| Item | Severity | Outcome | Remediation (if any) |
|------|----------|---------|----------------------|
| Custom `@Query` / `@NativeQuery` / JPQL | — | **OK** — named parameters throughout reviewed repositories. | None. |
| `LIKE :prefix` on encrypted columns (re-encryption) | Low (non-SQLi) | **OK** for injection — `prefix` is built internally (e.g. key id pattern), not raw HTTP. | If future code ever passes unvalidated search strings into `LIKE`, document or escape `%`/`_` for intended semantics. |
| Admin search (`Enrollment` / `Integration` name filters) | Low (non-SQLi) | **OK** for injection — Criteria binds the full pattern. | Optional: escape `%`/`_` if overly broad matches are a concern. |
| `Pageable` / `sort` query params | — | **OK** — Spring Data maps sort to entity properties; invalid properties error at resolution, not via SQL concatenation. | Optional: `PageableHandlerMethodArgumentResolver` / `Sort` whitelist if stricter API contracts are desired (UX, not SQLi). |
| Partition scheduler `tableName` / `partitionName` | — | **OK** — values are constants or date-derived strings; still bound to the function. | None. |
| Tests (`DatabaseHelper`, concatenated SQL in tests) | Out of scope for prod | Test-only helpers; not in application attack surface. | Keep test SQL as static or test-controlled ids. |

**No medium or high classic SQL injection findings** in reviewed production paths.

---

## Optional follow-ups

1. **CI guardrail:** Fail PRs that introduce suspicious patterns in `**/src/main/java`, e.g. `createNativeQuery("` immediately followed by `+` or `String.format` with user-controlled fragments (tune for false positives).
2. **Repeat:** Re-run the grep inventory when adding new repositories or JDBC-based modules.

---

## Grep commands (repeatable)

From repository root (Git Bash or compatible):

```bash
rg '@NativeQuery|nativeQuery\s*=\s*true' --glob '*.java' --glob '**/src/main/java/**'
rg 'createNativeQuery' --glob '*.java' --glob '**/src/main/java/**'
rg 'JdbcTemplate|NamedParameterJdbcTemplate' --glob '*.java' --glob '**/src/main/java/**'
```

For concatenation red flags in application code:

```bash
rg '"SELECT.*"\s*\+' --glob '*.java' --glob '**/src/main/java/**'
```
