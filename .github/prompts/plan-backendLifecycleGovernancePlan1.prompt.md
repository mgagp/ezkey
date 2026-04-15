## Plan: Backend Lifecycle Governance — Plan 1

Implement the locked decisions from the transverse governance v1 (§11–§13): create a centralized eligibility service, remove `INACTIVE` from Integration, unify reason policy, and expose `operational` in all response DTOs. Work follows entity hierarchy top-down.

---

### Phase A — Eligibility Service (new component, critical path)

**Exists:** No centralized service. Parent-state checks are scattered across 6+ services with duplicated inline `!tenant.getActive()` and `integration.getLifecycleStatus() != ACTIVE` patterns. One reusable guard exists (`TenantService.ensureTenantActive()`) but only used by `TenantService.updateTenant()` itself. `Integration.isOperational()` exists as a domain helper.

**Must change:**
1. Create `EntityEligibilityService` in `ezkey-core` (`@Service`, injectable)
2. Methods: `isTenantOperational()`, `isIntegrationOperational()`, `isEnrollmentOperational()`, `isApiKeyOperational()`, `isAdminOperational()` — each following locked evaluation chain: local state → parent → tenant → special guards
3. Guard variants `ensureXxxOperational()` throwing appropriate typed exceptions
4. Scattered inline checks replaced progressively in Phases B–F

**Files:**
- **New:** `ezkey-core/src/main/java/org/ezkey/core/service/EntityEligibilityService.java`
- **New:** `ezkey-core/src/test/java/org/ezkey/core/service/EntityEligibilityServiceTest.java`

---

### Phase B — Integration: remove INACTIVE (Decision §11.2)

**Exists:** `IntegrationLifecycleStatus` has 3 values (ACTIVE, INACTIVE, RETIRED). DB CHECK constraint in V1 migration enforces all three. `INACTIVE` has no direct service mutation endpoint — can only be set via compat `setActive(false)` helper (planned for removal). Controller search supports `active` boolean filter mapping to INACTIVE. Tests and UI reference INACTIVE.

**Must change:**
1. **New Flyway migration**: `UPDATE ... SET 'RETIRED' WHERE 'INACTIVE'`, then replace CHECK constraint to `('ACTIVE', 'RETIRED')` only
2. Remove `INACTIVE` from `IntegrationLifecycleStatus` enum
3. Remove or deprecate compat helpers `getActive()` / `setActive()` on entity
4. Simplify `IntegrationService.retire()` — only ACTIVE → RETIRED transition
5. Update controller search filters (drop `active` boolean param or map to lifecycleStatus)
6. Add `operational` to `IntegrationResponseDto` (from eligibility service)
7. Fix all tests referencing INACTIVE

**Key files:**
- `ezkey-core/src/main/java/org/ezkey/integration/domain/IntegrationLifecycleStatus.java`
- `ezkey-core/src/main/java/org/ezkey/integration/domain/entity/Integration.java` — remove `getActive()`/`setActive()`, keep `isOperational()`/`isRetired()`
- `ezkey-core/src/main/java/org/ezkey/integration/service/IntegrationService.java` — simplify `retire()`
- `ezkey-admin-api/src/main/java/org/ezkey/admin/controller/IntegrationController.java` — search filters
- `ezkey-admin-api/src/main/java/org/ezkey/integration/dto/IntegrationResponseDto.java` — drop `active`, add `operational`
- `ezkey-admin-api/src/main/java/org/ezkey/integration/mapper/IntegrationControllerMapper.java`
- **New:** Flyway migration `V8__remove_integration_inactive_status.sql` (verify next version number)
- `ezkey-core/src/test/java/org/ezkey/integration/service/IntegrationServiceTest.java`

**Depends on:** Phase A.

---

### Phase C — Tenant: route through eligibility service

**Exists:** `Tenant.active` boolean — already correct. `deactivateTenant()` / `activateTenant()` — idempotent, audit trail, token revocation. System tenant guard. `ensureTenantActive()` exists but not used by core services. No `operational` in `TenantResponseDto`.

