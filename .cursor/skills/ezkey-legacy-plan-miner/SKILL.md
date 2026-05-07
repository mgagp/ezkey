---
name: ezkey-legacy-plan-miner
description: Mines legacy Ezkey knowledge (historical plans, verbal briefings, ad hoc implementation history) to extract decisions, invariants, patterns, risks, and test signal. Use for weekly or opportunistic retrofit sessions.
disable-model-invocation: true
---
# Ezkey Legacy Knowledge Miner

## Purpose

Extract high-signal knowledge from legacy sources without migrating historical narrative wholesale.

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
