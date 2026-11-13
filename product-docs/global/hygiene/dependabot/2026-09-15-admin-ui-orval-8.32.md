# Admin UI Orval pin — 8.25.0 → 8.32.0 (2026-09-15)

## Metadata

- **Date:** 2026-09-15
- **Operator:** Marc (+ agent, solo T4 review of Dependabot #548)
- **Lane:** lightweight dependency hygiene (codegen pin ladder)
- **Branch:** `hygiene/admin-ui-orval-8.32.0` (worktree; based on Dependabot `orval-8.32.0`)
- **Scope:** `ezkey-admin-ui` only (`ezkey_mobile` remains on Orval **8.25.0**)
- **Highest accepted tier:** T4 (Orval / OpenAPI generator hard escalator)

## Change

| Item | From | To |
|------|------|-----|
| `orval` exact pin | `8.25.0` | `8.32.0` |
| `orval.config.ts` | unchanged | verb-aware `query.version: 5` only (Option B) |
| App source | unchanged | no hook / query-key / mutator fixes required |
| Generated client | gitignored | regenerated locally with 8.32.0; Option B preserved |

Docs synced: `ezkey-admin-ui/AGENTS.md`, dependabot-curated skill example pin.

## Changelog skim (8.26–8.32, Admin UI-relevant)

Most of the jump is Zod / Angular / Vue Pinia / MCP / mock-faker. Ezkey Admin UI uses
`client: 'react-query'`, a custom fetch mutator, and `tags-split`. Notable items that *could*
have bitten us:

- **8.26:** named `*MutationVariables` types (additive; app does not import them).
- **8.27:** per-operation mutation key getters (additive).
- **8.28:** `shouldExportQueryKey` → `shouldExportKeys` rename (N/A — we never set it).
- **8.29:** opt-in `useSkipToken`; mutator return-type inference (our mutator still returns
  `Promise<T>` explicitly).
- **8.30–8.32:** several GHSA string-escape / mock / yaml fixes; mixed-type enum key prefixes
  (8.31). No mixed-enum prefix names appeared in generated models. `useDatesTransform` stays off.

Do **not** jump to 8.33.0 in this change set (Dependabot asked 8.32.0).

## Validation evidence

| Step | Result |
|------|--------|
| `npm install` / `npm ls orval` | OK / `orval@8.32.0` exact pin |
| `npm audit` | 0 vulnerabilities |
| `npm run generate:api` | Orval 8.32.0 OK |
| Spot-check GET → `useQuery` / POST → `useMutation` | Option B preserved (`useGetTenant` / `useCreateTenant`) |
| Query key factories | Still present (`getGetTenantQueryKey`, `getGetByIdQueryKey`) |
| `npm run lint` | 0 errors (1 pre-existing warning in `alert-detail.tsx`) |
| `npm run build` (`tsc -b` + Vite) | OK — generated 8.32 client typechecks with the app |
| `npm test` (Vitest) | **pre-existing runner failure** on both 8.25 (current workspace) and 8.32 (this worktree): `describe` cannot read `config`. Not a regression from this bump. |
| Playwright (`./scripts/run-ui-tests.sh`) | **5/5** passed (language switch, 404, Demo Device login, authenticated workflow, elective deny) |

## Notes

- Exact pin preserved (`8.32.0`, not `^8.32.0`).
- Dependabot #548 is lockfile + `package.json` only. Generated output stays gitignored.
- Mobile catch-up is a **separate** session (same Option B + `orval-dom-shim.d.ts` + committed
  generated client). No Android/Gradle required for an Orval-only ladder.
