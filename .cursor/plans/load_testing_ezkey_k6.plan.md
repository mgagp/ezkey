---
name: Load testing Ezkey (k6)
overview: Two-tier load strategy with k6 — (A) autonomous ramp/burst scenarios and (B) optional steady background pressure during functional tests on Clean Start Docker.
todos:
  - id: phase1-k6-skeleton
    content: Add ezkey-load/ with k6, smoke + env config, README
  - id: phase2-category-a
    content: Implement Category A scripts (ramp, plausible Admin/M2M scenarios)
  - id: phase3-category-b
    content: Implement Category B steady companion (low RPS, long duration, optional launcher)
  - id: phase4-docs-integration
    content: Document pairing with ezkey-tests, docker-test profile, parallel execution
---

# Load Testing Strategy for Ezkey (k6) — Revised Plan

## Opinion on the two-category idea

**This is a strong, pragmatic split** and aligns with how mature teams separate concerns:

- **Category A (autonomous load)** answers: *"Where does the system break or degrade under increasing demand?"* — throughput, latency percentiles, saturation, quick wins on hotspots.
- **Category B (steady companion)** answers: *"Do correctness and security still hold when the stack is not idle?"* — race conditions, optimistic locking, connection pool contention, scheduler interactions, rate-limit edge cases, and "feels like shared dev" realism.

Functional tests alone often run against a **cold, idle** stack; that can hide bugs that only appear under **concurrent readers/writers** or **background churn**. Running light, continuous traffic in parallel is a well-known pattern (sometimes called **soak + functional**, **noise injection**, or **churn**); it is complementary, not redundant, with Category A.

**Caveats to design in:**

- **Rate limits**: Docker `docker` profile applies production-like limits; combined runs may hit 429s on the load generator unless you use `docker,docker-test` for permissive limits during dev (see [docker/README.md](docker/README.md)).
- **Shared database**: Background load increases row churn and lock contention; functional tests must remain **independent** (unique IDs, no assumptions about "empty DB") — consistent with existing ezkey-tests philosophy.
- **Determinism**: Category B is **not** for strict performance baselines; it is for **concurrency stress** and realism. Keep Category A for structured ramp and thresholds.

---

## Category A — Autonomous load (traditional)

**Purpose:** Plausible scenarios, **ramp-up** or **staged** load, measurable thresholds (p95, error rate).

**Characteristics:**

- Run **standalone** (no functional suite required).
- Scenarios: Admin API with API Key (M2M), read paths (health, list integrations), optional Auth API flows with pre-seeded enrollments + Crypto API.
- k6 options: `ramping-vus`, `ramping-arrival-rate`, or stages with explicit `duration`/`target`.
- Output: thresholds pass/fail, optional JSON summary for CI.

**When to use:** Before releases, after perf-related changes, capacity "order of magnitude" checks.

---

## Category B — Steady background companion (functional-test era)

**Purpose:** **Light, continuous** traffic while you run **ezkey-tests** (or manual exploration) against **Clean Start Docker** — simulates "you are not alone on the system."

**Characteristics:**

- **Low sustained RPS** (e.g. 1–10 req/s total, tunable) rather than spikes.
- **Long duration** or **until stopped** (k6 `duration: 0` with external stop, or a wrapper script).
- **Read-heavy or safe-idempotent** endpoints preferred to avoid corrupting functional test data (e.g. `GET /actuator/health`, `GET /integrations` with a dedicated token, optional trivial M2M calls if keys exist).
- **Optional**: separate shell script or npm/bash target, e.g. `./scripts/load-companion.sh` — not part of default `mvn test`.
- Run in a **second terminal** alongside `mvn test -pl ezkey-tests`; same host, same Docker ports.

**What it surfaces:**

- Connection pool / thread pool behavior under non-zero baseline load.
- Interactions with **ShedLock**, schedulers, or **DB contention** when functional tests write concurrently.
- **Rate limit** and **429** behavior if profiles are misaligned (signals misconfiguration, not necessarily product bugs).

**k6 fit:** `constant-arrival-rate` with low `rate`, or `constant-vus` with `sleep()` between iterations to avoid hammering; prefer **open** models for steady RPS.

---

## Operational matrix

| Aspect | Category A | Category B |
|--------|--------------|------------|
| **Trigger** | On demand / CI | Optional second terminal during functional runs |
| **Load shape** | Ramp, stages, spike | Flat, low, continuous |
| **Primary goal** | Capacity / latency / saturation | Concurrency / realism / "not idle" |
| **Typical duration** | Minutes | 30 min+ or entire test session |
| **Profile** | `docker` or `docker-test` | Prefer `docker,docker-test` when combined with heavy functional tests |

---

## Project layout (conceptual)

```
ezkey-load/
├── scripts/
│   ├── category-a/
│   │   ├── admin-m2m-ramp.js      # ramp: auth-attempts + wait (API Key)
│   │   └── admin-read-ramp.js     # ramp: list integrations, health
│   └── category-b/
│       └── steady-companion.js    # constant low RPS, health + safe reads (+ optional M2M)
├── config/
│   └── env.js                     # BASE_URL, tokens from env
├── scripts/
│   └── run-companion.sh           # optional; documents k6 invocation for Category B
└── README.md                      # Category A vs B, docker-test, parallel with ezkey-tests
```

---

## Implementation notes (k6)

- **Category A:** Use `export const options = { scenarios: { ... } }` with `ramping-vus` or `ramping-arrival-rate`; define `thresholds` for p95 and `http_req_failed`.
- **Category B:** Use `constant-arrival-rate` executor with `rate: 2`, `timeUnit: '1s'`, `preAllocatedVUs` small; loop body only **safe** requests; document that M2M scenarios need a **dedicated** API key to avoid invalidating functional test data.
- **Shared fixtures:** Same env vars as ezkey-tests (`EZKEY_ADMIN_TOKEN`, base URLs); Category B may use a **minimal** token or health-only if you want zero setup.

---

## Relationship to original "Phase 6" roadmap

Phases 1–5 from the earlier evaluation remain valid; add explicit deliverables:

- **Phase 3b:** Category B script + optional `run-companion.sh` + README section "Running alongside functional tests."
- **Phase 4:** Document **when to use `SPRING_PROFILES_ACTIVE=docker,docker-test`** for combined Category B + ezkey-tests.

---

## Summary

| # | Category | Role |
|---|----------|------|
| 1 | Autonomous ramp (A) | Traditional load test, thresholds, capacity |
| 2 | Steady companion (B) | Optional background noise during functional tests, concurrency realism |

Both use **k6**; they differ by **load shape**, **duration**, and **objective**, not by tooling.
