# Quality gate — on-demand full-suite gate

Keyword: **`quality-gate`**

Cloud-agent (or Git Bash) gate between merge batches. One command → GO / GO with
reservations / NO-GO. Artifacts under `logs/quality-gate/<UTC>-<shortsha>/` (gitignored).

**Not** a CI gate. A phase that can show GREEN wrongly is worse than no gate.

## Command

```bash
./scripts/quality-gate.sh
./scripts/quality-gate.sh --churn-minutes 5
```

Exit codes: **0** GO · **3** GO with reservations · other non-zero NO-GO.

## Sequence

1. **Preflight** — stop stacks; compare `docker info` MemTotal to gate memory budget (JVM
   `mem_limit` sum + postgres reserve + margin). Node-only (no `free` / Python).
2. **Unit tests** — `./scripts/build.sh` with stack down (`MAVEN_OPTS=-Xmx1024m`).
3. **Clean-start** — `ezkey-tests/clean-start.sh --ha --with-java-melody` plus gate-only
   memory overlay [`docker/docker-compose.ha.quality-gate.yml`](../../../../docker/docker-compose.ha.quality-gate.yml)
   (`EZKEY_COMPOSE_EXTRA_FILES`). Images come from `start-ha.sh` (no duplicate build phase).
4. **Post-clean health** — snapshot right after clean-start.
5. **Functional** → **elective** → **Playwright** → **churn init** → **2× churn**.
6. **Health** — JavaMelody dump into the run dir + container log scan / redaction / scoring.

Fail-fast on RED for preflight, unit-tests, or clean-start. After every stack-bearing phase,
`docker inspect` every API JVM replica; dead/restarted → phase AMBER + run marked HA invalid.
Ctrl-C kills background churn shells (`trap`).

## #747 (keyset race) and #748 (false GREEN test)

| Issue | Gate behaviour |
| --- | --- |
| [#747](https://github.com/mgagp/ezkey/issues/747) | Retry **only** if `ezkey_encryption_key_pkey` appears; else clean-start RED. Retry → AMBER. Allowlist entry active only when `COMPOSE_RETRY_FIRED=1`, clean-start context, admin replicas. After retry, verify **all** API replicas. |
| [#748](https://github.com/mgagp/ezkey/issues/748) | Auth API security test false pass (sticky RestAssured → crypto). Fixed in product tests; not an allowlist. |

## Thresholds and allowlist

| Path | Role |
| --- | --- |
| [`config/quality-gate/thresholds.json`](../../../../config/quality-gate/thresholds.json) | Provisional absolute bands (no baseline comparison). Recalibrate from a clean HA run. `httpErrorPct.minHits` ≈ 20; `oomKills` red at 1. |
| [`config/quality-gate/log-allowlist.txt`](../../../../config/quality-gate/log-allowlist.txt) | `service \| anchored regex \| issue \| expires=YYYY-MM-DD \| justification`. Missing issue or expired → RED. Invalid regex → blocking error. |

Missing/stale JavaMelody data scores AMBER/RED (“no data”), never GREEN. Raw-log secret patterns
(email, JWT, password/token/challenge/accessCode JSON or `key=`, etc.) are a dedicated RED check
before redaction; reports use sanitized signatures only.

JavaMelody collector publish: `127.0.0.1:8088:8080` (not `0.0.0.0`).

## Run note (copy per run)

```markdown
# Quality gate — YYYY-MM-DD run-N
- Tip SHA / overall / duration
- Phase table (from REPORT.md)
- Findings + classification (product / flaky / environment / test)
- Replica liveness + raw-log secret check
- Links to REPORT.md / summary.json / HEALTH.md (local)
```

Dated notes: `YYYY-MM-DD-run-N.md`. History: [run-1](2026-10-09-run-1.md) (folded),
[run-2](2026-10-09-run-2.md).

## Related

- Root [`AGENTS.md`](../../../../AGENTS.md) § Quality gate
- Scripts: [`quality-gate.sh`](../../../../scripts/quality-gate.sh),
  [`stack-health-check.sh`](../../../../scripts/stack-health-check.sh)
- Hygiene index: [`../README.md`](../README.md)
