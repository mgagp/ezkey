---
public: true
---
# Retire `ML-*` (Method Log) as a distinct artifact family

## Date

2026-07-28

## Context

A documentation-triage session investigating why `product-docs/global/` carries a large volume of
process/ceremony content (relative to component design-truth) traced `ML-*` (Method Log) — 24 files
under `product-docs/global/backlog/method-logs/` — back to a single origin: one bullet inside
[`2026-06-06-methodological-closeout-vs-code-hygiene.md`](2026-06-06-methodological-closeout-vs-code-hygiene.md),
a decision whose actual subject was classifying hygiene vs program before creating canonical
artifacts:

> `TB-*` (+ `TSP-*` when test evidence needs a standing record) for bounded execution programs;
> `ML-*` or methodology decision when the session teaches a reusable process lesson.

`ML-*` never received the foundation every other artifact type has:

- no entry in [`nomenclature.md`](../nomenclature.md) (ID scheme, status vocabulary);
- no row in [`workflow-overview.md`](../workflow-overview.md)'s artifact-by-stage table;
- no template under [`../../templates/`](../../templates/README.md);
- no purpose statement of its own — every mention lists it as an alternative to "methodology
  decision," never with a distinguishing rule for when to pick one over the other.

In practice the 24 files split cleanly into two shapes that already have a canonical, templated
home:

1. **Program-execution narrative** (kickoff notes, closeout summaries, investigation logs tied to a
   specific `I-*`/`TB-*`) — this is exactly what the `closeout` skill's evidence/deferred-items/
   residual-risks output and a `TB-*`/`I-*` **Closeout** section already exist to capture.
2. **Reusable process lessons** (a rule that should apply beyond the one session that surfaced it) —
   this is exactly what a `product-docs/methodology/decisions/*.md` record is for, and several
   `ML-*` files (e.g. the cold-start bootstrap one) already read as one in substance.

No `ML-*` file did something a Closeout section or a methodology decision could not already do.

## Working assumptions

- Orthogonality over new families: before adding a new artifact type, check whether an existing
  templated destination already covers the shape of content (this is the same principle already
  used to reject new artifact families in
  [`2026-05-28-methodology-feedback-and-post-delivery-reentry.md`](2026-05-28-methodology-feedback-and-post-delivery-reentry.md)
  Option C over Option B).
- An artifact type that only ever appears as a secondary option inside another decision's bullet —
  never defined on its own terms — has not earned independent status, regardless of how many
  instance files accumulated afterward.
- This is a **process correction**, not a reversal of the deeper methodology. Lanes A–E, the
  `I-*`/`V-*`/`TB-*`/`R-*` artifact model, and the hygiene-vs-program classification rule from the
  originating 2026-06-06 decision are all unaffected and remain fully valid.
- Retiring the type for **new** artifacts is separable from what happens to the **24 existing**
  files. Retention/archival policy for the backlog corpus as a whole is a distinct, still-open
  question (see Related follow-up in
  [`2026-07-28-legacy-documentation-default-gravity-to-adr.md`](2026-07-28-legacy-documentation-default-gravity-to-adr.md))
  and is out of scope here. The existing files are left in place as historical record.

## Options considered

| Option | Advantages | Disadvantages |
| --- | --- | --- |
| **A. Formalize `ML-*` properly** — add nomenclature entry, template, workflow-overview row, a distinguishing rule vs. methodology decisions | Legitimizes 24 existing files retroactively; no content migration | Institutionalizes a family that duplicates two destinations that already work; adds a fourth artifact type to remember and explain publicly |
| **B. Retire `ML-*` for new artifacts; redirect to `TB-*`/`I-*` Closeout section (program narrative) or a methodology decision (reusable lesson)** | Removes a redundant family; concentrates process learning at the two addresses the corpus already advertises; matches the orthogonality principle already used elsewhere | Existing 24 files remain an un-migrated pocket until a separate archival pass (accepted, out of scope) |
| **C. Leave as informal, undocumented habit (status quo)** | Zero editing cost | Reproduces the exact drift this session diagnosed; a cold agent or new contributor has no way to learn the intended distinction because none was ever written down |

## Decision

Adopt **Option B**.

`ML-*` is retired as a distinct artifact family for new work. Going forward:

- **Program-execution narrative** (kickoff context, evidence, deferred items, residual risks tied to
  a specific slice) is recorded in the **Closeout** section of the relevant `TB-*` or `I-*`, per the
  `closeout` skill.
- **Reusable process lessons** (a rule or pattern that should outlive the one session) are recorded
  as a **methodology decision** under `product-docs/methodology/decisions/`, following the existing
  template in [`README.md`](README.md).

No new `ML-*` file should be created after this decision. The 24 existing files under
`product-docs/global/backlog/method-logs/` are left in place as historical record; they are not
retroactively converted or deleted as part of this decision.

## Consequences

- [`2026-06-06-methodological-closeout-vs-code-hygiene.md`](2026-06-06-methodological-closeout-vs-code-hygiene.md)
  gains a **Historical interpretation note** (the originating decision's substance — classify
  hygiene vs program before materializing artifacts — is unaffected and is not rewritten).
- [`../methodological-values.md`](../methodological-values.md),
  [`../quality-gates.md`](../quality-gates.md), and
  [`../minimum-viable-method.md`](../minimum-viable-method.md) — each of which echoed `ML-*` as a
  peer of `I-*`/`TB-*`/`TSP-*` — are updated to point at the two real destinations instead.
- [`../../../.cursor/skills/closeout/SKILL.md`](../../../.cursor/skills/closeout/SKILL.md) is updated
  the same way so live agent guidance matches the corrected corpus.
- No change to Lanes A–E, the `I-*`/`V-*`/`TB-*`/`R-*` model, or any published site page beyond the
  one decision's additive note above — this does not require a methodology major-version bump (see
  [`2026-06-02-methodology-semantic-versioning-and-release-notes.md`](2026-06-02-methodology-semantic-versioning-and-release-notes.md)),
  a patch-level clarification is sufficient at the next release.

## Related documents

- [`2026-06-06-methodological-closeout-vs-code-hygiene.md`](2026-06-06-methodological-closeout-vs-code-hygiene.md) — originating decision; gains the historical note.
- [`2026-07-28-legacy-documentation-default-gravity-to-adr.md`](2026-07-28-legacy-documentation-default-gravity-to-adr.md) — sibling triage-session decision (ADR extraction reflex); names the backlog-volume question this decision partially resolves.
- [`../methodological-values.md`](../methodological-values.md) — value 6 (earned permanence), value 1 (orthogonality).
- [`../workflow-overview.md`](../workflow-overview.md)
- [`../nomenclature.md`](../nomenclature.md)
- [`../../../.cursor/skills/closeout/SKILL.md`](../../../.cursor/skills/closeout/SKILL.md)
