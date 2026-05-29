# Skill: `plan-incubation`

<section class="skill-detail-hero skill-detail-hero--workflow">
  <p class="skills-eyebrow">Workflow skill</p>
  <p class="skill-detail-summary">Incubates a live working plan in agent Plan mode, then converges it toward canonical method artifacts. Use when the operator wants to start with a current-session working plan under .cursor/plans/ or plans/ before materializing durable output into V-*, I-*, or TB-*.</p>
  <div class="skill-chip-row">
    <span class="skill-chip">Order 10</span>
    <span class="skill-chip">Source of truth: <code>.cursor/skills/plan-incubation/SKILL.md</code></span>
    <span class="skill-chip">Call next: <a href="#/skills/vision-intake.md">vision-intake</a>, <a href="#/skills/backlog-triage.md">backlog-triage</a>, <a href="#/skills/tracer-bullet-promote.md">tracer-bullet-promote</a></span>
  </div>
</section>

## Purpose

Use a live working plan as a deliberate brainstorming and convergence artifact before canonical materialization.

## Boundary contract

<div class="skill-contract-grid">
  <article class="skill-contract-card">
    <h3>Enter when</h3>
    <p>the operator deliberately starts from a current-session working plan before canonical docs.</p>
  </article>
  <article class="skill-contract-card">
    <h3>Exit when</h3>
    <p>durable signal is classified for V-*, I-*, TB-*, principle adoption, or a documented mix.</p>
  </article>
  <article class="skill-contract-card">
    <h3>Call next</h3>
    <p><a href="#/skills/vision-intake.md">vision-intake</a>, <a href="#/skills/backlog-triage.md">backlog-triage</a>, <a href="#/skills/tracer-bullet-promote.md">tracer-bullet-promote</a></p>
  </article>
  <article class="skill-contract-card">
    <h3>Not needed when</h3>
    <p>the source is historical retrofit input or the request is already a direct implementation task.</p>
  </article>
</div>

## Inputs

- current-session working plan under `.cursor/plans/` or `plans/`
- topic, options, or early recommendations
- optional links to related `V-*`, `I-*`, `TB-*`, or component docs

## Outputs

- clearer recommendation and open questions inside the working plan
- classification of the likely canonical destination (`V-*`, `I-*`, `TB-*`, principle candidate, or mix)
- explicit next materialization step

## Steps

1. Confirm that the source is a **current-session working plan**, not historical retrofit input.
2. Use the plan to compare options, structure the problem, and converge on the useful direction.
3. Identify what is durable enough to materialize into canonical docs.
4. Record the likely canonical destination:
   - `V-*` for direction or principle-level intent,
   - `I-*` for actionable backlog scope,
   - `TB-*` for a bounded executable slice.
5. Keep the working plan as a support artifact when it still contains valuable option space or execution notes.

## Rule

Treat the working plan as a legitimate incubation artifact.
Use the language of **materialization**, **canonicalization**, or **promotion into canonical docs**.
Do **not** frame current-session plan incubation as retrofit unless the source is genuinely historical or mixed with historical evidence.

## Method links

- [Skills overview](README.md)
- [AI Collaboration Model](#/methodology/ai-collaboration-model.md)
- [Session Start Guide](#/methodology/session-start-guide.md)
- [Methodology Pack](#/methodology/README.md)
- Next skills: [vision-intake](vision-intake.md), [backlog-triage](backlog-triage.md), [tracer-bullet-promote](tracer-bullet-promote.md)
