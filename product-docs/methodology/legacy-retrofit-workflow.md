# Legacy Knowledge Retrofit Workflow

## Purpose

This workflow captures value from historical plans, verbal briefings, and ad hoc implementation history, then redistributes that value into the canonical product-docs structure.

The goal is not to migrate old plans wholesale.  
The goal is to migrate **decision value**, **behavioral knowledge**, and **traceability signal**.

## Why this exists

Ezkey has a large historical knowledge corpus with uneven structure:

- historical plans (`plans/`, `.cursor/plans/`, archives),
- verbal product and engineering rationale shared in sessions,
- ad hoc implementation history not explicitly documented in canonical form.

Without a retrofit lane:

- useful decisions stay hard to discover,
- repeated analysis happens unnecessarily,
- canonical docs can drift from historical implementation intent.

## Retrofit lane overview

1. **Select** a small source batch (1 to 3 sources).
2. **Extract** decisions, invariants, patterns, risks, and test signal.
3. **Map** extracted signal to canonical destinations.
4. **Record** a retrofit artifact (`R-*`) with confidence and gaps.
5. **Integrate** targeted updates into global/component docs.
6. **Close** with explicit remaining gaps and next action.

## Canonical destinations

Route extracted signal to one or more of:

- `global/features-and-phases.md`
- `global/spec-test-traceability.md`
- `global/vision/*`
- component `design-decisions.md`
- component `functional-flows.md`
- component `api-and-boundary-mappings.md`
- component `exception-and-error-model.md`

## Source evidence types

Each retrofit slice should declare one source type:

- `plan` — historical plan files are available.
- `verbal` — knowledge captured from verbal briefing.
- `ad-hoc-code-history` — inferred from existing implementation and commit-era context.
- `mixed` — a combination of the above.

When source is verbal or mixed, record:

- capture date,
- capture context,
- validation-needed notes for uncertain items.

## Practical constraints

- Keep each retrofit session small and bounded.
- Prefer high-signal topics with current product relevance.
- Never duplicate full historical narrative when a concise canonical summary is enough.

## Weekly cadence recommendation

For a weekly retrofit session:

1. Pick one topic (for example: mobile local-auth, audit chain, lifecycle semantics).
2. Select up to three source artifacts (plans and/or verbal capture notes).
3. Produce one `R-*` artifact.
4. Apply one to three canonical updates.
5. Record open gaps and next batch candidate.

## Definition of done (retrofit slice)

A retrofit slice is done when:

- at least one canonical destination was updated,
- source-to-canonical links are explicit,
- confidence and residual gaps are documented,
- next action is explicit (or the slice is archived).

## Principle adoption channel

When retrofit work surfaces project principles or durable values:

1. Capture candidate principle in `R-*` slice.
2. Debate and refine in analysis.
3. Promote adopted principle to canonical location:
   - `product-docs/global/design-principles.md` for product/design principles,
   - `AGENTS.md` and/or `.cursor/rules/*.mdc` for agentic operating principles.
4. Mark adoption status (`proposed`, `adopted`, `superseded`) and link back to the source `R-*`.
