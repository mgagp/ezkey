# Ezkey Mobile Orval pin — 8.24.0 → 8.25.0 (2026-09-06)

## Metadata

- **Date:** 2026-09-06
- **Operator:** Marc (+ agent)
- **Lane:** lightweight dependency hygiene (codegen pin ladder)
- **Branch:** `hygiene/mobile-orval-8.25.0`
- **Scope:** `ezkey_mobile` only (Admin UI already on Orval **8.25.0**)

## Change

| Item | From | To |
|------|------|-----|
| `orval` exact pin | `8.24.0` | `8.25.0` |
| `orval.config.ts` | unchanged | verb-aware `query.version: 5` only (Option B) |
| App source | `resolveServerEnrollmentId` | accepts `EnrollmentIdSource` (`id` + optional `enrollmentId`) so Home metadata rows typecheck |
| Generated client | regenerated | lockstep with 8.25; Option B shapes preserved |
| `orval-dom-shim.d.ts` | absent | Fetch `HeadersInit` ambient type (RN `tsc` has no DOM lib) |

Docs synced: `ezkey_mobile/AGENTS.md`.

## Why the shim

Orval 8.25 emits a local `getHeaders` helper that types its argument as `HeadersInit`. Admin UI
already has browser DOM types, so that compile is clean there. React Native's TypeScript config
does not include the DOM lib. The shim is a type-only declaration; it does not enable
`useDatesTransform` and does not change runtime header handling (the mutator still normalizes
headers).

## Validation evidence

| Step | Result |
|------|--------|
| `yarn install` | OK (Orval 8.25.0 resolved) |
| `yarn generate:api` | Orval 8.25.0 OK |
| Spot-check generated POST → `useMutation` / GET → query | Option B shape preserved |
| `yarn typecheck` | OK after `EnrollmentIdSource` widen (Home metadata rows accepted) |
| `yarn test --runInBand` | OK — 41 suites / 280 tests passed; identity helper 10/10 |

No device/Gradle install (codegen-only risk; Orval is a `devDependency`).

## Notes

- Sibling catch-up after Admin UI Orval 8.25.
- Exact pin preserved (`8.25.0`, not `^8.25.0`).
- 8.25 changelog is mostly perf, Zod/Angular/Solid, and opt-in `useDatesTransform`. That option
  stays off so ISO date strings remain strings.
