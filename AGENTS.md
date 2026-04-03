# Ezkey — Agent notes (repo-wide)

For agents working anywhere in the repo. For module-specific conventions and patterns, see the `AGENTS.md` in each module (e.g. `ezkey-admin-ui/AGENTS.md`, `ezkey-admin-api/AGENTS.md`).

For full product and technical context, read **PRD.md**, **README.md**, **docs/PROJECT_POSITIONING.md**, and **docs/ENDPOINT.md** at the start of a new session.

---

## Maven and formatting (before any build)

After implementing or changing Java (or other Spotless-covered) code, use the safe Maven baseline
from the repository root in **Bash**:

1. `mvn spotless:apply`
2. `mvn checkstyle:check`
3. `mvn clean`
4. `mvn install -DskipTests`

This is the default autonomous validation path because Checkstyle depends on the reactor-built
`checkstyle-config` module. Only after that baseline succeeds should you run targeted follow-up
commands such as `mvn test -pl 'ezkey-admin-api,!ezkey-tests'`. `./scripts/build.sh` is the
reference example of this workflow. See `.cursor/rules/maven-build.mdc` for the authoritative
rule.

---

## Project values (analysis and design)

- **Simplicity and pragmatism**: 80–20 rule — target ~80% of the value with ~20% of the complexity. Prefer the simplest solution that meets the need.
- **Admin UI**: Sober, pragmatic interface that makes the operator's and their team's life easier. Avoid clutter and unnecessary decoration.
- **Admin roles**: **Global Admin** = IT-style; manages core/instance-level concerns (e.g. encryption keys). **Tenant Admin** = business-oriented; manages end users, integrations, API keys. Design and copy must reflect this split.
- **Comparables and best practices**: For any analysis or design, consider comparable projects and admin UIs; adopt widely recognised best practices from those comparables.
- **Stack and ecosystem**: Stay within the existing stack; avoid new frameworks or libraries unless there is a strong justification. Prefer what developers expect and what is considered best practice for the stack.
- **Complexity**: **Essential complexity** (required for the feature) is acceptable. **Accidental complexity** (extra indirection, unnecessary abstraction) must be minimised to keep maintenance and evolution manageable.

---

## Admin UI Browser Validation

- The repository includes a **Playwright** browser suite for the Admin UI in `ezkey-admin-ui/`.
- The preferred validation model is **real end to end** with the standard clean-start stack plus the pre-seeded **Demo Device**.
- Use the browser suite when a change materially affects Admin UI behavior or the Admin UI ↔ Demo Device flow.
- Do **not** treat browser tests as mandatory for every trivial UI tweak.
- See `ezkey-admin-ui/AGENTS.md` for the concrete commands and execution modes.

## UI Test Autonomy

- Treat browser UI tests as a **pragmatic judgment call**, not a mechanical checklist item.
- When a task changes a **critical UI workflow**, a security-sensitive action, a confirmation path, conditional navigation, or the Admin UI ↔ Demo Device interaction, explicitly evaluate whether the existing browser tests are:
  - already sufficient,
  - worth running as-is,
  - or worth extending with a **targeted** new scenario.
- State that judgment clearly in analysis or close-out instead of leaving it implicit.
- Prefer a **small representative test addition** over broad UI coverage when a gap is real.
- If the change is minor (copy, spacing, low-risk presentation only), do **not** over-recommend browser testing.

## Manual Exploratory Test Summaries

- When a task changes a meaningful workflow and a human validation pass would add value, consider proposing a **brief manual exploratory test summary** in addition to automated checks.
- The default assumption for that summary is a **clean-start baseline**:
  - standard `clean-start`
  - empty database
  - default Docker parameters
  - normal local developer/test posture
- When it helps make the workflow concrete, prefer examples that use the Admin UI **demo-mode themes** and presets rather than abstract placeholder data.
- Keep these summaries short and practical:
  - a few high-signal steps
  - expected outcomes
  - no bloated checklist unless the user asks for one
- Use this when it improves QA handoff, human review, or future historical traceability in plans and task summaries.
