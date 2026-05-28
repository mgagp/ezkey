---
name: legacy-plan-miner
description: Mines legacy project knowledge (historical plans, verbal briefings, ad hoc implementation history) to extract decisions, invariants, patterns, risks, and test signal. Use for weekly or opportunistic retrofit sessions.
disable-model-invocation: true
---
# Legacy Knowledge Miner

## Purpose

Extract high-signal knowledge from legacy sources without migrating historical narrative wholesale.

## Boundary contract

- **Enter when:** historical plans, verbal history, or ad hoc implementation history contain reusable signal.
- **Exit when:** decisions, invariants, patterns, risks, and test signals are extracted with confidence and source type.
- **Call next:** `retrofit-curator` to map extracted signal into canonical destinations.
- **Not needed when:** the source is a current-session working plan, which belongs to `plan-incubation`.

## Inputs

- Source batch of 1 to 3 sources (plans and/or verbal capture)
- Optional topic focus

## Output

- Extracted signal grouped as:
  - decisions,
  - invariants and rules,
  - patterns and posture,
  - risks and trade-offs,
  - test and validation signal.

## Method

1. Read sources quickly for topic boundaries.
2. Extract only reusable signal.
3. Mark confidence (`high`/`medium`/`low`) for each key item.
4. Flag contradictions or stale assumptions.
5. Mark source type (`plan`, `verbal`, `ad-hoc-code-history`, `mixed`).

## Rule

Prefer concise, reusable knowledge over archival storytelling.
