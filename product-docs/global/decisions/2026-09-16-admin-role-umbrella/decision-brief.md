# Decision brief — Admin role umbrella (Options A / B / C)

## Metadata

- **Pack:** [`README.md`](README.md)
- **Inventory:** [`inventory-status-quo.md`](inventory-status-quo.md)
- **Audience:** Marc / Patrick
- **Date:** 2026-09-16
- **Prior settlement:** Path A keep-umbrella (hygiene 2026-08-20) — this brief reopens as an
  explicit architecture decision, not a silent override

## Problem statement

`ROLE_ADMIN` means “authenticated human admin.” Isolation and Global-only privilege are **not**
that role. Agents and humans who treat `@PreAuthorize("hasRole('ADMIN')")` as sufficient will
reproduce SEC-017 / CTRL-ROLE-001 class defects. The question is whether the **status quo
umbrella** remains the best fit for a solo-maintained lab that still wants clean structure, or
whether authorities should be split greenfield-style (and how far that is from today).

---

## Option A — Keep umbrella + harden discoverability

### What it is

Keep `AdminTokenAuthenticationFilter` issuing `ROLE_ADMIN` + type role. Keep shared operator
surfaces on `hasRole('ADMIN')` (or optional later `hasAnyRole`). Treat missing **object** /
**Global-only** checks as the defect class. Invest in agent discoverability so cold sessions
cannot “fix” isolation by copying an umbrella-only neighbor.

### What must be written so agents do not violate isolation

Already largely in `ezkey-admin-api/AGENTS.md`. Remaining discoverability gaps (lightweight):

1. **Root / always-applied pointer** — one short rule or AGENTS domain-pointer row: “Admin
   controller authz = two checks; `ROLE_ADMIN` ≠ isolation; read Admin API AGENTS § Authorization.”
2. **PR / review checklist habit** — for every new Admin `*Controller` method: (a) function role
   honest? (b) object `canAccess*` or listed dialect? (c) hide-existence 404 for get-by-id?
3. **Do not** put `@PreAuthorize("hasRole('ADMIN')")` on services expecting tenant isolation —
   services are not the isolation boundary unless they take `AdminPrincipal` and encode tenant
   rules (dialect 3).
4. **Sub-resource rule** (already in `ezkey-tests/AGENTS.md`): `GET /{id}` coverage does not
   cover `GET /{id}/qrcode` (etc.); add deny + own-tenant rows when adding such routes.
5. Keep `docs/API_SECURITY_MATRIX.md` role story honest; do not resurrect “unrestricted ADMIN.”

### Gains

- Zero production authz cutover risk.
- Matches 2026-08-20 Path A and current code.
- Aligns with 80/20: isolation lives in `canAccess*` / list filters — where it already must live
  even if the umbrella is dropped.
- Low maintenance for a unified Global+Tenant SPA.

### Risks

- Isolation remains **opt-in** at each new endpoint; discoverability failure → fail-open.
- Reviewers may still misread `hasRole('ADMIN')` despite docs.
- Annotation honesty stays inconsistent (36 umbrella vs 1 `hasAnyRole`).

### Distance (touch surface)

| Work | Estimate |
|------|----------|
| Docs / rules / checklist | Small — this pack + optional `.cursor/rules` or root AGENTS pointer |
| Java production | **None** required |
| Optional annotation honesty peel | ~36 method annotations + tests expecting 403 on wrong role — medium, optional |

### Characterization tests prerequisite?

**Strongly recommended before claiming A is “hardened,”** but not a blocker to *accepting* A as
the role model. Fill unit gaps in `AccessControlServiceTest` (Tenant Admin allow/deny). Keep
running `TenantCrossIsolationSecurityTest` and `EncryptionKeyControllerSecurityWebMvcTest` on
authz-touching PRs.

---

## Option B — Split authorities greenfield-style

### What it is (from-scratch design)

If designing Admin authz today with the same product roles:

1. **Issue only type roles** from the filter: `ROLE_GLOBAL_ADMIN` or `ROLE_TENANT_ADMIN`
   (and later integration admin if revived). **Do not** issue `ROLE_ADMIN`.
2. **Function annotations:**
   - Platform / instance: `@PreAuthorize("hasRole('GLOBAL_ADMIN')")` (encryption keys, tenants,
     alerts, audit-chain, Global-only admin lifecycle ops).
   - Shared operator surfaces: `@PreAuthorize("hasAnyRole('GLOBAL_ADMIN','TENANT_ADMIN')")`
     (enrollments, integrations, API keys, dashboard, tenant-scoped admin lists).
3. **Object checks unchanged in kind:** `AccessControlService.canAccess*` (or dialect 2/3)
   remain mandatory. Dropping the umbrella is **annotation honesty**, not isolation.
4. **Permissions / scopes?** Not required for Ezkey’s two human admin types. Fine-grained
   permission strings (e.g. `enrollment:read`) add accidental complexity unless a third actor
   type or customer-facing RBAC appears. Prefer **roles + object scope** (current product
   shape).
