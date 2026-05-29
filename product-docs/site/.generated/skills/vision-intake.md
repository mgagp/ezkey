# Skill: `vision-intake`

<section class="skill-detail-hero skill-detail-hero--workflow">
  <p class="skills-eyebrow">Workflow skill</p>
  <p class="skill-detail-summary">Captures raw product direction into structured vision notes and backlog-ready seeds. Use when the user shares strategic ideas, product intent evolution, or early feature thoughts.</p>
  <div class="skill-chip-row">
    <span class="skill-chip">Order 01</span>
    <span class="skill-chip">Source of truth: <code>.cursor/skills/vision-intake/SKILL.md</code></span>
    <span class="skill-chip">Call next: <a href="#/skills/backlog-triage.md">backlog-triage</a>, <a href="#/skills/grill-me.md">grill-me</a></span>
  </div>
</section>

## Purpose

Convert free-form ideation into concise, structured artifacts without over-constraining expression.

## Boundary contract

<div class="skill-contract-grid">
  <article class="skill-contract-card">
    <h3>Enter when</h3>
    <p>the user shares strategic direction, product intent, or early feature ideas.</p>
  </article>
  <article class="skill-contract-card">
    <h3>Exit when</h3>
    <p>the durable direction is captured as V-* or an initial I-* seed with a next step.</p>
  </article>
  <article class="skill-contract-card">
    <h3>Call next</h3>
    <p><a href="#/skills/backlog-triage.md">backlog-triage</a>, <a href="#/skills/grill-me.md">grill-me</a></p>
  </article>
  <article class="skill-contract-card">
    <h3>Not needed when</h3>
    <p>the idea is already a bounded implementation slice ready for TB-* planning.</p>
  </article>
</div>

## Inputs

- Raw idea or direction statement
- Optional live working plan from the current session
- Optional context (phase, components, constraints)

## Outputs

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

## Method links

- [Skills overview](README.md)
- [AI Collaboration Model](#/methodology/ai-collaboration-model.md)
- [Session Start Guide](#/methodology/session-start-guide.md)
- [Methodology Pack](#/methodology/README.md)
- Next skills: [backlog-triage](backlog-triage.md), [grill-me](grill-me.md)