**Must change:**
1. Refactor `IntegrationService.create()`, `EnrollmentService.create()`, `ApiKeyService.createApiKey()`, `ApiKeyService.validateApiKey()` to use eligibility service instead of inline tenant checks
2. Add `operational` to `TenantResponseDto` (= `active` for now, but field exists for DTO consistency)
3. Update `TenantMapper`

**Key files:**
- `ezkey-core/src/main/java/org/ezkey/integration/service/IntegrationService.java`, `ezkey-core/src/main/java/org/ezkey/enrollment/service/EnrollmentService.java`, `ezkey-core/src/main/java/org/ezkey/integration/service/ApiKeyService.java` — replace inline checks
- `ezkey-admin-api/src/main/java/org/ezkey/admin/dto/response/TenantResponseDto.java`
- `ezkey-admin-api/src/main/java/org/ezkey/admin/mapper/TenantMapper.java`

**Depends on:** Phase A. *Parallel with B.*

---

### Phase D — Enrollment: move delete guards to service, add `operational`

**Exists:** Strong FSM already: `EnrollmentRevocationService` handles revoke/deactivate/reactivate with correct semantics. Delete guards enforced in **controller only** (not service) — fragile. Reason mandatory for revoke, optional for deactivate/reactivate. No `operational` field.

**Must change:**
1. Move delete preconditions (no self-deletion, not admin MFA, no auth history) from controller to service layer — create `validateAndDelete()` in `EnrollmentRevocationService` or new dedicated method
2. Add `operational` to `EnrollmentResponseDto` — computed: VERIFIED + active + integration ACTIVE + tenant active
3. Update mapper/controller to populate `operational`
4. Make delete reason **required** (currently optional — must align with v1 §10)

**Key files:**
- `ezkey-admin-api/src/main/java/org/ezkey/admin/controller/EnrollmentController.java` — move guards out
- `ezkey-admin-api/src/main/java/org/ezkey/admin/service/EnrollmentRevocationService.java` — absorb delete validation
- `ezkey-admin-api/src/main/java/org/ezkey/enrollment/dto/EnrollmentResponseDto.java`
- `ezkey-admin-api/src/main/java/org/ezkey/enrollment/mapper/EnrollmentAdminMapper.java`

**Depends on:** Phase A. *Parallel with B/C.*

---

### Phase E — API Key: require reason for revoke, add `operational`

**Exists:** Revoke-only model correct by design. DELETE endpoint semantically revokes (no physical delete). Reason currently optional. No `operational` field. No reactivation — correct.

**Must change:**
1. Make reason **required** for revoke (change from optional query param to mandatory — validate in controller)
2. Add `operational` to `ApiKeyResponseDto` — computed: active + not expired + integration operational + tenant active
3. Update controller to populate `operational`
4. Renaming DELETE to POST `/revoke` deferred (out of scope)

**Key files:**
- `ezkey-admin-api/src/main/java/org/ezkey/admin/controller/ApiKeyController.java`
- `ezkey-admin-api/src/main/java/org/ezkey/admin/dto/response/ApiKeyResponseDto.java`
- `ezkey-core/src/main/java/org/ezkey/integration/service/ApiKeyService.java` (optional reason validation)

**Depends on:** Phase A. *Parallel with B/C/D.*

---

### Phase F — Admin: add activate reason, add `operational`

**Exists:** `deactivateAdmin()` / `activateAdmin()` — idempotent, token revocation on deactivate. Deactivation has optional reason. **Activation has no reason support at all** (missing parameter). No `operational` field.

**Must change:**
1. Add reason parameter to `activateAdmin()` — optional, same 10-500 validation, pass to audit
2. Add `operational` to `AdminResponseDto` — computed: admin active + (global admin OR tenant active)
3. Update controller to populate `operational`
4. Document separation: admin deactivation ≠ enrollment revocation

