---
public: true
---
# Legacy documentation triage defaults to ADR extraction

## Date

2026-07-28

## Context

Two consecutive ad hoc documentation-triage sessions reviewed scattered legacy files
(`ezkey-admin-api/ADMIN_ZERO_MIGRATION_ANALYSIS.md`, `ADMIN_ZERO_OPTION_B_IMPLEMENTATION.md`,
`API_KEY_RATE_LIMIT_NOTE.md`) against the "keep / merge into canon / delete" test already used for
this methodology's documentation-rot cleanup. Both sessions surfaced a genuine architecture decision
(system tenant identified by an `is_system_tenant` flag rather than by name; API key and device
traffic rate-limited by actor identity rather than by IP) buried inside operational or historical
prose.

In both cases the agent's default move was to correct the surrounding text and add cross-links to
the nearest canonical document (a module README, or `global/rate-limit-baseline-policy.md`) rather
than to extract the decision itself into an ADR. A quantified check of the corpus showed why this
matters: `product-docs/` held **404** files at the time, of which only **28** lived under
`components/` (the layer `GOVERNANCE.md` names as canonical for component design truth, across only
3 of 8+ modules), and the two component `design-decisions.md` files that did exist held **4 ADRs**
total — none covering either decision just found. The gap was not a missing value (proportional
rigor and earned permanence are already stated in
[`../methodological-values.md`](../methodological-values.md)); it was a missing default target for
content discovered during legacy triage, as opposed to content authored during normal feature work.

## Working assumptions

- A component's ADR address (`components/<kebab-name>/design-decisions.md`) is fixed by the naming
  and skeleton already defined in [`../../components/README.md`](../../components/README.md). It
  does not need to wait for the full pack-instantiation threshold to be a valid extraction target —
  a single `design-decisions.md` file can exist ahead of the rest of the skeleton when a real
  decision needs a home.
- Operational and historical content (tuning guides, incident notes, troubleshooting logs) is not
  itself an ADR and does not need to move; it only needs a pointer to the ADR once the decision is
  extracted.
- This decision governs the **triage moment** (rediscovering an existing, already-implemented
  decision) and is a companion to normal-flow ADR creation described in
  [`../../GOVERNANCE.md`](../../GOVERNANCE.md) (recording a new decision as it is made).

## Options considered

| Option | Advantages | Disadvantages |
| --- | --- | --- |
| **A. Keep triaging case by case; cross-link when a decision surfaces (status quo)** | Low friction, no new rule | Proven unreliable across two consecutive sessions; decisions stay outside `design-decisions.md`, undiscoverable at the address `GOVERNANCE.md` promises |
| **B. Default gravity rule: extraction to the correct `design-decisions.md` (component or global) is part of any legacy-documentation triage session** | Concentrates "why" content at the address the corpus already advertises; testable via ADR count over time; matches `GOVERNANCE.md` Core Rule 1 in spirit | Adds one extra step (writing the ADR) to sessions that otherwise felt "done" after a correction |
| **C. Freeze component-pack instantiation until volume forces it; extraction stays optional** | No process change | Leaves genuinely component-scoped decisions with no reliable target; reproduces the drift already observed twice |

## Decision

Adopt **Option B**.

During any legacy-documentation triage session, once a passage is identified as recording a real
design decision (a "why X over Y" rationale, not operational or historical narrative), extraction to
the correct `design-decisions.md` is a required step before the session is considered closed for that
document — not an optional enrichment. The target is:

- `components/<pack>/design-decisions.md` when the decision is scoped to one component;
- `global/architecture-decisions.md` when it is cross-cutting (spans two or more components, as
  observed for rate-limit family policy across Auth API, Admin API, and Integration API).

The source document keeps operational content in place and gains a short pointer to the new ADR
instead of restating the rationale.

## Consequences

- [`../../GOVERNANCE.md`](../../GOVERNANCE.md) gains a Core Rule naming this default target and an
  amended "Retiring documentation" workflow step.
- [`../../components/README.md`](../../components/README.md) clarifies that a component's ADR
  address is usable before the full pack-instantiation threshold is met.
- Two ADRs were extracted retroactively in the same change set as this decision:
  `ADR-API-0005` (system tenant identification) in
  [`../../components/admin-api/design-decisions.md`](../../components/admin-api/design-decisions.md)
  and `ADR-0010` (rate limiting scoped by actor identity) in
  [`../../global/architecture-decisions.md`](../../global/architecture-decisions.md).
- Source documents (`ezkey-admin-api/README.md`, `global/rate-limit-baseline-policy.md`,
  `ezkey-admin-api/API_KEY_RATE_LIMIT_NOTE.md`) were updated with pointers to the new ADRs.

## Related follow-up (deferred, not part of this decision)

The same session also surfaced a broader question: `product-docs/global/` carries a large volume of
process artifacts (backlog ideas, tracer bullets, method logs, grill sessions, dated hygiene passes)
relative to the component design-truth layer. Whether that process volume needs its own retention or
archival policy is a separate, still-open question and is deliberately out of scope for this
decision — it is not resolved by adding an ADR-extraction reflex.

## Related documents

- [`../methodological-values.md`](../methodological-values.md) — values 1, 6, and 8 already implied
  this rule; this decision operationalizes it for the triage moment specifically.
- [`../../GOVERNANCE.md`](../../GOVERNANCE.md)
- [`../../components/README.md`](../../components/README.md)
- [`../../components/admin-api/design-decisions.md`](../../components/admin-api/design-decisions.md)
- [`../../global/architecture-decisions.md`](../../global/architecture-decisions.md)
