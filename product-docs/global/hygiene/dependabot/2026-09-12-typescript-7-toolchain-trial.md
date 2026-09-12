# TypeScript 7 toolchain trial — 2026-09-12

Experimental branch: `cursor/typescript-7-toolchain-trial-9de6`. **Not for merge to `main`.**

## Gate check (official)

| Claim | Status on 2026-09-12 |
|-------|----------------------|
| `typescript-eslint` latest stable | **8.70.0** (`latest`). No v9. Canary still peers `typescript >=4.8.4 <6.1.0`. |
| TypeScript 7.0 | **GA** (`latest` = **7.0.2**). Native Go `tsc`. |
| TypeScript 7.1 programmatic API | **Not GA.** Dist-tags: `beta` still 6.0.0-beta; `next` = `7.1.0-dev.*`. Iteration plan targets 7.1 stable ~2026-11-10. |
| `typescript-eslint` + TS 7 | **Not supported.** 8.70 throws if `ts.versionMajor >= 7`. Tracking: [typescript-eslint#10940](https://github.com/typescript-eslint/typescript-eslint/issues/10940). Native backend is still a draft PR. |

Wait-gate from the parked TypeScript 7 train (`#337`, `#498`, `#347`) is **still closed**.

## What this branch changed

- Admin UI: `typescript ~7.0.2`, `typescript-eslint ^8.70.0`; dropped `ignoreDeprecations` / `baseUrl`.
- JS SDK: `typescript ^7.0.2`; `moduleResolution` `node` → `bundler` (`node` / `node10` are TS5108 in 7.0).
- Mobile: `typescript 7.0.2`; Yarn `4.10.3` → **`4.18.0`** (4.10.3 hard-fails the builtin `compat/typescript` patch: missing `lib/_tsc.js`; fixed in Yarn 4.17.1).

## Results

| Surface | Command | Result |
|---------|---------|--------|
| Admin UI install | `npm install` | **FAIL** `ERESOLVE` — peer `typescript >=4.8.4 <6.1.0` |
| Admin UI install | `npm install --legacy-peer-deps` | OK (forced) |
| Admin UI | `tsc -b` | **PASS** |
| Admin UI | `vite build --mode test` | **PASS** |
| Admin UI | `vitest run` | **PASS** 12 files / 71 tests |
| Admin UI | `orval` (`generate:api`) | **PASS** (Node 22.14 vs Orval engine `>=22.18` warning only) |
| Admin UI | `eslint .` | **FAIL** exit 2 — `typescript-eslint does not support TS 7.0` |
| JS SDK | `npm install` + `tsc` | **PASS** after `moduleResolution: bundler` |
| Mobile install | Yarn 4.10.3 | **FAIL** `ENOENT .../typescript/lib/_tsc.js` |
| Mobile install | Yarn 4.18.0 | **PASS** (peer warnings unchanged) |
| Mobile | `yarn typecheck` | **PASS** (`tsc` 7.0.2) |
| Mobile | `yarn test --runInBand` | **PASS** 41 suites / 281 tests |
| Mobile | `yarn lint` | **FAIL** `TypeError: Cannot read properties of undefined (reading 'Cjs')` in `@typescript-eslint/typescript-estree` 8.59.1 |

## Verdict

Do **not** integrate on `main`. Compile / Vite / Vitest / Jest / Orval are viable on TypeScript 7.0.2 after small tsconfig and Yarn fixes. **ESLint is a hard stop** on Admin UI and mobile: the announced Node API gap is not resolved, and it does impact this repo.

Re-evaluate when TypeScript **7.1** ships a stable API **and** `typescript-eslint` peers include 7.x without a load-time throw.
