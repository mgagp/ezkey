# Enrollment Uniqueness Scope — Exploration

> **Status:** Exploration only. Not an ADR. Not accepted product direction.
> **Date:** 2026-09-22
> **Branch intent:** Documentary inventory for Marc / Alex product-intent lock.
> **Peers:** [`ADMIN_ONBOARDING_VS_ENROLLMENT_API_ANALYSIS.md`](ADMIN_ONBOARDING_VS_ENROLLMENT_API_ANALYSIS.md),
> [`multi-tenancy-strategy.md`](multi-tenancy-strategy.md) (historical).
> **Living canon already documenting name uniqueness:**
> [`docs/LIFECYCLE_GOVERNANCE.md`](../LIFECYCLE_GOVERNANCE.md) §3.3 (status-based name uniqueness).
> **80/20 complement (read before paradigm work):**
> [`ENROLLMENT_UNIQUENESS_QUICK_WINS_80_20.md`](ENROLLMENT_UNIQUENESS_QUICK_WINS_80_20.md) —
> ranked quick wins (docs/UX, status/`SUPERSEDED`, partial-unique+active) that unlock re-enroll
> after deactivate **without** per-tenant uniqueness.

---

## 0. Status / intent

This note inventories **how enrollment identity fields are constrained today**, and sketches what
**per-tenant uniqueness** would mean if product later chooses that direction. It does **not**
propose schema or service changes in this PR.

**Before deeper per-tenant analysis or validation-paradigm programs:** prefer the complement
[`ENROLLMENT_UNIQUENESS_QUICK_WINS_80_20.md`](ENROLLMENT_UNIQUENESS_QUICK_WINS_80_20.md). Most of
today’s re-create pain is the deactivate/`VERIFIED` unique-slot trap, not missing tenant-scoped
indexes.

**Product context (hypothesis):**

- Initial SME / single-tenant-heavy release: strong uniqueness around verified enrollment names
  (and globally unique admin usernames/emails) is reasonable and operationally simple.
- Next-level multi-tenant / multi-hat use: the **same** human or Linux username / contact email
  may legitimately appear in **different tenants**. Uniqueness should then be
  **compartmentalized per tenant** (or per integration), not instance-global.

**Observed pain (operator):**

- After **deactivate** (and sometimes after operators think of “cancel”), **re-create / re-verify**
  with the same enrollment name is blocked or half-blocked.
- Pain is amplified when two tenants share the same username or email — operators may attribute
  that to “global enrollment uniqueness,” which this inventory partially **reframes**.

**Important correction up front:** enrollment **`enrollment_name` uniqueness is already
per-integration (not instance-global)**, status-filtered to `VERIFIED`. What *feels* global is
often (a) deactivate leaving a `VERIFIED` row that still occupies the unique slot, (b) **admin**
username/email uniqueness (truly global), or (c) operators putting the Linux username into
`enrollment_name` rather than `user_identifier`.

---

## 1. Current inventory (facts)

### 1.1 Tenant model (how enrollment links to a tenant)

| Concept | Code / schema | Notes |
|--------|----------------|-------|
| Tenant entity | `org.ezkey.integration.domain.entity.Tenant` → table `ezkey_tenant` | Column `is_system_tenant` (`Boolean isSystemTenant`). Partial unique index `idx_tenant_system_tenant_unique` (V5) ensures one system tenant. |
| Tenant PK | `tenant_id` | |
| Integration → tenant | `Integration.tenant` / `ezkey_integration.tenant_id` | Required association for application tenants. |
| Enrollment → tenant | **Indirect only:** `Enrollment.integrationId` → `Integration` → `Tenant` | `ezkey_enrollment` has **no** `tenant_id` column. Admin access control walks enrollment → integration → tenant (`AccessControlService.canAccessEnrollment`). |
| System integration | `Integration.isSystemIntegration` | Admin MFA enrollments live here; create via Admin API enrollment create is blocked for system integration (`EnrollmentService.create`). |

There is no separate type named `SystemTenant` in Java; the flag is `isSystemTenant` on `Tenant`.

