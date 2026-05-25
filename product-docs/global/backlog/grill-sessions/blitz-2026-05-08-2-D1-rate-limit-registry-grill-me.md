# Grill Me — Blitz 2026-05-08-2, D1 (rate-limit baseline + controllers registry)

## Session control

| Field | Value |
|-------|--------|
| **Blitz source** | `product-docs/global/backlog/blitz-archive/blitz-2026-05-08-2.md` (D1a + D1b) |
| **Backlog** | `I-2026-0008`, `I-2026-0009` |
| **Canonical output** | [`../../api-controllers-registry.md`](../../api-controllers-registry.md) |
| **Status** | `complete` |
| **Date** | `2026-05-19` |

## Settled decisions (operator-confirmed)

### Work order (D1-1)

1. **`I-2026-0009`** — author controllers registry (canonical doc).
2. **`I-2026-0008`** — rate-limit baseline analysis **using registry** columns.

### Registry (D1-2 – D1-4, D1-8, D1-9)

- **Home:** `product-docs/global/api-controllers-registry.md`.
- **Schema (minimal):** module, base path, purpose (1 line), audience (admin session / API key / device / public), volume tier (A/B), sensitivity, rate-limit note, link to component flow or OpenAPI.
- **Modules:** admin-api, auth-api, integration-api, crypto-api.
- **Curation:** manual first; optional skill = **stub diff** on controller inventory only — **no** whole-repo token scan.
- **Role:** bounded context for grills, tracer bullets, rate limits, pagination, versioning — **not** a second OpenAPI.

### Rate-limit baseline (D1-5 – D1-7)

- Model: **documented baseline + per-action overrides** where needed (Integration throughput, sensitive admin ops, device auth flows).
- **R1:** policy + CONFIGURATION.md alignment **first**; code changes **only if** inventory proves simplification wins (#1).
- If no coherent single baseline: **record honestly** in policy/registry — do not force uniformity.

## Post-decision (2026-05-24)

Living controllers registry **downscoped** — [`../../../methodology/decisions/2026-05-24-controllers-registry-downscope.md`](../../../methodology/decisions/2026-05-24-controllers-registry-downscope.md). `I-2026-0009` dropped; semantic inventory absorbed into `I-2026-0008` policy output. Grill decisions above remain historical context.

## Links

- [`../blitz-archive/blitz-2026-05-08-2.md`](../blitz-archive/blitz-2026-05-08-2.md)
- [`../ideas/I-2026-0008-rate-limit-baseline-analysis.md`](../ideas/I-2026-0008-rate-limit-baseline-analysis.md)
- [`../ideas/I-2026-0009-global-controllers-registry.md`](../ideas/I-2026-0009-global-controllers-registry.md) (`dropped`)
- [`../../api-controllers-registry.md`](../../api-controllers-registry.md) (archived stub)
- [`../../../methodology/decisions/2026-05-24-controllers-registry-downscope.md`](../../../methodology/decisions/2026-05-24-controllers-registry-downscope.md)
