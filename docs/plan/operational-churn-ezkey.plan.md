---
name: Sustained operational churn (Ezkey)
overview: Short-term focus on Category B — sustained, production-representative background activity via dedicated JUnit tests in ezkey-tests (helpers, tags, bash launcher). Category A (k6 or similar) deferred until dedicated capacity testing is needed.
todos:
  - id: define-tag-and-docs
    content: Choose tag name, document meaning vs smoke/slow/elective, Surefire exclusion
  - id: implement-churn-test-class
    content: JUnit class — tenant → admins → integrations → API keys → enrollments → auth-attempt loop with tunable intensity
  - id: bash-launcher
    content: scripts/run-operational-churn.sh with profiles (light/medium) and duration/iteration flags
  - id: defer-category-a
    content: Optional future — k6 or Gatling for formal ramp/thresholds when a customer needs capacity proof
---

# Sustained Operational Churn — Plan (Ezkey)

**Canonical location (Git):** [docs/plan/operational-churn-ezkey.plan.md](operational-churn-ezkey.plan.md) — tracked in the repository; not only in a personal Cursor directory.

---

## Strategic repositioning

| Horizon | Focus | Tooling (default) |
|---------|--------|-------------------|
| **Short–medium term** | **Category B only**: sustained background activity while you run functional tests, exploration, or other work — intensity aligned with **SMB / internal validation** (not hyperscale). | **JUnit in [ezkey-tests](../../ezkey-tests)** — reuse helpers (`TenantAdminTestHelper`, `CryptoApiClient`, `AdminBootstrapService`, RestAssured config). |
| **Later** | **Category A**: autonomous ramp, formal thresholds, capacity story for a demanding customer. | **k6** (or similar) — still valid when that need appears; **not required** to deliver Category B. |

**Rationale:** Ezkey’s first deployments are **small**; the immediate risk is **correctness under non-idle conditions** (locks, races, optimistic conflicts), not proving **hundreds of thousands of RPS**. Category B addresses that. Category A is a **future gate** when scale becomes a commercial requirement.

---

## Honest comparison: k6 vs JUnit for Category B (only)

### When k6 is a real asset

- **High parallelism** from one process (many VUs, low memory).
- **Declarative** RPS / ramp (Category A).
- **Separate process** from the JVM under test (clean separation for *pure* HTTP stress).

### When k6 adds less value for *this* Category B

- You already need **Java helpers** for **crypto**, **multi-tenant rules**, and **realistic flows** (bind, verify, pending, respond). Reimplementing that in JavaScript **duplicates** logic and drifts from production behavior.
- Target intensity is **modest** (SMB profile: order of **tens** of admin actions/day scale on admin UI; **thousands to low tens of thousands** of auth attempts/day on integrations — amortized to a **low steady rate** in a churn loop). **A few JVM threads** or a **tight loop** in one test process is enough to create **contention**, not to saturate hardware.
- **Debugging**: failures surface with **same stack traces and logs** as functional tests if everything stays in `ezkey-tests`.

**Verdict for near-term Category B:** **Capitalizing on existing functional-test infrastructure is the pragmatic default.** Defer k6 until you explicitly want **Category A** (ramp, SLAs, many parallel clients without JVM crypto duplication).

---

## Naming the new test category

These are **not** default functional tests (tags `smoke`, `slow`, etc.) and **not** `elective` (rare, framework-edge operations like key rotation).

**Concept:** Long-running or batch-heavy scenarios that **simulate ongoing production-like activity** to surface **parallelism issues**, not to assert a single happy path.

### Recommended primary name (tag)

- **`operational-churn`** — clear, technical, implies continuous background activity.

### Alternative names (documentation / human language)

| Name | Pros | Cons |
|------|------|------|
| **Operational churn** | Matches industry “churn”; pairs well with “background” | Slightly jargon |
| **Sustained activity tests** | Plain English | Generic |
| **Concurrency validation (soak)** | Emphasizes locks/races | “Soak” sometimes implies hours and APM |
| **Production-representative simulation** | Matches “simili-production” | Long |
| **Background load (functional)** | Descriptive | “Load” may confuse with Category A |

**Maven / JUnit:** e.g. `@Tag("operational-churn")`, excluded from default Surefire like `elective` (only run via profile or explicit `-Dgroups`).

---

## Target activity profile (market positioning)

Rough **order-of-magnitude** for messaging (not rigid SLAs):

- **Admin / CBU-style portal:** very low frequency (e.g. **tens of logins per day** per org).
- **Customer integrations (broader user base):** **thousands to low tens of thousands** of authentications per day **across** integrations — in a **single-stack churn test**, translate to a **small number of auth-attempt cycles per minute** (plus spacing) so the DB and APIs stay “warm” and concurrent with your other tests, without imitating a DDoS.

The **churn test** should expose **parallelism** and **shared-resource** behavior at **internal validation** levels — enough to shake out **obvious** races — **not** to prove hyperscale.

---

