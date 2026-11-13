# Java Tenant SQL Isolation Assessment

**Date:** 2026-09-15
**Keyword:** `assessment-curated`
**Scope:** Java services and repository SQL in `ezkey-core`, `ezkey-admin-api`, `ezkey-auth-api`,
`ezkey-integration-api` (plus `ezkey-core-security` for completeness — no tenant-owned
repositories there).
**Method:** White-box review of repositories, service query builders, and the service context
that calls them; live clean-start stack probes for the oracles in the HITL lot. No hostile
exploitation of a production deployment.
**Re-verified:** 2026-11-13 on current `main` — see §11 (live evidence, corrections, execution plan).
**Operator HITL lane:**
[`product-docs/global/hygiene/java-tenant-sql-isolation/`](../product-docs/global/hygiene/java-tenant-sql-isolation/)
**Sibling (do not reopen closed controller HITL):**
[`java-controller-role-validation-assessment-2026-08.md`](java-controller-role-validation-assessment-2026-08.md)

## 1. Mandate (locked)

| Line | Content |
| --- | --- |
| **Surface** | Java services + the SQL they run (Spring Data derived queries, JPQL, native SQL, JPA `Specification` / Criteria). Controllers are in scope only as the caller that feeds `tenantId` / ACS into those services. |
| **Attention axes** | Tenant space integrity. A Tenant Admin, an Integration API key, or ordinary Auth / API-key usage must not escalate into another tenant, and must not gain visibility (row contents **or** hide-existence-breaking oracles) onto Global Admin space. Product intent: Global Admin is platform/IT; Tenant Admin is business-scoped; isolation is a trust property of the product. |
| **Non-goals** | Implementing remediations until HITL; doctor/pentest tool shortlists; SQL injection (see [`docs/security/SQL_INJECTION_POSTURE_AUDIT.md`](security/SQL_INJECTION_POSTURE_AUDIT.md)); Hibernate `@Filter` retrofit as a default; reopening closed CTRL-ROLE-001 QR; inventing `I-*` / `TB-*` per finding; Admin UI gating. |

## 2. Executive summary

Ezkey does **not** enforce tenant isolation in SQL. Repositories are ID-, token-, integration-, or
proof-hash-scoped. Tenant filtering is an **application** concern: list `Specification`s take a
`tenantId` from the principal, and get-by-id/mutate paths load the row then ask
`AccessControlService` (or an inline tenant compare). There is no Hibernate `@Filter` / tenant
interceptor. That architecture is coherent with Auth API proof-token flows (secret-bound, not
tenant-bound) and with Global Admin as a designed cross-tenant operator.

**No confirmed live path was found that returns another tenant's row contents** (names, enrollments,
API-key material, auth-attempt payloads) to a Tenant Admin or to a foreign Integration API key.
List endpoints overwrite request `tenantId` from the principal. Dashboard aggregates use
`principal.tenantId()`. Audit search and audit **context** hide out-of-scope anchors as 404.
Auth API bind / pending / respond stay on proof-token + device crypto with generic client errors.

The residual class this pass names is **existence and identity oracles** created when a service
runs an **unscoped SQL load** and then branches to **distinct client-visible outcomes**:

1. Integration API `findById(enrollmentId)` then different Problem `detail` for missing vs
   other-integration (hence other-tenant) rows.
2. Admin `getAdminById` unscoped `findById` then HTTP 404 vs 403 — Tenant Admin can tell that
   Global Admin id `1` exists.
3. `existsByUsername` / `existsByEmail` are instance-global; Tenant Admin peer-create reports
   "already exists" for Global Admin identities.

Those are not MFA takeovers and not dumps of foreign tenant data. They **do** violate the
hide-existence posture the product already claims for operator reads
(`docs/API_SECURITY_MATRIX.md`, Admin API `AGENTS.md` § Authorization). Sequential integer
primary keys make enumeration cheap. That is the gap between "the SQL cannot read tenant B's
row into tenant A's list" (holds) and "a tenant cannot learn that tenant B / Global Admin
space exists" (does not fully hold).

