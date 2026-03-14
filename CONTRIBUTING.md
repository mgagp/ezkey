# Contributing to Ezkey

Thank you for your interest in contributing. This document summarises how we work and the values that guide analysis and design decisions. For build, setup, and project organisation, see **README.md**. For product and functional context, see **PRD.md**.

## Project values (analysis and design)

These values guide feature analysis, design, and implementation across the project:

- **Simplicity and pragmatism**: 80–20 rule — target ~80% of the value with ~20% of the complexity. Prefer the simplest solution that meets the need.
- **Admin UI**: Sober, pragmatic interface that makes the operator's and their team's life easier. Avoid clutter and unnecessary decoration.
- **Admin roles**: **Global Admin** = IT-style; manages core/instance-level concerns (e.g. encryption keys). **Tenant Admin** = business-oriented; manages end users, integrations, API keys. Design and copy must reflect this split.
- **Comparables and best practices**: For any analysis or design, consider comparable projects and admin UIs; adopt widely recognised best practices from those comparables.
- **Stack and ecosystem**: Stay within the existing stack; avoid new frameworks or libraries unless there is a strong justification. Prefer what developers expect and what is considered best practice for the stack.
- **Complexity**: **Essential complexity** (required for the feature) is acceptable. **Accidental complexity** (extra indirection, unnecessary abstraction) must be minimised to keep maintenance and evolution manageable.

## Where to read more

- **README.md** — Overview, build, and setup
- **PRD.md** — Product requirements and principles
- **docs/ENDPOINT.md** — API endpoints and behaviour
- **.github/copilot-instructions.md** — Code style and conventions for AI-assisted development
- **AGENTS.md** (root and per-module) — Agent notes and patterns
