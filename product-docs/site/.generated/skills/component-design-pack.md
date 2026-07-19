# Skill: `component-design-pack`

<section class="skill-detail-hero skill-detail-hero--workflow">
  <p class="skills-eyebrow">Workflow skill</p>
  <p class="skill-detail-summary">Builds component-focused design briefs for impacted boundaries, mappings, validation rules, and error handling. Use after tracer bullet definition and before implementation.</p>
  <div class="skill-chip-row">
    <span class="skill-chip">Order 06</span>
    <span class="skill-chip">Source of truth: <code>.cursor/skills/component-design-pack/SKILL.md</code></span>
    <span class="skill-chip">Call next: <a href="#/skills/test-strategy-planner.md">test-strategy-planner</a>, <a href="#/skills/quality-gatekeeper.md">quality-gatekeeper</a></span>
  </div>
</section>

## Purpose

Create complementary component-level design slices that align to one global feature or tracer bullet.

## Boundary contract

<div class="skill-contract-grid">
  <article class="skill-contract-card">
    <h3>Enter when</h3>
    <p>a TB-* or feature scope touches component boundaries, mappings, validations, lifecycle/state behavior, or error behavior.</p>
  </article>
  <article class="skill-contract-card">
    <h3>Exit when</h3>
    <p>each impacted component has local responsibilities, boundaries, contracts, tests, and doc impact named; any durable component-local truth has a target component-pack location.</p>
  </article>
  <article class="skill-contract-card">
    <h3>Call next</h3>
    <p><a href="#/skills/test-strategy-planner.md">test-strategy-planner</a>, <a href="#/skills/quality-gatekeeper.md">quality-gatekeeper</a></p>
  </article>
  <article class="skill-contract-card">
    <h3>Not needed when</h3>
    <p>the change is local, internal, and does not alter component responsibilities or observable behavior.</p>
  </article>
</div>

## Inputs

- `TB-*` or feature scope
- impacted components

## Outputs

Use `product-docs/templates/component-design-brief.template.md`.

## Steps

1. For each component, define local responsibilities.
2. Identify inbound/outbound boundaries.
3. Specify mapping and invariant implications.
4. Specify validation and exception behavior.
5. Decide whether a decision table, finite-state model, mapping matrix, or lifecycle note is the clearest representation.
6. Specify contract and compatibility implications.
7. Specify test and documentation impact.
8. Promote only durable component-local truth into `product-docs/components/<pack>/`; keep exploratory analysis in the feature or tracer-bullet context.

## Rule

Keep component briefs separable but linked to the same global intent and IDs.

The point is to add the right value at the right moment, in the right format and location. Do not
create component-pack material by habit.

## Method links

- [Skills overview](README.md)
- [AI Collaboration Model](#/methodology/ai-collaboration-model.md)
- [Session Start Guide](#/methodology/session-start-guide.md)
- [Methodology Pack](#/methodology/README.md)
- Next skills: [test-strategy-planner](test-strategy-planner.md), [quality-gatekeeper](quality-gatekeeper.md)
