# Handoff — SQL-ISO-005-unscoped-service-loaders

**Status:** `fix authorized` — do not implement unless the operator also opens coding in that session  
**Keyword:** `assessment-curated`  
**Finding:** `SQL-ISO-005` (P2, Confirmed residual)  
**Campaign:** `product-docs/global/hygiene/java-tenant-sql-isolation/2026-09-15-pass-1.md`  
**Assessment:** `docs/java-tenant-sql-isolation-assessment-2026-09.md` § SQL-ISO-005

Use this prompt to start a **new Cursor session**. Default: do **not** implement unless the
operator also says implement now. Delete this file on PR/closeout after consolidating into the
campaign note — and, if the session locked a durable UX contract or product posture, only after
reinjecting those design decisions into the component's living docs (see assessment-curated README
§ Design reinjection gate).

---

## Operator decisions (already made)

1. **Disposition:** `fix` (operator **GO** 2026-09-15).
2. **Prerequisites / order:** none. Independent of SQL-ISO-001–004. This slice is **documentation
   of the service SQL contract**, not a live HTTP hole and not Hibernate filters.
3. HITL briefing accepted 2026-09-15. Implementation was **not** opened in the assessment
   session. Last item of pass-1 HITL lot.

---

## One-sentence problem

Core `getById` / `getAll` / `findAll` / `revokeKey` helpers are tenant-blind; isolation is
caller discipline, and `ezkey-core/AGENTS.md` does not say that new HTTP surfaces must ACS
(or pass principal `tenantId` into Specs) **before** those loaders.

---

## Scenario that led to the observation (preserve this narrative)

White-box 2026-09-15. Live Admin/Integration HTTP get-by-id paths reviewed in this pass wrap
ACS (or principal tenant overwrite) then call unscoped loaders. No production controller
caller of `EnrollmentService.getAll`, `IntegrationService.getAll`, `AuthAttemptService.getAll`,
or `ApiKeyService.findAll` was found (unit tests only). `ApiKeyService.revokeApiKey` runs
native `revokeKey` by PK after the controller ACS. `listAdmins(tenantId)` trusts the Integer;
the controller overwrites scope from the principal. The residual is fail-open **if** a future
caller skips the wrap — same class as CTRL-ROLE-008 / SEC-015, restated at the service SQL
layer because that is this mandate.

**Non-claim:** Not a live Tenant Admin dump of another tenant. Not a reason to delete
`findAll` without a caller audit. Global Admin cross-tenant **operator** reads remain
designed. Auth proof-token lookups must stay token-bound, not tenant-filtered.

---

## Evidence map (read these first)

- Assessment § SQL-ISO-005 and § How isolation is supposed to work:
  `docs/java-tenant-sql-isolation-assessment-2026-09.md`
- Representative unscoped loaders:
  `ezkey-core/src/main/java/org/ezkey/enrollment/service/EnrollmentService.java`
  (`getById`, `getAll`)
  `ezkey-core/src/main/java/org/ezkey/integration/service/IntegrationService.java`
  (`getById`, `getAll`)
  `ezkey-core/src/main/java/org/ezkey/authattempt/service/AuthAttemptService.java`
  (`getById`, `getAll`)
  `ezkey-core/src/main/java/org/ezkey/integration/service/ApiKeyService.java`
  (`getApiKey`, `findAll`, `revokeApiKey`)
- Controller wrap examples (do not “fix” these unless they skip ACS): enrollment GET ACS then
  `getById`; API-key revoke ACS then `revokeApiKey`; admin list overwrites `tenantId` from
  principal before `listAdmins`
- Sibling notes (do not reopen as a second finding):
  `docs/java-controller-role-validation-assessment-2026-08.md` CTRL-ROLE-008;
  `docs/SECURITY_CHALLENGE_REPORT_2026-06.md` SEC-015
