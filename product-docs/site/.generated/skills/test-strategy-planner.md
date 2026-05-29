# Skill: `test-strategy-planner`

<section class="skill-detail-hero skill-detail-hero--workflow">
  <p class="skills-eyebrow">Workflow skill</p>
  <p class="skill-detail-summary">Plans a risk-based test strategy for a bounded change slice across unit, functional, elective, operational, and UI layers. Use when preparing analysis/design, tracer bullets, or implementation plans and when deciding which tests are necessary, relevant, cheap, and high-confidence.</p>
  <div class="skill-chip-row">
    <span class="skill-chip">Order 06</span>
    <span class="skill-chip">Source of truth: <code>.cursor/skills/test-strategy-planner/SKILL.md</code></span>
    <span class="skill-chip">Call next: <a href="#/skills/quality-gatekeeper.md">quality-gatekeeper</a>, <a href="#/skills/traceability-sync.md">traceability-sync</a></span>
  </div>
</section>

## Purpose

Create a concise test plan slice that balances confidence and execution cost.

Use this skill for `I-*` ideas, `TB-*` tracer bullets, and implementation plans.

## Boundary contract

<div class="skill-contract-grid">
  <article class="skill-contract-card">
    <h3>Enter when</h3>
    <p>a change has known risks and needs explicit test-layer selection.</p>
  </article>
  <article class="skill-contract-card">
    <h3>Exit when</h3>
    <p>minimum, optional, deferred, and rejected test layers are justified with evidence expectations.</p>
  </article>
  <article class="skill-contract-card">
    <h3>Call next</h3>
    <p><a href="#/skills/quality-gatekeeper.md">quality-gatekeeper</a>, <a href="#/skills/traceability-sync.md">traceability-sync</a></p>
  </article>
  <article class="skill-contract-card">
    <h3>Not needed when</h3>
    <p>the change is purely editorial and does not affect behavior, contracts, workflows, or validation.</p>
  </article>
</div>

## Inputs

- Target ID (`I-*` or `TB-*`)
- Change summary
- Affected components
- Main risks

## Outputs

No additional output guidance recorded.

## Steps

Use the boundary contract and method links to position this skill.

## Method links

- [Skills overview](README.md)
- [AI Collaboration Model](#/methodology/ai-collaboration-model.md)
- [Session Start Guide](#/methodology/session-start-guide.md)
- [Methodology Pack](#/methodology/README.md)
- Next skills: [quality-gatekeeper](quality-gatekeeper.md), [traceability-sync](traceability-sync.md)
