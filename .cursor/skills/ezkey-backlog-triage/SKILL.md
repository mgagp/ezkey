---
name: ezkey-backlog-triage
description: Triages Ezkey backlog ideas into clear scope, value, risk, and status with standardized metadata. Use when refining I-* entries before promotion to tracer bullet planning.
disable-model-invocation: true
---
# Ezkey Backlog Triage

## Purpose

Normalize backlog ideas so they are comparable, filterable, and promotion-ready.

## Inputs

- `I-*` idea entry
- Optional current-session working plan as incubation source
- Optional linked vision notes or roadmap context

## Required outputs

- Updated status and priority
- Scope and non-scope
- Main risks and assumptions
- Phase tags and component tags
- Promotion notes

## Steps

1. Clarify problem and expected value.
2. Bound scope (`in` vs `out`).
3. Assign status (`captured` to `ready`) and priority (`P0` to `P3`).
4. Tag impacted phases and components.
5. Record top risks and unresolved questions.
6. Decide promotion posture (`incubating`, `ready`, or `parked`).

## Rule

Prefer small, explicit increments. Avoid large speculative scope during triage.
When a working plan is present, materialize only the durable actionable scope into the `I-*`; do not treat the plan itself as the canonical backlog artifact.

## Multi-branch note

When working on a feature branch or in a Git worktree:
- Use date+slug IDs: `I-YYYY-MM-DD-<slug>.md` (e.g. `I-2026-05-22-my-topic.md`).
- Create the file directly in `product-docs/global/backlog/ideas/` — do not update `backlog/index.md` on the branch.
- Defer the index update to post-merge on `main`.

See `product-docs/methodology/multi-branch-workflow.md` and `product-docs/methodology/nomenclature.md`.
