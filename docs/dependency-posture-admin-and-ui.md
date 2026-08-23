# Dependency posture — Admin API and Admin UI

This note captures the **baseline inventory**, **2026-05 hygiene pass**, and **follow-up backlog** for keeping the Admin backend and Admin UI dependencies current without unnecessary churn.

## Version anchors (do not desync casually)

| Layer | Source of truth | Current anchor |
| --- | --- | --- |
| Spring Boot (Java reactor) | `spring-boot.version` in root [`pom.xml`](../pom.xml) | 4.1.1 |
| SpringDoc OpenAPI | `springdoc.version` in root `pom.xml` | 3.0.3 |
| MapStruct | `mapstruct.version` in root `pom.xml` | 1.6.3 |
| Admin UI toolchain | [`ezkey-admin-ui/package.json`](../ezkey-admin-ui/package.json) | React 19, Vite 7, TypeScript ~5.9, Tailwind 4 |

Bumped third-party libraries under the parent [`dependencyManagement`](../pom.xml) (ZXing, Bucket4j, Caffeine, Tink, ShedLock, ipaddress) should move together when security or compatibility requires it.

## Admin API / shared core — direct dependency surfaces

- **Admin API** [`ezkey-admin-api/pom.xml`](../ezkey-admin-api/pom.xml): `ezkey-core`, `ezkey-core-security`, Spring Web MVC, validation, security, actuator, SpringDoc, Bucket4j, Caffeine, ZXing, MapStruct.
- **Core** [`ezkey-core/pom.xml`](../ezkey-core/pom.xml): JPA, security, PostgreSQL, Tink, ZXing, SpringDoc, Commons Codec, IPAddress (`com.github.seancfoley:ipaddress`), ShedLock, Micrometer. Versions for Tink, ShedLock, and ipaddress follow the parent, same as ZXing / Bucket4j.

Test-only starters **`spring-boot-starter-security-test`** and **`spring-boot-starter-data-jpa-test`** are managed by the **Spring Boot BOM** (no per-module `4.0.6` pin). **`spring-boot-starter-flyway`** in [`ezkey-migration/pom.xml`](../ezkey-migration/pom.xml) likewise follows the BOM.

## Admin UI — npm surface

- **Runtime**: React 19, React Router 7, TanStack Query 5, react-hook-form, Zod, i18next, qrcode, lucide-react, etc.
- **Build / quality**: Vite 7, TypeScript, ESLint 9, Vitest 3, Playwright, Orval, Tailwind 4.

Lockfile: committed [`ezkey-admin-ui/package-lock.json`](../ezkey-admin-ui/package-lock.json); installs should use `npm ci` where possible.

## Automated watch

- **[`.github/dependabot.yml`](../.github/dependabot.yml)** schedules weekly updates for Maven (repo root), npm (`ezkey-admin-ui`, `ezkey-sdk/javascript`), GitHub Actions, and pip (`ezkey-cli-python`).
- **Weekly triage:** use the **`dependabot-curated`** hygiene lane (skill
  [`.cursor/skills/dependabot-curated/SKILL.md`](../.cursor/skills/dependabot-curated/SKILL.md),
  campaign notes under [`product-docs/global/hygiene/dependabot/`](../product-docs/global/hygiene/dependabot/))
  to batch PRs by risk tier with human–AI HITL — not silent auto-merge.
- **OWASP Dependency-Check** is **not** wired as a Maven profile in this repo; run ad hoc or add a CI job if you want continuous CVE reports. Dependency hygiene is part of continuous operational discipline — see [`../product-docs/global/normative-posture.md`](../product-docs/global/normative-posture.md).

## 2026-05 pass — what was done

1. **Admin UI**: `npm audit fix` — resolved transitive advisories (e.g. Vite, postcss, picomatch, yaml, brace-expansion, flatted). **`npm audit`** reports **0** vulnerabilities afterward.
2. **Admin UI**: [`package.json`](../ezkey-admin-ui/package.json) **Vite** range updated to `^7.3.2` to match the patched lockfile resolution.
3. **Maven**: Removed redundant explicit `4.0.6` versions on Boot-managed test/Flyway dependencies so **one bump** of `spring-boot.version` propagates consistently.

## Follow-up backlog (prioritize in a later pass)

| Priority | Item | Rationale |
| --- | --- | --- |
| Medium | Grouped **patch/minor** npm updates within current majors (e.g. `@tanstack/react-query`, `react-router-dom`, `zod`, Tailwind patch) | Reduces drift; test `npm run lint`, `npm run build`, optional Playwright if auth/routing touched |
| Medium | Evaluate later **Spring Boot 4.1.x** patches when available | Single property change in root `pom.xml`; keep Tomcat / Logback / Jackson overrides; full [`scripts/build.sh`](../scripts/build.sh) baseline |
| Lower | **Major** bumps (e.g. Vitest 4, Vite 8, ESLint 10) | Higher regression risk; schedule explicitly |
| Lower | Add optional **Maven dependency-scan** job (OWASP or `mvnd`) in CI | Operational quick win from SOC2 guidance |
| Lower | Periodically refresh **Tink**, **ShedLock**, **ipaddress** via the parent properties (`tink.version`, `shedlock.version`, `ipaddress.version`) when CVEs or bugfix releases appear | Each needs a short test pass around crypto / scheduling |
| Lower | **Mockito / ByteBuddy** “dynamic Java agent” and inline-mock-maker messages on JDK 25+ tests | Harmless today; future JDK may require explicit Mockito agent config (see Mockito docs) |

## Validation commands

**Java (from repo root, Git Bash — Windows, Linux, or macOS):**

```bash
./scripts/build.sh
```

Single portable entrypoint; auto-detects canonical JDK 25 on the maintainer Windows workstation
when `JAVA_HOME` is unset (see [`maven-build.mdc`](../.cursor/rules/maven-build.mdc)).

**Admin UI:**

```bash
cd ezkey-admin-ui
npm ci
npm audit
npm run lint
npm run build
```

For production-like UI bundle checks, see [`ezkey-admin-ui/AGENTS.md`](../ezkey-admin-ui/AGENTS.md) (`build:cloudflare:verify`, demo leakage asserts).

## Maven build warnings observed (JDK 25 baseline)

When running [`scripts/build.sh`](../scripts/build.sh) with **`JAVA_HOME` set to JDK 25** (see [`jdk-25-windows-workstation.mdc`](../.cursor/rules/jdk-25-windows-workstation.mdc)), the reactor completes successfully. Remaining noise to track outside this hygiene pass:

| Warning | Notes |
| --- | --- |
| `version` contains an expression | CI-friendly `${revision}${changelist}` on the parent POM; intentional. |
| Spotless / `sun.misc.Unsafe` | Third-party tooling on JDK 25; monitor Spotless upgrades. |

If Spotless fails with `google-java-format` / `JCTree$JCAnyPattern`, the build is almost certainly running on a **JDK older than 25** — align the runtime with the repo rule.

## Admin UI build notes

Vite may emit a **chunk size** hint for the main bundle; that is a performance suggestion, not a security finding.
