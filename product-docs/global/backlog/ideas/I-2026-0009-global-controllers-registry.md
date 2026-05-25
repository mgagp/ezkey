# Backlog Idea — `I-2026-0009` Global controllers registry as documentation artifact

## Metadata

- **ID:** `I-2026-0009`
- **Status:** `dropped`
- **Priority:** `P2`
- **Created at:** `2026-05-08`
- **Updated at:** `2026-05-24`
- **Last reviewed at:** `2026-05-24`
- **Phase tags:** `P1-operability`
- **Component tags:** `docs`, `admin-api`, `auth-api`, `integration-api`, `crypto-api`

## Drop rationale (2026-05-24)

A **living** controllers registry duplicated OpenAPI and cheap targeted discovery at current scale
(~17 API controllers) without enough analysis payoff. The registry table was never populated —
signal that sync cost exceeded value.

**Downscope decision:** [`../../../methodology/decisions/2026-05-24-controllers-registry-downscope.md`](../../../methodology/decisions/2026-05-24-controllers-registry-downscope.md)

Cross-cutting examination (audience, tier, sensitivity, rate-limit posture) is **absorbed** into
policy outputs when analyses run:

- [`I-2026-0008`](I-2026-0008-rate-limit-baseline-analysis.md) — rate-limit baseline + CONFIGURATION.md
- [`I-2026-0015`](I-2026-0015-business-limits-large-volume-sql.md) — SQL limits policy inventory
- [`../../admin-ui-paginated-screens-matrix.md`](../../admin-ui-paginated-screens-matrix.md) — operator list tiers

**Supersedes:** former [`../../api-controllers-registry.md`](../../api-controllers-registry.md) (archived stub).

## Historical intent (Blitz D1)

Author a canonical high-level index of REST controllers for cross-cutting analysis. Grilled
2026-05-19 — see [`../grill-sessions/blitz-2026-05-08-2-D1-rate-limit-registry-grill-me.md`](../grill-sessions/blitz-2026-05-08-2-D1-rate-limit-registry-grill-me.md).

## Links

- Decision: [`../../../methodology/decisions/2026-05-24-controllers-registry-downscope.md`](../../../methodology/decisions/2026-05-24-controllers-registry-downscope.md)
- Archived artifact: [`../../api-controllers-registry.md`](../../api-controllers-registry.md)
- Successor work: `I-2026-0008`, `I-2026-0015`
