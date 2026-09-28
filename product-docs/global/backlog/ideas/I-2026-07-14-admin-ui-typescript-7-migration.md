# Backlog Idea — `I-2026-07-14-admin-ui-typescript-7-migration` Admin UI TypeScript 7 migration (prep + wait for 7.1)

## Metadata

- **ID:** `I-2026-07-14-admin-ui-typescript-7-migration`
- **Status:** `incubating`
- **Priority:** `P2`
- **Created at:** `2026-07-14`
- **Updated at:** `2026-07-14`
- **Last reviewed at:** `2026-07-14`
- **Progression markers:** `P2-maintainability`, `toolchain`
- **Component tags:** `admin-ui`, `docs`, `tooling`
- **Lane:** `A`
- **Captured by:** Marc (session analysis 2026-07-14; agent materialization)
- **GitHub issue:** _(optional — open when Prep slice or Cutover is scheduled)_

## Classification

**Program (toolchain), not cosmetic hygiene.** Comparable to the Orval ladder
([`I-2026-05-28-admin-ui-orval-upgrade`](I-2026-05-28-admin-ui-orval-upgrade.md)): a naive bump breaks
or desynchronizes the toolchain. This idea encodes an explicit **prep → wait gate → cutover**
sequence.

**Not on the September 2026 operable-release critical path.** Do not schedule ahead of Wave D
integrity soak residuals or release-compass parallel tracks unless idle capacity is deliberate.
See [`../../operational-readiness-prioritization-2026-09.md`](../../operational-readiness-prioritization-2026-09.md).

## Intent

Move Ezkey Admin UI from **TypeScript ~6.0.3** to **TypeScript 7.x** as a first-class toolchain
target, after (1) clearing TypeScript 6 deprecations that become hard errors in 7.0, and (2)
waiting until the ecosystem can consume TypeScript 7’s programmatic surface — currently deferred to
**TypeScript 7.1** — so ESLint (`typescript-eslint`) and related Node API consumers can run
against a single `typescript` dependency without a permanent dual-install workaround.

## Problem and value

### Problem

- TypeScript **7.0 is GA** (2026-07-08): native Go compiler / language service under npm `latest`,
  typically much faster CLI and editor feedback on large codebases.
- Admin UI remains on **`typescript ~6.0.3`**, already a valid bridge release, but
  [`tsconfig.app.json`](../../../../ezkey-admin-ui/tsconfig.app.json) still relies on:
  - `"ignoreDeprecations": "6.0"` — **not supported in TS 7**;
  - `"baseUrl": "."` plus `paths` for `@/*` — **`baseUrl` is a hard error in TS 7**.
- **typescript-eslint** (Admin UI: `^8.63.0`) does **not** support TypeScript 7 as the sole
  `typescript` package yet: peer ranges stop below 6.1, and load crashes because TS 7.0 does **not**
  ship a stable Node compiler API. Microsoft’s interim guidance is **side-by-side TS 6 + TS 7**.
- A naive `"typescript": "~7"` bump therefore fails **lint install / runtime**, even if `tsc`
  itself is fine after config cleanup.
- Absolute speedup on Admin UI’s SPA size is **modest**; the real risk is **tooling breakage** and
  deferred debt if we stay soft on 6.0 deprecations.

### Expected value

- Compile-clean under TS 6 **without** `ignoreDeprecations`, so the TS 7 cutover is a version bump
  and validation, not a surprise config rewrite.
- Preserve Admin UI invariants: `tsc -b`, Vite build, `generate:api` (Orval), ESLint, Vitest,
  Cloudflare / Docker build paths documented in `ezkey-admin-ui/AGENTS.md`.
- Avoid a long-lived dual-package layout unless temporarily useful for experiments; prefer cutover
  when **7.1+** (or certified `typescript-eslint` support) removes the API gap.
- Document milestones so agents and humans do not treat this as a one-line Dependabot merge.

## Scope

### In scope

