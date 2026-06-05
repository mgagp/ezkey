# Release Brief — `REL-YYYY-MM-DD-v<semver>-<slug>` `<release name>`

## Metadata

- **ID:** `REL-YYYY-MM-DD-v<semver>-<slug>`
- **Version:** `v<semver>`
- **Release name:** `<short name>`
- **Status:** `planned` / `scoped` / `feature-complete` / `stabilizing` / `released` / `maintaining` / `closed`
- **Target release date:** `YYYY-MM-DD`
- **Released at:** `YYYY-MM-DD` _(set when released)_
- **Release branch:** `release/v<semver>` _(optional until feature-complete)_
- **Release tag:** `v<semver>` _(set when released)_
- **Owner:** `<name>`

## Intent

One short paragraph explaining why this release exists.

## Release model

- **Model:** Evergreen / other
- **Support posture:** forward-moving release line; no long-term support unless explicitly stated
- **Patch posture:** release-line fixes are normally forward-ported to `main`

## Included scope

List only work that is merged, closed out, and traceable, or record the exception explicitly.

| Item | Source artifact | Evidence | Status |
| --- | --- | --- | --- |
| `<short item>` | `I-*` / `TB-*` / decision / component doc | tests, closeout, PR, docs | included |

## Excluded or deferred scope

| Item | Source artifact | Reason | Next location |
| --- | --- | --- | --- |
| `<short item>` | `I-*` / `TB-*` / decision | not ready / out of scope / superseded | backlog / later release / dropped |

## Readiness gate

- [ ] Included work is merged to `main`, or exceptions are documented.
- [ ] Closeout/status updates are complete for included `I-*` and `TB-*` artifacts.
- [ ] Minimum test evidence is recorded.
- [ ] User-facing or operator-facing documentation is updated where needed.
- [ ] Release notes or deployment notes are drafted where needed.
- [ ] Rollback or mitigation posture is known.
- [ ] Residual risks are accepted, deferred, or linked to follow-up work.

## Stabilization notes

- release branch opened:
- verification focus:
- known risks:
- fixes accepted during stabilization:

## Patch and forward-port log

Use this only after the release branch exists.

| Fix | Release branch reference | Forward-port to `main` | Notes |
| --- | --- | --- | --- |
| `<fix>` | commit / PR / artifact | yes / no / superseded | reason if not forward-ported |

## Publication evidence

- Build artifact:
- Deployment target:
- Release tag:
- Release notes:
- Verification result:

## Closeout

- Final status:
- Residual risks:
- Follow-up work:
- Lessons for next release:
