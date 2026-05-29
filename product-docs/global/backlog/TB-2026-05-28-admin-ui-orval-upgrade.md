# Tracer Bullet Brief — `TB-2026-05-28` Admin UI Orval upgrade (8.5 → 8.13)

## Metadata

- **ID:** `TB-2026-05-28-admin-ui-orval-upgrade`
- **Status:** `draft`
- **Posture:** `multi-pass` (one PR per ladder step; this TB tracks the full program)
- **Related idea:** `I-2026-05-28-admin-ui-orval-upgrade`
- **Lane:** `A`
- **GitHub issue:** `#175`
- **Created at:** `2026-05-28`
- **Updated at:** `2026-05-28`

## Objective

Migrate `ezkey-admin-ui` from Orval **8.5.2** (pinned baseline after the 2026-05-28 dependency
audit) to Orval **8.13.0** (latest stable at audit time), using a **version ladder with explicit
validation gates**, so that:

1. `npm run generate:api` produces a client compatible with application code;
2. `npm run build` (Docker and Cloudflare paths included) stays green after every merged step;
3. query invalidation and detail-page refresh behaviour is preserved;
4. the team has a repeatable playbook for future Orval bumps.

## Why this is not a “minor dependency” chore

During the 2026-05-28 Admin UI dependency audit, all routine npm updates succeeded. Orval did not.

**Observed at Orval 8.12.3 / 8.13.0** (after `npm run generate:api`):

| Symptom | Example | App expectation (8.5.x) |
|---------|---------|-------------------------|
| Missing query-key exports | `getGetAdminByIdQueryKey` absent | Used in `admins.tsx`, `use-expandable-related-details.ts` |
| URL helper instead | `getGetAdminByIdUrl` generated | Invalidations use QueryKey factories |
| GET → mutation hook | `useGetAdminById` returns `UseMutationResult` | Detail reads use `useQuery` + `getAdminById()` |
| POST/PUT → query hook | `useDeactivateAdmin` returns `UseQueryResult` | Pages call `.mutate()` |
| Discriminated responses | `getTenantResponseSuccess \| getTenantResponseError` | Pages read `.timezone` on DTO directly |
| Hook option shape change | `useGetTenant(42, { mutation: … })` invalid | `{ id }` mutation variables |

**Scale:** ~15 application source files import generated hooks or QueryKey factories; `admins.tsx`
and `enrollment-detail.tsx` are the heaviest consumers.

**Root cause hypothesis (to confirm at 8.10 spike):** global config enables both
`useQuery: true` and `useMutation: true`, and Orval **8.10+** changed query-key namespacing and
verb-level hook assignment (see Orval releases **8.10.0**, **8.11.0**).

## Boundaries in scope

- `ezkey-admin-ui/package.json` — explicit Orval version pin per step
- `ezkey-admin-ui/package-lock.json`
- `ezkey-admin-ui/orval.config.ts` — adjust at checkpoints if needed
- `ezkey-admin-ui/src/**` — application fixes driven by codegen changes
- `ezkey-admin-ui/AGENTS.md`, `README.md` — Orval pin policy and validation commands
- Regenerated output (`src/generated/admin-api/**`) — validated locally every step; not committed

## Out of scope

- Editing `specs/admin-api/openapi-spec.json` except via the normal spec-refresh workflow in a
  separate change set
- Manual edits under `src/generated/**` committed to git
- Admin API Java controller changes (unless spec validation fails — then separate ticket)
- Playwright scenario authoring beyond the TSP gates

---

## Version ladder (npm stable releases at 2026-05-28)

Latest stable: **8.13.0**

| Minor line | Latest patch | Releases in line | Expected risk |
|------------|--------------|------------------|---------------|
| 8.5.x | **8.5.3** | 4 | Very low — process validation |
| 8.6.x | **8.6.2** | 3 | Low |
| 8.7.x | **8.7.0** | 1 | Low |
| 8.8.x | **8.8.1** | 2 | Low |
| 8.9.x | **8.9.1** | 2 | Low–medium |
| 8.10.x | **8.10.0** | 1 | **High checkpoint** |
| 8.11.x | **8.11.0** | 1 | **High checkpoint** |
| 8.12.x | **8.12.3** | 3 | Medium (mock breaking changes — N/A to us) |
| 8.13.x | **8.13.0** | 1 | Low if 8.10/8.11 done |

