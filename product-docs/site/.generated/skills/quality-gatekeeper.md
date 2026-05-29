# Skill: `quality-gatekeeper`

<section class="skill-detail-hero skill-detail-hero--workflow">
  <p class="skills-eyebrow">Workflow skill</p>
  <p class="skill-detail-summary">Applies quality gates across analysis, design, implementation, tests, and documentation traceability. Use before execution and before closing work to prevent drift.</p>
  <div class="skill-chip-row">
    <span class="skill-chip">Order 08</span>
    <span class="skill-chip">Source of truth: <code>.cursor/skills/quality-gatekeeper/SKILL.md</code></span>
    
  </div>
</section>

## Purpose

Run a concise go/no-go quality check at key workflow transitions.

## Boundary contract

<div class="skill-contract-grid">
  <article class="skill-contract-card">
    <h3>Enter when</h3>
    <p>work is about to move from analysis to execution, or from execution to closeout.</p>
  </article>
  <article class="skill-contract-card">
    <h3>Exit when</h3>
    <p>the gate returns go or no-go with blockers, major issues, and follow-up actions.</p>
  </article>
  <article class="skill-contract-card">
    <h3>Call next</h3>
    <p>implementation after go, or targeted design/test/traceability work after no-go.</p>
  </article>
  <article class="skill-contract-card">
    <h3>Not needed when</h3>
    <p>the task is a trivial change with no analysis, contract, test, or traceability impact.</p>
  </article>
</div>

## Inputs

- target (`I-*`, `TB-*`, or implementation slice)
- related design/test/traceability artifacts

## Outputs

No additional output guidance recorded.

## Steps

Use the boundary contract and method links to position this skill.

## Rule

Prioritize correctness and traceability blockers over stylistic concerns.

## Method links

- [Skills overview](README.md)
- [AI Collaboration Model](#/methodology/ai-collaboration-model.md)
- [Session Start Guide](#/methodology/session-start-guide.md)
- [Methodology Pack](#/methodology/README.md)
