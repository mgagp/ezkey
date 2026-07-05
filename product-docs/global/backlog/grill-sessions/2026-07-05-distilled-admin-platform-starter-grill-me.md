# Grill Me — Distilled admin platform starter (2026-07-05)

## Session control

| Field | Value |
| ----- | ----- |
| **Trigger** | Operator idea: distill Admin API + Admin UI into an ecosystem starter beyond SDK jumpstart; address "passkeys + DIY token tables" objection. |
| **Vision** | [`V-2026-07-05-distilled-admin-platform-starter`](../../vision/V-2026-07-05-distilled-admin-platform-starter.md) |
| **Backlog** | [`I-2026-07-05-distilled-admin-platform-starter`](../ideas/I-2026-07-05-distilled-admin-platform-starter.md) |
| **Started** | `2026-07-05` |
| **Closed** | `2026-07-05` |
| **Status** | `complete` — pre-analysis accepted; program **parked** until post–R1 or distribution trigger |
| **Captured by** | Marc (operator); grill materialized by agent session |

## Executive summary (pre-analysis)

**The idea is valid and distinct from existing references**, but only if we are precise about what
is being distilled.

The starter is **not** a smaller Ezkey platform. It is a **generic Spring Boot + React operator
application scaffold** that:

1. Reuses Admin API / Admin UI **cross-cutting patterns** (security filters, opaque session tokens,
   RBAC hooks, DTO/service/OpenAPI conventions, sober UI shell).
2. Integrates **Ezkey MFA as an external dependency** via the **Java Integration SDK** on the
   backend (M2M auth-attempt flow), not via in-process `ezkey-core`.
3. Keeps the browser on **spec-first calls to the starter's own backend** (Orval-style), mirroring
   Admin UI posture — not browser-first Integration SDK usage.

**Revised reuse hypothesis:** ~**50–65%** of infrastructure patterns are reusable once platform
domain is stripped; the remaining effort is domain tables, Ezkey wiring, and operator workflows.

**Recommendation:** Park until post–September 2026 operable release. On resume, start with a
distillation boundary matrix and a single `TB-*` slice: clone → configure Ezkey sidecar → login →
one CRUD screen (sports-club stub).

---

## Q1 — What problem does this solve that Demo ACME and SDKs do not?

| Reference | Shape | Gap |
| --------- | ----- | --- |
| Java / TypeScript Integration SDK | M2M API client library | No full-stack app, no operator UI, no local session/token DB |
| `ezkey-demo-app-acme` | Thymeleaf + Spring Boot + Java SDK | Backend-centric; no modern SPA admin shell; Integration API M2M demo only |
| Admin UI + Admin API (monorepo) | Full platform operator console | Coupled to Ezkey domain (tenants, enrollments, keys, integrity, audit chain) |
| Passkeys / platform auth alone | Device or browser credential | Does not deliver API token lifecycle, RBAC, persistence baseline, documented REST + UI |

**Answer:** The starter fills the **greenfield full-stack admin application** gap — the segment where
builders need login, secured APIs, token tables, RBAC, and an operator UI together. That is the
credible rebuttal to the passkey objection for **non-trivial** community or small-business apps.

**Non-goal:** Claiming Ezkey replaces all auth for trivial one-page apps.

---

## Q2 — What exactly gets distilled vs left behind?

### Distill (generic scaffold)

| Layer | Examples from Admin API / Admin UI |
| ----- | ---------------------------------- |
| Spring Security | Filter ordering, bearer + optional HttpOnly cookie, method security, 401 entry point |
| Session tokens | Opaque server-side tokens, validation service, persistence, logout |
| API conventions | DTO/service/controller split, global exception handling, Springdoc/OpenAPI |
| Persistence baseline | Flyway migrations for users, roles, admin tokens (simplified schema) |
| Cross-cutting | Rate-limit filter hook, CORS profile, trusted-proxy awareness (optional) |
| React shell | Vite toolchain, routing, auth gate, login/wait UX pattern, layout, Orval client wrapper |
| RBAC (simplified) | Role-based method security — not platform multi-tenant semantics |

### Strip (Ezkey platform domain)