### Ladder execution order (recommended PR sequence)

Each row = **one PR**, **one explicit Orval version** in `package.json`, merge before starting the
next.

| Step | PR target | Type | Notes |
|------|-----------|------|-------|
| **0** | *(no version bump)* | Baseline | Inventory only — optional doc PR |
| **1** | `8.5.3` | Patch | Validate migration process |
| **2** | `8.6.0` | Minor floor | Fix breaks if any |
| **3** | `8.6.2` | Patch | Stabilize 8.6 line |
| **4** | `8.7.0` | Minor | |
| **5** | `8.8.0` → `8.8.1` | Minor + patch | Can split or combine if 8.8.0 clean |
| **6** | `8.9.0` → `8.9.1` | Minor + patch | Watch type/schema output |
| **7** | `8.10.0` | **Checkpoint** | Query keys / hook semantics — expect app changes |
| **8** | `8.11.0` | **Checkpoint** | GET vs mutation overrides |
| **9** | `8.12.0` → `8.12.3` | Minor + patches | Consolidate |
| **10** | `8.13.0` | Final | Relax pin policy; enable Dependabot |

**Pragmatic shortcut (operator decision after step 6):** if steps 2–6 are uniformly clean, keep
the **one-minor-per-PR** rule but skip intermediate patch-only PRs when `npm outdated` shows no
wanted delta within the line. Never skip **8.10** or **8.11**.

---

## Phase 0 — Baseline inventory (before step 1)

### Checklist

- [ ] Confirm `package.json` pins Orval at `~8.5.2` (post dependency audit).
- [ ] Record current lock resolution: `npm ls orval`.
- [ ] Run baseline validation on clean tree:
  ```bash
  cd ezkey-admin-ui
  npm ci
  npm run generate:api
  npm run lint
  npm run build
  npm test
  ```
- [ ] Capture baseline: Vitest count, build exit code, wall time (informational).
- [ ] List high-touch source files (see **Application impact inventory** below).
- [ ] Confirm `openapi-spec.json` matches `specs/admin-api/openapi-spec.json` byte size / hash.
- [ ] Note Docker path: `docker/Dockerfile` runs `npm run generate:api` before `npm run build`.

### Baseline application impact inventory

Files that **directly** depend on generated QueryKey factories, hook shapes, or generated types
(priority order for review at checkpoints):

| Priority | File | Patterns |
|----------|------|----------|
| P0 | `src/pages/admins.tsx` | `getGetAdminByIdQueryKey`, many `.mutate()`, invalidations |
| P0 | `src/pages/enrollment-detail.tsx` | detail hooks, invalidations, danger-zone mutations |
| P0 | `src/hooks/use-expandable-related-details.ts` | manual `useQuery` + `getGet*QueryKey` for 4 entities |
| P1 | `src/pages/tenant-detail.tsx` | mutations + detail refresh |
| P1 | `src/pages/api-key-detail.tsx` | `getGet*QueryKey`, revoke flow |
| P1 | `src/pages/encryption-keys.tsx` | generated hooks, operational actions |
| P1 | `src/context/display-timezone-context.tsx` | `useGetTenant` / tenant DTO fields |
| P2 | `src/pages/enrollments.tsx`, `integrations.tsx`, `api-keys.tsx` | list + invalidations |
| P2 | `src/pages/audit-logs.tsx`, `dashboard.tsx`, `alert-detail.tsx` | selective generated usage |
| P2 | `src/lib/query-keys.ts` | documents Orval invalidation rules (update if contract changes) |

Supporting wrappers (usually stable across minors):

- `src/hooks/use-paginated-orval.ts` — list pagination over raw fetch functions
- `src/lib/orval-mutator.ts` — `customInstance` / `ErrorType` / `BodyType`

### Phase 0 exit

Baseline commands green; inventory recorded in PR or TSP evidence section.

---

## Standard step playbook (applies to steps 1–10)

Use this checklist for **every** Orval bump PR unless a checkpoint adds items.

### A. Preparation

- [ ] Branch from updated `main`: `feature/<issue>-orval-<version>` (see GitHub issue workflow).
- [ ] Set **exact** Orval version in `package.json` (e.g. `"orval": "8.6.0"` — no caret during
  migration).
