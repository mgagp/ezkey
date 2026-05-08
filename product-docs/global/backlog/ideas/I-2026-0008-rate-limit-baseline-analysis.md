# Backlog Idea — `I-2026-0008` Rate-limit baseline analysis and generalization

## Metadata

- **ID:** `I-2026-0008`
- **Status:** `triaged`
- **Priority:** `P2`
- **Created at:** `2026-05-08`
- **Updated at:** `2026-05-08`
- **Last reviewed at:** `2026-05-08`
- **Phase tags:** `P2-hardening`, `P1-operability`
- **Component tags:** `admin-api`, `auth-api`, `integration-api`

## Intent

Evaluate whether a global rate-limit baseline applicable across all APIs would deliver enough value relative to its complexity to justify adoption. Today, rate limits are tuned per API and per action. A baseline could provide a uniform floor — possibly relatively high for actions known to have high but human-bounded frequency — while specialized per-action limits remain on top.

## Problem and value

- **Problem:** Rate-limit decisions are local and fragmented. There is no documented baseline policy. Onboarding a new endpoint requires re-deriving a sensible posture from scratch each time, which risks either over-restriction or accidental gaps.
- **Expected value:** A predictable security floor across the API surface, lower cognitive cost when adding new endpoints, easier operator narrative around abuse protection, and consistency with `Design Principle #12` (security as a posture, not a feature).

## Scope

- **In scope:**
  - Inventory current rate-limit configurations across `admin-api`, `auth-api`, `integration-api`.
  - Propose a baseline (single rate or a small set of bands) with explicit justification anchored in real expected human-bounded frequencies.
  - Compare the baseline against current per-action limits; identify which can collapse to baseline and which need to remain specialized.
  - Document the baseline as policy in component docs and `design-decisions.md` where relevant.
- **Out of scope:**
  - New rate-limit infrastructure (assume the current implementation suffices).
  - Per-tenant or per-API-key adaptive limits (separate decision).

## Key assumptions

- The current rate-limit infrastructure (`F-rate-limiting`) supports a baseline-plus-specialized model without major rework.
- A baseline is meaningful only if a meaningful number of endpoints can collapse to it; the inventory will validate this.

## Risks and exceptions

- If the per-action posture is too varied, a baseline adds documentation overhead without simplification — in which case the right answer is to document the variation rather than impose a baseline.
- High-throughput integration endpoints may require higher baselines that weaken protection for lower-throughput endpoints if applied uniformly; the baseline-plus-specialized model addresses this but must be honestly enforced.

## Promotion notes

Move to `triaged` after the inventory is complete. If the inventory shows a coherent collapse opportunity, promote to `incubating` with a baseline proposal; otherwise move to `parked` with the rationale recorded.

## Links

- Companion: `I-2026-0009` (controllers registry — would surface the same inventory data and reduce the cost of this analysis).
- Related feature: `F-rate-limiting`.
- Related principles: `#1` (simplicity), `#12` (security as posture).
