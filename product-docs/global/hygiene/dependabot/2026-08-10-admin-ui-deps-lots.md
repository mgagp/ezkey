# Admin UI dependency lots A+B — 2026-08-10

## Metadata

- **Date:** 2026-08-10
- **Operator:** Marc (+ agent)
- **Lane:** lightweight dependency hygiene
- **Branch:** `hygiene/admin-ui-deps-lots-2026-08-10`
- **Scope:** `ezkey-admin-ui` only (mobile deferred to a dedicated workspace session)

## Lots

### Lot A — low-risk caret / tooling

| Package | To |
|---------|-----|
| `vite` | `^8.2.1` |
| `eslint` | `^10.8.1` |
| `eslint-plugin-react-refresh` | `^0.5.4` |
| `globals` | `^17.9.0` |
| `@types/node` | `^26.2.0` |
| `@types/react-dom` | `^19.2.4` |
| `react-i18next` | `^17.0.11` |
| `react-hook-form` | `^7.85.0` |
| `@hookform/resolvers` | `^5.7.1` |
| `@playwright/test` | `^1.62.1` |

### Lot B — medium (included)

| Package | To | Notes |
|---------|-----|--------|
| `lucide-react` | `^1.31.0` | Icon minor train; build/Playwright green |
| `typescript-eslint` | `^8.67.0` | Without TypeScript 7 |

### Explicitly out of scope

| Item | Why |
|------|-----|
| TypeScript 7 (`~6.0.3` kept) | Already `deferred:later-train` (#342) |
| `ezkey_mobile` Orval / deps | Separate maintainer workspace session |
| Orval pin | Already at `8.24.0` via #447 |

## Validation evidence

| Step | Result |
|------|--------|
| `npm install` / `npm audit` | OK / 0 vulnerabilities |
| `npm outdated` | Only `typescript` 6 → 7 remains (intentional) |
| `npm run generate:api` | OK (Orval 8.24.0) |
| `npm test` | 69/69 |
| `npm run build` | OK (Vite 8.2.1) |
| `npm run lint` | 0 errors (1 pre-existing warning in `alert-detail.tsx`) |
| Playwright | 5/5 (Chromium download for Playwright 1.62.1) |

## Notes

No application source changes. Exact pins preserved for `orval@8.24.0`, `react@19.2.8`, `react-dom@19.2.8`, `typescript@~6.0.3`.
