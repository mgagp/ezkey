# Ezkey Core — Agent Notes

For agents working in `ezkey-core/`.

## JPA entity defaults

- **Timestamps** (`createdAt`, `updatedAt`): `@PrePersist` / `@PreUpdate` when null only.
- **`expiresAt`:** set in the service (config TTL). Do not put TTL math in `@PrePersist`.
- **Other defaults** (`active`, status): field init. No `@PrePersist` for these.
- **MapStruct** create→entity: ignore `id`, `createdAt`, `active` (etc.). Example: `IntegrationServiceMapper.toEntity`.
- Do not add a generic `DefaultValueEntityListener`. Leave `EncryptionEntityListener` as-is.

## At-rest encryption contract

New encrypted fields and JPA listeners use `EncryptionOperations` via
`EncryptionOperationsHolder`. Do not take a compile dependency on `EncryptionService` or
`TinkKeyManager` from `ezkey-core` — those live in `ezkey-core-security`.
`SensitiveDataHasher` stays in core (shared hash util).

## Admin enrollment FK naming

Ezkey has one enrollment identifier. `ezkey_admin.enrollment_id` maps to `EzkeyAdmin.enrollment`.
Do not reintroduce `mfa_enrollment_id` or `mfaEnrollment` — that was legacy wording, not a second
domain concept.

## Operational eligibility

Runtime “can this entity be used?” is **`EntityEligibilityService`** (`org.ezkey.service`), not
inline `!tenant.getActive()` or lifecycle-status checks. Methods: `isXxxOperational()` /
`ensureXxxOperational()`. Parent-chain blocking has no persistent cascade. Operator canon:
[`docs/LIFECYCLE_GOVERNANCE.md`](../docs/LIFECYCLE_GOVERNANCE.md). Boot apps that inject this bean
must include `org.ezkey.service` in `scanBasePackages`.

## Contact phone numbers

Contact phones exist on admin (`phoneNumber`), tenant (`primaryContactPhoneNumber`), and enrollment
(`contactPhoneNumber`) only. Persist canonical E.164 via `PhoneNumberUtils`. These fields are
operator contact metadata, not a verified possession factor. Do not add phone to QR payloads or
auth-attempt.

## Re-encryption remaining counts

Derived remaining/lifecycle snapshots go through `KeyUsageVerificationService`, which reuses
`ReencryptionTargetQueryService` and the same `discoverReencryptableTargets()` as batch creation.
Discovery is equality on indexed `*_encryption_key_id` companion columns (`I-2026-0029` /
`TB-2026-07-26`), not `LIKE 'ENC:{keyId}:%'` on ciphertext. Do not add a second counting stack or
revive prefix scans. Do not treat `recordsEncrypted` as live remaining (it is the
migration baseline). Indexed discovery: `docs/REENCRYPTION_OPERATIONS.md` §1.2.
Orchestration stays in `ReencryptionService`; row work is
`ReencryptionBatchCreationService` / `ReencryptionBatchProcessingService` /
`ReencryptionBatchParallelRunner` (mutex per table, or per shard when auth-attempt sharding is on).
Do not fold row crypto back into `ReencryptionService`. Canon: `docs/REENCRYPTION_OPERATIONS.md`,
`docs/SPEC_ENCRYPTION_KEY_LIFECYCLE_STRATEGY.md`.

## Pending enrollment expiry

Omitted create `expiresAt` uses `ezkey.enrollment.pending-expiration-days` (default **7**). Set `0`
to disable that default (explicit `expiresAt` still applies). The field is the pending bind/verify
invitation window only — not a post-`VERIFIED` MFA lifetime.

## Audit chain heartbeat

Peripherals read the latest checkpoint vs UTC now via `AuditChainHeartbeatGuardService` (not a
second clock). Fail-closed **503** on gated POSTs when the chain is stale. Keep `window-minutes`
aligned across Admin, Auth, and Integration. Canon: `docs/AUDIT_LOG_INTEGRITY.md`.

## Operator alerts

Do not emit operator-facing signals as audit `EventType` rows. Use
`AlertService.raiseOrTouch` / `resolveByDedupeKey` (`ezkey_alert`). Canon:
`docs/ALERTS.md` (how to add a type, dedupe keys, resolution paths).

## Audit log archive lifecycle

