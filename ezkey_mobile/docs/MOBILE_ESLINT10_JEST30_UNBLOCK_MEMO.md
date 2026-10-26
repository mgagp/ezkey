# Mobile Memo - ESLint 10 and Jest 30 Unblock Conditions

Date: 2026-06-18 (updated 2026-10-26)
Scope: `ezkey_mobile`

## Purpose

Keep dependency hygiene disciplined while avoiding premature major upgrades that are not yet supported by the current React Native lint stack. Record trial evidence so later agents do not re-learn the same blockers.

## Current baseline (validated 2026-10-26)

- React Native: `0.87.1`
- `@react-native/eslint-config`: `0.87.1` (peer `eslint ^8 || ^9` only)
- `@react-native/jest-preset`: `0.87.1` (still declares Jest **29** transitive deps)
- ESLint: `9.39.4` (ESLint **10** held — see trial below)
- Jest: `30.5.2` + `@types/jest` `30.0.0` (landed 2026-10-26; Dependabot `#653`)

## 2026-10-26 trial — ESLint 10.13.0 (`#1007`) — STOP

Dependabot `#1007` fails CI on `mobile / js-validate` with:

`TypeError: … eslint-comments/no-aggregating-enable: context.getSourceCode is not a function`

Isolated trial (hygiene branch, no workflow edits, frozen PRs `#878`/`#929`/`#870` untouched):

| Step | Result |
|------|--------|
| Alias `eslint-plugin-eslint-comments` → `@eslint-community/eslint-plugin-eslint-comments@4.8.1` via Yarn `resolutions` | Fixes the eslint-comments crash (community plugin supports ESLint 10). |
| Pin `settings.react.version` to `19.3.0` (skip `detect`) | Clears first `eslint-plugin-react` crash (`context.getFilename` removed in ESLint 10). |
| Next failure | `react-native/no-inline-styles`: `context.getSourceCode is not a function` from `eslint-plugin-react-native@5.0.0` (peer still `eslint ^3…^9` only). |

**Key plugins not ESLint-10-ready on this stack:**

1. **`eslint-plugin-react@7.37.5`** — maintainer: not compatible with ESLint 10 yet; open PR jsx-eslint/eslint-plugin-react#4022 not merged as of 2026-10-26. Peers stop at `^9.7`.
2. **`eslint-plugin-react-native@5.0.0`** — peers stop at `^9`; still calls removed `context.getSourceCode`. Latest npm release remains 5.0.0.

Per operator rule (adapt config only when clean; **stop rather than force** when a key plugin is incompatible): do **not** ship ESLint 10 by disabling RN react/react-native rules or wrapping the whole preset in `@eslint/compat`. Keep ESLint **9.39.4** until those plugins (or a newer `@react-native/eslint-config` that peers ESLint 10) ship compatible releases. Do **not** jump `@react-native/eslint-config` alone while RN-core is frozen (`#878`).

## 2026-10-26 trial — Jest 30.5.2 (`#653`) — LANDED

Earlier memo expected `clearMocksOnScope is not a function`. Re-trial on RN **0.87.1** + Yarn 4 with `jest@30.5.2` / `@types/jest@30.0.0`:

- `yarn test --runInBand` → **42 suites / 323 tests** green (no test rewrites).
- Preset still lists Jest 29 packages in its `dependencies`, but the resolved `jest-mock` / `jest-runtime` are 30.5.2 and the suite runs cleanly on this workspace.

Ship Jest 30 as tooling-only hygiene. Residual risk for Justin (device): none expected for APK/runtime (Jest is `devDependency` only); still confirm a debug install after merge if a cold agent later couples this with native/RN bumps.

## Unblock gates for ESLint 10 (all must pass)

1. `eslint-plugin-react` publishes an ESLint-10-compatible release (or `@react-native/eslint-config` switches away from the broken APIs).
2. `eslint-plugin-react-native` publishes an ESLint-10-compatible release (or RN config drops / replaces the rule that calls `getSourceCode`).
3. `@react-native/eslint-config` peer range includes ESLint 10 (or we document an accepted override with green `yarn lint`).
4. Trial: `yarn lint` + `yarn typecheck` + `yarn test --runInBand` without disabling core RN react rules.

## Jest 30 note (post-land)

`deps:monitor` may still classify Jest 30 as gated from the preset’s declared `^29` deps. Prefer runtime evidence (`yarn test`) over the gate alone when re-evaluating; update the monitor heuristic when the preset’s declared deps move to 30.

## Validation commands

```bash
yarn lint
yarn typecheck
yarn test --runInBand
yarn license:app-data   # after lockfile changes — keeps mobile / js-validate green
```

## Known Windows caveat during Android validation

- In this workspace path, `gradlew installDebug` can fail with CMake/Ninja path length (`MAX_PATH`) for native modules.
- Prefer a short-root workspace path for Android native validation runs.
