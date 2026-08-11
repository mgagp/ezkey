# Ezkey Mobile Orval pin — 8.22.0 → 8.24.0 (2026-08-10)

## Metadata

- **Date:** 2026-08-10
- **Operator:** Marc (+ agent)
- **Lane:** lightweight dependency hygiene (codegen pin ladder)
- **Branch:** `hygiene/mobile-orval-8.24.0`
- **Scope:** `ezkey_mobile` only (Admin UI already on Orval **8.24.0**)

## Change

| Item | From | To |
|------|------|-----|
| `orval` exact pin | `8.22.0` | `8.24.0` |
| `orval.config.ts` | unchanged | verb-aware `query.version: 5` only (Option B) |
| App source | unchanged | no facade / hook fixes required |
| Generated client | regenerated | banner + lockstep with 8.24; Option B shapes preserved |

Docs synced: `ezkey_mobile/AGENTS.md`.

## Validation evidence

| Step | Result |
|------|--------|
| `yarn install` | OK (Orval 8.24.0 resolved) |
| `yarn generate:api` | Orval 8.24.0 OK |
| Spot-check generated POST → `useMutation` / GET → query | Option B shape preserved |
| `yarn validate:ci` (lint + typecheck + test) | OK — 33 suites / 241 tests passed |

No device/Gradle install (codegen-only risk; Orval is a devDependency).

## Notes

- Sibling catch-up after Admin UI Orval 8.24 (`2026-08-10-admin-ui-orval-8.24.md`).
- Exact pin preserved (`8.24.0`, not `^8.24.0`).
