# Skill: `traceability-sync`

<section class="skill-detail-hero skill-detail-hero--workflow">
  <p class="skills-eyebrow">Workflow skill</p>
  <p class="skill-detail-summary">Synchronizes feature, specification, and test traceability across global and component documentation. Use when feature status, behavior, or validation evidence changes.</p>
  <div class="skill-chip-row">
    <span class="skill-chip">Order 09</span>
    <span class="skill-chip">Source of truth: <code>.cursor/skills/traceability-sync/SKILL.md</code></span>
    <span class="skill-chip">Call next: <a href="#/skills/closeout.md">closeout</a></span>
  </div>
</section>

## Purpose

Keep feature-to-spec-to-test linkage current after any meaningful change.

## Boundary contract

<div class="skill-contract-grid">
  <article class="skill-contract-card">
    <h3>Enter when</h3>
    <p>feature status, behavior, specs, tests, or validation evidence changed.</p>
  </article>
  <article class="skill-contract-card">
    <h3>Exit when</h3>
    <p>global and component traceability documents reflect current evidence and open gaps.</p>
  </article>
  <article class="skill-contract-card">
    <h3>Call next</h3>
    <p><a href="#/skills/closeout.md">closeout</a></p>
  </article>
  <article class="skill-contract-card">
    <h3>Not needed when</h3>
    <p>no observable behavior, contract, feature status, or validation evidence changed.</p>
  </article>
</div>

## Inputs

- Changed feature or tracer bullet
- Updated specs/docs/tests

## Outputs

No additional output guidance recorded.

## Steps

1. Confirm impacted feature IDs.
2. Update feature status if it changed.
3. Update spec references and acceptance criteria links.
4. Update test-suite references and current status.
5. Record open gaps explicitly when coverage is incomplete.

## Rule

Never hide coverage gaps. Track them explicitly with ownership and next action.

## Method links

- [Skills overview](README.md)
- [AI Collaboration Model](#/methodology/ai-collaboration-model.md)
- [Session Start Guide](#/methodology/session-start-guide.md)
- [Methodology Pack](#/methodology/README.md)
- Next skills: [closeout](closeout.md)
