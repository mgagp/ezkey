# UI Walk Gate — `WALK-YYYY-MM-DD-<slug>`

Copy into the PR description, a short note under the PR, or the dispatch brief to Isabelle.
**Delete sections that do not apply.** Empty sections are not a gate.

## Metadata

- **ID:** `WALK-YYYY-MM-DD-<slug>`
- **Owner (intention):** Julie / K / Marc — _who fills this block_
- **Walk executor:** Isabelle (default) / other
- **Single walkable SHA / PR:** `https://github.com/mgagp/ezkey/pull/NNN` @ `<sha>`
- **Related craft PR(s):** _trailers only — do not walk trailers separately_
- **Created:** `YYYY-MM-DD`

## Done (one line)

> Example: One SHA with `--runtime=base` honesty chrome on `/integrity` + Isabelle checklist 1–6 PASS with proofs → mergeable for UI.

## Stack

- Runtime profile: `default` / `base` / other: `___`
- How to start: clean-start command or EXP1 URL
- Admin role(s): Global Admin / Tenant Admin / both
- Login path: (e.g. `admin.docker` + Demo Device; challenge off?)

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
- **Do not declare UI complete** without Isabelle PASS (or explicit documented FAIL baseline).
- Entry may be Marc→Julie / Marc→Patrick / Marc→K; **exit walk always uses this gate**.
