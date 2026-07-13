# Hygiene campaign notes

Peripheral HITL decision tracks for punctual **doctor-curated** passes (Admin UI React and Java).

These folders are **not** product vision, ADR, or backlog execution. They record per-campaign triage
(fix / suppress / skip) so cold sessions can see *why* a finding was acted on or left alone —
without inventing `I-*` / `TB-*` / GitHub issues per finding.

Both lanes share the same operating model: curator shortlist → small lot → interactive HITL →
campaign note → hygiene branch + PR. See root [`AGENTS.md`](../../AGENTS.md) for keywords.

## Lanes

| Path | Keyword | Role |
|------|---------|------|
| [`react-doctor/`](react-doctor/) | `doctor-curated` | Admin UI React Doctor curated passes |
| [`java-doctor/`](java-doctor/) | `java-doctor-curated` | Java SpotBugs / Semgrep / PMD curated passes |

## Hygiene vs program

Routine polish stays on the lightweight hygiene path (commit/PR + these notes). Aligns with
[`methodology/decisions/2026-06-06-methodological-closeout-vs-code-hygiene.md`](../../methodology/decisions/2026-06-06-methodological-closeout-vs-code-hygiene.md).
