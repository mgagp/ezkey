# Method log — Wave B B2.5 investigation operability kickoff

## Metadata

- **Date:** `2026-06-30`
- **Program:** Wave B — integrity cluster R1 (September 2026 release target)
- **GitHub:** #269 (program umbrella)
- **Predecessor:** B2 manipulation remediation (reconcile API + Admin UI) on branch
  `feature/269-i-2026-0005-manipulation-integrity-remediation`
- **Parent idea:** `I-2026-0005-checkpoint-integrity-break-remediation`
- **Related TB:** `TB-2026-06-30-integrity-investigation-operability` (B2.5)

## Signals (operator lab, clean-start)

Maintainer induced audit row tampering; nightly batch raised `AUDIT_INTEGRITY_RUPTURE`.
Dashboard and alert detail behaved as designed (boundaries, payload, reconcile dialog).

**Gaps observed (investigation / forensic operability):**

1. No way to identify **which** `auditLogId`(s) failed entry HMAC — alert shows counts only.
2. Admin UI **Entry Integrity** report shows `invalidEntries: 1` without listing violations.
3. Audit list **HMAC column** shows green shield when `entryHmac` is present (signed ≠ verified).
4. Audit detail labels signed entries **"Chain intact"** without live verification.
5. **Gap highlight** pattern exists in Integrity panel (`focusGap` on undeclared gaps); no equivalent
   for entry HMAC violations; `focusCheckpointId` deep link from gap alert is not consumed in
   `audit-logs.tsx`.
6. Backend computes violation strings with `auditLogId=` during nightly/ad hoc checks but discards
   detail before persisting operator-facing artifacts (alert payload, `IntegrityReport` DTO).

## Conclusion (scope split)

| Lane | Status after B2 |
|------|-----------------|
| **Resolution** (alert → justify → crypto bridge → close) | Substantially delivered |
| **Investigation** (identify affected records → navigate → honest UI) | Gap — drives **B2.5** |

B2.5 is a bounded follow-on to `I-2026-0005`, not a new vision item. It completes the operator
story for cryptographic audit validation without expanding to B3 widgets or snooze (C9).

## Design decisions (sequential grill)

Decisions are captured in TB `TB-2026-06-30-integrity-investigation-operability` and resolved
one per maintainer session:

| # | Topic | Status |
|---|--------|--------|
| 1 | **Persistence** — where violation detail lives | **Decided** — E; alert snapshot + API + capped event; UI auto re-verify on alert open; structured chain + entry lists |
| 2 | **Cap** — how many IDs in alert / payloads | **Decided** — alert 25/10; API no cap; nightly event 10/5 (A) |
| 3 | **Badge semantics** — signed vs verified vs known-bad | **Decided** — neutral signed; row badges + pagination banner; detail live check; cache until logout |
| 4 | **Unified investigation UX** — alert → audit logs parity with gap flow | **Decided** — Investigate CTA; Show only affected entries; gap `focusCheckpointId` in scope; dual alert sections |

## Next

1. Merge B2 reconcile slice to `main` when ready (PR Part of #269).
2. Implement B2.5 per TB `TB-2026-06-30-integrity-investigation-operability` (status `ready`).
3. Then promote B3 (`I-2026-0007`) at Wave B closeout.

## Grill complete

All design decisions D1–D4 resolved **2026-06-30**. TB promoted to **`ready`**.
