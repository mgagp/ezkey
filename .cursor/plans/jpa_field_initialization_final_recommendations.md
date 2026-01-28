# JPA Field Initialization — Final Pragmatic Recommendations

**Context:** Balance between testability, reliability, and code quality for JPA entities.  
**Scope:** `createdAt`, `updatedAt`, `expiresAt`, and related defaults (`active`, `status`, etc.).  
**References:** Plan `standardiser_l'initialisation_des_champs_jpa_avec_@prepersist_1a1bb73b.plan.md`, counter-analysis `@PrePersist_counter_analysis.md`, `prompt_recadrage_prepersist.md`.

---

## 1. Executive Summary

| Aspect | Recommendation |
|--------|----------------|
| **Timestamps** (`createdAt`, `updatedAt`, `expiresAt`) | Use **`@PrePersist`** (and **`@PreUpdate`** for `updatedAt`) **only** when null. Keep logic minimal and documented. |
| **Other defaults** (`active`, `status`, etc.) | Use **field initialization** (e.g. `private Boolean active = true;`). Avoid `@PrePersist` for these. |
| **MapStruct** | Use **`@Mapping(target = "...", ignore = true)`** (or `NullValuePropertyMappingStrategy.IGNORE`) for fields not provided by DTOs. Do not rely on entity defaults for mapper contracts. |
| **EntityListeners** | **Do not** introduce a generic `DefaultValueEntityListener` in this pass. Keep `EncryptionEntityListener` as is. Revisit a shared listener only if we standardize more cross-entity defaults later. |

**Goal:** Improve consistency and maintainability where it matters, without large refactors. Validate practices for timestamp and expiration fields specifically.

---

## 2. Principles

1. **Single responsibility** — Entities hold data; initialization is either trivial (field init) or persistence-specific (timestamps).
2. **Explicit over implicit** — Prefer clear, documented rules over “magic” callbacks. Limit `@PrePersist` to timestamps.
3. **Testability** — MapStruct and unit tests treat “not provided” via explicit ignore; integration tests persist entities and assert DB/default behaviour.
4. **Align with DDL** — Values in code should match DB `DEFAULT` where applicable, but we avoid duplicating every default in `@PrePersist`.

---

## 3. Rules by Field Type

### 3.1 Timestamps: `createdAt`, `updatedAt`

- **Use `@PrePersist`** (and **`@PreUpdate`** for `updatedAt`) **only** to set these when null.
- **Pattern:**
  ```java
  @PrePersist
  protected void prePersist() {
    if (this.createdAt == null) {
      this.createdAt = OffsetDateTime.now();
    }
  }

  @PreUpdate
  protected void preUpdate() {
    this.updatedAt = OffsetDateTime.now();
  }
  ```
- **Rationale:** Values are computed at persist/update time; conditional init avoids overwriting explicit values. Widely accepted practice (Thorben Janssen, Baeldung). Keeps entities as single place for persistence metadata.

### 3.2 Expiration: `expiresAt`

- **Preferred:** Set **in the service** when creating the entity (e.g. `AuthAttempt`). Use **config** (`EzkeyCoreProperties.ttlSeconds`) instead of hardcoded `120`.
- **Fallback:** If the entity can be persisted without going through the service, **optional** `@PrePersist`:  
  `if (expiresAt == null && createdAt != null) expiresAt = createdAt.plusSeconds(ttlSeconds)`.  
  This requires access to config (e.g. via an `EntityListener` or similar). For a minimal change, **omitting** this fallback and **always** creating via the service is acceptable; document that constraint.
- **Avoid:** Complex logic or business rules in `@PrePersist` for `expiresAt`.

### 3.3 Other Defaults: `active`, `status`, etc.

- **Use field initialization** (e.g. `private Boolean active = true;`, `private AuthAttemptStatus authAttemptStatus = AuthAttemptStatus.PENDING;`).
- **Do not** use `@PrePersist` for these. Simpler, more testable, easier to reason about.
- **MapStruct:** Explicitly **ignore** these when mapping create-request DTO → entity, if they are not part of the DTO.

---

## 4. Entity-by-Entity Cheat Sheet

| Entity | `createdAt` | `updatedAt` | `expiresAt` | `active` / `status` / etc. |
|--------|-------------|-------------|-------------|----------------------------|
| **Integration** | `@PrePersist` when null | — | — | Field init `active = true`; mapper ignore |
| **Tenant** | `@PrePersist` when null | — | — | Field init `active = true` |
| **EzkeyAdmin** | `@PrePersist` when null | — | — | Field init `active = true` |
| **ApiKey** | `@PrePersist` when null | — | Set by service | Field init `active = true` |
| **Enrollment** | `@PrePersist` when null | — | — | Field init for `status`, `active`, `authAttemptChallengeRequired` |
| **AuthAttempt** | `@PrePersist` when null | — | Set in **service** (use `ttlSeconds`); optional fallback in listener | Field init `authAttemptStatus = PENDING` |
| **AuditLog** | `@PrePersist` when null | — | — | — |
| **EncryptionKey** | `@PrePersist` when null | — | — | — |
| **ReencryptionBatch** | `@PrePersist` when null | `@PreUpdate` | — | — |
| **AdminToken** | Set in factory ctor | — | Set in factory ctor | — |

