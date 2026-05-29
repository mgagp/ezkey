---
name: closeout
description: Closes workflow slices with explicit status transitions, residual risk notes, and next-step clarity. Use at the end of tracer bullets, feature slices, or implementation cycles.
disable-model-invocation: true
---
# Closeout

## Purpose

Finalize a work slice so progress remains auditable and easy to resume.

## Boundary contract

- **Enter when:** a `TB-*`, feature slice, blitz integration, retrofit slice, or implementation cycle is ending.
- **Exit when:** status transitions, evidence, deferred items, residual risks, and next actions are explicit.
- **Call next:** no next skill by default; reopen triage, traceability sync, or retrofit only for named follow-up work.
- **Not needed when:** the work is still actively changing or required evidence is not yet available.

## Inputs

- target (`I-*`, `TB-*`, or feature slice)
- execution evidence
- test evidence
- documentation updates

## Output

- final status transition
- completed evidence summary
- deferred items and rationale
- residual risks
- next review or next action date

## Status guidance

- Move to `done` only when required evidence is complete.
- Use `parked` for deliberate pause with review date.
- Use `archived` for closed historical retention.
- Use `dropped` only with explicit reason.

## Rule

Prefer explicit closure with small known debt over ambiguous "almost done" state.
