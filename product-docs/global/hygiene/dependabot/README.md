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
| Dependabot opens PRs | **Daily** for Maven (`/`) and npm (`/ezkey-admin-ui`, `/ezkey-sdk/javascript`, `/ezkey_mobile`); **weekly** for `github-actions` and `pip` | Cooldown ~**3** days patch/minor, ~**7** days semver-major (npm/maven/pip); Actions uses `default-days: 3` only (schema has no semver keys). `open-pull-requests-limit: 10` per ecosystem — see `.github/dependabot.yml` |
| Agent curation — weekday light | Every weekday | T1–T3 + Orval **O0/O1** only; **silent when queue empty**; default autonomy on that scope |
| Agent curation — Monday full | Monday (or first working day) | Full lots + **Java BOM** + **Mobile RN** pulses + holds review; one campaign note from `TEMPLATE.md` |
| Concurrency | **One owning pass per day** | Before merge/close/hygiene PR: if concurrent merges (~30 min) or another in-flight pass → back off, report only |

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
| Admin UI | `npm` at `/ezkey-admin-ui` (daily + cooldown) | Orval via **Orval risk grid** (skill); evaluate **separately** from mobile |
| Ezkey Mobile (Yarn 4) | `npm` at `/ezkey_mobile` (daily + cooldown) | **Mobile RN pulse** Monday full; Orval via **Orval risk grid** (separate from Admin UI) |
| SDK JS | `npm` at `/ezkey-sdk/javascript` (daily + cooldown) | — |
| GitHub Actions | `github-actions` at `/` (weekly) | — |
| Python CLI | `pip` at `/ezkey-cli-python` (weekly) | — |

Empty Dependabot queue for Maven or mobile ≠ stack current on Monday. Authority: skill
`dependabot-curated` §§ *Java BOM pulse*, *Mobile RN pulse*.

## Orval risk grid (pointer)

**Canonical table:** skill `dependabot-curated` § *Orval risk grid* (O0–O3). Do not duplicate the
full matrix elsewhere — pin lines in Admin UI / mobile `AGENTS.md` point here and to the skill.

Summary: generated-output diff is the **primary oracle**; O0 bit-identical and O1 additive/cosmetic
are autonomous (with proportional tests); O2 needs characterization tests against the old version
first; O3 (config/shim/behavior/major) stays HITL. Exact pin, Option B (`query: { version: 5 }`
only), Node engines pre-flight, and AGENTS pin sync always apply.

Evidence: 2026-10-05 Admin Orval 8.32→8.39 (#658) — empty codegen diff, 112 vitest green → O0.

## Crypto-adjacent and ecosystem gates

- **Crypto-adjacent** (e.g. `js-sha256`, keystore / crypto / proof-token): never autonomous on a
  **major**; route to security owner (Christophe). *If it ain't broken, don't fix it.*
- **Unchanged gates:** Jest 30 / ESLint 10 mobile (RN presets), TypeScript 7
  (`deferred:later-train`), RN-core / vision-camera coupled slice → HITL / defer as today.

## Majeures / Major upgrades

Platform / runtime / base-image **line** changes are tracked in
[`../major-upgrades/`](../major-upgrades/) (`registry.yaml`). They are reviewed on the full Monday
pass in January / April / July / October (not weekday light). Light path vs dedicated issue,
`security: true` opinion rules, and test-plan floors: lane README + root `AGENTS.md` § *Major
upgrades*. Crypto/auth **library** majors stay in this Dependabot lane (T4). Monthly
endoflife.date check: upcoming, not present yet.

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
| `#627` | RN 0.87 platform lot closeout (ex-`#582`–`#585`) | Versions on main **done** (RN 0.87.1 / React 19.3.0 + navigation / vision-camera / safe-area); **Pixel smoke pending** — close `#627` when smoke is recorded |

As of 2026-09-26: Dependabot PRs `#582`–`#585` were **closed without merge** (Dependabot
supersede 2026-09-22) after chantier F landed the versions otherwise. Residual human closeout is
**issue `#627`**, not those PRs — do not reopen them.
**2026-09-28 hebdo:** active Dependabot queue empty (only deferred `#337`/`#498`); `#627` Pixel
smoke still pending — see [`2026-09-28-pass-1.md`](2026-09-28-pass-1.md).
**2026-10-05 hebdo:** Lots A–D squash-merged (Maven + Admin/SDK + mobile tooling/runtime); held
`#647` (Admin Orval T4) and `#653` (Jest 30 / RN Jest 29 gate) comment-only; `#627` Pixel smoke
still pending (next Play AAB after `#650`/`#651`) — see [`2026-10-05-pass-1.md`](2026-10-05-pass-1.md).

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

Do **not** add Docker image tags to this pulse unless the operator asks. Tink / ShedLock /
ipaddress now follow parent properties (see
[`handoff-centralize-core-pins.md`](handoff-centralize-core-pins.md)).

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

**Standing policy — mobile ESLint / Jest:** leave them ungrouped. `deps:monitor` ecosystem gates (`ESLint 10`, `Jest 30`) mean those majors are not clear T1–T3 tooling-only; batching them inside `mobile-tooling` hides the gate and invites false autonomy.

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
