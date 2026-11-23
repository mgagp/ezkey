---
name: doctor-curated
description: >-
  Runs the Admin UI React Doctor curated pass (lint and polish shortlist, P1/P2/P3) and walks
  findings one at a time with the operator. Use when the operator says doctor-curated or asks for
  an Admin UI React lint or polish pass.
disable-model-invocation: true
---

# doctor-curated (Admin UI)

Punctual curated pass, **not** a CI gate and **not** a zero-warning campaign. Sibling of
`java-doctor-curated`, same operating model.

## Procedure

Follow [`ezkey-admin-ui/AGENTS.md`](../../../ezkey-admin-ui/AGENTS.md) § *React Doctor curated
pass*: command, output files and the full HITL contract live there.

In short:

1. Run the curated React Doctor workflow from `ezkey-admin-ui/`; read the shortlist.
2. Propose a small lot. Walk **one finding at a time**, with a short project-contextual
   learning briefing in each turn, and wait for the operator's decision.
3. Record decisions in a dated campaign note under
   [`product-docs/global/hygiene/react-doctor/`](../../../product-docs/global/hygiene/react-doctor/).
4. Implement accepted fixes on a dedicated hygiene branch and PR. Put the same briefing in the PR
   body and link the campaign note. A modest low-signal allotment is fine after high-signal items.

No `I-*` / `TB-*` / GitHub issue per finding.
