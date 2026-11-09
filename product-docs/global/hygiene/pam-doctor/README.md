# PAM doctor-curated — campaign notes

Lightweight HITL decision track for punctual `pam-doctor-curated` passes on the
Linux PAM module (`ezkey-pam/src`).

This folder is **peripheral** to product vision / ADR / backlog execution. It records
per-campaign triage (fix / suppress / skip) so a later session can see why a finding
was acted on or left alone. Do **not** invent `I-*` / `TB-*` / GitHub issues per finding.

The macOS install track is out of this lane.

## Contents

| Path | Role |
|------|------|
| [`TEMPLATE.md`](TEMPLATE.md) | Copy for each new campaign / lot |
| `YYYY-MM-DD-pass-N.md` | Dated instance (shortlist, context, decisions) |

## Related

- Hygiene index: [`../README.md`](../README.md)
- Keyword contract: root [`AGENTS.md`](../../../../AGENTS.md) § PAM doctor-curated
- Skill: [`.cursor/skills/pam-doctor-curated/SKILL.md`](../../../../.cursor/skills/pam-doctor-curated/SKILL.md)
- Layout and checks: [`config/pam-doctor/README.md`](../../../../config/pam-doctor/README.md)
- Machine suppressions: [`config/pam-doctor/suppressions.json`](../../../../config/pam-doctor/suppressions.json)
- GitHub: [#1269](https://github.com/mgagp/ezkey/issues/1269)
- First pass (static): [`2026-11-08-pass-1.md`](2026-11-08-pass-1.md)
- Sanitizer replay: `./scripts/pam-doctor-sanitizers.sh` (ASan, UBSan, Valgrind; not a CI gate)
