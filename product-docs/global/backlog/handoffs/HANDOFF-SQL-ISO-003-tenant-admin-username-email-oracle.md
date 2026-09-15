# Handoff — SQL-ISO-003-tenant-admin-username-email-oracle

**Status:** `fix authorized` — do not implement unless the operator also opens coding in that session  
**Keyword:** `assessment-curated`  
**Finding:** `SQL-ISO-003` (P2, Confirmed)  
**Campaign:** `product-docs/global/hygiene/java-tenant-sql-isolation/2026-09-15-pass-1.md`  
**Assessment:** `docs/java-tenant-sql-isolation-assessment-2026-09.md` § SQL-ISO-003

Use this prompt to start a **new Cursor session**. Default: do **not** implement unless the
operator also says implement now. Delete this file on PR/closeout after consolidating into the
campaign note — and, if the session locked a durable UX contract or product posture, only after
reinjecting those design decisions into the component's living docs (see assessment-curated README
§ Design reinjection gate).

---

## Operator decisions (already made)

1. **Disposition:** `fix` (operator **GO** 2026-09-15).
2. **Prerequisites / order:** none. Independent of SQL-ISO-001/002. Keep instance-global
   uniqueness (login is `findByUsername`). This slice is the **client dialect** on Tenant Admin
   peer-create, not a username-model change.
3. HITL briefing accepted 2026-09-15. Implementation was **not** opened in the assessment
   session.

---

## One-sentence problem

`existsByUsername` / `existsByEmail` are instance-global, and Tenant Admin
`POST /api/v1/admins/tenant` copies `"Username already exists: " + username` (and the email
twin) into HTTP 400 Problem `detail`, so a tenant can confirm a Global Admin (or any other
tenant admin) identity.

---

## Scenario that led to the observation (preserve this narrative)

Clean-start 2026-09-15. Tenant Admin A posts `POST /api/v1/admins/tenant` with username
`admin.docker` (bootstrap Global Admin). Response is HTTP **400**
`detail=Username already exists: admin.docker`. An unused username proceeds (201) or fails
for other reasons. Probe:
`ezkey-tests` `TenantSqlIsolationOracleSecurityTest.tenantAdminACannotReuseGlobalAdminUsername`
(asserts not 201; does **not** yet lock a generic `detail`).

**Honest limit (do not over-claim the fix):** even a generic 400 vs 201 on an otherwise-valid
payload still tells a caller that the name is taken. That is inherent while usernames are
globally unique. This GO stops **naming** the collision and echoing the identifier. It cannot
make create succeed for a taken GA name, and it must not move to per-tenant usernames.

**Non-claim:** This is not a login oracle (SEC-006 already uses a generic HTTP 401;
`AdminAuthServiceLoginAntiEnumerationTest`). Login must stay globally unique.

---

## Evidence map (read these first)

- Assessment § SQL-ISO-003 and § Runtime evidence:
  `docs/java-tenant-sql-isolation-assessment-2026-09.md`
- Pre-check throws with echoed identifier (`createTenantAdmin` and `createGlobalAdmin`):
  `ezkey-admin-api/src/main/java/org/ezkey/admin/service/AdminProvisioningService.java`
- SQL: `ezkey-core` `EzkeyAdminRepository.existsByUsername` / `existsByEmail`
- IAE → HTTP 400 `admin/invalid-argument`, `detail = ex.getMessage()`:
  `ezkey-admin-api/src/main/java/org/ezkey/exception/ValidationExceptionHandler.java`
  `handleIllegalArgumentException`
- Race path still says `"Username already exists"` / `"Email already exists"` (no value echo):
  same handler `handleDataIntegrityViolationException` unique-constraint branch
- Controller rethrows IAE after audit (`errorMessage` may keep the specific string server-side):
  `AdminProvisioningController.createTenantAdmin`
- Login anti-enumeration gold (do not weaken):
  `ezkey-admin-api/src/test/java/org/ezkey/admin/service/AdminAuthServiceLoginAntiEnumerationTest.java`
- Bruno: `bruno/admin-provisioning-admin/create-tenant-admin.bru` (and activation-code sibling)
- PATCH email uniqueness (related, same string class):
  `AdminProvisioningService.updateAdmin` → `existsByEmailAndAdminIdNot`

---

## Intended fix shape _(if fix)_ / analysis goals _(if analysis)_

