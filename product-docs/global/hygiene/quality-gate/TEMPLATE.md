# Quality gate — YYYY-MM-DD run-N

## Metadata

- **Date:** YYYY-MM-DD (America/Toronto)
- **Keyword:** `quality-gate`
- **Command:** `./scripts/quality-gate.sh` _(flags if any — prefer none for a trusted run)_
- **Tip SHA:** `…`
- **Stack / mode:** HA + JavaMelody
- **Report:** `logs/quality-gate/<UTC>-<shortsha>/REPORT.md` (local, gitignored)
- **Summary:** `logs/quality-gate/<UTC>-<shortsha>/summary.json`
- **Operator:** Marc (+ agent)
- **Status:** Open / Closed
- **Overall verdict:** GO / GO with reservations / NO-GO

## Machine

- CPUs / RAM / disk:
- Available RAM vs `HA_JM_MIN_AVAILABLE_MIB` (8192):
- Docker / Compose:

## Phase summary

| Phase | Verdict | Duration | Counts | Notes |
| --- | --- | --- | --- | --- |
| preflight | | | | stack stopped; RAM check |
| unit-tests | | | | before stack (RAM headroom) |
| build-images | | | | tip images |
| clean-start | | | | AMBER if #747 compose retry fired |
| functional-tests | | | | |
| elective-tests | | | | |
| playwright | | | | |
| churn-init | | | | |
| churn | | | | |
| health | | | | |

## Failures (classified)

| Phase | Test / finding | Classification | Evidence |
| --- | --- | --- | --- |
| | | product bug / flaky / environment / test bug | |

## Health highlights

- Top HTTP / SQL per app
- Error % / system errors (Error404 excluded from httpErrorPct)
- Log error signatures
- Restarts / OOM / unhealthy
- Threshold verdict (and whether this run set the baseline)

## Reservations / follow-ups

- #747 compose retry (remove when fixed)

## Out of scope

- Product code fixes inside the gate PR
- Silent HA → non-HA downgrade (must be explicit if resources force it)