### 1.2 Enrollment identity fields vs admin identity (do not conflate)

| Surface | Field(s) | Scope of uniqueness today |
|--------|----------|---------------------------|
| **Enrollment** | `enrollment_name` | **Per integration**, only while `enrollment_status = 'VERIFIED'` |
| **Enrollment** | `user_identifier` | **No unique constraint** (non-unique lookup index per integration) |
| **Enrollment** | `contact_email` | **No unique constraint** |
| **Enrollment** | `contact_phone_number` | Format check only (E.164); no uniqueness |
| **Enrollment** | `enrollment_proof_token_hash` | **Global** unique |
| **Enrollment** | `device_public_key_hash` | **Global** unique among non-NULL values (DB); app check further filters `status = VERIFIED` |
| **Admin** | `username` | **Global** (`ezkey_admin.username` UNIQUE in V1) |
| **Admin** | `email` | **Global** (`uq_admin_email` in V2) |
| **Tenant** | `tenant_name` | **Global** UNIQUE |
| **Integration** | `integration_code` | **Per tenant** (`unique_integration_code_per_tenant` on `(tenant_id, integration_code)`) |

Admin MFA still uses an `Enrollment` row (system integration). Display name construction:
`AdminEnrollmentDisplayNames` — person-first name, with optional `(username)` suffix when the
person name is already taken by a VERIFIED enrollment on the system integration. That is still
**enrollment_name** uniqueness on the **system integration**, not admin username uniqueness.

### 1.3 Database constraints (Flyway under `ezkey-core/src/main/resources/db/migration/`)

Migrations live in **`ezkey-core`** (not a separate `ezkey-migration` module).

#### Enrollment-related

| Constraint / index | Migration | Definition | Scope | Status-filtered? |
|--------------------|-----------|------------|-------|------------------|
| `idx_enrollment_unique_verified_name` | V5 | `UNIQUE (integration_id, enrollment_name) WHERE enrollment_status = 'VERIFIED'` | **Per integration** | **Yes — VERIFIED only** |
| `idx_enrollment_integration_name_status` | V5 | Non-unique `(integration_id, enrollment_name, enrollment_status) WHERE status = VERIFIED` | Lookup helper | N/A |
| `idx_enrollment_integration_user_identifier` | V5 | Non-unique `(integration_id, user_identifier) WHERE user_identifier IS NOT NULL` | Per-integration lookup | No uniqueness |
| `uq_enrollment_proof_token_hash` | V2 | `UNIQUE (enrollment_proof_token_hash)` | **Global** | No (NULLs allowed until set) |
| `idx_enrollment_device_public_key_hash_unique` | V2 | `UNIQUE (device_public_key_hash) WHERE device_public_key_hash IS NOT NULL` | **Global** | **No** — any status with a non-null hash occupies the slot |

**Comment drift (user_identifier):** V5 column comment on `user_identifier` says
“Unique per integration for lookup,” but the index comment and DDL explicitly create a
**non-unique** index “Supports single- and multi-device.” Runtime auth-attempt lookup returns a
**list** and requires disambiguation when 2+ active VERIFIED rows share the same
`(integration_id, user_identifier)` — evidence the design intends multi-device, not uniqueness.

#### Related but not enrollment identity

| Constraint | Migration | Notes |
|------------|-----------|-------|
| `unique_integration_code_per_tenant` | V5 | Precedent for **per-tenant** composite uniqueness |
| `ezkey_admin.username` UNIQUE | V1 | Global admin login id |
| `uq_admin_email` | V2 | Global admin email |
| Auth-attempt proof / device proof hashes | V2 / V4 | Global (partition-aware) one-time tokens — not enrollment identity |

### 1.4 JPA entity (`Enrollment`)

File: `ezkey-core/src/main/java/org/ezkey/enrollment/domain/entity/Enrollment.java`

