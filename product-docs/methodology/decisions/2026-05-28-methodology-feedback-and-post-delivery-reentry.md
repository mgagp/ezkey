---
public: false
---
# Methodology feedback lane and post-delivery re-entry

## Date

2026-05-28

## Context

The methodology already defined three strong lanes:

- Lane A for ideation and backlog capture,
- Lane B for scoped delivery,
- Lane C for legacy knowledge retrofit.

Two practical realities were still under-specified.

The first is **methodology feedback itself**. Real work sessions regularly expose friction,
ambiguity, over-materialization risk, or missing state boundaries in the method. The repository
already had `product-docs/methodology/decisions/`, but the workflow did not yet name this as an
explicit lane.

The second is **post-delivery change inception**. After a feature has been delivered, most real
project time is spent on enhancements, rule adjustments, operator feedback, and the occasional
apparent bug. The method described ideation, delivery, and retrofit well, but it did not yet say
how later changes should re-enter the corpus without either over-escalating routine fixes or
losing traceability when the real problem is missing intent.

## Historical interpretation note (optional)

This record preserves the lane taxonomy and wording that existed at the moment the two new lanes
were first introduced. The current canonical lettering and lane presentation were adjusted later
in [2026-05-28-lane-d-e-ordering.md](2026-05-28-lane-d-e-ordering.md) and in the current
methodology overview documents.

## Source signal (optional)

- "Il faut un couloir D pour définir la boucle de rétroaction."
- "Le couloir qui manque, c'est ce couloir-là... le processus d'évolution et de correction de bug."
- "Si c'est un pur bug technique, alors on règle ça live."
- "Si... ça relève d'un manque de précision au niveau du grillage, du tracer bullet, ou encore de
  l'idée... ou carrément de la vision incorrecte", il faut repartir de l'endroit méthodologique
  le plus haut approprié.

## Working assumptions

- The method should stay **orthogonal**: prefer entry rules and artifact reuse over new artifact
  families.
- Not every observed bug deserves methodological elevation.
- A pure local technical defect should remain a lightweight engineering task.
- When the defect is actually missing or incorrect intent, the corpus must be updated at the
  highest level whose truth changed.
- `V-*` and `I-*` are allowed to remain living canonical artifacts when they still represent the
  right current truth.
- Closed `TB-*` artifacts should remain bounded execution records rather than reopened indefinitely.

If repeated use later shows that a dedicated doc or skill is necessary, that should be added only
after the manual pattern proves its value.

## Options considered

| Option | Advantages | Disadvantages |
| --- | --- | --- |
| **A. Keep A/B/C only** and handle the rest informally | Zero documentation churn | Leaves two recurring realities implicit; weak resumability; encourages silent drift |
| **B. Add dedicated new artifact families** for methodology feedback and bug/evolution work | Highly explicit | Breaks proportional rigor; adds ceremony and taxonomy weight |
| **C. Add two lightweight lanes** that reuse existing artifacts and decisions | Makes entry rules explicit without corpus sprawl; preserves orthogonality | Requires judgment to classify the right re-entry level |

## Decision

Adopt **Option C**.

### 1. Make methodology feedback an explicit lane

The workflow now recognizes a **methodology feedback and evolution** lane for sessions where the
subject is the method itself. The canonical record for that lane is a methodology decision under
`product-docs/methodology/decisions/`, optionally preserving short verbatim source signal when the
exact wording carries the rationale.

This lane is the method's reflective loop. It is where the methodology learns from its own real
usage.

### 2. Make post-delivery change inception explicit

The workflow now recognizes a **post-delivery evolution and corrective re-entry** lane.

It starts from an observation on existing implemented behavior and classifies the work into two
families:

- **Pure local technical defect**: fix directly, validate, and close.
- **Intent or scope change**: re-enter the canonical flow at the highest level whose truth changed.

Re-entry rule:

- re-enter at `TB-*` when only the execution slice changes,
- re-enter at `I-*` when scope, rules, or acceptance behavior changes,
- re-enter at `V-*` when product direction or governing intent changes.

### 3. Reuse existing lineage rules instead of inventing new ones

For post-delivery corpus updates:

- amend an existing `V-*` or `I-*` when it is still the right canonical home;
- use `Superseded` / `Supersedes` when the change materially reformulates the artifact;
- create a **new `TB-*`** for each new bounded implementation slice instead of reopening a closed
  tracer bullet.

### 4. Do not create a dedicated skill yet

No new skill is mandatory at this stage. The manual pattern is small enough to validate first.
Only repeated drift should justify a specialized skill or a standalone workflow document.

## Consequences

Files updated in this change:

- `product-docs/methodology/workflow-overview.md` — adds the methodology-feedback lane, the
  post-delivery re-entry lane, and the canonical update rule.
- `product-docs/methodology/session-start-guide.md` — adds session entry guidance and prompt
  starters for both lanes.
- `product-docs/methodology/README.md` — adds quick-start prompts for both lanes.
- `product-docs/methodology/decisions/README.md` — clarifies how short verbatim source signal may
  live inside a methodology decision.

Practical effect:

- The methodology can now evolve itself explicitly.
- Existing-code changes now have a documented entry posture.
- Routine debugging stays lightweight.
- Requirement and direction corrections regain full traceability without inventing new artifact
  types.

## Related documents

- [`../workflow-overview.md`](../workflow-overview.md)
- [`../session-start-guide.md`](../session-start-guide.md)
- [`../README.md`](../README.md)
- [`README.md`](README.md)
- [`../methodological-values.md`](../methodological-values.md)
- [`../nomenclature.md`](../nomenclature.md)