- **Prep (doable on TS 6 now):**
  - Remove `"ignoreDeprecations": "6.0"` and fix every remaining deprecation warning/error.
  - Remove `"baseUrl"`; keep `@/*` via `paths` rooted relative to the tsconfig project (Microsoft
    migration guidance).
  - Confirm `moduleResolution: bundler`, `strict`, `verbatimModuleSyntax`, and related options
    remain green after cleanup.
  - Validation gate: `npm run lint`, `npm run build` (or `tsc -b`), `npm test`, and ideally
    `npm run generate:api` on a regenerated client.
- **Wait gate:**
  - Hold **sole** `typescript@7` cutover until TypeScript **7.1** (or equivalent) ships a usable
    programmatic API **and** `typescript-eslint` (and any Orval/plugin path that requires the API)
    officially or practically supports that surface.
  - Track Microsoft release notes / `typescript-eslint` peer range / issue tracker as the gate
    criteria (not calendar guessing alone).
- **Optional interim (explicit, not required):**
  - Side-by-side install per Microsoft (`typescript` → `@typescript/typescript6`, plus
    `@typescript/native` / `typescript@7` alias) only if Prep validation or IDE experiments need
    native `tsc` early. Must not become the permanent default without a documented reason.
- **Cutover (after wait gate):**
  - Single primary `typescript` major **7.x**.
  - Re-validate lint / typecheck / Orval / Vitest / Docker + Cloudflare build scripts.
  - Update `ezkey-admin-ui/AGENTS.md` (and README stack line) with the new baseline and any
    remaining dual-tool caveats.
- Tracer bullet + light test-plan slice when Prep or Cutover is promoted to execution.

### Out of scope

- Application feature work, UI redesign, Orval major bumps, Vite majors in the same change sets.
- Mobile / other packages TypeScript majors (separate ideas if needed).
- Vue / Svelte / Volar concerns (Admin UI is React + Vite only).
- Mandating TS 7 language-server extension policy for every editor — optional operator DX.
- Treating residual Security Challenge LOW items or September integrity work as dependents of this
  migration.

## Delivery milestones

| Milestone | Name | When | Exit criteria |
|-----------|------|------|---------------|
| **M1** | **Prep on TS 6** | Now (capacity permitting) | `tsconfig*.json` clean without `ignoreDeprecations` / `baseUrl`; lint + `tsc -b` + tests green on `~6.0.x` |
| **M2** | **Wait / monitor** | After M1 until gate | Written note (in this idea or method log) when 7.1 API + eslint support land; no forced calendar date |
| **M3** | **Optional side-by-side** | Only if useful during M2 | Dual packages documented; CI still has one authoritative typecheck path; no silent peer overrides |
| **M4** | **Cutover to TS 7** | After wait gate | Single `typescript@7` (or latest 7.x); eslint + orval + builds green; AGENTS.md updated |
| **M5** | **Closeout** | After M4 | `I-*` / `TB-*` → `done`; residual risks recorded |

**Default execution order:** M1 → M2 → M4 → M5. M3 is optional.

## Impacts

| Surface | Impact |
|---------|--------|
| `ezkey-admin-ui/tsconfig.app.json` | Must drop `ignoreDeprecations` and `baseUrl`; adjust `paths` |
| `npm run lint` | Blocked on sole TS 7 until eslint supports the API; Prep stays on TS 6 |
| `npm run build` (`tsc -b && vite build`) | Needs Prep green; gains native `tsc` speed primarily after cutover |
| Orval `generate:api` | Validate at Prep and at Cutover (API-consuming tools may lag like eslint) |
| Vite / Vitest / Playwright | Low direct coupling (esbuild runtime); still re-run as regression nets |
| Docker / Cloudflare Admin UI builds | Must stay green after Prep and Cutover |
| September release compass | **No dependency** — schedule as maintainability, not operability |

## Key assumptions

- TypeScript 7.0 GA language-check behaviour matches clean TypeScript 6.0 (no `ignoreDeprecations`).
- TypeScript **7.1** (≈ 3–4 months after 7.0 per Microsoft cadence) is the intended unlock for a
  single-package toolchain including eslint-class tools; if support lands via another channel first,
  re-evaluate the wait gate on evidence.
- Admin UI does not embed Volar-style template checkers; React+Vite path is not blocked by
  embedded-language caveats that affect Vue/Svelte.
