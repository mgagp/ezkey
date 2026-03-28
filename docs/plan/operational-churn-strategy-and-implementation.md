# Operational churn — strategy, session synthesis, and implementation framing

**Purpose:** Single reference for **why** operational churn exists, **what** we learned in design discussions, **trade-offs**, **agreed direction**, and **implementation** guidance. Complements the narrower [operational-churn-ezkey.plan.md](operational-churn-ezkey.plan.md) (tooling, tags, scripts).

**Audience:** Maintainers implementing or extending operational churn and related `ezkey-tests` helpers.

---

## 1. Initial idea (brief)

Introduce **Category B** sustained activity: long-running JUnit flows in `ezkey-tests` (not default CI), with a **bash launcher**, **profiles**, **logs**, and **tags** (`operational-churn`), to surface **concurrency / locking / ordering** issues under modest, production-like load — without building a formal load-testing stack (Category A / k6) upfront.

---

## 2. Stakes and constraints

| Topic | Detail |
|-------|--------|
| **Functional test philosophy** | Suite intended to run **sequentially**, **once**, **without parallelism**; accidental parallel or mixed profiles (e.g. elective + all-tests) can interact badly — **out of scope** for the operational-churn redesign unless explicitly tackled later. |
| **Bootstrap global admin** | After **clean start**, one **Global Admin** (e.g. `admin.docker`) exists. Many helpers assume **one** primary global token path. |
| **Token rotation on login** | `ezkey.admin.token.rotation-on-login` (default **true**) deactivates **previous active tokens** for **that administrator** when a **new** session is established. Logging into **Admin UI** with the **same** account the churn uses invalidates the churn JVM’s bearer token → **401** on Admin API. |
| **AuthTokenManager caching** | In-memory cache must be **revalidated** (or cleared) when rotation can occur; otherwise long runs keep a stale string. |
| **DB constraints** | `enrollment_name` **VARCHAR(64)**; enrollment display names include `username` — **long synthetic usernames** can overflow. **Email** CHECK exists; avoid gratuitous edge-case local parts in tests. |
| **Product roles** | **Global Admin** ≈ platform / IT operations. **Tenant Admin** ≈ business operator per tenant. Operational reality is mostly **tenant-scoped** work after the platform exists. |

---

## 3. Compromises and rejected shortcuts

| Approach | Why not (or when it’s secondary) |
|----------|-----------------------------------|
| **Widen DB columns** just for tests | Does not fix wrong **role modeling**; schema limits stay meaningful for UX and indexing. |
| **Rely only on revalidation** of `admin.docker` token | Reduces 401s after UI login but **does not** isolate **interactive** bootstrap from **automated** churn — same identity, same rotation rules. |
| **Many peer Global Admins created every churn run** | Wrong operational story; hits **max global admins** and adds noise. |
| **Heavy duplication** of helpers | Avoid unless generalization is risky; **minimal overloads** (explicit bearer token) preferred. |

---

## 4. Solution direction (agreed)

### 4.1 Operational story

- **One** operational churn instance should model **tenant-operator** activity: after platform prep, **Tenant Admin** does integrations, enrollments, and **repeated authentication** at a human-intense-but-reasonable rate.
- **Global Admin (bootstrap)** is **IT / platform**: used **briefly** to provision **tenant-level** context, **not** as the steady-state identity for churn loops.

### 4.2 Two-phase operator workflow

1. **`init` (once per environment / after clean start)**  
   - Caller uses the **bootstrap** Global Admin (`admin.docker` or equivalent) **only here**.  
   - Creates **one peer Global Admin dedicated to operational churn** via `POST /api/v1/admins/global` (subject to `ezkey.security.admin.max-global-admins`, default **3** — room for bootstrap + peers).  
   - Completes **passwordless enrollment** for that admin and persists **state** (username + bearer token) in a **gitignored** file under `.ezkey-test/` (same pattern as other test secrets).