- `@Table(name = "ezkey_enrollment")` — **no** `@Table(uniqueConstraints=…)`.
- `enrollmentProofTokenHash`: `@Column(..., unique = true)` — mirrors `uq_enrollment_proof_token_hash`.
- `enrollmentName`, `userIdentifier`, `contactEmail`, `devicePublicKeyHash`: **no** `unique = true`
  on the column mapping; uniqueness for name and device-key hash is carried by Flyway indexes /
  service checks.

### 1.5 Service / domain validation

#### Create — `EnrollmentService.create`

File: `ezkey-core/.../enrollment/service/EnrollmentService.java`

- Loads VERIFIED rows via
  `EnrollmentRepository.findByIntegrationIdAndEnrollmentNameAndStatus(integrationId, name, VERIFIED)`.
- If an **active** VERIFIED exists → `ActiveVerifiedEnrollmentExistsException` (Admin API → **409**
  Problem type `https://ezkey.io/problems/enrollment/active-verified-enrollment-exists`).
- If a VERIFIED exists but **inactive** → create is **allowed** (log: “Creating new enrollment for
  replacement”).
- Does **not** check `userIdentifier` or `contactEmail` for conflicts.
- Blocks system-integration creates (admin MFA uses provisioning path instead).

Same active/inactive name pattern appears in
`AdminProvisioningService` when creating admin MFA enrollments on the system integration.

#### Verify — `EnrollmentVerifyService`

File: `ezkey-core/.../enrollment/service/EnrollmentVerifyService.java`

1. **Device public key:** `existsByDevicePublicKeyHash` — JPQL filters `status = 'VERIFIED'`
   (global across integrations). On conflict → mark INVALID + generic verify failure.
2. **Name uniqueness:**
   `findByIntegrationIdAndEnrollmentNameAndStatusAndEnrollmentIdNot(..., VERIFIED, ...)` —
   rejects if **any** other VERIFIED row exists for same integration+name, **including inactive**.
   Message directs to recovery/reset. Does **not** inspect `active`.

#### Update (PATCH) — `EnrollmentUpdateService`

File: `ezkey-admin-api/.../EnrollmentUpdateService.java`

- Renaming a VERIFIED enrollment re-checks
  `findByIntegrationIdAndEnrollmentNameAndStatusAndEnrollmentIdNot`.
- `userIdentifier` / `contactEmail` updates: trim / validate format only — **no** duplicate check.

#### Deactivate / revoke / reset (lifecycle vs unique slot)

| Action | Status after | `active` | Releases `idx_enrollment_unique_verified_name`? | Notes |
|--------|--------------|----------|--------------------------------------------------|-------|
| **Deactivate** (`EnrollmentRevocationService.deactivate`) | stays **`VERIFIED`** | `false` | **No** | Soft-disable; designed as reversible. |
| **Revoke** | **`REVOKED`** | `false` | **Yes** | Leaves VERIFIED predicate; slot free. |
| **Admin reset** (`AdminRecoveryService.resetEnrollment`) | **`CREATED`** | (unchanged path) | **Yes** | Clears `devicePublicKey`; sets new proof token/challenge. **Does not explicitly null `devicePublicKeyHash`** (see ambiguity below). |
| Failed verify → `markInvalidAndClear` | **`INVALID`** | `false` | Yes (if never VERIFIED) | Does not clear device key hash fields on the failed row. |

**There is no enrollment “cancel” status.** HTTP “cancel” in the product is
`POST /api/v1/auth-attempts/{id}/cancel` (auth-attempt lifecycle), unrelated to enrollment name
slots. Operator “cancel then re-create” almost certainly means **deactivate** and/or abandon a
pending create.

#### Auth-attempt lookup by `userIdentifier` (Integration API)

File: `ezkey-integration-api/.../IntegrationApiAuthAttemptController.java`

- Resolves via
  `findByIntegrationIdAndUserIdentifierAndStatusAndActive(integrationId, userIdentifier, VERIFIED, true)`.
- Scope is **the API key’s integration** (implicitly one tenant). Not global.
- PAM-relevant: Linux username as `userIdentifier` is already **integration-scoped** at lookup
  time; uniqueness is **not** enforced, so multi-device same username is possible and requires
  disambiguation (or prefer `enrollmentId`).

