---
status: archived
archived_date: 2026-01-16
completion_status: fully_implemented
---

# ⚠️ ARCHIVED PLAN

**This plan has been fully implemented and archived for historical reference.**

**Completion Date:** 2026-01-16
**Status:** ✅ Fully Implemented and Validated

---
name: maven_pom_versioning_no_branch_collisions
overview: Eliminate Maven artifact collisions between branches locally, while keeping the project aligned with Maven Central release conventions and a CI-friendly versioning strategy.
todos:
  - id: analyze-collision-sources
    content: Identify where branch artifacts are consumed from local repo (IDE runs, partial builds, module boundaries) and document the failure mode.
    status: completed
  - id: add-branch-aware-mvn-wrapper
    content: Plan wrapper scripts to set -Dmaven.repo.local per git branch (PowerShell + bash) and a consistent developer command.
    status: completed
  - id: document-dev-workflow
    content: Update development documentation with the recommended local build command and IDE guidance.
    status: completed
  - id: design-release-versioning
    content: Propose Maven Central-ready release versioning (tag-based, CI-friendly versions + flatten on deploy) without impacting day-to-day dev.
    status: completed
  - id: define-release-branches-policy
    content: Define conventions for main, feature branches, and optional future release branches.
    status: completed
---

## Context and problem statement

- The root Maven parent sets a fixed project version (`0.0.1-SNAPSHOT`) in the repository root POM, so **different branches produce identical GAV coordinates**.
- When you switch branches in the same working directory, your local Maven repository (`~/.m2/repository`) can contain artifacts installed from the other branch under the same coordinates, causing "last build wins" interference.

Key file:

- `pom.xml` (root): `<version>0.0.1-SNAPSHOT</version>`.

## Your constraints / intent (captured)

- **Primary pain**: you switch branches in the same working directory, and local Maven artifacts collide.
- **Short-term goal**: solve collisions **without** pulling in a heavy/fragile Maven versioning toolchain on day 1.
- **Future-proof goal**: stay aligned with **GitHub CI** and the eventual publication of artifacts to **Maven Central** (release conventions, reproducibility, "clean" POMs).

## What is common / expected in similar projects (industry patterns)

- **Most multi-module Maven repos** keep a **single version in the root parent POM**, and all modules inherit it.
- **Feature branches** typically keep `*-SNAPSHOT` and do not attempt to publish branch artifacts to Maven Central.
- **Releases** are typically produced from `main` (or a dedicated release branch) using:
- a tagged release version (e.g., `1.2.0`), and
- a return to the next `*-SNAPSHOT` after release.
- For CI pipelines and reproducible builds, common approaches are:
- **Maven release flow** (tagging + versions set during release), or
- **CI-friendly versions** (properties like `revision`/`changelist`) + a POM flattening step for deployment.

## Options to prevent local branch interference (ranked by pragmatism)

### Option A (recommended short-term): Branch-aware local Maven repository

Goal: keep project versioning simple, but **avoid collisions by isolating the local repo by branch**.

- Mechanism: run Maven with `-Dmaven.repo.local=<path-per-branch>`.
- Implementation style:
- A wrapper script that computes the current git branch name and routes Maven to a dedicated local repo folder.
- Works even when switching branches inside the same folder.

Pros:

- Minimal Maven "versioning machinery"
- No POM changes required
- Stops collisions immediately

Cons:

- Requires using the wrapper command consistently (or a small IDE configuration)

### Option B: Per-working-copy local repo (worktrees / multiple checkouts)

Goal: isolate builds by checkout path, not by branch.

- Mechanism: `-Dmaven.repo.local=<repo-root>/.mvn/local-repo` (or similar).

Pros:

- Very simple
- Great with git worktrees

Cons:

- If you switch branches in the same folder, artifacts can still be reused incorrectly unless you also separate by branch

### Option C (later, CI-friendly): CI-friendly versions + flatten on deploy

Goal: Maven Central alignment without fragile branch-name versions.

- Use Maven CI-friendly version properties:
- Root `pom.xml` version becomes something like `${revision}${changelist}`.
- Default local/dev: `revision=0.0.1`, `changelist=-SNAPSHOT`.
- Release: `changelist=` and `revision=<release>` set by CI.
- Add `flatten-maven-plugin` (or equivalent) to ensure deployed POMs have a resolved, non-expression version.

Pros:

- Clean, scalable, and deployment-friendly
- Keeps `main` readable and "release-ready"

Cons:

- Adds some Maven configuration and release-pipeline rules
- Needs careful integration with `deploy`/Central signing

## Recommended approach (phased, future-proof without day-1 complexity)

### Phase 1 (immediate): Stop collisions with a branch-aware local repo (solves your "same folder, switch branches" case)

