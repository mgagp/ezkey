# Ezkey — Agent notes (repo-wide)

For agents working anywhere in the repo. For module-specific conventions and patterns, see the `AGENTS.md` in each module (e.g. `ezkey-admin-ui/AGENTS.md`, `ezkey-admin-api/AGENTS.md`).

For full product and technical context, read **PRD.md**, **README.md**, and **docs/ENDPOINT.md** at the start of a new session.

---

## Project values (analysis and design)

- **Simplicity and pragmatism**: 80–20 rule — target ~80% of the value with ~20% of the complexity. Prefer the simplest solution that meets the need.
- **Admin UI**: Sober, pragmatic interface that makes the operator's and their team's life easier. Avoid clutter and unnecessary decoration.
- **Admin roles**: **Global Admin** = IT-style; manages core/instance-level concerns (e.g. encryption keys). **Tenant Admin** = business-oriented; manages end users, integrations, API keys. Design and copy must reflect this split.
- **Comparables and best practices**: For any analysis or design, consider comparable projects and admin UIs; adopt widely recognised best practices from those comparables.
- **Stack and ecosystem**: Stay within the existing stack; avoid new frameworks or libraries unless there is a strong justification. Prefer what developers expect and what is considered best practice for the stack.
- **Complexity**: **Essential complexity** (required for the feature) is acceptable. **Accidental complexity** (extra indirection, unnecessary abstraction) must be minimised to keep maintenance and evolution manageable.