Physical deletion is the last step of one checkpoint FSM
(`ACTIVE → SEALED → [EXPORTED] → PURGEABLE → PURGED`). Auto-seal is the product path;
`seal-archive` is exceptional. Policy: `CONFIGURATION.md` § Audit Log Archive. Operator
endpoints: `docs/AUDIT_LOG_INTEGRITY.md`. External export remains `I-2026-06-28`.

## Audit EventType families

Each new {@code EventType} must be assigned to exactly one {@code EventTypeFamily} (exhaustive
coverage is enforced by {@code EventTypeFamilyTest}). Admin UI optgroups live in
{@code ezkey-admin-ui/src/lib/audit-event-type-family.ts} and must stay aligned. Filter param:
{@code GET /api/v1/audit-logs?eventTypeFamily=} (mutually exclusive with {@code eventType}).

## Audit event_details — JSON required

**Critical:** The `event_details` column is indexed with `event_details::jsonb` (V19). Plain strings (e.g. `"Username: john"`) cause PostgreSQL cast failures on INSERT for event types covered by those indexes (KEY_*, REENCRYPTION_*).

- **Always use valid JSON** for `eventDetails(...)`. Prefer `AuditDetailsBuilder.builder().custom("key", value).toJson()` or the builder’s typed methods.
- **Never pass** concatenated plain strings like `"New key ID: " + id` — they are not valid JSON.
- **Event types affected:** `KEY_INTRODUCED`, `KEY_PROMOTED_PRIMARY`, `KEY_DEMOTED`, `KEY_DISABLED`, `REENCRYPTION_*`.

## Non-negotiables

- All project content **in English**.
- Constructor injection; no `@Autowired`.
- **Do not edit OpenAPI spec files** under `specs/` (e.g. `openapi-spec.json`). They are generated by `scripts/update-specs.sh` after a clean Docker start. See `.cursor/rules/openapi-specs.mdc`.

## Testing philosophy: Opportunistic database validation

**Critical security principle**: Security functionality must be validated at all times, not just through API responses.

- **Use APIs primarily**: Tests should primarily use and exercise the APIs as real clients would.
- **Be opportunistic**: When relevant and appropriate, use `DatabaseHelper` to validate or cross-check database state directly.
- **Security validation**: For security-critical features, it is imperative to verify the actual database state, not just API responses. This ensures security controls are working correctly "under the hood".
- **When to use**: Use `DatabaseHelper` for:
  - State verification (faster than API calls when appropriate)
  - Finding existing entities before creating new ones
  - Cross-validation of security constraints (e.g., uniqueness constraints, status transitions)
  - Cleanup operations for test idempotence
  - Resetting state when needed

**Example**: When testing enrollment uniqueness, verify both:
1. API rejects duplicate creation (API-level validation)
2. Database constraint prevents duplicate VERIFIED enrollments (database-level validation)

This dual validation ensures security is enforced at both application and database levels.

---

## Integration model (flat name/description since V7)

`Integration` stores a single `name` and optional `description` on `ezkey_integration`
(`integration_name`, `integration_description`). The historical child table
`ezkey_integration_i18n` was backfilled and dropped in migration V7 — do not reintroduce a
separate i18n entity or join table. Do not reintroduce `integration_logo` or bind-response
`integrationLogo`. `code` is the per-tenant unique slug (`^[a-zA-Z0-9_-]+$`); uniqueness is
`(tenant_id, integration_code)`. Duplicate create returns 409. Same code in different tenants is
allowed. `code` is set at create; there is no update-code API.

Bind and admin flows read display text from those scalar columns via standard
`integrationRepository.findById()`.

---

## CascadeType.ALL + orphanRemoval shared-reference hazard

### What it is

Hibernate tracks `@OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)` collections as
**owned bags**. It snapshots them at entity load and dirty-checks them at flush time to determine
whether to INSERT, UPDATE, or DELETE children.

The hazard: under concurrent load, if two or more transactions load the **same parent entity row**
(same primary key), Hibernate may detect that two managed entity states reference the same
collection object in the JVM heap and throw:

```
org.hibernate.HibernateException: Found shared references to a collection: …
```

This is intermittent at low concurrency but becomes **progressively more likely** as the number of
parallel requests sharing the same parent entity grows.

### Historical note (resolved for Integration)

Before V7, `Integration.i18n` triggered this hazard on the system integration (`id=1`) during
parallel admin MFA bind flows (observed 2026-02-20). Flattening integration name/description onto
`ezkey_integration` removed that collection entirely.

