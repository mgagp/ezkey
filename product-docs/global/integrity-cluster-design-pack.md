# Integrity Cluster — Design Pack (Wave B)

## Purpose

Canonical design for **Wave B** — the integrity and operational-trust cluster ahead of the
September 2026 operable-release target. Covers rolling attach vs nightly detect, heartbeat/degraded
mode, alert queue semantics, batch last-run visibility, and the **implementation order** for
`I-2026-0005`, `I-2026-0006`, and `I-2026-0007`.

**Grilled:** Blitz 2026-05-08-1 D4–D6
([`backlog/grill-sessions/integrity-cluster-D4-D6-grill-me.md`](backlog/grill-sessions/integrity-cluster-D4-D6-grill-me.md)).

**Vision:** [`V-2026-0004`](vision/V-2026-0004-integrity-validation-strategy.md).

**Release compass:**
[`operational-readiness-prioritization-2026-09.md`](operational-readiness-prioritization-2026-09.md).

**GitHub program issue:** #269 (Wave B umbrella — see `I-2026-0005` / `I-2026-0006` / `I-2026-0007` metadata).

## Design principles

| Principle | Application |
|-----------|-------------|
| Rolling = attach; nightly = detect | Honest two-layer contract (`V-2026-0004`); no false “all green” during lookback blind spot. |
| Alerts-first queue (R1) | Reuse `ezkey_alert`; no parallel incident universe (`INC-2` settled). |
| Invisible when healthy | Dashboard/widgets and alert banner **after** detection/resolution paths work. |
| Global Admin only | Integrity remediation, snooze, crypto ops — not Tenant Admin (`operator-alignment-guide`). |
| Beautiful problems (#14) | Full-window nightly scan R1; no audit-count cap pre-optimization. |
| Backend-first (#3) | Degraded 503 on Auth/Integration APIs; Admin API owns schedulers and verification. |

## Current state (shipped baseline)

| Capability | Status | Code / doc |
|------------|--------|------------|
| Rolling checkpoint scheduler | **Shipped** | `AuditChainScheduler`, `ezkey.audit.chain.*` |
| Gap pending alerts | **Shipped** | `AUDIT_CHAIN_GAP_PENDING` → declare-gap resolves |
| Heartbeat guard + degraded mode | **Shipped** | `AuditChainHeartbeatGuardService`; 503 on I/A APIs |
| Heartbeat stale alerts | **Shipped** | `AUDIT_CHAIN_HEARTBEAT_STALE` auto-resolve |
| Chain verification service | **Shipped** | `AuditChainVerificationService.verifyChain(from, to)` |
| Ad hoc verification API | **Shipped** | Lifecycle / verification endpoints (operator-triggered) |
| Alerts list + detail (read-only) | **Shipped** | Admin UI `/alerts`; no manual resolve/snooze UI yet |
| Dashboard open-alert strip | **Shipped** | `DashboardService` includes recent OPEN alerts |
| **Nightly retroactive batch** | **Shipped** (B1 / PR #270) | `NightlyIntegrityValidationScheduler`, `ezkey.audit.integrity.nightly.*` |
| **Manipulation remediation flow (chain)** | **Shipped** (B2) | `MANIPULATION_CONCILIATION` + reconcile API |
| **Integrity investigation UX** | **Shipped** (B2.5) | `TB-2026-06-30-integrity-investigation-operability` |
| **Entry HMAC conciliation + alert dedupe coherence** | **Ready → B2.6** | [`TB-2026-07-02-entry-integrity-conciliation-and-alert-coherence.md`](backlog/TB-2026-07-02-entry-integrity-conciliation-and-alert-coherence.md) |
| **Retroactive validation operability (generic detect + operator POST)** | **Ready → B2.7** | [`TB-2026-07-02-retroactive-integrity-validation-operability.md`](backlog/TB-2026-07-02-retroactive-integrity-validation-operability.md) |
| **Batch last-run registry + widgets** | **Gap** | `I-2026-0007` |
| **Snooze** | **Gap** | Grilled C9; deferred to `I-2026-0005` or follow-on TB |

Reference: [`docs/ALERTS.md`](../docs/ALERTS.md).

## Two-layer integrity model (normative)

| Layer | Job | Detection posture | Default config |
|-------|-----|-------------------|----------------|
| **Rolling attach** | `AuditChainScheduler` | Chains checkpoints in lookback; **does not** promise manipulation detection inside lookback | `lookbackMinutes=60`, bounds **15 min–8 h** (enforce in config validation) |
| **Nightly retroactive** | Scheduled batch (default **24 h** window) | Full HMAC + checkpoint chain validation; anomalies → alert | Window **24 h**, cron **once per 24 h** (off-peak default) |
| **Operator retroactive run** | `POST integrity-validation/run` (B2.7) | Same orchestration as nightly; operator-chosen `[from, to)`; may raise/touch alert | Independent of `nightly.enabled`; capped window (TB B2.7) |
| **Ad hoc verify (read-only)** | `GET integrity-check` / `chain-integrity` | Forensic reports only — **no** alert side effects | Always when HMAC active |

**Layer 1** = rolling attach only. **Layer 2 (detect)** = scheduled nightly + operator retroactive run
(shared orchestration per B2.7) + read-only verify for investigation without alert side effects. Periods already closed via gap declaration, heartbeat conciliation, or manipulation
resolution are **excluded** from re-litigation (D5). **Chain:** implemented via conciliation
checkpoint types. **Entry HMAC:** gap — requires `EntryIntegrityConciliation` registry (TB B2.6);
detection must skip alert-eligible re-litigation while verify remains honestly HMAC-invalid.

## Batch last-run registry (shared by `I-2026-0006` + `I-2026-0007`)

Introduce a small **scheduled job registry** (one row per logical job):

| Column | Type | Notes |
|--------|------|-------|
| `job_key` | VARCHAR PK | Stable enum string, e.g. `AUDIT_CHAIN_CHECKPOINT`, `NIGHTLY_INTEGRITY_VALIDATION`, `REENCRYPTION` |
| `last_execution_at` | TIMESTAMPTZ | Nullable until first run |
| `last_status` | VARCHAR | `SUCCESS`, `FAILED`, `NEVER_RUN` (initial seed) |
| `last_run_scope` | VARCHAR | Human-readable, e.g. `Validated 24 h ending 2026-06-28T02:00Z` |
| `last_error_summary` | VARCHAR | Optional; safe operator text on `FAILED` (no stack traces) |
| `updated_at` | TIMESTAMPTZ | Row touch |

**R1 jobs registered:** checkpoint scheduler tick (update on each successful scheduler pass or
aggregate — design choice in TB: **update per successful scheduler cycle** with scope =
`lookback {N} min`), nightly integrity validation, re-encryption scheduler (hook existing job when
present).

**C9 cut line:** Batch **failure** or **staleness** → widget visibility only; **no** “batch did not
run” alert.

## Nightly batch algorithm (R1)

1. Compute `[windowStart, windowEnd)` = retroactive window ending at batch start (default 24 h).
2. Skip sub-ranges covered by resolved gap/heartbeat/manipulation conciliation (reuse lifecycle
   queries; TB specifies repository methods).
3. Run `AuditChainVerificationService.verifyChain(windowStart, windowEnd)`.
4. Run per-audit **entry HMAC** validation for audits in window (TB: delegate to existing verification
   paths or extend service — single orchestration method preferred).
5. **Classify** discontinuity before alert emission (**C8-6 hypothesis**): heartbeat-covered outage
   windows must not also emit manipulation-family alerts for the same underlying gap.
6. On integrity failure: `alertService.raiseOrTouch(...)` with new alert type
   `AUDIT_INTEGRITY_RUPTURE` (R1 name — payload includes fail boundary, verification summary JSON).
7. Update registry row: `SUCCESS` or `FAILED` (batch infrastructure failure ≠ integrity rupture alert).
8. Emit audit meta-event `NIGHTLY_INTEGRITY_VALIDATION_COMPLETED` (TB defines event type).

## Open grill items — resolutions for R1

| ID | Decision for R1 |
|----|-----------------|
| **D5-13** Ad hoc + nightly same day | **Settled (B2.7 + B2.6 D4):** Overlap allowed; incident fingerprint dedupe + entry conciliation skip prevent duplicate OPEN alerts. Operator POST and scheduled batch share `RetroactiveIntegrityValidationService`. |
| **D5-14** Nightly disable in dev | `ezkey.audit.integrity.nightly.enabled=false` default in `application-dev` profile optional; **enabled=true** in Docker clean-start / EXP1. Document in `CONFIGURATION.md`. |
| **D5-15** Sealed audits in window | `ARCHIVE_SEAL` checkpoints: verification **skips entries_digest recompute** (existing service behavior); nightly pass still verifies **chain_hmac** linkage. |
| **C8-6** False manipulation vs heartbeat | Classifier step before alert: if rupture is explainable by active/recovered heartbeat incident in window, emit **heartbeat family** path only. |
| **INC-1** Incident justification storage | R1: conciliation emits **first-class audit event**; incidents/checkpoints remain operational index; no infinite validator recursion. Detail in `I-2026-0005` TB. |

## Implementation waves (TB sequence)

| Wave | TB | Backlog | Primary deliverable |
|------|-----|---------|---------------------|
| **B1** | `TB-2026-06-28-nightly-integrity-validation-batch` | `I-2026-0006` | Nightly scheduler + registry table + rupture alert type + scheduler last-run hooks — **merged PR #270** |
| **B2.5** | `TB-2026-06-30-integrity-investigation-operability` | B2.5 | Investigation UX — shipped |
| **B2.6** | `TB-2026-07-02-entry-integrity-conciliation-and-alert-coherence` | `I-2026-0005` | Entry conciliation + alert dedupe — **under review** |
| **B2.7** | `TB-2026-07-02-retroactive-integrity-validation-operability` | `I-2026-0006` (extends) | Generic retroactive validation + operator POST — **under review** |
| **B3** | `TB-2026-06-28-…` (next) | `I-2026-0007` | Dashboard widgets + open-alert count banner + config summary |

**Branch rule:** one `feature/<issue#>-i-2026-0006-…` branch per TB when **code** starts; design pack
and TB artifacts land on `main`.

## Component boundaries

| Component | Responsibilities |
|-----------|------------------|
| **ezkey-core** | Schedulers, `AuditChainVerificationService` extensions, `AlertService` producers, registry entity/repository, properties |
| **ezkey-admin-api** | Dashboard DTO extension for batch rows (B3); optional read API for job registry (B3); CONFIGURATION.md |
| **ezkey-admin-ui** | Dashboard widgets + banner (B3); alert detail renderers for new types (B1/B2) |
| **ezkey-auth-api / integration-api** | No change in B1; heartbeat guard already emits degraded 503 |

## Configuration (new prefix sketch)

```properties
# ezkey.audit.integrity.nightly.*
ezkey.audit.integrity.nightly.enabled=true
ezkey.audit.integrity.nightly.cron=0 0 2 * * ?
ezkey.audit.integrity.nightly.window-hours=24
```

Rolling bounds validation on existing `ezkey.audit.chain.lookbackMinutes` (15–480) — add in B1 or
small hygiene commit with nightly work.

## Test strategy (cluster)

| Scenario | When |
|----------|------|
| Unit: verification + skip resolved periods | B1 |
| Unit: registry update SUCCESS/FAILED | B1 |
| Integration: nightly batch on clean-start fixture | B1 |
| **Lab:** stop Admin API → heartbeat alert + I/A 503 | B1 validation manual |
| **Lab:** delete audit row → nightly detects → alert | B1 after batch runs |
| Manipulation remediation E2E | B2 |
| Dashboard widget smoke | B3 |

Functional tests in `ezkey-tests` where stable; elective tests for long-running nightly cron.

## Traceability

| Artifact | Role |
|----------|------|
| `I-2026-0005` | Rupture remediation (B2) |
| `I-2026-0006` | Nightly batch (B1) — **first TB promoted** |
| `I-2026-0007` | Dashboard (B3) |
| `TB-2026-07-02-entry-integrity-conciliation-and-alert-coherence` | B2.6 — entry conciliation + dedupe |
| `TB-2026-07-02-retroactive-integrity-validation-operability` | B2.7 — generic detect + operator POST |
| [`integrity-wave-b-operator-end-state-compass.md`](integrity-wave-b-operator-end-state-compass.md) | Fresh-session end-state + corpus map |
| `F-audit-chain` | Feature milestone in [`features-and-phases.md`](features-and-phases.md) |
| `ML-2026-06-28-wave-b-integrity-cluster-kickoff.md` | Method log for this kickoff |
| `ML-2026-07-02-entry-integrity-conciliation-kickoff.md` | B2.6 + B2.7 analysis kickoff |

## Complexity compass (scope guard)

Marginal in-DB integrity sophistication is bounded by
[`integrity-assurance-honest-line.md`](integrity-assurance-honest-line.md): the HMAC key is off-DB,
so the chain is genuinely tamper-evident against accidental corruption and DB-only adversaries, but
blind to a host-rooted adversary. The move that changes that adversary class — external immutable
export — is **deliberately deferred to Phase 2** via a vendor-neutral SPI
([`vision/V-2026-06-28-audit-archive-export-spi.md`](vision/V-2026-06-28-audit-archive-export-spi.md);
the `AuditLifecycleService` seal/confirm FSM intentionally reserves the export transition). For R1,
finish the nightly detective minimum (`I-2026-0006`), keep manipulation-remediation machinery
proportional, and make the **honest Phase-1 posture** discoverable
([`../../docs/SECURITY_POSTURE.md`](../../docs/SECURITY_POSTURE.md)). Apply the red-line test before
adding controls.

## Out of scope (cluster R1)

- Snooze UI/API (grilled yes for R1 — schedule in B2 with `I-2026-0005` unless split)
- Email / external notification
- `V-2026-0013` meta-resolution
- Weekly/monthly mega-windows
- Alerts list matrix polish (`I-2026-0028` P3 — after cluster gate)
- PostgreSQL roles matrix (`I-2026-0021`) — parallel hardening, not blocking B1
