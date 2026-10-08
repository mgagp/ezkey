---
name: dependabot-curated
description: Triages open Dependabot PRs into risk-tiered lots (weekday light or Monday full), applies maximum agent autonomy for minimum risk with proportional tests, merges individually after CI green, and closes with a short daily log or weekly campaign note. Use when the operator says dependabot-curated or asks to triage Dependabot upgrade PRs.
disable-model-invocation: true
---
# Dependabot curated

## Purpose

Keep Dependabot upgrade PRs from accumulating by running **near-continuous** curation: **maximum
agent autonomy for minimum risk**, with **proportional targeted tests**. Turn the open queue into
**few risk-tiered lots**, decide them (HITL by default, or autonomous validation when the operator
opts in — weekday light passes default to autonomy on T1–T3 + Orval O0/O1), **merge the existing
Dependabot PRs** in each lot (so GitHub closes them as you go), and close out with either a short
daily log entry or a Monday consolidated campaign note.

This is **hygiene**, not a methodology lane — the same weight class as `doctor-curated` /
`java-doctor-curated`. Do
**not** invent `I-*` / `TB-*` for the routine. Materialize backlog only when a **specific**
bump is disruptive and deliberately deferred (investigation cost should not be lost).

Standing policy (cadence table, Orval pointer, reporting): 
[`product-docs/global/hygiene/dependabot/README.md`](../../product-docs/global/hygiene/dependabot/README.md).
**Canonical Orval O0–O3 grid:** this skill § *Orval risk grid*.

## Cadence

**Trial period:** one week starting **2026-10-07**. Review the method at the **Monday 2026-10-12**
full pass (keep, tune, or roll back).

| Pass | When | Scope | Reporting |
|------|------|-------|-----------|
| **Weekday light** | Every weekday (Mon–Fri) | T1–T3 lots + Orval **O0/O1** only (Admin UI and mobile **separately**). Skip Java BOM pulse, Mobile RN pulse, and holds/deferral deep review. **Silent when the actionable queue is empty** (no file write). | Short append-only dated section in [`daily-log.md`](../../product-docs/global/hygiene/dependabot/daily-log.md) **only when something was merged/held/deferred** |
| **Monday full** | Monday (or first working day of the week) | Full pass: T1–T4, Orval O0–O3, **Java BOM pulse**, **Mobile RN pulse**, holds review, ecosystem-gate check | One consolidated campaign note `YYYY-MM-DD-pass-N.md` from [`TEMPLATE.md`](../../product-docs/global/hygiene/dependabot/TEMPLATE.md); may summarize the prior week's daily-log entries |

Dependabot itself opens PRs on a **daily** schedule for Maven and npm (Admin UI, SDK JS, mobile),
with a **cooldown** (~3 days patch/minor, ~7 days major) and `open-pull-requests-limit: 10` per
ecosystem (stay current; no smoothing). GitHub Actions and pip stay **weekly**; pip uses the same
3/3/7 cooldown, Actions uses `default-days: 3` only (Dependabot schema has no semver keys for
`github-actions`). Config: [`.github/dependabot.yml`](../../.github/dependabot.yml).

**Default autonomy for weekday light:** proceed on T1–T3 and Orval O0/O1 without per-lot Go when
the operator kickoff says `dependabot-curated` (light / weekday / autonomous). Still pause on T4,
Orval O2 (unless characterization path clears), O3, crypto-adjacent majors, and labeled
`deferred:*`. Monday full remains HITL-by-default unless the operator opts into autonomous
validation for that session.

## Concurrency (one owning pass per day)

**One owning pass per calendar day.** Before any write (merge, close, comment that changes PR
state, or opening a hygiene PR):

1. Check for **another in-flight** `dependabot-curated` / companion Orval agent, or merges by
   others on Dependabot / hygiene dependency PRs in the **last ~30 minutes**
   (`gh pr list`, recent `gh pr view` merge events, open agent runs if visible).
2. If concurrent activity is found: **back off** — do **not** merge or close. Report only
   (queue snapshot + “backed off: concurrent pass”).
3. Record the concurrency check result in the daily-log section or Monday campaign note.

Provenance: double run on 2026-10-05 and a parallel Orval cloud agent.

## Boundary contract

- **Enter when:** the operator says `dependabot-curated`, asks to triage/batch Dependabot PRs, or
  wants a weekday-light or Monday-full dependency-upgrade hygiene session.
