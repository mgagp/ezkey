# Java GraalVM / Native Compilation Assessment (2026-07)

## Mandate

- **Surface:** Java backend GraalVM native-image attempt — Auth API, Integration API, parent/module
  Maven profiles, Docker native stack, scripts, and living documentation that still implies native
  support. Admin API native leftovers where present.
- **Attention axes:** honest inventory of what still ships or documents a native path; classify
  live vs orphan vs historical; prefer a clean slate (remove dead surface) over “keep for someday.”
- **Non-goals:** reopening native as an active initiative; React Native / mobile Keystore modules;
  HA compose; inventing `I-*` / `TB-*` per finding; zero-warning doctor campaigns; implementing
  remediations inside the assessment session until HITL decides per finding.

## Scope and method

- Static white-box inventory (grep + file presence + status docs).
- Cross-check against the April 2026 outcome note
  (`docs/NATIVE_INITIATIVE_STATUS_2026-04.md`): spike closed; Auth and Integration native images
  built but **not** operationally viable at runtime; JVM-only recommendation for all Boot APIs.
- Operator posture for this pass: native compilation is a **non-objective** for now; residual
  artifacts are treated as hygiene noise. A future restart should not inherit stale hints, POM
  profiles, or “how to run `--native`” docs.

## Honest verdict (one paragraph)

The native initiative left a **large residual footprint** that is mostly **dead or misleading**.
RuntimeHints Java classes still sit on the Auth and Integration classpaths; Maven `-Pnative`
profiles remain in parent + two modules; Docker/`clean-start` still advertise `--native`; and a
dozen living `docs/NATIVE_*.md` files plus module `NATIVE_BUILD.md` guides read as if native were
a supported path. The April 2026 status note already recorded runtime failure (Hibernate /
Hikari). Keeping this material does not help day-to-day JVM delivery and would mislead a cold
reader who greps for “native.” Prefer delete + short “not pursued” pointer over archival sprawl
in the living tree.

## Surface map

### Live code / config (still in tree)

| Area | Paths | Notes |
| --- | --- | --- |
| Auth AOT hints | `ezkey-auth-api/.../AuthNativeConfiguration.java` (~657 LOC) | `@Configuration` + `@ImportRuntimeHints` — always on component scan |
| Integration AOT hints | `ezkey-integration-api/.../IntegrationNativeConfiguration.java` (~361 LOC) | Same pattern |
| Graal flags | `META-INF/native-image/.../native-image.properties` (auth + integration) | Auth also has `*.json.disabled` stubs |
| Spring native profile props | `application-native.properties` (auth + **admin**) | Admin has props without `AdminNativeConfiguration` (already removed) |
| Maven | Parent `pom.xml` profile `native`; auth + integration module `-Pnative` blocks | `native-maven-plugin` skip=true; buildpack `BP_NATIVE_IMAGE` |
| Docker | `docker/docker-compose.native.yml`, `docker-compose.native.docker-dev.yml` | Separate project name `ezkey-native` |
| Ops scripts | `docker/start.sh` / `start.ps1` `--native`; `ezkey-tests/clean-start.sh` / `.ps1`; `generate-encryption-keys.* --native` | Still wires native compose + volume names |
| Build/debug | `scripts/build-native-aot.sh`, `test-native-build.sh`, `measure-native-memory.*`, `debug-aot-*.sh` | Spike tooling |

### Living documentation (implies or teaches native)

- `docs/NATIVE_*.md` — 13 files (strategy, quickstart, optimization, challenges, Hibernate loggers, …)
- `docs/NATIVE_INITIATIVE_STATUS_2026-04.md` — **useful outcome record**; keep content or fold into a
  short non-goal note, then remove the how-to corpus
- `ezkey-admin-api/NATIVE_BUILD.md`, `ezkey-auth-api/NATIVE_BUILD.md` — stale how-tos (Admin doc
  still references deleted `AdminNativeConfiguration`)
- Scattered: `docker/README.md`, `scripts/README.md`, `docs/features/SECURITY_MULTI_TENANT.md`
  (Graal section), license notes mentioning GraalVM “if used”, module READMEs

### Explicitly out of this mandate (false positives)

- `ezkey_mobile/` React Native / `NATIVE_MODULES.md` / `nativeCrypto.ts` — mobile native bridge,
  not GraalVM