### 1.6 API surfaces that create enrollments

| Surface | Creates enrollment? | Uniqueness enforced |
|---------|---------------------|---------------------|
| Admin API `POST /api/v1/enrollments` | Yes (`EnrollmentService.create`) | Active VERIFIED name per integration |
| Admin provisioning / bootstrap (admin MFA) | Yes (system integration) | Same pattern on system integration |
| Integration API | **No** enrollment create controller found | Auth-attempt create only (lookup) |
| Auth API | Bind / verify only | Verify-time name + device-key checks |

### 1.7 Tests that lock current behavior

| Test | What it proves |
|------|----------------|
| `ezkey-core/.../EnrollmentUniquenessTest` | Create rejects active VERIFIED same name; allows inactive VERIFIED; allows multiple CREATED. |
| `ezkey-tests/.../EnrollmentUniquenessIntegrationTest` | API 409 on active duplicate; create OK when inactive VERIFIED; **DB index** blocks second VERIFIED even if first is inactive; index name `idx_enrollment_unique_verified_name` present. |

Living prose: `docs/LIFECYCLE_GOVERNANCE.md` §3.3 — “Name uniqueness is status-based, not
active-based.”

### 1.8 Ambiguities (characterization preferred over Isabelle)

| Ambiguity | Why | Suggested characterization (no live stack required if unit/repo) |
|-----------|-----|-------------------------------------------------------------------|
| Does `AdminRecoveryService.resetEnrollment` clear `device_public_key_hash`? | Code sets `devicePublicKey = null` but never `setDevicePublicKeyHash(null)`. DB unique index is **not** status-filtered. | Unit/repo test: after reset, assert hash column null **or** document intentional retention; if retained, assert re-verify of **same** row with same device still succeeds (UPDATE same PK), and that a **different** enrollment cannot take the same hash while the old hash row remains non-null. |
| Do operators put Linux username in `enrollment_name` vs `user_identifier`? | Explains “username uniqueness” reports despite no DB unique on `user_identifier`. | Admin UI / Demo Device / PAM dogfood audit of which field is filled; optional query counting non-null `user_identifier` vs names matching usernames. |
| Cross-tenant duplicate `user_identifier` already possible? | No unique constraint → yes in theory. | SQL: `SELECT user_identifier, COUNT(DISTINCT i.tenant_id) … GROUP BY 1 HAVING COUNT(DISTINCT i.tenant_id) > 1` on a multi-tenant dogfood DB (Isabelle optional). |
| Admin “same email two tenants” | Blocked by `uq_admin_email` / `existsByEmail` — **admin**, not enrollment. | Already evidenced by V1/V2 + `AdminProvisioningService`. |

**Isabelle / live stack:** not required for this exploration. Existing uniqueness integration tests
already cover the deactivate-vs-verify trap for **enrollment_name**.

---

## 2. Target design sketch (analysis, not decision)

### Hypothesis (Marc’s desired next level)

**Uniqueness of enrollment identity fields compartmentalized per tenant** — mandatory uniqueness
**within** a tenant only; the same `user_identifier` / `contact_email` / (optionally)
`enrollment_name` may exist in tenant A and tenant B.

Because enrollment has no `tenant_id` today, “per tenant” in SQL means either:

1. **Composite via join:** unique on `(integration.tenant_id, …)` expressed as a unique index that
   includes a denormalized `tenant_id` column on `ezkey_enrollment`, or
2. **Keep per-integration uniqueness** (already true for `enrollment_name`) and treat “per tenant”
   as a product rule: one integration per tenant for PAM, or accept same name across integrations
   inside one tenant.

### Alternatives (brief)

