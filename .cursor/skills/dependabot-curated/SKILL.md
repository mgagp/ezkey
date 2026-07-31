---
name: dependabot-curated
description: Triages open Dependabot PRs into risk-tiered lots, runs interactive human-AI HITL for Go/No-Go per lot, merges individually after CI green, and closes with a proportional validation ladder. Use when the operator says dependabot-curated or asks to batch weekly dependency update PRs.
disable-model-invocation: true
---
# Dependabot curated

## Purpose

Turn a weekly Dependabot PR backlog into **few risk-tiered lots**, decide them with interactive
HITL, merge each PR individually (Dependabot provenance preserved), and run **one session
closeout** validation ladder. Minimize recurring human time without silent auto-merge.

This is **hygiene**, not a methodology lane — the same weight class as `doctor-curated` /
`java-doctor-curated`. Do
**not** invent `I-*` / `TB-*` for the weekly routine. Materialize backlog only when a **specific**
bump is disruptive and deliberately deferred (investigation cost should not be lost).

## Boundary contract

- **Enter when:** the operator says `dependabot-curated`, asks to triage/batch Dependabot PRs, or
  wants a weekly dependency-upgrade hygiene session.
- **Exit when:** each open Dependabot PR in scope is **merged**, **held**, or **deferred** with a
  written rationale; session closeout validation matching the highest accepted tier has run (or
  an explicit T1-only shortcut was recorded); a dated campaign note exists under
  `product-docs/global/hygiene/dependabot/`.
- **Call next:** none required. Optional: create one `I-*` only for a deferred disruptor; optional
  GitHub issue via `github-issue-promote` if the operator wants board visibility for that deferral.
- **Not needed when:** a single Dependabot PR is already under explicit solo review, or the
  operator only wants a one-off merge with no lotting.

## Authority

- Lane notes: [`product-docs/global/hygiene/dependabot/README.md`](../../product-docs/global/hygiene/dependabot/README.md)
- Campaign template: [`product-docs/global/hygiene/dependabot/TEMPLATE.md`](../../product-docs/global/hygiene/dependabot/TEMPLATE.md)
- Hygiene vs program: [`product-docs/methodology/decisions/2026-06-06-methodological-closeout-vs-code-hygiene.md`](../../product-docs/methodology/decisions/2026-06-06-methodological-closeout-vs-code-hygiene.md)
- Dependabot config: [`.github/dependabot.yml`](../../.github/dependabot.yml)
- Root keyword: `AGENTS.md` § Dependabot curated

## Risk taxonomy

| Tier | Rule of thumb | Merge posture | Before merge | Session closeout contribution |
|------|---------------|---------------|--------------|-------------------------------|
| **T1 Patch** | Only 3rd SemVer digit (Maven plugin quads like `4.10.2.0` → `4.10.3.0` count as patch) | Batch by ecosystem | Dependabot PR CI green | Counts toward closeout |
| **T2 Tooling minor** | Minor bump limited to build/lint/test/CI tooling (Checkstyle, SpotBugs, ESLint, Rewrite, Rest Assured, types-only) | Batch tooling minors (Maven and Admin UI may be separate sublots) | CI green; skim changelog for rule-breaking notes | `./scripts/build.sh` mandatory |
| **T3 Surface minor** | Minor bump touching shipped/runtime or shipping UI toolchain (Vite, i18n, icons, Spring runtime libs) | Batch by surface | CI + blast-radius note | `./scripts/build.sh` + clean-start + functional; Playwright if Admin UI runtime/Vite touched |
| **T4 Major / known disruptor** | Major SemVer or known painful migrations (TypeScript 7, Boot majors, crypto/auth, Actions majors with workflow semantics) | Never silent-batch; solo review or hold+defer | Explicit HITL; changelog + migration notes | Dedicated plan or deferral artifact |

**Hard escalators** (any tier → T4): security advisories with behavioral change, crypto/auth/encryption
listeners, OpenAPI generator / Orval pin policy, Spring Boot major, anything previously deferred
to a later release train.

**Primary axis** = SemVer risk + known disruptors. **Secondary axis** = ecosystem / surface
(Maven, Admin UI, mobile, SDK, Actions). Runtime vs tooling is a risk *modifier inside a lot*,
not a mandatory top-level sort.

## Lot proposal algorithm

