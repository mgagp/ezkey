# Tracer Bullet Iteration Posture: Explicit Gate Before Scoping

## Date

2026-05-23

## Context

During the first real application of the Ezkey methodology, a hesitation emerged at the transition
from design to implementation. The `tracer-bullet-method.md` was clear that iteration is optional
and that a single-pass TB is legitimate. However, the agent defaulted to an iterative framing —
presenting the work as "TB1, then we'll see" — even when the scope was fully bounded and the risk
was low enough for a single implementation pass.

The root cause was structural: no explicit moment in the process or the skill forced the question
*"single-pass or iterative?"* to be answered. The agent adopted the iterative posture by inertia,
not by analysis. This created unnecessary cognitive overhead and a misalignment between the framing
and the actual intent.

The discussion that produced this decision was itself an example of the methodology's
self-improvement loop — a working session used to surface and fix a process gap in real time.

## Working assumptions

**Iteration manages uncertainty, not scope.** The right trigger for an iterative approach is
*uncertainty* (unknown component interactions, unclear contracts, high risk of re-work), not
*scope size*. A well-understood, bounded, low-risk job is a legitimate single-pass implementation
regardless of how much code it touches.

**The fast path must remain fast.** Before this methodology, the standard approach was
plan → implement, with the agent helping refine the plan in dialogue. That path was fast and
effective for simple cases. The methodology adds traceability and quality gates — it must not
replace the fast path with mandatory planning overhead.

## Options considered

| Option | Advantages | Disadvantages |
|---|---|---|
| Status quo (iteration is optional, documented) | No change required | The gate question is never asked explicitly; agent defaults to iterative by inertia |
| New artifact type (`IMPL-*` for single-pass) | Makes the distinction explicit at artifact level | Adds a new decision point ("TB or IMPL?"); creates two paths from I-* to implementation; the distinction may not be clear before scoping begins |
| Posture field in TB brief + gate question in skill | Explicit decision within one artifact type; no new vocabulary; decision is recorded | Slight additional field in TB brief structure |

## Decision

Keep `TB-*` as the single artifact type for implementation planning. Add an explicit
**iteration posture gate** as the first step of `ezkey-tracer-bullet-promote`:

> *"Single-pass or iterative?"*

The answer is recorded as `posture: single-pass` or `posture: iterative` in the TB brief.

- **`single-pass`**: the TB brief is the complete implementation plan. "Expand slice iteratively"
  does not apply. Close the TB once the one-shot implementation is validated.
- **`iterative`**: subsequent TBs are expected. The full iterative recommended sequence applies.

The posture is decided **before** scoping begins, not discovered after. This prevents the agent
from drifting into an iterative framing by default.

## Consequences

- `tracer-bullet-method.md` updated: `posture` added to the TB structure definition; the
  recommended sequence now starts with the posture decision; step 8 ("expand slice iteratively")
  is gated on `posture: iterative`.
- `ezkey-tracer-bullet-promote/SKILL.md` updated: Step 1 is now the posture gate question;
  subsequent steps renumbered; Rule section extended with single-pass closure guidance.
- `design-principles.md` updated: Principle 15 ("Simple cases stay simple") formalizes the
  fast-path requirement for the methodology process itself.
- No backward-compatible change to existing TB-* artifacts: the posture field is a new
  convention applied to new artifacts only.

## Related documents

- [tracer-bullet-method.md](../tracer-bullet-method.md)
- [ezkey-tracer-bullet-promote skill](../../../.cursor/skills/ezkey-tracer-bullet-promote/SKILL.md)
- [design-principles.md](../../global/design-principles.md)
