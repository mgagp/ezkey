# Versioning and deploy traceability

Normative convention for how Ezkey talks about its maturity and how operators record
what is live on a deploy surface. Sibling ops context: [`OPERATIONAL.md`](OPERATIONAL.md),
[`lightsail/community-host.md`](lightsail/community-host.md).

## Product claims (operator / public)

| Layer | Rule |
|-------|------|
| **Public / operator claim** | **Public alpha** (French: **Alpha publique**). Honest pre-release language. |
| **Technical identity** | **Git commit SHA** of the deployed tree (full SHA in the ledger; short SHA in UI chrome). |
| **Not yet** | No GitHub Releases, no product semver tags (`v0.x`), no “stable release” numbering until there are published artefacts (JAR / Docker image / mobile store build). |

### Forbidden / discouraged chrome

- Do **not** show bare `v0.1.0` (reads like a stable release).
- Do **not** invent a parallel marketing version such as `0.0.0-alpha`.
- Acceptable UI chrome (preference order):
  1. `Public alpha · <sha7>` / `Alpha publique · <sha7>`
  2. `alpha · <sha7>`
  3. At worst `0.1.0-alpha · <sha7>` — still **never** `v0.1.0` alone

Maven / npm module versions remain **internal build identity**. Do not conflate them with the
public alpha label.

## Source of truth for “what is live”

For each deploy surface (community / ezkey.online first), the **live state ledger** is the
source of truth (SOOT):

- Path: [`lightsail/community/DEPLOYED.md`](lightsail/community/DEPLOYED.md)
- Fields: surface, full git SHA, short SHA, UTC timestamp, operator, optional note
- History: **git log of that file** (amend by commit after a successful publish)

### Update procedure (after a successful publish)

Operators (e.g. Edgar on community/Lightsail) update the ledger **only after** the deploy
actually succeeded:

1. Confirm the running stack matches the intended commit (images / Pages deployment /
   containers as applicable).
2. Edit `docs/lightsail/community/DEPLOYED.md`: set full SHA, short SHA (7 chars), UTC time
   (`date -u +%Y-%m-%dT%H:%M:%SZ`), operator handle, optional note.
3. Commit on a normal ops PR/branch (conventional message, e.g.
   `docs(ops): community deploy <sha7>`).
4. Optionally move the lightweight tag `deploy/community` to the same SHA (see below).

Do **not** update the ledger “in advance” of a failed or rolled-back publish.

## Optional deploy tags

Tags are a **convenience** for `git show deploy/community`, not the SOOT.

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

- Creating GitHub Releases or product semver tags
- Changing live DNS / Cloudflare / Lightsail without a separate ops go-ahead
- Rewriting Maven `revision` / module versions to match the public alpha label
