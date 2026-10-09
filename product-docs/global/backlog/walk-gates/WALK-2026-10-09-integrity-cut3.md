# UI Walk Gate — `WALK-2026-10-09-integrity-cut3`

Copy into the PR description / Isabelle dispatch brief.
Canon: [`../../../templates/ui-walk-gate.template.md`](../../../templates/ui-walk-gate.template.md), [`../../ui-walk-done-gate.md`](../../ui-walk-done-gate.md).

## Metadata

- **ID:** `WALK-2026-10-09-integrity-cut3`
- **Owner (intention):** Julie / Marc / Patrick
- **Walk executor:** Isabelle (default)
- **Single walkable SHA / PR:** `TBD` — fill after PR 2 (or combined walkable tip); do not walk this docs-only intention PR
- **Related craft PR(s):** PR 1 backend (projection, counters, gate, cap); PR 2 UI (micro-dashboard, D1 rule, #696)
- **Created:** `2026-10-09`
- **Intention note:** [`../../admin-ui-integrity-cut3.md`](../../admin-ui-integrity-cut3.md)
- **Parent IA:** [`../../admin-ui-audit-integrity-hard-split-intention.md`](../../admin-ui-audit-integrity-hard-split-intention.md)
- **Predecessor:** cut 2 phase A/B Walk Gates (density + modes stay; D1 supersedes auto-verify-on-mount)

## Done (one line)

> One SHA where `/integrity` shows the last verdict micro-dashboard (honest scope, aging time, no "réussi" as verdict), auto-runs only when no fresh (<24 h) verdict exists, and Isabelle checks 1–10 PASS with network + captures → mergeable for cut 3 UI.

## Stack

- Runtime profile: **default** (`integrity` — monitoring on, nightly enabled for check #9).
- How to start: From `ezkey-tests/`: `./clean-start.sh`; Admin UI via usual local/dev path.
- Admin role(s): **Global Admin** only.
- Login path: `<admin>` + Demo Device; challenge as usual.

## Checklist (numbered, binary)

| # | Assert (what must be true) | Also assert absence (must NOT) | Proof |
|---|----------------------------|--------------------------------|-------|
| 1 | 3-second glance of micro-dashboard without reading a sentence works for green / gap / violation / stale / running / did-not-complete | Must NOT require opening Vérifier or reading a sentence banner | capture |
| 2 | Result arriving while staying on Observer is noticed (tile change + ~2 s highlight + aria-live) | Must NOT toast when green | capture / a11y note |
| 3 | Gap or violation never labeled "réussi" / "succeeded" as the verdict | Must NOT show sober green for non-intact | capture |
| 4 | Relative "checked" time ages on the page’s 1 s clock | Must NOT stay stuck on "À l'instant" across minutes | capture / timed note |
| 5 | Scope line + author visible in Observer, Vérifier, and Remédier | Must NOT hide scope only under Vérifier | capture |
| 6 | When Vérifier filter ≠ verdict range, mismatch copy appears | Must NOT silently imply the picker is the verified window | capture |
| 7 | FR and EN glance strings are UI i18n only | Must NOT show server English `resultSummary` in the glance | capture |
| 8 | Non-green result arriving mid-work does not auto-switch tab; **Ouvrir Remédier** is available | Must NOT yank the operator off Observer/Vérifier mid-work | capture |
| 9 | Open during nightly shows "Vérification nocturne en cours…" (or EN equivalent) | Must NOT show 409 error toast on open | capture / network |
| 10 | With a fresh (<24 h) last verdict, open starts no job | Must NOT `POST …/jobs` or `GET /chain-integrity` on open | network tab |

## Locales

- [ ] EN
- [ ] FR
- _or:_ EN walk + FR key spot-check (both locales required in the PR)

## Network / API claims (if any)

- Must see when fresh verdict: `GET …/integrity/jobs/current` (and/or nightly last-run source once OQ4 lands).
- Must not see on open with fresh verdict: `POST …/integrity/jobs`, `GET …/chain-integrity`.
- Cap: selecting / requesting beyond `max-window-days` (default 92) → 400.

## Out of scope (one line)

> No cut-2 density/modes redesign; no timeline `cpQuiet` changes; no job-history purge; no Orval migration; no inventing measurement numbers.

## Expected verdict

- [ ] PASS (ship / merge UI slice)
- [ ] FAIL documented (baseline / intentional gap) — list which rows must fail

## Dispatch rule

- **Do not walk** until this block is filled and the SHA is walkable (implementation PRs, not this docs-only intention PR).
- **Do not declare UI complete** without Isabelle PASS (or explicit documented FAIL baseline).
