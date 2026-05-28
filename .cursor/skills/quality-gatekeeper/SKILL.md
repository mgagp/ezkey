---
name: quality-gatekeeper
description: Applies Ezkey quality gates across analysis, design, implementation, tests, and documentation traceability. Use before execution and before closing work to prevent drift.
disable-model-invocation: true
---
# Quality Gatekeeper

## Purpose

Run a concise go/no-go quality check at key workflow transitions.

## Boundary contract

- **Enter when:** work is about to move from analysis to execution, or from execution to closeout.
- **Exit when:** the gate returns `go` or `no-go` with blockers, major issues, and follow-up actions.
- **Call next:** implementation after `go`, or targeted design/test/traceability work after `no-go`.
- **Not needed when:** the task is a trivial change with no analysis, contract, test, or traceability impact.

## Inputs

- target (`I-*`, `TB-*`, or implementation slice)
- related design/test/traceability artifacts

## Gate checklist

1. **Analysis gate**: intent, scope, assumptions, and exception paths are explicit.
2. **Design gate**: boundaries, mappings, validations, and trade-offs are explicit.
3. **Test gate**: chosen layers are justified (unit/functional/elective/operational/UI).
4. **Traceability gate**: feature/spec/test links are updated or planned in same slice.
5. **Closeout gate**: residual risks and deferred work are explicit.

## Output

- `go` or `no-go`
- blocking issues
- major issues
- follow-up actions

## Rule

Prioritize correctness and traceability blockers over stylistic concerns.
