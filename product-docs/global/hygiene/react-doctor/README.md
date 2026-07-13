# Admin UI doctor-curated — campaign notes

Lightweight HITL decision track for punctual `doctor-curated` passes (Admin UI React).

This folder is **peripheral** to product vision / ADR / backlog execution. It records per-campaign
triage (fix / suppress / skip) so cold sessions can see *why* a finding was acted on or left alone
— without inventing `I-*` / `TB-*` / GitHub issues per finding.

Sibling lane: [`../java-doctor/`](../java-doctor/) (`java-doctor-curated`).

## Contents

| Path | Role |
|------|------|
| [`TEMPLATE.md`](TEMPLATE.md) | Copy for each new campaign / lot |
| `YYYY-MM-DD-pass-N.md` | Dated instance (decisions table + short rationale) |

## Related

- Keyword contract: [`ezkey-admin-ui/AGENTS.md`](../../../ezkey-admin-ui/AGENTS.md) § React Doctor curated pass
- Root pointer: [`AGENTS.md`](../../../AGENTS.md) § Admin UI lint-polish keyword
- Machine suppressions: `SUPPRESSED_RULES` in [`ezkey-admin-ui/scripts/doctor-curated.mjs`](../../../ezkey-admin-ui/scripts/doctor-curated.mjs)
- Hygiene index: [`../README.md`](../README.md)
