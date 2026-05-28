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

## Contribution workflow

Ezkey uses a methodology-first approach. Before writing code, position the work in the product corpus.

**1 — Capture and triage**  
New ideas or improvements start as a backlog idea (`I-*`) under `product-docs/global/backlog/ideas/`. The methodology provides lane and skill guidance:
- `product-docs/methodology/session-start-guide.md` — choose your lane and skill sequence
- `product-docs/methodology/README.md` — full methodology overview

**2 — Open a GitHub issue**  
Once the idea passes the "title that stands alone" test, open an issue on `mgagp/ezkey`. Apply the appropriate labels (`lane:*`, `type:*`, `component:*`, `priority:*`). See `product-docs/methodology/github-issues-workflow.md` for the skill and label taxonomy.

**3 — Branch from the issue**  
Create a branch using the convention:
```
feature/<issue-number>-<artifact-slug>
```
Example: `feature/152-i-2026-0027-mobile-ios`

**4 — Implement and validate**  
Follow the tracer bullet (`TB-*`) scope. Use the methodology skills (`component-design-pack`, `test-strategy-planner`, `quality-gatekeeper`) as checkpoints before opening a PR.

**5 — Open a pull request**  
PR title follows conventional commit format: `feat(component): short description (#NNN)`.  
PR body must include `Closes #NNN` and a traceability block linking to the I-* and TB-* artifacts.

## Build and verification

Run the standard build from the repository root:

```bash
scripts/build.sh
```

This runs Spotless apply, Checkstyle, and `mvn clean install`. See **README.md** for prerequisites and module layout.

## Functional validation

Before opening a PR for backend or API changes, start a clean stack and run the functional test suite:

```bash
# Start a clean stack (empty database, default config)
ezkey-tests/clean-start.sh

# Run the full functional test suite
mvn test -pl ezkey-tests -P all-tests
```

Postman collections are available under `postman/collections/` for exploratory or manual validation.

For the testing strategy and what layer covers what, read `product-docs/methodology/testing-strategy-in-workflow.md`.