Unscoped `findAll` / `getById` helpers remain fail-open if a future caller skips ACS
(CTRL-ROLE-008 / SEC-015). No production HTTP caller of those helpers was found in this pass.

## 3. How isolation is supposed to work

### 3.1 Product roles

| Actor | Intended data plane |
| --- | --- |
| **Global Admin** | Platform operator. Reads across tenants. Creates integrations in the **system tenant** only (no impersonation). Manages tenants, encryption keys, alerts, audit-chain. |
| **Tenant Admin** | Business operator of one tenant. Lists and mutates only that tenant's integrations, enrollments, API keys, auth attempts, peer admins, tenant-scoped audit. Must not see other tenants or Global Admin MFA / platform rows. |
| **Integration API key** | Machine principal bound to **one integration** (hence one tenant). Creates/waits/cancels auth attempts for enrollments of that integration only. |
| **Auth API device** | Capability token (`enrollmentProofToken`) + device key. No admin role. Isolation is cryptographic association, not a tenant predicate. |

Canon: [`product-docs/global/product-intent.md`](../product-docs/global/product-intent.md) § Target
audience (Global Admin vs Tenant Admin);
[`docs/LIFECYCLE_GOVERNANCE.md`](LIFECYCLE_GOVERNANCE.md) §3.5;
[`ezkey-tests/reference/MULTI_TENANT.md`](../ezkey-tests/reference/MULTI_TENANT.md).

### 3.2 Ownership chain

```
Tenant ← Integration ← Enrollment ← AuthAttempt
              ↑
           ApiKey
EzkeyAdmin.tenant  (null for Global Admin)
```

Admin MFA enrollments live on the **system integration** (system tenant). Tenant Admins reach
their own onboarding via admin-scoped APIs (`/admins/{id}/onboarding`), not
`GET /enrollments/{id}` (that path is enrollment → integration → tenant, so system-integration
rows 403 for a business Tenant Admin). That split is intentional.

### 3.3 Enforcement layers (no automatic SQL filter)

| Layer | Mechanism |
| --- | --- |
| Repository SQL | Almost entirely tenant-blind. Exceptions: `findByTenantTenantId*`, `findByIntegration_Tenant_TenantId`, `existsByCodeAndTenant`. |
| List services | `Specification` / Criteria: `tenantId != null` → `integration.tenant.tenantId = :id` (or `audit.tenantId = :id`). `null` means Global Admin / all tenants. |
| Get-by-id / mutate | Load by PK, then `AccessControlService.canAccess*` or inline tenant compare. |
| Auth / device | Proof-token hash + enrollment id match; generic failure strings. |
| Integration API | API key → integration id principal; `resolveEnrollmentId` then ACS on wait/cancel. |

`AccessControlService` (Admin API) fail-closes when the principal or `tenantId` is missing.
Global Admin short-circuits to `true` without a tenant predicate (designed).

## 4. Inventory (SQL posture)

Sixteen `*Repository.java` files, all under `ezkey-core`. Native SQL that **locks or updates by
PK / encryption-key id** is tenant-blind (enrollment lock, auth-attempt lock, re-encryption
sweeps, token expiry). Native SQL that **resolves tenant labels** (`findTenantInfoByIntegrationId`,
`findTenantInfoByAdminEnrollmentId`) is used on Auth API bind for the enrollment the proof token
already selected — not a list across tenants.

Alerts, encryption keys, keyset blob, and audit-chain tables are **instance-scoped**. Controllers
gate those with `ROLE_GLOBAL_ADMIN`. Dashboard omits alerts and integrity jobs for Tenant Admin.

## 5. Findings register

Severity: **P0** auth/data-plane bypass with low prerequisites; **P1** material isolation break
(foreign **contents** or privilege escalation); **P2** hide-existence / identity oracle or
fail-open service SQL; **P3** coherence / docs / defense-in-depth.

Confidence: **Confirmed** = source-evident and/or live-probed; **Hypothesis** = needs more
dynamic evidence.

### HITL lot (this pass)

