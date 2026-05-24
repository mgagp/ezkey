# Methodology Pack

This folder defines the lightweight operating method used to move from product ideation to implementation in Ezkey.

It is designed for mixed collaboration:

- human to human,
- human to AI,
- AI to AI (through bounded context documents and specialized skills).

## Why this exists

Ezkey already has strong global and component documentation. This pack adds the missing connective tissue:

- how ideas are captured,
- how they are challenged and refined,
- how they are promoted into delivery work,
- how quality and traceability are enforced with minimal ceremony.

## Reading order

1. `workflow-overview.md`
2. `analysis-and-design-canon.md`
3. `tracer-bullet-method.md`
4. `testing-strategy-in-workflow.md`
5. `plan-incubation-workflow.md`
6. `legacy-retrofit-workflow.md`
7. `blitz-intake-pattern.md`
8. `multi-branch-workflow.md`
9. `session-start-guide.md`
10. `quality-gates.md`
11. `ai-collaboration-model.md`
12. `nomenclature.md`

## Scope boundaries

- Product direction and intent remain in `../global/`.
- Component implementation details remain in `../components/`.
- This pack defines process and collaboration mechanics.
- Rationale for non-obvious methodology choices lives in `decisions/`.

## Core principle

Use the lightest process that still preserves:

- analytical rigor,
- design consistency,
- end-to-end traceability,
- clear handoff quality between humans and AI agents.

Apply [`../global/design-principles.md`](../global/design-principles.md) when judging scope — especially **#1**, **#2**, and **#14 (beautiful problems)**: defer scale/performance sophistication until evidence shows the problem is real and earned by adoption, not hypothetical.

For Admin UI, role visibility, and **deployment operator geometries**, read [`../global/operator-alignment-guide.md`](../global/operator-alignment-guide.md). Canonical cross-cutting artifacts: [`../global/admin-ui-paginated-screens-matrix.md`](../global/admin-ui-paginated-screens-matrix.md), [`../global/api-controllers-registry.md`](../global/api-controllers-registry.md), [`../global/ezkey-system-identity-sensitivity-report.md`](../global/ezkey-system-identity-sensitivity-report.md), [`../global/sql-business-limits-policy.md`](../global/sql-business-limits-policy.md).

## Quick start prompts

Use these prompts in a fresh session to trigger the method quickly.

### 1) Start from a raw idea

`Use ezkey-vision-intake, then ezkey-backlog-triage for this new idea. Create V-* and I-* entries in product-docs.`

### 2) Stress-test before design lock-in

`Run ezkey-grill-me on I-* or TB-* and produce critical questions, top risks, and 2-3 design options with recommendation.`

### 3) Start with a live working plan first

`Use ezkey-plan-incubation. Start in Plan mode for freeform option exploration, create a working plan, then materialize the durable output into V-* and/or I-* without treating it as retrofit.`

### 4) Promote to bounded execution

`If ready, use ezkey-tracer-bullet-promote and ezkey-test-strategy-planner to create TB-* and a test-plan slice.`

### 5) Prepare component-level design

`Use ezkey-component-design-pack for impacted components and link boundaries, mappings, validation, and error paths.`

### 6) Gate and close

`Run ezkey-quality-gatekeeper, then ezkey-traceability-sync and ezkey-closeout for explicit status transitions and residual risks.`

### 7) Retrofit historical plans

`Run ezkey-legacy-plan-miner on selected historical plans, then ezkey-retrofit-curator to map signal into canonical product-docs targets.`

### 8) Retrofit from verbal history

`Start legacy knowledge retrofit from verbal briefing. Use source type verbal, create R-*, then map signal into canonical docs.`

### 9) Run a blitz intake (multi-item capture session)

`Run a blitz intake. I will dictate several items; capture verbatim, classify by batch, materialize V-*/I-*/R-* in English, and archive the scratch board to blitz-archive (do not delete).`
