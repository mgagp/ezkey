---
public: true
---
# Methodological closeout invitations vs code hygiene

## Date

2026-06-06

## Context

After the Admin UI Orval program (`I-2026-05-28`, multi-PR ladder, checkpoints 8.10 / 8.11), a
follow-up mobile track bumped Orval **8.14.0 → 8.15.0**, applied the already-known Option B config
fix, regenerated committed Auth API output, and passed standard validation plus release device smoke
(enrollment + authentication).

An operator invitation to produce a **methodological closeout** led an agent to create a full
`I-*` / `TB-*` / `TSP-*` / `ML-*` set. That was disproportionate: the work was **code and
toolchain hygiene** applying a validated recipe, not a new discovery program.

The over-materialization was corrected (artifacts removed; hygiene closed with commit + `AGENTS.md`
updates only).

## Historical interpretation note

This record preserves `ML-*` as it was named on the decision date: a program-path option alongside
`I-*`/`TB-*`/`TSP-*`. `ML-*` never received its own nomenclature entry, template, or workflow-stage
row, and was retired as a distinct artifact family in
[`2026-07-28-retire-method-log-artifact-type.md`](2026-07-28-retire-method-log-artifact-type.md).
The substance of **this** decision — classify hygiene vs program before materializing canonical
artifacts — is unaffected; only the `ML-*` option within the program path is superseded. Read
"`ML-*`" below as historical; current guidance routes that content to a `TB-*`/`I-*` Closeout
section (program narrative) or a new methodology decision (reusable process lesson).

## Source signal

- "Close this properly" or "methodological closeout" does not automatically mean "create canonical
  backlog artifacts."
- Proportional rigor and earned permanence already exist in the methodology but did not stop reflex
  application of a completed program's playbook to a lightweight follow-up.
- Agents should **challenge** methodological invitations when the change profile is hygiene-shaped.

## Options considered

| Option | Advantages | Disadvantages |
| --- | --- | --- |
| **A. Always create I/TB/TSP on closeout invitations** | Uniform traceability | Inflates backlog; confuses hygiene with programs; maintenance cost without new learning |
| **B. Agent classifies hygiene vs program; challenges invitation when hygiene** | Proportional; preserves fast path; reuses existing values | Requires explicit judgment and operator-visible rationale |
| **C. Ban closeout skill for non-TB work** | Hard guardrail | Too rigid; some small programs still need honest closure |

## Decision

Adopt **Option B**.

When an operator invites methodological closeout, documentation, or traceability for a completed
change, the agent must **classify first** and state the classification before creating canonical
artifacts.

### Hygiene path (default for many toolchain bumps)

Use when most hygiene signals are true (see checklist in
[`minimum-viable-method.md`](../minimum-viable-method.md)):

- commit (or PR) with a clear message;
- targeted local docs (`AGENTS.md`, module README, config comments) when durable;
- optional GitHub issue/PR labels for visibility;
- **no new** `I-*`, `TB-*`, `TSP-*`, or `ML-*` unless the operator explicitly overrides after the
  challenge.

### Program path

Use when uncertainty, contract migration, multi-step execution, or new learning justify canonical
artifacts:

- `I-*` when intent or scope still needs capture;
- `TB-*` (+ `TSP-*` when test evidence needs a standing record) for bounded execution programs;
- `ML-*` or methodology decision when the session teaches a reusable process lesson.

### Agent obligation

If the operator requests methodological artifacts and the agent classifies **hygiene**, respond
with:

1. the hygiene vs program classification and why;
2. the lighter closeout proposed (commit/PR + doc touchpoints);
3. what would need to be true to justify full artifacts.

Proceed with full artifacts only after explicit operator confirmation or when program signals are
clear without override.

## Evidence

- Mobile Orval 8.14 → 8.15 (2026-06-06): hygiene closeout sufficient; full artifact set reverted.
- Admin UI Dependabot Orval 8.13 → 8.15 (#190): merged without a new I/TB/TSP program — same class.

## Validation signal

This decision helps if a future agent, invited to "close methodologically," proposes hygiene first
and only materializes `I-*` / `TB-*` when program criteria are met or the operator insists after
seeing the challenge.
