# Skills

Project skills for Claude Code and Cursor (Cursor also reads `.claude/skills/`). Each skill is a
directory with a `SKILL.md`. The root [`AGENTS.md`](../../AGENTS.md) § Skills lists the keywords.

Lane skills set `disable-model-invocation: true`: the operator starts them by keyword or
`/keyword`, and their descriptions cost no context. `github-issue` is the exception: the agent uses
it whenever it opens an issue.

| Skill | Purpose |
|---|---|
| `assessment-curated` | Mandate-driven white-box assessment (HITL + handoff) |
| `corpus-ablation` | Prune ephemeral scaffolds into canon |
| `critical-review` | Read-only adversarial review, different model family |
| `dependabot-curated` | Dependabot triage and merges |
| `doctor-curated` | Admin UI React Doctor pass |
| `document-hygiene-curated` | Legacy document canonicality and archive-vs-delete |
| `github-issue` | Open an issue with the five mandatory label groups |
| `java-doctor-curated` | Java SpotBugs / Semgrep / PMD pass |
| `javamelody-curated` | JavaMelody performance pass |
| `mobile-doctor-curated` | Ezkey Mobile static-analysis pass |
| `monthly-digest` | Monthly activity digest for ezkey.org |
| `pam-doctor-curated` | PAM C module pass |
| `quality-gate` | On-demand full-suite gate |
| `security-pentest-curated` | Live local-stack DAST / OpenAPI campaign |

The 14 methodology-workflow skills removed in the 2026-08 ablation are recorded in
[`product-docs/methodology/README.md`](../../product-docs/methodology/README.md#ablation-note-2026-08).
