# Agent surface (hygiene)

Review of what a cold agent (Claude Code, Cursor) loads before it reads the request: root
`CLAUDE.md` and `AGENTS.md`, `.cursor/rules/`, project skills in `.claude/skills/`, harness settings
and hooks, module `AGENTS.md` files.

**Not** product vision, ADR or backlog execution. No `I-*` / `TB-*` per finding.

**Cadence:** on demand. Trigger a pass when the root guide passes the soft guardrail below, when
`/doctor` flags stale or conflicting instructions, or when an agent keeps missing a rule.

## Operating rules

1. The root guide follows **mission and compass → rules → what "done" means → how to prove it**.
   Everything else is a pointer, a skill, a module guide, or a harness control.
2. **Soft guardrail:** root `AGENTS.md` ≤ 200 lines (Anthropic's per-file target for instruction
   files). Exceeding it blocks nothing; it triggers a prune pass.
3. **Per-line test:** would a cold agent make a real mistake without this line, and would it not
   find it by reading the repo? If not, cut it or move it.
4. One home per concept. Module-scoped guidance lives in the module `AGENTS.md`; recurring
   procedures and hygiene lanes are skills in `.claude/skills/` (read by Claude Code and Cursor).
5. A zero-exception rule that is mechanically checkable and repeatedly missed becomes a hook or a
   permission, not a louder sentence.
6. Walk changes one section at a time with the operator (HITL); each PR leaves main coherent.

## Passes

- 2026-11: [framing](2026-11-23-cadrage.md) · [inventory and decisions](2026-11-23-inventory.md)