| ID | Title | Severity | Confidence | Quick win |
| --- | --- | --- | --- | --- |
| SQL-ISO-001 | Integration API unscoped `enrollmentRepository.findById` + distinct Problem details (missing vs other-integration) | P2 | Confirmed | **closed** 2026-11-13 — missing and other-integration share HTTP 404 `resource/not-found` |
| SQL-ISO-002 | Admin `getAdminById` unscoped `findById` then 404 vs 403 (Global Admin existence) | P2 | Confirmed | **closed** 2026-11-13 — every `/admins/{id}` route answers 404 for missing and out-of-tenant alike (`AdminProvisioningService.loadAdminVisibleTo`) |
| SQL-ISO-003 | `existsByUsername` / `existsByEmail` instance-global; Tenant Admin peer-create identity oracle | P2 | Confirmed | **suppress** 2026-11-13 — accepted posture: usernames and emails are instance-global because login is; a generic 400 would still differ from 201 |
| SQL-ISO-004 | Admin auth-attempt create Path 2/3 looks up `userIdentifier` by **request** `integrationId` before ACS | P2 | Confirmed (static) | **closed** 2026-11-13 — `canAccessIntegration` runs before the `userIdentifier` lookup; missing and foreign integration share 404 |
| SQL-ISO-005 | Core `findById` / `findAll` / `revokeKey` remain unscoped; isolation is caller discipline | P2 | Confirmed residual | **closed** 2026-11-13 — contract in `ezkey-core/AGENTS.md` § Tenant scope of service loaders, pointer in `ezkey-admin-api/AGENTS.md`, Javadoc on the loaders |

### Category 2 — noted, not in this HITL lot

| ID | Title | Why noted only |
| --- | --- | --- |
| SQL-ISO-006 | API-key GET 404 (missing) vs 403 (foreign) after `getApiKey(id)` | **Closed 2026-11-13** with SQL-ISO-002: get, update and revoke answer 404 for missing and foreign alike, same audit row. |
| SQL-ISO-007 | `existsByDevicePublicKeyHash` is instance-global | Product anti-hijack (one device key, one verified enrollment). Client error is generic. Not an HTTP oracle of another tenant's key. |
| SQL-ISO-008 | Native UPDATE/lock SQL without `tenant_id` | Schedulers and re-encryption are instance jobs (Global Admin / internal). Not a Tenant Admin query path. |
| — | CTRL-ROLE-001 QR | Closed; Auth bind remains token-gated. |
| — | CTRL-ROLE-008 / SEC-015 `findAll` | Same residual as SQL-ISO-005; not a second live HTTP path. |

## 6. What looks solid (do not relitigate without new evidence)

1. **List Specs overwrite tenant scope from the principal.** Integrations, enrollments, auth
   attempts, API keys, admins, audit logs: Tenant Admin `tenantId` wins over query `tenantId`.
2. **Dashboard** passes `principal.tenantId()` into the same Specs; alerts / integrity jobs are
   Global Admin only.
3. **Audit context** loads by id then `isVisibleToRequester`; out-of-scope anchors throw
   `ResourceNotFoundException` (hide-existence). Gold pattern for SQL-ISO-001/002.
4. **Auth API bind** uses `findByEnrollmentIdAndEnrollmentProofTokenHash`; missing and wrong
   token share `"Enrollment binding failed"`. Pending looks up by proof-token hash, then checks
   id match, still generic `"Authentication request failed"`.
5. **Enrollment instance-info** looks up by proof-token hash only (anti-enumeration).
6. **Integration API wait/cancel** `@PreAuthorize` ACS: attempt → enrollment → **this**
   integration id.
7. **API-key authentication** binds the security principal to one integration; subsequent
   `userIdentifier` lookup is `findByIntegrationIdAndUserIdentifier…`.
8. **Tenant Admin cannot list Global Admins** (`tenant_id` IS NULL excluded by
   `findByTenantTenantIdForAdminProvisioningList`).
9. **Audit search for Tenant Admin** is `tenant_id = requesterTenantId`; NULL-tenant system
   rows are excluded.
10. **Enrollment create** for a foreign integration is denied by `canAccessIntegration` before
    `EnrollmentService.create`. Path-1 Admin auth-attempt create (enrollment id only) hits ACS
    with missing and foreign both `false` → 403 (no distinct "not found").