| Alternative | Idea | Pros | Cons |
|-------------|------|------|------|
| **A. Per-tenant uniqueness** (hypothesis) | Composite unique `(tenant_id, field)` | Matches multi-hat mental model | Needs denormalized `tenant_id` or complex exclusion constraints; careful with system tenant / admin MFA |
| **B. Stay per-integration** (status quo for names) | Unique `(integration_id, enrollment_name)` where VERIFIED | Already shipped; PAM lookup already integration-scoped | Same name across two integrations in one tenant allowed; deactivate still traps verify |
| **C. Soft-unique excluding terminal statuses** | Partial unique where status ∈ {VERIFIED} **and** active (or exclude deactivated) | Fixes deactivate→re-verify without revoke | Weakens “deactivate is reversible without destroying credential identity”; two VERIFIED inactive+active same name possible if not careful |
| **D. Release key on deactivate** | On deactivate, transition status away from VERIFIED **or** rename/suffix old name | Unblocks re-create | Breaks “reactivate without re-enroll” story in LIFECYCLE §3.3 |
| **E. Explicit supersession** | Deactivate+create atomically revokes or INVALID-archives prior VERIFIED | Clear operator path | New workflow; closer to recovery/reset than soft deactivate |

**Patrick craft note:** prefer **explicit tenant (or integration) scope in unique keys** over clever
status-only filters alone. Soft-delete / deactivate that **still holds** a unique key is exactly
the operational trap Marc hit (`VERIFIED` + `active=false` still occupies
`idx_enrollment_unique_verified_name`).

---

## 3. Impact analysis if generalized to per-tenant uniqueness

Assume product locks: “`user_identifier` and/or `contact_email` unique per tenant among active
VERIFIED enrollments; `enrollment_name` remains per-integration or also becomes per-tenant.”

### 3.1 SQL / migrations

- **Today:** no unique on `user_identifier` / `contact_email`; name unique is per-integration
  VERIFIED-only.
- **Would need:**
  - Likely add denormalized `tenant_id` on `ezkey_enrollment` (FK + backfill from integration) **or**
    PostgreSQL unique index on an expression/join is not practical — denormalize.
  - New partial unique indexes, e.g.
    `UNIQUE (tenant_id, user_identifier) WHERE user_identifier IS NOT NULL AND enrollment_status = 'VERIFIED' AND enrollment_active` —
    exact predicate is a **product** choice (active-only vs all VERIFIED).
  - Conflict detection query before cutover: find duplicate `(tenant_id, user_identifier)` across
    existing rows (should be rare/empty if dogfood never duplicated; possible if multi-device
    intentionally shares identifier — **multi-device directly conflicts with unique user_identifier**).
  - **Multi-device tension:** current Integration API explicitly supports multiple active VERIFIED
    enrollments per `user_identifier`. Per-tenant unique `user_identifier` would **break**
    multi-device unless uniqueness is narrowed (e.g. unique only when a “primary” flag) or
    multi-device moves to a different model.
  - Dropping/replacing `idx_enrollment_unique_verified_name` only if name scope changes; otherwise
    leave it.
  - Device / proof token hashes should likely **remain globally unique** (cryptographic one-time
    identity), not tenant-scoped.

### 3.2 URL / routing / payloads

- Admin UI routes are enrollment-id / integration-id based (`/enrollments/{id}`) — **no** assumption
  of global username in URLs observed for end-user enrollments.
- QR / bind payload (`QrCodePayloadService`): `enrollmentId`, `enrollmentProofToken`, optional
  `authUrl` — **no** tenant id required for bind; tenant context is implicit via enrollment row.
- Deep links: unlikely blocked by uniqueness scope; verify if any operator search assumes global
  email → single enrollment.

### 3.3 API surface

- Create/update: new **409** (or keep 409) problem types when tenant-scoped duplicate
  `user_identifier` / email; clarify detail strings (“this tenant” vs “this integration”).
- OpenAPI refresh via normal clean-start + `update-specs` if contracts change (not in this PR).
- Integration API auth-attempt by `userIdentifier`: **must stay integration-scoped** (API key).
  Per-tenant unique does not remove the need for explicit integration context; it only constrains
  how many VERIFIED actives share an identifier **inside** a tenant (possibly across integrations).
