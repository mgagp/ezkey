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

- `.cursor/plans/`
- `plans/`

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
5. **Cross-link**
   - link the canonical artifacts back to the working plan when useful;
   - record related artifact IDs in the plan if the plan remains in active use.
6. **Continue or close**
   - continue using the plan if it still helps the next slice,
   - or leave it as a retained supporting artifact once the canonical docs are sufficient.

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
- Keep the working plan when it still contains useful option space, rejected alternatives, or execution notes.
- Promote only what has durable product, delivery, or traceability value.

## Definition of done

This lane is complete when:

- the working plan has produced at least one canonical artifact,
- the canonical artifacts clearly express the durable intent,
- the plan's role is explicit (still active support artifact, or retained source only),
- and the language used is "materialization/canonicalization," not accidental "retrofit," unless the source truly warrants it.

## Related documents

- [`workflow-overview.md`](workflow-overview.md)
- [`session-start-guide.md`](session-start-guide.md)
- [`legacy-retrofit-workflow.md`](legacy-retrofit-workflow.md)
- [`ai-collaboration-model.md`](ai-collaboration-model.md)
- [`nomenclature.md`](nomenclature.md)
