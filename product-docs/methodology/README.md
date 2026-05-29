# Methodology Pack

This folder defines a lightweight operating method used to move from product ideation to implementation.

It is designed for mixed collaboration:

- human to human,
- human to AI,
- AI to AI (through bounded context documents and specialized skills).

## Why this exists

When a documentation system already has strong product and component documentation, this pack adds
the missing connective tissue:

- how ideas are captured,
- how they are challenged and refined,
- how they are promoted into delivery work,
- how quality and traceability are enforced with minimal ceremony.

## Reading order

`README.md` stays first in the explorer navigation. After that, keep the pack ordered by the
reader journey rather than alphabetically.

If you only need the fastest reliable entry, start with [`minimum-viable-method.md`](minimum-viable-method.md).

### Entry and orientation

1. `minimum-viable-method.md`
2. `workflow-overview.md`
3. `session-start-guide.md`

### Core working method

1. `analysis-and-design-canon.md`
2. `tracer-bullet-method.md`
3. `testing-strategy-in-workflow.md`
4. `quality-gates.md`

### Specialized lanes and workflow variants

1. `plan-incubation-workflow.md`
2. `legacy-retrofit-workflow.md`
3. `blitz-intake-pattern.md`
4. `github-issues-workflow.md`
5. `multi-branch-workflow.md`

### Reference context

1. `methodological-values.md`
2. `ai-collaboration-model.md`
3. `nomenclature.md`

### Example and institutional memory

1. `case-study-ezkey.md` — concrete instantiation inside this repository's source project.
2. `decisions/` — rationale and historical record for non-obvious methodology choices.

## Scope boundaries

- Product direction and intent remain in `../global/`.
- Component implementation details remain in `../components/`.
- This pack defines process and collaboration mechanics.
- Rationale for non-obvious methodology choices lives in `decisions/`.
- Project-specific taxonomies, examples, and terminology belong in `case-study-ezkey.md` when they
  clarify the method without making the generic docs carry project-local coupling.

## Publication boundary

When this methodology is published as a standalone product, publish the **method** and its
teaching surfaces, not the source project's active delivery corpus.

- Publishable by default: `methodology/`, `templates/`, derived public `skills/`, `glossary.md`,
  rich views, and methodology decisions explicitly marked `public: true`.
- Not publishable by default: instantiated Ezkey delivery artifacts under `product-docs/global/`,
  component implementation packs under `product-docs/components/`, backlog / roadmap / vision
  execution records, and editor-local `.cursor/` assets.
- If a project artifact contains a reusable methodological lesson, promote or restate that lesson in
  a method-level document or decision instead of publishing the project artifact itself.
- Public methodology docs may reference the source project as a case study, but they must remain
  navigable and understandable without access to Ezkey-only working documents.

## Core principle

Use the lightest process that still preserves:

- analytical rigor,
- design consistency,
- end-to-end traceability,
- clear handoff quality between humans and AI agents.

## Fast routing

Use this quick routing before reading deeper:

- New idea or normal product work: start with **Lane A**.
- Freeform exploration first, canonical docs later: start with **Lane B**.
- Historical plans or verbal history to mine: start with **Lane C**.
- Existing implemented behavior that now needs change: start with **Lane D**.
- Method improvement itself: start with **Lane E**.

Then use [`session-start-guide.md`](session-start-guide.md) for the detailed prompts and skill
sequence.

If the full method still feels too large for the current topic, use
[`minimum-viable-method.md`](minimum-viable-method.md) as the lightweight front door.

Apply the source project's product design principles when judging scope. In Ezkey, that companion
canon lives in `product-docs/global/design-principles.md` — especially **#1**, **#2**, and **#14
(beautiful problems)**: defer scale/performance sophistication until evidence shows the problem is
real and earned by adoption, not hypothetical.

Apply [`methodological-values.md`](methodological-values.md) when judging the workflow itself:
whether a gate is proportional, whether an artifact deserves to be living, whether a skill should
exist, and whether a closeout is honest enough to resume later.

## Source-project hooks

This README stays method-first. When you are applying the methodology inside this repository's
source project, the most relevant project-local companions live under `../global/` and
`../components/`.

For example, Admin UI role visibility and **deployment operator geometries** are documented in
`product-docs/global/operator-alignment-guide.md`, with related cross-cutting artifacts in
`product-docs/global/admin-ui-paginated-screens-matrix.md`,
`product-docs/global/rate-limit-baseline-policy.md`,
`product-docs/global/ezkey-system-identity-sensitivity-report.md`, and
`product-docs/global/sql-business-limits-policy.md`. (Former
`product-docs/global/api-controllers-registry.md` downscoped — see
[`decisions/2026-05-24-controllers-registry-downscope.md`](decisions/2026-05-24-controllers-registry-downscope.md).)

## Rich view

For a visual companion to this methodology pack — workflow diagram, artifact types, parallel lanes,
naming conventions, and skills reference — open [`view/index.html`](view/index.html).

The public explorer also packages a derived **Skills** section sourced from `.cursor/skills/`, so
the collaboration mechanics stay discoverable without making the editor-local skill files the
public corpus of record.

The next distribution direction is a generated **download pack** for local reuse, with an explicit
preference for packaging before any installer-style automation. See
[`decisions/2026-05-29-download-pack-first-for-methodology-distribution.md`](decisions/2026-05-29-download-pack-first-for-methodology-distribution.md).

See [`decisions/2026-05-24-rich-views.md`](decisions/2026-05-24-rich-views.md) for the rationale
and convention governing rich views across the whole documentation corpus.

## Quick start prompts

Use these prompts in a fresh session to trigger the method quickly.

### 0) Start with the smallest rigorous path

`Use the Minimum Viable Method for this topic. First classify the lane, then create only the next necessary artifact.`

### 1) Start from a raw idea

`Use vision-intake, then backlog-triage for this new idea. Create V-* and I-* entries in product-docs.`

### 2) Stress-test before design lock-in

`Run grill-me on I-* or TB-* and produce critical questions, top risks, and 2-3 design options with recommendation.`

### 3) Start with a live working plan first

`Use plan-incubation. Start in Plan mode for freeform option exploration, create a working plan, then materialize the durable output into V-* and/or I-* without treating it as retrofit.`

### 4) Promote to bounded execution

`If ready, use tracer-bullet-promote and test-strategy-planner to create TB-* and a test-plan slice.`

### 5) Prepare component-level design

`Use component-design-pack for impacted components and link boundaries, mappings, validation, and error paths.`

### 6) Gate and close

`Run quality-gatekeeper, then traceability-sync and closeout for explicit status transitions and residual risks.`

### 7) Retrofit historical plans

`Run legacy-plan-miner on selected historical plans, then retrofit-curator to map signal into canonical product-docs targets.`

### 8) Retrofit from verbal history

`Start legacy knowledge retrofit from verbal briefing. Use source type verbal, create R-*, then map signal into canonical docs.`

### 9) Run a blitz intake (multi-item capture session)

`Run a blitz intake. I will dictate several items; capture verbatim, classify by batch, materialize V-*/I-*/R-* in English, and archive the scratch board to blitz-archive (do not delete).`

### 10) Start from a delivered feature that now needs change

`Start a post-delivery change inception. Diagnose whether this is a direct technical fix or requires re-entry through TB, I, or V, then use only the necessary artifacts.`

### 11) Improve the methodology itself

`Start a methodology feedback lane from this session. Record the process decision under methodology/decisions, preserve the key verbatim signal, and update the smallest affected methodology files.`
