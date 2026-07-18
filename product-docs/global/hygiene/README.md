# Hygiene campaign notes

Peripheral HITL decision tracks for punctual **doctor-curated** passes (Admin UI React and Java),
weekly **Dependabot** triage, and punctual **mobile protocol / crypto** assessment follow-ups.

These folders are **not** product vision, ADR, or backlog execution. They record per-campaign triage
(fix / suppress / skip / defer, or Dependabot merge / hold / defer) so cold sessions can see *why* a
finding or dependency PR was acted on or left alone — without inventing `I-*` / `TB-*` / GitHub
issues per routine item.

Doctor lanes share the operating model: curator shortlist → small lot → interactive HITL →
campaign note → hygiene branch + PR. Dependabot uses the same HITL shape with risk-tiered PR lots
and a session closeout validation ladder. Mobile protocol / crypto uses the same one-finding-at-a-time
HITL shape after a formal assessment register. See root [`AGENTS.md`](../../AGENTS.md) for keywords.

## Lanes

| Path | Keyword | Role |
|------|---------|------|
| [`react-doctor/`](react-doctor/) | `doctor-curated` | Admin UI React Doctor curated passes |
| [`java-doctor/`](java-doctor/) | `java-doctor-curated` | Java SpotBugs / Semgrep / PMD curated passes |
| [`dependabot/`](dependabot/) | `dependabot-curated` | Weekly Dependabot PR triage and batched merges |
| [`mobile-protocol-security/`](mobile-protocol-security/) | _(assessment HITL)_ | Mobile protocol / crypto white-box assessment follow-ups |

## Hygiene vs program

Routine polish stays on the lightweight hygiene path (commit/PR + these notes). Aligns with
[`methodology/decisions/2026-06-06-methodological-closeout-vs-code-hygiene.md`](../../methodology/decisions/2026-06-06-methodological-closeout-vs-code-hygiene.md).
