# Handoff — SQL-ISO-002-admin-get-by-id-existence-oracle

**Status:** `fix authorized` — do not implement unless the operator also opens coding in that session  
**Keyword:** `assessment-curated`  
**Finding:** `SQL-ISO-002` (P2, Confirmed)  
**Campaign:** `product-docs/global/hygiene/java-tenant-sql-isolation/2026-09-15-pass-1.md`  
**Assessment:** `docs/java-tenant-sql-isolation-assessment-2026-09.md` § SQL-ISO-002

Use this prompt to start a **new Cursor session**. Default: do **not** implement unless the
operator also says implement now. Delete this file on PR/closeout after consolidating into the
campaign note — and, if the session locked a durable UX contract or product posture, only after
reinjecting those design decisions into the component's living docs (see assessment-curated README
§ Design reinjection gate).

---

## Operator decisions (already made)

1. **Disposition:** `fix` (operator **GO** 2026-09-15).
2. **Prerequisites / order:** none. Independent of SQL-ISO-001. List SQL already excludes
   Global Admin rows from Tenant Admin lists; this slice is get-by-id (and PATCH, which
   reuses `getAdminById`).
3. HITL briefing accepted 2026-09-15. Implementation was **not** opened in the assessment
   session.

---

## One-sentence problem

Tenant Admin `GET /api/v1/admins/{id}` (and `PATCH` via the same service load) distinguishes
missing ids (**404**) from existing out-of-tenant ids including Global Admin `tenant=null`
(**403** on GET, **400** on PATCH), so a tenant can confirm that the platform admin row
exists.

---

## Scenario that led to the observation (preserve this narrative)

Clean-start 2026-09-15. Tenant Admin A calls `GET /api/v1/admins/1` (bootstrap Global Admin)
→ HTTP **403** with no username in the body. The same caller, `GET /api/v1/admins/2000000000`
→ HTTP **404**. Sequential integer PKs make enumeration cheap. Probe:
`ezkey-tests` `TenantSqlIsolationOracleSecurityTest.tenantAdminACannotGetGlobalAdminById`
(asserts GA GET is 403-or-404 and does not echo the GA username; missing is 404; does **not**
yet lock identical 404).

**Non-claim:** This is not a dump of Global Admin MFA / onboarding material. List isolation
holds. Global Admin cross-tenant **operator** reads remain designed. `getAdminOnboarding`
is a **separate** method with a similar 400 dialect and is **out of this GO** unless the
operator expands the slice.

---

## Evidence map (read these first)

- Assessment § SQL-ISO-002 and § Runtime evidence:
  `docs/java-tenant-sql-isolation-assessment-2026-09.md`
- Service load + tenant compare throwing `IllegalArgumentException` for Tenant Admin
  out-of-tenant (GA `getTenant()` is null → mismatch):
  `ezkey-admin-api/src/main/java/org/ezkey/admin/service/AdminProvisioningService.java`
  `getAdminById`
- GET maps `ResourceNotFoundException` → empty 404, `IllegalArgumentException` → empty 403:
  `ezkey-admin-api/src/main/java/org/ezkey/admin/controller/AdminProvisioningController.java`
  `getAdminById`
- PATCH calls `getAdminById` first, then rethrows: RNFE → 404 Problem, IAE → 400 Problem
  (`ValidationExceptionHandler`)
- Hide-existence target (known debt names this GET):
  `ezkey-admin-api/AGENTS.md` § Authorization — Cross-tenant deny; dialect 3
  (`admin GET foreign is 403`, `onboarding/PATCH foreign is 400`)
- Problem type to use: `https://ezkey.io/problems/admin/resource-not-found`
  (`AdminApiProblemCatalog.TYPE_RESOURCE_NOT_FOUND`). Safe generic detail already exists:
  `AdminApiProblemCatalog.DETAIL_NOT_FOUND`
- Enrollment QR gold (404 hide-existence): `docs/ENDPOINT.md` GET enrollment QR status notes
- Living GET contract still lists 403 “not authorized for this admin”:
  `docs/ENDPOINT.md` § e3 Get Administrator by ID
- Unit tests that currently expect IAE for other-tenant GET:
  `ezkey-admin-api/src/test/java/org/ezkey/admin/service/AdminProvisioningServiceTest.java`
  `GetAdminByIdTests.tenantAdminCannotGetAdminFromOtherTenant`
- Bruno: `bruno/admin-provisioning-admin/get-admin-by-id.bru` (post-response currently
  accepts 200, 403, or 404); `update-admin-profile.bru` for PATCH
- Sibling CTRL-ROLE-003 / 003b: later JSON GET peels; this GO is the **admin id** peel with
  Global Admin impact, not a mass 403→404 campaign

---

## Intended fix shape _(if fix)_ / analysis goals _(if analysis)_

**Must:** For a Tenant Admin, missing admin id, other-tenant admin id, and Global Admin id
(`tenant=null`) produce the **same** client-visible HTTP **404**. Do not return 403 (GET) or
400 (PATCH) as the “row exists but you may not see it” dialect. Global Admin GET/PATCH of any
admin stays 200 (designed operator). Tenant Admin GET of an **own-tenant** peer stays 200.

**Authorized default:**

