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

## macOS workstation notes

- The shell is zsh/bash on macOS: ignore the Windows-only sections of `AGENTS.md`
  (Git Bash launcher, PowerShell quoting, `git-commit.sh` / `git-pr.sh` file-based flow) and the
  Windows rules (`*-windows*.mdc`, `shell-preferences.mdc` launcher parts, `cloud-gh-token.mdc`).
  Plain `git commit` with a HEREDOC and `gh pr create --body-file` are fine here.
- iOS work (issue #152, ADR-MOB-0005) happens in the worktree `~/w/ezkey-ios` on branch
  `feature/152-mobile-ios`. Start with `ezkey_mobile/AGENTS.md`.
- No physical iPhone and no Apple Developer account yet: iOS work is simulator-only until then
  (Secure Enclave is not available on the simulator).
