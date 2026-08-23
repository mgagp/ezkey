# Dependabot curated — campaign notes

Lightweight HITL decision track for punctual `dependabot-curated` passes (weekly Dependabot PR
triage and batched merges).

This folder is **peripheral** to product vision / ADR / backlog execution. It records per-campaign
lot decisions (merge / hold / defer) so cold sessions can see *why* a PR was batched or left alone —
without inventing `I-*` / `TB-*` for routine bumps.

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
| `#337`, `#450`, `#347` | TypeScript 7 (SDK + Admin UI group + migration idea) | TS 7.1 / typescript-eslint Node API readiness (~months), not routine weekly passes |

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
