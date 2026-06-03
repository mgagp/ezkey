# Skills Explorer

<section class="skills-hero">
  <p class="skills-eyebrow">Derived public layer</p>
  <p class="skills-lede">
    This section exposes the methodology skills as a public navigation layer inside the methodology
    site.
  </p>
  <p class="skills-sublede">
    The source of truth remains <code>.cursor/skills/</code>. The pages here are derived views
    packaged for discoverability, traceability, and public explanation.
  </p>
</section>

## Why skills belong here

- the methodology already depends on named skill boundaries,
- the public site should show how human and AI collaboration becomes operational,
- public discoverability should not require browsing editor-local folders,
- the published view should stay derived so the source of truth remains singular.

## Common boundary contract

Every published skill answers the same four questions:

| Field | Meaning |
| --- | --- |
| Enter when | What condition makes the skill useful now. |
| Exit when | What evidence means the skill has completed its job. |
| Call next | What skill or workflow step usually follows. |
| Not needed when | What condition preserves the fast path. |

See [AI Collaboration Model](#/methodology/ai-collaboration-model.md) for the canonical protocol.

## Skill map

```mermaid
flowchart LR
  VI[vision-intake] --> BT[backlog-triage]
  BT --> GM[grill-me]
  GM --> TB[tracer-bullet-promote]
  TB --> CD[component-design-pack]
  CD --> TS[test-strategy-planner]
  TS --> QG[quality-gatekeeper]
  QG --> TR[traceability-sync]
  TR --> CO[closeout]
  PI[plan-incubation] --> VI
  PI --> BT
  PI --> TB
  LM[legacy-plan-miner] --> RC[retrofit-curator]
  RC --> TR
  RC --> CO
```

## Workflow skills

<div class="skill-card-grid">
  <article class="skill-card skill-card--workflow">
    <div class="skill-card-meta">
      <span class="skill-card-order">01</span>
      <span class="skill-card-kind">Workflow skill</span>
    </div>
    <h3><a href="#/skills/vision-intake.md"><code>vision-intake</code></a></h3>
    <p class="skill-card-summary">Captures raw product direction into structured vision notes and backlog-ready seeds. Use when the user shares strategic ideas, product intent evolution, or early feature thoughts.</p>
    <div class="skill-card-facts">
      <p><strong>Enter when</strong><br />the user shares strategic direction, product intent, or early feature ideas.</p>
      <p><strong>Call next</strong><br /><a href="#/skills/backlog-triage.md">backlog-triage</a>, <a href="#/skills/grill-me.md">grill-me</a></p>
      <p><strong>Not needed when</strong><br />the idea is already a bounded implementation slice ready for TB-* planning.</p>
    </div>
    <p class="skill-card-link"><a href="#/skills/vision-intake.md">Open detail</a></p>
  </article>
  <article class="skill-card skill-card--workflow">
    <div class="skill-card-meta">
      <span class="skill-card-order">02</span>
      <span class="skill-card-kind">Workflow skill</span>
    </div>
    <h3><a href="#/skills/backlog-triage.md"><code>backlog-triage</code></a></h3>
    <p class="skill-card-summary">Triages backlog ideas into clear scope, value, risk, and status with standardized metadata. Use when refining I-* entries before promotion to tracer bullet planning.</p>
    <div class="skill-card-facts">
      <p><strong>Enter when</strong><br />an I-* exists or a vision note has enough actionable scope to become one.</p>
      <p><strong>Call next</strong><br /><a href="#/skills/grill-me.md">grill-me</a>, <a href="#/skills/tracer-bullet-promote.md">tracer-bullet-promote</a></p>
      <p><strong>Not needed when</strong><br />the item is still pure orientation, or the slice is already fully scoped and validated.</p>
    </div>
    <p class="skill-card-link"><a href="#/skills/backlog-triage.md">Open detail</a></p>
  </article>
  <article class="skill-card skill-card--workflow">
    <div class="skill-card-meta">
      <span class="skill-card-order">03</span>
      <span class="skill-card-kind">Workflow skill</span>
    </div>
    <h3><a href="#/skills/grill-me.md"><code>grill-me</code></a></h3>
    <p class="skill-card-summary">Runs structured critical questioning on ideas and plans to expose assumptions, exception paths, and hidden risks. Use during analysis before committing to design or execution.</p>
    <div class="skill-card-facts">
      <p><strong>Enter when</strong><br />assumptions, exception paths, lifecycle effects, or boundary risks need pressure-testing.</p>
      <p><strong>Call next</strong><br /><a href="#/skills/backlog-triage.md">backlog-triage</a>, <a href="#/skills/tracer-bullet-promote.md">tracer-bullet-promote</a>, <a href="#/skills/component-design-pack.md">component-design-pack</a></p>
      <p><strong>Not needed when</strong><br />the change is low-risk, already bounded, and has known validation criteria.</p>
    </div>
    <p class="skill-card-link"><a href="#/skills/grill-me.md">Open detail</a></p>
  </article>
  <article class="skill-card skill-card--workflow">
    <div class="skill-card-meta">
      <span class="skill-card-order">04</span>
      <span class="skill-card-kind">Workflow skill</span>
    </div>
    <h3><a href="#/skills/tracer-bullet-promote.md"><code>tracer-bullet-promote</code></a></h3>
    <p class="skill-card-summary">Promotes ready ideas into bounded tracer bullets with vertical-slice scope, boundaries, and evidence criteria. Use when an I-* item is ready for execution planning.</p>
    <div class="skill-card-facts">
      <p><strong>Enter when</strong><br />an I-* is ready and execution needs a bounded vertical slice.</p>
      <p><strong>Call next</strong><br /><a href="#/skills/component-design-pack.md">component-design-pack</a>, <a href="#/skills/test-strategy-planner.md">test-strategy-planner</a></p>
      <p><strong>Not needed when</strong><br />the item still lacks direction, or the change is a tiny code/doc fix with no workflow slice.</p>
    </div>
    <p class="skill-card-link"><a href="#/skills/tracer-bullet-promote.md">Open detail</a></p>
  </article>
  <article class="skill-card skill-card--workflow">
    <div class="skill-card-meta">
      <span class="skill-card-order">05</span>
      <span class="skill-card-kind">Workflow skill</span>
    </div>
    <h3><a href="#/skills/component-design-pack.md"><code>component-design-pack</code></a></h3>
    <p class="skill-card-summary">Builds component-focused design briefs for impacted boundaries, mappings, validation rules, and error handling. Use after tracer bullet definition and before implementation.</p>
    <div class="skill-card-facts">
      <p><strong>Enter when</strong><br />a TB-* or feature scope touches component boundaries, mappings, validations, lifecycle/state behavior, or error behavior.</p>
      <p><strong>Call next</strong><br /><a href="#/skills/test-strategy-planner.md">test-strategy-planner</a>, <a href="#/skills/quality-gatekeeper.md">quality-gatekeeper</a></p>
      <p><strong>Not needed when</strong><br />the change is local, internal, and does not alter component responsibilities or observable behavior.</p>
    </div>
    <p class="skill-card-link"><a href="#/skills/component-design-pack.md">Open detail</a></p>
  </article>
  <article class="skill-card skill-card--workflow">
    <div class="skill-card-meta">
      <span class="skill-card-order">06</span>
      <span class="skill-card-kind">Workflow skill</span>
    </div>
    <h3><a href="#/skills/test-strategy-planner.md"><code>test-strategy-planner</code></a></h3>
    <p class="skill-card-summary">Plans a risk-based test strategy for a bounded change slice across unit, functional, elective, operational, and UI layers. Use when preparing analysis/design, tracer bullets, or implementation plans and when deciding which tests are necessary, relevant, cheap, and high-confidence.</p>
    <div class="skill-card-facts">
      <p><strong>Enter when</strong><br />a change has known risks and needs explicit test-layer selection.</p>
      <p><strong>Call next</strong><br /><a href="#/skills/quality-gatekeeper.md">quality-gatekeeper</a>, <a href="#/skills/traceability-sync.md">traceability-sync</a></p>
      <p><strong>Not needed when</strong><br />the change is purely editorial and does not affect behavior, contracts, workflows, or validation.</p>
    </div>
    <p class="skill-card-link"><a href="#/skills/test-strategy-planner.md">Open detail</a></p>
  </article>
  <article class="skill-card skill-card--workflow">
    <div class="skill-card-meta">
      <span class="skill-card-order">07</span>
      <span class="skill-card-kind">Workflow skill</span>
    </div>
    <h3><a href="#/skills/quality-gatekeeper.md"><code>quality-gatekeeper</code></a></h3>
    <p class="skill-card-summary">Applies quality gates across analysis, design, implementation, tests, and documentation traceability. Use before execution and before closing work to prevent drift.</p>
    <div class="skill-card-facts">
      <p><strong>Enter when</strong><br />work is about to move from analysis to execution, or from execution to closeout.</p>
      <p><strong>Call next</strong><br />implementation after go, or targeted design/test/traceability work after no-go.</p>
      <p><strong>Not needed when</strong><br />the task is a trivial change with no analysis, contract, test, or traceability impact.</p>
    </div>
    <p class="skill-card-link"><a href="#/skills/quality-gatekeeper.md">Open detail</a></p>
  </article>
  <article class="skill-card skill-card--workflow">
    <div class="skill-card-meta">
      <span class="skill-card-order">08</span>
      <span class="skill-card-kind">Workflow skill</span>
    </div>
    <h3><a href="#/skills/traceability-sync.md"><code>traceability-sync</code></a></h3>
    <p class="skill-card-summary">Synchronizes feature, specification, and test traceability across global and component documentation. Use when feature status, behavior, or validation evidence changes.</p>
    <div class="skill-card-facts">
      <p><strong>Enter when</strong><br />feature status, behavior, specs, tests, or validation evidence changed.</p>
      <p><strong>Call next</strong><br /><a href="#/skills/closeout.md">closeout</a></p>
      <p><strong>Not needed when</strong><br />no observable behavior, contract, feature status, or validation evidence changed.</p>
    </div>
    <p class="skill-card-link"><a href="#/skills/traceability-sync.md">Open detail</a></p>
  </article>
  <article class="skill-card skill-card--workflow">
    <div class="skill-card-meta">
      <span class="skill-card-order">09</span>
      <span class="skill-card-kind">Workflow skill</span>
    </div>
    <h3><a href="#/skills/closeout.md"><code>closeout</code></a></h3>
    <p class="skill-card-summary">Closes workflow slices with explicit status transitions, residual risk notes, and next-step clarity. Use at the end of tracer bullets, feature slices, or implementation cycles.</p>
    <div class="skill-card-facts">
      <p><strong>Enter when</strong><br />a TB-*, feature slice, blitz integration, retrofit slice, or implementation cycle is ending.</p>
      <p><strong>Call next</strong><br />no next skill by default; reopen triage, traceability sync, or retrofit only for named follow-up work.</p>
      <p><strong>Not needed when</strong><br />the work is still actively changing or required evidence is not yet available.</p>
    </div>
    <p class="skill-card-link"><a href="#/skills/closeout.md">Open detail</a></p>
  </article>
  <article class="skill-card skill-card--workflow">
    <div class="skill-card-meta">
      <span class="skill-card-order">10</span>
      <span class="skill-card-kind">Workflow skill</span>
    </div>
    <h3><a href="#/skills/plan-incubation.md"><code>plan-incubation</code></a></h3>
    <p class="skill-card-summary">Incubates a live working plan, then materializes durable output into product-docs with mandatory bidirectional traceability. Use when starting from a current-session plan under .cursor/plans/, plans/, or .github/prompts/plan-*.prompt.md before creating V-*, I-*, or TB-* artifacts.</p>
    <div class="skill-card-facts">
      <p><strong>Enter when</strong><br />the operator deliberately starts from a current-session working plan before canonical docs.</p>
      <p><strong>Call next</strong><br /><a href="#/skills/vision-intake.md">vision-intake</a>, <a href="#/skills/backlog-triage.md">backlog-triage</a>, <a href="#/skills/tracer-bullet-promote.md">tracer-bullet-promote</a></p>
      <p><strong>Not needed when</strong><br />the source is historical retrofit input or the request is already a direct implementation task.</p>
    </div>
    <p class="skill-card-link"><a href="#/skills/plan-incubation.md">Open detail</a></p>
  </article>
</div>

## Legacy retrofit skills

<div class="skill-card-grid">
  <article class="skill-card skill-card--retrofit">
    <div class="skill-card-meta">
      <span class="skill-card-order">11</span>
      <span class="skill-card-kind">Legacy retrofit skill</span>
    </div>
    <h3><a href="#/skills/legacy-plan-miner.md"><code>legacy-plan-miner</code></a></h3>
    <p class="skill-card-summary">Mines legacy project knowledge (historical plans, verbal briefings, ad hoc implementation history) to extract decisions, invariants, patterns, risks, and test signal. Use for weekly or opportunistic retrofit sessions.</p>
    <div class="skill-card-facts">
      <p><strong>Enter when</strong><br />historical plans, verbal history, or ad hoc implementation history contain reusable signal.</p>
      <p><strong>Call next</strong><br /><a href="#/skills/retrofit-curator.md">retrofit-curator</a></p>
      <p><strong>Not needed when</strong><br />the source is a current-session working plan, which belongs to plan-incubation.</p>
    </div>
    <p class="skill-card-link"><a href="#/skills/legacy-plan-miner.md">Open detail</a></p>
  </article>
  <article class="skill-card skill-card--retrofit">
    <div class="skill-card-meta">
      <span class="skill-card-order">12</span>
      <span class="skill-card-kind">Legacy retrofit skill</span>
    </div>
    <h3><a href="#/skills/retrofit-curator.md"><code>retrofit-curator</code></a></h3>
    <p class="skill-card-summary">Curates legacy retrofit slices by mapping extracted signal (plans, verbal, ad hoc) into canonical product-docs destinations, recording residual gaps, and promoting principle candidates when relevant.</p>
    <div class="skill-card-facts">
      <p><strong>Enter when</strong><br />mined historical signal needs mapping into canonical product-docs destinations.</p>
      <p><strong>Call next</strong><br /><a href="#/skills/traceability-sync.md">traceability-sync</a>, <a href="#/skills/closeout.md">closeout</a></p>
      <p><strong>Not needed when</strong><br />there is no reusable signal, or the source already lives in current canonical docs.</p>
    </div>
    <p class="skill-card-link"><a href="#/skills/retrofit-curator.md">Open detail</a></p>
  </article>
</div>

## Additional skills

<div class="skill-card-grid">
  <article class="skill-card skill-card--additional">
    <div class="skill-card-meta">
      <span class="skill-card-order">13</span>
      <span class="skill-card-kind">Method skill</span>
    </div>
    <h3><a href="#/skills/methodology-release.md"><code>methodology-release</code></a></h3>
    <p class="skill-card-summary">Classifies a methodology SemVer bump, drafts a release note, and updates the canonical version file when publishing to methodology.ezkey.org. Use after a publication milestone following Lane E feedback cycles.</p>
    <div class="skill-card-facts">
      <p><strong>Enter when</strong><br />a publication milestone is declared — enough methodology improvements justify</p>
      <p><strong>Call next</strong><br />site build/preview, then Cloudflare deploy per product-docs/site/HANDOFF-PHASE-4.md.</p>
      <p><strong>Not needed when</strong><br />changes are repo-only Lane E decisions with no public site update planned,</p>
    </div>
    <p class="skill-card-link"><a href="#/skills/methodology-release.md">Open detail</a></p>
  </article>
</div>

## Method links

- [Methodology Pack](#/methodology/README.md)
- [Session Start Guide](#/methodology/session-start-guide.md)
- [AI Collaboration Model](#/methodology/ai-collaboration-model.md)
- [Rich methodology view](#/methodology/view/index.html)
