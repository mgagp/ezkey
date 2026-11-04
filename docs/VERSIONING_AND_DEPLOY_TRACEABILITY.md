# Versioning and deploy traceability

Normative convention for how Ezkey talks about its maturity, how product versions are named,
and how operators record what is live on a deploy surface. Sibling ops context:
[`OPERATIONAL.md`](OPERATIONAL.md),
[`lightsail/community-host.md`](lightsail/community-host.md).

**Architecture decision:** [ADR-0017](../product-docs/global/architecture-decisions.md#adr-0017-product-versioning-one-semver-for-the-monorepo)
(one SemVer for the whole monorepo). Issue:
[#1211](https://github.com/mgagp/ezkey/issues/1211).

## Product version (SemVer)

| Rule | Detail |
|------|--------|
| **Scheme** | [Semantic Versioning](https://semver.org/) |
| **Scope** | **One** product version for the **whole monorepo** (backends, Admin UI, demo, CLI, PAM, mobile) |
| **Start** | `0.2.0` |
| **0.x meaning** | No stability promise (lab / public-alpha posture) |

**Trace:** First product version is `0.2.0`, not `0.1.0` — Play already shipped
`versionName` `0.1.2-alpha`, and the mobile version must not go backwards —
Marc, 2026-11-04, [#1211](https://github.com/mgagp/ezkey/issues/1211).

**Trace:** Product tags are annotated `vX.Y.Z` on `main` (optional `-alpha.N` pre-release) —
names the whole ship set; keeps tag grammar distinct from other namespaces —
Marc / [#1211](https://github.com/mgagp/ezkey/issues/1211).

| Kind | Form | Notes |
|------|------|-------|
| **Product tag** | Annotated git tag `vX.Y.Z` on `main` | Optional pre-release: `vX.Y.Z-alpha.N`. Not cut in ADR step 1; first tag is a later [#1211](https://github.com/mgagp/ezkey/issues/1211) step. |
| **Not a product version** | `methodology/*` | Methodology pack releases; keep as-is. |
| **Not a product version** | `deploy/*` | Movable deploy pointers (e.g. `deploy/community`); keep as-is. |

**Trace:** Existing `methodology/*` and `deploy/*` tags stay and are **not** product versions —
avoids conflating method/deploy pointers with the product line —
[#1211](https://github.com/mgagp/ezkey/issues/1211).

### Mobile (`versionName` / `versionCode`)

| Field | Rule |
|-------|------|
| **`versionName`** | Equals the product version `X.Y.Z` (same string as the SemVer without the leading `v`; use a matching pre-release suffix when the product tag does). |
| **`versionCode`** | Monotonic increasing integer (Play requirement). |

**Trace:** Mobile `versionName` = product `X.Y.Z` with a monotonic `versionCode` — keeps the store
upload identity tied to the monorepo product line; an own mobile cadence later would be a
**separate** decision — [#1211](https://github.com/mgagp/ezkey/issues/1211).

### SHA complementarity

**Trace:** Git **SHA** deploy traceability stays and **complements** the product tag — the live
ledger remains the source of truth for what is running; the tag names a release cut, not a
substitute for the ledger — [#1211](https://github.com/mgagp/ezkey/issues/1211).

### Alignment mechanisms (step 2)

Parent POM CI-friendly properties are the **single source of truth**:

| Property | Dev default | Role |
|----------|-------------|------|
| `revision` | `0.2.0` | Product SemVer core (local branch isolation may append `-<branch>` via `scripts/mvn-branch.sh`) |
| `changelist` | `-SNAPSHOT` | Dev suffix; clear for a release cut |

Full Maven coordinate = `${revision}${changelist}` (e.g. `0.2.0-SNAPSHOT`).
`flatten-maven-plugin` (`resolveCiFriendliesOnly`) resolves those placeholders in installed POMs.
Do not embed custom properties inside `<version>` — flatten would leave them unresolved.

| Surface | Mapped form | Notes |
|---------|-------------|-------|
| Maven reactor modules | inherit `${revision}${changelist}` | No hard-coded module versions |
| npm (Admin UI, `ezkey-javascript-sdk`, generated `ezkey-admin-client` / `ezkey-auth-client`) | Maven string as-is | e.g. `0.2.0-SNAPSHOT` |
| .NET `Ezkey.Sdk` `PackageVersion` | Maven string as-is | NuGet prerelease label |
| Python (`ezkey-python-sdk`, `ezkey-cli`) | `-SNAPSHOT` → `.dev0` (PEP 440) | e.g. `0.2.0.dev0` |
| Mobile `package.json` / Android `versionName` | strip trailing `-SNAPSHOT` only | e.g. `0.2.0`; keep `-alpha.N` when present |
| Android `versionCode` | monotonic integer **> 6** | Floor `7` for the `0.2.0` train (Play already shipped vc6) |
| Jenkins lab `ezkey.sdk.version` + install-file pins | Maven string | Lab **plugin** SemVer stays independent |

**Commands:**

```bash
./scripts/sync-version.sh          # write aligned versions from parent POM
./scripts/check-version-alignment.sh   # CI gate — fail on divergence
```

Spring Boot APIs generate `META-INF/build-info.properties` and expose product SemVer on
management `/actuator/info` (`build.version`). Env dumps stay off (`management.info.env.enabled=false`).
Admin UI chrome stays **Public alpha · \<sha7\>** (do not show bare `v0.2.0`); `package.json`
version is aligned for tooling only.

**Trace:** POM / npm / SDK / Android / CLI alignment + CI divergence check —
[#1211](https://github.com/mgagp/ezkey/issues/1211) step 2.

## Product claims (operator / public)

| Layer | Rule |
|-------|------|
| **Public / operator claim** | **Public alpha** (French: **Alpha publique**). Honest pre-release language. |
| **Technical identity (live deploy)** | **Git commit SHA** of the deployed tree (full SHA in the ledger; short SHA in UI chrome). Complements a product tag when one exists for that cut. |
| **Product version** | SemVer for the monorepo per ADR-0017 (`0.2.0` start). First annotated `v*` tag and GitHub Release are **not** cut in this docs step. |
| **Not yet** | No GitHub Releases and no published installable artefacts (JAR / Docker image / store build) until later [#1211](https://github.com/mgagp/ezkey/issues/1211) steps. Do not imply a “stable release” from a bare `v1.x` style claim while the line is `0.x`. |

### Forbidden / discouraged chrome

A git tag such as `v0.2.0` is the correct product-tag form; the UI must never show bare
`v0.2.0` alone (keep the public-alpha framing and SHA chrome below).

- Do **not** show bare `v0.2.0` alone in operator chrome (reads like a finished stable release).
- Do **not** invent a parallel marketing version such as `0.0.0-alpha` beside the product SemVer.
- Acceptable UI chrome (preference order):
  1. `Public alpha · <sha7>` / `Alpha publique · <sha7>`
  2. `alpha · <sha7>`
  3. At worst `0.2.0-alpha · <sha7>` — still **never** `v0.2.0` alone without the public-alpha
     framing

Maven / npm / SDK package versions follow the parent POM via `scripts/sync-version.sh`. They are
**build identity**, not a second public claim — UI chrome still prefers Public alpha + SHA (see
Forbidden / discouraged chrome).

## Source of truth for “what is live”

For each deploy surface (community / ezkey.online first), the **live state ledger** is the
source of truth (SOOT):

- Path: [`lightsail/community/DEPLOYED.md`](lightsail/community/DEPLOYED.md)
- Fields: surface, full git SHA, short SHA, UTC timestamp, operator, optional note
- History: **git log of that file** (amend by commit after a successful publish)

### Community surface shape (inventory lock)

As operated today (Seb read-only inventory of ezkey.online):

| Piece | Where it runs | Traceability note |
|-------|---------------|-------------------|
| **Admin UI** | **Cloudflare Pages** (not a Lightsail container) | Record the **git SHA of the tree that was built and published** to Pages. Pages deploy id alone is not enough unless mapped back to that SHA. |
| **APIs / Demo ACME** | Lightsail Docker VM | Images tagged `*:latest` are **not** deploy traceability. Ledger must record the **full git SHA** that produced (or was intended for) those images. Rolling / compose builds also stamp OCI `org.opencontainers.image.revision` via `OCI_IMAGE_REVISION` (#814; see [`experimental-hybrid/BACKEND_ROLLING_UPDATE.md`](../experimental-hybrid/BACKEND_ROLLING_UPDATE.md)). |
| **Demo ACME / health / actuator** | same VM | Management `/actuator/info` exposes `build.version` (product SemVer) when the stack is built from this tree; live deploy SOOT remains the SHA ledger. |

Closing the pre-convention gap: until the first post-publish ledger row exists, live SHA is
**unknown** even if containers are `latest`. The procedure below is how that gap is closed.

### Update procedure (after a successful publish)

Operators (e.g. Edgar on community/Lightsail + Pages) update the ledger **only after** the
deploy actually succeeded:

1. Confirm the running stack matches the intended commit:
   - Admin UI: Cloudflare Pages deployment from the build that stamped `VITE_GIT_SHA`.
   - Backend: Lightsail containers rebuilt/reloaded from that same monorepo SHA (or note a
     known split in the ledger **Note** field — prefer same SHA).
2. Edit `docs/lightsail/community/DEPLOYED.md`: set **full** SHA, short SHA (7 chars), UTC time
   (`date -u +%Y-%m-%dT%H:%M:%SZ`), operator handle, optional note.
3. Commit on a normal ops PR/branch (conventional message, e.g.
   `docs(ops): community deploy <sha7>`).
4. Optionally move the lightweight tag `deploy/community` to the same SHA (see below).

Do **not** update the ledger “in advance” of a failed or rolled-back publish.
Do **not** treat Docker image tag `:latest` (or npm `0.0.0`) as a substitute for the full SHA.


## Optional deploy tags

Tags are a **convenience** for `git show deploy/community`, not the SOOT.
They are **not** product versions (see Product version above).

| Tag | Meaning |
|-----|---------|
| `deploy/community` | Movable lightweight tag → same SHA as the community ledger row |
| `deploy/ezkey-org` | Optional later, same pattern for the public site |

Rules:

- Prefer **one movable tag per surface**.
- Do **not** mass-create `deploy/<surface>/<date>` tags.
- Ledger first; tags second and optional.

Example (after ledger commit is on the intended SHA):

```bash
git tag -f deploy/community <full-sha>
git push -f origin deploy/community
```

(`-f` is expected for a movable pointer; coordinate with the surface owner.)

## Admin UI build identity (`VITE_GIT_SHA`)

The Admin UI sidebar shows **Public alpha · \<sha7\>**. The short SHA is injected at **Vite
build time**:

1. If env `VITE_GIT_SHA` is set, use it (prefer a 7-character short SHA; longer values are
   truncated in the UI helper).
2. Else Vite resolves `git rev-parse --short=7 HEAD` when `.git` is available.
3. Else fallback: `dev` (local) / `unknown` when git is unavailable.

### Community / Lightsail / Cloudflare builds

Set the SHA explicitly so Docker or CI contexts without a full git tree still stamp correctly:

```bash
export VITE_GIT_SHA="$(git rev-parse --short=7 HEAD)"
# then: npm run build / build:cloudflare, or docker compose build with build-arg
```

- Docker Admin UI: pass build-arg `VITE_GIT_SHA` (see `ezkey-admin-ui/docker/Dockerfile` and
  `docker-compose.admin-ui.yml`).
- Cloudflare Pages scripts with `--build`: export `VITE_GIT_SHA` before
  `npm run build:cloudflare` (deploy scripts do this when git is available).
- Git-connected Pages: set `VITE_GIT_SHA` in the project env, or rely on a build command that
  exports it from `CF_PAGES_COMMIT_SHA` / `git rev-parse`.

## Out of scope here

- Creating the first annotated product tag or GitHub Release (later [#1211](https://github.com/mgagp/ezkey/issues/1211) steps)
- CHANGELOG / RELEASING.md / registry publication / signing (later [#1211](https://github.com/mgagp/ezkey/issues/1211) steps)
- Changing live DNS / Cloudflare / Lightsail without a separate ops go-ahead
