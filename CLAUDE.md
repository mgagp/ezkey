# Claude Code entrypoint

@AGENTS.md

## Always-applied rules (shared with Cursor)

These Cursor rules are `alwaysApply: true` and platform-neutral, so Claude Code loads them too:

@.cursor/rules/cold-start-context.mdc
@.cursor/rules/product-docs-workflow-bootstrap.mdc
@.cursor/rules/project-values-analysis.mdc
@.cursor/rules/github-issue-labels.mdc
@.cursor/rules/manual-exploratory-test-summaries.mdc
@.cursor/rules/voice-dictation.mdc

Read on demand, when the work touches that surface:

- Java / Maven: `.cursor/rules/maven-build.mdc`, `java-spring-cursor-rules.mdc`,
  `javadoc-type-param.mdc`, `mapstruct.mdc`, `dto-match-domain.mdc`, `java-unused-lambda-unnamed.mdc`
- API contract: `.cursor/rules/openapi-specs.mdc`
- Admin UI: `.cursor/rules/ui-browser-test-autonomy.mdc`, `agent-ui-closeout-validation.mdc`
- Schema: `.cursor/rules/flyway-partitioned-tables.mdc`

## Taking over a task: goal frame

Before a takeover or a multi-step slice (a Cursor handoff, an open PR, an issue), read the issue and
the PR as they stand, challenge them against the project values, then agree with Marc on four lines
before coding: **objective**, **quality criteria**, **what done means** (always ending on Marc's
explicit merge go) and **constraints** (scope, contract, worktree). Record the frame in a PR
comment so the next agent finds it. First used on #1455.

## Team roles in Claude Code

[`product-docs/global/team-roles.md`](product-docs/global/team-roles.md) roles were Cursor bots;
in Claude Code they are played like this (adopted on #1455, 2026-11-23):

- **Patrick** (engineering review): the `/code-review` skill on the PR diff. When the author is
  this session, that run is the independent pass; the author does not self-approve.
- **Christophe** (security review): a read-only sub-agent with a written mandate (disclosure,
  honesty of claims against the backend, validation framed as a control or not, injection,
  system-integration isolation). Its findings are posted in the PR by the main session.
- **Julie** (Walk Gate): a scripted Playwright walk (EN and FR), recorded under
  `product-docs/global/backlog/walk-gates/` and indexed in `ui-walk-done-gate.md`.
- Sub-agents are the same model family as the author. Where the `critical-review` canon requires
  a different family (P0/P1 visions, G1 briefs), keep that review in Cursor.

## Claude Code cloud sessions

Only when `CLAUDE_CODE_REMOTE=true` (claude.ai/code container; not the Mac, Rootbeer, or Cursor Cloud):
read [`docs/CLAUDE_CODE_CLOUD.md`](docs/CLAUDE_CODE_CLOUD.md) before building or testing. The
SessionStart hook (`.claude/hooks/session-start.sh`) starts Docker, relays the proxy for image builds
and exports JDK 25. After a container restart, rerun it by hand: daemons are gone and the proxy port
changes.

## macOS workstation notes

- The shell is zsh/bash on macOS: ignore the Windows-only sections of `AGENTS.md`
  (Git Bash launcher, PowerShell quoting, `git-commit.sh` / `git-pr.sh` file-based flow) and the
  Windows rules (`*-windows*.mdc`, `shell-preferences.mdc` launcher parts, `cloud-gh-token.mdc`).
  Plain `git commit` with a HEREDOC and `gh pr create --body-file` are fine here.
- iOS work (issue #152, ADR-MOB-0005) happens in the worktree `~/w/ezkey-ios` on branch
  `feature/152-mobile-ios`. Start with `ezkey_mobile/AGENTS.md`.
- No physical iPhone and no Apple Developer account yet: iOS work is simulator-only until then
  (Secure Enclave is not available on the simulator).

## Windows workstation notes (Claude Code desktop)

- The Bash tool is Git Bash, so a HEREDOC `git commit` and `gh pr create --body-file` work. The
  `git-commit.sh` / `git-pr.sh` file-based flow in `AGENTS.md` exists for Cursor's PowerShell shell;
  it is not required here.
- Parallel work goes in its own worktree from `origin/<branch>`; the main checkout
  `C:\github\ezkey` often carries Marc's in-progress branch. In a fresh worktree, follow the
  module's `AGENTS.md` before building (Admin UI: `npm ci`, then `npm run generate:api`).
- Python here is the Windows build: text-mode writes turn LF into CRLF. Open files with
  `newline=''`, or edit with the Edit tool.
