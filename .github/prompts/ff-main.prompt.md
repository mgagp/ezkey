# Fast-forward onto origin/main (deterministic)

Keep the current working branch synchronized with `origin/main` using fetch + fast-forward only.
No merge commit. No rebase. No push.

## One-command execution path

Always run the repository helper script so shell quoting and command chaining do not drift.

- Inside Git Bash:
  `./scripts/git-ff-only.sh`
- Windows agent shell (PowerShell/CMD):
  `& "C:\Program Files\Git\bin\bash.exe" -lc './scripts/git-ff-only.sh'`

If the user explicitly asks another upstream ref, pass it as argument:

- Git Bash: `./scripts/git-ff-only.sh origin/release/x.y`
- PowerShell/CMD: `& "C:\Program Files\Git\bin\bash.exe" -lc './scripts/git-ff-only.sh origin/release/x.y'`

## Safety constraints

- Never use rebase.
- Never create merge commits.
- Never push.
- Never run destructive commands (`reset --hard`, `checkout --`, force-clean, etc.).
- If the script fails, stop immediately and report the exact error.

## Output contract

Return:

- current branch name,
- upstream ref used,
- whether fast-forward was applied,
- old HEAD -> new HEAD,
- short status summary after operation.
