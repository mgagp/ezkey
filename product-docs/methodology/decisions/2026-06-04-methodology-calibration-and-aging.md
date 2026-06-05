---
public: true
---
# Methodology calibration and artifact aging

## Date

2026-06-04

## Context

The Ezkey Methodology now covers the main path from product direction to implementation: vision,
backlog ideas, challenge, tracer bullets, design, test planning, implementation evidence, closeout,
retrofit, decisions, publication, and repository-native review.

At this maturity level, the most useful critique is not to add a new heavy layer. The stronger risk
is miscalibration: applying too much method to small work, leaving old artifacts in ambiguous states,
or making the method clearer to its author than to a new adopter.

## Critique items to preserve

The following six improvement candidates should remain visible for future methodology evolution:

1. **Methodology weight calibration.**
   Add simple guidance for deciding how much method to apply: small change, uncertain change, risky
   slice, or direction change. This protects proportional rigor by helping new users avoid both
   under-analysis and over-ceremony.

2. **Artifact aging rule.**
   Add a lightweight rule for inactive `I-*`, `V-*`, and `TB-*` artifacts. If an artifact sits
   without movement for long enough, it should be confirmed as still alive or moved to `parked`,
   `archived`, or `dropped`.

3. **Short end-to-end example.**
   Provide a compact example that shows the expected depth from idea to challenge, tracer bullet,
   test plan, and closeout. Templates explain what to fill in; an example shows what "enough" looks
   like.

4. **Lightweight role clarification.**
   Define minimal roles such as operator, contributor, reviewer, and agent. Avoid a full RACI model,
   but make group usage easier to understand.

5. **Explicit simplification rule.**
   Make it easy to retire, downgrade, or remove documents, skills, matrices, or template sections
   that do not earn their maintenance cost after real use.

6. **Delivery and release readiness bridge.**
   Clarify closeout language around whether work is merely merged, actually delivered, activable, or
   documented for the intended user.

## Decision

Implement the first two items now:

- add a calibration table to `minimum-viable-method.md`;
- add a lightweight artifact aging rule to `minimum-viable-method.md`.

Do not implement the other four items immediately. They remain valuable candidates, but they should
be introduced only when experience shows the added guidance will reduce real confusion or drift.

## Consequences

- The minimum method becomes more useful to new adopters because it tells them how much process to
  apply.
- Long-lived uncertain artifacts get a simple revalidation path without creating a new review
  ceremony.
- The critique backlog is preserved without becoming an immediate expansion mandate.

## Validation signal

This change helped if future sessions choose a smaller artifact set more quickly, and if old
`captured`, `draft`, `incubating`, or `under-review` items are easier to classify as still relevant,
parked, archived, or dropped.

## Related documents

- [`../minimum-viable-method.md`](../minimum-viable-method.md)
- [`../methodological-values.md`](../methodological-values.md)
- [`../artifact-identity-and-review.md`](../artifact-identity-and-review.md)
