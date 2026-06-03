# Methodology Skills

This folder contains skills that support the documentation-first workflow from ideation to closeout.

## Lanes

- **Lane A — Vision lane**: direction, hypotheses, and non-immediate execution framing.
- **Lane B — Plan incubation lane**: live working plan first, canonical materialization second.
- **Lane C — Legacy knowledge retrofit lane**: extraction and reintegration of historical plans, verbal rationale, and ad hoc implementation signal.
- **Lane D — Post-delivery re-entry lane**: start from existing implemented behavior, classify local defect vs intent gap, then either fix directly or re-enter at `TB-*`, `I-*`, or `V-*`.
- **Lane E — Methodology feedback lane**: decisions about the method itself, recorded as methodology decisions plus targeted corpus updates.

Lanes D and E do not require dedicated skills by default. Reuse the existing workflow skills only at the level where the work actually re-enters the canonical flow.

## Workflow skills

- `vision-intake`
- `backlog-triage`
- `grill-me`
- `plan-incubation`
- `tracer-bullet-promote`
- `component-design-pack`
- `test-strategy-planner`
- `quality-gatekeeper`
- `traceability-sync`
- `closeout`
- `legacy-plan-miner`
- `retrofit-curator`
- `methodology-release` — classify SemVer bump and draft release notes when publishing the public methodology product

## Suggested sequence

This is the default sequence for Lane A leading into delivery. Lanes B, C, D, and E are conditional entry variants or parallel lanes, not mandatory extra phases.

1. Vision intake
2. Backlog triage
3. Grill Me analysis
4. Plan incubation
5. Tracer bullet promotion
6. Component design pack
7. Test strategy planning
8. Quality gatekeeper
9. Traceability sync
10. Closeout

## Legacy retrofit skills (parallel lane)

1. Legacy plan miner
2. Retrofit curator

## Operator prompt cues (quick memory aid)

- "brainstorm", "direction", "not implementing now" -> vision lane
- "start with a plan", "use plan mode first", "brainstorm in a working plan" -> plan incubation lane
- "ready to execute", "implement now", "vertical slice" -> delivery lane
- "retrofit", "historical plans", "verbal history", "project principles" -> legacy knowledge retrofit lane
- "enhancement on existing code", "apparent bug", "change an existing feature" -> post-delivery re-entry lane
- "methodology", "process rule", "workflow improvement", "how should the method evolve" -> methodology feedback lane
