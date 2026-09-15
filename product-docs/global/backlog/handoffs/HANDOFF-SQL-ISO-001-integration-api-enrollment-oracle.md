# Handoff — SQL-ISO-001-integration-api-enrollment-oracle

**Status:** `fix authorized` — do not implement unless the operator also opens coding in that session  
**Keyword:** `assessment-curated`  
**Finding:** `SQL-ISO-001` (P2, Confirmed)  
**Campaign:** `product-docs/global/hygiene/java-tenant-sql-isolation/2026-09-15-pass-1.md`  
**Assessment:** `docs/java-tenant-sql-isolation-assessment-2026-09.md` § SQL-ISO-001

Use this prompt to start a **new Cursor session**. Default: do **not** implement unless the
operator also says implement now. Delete this file on PR/closeout after consolidating into the
campaign note — and, if the session locked a durable UX contract or product posture, only after
reinjecting those design decisions into the component's living docs (see assessment-curated README
§ Design reinjection gate).

---

## Operator decisions (already made)

1. **Disposition:** `fix` (operator **GO** 2026-09-15).
2. **Prerequisites / order:** none. Isolation of **use** already fail-closes (no 201). This slice
   is the **client error dialect** after unscoped `findById`.
3. HITL briefing accepted 2026-09-15. Implementation was **not** opened in the assessment
   session.

---

## One-sentence problem

An Integration API key can tell "this enrollment id does not exist" from "this enrollment id
exists on another integration" because Path 1 of `resolveEnrollmentId` runs unscoped
`enrollmentRepository.findById` and copies distinct exception messages into RFC 9457 `detail`.

---

## Scenario that led to the observation (preserve this narrative)

Tenant A holds a valid Integration API key. Tenant B has enrollment id `N` (`CREATED` is
enough; the integration check runs before `VERIFIED`). `POST /api/v1/auth-attempts` with A's
key and `enrollmentId=N` returns HTTP **400** with
`detail=Enrollment does not belong to this integration.` The same call with
`enrollmentId=2000000000` returns HTTP **400** with
`detail=Enrollment not found for ID: 2000000000`. Neither body includes Tenant B names; the
**existence** of `N` is confirmed. Enrollment ids are sequential integers, so enumeration is
cheap.

Live clean-start 2026-09-15 (this campaign): foreign enrollment id `5` with Tenant A API key →
400 belong-message; unknown id `2000000000` → 400 not-found-with-id. Probe:
`ezkey-tests` `TenantSqlIsolationOracleSecurityTest.apiKeyACannotCreateAuthAttemptForEnrollmentB`
(asserts 4xx and no B names; does **not** yet lock identical details).

**Non-claim:** This is not an MFA takeover and not a dump of Tenant B row contents. The **use**
of B's enrollment is fail-closed. The finding is hide-existence (fail-open existence signal vs
the posture already claimed for operator reads). Inactive **own**-integration enrollments that
later throw `EnrollmentInactiveException` (HTTP 403) are in-tenant and **out of this GO**.

---

## Evidence map (read these first)

- Assessment § SQL-ISO-001 and § Runtime evidence:
  `docs/java-tenant-sql-isolation-assessment-2026-09.md`
- Path 1 load + distinct throws:
  `ezkey-integration-api/src/main/java/org/ezkey/integration/api/controller/IntegrationApiAuthAttemptController.java`
  (`resolveEnrollmentId` enrollmentId-only branch; Springdoc `@ApiResponse(403, "Enrollment does
  not belong to this integration")` on `create` — live Path 1 is **400**, not 403)
- Handler copies `ex.getMessage()` to Problem `detail`, HTTP 400,
  type `https://ezkey.io/problems/validation/auth-attempt-create-invalid`:
  `ezkey-integration-api/src/main/java/org/ezkey/integration/api/exception/GlobalExceptionHandler.java`
  `handleAuthAttemptCreateValidationException`
- Living create contract (400 enrollment not found; 403 access denied):
  `docs/ENDPOINT.md` Authentication Attempts create status codes
- Hide-existence gold (Admin, not Integration, but the dialect target):
  `ezkey-admin-api/AGENTS.md` § Authorization — Cross-tenant deny; enrollment QR 404
- Probe test to tighten after the fix:
  `ezkey-tests/src/test/java/org/ezkey/tests/security/multitenant/TenantSqlIsolationOracleSecurityTest.java`
- Bruno (same change set as the controller): `bruno/auth-attempts-integration-api/`
  (`create.bru`, `error-scenarios/`; no dedicated foreign-enrollment-id file yet)
- Unit coverage that will need new Path 1 cases:
  `ezkey-integration-api/src/test/java/org/ezkey/integration/api/controller/IntegrationApiAuthAttemptControllerTest.java`

---

## Intended fix shape _(if fix)_ / analysis goals _(if analysis)_

**Must:** Path 1 missing enrollment and Path 1 enrollment-on-another-integration produce the
**same** client-visible HTTP status, RFC 9457 `type`, and generic `detail`. Do **not** put the
numeric enrollment id in `detail`. Do **not** tell the client that the row belongs to another
integration.

