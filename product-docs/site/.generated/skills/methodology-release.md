# Skill: `methodology-release`

<section class="skill-detail-hero skill-detail-hero--additional">
  <p class="skills-eyebrow">Method skill</p>
  <p class="skill-detail-summary">Classifies a methodology SemVer bump, drafts a release note, and updates the canonical version file when publishing to methodology.ezkey.org. Use after a publication milestone following Lane E feedback cycles.</p>
  <div class="skill-chip-row">
    <span class="skill-chip">Order 14</span>
    <span class="skill-chip">Source of truth: <code>.cursor/skills/methodology-release/SKILL.md</code></span>
    
  </div>
</section>

## Purpose

Prepare a deliberate methodology publication release: determine the SemVer level, draft the release
note, and update `product-docs/methodology-version.properties`.

## Boundary contract

<div class="skill-contract-grid">
  <article class="skill-contract-card">
    <h3>Enter when</h3>
    <p>a publication milestone is declared — enough methodology improvements justify</p>
  </article>
  <article class="skill-contract-card">
    <h3>Exit when</h3>
    <p>bump level is justified, release note is drafted (or written), version properties</p>
  </article>
  <article class="skill-contract-card">
    <h3>Call next</h3>
    <p>site build/preview, annotated Git tag creation, then Cloudflare deploy per</p>
  </article>
  <article class="skill-contract-card">
    <h3>Not needed when</h3>
    <p>changes are repo-only Lane E decisions with no public site update planned,</p>
  </article>
</div>

## Inputs

No additional input guidance recorded.

## Outputs

No additional output guidance recorded.

## Steps

Use the boundary contract and method links to position this skill.

## Rule

Keep release notes short and scannable. Link to methodology decisions for rationale; do not paste
full decision text. One release note per publication milestone — not per decision.

## Method links

- [Skills overview](README.md)
- [AI Collaboration Model](#/methodology/ai-collaboration-model.md)
- [Session Start Guide](#/methodology/session-start-guide.md)
- [Methodology Pack](#/methodology/README.md)