**Note:** `Integration` uses field init for `active` and `@PrePersist` only for `createdAt` (done).

---

## 5. MapStruct

- **Create-request DTO → Entity:** Use `@Mapping(target = "id", ignore = true)`, `@Mapping(target = "createdAt", ignore = true)`, `@Mapping(target = "active", ignore = true)`, etc. for any field not supplied by the DTO.
- **Entity → Response DTO:** No special handling for defaults; map as usual.
- **`NullValuePropertyMappingStrategy.IGNORE`** can be used instead of per-field ignore where appropriate; ensure it does not unintentionally skip required mappings.

### 5.1 MapStruct verification (create-request → entity)

| Mapper | Method | Target entity | Ignore rules | Status |
|--------|--------|---------------|--------------|--------|
| **IntegrationServiceMapper** (ezkey-core) | `toEntity(IntegrationCreateRequest)` | `Integration` | `id`, `createdAt`, `active` | OK |
| **IntegrationServiceMapper** (ezkey-core) | `map(IntegrationI18nCreate)` | `IntegrationI18n` | `id`, `integration` | OK (no timestamps/defaults on I18n) |
| **EnrollmentCoreMapper** | — | — | No create-request → entity | N/A |
| **EnrollmentAdminMapper** | — | — | DTO ↔ domain only; entity built in service | N/A |
| **EnrollmentAuthMapper** | — | — | DTO ↔ domain only | N/A |
| **AuthAttemptAdminApiMapper** | — | — | DTO ↔ domain only; entity built in service | N/A |
| **AuthAttemptAuthApiMapper** | — | — | DTO ↔ domain only | N/A |
| **IntegrationControllerMapper** | — | — | DTO ↔ domain; entity from IntegrationServiceMapper | N/A |
| **AuditLogMapper** | — | — | Entity → DTO only | N/A |

**Conclusion:** The only create-request → entity mappings are in `IntegrationServiceMapper`. Both have appropriate `@Mapping(ignore = true)` for fields not supplied by the DTO. No changes required.

---

## 6. AuthAttemptService and TTL

- **Done:** `AuthAttemptService` uses `EzkeyCoreProperties.getAuthAttempt().getTtlSeconds()` for `expiresAt` and `timeoutSeconds` (no more hardcoded `120`).
- **Keep** setting both `createdAt` and `expiresAt` in the service; treat `@PrePersist` as a **safety net** only when null (e.g. if something persists `AuthAttempt` without the service).

---

## 7. Concrete Action Items

1. **Standardize timestamp handling**
   - Use `@PrePersist` / `@PreUpdate` **only** for `createdAt` / `updatedAt`.
   - Apply consistently across Tenant, EzkeyAdmin, ApiKey, Enrollment, AuthAttempt, AuditLog, EncryptionKey, ReencryptionBatch (and Integration as above).

2. **Move non-timestamp defaults to field init**
   - e.g. `active`, `authAttemptStatus`, `status`, `authAttemptChallengeRequired` where applicable.
   - **Integration:** Done — `active` via field init, `@PrePersist` only for `createdAt`.

3. **MapStruct**
   - Ensure all create-request → entity mappers explicitly **ignore** `id`, `createdAt`, `active`, etc. where not in the DTO.
   - **Verification:** Done — see §5.1; only `IntegrationServiceMapper` maps create → entity; ignore rules OK.

4. **AuthAttemptService**
   - **Done** — Use `ttlSeconds` from config for `expiresAt` and `timeoutSeconds`.

5. **Documentation**
   - In JavaDoc (and optionally `AGENTS.md` or dev docs):  
     *“Timestamps (`createdAt`, `updatedAt`) are set via `@PrePersist` / `@PreUpdate` when null. Other defaults use field initialization. MapStruct create-request mappers ignore these fields.”*

6. **Tests**
   - **Unit:** Mappers and entities without persistence; use explicit `@Mapping` ignore.
   - **Integration:** Persist entities with null timestamps where allowed; verify `@PrePersist` and DB defaults.

---

## 8. What We Explicitly Avoid (This Pass)

- Introducing a **`DefaultValueEntityListener`** for generic defaults.
- Putting **business logic** or **many non-timestamp fields** in `@PrePersist`.
- Relying on **database-only defaults** for `createdAt` / `expiresAt` when JPA can insert null (we keep explicit init in code).
- Changing **`EncryptionEntityListener`** or callback order.

---

## 9. References

- Plan: `standardiser_l'initialisation_des_champs_jpa_avec_@prepersist_1a1bb73b.plan.md`
- Counter-analysis: `@PrePersist_counter_analysis.md`
- Recadrage: `prompt_recadrage_prepersist.md`
- Community: Thorben Janssen (Hibernate Tips), Baeldung (JPA defaults), Vlad Mihalcea (EntityListeners / hybrid approach).
