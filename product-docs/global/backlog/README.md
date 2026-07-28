# Markdown Backlog

This folder is the markdown-native backlog for Ezkey.

It is intentionally simple:

- one idea per file,
- stable identifiers,
- explicit status lifecycle,
- lightweight metadata for filtering and prioritization.

## Structure

- `index.md` — compact backlog index for quick navigation.
- `statuses-and-lifecycle.md` — status transition model.
- `ideas/` — idea files (`I-*`), regardless of status; includes `done`/`parked`/`archived`/`dropped`.
- Tracer bullets (`TB-*`) live at `backlog/` root, not under `ideas/` (see `statuses-and-lifecycle.md`
  § Tracer bullet placement).

**`archived` is a status, not a folder.** An idea that is `archived` stays in `ideas/` with that
status value; `index.md`'s tables (or a per-status filter) are the discoverability layer. Files are
never physically moved on status change alone.

## Operating rules

- Keep idea files short and actionable.
- Update idea metadata when status or priority changes.
- Promote implementation-ready ideas into tracer bullets.
- Keep links to related roadmap/features/documents explicit.
