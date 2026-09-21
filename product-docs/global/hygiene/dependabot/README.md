# Dependabot curated — campaign notes

Lightweight HITL decision track for punctual `dependabot-curated` passes (weekly Dependabot PR
triage and batched merges).

This folder is **peripheral** to product vision / ADR / backlog execution. It records per-campaign
lot decisions (merge / hold / defer) so cold sessions can see *why* a PR was batched or left alone —
without inventing `I-*` / `TB-*` for routine bumps.

## What we cover

Weekly Dependabot ecosystems in [`.github/dependabot.yml`](../../../.github/dependabot.yml), plus
mandatory pulses that Dependabot alone cannot prove current:

| Surface | Dependabot | Mandatory pulse |
|---------|------------|-----------------|
| Java / Maven (reactor root) | `maven` at `/` | **Java BOM pulse** (`spring-boot.version`, SEC-019, `google-java-format`) |
| Admin UI | `npm` at `/ezkey-admin-ui` | — (PRs + groups) |
| Ezkey Mobile (Yarn 4) | `npm` at `/ezkey_mobile` | **Mobile RN pulse** (`yarn deps:monitor` + coupled RN escalators) |
| SDK JS | `npm` at `/ezkey-sdk/javascript` | — |
| GitHub Actions | `github-actions` at `/` | — |
| Python CLI | `pip` at `/ezkey-cli-python` | — |

Empty Dependabot queue for Maven or mobile ≠ stack current. Authority: skill
`dependabot-curated` §§ *Java BOM pulse*, *Mobile RN pulse*.

## Contents

| Path | Role |
|------|------|
| [`TEMPLATE.md`](TEMPLATE.md) | Copy for each new campaign |
| `YYYY-MM-DD-pass-N.md` | Dated instance (lots table + validation evidence) |
| [`handoff-centralize-core-pins.md`](handoff-centralize-core-pins.md) | Implemented on `hygiene/java-dependency-pins`: Tink / ShedLock / ipaddress now parent-pinned |

## Standing deferrals (`deferred:later-train`)

PRs labeled **`deferred:later-train`** are **out of weekly lot HITL**. Cold agents list them once
under *Already deferred — skip HITL*, then triage only unlabeled (or non-deferred) Dependabot PRs.

| PRs | Topic | Re-evaluate when |
|-----|--------|------------------|
| `#337`, `#498`, `#347` | TypeScript 7 (SDK + Admin UI group + migration idea) | TS 7.1 / typescript-eslint Node API readiness (~months), not routine weekly passes |

Note: Dependabot `#450` (Admin UI TS 7 group) was superseded/closed when `#498` opened; keep `#498`
in the standing set. See empty-queue pass [`2026-09-21-pass-1.md`](2026-09-21-pass-1.md).

To park a new disruptor for weeks/months: comment + `gh pr edit <n> --add-label deferred:later-train`.

## Preferred weekly posture (lots without duplication)

**Default apply path:** triage into risk lots, then **merge the existing Dependabot PRs** in that
lot (individually, after CI / local validation for the lot). That closes GitHub PRs as you go and
avoids duplicate open PRs.

**Hygiene-branch path** (re-apply bumps onto `hygiene/dependabot-…`): only when the operator wants
a single reviewable PR with companion fixes, or when Dependabot branches cannot merge cleanly.
After the hygiene PR lands on `main`, **close superseded Dependabot PRs** with a short
“already integrated via #NNN” comment — they will not auto-close.

**Autonomous validation:** opt-in phrase for cold agents is documented in the skill
(`dependabot-curated` § *Autonomous validation mode*). Autonomy means the agent owns the closeout
ladder and evidence; it does **not** mean inventing a second integration path by default.

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

```text
dependabot-curated, autonomous.
GH_TOKEN write authorized: squash-merge Dependabot PRs; comment/close superseded PRs after a hygiene PR lands.
```

That last sentence is the explicit write waiver the Cloud harness asks for. Without it, a cold
agent may still pick the hygiene-branch exception even though `GH_TOKEN` is in the environment.

