# Grill Me — 2026-06-18 (Demo Device QR `authUrl` parity)

## Session control

| Field | Value |
|-------|--------|
| **Backlog** | `I-2026-06-18-demo-device-qr-auth-url-parity` |
| **Tracer bullet** | `TB-2026-06-18-demo-device-qr-auth-url-parity` |
| **Lane** | `D` (post-delivery / parity gap) |
| **Status** | `complete` |
| **Date** | `2026-06-18` |
| **Captured by** | Marc |

## Context (observed)

- Standalone `ezkey-demo-device` Docker default: `EZKEY_AUTH_API_URL=https://exp1-auth-api.ezkey.org`
  (port `3080` on host).
- Clean-start monorepo demo device: `http://auth-api:8080` (port `8083`).
- Enrollment created in **local** Admin UI; bind via standalone failed (HTTP 400 to Exp1); same
  enrollment succeeded on clean-start demo device.
- Java `src/` identical between monorepo and standalone; QR JS already parses `authUrl` but does not
  act on it.

## Settled decisions (operator-confirmed 2026-06-18)

| ID | Decision |
|----|----------|
| G1 | **No new `V-*`** — mobile QR-first is the existing product direction |
| G2 | **QR `authUrl` wins** automatically for that enrollment when present and server-valid; keep informational mismatch banner (no blocking modal) |
| G3 | **Manual entry (no QR URL)** keeps configured `ezkey.auth.api.url` only |
| G4 | **Persist** effective base in store `Record.enrollmentUrl` |
| G5 | **One TB** covers bind + verify + pending + respond routing |
| G6 | **Server-side URL validation** mirrors mobile/JS rules |
| G7 | Mismatch banner stays **informational**; QR URL still used |
| G8 | **Monorepo first** (`ezkey/ezkey-demo-device/`), then **sync** public `mgagp/ezkey-demo-device`, then **maintainer standalone test** on `:3080` before closeout |
| G9 | **No Playwright**; manual matrix + unit tests |
| G10 | **Invalid QR `authUrl`:** hard error with clear message — no silent fallback to config |
| G11 | **Priority:** `P2` — hygiene dev-tooling, next reasonable slot after current PR work |

## Open questions (operator)

_All resolved._

## Outcome

Grill complete. Slice closed 2026-06-18: `I-*` and `TB-*` → `done`. See TB § Closeout.

## Links

- [`../ideas/I-2026-06-18-demo-device-qr-auth-url-parity.md`](../ideas/I-2026-06-18-demo-device-qr-auth-url-parity.md)
- [`../TB-2026-06-18-demo-device-qr-auth-url-parity.md`](../TB-2026-06-18-demo-device-qr-auth-url-parity.md)