- [ ] `npm install` / refresh lockfile.
- [ ] Read Orval release notes for target version (GitHub releases / changelog).

### B. Regenerate

- [ ] `npm run generate:api` (always — never rely on stale `src/generated`).
- [ ] Scan diff in `src/generated/admin-api/` for:
  - [ ] QueryKey export renames or removals
  - [ ] Hook type flips (query ↔ mutation)
  - [ ] Response type unions vs flat DTOs
  - [ ] New exports used by existing imports

### C. Fix application code

- [ ] `npm run build` — fix all TypeScript errors before lint.
- [ ] Prefer adapting **call sites** to generated contract; adjust `orval.config.ts` only when
  intentional (document rationale in PR).
- [ ] Update `src/lib/query-keys.ts` comments if invalidation rules change.
- [ ] Do **not** hand-edit generated files.

### D. Automated validation (required every step)

Run from `ezkey-admin-ui/`:

| # | Command | Pass criterion |
|---|---------|----------------|
| 1 | `npm run generate:api` | Exit 0; client regenerated |
| 2 | `npm run lint` | Exit 0 |
| 3 | `npm run build` | Exit 0 (`tsc -b && vite build`) |
| 4 | `npm test` | All Vitest tests pass |

### E. Docker-equivalent validation (required at checkpoints + final)

| # | Command | Pass criterion |
|---|---------|----------------|
| 5 | `npm run generate:api && npm run build` | Same as Docker builder stage logic |
| 6 | `npm run build:cloudflare:verify` | **Final step only**, or if mutator/build mode touched |

### F. Manual smoke (see TSP for depth by step)

Minimum manual path after **every merged checkpoint** (8.10, 8.11, 8.13); lightweight after low-risk
steps:

- [ ] Clean-start stack + Admin UI dev or preview
- [ ] Login (Global Admin)
- [ ] Open one list screen (e.g. Integrations) — pagination works
- [ ] Open one detail screen — data loads
- [ ] Perform one reversible mutation — **detail and list refresh** (invalidation regression)
- [ ] Logout

### G. PR hygiene

- [ ] Single concern: Orval version + driven fixes only
- [ ] PR title: `chore(admin-ui): bump orval to 8.x.y`
- [ ] Traceability block: `I-2026-05-28`, `TB-2026-05-28`, step number
- [ ] Record commands + results in TSP evidence appendix
- [ ] Merge before starting next step

---

## Checkpoint 8.10.0 — Query generation semantics

**Orval release signals (2026-05-12):**

- `fix(query): namespace non-GET query keys with the HTTP verb`
- `fix(query): apply global override.query.useQuery to non-GET verbs`
- TypeScript 6/7 support improvements

### Additional checklist (beyond standard playbook)

- [ ] **Spike decision (before coding):** choose config strategy:
  - **Option A — Adapt app to dual hooks:** keep `useQuery: true` + `useMutation: true`; update all
    call sites to new hook types.
  - **Option B — Config narrowing (preferred if feasible):** default `useQuery: true`; enable
    mutations only for operations that need them (global or per-tag overrides).
  - **Option C — Fetch-first:** rely on generated fetch functions + existing wrappers
    (`usePaginatedFromOrval`, manual `useQuery`) for reads; mutations generated selectively.
- [ ] Grep audit:
  ```bash
  rg "getGet.*QueryKey|\\.mutate\\(|useGet[A-Z]" src --glob '*.{ts,tsx}'
  ```
- [ ] Update all QueryKey-based invalidations to match new factories.
- [ ] Fix discriminated response access (`data.status`, narrow success type, or normalize in
  mutator).
- [ ] Re-read `AGENTS.md` “List refresh after mutations” and detail invalidation sections — update
  if key shape changed.

### 8.10 exit criteria

- All standard automated gates pass.
- Manual smoke checklist complete (full).
- **Browser test:** run `npm run test:browser` against clean-start stack (required).
- Document chosen config strategy in PR + `orval.config.ts` comment block.

---

## Checkpoint 8.11.0 — GET / mutation override behaviour

**Orval release signals (2026-05-18):**

- `fix(query): honor per-operation useMutation override for GET operations`

### Additional checklist

- [ ] Re-verify every **GET** detail read still uses `useQuery` (or deliberate fetch wrapper).
- [ ] Re-verify state-changing operations still expose `.mutate()` / `isPending` where UI expects
  mutations.