- **Exit when:** each open Dependabot PR in scope for this pass type is **merged**, **held**, or
  **deferred** with a written rationale (or the queue was empty and the light pass stayed silent);
  closeout matching the pass type has run; Monday full always leaves a dated campaign note under
  `product-docs/global/hygiene/dependabot/`.
- **Call next:** none required. Optional: create one `I-*` only for a deferred disruptor; optional
  GitHub issue via `github-issue-promote` if the operator wants board visibility for that deferral.
- **Not needed when:** a single Dependabot PR is already under explicit solo review, or the
  operator only wants a one-off merge with no lotting.

## Authority

- Lane notes: [`product-docs/global/hygiene/dependabot/README.md`](../../product-docs/global/hygiene/dependabot/README.md)
- Campaign template: [`product-docs/global/hygiene/dependabot/TEMPLATE.md`](../../product-docs/global/hygiene/dependabot/TEMPLATE.md)
- Hygiene vs program: [`product-docs/methodology/README.md`](../../product-docs/methodology/README.md) § *Three rules worth keeping*
- Dependabot config: [`.github/dependabot.yml`](../../.github/dependabot.yml)
- Root keyword: `AGENTS.md` § Dependabot curated

## Risk taxonomy

| Tier | Rule of thumb | Merge posture | Before merge | Session closeout contribution |
|------|---------------|---------------|--------------|-------------------------------|
| **T1 Patch** | Only 3rd SemVer digit (Maven plugin quads like `4.10.2.0` → `4.10.3.0` count as patch) | Batch by ecosystem | Dependabot PR CI green | Counts toward closeout |
| **T2 Tooling minor** | Minor bump limited to build/lint/test/CI tooling (Checkstyle, SpotBugs, ESLint, Rewrite, Rest Assured, types-only) | Batch tooling minors (Maven and Admin UI may be separate sublots) | CI green; skim changelog for rule-breaking notes | `./scripts/build.sh` mandatory |
| **T3 Surface minor** | Minor bump touching shipped/runtime or shipping UI toolchain (Vite, i18n, icons, Spring runtime libs) | Batch by surface | CI + blast-radius note | `./scripts/build.sh` + clean-start + functional; Playwright if Admin UI runtime/Vite touched |
| **T4 Major / known disruptor** | Major SemVer or known painful migrations (TypeScript 7, Boot majors, crypto/auth, Actions majors with workflow semantics) | Never silent-batch; solo review or hold+defer | Explicit HITL; changelog + migration notes | Dedicated plan or deferral artifact |

**Hard escalators** (any tier → T4 / HITL): security advisories with behavioral change,
crypto/auth/encryption listeners, Spring Boot major, anything previously deferred to a later
release train. **Orval** is **not** a blanket T4 — classify with the **Orval risk grid** (O0–O3)
below; only O3 (and uncleared O2) stay on the prudent HITL path.

**Crypto-adjacent libs** (e.g. `js-sha256`, keystore / crypto / proof-token surfaces): **never
autonomous on a major**. Route to the security owner (Christophe). Standing rule: *if it ain't
broken, don't fix it*.

**Ecosystem gates (unchanged HITL / defer):** Jest 30 / ESLint 10 on mobile (RN presets),
TypeScript 7 (`deferred:later-train`), RN-core / vision-camera coupled slice.

**Primary axis** = SemVer risk + known disruptors. **Secondary axis** = ecosystem / surface
(Maven, Admin UI, mobile, SDK, Actions). Runtime vs tooling is a risk *modifier inside a lot*,
not a mandatory top-level sort.

## Lot proposal algorithm

0. **Pass type + concurrency:** decide weekday-light vs Monday-full; run the **Concurrency**
   check. If backing off, stop after a report-only snapshot.

1. List open Dependabot PRs:

   ```bash
   gh pr list --author "app/dependabot" --state open --limit 50 \
     --json number,title,url,mergeable,labels,statusCheckRollup
   ```

   On Windows when `gh` is not on PATH: `"C:\Program Files\GitHub CLI\gh.exe"`.

   **Cursor Cloud:** `test -n "${GH_TOKEN:-}" && gh auth status` (never print the secret). If the
   active account is the operator PAT, default apply is merge/close with that identity — see
   **Cloud GitHub identities**. Do not skip to a hygiene branch because the harness labeled
   default `gh` read-only.

