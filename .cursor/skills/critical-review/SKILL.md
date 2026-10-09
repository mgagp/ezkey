---
name: critical-review
description: >-
  Thin entry point for Ezkey's existing two-stage plan hardening pattern (fresh-session /
  adversarial review). Launches a read-only Stage 2 review of one artifact — vision note, G1
  tracer-bullet brief, execution plan, or PR — using the canonical prompt; then one-finding HITL.
  Use when the operator or a gate says critical-review, critical review, revue critique,
  adversarial review, plan hardening, cold-agent plan review, or Stage 2 fresh-session review.
disable-model-invocation: true
---

# Critical review (adversarial / plan hardening)

## Purpose

**Keyword aliases for one existing method** — not a second methodology. Stage 2 of the
[two-stage plan hardening pattern](../../product-docs/methodology/README.md#the-two-stage-plan-hardening-pattern-fresh-session-review)
(`adversarial review`, `critical review`, `revue critique`, `plan hardening`,
`cold-agent plan review`).

Fresh session on a **forced different model family** from the author's (mandatory — do not use the
same family). Read-only. Same product compass. Not a CI gate.

## Boundary contract

- **Enter when:** G1 brief / P0–P1 vision before merge / security, migration, or cross-component
  contract / autonomous cold-execution plan; or the operator uses any alias above.
- **Exit when:** findings decided (`amend` / `defer` / `skip`) and the operator has aligned on
  amendments before the plan or brief file is edited.
- **Call next:** the author's branch or a follow-up PR applies amendments — never the reviewer.
- **Not needed when:** routine hygiene; a `*-curated` lane already owns the signal; local obvious
  fix.

## Authority (single source of truth)

- Pattern: [`product-docs/methodology/README.md`](../../product-docs/methodology/README.md) §
  *The two-stage plan hardening pattern*
- **Canonical prompt (edit angles here, not in this skill):**
  [`product-docs/templates/cold-agent-plan-review.prompt.md`](../../product-docs/templates/cold-agent-plan-review.prompt.md)
- Compass pointers inside that prompt: `operator-alignment-guide.md`, `design-principles.md` §17
- Root pointer: `AGENTS.md` domain table row *Plan review / cold-agent hardening*

## Procedure

1. Resolve the target (doc path @ SHA, or PR via `git fetch origin pull/<N>/head`; record head SHA).
2. Fill and send **only**
   [`cold-agent-plan-review.prompt.md`](../../product-docs/templates/cold-agent-plan-review.prompt.md)
   in a **fresh** cloud-agent session. **Model family must differ from the author's** (mandatory;
   deduce from the PR agent link or ask). Read-only: no branch, push, PR, issue, or GitHub comment
   from the reviewer.
3. Relay the report: short lot overview, then **one finding at a time** → `amend` / `defer` /
   `skip` (never bare “No-Go”; `amend` = GO for that finding).
4. After HITL alignment, amendments land on the author's artifact — not by the reviewer agent.

## If the operator asks how to invoke

Say: open a fresh session; paste
[`cold-agent-plan-review.prompt.md`](../../product-docs/templates/cold-agent-plan-review.prompt.md)
with the target path or PR; use any alias (`critical-review`, `adversarial review`, …). Keep the
answer short.
