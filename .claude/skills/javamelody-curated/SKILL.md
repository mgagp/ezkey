---
name: javamelody-curated
description: >-
  Freezes and ranks JavaMelody counters from a live local stack after a known workload, then walks
  a small performance lot one finding at a time. Use when the operator says javamelody-curated or
  asks for a performance pass on JavaMelody data.
disable-model-invocation: true
---

# javamelody-curated

Punctual performance pass, **not** a CI gate and **not** a zero-warning campaign. Canon and
operating contract: [`product-docs/global/hygiene/javamelody/README.md`](../../../product-docs/global/hygiene/javamelody/README.md).

## Procedure

1. **Do not clean-start** before `logs/javamelody/raw/` holds the current counters: per-request
   stats live in memory and are lost when an API restarts.
2. Run `./scripts/javamelody-curated.sh` (re-parse a frozen dump without HTTP: `--offline`).
   Config: `config/javamelody/` (noise paths, expected-hot churn routes).
3. Read `logs/javamelody/javamelody.curated.md`, including the workload-bias banner.
4. Propose a small lot (about 3–6); walk **one finding at a time**; record `fix` / `defer` / `skip`
   in a dated note from the lane `TEMPLATE.md`. An empty high-signal lot is a valid closeout.

## Reading the signal

- A high hit count on churn protocol paths is **not** a signal. Look at mean time,
  SQL-per-request, errors and pool wait.
- Integration API silence under operational churn means **no coverage**, not good health.

No `I-*` / `TB-*` per finding.