2. **Split deferred first:** any PR labeled `deferred:*` — notably `deferred:later-train` **or**
   `deferred:rn-upgrade` — goes under **Already deferred — skip HITL** in the overview. Do **not**
   put them in active lots or re-ask Go/No-Go unless the operator explicitly reopens that train.
   Non-Dependabot parked PRs with the same label (e.g. a migration-idea PR) may be mentioned once
   for traceability.
3. **Java BOM pulse** — **Monday full only** (mandatory). Weekday light skips. Silence in the
   Dependabot queue is not proof that the Boot line is current.
4. **Mobile RN pulse** — **Monday full only** (same weight as Java BOM pulse). Weekday light
   skips. Empty Dependabot mobile queue ≠ stack current.
5. Classify each **remaining** PR **T1–T4** from the title SemVer digits and ecosystem path.
   Orval / OpenAPI-generator PRs: classify with the **Orval risk grid** (Admin UI and mobile
   **separately**, never batched together). Surface ambiguity to the operator (e.g. icon library
   minor spanning several patch bumps → T3).
6. Propose **3–6 lots max** for the session (80/20). Prefer fewer lots over one-PR theater.
   A BOM-pulse Boot bump or a Mobile RN pulse actionable lot counts even when no Dependabot PR
   exists (Monday full). Weekday light: if nothing actionable after peel → **silent exit**.
7. Present a **short lots overview** (deferred block first, then active lots).
   - **Weekday light:** default autonomy on T1–T3 + Orval O0/O1; pause on T4 / O2–O3 / crypto
     majors / ecosystem gates.
   - **Monday full — Default (HITL):** then **HITL one lot at a time** — wait for Go / No-Go /
     hold / defer before merging that lot or presenting the next. Do **not** ask for a bulk
     `1A, 2B, 3B…` reply as the primary vehicle.
   - **Autonomous validation mode** (see below): after the overview, proceed without waiting for
     per-lot Go when the operator explicitly delegated autonomy for this pass.
8. On **Go** (or autonomous proceed): apply the lot (see **Apply modes**), then continue.
9. On **Defer:** leave open or close with a rationale comment; apply `deferred:later-train` (or a
   more specific `deferred:*` label) when the PR should stay out of routine lots for weeks/months.
   If investigation cost should not be lost, create **one** `I-*` for that dependency (program
   deferral), not a Dependabot methodology program.
10. After all lot decisions: run **session closeout** proportional to pass type and highest
    accepted tier. **Monday full:** write
    `product-docs/global/hygiene/dependabot/YYYY-MM-DD-pass-N.md` from the template.
    **Weekday light with activity:** append a short dated section to `daily-log.md`.

## Orval risk grid (canonical)

Admin UI and mobile Orval bumps are evaluated **separately** — never batched together.

**Order of evidence:** the **generated-output diff is the primary oracle**. Release notes only
help interpret a **non-empty** diff. Agent reading of release notes alone is a **weak** signal.

| Surface | How to obtain the codegen diff |
|---------|--------------------------------|
| **Admin UI** | `src/generated/` is **gitignored**. Generate on the PR base and on the bump locally, then diff the two trees. |
| **Mobile** | Generated client is **committed** — the Dependabot / hygiene PR diff is the oracle. |

| Tier | Criterion | Autonomy |
|------|-----------|----------|
| **O0** | Generated output **bit-identical** | Autonomous merge after lint + tsc/build + unit tests (+ targeted Playwright for Admin UI). Mobile physical-phone smoke: **recommended, non-blocking for merge**, but must be **tracked** (issue / residual line, e.g. `#627`) and **gates the next Play AAB**. |
| **O1** | Non-empty but **additive/cosmetic** (new unused types/exports, comments, formatting, ordering) | Same autonomous path as O0; **summarize the diff** in the daily log or campaign note. |
| **O2** | Touches **signatures the app uses** (hooks, query keys, DTO shapes, mutator, callbacks) but absorbable by a **small** app-side adaptation | Autonomous **only if** characterization tests written against the **OLD** version first pass **before and after** the bump. “Small” = no `orval.config.ts` change, no new shim, adaptation limited to app code. Otherwise → **O3**. |
| **O3** | `orval.config.ts` change, new/modified shim (e.g. `orval-dom-shim.d.ts`), behavior change (dates, enums, serialization, error handling), or Orval **major** | Current prudent **HITL** path (Marc OK). |