2. **Normal churn run (default script, no `init`)**  
   - **Requires** that state file exists and token is still valid (or clear error: run `init`).  
   - Uses the **churn-dedicated Global Admin token** **only** for operations that **require** Global scope (e.g. **create tenant**).  
   - Then provisions **Tenant Admin** + integrations + enrollments **once per run** (or per agreed setup phase).  
   - **Steady-state loop**: **authentication attempts** (and related Auth API steps) using **Tenant Admin** context where appropriate — **no** ongoing use of bootstrap `admin.docker` token.

### 4.3 Interaction with Admin UI

- **Interactive** sessions should use **bootstrap** `admin.docker` (or another human account **not** used by churn automation).
- **Churn-dedicated** global admin should **not** be used for routine UI work; if someone logs in as that user, **rotation** can invalidate automation — acceptable if documented.

### 4.4 Multiple windows

- Several churn processes can share the **same** churn global token file **for API-only** use; avoid logging into the UI as that **same** global user while churn runs.
- Parallel churn + **bootstrap** UI remains isolated by **identity**.

### 4.5 Helpers

- **Minimal generalization**: e.g. `TestDataFactory` overloads that accept an explicit **bearer token** for Admin API calls, delegating existing methods to `getAdminToken()` for backward compatibility — **no** change to default functional-test behavior.

---

## 5. Loop shape (recalibrated)

| Phase | Actor | Behavior |
|-------|--------|----------|
| **Setup (once per run)** | Churn Global (from file) + Tenant Admin helper | Create **one** tenant, **one** Tenant Admin (full enrollment), **integrations**, API keys, **integration enrollments** as needed. |
| **Steady state (loop)** | Tenant Admin (and Auth/Crypto as today) | **Repeated auth flows** at configured intensity — **no** per-iteration full tenant teardown/rebuild unless a future scenario explicitly requires it. |

This matches **“tenant operator after platform handoff”** and minimizes **Global Admin** calls during the long loop.

---

## 6. Synthesis

- Operational churn is a **probe** for real interactions (tokens, roles, DB limits, concurrency).  
- The **dominant** issue for “churn vs UI” was **shared identity** with **rotation on login**, not a broken Admin UI.  
- The **stable** fix is **role-appropriate identities**: **bootstrap** for humans/init, **dedicated churn Global** for tenant creation, **Tenant Admin** for steady operations.  
- **Minimal helper changes** preserve **developer-first** simplicity and keep the **default functional suite** behavior unchanged.

---

## 7. Implementation checklist (living)

**Status (2026-03): completed and manually validated** (functional suite, elective tests, churn init, then churn with two parallel instances).

- [x] State file: `.ezkey-test/operational-churn-global-admin.json` (gitignored via `.ezkey-test/`).  
- [x] `init` entry: script flag and/or Maven profile + JUnit (`operational-churn-init` tag).  
- [x] Init flow: bootstrap token → `POST /admins/global` → enrollment + login → write state (idempotent: skip if valid token exists).  
- [x] `OperationalChurnTest`: require state; setup once; loop auth-only; short usernames for enrollment name limits.  
- [x] `TestDataFactory`: bearer-token overloads for enrollment/auth-attempt where churn uses Tenant Admin.  
- [x] Docs: `operational-churn-ezkey.plan.md` cross-link; operator note on **init** then **run**.  
- [ ] Optional: document `ezkey.security.admin.max-global-admins` if more than one peer global is needed in a given deployment.

---

## 8. Related files

| File | Role |
|------|------|
| [operational-churn-ezkey.plan.md](operational-churn-ezkey.plan.md) | Tags, Surefire, script contract, QA quick guide |
| [ezkey-tests/scripts/run-operational-churn.sh](../../ezkey-tests/scripts/run-operational-churn.sh) | Launcher (`--init` for one-shot peer Global Admin provisioning) |
| `ezkey-admin-api` … `AdminProvisioningController` | `POST /api/v1/admins/global` |
| `AdminTokenRotationProperties` | Rotation-on-login semantics |

---

*Last updated: 2026-03 — strategy agreed; implementation delivered and manually validated (init + churn, including parallel instances).*