11. **Existing P0 suite** `TenantCrossIsolationSecurityTest` still encodes list + get-by-id
    isolation for integrations, enrollments, API keys.

## 7. Finding details

### SQL-ISO-001 — Integration API enrollment id oracle

**Verdict:** An Integration API key can distinguish "this enrollment id does not exist" from
"this enrollment id exists on another integration" (another tenant, or another integration in
the same tenant) because `resolveEnrollmentId` runs unscoped `findById` and copies distinct
exception messages into RFC 9457 `detail`.

**SQL:** `EnrollmentRepository.findById` (inherited `JpaRepository`, no tenant predicate).

**Caller:**
`ezkey-integration-api` `IntegrationApiAuthAttemptController.resolveEnrollmentId` Path 1.

```589:613:ezkey-integration-api/src/main/java/org/ezkey/integration/api/controller/IntegrationApiAuthAttemptController.java
    if (enrollmentId != null && userIdentifier == null) {
      // ...
      Enrollment enrollmentForPath1 =
          enrollmentRepository
              .findById(enrollmentId)
              .orElseThrow(
                  () ->
                      new AuthAttemptCreateValidationException(
                          "Enrollment not found for ID: " + enrollmentId));
      if (!integrationIdForPath1.equals(enrollmentForPath1.getIntegrationId())) {
        // ...
        throw new AuthAttemptCreateValidationException(
            "Enrollment does not belong to this integration.");
      }
```

`GlobalExceptionHandler.handleAuthAttemptCreateValidationException` puts `ex.getMessage()` on
the Problem `detail` (HTTP 400). Inactive **own**-integration enrollments take a later 403
(`EnrollmentInactiveException`) — that dialect is in-tenant and is not this finding.

**Observation scenario:** Tenant A holds a valid API key. Tenant B has enrollment id `N`
(CREATED is enough; the integration check runs before VERIFIED). `POST /api/v1/auth-attempts`
with A's key and `enrollmentId=N` returns 400 `Enrollment does not belong to this integration.`
The same call with `enrollmentId=2000000000` returns 400 `Enrollment not found for ID:
2000000000`. Neither response includes B's names; the **existence** of `N` is confirmed.
Enrollment ids are sequential integers.

**Fail-closed vs fail-open:** The **use** of B's enrollment is fail-closed (attempt is not
created). The **existence** signal is fail-open relative to hide-existence.

**Related:** CTRL-ROLE treated `resolveEnrollmentId` as the ownership check that made a second
controller re-check redundant. That remains true for *authorization to create*. This finding is
the *error dialect* after that SQL load.

**Recommended GO:** Unify missing and foreign-integration outcomes to one client-visible
Problem (prefer hide-existence 404 `resource-not-found`, or one generic 400). Do not return the
numeric id in `detail`. Keep WARN logs server-side. Add a functional test that both cases share
status + type (or `detail`). Out of GO: Hibernate tenant filters; changing API-key principal
shape.

**HITL 2026-09-15:** operator **GO** (`fix`). **Closed 2026-11-13** with HTTP 404 `resource/not-found` (the other authorized shape; see §11, closure). Scope at HITL:
Authorized default: keep HTTP 400 + one generic `detail`; 404 `resource-not-found` also
authorized.

---

### SQL-ISO-002 — Global Admin existence via `getAdminById`

**Verdict:** Tenant Admin `GET /api/v1/admins/{id}` reveals whether a Global Admin (or any
foreign-tenant admin) row exists: 403 if present and out of tenant, 404 if absent.

**SQL:** `EzkeyAdminRepository.findById` then compare `admin.tenant.tenantId` to
`principal.tenantId()`. The Global Admin row never matches a business tenant (bootstrap
`admin.docker` has `tenant_id = 1`, the system tenant; a null tenant is also handled) → mismatch
→ `IllegalArgumentException`.

