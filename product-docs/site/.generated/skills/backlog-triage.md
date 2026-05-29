# Skill: `backlog-triage`

<section class="skill-detail-hero skill-detail-hero--workflow">
  <p class="skills-eyebrow">Workflow skill</p>
  <p class="skill-detail-summary">Triages backlog ideas into clear scope, value, risk, and status with standardized metadata. Use when refining I-* entries before promotion to tracer bullet planning.</p>
  <div class="skill-chip-row">
    <span class="skill-chip">Order 02</span>
    <span class="skill-chip">Source of truth: <code>.cursor/skills/backlog-triage/SKILL.md</code></span>
    <span class="skill-chip">Call next: <a href="#/skills/grill-me.md">grill-me</a>, <a href="#/skills/tracer-bullet-promote.md">tracer-bullet-promote</a></span>
  </div>
</section>

## Purpose

Normalize backlog ideas so they are comparable, filterable, and promotion-ready.

## Boundary contract

<div class="skill-contract-grid">
  <article class="skill-contract-card">
    <h3>Enter when</h3>
    <p>an I-* exists or a vision note has enough actionable scope to become one.</p>
  </article>
  <article class="skill-contract-card">
    <h3>Exit when</h3>
    <p>scope, non-scope, priority, status, tags, risks, and promotion posture are explicit.</p>
  </article>
  <article class="skill-contract-card">
    <h3>Call next</h3>
    <p><a href="#/skills/grill-me.md">grill-me</a>, <a href="#/skills/tracer-bullet-promote.md">tracer-bullet-promote</a></p>
  </article>
  <article class="skill-contract-card">
    <h3>Not needed when</h3>
    <p>the item is still pure orientation, or the slice is already fully scoped and validated.</p>
  </article>
</div>

## Inputs

- `I-*` idea entry
- Optional current-session working plan as incubation source
- Optional linked vision notes or roadmap context

## Outputs

No additional output guidance recorded.

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

## Method links

- [Skills overview](README.md)
- [AI Collaboration Model](#/methodology/ai-collaboration-model.md)
- [Session Start Guide](#/methodology/session-start-guide.md)
- [Methodology Pack](#/methodology/README.md)
- Next skills: [grill-me](grill-me.md), [tracer-bullet-promote](tracer-bullet-promote.md)
