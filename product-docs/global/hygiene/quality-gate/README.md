# Quality gate — on-demand full-suite gate

Keyword: **`quality-gate`**

Repeatable cloud-agent gate run between sizable batches of merged PRs. One visible verdict
covering the whole test suite plus stack health, backed by a report under
`logs/quality-gate/<UTC>-<shortsha>/` (gitignored).

**Not** a CI gate. **Not** a workstation habit — run in a cloud agent only.

## When to run

- After a sizable batch of merges lands on `main`, before starting the next batch.
- When you need a single GO / GO with reservations / NO-GO on tip health.

## Sequence

1. Preflight (tip SHA, CPU/RAM/disk, Docker/Compose)
2. Clean start HA + JavaMelody: `ezkey-tests/clean-start.sh --ha --with-java-melody`
3. Unit tests: `./scripts/build.sh`
4. Functional: `mvn test -pl ezkey-tests -P all-tests`
5. Elective: `ezkey-tests/scripts/run-elective-tests.sh` (after functional; not concurrent)
6. Playwright: `ezkey-admin-ui/scripts/run-ui-tests.sh` against the running stack
7. Churn init: `ezkey-tests/scripts/run-operational-churn.sh --init`
8. Churn: two concurrent shells × N minutes (default 5), distinct seeds and log files
9. Health: `./scripts/stack-health-check.sh` (JavaMelody + container logs)

Orchestrator: [`scripts/quality-gate.sh`](../../../../scripts/quality-gate.sh)

```bash
./scripts/quality-gate.sh
./scripts/quality-gate.sh --churn-minutes 5
./scripts/quality-gate.sh --no-clean-start --skip unit-tests   # rerun slices
```

## How to read the verdict

| Overall | Meaning |
| --- | --- |
| **GO** | All phases GREEN; health within thresholds. Safe to start the next batch. |
| **GO with reservations** | No RED phases, but AMBER health/threshold signals. Review before proceeding. |
| **NO-GO** | One or more RED phases (or stack down). Classify failures before the next batch. |

Per-phase verdicts: GREEN / AMBER / RED / SKIP. Orchestrator exits non-zero on any RED.

Do **not** fix product bugs inside the gate PR. Classify each failure as product bug / flaky /
environment (cloud VM) / test bug with evidence.

## Thresholds and allowlist

| Path | Role |
| --- | --- |
| [`config/quality-gate/thresholds.json`](../../../../config/quality-gate/thresholds.json) | GREEN / AMBER / RED bands for JavaMelody and container signals |
| [`config/quality-gate/log-allowlist.txt`](../../../../config/quality-gate/log-allowlist.txt) | Known log noise (`pattern \| justification`) |

The first run is the **baseline**: set thresholds from observed values plus a reasonable margin,
then set `baselineLocked: true` and refresh `baselineNote` with the tip SHA.

Health reuses [`javamelody-curated`](../javamelody/) for collector extraction and
[`config/javamelody/`](../../../../config/javamelody/) noise / expected-hot lists.

## Contents

| Path | Role |
| --- | --- |
| [`TEMPLATE.md`](TEMPLATE.md) | Copy for each dated run note |
| `YYYY-MM-DD-run-N.md` | Dated instance (summary table + findings) |

## Related

- Hygiene index: [`../README.md`](../README.md)
- Keyword contract: root [`AGENTS.md`](../../../../AGENTS.md) § Quality gate
- Scripts: [`scripts/quality-gate.sh`](../../../../scripts/quality-gate.sh),
  [`scripts/stack-health-check.sh`](../../../../scripts/stack-health-check.sh)
