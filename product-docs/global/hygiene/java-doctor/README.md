# Java doctor-curated — campaign notes

Lightweight HITL decision track for punctual `java-doctor-curated` passes.

This folder is **peripheral** to product vision / ADR / backlog execution. It records per-campaign
triage (fix / suppress / skip) so cold sessions can see *why* a finding was acted on or left alone
— especially around security-sensitive code — without inventing `I-*` / `TB-*` / GitHub issues pe
finding.

## Contents

| Path | Role |
|------|------|
| [`TEMPLATE.md`](TEMPLATE.md) | Copy for each new campaign / lot |
| `YYYY-MM-DD-pass-N.md` | Dated instance (decisions table + short rationale) |
| [`2026-10-21-pass-1.md`](2026-10-21-pass-1.md) | Biweekly Java rotation triage (docs-only; dispositions, no code) |
| [`2026-08-09-illegalcatch-p2.md`](2026-08-09-illegalcatch-p2.md) | IllegalCatch P2 follow-up |
| [`2026-07-11-pass-1.md`](2026-07-11-pass-1.md) | First curated pass (fix / suppress lot) |

## Related

- Hygiene index: [`../README.md`](../README.md)
- Keyword contract: root [`AGENTS.md`](../../../AGENTS.md) § Java doctor-curated
- Machine suppressions: [`config/java-doctor/suppressions.json`](../../../config/java-doctor/suppressions.json)
- Evaluation: [`../java-doctor-curated-evaluation-2026-07-11.md`](../java-doctor-curated-evaluation-2026-07-11.md)
- Sibling lane: [`../react-doctor/`](../react-doctor/) (`doctor-curated`)
