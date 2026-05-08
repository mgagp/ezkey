# Backlog Idea — `I-2026-0005` Checkpoint integrity breaks: declared remediation and reattachment

## Metadata

- **ID:** `I-2026-0005`
- **Status:** `captured`
- **Priority:** `P1`
- **Created at:** `2026-05-08`
- **Updated at:** `2026-05-08`
- **Last reviewed at:** `2026-05-08`
- **Phase tags:** `P2-hardening`
- **Component tags:** `admin-api`, `audit`

## Intent

Extend the existing checkpoint and heartbeat operational mechanism so that **detected** audit-sequence integrity breaks (for example, a deleted audit row that breaks cryptographic chaining) trigger a structured operator-facing remediation flow comparable to the existing gap-declaration flow used for full outages. Detection itself already works; what is missing is the alerting, declaration, rectification, and reattachment workflow.

## Problem and value

- **Problem:** Today, a full outage gap is handled with an explicit gap declaration that justifies the quiet period. A partial degradation (for example, `admin-api` down) trips Integration API and Auth API into degraded mode and emits an alert. But when an audit row is deleted manually, integrity validation correctly **detects** the break, yet there is no symmetric mechanism to (a) raise an alert, (b) declare and explain the break, (c) rectify, and (d) reattach the broken sequence so that the dashboard reflects an explained-and-recovered state rather than an indefinite "broken" state.
- **Expected value:** Operational continuity in the face of detected integrity tampering or accidental data loss; explicit, auditable record of the incident and the remediation; consistency with the gap-declaration model already in place for outages.

## Scope

- **In scope:**
  - Define the conceptual model for a "detected integrity break" incident (lifecycle, fields, persistence).
  - Extend the existing checkpoints and incidents table (or introduce a sibling) to record the break, the declaration, the reattachment, and the resulting cryptographic continuation.
  - Trigger an alert (consistent with the existing degradation-alert table) on detection.
  - Provide the operator path to declare, rectify, and reattach.
  - Ensure the dashboard reflects the resolved state once reattachment is recorded.
- **Out of scope:**
  - Proactive deeper retroactive integrity checks beyond the current rolling window — covered by `V-2026-0004` and `I-2026-0006`.
  - Dashboard widget design for the resolved state — covered by `I-2026-0007`.
  - Backfill of historical undetected breaks (separate evaluation).

## Key assumptions

- The current detection logic for sequence breaks is correct and reusable as the trigger.
- Symmetry with the gap-declaration flow keeps the operator mental model coherent.
- Reattachment does not weaken cryptographic guarantees; it documents and bridges, it does not silence.

## Risks and exceptions

- Reattachment semantics must be conservative: the declared explanation is part of the audit story, not a way to rewrite history. Strong guards required.
- Alert noise: integrity-break detection should be rare; tuning must avoid floods if a misconfiguration triggers many false positives.

## Promotion notes

Move to `incubating` once the conceptual model (incident lifecycle, schema impact, declaration UX) is sketched. Promote to `TB-*` once the design is consistent with `V-2026-0004` and the dashboard story (`I-2026-0007`).

## Links

- Related vision: `V-2026-0004` (integrity validation strategy)
- Related feature: `F-audit-chain`
- Related principles: `#11` (lifecycle without surprise), `#12` (security as posture)
