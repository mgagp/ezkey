---
name: quality-gate
description: >-
  Runs the on-demand full-suite and stack-health gate and reports GO / NO-GO / GO with
  reservations / INCONCLUSIVE. Use when the operator says quality-gate or asks for a full gate
  between merge batches.
disable-model-invocation: true
---

# quality-gate

Canon: [`product-docs/global/hygiene/quality-gate/README.md`](../../../product-docs/global/hygiene/quality-gate/README.md)
(prerequisites, phases, artifacts). **Not** a CI gate.

1. Run `./scripts/quality-gate.sh` (Bash 4 or later; on macOS use Homebrew bash).
2. Report the exit code with its meaning: **0** GO · **1** NO-GO · **2** bad arguments ·
   **3** GO with reservations · **4** INCONCLUSIVE (HA invalid or a skipped phase).
3. Point to the artifacts under `logs/quality-gate/<UTC>-<shortsha>/` and name each failed or
   skipped phase. Never present a skipped phase as green.
