# Plan Incubation and Canonical Materialization

## Purpose

This workflow defines the legitimate hybrid path where a session starts with a **live working plan** created in agent Plan mode, then later materializes the useful signal into the canonical `product-docs` structure.

It exists to distinguish **current-session incubation** from **legacy retrofit**.

The key difference is intent:

- **legacy retrofit** mines value from historical or previously uncategorized sources;
- **plan incubation** uses a deliberate, current-session planning artifact as a high-value brainstorming and convergence tool before canonicalization.

## Why this lane exists

Modern coding agents are strong at freeform planning, option comparison, and early-stage brainstorming.
That strength is valuable and should not be suppressed just because the repository also uses a structured documentation method.

This methodology therefore recognizes a hybrid posture:

1. start with a pragmatic working plan when that accelerates exploration;
2. iterate until the direction is coherent enough;
3. materialize the durable value into the canonical method artifacts (`V-*`, `I-*`, `TB-*`, component docs, traceability docs).

This is **not** framed as "retrofitting the past."  
It is framed as **canonical materialization of a live incubation artifact**.

## Definition: working plan

A **working plan** is a non-canonical planning artifact used to:

- explore options,
- compare tools or designs,
- capture emerging structure,
- support brainstorming with an AI planner,
- prepare later promotion into canonical docs.

Typical locations:

- `.cursor/plans/` (inside the **git clone**)
- `plans/`
- `.github/prompts/plan-*.prompt.md` (repo-hosted working plans for GitHub Copilot or shared intake)

