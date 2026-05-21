# Grill Me — Blitz 2026-05-08-2, D7 (Auth API versioning)

## Session control

| Field | Value |
|-------|--------|
| **Blitz source** | `product-docs/global/backlog/blitz-archive/blitz-2026-05-08-2.md` (D7) |
| **Vision** | `V-2026-0008` |
| **Backlog (R1 slice)** | `I-2026-0025` |
| **Cross-links** | `V-2026-0001`, `I-2026-0001` |
| **Status** | `complete` |
| **Date** | `2026-05-19` |

## Settled decisions (operator-confirmed)

| ID | Decision |
|----|----------|
| D7-1 | **Capability negotiation** on existing Auth API endpoints — **not** parallel full `/v1` `/v2` URL trees |
| D7-2 | Rolling window: **current + one previous** protocol generation on Auth API |
| D7-3 | Mobile sends **protocol/capability version**; unsupported → RFC 9457 with clear detail |
| D7-4 | Backend upgraded **before** mandating new capabilities |
| D7-5 | Legacy generation may have **reduced** security guarantees — **operator-visible** |
| D7-6 | **Single app line** in stores R1; compatibility via backend rolling window |
| D7-7 | Documented sunset policy (releases/months); execute removal only when earned (#14) |
| D7-8 | Light **Global Admin** read-only visibility of active generations |
| D7-9 | R1 = **vision + contract sketch** in product-docs; implementation when local-auth need is concrete |
| D7-10 | Prevent **silent total outage** when mobile and backend diverge |

## Links

- [`../../vision/product-orientation-notes.md`](../../vision/product-orientation-notes.md) (`V-2026-0008`)
- [`../ideas/I-2026-0025-auth-api-protocol-capability-versioning.md`](../ideas/I-2026-0025-auth-api-protocol-capability-versioning.md)
