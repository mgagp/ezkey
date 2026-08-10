# Admin UI npm audit hygiene — 2026-08-10

## Metadata

- **Date:** 2026-08-10
- **Operator:** Marc (+ agent)
- **Lane:** lightweight dependency hygiene (not a weekly `dependabot-curated` lot)
- **Branch:** `hygiene/admin-ui-npm-audit-2026-08-10`
- **Trigger:** `npm install` on `main` reported **7 high** severity advisories in `ezkey-admin-ui`

## Scope

Clear Admin UI `npm audit` highs without bumping the exact Orval pin (`8.22.0`).

| Package | Action |
|---------|--------|
| `react-router-dom` | Direct bump `^7.18.1` → `^7.18.2` (fixes nested `react-router` CSRF advisory in RSC mode) |
| `js-yaml` | Override `4.2.0` → `4.3.1` (keeps Orval on 4.x; avoids forced `orval@8.24.0`) |
| `brace-expansion` | Override → `5.0.9` |
| `fast-uri` | Override → `3.1.5` |
| `linkify-it` | Override → `5.0.2` (stay on 5.x for `markdown-it`) |
| `orval` | **Unchanged** exact pin `8.22.0` |

## Validation evidence

| Step | Result |
|------|--------|
| `npm audit` | **0** vulnerabilities |
| `npm run generate:api` (Orval 8.22.0) | OK; no generated-source churn |
| `npm test` (Vitest) | 11 files / 69 tests passed |
| `npm run build` | OK |
| Playwright (`./scripts/run-ui-tests.sh`) | 5/5 passed (smoke + device-backed login/workflow/deny) |
| Java `ezkey-tests` | n/a (Admin UI lockfile-only; Playwright is the functional surface) |

## Notes

- Prefer overrides over `npm audit fix --force` so Orval stays on the documented exact pin and does not enter the 8.23+ validation ladder.
- No application source changes; only `package.json` + `package-lock.json`.
