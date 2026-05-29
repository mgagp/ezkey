# Testing Strategy in Workflow

## Purpose

This document makes test strategy an explicit part of the standard ideation-to-delivery workflow.

It follows the methodology's pragmatic test philosophy:

- choose the cheapest meaningful gate first,
- escalate only when justified by risk,
- preserve test quality without adding process weight.

## Test layers

1. **Unit tests**
   - Fast, cheap, first-line regression protection.
   - Preferred default after any localized logic change.
2. **Functional tests**
   - End-to-end validation on the real Docker stack.
   - Reference validation for critical feature behavior.
3. **Elective tests**
   - On-demand specialized checks for stable but critical domains.
   - Not automatic by default.
4. **Operational tests**
   - Long-running churn and time-compression scenarios.
   - Used to expose concurrency and lifecycle issues over volume.
5. **UI tests (Playwright)**
   - Risk-based usage for high-value UI workflows.
   - Keep scope targeted; avoid broad fragile suites.

## Decision heuristic

For each change, ask:

- which tests are **necessary**,
- which are **relevant** to changed risk,
- which are **cheap and fast** enough for routine gates,
- which provide the best **confidence per execution cost**.

## Workflow insertion points

- **Ideation**: identify risk profile and likely test layers.
- **Analysis**: define candidate tests for nominal and critical exception paths.
- **Design**: commit to a minimum test set and optional higher layers.
- **Execution**: run selected tests and capture evidence.
- **Closeout**: record what was executed, deferred, and why.

## UI testability posture

When UI changes are involved, include low-cost testability improvements when relevant:

- stable selectors,
- predictable states,
- explicit loading and error states,
- deterministic interaction boundaries.

This keeps future Playwright and agentic UI validation affordable.
