# Handoffs

Files in this directory are working prompts for queued or in-flight sessions, not durable records
of completed work.

- Keep `open` and `dormant` handoffs while they still route future work.
- Before closing completed work, reinject durable product or design decisions into the relevant
  living canon and record implementation evidence in the PR or campaign note.
- Delete the completed handoff in the same closeout change. Git history preserves its execution
  context without leaving stale prompt noise in the active backlog.
- Retarget dependent handoffs to living canon or implementation evidence before deletion.

This applies to all `HANDOFF-*.md` files here, including lightweight product and release hygiene
outside an `assessment-curated` campaign.
