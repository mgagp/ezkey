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

**One command from scratch** (no manual clean-start, no `--no-clean-start`):

```bash
./scripts/quality-gate.sh
```

Phase order (orchestrator):

1. Preflight (tip SHA, CPU/RAM/disk, Docker/Compose; **stops any running stack** for unit-test headroom; AMBER if available RAM < `HA_JM_MIN_AVAILABLE_MIB`, default **8192 MiB**)
2. Unit tests: `./scripts/build.sh` — **with no stack running**
3. Build images from tip (HA compose files + JavaMelody overlay)
4. Clean start HA + JavaMelody: `ezkey-tests/clean-start.sh --ha --with-java-melody`
5. Functional: `mvn test -pl ezkey-tests -P all-tests`
6. Elective: `ezkey-tests/scripts/run-elective-tests.sh` (after functional; not concurrent)
7. Playwright: `ezkey-admin-ui/scripts/run-ui-tests.sh` against the running stack
8. Churn init: `ezkey-tests/scripts/run-operational-churn.sh --init`
9. Churn: two concurrent shells × N minutes (default 5), distinct seeds and log files
10. Health: `./scripts/stack-health-check.sh` (JavaMelody + container logs)

### Why unit tests run before the stack

On ~16Gi cloud VMs, running `./scripts/build.sh` while the 6-JVM HA stack is up can OOM-kill an
admin-api replica (exit 137). The gate therefore runs unit tests **first** (stack stopped), then
builds images and clean-starts HA. This **intentionally differs** from a naive “stack first, then
Maven” list — memory headroom is the reason.

Functional/elective still run host Maven against the live HA stack (required). On 15Gi that can
still OOM a replica (see run-2); prefer ≥32Gi or a constrained Maven heap for unattended gates.

Orchestrator: [`scripts/quality-gate.sh`](../../../../scripts/quality-gate.sh)

```bash
./scripts/quality-gate.sh
./scripts/quality-gate.sh --churn-minutes 5
./scripts/quality-gate.sh --no-clean-start --skip unit-tests   # rerun slices only
```

## HA keyset race (#747)

Concurrent admin-api startup can race on `ezkey_encryption_key_pkey` during keyset sync — tracked
as **[#747](https://github.com/mgagp/ezkey/issues/747)** (`priority:p1`).

- The gate keeps a **temporary** Compose retry workaround so clean-start can finish.
- **Every time that retry fires, the clean-start phase is AMBER** (never GREEN), with an explicit
  `#747` note in the phase record and report.
- Allowlist entries for the keyset signatures **cite #747** and must be **removed when #747 is
  fixed**.

## How to read the verdict

| Overall | Meaning |
| --- | --- |
| **GO** | All phases GREEN; health within thresholds. Safe to start the next batch. |
| **GO with reservations** | No RED phases, but AMBER health/threshold signals (includes #747 compose retry, low-RAM preflight). Review before proceeding. |
| **NO-GO** | One or more RED phases (or stack down). Classify failures before the next batch. |

Per-phase verdicts: GREEN / AMBER / RED / SKIP. Orchestrator exits non-zero on any RED.

Do **not** fix product bugs inside the gate PR. Classify each failure as product bug / flaky /
environment (cloud VM) / test bug with evidence.

## Thresholds and allowlist

| Path | Role |
| --- | --- |
| [`config/quality-gate/thresholds.json`](../../../../config/quality-gate/thresholds.json) | GREEN / AMBER / RED bands for JavaMelody and container signals |
| [`config/quality-gate/log-allowlist.txt`](../../../../config/quality-gate/log-allowlist.txt) | Known log noise (`pattern \| justification`) |

Lock the baseline from a **healthy complete HA run** (no OOM, all replicas up): set thresholds from
observed values plus margin, then `baselineLocked: true` and refresh `baselineNote` with the tip SHA.

JavaMelody synthetic **Error404** (and other `ErrorNNN`) buckets are excluded from `httpErrorPct`
scoring via `javamelody.httpErrorPctExclusions` in the thresholds file — they always report 100%
error rate and must not drive AMBER/RED.

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
- Product bug: [#747](https://github.com/mgagp/ezkey/issues/747) HA admin keyset race
- Scripts: [`scripts/quality-gate.sh`](../../../../scripts/quality-gate.sh),
  [`scripts/stack-health-check.sh`](../../../../scripts/stack-health-check.sh)
