---
name: ezkey-test-strategy-planner
description: Plans a risk-based Ezkey test strategy for a bounded change slice across unit, functional, elective, operational, and UI layers. Use when preparing analysis/design, tracer bullets, or implementation plans and when deciding which tests are necessary, relevant, cheap, and high-confidence.
disable-model-invocation: true
---
# Ezkey Test Strategy Planner

## Purpose

Create a concise test plan slice that balances confidence and execution cost.

Use this skill for `I-*` ideas, `TB-*` tracer bullets, and implementation plans.

## Boundary contract

- **Enter when:** a change has known risks and needs explicit test-layer selection.
- **Exit when:** minimum, optional, deferred, and rejected test layers are justified with evidence expectations.
- **Call next:** `ezkey-quality-gatekeeper` before execution, and `ezkey-traceability-sync` after evidence changes.
- **Not needed when:** the change is purely editorial and does not affect behavior, contracts, workflows, or validation.

## Inputs

- Target ID (`I-*` or `TB-*`)
- Change summary
- Affected components
- Main risks

## Output format

Produce a test plan using `product-docs/templates/test-plan-slice.template.md`.

## Planning steps

1. Identify the primary risk and changed boundaries.
2. Select the minimum meaningful unit tests.
3. Select functional end-to-end scenarios for critical paths.
4. Decide if elective tests are needed now or deferred.
5. Decide if operational churn tests are needed now or deferred.
6. Decide if Playwright UI tests are needed now or deferred.
7. For any deferred layer, record rationale and follow-up test debt.

## Decision heuristic

For each layer, answer:

- Is it necessary for this risk?
- Is it relevant to changed behavior?
- Is it cheap/fast enough for this stage?
- Does it provide strong confidence per cost?

## UI-specific rule

When UI changes are present, always include UI testability notes, even if Playwright execution is deferred.

Examples:

- stable selectors,
- predictable loading and error states,
- deterministic interaction boundaries.

## References

- `product-docs/methodology/testing-strategy-in-workflow.md`
- `product-docs/methodology/quality-gates.md`
- `product-docs/templates/test-plan-slice.template.md`
