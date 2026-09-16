# Ezkey Mobile Orval pin — 8.25.0 → 8.33.0 (2026-09-16)

## Metadata

- **Date:** 2026-09-16
- **Operator:** Fred (+ agent)
- **Lane:** lightweight dependency hygiene (codegen pin ladder)
- **Branch:** `cursor/mobile-orval-8.33.0-c159`
- **Scope:** `ezkey_mobile` only (Admin UI already past Orval **8.25**; catch-up to **8.33.0**)
- **GitHub write identity:** `GH_TOKEN` as `mgagp` (operator PAT); agent PR via ManagePullRequest

## Change

| Item | From | To |
|------|------|-----|
| `orval` exact pin | `8.25.0` | `8.33.0` |
| `orval.config.ts` | unchanged | verb-aware `query.version: 5` only (Option B) |
| Generated client | regenerated | lockstep with 8.33; Option B shapes preserved |
| `orval-dom-shim.d.ts` | present (`HeadersInit`) | removed — 8.33 emits `RequestInit['headers']`; RN `tsc` green without shim |
| App source | unchanged | no EnrollmentIdSource-class widen needed |

Docs synced: `ezkey_mobile/AGENTS.md`.

## Why remove the shim

Orval 8.25 emitted a local `getHeaders` helper typed with `HeadersInit`, which RN `tsc` (no DOM lib)
could not resolve. Orval 8.33 types that helper as `NonNullable<RequestInit['headers']>` instead.
`RequestInit` is already available via the RN/TypeScript ambient set, so the Fetch `HeadersInit`
shim is unused. Confirmed: `yarn typecheck` green with the file deleted.

`useDatesTransform` stays off so ISO date strings remain strings.

## Validation evidence

| Step | Result |
|------|--------|
| `corepack yarn add -D orval@8.33.0 --exact` | OK (Orval 8.33.0 resolved) |
| `yarn generate:api` | Orval 8.33.0 OK |
| Spot-check generated POST → `useMutation` / GET → query | Option B shape preserved (`useBind`/`useVerify`/`usePending`/`useRespond` → mutation; `useGetInstanceInfo` → query) |
| `yarn typecheck` | OK (with and without former shim) |
| `yarn test --runInBand` | OK — 41 suites / 281 tests passed |
| `yarn lint` | OK — 0 errors (3 pre-existing `no-void` warnings in `HomeScreen.tsx`, untouched) |

No device/Gradle install (codegen-only risk; Orval is a `devDependency`). No ESLint 10 / Jest 30 /
TypeScript 7 / RN 0.87 bumps in this lot.

## Notes

- Sibling catch-up after Admin UI moved past Orval 8.25 (Admin UI pin at time of write: **8.32.0**).
- Exact pin preserved (`8.33.0`, not `^8.33.0`).
- Ceremony: `ezkey_mobile/docs/MOBILE_DEPENDENCY_HYGIENE_CEREMONY.md`.
