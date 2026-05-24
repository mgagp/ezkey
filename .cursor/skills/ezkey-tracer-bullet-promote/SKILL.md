---
name: ezkey-tracer-bullet-promote
description: Promotes ready Ezkey ideas into bounded tracer bullets with vertical-slice scope, boundaries, and evidence criteria. Use when an I-* item is ready for execution planning.
disable-model-invocation: true
---
# Ezkey Tracer Bullet Promote

## Purpose

Turn a `ready` idea into a validated first vertical slice (`TB-*`).

## Inputs

- `I-*` idea
- Current risk and scope understanding

## Outputs

- `TB-*` brief
- In-scope boundaries
- Out-of-scope boundaries
- Acceptance evidence plan
- Initial test-layer plan link

## Steps

1. **Evaluate iteration posture** — before scoping the slice, answer explicitly:
   *"Single-pass or iterative?"*
   - `single-pass`: the entire bounded job fits one coherent implementation cut with acceptable
     risk and evidence cost. The TB brief is the complete implementation plan.
   - `iterative`: scope, risk, or cross-boundary complexity warrants separate slices.
   Record the posture as `posture: single-pass` or `posture: iterative` in the TB brief.
   When `single-pass`, do not frame the session around future iterations.
2. Define the smallest meaningful end-to-end slice.
3. Identify touched component boundaries.
4. Define critical nominal and exception path.
5. Define expected evidence (tests, docs, traceability).
6. Mark explicit exclusions for this slice.

## Rule

Favor one deep vertical over broad partial coverage.
When `posture: single-pass`, close the TB once the one-shot implementation is validated — do not
extend or re-scope it as an iterative series.