1. List open Dependabot PRs:

   ```bash
   gh pr list --author "app/dependabot" --state open --limit 50 \
     --json number,title,url,mergeable,labels,statusCheckRollup
   ```

   On Windows when `gh` is not on PATH: `"C:\Program Files\GitHub CLI\gh.exe"`.

2. **Split deferred first:** any PR labeled `deferred:*` (notably `deferred:later-train`) goes under
   **Already deferred — skip HITL** in the overview. Do **not** put them in weekly lots or re-ask
   Go/No-Go unless the operator explicitly reopens that train. Non-Dependabot parked PRs with the
   same label (e.g. a migration-idea PR) may be mentioned once for traceability.
3. Classify each **remaining** PR **T1–T4** from the title SemVer digits and ecosystem path.
   Surface ambiguity to the operator (e.g. icon library minor spanning several patch bumps → T3).
4. Propose **3–6 lots max** for the session (80/20). Prefer fewer lots over one-PR theater.
5. Present a **short lots overview** (deferred block first, then active lots).
   - **Default (HITL):** then **HITL one lot at a time** — wait for Go / No-Go / hold / defer
     before merging that lot or presenting the next. Do **not** ask for a bulk `1A, 2B, 3B…`
     reply as the primary vehicle.
   - **Autonomous validation mode** (see below): after the overview, proceed without waiting for
     per-lot Go when the operator explicitly delegated autonomy for this pass.
6. On **Go** (or autonomous proceed): apply the lot (see **Apply modes**), then continue.
7. On **Defer:** leave open or close with a rationale comment; apply `deferred:later-train` (or a
   more specific `deferred:*` label) when the PR should stay out of weekly lots for weeks/months.
   If investigation cost should not be lost, create **one** `I-*` for that dependency (program
   deferral), not a Dependabot methodology program.
8. After all lot decisions: run **session closeout**, then write
   `product-docs/global/hygiene/dependabot/YYYY-MM-DD-pass-N.md` from the template.

## Autonomous validation mode

When the operator says they want a **more autonomous** Dependabot pass (keywords such as
`autonomous validation`, `délègue la validation`, `full ladder yourself`, or an explicit waiver of
per-lot HITL Go), cold agents **must**:

1. Still list, classify, peel `deferred:*`, and publish a short lots overview in the campaign note.
2. **Skip waiting** for per-lot Go/No-Go for T1–T3 lots that have a clear blast-radius story.
   Keep HITL (or hold) only for **T4 / hard escalators** unless the operator also waived those.
3. Prefer a **hygiene branch** with **one commit per lot** (plus companion fix commits when a bump
   breaks build/config). Push the branch and open one hygiene PR (or report the branch URL).
4. **Own the validation ladder** end-to-end for the highest accepted tier — do not stop at
   “CI green on Dependabot” or hand the stack/Playwright back to the operator by default.
5. Deliver a **brief evidence report**: lots table, commands run, pass/fail counts, deferred set,
   and any companion fixes discovered during the pass.
6. Record `Operator: … (+ agent, autonomous validation mode)` in the campaign note metadata.

Default weekly mode remains interactive HITL. Autonomy is **opt-in per session**, not silent
auto-merge of Dependabot PRs to `main`.

## Apply modes

| Mode | When | How |
|------|------|-----|
| **Per-PR squash-merge to `main`** | Classic weekly HITL; preserves Dependabot provenance | `gh pr merge <n> --squash` per PR after lot Go |
| **Hygiene branch + lot commits** | Operator asks for one branch / lot-by-lot review, or autonomous mode | Branch `hygiene/dependabot-YYYY-MM-DD`; squash or manually apply each Dependabot branch into **one commit per lot**; companion fixes as follow-up commits; one PR to `main`; then close superseded Dependabot PRs |

When applying multiple Dependabot branches that touch the same file (e.g. `pom.xml`), prefer
manual version edits or sequential squash-then-commit — do not leave overlapping uncommitted
squash merges.

**Checkstyle companion tip:** `./scripts/build.sh` runs `checkstyle:check` **before** reactor
`install`. If this pass changes `checkstyle-config` XML, install that module first
(`mvn install -pl checkstyle-config -DskipTests`) or the check still loads the previous jar from
`~/.m2`.

## Session closeout validation ladder

Run **once** at end of session (or at a milestone if the session is split), not once per patch PR:

1. **Always:** `./scripts/build.sh` from Git Bash (Spotless, Checkstyle, clean install, unit tests).
2. **When any T2/T3/T4 Maven or runtime lot merged:** clean-start stack + functional suite (P0
   minimum; fuller when time allows).