- Add a lightweight wrapper for Windows and *nix (PowerShell + bash) that:
- detects current branch (e.g., `git rev-parse --abbrev-ref HEAD`)
- chooses a repo-local path (e.g., `%USERPROFILE%/.m2/ezkey/<sanitized-branch>`)
- invokes `mvn` with `-Dmaven.repo.local=...` (and passes all args through)
- Document the one-liner developers should use.

Notes:

- This keeps the existing single project version in `pom.xml` (simple mental model) while eliminating cross-branch contamination.
- It is compatible with both "switch branches" and "multiple checkouts" workflows.

### Phase 2 (near-term): IDE ergonomics and team consistency

- Provide IntelliJ/IDEA run config guidance: add `-Dmaven.repo.local=...` or call the wrapper.
- (Optional) provide a `.mvn/maven.config` baseline for non-branch-aware setups (useful in worktrees).

### Phase 3 (when preparing for Maven Central): CI-friendly versions (deploy-time) + clean POMs via flatten

Core idea:

- **Developers keep building locally as today** (Phase 1 prevents collisions).
- **CI becomes the "source of truth" for released versions** using a tag-based workflow, while ensuring the deployed POMs contain resolved versions (no `${revision}` expressions).

Implementation direction (to be executed later, not day-1):

- Switch root version expression to a CI-friendly form (e.g., `${revision}${changelist}`), with defaults that match today's behavior:
- `revision=0.0.1`
- `changelist=-SNAPSHOT`
- Use `flatten-maven-plugin` during `deploy` (or a dedicated profile) so artifacts uploaded to Central have a flattened, conventional POM.
- In GitHub Actions, derive release version from tag `vX.Y.Z`, set `-Drevision=X.Y.Z -Dchangelist=` (and any signing/repository credentials), then deploy.
- After release, bump `revision` (or a dedicated property) to the next development line and restore `-SNAPSHOT`.

Maven Central readiness checklist to align with (later):

- GPG signing, sources/javadoc jars, reproducible builds, proper `scm`/`licenses`/`developers` metadata, and deployment via Sonatype Central Portal / OSSRH equivalent (depending on current ecosystem requirements).

## Release conventions for the repo (proposed)

- **`main`**: always `X.Y.Z-SNAPSHOT` (or `revision + -SNAPSHOT`).
- **feature branches**: do not change project version; rely on local repo isolation.
- **release**: produced from `main` by CI using a tag `vX.Y.Z`.
- **future maintenance branch** (optional, later): `release/X.Y` branch for patch releases.

### Release branches (future) and "future branch" expectations

- **Mainline development**: `main` tracks the next minor/major line as `*-SNAPSHOT`.
- **Maintenance** (only if/when needed): create `release/X.Y` for patch releases (`X.Y.(Z+1)`), with `main` continuing forward.
- **No branch-specific published versions**: feature branches should not publish to Maven Central; if you ever need preview artifacts, publish to a separate internal repo with explicit coordinates/policies (not Central).

## Files likely to change

- Root build / tooling:
- `pom.xml` (Phase 3 only)
- `scripts/` (new wrapper scripts in Phase 1)
- `docs/DEVELOPMENT.md` (developer workflow note)

## Acceptance criteria

- Two local branches can be built/run on the same machine without any Maven artifact cross-contamination.
- The chosen strategy does not block a later Maven Central publication path.
- The release process is documented and repeatable.

## Implementation Summary

**What Was Implemented:**
- ✅ Refactored all POMs to use CI-friendly versions: `${revision}${buildQualifier}${changelist}`
- ✅ Created `.mvn/maven.config` with default values for developer-first experience
- ✅ Created `scripts/mvn-branch.sh` (bash wrapper) with branch name detection and SHA fallback
- ✅ Created `scripts/mvn-branch.ps1` (PowerShell 7.x wrapper) with same logic
- ✅ Updated `docs/DEVELOPMENT.md` with version management documentation
- ✅ All 9 modules updated to use CI-friendly parent version

**Key Features:**
- Branch-specific artifact versions prevent collisions (e.g., `0.0.1-feature-login-SNAPSHOT`)
- Standard `mvn clean install` still works (no wrapper required, but no branch isolation)
- Wrapper scripts provide automatic branch isolation for developers
- Future-proof for Maven Central releases via tag-based CI workflow

**Files Changed:**
- `pom.xml` (root) - CI-friendly version expression and properties
- All module `pom.xml` files (9 modules) - Updated parent version
- `.mvn/maven.config` - Default Maven properties
- `scripts/mvn-branch.sh` - Bash wrapper script
- `scripts/mvn-branch.ps1` - PowerShell wrapper script
- `docs/DEVELOPMENT.md` - Version management documentation