### The fix pattern (for future `@OneToMany(cascade=ALL)` parents)

For any code path that **reads** an entity with `cascade=ALL` on a collection but does **not
mutate** it, load via a dedicated read-only repository method with
`@QueryHints(org.hibernate.readOnly=true)` (and JOIN FETCH only when the association is actually
needed in that read path).

### Rule for all code generation

| Goal | Method to use |
|---|---|
| Read parent for display only | Read-only query with `@QueryHint(readOnly=true)` |
| Mutate parent or owned children | `findById()` (standard managed entity) |
| Any new `@OneToMany(cascade=ALL)` introduced in the future | Apply the same read/write split |

### How to flag this in code review

Flag any code that calls `repository.findById()` on an entity with `cascade=ALL` on a collection
and then only reads that collection (no `set*`, `add`, or `remove` calls follow). Migrate to a
read-only query method.

### OSIV amplifier

Spring Boot enables `spring.jpa.open-in-view=true` by default, extending the Hibernate session
across the entire HTTP request lifecycle. This widens the window during which the hazard can
manifest. For high-concurrency endpoints, consider disabling OSIV in `application.properties`:

```properties
spring.jpa.open-in-view=false
```

---

## Transaction boundaries (JPA / Spring)

- **Self-invocation bypasses proxy:** Calling `this::method` or `this.helper()` does not go through the Spring proxy. `@Transactional` on the called method is ignored. Place `@Transactional` on the entry point (e.g. event listener, controller-called service) so the whole flow runs in one transaction.
- **Avoid nested @Transactional:** Prefer a single transactional boundary at the top-level use case. Inner services should not declare `@Transactional` unless they need `REQUIRES_NEW`.
- **ApplicationReadyEvent order:** keyset empty-table sync (`ApplicationReadyStartupOrder.KEYSET_SYNC`) must run before Admin API MFA bootstrap. Enrollment rows write `*_encryption_key_id` FKs. Only Admin (`ezkey.encryption.keyset.writer=true`) performs that sync (ADR-0012). Auth/Integration skip it and wait for `ezkey_keyset_blob`.
- **See:** `docs/plan/JPA_TRANSACTION_DESIGN_NOTES.md` for full lessons learned.

---

## Partitioned tables and Flyway (audit log, auth attempts)

High-volume tables `ezkey_audit_log` and `ezkey_auth_attempt` are **range-partitioned by
`created_at`**. When adding migrations or entities that reference audit entries:

- **Greenfield formulation:** write Flyway SQL as if designing the schema fresh (pre-production).
  Prefer the natural PostgreSQL shape, not a workaround for an earlier migration gap.
- **Composite identity:** partitioned row identity depends on partition strategy:
  - RANGE only (`ezkey_auth_attempt`): `(auth_attempt_id, created_at)` — PK in V4.
  - RANGE + LIST (`ezkey_audit_log`): full FK target would be
    `(audit_log_id, created_at, api_name)`; no parent PK today.
- **Primary key on partitioned parents:** must include **every** partition key column. Auth attempt
  needs `created_at`; audit log also needs `api_name`. Do not copy the auth_attempt PK shape blindly.
- **Durable overlays vs purgeable facts:** tables that must **outlive** audit partition purge
  (e.g. `ezkey_audit_entry_integrity_conciliation`) store an **immutable composite snapshot** and
  **omit FK** to `ezkey_audit_log`. A restrictive FK would block
  `AuditLogService.purgeLifecycleEligibleLogs()` or destroy the operator-visible audit narrative on `ON DELETE CASCADE`.
- **When FK is appropriate:** reference non-partitioned tables (`ezkey_alert`, `ezkey_admin`) or
  use composite FK to partitioned tables only when the dependent row lifecycle matches the target
  (same purge window or `ON DELETE` semantics explicitly designed).
- **`create_monthly_partition` call pattern:** the SECURITY DEFINER helper (in
  `V4__partitioning_auth_audit_and_function.sql`) returns **BOOLEAN** (`true` created,
  `false` already existed). Call it with `SELECT …` + `getSingleResult()` (see
  `PartitionSchedulerService`). Do **not** use `executeUpdate()` on that SELECT — Hibernate
  expects no result set and fails with “A result was returned when none was expected”.

Authoritative detail: `docs/DATABASE_PARTITIONING_IMPLEMENTATION.md` (§ Flyway greenfield patterns),
`.cursor/rules/flyway-partitioned-tables.mdc`.
