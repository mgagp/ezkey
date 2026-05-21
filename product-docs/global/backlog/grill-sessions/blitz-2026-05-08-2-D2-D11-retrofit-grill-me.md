# Grill Me — Blitz 2026-05-08-2, D2 + D11 (retrofit cluster — pinning + Melody)

## Session control

| Field | Value |
|-------|--------|
| **Blitz source** | `product-docs/global/backlog/blitz-archive/blitz-2026-05-08-2.md` (D2, D11) |
| **Vision** | `V-2026-0006`, `V-2026-0009` |
| **Retrofit** | `R-2026-0001`, `R-2026-0002` |
| **Status** | `complete` |
| **Date** | `2026-05-19` |

## Settled decisions (operator-confirmed)

### D2 — Mobile SPKI pinning (`V-2026-0006`, `R-2026-0001`)

| ID | Decision |
|----|----------|
| D2D11-1 | **SPKI pin**; **TOFU at enrollment** |
| D2D11-2 | Native pinning in normal operation |
| D2D11-3 | **Auth API recovery** endpoint (unpinned narrow path) + device proof |
| D2D11-4 | Recovery challenge **optional** if proof + backend signature sufficient |
| D2D11-5 | **Audit** pin transitions; compliance batch **later** (not R1 blocker) |
| D2D11-6 | **Android-first** |
| D2D11-7 | Complete **`R-2026-0001` retrofit** before implementation `I-*` |

### D11 — Java Melody (`V-2026-0009`, `R-2026-0002`)

| ID | Decision |
|----|----------|
| D2D11-8 | **Opt-in** clean-start / compose (`--with-java-melody` style) — **not** default enabled (grill settles plan vs verbatim divergence) |
| D2D11-9 | admin-api, auth-api, integration-api; **exclude crypto-api** |
| D2D11-10 | Standalone collector + persistent volume |
| D2D11-11 | **No Caddy** for Melody R1 — dedicated/internal port |
| D2D11-12 | Complete **`R-2026-0002` retrofit** before implementation `I-*` |
| D2D11-13 | **DX / troubleshooting** tool — not production APM substitute |

## Blitz 2 grill lane

**Closed** — all items D1–D11 grilled (2026-05-19).

## Links

- [`../../legacy-retrofit/R-2026-0001-mobile-certificate-pinning-spki.md`](../../legacy-retrofit/R-2026-0001-mobile-certificate-pinning-spki.md)
- [`../../legacy-retrofit/R-2026-0002-java-melody-collector.md`](../../legacy-retrofit/R-2026-0002-java-melody-collector.md)
