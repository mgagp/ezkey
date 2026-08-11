# Admin UI Orval pin — 8.22.0 → 8.24.0 (2026-08-10)

## Metadata

- **Date:** 2026-08-10
- **Operator:** Marc (+ agent)
- **Lane:** lightweight dependency hygiene (codegen pin ladder)
- **Branch:** `hygiene/admin-ui-orval-8.24.0`
- **Scope:** `ezkey-admin-ui` only (`ezkey_mobile` remains on Orval **8.22.0**)

## Change

| Item | From | To |
|------|------|-----|
| `orval` exact pin | `8.22.0` | `8.24.0` |
| `orval.config.ts` | unchanged | verb-aware `query.version: 5` only (Option B) |
| App source | unchanged | no hook / query-key fixes required |

Docs synced: `ezkey-admin-ui/AGENTS.md`, dependabot-curated skill example pin.

## Validation evidence

| Step | Result |
|------|--------|
| `npm install` / `npm audit` | OK / 0 vulnerabilities |
| `npm run generate:api` | Orval 8.24.0 OK |
| Spot-check generated query keys / GET hooks | Present (Option B shape preserved) |
| `npm test` | 69/69 passed |
| `npm run build` | OK |
| `npm run lint` | 0 errors (1 pre-existing warning in `alert-detail.tsx`) |
| Playwright (`./scripts/run-ui-tests.sh`) | 5/5 passed |

## Notes

- Not driven by remaining audit gaps (cleared in #446 via overrides); proactive pin catch-up through 8.23 + 8.24.
- No `npm audit fix --force`; exact pin preserved (`8.24.0`, not `^8.24.0`).