**Authorized default (smaller contract churn):** keep HTTP **400** and type
`https://ezkey.io/problems/validation/auth-attempt-create-invalid`, but throw
`AuthAttemptCreateValidationException` with **one generic message** for both missing and
foreign-integration (for example a stable "Enrollment not found." with no id). Living
`docs/ENDPOINT.md` already treats create validation / enrollment not found as 400.

**Also authorized (Admin-style hide-existence):** HTTP **404** with Integration API
`https://ezkey.io/problems/resource/not-found` for both cases. Pick this only if the session
explicitly wants status-code alignment with Admin get-by-id hide-existence. Then update
ENDPOINT + Bruno + Springdoc for a 404 on create Path 1, and refresh OpenAPI via the canonical
workflow (never hand-edit `specs/**`). If using `ResourceNotFoundException`, do **not** pass
`ex.getMessage()` through to the client as-is: that constructor embeds `{resource} with id {id}
not found`. Use a generic `detail`.

**Keep (server-side):** WARN log on cross-integration attempts (integration id, enrollment id,
owning integration id). Audit failure path may keep a server-side error message; the **HTTP
body** must not distinguish.

**Keep (in-tenant):** own-integration inactive / not-VERIFIED → `EnrollmentInactiveException`
HTTP 403 `enrollment-inactive`. Do not fold that into the hide-existence pair.

**Springdoc:** remove or retarget the create `@ApiResponse(403, "Enrollment does not belong to
this integration")` lie. Live Path 1 foreign is 400 today; after this GO, 403 on create should
mean inactive/not-VERIFIED (or be omitted if unused).

**Default vs 404:** do not invent a third dialect. Choose **one** of the two authorized shapes
in the implementing PR and apply it to both Path 1 outcomes.

### Tests (minimum)

- Tighten `TenantSqlIsolationOracleSecurityTest.apiKeyACannotCreateAuthAttemptForEnrollmentB`:
  foreign enrollment id and unknown id share **status + type** (and `detail` if the generic
  string is stable). Keep "no Tenant B names" assertions. Still assert not 201.
- Add Integration API unit tests for Path 1: missing `findById` vs other `integrationId` throw
  the **same** exception type and client message.
- Bruno: add or extend `bruno/auth-attempts-integration-api/error-scenarios/` so unknown
  enrollment id and foreign enrollment id document the same expected status/type. Update
  `create.bru` docs if they mention the old 403 belong-message.
- Update `docs/ENDPOINT.md` create status notes so they no longer describe distinct
  not-found vs access-denied dialects for Integration API Path 1.
- After Java + Bruno: clean-start stack, then `./scripts/update-specs.sh` (Integration API).
  **Never hand-edit** `specs/**` or dispatched copies.

---

## Product / methodology constraints

- Lane D hygiene; no `I-*` / `TB-*` unless the operator asks after Grill Me.
- Out of scope: Hibernate `@Filter` / tenant interceptor; changing API-key principal shape;
  Auth API proof-token lookups; SQL-ISO-002+ unless the operator expands the session;
  reopening CTRL-ROLE-001; folding own-integration inactive 403 into this pair.
- Controller change ⇒ Bruno in the **same** change set. OpenAPI refresh is part of the work
  when the stack can be clean-started; if Docker is unavailable, finish Java/tests/Bruno/ENDPOINT
  and report pending `update-specs` — do not patch generated specs.
- Canonical Java validation: `./scripts/build.sh` from repo root (Git Bash).
- Delete this handoff on closeout after consolidating into the campaign note. Reinject the
  chosen create error contract into `docs/ENDPOINT.md` (and Springdoc) before deleting.

### Design reinjection checklist

- `docs/ENDPOINT.md` — Authentication Attempts create status codes (Integration API Path 1
  missing vs foreign is one client outcome; 403 reserved for inactive if that stays).
- `IntegrationApiAuthAttemptController` `@ApiResponses` on `create`.
- Bruno `bruno/auth-attempts-integration-api/`.
- Generated OpenAPI only via `./scripts/update-specs.sh` after clean-start.

---

## Paste-ready starter message

```text
Read product-docs/global/backlog/handoffs/HANDOFF-SQL-ISO-001-integration-api-enrollment-oracle.md end-to-end.
Then read docs/java-tenant-sql-isolation-assessment-2026-09.md § SQL-ISO-001.

Task: Unify Integration API create auth-attempt Path 1 so a missing enrollment id and an
enrollment that belongs to another integration produce the same client Problem (authorized
default: HTTP 400 + auth-attempt-create-invalid + one generic detail with no numeric id;
404 resource-not-found is also authorized if you take the Admin hide-existence path). Keep
server WARN on cross-integration attempts. Keep own-integration inactive as 403. Extend
TenantSqlIsolationOracleSecurityTest so foreign and missing share status+type. Update Bruno
and ENDPOINT in the same change set. Refresh OpenAPI with clean-start + ./scripts/update-specs.sh
— never hand-edit specs. Do not add Hibernate tenant filters. Do not create I-*/TB-*. Do not
commit until I ask.
```
