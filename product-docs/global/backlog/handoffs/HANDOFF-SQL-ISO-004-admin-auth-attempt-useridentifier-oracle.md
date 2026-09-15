# Handoff — SQL-ISO-004-admin-auth-attempt-useridentifier-oracle

**Status:** `fix authorized` — do not implement unless the operator also opens coding in that session  
**Keyword:** `assessment-curated`  
**Finding:** `SQL-ISO-004` (P2, Confirmed static)  
**Campaign:** `product-docs/global/hygiene/java-tenant-sql-isolation/2026-09-15-pass-1.md`  
**Assessment:** `docs/java-tenant-sql-isolation-assessment-2026-09.md` § SQL-ISO-004

Use this prompt to start a **new Cursor session**. Default: do **not** implement unless the
operator also says implement now. Delete this file on PR/closeout after consolidating into the
campaign note — and, if the session locked a durable UX contract or product posture, only after
reinjecting those design decisions into the component's living docs (see assessment-curated README
§ Design reinjection gate).

---

## Operator decisions (already made)

1. **Disposition:** `fix` (operator **GO** 2026-09-15).
2. **Prerequisites / order:** none. Independent of SQL-ISO-001 (Integration API Path 1). Admin
   Path 1 (enrollment id only) is already dialect-consistent (missing and foreign both ACS
   `false` → 403) and is **out of this GO**.
3. HITL briefing accepted 2026-09-15. Implementation was **not** opened in the assessment
   session. Pass-1 did **not** live-probe the 400-vs-403 pair; implementers should add that
   test as evidence.

---

## One-sentence problem

Admin `POST /api/v1/auth-attempts` Paths 2 and 3 run
`findByIntegrationIdAndUserIdentifierAndStatusAndActive(request.integrationId, …)` **before**
`canAccessEnrollment`, so a Tenant Admin can distinguish “no verified enrollment for this
userIdentifier on that integration” (HTTP 400) from “there is one, but it is not yours”
(HTTP 403).

---

## Scenario that led to the observation (preserve this narrative)

Static 2026-09-15. Tenant Admin A posts create with `userIdentifier` and Tenant B’s
`integrationId` (sequential, enumerable). If B has no verified enrollment for that identifier
→ HTTP **400** `detail=No verified enrollment found for userIdentifier '…'`. If B has one →
ACS **403** (empty body). Own-integration unknown `userIdentifier` is the same 400 — that
part is in-tenant operational feedback, not the finding.

**Non-claim:** Creating an attempt on B’s enrollment is fail-closed (no 201). Echoing the
request `userIdentifier` is not the oracle (the client sent it). Integration API Path 2 looks
up inside the API key’s own integration and is **out of this GO**. Admin Path 1 403→404 is a
CTRL-ROLE-003 peel, not required here.

---

## Evidence map (read these first)

- Assessment § SQL-ISO-004:
  `docs/java-tenant-sql-isolation-assessment-2026-09.md`
- Paths 2–3 SQL then return id; ACS only after `resolveEnrollmentId`:
  `ezkey-admin-api/src/main/java/org/ezkey/admin/controller/AuthAttemptController.java`
  (`resolveEnrollmentId`, `create`)
- Handler copies validation message to HTTP 400
  `https://ezkey.io/problems/validation/auth-attempt-create-invalid`:
  `ezkey-admin-api/src/main/java/org/ezkey/exception/ValidationExceptionHandler.java`
- `canAccessIntegration` already returns `false` for missing **and** other-tenant
  integrations (`canAccessIntegrationForTenant`). Global Admin short-circuits `true` without
  an existence check — **keep that**; do not use ACS-first to hide integrations from the
  platform operator.
- Hide-existence target:
  `ezkey-admin-api/AGENTS.md` § Authorization — Cross-tenant deny (404
  `admin/resource-not-found`)
- Existing ownership unit test (Path 1 / ACS enrollment):
  `ezkey-admin-api/src/test/java/org/ezkey/admin/controller/AuthAttemptControllerOwnershipTest.java`
- Bruno: `bruno/auth-attempts-admin/create.bru` (documents `userIdentifier` + `integrationId`;
  no foreign-integration error file yet)
- Living create contract: `docs/ENDPOINT.md` Authentication Attempts create (400 vs 403)

---

## Intended fix shape _(if fix)_ / analysis goals _(if analysis)_

**Must:** When `userIdentifier` is present, call `canAccessIntegration(auth, request.integrationId)`
**before** the enrollment SQL (Paths 2 and 3). Tenant Admin missing and foreign integration
must share hide-existence **HTTP 404** with Problem type
`https://ezkey.io/problems/admin/resource-not-found`. Do not run the userIdentifier query
when ACS is false (verify with a unit test that the repository is not called).

