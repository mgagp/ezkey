# Backlog Idea — `I-2026-0008` Rate-limit baseline analysis and generalization

## Metadata

- **ID:** `I-2026-0008`
- **Status:** `incubating`
- **Priority:** `P2`
- **Created at:** `2026-05-08`
- **Updated at:** `2026-05-24`
- **Last reviewed at:** `2026-05-24`
- **Phase tags:** `P2-hardening`, `P1-operability`
- **Component tags:** `admin-api`, `auth-api`, `integration-api`

## Intent

Evaluate a **documented rate-limit baseline + override** model. Code changes only if inventory
proves simplification wins. Honest “no single baseline” outcome is acceptable.

## Grilling decisions (2026-05-19)

See [`../grill-sessions/blitz-2026-05-08-2-D1-rate-limit-registry-grill-me.md`](../grill-sessions/blitz-2026-05-08-2-D1-rate-limit-registry-grill-me.md).

- Model: baseline + specialized limits (Integration throughput, sensitive admin ops, device auth).
- R1 deliverable: policy + CONFIGURATION.md; code optional.

## Cross-cutting discovery (post downscope 2026-05-24)

No standing controllers registry — see [`../../../methodology/decisions/2026-05-24-controllers-registry-downscope.md`](../../../methodology/decisions/2026-05-24-controllers-registry-downscope.md).

**Structural inventory (one analysis pass, not a living doc):**

1. OpenAPI tags / paths under `specs/admin-api`, `specs/auth-api`, `specs/integration-api`.
2. `RateLimitFilter`, `RateLimitProperties`, and related entries in each module `CONFIGURATION.md`.
3. Targeted read of `@RestController` classes in the four API modules (no whole-repo scan).

**Semantic columns** (audience, volume tier, sensitivity, rate-limit posture) are recorded in the
**rate-limit policy output** of this analysis — not in a separate synchronized registry.

## Scope

- **In scope:**
  - Inventory current rate limits using discovery steps above.
  - Propose baseline bands or record why none fits.
  - Update component docs / CONFIGURATION.md.
- **Out of scope:**
  - New rate-limit infrastructure; adaptive per-tenant limits.
  - Maintaining a parallel controllers registry (`I-2026-0009` dropped).

## Promotion notes

Ready for design pack / policy doc when analysis pass completes (baseline proposal or explicit
no-baseline record). No registry prerequisite.

## Links

- Decision (registry downscope): [`../../../methodology/decisions/2026-05-24-controllers-registry-downscope.md`](../../../methodology/decisions/2026-05-24-controllers-registry-downscope.md)
- Grill: `../grill-sessions/blitz-2026-05-08-2-D1-rate-limit-registry-grill-me.md`
- Paginated tiers: [`../../admin-ui-paginated-screens-matrix.md`](../../admin-ui-paginated-screens-matrix.md)