**Key files:**
- `ezkey-admin-api/src/main/java/org/ezkey/admin/service/AdminProvisioningService.java`
- `ezkey-admin-api/src/main/java/org/ezkey/admin/controller/AdminProvisioningController.java`
- `ezkey-admin-api/src/main/java/org/ezkey/admin/dto/response/AdminResponseDto.java`

**Depends on:** Phase A. *Parallel with B/C/D/E.*

---

### Phase G — Reason Policy: final enforcement pass

After all entity phases are done, one pass to verify alignment with v1 §10:

| Operation | Target | Change needed |
|-----------|--------|---------------|
| Enrollment revoke | Required | Already correct |
| Enrollment delete | **Required** | Currently optional → make required |
| API key revoke | **Required** | Currently optional → make required |
| Integration retire | **Required** | Currently optional → make required |
| Integration delete | **Required** | Currently optional → make required |
| Admin activate | Optional | Currently missing → add |
| All reversible actions | Optional | Already correct |

**Depends on:** Phases B–F.

---

### Phase H — DTO `operational` field: enrichment pass

After all entity phases set up the field, one verification pass to ensure:
1. Every response DTO has `Boolean operational`
2. Detail responses enrich with parent context where missing:
   - `IntegrationResponseDto` → add `tenantName`, `tenantActive`
   - `ApiKeyResponseDto` → add `integrationName`, `integrationLifecycleStatus`
   - `AdminResponseDto` → add `tenantName`
3. List endpoints compute `operational` efficiently (watch N+1 on parent fetches)

**Depends on:** Phases A–F.

---

### Phase I — Tests

1. **New:** `EntityEligibilityServiceTest` — all entity combinations, degraded parent chains
2. **Update:** `IntegrationServiceTest` — remove all INACTIVE references
3. **Update:** `ApiKeyServiceTest` — required reason on revoke
4. **Update:** `EnrollmentRevocationServiceTest` — required reason on delete
5. **Update:** all test files referencing `IntegrationLifecycleStatus.INACTIVE`
6. **Consider:** one integration test verifying `operational` propagates correctly in a GET response

**Depends on:** Phases A–H.

---

### Phase J — Documentation

1. Update `docs/ENDPOINT.md` — new vocabulary, `operational` field, INACTIVE removal
2. Update `docs/API_KEYS_IMPLEMENTATION.md` — required reason for revoke
3. Update OpenAPI annotations on controllers
4. Javadoc on `EntityEligibilityService` — explain evaluation chain and rationale

**Depends on:** Phases A–H. *Parallel with I.*

---

### Execution Order

```
A ─────────────────────────┐
                           ├── B, C, D, E, F (parallel after A)
                           │
                           ├── G, H (after B–F)
                           │
                           └── I, J (after G–H, parallel with each other)
```

**Minimum critical path:** A → (B+C+D+E+F) → (G+H) → (I+J)

---

### Verification

1. `mvn spotless:apply && mvn checkstyle:check && mvn clean install` passes
2. `IntegrationLifecycleStatus.INACTIVE` no longer exists anywhere in Java
3. DB migration converts INACTIVE → RETIRED and removes from CHECK constraint
4. Every response DTO includes `operational` boolean
5. Every irreversible action requires a reason
6. Eligibility service is single source of truth — no inline parent-state checks remain
7. Manual: clean-start stack → create tenant → integration → enrollment → deactivate tenant → GET enrollment shows `operational: false` without any persistent cascade

---

### Decisions

- `EntityEligibilityService` in `ezkey-core` (not admin-api) — needed by auth-time services
- No `blockedBy` or `effectiveStatus` — only `operational` boolean in Phase 1
- Same DTO for list/detail — no split
- Enrollment dual model preserved as-is
- DELETE `/api-keys/{id}` kept (renaming to POST `/revoke` deferred)
- Integration `active` boolean in DTO: deprecate or remove — open for Phase B discussion

### Scope Exclusions

- Admin UI alignment (Plan 2)
- Tenant/admin/bulk delete, GDPR (Phase 2+)
- Encryption key and recovery code lifecycle changes
- Auth attempt lifecycle (event-driven, no changes)
- `blockedBy` diagnostic field
