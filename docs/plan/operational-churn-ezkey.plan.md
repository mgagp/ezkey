---
name: Sustained operational churn (Ezkey)
overview: Short-term focus on Category B — sustained, production-representative background activity via dedicated JUnit tests in ezkey-tests (helpers, tags, bash launcher, per-run log files). Category A (k6 or similar) deferred until dedicated capacity testing is needed.
todos:
  - id: define-tag-and-docs
    content: Tag operational-churn, document vs smoke/slow/elective, Surefire exclusion, log file convention
  - id: implement-churn-test-class
    content: JUnit — per-iteration tenant+admins+integrations+keys+enrollments+auth batch; random suffix; optional seed; tunable stop conditions; max duration cap for tokens
  - id: bash-launcher
    content: ezkey-tests/scripts/run-operational-churn.sh — profiles, duration/iterations, default log file per shell, multi-terminal guidance
  - id: defer-category-a
    content: Optional future — k6 or Gatling for formal ramp/thresholds when a customer needs capacity proof
---

# Sustained Operational Churn — Plan (Ezkey)

## Status (2026-03)

**Implemented, validated, and tested.** The churn stack (tag `operational-churn`, profile `operational-churn-tests`, launcher script, peer Global Admin `init` with profile `operational-churn-init`, state file under `.ezkey-test/`) is in the codebase. **Manual validation:** functional test suite, elective tests, operational churn **init**, then operational churn runs — including **two parallel instances** against the same stack — behaved as expected.

For the agreed **two-phase** workflow (bootstrap / dedicated churn global / tenant steady state), see [operational-churn-strategy-and-implementation.md](operational-churn-strategy-and-implementation.md).

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

