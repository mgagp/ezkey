# Dependabot curated — campaign notes

Lightweight HITL / autonomy decision track for punctual `dependabot-curated` passes (near-continuous
Dependabot PR triage and batched merges).

This folder is **peripheral** to product vision / ADR / backlog execution. It records per-campaign
lot decisions (merge / hold / defer) so cold sessions can see *why* a PR was batched or left alone —
without inventing `I-*` / `TB-*` for routine bumps.

## Cadence (standing policy)

Goal: **maximum agent autonomy for minimum risk**, with proportional targeted tests, so upgrade
PRs stop accumulating.

**Trial period:** one week starting **2026-10-07**. Review the method at the **Monday 2026-10-12**
full pass (keep, tune, or roll back).

| Layer | Cadence | Notes |
|-------|---------|-------|
| Dependabot opens PRs | **Daily** for Maven (`/`) and npm (`/ezkey-admin-ui`, `/ezkey-sdk/javascript`, `/ezkey_mobile`); **weekly** for `github-actions`, `pip`, and npm at `/bruno` | Cooldown ~**3** days patch/minor, ~**7** days semver-major (npm/maven/pip); Actions uses `default-days: 3` only (schema has no semver keys). `open-pull-requests-limit: 10` per ecosystem — see `.github/dependabot.yml` |
| Agent curation — weekday light | **Weekdays 09:14 ET** | T1–T3 + Orval **O0/O1** only; **silent when queue empty**; default autonomy on that scope |
| Agent curation — Monday full | **Monday 09:14 ET** (or first working day) | Full lots + **Java BOM** + **Mobile RN** pulses (`yarn deps:monitor --strict`) + **Admin UI advisory pulse** (`npm audit --json`, reduced to distinct GHSA) + holds review; one campaign note from `TEMPLATE.md` |
| Concurrency | **One owning pass per day** | Before merge/close/hygiene PR: if concurrent merges (~30 min) or another in-flight pass → back off, report only |

**2026-10-19:** fixed **09:14 ET** weekday/Monday slot (Marc hygiene-cadence approval) so Dependabot
curation does not collide with code-scanning triage at **10:15 ET**; Monday full adds
`yarn deps:monitor --strict` in `ezkey_mobile/` so an empty Dependabot mobile queue is not treated
as “current.” Index: [`../README.md`](../README.md) § *Cadence & automation*.

Authority: skill
[`.cursor/skills/dependabot-curated/SKILL.md`](../../../.cursor/skills/dependabot-curated/SKILL.md)
§§ *Cadence*, *Concurrency*, *Orval risk grid*.

### Reporting (least noise)

| Pass | Artifact |
|------|----------|
| Weekday light with activity | Append a short `## YYYY-MM-DD` section to [`daily-log.md`](daily-log.md) (append-only). Empty queue → **no write**. |
| Monday full | Always write `YYYY-MM-DD-pass-N.md` from [`TEMPLATE.md`](TEMPLATE.md). May summarize that week’s `daily-log.md` entries. |

Justification: a single rolling `daily-log.md` avoids a file per weekday and keeps README residual
out of the changelog path; Monday still owns the durable decision table.

## What we cover

Ecosystems in [`.github/dependabot.yml`](../../../.github/dependabot.yml), plus Monday pulses that
Dependabot alone cannot prove current:

