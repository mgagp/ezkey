# Claude Code cloud sessions — operating notes

How to build and test Ezkey in a **Claude Code cloud session** (claude.ai/code, the Claude app). Written
after the first cloud-agent run on Ezkey (2026-11-22: #1433 fixed and tested end to end, PRs #1448 and
#1449).

**Scope.** Claude Code cloud containers only. Nothing here applies to local Claude Code on the Mac or
Rootbeer, to Cursor, or to Cursor Cloud. Cursor Cloud has its own setup (`.cursor/environment.json`,
`.cursor/setup/`), and these notes do not touch it. The session hook below exits at once unless
`CLAUDE_CODE_REMOTE=true`.

## 1. Before the session: the `ezkey` cloud environment

Start cloud sessions on the personal cloud environment **`ezkey`** (environment menu in the session title
bar). Compared with *Default* it:

- allows `dl-cdn.alpinelinux.org` (Alpine packages for the service images; blocked on *Default*);
- runs a setup script that installs **OpenJDK 25**, **Node 24** (`/opt/node24`) and **socat**.

Maven Central, Docker Hub, the npm registry and nodejs.org are reachable through the package-manager
allowance. The JavaMelody overlay also fetches a GitHub release asset; that worked on 2026-11-22.

## 2. At session start: the hook (automatic)

`.claude/hooks/session-start.sh` (registered in `.claude/settings.json`) runs at every session start,
resume, clear and compact. In a cloud container it:

1. installs JDK 25 / socat if the setup script did not (fallback);
2. starts `dockerd` (no init system in the container);
3. relays the session HTTPS proxy onto the Docker bridge for image builds;
4. exports `JAVA_HOME` (JDK 25; the image default is 21 and **Maven follows `JAVA_HOME`**), `PATH`
   (Node 24 first) and `EZKEY_BUILD_HTTPS_PROXY`.

It takes about 0.2 s when everything already runs and about 2 s from cold.

**After a container restart mid-session** (it happened three times on 2026-11-22, for example after a
usage-limit pause), `dockerd` and the relay are gone, the **proxy port changes**, and no SessionStart
fires. Rerun it by hand and export what it prints:

```bash
bash .claude/hooks/session-start.sh
```

Running containers come back by themselves (restart policies, volumes kept). Check with `uptime` when in
doubt.

## 3. Running the test hierarchy in the cloud

Prefix Maven commands with `export JAVA_HOME=/usr/lib/jvm/java-25-openjdk-amd64` if a shell did not get
the hook's exports.

| Tier | Command | Cloud notes |
|---|---|---|
| Build + unit | `./scripts/build.sh` | Needs Docker up: the 12 `@Testcontainers(disabledWithoutDocker = true)` classes **skip silently** otherwise (#1446). About 3 min warm. |
| Stack | `cd ezkey-tests && ./clean-start.sh` | Needs `EZKEY_BUILD_HTTPS_PROXY` (from the hook) for image builds: docker/README.md § *Building behind an HTTPS proxy* (#1449). |
| Functional | `mvn test -pl ezkey-tests` | 172 tests, about 1 min 15 s. |
| Elective | `./ezkey-tests/scripts/run-elective-tests.sh` | Single stack is fine; HA-only checks need `--ha`. |
| Operational churn | `./ezkey-tests/scripts/run-operational-churn.sh --init`, then `--profile light --minutes 5` | **Does not call the Integration API** (0 API-key authentications in 5 min): it cannot cover an Integration API change. |
| Playwright | demo-login: `npm ci && npm run e2e` | Projects pin a newer Playwright than the pre-installed Chromium. Point `launchOptions.executablePath` at `/opt/pw-browsers/chromium`. Setup code that calls `chromium.launch()` itself also needs the expected `chromium_headless_shell-<rev>` path symlinked to the installed one (outside the repo). Pass `--trace off`. |
| Quality gate | `./scripts/quality-gate.sh` | Documented as a cloud-agent gate; its memory budget (11 464 MiB) fits the container (about 16 GiB). |
| Mobile real device | — | Not in the cloud: Mac + device. |

## 4. Traps that look like product failures

- **Auth API 503 right after a restart.** After a long stop, the audit-chain heartbeat is stale and the
  Auth API fails closed (`DEGRADED_SERVICE`) until admin-api writes catch-up checkpoints (about one
  scheduler tick). The Auth API re-evaluates only on the next request. This is expected behaviour, not
  your change: wait a few minutes or clean-start.
- **Red for the wrong reason.** Read the assertion message of a "red" test: on 2026-11-22 the first red
  run failed on admin-token bootstrap (the 503 above), not on the bug.
- **Green that does nothing.** When a fix moves a write, check the database too
  (`docker exec ezkey-postgres psql -U postgres -d ezkey_db …`).
- **`.gitignore` ignores `build/` anywhere.** A new file under any `build/` directory is silently left out
  of a commit. Check that `git status` shows it as added (`A`), or run `git check-ignore -v <path>`.
- **Never pipe a probe whose exit status matters** (`curl … | head -1` hid a TLS failure).
- **After a failed Maven download inside an image build**, run `docker builder prune -af` before retrying.

## 5. GitHub from a cloud session

- `gh` is REST-only here (`gh api …`); `gh pr` and `gh issue` are refused.
- Pushing and creating issues need the Claude GitHub App installed with write access on `mgagp/ezkey`;
  before that, both fail with 403.
- `.claude/settings.json` denies force pushes: undo a pushed commit with `git revert`.
- Push only to the session's branch unless the maintainer names another one.

## 6. If an image or registry is unreachable

Options, in order: allow the host in the cloud environment; build through the proxy (above); as a
fallback, export images from a working stack (`docker save`) and load them in the session
(`docker load`). That is how the Lightsail deploys avoid depending on a registry. Evaluate before relying
on it: images must match the commit under test.