- Admin UI “native HTML dialog” backlog idea — unrelated
- Tink “native keyset blob” wording — crypto envelope design, not GraalVM
- Archived plans under `.cursor/plans/archived/` and `docs/plan/archives/` — historical; do not
  rewrite; optional leave-as-is

## Findings register (HITL lot)

| ID | Title | Severity | Confidence | Disposition |
| --- | --- | --- | --- | --- |
| NAT-001 | Auth + Integration Java RuntimeHints and `META-INF/native-image` on classpath | P1 | High | Closed (removed 2026-07-26) |
| NAT-002 | Maven `-Pnative` profiles (parent + auth + integration) | P1 | High | Open |
| NAT-003 | Admin API orphaned native remnants (`application-native.properties`, `NATIVE_BUILD.md`) | P2 | High | Open |
| NAT-004 | Docker / clean-start / keygen `--native` ops surface | P1 | High | Open |
| NAT-005 | Native/AOT build and debug scripts under `scripts/` | P2 | High | Open |
| NAT-006 | Living documentation corpus and scattered native how-to references | P2 | High | Open |

## Evidence (brief)

### NAT-001

- `AuthNativeConfiguration` / `IntegrationNativeConfiguration` are Spring `@Configuration` classes
  with large hand-maintained reflection/resource/serialization hint lists aimed at GraalVM.
- They are **not** behind a profile gate; they load on every JVM boot of those apps.
- Companion `native-image.properties` and disabled JSON configs exist only for native-image builds.
- April 2026: images could build; runtime still failed — hints did not deliver an operable stack.

### NAT-002

- Parent profile sets Paketo builder + `BP_NATIVE_IMAGE` + `native.maven.plugin.version` (1.1.5).
- Auth and Integration duplicate full `-Pnative` plugin wiring (`process-aot`,
  `native-maven-plugin` with `skip=true`).
- Default `./scripts/build.sh` does not activate `-Pnative`, so profiles are unused noise unless
  someone deliberately rebuilds native images.

### NAT-003

- `AdminNativeConfiguration.java` is **already absent** from
  `ezkey-admin-api/.../config/`.
- `application-native.properties` (~148 lines) and `NATIVE_BUILD.md` remain and still describe the
  deleted Java config — pure orphan documentation/config.

### NAT-004

- `docker-compose.native.yml` defines a full parallel stack (`ezkey-*-native` containers/volumes).
- `clean-start.sh --native` and `docker/start.sh --native` still present as first-class modes.
- Encryption key script has a dedicated native volume name path.
- Misleading for operators: flag suggests a supported alternate runtime.

### NAT-005

- `build-native-aot.sh`, `test-native-build.sh`, `measure-native-memory.sh` / `.ps1`,
  `debug-aot-classpath.sh`, `debug-aot-runtime-classpath.sh`, `debug-aot-execution-order.sh`.
- Documented from `scripts/README.md` and quickstarts; only useful if native is revived.

### NAT-006

- Thirteen root `docs/NATIVE_*.md` files plus two module `NATIVE_BUILD.md` files.
- `NATIVE_INITIATIVE_STATUS_2026-04.md` is the only high-signal living summary of failure/outcome.
- Older guides contradict that outcome (quickstart, optimization plans, Admin Lambda native story).
- Editorial pass should delete or demote how-tos; retain a **short** non-goal pointer (possibly
  by rewriting the April status into a one-pager “not pursued”).

## Risk posture for remediation

- Removals are **hygiene / fail-open for product**: default path is already JVM; deleting native
  paths reduces false support claims.
- Must not break default `clean-start` / Docker JVM stack when stripping `--native` branches.
- License docs that say “GraalVM if used” can stay or be lightly trimmed — low priority.
- Do **not** invent a methodology program; one hygiene branch/PR (or small PR series) after HITL.

## Recommended remediation shape (after HITL)

1. Delete live Java/config/META-INF (NAT-001) and Admin orphans (NAT-003).
2. Remove Maven native profiles (NAT-002).
3. Remove Docker native compose + `--native` CLI branches (NAT-004) and scripts (NAT-005).
4. Editorial: delete living how-to corpus; keep one short non-goal / historical outcome note
   (NAT-006).
5. Validate with `./scripts/build.sh` and a standard (non-native) clean-start smoke.

## Out of scope reminders

- React Native mobile native modules
- Re-attempting GraalVM / Spring AOT in this pass
- Rewriting archived Cursor plans