**Scaling pressure (intentional design):** Prefer tuning **duration**, **iteration count**, and **auth batch size** via properties; for more concurrent load without turning churn into a formal performance test, run **multiple shell instances** (e.g. two to four) against the same stack — see [Multi-terminal churn](#multi-terminal-churn-parallel-shells).

---

## Default churn loop (per-invocation semantics)

**Design goal:** One invocation from a shell should exercise **both** enrollment-heavy and authentication-heavy paths repeatedly, so the default behavior matches operator intuition: “this run walks the full realistic slice, many times.”

**As implemented in `OperationalChurnTest`:** after [init](operational-churn-strategy-and-implementation.md) (peer Global Admin state file), each run performs **one** tenant + integrations + enrollments **setup**, then **loops on auth batches only** until a stop condition. That matches the two-phase operational story (Global + Tenant Admin roles) and differs from the earlier “full tenant rebuild every iteration” sketch below, which remains a possible future scenario.

**Recommended default outer loop (each iteration):**

1. Generate a **unique resource scope** for this iteration (see [Random suffix and test independence](#random-suffix-and-test-independence)).
2. **Create tenant** and **tenant admins** (using existing helpers).
3. **Create integrations** (e.g. internal vs public), **API keys**, and complete **enrollments** (bind / verify / activate as helpers already model).
4. Run a **batch of authentication attempts** (and complete device-side simulation where required) at the configured **intensity profile**.
5. **Repeat** until a stop condition fires (`maxIterations`, `maxDurationMinutes`, or process stop).

Optional JVM properties tune **how many** enrollments vs **how large** the auth batch is per iteration, and overall **stop** conditions. This is **not** a load-test harness; it is **sustained realistic churn** with a clear knob for “more of the same” via extra terminals.

---

## Random suffix and test independence

Operational churn must align with **test independence**, **idempotence where it matters**, and **pragmatic opportunism** (same values as the rest of `ezkey-tests`):

- Every churn **iteration** (and thus every **tenant / named resource** created in that iteration) should use a **random suffix** (or equivalent unique token) so runs do not collide on unique constraints and so **parallel shells** do not step on each other.
- Log the suffix (or a short **run id** derived from it) at the start of each iteration so logs, DB rows, and correlation IDs can be tied together during investigation.

---

## Sessions, tokens, and maximum duration

Long-running churn can outlive **admin session** or **JWT** lifetimes if not handled.

**Preferred approach (implement one or both):**

1. **Refresh** admin/API credentials inside the churn engine on expiry or on HTTP **401**, using the same patterns as existing functional tests; **or**
2. **Document and enforce** a **hard upper bound** on churn duration for a single JVM invocation so token expiry is not a false failure.

**Concrete default:** Cap a single operational-churn invocation at **two hours** (`maxDurationMinutes` default or ceiling, e.g. 120). That is **sufficient** for typical validation: it stays below common long-session configurations, avoids accidental multi-day runs, and keeps investigation bounded. Operators who need longer wall-clock coverage should use **multiple sequential invocations** (fresh tokens each time) or extend duration only after refresh is implemented.

---

## Multi-terminal churn (parallel shells)

With **random suffixes per iteration**, **multiple churn processes** (two, three, or four — a reasonable upper bound) are a **first-class** way to increase concurrent pressure on Ezkey **without** duplicating Category A tooling.

- Each shell = one Maven/JUnit process with its **own** suffix stream and **own** log file (see [Logging and investigation](#logging-and-investigation)).
- Document that **host** CPU/RAM and Docker limits still apply; saturation may look like app bugs when it is local capacity.

---

## Reproducibility for concurrency investigation

The primary goal of operational churn is to **observe** rare **ordering / timing** issues. Root-cause work is much easier with **controlled replay**.

**Pragmatic recommendation:**

- Support an optional **random seed** (e.g. `-Dchurn.random.seed=<long>`). When set, use it to seed all RNG used for: suffix generation, optional **timing jitter** between requests, and any **shuffle** of sub-steps. **Log the seed** (and whether it was explicit or auto-generated) at startup.
- When a failure occurs, the operator can **rerun with the same seed** and, if possible, the same **Ezkey version** and **clean DB** snapshot, to increase the chance of reproducing the failure for a coding assistant or a developer.

This does **not** guarantee deterministic races (OS scheduling, GC, DB still vary), but it **narrows** the search space and is **cheap** to implement.

---

## Logging and investigation

**Scope:** Ezkey is **self-contained** (no outbound email, webhooks, or external SaaS in the product path relevant to churn). Investigation is **logs + DB + API responses** inside the deployment.

**JUnit / operational-churn:**

- Use clear **log prefixes** or **MDC** fields where helpful: `churnInstanceId`, iteration id, tenant id, correlation id from responses when exposed.
- The **bash launcher** should, by default, **write test output to a file** (e.g. under `logs/` or a path passed with a flag), one file **per shell invocation**, including **timestamps** and Maven Surefire output. That supports:
  - Sharing artifacts with **coding assistants** for diagnosis,
  - Cross-checking with **correlation IDs**, entity ids, and timestamps from the Admin/Auth/Crypto services.

**“Metrics” in this plan** means: **structured visibility** (logs, ids, timestamps, optional JVM/process stats if already easy) — **not** a separate metrics stack requirement for Category B.

---

## Audit and history tables (intentional volume)

Growth in **audit** and **history** tables from repeated churn is **expected and desirable**. If Ezkey is to succeed in production, it must **tolerate** this style of sustained, repeated operational churn without falling over. Churn is an intentional stress on **retention-shaped** tables at modest SMB-realistic rates — not an accident to minimize.

---

## Workflow assumption: clean stack and schema

Operational churn is intended to run against a **clean Docker start**, **up-to-date Flyway schema**, and an **empty or known baseline database** — the same precondition as exploratory functional runs and Postman work. **Schema drift** or half-migrated DBs are out of scope for churn debugging; fix the environment first.

---

## Proposed JUnit shape (without distorting JUnit)

**Goal:** One (or few) test class(es) that read **system properties** or **environment** for:

- **Intensity profile:** e.g. `light` | `medium` (spacing between iterations, number of parallel workers, auth batch size).
- **Stop condition:** `maxIterations` **or** `maxDurationMinutes` (with **default cap**, e.g. 120 minutes) **or** run until **Ctrl+C** / process kill (Surefire: use `forkCount=1` and disable per-test timeout for this profile, or run via a **small `main`** wrapper that calls the same engine — only if Surefire gets in the way).
- **Optional:** `churn.random.seed` for reproducibility (see above).

**Pattern (preferred):**

- **`@Tag("operational-churn")`** on a class dedicated to churn only.
- **Single `@Test`** (or one per scenario) implementing the [default churn loop](#default-churn-loop-per-invocation-semantics).
- **Default exclusion** in [ezkey-tests/pom.xml](../../ezkey-tests/pom.xml): `<excludedGroups>...,operational-churn</excludedGroups>` (exact mechanism aligned with existing groups).

This **does** stretch JUnit’s usual “short test” model slightly; mitigations:

- Document that **operational-churn** is **not** for CI on every commit — **manual / pre-release / parallel terminal** only.
- Keep **timeouts** explicit in code (`maxDurationMinutes`) so CI never hangs if you later add a nightly job.

---

## Bash launcher (example contract)

`ezkey-tests/scripts/run-operational-churn.sh` (Git Bash on Windows per project conventions; same location pattern as `run-elective-tests.sh`):

- `--profile light|medium` — maps to JVM properties (`-Dchurn.profile=light`).
- `--minutes N` | `--iterations N` — stop condition (respect **max cap** unless overridden explicitly with documentation).
- **`--log-file PATH` or default** — capture **full Maven/Surefire output** to a **per-invocation file** (timestamp or pid in the name) for sharing and assistant-assisted RCA.
- Passes through to Maven:  
  `mvn test -pl ezkey-tests -Dgroups=operational-churn -Dchurn.profile=medium ...`

Optional: second (or third) terminal while **other** tests run — same Docker stack, **`SPRING_PROFILES_ACTIVE=docker,docker-test`** recommended to avoid **429** noise from rate limits during dev (see [docker/README.md](../../docker/README.md)).

---

## Operator and QA quick guide

Audience: maintainers and QA running sustained churn **manually** (not default CI). Assumes a **clean Docker stack** and **up-to-date schema** (e.g. `./clean-start.sh` or equivalent) before churn.

### Prerequisites

1. Start the Ezkey stack (Admin API, Auth API, Crypto API, Postgres) the same way you use for functional tests.
2. Recommended for local dev: set **`SPRING_PROFILES_ACTIVE=docker,docker-test`** when starting services to reduce **429** rate-limit noise (see [docker/README.md](../../docker/README.md)).
3. Optional: **`EZKEY_ADMIN_TOKEN`** if you inject a pre-obtained Global Admin token; otherwise tests bootstrap/cache tokens like other `ezkey-tests` runs.

### How to start (recommended)

From the **repository root**, using **Git Bash** (Windows):

```bash
./ezkey-tests/scripts/run-operational-churn.sh --profile medium --minutes 30
```

- Output is **tee’d** to a **log file** under `logs/` by default (path printed at start). Use **`--log-file PATH`** to choose a fixed path.
- Stop early with **Ctrl+C** (process exit); the JVM also respects **`--minutes`** and **`--iterations`** caps.

Equivalent without the script (same effect, but you manage logging yourself):

```bash
mvn test -pl ezkey-tests -P operational-churn-tests \
  -Dchurn.profile=medium \
  -Dchurn.maxDurationMinutes=30
```

### Main options

| Option / property | Meaning |
|-------------------|--------|
| **`--profile light \| medium`** / **`-Dchurn.profile`** | **Light**: fewer auth attempts per iteration, longer pauses. **Medium**: more auth attempts per iteration, shorter pauses. |
| **`--minutes N`** / **`-Dchurn.maxDurationMinutes`** | Hard stop after **N** minutes (default in implementation **120** max per invocation unless overridden). |
| **`--iterations N`** / **`-Dchurn.maxIterations`** | Stop after **N** rounds of the **steady-state** loop (auth batches on the enrollments created at setup). Omit for “until duration cap or Ctrl+C”. |
| **`--seed N`** / **`-Dchurn.random.seed`** | Optional RNG seed for **reproducibility** (names/jitter). Logged at startup; reuse the same seed to retry investigation. |
| **`--log-file PATH`** | Script only: write Maven/Surefire stdout/stderr to this file (default: `logs/operational-churn-<timestamp>-<pid>.log`). |

### One shell vs several shells

| Mode | When to use |
|------|----------------|
| **Single shell** | Default: one churn process, one log file, modest sustained load. Tune **duration**, **iterations**, or **profile** to control how long and how heavy each iteration is. |
| **Multiple shells (2–4)** | Same stack, **more concurrent pressure** without turning churn into a formal load test. Each invocation gets its **own** random suffixes and **own** log file—**no manual coordination** beyond starting another terminal and running the same script again with different `--log-file` if you want clear separation. |

**Tip:** Prefer a few parallel shells over extreme single-process settings when you want to stress **concurrency** paths; watch **CPU/RAM** and Docker limits so slowdowns are not mistaken for product bugs.

---

## Additional considerations and blind spots

These should be explicit when implementing or running operational churn:

1. **Surefire / IDE timeouts** — Long loops may hit default test timeouts. Use a dedicated Maven profile with relaxed or disabled Surefire timeout for churn, or run the churn engine from a small `main` that reuses the same setup logic.
2. **Two JVMs competing for host resources** — Churn (Maven process) + Docker Desktop + another `mvn test` terminal can stress **RAM and CPU** on a laptop; symptoms may look like “backend bugs” when they are **local resource limits**. Document expected baseline (close browsers, allocate enough Docker memory).
3. **Crypto API as bottleneck** — If every iteration calls the Crypto API for signing, churn may **saturate 9090** before Admin/Auth APIs, masking realistic contention. Option: batch pre-sign off hot path, or document that Crypto-bound churn measures **integration test path**, not pure API throughput.
4. **Data accumulation** — Churn **creates** rows (tenants, attempts, audit). For **multi-day** accidental runs, cap iterations or document periodic `clean-start`; for normal **single-session** churn, volume is **intentional** (see [Audit and history tables](#audit-and-history-tables-intentional-volume)).
5. **Flaky interpretation** — Intermittent failures under churn may be **ordering/timing**, not always bugs. Use **log files**, **seed**, and DB state; consider rerunning once on idle stack to separate **concurrency bugs** from **environment noise**.
6. **HA vs single instance** — Locks and ShedLock behavior differ in [docker-compose.ha.yml](../../docker/docker-compose.ha.yml). If churn is meant to catch **distributed** issues, document occasional runs against HA; default single-stack is enough for **single-node** races.
7. **Security / secrets** — Churn scripts must not commit tokens; use env vars and same patterns as existing tests (`.ezkey-test` gitignored).
8. **CI guardrails** — Ensure `operational-churn` is **never** enabled in default CI jobs to avoid hanging pipelines; if a nightly job is added later, use strict `maxDurationMinutes`.
9. **External side effects** — None expected: Ezkey is **self-contained** for this scope; no email/notification pipeline to mock for churn.

---

## Relationship to previous “k6 + Category A/B” plan

- **Category B** is **retained** in intent (second terminal, sustained activity, concurrency realism).
- **Implementation priority** shifts to **JUnit + helpers + `operational-churn` tag + bash script + investigation hooks (seed, logs)**.
- **k6** is **deferred** and **optional** for **Category A** when formal load testing becomes a business requirement — not a prerequisite for the churn strategy.

---

## Summary table

| Item | Decision |
|------|----------|
| **Near-term delivery** | JUnit `operational-churn` in ezkey-tests |
| **Category A (ramp / formal load)** | Deferred; k6 remains a reasonable option when needed |
| **Naming** | Prefer tag **`operational-churn`**; doc alias “production-representative sustained activity” |
| **Intensity** | Profiles (`light` / `medium`) via properties + bash; SMB-realistic rates; extra pressure via **multiple shells** (2–4) |
| **Default loop** | **Once per run:** tenant + admins + integrations + keys + enrollments; **then** repeat **auth batches** until stop (not a full tenant rebuild every round). See [strategy doc](operational-churn-strategy-and-implementation.md) §5–7. |
| **Independence** | **Random suffix** per iteration; supports **parallel terminals** |
| **Tokens / duration** | **Refresh** and/or **documented max**; default cap **~2 h** per invocation |
| **Reproducibility** | Optional **`-Dchurn.random.seed`**; log seed at startup |
| **Logs** | **Per-shell log file** by default from launcher; MDC/prefixes for churn in JUnit |
| **Objective** | Surface parallelism / lock / conflict issues under modest sustained activity; **audit volume is intentional** |
| **Operator / QA guide** | [Operator and QA quick guide](#operator-and-qa-quick-guide) in this document |
| **Implementation** | `OperationalChurnTest`, tag `operational-churn`, profile `operational-churn-tests`, `ezkey-tests/scripts/run-operational-churn.sh` |
| **Plan file (Git)** | `docs/plan/operational-churn-ezkey.plan.md` |
