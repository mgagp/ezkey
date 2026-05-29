# Skill: `closeout`

<section class="skill-detail-hero skill-detail-hero--workflow">
  <p class="skills-eyebrow">Workflow skill</p>
  <p class="skill-detail-summary">Closes workflow slices with explicit status transitions, residual risk notes, and next-step clarity. Use at the end of tracer bullets, feature slices, or implementation cycles.</p>
  <div class="skill-chip-row">
    <span class="skill-chip">Order 09</span>
    <span class="skill-chip">Source of truth: <code>.cursor/skills/closeout/SKILL.md</code></span>
    
  </div>
</section>

## Purpose

Finalize a work slice so progress remains auditable and easy to resume.

## Boundary contract

<div class="skill-contract-grid">
  <article class="skill-contract-card">
    <h3>Enter when</h3>
    <p>a TB-*, feature slice, blitz integration, retrofit slice, or implementation cycle is ending.</p>
  </article>
  <article class="skill-contract-card">
    <h3>Exit when</h3>
    <p>status transitions, evidence, deferred items, residual risks, and next actions are explicit.</p>
  </article>
  <article class="skill-contract-card">
    <h3>Call next</h3>
    <p>no next skill by default; reopen triage, traceability sync, or retrofit only for named follow-up work.</p>
  </article>
  <article class="skill-contract-card">
    <h3>Not needed when</h3>
    <p>the work is still actively changing or required evidence is not yet available.</p>
  </article>
</div>

## Inputs

- target (`I-*`, `TB-*`, or feature slice)
- execution evidence
- test evidence
- documentation updates

## Outputs

No additional output guidance recorded.

## Steps

Use the boundary contract and method links to position this skill.

## Rule

Prefer explicit closure with small known debt over ambiguous "almost done" state.

## Method links

- [Skills overview](README.md)
- [AI Collaboration Model](#/methodology/ai-collaboration-model.md)
- [Session Start Guide](#/methodology/session-start-guide.md)
- [Methodology Pack](#/methodology/README.md)