| Layer | Reason |
| ----- | ------ |
| `ezkey-core` in-process auth | Starter is a **consumer** of Ezkey, not the authority |
| Tenants, enrollments (platform), encryption keys, re-encryption batches | Platform operability |
| Integrity checkpoints, peripheral audit chain, gap declaration | Platform-only |
| Global Admin / Tenant Admin platform split | Replace with single-tenant starter roles |
| Bootstrap system user, demo-mode themes, dashboard integrity widgets | Platform-specific |

### Integrate fresh (not copy-paste)

| Layer | Approach |
| ----- | -------- |
| Ezkey MFA login | Starter backend uses **Java Integration SDK** (`EzkeyClient`) against a running Ezkey instance; maps approved auth attempt → local session token |
| Domain example | Optional sports-club registration module — proves extension, not shipped as product |

---

## Q3 — How does auth work in the starter without `ezkey-core`?

**Category:** External Ezkey consumer (like Demo ACME), not platform authority.

| Step | Behavior |
| ---- | -------- |
| 1 | User opens starter UI login |
| 2 | UI calls **starter backend** `/auth/login` + `/auth/passwordless-wait` (starter-owned endpoints, Admin-UI-shaped UX) |
| 3 | Starter backend creates auth attempt via **Integration API + Java SDK** |
| 4 | User approves on mobile / Demo Device |
| 5 | Starter backend issues **local opaque session token** (same pattern as Admin API token table) |
| 6 | UI uses bearer or cookie against starter APIs |

**Browser never holds Integration API credentials.** Aligns with [`V-2026-06-30-sdk-dogfood-integrity-posture`](../../vision/V-2026-06-30-sdk-dogfood-integrity-posture.md) D2 and gives the starter a legitimate **layer-B SDK dogfood** role without conflating it with Admin UI in the monorepo.

**Optional adopter path:** username/password added locally — documented as swap, not default.

---

## Q4 — Extraction mechanism: fork, sync, or generator?

| Option | Assessment |
| ------ | ---------- |
| Live sync from monorepo | **Reject** — couples release cadences; distillation boundary blurs |
| Code generator from monorepo subset | **Defer** — accidental complexity for first slice |
| **One-time distillation fork → ecosystem repo** | **Accept (D1)** — explicit copy of selected modules with documented provenance |
| Periodic manual cherry-pick for security fixes | **Accept (D2)** — maintainer-driven; no automated merge |

First slice should **not** block on extraction automation.

---

## Q5 — RBAC, UI fidelity, and naming

| Question | Decision |
| -------- | -------- |
| RBAC model | **Single-tenant starter:** `ROLE_ADMIN` + optional `ROLE_USER`; extension doc for multi-tenant later (**D3**) |
| UI stack fidelity | **Match toolchain** (Vite, Orval, Tailwind, sober layout); **do not** copy Ezkey platform screens (**D4**) |
| Ecosystem repo name | **`ezkey-app-starter`** (working name; catalog entry deferred until repo creation) (**D5**) |
| Minimum Ezkey runtime | **Running Ezkey instance required** for MFA path; optional `mock-auth` profile for UI-only dev with loud disclaimer (**D6**) |

---

## Q6 — Does the 60–75% reuse claim hold?

**Partially — revise downward.**

| Bucket | Estimate |
| ------ | -------- |
| Security + token + Spring conventions | High reuse (~70% of that slice) |
| UI shell (routing, auth, layout, Orval wiring) | Moderate reuse (~60%) |
| Platform domain controllers/services | ~0% — stripped |
| Ezkey integration layer in starter | Net-new wiring (~30% of backend auth slice) |
| Adopter domain (sports club, etc.) | 100% net-new |

**Settled:** Communicate **50–65% infrastructure reuse**, not 75%, until a distillation matrix proves otherwise (**D7**).

---

## Q7 — Relationship to parked SDK dogfood program (`I-2026-0031`)

| Program | Focus |
| ------- | ----- |
| `I-2026-0031` | Honest **first-party narrative** inside monorepo; optional admin-session TS client |
| `I-2026-07-05` | **External greenfield starter** for adopters |

**No collision if:**

- Starter README states it is a **consumer app template**, not Ezkey platform admin.
- Starter backend dogfoods **Java Integration SDK** — complementary to `I-2026-0031` Phase 1 M2M alignment.
- Monorepo Admin UI **does not** migrate for starter extraction (**D8**).

Resume of either program does not require the other, but **`I-2026-0004`** (Integration API alignment) benefits both.

