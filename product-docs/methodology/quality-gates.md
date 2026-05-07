# Quality Gates

## Purpose

This document defines lightweight but explicit gates that protect analysis quality, design quality, and implementation integrity.

## Gate categories

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

## Severity guideline

- **Blocking**: contract mismatch, missing critical validation path, missing mandatory traceability.
- **Major**: incomplete exception handling coverage, unclear ownership.
- **Minor**: readability or completeness improvements that do not affect correctness.
