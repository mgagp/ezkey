# Claude Code entrypoint

@AGENTS.md

Scoped rules load by themselves: [`.claude/rules/java.md`](.claude/rules/java.md) when you touch a
Java file. Project skills are in [`.claude/skills/`](.claude/skills/).

## Taking over a task: goal frame

Before a takeover or a multi-step slice (a Cursor handoff, an open PR, an issue), read the issue and
the PR as they stand, challenge them against the project values, then agree with Marc on four lines
before coding: **objective**, **quality criteria**, **what done means** (always ending on Marc's
explicit merge go) and **constraints** (scope, contract, worktree). Record the frame in a PR
comment so the next agent finds it. First used on #1455.

## Verification functions in Claude Code

[`product-docs/global/team-roles.md`](product-docs/global/team-roles.md) lists the functions and
their Grokbot aliases. Lead with the function; follow its naming rule. In Claude Code they run like
this:

- **Engineering review:** the `/code-review` skill on the PR diff. When the author is this session,
  that run is the independent pass; the author does not self-approve.
- **Security review:** `/security-review` or a read-only sub-agent with a written mandate
  (disclosure, honesty of claims against the backend, validation framed as a control or not,
  injection, system-integration isolation). Its findings are posted in the PR by the main session.
- **Interface gate and scripted walk:** the gate is a numbered checklist written before the PR is
  called ready; the walk is a Playwright spec (EN and FR) that executes it. Sizing and records:
  `product-docs/global/ui-walk-done-gate.md`.
- **Product decision:** a "Decision needed" line with a recommended option; Marc decides.
- Sub-agents are the same model family as the author. Where the `critical-review` canon requires
  a different family (P0/P1 visions, G1 briefs), keep that review in Cursor.

In an issue or PR, a short **Verifications** block lists the functions that apply, each with its
check (for example: `/code-review`; a security mandate in two to four lines; `npm run test:walk --
--grep <n>`; the decision Marc must take; what happens outside the PR, such as a deploy).

## Claude Code cloud sessions

Only when `CLAUDE_CODE_REMOTE=true` (claude.ai/code container; not the Mac, Rootbeer, or Cursor Cloud):
read [`docs/CLAUDE_CODE_CLOUD.md`](docs/CLAUDE_CODE_CLOUD.md) before building or testing. The
SessionStart hook (`.claude/hooks/session-start.sh`) starts Docker, relays the proxy for image builds
and exports JDK 25. After a container restart, rerun it by hand: daemons are gone and the proxy port
changes.

## macOS workstation notes

- The shell is zsh/bash on macOS: the Windows PowerShell rule (`.cursor/rules/windows-agent-shell.mdc`)
  does not apply. Plain `git commit` with a HEREDOC and `gh pr create --body-file` are fine here.
- iOS work (issue #152, ADR-MOB-0005) happens in the worktree `~/w/ezkey-ios` on branch
  `feature/152-mobile-ios`. Start with `ezkey_mobile/AGENTS.md`.
- No physical iPhone and no Apple Developer account yet: iOS work is simulator-only until then
  (Secure Enclave is not available on the simulator).

## Windows workstation notes (Claude Code desktop)

- The Bash tool is Git Bash, so a HEREDOC `git commit` and `gh pr create --body-file` work. The
  `git-commit.sh` / `git-pr.sh` file-based flow (`.cursor/rules/windows-agent-shell.mdc`) exists
  for Cursor's PowerShell shell; it is not required here.
- Parallel work goes in its own worktree from `origin/<branch>`; the main checkout
  `C:\github\ezkey` often carries Marc's in-progress branch. In a fresh worktree, follow the
  module's `AGENTS.md` before building (Admin UI: `npm ci`, then `npm run generate:api`).
- Python here is the Windows build: text-mode writes turn LF into CRLF. Open files with
  `newline=''`, or edit with the Edit tool.
