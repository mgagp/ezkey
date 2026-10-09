---
name: critical-review
description: >-
  Launches a fresh-session adversarial (critical) review of one Ezkey artifact — a corpus doc or a
  PR — by a cloud agent on a forced different model family from the author, read-only, returning a
  ranked report tied to product intent, values, operability and pragmatism; then one-finding HITL.
  Use when the operator or a gate says critical-review / critical review / revue critique /
  adversarial review / plan hardening (Stage 2).
disable-model-invocation: true
---

# Critical review

## Purpose

Launched adversarial review for Stage 2 of the two-stage plan hardening pattern (methodology
README). Fresh cloud agent, **forced different model family** from the author (mandatory), high
effort, read-only, same product compass. Not a CI gate; not a second methodology. Same weight
class as `assessment-curated` (skill + hygiene canon + prompt).

## Boundary contract

- **Enter when:** G1 brief / P0–P1 vision before merge / security, migration, cross-component
  contract / autonomous cold-execution plan; or operator says `critical-review` (or aliases).
- **Exit when:** findings decided (`amend` / `defer` / `skip`) and the dated note exists.
- **Not needed when:** routine hygiene; a `*-curated` lane owns the signal; local obvious fix.

## Parameters

| Param | Required | Notes |
| --- | --- | --- |
| `target` | yes | Doc path or `PR #N` |
| `extras` | no | Related vision / issue / brief paths or numbers |
| `model` + `effort` | yes | **≠ author model family** (mandatory); high effort |
| `angle` | no | e.g. product premise, security, operability, autonomous execution |

**Launchers:** Mathieu default (visions, G1 briefs); Patrick (PR craft); Christophe (security);
Marc on request.

## Procedure

1. Resolve the target: doc → path @ SHA; PR → `git fetch origin pull/<N>/head`, record head SHA.
2. Fill [`product-docs/templates/critical-review.prompt.md`](../../product-docs/templates/critical-review.prompt.md)
   (parameters + baseline list).
3. Launch **ONE** cloud agent (fresh session, chosen model/effort ≠ author family), read-only —
   no branch, push, PR, issue, or GitHub comment (disable auto-PR if offered).
4. Relay the report: lot overview, then **one finding at a time** → `amend` / `defer` / `skip`
   (never bare “No-Go”; `amend` = GO for that finding).
5. Write `product-docs/global/hygiene/critical-review/YYYY-MM-DD-<slug>.md` from TEMPLATE.
6. Amendments go to the author's branch (or a follow-up PR) — never by the reviewer.

## Authority

- Canon: [`product-docs/global/hygiene/critical-review/README.md`](../../product-docs/global/hygiene/critical-review/README.md)
- Prompt: [`product-docs/templates/critical-review.prompt.md`](../../product-docs/templates/critical-review.prompt.md)
- Execution-hardening angle (by reference): [`product-docs/templates/cold-agent-plan-review.prompt.md`](../../product-docs/templates/cold-agent-plan-review.prompt.md)
- Pattern: [`product-docs/methodology/README.md`](../../product-docs/methodology/README.md) § two-stage

## If the operator asks how to invoke

Quote README § How to invoke (operator cheat sheet).