## Java BOM pulse (weekly, not optional)

Dependabot Maven updates **declared** POM versions. It does not inventory Boot-managed transitives
(Hibernate, Spring Framework, Spring Security, Flyway, …). Those move only with
`spring-boot.version`. An empty Maven PR queue is not proof that Java is current — the weekly
limit of 5 open PRs can starve a Boot property bump.

On every `dependabot-curated` pass the agent must:

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

## Mobile RN pulse (weekly, not optional — parity with Java BOM pulse)

Dependabot npm at `/ezkey_mobile` (Yarn 4) opens grouped PRs for declared bumps. It does not
replace the mobile stack inventory. Groups:

| Group | Intent |
|-------|--------|
| `mobile-tooling` | Prettier / Husky / lint-staged / TypeScript / Babel **only**. Excludes `eslint`, `eslint-*`, `@eslint/*`, `jest`, `@types/jest` so gated majors stay **ungrouped** (solo PRs; never auto-merged). Provenance: #581 closed after HITL — that group had bundled ESLint 10 + Jest 30 with safe Babel patches. |
| `mobile-rn-core` | `react` / `react-native` / presets / CLI / test-renderer types |
| `mobile-vision-camera` | VisionCamera + worklets + nitro family (coupled with RN) |
| `mobile-navigation` | `@react-navigation/*` |

**`orval` is not grouped** — solo PRs, T4 hard escalator, exact pin (no caret).

**Standing policy — mobile ESLint / Jest:** leave them ungrouped. `deps:monitor` ecosystem gates (`ESLint 10`, `Jest 30`) mean those majors are not clear T1–T3 tooling-only; batching them inside `mobile-tooling` hides the gate and invites false autonomy.

On every `dependabot-curated` pass the agent must:

1. Record declared `react` / `react-native` / key coupled libs from `ezkey_mobile/package.json`.
2. Run `yarn deps:monitor` (or `node scripts/dependency-monitor.mjs`) from `ezkey_mobile/` and
   capture actionable vs ecosystem-gated deferred (ESLint 10, Jest 30, TS7) plus high audit in
   the campaign note. Empty Dependabot mobile queue ≠ stack current.
3. Peel Dependabot mobile PRs under T1–T4. If no mobile PRs but the monitor shows actionable
   upgrades, propose lots from the pulse (hygiene-branch only when no PR exists).
4. Escalators: RN-core coupled slice = T3 minimum (major RN line → T4 HITL); vision-camera /
   worklets / nitro stay coupled with RN (never silent-batch with tooling); Orval = T4 + generate
   + unit tests. Autonomy may merge tooling-only T1–T3; never auto-merge RN-core / vision-camera /
   orval without Marc unless an Orval-mobile cheap exception is already standing routine.

Authority: skill `dependabot-curated` § *Mobile RN pulse*.

## Related

- Hygiene index: [`../README.md`](../README.md)
- Keyword contract: root [`AGENTS.md`](../../../AGENTS.md) § Dependabot curated
- Skill: [`.cursor/skills/dependabot-curated/SKILL.md`](../../../.cursor/skills/dependabot-curated/SKILL.md)
- Dependabot config: [`.github/dependabot.yml`](../../../.github/dependabot.yml)
- Hygiene vs program: [`../../methodology/decisions/2026-06-06-methodological-closeout-vs-code-hygiene.md`](../../methodology/decisions/2026-06-06-methodological-closeout-vs-code-hygiene.md)
- Sibling lanes: [`../react-doctor/`](../react-doctor/), [`../java-doctor/`](../java-doctor/)

## Closeout reminder (pins / install / codegen)

When a pass merges Orval or another **exact-pin** / codegen dependency, the campaign note must cover
exact pin preservation, `AGENTS.md` pin sync, workspace install, and regenerate — see skill
`dependabot-curated` § *Pin, install, and codegen hygiene*. This is not part of `doctor-curated`.