**Always (any Orval tier):**

- Exact pin (no `^`).
- Option B: `query: { version: 5 }` only — **no** global `useQuery` / `useMutation` in
  `orval.config.ts`.
- Sync pin lines in `ezkey-admin-ui/AGENTS.md` / `ezkey_mobile/AGENTS.md` in the same change set.
- **Node version pre-flight:** before local generate/lint/tsc, use the repo root `.nvmrc` (Node **24**
  LTS) and confirm `node -v` / CI satisfy package `engines` and Orval’s stated floor. On
  **nvm-windows**, `.nvmrc` is not applied automatically — `nvm install 24` then `nvm use 24`, then
  `corepack enable`. Do not treat an engine mismatch as an Orval regression.

**Evidence:** 2026-10-05 Admin Orval 8.32→8.39 (#658, squash `5ebbd164`) — **empty** codegen diff,
112 vitest green → **O0**.

Pin/install/codegen closeout checklist: § *Pin, install, and codegen hygiene*.

## Java BOM pulse

Dependabot Maven at `directory: "/"` walks the reactor and updates **declared** POM versions. It
does **not** inventory the effective graph. Hibernate, Spring Framework, Spring Security, Flyway,
and most other Boot-managed libraries move only when `spring-boot.version` moves. The open-PR
limit is **10** per ecosystem (stay current; no smoothing). **Do not treat an empty Maven
Dependabot list as "Java is current."**

**Monday full only.** On every Monday `dependabot-curated` full pass, before proposing lots:

1. Read `spring-boot.version` in the root `pom.xml`.
2. Compare it to the latest **same-minor** Spring Boot release (today the `4.1.x` line) on Maven
   Central or [endoflife.date/spring-boot](https://endoflife.date/spring-boot). Do not jump a Boot
   **major** here — that remains a T4 hard escalator.
3. If a newer patch exists and there is **no** Dependabot PR for it, propose one hygiene-branch
   lot (SemVer patch, closeout as T3 runtime because the BOM is shipped). Provenance example:
   `hygiene/spring-boot-4.1.1` / campaign `2026-08-21-pass-1`.
4. After any accepted Boot bump (Dependabot or manual), review the SEC-019 overrides in the parent
   `dependencyManagement` (`postgresql.version`, `logback.version`, `tomcat.version`,
   `jackson-bom.version`, `jackson-2-bom.version`, and the Caffeine pin when present). **Drop** an
   override when Boot has caught up. **Keep** it only when Ezkey is still intentionally ahead
   (CVE or compatibility). Record the keep/drop table in the campaign note.
5. **Nested pin Dependabot typically misses:** `google-java-format.version` in the root
   `pom.xml` (Spotless `<googleJavaFormat>`). Check Maven Central in this same pulse — do
   **not** invent a weekly lot if unchanged. Bump only when Spotless itself moved, JDK
   compatibility requires it, or a real formatter bug exists. Treat a bump as T2 tooling.

Out of this pulse (operator-owned, separate hygiene): Docker image tags (e.g. `postgres:18-alpine`).
Do not add a `docker` ecosystem or invent a Compose-image lot unless the operator asks.

Tink / ShedLock / ipaddress now live in parent properties (`tink.version`, `shedlock.version`,
`ipaddress.version`). Do not re-propose that lift; see
[`product-docs/global/hygiene/dependabot/handoff-centralize-core-pins.md`](../../product-docs/global/hygiene/dependabot/handoff-centralize-core-pins.md).

## Mobile RN pulse

Dependabot npm at `directory: "/ezkey_mobile"` (Yarn 4; `packageManager: yarn@4.x`) opens grouped
PRs for declared `package.json` / `yarn.lock` bumps. It does **not** replace the mobile stack
inventory. Groups keep tooling, RN-core, vision-camera, and navigation from naive cross-batching;
**`orval` is intentionally ungrouped** (solo PRs → **Orval risk grid**, never batched with Admin
UI Orval). **Do not treat an empty Dependabot mobile list as "mobile is current."**

**Monday full only.** On every Monday `dependabot-curated` full pass, before proposing lots
(same weight as **Java BOM pulse**):

1. Record declared `react`, `react-native`, and key coupled libs from
   `ezkey_mobile/package.json` (at least VisionCamera / worklets / nitro family, navigation, and
   the Orval exact pin).
2. Prefer existing tooling from `ezkey_mobile/`:

   ```bash
   yarn deps:monitor
   # or: node scripts/dependency-monitor.mjs
   ```

   The monitor already classifies **actionable** vs **ecosystem-gated deferred** (e.g. ESLint 10,
   Jest 30, TypeScript 7). Capture actionable / deferred / high-audit highlights in the campaign
   note.
3. Peel Dependabot mobile PRs into lots under normal T1–T4 rules (see escalators below).
4. If there are **no** Dependabot mobile PRs but `deps:monitor` shows **actionable** upgrades,
   propose lots from the pulse. Hygiene-branch exception applies **only** when no Dependabot PR
   exists for that bump (same posture as a Boot property bump with no Maven PR).

### Mobile escalators

| Surface | Posture |
|---------|---------|
| `react` + `react-native` + aligned `@react-native/*` presets / CLI | **Coupled slice** → **T3 minimum**; major RN line → **T4 HITL** |
| `react-native-vision-camera` / `react-native-vision-camera-*` / `react-native-worklets` / `react-native-nitro-*` | Coupled with RN — **do not** silent-batch with eslint / prettier tooling |
| `orval` / OpenAPI generate path | Classify with **Orval risk grid** (O0–O3); keep **exact pin** (no caret); after bump run `yarn generate:api` + unit tests; phone smoke recommended non-blocking for O0/O1 (track residual; gates next Play AAB) |
| Autonomy mode | T1–T3 **tooling-only** mobile lots OK; **never** auto-merge RN-core / vision-camera without Marc; Orval O0/O1 OK autonomously per grid; O2 only with characterization path; O3 → HITL |

Do **not** invent a Docker or Gradle Dependabot ecosystem for mobile unless already patterned
in-repo. Authority: lane
[`product-docs/global/hygiene/dependabot/README.md`](../../product-docs/global/hygiene/dependabot/README.md)
§ *Mobile RN pulse*.

## Autonomous validation mode

Autonomy means **who owns the validation ladder and evidence**, not **how bumps land on
`main`**.

When the operator opts in (keywords such as `autonomous validation`, `délègue la validation`,
`full ladder yourself`, or an explicit waiver of per-lot HITL Go), cold agents **must**:

1. Still list, classify, peel `deferred:*`, and record a short lots overview in the campaign note.
2. **Skip waiting** for per-lot Go/No-Go for T1–T3 lots with a clear blast-radius story.
   Keep HITL (or hold) only for **T4 / hard escalators** unless those are also waived.
3. **Default apply path stays:** merge the **existing Dependabot PRs** in the lot individually
   (`gh pr merge <n> --squash` or the repo’s usual method) so GitHub closes them as you go.
   On Cursor Cloud, do this with the operator `GH_TOKEN` identity (see **Cloud GitHub
   identities** below) — do not skip merge/close because the harness labeled default `gh`
   read-only.
4. **Own the validation ladder** end-to-end for the highest accepted tier — do not stop at
   “Dependabot CI green” or hand clean-start / functional / Playwright back to the operator when
   Docker and scripts are available.
5. Deliver a **brief evidence report**: lots table, commands run, pass/fail counts, deferred set,
   and any companion fixes discovered during the pass.
6. Record `Operator: … (+ agent, autonomous validation mode)` in the campaign note metadata.

If a lot needs companion fixes that cannot land cleanly on the Dependabot PR (conflicts, config
migration, classpath pins), use the **hygiene-branch exception** below — then close superseded
Dependabot PRs after the hygiene PR merges.

**Monday full** default remains interactive HITL; autonomy is **opt-in per session**.
**Weekday light** defaults to autonomy on T1–T3 + Orval O0/O1 (see **Cadence**).

## Apply modes

| Mode | When | How |
|------|------|-----|
| **Merge existing Dependabot PRs (default)** | Normal lots, including autonomous validation | After lot decision, `gh pr merge <n> --squash` (or usual method) **per PR in the lot**. GitHub closes those PRs. Provenance preserved. |
| **Hygiene branch (exception)** | Operator explicitly wants one reviewable PR, or Dependabot branches cannot merge cleanly and need companion fixes | Branch `hygiene/dependabot-YYYY-MM-DD`; apply bumps as lot commits; open one PR; after it lands on `main`, **close superseded Dependabot PRs** with “already integrated via #NNN” |

Do **not** treat “lots” or “autonomy” as a signal to re-copy Dependabot bumps onto a second branch
by default — that is what creates duplicate open PRs.

## Cloud GitHub identities (`GH_TOKEN`)

Cursor Cloud injects a **default read-only** `gh` identity (`cursor` / `ghs_`) **and**, when the
operator configured it, **`GH_TOKEN`** (fine-grained PAT as `mgagp`). `gh` prefers `GH_TOKEN`, so
`gh auth status` shows the PAT as the **active** account.

| Do | Do not |
|----|--------|
| Probe `test -n "${GH_TOKEN:-}" && gh auth status` at the start of the pass (never print the secret) | Infer “cannot close/merge Dependabot PRs” from the harness read-only sentence alone |
| Squash-merge Dependabot PRs / comment+close superseded PRs with that PAT | Use `gh pr create` for *this agent's* hygiene PR — still `ManagePullRequest` |
| Record `GitHub write identity: GH_TOKEN as <login>` in the campaign note | Put the token in git, `environment.json`, or chat |

Hygiene-branch exception is for **unmergeable Dependabot branches or an explicit single reviewable
PR** — not for “I assumed `gh` was read-only.” After that hygiene PR lands, the **same session**
(or the immediate follow-up) must close leftover Dependabot PRs with `GH_TOKEN`.

GitHub MCP may 403 on this PAT; Actions/`check-runs` often 403 (`actions=read` missing). Merge
gating: GraphQL `mergeStateStatus` / `CLEAN`. Provenance: campaign `2026-09-12-pass-1` and
`2026-09-15-pass-1`.

Paste-ready operator kickoff (removes harness ambiguity for a cold agent):

```text
dependabot-curated, autonomous.
GH_TOKEN write authorized: squash-merge Dependabot PRs; comment/close superseded PRs after a hygiene PR lands.
```

When a hygiene branch is required and multiple Dependabot branches touch the same file (e.g.
`pom.xml`), prefer manual version edits or sequential squash-then-commit — do not leave overlapping
uncommitted squash merges.

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

Record the **Java BOM pulse** result in the campaign note even when no Boot bump was needed
(`none` is a valid outcome). Same for the nested `google-java-format` check. Record the
**Mobile RN pulse** (`deps:monitor` + declared RN versions + Dependabot mobile PRs peeled) even
when no mobile lot was proposed.

## Pin, install, and codegen hygiene (after merges)

Run this checklist in the **same closeout** whenever a merged PR changed a declared pin or a
codegen tool. Merge + CI green is not enough if docs or local `node_modules` stay behind.

0. **Node engines pre-flight:** align with root `.nvmrc` (Node 24 LTS); `node -v` (and CI Node) must
   satisfy `package.json` `engines` and the codegen tool’s floor before local generate/lint/tsc. Do
   not mis-attribute engine mismatches to Orval or the app.
1. **Exact pin preserved:** for packages the repo pins without a caret (notably Orval), confirm
   `package.json` still uses an exact version (`8.39.0`, not `^8.39.0`) after any manual
   `npm install` follow-up.
2. **Documented pins synced:** update version strings in `AGENTS.md` (and any module note that
   restates the pin) in the **same change set** as the bump when the docs call out an exact pin.
3. **Workspace install:** in each touched npm/Yarn workspace (`ezkey-admin-ui`, `ezkey_mobile`,
   SDK, …), run the project’s normal install so `npm ls <pkg>` / Yarn does not report
   `invalid: "X" from the root project`. Cold agents and the maintainer workstation both need this;
   Dependabot only updates the lockfile in git.
4. **Codegen when Orval (or OpenAPI generator) moved:** run `npm run generate:api` (Admin UI) and/or
   the mobile generate path; classify the output with the **Orval risk grid**; commit regenerated
   clients only if the bump actually changes output (mobile) or document the empty/local tree diff
   (Admin UI). Prefer this **before** Playwright so missing generated imports do not fail the
   closeout ladder.

Record completion (or N/A) in the campaign note validation table (Monday) or the daily-log section
(weekday). Orval bumps follow the **Orval risk grid** — this checklist is the operational tail.

## Merge rules

- Lots = **decision + validation batches**, not a reason to re-implement bumps elsewhere.
- **Default apply:** merge the **existing Dependabot PRs** in the lot individually after the lot
  decision (preserves provenance; GitHub closes those PRs).
- **Hygiene branch:** exception only — explicit operator request for one reviewable PR, or
  unmergeable Dependabot branches that need companion fixes. After land, close superseded
  Dependabot PRs manually.
- **Never** silent auto-merge to `main` without either per-lot Go **or** an explicit autonomous
  validation waiver for that session.
- Prefer Dependabot **groups** in `.github/dependabot.yml` to reduce atomization.
  `open-pull-requests-limit` is **10** per ecosystem (Marc 2026-10-07: stay as current as
  possible; no smoothing over time). Do not lower it casually.

## HITL contract (default for cold agents)

1. Determine **pass type** (weekday light vs Monday full); run **Concurrency** check; list and
   classify; peel off `deferred:*` first. **Monday full:** run the **Java BOM pulse** and the
   **Mobile RN pulse**. **Weekday light:** skip those pulses. **Probe `GH_TOKEN` / `gh auth
   status`** (skill § *Cloud GitHub identities*) before choosing hygiene-branch vs merge-existing;
   propose lots overview (3–6 active lots; Monday may include a Boot or Mobile RN pulse lot when
   the pulse found actionable work with no Dependabot PR). Empty weekday queue → silent exit.
2. Iterate **one lot at a time** when HITL applies: members, tier (and Orval **O-tier** when
   relevant), blast radius, CI status, open question → wait for Go / No-Go / hold / defer —
   **unless** weekday-light defaults or autonomous validation mode apply (then proceed for
   T1–T3 + Orval O0/O1 and only pause on T4 / O2–O3 / crypto majors / hard escalators).
3. Record final decisions: Monday → campaign note; weekday with activity → `daily-log.md` section.
4. Execute session closeout proportional to pass type and highest accepted tier; in autonomous
   mode the agent runs the ladder and presents evidence (do not defer stack/Playwright to the
   operator by default when Docker and scripts are available).
5. Do not invent `I-*` / `TB-*` for routine merged patches.

## Majeures / Major upgrades

Platform, runtime, and base-image **line** changes (endoflife.date support line or semver major)
are **not** routine Dependabot lots. They live in
[`product-docs/global/hygiene/major-upgrades/`](../../product-docs/global/hygiene/major-upgrades/)
(`registry.yaml` + lane README). On the **Monday full** pass in January / April / July / October,
include a short register walk (Fred prepares; Christophe opinion on `security: true`; Patrick on
build/mobile plans; Marc decides).

- **Light path:** semver-minor line bump (Dependabot PR or a normal PR when Dependabot cannot
  open it) → update the registry in the same PR + family test-plan floor in the PR body.
- **Light path + `security: true`:** short **opinion-only** issue linked to the existing PR;
  test plan stays in the PR body.
- **Dedicated issue with test plan before the PR:** semver major, PostgreSQL data migration, or
  React Native line only. Security opinion in that issue when `security: true`.
- Crypto/auth **library** majors on npm/Maven stay T4 here. Exception in the register: libs whose
  majors Dependabot ignores (Gradle ecosystem, #712) — e.g. Conscrypt, AndroidX Biometric.
- Monthly endoflife.date check workflow: **upcoming**, not present yet.
- Full agent contract: root `AGENTS.md` § Dependabot curated → *Major upgrades*.

## Deferred labels

| Label | Meaning |
|-------|---------|
| `deferred:later-train` | Parked for a later release-train / disruptor review. Skip weekly lots until the operator reopens. |
| `deferred:rn-upgrade` | Mobile deps that must move with a React Native line bump. Skip weekly lots until a scheduled RN upgrade. |

Create once per repo (descriptions must be ≤100 characters for GitHub):

```bash
gh label create "deferred:later-train" --description "…" --color "6E7781"
gh label create "deferred:rn-upgrade" --description "…" --color "B76E3F"
```

Standing parked set is recorded in [`product-docs/global/hygiene/dependabot/README.md`](../../product-docs/global/hygiene/dependabot/README.md).

## Windows shell notes

- Prefer Git Bash for `./scripts/build.sh` and clean-start scripts.
- From PowerShell: `& "C:\Program Files\Git\bin\bash.exe" -lc './scripts/build.sh'`
- Prefer `"C:\Program Files\GitHub CLI\gh.exe"` when `gh` is missing from PATH.
