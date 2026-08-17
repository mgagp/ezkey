# Corpus ablation — Cursor plans closeout (2026-08-17)

## Metadata

- **Date:** 2026-08-17
- **Branch:** `hygiene/corpus-ablation-2026-08`
- **Pass log:** [`2026-08-cursor-plans-pass.md`](2026-08-cursor-plans-pass.md)
- **Skill:** `.cursor/skills/corpus-ablation/SKILL.md`
- **Operator:** Marc (+ agent)

This is a **hygiene campaign instance**, not an `I-*` / `TB-*`. The pass log stays the working
inventory (Resume, Done, Parking). This note is the dated closeout when the plan queue emptied
and remaining parking was reclassified.

## What stays (living scaffolds — not parking)

Verified unique option space. Do **not** delete in this PR.

| Keep | Why |
|------|-----|
| `.cursor/plans/authentication_wait_evolution_brainstorm.plan.md` | Lane B option space vs `V-2026-07-18` / `I-2026-07-18` |
| `.github/prompts/reported/plan-worktreeDockerStackNaming.prompt.md` | Compose `-p` from `buildQualifier` + dropping fixed `container_name` |

Hors-passe, untouched: `.github/prompts/ff-main.prompt.md`,
`mini-rfc-onboarding-experimental-recovery-codes-first.prompt.md`.

## What this pass liquidated

HITL prune of `.cursor/plans/*` and related GitHub `plan-*.prompt.md` scaffolds into existing
canon. Git history is the archive. Archived `plan-*` queue is **empty**.

## Complementary parking closeout (same day)

Spot-checked remaining `P-*` rows against living code/docs.

**Stale (closed):** P-072 (mobile `package.json` `1.0.0` drives Android `versionName`); P-078
(`postman/collections/v2.1/` JSON gone).

**Deferred unfunded / experimental (closed for this pass, like P-050):** P-057, P-070, P-075,
P-076, P-079, P-080, P-090, P-094, P-096, P-098, P-100, P-101 (OSIV stays `true` on purpose),
P-106, P-109. No new `I-*`. Experimental work stays in `ezkey-pam/`, `ezkey_dart/`,
`experimental-hybrid/`.

## Residue register (verified, still open)

These are **real leftover engineering or the next documentary pass**. They are not unfinished
scaffold prune. Full rows: pass-log **Parking (open — verified residue)**.

| Bucket | IDs | What it is |
|--------|-----|------------|
| Next documentary ablation | P-071 | Temporary `plans/` corpus (~25 lifecycle files) |
| Admin UI operator gaps | P-059–P-065, P-069, P-085–P-087, P-089 | Filters, Orval leftovers, GA route guard, audit-gap UX, Playwright |
| Security / protocol | P-056, P-066–P-068, P-088, P-104–P-105 | Tests, respond lock, HMAC compare, non-root compose, filter duplication, in-memory rate limit |
| Mobile release / iOS | P-073, P-091, P-093 | Play Console listing, `Podfile.lock`, banner dismiss |
| CLI / TUI | P-103, P-107–P-108 | Dead create modal; tenant detail copy |
| Tests | P-077 | Integration-API auth-attempt wait coverage |

Do **not** invent `I-*` / `TB-*` per row. Fund a slice only when the operator chooses.

## Decisions

| Topic | Decision |
|-------|----------|
| Kept plans | Remain. Unique option space. |
| Hors-passe prompts | Remain. Out of this pass. |
| Unfunded product / experimental parking | Closed as deferred for this pass. Not ablation residue. |
| Residue register | Stays in the pass log until a later funded slice or a `plans/` ablation pass. |
| Integration HA docs | Do not rewrite `docker/README-HA.md` without keeping Integration HA + V5 ShedLock. |

## Follow-ups

- Push + PR when the operator asks.
- Next ablation (if any): `plans/` (P-071), as a **new** pass log.
