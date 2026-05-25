# Backlog Idea — `I-2026-0008` Rate-limit baseline analysis and generalization

## Metadata

- **ID:** `I-2026-0008`
- **Status:** `done`
- **Priority:** `P2`
- **Created at:** `2026-05-08`
- **Updated at:** `2026-05-24`
- **Last reviewed at:** `2026-05-24`
- **Phase tags:** `P2-hardening`, `P1-operability`
- **Component tags:** `admin-api`, `auth-api`, `integration-api`

## Intent

Evaluate a **documented rate-limit baseline + override** model. Code changes only if inventory
proves simplification wins. Honest “no single baseline” outcome is acceptable.

## Resolution (2026-05-24)

Analysis complete. **No single numeric baseline** — four policy families (device auth, integration
throughput, admin sensitive ops, admin login) already implemented with justified overrides.

**Deliverable:** [`../../rate-limit-baseline-policy.md`](../../rate-limit-baseline-policy.md) +
existing module `CONFIGURATION.md` tables (cross-linked). **No code unification R1.**

## Grilling decisions (2026-05-19)

See [`../grill-sessions/blitz-2026-05-08-2-D1-rate-limit-registry-grill-me.md`](../grill-sessions/blitz-2026-05-08-2-D1-rate-limit-registry-grill-me.md).

- Model: baseline + specialized limits (Integration throughput, sensitive admin ops, device auth).
- R1 deliverable: policy + CONFIGURATION.md; code optional — **met**.

## Cross-cutting discovery (post downscope 2026-05-24)

Documented in policy deliverable — OpenAPI + `RateLimit*` code + `CONFIGURATION.md`; no standing
controllers registry.

## Links

- Policy: [`../../rate-limit-baseline-policy.md`](../../rate-limit-baseline-policy.md)
- Registry downscope: [`../../../methodology/decisions/2026-05-24-controllers-registry-downscope.md`](../../../methodology/decisions/2026-05-24-controllers-registry-downscope.md)
- Grill: `../grill-sessions/blitz-2026-05-08-2-D1-rate-limit-registry-grill-me.md`
- Paginated tiers: [`../../admin-ui-paginated-screens-matrix.md`](../../admin-ui-paginated-screens-matrix.md)
