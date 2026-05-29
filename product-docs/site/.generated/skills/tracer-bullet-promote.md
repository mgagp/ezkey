# Skill: `tracer-bullet-promote`

<section class="skill-detail-hero skill-detail-hero--workflow">
  <p class="skills-eyebrow">Workflow skill</p>
  <p class="skill-detail-summary">Promotes ready ideas into bounded tracer bullets with vertical-slice scope, boundaries, and evidence criteria. Use when an I-* item is ready for execution planning.</p>
  <div class="skill-chip-row">
    <span class="skill-chip">Order 05</span>
    <span class="skill-chip">Source of truth: <code>.cursor/skills/tracer-bullet-promote/SKILL.md</code></span>
    <span class="skill-chip">Call next: <a href="#/skills/component-design-pack.md">component-design-pack</a>, <a href="#/skills/test-strategy-planner.md">test-strategy-planner</a></span>
  </div>
</section>

## Purpose

Turn a `ready` idea into a validated first vertical slice (`TB-*`).

## Boundary contract

<div class="skill-contract-grid">
  <article class="skill-contract-card">
    <h3>Enter when</h3>
    <p>an I-* is ready and execution needs a bounded vertical slice.</p>
  </article>
  <article class="skill-contract-card">
    <h3>Exit when</h3>
    <p>the TB-* has posture, scope, boundaries, exclusions, and evidence criteria.</p>
  </article>
  <article class="skill-contract-card">
    <h3>Call next</h3>
    <p><a href="#/skills/component-design-pack.md">component-design-pack</a>, <a href="#/skills/test-strategy-planner.md">test-strategy-planner</a></p>
  </article>
  <article class="skill-contract-card">
    <h3>Not needed when</h3>
    <p>the item still lacks direction, or the change is a tiny code/doc fix with no workflow slice.</p>
  </article>
</div>

## Inputs

- `I-*` idea
- Current risk and scope understanding

## Outputs

- `TB-*` brief
- In-scope boundaries
- Out-of-scope boundaries
- Acceptance evidence plan
- Initial test-layer plan link

## Steps

1. **Evaluate iteration posture** — before scoping the slice, answer explicitly:
   *"Single-pass or iterative?"*
   - `single-pass`: the entire bounded job fits one coherent implementation cut with acceptable
     risk and evidence cost. The TB brief is the complete implementation plan.
   - `iterative`: scope, risk, or cross-boundary complexity warrants separate slices.
   Record the posture as `posture: single-pass` or `posture: iterative` in the TB brief.
   When `single-pass`, do not frame the session around future iterations.
2. Define the smallest meaningful end-to-end slice.
3. Identify touched component boundaries.
4. Define critical nominal and exception path.
5. Define expected evidence (tests, docs, traceability).
6. Mark explicit exclusions for this slice.

## Rule

Favor one deep vertical over broad partial coverage.
When `posture: single-pass`, close the TB once the one-shot implementation is validated — do not
extend or re-scope it as an iterative series.

## Method links

- [Skills overview](README.md)
- [AI Collaboration Model](#/methodology/ai-collaboration-model.md)
- [Session Start Guide](#/methodology/session-start-guide.md)
- [Methodology Pack](#/methodology/README.md)
- Next skills: [component-design-pack](component-design-pack.md), [test-strategy-planner](test-strategy-planner.md)