## Proposed JUnit shape (without distorting JUnit)

**Goal:** One (or few) test class(es) that read **system properties** or **environment** for:

- **Intensity profile:** e.g. `light` | `medium` (spacing between iterations, number of parallel workers, batch size).
- **Stop condition:** `maxIterations` **or** `maxDurationMinutes` **or** run until **Ctrl+C** / process kill (Surefire: use `forkCount=1` and disable per-test timeout for this profile, or run via a **small `main`** wrapper that calls the same engine — only if Surefire gets in the way).

**Pattern (preferred):**

- **`@Tag("operational-churn")`** on a class dedicated to churn only.
- **Single `@Test`** (or one per scenario) that runs a **`while`** loop with:
  - setup once: create tenant → tenant admins → two integrations (internal vs public) → API keys → enrollments (using existing helpers).
  - loop: create auth attempts (and complete path with device simulation if needed) at the configured rate; optional **parallelism** via `ExecutorService` with fixed pool size from profile.
- **Default exclusion** in [ezkey-tests/pom.xml](../../ezkey-tests/pom.xml): `<excludedGroups>...,operational-churn</excludedGroups>` (exact mechanism aligned with existing groups).

This **does** stretch JUnit’s usual “short test” model slightly; mitigations:

- Document that **operational-churn** is **not** for CI on every commit — **manual / pre-release / parallel terminal** only.
- Keep **timeouts** explicit in code (`maxDurationMinutes`) so CI never hangs if you later add a nightly job.

---

## Bash launcher (example contract)

`scripts/run-operational-churn.sh` (Git Bash on Windows per project conventions):

- `--profile light|medium` — maps to JVM properties (`-Dchurn.profile=light`).
- `--minutes N` | `--iterations N` — stop condition.
- Passes through to Maven:  
  `mvn test -pl ezkey-tests -Dgroups=operational-churn -Dchurn.profile=medium ...`

Optional: second terminal while **other** tests run — same Docker stack, **`SPRING_PROFILES_ACTIVE=docker,docker-test`** recommended to avoid **429** noise from rate limits during dev (see [docker/README.md](../../docker/README.md)).

---

## Additional considerations and blind spots

These should be explicit when implementing or running operational churn:

1. **Surefire / IDE timeouts** — Long loops may hit default test timeouts. Use a dedicated Maven profile with relaxed or disabled Surefire timeout for churn, or run the churn engine from a small `main` that reuses the same setup logic.
2. **Two JVMs competing for host resources** — Churn (Maven process) + Docker Desktop + another `mvn test` terminal can stress **RAM and CPU** on a laptop; symptoms may look like “backend bugs” when they are **local resource limits**. Document expected baseline (close browsers, allocate enough Docker memory).
3. **Crypto API as bottleneck** — If every iteration calls the Crypto API for signing, churn may **saturate 9090** before Admin/Auth APIs, masking realistic contention. Option: batch pre-sign off hot path, or document that Crypto-bound churn measures **integration test path**, not pure API throughput.
4. **Data accumulation** — Align with ezkey-tests philosophy: churn **creates** rows (tenants, attempts). Do not assume a clean DB; avoid unbounded disk growth in multi-day runs (cap iterations or document periodic `clean-start`).
5. **Flaky interpretation** — Intermittent failures under churn may be **ordering/timing**, not always bugs. Capture logs + DB state; consider rerunning once on idle stack to separate **concurrency bugs** from **environment noise**.
6. **HA vs single instance** — Locks and ShedLock behavior differ in [docker-compose.ha.yml](../../docker/docker-compose.ha.yml). If churn is meant to catch **distributed** issues, document occasional runs against HA; default single-stack is enough for **single-node** races.
7. **Security / secrets** — Churn scripts must not commit tokens; use env vars and same patterns as existing tests (`.ezkey-test` gitignored).
8. **CI guardrails** — Ensure `operational-churn` is **never** enabled in default CI jobs to avoid hanging pipelines; if a nightly job is added later, use strict `maxDurationMinutes`.

---

## Relationship to previous “k6 + Category A/B” plan

- **Category B** is **retained** in intent (second terminal, sustained activity, concurrency realism).
- **Implementation priority** shifts to **JUnit + helpers + `operational-churn` tag + bash script**.
- **k6** is **deferred** and **optional** for **Category A** when formal load testing becomes a business requirement — not a prerequisite for the churn strategy.

---

## Summary table

| Item | Decision |
|------|----------|
| **Near-term delivery** | JUnit `operational-churn` in ezkey-tests |
| **Category A (ramp / formal load)** | Deferred; k6 remains a reasonable option when needed |
| **Naming** | Prefer tag **`operational-churn`**; doc alias “production-representative sustained activity” |
| **Intensity** | Profiles (`light` / `medium`) via properties + bash; SMB-realistic rates, not stress |
| **Objective** | Surface parallelism / lock / conflict issues under modest sustained activity |
| **Plan file (Git)** | `docs/plan/operational-churn-ezkey.plan.md` |