5. **Where checks live:** keep **controller** method security for function gates; keep object
   checks at controller boundary (or dialect 3 service with principal). Avoid dual
   controller+service `@PreAuthorize` duplication.
6. **Admin UI:** still branches on `adminType`; no Spring authority change needed in the SPA.

### Gains

- `hasRole('ADMIN')` stop-word disappears — copy-paste cannot admit both types via a misleading
  umbrella (Tenant Admin simply lacks `GLOBAL_ADMIN`).
- **Global-only** mistakes fail closed more often (Tenant Admin lacks that authority). Shared
  `hasAnyRole(GLOBAL,TENANT)` still admits Tenant Admin to the *route*; object checks remain
  mandatory. Honesty helps Global-only gates more than object isolation.
- Matches the greenfield note already in Admin API `AGENTS.md`.

### Risks

- **Large simultaneous cutover:** filter + every `hasRole('ADMIN')` + any code/tests that assert
  `ROLE_ADMIN` + docs/matrix/Bruno assumptions.
- **Does not remove** the need for `canAccess*` — agents can still ship get-by-id without object
  checks (`hasAnyRole` admits Tenant Admin to the route).
- Mid-cutover broken main if not atomic or feature-flagged.
- Low product gain for solo maintainer relative to characterization + discoverability cost.

### Distance (touch surface)

| Area | Estimate |
|------|----------|
| `AdminTokenAuthenticationFilter` | 1 file — stop adding `ROLE_ADMIN` |
| Controllers | ~36 `ADMIN` annotations → `hasAnyRole` or `GLOBAL_ADMIN`; ~25 Global already OK |
| Tests asserting `ROLE_ADMIN` | WebMvc / security tests (e.g. encryption-key “generic ADMIN alone → 403” cases need rewrite) |
| Docs | AGENTS, API_SECURITY_MATRIX, assessment cross-links, Bruno notes if any |
| Admin UI / OpenAPI | Likely none for authorities (session still `adminType`) |
| Auth / Integration APIs | None for this human-admin umbrella |

Roughly: **one focused Admin API module program**, not a repo-wide rewrite — but **frontal** on
the filter+annotation pair (unsafe to drop filter grant without migrating annotations).

### Characterization tests prerequisite?

**Yes — mandatory.** Before dropping `ROLE_ADMIN` from the filter:

1. Green functional isolation suite (`TenantCrossIsolationSecurityTest` and related).
2. Green Global-only WebMvc suite (`EncryptionKeyControllerSecurityWebMvcTest` and peers for
   tenants/alerts if thin).
3. Expanded `AccessControlServiceTest` for Tenant Admin allow/deny.
4. Explicit tests that Tenant Admin receives **403** on platform routes (not only encryption
   keys).
5. A temporary “both models” period is possible (issue type roles only *and* keep umbrella
   until annotations migrated) — that is Option C’s phase, not pure B.

---

## Option C — Hybrid / phased (honest middle)

### What it is

1. **Now:** accept Option A as the living model (keep umbrella).
2. **Harden discoverability** (A’s doc/rule checklist) without Java remodel.
3. **Fund characterization gaps** (test preamble below) as the real safety net.
4. **Optional later peels (independent, ordered):**
   - Hide-existence 403→404 (CTRL-ROLE-003b) — *not* a role remodel.
   - Dialect 2→1 on retire/delete (004b).
   - Annotation honesty: migrate shared `ADMIN` → `hasAnyRole(GLOBAL,TENANT)` while **still**
     issuing `ROLE_ADMIN` (harmless redundancy) or stop issuing it only after migration is
     complete.
5. **Only if** isolation defects keep recurring after A+tests: fund Option B as a named
   program slice with the characterization gate.

### Gains

- Matches pragmatism and prior Path A without pretending B is free.
- Separates deny-shape / dialect debt from authority remodel.
- Gives Patrick a clear “accept now” vs “schedule B” fork.

### Risks

- Hybrid can become “never finish honesty peel” — acceptable if A+tests hold.
- Requires discipline not to bundle unrelated peels into one scary PR.

### Distance

Near-term = A. Optional honesty peel = medium. Full B = medium-large Admin API cutover after
tests.

### Characterization tests prerequisite?

Yes for any step that removes `ROLE_ADMIN` from the filter. Recommended (not blocking) for
accepting C/A as the decision.

---

## Test preamble recommendation

### Existing tests to keep / extend

| Test | Role |
|------|------|
| `ezkey-tests/.../TenantCrossIsolationSecurityTest` | P0 HTTP isolation: lists filtered; cross-tenant get/mutate deny; Global cross-tenant read; QR deny/own |
| `ezkey-admin-api/.../EncryptionKeyControllerSecurityWebMvcTest` | SEC-017: Tenant Admin + bare `ROLE_ADMIN` → 403 on encryption-key routes |
| `ezkey-admin-api/.../EnrollmentControllerQrAccessTest` | Object check on QR (CTRL-ROLE-001) |
| `ezkey-admin-api/.../AccessControlServiceTest` | Unit ACS — **today Global Admin + invalid only** |
| `ezkey-admin-api/.../AdminProvisioningServiceTest` | Tenant Admin listing isolation for admins |
| `ezkey-tests` multi-tenant helpers / `MultiTenantGlobalAdminTest` | Global operator behaviors |

