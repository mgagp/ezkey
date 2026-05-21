# Grill Me — Blitz 2026-05-08-1, D1 + D2 (re-encryption async + HA Docker)

## Session control

| Field | Value |
|-------|--------|
| **Blitz source** | `product-docs/global/backlog/blitz-archive/blitz-2026-05-08-1.md` (D1, D2) |
| **Backlog** | `I-2026-0002`, `I-2026-0003` |
| **Status** | `complete` |
| **Date** | `2026-05-19` |

---

## D1 — Re-encryption manual triggers (async only)

### D1-1 — No blocking API work

Manual re-encryption triggers must **never** block the HTTP request while batch crypto runs. Work is always **background** (existing batch engine / scheduler / async execution path).

### D1-2 — HTTP contract (grill recommendation adopted)

**Use `202 Accepted`** for all manual trigger endpoints once enqueue is done.

| Aspect | Decision |
|--------|----------|
| Status | **202** — semantically correct for “accepted, processing later”; avoids implying completion; aligns with REST/async norms and future consumers (CLI, proxies, monitoring). |
| Body | Minimal JSON: `message` (human-readable) + **`batchIds` optional** — include when already known at enqueue time (low cost); **not required** for clients. |
| Source of truth for UI | Existing paginated **`GET …/reencryption-batches`** — Admin UI invalidates/refetches after trigger; no dependency on response IDs for progress. |

**Why not `200` at equal complexity:** `200` suggests the operation finished; here only **submission** finished. The UI gain from IDs in the response is marginal; the win is **correct semantics**, not extra features.

### D1-3 — Operator UX

- Toast: batches **started** (or equivalent).
- Operator follows progress in the **existing re-encryption batches table** in Admin UI.
- Do not keep a blocking dialog waiting on HTTP completion.

### D1-4 — One async contract for R1

All three manual paths share the same async contract in R1:

- Per-key re-encrypt
- Full re-encrypt trigger
- Create batches + immediate execution (enqueue path, not inline process in request thread)

### D1-5 — Scope boundary

- **In scope:** remove synchronous processing from manual trigger endpoints; standardize enqueue + **202**.
- **Out of scope:** batch engine, parallelism, sharding, scheduler behavior.

### Implementation note (pre-grill evidence)

`ReencryptionService.triggerReencryptionForKey` / `triggerFullReencryption` currently create and **process** batches before returning — confirmed sync today; UI `mutation.isPending` matches that.

---

## D2 — HA Docker stack parity

### D2-1 — Success criterion

Fix **blockers** until HA behaves like the `clean-start.sh` baseline — not a gap inventory-only pass.

### D2-2 — Functional parity

No intentional regression. HA exists to validate **ShedLock** and safe **parallelism** across replicas (schedulers, heartbeat, checkpoints).

### D2-3 — Functional tests posture

- Functional tests remain **sequential** and **unchanged** as a suite — no special HA-only test mode or parallel test option.
- HA validation: run the stack via **`clean-start.sh --ha`** (or equivalent), then exercise the same functional expectations; no new test harness fork for R1.

### D2-4 — Topology (mandatory)

| Service | Instances |
|---------|-----------|
| Admin API | **2** |
| Integration API | **2** |
| Auth API | **2** |

Required to exercise ShedLock mutual exclusion and multi-instance behavior.

### D2-5 — QA + product narrative

HA serves **dual role**:

1. **Dev/QE** — prove distributed locking and multi-instance stability.
2. **Product narrative** — short **deployment profile** doc so operators can run local HA for demos (internal scalability story).

Link to `V-2026-0010` profile elaboration when documenting.

### D2-6 — Alignment vs tolerance

- Expect **modest** Caddy/proxy glue only; stack was working months ago.
- **Goal: full alignment** with baseline — do not document large accepted drifts as the default outcome.
- Entry points: `ezkey-tests/clean-start.sh --ha`, `docker/start-ha.sh`, `docker-compose.ha.yml`.

---

## Links

- [`../blitz-archive/blitz-2026-05-08-1.md`](../blitz-archive/blitz-2026-05-08-1.md)
- [`../ideas/I-2026-0002-reencryption-batch-async-button.md`](../ideas/I-2026-0002-reencryption-batch-async-button.md)
- [`../ideas/I-2026-0003-ha-docker-stack-parity-review.md`](../ideas/I-2026-0003-ha-docker-stack-parity-review.md)
- [`../../vision/product-orientation-notes.md`](../../vision/product-orientation-notes.md) (`V-2026-0010`)
