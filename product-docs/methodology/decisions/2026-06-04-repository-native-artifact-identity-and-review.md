---
public: true
---
# Repository-native artifact identity and review attribution

## Date

2026-06-04

## Context

The methodology is being adapted in environments where tracker-centric habits are common: analysis,
design, bugs, backlog items, and implementation slices are often represented first as tickets. That
model is useful for coordination, but it can make the tracker feel like the only legitimate place
where product work exists.

The Ezkey Methodology made a different foundational choice: methodology artifacts can live directly
in the repository documentation corpus with stable date-and-slug identifiers. This makes early
analysis possible before a ticket or branch exists and keeps product reasoning discoverable to both
humans and AI agents.

A related question is whether review attribution should become another required metadata field in
every template.

## Source signal

- Date-and-slug artifact IDs reduce collisions and avoid coordination overhead.
- Repository-native artifacts let ideas, analysis, design, test planning, and tracer bullets exist
  before an implementation branch is opened.
- Lifecycle metadata prevents incomplete or abandoned work from being confused with implemented
  truth.
- Git history and PR review records already attribute ordinary review edits.
- Additional review metadata should be reserved for cases where it changes the artifact's meaning or
  governance state.

## Options considered

| Option | Advantages | Disadvantages |
| --- | --- | --- |
| **A. Tracker-first identity** | Familiar in corporate delivery environments; central assignment and milestone tracking | Couples the method to a tool lifecycle; encourages one-line tickets before analysis has substance |
| **B. Repository-native identity with optional tracker links** | Keeps reasoning self-contained; supports early analysis and AI-readable traceability; still allows external coordination | Requires discipline around lifecycle metadata and cross-links |
| **C. Add required `Reviewed by` metadata everywhere** | Makes review attribution visible in each artifact | Adds ceremony and duplicates Git/PR history for ordinary edits |
| **D. Use Git/PR history by default; add review metadata only when it changes governance meaning** | Keeps templates light; preserves accountability through existing tools | Requires judgment about exceptional cases |

## Decision

Adopt **Option B** for artifact identity and **Option D** for review attribution.

Methodology artifacts keep repository-native identifiers such as `I-YYYY-MM-DD-<slug>` and
`TB-YYYY-MM-DD-<slug>`. Issue trackers, branches, pull requests, and review records are optional
coordination layers that link to the corpus; they do not replace it as the durable source of
reasoning.

Do not add a required `Reviewed by` field to all templates. Use Git history, PR review records,
status transitions, closeout notes, and decision records as the normal review attribution path.
Add explicit review metadata only when the review outcome is itself part of the artifact's meaning
or governance requirement.

## Consequences

- The methodology remains portable across GitHub Issues, Jira, or no external tracker.
- Early artifacts may land on `main` without implying implementation commitment, provided lifecycle
  status is explicit.
- Branches are created when implementation isolation is useful, not merely because an idea exists.
- Templates stay minimal; review fields are not propagated mechanically.
- Review remains a lifecycle pressure through vision, idea, design, test-plan, tracer-bullet, and
  closeout checks.

## Related documents

- [`../artifact-identity-and-review.md`](../artifact-identity-and-review.md)
- [`../nomenclature.md`](../nomenclature.md)
- [`../github-issues-workflow.md`](../github-issues-workflow.md)
- [`../multi-branch-workflow.md`](../multi-branch-workflow.md)
- [`../methodological-values.md`](../methodological-values.md)