3. **Playwright:** when T3 Admin UI or any Admin UI **runtime** lib changed. Skip for pure Admin UI
   **tooling-only** T2 (e.g. ESLint) if PR CI is already green, unless the lot also included Vite
   or runtime packages.
4. **Elective functional tests:** when auth/enrollment/integration **test** deps changed (e.g.
   Rest Assured) or when this is release-hygiene closeout rather than a mid-week patch sweep.
5. **Exploratory human:** only for T4 or failed automation; keep brief (clean-start + demo-mode).

**T1-only session shortcut:** if the entire session accepted only T1 patches and all Dependabot
CIs were green, closeout may be `./scripts/build.sh` only, with an explicit campaign-note line that
stack/Playwright were deferred to the next T2+ session or weekly milestone.

## Pin, install, and codegen hygiene (after merges)

Run this checklist in the **same closeout** whenever a merged PR changed a declared pin or a
codegen tool. Merge + CI green is not enough if docs or local `node_modules` stay behind.

1. **Exact pin preserved:** for packages the repo pins without a caret (notably Orval), confirm
   `package.json` still uses an exact version (`8.22.0`, not `^8.22.0`) after any manual
   `npm install` follow-up.
2. **Documented pins synced:** update version strings in `AGENTS.md` (and any module note that
   restates the pin) in the **same change set** as the bump when the docs call out an exact pin.
3. **Workspace install:** in each touched npm/Yarn workspace (`ezkey-admin-ui`, `ezkey_mobile`,
   SDK, …), run the project’s normal install so `npm ls <pkg>` / Yarn does not report
   `invalid: "X" from the root project`. Cold agents and the maintainer workstation both need this;
   Dependabot only updates the lockfile in git.
4. **Codegen when Orval (or OpenAPI generator) moved:** run `npm run generate:api` (Admin UI) and/or
   the mobile generate path; commit regenerated clients only if the bump actually changes output.
   Prefer this **before** Playwright so missing generated imports do not fail the closeout ladder.

Record completion (or N/A) in the campaign note validation table. Orval / OpenAPI generator bumps
are already **T4 hard escalators** — this checklist is the operational tail of that policy.

## Merge rules

- Lots = **decision + validation batches**, not umbrella rewrite commits.
- **HITL mode:** merge Dependabot PRs **individually** after lot approval (preserves provenance
  and per-PR CI).
- **Autonomous / hygiene-branch mode:** lot commits on `hygiene/dependabot-YYYY-MM-DD`, one PR;
  close superseded Dependabot PRs after merge to `main`.
- **Never** silent auto-merge to `main` without either per-lot Go **or** an explicit autonomous
  validation waiver for that session.
- Do not raise `open-pull-requests-limit` casually; prefer Dependabot **groups** in
  `.github/dependabot.yml` to reduce future atomization.

## HITL contract (default for cold agents)

1. List and classify; peel off `deferred:*` first; propose lots overview (3–6 active lots).
2. Iterate **one lot at a time**: members, tier, blast radius, CI status, open question → wait
   for Go / No-Go / hold / defer — **unless** autonomous validation mode was granted for the
   session (then proceed for T1–T3 and only pause on T4 / hard escalators).
3. Record final decisions in the campaign note after the session (table is fine *there*).
4. Execute session closeout proportional to the highest accepted tier; in autonomous mode the
   agent runs the ladder and presents evidence (do not defer stack/Playwright to the operator by
   default when Docker and scripts are available).
5. Do not invent `I-*` / `TB-*` for routine merged patches.

## Deferred labels

| Label | Meaning |
|-------|---------|
| `deferred:later-train` | Parked for a later release-train / disruptor review. Skip weekly lots until the operator reopens. |

Create with: `gh label create "deferred:later-train" --description "…" --color "6E7781"` (once per repo).
Standing parked set is recorded in [`product-docs/global/hygiene/dependabot/README.md`](../../product-docs/global/hygiene/dependabot/README.md).

## Windows shell notes

- Prefer Git Bash for `./scripts/build.sh` and clean-start scripts.
- From PowerShell: `& "C:\Program Files\Git\bin\bash.exe" -lc './scripts/build.sh'`
- Prefer `"C:\Program Files\GitHub CLI\gh.exe"` when `gh` is missing from PATH.