### Gaps to fund *before* any redressement (intent-level)

1. **`AccessControlServiceTest` Tenant Admin matrix** — own-tenant allow / foreign-tenant deny
   for integration, enrollment, auth attempt (mock repositories; no Spring context required).
2. **Platform surface WebMvc sample beyond encryption keys** — Tenant Admin 403 on at least one
   Tenant CRUD or Alert route (guards against SEC-017-class regression on a second controller).
3. **New `/{id}/…` sub-resource template** — whenever a sub-resource is added, add
   `TenantCrossIsolationSecurityTest` deny + own-tenant 200 (already stated in
   `ezkey-tests/AGENTS.md`).
4. **Optional:** assert Tenant Admin **403** (not 200) on `GET /api/v1/encryption-keys` in
   functional suite (complements WebMvc) if clean-start cost is acceptable on security PRs.
5. **Do not block** on full 403→404 oracle unification before deciding A/B/C — that is a separate
   contract peel; characterization should accept today’s documented mixed deny until 003b lands.

### Tiny sketch (unit intent only)

```text
tenantAdmin_canAccessIntegration_whenSameTenant() -> true
tenantAdmin_canAccessIntegration_whenForeignTenant() -> false
tenantAdmin_canAccessEnrollment_whenForeignTenant() -> false
globalAdmin_canAccessIntegration_any() -> true   // already present
unknownRole_denied()                             // already present
```

---

## Phasing plan

### Can this be phased safely?

**Yes for A and for C’s peels. No for naïve B.**

Dropping `ROLE_ADMIN` from the filter **before** migrating `@PreAuthorize("hasRole('ADMIN')")`
would lock Tenant Admins (and Global Admins) out of ~36 shared endpoints. That cut is
**frontal on the filter+annotation pair** (single coordinated change or ordered migrate-then-
drop). Prefer rebase/atomic PR over multi-week half-migrated main.

### Concrete phases (recommended under Option C)

| Phase | Content | Safe alone? |
|-------|---------|-------------|
| **P0** | Accept decision (ADR-0013); publish this pack; optional root AGENTS / cursor rule pointer | Yes |
| **P1** | Characterization: ACS Tenant Admin unit matrix + one extra Global-only WebMvc sample | Yes |
| **P2** | Independent hygiene: hide-existence 404 peels / dialect 1 (funded separately) | Yes |
| **P3** | Optional annotation honesty (`hasAnyRole` on shared surfaces) while still issuing umbrella | Yes |
| **P4** | Only if funded: stop issuing `ROLE_ADMIN` in the same PR that removes last `hasRole('ADMIN')` | Atomic with P3 completion |

**Frontal cut + possible rebase** applies only to **P4**. Do not combine P4 with 403→404 mass
edits (prior hygiene explicitly: “Do not bundle dropping `ROLE_ADMIN` with those peels”).

---

## Recommended verdict draft (for Patrick to edit)

**Verdict: Option C, executing Option A now — keep the umbrella; do not fund a role-model
redesign unless isolation defects recur after discoverability + characterization.**

### Why (evidence-based, pragmatic)

1. **Isolation is not a Spring role today and would not become one under B.** Object checks and
   list filters remain the real tenant boundary. B improves honesty for Global-only gates; it
   does not delete the SEC-017/QR class of “forgot the second check.”
2. **Path A already settled 2026-08-20** after HITL; this pack elevates that hygiene call to an
   ADR without discarding it. Reversing to B should be a conscious program, not a drive-by.
3. **Distance vs gain for B** is poor for a solo-maintainer lab: filter + ~36 annotations + test
   rewrites, for a property AGENTS can teach in one screen.
4. **Real residual risks** (403 vs 404, dialects, SQL existence oracles) are **orthogonal** and
   already tracked; bundling them into a role remodel increases blast radius.
5. **Invest where fail-open hurts:** characterization tests (especially ACS Tenant Admin) +
   agent discoverability. That is the high-leverage redressement.

### What “done” looks like if Patrick accepts

- ADR-0013 status → `accepted` (or edited judgment).
- Optional: one root/agent pointer to Admin API AGENTS authz section.
- Backlog optional P1 tests — no production Java authority change required.

### When to reopen B

- Repeated SEC-017-class bugs after P0–P1, or
- A third human role / customer RBAC that makes a two-role umbrella actively harmful, or
- Maintainer capacity for an atomic P3→P4 cutover with the characterization suite green.

### Explicit ask

Patrick: **merge this decision pack + accept ADR-0013 (Option C→A)**, or reply with the single
challenge to grill (typically: “is annotation honesty worth funding as P3?”). No application
redesign in the same breath.
