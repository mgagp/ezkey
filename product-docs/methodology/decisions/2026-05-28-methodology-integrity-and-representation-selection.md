---
public: true
---
# Methodology integrity, representation selection, and simplification-first refinement

## Date

2026-05-28

## Context

An evaluation pass on the Ezkey methodology found that the main risk was no longer conceptual
direction but **internal integrity drift** between canonical methodology rules and the templates
that instantiate them. Several templates still reflected older identifier formats, older tracer
bullet statuses, older lane enumerations, or older product-level terminology.

The same evaluation found that the methodology already used strong practical software analysis
techniques, but did not yet express clearly enough **how to choose the right representation** for a
given problem. Sequence diagrams, state machines, decision tables, mapping matrices, lifecycle
notes, wireflows, and error models were present in the corpus, but the selection rule remained too
implicit.

The goal of this refinement was to improve coherence, comprehensibility, and methodological
integrity without pushing the methodology toward a heavier framework identity.

## Source signal (optional)

- "Il faut aligner ça."
- "guide de choix de représentations. Il faut l'implémenter."
- "minimum de strict minimum de complexité"

## Working assumptions

- The methodology remains a project-backed practice, not a separately maintained framework.
- The highest-value improvement is to reduce silent drift between canon and daily-use templates.
- Better expression and selection guidance is preferable to adding new artifact families.
- The right first pass is a small, reversible refinement that improves clarity immediately.

## Options considered

| Option | Advantages | Disadvantages |
| --- | --- | --- |
| Fix template drift only | Fastest integrity repair | Leaves representation choice too implicit; misses a real usability gain |
| Add new templates for every representation gap | Makes more forms explicit | Raises ceremony and maintenance cost too quickly |
| Fix template drift, add a representation chooser, and simplify entry wording | Restores internal coherence and improves usability with low extra weight | Requires a coordinated documentation pass |

## Decision

Adopt the third option.

The methodology will be refined through a **simplification-first** pass with three priorities:

1. align daily-use templates with the current canonical rules;
2. add an explicit representation chooser to the analysis and design canon;
3. simplify methodological entry wording where the same reality was being described twice.

The method should become easier to apply by making existing truth clearer, not by adding a new
ceremony layer.

## Consequences

Files updated in this pass:

- `product-docs/templates/backlog-idea.template.md`
- `product-docs/templates/tracer-bullet-brief.template.md`
- `product-docs/templates/vision-note.template.md`
- `product-docs/templates/legacy-plan-retrofit.template.md`
- `product-docs/templates/feature-brief.template.md`
- `product-docs/templates/roadmap.template.md`
- `product-docs/templates/spec-test-traceability.template.md`
- `product-docs/methodology/analysis-and-design-canon.md`
- `product-docs/methodology/session-start-guide.md`
- `product-docs/methodology/decisions/README.md`

Ordered follow-up simplification plan:

1. Add short filled examples to the highest-frequency templates only.
2. Review remaining template language for product-level `milestone` consistency.
3. Keep expanding representation guidance by example before adding any new template.
4. Re-evaluate whether any future rich view is warranted only after repeated evidence of textual
   friction.

## Related documents

- [`../analysis-and-design-canon.md`](../analysis-and-design-canon.md)
- [`../session-start-guide.md`](../session-start-guide.md)
- [`../nomenclature.md`](../nomenclature.md)
- [`../workflow-overview.md`](../workflow-overview.md)
- [`../../templates/tracer-bullet-brief.template.md`](../../templates/tracer-bullet-brief.template.md)
