# Methodology Ablation Record — 2026-08-08

## Status

**Published as methodology v2.0.0.** The corpus cut landed in commit `a9d65cc4`. The public
explorer (`methodology.ezkey.org`) and the apex pointer pages (`ezkey.org/methodology.html`,
`ezkey.org/fr/methodologie.html`) were rebuilt around the condensed corpus in the same
republication effort. `methodology-version.properties` declares `2.0.0`.

## Why

A companion article (`sites/ezkey-org-editorial/fr/draft-ablation-methodologie-mode-plan.md`)
argued that most of this methodology pack had become ceremony compensating for weaker models, and
that repeated attempts to trim it had quietly preserved the status quo instead of applying the
ablation principle for real. This record is the result of applying it literally: delete, then
watch what a cold agent visibly needs back.

## Before / after

| Surface | Before | After |
| --- | --- | --- |
| Method documents | 21 (`README.md` + 20 workflow docs) | 1 (`README.md`, ~230 lines) |
| Decision records | 32 dated files + index | 0 (git history is the archive) |
| Templates | 20 | 4 (`vision-note`, `backlog-idea`, `tracer-bullet-brief`, `architecture-decision`) |
| Methodology-workflow skills | 14 | 0 |
| Operational skills (unchanged) | 3 | 3 (`dependabot-curated`, `monthly-digest`, `assessment-curated`) |
| Rich HTML view | 1 (~1,450 lines) | 0 (apex pages are short pointers to the explorer) |
| **Approximate total weight** | **~10,500 lines / ~61,000 words** | **~650 lines** |

Roughly a 93% reduction.

## What was cut outright

- All 32 methodology decision records — rationale remains recoverable from Git history; none of it
  was load-bearing enough to justify a live file once its conclusion was folded into the core
  document (see below) or simply left to expire.
- 20 of 21 method documents: `workflow-overview.md`, `session-start-guide.md`,
  `minimum-viable-method.md`, `nomenclature.md`, `plan-incubation-workflow.md`,
  `github-issues-workflow.md`, `blitz-intake-pattern.md`, `legacy-retrofit-workflow.md`,
  `design-judgment-principles.md`, `methodological-values.md`, `release-management-workflow.md`,
  `artifact-identity-and-review.md`, `multi-branch-workflow.md`,
  `methodology-publication-and-versioning.md`, `case-study-ezkey.md`, `tracer-bullet-method.md`,
  `analysis-and-design-canon.md`, `ai-collaboration-model.md`, `quality-gates.md`,
  `testing-strategy-in-workflow.md`.
- 14 methodology-workflow skills: `vision-intake`, `backlog-triage`, `grill-me`,
  `plan-incubation`, `tracer-bullet-promote`, `github-issue-promote`, `component-design-pack`,
  `test-strategy-planner`, `quality-gatekeeper`, `traceability-sync`, `closeout`,
  `legacy-plan-miner`, `retrofit-curator`, `methodology-release`.
- 16 of 20 templates (kept the 4 mapped directly to a documentary level).
- The rich HTML view (`view/index.html`) and its apex publication script
  (`scripts/publish-methodology-view.ps1`).

## What was folded into the condensed core, and where

Everything judged genuinely load-bearing moved into
[`../README.md`](../README.md) as a section, not a file:

| Old surface | New home |
| --- | --- |
| `nomenclature.md` (ID formats, status vocabularies, priority scale) | § *1. Documentary levels* |
| `methodological-values.md` + `design-judgment-principles.md` (20+ values/principles) | § *3. The values compass* (6 retained) |
| `2026-06-06-methodological-closeout-vs-code-hygiene.md` | § *Hygiene vs. program* |
| `2026-07-11-cursor-plan-ephemeral-vs-retained-working-plan.md` | § *Ephemeral scaffold vs. retained plan* |
| `2026-05-28-methodology-feedback-and-post-delivery-reentry.md` (closed-`TB-*` non-reopening rule) | § *Closed uncertainty stays closed* |
| `github-issues-workflow.md` label taxonomy + checklist | `.cursor/rules/github-issue-labels.mdc` (made self-contained) |

Lane taxonomy (A/B/C/D/E), the skill-sequence diagrams, and the finite-state "enter/exit/call
next/not needed when" scaffolding for each lane were not folded anywhere — the article's verdict
was that this is exactly the class of structure a competent Plan-mode session already provides.

## Public surfaces after republication

- **`methodology.ezkey.org`** — explorer rebuilt for the condensed corpus: one guided **Read**
  track, search, download pack; no skills layer, no workflow-phase ribbon, no cognitive map.
- **`ezkey.org/methodology.html`** / **`/fr/methodologie.html`** — short hand-written pointers to
  the explorer (the generated rich view is gone).
- Dated artifacts under `product-docs/global/**` may still mention deleted decisions and docs.
  They are historical records of what was true when they were written, not live navigation —
  updating them would be new ceremony, not ablation.

## Observation protocol

Work normally for the coming sessions. When a cold agent visibly misses something the deleted
corpus used to carry, note it before re-adding — and re-add only as a line in
[`../README.md`](../README.md), never as a new file. That running list, plus this record, is the
intended input for further calibration.