- Path alias `@/` continues to be required; Vite already resolves it — only tsconfig shape changes.
- Absolute CI time savings alone do not justify cutting over before eslint support.

## Risks and exceptions

- **Premature sole `typescript@7`:** breaks `npm ci` peer resolution and/or eslint load — treat as
  failed cutover, revert or restore dual layout.
- **Leaving `ignoreDeprecations` indefinitely:** hides debt that hard-fails on first TS 7 attempt.
- **Side-by-side forever:** accidental complexity; prefer exit in M4.
- **Orval or other generators** still needing TS 6 API after eslint catches up — may force a short
  dual period; record explicitly if observed.
- **Rare type-system deltas** (e.g. template-literal Unicode inference) — low probability for this
  SPA; catch in `tsc -b` at cutover.
- **Capacity steal from Wave D / integrity P1** — reject schedule conflicts; Prep can wait.

## Grill Me (lightweight — 2026-07-14)

Condensed challenge for a bounded toolchain program (no separate grill-session file unless M4
uncovers new uncertainty).

| # | Question | Answer / decision |
|---|----------|-------------------|
| G1 | Is TS 7 GA? | **Yes** (2026-07-08). Not a preview bump. |
| G2 | Can we cut over to sole TS 7 now? | **No** — `typescript-eslint` lacks TS 7 Node API support in 7.0. |
| G3 | What do we do now? | **M1 Prep** on TS 6: remove deprecations / `baseUrl`; keep ~6.0.x. |
| G4 | Wait for what exactly? | **TS 7.1 (API) + workable eslint (and Orval if needed)** — evidence-based gate, not a fixed date. |
| G5 | Side-by-side required? | **Optional (M3)** for experiments; not the committed end state. |
| G6 | Priority vs September operable release? | **P2 maintainability**; do not preempt integrity soak / compass items. |
| G7 | Methodology weight? | **Program `I-*`** (multi-milestone, tool coordination). Promote `TB-*` when M1 or M4 starts. |
| G8 | Browser tests gate? | Not for Prep. Optional smoke at Cutover only if stack is cheap to run. |

## Promotion notes

- **M1 ready for `TB-*`:** when an owner schedules the Prep PR — tracer bullet should list exact
  tsconfig edits, validation commands, and “no typescript major bump” boundary.
- **M4 ready for `TB-*`:** when wait-gate evidence is recorded in this idea (links to release notes /
  peer ranges). Do not promote Cutover on rumour of 7.1 alone.
- **Park or lower priority** if September capacity remains tight; M1 debt is still worth clearing
  independently of Cutover.
- Optional GitHub issue when Prep or Cutover is scheduled (labels: `lane:a`, `type:chore` or
  `type:refactor`, `component:admin-ui`, priority matching, `status:ready`).

## Links

- Session analysis (2026-07-14): Admin UI on `typescript ~6.0.3`; impacts of TS 7; GA vs pre-release
  clarification.
- Microsoft: [Announcing TypeScript 7.0](https://devblogs.microsoft.com/typescript/announcing-typescript-7-0/)
  (side-by-side with `@typescript/typescript6`; no API in 7.0; 7.1 roadmap).
- Comparable toolchain programs:
  - [`I-2026-05-28-admin-ui-orval-upgrade`](I-2026-05-28-admin-ui-orval-upgrade.md)
  - [`I-2026-05-24-admin-ui-vite8-upgrade`](I-2026-05-24-admin-ui-vite8-upgrade.md)
- Runtime stack notes: [`../../../../ezkey-admin-ui/AGENTS.md`](../../../../ezkey-admin-ui/AGENTS.md)
- Config under stress: [`../../../../ezkey-admin-ui/tsconfig.app.json`](../../../../ezkey-admin-ui/tsconfig.app.json)
- Release-order compass (non-blocker reminder):
  [`../../operational-readiness-prioritization-2026-09.md`](../../operational-readiness-prioritization-2026-09.md)

## Incubation sources

- Maintainer Q&A session (2026-07-14): TypeScript version audit, TS 7 impact analysis, GA confirmation,
  explicit request to backlog the migration as prep-then-wait-for-7.1.
