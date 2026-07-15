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

## Related

- Hygiene index: [`../README.md`](../README.md)
- Keyword contract: root [`AGENTS.md`](../../../AGENTS.md) § Dependabot curated
- Skill: [`.cursor/skills/dependabot-curated/SKILL.md`](../../../.cursor/skills/dependabot-curated/SKILL.md)
- Dependabot config: [`.github/dependabot.yml`](../../../.github/dependabot.yml)
- Hygiene vs program: [`../../methodology/decisions/2026-06-06-methodological-closeout-vs-code-hygiene.md`](../../methodology/decisions/2026-06-06-methodological-closeout-vs-code-hygiene.md)
- Sibling lanes: [`../react-doctor/`](../react-doctor/), [`../java-doctor/`](../java-doctor/)
