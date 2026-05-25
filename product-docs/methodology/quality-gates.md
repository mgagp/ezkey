# Quality Gates

## Purpose

This document defines lightweight but explicit gates that protect analysis quality, design quality, and implementation integrity.

## Gate categories

### 0) Methodology fit gate

- Process weight is proportional to uncertainty and risk.
- The active lane, skill, or status has clear entry and exit criteria.
- Living documents, registries, or skills have earned their maintenance cost.
- Closure distinguishes corpus integration, product implementation, and residual backlog.

### 1) Analysis gate

- Problem and intended outcome are explicit.
- Scope and non-scope are explicit.
- Main assumptions are listed.
- Critical exception paths are identified.

### 2) Design gate

- Boundary contracts are explicit.
- Mapping rules are explicit.
- Validation rules are explicit.
- Decision rationale is recorded when trade-offs exist.

### 3) Implementation gate

- Tests validate nominal and critical exception behavior.
- Test layer choices (unit/functional/elective/operational/UI) are explicit and justified.
- Changed workflows and mappings are documented.
- Traceability rows are updated.
- Generated artifacts are refreshed only through approved workflows.

### 4) Closeout gate

- Backlog and tracer-bullet statuses are transitioned.
- Open questions are either resolved or explicitly parked.
- Residual risks are documented.
- Source artifacts, canonical outputs, decisions, and supersessions are cross-linked where relevant.
- The closeout states whether the methodology slice is complete, even when product backlog remains active.

## Severity guideline

- **Blocking**: contract mismatch, missing critical validation path, missing mandatory traceability.
- **Major**: incomplete exception handling coverage, unclear ownership.
- **Minor**: readability or completeness improvements that do not affect correctness.

Use [`methodological-values.md`](methodological-values.md) to resolve judgment calls where a gate
could add clarity or become accidental ceremony.
