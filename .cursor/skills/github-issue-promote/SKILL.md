---
name: github-issue-promote
description: Opens a GitHub issue from I-* or TB-* with mandatory labels, traceability body, and canon back-reference. Use when an operator or program slice needs GitHub visibility (issue, billet, ticket).
disable-model-invocation: true
---
# GitHub Issue Promote

## Purpose

Create a **labeled** GitHub issue that mirrors canonical `I-*` / `TB-*` intent without becoming a
second source of truth.

## Boundary contract

- **Enter when:** an idea or tracer bullet warrants GitHub visibility (program slice, PR board,
  dual-repo sync, operator request, or retroactive visibility at closeout).
- **Exit when:** issue exists with **all required labels**, traceability body, verification output,
  and `#NNN` recorded in canonical metadata.
- **Read first (mandatory):** [`product-docs/methodology/github-issues-workflow.md`](../../product-docs/methodology/github-issues-workflow.md)
- **Not needed when:** hygiene-only work with a single small PR and the operator did not ask for an issue.

## Required labels (non-negotiable)

Every new issue must include **at least one label from each group** before you report success:

| Group | Example | Derive from |
| ----- | ------- | ----------- |
| Lane | `lane:a` | TB/I **Lane** field or workflow lane |
| Type | `type:feat` | Nature of change (feat/fix/refactor/docs/chore/test/security) |
| Component | `component:admin-ui` | TB/I **Component tags** (one or more) |
| Priority | `priority:p2` | Backlog **Priority** (`P0`→`p0`, …) or TB risk |
| Status | `status:ready` | Grill done → `ready`; else `needs-challenge` |

**Do not** run bare `gh issue create` without `--label` flags unless the operator explicitly defers
labeling — and then say so in the closeout.

## Steps

1. Read the target `I-*` and/or `TB-*` artifact (Intent, Scope, Metadata, Component tags, Lane).
2. Draft title (passes the **"title that stands alone"** test — see workflow doc).
3. Build the issue body from the template in `github-issues-workflow.md` § Issue body template.
4. Assemble labels — write them in the agent response **before** creating the issue (operator
   sanity check).
5. Create the issue with explicit labels (repeat `--label` for each):

   ```bash
   gh issue create \
     --title "feat(admin-ui): audit logs Tier B selective name joins" \
     --body-file /path/to/body.md \
     --label "lane:a" \
     --label "type:feat" \
     --label "component:admin-api" \
     --label "component:admin-ui" \
     --label "priority:p2" \
     --label "status:ready"
   ```

   On Windows when `gh` is not on PATH: `"C:\Program Files\GitHub CLI\gh.exe" issue create ...`

6. **Verify** labels landed:

   ```bash
   gh issue view <N> --json number,title,labels
   ```

   If any required group is missing, add immediately:

   ```bash
   gh issue edit <N> --add-label "component:admin-ui"
   ```

7. Update canonical metadata:
   - `I-*` and/or `TB-*` → `- **GitHub issue:** \`#NNN\``
   - Optional: `- **Issue labels:** \`lane:a\`, \`type:feat\`, …\`` for cold-session discoverability

8. Report to operator: issue URL + label list + canon paths updated.

## PR follow-up

When a branch exists, comment on the issue: `Implementation branch: feature/<NNN>-...` (see workflow
doc § Branch naming).

## Rule

An unlabeled issue is **incomplete work** — same class of gap as missing traceability in `TB-*`.
Fix labels before moving on to implementation or closeout.
