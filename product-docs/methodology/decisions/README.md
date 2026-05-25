# Methodology Decisions

This folder captures decisions about the Ezkey methodology itself — the "why" behind choices in
workflow conventions, naming schemes, artifact formats, and collaboration patterns.

## Purpose

These are not product architecture decisions (those live in
[`../../global/architecture-decisions.md`](../../global/architecture-decisions.md) and component
`design-decisions.md` files) and not product vision notes (those live in
`product-docs/global/vision/`). They are decisions about the **process and tooling** that govern
how the team and AI agents work together.

Methodology decisions serve as institutional memory for the practice itself. Without them, the
rationale for non-obvious rules gets lost between sessions and the same questions get re-debated
from scratch.

## File naming

`YYYY-MM-DD-<slug>.md` — consistent with all other date+slug artifacts in this methodology.
No counter, no global index needed. One file per decision.

## When to create a methodology decision

- A non-obvious convention is chosen over obvious alternatives.
- A working assumption is explicitly adopted to avoid intractable complexity.
- A workflow rule is changed and the rationale should survive the conversation that produced it.
- A pattern is promoted from an ad-hoc practice to a canonical rule.
- The team resolves a recurring debate and wants to close it for future sessions.

## What a methodology decision is NOT

- A task or backlog item (use `I-*`).
- A product vision note (use `V-*`).
- A session summary or plan incubation artifact (use blitz archive or `plans/`).
- An architecture or product design decision (use
  [`../../global/architecture-decisions.md`](../../global/architecture-decisions.md) or a
  component `design-decisions.md`).

## Structure of a decision file

```markdown
# [Short decision title]

## Date
YYYY-MM-DD

## Context
What situation prompted this decision. What problem was being solved.

## Working assumptions
Explicit pragmatic assumptions adopted to avoid over-engineering.
These are the load-bearing hypotheses — if they break, the decision should be revisited.

## Options considered
| Option | Advantages | Disadvantages |
|---|---|---|

## Decision
What was chosen and why, in plain language.

## Consequences
What changes in practice — files updated, rules modified, skills adjusted.

## Related documents
Links to impacted methodology files, skills, or canonical docs.
```

## Multi-branch note

Methodology decisions are written on whatever branch or worktree is active at the time.
No index file to update. Date+slug ensures no collision across branches.
