# Ezkey — Agent notes (repo-wide)

For agents working anywhere in the repo. For module-specific conventions and patterns, see the `AGENTS.md` in each module (e.g. `ezkey-admin-ui/AGENTS.md`, `ezkey-admin-api/AGENTS.md`, `sites/ezkey-org/AGENTS.md` for the public static site and Cloudflare Pages context).

For full product and technical context, read **PRD.md**, **README.md**, **docs/PROJECT_POSITIONING.md**, and **docs/ENDPOINT.md** at the start of a new session.

For configuration properties, each backend module has a colocated **`CONFIGURATION.md`** (property tables, obligation levels, profile matrix, Docker env var mapping). The central index is **`docs/configuration/README.md`**. When adding or changing a `@ConfigurationProperties` class, update the relevant `CONFIGURATION.md` and, if a new prefix is introduced, the index.

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
reference example of this workflow. On this Windows workstation, the preferred autonomous
entrypoint from a Windows-hosted shell is `scripts/build-local.cmd`, because it forces the correct
Git Bash + JDK 25 + Maven path before delegating to `./scripts/build.sh`. See
`.cursor/rules/maven-build.mdc` for the authoritative rule.

**Local Maven version properties:** The parent POM defines `revision`, `changelist`, and an empty default `buildQualifier`. To override from the CLI for all reactor builds (for example a per–git-worktree suffix such as `-wt1` so local `install` artifacts do not clash), copy `.mvn/maven.config.example` to `.mvn/maven.config` and edit the last line. That file is gitignored and is not committed; CI and clones without the file use POM defaults only.

---

## Java Javadoc and Checkstyle (`@param` on types)

Checkstyle `JavadocType` validates Javadoc on **classes, interfaces, enums, and record types**. Tags such as `@param`, `@return`, and `@throws` belong on **methods and constructors** (validated by `JavadocMethod`), not on the type itself. Putting `@param` on a class or `record` produces `Unused @param tag … [JavadocType]`.

**Do:** summarize the type in its class Javadoc; put per-parameter descriptions on the **constructor** (or on fields / accessors as appropriate). See `.cursor/rules/javadoc-type-param.mdc`.

---

## `scanBasePackages` — keeping Application classes in sync

Each boot module (`AdminApplication`, `AuthApplication`, `IntegrationApiApplication`, …) declares an **explicit** `scanBasePackages` list. When a new top-level package is added to `ezkey-core` (e.g. `org.ezkey.service`), every boot application that transitively uses a bean from that package must include the new entry, or Spring will fail to start with a *"required a bean … that could not be found"* error.

**Rule:** whenever you introduce a new `org.ezkey.<package>` in `ezkey-core` that contains `@Service`, `@Component`, or `@Repository` classes, check and update `scanBasePackages` in **all three** application classes:

- `ezkey-admin-api` → `AdminApplication.java`
- `ezkey-auth-api` → `AuthApplication.java`
- `ezkey-integration-api` → `IntegrationApiApplication.java`

The symptom is a clean compile but a startup failure — not caught by unit tests.

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