---

## Q8 — What fails first under realistic misuse?

| Failure mode | Mitigation |
| ------------ | ---------- |
| Adopter expects zero Ezkey runtime | README + compose sidecar; fail fast on missing Integration API config |
| Starter confused with "lite Ezkey platform" | Naming, stripped domain, explicit non-goals in README |
| Security drift vs monorepo patterns | Document cherry-pick policy; tag starter releases |
| Over-scoped first slice (full RBAC + multi-tenant) | First `TB-*` = login + one CRUD only |
| Passkey objection still wins for tiny apps | Honest positioning: target non-trivial admin apps |

---

## Q9 — Priority and sequencing

| Item | Priority | Rationale |
| ---- | -------- | --------- |
| September 2026 operable release / integrity | **P1** (unchanged) | This program must not displace |
| `I-2026-0004` Integration API alignment | **P1** (existing) | Prerequisite for correct starter auth wiring |
| Distillation matrix + ecosystem repo shell | **P3** | Post–R1 or side capacity |
| First `TB-*` vertical slice | **P3** | After matrix + repo shell |

**Park active execution** until post–R1 gate or explicit distribution trigger (**D9**).

---

## Q10 — Minimal evidence that would disprove the plan

1. Distillation matrix shows **<40%** reusable infrastructure → reconsider separate repo; SDK + ACME may suffice.
2. Java Integration SDK cannot cleanly support starter login flow without hacks → revisit BFF or admin-session client (`I-2026-0031` phase 2).
3. Maintainer cost of second repo exceeds adopter pull (no clones, no issues) within 6 months of publish → archive or merge into SDK demo workspace.

---

## Settled decisions (operator session 2026-07-05)

| ID | Decision | Status |
| -- | -------- | ------ |
| D1 | Extraction = **one-time distillation fork** to ecosystem repo, not live monorepo sync | **Confirmed** |
| D2 | Security fixes = **manual cherry-pick** policy, documented in starter README | **Confirmed** |
| D3 | RBAC = **single-tenant** `ROLE_ADMIN` (+ optional `ROLE_USER`); no Global/Tenant Admin split | **Confirmed** |
| D4 | UI = **match toolchain**, strip platform screens | **Confirmed** |
| D5 | Working repo name = **`ezkey-app-starter`** | **Confirmed** (catalog update deferred) |
| D6 | MFA path requires **running Ezkey**; mock-auth dev-only with disclaimer | **Confirmed** |
| D7 | Reuse hypothesis revised to **50–65%** infrastructure (not 75%) | **Confirmed** |
| D8 | Monorepo Admin UI/API **not rewritten** for extraction | **Confirmed** |
| D9 | Program **parked** until post–R1 or distribution trigger | **Confirmed** |
| D10 | Starter auth = **backend Java SDK + local opaque tokens**; UI → starter API only | **Confirmed** |

---

## Open items (non-blocking — for resume)

1. Distillation boundary **mapping matrix** artifact (component design pack).
2. `docs/ECOSYSTEM_REPOSITORIES.md` row when repo is created.
3. Whether sports-club stub ships in v0.1 or v0.2 of starter.
4. Optional packaged TypeScript login helpers — defer to `I-2026-0031` phase 2 if needed.

---

## Closeout (2026-07-05)

Pre-analysis and grill complete. **`I-2026-07-05` parked** — no `TB-*`, no ecosystem repo, no
distillation matrix until resume trigger.

**Resume when:** post–September 2026 operable-release gate; explicit `F-sdk-and-cli-growth` capacity;
or repeated adopter demand for full-stack starter beyond SDKs and Demo ACME.

## Related code anchors (evidence)

- Admin SecurityConfig: `ezkey-admin-api/.../config/SecurityConfig.java`
- Opaque bearer filter: `ezkey-admin-api/.../security/AdminTokenAuthenticationFilter.java`
- Admin UI Orval login: `ezkey-admin-ui/src/pages/login.tsx`
- Demo ACME SDK pattern: `ezkey-demo-app-acme/.../controller/LoginController.java`, `EzkeyClientConfig.java`
- SDK dogfood grill (layer A/B): [`2026-06-30-sdk-dogfood-grill-me.md`](2026-06-30-sdk-dogfood-grill-me.md)
