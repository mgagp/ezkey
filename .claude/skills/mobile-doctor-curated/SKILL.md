---
name: mobile-doctor-curated
description: >-
  Runs the Ezkey Mobile static-analysis curated pass (react-doctor, Semgrep mobile pack, Detekt;
  SwiftLint and Periphery on macOS) and walks findings one at a time. Use when the operator says
  mobile-doctor-curated or asks for a mobile static-analysis hygiene pass.
disable-model-invocation: true
---

# mobile-doctor-curated

Punctual curated pass for `ezkey_mobile/`, **not** a CI gate and **not** a zero-warning campaign.

## Procedure

Follow [`ezkey_mobile/AGENTS.md`](../../../ezkey_mobile/AGENTS.md) § *Mobile doctor-curated pass*:
command, outputs, suppressions and the HITL contract live there.

In short:

1. From `ezkey_mobile/`: `yarn doctor:curated` (or `./scripts/mobile-doctor-curated.sh`).
2. Read `ezkey_mobile/logs/mobile-doctor/mobile-doctor.curated.md`.
3. Propose a small lot; walk **one finding at a time** and wait for the operator's decision.
4. Record the pass under
   [`product-docs/global/hygiene/mobile-doctor/`](../../../product-docs/global/hygiene/mobile-doctor/);
   suppressions go in `ezkey_mobile/config/mobile-doctor/suppressions.json`.

No `I-*` / `TB-*` / GitHub issue per finding.
