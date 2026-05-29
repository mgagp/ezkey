# Architecture Decision — `<ADR-id>` `<short title>`

> Template for a single architecture or design decision. Used in `global/architecture-decisions.md` and component-level `design-decisions.md`. Remove this blockquote on instantiation.

## Metadata

- **ID:** `<ADR-id>` (stable identifier, e.g. `ADR-0007`).
- **Date:** `YYYY-MM-DD`.
- **Status:** `proposed` / `accepted` / `superseded-by-<ADR-id>` / `deprecated`.
- **Scope:** `global` / `component:<component-name>`.
- **Owners:** `<role or person>`.

## Context

Describe the situation that requires a decision. Explain the forces at play: constraints, trade-offs, prior decisions, product requirements. One to three short paragraphs.

## Decision

State the decision in one short paragraph. Use clear, active language.

## Alternatives Considered

For each alternative, a short paragraph or a bullet with trade-offs.

- **Alternative A.** Description. Why it was rejected.
- **Alternative B.** Description. Why it was rejected.

## Consequences

- **Positive.** What this decision enables.
- **Negative.** What becomes harder.
- **Neutral.** Side effects worth recording.

## Impact

- **Affected components.** Link to the relevant component packs.
- **Affected features.** Link to entries in the product feature catalog.
- **Affected boundaries.** Link to entries in the relevant mapping matrices.

## Validation

- How the decision will be validated in practice (tests, metrics, exploratory review).
- Link to the relevant traceability entries in the appropriate spec-test traceability matrix.

## Related Decisions

- Prior ADRs that this decision refines or supersedes.
- Related ADRs in sibling components.