- Living docs to extend (no unscoped-loader contract today):
  `ezkey-core/AGENTS.md`; `ezkey-admin-api/AGENTS.md` § Authorization at controllers
- Mandate non-goal: Hibernate `@Filter` as default (Auth proof-token paths would fight them)

---

## Intended fix shape _(if fix)_ / analysis goals _(if analysis)_

**Must (authorized default — 80/20):** Document the contract.

1. Add a short section to `ezkey-core/AGENTS.md`: unscoped PK loaders (`getById`, `getAll`,
   `findAll`, `getApiKey`, `revokeApiKey` / `revokeKey`) and `listAdmins(tenantId)` do **not**
   apply a tenant predicate. They are internal. New HTTP surfaces must call
   `AccessControlService.canAccess*` (or overwrite list `tenantId` from the principal into
   Specs) **before** these methods. Auth/device proof-token lookups stay token-bound and must
   **not** grow a tenant filter. Do not add Hibernate `@Filter`.
2. Add a one-paragraph pointer under Admin API `AGENTS.md` § Authorization (object split):
   core loaders are not the object check; skipping ACS and calling `getById`/`revokeApiKey`
   is a regression of this class.
3. Tighten Javadoc on the listed methods to say they are unscoped / caller must already have
   authorized (keep existing “GlobalAdmin” wording on `findAll` honest: there is still no
   HTTP caller).

**Optional (not required in this GO):** `requireAccessible*(principal, id)` helpers that
ACS-then-load. Only add if the operator expands the session; do not invent a new dialect
(`canAccessAdmin` was already declined for vocabulary in the 2026-08 pass).

**Out of this GO:** deleting `findAll`/`getAll` without a caller audit; wrapping every
existing controller that already ACS-first; implementing SQL-ISO-001–004; Hibernate tenant
filters; mass JSON GET 403→404.

This is a **docs + Javadoc** slice. No OpenAPI / Bruno / `update-specs` unless you also
change HTTP behavior (you should not).

### Tests (minimum)

- No new functional HTTP test is required (no live path).
- After Javadoc / AGENTS edits: `./scripts/build.sh` so Checkstyle/Spotless accept the Java
  comment changes.
- Do not add a test that merely calls `getAll()` to “prove” it is unscoped.

---

## Product / methodology constraints

- Lane D hygiene; no `I-*` / `TB-*` unless the operator asks after Grill Me.
- Out of scope: Hibernate `@Filter`; deleting unused `findAll`; SQL-ISO-001–004; SQL-ISO-006
  API-key GET 404 vs 403 (category 2).
- Canonical Java validation: `./scripts/build.sh` from repo root (Git Bash) if Java Javadoc
  changes.
- Delete this handoff on closeout after consolidating into the campaign note. The AGENTS.md
  sections **are** the living canon for this finding — that is the design reinjection.

### Design reinjection checklist

- `ezkey-core/AGENTS.md` — unscoped loader contract (new short section).
- `ezkey-admin-api/AGENTS.md` § Authorization — object split pointer to core loaders.
- Method Javadoc on the listed unscoped helpers.

---

## Paste-ready starter message

```text
Read product-docs/global/backlog/handoffs/HANDOFF-SQL-ISO-005-unscoped-service-loaders.md end-to-end.
Then read docs/java-tenant-sql-isolation-assessment-2026-09.md § SQL-ISO-005.

Task: Document that ezkey-core unscoped getById/getAll/findAll/revokeKey loaders are internal
and that new HTTP surfaces must ACS or pass principal tenantId into Specs before calling them.
Add that contract to ezkey-core/AGENTS.md, a short pointer in ezkey-admin-api/AGENTS.md
§ Authorization, and Javadoc on the listed methods. Do not add Hibernate tenant filters.
Do not delete findAll. Do not add requireAccessible* helpers unless I expand the session.
Do not implement SQL-ISO-001–004 here. Do not create I-*/TB-*. Do not commit until I ask.
```