- [ ] Pay special attention to:
  - `admins.tsx` (activate / deactivate / onboarding / recovery flows)
  - `enrollment-detail.tsx` (revoke, deactivate, policy updates)
  - `encryption-keys.tsx` (operational mutations)
- [ ] Browser test required (same as 8.10).

### 8.11 exit criteria

Same as 8.10 checkpoint gates; no known hook inversion regressions in manual smoke.

---

## Final step 8.13.0 — Program closeout

### Additional checklist

- [ ] Bump to `8.13.0`; regenerate; full standard + Docker gates.
- [ ] `npm run build:cloudflare:verify` passes.
- [ ] Browser test suite passes.
- [ ] Update `package.json` Orval pin policy:
  - either `"orval": "^8.13.0"`, or
  - keep explicit pin with documented rationale in `AGENTS.md`.
- [ ] Update `AGENTS.md` stack section (Orval version, migration TB link).
- [ ] Update TSP with final evidence.
- [ ] Transition `I-2026-05-28` → `done`; TB → `promoted`.
- [ ] Re-enable / unblock Dependabot Orval PRs.

---

## Config reference — current vs likely future

Current `orval.config.ts` (abbreviated):

```typescript
override: {
  mutator: {
    path: './src/lib/orval-mutator.ts',
    name: 'customInstance',
  },
  query: {
    useQuery: true,
    useMutation: true,
    version: 5,
  },
},
```

**Decision point at 8.10:** the combination `useQuery: true` + `useMutation: true` is the leading
suspect for hook inversion at 8.12+. The checkpoint PR must record which option (A/B/C) was chosen
and why.

**Mutator note:** `customInstance` returns `Promise<T>` directly (not `{ data, status }`). If Orval
starts generating status-discriminated unions at the type level, decide whether to:

1. unwrap in the mutator (preserve flat DTO ergonomics), or
2. update pages to narrow on `status` (more explicit, more churn).

Prefer (1) only if runtime already returns flat DTOs today (verify against live API responses).

---

## Quality gates (methodology)

| Gate | Requirement |
|------|-------------|
| **Analysis** | I-* Grill Me complete; ladder and checkpoints documented (this TB) |
| **Design** | Config strategy chosen at 8.10; invalidation contract explicit |
| **Implementation** | Each step: TSP automated gates + checkpoint manual/browser gates |
| **Closeout** | I-* / TB-* status transitions; AGENTS.md updated; Dependabot unblocked |

---

## Program exit criteria (TB promoted)

All must be true:

1. Orval at **8.13.0** (or latest stable if superseded during execution).
2. `npm ci && npm run generate:api && npm run build && npm test` green on `main`.
3. Docker Admin UI image build path green (`generate:api` + build).
4. Cloudflare verify path green.
5. Playwright smoke green on clean-start stack after final merge.
6. No hand-edited generated sources; pin policy documented.
7. TSP evidence complete for all checkpoint steps.

---

## Evidence plan

- Primary: [`test-plans/TSP-2026-05-28-admin-ui-orval-upgrade.md`](test-plans/TSP-2026-05-28-admin-ui-orval-upgrade.md)
- Per-PR: command output snippets in PR description or TSP appendix (`### Step N — 8.x.y`)
- Optional: store `git diff --stat src/` after regenerate (local only; generated is gitignored)

---

## Links

- GitHub issue: `#175` — https://github.com/mgagp/ezkey/issues/175
- [`ideas/I-2026-05-28-admin-ui-orval-upgrade.md`](ideas/I-2026-05-28-admin-ui-orval-upgrade.md)
- [`test-plans/TSP-2026-05-28-admin-ui-orval-upgrade.md`](test-plans/TSP-2026-05-28-admin-ui-orval-upgrade.md)
- [`../../methodology/github-issues-workflow.md`](../../methodology/github-issues-workflow.md)
- [`../../methodology/testing-strategy-in-workflow.md`](../../methodology/testing-strategy-in-workflow.md)
- Orval releases: https://github.com/orval-labs/orval/releases
- Prior Admin UI toolchain TB: [`TB-2026-05-24-admin-ui-vite8-upgrade.md`](TB-2026-05-24-admin-ui-vite8-upgrade.md)
