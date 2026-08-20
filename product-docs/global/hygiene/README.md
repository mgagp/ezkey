# Hygiene campaign notes

Peripheral HITL decision tracks for punctual **doctor-curated** passes (Admin UI React, Java,
mobile static analysis), weekly **Dependabot** triage, **security-pentest** campaigns,
mandate-driven **assessment-curated** white-box investigation follow-ups, and **corpus-ablation**
passes that prune ephemeral plans/prompts into existing canon.

These folders are **not** product vision, ADR, or backlog execution. They record per-campaign triage
(fix / suppress / skip / defer, or Dependabot merge / hold / defer) so cold sessions can see *why* a
finding or dependency PR was acted on or left alone — without inventing `I-*` / `TB-*` / GitHub
issues per routine item.

Doctor / Dependabot / pentest lanes start from **tool shortlists**. **assessment-curated** starts
from an operator **mandate** + white-box assessment register, then the same one-finding-at-a-time
HITL shape. See root [`AGENTS.md`](../../AGENTS.md) for keywords.

## Lanes

| Path | Keyword | Role |
|------|---------|------|
| [`assessment-curated/`](assessment-curated/) | `assessment-curated` | **Method canon** — mandate → assessment → HITL → handoff |
| [`mobile-protocol-security/`](mobile-protocol-security/) | `assessment-curated` (instance) | Mobile protocol / crypto assessment campaigns |
| [`java-tink-deprecations/`](java-tink-deprecations/) | `assessment-curated` (instance) | Java Tink deprecated API migration campaigns |
| [`java-jpa-deprecations/`](java-jpa-deprecations/) | `assessment-curated` (instance) | Java JPA / Spring Data deprecated API inventory |
| [`java-json-deprecations/`](java-json-deprecations/) | `assessment-curated` (instance) | Java JSON / Jackson 3 deprecated tree APIs |
| [`java-bucket4j-deprecations/`](java-bucket4j-deprecations/) | `assessment-curated` (instance) | Java Bucket4j deprecated bandwidth / refill APIs |
| [`java-native-compilation/`](java-native-compilation/) | `assessment-curated` (instance) | Java GraalVM / native-image residual cleanup |
| [`java-controller-dead-paths/`](java-controller-dead-paths/) | `assessment-curated` (instance) | Java controller dead / phantom path hygiene |
| [`java-controller-role-validation/`](java-controller-role-validation/) | `assessment-curated` (instance) | Java controller role / tenant-boundary coherence |
| [`react-doctor/`](react-doctor/) | `doctor-curated` | Admin UI React Doctor curated passes |
| [`java-doctor/`](java-doctor/) | `java-doctor-curated` | Java SpotBugs / Semgrep / PMD curated passes |
| [`mobile-doctor/`](mobile-doctor/) | `mobile-doctor-curated` | Ezkey Mobile static-analysis curated passes |
| [`dependabot/`](dependabot/) | `dependabot-curated` | Weekly Dependabot PR triage and batched merges |
| [`security-pentest/`](security-pentest/) | `security-pentest-curated` | Live API / DAST-style security campaigns |
| [`corpus-ablation/`](corpus-ablation/) | `corpus-ablation` | Cursor plans / prompt scaffolds → canon + discoverability; skill `.cursor/skills/corpus-ablation` |

## Hygiene vs program

Routine polish stays on the lightweight hygiene path (commit/PR + these notes). Aligns with
[`methodology/decisions/2026-06-06-methodological-closeout-vs-code-hygiene.md`](../../methodology/decisions/2026-06-06-methodological-closeout-vs-code-hygiene.md).
