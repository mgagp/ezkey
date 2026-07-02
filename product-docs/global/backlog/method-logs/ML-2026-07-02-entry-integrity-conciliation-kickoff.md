# Method log — Entry integrity conciliation kickoff

## Metadata

- **ID:** `ML-2026-07-02-entry-integrity-conciliation-kickoff`
- **Date:** `2026-07-02`
- **Kind:** Analysis kickoff → TB promotion
- **TBs:**
  - [`TB-2026-07-02-entry-integrity-conciliation-and-alert-coherence.md`](../TB-2026-07-02-entry-integrity-conciliation-and-alert-coherence.md) (B2.6)
  - [`TB-2026-07-02-retroactive-integrity-validation-operability.md`](../TB-2026-07-02-retroactive-integrity-validation-operability.md) (B2.7)
- **Program:** Wave B / #269

## Signal

Post-B2.5 investigation operability, maintainer review identified an **asymmetric remediation**
gap: chain ruptures can be bridged and skipped on re-validation; per-entry HMAC violations persist
forever in verify (correct) but have **no conciliation act**, causing **re-alerts** and weak
operator narrative for explained tamper.

Same session: **detect operability** gap — `NightlyIntegrityValidationService` is schedule-shaped;
labs and operators need **retroactive validation** (detect + alert + completion audit) on demand
without waiting for cron, while GET verify remains read-only. Session analysis captured in B2.7 TB.

Maintainer decision:

- **B2.6:** entry-level conciliation registry (no re-sign), unified alert dedupe, skip re-litigation.
- **B2.7:** generalize orchestration (`RetroactiveIntegrityValidationService`), POST operator trigger,
  Verify vs Run validation UI — depends on B2.6 dedupe/skip for safe overlap with nightly.

## Outcome

- TB B2.6 and B2.7 drafts created for maintainer review.
- Design pack updated (gap rows, D5-13 settled via TB pair).

## Residual

- Sign-off on D1–D6 (B2.6) and D1–D6 (B2.7).
- Implementation: recommended order B2.6 slice 1 → B2.7 slice 1 → UI slices.
