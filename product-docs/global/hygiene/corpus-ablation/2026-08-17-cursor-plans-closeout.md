# Corpus ablation — Cursor plans closeout (2026-08-17)

## Metadata

- **Date:** 2026-08-17
- **Branch:** `hygiene/corpus-ablation-2026-08`
- **Pass log:** [`2026-08-cursor-plans-pass.md`](2026-08-cursor-plans-pass.md)
- **Skill:** `.cursor/skills/corpus-ablation/SKILL.md`
- **Operator:** Marc (+ agent)

This is a **hygiene campaign instance**, not an `I-*` / `TB-*`. The pass log stays the working
inventory (Resume, Done, Parking). This note is the dated closeout when the plan queue emptied.

## What this pass liquidated

HITL prune of `.cursor/plans/*` and related GitHub `plan-*.prompt.md` scaffolds into existing
canon (`docs/`, module `AGENTS.md`, living code). Git history is the archive.

- Cursor living plans: **one kept** — `authentication_wait_evolution_brainstorm.plan.md`
- GitHub prompts: archived `plan-*` queue **empty**; **one kept** —
  `reported/plan-worktreeDockerStackNaming.prompt.md`
- Skip: `ff-main.prompt.md` (workflow helper)

## Documentary parking burn-down (this closeout)

Closed: P-058, P-082, P-084, P-092, P-095, P-097, P-099, P-102.

Not closed (product/code, no new `I-*`): tests, Admin UI features, security code, unfunded
protocol extras, experimental-lane renames, TUI mutations. See pass-log **Parking (open)**.

## Decisions

| Topic | Decision |
|-------|----------|
| Remaining parking | Leave as deferred rows. Do not invent backlog artifacts per row. |
| Experimental lanes | Stay in `ezkey-pam/`, `ezkey_dart/`, `experimental-hybrid/`. |
| Integration HA docs | Do not rewrite `docker/README-HA.md` without keeping Integration HA + V5 ShedLock. |

## Follow-ups

- Rebase this branch onto current `origin/main` when the operator asks.
- Next ablation (if any) is a **new pass log**, not a continuation of the empty queue.