| Surface | Dependabot | Mandatory pulse |
|---------|------------|-----------------|
| Java / Maven (reactor root) | `maven` at `/` (daily + cooldown) | **Java BOM pulse** (`spring-boot.version`, SEC-019, `google-java-format`) — Monday full |
| Admin UI | `npm` at `/ezkey-admin-ui` (daily + cooldown) | Orval via **Orval risk grid** (skill); **Admin UI advisory pulse** Monday full; evaluate **separately** from mobile |
| Ezkey Mobile (Yarn 4) | `npm` at `/ezkey_mobile` (daily + cooldown) | **Mobile RN pulse** Monday full; Orval via **Orval risk grid** (separate from Admin UI) |
| SDK JS | `npm` at `/ezkey-sdk/javascript` (daily + cooldown) | — |
| Bruno CLI | `npm` at `/bruno` (weekly + cooldown) | Smoke with `./scripts/bruno-health.sh --suite g0` |
| GitHub Actions | `github-actions` at `/` (weekly); group `codeql-action` (`github/codeql-action/*`) so init/analyze bump together (#1061/#1063 failed alone; fixed by #1072) | — |
| Python CLI | `pip` at `/ezkey-cli-python` (weekly) | — |
| Delivery connectors | Reactor modules since #1278, so `maven` at `/` covers them; their Dockerfiles in the `docker` entry | Same **Java BOM pulse** as the core; licences in root `THIRD-PARTY.txt` |

**Alerts (enabled 2026-11-06, Marc's decision).** Version updates and alerts are complementary:
transitive dependencies rarely move through version-update PRs, so lockfiles also need a refresh.
Triage by **what ships**, not by GitHub's `runtime` / `development` label, which is unreliable on
the Yarn mobile lockfile: at activation, 79 of its 82 alerts were outside the React Native release
bundle (checked from Android and iOS source maps). Fix what ships first; refresh tooling lockfiles;
dismiss the rest with a written reason. Security-update PRs are on since the stock was triaged
(2026-11-06); patch-level ones go through `dependabot-auto-merge.yml` with its exclusions.
`update-types` in an `ignore` rule filters version updates only, so a security PR can still propose
a deferred major such as TypeScript 7. It will not auto-merge. Treat it as T4 (Marc decides) and
label it `deferred:later-train` if deferred; it is not a configuration error.
An `npm audit` count is not a second queue. Reduce it with the skill § *npm audit reduction*.
The Monday **Admin UI advisory pulse** is the standing run of that reduction.
Mobile refresh recipe: `yarn up -R <alert packages> --mode=update-lockfile`, then compare the
release bundles line by line (Android and iOS). `package.json` must not move.

### Check upstream before dismissing a vulnerability

Applies to any lane that dismisses or accepts a third-party vulnerability. A dismissal assumes the
situation will not change, so verify that first:

1. **Read the upstream default branch, not only the latest release:** its version pins, its open PRs
   and issues on the advisory, and its release cadence.
2. **Name what blocks the fix:** a version range, a pin, a major with breaking changes, or no patch
   at all. If it matters, test the fix in a scratch copy with `overrides`.
3. **If upstream is moving, leave the alert open** and add a row to the table below. An open alert
   closes itself when the lockfile leaves the vulnerable range.
4. **Dismiss only when no fix is coming** (no patch published, or upstream declined), with a written
   reason. GitHub documents automatic reopening only for auto-dismissed alerts, so treat a manual
   dismissal as silent. The Monday full pass lists them (see *Dismissed-alert check* in the skill).

**Why (2026-11-06):** we were about to dismiss 16 alerts as imposed by upstream. Marc asked to check
upstream first. Bruno's default branch had already fixed or removed 12 of its 14, ahead of the next
release. ESLint and typescript-eslint had open PRs for `minimatch` 11, and the same override passed
lint on the Admin UI. Dismissing would have hidden all of it.

| Open alerts waiting on upstream (2026-11-06) | Closes when | Fallback |
|---|---|---|
| Bruno: `axios` (9), `form-data`, `js-yaml`, `csv-parse` | Next `@usebruno/cli` release, through the weekly Dependabot PR | None needed: tooling, not shipped |
| Bruno: `@faker-js/faker`, `uuid` | Upstream moves off `^9` and `^10` (still there on its default branch) | Re-decide if still open after the next release |
| Admin UI: `minimatch` | ESLint, `config-array` and `typescript-estree` allow `^11` | Monday full of **2026-11-30**: apply the tested `overrides` |
| SDK JS: `minimatch` | `openapi-generator-cli` leaves `glob` 13 | Review with #1218 |
| Mobile: `minimatch` | `typescript-estree` allows `^11` and tooling leaves `glob` 10 | Longest wait: tooling only, not in the release bundle |

Delete a row when its alert closes.

**Maven dependency graph:** Dependabot PR updates are separate from GitHub’s Insights dependency
graph. Ezkey submits the full transitive Maven graph via
[`.github/workflows/maven-dependency-submission.yml`](../../../../.github/workflows/maven-dependency-submission.yml)
(JDK 25 + `checkstyle-config` install; `main` / `workflow_dispatch` only — not `ci-gate`).
**Automatic dependency submission** is one repo-wide toggle (Settings → Advanced Security →
Dependency graph → Automatic dependency submission); set to **Disabled** (2026-10-08) so the
dedicated workflow feeds the graph. Details:
[`docs/DEVELOPMENT.md`](../../../../docs/DEVELOPMENT.md) § Continuous Integration.

Empty Dependabot queue for Maven, mobile, or Admin UI advisories ≠ stack current on Monday.
Authority: skill `dependabot-curated` §§ *Java BOM pulse*, *Mobile RN pulse*, *Admin UI advisory pulse*.

## Orval risk grid (pointer)

**Canonical table:** skill `dependabot-curated` § *Orval risk grid* (O0–O3). Do not duplicate the
full matrix elsewhere — pin lines in Admin UI / mobile `AGENTS.md` point here and to the skill.

Summary: generated-output diff is the **primary oracle**; O0 bit-identical and O1 additive/cosmetic
are autonomous (with proportional tests); O2 needs characterization tests against the old version
first; O3 (config/shim/behavior/major) stays HITL. Exact pin, Option B (`query: { version: 5 }`
only), Node engines pre-flight, and AGENTS pin sync always apply.

Evidence: 2026-10-05 Admin Orval 8.32→8.39 (#658) — empty codegen diff, 112 vitest green → O0.

**springdoc-openapi 3.2.0+:** live Admin `/api-docs` marks `PagedModel*` / `PageMetadata`
properties as `required`. After `./scripts/update-specs.sh` + Admin UI `npm run generate:api`,
list call sites that cast Orval envelope unions to `PagedModel*` hit TS2352 — use
`orvalPagedBody` / `asOrvalPagedBody` in `ezkey-admin-ui` (mutator returns the page JSON body).
Companion hygiene for Dependabot `#1055`: see the springdoc 3.2.0 companion PR (do not merge
`#1055` alone without that Admin UI + spec refresh). Mobile Auth Orval path stays **O0**.

## Crypto-adjacent and ecosystem gates

- **Crypto-adjacent** (e.g. `js-sha256`, keystore / crypto / proof-token): never autonomous on a
  **major**; route to security owner (Christophe). *If it ain't broken, don't fix it.*
- **Unchanged gates:** ESLint 10 mobile (RN lint plugins; `#1007` trial 2026-10-26 stopped —
  `eslint-plugin-react` + `eslint-plugin-react-native` still call removed ESLint 10 APIs; see
  [`ezkey_mobile/docs/MOBILE_ESLINT10_JEST30_UNBLOCK_MEMO.md`](../../../../ezkey_mobile/docs/MOBILE_ESLINT10_JEST30_UNBLOCK_MEMO.md)),
  TypeScript 7 (`deferred:later-train`), RN-core / vision-camera coupled slice → HITL / defer as
  today. **Jest 30 mobile:** gate lifted 2026-10-26 after green `yarn test` on RN 0.87.1 (Dependabot
  `#653` integrated via hygiene PR; preset still *declares* Jest 29 deps — trust runtime evidence).

## Majeures / Major upgrades

Platform / runtime / base-image **line** changes are tracked in
[`../major-upgrades/`](../major-upgrades/) (`registry.yaml`). They are reviewed on the full Monday
pass in January / April / July / October (not weekday light). Light path vs dedicated issue,
`security: true` opinion-only issue on light path, and test-plan floors: lane README + root
`AGENTS.md` § *Major upgrades*. Crypto/auth **library** majors on npm/Maven stay in this
Dependabot lane (T4); Gradle-ecosystem libs Dependabot ignores (#712) live in the register.
Monthly endoflife.date check:
[`.github/workflows/endoflife-check.yml`](../../../../.github/workflows/endoflife-check.yml)
(see [`../major-upgrades/`](../major-upgrades/)).

## Contents

| Path | Role |
|------|------|
| [`TEMPLATE.md`](TEMPLATE.md) | Copy for each Monday (or full) campaign |
| [`daily-log.md`](daily-log.md) | Append-only weekday light entries (dated sections) |
| `YYYY-MM-DD-pass-N.md` | Dated Monday / full instance (lots table + validation evidence) |
| [`handoff-centralize-core-pins.md`](handoff-centralize-core-pins.md) | Implemented on `hygiene/java-dependency-pins`: Tink / ShedLock / ipaddress now parent-pinned |

## Standing deferrals (`deferred:*`)

PRs labeled **`deferred:later-train`** or **`deferred:rn-upgrade`** (any `deferred:*`) are **out of
routine lot HITL**. Cold agents list them once under *Already deferred — skip HITL*, then triage
only unlabeled (or non-deferred) Dependabot PRs. The peel skips **both** labels.

### `deferred:later-train`

| PRs | Topic | Re-evaluate when |
|-----|--------|------------------|
| `#337`, `#498`, `#347` | TypeScript 7 (SDK + Admin UI group + migration idea) | TS 7.1 / typescript-eslint Node API readiness (~months), not routine weekday/Monday passes |

Note: Dependabot `#450` (Admin UI TS 7 group) was superseded/closed when `#498` opened; keep `#498`
in the standing set. See empty-queue pass [`2026-09-21-pass-1.md`](2026-09-21-pass-1.md).

Unlike the RN platform lot (versions can land outside Dependabot PRs), these **open PRs** remain
the visible backlog until the TypeScript 7 bump actually merges — do not replace them with an
issue while the Dependabot PRs stay open.

To park a new later-train disruptor: comment + `gh pr edit <n> --add-label deferred:later-train`.

### `deferred:rn-upgrade`

| Tracker | Topic | Status / re-evaluate when |
|---------|--------|---------------------------|
| `#627` | RN 0.87 platform lot closeout (ex-`#582`–`#585`) | **Closed 2026-11-06.** Versions had already landed. Pixel smoke recorded on the #1241 debug build (screen pass, admin.community re-enrollment, login) |

As of 2026-09-26: Dependabot PRs `#582`–`#585` were **closed without merge** (Dependabot
supersede 2026-09-22) after chantier F landed the versions otherwise. Residual human closeout is
**issue `#627`**, not those PRs — do not reopen them.
**2026-11-06:** Pixel smoke recorded on the #1241 debug build (Marc, Pixel 7 Pro): screen pass, admin.community re-enrollment, and login succeeded. `#627` closed. The "still pending" lines below are the notes from those earlier passes.
**2026-09-28 hebdo:** active Dependabot queue empty (only deferred `#337`/`#498`); `#627` Pixel
smoke still pending — see [`2026-09-28-pass-1.md`](2026-09-28-pass-1.md).
**2026-10-05 hebdo:** Lots A–D squash-merged (Maven + Admin/SDK + mobile tooling/runtime); held
`#647` (Admin Orval T4) and `#653` (Jest 30 / RN Jest 29 gate) comment-only; `#627` Pixel smoke
still pending (next Play AAB after `#650`/`#651`) — see [`2026-10-05-pass-1.md`](2026-10-05-pass-1.md).
**2026-10-12 Monday full (trial-week review):** merged `#732`/`#733`/`#734` + hygiene `#759`
(supersedes `#735` license-snapshot companion); held `#717`/`#718` (post-`#702`) and T4/gates;
`#627` Pixel smoke still pending — see [`2026-10-12-pass-1.md`](2026-10-12-pass-1.md).
**2026-10-26 Monday full:** merged `#1001`/`#1002`/`#1003`/`#1004` + hygiene `#1026`
(supersedes `#1005`/`#1006` license-snapshot companion); held `#1007` (eslint 10) and standing
T4/crypto/gates (`#870`/`#878`/`#929`/`#767`/`#653`); Boot 4.1.2 HITL; `#917` DRAFT residual;
`#627` Pixel smoke still pending — see [`2026-10-26-pass-1.md`](2026-10-26-pass-1.md).
**2026-10-26 Marc go (mobile tooling hygiene):** ESLint 10 (`#1007`) **stopped** — key plugins
`eslint-plugin-react` / `eslint-plugin-react-native` incompatible; Jest 30 + `@types/jest` 30
(`#653`) **landed** after 42/323 green without test rewrites — memo
[`MOBILE_ESLINT10_JEST30_UNBLOCK_MEMO.md`](../../../../ezkey_mobile/docs/MOBILE_ESLINT10_JEST30_UNBLOCK_MEMO.md).
**2026-11-02 Monday full:** Dependabot queue **empty** (0 open); no merges. Java BOM **4.1.2**
current; Mobile RN pulse observation-only (hold). **HITL Marc:** Dependabot version-update jobs
stopped since 2026-10-30 — see [`2026-11-02-pass-1.md`](2026-11-02-pass-1.md).
**2026-11-09 Monday full:** queue was live again (11 open at start). Merged `#1290` `#1291`
`#1293` `#1289` `#1304`. The 2026-10-30 blanket mobile hold is lifted for mobile tooling T1–T3;
RN-core, vision-camera, and crypto majors stay non-autonomous. `#1291` needed `gh pr merge --admin`
after an owner approval left the ruleset blocking the github-actions license commit — that
**bypassed** the ruleset; do not repeat without an explicit go. `#1288` (Conscrypt 2.9.0) is
ci-gate green at `c743b97c` and stays unmerged (Pixel smoke + Marc's go). See
[`2026-11-09-pass-1.md`](2026-11-09-pass-1.md).

**2026-10-19 Monday full (rerun):** merged Maven #868/#853, Admin UI #869/#871/#873/#875/#855 + Orval #872 (O0) + #874/#876/#822/#885, mobile lot via hygiene #913 (supersedes #879–#884); held #870 (Tink → Christophe), #877 (react without react-dom), #878 (RN 0.88 line), #767, #717 (post-#702 decision), gates #779/#653; `#627` Pixel smoke still pending — see [`2026-10-19-pass-1.md`](2026-10-19-pass-1.md).

Routine peel still skips any PR labeled `deferred:rn-upgrade`. To park *future* RN-coupled mobile
deps until a line bump: comment + `gh pr edit <n> --add-label deferred:rn-upgrade`.

**Process rule:** when Dependabot closes a deferred lot (supersede / recreate) but human
validation remains, open a tracking **issue** (like `#627`) so residual work stays visible — open
PRs alone are not enough after supersede.

## Preferred posture (lots without duplication)

**Default apply path:** triage into risk lots, then **merge the existing Dependabot PRs** in that
lot (individually, after CI / local validation for the lot). That closes GitHub PRs as you go and
avoids duplicate open PRs.

**Hygiene-branch path** (re-apply bumps onto `hygiene/dependabot-…`): only when the operator wants
a single reviewable PR with companion fixes, or when Dependabot branches cannot merge cleanly.
After the hygiene PR lands on `main`, **close superseded Dependabot PRs** with a short
“already integrated via #NNN” comment — they will not auto-close.

**Autonomous validation:** weekday light defaults to autonomy on T1–T3 + Orval O0/O1. Monday full
remains HITL-by-default unless the operator opts in (skill § *Autonomous validation mode*).
Autonomy means the agent owns the closeout ladder and evidence; it does **not** mean inventing a
second integration path by default.

## Cursor Cloud — `GH_TOKEN` vs read-only `gh`

A cold Cloud Agent will see a harness line that `gh` is **read-only**. That refers to the default
agent identity (`cursor` / `ghs_`) and to creating *the agent's own* PRs (`ManagePullRequest`).
The operator PAT is a **separate** env var, `GH_TOKEN`, injected as a Cursor environment secret
(not in `.cursor/environment.json`). When it is present, `gh` authenticates as `mgagp` and **can**
squash-merge, comment, and close Dependabot PRs.

**Probe (agents, never print the value):** `test -n "${GH_TOKEN:-}" && gh auth status`

Do not treat a missing MCP GitHub server, or the harness read-only sentence, as “PAT absent.”
Campaign `2026-09-15-pass-1` stumbled on that: the secret was injected; the agent still opened a
hygiene branch and deferred Dependabot closeout.

### Operator kickoff (paste-ready)

Weekday light:

```text
dependabot-curated, weekday light, autonomous.
GH_TOKEN write authorized: squash-merge Dependabot PRs; comment/close superseded PRs after a hygiene PR lands.
```

Monday full (HITL default):

```text
dependabot-curated, Monday full.
GH_TOKEN write authorized: squash-merge Dependabot PRs; comment/close superseded PRs after a hygiene PR lands.
```

Monday full with autonomy waiver:

```text
dependabot-curated, Monday full, autonomous.
GH_TOKEN write authorized: squash-merge Dependabot PRs; comment/close superseded PRs after a hygiene PR lands.
```

The last sentence is the explicit write waiver the Cloud harness asks for. Without it, a cold
agent may still pick the hygiene-branch exception even though `GH_TOKEN` is in the environment.

## Java BOM pulse (Monday full, not optional)

Dependabot Maven updates **declared** POM versions. It does not inventory Boot-managed transitives
(Hibernate, Spring Framework, Spring Security, Flyway, …). Those move only with
`spring-boot.version`. An empty Maven PR queue is not proof that Java is current — the
`open-pull-requests-limit` is **10** per ecosystem (stay current; no smoothing).

On every Monday-full `dependabot-curated` pass the agent must:

1. Compare root `spring-boot.version` to the latest **same-minor** Boot release.
2. If newer and no Dependabot PR exists, propose a hygiene-branch lot (closeout as T3 runtime).
3. After an accepted Boot bump, review SEC-019 overrides (keep only when still ahead of Boot).
4. Check `google-java-format.version` (Spotless nested pin; Dependabot typically misses it).
   Bump only when Spotless, JDK compatibility, or a real formatter bug requires it (T2 tooling).
   **Current pin `2.0.0` (adopted 2026-11-02 hygiene trial):** keep cleanup default on; Spotless
   3.10.4 needs no bump for that pin; blame-ignore the mechanical reformat commit. Refs
   [#1136](https://github.com/mgagp/ezkey/pull/1136).

**Boot 4.1.2 (2026-10-26, replaces Dependabot [#1031](https://github.com/mgagp/ezkey/pull/1031)):**
dropped parent `postgresql.version` / `tomcat.version` SEC-019 overrides — Boot BOM already
manages **42.7.14** / **11.0.27**. Kept `logback` 1.6.5 (and jackson / caffeine ahead pins).
Dependabot `#1031` `backend` failed resolving Spotless plugin transitive
`commons-codec:1.22.1` (`PluginResolutionException`) — same Maven Central flake family as
[#939](https://github.com/mgagp/ezkey/issues/939); hygiene PR retries the bump without pinning
codec. Provenance: Monday pulse [`2026-10-26-pass-1.md`](2026-10-26-pass-1.md).

Do **not** add Docker image tags to this pulse unless the operator asks. Tink / ShedLock /
ipaddress now follow parent properties (see
[`handoff-centralize-core-pins.md`](handoff-centralize-core-pins.md)).
When Docker image PRs are in scope: **keep** `maven:3.9-eclipse-temurin-25` (project JDK =
25 LTS; Marc 2026-10-12). Dependabot docker `ignore.versions` for `maven` uses Bundler
ranges `">= 3.9.26, < 3.9.29"` / `3.10.*` / `4.0.*` (JDK 26–28 on those Maven lines). Tag
`3.9-eclipse-temurin-N` maps to comparable `3.9.N` via dependabot-core
[tag.rb](https://github.com/dependabot/dependabot-core/blob/main/docker/lib/dependabot/docker/tag.rb)
`WORDS_WITH_BUILD` (see skill § Java BOM pulse). Extend one range per new Maven minor;
revisit at JDK 29 LTS. Fallback: close stray non-LTS Temurin PRs with
`@dependabot ignore this version`. Shell globs in docker `versions` fail the
`.github/dependabot.yml` check (#762). Align Playwright
`mcr.microsoft.com/playwright:vX.Y.Z-noble` with the Admin UI lockfile `@playwright/test`
version — do not jump the image ahead of npm.

Authority: skill `dependabot-curated` § *Java BOM pulse*. Provenance: Boot 4.1.1 pass
[`2026-08-21-pass-1.md`](2026-08-21-pass-1.md) (hygiene branch; no Dependabot PR).

## Mobile RN pulse (Monday full — parity with Java BOM pulse)

Dependabot npm at `/ezkey_mobile` (Yarn 4) opens grouped PRs for declared bumps. It does not
replace the mobile stack inventory. Groups:

| Group | Intent |
|-------|--------|
| `mobile-tooling` | Prettier / Husky / lint-staged / TypeScript / Babel **only**. Excludes `eslint`, `eslint-*`, `@eslint/*`, `jest`, `@types/jest` so gated majors stay **ungrouped** (solo PRs; never auto-merged). Provenance: #581 closed after HITL — that group had bundled ESLint 10 + Jest 30 with safe Babel patches. |
| `mobile-rn-core` | `react` / `react-native` / presets / CLI / test-renderer types |
| `mobile-vision-camera` | VisionCamera + worklets + nitro family (coupled with RN) |
| `mobile-navigation` | `@react-navigation/*` |

**`orval` is not grouped** — solo PRs, classified by the **Orval risk grid**, exact pin (no caret).

**Standing policy — mobile ESLint / Jest:** leave them ungrouped. ESLint 10 remains an
ecosystem gate (RN lint plugins; `#1007` stopped 2026-10-26). Jest 30 is landed on the app
(`#653`); keep Jest ungrouped so further majors stay visible — do not fold eslint/jest into
`mobile-tooling`.

On every Monday-full `dependabot-curated` pass the agent must:

1. Record declared `react` / `react-native` / key coupled libs from `ezkey_mobile/package.json`.
2. Run `yarn deps:monitor` (or `node scripts/dependency-monitor.mjs`) from `ezkey_mobile/` and
   capture actionable vs ecosystem-gated deferred (ESLint 10, Jest 30, TS7) plus high audit in
   the campaign note. Empty Dependabot mobile queue ≠ stack current.
3. Peel Dependabot mobile PRs under T1–T4 / Orval O0–O3. If no mobile PRs but the monitor shows
   actionable upgrades, propose lots from the pulse (hygiene-branch only when no PR exists).
4. Escalators: RN-core coupled slice = T3 minimum (major RN line → T4 HITL); vision-camera /
   worklets / nitro stay coupled with RN (never silent-batch with tooling); Orval = risk grid +
   generate + unit tests. Autonomy may merge tooling-only T1–T3 and Orval O0/O1; never auto-merge
   RN-core / vision-camera without Marc.

Authority: skill `dependabot-curated` § *Mobile RN pulse*.

## Admin UI advisory pulse (Monday full)

Dependabot version updates at `/ezkey-admin-ui` move declared dependencies. A transitive module
stays put while its parents reject the patched range, so the PR queue can be empty while
`npm install` still prints a vulnerability count. That count lists every ancestor of the advisory.
`npm audit fix --force` will offer a downgrade when that is what clears the count.

On every Monday-full `dependabot-curated` pass the agent must:

1. From `ezkey-admin-ui/`, run `npm audit --json` and apply the skill § *npm audit reduction*:
   one row per GHSA + package. `package-lock.json` and `yarn.lock` for the same GHSA are one
   advisory and two manifests.
2. Match each row to the waiting-upstream table above. Record GHSA, package, manifests, severity,
   and the match (including the fallback date) or **new** in the campaign note.
3. Propose a lot only for a **new** GHSA or a row whose fallback date is this Monday. A row whose
   fallback is still ahead stays waiting. The ancestor count is not a lot.

Weekday light does not run this pulse. If the operator brings an `npm audit` count mid-week, apply
the same reduction and answer from the waiting-upstream table.

Triage by **what ships**. A moderate advisory that lives only on the lint toolchain stays a
recorded row until its fallback date. This pulse is not a CI gate.

Authority: skill `dependabot-curated` § *Admin UI advisory pulse*. Provenance: 2026-11-13,
`npm install` reported 10 moderate for GHSA-g3hv-75wg-m249 (`minimatch`), already in the table
above (alerts 260 and 289).

## Related

- Hygiene index: [`../README.md`](../README.md)
- Keyword contract: root [`AGENTS.md`](../../../AGENTS.md) § Dependabot curated
- Skill: [`.cursor/skills/dependabot-curated/SKILL.md`](../../../.cursor/skills/dependabot-curated/SKILL.md)
- Dependabot config: [`.github/dependabot.yml`](../../../.github/dependabot.yml)
- Hygiene vs program: [`../../methodology/decisions/2026-06-06-methodological-closeout-vs-code-hygiene.md`](../../methodology/decisions/2026-06-06-methodological-closeout-vs-code-hygiene.md)
- Sibling lanes: [`../react-doctor/`](../react-doctor/), [`../java-doctor/`](../java-doctor/)

## Closeout reminder (pins / install / codegen)

When a pass merges Orval or another **exact-pin** / codegen dependency, the campaign note or
daily-log section must cover Node engines pre-flight, exact pin preservation, `AGENTS.md` pin
sync, workspace install, and regenerate — see skill `dependabot-curated` § *Pin, install, and
codegen hygiene*. This is not part of `doctor-curated`.
