# Ezkey Methodology

## Ablation note (2026-08)

This pack was condensed from roughly 10,000 lines (21 workflow documents, 32 dated decisions, 14
skills, a rich HTML view) to this single document, following the same ablation principle Anthropic
engineer Boris Cherny describes for system prompts: delete, then watch what a cold agent actually
needs. Everything that was ceremony compensating for weaker models is gone. What is left is the
three things IDE/agent Plan mode does not carry on its own. Git history is the archive — nothing
was moved to an `archive/` folder. See [`release-notes/`](release-notes/) for the version record.

## Posture: Plan mode first

Use IDE/agent **Plan mode** for research, challenging assumptions, and comparing alternatives.
That capability improves with every model release, funded and refined at a scale no single project
can match. This methodology is not a competing workflow — it exists only to carry what a Plan mode
session, by design, does not: continuity across sessions and months.

## The three things this methodology carries

### 1. Documentary levels

Four artifact types, each with its own lifecycle:

| Prefix | Artifact | Status vocabulary |
| --- | --- | --- |
| `V-*` | Vision note — directional product orientation | `draft` → `under-review` → `promoted` / `archived` |
| `I-*` | Backlog idea — a concrete idea not yet execution-ready | `captured` → `triaged` → `incubating` → `ready` → `active` → `done` / `parked` / `archived` / `dropped` |
| `TB-*` | Tracer bullet — a bounded, execution-ready vertical slice | `draft` → `under-review` → `promoted` / `archived` |
| `ADR-*` | Architecture or design decision | `proposed` / `accepted` / `superseded-by-<ADR-id>` / `deprecated` |

**Identifiers:** `<Prefix>-YYYY-MM-DD-<slug>.md`. The date avoids collisions across parallel work;
the slug keeps the subject discoverable. No counter or index file to consult before creating one.

**Priority (when it matters):** `P0` critical blocker · `P1` most pressing / foundational · `P2`
standard · `P3` comfort, safe to defer.

**Choose the lightest artifact that protects the decision:**

| Situation | Artifact |
| --- | --- |
| Small local change, clear intent | None — fix and validate directly |
| New idea, unclear value or scope | `I-*` |
| Directional or product-wide question | `V-*` |
| Execution-ready, non-trivial slice | `TB-*` |
| A design choice with real trade-offs | `ADR-*`, next to the code it governs |

A `V-*` is not itself the final decision — when the direction is settled, canonize the substance
in the durable doc it belongs to (roadmap, design principles, an `ADR-*`) and move the vision note
to `promoted`. A `TB-*` follows the same pattern for execution learnings.

When an artifact is archived because its substance moved into a successor rather than being
dropped, record it both ways: `Superseded by: <id>` on the old one, `Supersedes: <id>` on the new
one.

### 2. Bidirectional discoverability

Every artifact carries enough metadata that a cold agent — no memory of the prior session — can
resume from the corpus alone:

- **ID, status, created/updated dates.**
- **Captured by:** the human who originated the idea, not the agent that wrote the file.
- **Links**, both directions: an `I-*` points forward to the `TB-*` it produced and back to the
  `V-*` it came from, if any; code comments and commit messages may cite the artifact ID they
  implement.

This is the property Plan mode does not have by default: a session started cold has no way to know
what a session three months ago decided, unless the corpus carries it.

### 3. The values compass

Not rules to apply mechanically — a compass for when no precise rule applies:

- **Proportional rigor.** Use the lightest process that still protects the quality of the decision.
  Ask: *what concrete risk does this extra artifact or gate reduce?*
- **Simplicity and pragmatism (80/20).** Target roughly 80% of the value with 20% of the
  complexity.
- **Essential vs. accidental complexity.** Essential complexity is fine when the problem genuinely
  requires it. An abstraction or process layer that does not materially improve the outcome does
  not belong.
- **One canonical place per concept.** Link to the source of truth; never silently duplicate it.
- **Fail-open vs. fail-closed at real boundaries.** When a control can fail, name whether the
  primary path *continues* (fail-open — keep the failure observable) or *stops* (fail-closed — when
  continuing would silently weaken a claimed guarantee). A one-line compass, not a ceremony for
  every call site.
- **Stay in the chosen stack.** Prefer the idioms developers already expect; a new dependency or
  framework needs explicit justification.

## Three rules worth keeping

The rest of the corpus is gone, but these three survived the ablation because real sessions kept
needing them and they were too easy to miss buried on page four of a workflow document.

### Hygiene vs. program

An invitation to "close this methodologically" is not, by itself, permission to create `I-*` or
`TB-*` artifacts. Classify first:

- **Hygiene** — a known recipe, one bounded change, no new contract with consumers: a commit or PR
  plus a targeted doc touch (`AGENTS.md`, a module README) is enough. No new artifact.
- **Program** — multi-step, a new contract, cross-module coordination, or genuinely new
  uncertainty: `I-*` and/or `TB-*`, proportional to the program, not reflexively all of them.

If asked for full artifacts and the work is hygiene-shaped, say so and propose the lighter path
before proceeding.

### Ephemeral scaffold vs. retained plan

A Plan-mode working file is scaffolding by default. When its signal is fully captured in a
`V-*`/`I-*`/`TB-*`, do not copy it into the repository just to satisfy a traceability habit, and
never link a corpus document to a path outside the clone. Promote a plan file into the repository
only when it still carries option space, rejected alternatives, or execution notes the canon
should not flatten.

### Closed uncertainty stays closed

Once an uncertainty has been addressed and recorded, treat it as closed. Do not reopen it without a
genuinely new signal. Re-reading settled ground "for alignment" produces summary on summary, not
clarity — recognizing when something is already sufficient is a discipline worth applying to the
corpus itself, not only to code.

## GitHub issues (optional)

An optional visibility layer, not a second source of truth — the artifact above remains canonical.
When you open an issue, apply all five label groups (`lane:*`, `type:*`, `component:*`,
`priority:*`, `status:*`) at create time; see
[`.cursor/rules/github-issue-labels.mdc`](../../.cursor/rules/github-issue-labels.mdc). Link back
to the `I-*`/`TB-*` in the issue body instead of duplicating its content.

## Templates

Four kept, under [`../templates/`](../templates/): `vision-note`, `backlog-idea`,
`tracer-bullet-brief`, `architecture-decision`. Copy, fill in, delete sections that do not apply.

## Versioning

Independent SemVer in [`../methodology-version.properties`](../methodology-version.properties);
see [`release-notes/`](release-notes/) for published snapshots.
