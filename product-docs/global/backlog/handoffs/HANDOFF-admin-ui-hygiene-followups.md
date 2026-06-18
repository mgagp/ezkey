# Handoff — Admin UI hygiene follow-ups (post #225)

Use this prompt to start a **new Cursor session** on a **separate branch** after PR `hygiene/admin-ui-doctor-curated` is merged.

---

## Context

We completed a React Doctor curated hygiene campaign on Admin UI ([#225](https://github.com/mgagp/ezkey/issues/225)), merged via PR `hygiene/admin-ui-doctor-curated`. Five commits:

1. `doctor-curated.mjs` CLI flags for react-doctor 0.5.x + `Button` default `type="button"` + Playwright Chromium install in `run-ui-tests.sh`
2. `audit-logs.tsx` — integrity panel auto chain-check uses stable `effectiveRange` on expand
3. `dashboard.tsx` — audit-chain alerts use `<Link>` + `data-testid="dashboard-audit-chain-alert-link"`
4. `utils.ts` / `countries.ts` — cached `Intl` formatters
5. `auth-context`, `toast-context`, `demo-mode-context` — memoized provider values

Validation done: build OK, Playwright `@smoke` + device-backed green (with `docker-test` profile), manual audit-logs integrity OK.

**Branch for this follow-up:** create e.g. `hygiene/admin-ui-playwright-orval` (or split into two branches if you prefer).

---

## Scope A — Playwright / stack posture (small, same caliber as #225)

**Goal:** Reduce friction when operators run device-backed browser tests without a full `clean-start`.

1. **Document** in `ezkey-admin-ui/README.md` (Browser Tests section):
   - Device-backed tests need Demo Device + Admin API reachable
   - Auth API must run with `SPRING_PROFILES_ACTIVE=docker,docker-test` (or use `./ezkey-tests/clean-start.sh` which sets this)
   - Partial Docker restart without `docker-test` → 429 on `POST /auth-attempts/pending` → Playwright fails after ~55s

2. **Optional quick win** in `e2e/support/auth-flow.ts`:
   - Increase `DEMO_DEVICE_PENDING_INTERVAL_MS` from 1500 to ~6000 as a safety net when rate limit is accidentally on (prod-like `docker` profile allows ~10 pending/min per enrollment)
   - Keep poll loop + reload pattern; do not rewrite harness

3. **Re-run** `./scripts/run-ui-tests.sh` after changes; no new scenarios unless trivial.

**Out of scope:** new Playwright coverage, MCP browser tooling, CI gates.

---

## Scope B — Orval 8.18 Admin UI (separate hygiene branch recommended)

**Goal:** Bump Admin UI Orval from pinned **8.17.0** to **8.18.x**, mirroring mobile which already upgraded without pain.

**Read first:**
- `ezkey-admin-ui/AGENTS.md` — Orval 8.17 pin, verb-aware defaults (`query: { version: 5 }` only; no global `useQuery`/`useMutation`)
- `product-docs/global/backlog/TB-2026-05-28-admin-ui-orval-upgrade.md` — prior upgrade ladder
- Mobile reference: branch/commits on `main` for orval 8.18 (ezkey_mobile)

**Expected steps:**
1. Branch `hygiene/admin-ui-orval-8.18`
2. Bump `orval` in `ezkey-admin-ui/package.json` (exact pin, e.g. `8.18.0` or latest 8.18.x)
3. `npm install` in `ezkey-admin-ui`
4. `npm run generate:api` — review generated client diff
5. `npm run build` + `npm run lint`
6. `./scripts/run-ui-tests.sh` if generated hooks changed login/list paths
7. Commit + PR; GitHub issue optional (lane:c hygiene)

**Watchouts:**
- Orval 8.10+ global `useQuery`/`useMutation` breaks hook shapes — do not add to `orval.config.ts`
- Treat 8.18+ as new validation ladder per AGENTS.md
- Do not hand-edit `specs/**` or generated client except via regen

---

## Scope C — React Doctor re-scan (optional, after A or B)

Run `npm run doctor:curated` and confirm reduced counts on:
- `button-has-type`
- `no-adjust-state-on-prop-change` (audit-logs)
- `click-events-have-key-events` / `no-noninteractive-element-interactions` (dashboard)
- `js-hoist-intl`
- `jsx-no-constructed-context-values`

Defer: `prefer-html-dialog`, `no-giant-component`, `forwardRef` cleanup.

---

## Repo conventions (reminders)

- Git commits via Git Bash: `"C:\Program Files\Git\bin\bash.exe" -lc './scripts/git-commit.sh -m "..."'`
- Hygiene lane: commit/PR + targeted docs; no mandatory new `I-*`/`TB-*` unless scope grows
- Playwright: `scripts/run-ui-tests.sh` auto-runs `npm run test:browser:install`
- Device-backed tests: Demo Device `:8083`, Admin UI dev `:4173` (or `PLAYWRIGHT_SKIP_WEBSERVER=1`)

---

## Suggested session opening message (copy-paste)

```
Continue Admin UI hygiene follow-ups after merged PR hygiene/admin-ui-doctor-curated (#225).

Start with Scope A (README docker-test + optional auth-flow poll interval), then Scope B (Orval 8.18 bump on a dedicated branch). One commit per logical step. Run build and Playwright smoke after each scope.

Read ezkey-admin-ui/AGENTS.md and the handoff notes above. Do not expand into dialog migration or broad refactors.
```
