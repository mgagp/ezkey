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

## When the operator asks to commit (program slice gate)

**Read this section before staging or `git commit`** when either is true:

- an open `I-*` or `TB-*` in `product-docs/global/backlog/` covers the current work, or
- the session already created or updated program artifacts (`I-*`, `TB-*`, grill, `TSP-*`) for this slice.

**Sequence (mandatory for program slices):**

1. **`traceability-sync`** — update or explicitly skip global/component traceability with a recorded reason.
2. **`closeout`** (this skill) — status transitions, evidence summary, deferred items, residual risks on the canonical artifacts.
3. **Then** git commit / PR — not before.

**Do not substitute** a prose “pre-commit checklist” to the operator for steps 1–2 when program artifacts exist.

If the slice is **hygiene-only** (no open `I-*`/`TB-*` for this work), classify per § Hygiene vs program below and commit with targeted docs only.

See also `product-docs/methodology/minimum-viable-method.md` § *Program slice exit sequence* and
`product-docs/global/backlog/method-logs/ML-2026-06-18-closeout-skill-before-commit-gap.md`.

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
- **GitHub issue posture (one line):** canon sufficient alone, or issue recommended / opened /
  deferred with reason (see § GitHub visibility below)

## GitHub visibility (optional, not canon)

`product-docs` artifacts are the source of truth. A GitHub issue is **optional** visibility for the
host project — never a substitute for `I-*` / `TB-*` closeout.

**Suggest or open an issue** when any of these apply: monorepo PR expected; dual-repo or public
mirror sync; coordination across PRs; operator wants a GitHub anchor. **Skip by default** for Lane C
hygiene with a single small PR.

**Timing:** at implementation start (preferred) or **retroactively** at closeout/PR — both are valid.
Issue body: link to canonical `I-*` / `TB-*` paths; do not copy the full TB.

**Labels:** if an issue was opened this slice, confirm all five label groups are present
(`gh issue view <N> --json labels`). If missing, add with `gh issue edit` before closeout. See
`github-issue-promote` skill and `github-issues-workflow.md` § Mandatory label checklist.

See `product-docs/methodology/minimum-viable-method.md` § *GitHub issue vs product-docs canon*.

## Status guidance

- Move to `done` only when required evidence is complete.
- Use `parked` for deliberate pause with review date.
- Use `archived` for closed historical retention.
- Use `dropped` only with explicit reason.

## Rule

Prefer explicit closure with small known debt over ambiguous "almost done" state.
