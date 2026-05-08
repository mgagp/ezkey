# Backlog Idea — `I-2026-0016` Methodology hygiene follow-ups (Tier 2 from 2026-05-08 nomenclature pass)

## Metadata

- **ID:** `I-2026-0016`
- **Status:** `captured`
- **Priority:** `P3`
- **Created at:** `2026-05-08`
- **Updated at:** `2026-05-08`
- **Last reviewed at:** `2026-05-08`
- **Phase tags:** `P1-operability`
- **Component tags:** `docs (product-docs)`, `methodology`

## Intent

Track the residual methodology-precision items observed during the nomenclature hardening pass on 2026-05-08, that were not part of the Tier 1 batch. The Tier 1 batch (lifecycle vocabularies for `V-*` and `TB-*`, transition triggers for `R-*`, `promoted` vs `archived` semantics, priority refinement, identifier hygiene for slug-based IDs) was already applied to `nomenclature.md` in the same session. This idea collects the lower-priority items for a future hygiene session.

## Problem and value

- **Problem:** Several methodology conventions are currently implicit (used in practice, not documented), creating a risk of drift over time as more artifacts are created.
- **Expected value:** Tighter methodology vocabulary, lower onboarding cost for any new operator (human or AI), reduced ambiguity in artifact lifecycle and references.

## Scope

Items to address (Tier 2 from the 2026-05-08 review):

- **(E) `V-*` purpose vs decision recording.** Clarify in `product-docs/global/vision/README.md` whether a `V-*` may contain a tranched-but-not-yet-canonized decision (current practice, working — see `V-2026-0003`) or whether a tranched decision should immediately go to the canonical decisions log and the `V-*` be promoted in the same step. Document the chosen pattern explicitly.
- **(F) Canonical filename for the decisions log.** The decisions log has been referenced as both `architecture-decisions.md` and `design-decisions.md` in conversation and in `nomenclature.md`. Pick one, document it, and reference consistently from `methodology/` and `global/` docs.
- **(H) Phase tags as closed enumeration.** Confirm and document explicitly whether `P0-foundations`, `P1-operability`, `P2-hardening`, `P3-distribution`, `P4-compliance-readiness` is a closed list. Recommended location: `nomenclature.md` or `features-and-phases.md`. If extensible, document the rule for adding a new phase.
- **(I) Component tags as closed enumeration.** Same question for the component tag vocabulary (`mobile`, `admin-api`, `auth-api`, `integration-api`, `admin-ui`, `infra`, `docs`, `audit`, `core`, `bootstrap`, `repositories`, etc.). Decide and document. Note: backlog ideas have used variations such as `docs (product-docs)`, suggesting the convention is currently soft.
- **(J) "Future skill candidate" pattern.** Used in `I-2026-0009` and `I-2026-0015`. Either formalize as a documented sub-section in the `I-*` template (with explicit fields), or leave implicit and acknowledge it is a soft convention.

**Excluded** (already addressed in Tier 1 on 2026-05-08):

- `V-*` status vocabulary (added).
- `TB-*` status vocabulary (added).
- `R-*` status transition triggers (added).
- `promoted` vs `archived` semantics (clarified).
- Priority semantics refinement (`P0`/`P1`/`P2`/`P3` redefined with operational meaning).
- Identifier hygiene for slug-based IDs (added).

## Key assumptions

- These items create no immediate operational pain — current practice works as long as operators (human or AI) are careful.
- A single follow-up hygiene session can address all five items, in roughly one hour.
- The right outcome may be partial closure (some items closed, others intentionally left soft); the goal is explicit decision, not maximum formalization.

## Risks and exceptions

- Letting these drift further may cause silent inconsistencies between artifacts (for example, inconsistent component tags between two backlog ideas analyzing the same component).
- Going too prescriptive (e.g. fully closed enumerations with rejection of any new tag) may increase friction without proportional value. Aim for explicit decisions and documented soft conventions where appropriate.

## Promotion notes

Move to `triaged` once the operator allocates a hygiene session. Promotion to `ready` requires deciding whether the work warrants a tracer bullet (overkill — methodology has no runtime contract) or a single batch edit (recommended).

## Links

- Source: nomenclature hardening pass conducted on 2026-05-08 in the same session as the second blitz materialization.
- Methodology: [`nomenclature.md`](../../methodology/nomenclature.md), [`vision/README.md`](../vision/README.md), [`features-and-phases.md`](../features-and-phases.md).
- Related principles: `#9` (one canonical place per concept), `#13` (open-source transparency).
