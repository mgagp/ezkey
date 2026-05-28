---
name: vision-intake
description: Captures raw product direction into structured Ezkey vision notes and backlog-ready seeds. Use when the user shares strategic ideas, product intent evolution, or early feature thoughts.
disable-model-invocation: true
---
# Vision Intake

## Purpose

Convert free-form ideation into concise, structured artifacts without over-constraining expression.

## Boundary contract

- **Enter when:** the user shares strategic direction, product intent, or early feature ideas.
- **Exit when:** the durable direction is captured as `V-*` or an initial `I-*` seed with a next step.
- **Call next:** `backlog-triage` for actionable scope, or `grill-me` for stress-testing.
- **Not needed when:** the idea is already a bounded implementation slice ready for `TB-*` planning.

## Inputs

- Raw idea or direction statement
- Optional live working plan from the current session
- Optional context (phase, components, constraints)

## Output target

- `product-docs/global/vision/` note (`V-*`), or
- initial backlog seed (`I-*`) when immediately actionable

## Steps

1. Capture the core intent in one paragraph.
2. Extract likely product impact (phases, components, users).
3. Record assumptions and constraints.
4. Decide: keep as vision note or promote to backlog seed.
5. Add explicit next step (`refine`, `promote`, or `archive`).

## Rule

Keep intake lightweight and expressive. Do not force implementation-level detail at this stage.
If the source is a current-session working plan, extract and condense the durable direction rather than copying the planning artifact verbatim.

## Multi-branch note

When working on a feature branch or in a Git worktree:
- Use date+slug IDs: `V-YYYY-MM-DD-<slug>.md` (e.g. `V-2026-05-22-my-topic.md`).
- Create the file directly in `product-docs/global/vision/` — do not update `product-orientation-notes.md` on the branch.
- Defer the index update to post-merge on `main`.

See `product-docs/methodology/multi-branch-workflow.md` and `product-docs/methodology/nomenclature.md`.
