# Java doctor-curated configuration

Pinned analyzer inputs for `./scripts/java-doctor-curated.sh` (keyword `java-doctor-curated`).

| File | Role |
|------|------|
| `pmd-ruleset.xml` | Narrow PMD design/maintainability rules (not the full catalogue) |
| `semgrep.yml` | Small Semgrep security / footgun pack |
| `spotbugs-exclude.xml` | SpotBugs Match excludes (start empty) |
| `suppressions.json` | Curator rule suppressions with written reasons |
| Campaign HITL notes | `product-docs/global/hygiene/java-doctor/` (template + dated passes) |

See the [`java-doctor-curated` skill](../../.claude/skills/java-doctor-curated/SKILL.md) (including the HITL contract) and
`product-docs/global/java-doctor-curated-evaluation-2026-07-11.md`.
