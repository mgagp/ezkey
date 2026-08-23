# Handoff — centralize Tink / ShedLock / ipaddress pins in the parent POM

**Status:** `open` — analysis parked; **do not implement** until the operator pastes this prompt
and authorizes the slice.
**Keyword:** hygiene (not `assessment-curated`, not a weekly `dependabot-curated` lot)
**Origin:** 2026-08-21 Java Dependabot coverage evaluation (after Spring Boot 4.1.1)

Use this prompt to start a **new Cursor session**. Default: do **not** invent `I-*` / `TB-*`.
This is a bounded hygiene lift. Docker image tags are **out of scope**.

---

## Paste-ready operator prompt

```text
Hygiene handoff: centralize Tink, ShedLock, and ipaddress versions in the Ezkey parent POM.

Read product-docs/global/hygiene/dependabot/handoff-centralize-core-pins.md and do only
what that file authorizes. Do not start a Dependabot weekly pass. Do not touch Docker
images. Do not bump those three libraries unless a pin lift cannot land without a
same-change-set patch. After Go, apply on a dedicated hygiene branch and validate with
./scripts/build.sh.
```

---

## Operator decisions (already made)

1. **Disposition:** parked. Express the observation as a handoff; implement later when the
   operator funds a separate hygiene pause.
2. **Docker:** leave as-is. Compose image tags (`postgres:18-alpine`, etc.) are a different
   pause, operator-owned, not this slice.
3. **Dependabot weekly:** do not fold this lift into a routine `dependabot-curated` lot.

---

## One-sentence problem

Three runtime libraries are pinned with duplicated literal versions in child POMs instead of
parent properties + `dependencyManagement`, so Dependabot atomizes bumps and the pins can drift
across `ezkey-core`, `ezkey-core-security`, and demo-acme.

---

## Scenario that led to the observation

A 2026-08-21 review asked whether Dependabot Maven at `directory: "/"` covers the whole Java
reactor. It does walk `<modules>` and **does** see these pins (Tink PRs `#248`, `#350` landed).
The gap is not invisibility — it is **shape**:

- Versions live in child POMs, not in root `pom.xml` `<properties>`.
- The same coordinates are repeated in two or three modules.
- Dependabot can open one PR per module instead of one parent property bump.
- `docs/dependency-posture-admin-and-ui.md` already listed Tink / ShedLock / ipaddress as a
  lower-priority periodic refresh.

**Non-claim:** Dependabot is not missing `ezkey-core` or `ezkey-core-security`. Grouping in the
parent is hygiene against atomization and drift, not a coverage fix.

---

## Evidence map (read these first)

| Coordinate | Current pin (2026-08-21) | Where it is hardcoded |
|---|---|---|
| `com.google.crypto.tink:tink` | `1.23.0` | `ezkey-core/pom.xml`, `ezkey-core-security/pom.xml` |
| `net.javacrumbs.shedlock:shedlock-spring` | `7.7.0` | same two modules |
| `net.javacrumbs.shedlock:shedlock-provider-jdbc-template` | `7.7.0` | same two modules |
| `com.github.seancfoley:ipaddress` | `5.6.2` | `ezkey-core/pom.xml`, `ezkey-core-security/pom.xml`, `ezkey-demo-app-acme/pom.xml` |

Parent already uses this pattern for ZXing, Bucket4j, Caffeine, MapStruct, SpringDoc. Copy that
shape. Do **not** add a Dependabot group unless the operator asks after the lift (a parent
property usually makes grouping unnecessary).

---

## Intended fix shape (when authorized)

1. Add parent properties, for example `tink.version`, `shedlock.version`, `ipaddress.version`.
2. Declare the artifacts once under parent `dependencyManagement`.
3. Remove `<version>` from the child dependency declarations listed above.
4. Leave the declared versions **unchanged** unless a pin is already inconsistent across modules
   (then stop and ask — do not pick a winner silently).
5. Optional one-line note in `docs/dependency-posture-admin-and-ui.md` that these three now
   follow the parent, same as ZXing / Bucket4j.

### Tests (minimum)

- `./scripts/build.sh` from Git Bash.
- No API contract change → no `update-specs`, no Playwright.

---

## Product / methodology constraints

- Lane hygiene; no `I-*` / `TB-*` unless the operator asks after seeing the diff.
- Out of scope: version bumps of Tink / ShedLock / ipaddress, Docker Compose images, mobile /
  Gradle, SEC-019 Boot overrides, Google Java Format.
- Tink is crypto-adjacent: if the lift is **property-only** (same version), no extra
  characterization tests. If a bump sneaks in, stop and treat it as T4 / crypto HITL.

---

## Suggested closeout

- Dedicated branch `hygiene/centralize-core-pins`.
- Campaign note under `product-docs/global/hygiene/dependabot/` only if this lands in a
  Dependabot-adjacent session; otherwise a short PR body is enough.
- Delete or mark this handoff `done` after the PR merges so a cold agent does not re-propose it.
