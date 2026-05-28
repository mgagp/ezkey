---
name: grill-me
description: Runs structured critical questioning on Ezkey ideas and plans to expose assumptions, exception paths, and hidden risks. Use during analysis before committing to design or execution.
disable-model-invocation: true
---
# Grill Me

## Purpose

Stress-test an idea or plan through focused questions before design lock-in.

## Boundary contract

- **Enter when:** assumptions, exception paths, lifecycle effects, or boundary risks need pressure-testing.
- **Exit when:** risks, open questions, decision pressure points, and next clarifications are explicit.
- **Call next:** `backlog-triage` for status alignment, `tracer-bullet-promote` for execution scope, or `component-design-pack` for component detail.
- **Not needed when:** the change is low-risk, already bounded, and has known validation criteria.

## Input

- `I-*`, `TB-*`, or draft plan
- current-session working plan during plan incubation

## Output

- Critical questions
- Risk list
- Exception and error paths
- Decision pressure points
- Recommended next clarifications

## Question set

1. What must be true for this to succeed?
2. What fails first under realistic misuse?
3. Which boundary contracts are most fragile?
4. Which lifecycle transitions are high risk?
5. Which role/permission assumptions may break?
6. What minimal evidence would disprove the current plan?

## Rule

Keep questioning concrete and decision-oriented. Avoid abstract critique without action value.
When the input is a live working plan, use questioning to help convergence toward canonical materialization, not to force premature execution detail.
