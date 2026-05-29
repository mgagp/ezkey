---
public: false
---
# Controllers Registry Downscope

## Date

2026-05-24

## Context

Blitz 2026-05-08-2 D1 materialized [`api-controllers-registry.md`](../../global/api-controllers-registry.md) and
backlog [`I-2026-0009`](../../global/backlog/ideas/I-2026-0009-global-controllers-registry.md) as a **living**
cross-cutting index of REST controllers with semantic columns (audience, volume tier, sensitivity,
rate-limit posture).

After hygiene formalization (including optional skill `ezkey-controllers-registry-sync`), the
registry table remained **empty**. At ~17 `@RestController` classes across four API modules,
structural discovery is cheap (OpenAPI, targeted `rg`, component flows). **Keeping a second
document synchronized on every controller change** duplicates OpenAPI and code without enough
payoff at current scale.

The underlying need remains valid: **cross-cutting examination** for rate limits, SQL business
limits, and grills. The separate permanent registry is not the lightest way to serve that need.

## Working assumptions

- OpenAPI specs and `docs/ENDPOINT.md` remain authoritative for **what endpoints exist**.
- Semantic attributes (audience, tier, sensitivity, rate-limit posture) belong in **policy outputs**
  of the analyses that need them — not in a permanently synced parallel index.
- Revisit a dedicated registry only if API surface grows materially (order-of-magnitude more
  controllers) or churn makes rediscovery repeatedly expensive.

## Options considered

| Option | Advantages | Disadvantages |
|--------|------------|---------------|
| **Keep living registry + skill** | Single table for all cross-cutting reads | Sync tax; empty table signal; redundant with OpenAPI for structure |
| **Downscope (chosen)** | Keeps analysis intent; drops redundant maintenance | One-time realignment work; grill D1 materialization partially superseded |
| **Full abandon without replacement** | Minimal docs | Loses explicit cross-cutting discovery pattern for `I-2026-0008` / `I-2026-0015` |

## Decision

**Downscope:** retire the living controllers registry and sync skill. Absorb cross-cutting
**semantic** inventory into the analyses that consume it:

| Need | New home |
|------|----------|
| Rate-limit baseline (`I-2026-0008`) | Policy section + CONFIGURATION.md alignment; structural discovery via OpenAPI + `RateLimitFilter` / properties + targeted controller reads |
| SQL business limits (`I-2026-0015`) | [`sql-business-limits-policy.md`](../../global/sql-business-limits-policy.md) inventory table; paginated matrix Tier B; targeted repository grep |
| Pagination / operator volume | [`admin-ui-paginated-screens-matrix.md`](../../global/admin-ui-paginated-screens-matrix.md) |
| Grills / TB | Targeted reads in affected modules; no standing registry gate |

**Dropped:** `I-2026-0009` (separate registry artifact). **Not dropped:** cross-cutting analysis
backlog items (`I-2026-0008`, `I-2026-0015`).

No PR-by-PR registry sync obligation. Update policy tables **when the analysis or implementation
slice runs**, in the same change set as the policy or code.

## Consequences

- [`api-controllers-registry.md`](../../global/api-controllers-registry.md) → archival stub with supersession pointer (no living table).
- [`I-2026-0009`](../../global/backlog/ideas/I-2026-0009-global-controllers-registry.md) → `dropped` with rationale.
- Removed [`.cursor/skills/ezkey-controllers-registry-sync/`](../../../.cursor/skills/ezkey-controllers-registry-sync/) skill.
- [`I-2026-0008`](../../global/backlog/ideas/I-2026-0008-rate-limit-baseline-analysis.md) no longer blocked on registry; documents discovery pattern instead.
- Grill session D1 and blitz archive retain historical record; post-decision notes added where helpful.
- Corpus completeness audit: a blitz item may be **dropped with recorded rationale** — integration is not "everything still incubating."

## Related documents

- [`../../global/backlog/grill-sessions/blitz-2026-05-08-2-D1-rate-limit-registry-grill-me.md`](../../global/backlog/grill-sessions/blitz-2026-05-08-2-D1-rate-limit-registry-grill-me.md)
- [`../../global/backlog/ideas/I-2026-0008-rate-limit-baseline-analysis.md`](../../global/backlog/ideas/I-2026-0008-rate-limit-baseline-analysis.md)
- [`../../global/sql-business-limits-policy.md`](../../global/sql-business-limits-policy.md)
- [`../nomenclature.md`](../nomenclature.md) — Superseded / dropped conventions
