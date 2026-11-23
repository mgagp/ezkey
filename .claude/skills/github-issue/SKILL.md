---
name: github-issue
description: >-
  Creates or promotes a GitHub issue on the Ezkey repository with the mandatory five label groups
  (lane, type, component, priority, status) and verifies them after creation. Use whenever you are
  about to open a GitHub issue, ticket or billet, or promote an I-* / TB-* to an issue.
---

# GitHub issue with mandatory labels

An issue reported **without labels is incomplete**: fix labels before continuing.

1. **Review existing labels first** (`gh label list`). Apply every label that fits. Create a new
   label only when none fits, and justify it in the issue body.
2. **Apply the five label groups** (at least one per group), in the create call when the tool
   allows it (`gh issue create --label …`, API `labels` array):
   - `lane:*`: `lane:a` (default ideation-to-delivery), `lane:b` (plan incubation), `lane:c`
     (legacy retrofit / hygiene), `lane:d` (post-delivery change), `lane:e` (methodology feedback)
   - `type:*`: `type:feat`, `type:fix`, `type:refactor`, `type:test`, `type:docs`, `type:chore`,
     `type:security`
   - `component:*` (one or more), e.g. `component:admin-api`, `component:admin-ui`,
     `component:mobile`, `component:core`, `component:methodology`
   - `priority:*`: `priority:p0` (blocks release) … `priority:p3` (backlog, no urgency)
   - `status:*`: `status:needs-challenge`, `status:ready`, `status:blocked`
3. **Verify after create (mandatory):** `gh issue view <N> --json labels`, then add any missing
   group. MCP, web and app clients often drop labels even when they were intended.
4. **Back-reference:** add `- **GitHub issue:** \`#NNN\`` to the `I-*` / `TB-*` metadata when the
   issue anchors one.

Hygiene-lane work usually takes `lane:c`, `type:refactor` (or `type:chore` for tooling),
`component:<target>`, `priority:p1`/`p2`, `status:ready`.

On Windows, if `gh` is not on PATH: `C:\Program Files\GitHub CLI\gh.exe`.