- Error codes: prefer **409 Conflict** for uniqueness (existing name pattern) over 422 unless
  validation-framework constraints are used.

### 3.4 Mobile / Demo Device / CLI

- Demo Device groups by tenant for display (`EnrollmentTenantGrouper`) — assumes multiple
  enrollments can coexist; unlikely to require global uniqueness.
- Mobile installation-scoped identity is a **separate** backlog track
  (`I-2026-07-20-mobile-installation-scoped-enrollment-identity`) — do not conflate with server
  uniqueness scope.
- CLI / PAM: Linux username → `userIdentifier` lookup already integration-scoped; changing to
  per-tenant unique mainly affects **operator create** conflicts, not lookup algorithm — unless
  multi-device is removed.

### 3.5 Security / isolation

- Wrongly scoping unique keys (e.g. unique on `user_identifier` alone without tenant) would
  reintroduce cross-tenant blocking and is worse than today.
- Keeping **admin** email/username global while allowing enrollment contact emails per tenant is
  coherent: admins are instance operators; end users are tenant customers.
- Whether **enrollment `contact_email` should stay globally unique for abuse/support** is an open
  product question — today it is **not** unique at all. Introducing global-unique email would be a
  **tightening**, not a relaxation.
- Device public key global uniqueness remains a anti-hijack control; tenant-scoping device keys
  would allow the same hardware key material in two tenants (may be desirable for multi-hat phones)
  — security review (Christophe) if that boundary moves.

---

## 4. Phased “change the wheel while driving” plan

Preparatory phases; each independently shippable / reversible where noted. Adjust after product
lock. **No phase is authorized by this document alone.**

### Phase 0 — Characterization (lock current behavior)

- Extend / cite existing `EnrollmentUniquenessTest` + `EnrollmentUniquenessIntegrationTest`.
- Add focused tests: deactivate → create OK → verify fails; revoke → create → verify OK; reset
  clears name slot; document `device_public_key_hash` after reset.
- Optional: repo query for cross-tenant duplicate `user_identifier` in dogfood DB.
- **PAM / single-tenant PME:** no behavior change.

### Phase 1 — Document + ADR draft

- Promote accepted direction to ADR (or explicitly reject).
- Decide: per-tenant vs per-integration; which fields; interaction with multi-device; deactivate
  vs unique slot policy.
- Update `LIFECYCLE_GOVERNANCE.md` only after ADR accept.
- **No schema change.**

### Phase 2 — Additive schema / dual constraints

- Backfill denormalized `tenant_id` on enrollment if needed (nullable → NOT NULL).
- Add **new** composite unique indexes **in addition to** existing ones where safe.
- Or: widen validation queries to tenant scope without dropping global/integration indexes yet.
- Keep old `idx_enrollment_unique_verified_name` until Phase 4.

### Phase 3 — Dual-check period

- Service layer enforces new scope; metrics/logs on would-be cross-tenant collisions (should be
  zero) and same-tenant duplicates.
- Data migration / conflict report for any multi-device rows that would violate new unique
  `user_identifier`.
- Feature flag or config if needed for PME dogfood.

### Phase 4 — Cut over

- Drop obsolete unique indexes; flip validation messages; OpenAPI / Bruno copy.
- Operator messaging: deactivate does / does not free the identity key (must match ADR).

### Phase 5 — Admin UI / operator UX

- Multi-tenant same email/username guidance; clearer deactivate vs revoke vs reset.
- Search/filter copy that identity is tenant-scoped.

### What can ship without breaking PAM dogfood / single-tenant PME

- Phase 0–1 entirely.
- Deactivate/verify **messaging** clarity (docs/UI only) without schema change.
- Denormalized `tenant_id` backfill with **no** new unique index yet (additive, low risk).
- **Avoid** making `user_identifier` unique while PAM relies on multi-device same username without
  an alternate disambiguation story.

---

## 5. Go / no-go for further work

