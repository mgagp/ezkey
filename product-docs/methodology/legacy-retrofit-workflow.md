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

1. **Select** a small source batch (1 to 3 sources). Record the search scope — what was searched, what was found, what was excluded.
2. **Extract** decisions, invariants, patterns, risks, and test signal.
3. **Annotate** each source file immediately after extraction (see [Source file annotation](#source-file-annotation)). Do not defer this step.
4. **Map** extracted signal to canonical destinations.
5. **Record** a retrofit artifact (`R-*`) with confidence, gaps, and a checklist of canonical destinations (see [R-* completeness checklist](#r-completeness-checklist)).
6. **Integrate** targeted updates into global/component docs. Each updated canonical section must back-reference the R-* (see [Canonical back-reference rule](#canonical-back-reference-rule)).
7. **Close** with explicit remaining gaps and next action.

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
- **all planned canonical destinations have an explicit checklist entry** (checked or deferred with reason),
- **all source files have been annotated** with `retrofitted_by` + `retrofitted_at`,
- **each updated canonical section back-references the R-*** artifact,
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

---

## Source file annotation

**Rule:** immediately after mining a source file, add the following fields to its YAML frontmatter (or, for files without frontmatter, add a visible header block):

```yaml
retrofitted_by: R-YYYY-NNNN
retrofitted_at: YYYY-MM-DD
```

**Why this matters:** without annotation, a future agent or contributor sees `status: pending` on plan todos and cannot tell whether the file has already been mined. The annotation prevents duplicate work and makes the trail auditable.

**For plan files with todos:** `retrofitted_by` means "the knowledge in this file has been mined and integrated into the canonical corpus." It does not imply that the todos have been executed. Execution tracking belongs in `TB-*` artifacts and GitHub issues.

**For files without YAML frontmatter** (Markdown without front matter, legacy docs, etc.): add a visible annotation block at the top:

```markdown
> **Retrofitted:** R-YYYY-NNNN — YYYY-MM-DD
```

---

## Canonical back-reference rule

**Rule:** whenever a retrofit slice adds or updates content in a canonical doc (design decision, functional flow, spec traceability, etc.), that content must include a reference back to the source R-* artifact.

For design decision entries, this goes in the `### Impact` or a `### Reference` sub-section:
```
- Reference: `product-docs/global/legacy-retrofit/R-YYYY-NNNN-<topic>.md`
```

For prose sections in component docs, a parenthetical is sufficient:
```
(Source: R-YYYY-NNNN)
```

**Why this matters:** canonical docs must be self-explaining. A reader should be able to follow the chain from any canonical decision back to the historical evidence that produced it.

---

## R-* completeness checklist

The `## Changes applied` section of a retrofit artifact must be an explicit checklist, not narrative prose. For each canonical destination planned in the mapping:

```markdown
## Changes applied

- [x] `product-docs/components/mobile/design-decisions.md` — added ADR-MOB-0005
- [x] `product-docs/global/backlog/ideas/I-YYYY-NNNN.md` — created
- [ ] `product-docs/global/features-and-phases.md` — deferred, not updated this slice
```

Unchecked items carry forward as gaps. The checklist makes completeness verifiable without reading every destination file.

---

## Mining scope note

When the source batch is not obvious (topic spans multiple directories, mixed sources, or verbal briefing), add a `## Search scope` note to the R-* artifact:

```markdown
## Search scope

- Searched: `plans/`, `.cursor/plans/`, `docs/`, `product-docs/`, GitHub issues
- Found relevant: [list of selected sources]
- Excluded: [files that were found but judged out of scope, with brief reason]
```

This is optional for plan-type slices with clearly bounded sources, and mandatory for verbal or mixed slices where the search surface is not self-evident.
