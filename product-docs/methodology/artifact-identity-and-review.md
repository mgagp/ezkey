# Artifact Identity and Review

## Purpose

This document explains two related choices in the Ezkey Methodology:

- why methodology artifacts use repository-native identifiers such as `I-YYYY-MM-DD-<slug>` instead
  of depending on an external tracker ID;
- how review is handled without adding a permanent `Reviewed by` field to every template.

The goal is not to reject issue trackers. The goal is to keep the methodology self-contained enough
that a human or AI agent can understand the product state from the corpus itself.

## Repository-native artifact identity

The core methodology artifacts are named from their role, date, and subject:

```text
V-YYYY-MM-DD-<slug>
I-YYYY-MM-DD-<slug>
TB-YYYY-MM-DD-<slug>
R-YYYY-MM-DD-<slug>
```

This naming model is intentional:

- The role prefix says what kind of artifact this is.
- The date makes creation cheap and strongly reduces collisions.
- The slug keeps the topic discoverable without opening another system.
- The identifier remains valid even when no issue, branch, or pull request exists yet.

This lets early analysis, design, test planning, and methodology feedback live directly in the
project documentation corpus. A captured idea does not need to become a tracker item before it has
enough substance. Conversely, an issue or branch can be added later when collaboration,
implementation, or external visibility makes it useful.

## Relationship with issue trackers

External issue trackers are an optional traceability layer, not the canonical source of product
intent.

Use an issue when the work is ready for coordination: the title stands alone, the scope is
understandable, and the item benefits from assignment, milestone tracking, or contributor
discussion. Until then, the repository artifact is enough.

This distinction keeps two things true at the same time:

- teams can still use GitHub Issues, Jira, or another tracker when it provides operational value;
- the methodology does not become dependent on a tool-specific ticket lifecycle to preserve product
  reasoning.

When a tracker item exists, link it from the artifact metadata. The tracker points back to the
artifact. The durable analysis remains in the corpus.

## Main-branch documentation is valid work

It is acceptable for early methodology artifacts to land on `main`.

An artifact on `main` does not mean implementation is committed, approved, or inevitable. Its
metadata says what state it is in:

- `captured` or `draft` means the signal exists but is not yet execution-ready;
- `incubating` or `under-review` means active analysis is still happening;
- `ready`, `active`, `done`, `promoted`, `archived`, and `dropped` distinguish the later outcomes.

This is why lifecycle metadata matters. It lets the corpus carry incomplete, abandoned, or evolving
work without confusing it with implemented truth.

Branches are created when implementation work benefits from isolation. They are not required merely
to make an idea legitimate.

## Review attribution

Do not add a blanket `Reviewed by` metadata field to every methodology template.

The default review attribution mechanisms are:

- Git history for who changed the document and when;
- pull request comments or review records when a PR exists;
- explicit status transitions and closeout notes when review changes the artifact's lifecycle;
- decision records when review produces a reusable methodological or product decision.

This keeps the base templates small. Review metadata should be added only when it changes how the
artifact is interpreted. For example:

- a decision table may record who approved a rule set if that approval is part of the governance
  model;
- a test plan may record execution evidence and owner because validation accountability matters;
- a methodology decision may preserve short source signal when exact wording explains the rule.

In ordinary collaborative editing, the diff is the attribution record. The corpus should not repeat
what Git already records unless the review result changes the product or methodology state.

## Review as a lifecycle check

Review is still part of the software development lifecycle. The method expects review pressure at
the points where a wrong decision would be expensive:

- vision review: is the direction still valid and worth promoting?
- idea review: is the scope clear enough to challenge or split?
- design review: are boundaries, mappings, validation rules, and exception paths explicit?
- test-plan review: is the selected evidence proportional to risk?
- tracer-bullet review: did the slice prove what it was supposed to prove?
- closeout review: are statuses, links, residual risks, and durable decisions updated?

The review output should normally be one of:

- a status change;
- a small edit to the artifact;
- a linked decision;
- a follow-up artifact;
- an explicit `parked`, `archived`, or `dropped` outcome.

Avoid review theater. A review that leaves no decision, status change, correction, or evidence does
not need a new metadata field just to prove that it happened.

## Practical rule

Use the repository artifact as the durable source of reasoning.

Use the tracker, branch, pull request, and review record when they add coordination value.

Link them together, but do not let any one tool become the only place where the method can be
understood.