| Gate | Recommendation |
|------|----------------|
| **Marc / Alex product intent** | **Keep this as exploration** until they lock: (1) which fields must be unique, (2) per-tenant vs per-integration, (3) multi-device vs unique `user_identifier`, (4) whether deactivate should free the name slot. |
| **Pre-paradigm 80/20** | Decide quick wins in [`ENROLLMENT_UNIQUENESS_QUICK_WINS_80_20.md`](ENROLLMENT_UNIQUENESS_QUICK_WINS_80_20.md) **before** funding per-tenant uniqueness work. Recommended: docs/UI (A), then one of explicit `SUPERSEDED`/Replace (D) vs partial-unique+active (C). |
| **Christophe** | Flag **only if** device-key uniqueness becomes tenant-scoped, or email uniqueness is used as an anti-abuse trust boundary. Name-per-integration and admin global username are existing posture. Quick-win Option E (end-user reset / key clear) also warrants a glance. |
| **Patrick craft** | Prefer explicit `(tenant_id, …)` or keep honest per-integration keys; do not “fix” deactivate with a status-only soft-unique that still surprises operators. Soft-deactivate holding a VERIFIED unique key **is** the trap — see complement craft verdict (explicit Replace/`SUPERSEDED` over clever `active` predicates). |
| **Isabelle** | Not required unless inventory gaps above stay ambiguous after characterization tests. |
| **This PR** | Documentary only — **no merge urgency**. |

### Executive finding (for PR reviewers)

1. **Enrollment name uniqueness is not instance-global** — it is **per `integration_id`**, partial
   unique on `VERIFIED` (`idx_enrollment_unique_verified_name`).
2. **Deactivated rows still block re-verify** (and occupy the DB unique slot) because status remains
   `VERIFIED`; create alone may succeed when `active=false`.
3. **`user_identifier` / `contact_email` are not uniquely constrained** today (multi-device-friendly
   lookup). Cross-tenant duplicate usernames at enrollment level are **already allowed** by schema.
4. **Truly global username/email uniqueness applies to `ezkey_admin`**, not to enrollment contact
   fields — separate that pain from enrollment scope work.
5. Desired “per-tenant uniqueness” is therefore less about relaxing a global enrollment unique key
   (it does not exist for user/email) and more about **(a)** fixing the deactivate/VERIFIED slot
   trap for names, **(b)** possibly introducing tenant-scoped uniqueness for fields that are
   currently unconstrained, and **(c)** optionally relaxing **admin** global identity for
   multi-hat operators — a different change set.

---

## Appendix — Key file index

| Path | Role |
|------|------|
| `ezkey-core/src/main/resources/db/migration/V1__core_domain_and_multi_tenant.sql` | Admin username UNIQUE; tenant table |
| `ezkey-core/src/main/resources/db/migration/V2__…sql` | `uq_admin_email`; proof/device key hash uniques |
| `ezkey-core/src/main/resources/db/migration/V5__…sql` | `idx_enrollment_unique_verified_name`; user_identifier index; integration code per tenant |
| `ezkey-core/.../enrollment/domain/entity/Enrollment.java` | JPA mapping |
| `ezkey-core/.../enrollment/service/EnrollmentService.java` | Create-time name check |
| `ezkey-core/.../enrollment/service/EnrollmentVerifyService.java` | Verify-time name + device key |
| `ezkey-admin-api/.../EnrollmentRevocationService.java` | Deactivate vs revoke |
| `ezkey-admin-api/.../AdminRecoveryService.java` | Reset → CREATED |
| `ezkey-admin-api/.../EnrollmentUpdateService.java` | PATCH name uniqueness |
| `ezkey-integration-api/.../IntegrationApiAuthAttemptController.java` | userIdentifier lookup |
| `docs/LIFECYCLE_GOVERNANCE.md` §3.3 | Living uniqueness prose |
| `ezkey-tests/.../EnrollmentUniquenessIntegrationTest.java` | DB + API characterization |
| `docs/analysis/ENROLLMENT_UNIQUENESS_QUICK_WINS_80_20.md` | 80/20 complement — ranked quick wins before paradigm |
