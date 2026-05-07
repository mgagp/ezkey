# Workflow Overview

## Purpose

This document defines the default end-to-end workflow from idea capture to implemented feature.

It complements, but does not replace, the engineering and governance rules in the repository.

## Parallel lane: legacy retrofit

In addition to ideation-to-delivery, use a retrofit lane for historical plans and ad hoc implementation history:

1. Select a small source batch.
2. Extract decisions, invariants, patterns, and test signal.
3. Map into canonical docs.
4. Record a retrofit slice (`R-*`).
5. Close with residual gaps and next action.

See `legacy-retrofit-workflow.md`.

## End-to-end flow

1. **Capture**
   - Record a new idea in `../global/backlog/ideas/` using the backlog idea template.
   - Keep the first version short and expressive.
2. **Triage**
   - Clarify intent, user value, risk, and rough scope.
   - Assign status, priority, phase tags, and component tags.
3. **Challenge**
   - Run a structured questioning pass ("Grill Me" style).
   - Surface assumptions, exceptions, error paths, and non-goals.
4. **Promote**
   - If ready, promote the idea into a tracer bullet (`TB-*`) brief.
   - Define the first vertical slice and expected evidence.
5. **Analyze and design**
   - Perform global analysis.
   - Perform component-specific analysis for each impacted boundary.
6. **Plan tests**
   - Select minimum and optional test layers (unit, functional, elective, operational, UI).
   - Keep selection risk-based and cost-aware.
7. **Gate**
   - Apply quality gates (contracts, tests, docs, traceability).
8. **Implement**
   - Execute the plan in code and tests.
9. **Close out**
   - Update feature and traceability documents.
   - Transition backlog and tracer-bullet status.

## Artifacts by stage

| Stage | Primary artifact |
|------|-------------------|
| Capture | `I-*` backlog file |
| Triage / Challenge | Updated `I-*` + open questions |
| Promote | `TB-*` tracer bullet brief |
| Analyze / Design | Component design briefs and mappings |
| Test planning | Test plan slice |
| Gate / Implement | Code, tests, contract artifacts, docs updates |
| Close out | Status transitions + traceability updates |

## Exit criteria per stage

- **Capture done** when the idea has clear intent and tags.
- **Triage done** when scope and value are understandable.
- **Challenge done** when key risks and exceptions are explicit.
- **Promote done** when one vertical slice is testable.
- **Analyze/Design done** when boundaries and decision points are explicit.
- **Test planning done** when minimum test layers are explicitly selected.
- **Gate done** when mandatory quality checks pass.
- **Close out done** when status and traceability are updated.
