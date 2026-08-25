# Backlog Idea — `I-2026-07-10-nightly-integrity-boundary-false-positives` Nightly integrity: boundary false positives

## Metadata

- **ID:** `I-2026-07-10-nightly-integrity-boundary-false-positives`
- **Status:** `done`
- **Priority:** `P1`
- **Created at:** `2026-07-10`
- **Updated at:** `2026-08-02`
- **Last reviewed at:** `2026-08-02`
- **Closed at:** `2026-07-11`
- **Progression markers:** `P3-operable-release`
- **Component tags:** `core`, `admin-api`, `admin-ui`, `audit`, `docs`
- **Lane:** `D`
- **Captured by:** maintainer + agent (EXP1 investigation 2026-07-09)
- **GitHub issue:** `#315` (closed)
- **Issue labels:** `lane:d`, `type:fix`, `component:core`, `component:admin-api`, `component:admin-ui`, `priority:p1`, `status:ready`
- **GitHub PR:** `#318` (merged 2026-07-11)

## Intent

Stop the nightly retroactive integrity batch from raising CRITICAL `AUDIT_INTEGRITY_RUPTURE`
alerts when the chain is cryptographically intact and the only findings are **boundary artifacts**
(sub-second window misalignment and/or checkpoint-scheduler race at the window edge). Keep real
manipulation and undeclared interior gaps alert-eligible. Align detective windows to the checkpoint
grid and make operator-facing summaries honest about undeclared gaps vs crypto violations.

## Problem and value

- **Problem:** On EXP1, the nightly job at 02:00 UTC raised one OPEN `AUDIT_INTEGRITY_RUPTURE` per
  day for six consecutive days. Payloads showed `chainStatus=UNDECLARED_GAP_DETECTED` with
  **zero** entry HMAC and **zero** chain crypto violations. The Admin UI summarized
  “0 entry + 0 chain violations,” which looked like a false CRITICAL. Root cause: detective window
  ends at `OffsetDateTime.now()` (sub-second precision) while checkpoints sit on exact 5-minute
  boundaries, so the first aligned checkpoint is excluded and a leading “undeclared gap” is
  reported; fingerprint dedupe then creates a **new** alert each night.
- **Expected value:** Healthy instances stay quiet. Operators trust CRITICAL integrity alerts again.
  Future integrity work inherits an explicit design rule (window alignment + alert taxonomy honesty).

## Scope

- **In scope:**
  - Align scheduled (and preferably operator) validation window ends to the checkpoint grid
    (`AuditChainScheduler.roundDownToWindow`).
  - Ensure boundary-only undeclared gaps do not raise `AUDIT_INTEGRITY_RUPTURE` when crypto is intact
    (or are not produced after alignment — prefer prevention over special-casing).
  - Unit coverage for the race/alignment case.
  - Operator honesty: include undeclared-gap signal in alert payload and/or list summary when status
    is gap-only (so “0 + 0” cannot mask `UNDECLARED_GAP_DETECTED`).
  - Corpus: method log, ADR, integrity design-pack pitfall note.
  - Optional config note: prefer nightly cron a few minutes after the hour if operators want extra
    margin vs the `1 */5` checkpoint tick.
- **Out of scope:**
  - Re-litigating C8-6 heartbeat deferral.
  - Snooze UI.
  - Changing gap-declaration or manipulation-conciliation workflows.
  - Auto-resolving historical EXP1 false-positive alerts (operator may resolve manually).

## Key assumptions

- Investigation on EXP1 (2026-07-09) is authoritative; see method log.
- `lastInRangeIsLatest` already suppresses trailing gaps when the global latest checkpoint is in
  range; the dominant false positive is the **leading** sub-second exclusion.
- Aligning `windowEnd` to the grid is the primary fix; payload/UI honesty is the secondary fix.

## Risks and exceptions

- Over-suppressing real leading gaps inside the extent (must not weaken interior gap detection).
- Operator POST with arbitrary bounds should remain honest for forensic ranges; scheduled path must
  be safe by default.
- Changing fingerprint inputs may leave old OPEN false positives untouched (acceptable).

## Promotion notes

Challenge complete via EXP1 DB + code analysis (2026-07-09). Implemented without a separate Grill Me
or `TB-*` (bounded Lane D single-pass on `I-*`).

## Closeout (2026-07-11 / canon synced 2026-08-02)

- **Delivered:** detective `windowEnd` aligned to checkpoint grid; boundary-only undeclared gaps no
  longer raise `AUDIT_INTEGRITY_RUPTURE`; Admin UI list summary honest for gap-only status;
  ADR-0009 + design-pack pitfall note.
- **Evidence:** unit coverage on scheduler/verification; PR `#318` merged; issue `#315` closed.
- **Residual:** historical EXP1 OPEN false-positive alerts remain for manual resolve (by design).
- **Next:** none on this idea. Nightly quietness on EXP1 is operator watch, not remaining debt.

## Links

- Method log: [`../method-logs/ML-2026-07-09-exp1-nightly-integrity-boundary-false-positives.md`](../method-logs/ML-2026-07-09-exp1-nightly-integrity-boundary-false-positives.md)
- Design pack: [`../../integrity-cluster-design-pack.md`](../../integrity-cluster-design-pack.md)
- ADR: [`../../architecture-decisions.md#adr-0009-detective-integrity-windows-align-to-checkpoint-grid`](../../architecture-decisions.md#adr-0009-detective-integrity-windows-align-to-checkpoint-grid)
- Parent shipped work: `I-2026-0006` / `TB-2026-06-28-nightly-integrity-validation-batch`
- Vision: [`../../vision/V-2026-0004-integrity-validation-strategy.md`](../../vision/V-2026-0004-integrity-validation-strategy.md)
- GitHub branch: `feature/315-i-2026-07-10-nightly-integrity-boundary-false-positives`
- GitHub PR: `#318`
