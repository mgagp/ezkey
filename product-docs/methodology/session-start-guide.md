# Session Start Guide and Lexicon

## Purpose

This is a practical cheat sheet for starting sessions with the right lane, vocabulary, and skill sequence.

## Choose the lane

### Lane A — Vision brainstorming (no immediate execution)

Use when the goal is orientation, options, or product direction.

- Output target: `V-*`, optionally `I-*` in `captured` or `incubating`
- Typical mode: **Plan**
- Typical skills: `ezkey-vision-intake`, `ezkey-backlog-triage`, `ezkey-grill-me`

For **multi-item capture sessions** (typical when several ideas accumulate or voice dictation is used), apply the [blitz intake pattern](blitz-intake-pattern.md) as a Lane A variant. It preserves the verbatim source through an explicit archival step.

### Lane B — Delivery execution

Use when the goal is scoped implementation.

- Output target: `TB-*` + component design + test plan + implementation
- Typical mode: **Plan -> Agent**
- Typical skills: `ezkey-tracer-bullet-promote`, `ezkey-component-design-pack`, `ezkey-test-strategy-planner`, `ezkey-quality-gatekeeper`, `ezkey-traceability-sync`, `ezkey-closeout`

### Lane C — Legacy knowledge retrofit

Use when the goal is extracting value from historical plans, verbal rationale, or ad hoc history.

- Output target: `R-*` + canonical doc updates
- Typical mode: **Plan -> Agent**
- Typical skills: `ezkey-legacy-plan-miner`, `ezkey-retrofit-curator`, `ezkey-traceability-sync`

## Vocabulary quick reference

- `V-*`: vision direction note
- `I-*`: backlog idea
- `TB-*`: tracer bullet execution slice
- `R-*`: legacy knowledge retrofit slice
- `captured/triaged/incubating/ready/active/done/parked/archived/dropped`: backlog lifecycle
- `captured/mapped/integrated/archived`: retrofit lifecycle

## Prompt starters

### Start a vision brainstorm

`Use ezkey-vision-intake and ezkey-grill-me for this product direction. I do not want implementation now.`

### Start a delivery slice

`Promote this ready idea to TB and prepare component design plus test plan.`

### Start a retrofit session

`Start a weekly retrofit lane for topic X. Use ezkey-legacy-plan-miner and ezkey-retrofit-curator. Sources are verbal plus these plan files: ...`

### Promote principle candidates

`From this retrofit slice, extract principle candidates and propose where to adopt them (design principles vs AGENTS/rules).`
