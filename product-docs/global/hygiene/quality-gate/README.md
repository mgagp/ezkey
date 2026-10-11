# Quality gate — on-demand full-suite gate

Keyword: **`quality-gate`**

Cloud-agent gate between merge batches. One command → GO / GO with reservations /
`INCONCLUSIVE: HA invalid` / NO-GO.
Artifacts under `logs/quality-gate/<UTC>-<shortsha>/` (gitignored).

**Not** a CI gate. A phase that can show GREEN wrongly is worse than no gate.
Cloud agent tooling; Git Bash best-effort, untested on macOS.

## Command

```bash
./scripts/quality-gate.sh
./scripts/quality-gate.sh --churn-minutes 5
./scripts/quality-gate-ha.test.sh   # fake-docker HA helpers
node scripts/lib/quality-gate-secrets.test.mjs
```

Exit codes: **0** GO · **3** reservations / HA inconclusive · other non-zero NO-GO.

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
| [#747](https://github.com/mgagp/ezkey/issues/747) | Retry only if `ezkey_encryption_key_pkey`; else clean-start RED. Allowlist: `requires=COMPOSE_RETRY_FIRED` + Spring `<TS> ERROR` signature. |
| [#748](https://github.com/mgagp/ezkey/issues/748) | Auth security false pass (sticky RestAssured → crypto). Fixed in product tests. |
| [#750](https://github.com/mgagp/ezkey/issues/750) | Bootstrap proof/challenge/QR **emitted during clean-start only** (`kind=secret-channel`). Still valid when scanned later; emission outside clean-start stays RED. |
| [#751](https://github.com/mgagp/ezkey/issues/751) | Recovery codes / login challenges no longer logged. |
| [#752](https://github.com/mgagp/ezkey/issues/752) / [#753](https://github.com/mgagp/ezkey/issues/753) | Real product log leaks (auth-api DTO/DEBUG MVC; demo-device INFO). Stay RED in the gate — no allowlist. Run-6 secret triage lives on PR #746 comments (not in-tree). Run 7 after those fix PRs merge. |

## Config

| Path | Role |
| --- | --- |
| [`config/quality-gate/thresholds.json`](../../../../config/quality-gate/thresholds.json) | Provisional bands (run 6 NO-GO — not locked). |
| [`config/quality-gate/log-allowlist.txt`](../../../../config/quality-gate/log-allowlist.txt) | Emission-time allowlist; unknown phases/`requires=`/`^.*` rejected. |

Shared redaction: [`scripts/lib/quality-gate-secrets.mjs`](../../../../scripts/lib/quality-gate-secrets.mjs).
HA helpers: [`scripts/lib/quality-gate-ha.sh`](../../../../scripts/lib/quality-gate-ha.sh).
Run notes for #746 live on the PR comment (not in-tree).

## Related

- Scripts: [`quality-gate.sh`](../../../../scripts/quality-gate.sh),
  [`stack-health-check.sh`](../../../../scripts/stack-health-check.sh)
