# Method log — EXP1 nightly integrity boundary false positives

## Metadata

- **Date:** `2026-07-09` (investigation); corpus capture `2026-07-10`
- **Program:** Wave B follow-on — Lane D post-delivery (`I-2026-07-10-nightly-integrity-boundary-false-positives`)
- **Environment:** EXP1 production-like instance (SSH tunnel `localhost:6432` → Postgres)
- **Symptom:** Daily CRITICAL `AUDIT_INTEGRITY_RUPTURE` alerts at 22:00 local (EDT) /
  **02:00:01 UTC**, summary “0 entry + 0 chain violations”, all OPEN, occurrence_count=1 each

## Observation

| alert_id | created_at (UTC) | chainStatus | entry/chain crypto counts |
|----------|------------------|-------------|---------------------------|
| 2–7 | 2026-07-04 … 2026-07-09 at `02:00:01` | `UNDECLARED_GAP_DETECTED` | 0 / 0 |

Example payload (alert 7):

```json
{
  "chainStatus": "UNDECLARED_GAP_DETECTED",
  "violationCount": 0,
  "entryHmacViolationCount": 0,
  "windowStart": "2026-07-08T02:00:00.017784077Z",
  "windowEnd": "2026-07-09T02:00:00.017784077Z",
  "failBoundary": "2026-07-08T02:05Z",
  "resumeBoundary": "2026-07-09T01:55Z",
  "chainViolations": { "totalCount": 0 },
  "entryViolations": { "totalCount": 0 }
}
```

Matching audit events: `NIGHTLY_INTEGRITY_VALIDATION_COMPLETED` with `event_status=FAILURE`,
`triggerSource=SCHEDULED`, `intact=false`.

No open `AUDIT_CHAIN_HEARTBEAT_STALE` at investigation time (one historical RESOLVED). Platform
otherwise stable.

## Root cause (confirmed)

Two coupled design/implementation gaps in the **detective** layer shipped with B1 (`I-2026-0006`):

### 1. Window end not aligned to the checkpoint grid

`NightlyIntegrityValidationScheduler` sets `windowEnd = OffsetDateTime.now(UTC)` without rounding.
Checkpoints use exact 5-minute boundaries (`roundDownToWindow`).

`findByWindowRange` selects `windowStart >= from AND windowStart < to`. A `from` of
`02:00:00.017Z` **excludes** the checkpoint whose `windowStart` is `02:00:00.000Z`. Verification
then reports a **leading undeclared gap** from `from` to the next checkpoint (`02:05`), status
`UNDECLARED_GAP_DETECTED`, `intact=false`, even though crypto digests and chain links are fine.

### 2. Alert taxonomy vs UI summary

`RetroactiveIntegrityValidationService` raises `AUDIT_INTEGRITY_RUPTURE` whenever the chain report
is not intact (including undeclared gaps), subject to C8-6 heartbeat deferral. The alert payload
carries `chainViolations` (crypto) but **not** `undeclaredGaps`. Admin UI list summary uses only
entry/chain violation counts → “0 entry + 0 chain violations” for a CRITICAL rupture alert.

### 3. Why a new alert every night

Incident fingerprint dedupe (`AUDIT_INTEGRITY_RUPTURE:<sha256>`) includes coverage boundaries that
shift with each night’s window → new OPEN row instead of `raiseOrTouch` on one stable key.

### 4. Scheduler race (secondary)

Checkpoint cron is `1 */5 * * * ?` (second 1). Nightly cron is `0 0 2 * * ?` (second 0). On EXP1,
batch completion (~`02:00:01.9`) preceded creation of the `01:55–02:00` checkpoint (~`02:00:02.2`).
`verifyChain` already suppresses **trailing** gaps when the last checkpoint in range is the global
latest (`lastInRangeIsLatest`), so the race alone does not explain the alert; the **leading**
sub-second exclusion does. The race remains a pitfall for any future logic that assumes “hour
boundary = sealed window already persisted.”

## Design reflection (beyond the bugfix)

Original Wave B design correctly separated **rolling attach** vs **nightly detect**, and correctly
treated undeclared gaps as integrity failures for forensic honesty. What was under-specified:

1. **Detective windows must be expressed in the same discrete time base as checkpoints** (grid
   alignment), not wall-clock `now()` with nanoseconds.
2. **Manipulation-family alerts** (`AUDIT_INTEGRITY_RUPTURE`) should not be the only operator
   signal for “coverage incomplete at the edge of a scheduled scan”; payload and UI must distinguish
   **boundary/coverage findings** from **crypto violations**.
3. **Coupled schedulers** (nightly at `:00`, chain at `:01`) need an explicit contract: either
   validate only completed windows, or schedule detect after attach has caught up.

These lessons are recorded as [ADR-0008](../../architecture-decisions.md#adr-0008-detective-integrity-windows-align-to-checkpoint-grid)
and a pitfall section in the integrity design pack.

## Recommended fix (implementation slice)

1. Round scheduled `windowEnd` down to the checkpoint window boundary before computing the 24 h
   range (reuse `AuditChainScheduler.roundDownToWindow` + configured `windowMinutes`).
2. Unit test: sub-second `now` must not produce leading undeclared gap when checkpoints are contiguous
   on the grid.
3. Payload/UI: surface undeclared-gap count (or status) in list summary when crypto counts are zero.
4. Docs: this ML, ADR-0008, design-pack pitfall note; optional CONFIGURATION note on cron margin.

## Links

- Idea: [`../ideas/I-2026-07-10-nightly-integrity-boundary-false-positives.md`](../ideas/I-2026-07-10-nightly-integrity-boundary-false-positives.md)
- Design pack: [`../../integrity-cluster-design-pack.md`](../../integrity-cluster-design-pack.md)
- ADR-0008: [`../../architecture-decisions.md#adr-0008-detective-integrity-windows-align-to-checkpoint-grid`](../../architecture-decisions.md#adr-0008-detective-integrity-windows-align-to-checkpoint-grid)
- Code: `NightlyIntegrityValidationScheduler`, `AuditChainVerificationService`,
  `RetroactiveIntegrityValidationService`, `alert-list-summary.ts`
