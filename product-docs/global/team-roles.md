# Verification functions

The project names **functions**, not personas. Each function is a check that an issue states as an
acceptance line and a PR backs with proof (a command, a skill run, a recorded review). It is played
by the session doing the work, by an independent Claude Code pass, or by **Marc**, who owns every
decision and every merge. How Claude Code runs them: [`CLAUDE.md`](../../CLAUDE.md) § *Verification
functions in Claude Code*.

| Function | What it checks | How it runs | Former name |
|----------|----------------|-------------|-------------|
| Engineering review | Structure, craft, uniform practice; for a mode change, the width (process → API → UI) | `/code-review` on the diff, independent of the author | Patrick |
| Security review | Trust boundaries, honesty of claims against `SECURITY_POSTURE`, fail-open vs fail-closed, secrets and logs | `/security-review` or a read-only sub-agent with a written mandate; accepted findings have a reproduction | Christophe |
| Interface gate | Every touched human surface: keep / adapt / hide, no dead end, nothing duplicated from another surface | A numbered checklist with "must NOT" rows, written before the PR is called ready | Julie |
| Scripted walk | The interface gate, executed with one proof per row, EN and FR | Playwright walk spec (`npm run test:walk`); a manual test summary when the flow needs the stack | Isabelle |
| Product decision | Intent, adopter posture, hygiene vs program, copy and naming | "Decision needed" line with a recommended option; **Marc** decides; one line in the owning doc | Alex |
| Hygiene pass | Tool-backed campaigns (doctors, Dependabot, assessments) | The named skill, findings walked one at a time with Marc | Fred |
| Performance pass | Measured before / after on a named scenario; no claim without numbers | `javamelody-curated` | Carl |
| Deploy | Scripted publish, DNS, edge, host patching | Repo scripts, outside the PR; **Marc go** on anything production | Edgar |
| Diagnosis (RCA) | Lab incidents: what we know, what we tried, what we recommend | Minimal reproduction; cause recorded in `docs/lightsail/` | Seb |
| Android device build | Builds and installs on the Windows workstation, short paths only | `ezkey_mobile/AGENTS.md` § *Android debug build* | Justin |
| Coordination | Routing and framing across work items | The project coordinator session | Mathieu, K |
| Communication, editorial | Public site, video, articles; conservative claims | Outside code work | Audrey, Clémentine |

Older records (issues, closed PRs, campaign notes, walk records, visions) use the former names: read
them as the function in the same row and leave them as written. New text uses the function only
(Marc, 2026-11-24: the personas were Cursor / Grokbot bots and no longer fit the project).

See also [`AGENTS.md`](../../AGENTS.md) § *Rules*.