1. In `getAdminById`, Tenant Admin out-of-tenant (including GA) throws
   `ResourceNotFoundException` (same as missing), **not** `IllegalArgumentException`.
2. GET should not keep a local `catch (IllegalArgumentException) → 403`. Prefer HTTP 404 with
   Problem type `https://ezkey.io/problems/admin/resource-not-found`. Prefer generic client
   `detail` (`AdminApiProblemCatalog.DETAIL_NOT_FOUND` or equivalent) rather than
   `ResourceNotFoundException`'s `"Administrator with id {id} not found"` if that is easy in
   the same change; echoing the **request** id is not the oracle, but the AGENTS.md contract
   is a resource-not-found Problem, not an authorization message.
3. PATCH foreign follows automatically if `getAdminById` throws RNFE (today PATCH foreign is
   400 because of IAE). That status shift **is in this GO**.
4. Log the deny server-side at DEBUG/WARN with requester id and target id; do not put
   “tenant administrators can only access…” on the HTTP body.

**Keep:** object check in the service (dialect 3). Do **not** invent `canAccessAdmin` solely
for vocabulary (`AGENTS.md`). Do **not** change list SQL.

**Out of this GO:** `getAdminOnboarding` / onboarding QR 400 dialect (related peel; mention
in the PR, do not require unless the operator expands). SQL-ISO-003 username uniqueness.
Hibernate filters. Mass-changing other JSON GET 403s (API-key GET is SQL-ISO-006 /
CTRL-ROLE-003).

### Tests (minimum)

- `AdminProvisioningServiceTest`: Tenant Admin + other-tenant and Tenant Admin + Global
  Admin (`tenant=null`) throw the same not-found type as missing; own-tenant still returns
  the entity; Global Admin still loads any admin.
- Controller GET: Tenant Admin missing and foreign/GA share 404 (and Problem `type` if GET
  starts returning Problem JSON). Own-tenant 200 unchanged.
- Tighten `TenantSqlIsolationOracleSecurityTest.tenantAdminACannotGetGlobalAdminById`: GA id
  and unused id share **404** (and `type` if applicable). Keep “body does not contain GA
  username”. Own-peer 200 is a useful extra assertion if cheap.
- Bruno `get-admin-by-id.bru`: Tenant Admin foreign/missing is 404, not 403. PATCH docs if
  they still describe 400 for foreign admin.
- `docs/ENDPOINT.md` GET `/admins/{id}` status codes: Tenant Admin out-of-scope is 404
  hide-existence, not 403. PATCH notes if they list 400 for unauthorized admin.
- Reinject `ezkey-admin-api/AGENTS.md` known-debt line: admin GET foreign is no longer 403
  once this lands (leave onboarding 400 on the debt list unless expanded).
- After Java + Bruno: `./scripts/build.sh`; if the HTTP contract changed and the stack can
  be clean-started, `./scripts/update-specs.sh` (Admin API). **Never hand-edit** `specs/**`.

---

## Product / methodology constraints

- Lane D hygiene; no `I-*` / `TB-*` unless the operator asks after Grill Me.
- Out of scope: per-tenant usernames; list SQL; Hibernate `@Filter`; SQL-ISO-001/003+ unless
  the operator expands; reopening CTRL-ROLE-001; mass JSON GET 403 peels.
- Controller change ⇒ Bruno in the **same** change set.
- Canonical Java validation: `./scripts/build.sh` from repo root (Git Bash).
- Delete this handoff on closeout after consolidating into the campaign note. Reinject the
  hide-existence contract into `ezkey-admin-api/AGENTS.md` and `docs/ENDPOINT.md` before
  deleting.

### Design reinjection checklist

- `ezkey-admin-api/AGENTS.md` § Authorization — remove “admin GET foreign is 403” from known
  debt once the peel lands; keep onboarding 400 unless that slice is included.
- `docs/ENDPOINT.md` § Get Administrator by ID (and PATCH if status codes change).
- Bruno `bruno/admin-provisioning-admin/get-admin-by-id.bru` (and PATCH if needed).
- Generated OpenAPI only via `./scripts/update-specs.sh` after clean-start.

---

## Paste-ready starter message

```text
Read product-docs/global/backlog/handoffs/HANDOFF-SQL-ISO-002-admin-get-by-id-existence-oracle.md end-to-end.
Then read docs/java-tenant-sql-isolation-assessment-2026-09.md § SQL-ISO-002.

Task: Tenant Admin GET /api/v1/admins/{id} must not distinguish missing from out-of-tenant
(including Global Admin tenant=null). Use hide-existence HTTP 404 and Problem type
https://ezkey.io/problems/admin/resource-not-found. Change getAdminById so Tenant Admin
out-of-scope throws ResourceNotFoundException like missing, not IllegalArgumentException.
PATCH foreign should follow that 404. Keep Global Admin access and Tenant Admin own-peer
200. Update unit tests, TenantSqlIsolationOracleSecurityTest, Bruno, ENDPOINT, and AGENTS.md
known-debt. Refresh OpenAPI with clean-start + ./scripts/update-specs.sh — never hand-edit
specs. Do not change list SQL, onboarding, or usernames. Do not create I-*/TB-*. Do not
commit until I ask.
```
