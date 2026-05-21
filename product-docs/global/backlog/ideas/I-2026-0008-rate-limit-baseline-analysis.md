# Backlog Idea — `I-2026-0008` Rate-limit baseline analysis and generalization

## Metadata

- **ID:** `I-2026-0008`
- **Status:** `incubating`
- **Priority:** `P2`
- **Created at:** `2026-05-08`
- **Updated at:** `2026-05-19`
- **Last reviewed at:** `2026-05-19`
- **Phase tags:** `P2-hardening`, `P1-operability`
- **Component tags:** `admin-api`, `auth-api`, `integration-api`

## Intent

After the controllers registry exists, evaluate a **documented rate-limit baseline + override** model. Code changes only if inventory proves simplification wins. Honest “no single baseline” outcome is acceptable.

## Grilling decisions (2026-05-19)

See [`../grill-sessions/blitz-2026-05-08-2-D1-rate-limit-registry-grill-me.md`](../grill-sessions/blitz-2026-05-08-2-D1-rate-limit-registry-grill-me.md).

- **Depends on:** `I-2026-0009` / [`api-controllers-registry.md`](../../api-controllers-registry.md).
- Model: baseline + specialized limits (Integration throughput, sensitive admin ops, device auth).
- R1 deliverable: policy + CONFIGURATION.md; code optional.

## Scope

- **In scope:**
  - Inventory rate limits using registry columns.
  - Propose baseline bands or record why none fits.
  - Update component docs / CONFIGURATION.md.
- **Out of scope:**
  - New rate-limit infrastructure; adaptive per-tenant limits.

## Promotion notes

Blocked on registry draft for at least one module; then baseline proposal or explicit no-baseline record.

## Links

- Registry: [`../../api-controllers-registry.md`](../../api-controllers-registry.md)
- Companion: `I-2026-0009`
- Grill: `../grill-sessions/blitz-2026-05-08-2-D1-rate-limit-registry-grill-me.md`
