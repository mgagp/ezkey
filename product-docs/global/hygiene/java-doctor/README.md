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

## Related

- Keyword contract: root [`AGENTS.md`](../../../AGENTS.md) § Java doctor-curated
- Machine suppressions: [`config/java-doctor/suppressions.json`](../../../config/java-doctor/suppressions.json)
- Evaluation: [`../java-doctor-curated-evaluation-2026-07-11.md`](../java-doctor-curated-evaluation-2026-07-11.md)