**Authorized default:**

1. Pass `Authentication` into `resolveEnrollmentId` (or gate in `create` immediately before
   it when `userIdentifier != null`).
2. Tenant Admin unauthorized/missing integration → `ResourceNotFoundException` (or equivalent
   404 Problem), **not** `AuthAttemptCreateValidationException` 400 and **not** empty 403.
   Prefer generic `detail` (`AdminApiProblemCatalog.DETAIL_NOT_FOUND`).
3. After ACS passes, existing Path 2/3 SQL stays. Own-integration empty list may remain
   HTTP **400** create-validation (“no verified enrollment…”) — that is in-tenant operator
   feedback. Do not force own-directory misses to 404 unless it is cheaper to unify; default
   is keep 400 for authorized empty.
4. Global Admin: leave `canAccessIntegration` short-circuit `true`. Missing integration then
   still yields 400 empty-SQL, which is acceptable for the platform operator.
5. After a resolved enrollment id, existing `canAccessEnrollment` on `create` stays (defense
   in depth). Do **not** require Path 1 403→404 in this slice.

**Keep:** `integrationId` required when `userIdentifier` is used with admin auth (already
validated).

**Out of this GO:** userIdentifier uniqueness rules; Integration API Path 2; Hibernate
filters; Admin Path 1 empty-403 hide-existence peel; SQL-ISO-001.

### Tests (minimum)

- Unit: Tenant Admin + foreign `integrationId` + `userIdentifier` → 404; verify
  `enrollmentRepository.findByIntegrationIdAndUserIdentifier…` is **never** called.
- Unit: Tenant Admin + missing `integrationId` (unknown PK) + `userIdentifier` → same 404.
- Unit: Tenant Admin + own integration + unknown `userIdentifier` → still 400 create-validation
  (if you keep that default).
- Unit: Global Admin + any integration still reaches SQL / create as today.
- Functional (add to `TenantSqlIsolationOracleSecurityTest` or sibling): TA + Tenant B
  `integrationId` + unknown vs known `userIdentifier` share **404**; no 201; body does not
  contain Tenant B names. Pass-1 did not have this probe — this is the confirmation test.
- Bruno: document Path 2 unauthorized `integrationId` as 404 hide-existence.
  `docs/ENDPOINT.md` create status notes: Admin userIdentifier + foreign/missing integration
  is 404, not 400-vs-403.
- After Java + Bruno: `./scripts/build.sh`; if the HTTP contract changed and the stack can
  be clean-started, `./scripts/update-specs.sh` (Admin API). Never hand-edit `specs/**`.

---

## Product / methodology constraints

- Lane D hygiene; no `I-*` / `TB-*` unless the operator asks after Grill Me.
- Out of scope: Hibernate `@Filter`; Integration API Path 2; Path 1 403→404; SQL-ISO-001/005
  unless the operator expands.
- Controller change ⇒ Bruno in the **same** change set.
- Canonical Java validation: `./scripts/build.sh` from repo root (Git Bash).
- Delete this handoff on closeout after consolidating into the campaign note. Reinject the
  create Path 2/3 hide-existence contract into `docs/ENDPOINT.md` (and Springdoc on `create`)
  before deleting.

### Design reinjection checklist

- `docs/ENDPOINT.md` — Authentication Attempts create status codes (Admin `userIdentifier`
  + `integrationId`: unauthorized/missing integration is 404 hide-existence).
- `AuthAttemptController` `@ApiResponses` on `create` (today 201/400/500 only).
- Bruno `bruno/auth-attempts-admin/`.
- Generated OpenAPI only via `./scripts/update-specs.sh` after clean-start.

---

## Paste-ready starter message

```text
Read product-docs/global/backlog/handoffs/HANDOFF-SQL-ISO-004-admin-auth-attempt-useridentifier-oracle.md end-to-end.
Then read docs/java-tenant-sql-isolation-assessment-2026-09.md § SQL-ISO-004.

Task: On Admin POST /api/v1/auth-attempts Paths 2 and 3, call canAccessIntegration on the
request integrationId before findByIntegrationIdAndUserIdentifier…. Tenant Admin missing and
foreign integration must share HTTP 404 admin/resource-not-found. Do not run that SQL when
ACS is false. Own-integration empty userIdentifier may stay 400. Keep Global Admin
short-circuit. Add unit + functional tests for the former 400-vs-403 split. Update Bruno and
ENDPOINT. Refresh OpenAPI with clean-start + ./scripts/update-specs.sh — never hand-edit
specs. Do not change Integration API Path 2, userIdentifier uniqueness, or Admin Path 1
403. Do not create I-*/TB-*. Do not commit until I ask.
```
