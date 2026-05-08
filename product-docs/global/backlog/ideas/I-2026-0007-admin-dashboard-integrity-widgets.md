# Backlog Idea — `I-2026-0007` Admin Dashboard: security and integrity health widgets

## Metadata

- **ID:** `I-2026-0007`
- **Status:** `captured`
- **Priority:** `P2`
- **Created at:** `2026-05-08`
- **Updated at:** `2026-05-08`
- **Last reviewed at:** `2026-05-08`
- **Phase tags:** `P1-operability`, `P2-hardening`
- **Component tags:** `admin-ui`, `admin-api`

## Intent

Add one or two Dashboard widgets that summarize the most recent integrity validation outcomes (rolling window plus retroactive batch), with badges consistent with the existing health-status widget pattern, and a brief explanation of the validation scope so the operator understands "what is OK" and "for which window".

## Problem and value

- **Problem:** Integrity validation is foundational to the security posture (`Design Principle #12`) but is currently invisible on the operator's primary surface. Existing widgets cover entities (integrations, enrolments, authentications) but not the integrity health of the system itself. Without surfacing, the operator cannot tell whether nightly validation ran successfully, what window it covered, or whether a break is currently outstanding.
- **Expected value:** Operator confidence; explicit, glance-able security-posture indicator; alignment with `Design Principle #5` (operator-first) and `#13` (open-source transparency); makes the work in `I-2026-0005` and `I-2026-0006` visible and actionable.

## Scope

- **In scope:**
  - One or two widgets on the existing Admin Dashboard, using the existing health-badge pattern.
  - Display the most recent integrity validation status (from the single-purpose status row introduced in `I-2026-0006`).
  - Display the validation scope summary (window covered, last run timestamp, last result).
  - Honest copy: this is operational reporting, not an integrity proof in itself; if the operator suspects tampering of the report itself, they can re-run the on-demand integrity check.
  - Admin API endpoint(s) feeding the widget data.
- **Out of scope:**
  - Generic background-process dashboard framework (single-purpose first, per `Design Principle #2`).
  - Strong integrity guarantees on the report row itself — it is a derived convenience, not a primary record. The cost of over-engineering here would not pay back.
  - Historical timeline view of past validation runs (V1 shows the most recent run only).

## Key assumptions

- Operators need a glance-level signal more than a deep history. A single most-recent-status row is sufficient for V1.
- The existing widget and badge pattern is reusable without redesign.
- The status row from `I-2026-0006` is sufficient as the data source; no parallel pipeline needed.

## Risks and exceptions

- Copy must avoid overstating assurance. The widget reports what was run and what it found, not "the system is provably secure".
- A long-running break or persistent missing status (for example, the nightly batch has not run for several days) must be visually distinct from "all green".

## Promotion notes

Move to `incubating` once `I-2026-0005` and `I-2026-0006` have stable schemas. Promote to `TB-*` together with `I-2026-0006` once the data contract is firm.

## Links

- Related vision: `V-2026-0004` (integrity validation strategy)
- Related backlog: `I-2026-0005`, `I-2026-0006`
- Related features: `F-admin-ui-workflows`, `F-audit-chain`
- Related principles: `#5` (operator-first), `#10` (admin UI sobriety), `#13` (transparency)