**Not automatically retained:** Cursor IDE Plan mode may write plan files under the **user** Cursor
directory (for example `~/.cursor/plans/`), outside the repository. Those files are useful session
scaffolding; they are **not** methodology working plans until deliberately promoted into a
repo-hosted location above. See [Ephemeral Plan mode vs retained working plan](#ephemeral-plan-mode-vs-retained-working-plan).

Typical properties:

- richer and more conversational than canonical docs,
- may contain design-space exploration that should not be copied verbatim,
- may mix options, recommendations, and provisional execution details,
- is valuable as a source artifact even when not itself the canonical destination.

## When to use this lane

Use plan incubation when:

- the operator wants the freedom and speed of Plan mode first,
- the topic is substantial enough to benefit from tool comparison or branching analysis,
- the direction is still fluid,
- the canonical artifact shape is not yet obvious at session start,
- the plan is being created **now**, not mined as historical material.

Do **not** use this lane when:

- the idea is already crisp enough for direct `V-*` / `I-*` capture,
- the goal is specifically to mine older plans or verbal history,
- the source material is mainly historical and belongs in the retrofit lane.

## Output model

The working plan is an **incubation source**, not the final home.

After convergence, materialize the signal into one or more of:

- `V-*` when the output is orientation, direction, or principle-level product intent,
- `I-*` when the output is actionable backlog scope,
- `TB-*` when the output is a bounded executable slice,
- component design documents when boundary analysis is mature enough,
- test strategy and traceability artifacts when delivery is being prepared.

## Recommended sequence

1. **Incubate in Plan mode**
   - create or evolve a working plan;
   - compare options freely;
   - use the plan as the conversation scaffold.
2. **Converge**
   - identify the stable recommendation, open questions, and likely next artifact type.
3. **Classify**
   - decide whether the plan materializes primarily into `V-*`, `I-*`, `TB-*`, principle candidates, or a mix.
4. **Materialize**
   - write the canonical artifacts in English;
   - keep them concise and method-aligned;
   - avoid copying the working plan verbatim.
5. **Classify retention** — ephemeral scaffold vs retained working plan (see
   [Ephemeral Plan mode vs retained working plan](#ephemeral-plan-mode-vs-retained-working-plan)).
   If ephemeral, skip the bidirectional gate for that plan and do not invent half-links.
6. **Bidirectional traceability gate** (mandatory **only for retained** working plans)
   - complete the gate in [Bidirectional traceability gate](#bidirectional-traceability-gate-mandatory-for-retained-plans) before treating retained-plan materialization as done.
7. **Cross-link**
   - link the canonical artifacts back to the working plan when the plan is **retained**;
   - record related artifact IDs in the plan if the plan remains in active use.
8. **Continue or close**
   - continue using the plan if it still helps the next slice,
   - or leave it as a retained supporting artifact once the canonical docs are sufficient,
   - or close as ephemeral if classification A applied.

## Ephemeral Plan mode vs retained working plan

Cursor Plan mode is encouraged. At materialization, choose explicitly:

| Classification | When | What to do |
| --- | --- | --- |
| **A — Ephemeral scaffold** | Durable signal is fully and unambiguously in `V-*` / `I-*` / `TB-*`; plan adds no important residual option space | Materialize canon only. Do **not** copy the plan into the repo just for ceremony. Do **not** link to paths outside the clone. Optional one-line note that Plan mode was ephemeral. Do **not** claim the retained-plan bidirectional gate for that file. |
| **B — Retained working plan** | Plan still holds useful option space, rejected alternatives, or execution notes | Promote/copy into a repo-hosted location, then pass the **bidirectional gate**. |

Authority: [`decisions/2026-07-11-cursor-plan-ephemeral-vs-retained-working-plan.md`](decisions/2026-07-11-cursor-plan-ephemeral-vs-retained-working-plan.md).

**Hard rule:** never leave corpus → plan provenance that a cold agent cannot resolve from a fresh clone.

## Bidirectional traceability gate (mandatory for retained plans)

When step 4 produces any artifact under `product-docs/` from a **retained** working plan, **the
lane is not complete** until this gate passes. Ephemeral scaffolds (classification A) do not use
this gate. The goal is a **conductive thread** between original intent (plan) and canonical record
(`product-docs`) so humans and agents can move in either direction when revisiting, adjusting, or
extending the work.

Invoke the `plan-incubation` skill at materialization time; its closeout step enforces this gate
for retained plans.

### Gate checklist

| # | Direction | Requirement |
| --- | --- | --- |
| 1 | **Corpus → plan** | Every materialized artifact (`V-*`, `I-*`, `TB-*`, design docs, etc.) lists **all retained** working plan paths under `## Related documents`, `## Links`, or an `## Incubation sources` section. |
| 2 | **Plan → corpus** | Every retained working plan ends with a **`Canonical materialization`** section listing every generated artifact, materialization date, lane (`B`), and the plan's role after ingestion. |
| 3 | **Cross-IDs** | Canonical artifact IDs (`V-*`, `I-*`, `TB-*`) appear in both directions where they exist. |
| 4 | **Implementation** (when started) | GitHub issue and branch are recorded in `I-*` / `TB-*` and optionally echoed in plan closeout. |

If multiple working plans exist for one incubation (e.g. `.cursor/plans/*.plan.md` **and**
`.github/prompts/plan-*.prompt.md`), link **all** retained copies — not only one tool's path.

### Canonical materialization section (template)

Add this block at the end of each retained working plan after ingestion:

```markdown
**Canonical materialization**
- Materialization lane: `Lane B` — plan incubation → canonical `product-docs`.
- Status: materialized on `YYYY-MM-DD`.
- Vision: `product-docs/global/vision/V-...`
- Backlog idea: `product-docs/global/backlog/ideas/I-...`
- Tracer bullet: `product-docs/global/backlog/TB-...` (if any; lives at `backlog/` root, not under `ideas/`)
- Design / other canon: `product-docs/global/...` (if any)
- GitHub issue / branch: `#NNN`, `feature/NNN-...` (if implementation started)
- Methodology gate: retained-plan bidirectional traceability completed
- Plan role after materialization: retained source and option-space record; canonical direction lives in the linked artifacts above.
```

Adjust paths for plans under `.github/prompts/` (use relative links into `product-docs/`).

### Incubation sources block (template for corpus artifacts)

```markdown
## Incubation sources

- Working plan (GitHub): `.github/prompts/plan-<slug>.prompt.md`
- Working plan (Cursor): `.cursor/plans/<slug>.plan.md` (if retained)
- Lane: `B` — plan incubation, materialized `YYYY-MM-DD`
```

## Framing rule

When a working plan is current-session source material, describe the follow-up step as:

- **materialization**,
- **canonicalization**,
- **promotion into canonical docs**,
- or **ingestion into the methodology**.

Do **not** describe it as **legacy retrofit** unless the source is truly historical or mixed with historical evidence.

## Relationship to the retrofit lane

The two lanes share one important rule:

- extract the durable **decision value**, not the whole verbatim source.

But they differ in posture:

- **Plan incubation** is forward-looking and session-native.
- **Retrofit** is historical or previously uncategorized knowledge recovery.

If a working plan later becomes old and must be mined after the fact, it may become retrofit input **later**. That does not make its original use a retrofit session.

## Practical guidance

- Prefer the lightest canonical destination that preserves the value.
- Do not force an `R-*` just because a plan file exists.
- Do not force a repo-hosted plan copy when classification A (ephemeral) already applies.
- Keep the working plan when it still contains useful option space, rejected alternatives, or execution notes.
- Promote only what has durable product, delivery, or traceability value.

## Definition of done

**Retained** plan incubation (classification B) is complete when:

- the working plan has produced at least one canonical artifact,
- the canonical artifacts clearly express the durable intent,
- **the bidirectional traceability gate has passed** (see above),
- the plan's role is explicit (still active support artifact, or retained source only),
- and the language used is "materialization/canonicalization," not accidental "retrofit," unless the source truly warrants it.

**Ephemeral** Plan-aided capture (classification A) is complete when:

- durable signal lives unambiguously in `V-*` / `I-*` / `TB-*` (or related canon),
- no half-link points outside the clone,
- and the optional ephemeral-scaffolding note (if any) does not claim the retained-plan gate.

## Related documents

- [`decisions/2026-07-11-cursor-plan-ephemeral-vs-retained-working-plan.md`](decisions/2026-07-11-cursor-plan-ephemeral-vs-retained-working-plan.md)
- [`workflow-overview.md`](workflow-overview.md)
- [`session-start-guide.md`](session-start-guide.md)
- [`legacy-retrofit-workflow.md`](legacy-retrofit-workflow.md)
- [`ai-collaboration-model.md`](ai-collaboration-model.md)
- [`nomenclature.md`](nomenclature.md)
