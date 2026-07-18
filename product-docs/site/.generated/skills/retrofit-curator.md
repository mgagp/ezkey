# Skill: `retrofit-curator`

<section class="skill-detail-hero skill-detail-hero--retrofit">
  <p class="skills-eyebrow">Legacy retrofit skill</p>
  <p class="skill-detail-summary">Curates legacy retrofit slices by mapping extracted signal (plans, verbal, ad hoc) into canonical product-docs destinations, recording residual gaps, and promoting principle candidates when relevant.</p>
  <div class="skill-chip-row">
    <span class="skill-chip">Order 13</span>
    <span class="skill-chip">Source of truth: <code>.cursor/skills/retrofit-curator/SKILL.md</code></span>
    <span class="skill-chip">Call next: <a href="#/skills/traceability-sync.md">traceability-sync</a>, <a href="#/skills/closeout.md">closeout</a></span>
  </div>
</section>

## Purpose

Convert mined historical signal into canonical updates with explicit traceability.

## Boundary contract

<div class="skill-contract-grid">
  <article class="skill-contract-card">
    <h3>Enter when</h3>
    <p>mined historical signal needs mapping into canonical product-docs destinations.</p>
  </article>
  <article class="skill-contract-card">
    <h3>Exit when</h3>
    <p>the R-* slice records mappings, canonical updates, residual gaps, and index status.</p>
  </article>
  <article class="skill-contract-card">
    <h3>Call next</h3>
    <p><a href="#/skills/traceability-sync.md">traceability-sync</a>, <a href="#/skills/closeout.md">closeout</a></p>
  </article>
  <article class="skill-contract-card">
    <h3>Not needed when</h3>
    <p>there is no reusable signal, or the source already lives in current canonical docs.</p>
  </article>
</div>

## Inputs

- Extracted signal from legacy plans
- Source metadata and source type
- Target topic and canonical destinations

## Outputs

Use `product-docs/templates/legacy-plan-retrofit.template.md` and create/update `R-*` retrofit slices under `product-docs/global/legacy-retrofit/`.

## Steps

1. Create or update an `R-*` slice.
2. Map each signal item to canonical destination docs.
3. Apply targeted canonical updates.
4. Record residual gaps and next action.
5. Update `product-docs/global/legacy-retrofit/index.md`.
6. When durable project values emerge, propose principle adoption targets.

## Rule

Do not duplicate old plans. Keep canonical docs concise and link source references in the retrofit slice.

## Method links

- [Skills overview](README.md)
- [AI Collaboration Model](#/methodology/ai-collaboration-model.md)
- [Session Start Guide](#/methodology/session-start-guide.md)
- [Methodology Pack](#/methodology/README.md)
- Next skills: [traceability-sync](traceability-sync.md), [closeout](closeout.md)