```907:923:ezkey-admin-api/src/main/java/org/ezkey/admin/service/AdminProvisioningService.java
    EzkeyAdmin admin =
        adminRepository
            .findById(adminId)
            .orElseThrow(() -> new ResourceNotFoundException("Administrator", adminId));
    // ...
    if (requesterPrincipal.isTenantAdmin()) {
      Integer requesterTenantId = requesterPrincipal.tenantId();
      Integer adminTenantId = admin.getTenant() != null ? admin.getTenant().getTenantId() : null;
      if (requesterTenantId == null || !requesterTenantId.equals(adminTenantId)) {
        throw new IllegalArgumentException(
            "Tenant administrators can only access admins in their tenant");
      }
    }
```

Controller maps `ResourceNotFoundException` → 404 and `IllegalArgumentException` → 403.
Bootstrap Global Admin is typically id `1`. Sequential ids enumerate the admin table.

**Product stake:** The mandate asked specifically whether a tenant can gain visibility onto a
**Global Admin**. List isolation holds (GA rows never appear in the tenant-scoped list). Get-by-id
does not hide GA existence.

**Related:** CTRL-ROLE-003 hide-existence target; 003b parked JSON GET 403s as later peels.
This pass names the **Global Admin** impact so the peel is prioritized on `/admins/{id}` rather
than treated as generic 403/404 noise.

**Recommended GO:** Same hide-existence 404 + `admin/resource-not-found` as enrollment QR.
Do not return 403 for foreign-or-missing. Tests: TA GET GA id and TA GET unused id share 404;
TA GET own peer remains 200. Out of GO: dropping global unique usernames; changing list SQL.

**HITL 2026-09-15:** operator **GO** (`fix`). **Closed 2026-11-13** (see §11, closure). Scope at HITL:
Authorized: Tenant Admin missing / other-tenant / GA share 404; PATCH foreign follows;
onboarding 400 is a related peel, not required in this slice.

---

### SQL-ISO-003 — Global username / email uniqueness oracle

