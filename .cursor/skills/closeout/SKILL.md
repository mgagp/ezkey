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

## Hygiene vs program (classify before materializing)

A closeout invitation is **not** automatic permission to create `I-*`, `TB-*`, `TSP-*`, or `ML-*`.

1. **Classify** the slice as **code hygiene** or **program** using
   `product-docs/methodology/minimum-viable-method.md` (section *Hygiene vs program closeout*) and
   `product-docs/methodology/decisions/2026-06-06-methodological-closeout-vs-code-hygiene.md`.
2. If **hygiene:** close with commit/PR message, targeted module docs (`AGENTS.md`, README), and
   optional GitHub visibility — skip new backlog artifacts unless the operator explicitly confirms
   after you state the lighter path.
3. If **program:** use this skill with the relevant canonical artifacts and honest status transitions.
4. If the operator asked for full methodology closeout but you classify hygiene, **challenge first**:
   state why, propose the lighter closeout, and list what would justify escalation.

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
