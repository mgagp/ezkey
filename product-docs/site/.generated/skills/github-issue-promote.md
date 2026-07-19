# Skill: `github-issue-promote`

<section class="skill-detail-hero skill-detail-hero--additional">
  <p class="skills-eyebrow">Method skill</p>
  <p class="skill-detail-summary">Opens a GitHub issue from I-* or TB-* with mandatory labels, traceability body, and canon back-reference. Use when an operator or program slice needs GitHub visibility (issue, billet, ticket).</p>
  <div class="skill-chip-row">
    <span class="skill-chip">Order 05</span>
    <span class="skill-chip">Source of truth: <code>.cursor/skills/github-issue-promote/SKILL.md</code></span>
    
  </div>
</section>

## Purpose

Create a **labeled** GitHub issue that mirrors canonical `I-*` / `TB-*` intent without becoming a
second source of truth.

## Boundary contract

<div class="skill-contract-grid">
  <article class="skill-contract-card">
    <h3>Enter when</h3>
    <p>an idea or tracer bullet warrants GitHub visibility (program slice, PR board,</p>
  </article>
  <article class="skill-contract-card">
    <h3>Exit when</h3>
    <p>issue exists with **all required labels**, traceability body, verification output,</p>
  </article>
  <article class="skill-contract-card">
    <h3>Call next</h3>
    <p>—</p>
  </article>
  <article class="skill-contract-card">
    <h3>Not needed when</h3>
    <p>hygiene-only work with a single small PR and the operator did not ask for an issue.</p>
  </article>
</div>

## Inputs

No additional input guidance recorded.

## Outputs

No additional output guidance recorded.

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

## Rule

An unlabeled issue is **incomplete work** — same class of gap as missing traceability in `TB-*`.
Fix labels before moving on to implementation or closeout.

## Method links

- [Skills overview](README.md)
- [AI Collaboration Model](#/methodology/ai-collaboration-model.md)
- [Session Start Guide](#/methodology/session-start-guide.md)
- [Methodology Pack](#/methodology/README.md)
