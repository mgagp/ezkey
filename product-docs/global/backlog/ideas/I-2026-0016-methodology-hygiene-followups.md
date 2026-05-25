# Backlog Idea — `I-2026-0016` Methodology hygiene follow-ups (Tier 2 from 2026-05-08 nomenclature pass)

## Metadata

- **ID:** `I-2026-0016`
- **Status:** `done`
- **Priority:** `P3`
- **Created at:** `2026-05-08`
- **Updated at:** `2026-05-24`
- **Last reviewed at:** `2026-05-24`
- **Closed at:** `2026-05-24`
- **Phase tags:** `P1-operability`
- **Component tags:** `docs`, `methodology`

## Intent

Track the residual methodology-precision items observed during the nomenclature hardening pass on 2026-05-08, that were not part of the Tier 1 batch. The Tier 1 batch (lifecycle vocabularies for `V-*` and `TB-*`, transition triggers for `R-*`, `promoted` vs `archived` semantics, priority refinement, identifier hygiene for slug-based IDs) was already applied to `nomenclature.md` in the same session. This idea collected the lower-priority items for a future hygiene session.

## Resolution (2026-05-24)

All Tier 2 items closed. Canonical record:
[`../../methodology/decisions/2026-05-24-artifact-tagging-canonization-conventions.md`](../../methodology/decisions/2026-05-24-artifact-tagging-canonization-conventions.md).

| Item | Decision summary | Where documented |
|------|------------------|----------------|
| **(E)** V-* vs decisions | Tranched decisions OK in `V-*` + grill while `under-review`; canonize to durable destinations before `promoted` | [`../../global/vision/README.md`](../../global/vision/README.md) |
| **(F)** Decision log filename | Global: `architecture-decisions.md`; component: `design-decisions.md`; process: `methodology/decisions/` | [`../../methodology/nomenclature.md`](../../methodology/nomenclature.md) |
| **(H)** Phase tags | Closed P0–P4 from roadmap; extension via ADR only | `nomenclature.md` |
| **(I)** Component tags | Closed table + GitHub label alignment; use `docs` not `docs (product-docs)` | `nomenclature.md` |
| **(J)** Skill candidate | Soft convention: optional `## Automation follow-up (optional)` in `I-*` | `nomenclature.md`, [`../../templates/backlog-idea.template.md`](../../templates/backlog-idea.template.md) |
| **(K)** Superseded / Supersedes | Formalized for reformulation archives | `nomenclature.md` |

Additional: **Corpus completeness audit** checklist (seven points) added to `nomenclature.md` and
referenced from `blitz-intake-pattern.md` for repeatable blitz/backlog integration reviews.

## Links

- Decision: [`../../methodology/decisions/2026-05-24-artifact-tagging-canonization-conventions.md`](../../methodology/decisions/2026-05-24-artifact-tagging-canonization-conventions.md)
- Methodology: [`../../methodology/nomenclature.md`](../../methodology/nomenclature.md), [`../../global/vision/README.md`](../../global/vision/README.md)
