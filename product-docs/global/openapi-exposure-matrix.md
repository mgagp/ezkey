# OpenAPI Exposure Matrix

## Purpose

Canonical environment-level posture for how Ezkey exposes raw OpenAPI artifacts (`/api-docs`), embedded Springdoc UIs, and the curated public API portal across its main backend surfaces.

This document turns `V-2026-0014` and `I-2026-0026` into an explicit decision baseline before any tracer-bullet implementation work.

## Working posture summary

- Raw Springdoc endpoints are **operator/developer tooling**, not the target long-term public surface for production-capable deployments.
- The public API portal on `ezkey.org` is **near-term**, **read-only**, and **curated**.
- The **community instance** (`ezkey.online`, historically EXP1) is treated as **prod-like** for raw OpenAPI exposure posture.
- `Integration API` belongs to the production-capable documentation group.
- `Crypto API` remains outside the normal public documentation posture.

## Surface groups

| Group | APIs | Notes |
|------|------|-------|
| **Production-capable** | `Admin API`, `Auth API`, optional `Integration API` | Public/prod-capable group for portal and exposure policy |
| **Test/internal only** | `Crypto API` | Keep outside the default public portal posture |

## Exposure matrix

| Environment posture | Admin API `/api-docs` | Admin Swagger UI | Auth API `/api-docs` | Auth Swagger UI | Integration API `/api-docs` | Integration Swagger UI | Crypto API docs | Curated public portal | Notes |
|---------------------|-----------------------|------------------|----------------------|-----------------|-----------------------------|------------------------|-----------------|-----------------------|-------|
| **Local / dev** | `enabled` | `enabled` | `enabled` | `enabled` | `enabled` when deployed | `enabled` when deployed | `enabled` when explicitly used | `not required` | Developer tooling first |
| **Internal shared** | `controlled` | `controlled` | `controlled` | `controlled` | `controlled` when deployed | `controlled` when deployed | `internal only` | `optional internal/public preview` | Internal teams may still need direct tooling |
| **Community (`ezkey.online`)** | `disabled publicly` | `disabled publicly` | `disabled publicly` | `disabled publicly` | `disabled publicly` when deployed | `disabled publicly` when deployed | `not exposed` | `enabled` | Community / evaluator access should converge toward the curated portal, not raw host tooling (historical label: EXP1) |
| **Public production-like** | `disabled publicly` | `disabled publicly` | `disabled publicly` | `disabled publicly` | `disabled publicly` when deployed | `disabled publicly` when deployed | `not exposed` | `enabled` | Target public posture |
| **Test-only / lab** | `optional` | `optional` | `optional` | `optional` | `optional` | `optional` | `enabled` | `not required` | Includes `Crypto API` and other lab-only tooling |

## Interpretation notes

- **`enabled`** means directly reachable in the environment as ordinary tooling.
- **`controlled`** means reachable only through deliberate internal controls such as private networking, VPN, authentication, or equivalent operator-only paths.
- **`disabled publicly`** means the API host should not expose those raw docs on its public runtime hostname, even if the same spec is published elsewhere in curated form.
- **`enabled`** in the curated portal column refers to the `ezkey.org` public documentation portal, not to per-service Springdoc UI.

## Immediate implications

1. The community host (`ezkey.online`, historically EXP1) should no longer be treated as a tolerated public exception for raw Swagger/UI exposure.
2. The public portal should cover `Admin API`, `Auth API`, and `Integration API` in a way that tolerates deployments where `Integration API` is absent.
3. `Crypto API` should stay out of the public portal and be documented separately for test/internal contexts only.
4. The next recommendation slice must compare portal renderers against this matrix instead of comparing them in the abstract.

## Links

- Vision: [`vision/product-orientation-notes.md`](vision/product-orientation-notes.md) (`V-2026-0014`)
- Backlog: [`backlog/ideas/I-2026-0026-openapi-exposure-and-api-portal-posture.md`](backlog/ideas/I-2026-0026-openapi-exposure-and-api-portal-posture.md)
- Grill: [`backlog/grill-sessions/2026-05-21-openapi-exposure-api-portal-grill-me.md`](backlog/grill-sessions/2026-05-21-openapi-exposure-api-portal-grill-me.md)
- Hosting context: [`../../docs/cloudflare/ezkey-org-site.md`](../../docs/cloudflare/ezkey-org-site.md), [`../../sites/ezkey-org/AGENTS.md`](../../sites/ezkey-org/AGENTS.md)
