# Java JPA / Spring Data Deprecated API Assessment (2026-07)

## Mandate

- **Surface:** Java persistence call sites across `ezkey-core`, `ezkey-core-security`,
  `ezkey-admin-api`, `ezkey-auth-api`, and `ezkey-integration-api` (JPA entities, Spring Data
  repositories, `EntityManager` / Criteria usage).
- **Attention axes:** methods and types marked `@Deprecated` in the stack we actually run
  (Spring Boot **4.1.0** → Spring Data JPA / Commons **4.1.0**, Hibernate ORM **7.4.1.Final**,
  Jakarta Persistence **3.2.0**); split mechanical replacement (category 1) vs redesign-heavy
  (category 2: concurrency, caching, query-shape changes).
- **Non-goals:** implementing fixes in the assessment session; zero-warning campaigns;
  `java-doctor-curated` / Dependabot shortlists; transactional-boundary redesign unless a
  deprecation forces it; inventing `I-*` / `TB-*` per finding.

## Scope and method

- Static white-box inventory of known deprecated Spring Data JPA / Commons and Jakarta Persistence
  APIs against production Java sources.
- Cross-check of Hibernate 7 proprietary annotations / Session APIs (usage scan; none found in
  Ezkey entities).
- Compiler confirmation for the actionable Criteria API finding via
  `javac -Xlint:deprecation` against Jakarta Persistence 3.2.0.

## Stack versions (evidence)

| Component | Version |
| --- | --- |
| Spring Boot (parent) | 4.1.0 |
| spring-data-jpa | 4.1.0 |
| spring-data-commons | 4.1.0 |
| hibernate-core | 7.4.1.Final |
| jakarta.persistence-api | 3.2.0 |

## Findings register

| ID | Title | Severity | Confidence | Category | Deprecated API in use | Sites | Replacement direction | Quick-win potential |
| --- | --- | --- | --- | --- | --- | ---: | --- | --- |
| JPA-DEP-001 | Criteria `multiselect` on dashboard aggregates | P2 | High | **1 — quick win** | `CriteriaQuery.multiselect(...)` (Jakarta Persistence 3.2) | 2 → 0 | `cq.select(cb.tuple(...))` | **Closed (fixed 2026-07-26)** |

### Category 2 — complex / redesign

No deprecated JPA / Spring Data call sites were found that require concurrency, caching, or
query-formation redesign. Category 2 is **empty** for this pass.

## Evidence

### JPA-DEP-001

Former usage (dashboard aggregation Criteria queries):

- `AuthAttemptService.aggregateDashboard24h` —
  `cq.multiselect(root.get("authAttemptStatus"), cb.count(root));`
- `EnrollmentService.aggregateDashboardEnrollmentStats` —
  `cq.multiselect(root.get("status"), root.get("active"), cb.count(root));`

Deprecation verification:

- Jakarta Persistence 3.2 marks both `multiselect(Selection<?>...)` and
  `multiselect(List<Selection<?>>)` `@Deprecated(since = "3.2")`, preferring
  `CriteriaBuilder.tuple` / `array` with `select`.
- `javac -Xlint:deprecation` against `jakarta.persistence-api:3.2.0` warns:
  `multiselect(Selection<?>...) in CriteriaQuery has been deprecated`.

Replacement applied (2026-07-26):

```java
cq.select(cb.tuple(root.get("authAttemptStatus"), cb.count(root)));
// and
cq.select(cb.tuple(root.get("status"), root.get("active"), cb.count(root)));
```

Risk notes:

- Still returns `Tuple` rows; index-based `tuple.get(0/1/2)` remain equivalent.
- Low blast radius: two dashboard aggregate paths; no locking / batch mutation / flush semantics.

Disposition: **closed** — HITL **fix**; both call sites migrated.

## Checked clean (not deprecated or not used)

These were inventoried explicitly so a cold session does not re-hunt them without evidence.

| API / pattern | Status in Ezkey sources |
| --- | --- |
| `JpaRepository.getOne(ID)` | **Not used** |
| `JpaRepository.getById(ID)` | **Not used** (see false-positive note) |
| `JpaRepository.deleteInBatch(...)` | **Not used** |
| `JpaRepository.getReferenceById(ID)` | Not used (replacement available; no caller of deprecated getters) |
| `@Temporal` / `TemporalType` / Query temporal setters | **Not used** |
| `Sort.sort(Class)` / `Sort.TypedSort` (deprecated since Spring Data Commons 4.1) | **Not used** — string/`Order` `Sort.by(...)` remains current |
| `ChainedTransactionManager` | **Not used** |
| Hibernate proprietary entity annotations (`@Where`, `@Cascade`, `@Type`, …) | **Not used** on Ezkey entities |
| Legacy Hibernate Criteria / `Session.load` / `createSQLQuery` | **Not used** |
| Jakarta `EntityGraph` / `Graph` deprecated subgraph APIs | **Not used** — Spring Data `@EntityGraph(attributePaths=...)` only |
| `org.hibernate.readOnly` query hint | Still the documented `HibernateHints.HINT_READ_ONLY` value — **not** deprecated |
| Untyped `EntityManager.createNativeQuery(String)` | Used in `PartitionSchedulerService`; **not** marked `@Deprecated` in JPA 3.2 |

### False-positive note — `getById` naming

Many hits for `getById(` are **Ezkey service / controller methods** that wrap
`repository.findById(...)`, not `JpaRepository.getById(ID)`. Examples:
`EnrollmentService.getById`, `AuthAttemptService.getById`, `IntegrationService.getById`. Those are
out of scope for this deprecation assessment.

## Out of scope observations (not deprecations)

- Pessimistic `@Lock` repository methods, `saveAndFlush`, and `EntityManager.flush` in
  reencryption paths are current APIs; redesign of those flows belongs to other mandates
  (transactions / reencryption), not this deprecation inventory.
- Native Graal configurations that list Hibernate logger class names are registration lists, not
  deprecated call sites.

## Recommended HITL lot

1. **JPA-DEP-001** — replace `multiselect` with `select(cb.tuple(...))` in the two dashboard
   aggregate methods (category 1) — **closed (fixed 2026-07-26)**.

No category-2 handoff candidates in this pass.