**Verdict:** Username and email uniqueness SQL is instance-wide. A Tenant Admin creating a peer
learns whether a Global Admin (or any other tenant's admin) already uses that username or email.

**SQL:** `EzkeyAdminRepository.existsByUsername` / `existsByEmail` (no tenant predicate).
Login is global (`findByUsername`), so uniqueness is a real product constraint, not an accident.

```405:412:ezkey-admin-api/src/main/java/org/ezkey/admin/service/AdminProvisioningService.java
    if (adminRepository.existsByUsername(username)) {
      throw new IllegalArgumentException("Username already exists: " + username);
    }
    if (email != null && !email.isBlank() && adminRepository.existsByEmail(email)) {
      throw new IllegalArgumentException("Email already exists: " + email);
    }
```

The message is returned to the Tenant Admin (400). Same oracle exists on Global Admin create
(expected for the platform operator).

**Observation scenario:** Tenant Admin posts `POST /api/v1/admins/tenant` with the bootstrap
Global Admin username. Response is 400 mentioning the username already exists. A random unused
username proceeds (201) or fails for other reasons.

**Recommended GO:** Keep global uniqueness (login key). Change the **client** string for Tenant
Admin (and optionally Global Admin) to a generic provisioning failure that does not confirm
existence; log the collision at INFO/WARN with the requester id. Optional later: reserved
namespace for Global Admin usernames. Out of GO: per-tenant usernames (would break global
login).

**HITL 2026-09-15:** operator **GO** (`fix`). **Re-dispositioned 2026-11-13: `suppress`** (operator; see §11). Scope considered at HITL:
Authorized: keep uniqueness; generic HTTP 400 detail (no “already exists”, no echoed
identifier); align unique-constraint race path. Honest limit: 400 vs 201 still signals a
taken name. Out of GO: per-tenant usernames.

---

### SQL-ISO-004 — Admin auth-attempt `userIdentifier` lookup before ACS

**Verdict:** Path 1 (enrollment id only) is hide-existence-consistent (ACS `false` for missing
and foreign). Paths 2 and 3 query
`findByIntegrationIdAndUserIdentifierAndStatusAndActive(request.integrationId, …)` **before**
`canAccessEnrollment`. Empty → 400 "No verified enrollment found for userIdentifier '…'". Hit
on a foreign integration → ACS 403. That pair is a cross-tenant
`(integrationId, userIdentifier)` existence oracle. Integration ids are sequential.

**SQL:** enrollment derived query scoped to integration, **not** to caller tenant.

**Caller:** `AuthAttemptController.resolveEnrollmentId` Paths 2–3, then ACS on the resolved id.

**Recommended GO:** If `integrationId` is present, `canAccessIntegration` first (foreign and
missing → 404). Only then run the userIdentifier SQL. Align empty and unauthorized to the same
client outcome. Out of GO: changing userIdentifier uniqueness rules.

**HITL 2026-09-15:** operator **GO** (`fix`). **Closed 2026-11-13** (see §11, closure). Scope at HITL:
Authorized: ACS-first on request `integrationId`; Tenant Admin missing/foreign share 404;
own-integration empty may stay 400. No live probe in pass-1; implementing session adds it.

---

### SQL-ISO-005 — Unscoped service loaders (defense-in-depth)

**Verdict:** `EnrollmentService.getById` / `getAll`, `IntegrationService.findById` / `getAll`,
`AuthAttemptService.getById` / `getAll` / `create`, `ApiKeyService.getApiKey` / `findAll` /
`revokeKey`, `EnrollmentRevocationService` mutations, `AdminProvisioningService.listAdmins(tenantId)`
all trust the caller to pass a scoped principal or to have already called ACS. SQL will happily
return or mutate any PK.

Live Admin/Integration HTTP paths reviewed in this pass **do** wrap get-by-id. No production
caller of `findAll`/`getAll` was found (tests only). This is the same residual as CTRL-ROLE-008
and SEC-015, restated at the **service SQL** layer because that is this mandate.

**Recommended GO:** Document in `ezkey-core` / Admin API `AGENTS.md` that unscoped loaders are
internal and that new HTTP surfaces must ACS or pass principal `tenantId` into Specs. Optional
follow-up: `requireAccessible*(principal, id)` helpers. Do **not** add Hibernate filters in this
GO (Auth proof-token paths would fight them). Out of GO: deleting `findAll` without a caller
audit.

**HITL 2026-09-15:** operator **GO** (`fix`). **Closed 2026-11-13:** contract written in
`ezkey-core/AGENTS.md` § Tenant scope of service loaders, pointer under Admin API `AGENTS.md`
§ Authorization (object split), Javadoc on the listed loaders. No helpers added. Handoff
deleted.

## 8. Runtime evidence

Clean-start Docker stack (2026-09-15, this environment): Admin API `:9080`, Integration API
`:7080` healthy.

`mvn test -pl ezkey-tests -Dtest=TenantSqlIsolationOracleSecurityTest` — **3 tests, 0 failures**:

| Probe | Result |
| --- | --- |
| SQL-ISO-001 foreign enrollment id `5` (Tenant B) with Tenant A API key | HTTP **400** `detail=Enrollment does not belong to this integration.` Body does not contain Tenant B names. |
| SQL-ISO-001 unknown enrollment id `2000000000` | HTTP **400** `detail=Enrollment not found for ID: 2000000000` |
| SQL-ISO-002 Tenant Admin GET `/admins/1` (bootstrap Global Admin) | HTTP **403** (no username in body) |
| SQL-ISO-002 Tenant Admin GET `/admins/2000000000` | HTTP **404** |
| SQL-ISO-003 Tenant Admin `POST /admins/tenant` with username `admin.docker` | HTTP **400** `detail=Username already exists: admin.docker` |

Authorization remains fail-closed (no 201, no foreign row payload). The **dialects** above are
the oracles.

Regression: `TenantCrossIsolationSecurityTest` list/get samples (own integrations only; foreign
enrollment GET 403/404; own API keys only) — **3 tests, 0 failures**.


## 9. Related assessments (avoid duplicate HITL)

(Re-verification and execution plan: §11.)

| ID | Relation |
| --- | --- |
| CTRL-ROLE-001 | QR object check; closed. Auth bind still token-gated. |
| CTRL-ROLE-002 | `ROLE_ADMIN` is not isolation; object split remains ACS / principal. |
| CTRL-ROLE-003 / 003b | Hide-existence 404 target; JSON GET 403s parked. SQL-ISO-002 and SQL-ISO-006 are the next peels with a tenant-SQL mandate. |
| CTRL-ROLE-008 / SEC-015 | Unscoped `findAll`; SQL-ISO-005. |
| SEC-022 / 023 | API-key revoke/read object check closed; GET 403 vs 404 remaining as SQL-ISO-006. |
| SQL injection audit 2026-04 | Parameter binding; not tenant isolation. |

## 10. Explicit non-claims

This assessment does **not** assert that adding Hibernate `@Filter` is required, that Global
Admin cross-tenant **operator** reads are a defect (they are the platform role), or that
proof-token lookups should grow a tenant predicate (that would break Auth API).

## 11. Re-verification 2026-11-13 and execution plan

Two months and 564 commits after pass-1, every HTTP finding was replayed on current `main`
(clean-start stack). None had been fixed in between. Caller: Tenant Admin A bearer token, or
Tenant A API key for SQL-ISO-001.

| Finding | Row exists elsewhere | Row does not exist | Status |
| --- | --- | --- | --- |
| SQL-ISO-001 Integration API Path 1 | 400 `Enrollment does not belong to this integration.` | 400 `Enrollment not found for ID: …` | Reproduced |
| SQL-ISO-002 `GET /admins/{id}` (Global Admin id 1, Tenant B admin) | 403 empty | 404 | Reproduced |
| SQL-ISO-002 `PATCH /admins/{id}` | 400 `Tenant administrators can only access admins in their tenant` | 404 | Reproduced |
| SQL-ISO-002 `GET /admins/{id}/onboarding` | 400 `…onboarding credentials for admins in their tenant` | 404 | Reproduced (was out of the handoff) |
| SQL-ISO-003 `POST /admins/tenant`, taken username or email | 400 `Username already exists: …` / `Email already exists: …` | 201 | Reproduced |
| SQL-ISO-003 `PATCH /admins/{own id}` with a taken email | 400 `Email already exists: …` | 200 | Reproduced |
| SQL-ISO-004 Admin create, `userIdentifier` + Tenant B `integrationId` (Paths 2 and 3) | 403 empty | 400 `No verified enrollment found…` (also for an unknown integration id) | Reproduced — first live evidence |
| SQL-ISO-006 `GET /api-keys/{id}` | 403 empty | 404 | Reproduced |

Controls that hold: `GET /integrations/{id}` and `GET /enrollments/{id}` answer 403 for both
foreign and missing ids; Admin create Path 1 answers 403 for both.

**Corrections to pass-1:**

- The Global Admin row is not `tenant = null` on a bootstrapped stack: `admin.docker` has
  `tenant_id = 1` (system tenant). Outcome is unchanged. Tests must cover the system-tenant
  case, not only null.
- SQL-ISO-003 is wider than the Global Admin username: email too, any tenant's admin, and the
  PATCH of one's own profile.
- SQL-ISO-004 needs a `VERIFIED` and active enrollment carrying the `userIdentifier` on the
  foreign integration. The 2026-11-13 probe set that state by SQL on the lab stack, then
  reverted it.
- The probe test records dialects without locking them, and skips its three cases (green
  build) when admin bootstrap fails. Each fix PR tightens its own case.

**Execution plan (operator-approved grouping, 2026-11-13):**

1. This PR: register, campaign note, probe test, SQL-ISO-005 (docs and Javadoc).
2. One Admin API PR: SQL-ISO-002 (GET, PATCH, onboarding), SQL-ISO-004, SQL-ISO-006. Same
   module, same target (HTTP 404 `admin/resource-not-found`), one Bruno / ENDPOINT / OpenAPI
   refresh.
3. One Integration API PR: SQL-ISO-001 (public integrator contract).

SQL-ISO-003 — **`suppress`** (operator, 2026-11-13). Usernames and emails are instance-global
because login is. A generic 400 would still differ from 201, so the oracle would survive the fix
while the operator lost a useful message. Accepted posture; no code. Reopen only if usernames
become tenant-scoped.

### Closure 2026-11-13 — Admin API (SQL-ISO-002, SQL-ISO-004, SQL-ISO-006)

Replayed on a clean-start stack built from the fix:

| Route (Tenant Admin A) | Global Admin / other tenant | Missing |
| --- | --- | --- |
| `GET`, `PATCH /admins/{id}` | 404 `admin/resource-not-found` | same |
| `GET /admins/{id}/onboarding`, `/onboarding/qrcode` | 404 `admin/resource-not-found` | same |
| `POST /admins/{id}/recovery-codes/regenerate`, `/issue-initial` | 404 `admin/resource-not-found` | same |
| `GET`, `PATCH`, `DELETE /api-keys/{id}` | 404 `admin/resource-not-found` | same |
| `POST /auth-attempts` with `userIdentifier` + Tenant B `integrationId` | 404, for a hit and for a miss | 404 |
| `POST /auth-attempts` with a Tenant B `enrollmentId` | 404 `admin/resource-not-found` (was 403) | same |

Found while fixing, not in pass-1:

- The recovery-code POSTs had the same split (403 vs 404). Fixed with the rest.
- **The audit log was a second channel.** A Tenant Admin reads their own tenant's audit log, and
  the failure rows differed between missing and foreign (different `errorMessage`, or a row in one
  case and none in the other). Both cases now write the same row. Rule recorded in
  `ezkey-admin-api/AGENTS.md`.
- `deactivate`, `activate` and `activation-code/regenerate` are Global Admin only and already
  answer 403 for any id: no oracle.

**Posture 2026-11-13 (operator):** Ezkey is alpha with no production integrator, so contracts are
made uniform now rather than preserved. Applied here: every deny in this PR is the same 404
Problem (no empty 404, no 403 for "exists elsewhere"), Admin create answers 404 for a foreign or
missing enrollment as well, and a Global Admin gets 404 (not a "no enrollment" 400) for an
integration that does not exist.

The remaining 403-for-missing-and-foreign routes were peeled in the last PR of the campaign (see
below).

### Closure 2026-11-13 — Integration API (SQL-ISO-001)

`POST /api/v1/auth-attempts` with an `enrollmentId`: a missing id and an id owned by another
integration both answer HTTP 404, type `https://ezkey.io/problems/resource/not-found`. The
cross-integration attempt keeps its server WARN. The first cut kept HTTP 400 with a generic detail
to spare integrators; under the alpha posture above it was moved to 404, the same hide-existence
answer as the Admin API. The failure audit row carries no tenant, so no tenant can read it: no
second channel here. 403 `enrollment-inactive` stays reserved for the caller's own integration.

### Closure 2026-11-13 — remaining 403 → 404 (no finding, contract uniformity)

Not an oracle: these routes answered 403 for missing and foreign alike. Peeled because Ezkey is
alpha and the rule is now one answer everywhere (`docs/ENDPOINT.md` § Out-of-scope rows,
`ezkey-admin-api/AGENTS.md` § Authorization).

- Admin API: enrollment get / update / revoke / deactivate / reactivate; create enrollment or API
  key on a foreign integration; integration get and its three bulk enrollment actions; API-key
  list per integration; auth-attempt get / cancel / wait.
- Integration API: auth-attempt wait / cancel on an attempt of another integration.
- Empty 404 bodies on these routes (enrollment get, enrollment QR, auth-attempt get / wait /
  cancel) became the Problem `resource-not-found`.
- The object check left `@PreAuthorize` (a failed SpEL check can only be a 403) for an explicit
  call at the top of the method.

Still 403, on purpose: role gates (Tenant Admin on a Global Admin surface, which includes every
`/tenants` route, `GET /tenants/{id}` of one's own tenant included) and guards on a visible
row (self-revocation, system integration, inactive enrollment).

**Campaign status:** closed 2026-11-13. SQL-ISO-001/002/004/005/006 fixed, SQL-ISO-003 suppressed,
SQL-ISO-007/008 noted as designed. No handoff left.
