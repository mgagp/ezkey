---
public: true
---
# Promote fail-open vs fail-closed into the design-judgment compass

## Date

2026-07-18

## Context

A live Ezkey design session examined `AuditLogService.log()`, where audit persistence failures are
logged and absorbed so the primary business operation can continue. The discussion clarified that
this is not merely an exception-handling detail: it is an explicit **fail-open** availability
posture with consequences for audit-delivery guarantees.

The vocabulary proved useful beyond that product-specific case. At any critical boundary where a
control or side effect can fail, analysis improves when it states whether the primary path
continues (**fail-open**) or stops (**fail-closed**), why that trade-off is appropriate, and how a
failure remains observable.

This reusable signal warranted a Lane E methodology decision because it improves future design
judgment and should remain visible in a later methodology publication.

## Source signal (translated from the live operator discussion)

- "This is a structuring concept for analysis and design that goes beyond the specific problem."
- "It should become a project value and a conceptual compass for informed decisions."
- "Keep it visible in future design sessions without making the method unnecessarily complex."
- "Preserve a methodology-evolution trace so the next publication can include the decision."

## Working assumptions

- The vocabulary is most valuable at boundaries where availability, integrity, security, or
  honesty of claims are genuinely in tension.
- Not every exception path needs a formal fail-open/fail-closed matrix.
- A fail-open choice is not automatically weak, and a fail-closed choice is not automatically
  safer; each must be judged against the primary path and its risk.
- Fail-open behavior should remain observable to operators rather than becoming silent failure.
- A short principle in existing canon is sufficient; no dedicated artifact family, mandatory
  checklist, or new workflow stage is needed.

## Options considered

| Option | Advantages | Disadvantages |
| --- | --- | --- |
| Keep the vocabulary local to the audit-log backlog idea | No methodology change | Loses a broadly reusable design lesson; future sessions may reconstruct it inconsistently |
| Create a dedicated fail-open/fail-closed framework, matrix, or skill | Highly explicit and repeatable | Adds ceremony and accidental complexity disproportionate to the principle |
| Add one concise design-judgment principle and lightweight agent reminder | Durable and publishable; available during future analysis; minimal process cost | Requires judgment about when the principle is relevant |

## Decision

Adopt the third option.

Promote **name fail-open vs fail-closed at critical boundaries** into the generic
`design-judgment-principles.md` companion and the Ezkey source-project design canon.

The practical prompt is:

> If this control or side effect fails, does the important path continue or stop — and how do we
> know?

Use **fail-closed** when continuing would silently weaken a claimed security, integrity, or trust
guarantee. Use **fail-open** when preserving availability is the better trade-off and the failure
remains observable. Record the choice only when the trade-off is non-obvious or load-bearing.

This is a light design compass, not a required matrix for every call site.

## Consequences

- `design-judgment-principles.md` carries the generic, publishable principle.
- `product-docs/global/design-principles.md` carries the Ezkey source-project companion.
- Short reminders in `AGENTS.md` and the project analysis rule make the principle available during
  future human/agent analysis without creating a new ceremony.
- The audit-log fail-open backlog idea remains the first product-specific example and retains its
  own later grill.
- This Lane E decision is eligible for inclusion in the next methodology publication milestone.
  It does **not** independently trigger a SemVer bump or release.
- Evidence that the decision helped should come from future briefs, tracer bullets, or ADRs naming
  a failure posture where the distinction materially affects a trade-off.

## Related documents

- [`../design-judgment-principles.md`](../design-judgment-principles.md) — generic principle 12
- [`../workflow-overview.md`](../workflow-overview.md) — Lane E feedback and evolution
- [`../methodology-publication-and-versioning.md`](../methodology-publication-and-versioning.md)

The source project retains the originating audit-log example and its product-specific companion
principle outside the public methodology corpus.
