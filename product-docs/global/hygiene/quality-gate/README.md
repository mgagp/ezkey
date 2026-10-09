# Quality gate — on-demand full-suite gate

Keyword: **`quality-gate`**

Cloud-agent gate between merge batches. One command → GO / GO with reservations / NO-GO.
Artifacts under `logs/quality-gate/<UTC>-<shortsha>/` (gitignored).

**Not** a CI gate. A phase that can show GREEN wrongly is worse than no gate.
Cloud agent tooling; Git Bash best-effort, untested on macOS.

## Command

```bash
./scripts/quality-gate.sh
./scripts/quality-gate.sh --churn-minutes 5
```

Exit codes: **0** GO · **3** GO with reservations · other non-zero NO-GO.

## Sequence

1. **Preflight** — stop stacks; `docker info` MemTotal vs gate memory budget.
2. **Unit tests** — `./scripts/build.sh` with stack down (`MAVEN_OPTS=-Xmx1024m`).
3. **Clean-start** — HA + JavaMelody + gate memory overlay
   ([`docker/docker-compose.ha.quality-gate.yml`](../../../../docker/docker-compose.ha.quality-gate.yml)).
4. **Post-clean health** — snapshot (`QUALITY_GATE_PHASE=post-clean-health`).
5. **Functional** → **elective** → **docker start** restore (ShedLock) → **Playwright** →
   **churn** → **health**.

Fail-fast on RED for preflight, unit-tests, or clean-start.

After elective, `ShedLockDistributedTest` may `docker kill` an admin-api replica. The gate
runs `check_replicas` (tolerating only that kill), then `docker start` + wait healthy — **not**
`compose up -d` (preserves `docker,docker-dev,docker-test` profiles and logs). **HA invalid** if
restore fails, a replica dies elsewhere, or `RestartCount` rises (except the restored ShedLock
replica). Docker `oom` events / `ExitOnOutOfMemoryError` log lines → RED.

API replica check: 7 HA API JVMs. demo-device / demo-app-acme are mem-sampled only (not LB
replicas). Gate memory overlay stays as a runaway-heap guard on ~15Gi hosts.
`containers.oom` trusts Docker `OOMKilled` plus event/log evidence.

## #747 / #748 / #750

| Issue | Gate behaviour |
| --- | --- |
| [#747](https://github.com/mgagp/ezkey/issues/747) | Retry only if `ezkey_encryption_key_pkey`; else clean-start RED. Allowlist: phase=`clean-start` + `COMPOSE_RETRY_FIRED=1`. |
| [#748](https://github.com/mgagp/ezkey/issues/748) | Auth security false pass (sticky RestAssured → crypto). Fixed in product tests. |
| [#750](https://github.com/mgagp/ezkey/issues/750) | Bootstrap proof token / challenge / QR on AdminBootstrapService at clean-start/post-clean-health are **by design** (allowlisted). Recovery codes and AdminAuthService challenges stay RED. |

## Thresholds and allowlist

| Path | Role |
| --- | --- |
| [`config/quality-gate/thresholds.json`](../../../../config/quality-gate/thresholds.json) | Provisional bands. `oomKills` red at 1. |
| [`config/quality-gate/log-allowlist.txt`](../../../../config/quality-gate/log-allowlist.txt) | `service \| phase \| anchored regex \| issue \| expires= \| justification`. Regex tested against post-`signature()` text. |

Shared redaction + `detectSecrets` live in [`scripts/lib/quality-gate-secrets.mjs`](../../../../scripts/lib/quality-gate-secrets.mjs).
JavaMelody collector: `127.0.0.1:8088:8080` (product publish change).

## Run notes

Dated notes: `YYYY-MM-DD-run-N.md`. Current: [run-5](2026-10-09-run-5.md).
Earlier run-1…3 history lives on the PR comment for #746 (folded out of the tree).

## Related

- Root [`AGENTS.md`](../../../../AGENTS.md) § Quality gate
- Scripts: [`quality-gate.sh`](../../../../scripts/quality-gate.sh),
  [`stack-health-check.sh`](../../../../scripts/stack-health-check.sh)
