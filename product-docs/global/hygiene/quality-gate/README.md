# Quality gate — on-demand full-suite gate

Keyword: **`quality-gate`**

Cloud-agent gate between merge batches. Exit **0** GO · **1** NO-GO · **2** usage ·
**3** GO with reservations · **4** INCONCLUSIVE (HA invalid or skipped phases).
Artifacts under `logs/quality-gate/<UTC>-<shortsha>/` (gitignored).

**Not** a CI gate. A phase that can show GREEN wrongly is worse than no gate.
Cloud agent tooling; portable Bash (Linux, Git Bash, macOS).

## Command

```bash
./scripts/quality-gate.sh
./scripts/quality-gate.sh --churn-minutes 5
./scripts/quality-gate-ha.test.sh   # fake-docker HA helpers
node scripts/lib/quality-gate-secrets.test.mjs
```

### Exit-code table

| Code | Meaning |
| --- | --- |
| **0** | GO — all phases ran; no RED; no AMBER; HA valid |
| **1** | NO-GO — any RED check/phase |
| **2** | Bad args / usage |
| **3** | GO with reservations — AMBER only (HA valid; no skipped phases) |
| **4** | INCONCLUSIVE — HA invalid and/or any `--skip` phase (never GO) |

A skipped phase (`--skip` / `--no-clean-start`) makes the overall verdict at least
`INCONCLUSIVE: skipped phase(s)` and exit **4**, even when remaining phases are GREEN.
Inherited `QUALITY_GATE_LOG_SINCE` / `UNTIL` and `DOCKER_LOGS_SINCE` / `UNTIL` are **unset**
at gate start so a full run cannot silently inherit a narrowed docker-log window.

ShedLock kill proof uses `State.Status=exited` first, else a docker `die` event **since
`ELECTIVE_STARTED_AT`** that matches the elective `Crashing container:` name **and** exit
code **137**. Searching from `GATE_STARTED_AT` is wrong: preflight `stop_stacks` emits
`die` for admin-api replicas.

## Sequence

1. **Preflight** — stop stacks; `docker info` MemTotal vs gate memory budget.
2. **Unit tests** — `./scripts/build.sh` with stack down (`MAVEN_OPTS=-Xmx1024m`).
3. **Clean-start** — HA + JavaMelody + gate memory overlay
   ([`docker/docker-compose.ha.quality-gate.yml`](../../../../docker/docker-compose.ha.quality-gate.yml)).
4. **Post-clean health** → **functional** → **elective** → **docker-start restore** →
   **Playwright** → **churn** → **health** → **OOM check** (whole-run window).

Fail-fast on RED for preflight, unit-tests, or clean-start.

After elective, `ShedLockDistributedTest` may `docker kill` an admin-api replica. The gate
tolerates that kill only during restore (`tolerating=`), then `docker start` + wait healthy —
**not** `compose up -d`. A later restart of the restored replica is **HA invalid** (C1-bis).
Docker `oom` events / `ExitOnOutOfMemoryError` log lines → RED; failed `docker events` →
UNKNOWN/AMBER (never silent "no OOM").

## #747 / #748 / #750

| Issue | Gate behaviour |
| --- | --- |
| [#747](https://github.com/mgagp/ezkey/issues/747) | Retry only if `ezkey_encryption_key_pkey` appears in admin-api replica logs **since clean-start phase start − 5 s** (`QUALITY_GATE_SINCE_BACKOFF_S`, Docker Desktop macOS VM clock skew) via `docker logs --since` (no `tail` cap); else clean-start RED. Match uses `grep -F … >/dev/null` **without** `-q` so `set -o pipefail` does not treat writer SIGPIPE (141) as a miss when the signature is early (Patrick B1). HA test `5e` uses signature then `seq 1 2000000` (a single fat line is one write and does not prove SIGPIPE). `quality_gate_since_backoff` never returns empty on python3 failure (raw timestamp + stderr; Patrick N1) and validates backoff secs as `^[0-9]+$` (N2). Allowlist: `requires=COMPOSE_RETRY_FIRED` + Spring `<TS> ERROR` signature. Phase timing: portable `now_ms` (GNU `%3N` when numeric; else `python3` / `node` / whole seconds with stderr note) — BSD `date` on macOS must not poison arithmetic (Carl 2026-10-19). CI: dedicated `quality-gate` job runs `quality-gate-ha.test.sh`. |
| [#748](https://github.com/mgagp/ezkey/issues/748) | Auth security false pass (sticky RestAssured → crypto). Fixed in product tests. |
| [#750](https://github.com/mgagp/ezkey/issues/750) | Bootstrap proof/challenge/QR **emitted during clean-start only** (`kind=secret-channel`). Still valid when scanned later; emission outside clean-start stays RED. |
| [#751](https://github.com/mgagp/ezkey/issues/751) | Recovery codes / login challenges no longer logged. |
| [#752](https://github.com/mgagp/ezkey/issues/752) / [#753](https://github.com/mgagp/ezkey/issues/753) | Fixed on main (`93445ba2` / #754). Run 7 (`65db9d72`) shows **zero** #752/#753 signatures. Residual secrets RED is scanner/product FP (tenant names as opaque-token; demo `applyApiKey=` key name). Run notes on PR #746 comments. |

## Config

| Path | Role |
| --- | --- |
| [`config/quality-gate/thresholds.json`](../../../../config/quality-gate/thresholds.json) | Baseline locked (`baselineLocked: true`) from run 8 tip `79679907`. **Declarative only** — nothing reads the flag yet (no drift comparison). |
| [`config/quality-gate/log-allowlist.txt`](../../../../config/quality-gate/log-allowlist.txt) | Emission-time allowlist; unknown phases/`requires=`/`^.*` rejected. |

Shared redaction: [`scripts/lib/quality-gate-secrets.mjs`](../../../../scripts/lib/quality-gate-secrets.mjs).
HA helpers: [`scripts/lib/quality-gate-ha.sh`](../../../../scripts/lib/quality-gate-ha.sh).
Run notes for #746 live on the PR comment (not in-tree).

## Related

- Scripts: [`quality-gate.sh`](../../../../scripts/quality-gate.sh),
  [`stack-health-check.sh`](../../../../scripts/stack-health-check.sh)
