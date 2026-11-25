# UI Walk Gate — `WALK-YYYY-MM-DD-<slug>`

Copy into a `WALK-*` record under `product-docs/global/backlog/walk-gates/`. A copy-only change on one
screen takes a three-to-five-row checklist in the PR description instead (sizing:
[`ui-walk-done-gate.md`](../global/ui-walk-done-gate.md) § *Sizing the gate*).
**Delete sections that do not apply.** Empty sections are not a gate.

Scope: **any human UI surface** (Admin UI, demo apps, tester pages, mobile copy).

## Metadata

- **ID:** `WALK-YYYY-MM-DD-<slug>`
- **Interface gate filled by:** _session or person_ — Marc confirms
- **Walk executor:** scripted walk (`ezkey-admin-ui/e2e/walks/<slug>.walk.spec.ts`) / manual (stack-dependent flow)
- **Single walkable SHA / PR:** `https://github.com/mgagp/ezkey/pull/NNN` @ `<sha>`
- **Related craft PR(s):** _trailers only — do not walk trailers separately_
- **Created:** `YYYY-MM-DD`
- **Surface(s):** Admin UI / demo / tester page / mobile copy — _list which_

## Done (one line)

> Example: One SHA with `--runtime=base` honesty chrome on `/integrity` + walk checklist 1–6 PASS with proofs → mergeable for UI.

## Stack

- Runtime profile: `default` / `base` / other: `___`
- How to start: clean-start command or community host URL
- Admin role(s): Global Admin / Tenant Admin / both / N/A (demo/tester)
- Login path: (e.g. `admin.docker` + Demo Device; challenge off?)

## New-mode content pass (when a new entry mode or use case is introduced)

| Existing block | keep / adapt / hide | Reason |
|----------------|---------------------|--------|
| | | |

- [ ] Visual composition checked (presence of an element is not enough)

## Realistic timing and error states (human-approval flows)

- [ ] Approve at ~5 s
- [ ] Approve just past each inventoried timeout on the path
- [ ] Approve near TTL
- [ ] Refuse, expiry, session loss
- [ ] EN / FR as required by vision locks
- An automated walk that approves in <5 s does **not** close this gate.

## "Never logged" properties

- [ ] Proven on **runtime output of all containers** (dependencies included), not only module source

## Vision locks

| Vision lock | Verified how | INTENTION if finding contradicts lock |
|-------------|--------------|---------------------------------------|
| | | |

Findings that contradict a vision lock are marked **INTENTION** and block until an explicit
maintainer deferral.

## Checklist (numbered, binary)

Each row: **observable** assert + **pass/fail**. Add rows as needed; keep ≤10.

| # | Assert (what must be true) | Also assert absence (must NOT) | Proof |
|---|----------------------------|--------------------------------|-------|
| 1 | | | capture / network / note |
| 2 | | | |
| 3 | | | |

## Locales

- [ ] EN
- [ ] FR
- _or:_ EN only this walk

## Network / API claims (if any)

- Must see: e.g. `GET …/integrity/bootstrap`
- Must not see: e.g. `GET …/dashboard/overview` used for honesty chrome

## Out of scope (one line)

> Example: no cut-2 layout; no Tenant Admin; no mobile enroll; no Alerts remediation atelier.

## Expected verdict

- [ ] PASS (ship / merge UI slice)
- [ ] FAIL documented (baseline / intentional gap) — list which rows must fail

## Dispatch rule

- **Do not walk** until this block is filled and the SHA is walkable.
- **Do not declare UI complete** without a walk PASS (or explicit documented FAIL baseline).
- Entry may be any issue, review or session; **exit walk always uses this gate**.
