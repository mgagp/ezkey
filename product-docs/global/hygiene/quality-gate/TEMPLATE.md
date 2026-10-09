# Quality gate — YYYY-MM-DD run-N

## Metadata

- **Date:** YYYY-MM-DD (America/Toronto)
- **Keyword:** `quality-gate`
- **Command:** `./scripts/quality-gate.sh` _(flags if any)_
- **Tip SHA:** `…`
- **Stack / mode:** HA + JavaMelody
- **Report:** `logs/quality-gate/<UTC>-<shortsha>/REPORT.md` (local, gitignored)
- **Summary:** `logs/quality-gate/<UTC>-<shortsha>/summary.json`
- **Operator:** Marc (+ agent)
- **Status:** Open / Closed
- **Overall verdict:** GO / GO with reservations / NO-GO

## Machine

- CPUs / RAM / disk:
- Docker / Compose:

## Phase summary

| Phase | Verdict | Duration | Counts | Notes |
| --- | --- | --- | --- | --- |
| preflight | | | | |
| clean-start | | | | |
| unit-tests | | | | |
| functional-tests | | | | |
| elective-tests | | | | |
| playwright | | | | |
| churn-init | | | | |
| churn | | | | |
| health | | | | |

## Failures (classified)

| Phase | Test | Classification | Evidence |
| --- | --- | --- | --- |
| | | product bug / flaky / environment / test bug | |

## Health highlights

- Top HTTP / SQL per app
- Error % / system errors
- Log error signatures
- Restarts / OOM / unhealthy
- Threshold verdict (and whether this run set the baseline)

## Reservations / follow-ups

-

## Out of scope

- Product code fixes inside the gate PR
- Silent HA → non-HA downgrade (must be explicit if resources force it)