**Must:** Keep instance-global uniqueness (login key). For **Tenant Admin**
`POST /api/v1/admins/tenant`, the client Problem `detail` must **not** say “already exists”
and must **not** echo the username or email. Log the real collision at INFO/WARN with
requester id and which field collided.

**Authorized default:**

1. In `createTenantAdmin`, replace the two IAE messages with one generic provisioning
   failure (stable string, no identifier). Apply for every caller of that method (Tenant
   Admin and Global Admin creating a tenant peer). `EvaluatorSelfRegistrationService` goes
   through the same method — it inherits the generic client string; that is intended.
2. Align the unique-constraint race path in `ValidationExceptionHandler` so username/email
   unique violations use the **same generic detail** (not `"Username already exists"`).
   Pre-check and DB constraint must not re-split the dialect.
3. Audit `errorMessage` / server logs may keep the specific reason; the **HTTP body** must
   not.

**Optional in this GO (cheap, same PR):** same generic client string on `createGlobalAdmin`
collisions. Platform operators can read logs. Do not require a reserved GA username
namespace in this slice.

**Optional related peel (do not require):** PATCH `existsByEmailAndAdminIdNot` still throws
`"Email already exists: " + email`. Same class; include only if the uniqueness messages are
already open. Mention in the PR if left.

**Keep HTTP 400** (invalid-argument / data-constraint). Do not turn collisions into 409
unless ENDPOINT already documents that — default is stay 400 with generic detail.

**Out of this GO:** per-tenant usernames; reserved Global Admin username namespace; changing
login `findByUsername`; SQL-ISO-002 get-by-id.

### Tests (minimum)

- Tighten `TenantSqlIsolationOracleSecurityTest.tenantAdminACannotReuseGlobalAdminUsername`:
  still not 201; `detail` must not contain the GA username and must not contain
  `"already exists"` (case-insensitive is fine). Same idea for a colliding **email** if the
  factory can supply the GA email cheaply.
- Unit tests on `createTenantAdmin`: when `existsByUsername` / `existsByEmail` is true, thrown
  exception message is generic (no identifier).
- If the constraint-handler string changes, extend the existing
  `ValidationExceptionHandler` unique-constraint tests.
- Bruno `create-tenant-admin.bru`: do not document “Username already exists: …” as the
  expected client `detail`.
- After Java + Bruno: `./scripts/build.sh`. OpenAPI refresh only if Springdoc/error examples
  changed and the stack can be clean-started (`./scripts/update-specs.sh`). Never hand-edit
  `specs/**`.

---

## Product / methodology constraints

- Lane D hygiene; no `I-*` / `TB-*` unless the operator asks after Grill Me.
- Out of scope: per-tenant usernames (would break global login); Hibernate `@Filter`;
  SQL-ISO-001/002/004+ unless the operator expands.
- Controller/service client-visible error change ⇒ Bruno in the **same** change set.
- Canonical Java validation: `./scripts/build.sh` from repo root (Git Bash).
- Delete this handoff on closeout after consolidating into the campaign note. Reinject the
  generic provisioning-error contract into living docs if ENDPOINT or Admin API AGENTS.md
  currently describe “username already exists” as the client string.

### Design reinjection checklist

- `docs/ENDPOINT.md` tenant-admin create error notes, if they name the old `detail`.
- Bruno `bruno/admin-provisioning-admin/create-tenant-admin.bru` (and activation-code
  sibling if it documents the collision).
- Do not weaken login anti-enumeration docs or tests.

---

## Paste-ready starter message

```text
Read product-docs/global/backlog/handoffs/HANDOFF-SQL-ISO-003-tenant-admin-username-email-oracle.md end-to-end.
Then read docs/java-tenant-sql-isolation-assessment-2026-09.md § SQL-ISO-003.

Task: Keep global username/email uniqueness. For POST /api/v1/admins/tenant, stop returning
"Username already exists: {username}" / "Email already exists: {email}" to the client.
Use one generic HTTP 400 detail that does not confirm which field collided and does not echo
the identifier. Log the real collision server-side. Align the unique-constraint race path to
the same generic detail. Tighten TenantSqlIsolationOracleSecurityTest so the body does not
contain the GA username or "already exists". Update Bruno. Do not move to per-tenant
usernames. Do not create I-*/TB-*. Do not commit until I ask.
```
