---
public: false
---
# Lane D/E ordering

## Date

2026-05-28

## Context

The methodology had already been extended with two late-stage lanes:

- one for post-delivery evolution and corrective re-entry,
- one for methodology feedback and evolution.

The first cut assigned:

- Lane D = methodology feedback,
- Lane E = post-delivery re-entry.

That worked mechanically, but it weakened the visual logic of the lane sequence. Lanes A through D
should read as the product and delivery lifecycle that the methodology governs. The final lane
should sit outside that lifecycle as the reflective loop on the methodology itself.

## Working assumptions

- Lane lettering carries conceptual meaning, not only ordinal meaning.
- The visual order should help a reader infer the boundary between product work and meta-method work.
- The software development lifecycle covered by the method is clearer when post-delivery change
  still belongs to the project-facing lanes.
- Methodology feedback remains important, but it is the outer reflective layer rather than the last
  project-facing lane.

## Options considered

| Option | Advantages | Disadvantages |
| --- | --- | --- |
| Keep D = methodology feedback, E = post-delivery re-entry | No churn after the previous change | Weakens the visual story; puts the meta lane before the final project-facing lane |
| Swap them: D = post-delivery re-entry, E = methodology feedback | Restores lifecycle readability; keeps the reflective loop as the final lane | Requires small harmonization edits across docs and published views |

## Decision

Adopt the swap.

- **Lane D** = post-delivery evolution and corrective re-entry.
- **Lane E** = methodology feedback and evolution.

This keeps the visible lane sequence coherent:

- Lane A = end-to-end product workflow,
- Lane B = plan incubation,
- Lane C = legacy retrofit,
- Lane D = post-delivery project evolution,
- Lane E = reflective improvement of the methodology itself.

## Consequences

- Session-start and quick-start docs must use the new lettering.
- Skills overview must reflect the new ordering.
- Rich-view and published marketing pages must swap the lane labels and the collaboration-context
  applicability note (`A, B, C, D`; Lane E usually single-branch).
- The earlier decision about adding the two lanes remains valid in substance; this record adjusts
  only their lettering and presentation order.

## Related documents

- [`2026-05-28-methodology-feedback-and-post-delivery-reentry.md`](2026-05-28-methodology-feedback-and-post-delivery-reentry.md)
- [`../session-start-guide.md`](../session-start-guide.md)
- [`../workflow-overview.md`](../workflow-overview.md)
- [`../README.md`](../README.md)
- [`../view/index.html`](../view/index.html)
