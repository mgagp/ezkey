# Ezkey Mobile — Agent Notes

For agents working in `ezkey_mobile/`.

## Purpose

React Native companion app for Ezkey MFA. Core flows only: enroll, list enrollments, approve or deny authentication requests. User-facing content supports English and French, with English as the default runtime language and a manual language switch in Settings.

## Documentation Routing

- Start with `docs/README.md` for the current mobile documentation corpus.
- Treat `docs/MOBILE_API_MAPPINGS.md`, `docs/MOBILE_FUNCTIONAL_FLOWS.md`, `docs/MOBILE_DATA_MODEL.md`, `docs/MOBILE_SCREENS_AND_WIREFLOWS.md`, `docs/MOBILE_STACK_AND_ARCHITECTURE.md`, and `docs/MOBILE_POSITIONING.md` as the primary conceptual set.
- For release and publishing work, read `docs/MOBILE_RELEASE_SIGNING.md`, `docs/MOBILE_PLAY_PUBLISHING.md`, `docs/MOBILE_PLAY_RELEASE_READINESS_AUDIT.md`, and `docs/MOBILE_RELEASE_DECISION_MEMO.md` before proposing release conclusions.
- Treat `package.json`, Android Gradle files, and manifests as the source of truth for the current workspace stack and release configuration.

## Directives

- Keep the app simple. Avoid feature creep, decorative UI, and extra screens.
- Security first: proof tokens stay in memory or secure storage only.
- Keep the pull model: no background polling for auth attempts.
- Do not reintroduce integration logos or related fields.
- When discussing stack modernization, treat React, React Native, `react-native-vision-camera`, and camera-adjacent dependencies as a coupled compatibility slice rather than as independent bumps.
- Treat the current mobile product and security posture as **Android-first**. iOS is a later planned milestone, not a short-term parity target, so do not report missing iOS parity as a current defect unless documentation overclaims it.

## Mobile test operator segmentation

- For clean-start mobile test sessions, treat `admin.docker` as a human-reserved recovery account.
- Prefer a dedicated global admin for agent-driven mobile testing (`mobile_tester` now, target naming `admin.mobile`).
- Agent automation should operate on the dedicated mobile test admin and avoid consuming `admin.docker` recovery codes unless explicitly requested by the maintainer.
- Validate baseline before any churn run:
  - dedicated test admin exists and is `GLOBAL_ADMIN`, active, lifecycle `ACTIVE`;
  - linked enrollment is `VERIFIED` with `device_public_key` present;
  - Demo Device has a persisted enrollment JSON entry under `data/enrollments/` for that enrollment id.
- Do not hardcode one-shot credentials in docs, scripts, or committed files. Generate fresh bind material per run.

## Contract-First Rules

- Orval is pinned at **8.20.0** (exact). Config uses verb-aware defaults only (`query: { version: 5 }` —
  no global `useQuery` / `useMutation`). Same Option B as Admin UI `TB-2026-05-28` checkpoints 8.10 / 8.11.
- Never hand-edit `openapi-spec.json` in `ezkey_mobile/`.
- Refresh specs only through the root script `scripts/update-specs.sh` (Git Bash) after a human has started a clean Docker stack.
- After refreshing the spec, run `yarn generate:api`.
- Treat `app/services/api/generated/auth-api/model/` as the source of truth for Auth API DTOs.
- Keep `app/services/api/types.ts` thin: local mobile domain types and wrapper input shapes are fine, but no second hand-maintained copy of the Auth API contract.

## Security Rules

- Never break Auth API contracts without backend coordination.
- Generate device proof tokens only via `app/utils/generateProofToken.ts`.
- Keep EC P-256 key handling on the native keystore path.
- For Android wording, prefer `Android Keystore` and `StrongBox when available`.
- Do not revive deleted historical analysis notes when the living corpus already states the current rule.

## Android debug build (agents — read first)

**Do not improvise `JAVA_HOME` or bare `./gradlew` on Windows.** The maintainer PATH often exposes **JDK 25** (`C:\Tools\jdk-25…`), which breaks React Native 0.85 Android (`Unsupported class file major version 69`, `com.facebook.react.settings` plugin errors). Android Studio JBR is also **not** always at `C:\Program Files\Android\Android Studio\jbr` (this workstation uses `Android Studio1\jbr`).

### Mandatory agent workflow

1. **Git Bash** from `ezkey_mobile/` (not bare PowerShell for Gradle).
2. **Device before Gradle**: `adb devices -l` — if empty, stop; do not start a multi-minute build (wireless adb often drops mid-build).
3. **Canonical install** (JDK probe + clean + install + launch):

   ```bash
   cd ezkey_mobile
   corepack enable
   ./scripts/build-install-debug-clean.sh
   ```

   Or: `yarn android:install:debug:clean` (same script).

4. **JDK resolution** is centralized in `scripts/resolve-android-jdk.sh` (Android Studio / Studio1 JBR, `C:\Tools\jdk17`, Microsoft JDK 17, macOS `java_home -v 17`). Override only with `EZKEY_ANDROID_JAVA_HOME` if needed.
5. After dependency changes: `corepack yarn install --immutable` then the script above.
6. **Fast reinstall** when APK already built and device reconnected: `./scripts/build-install-debug-clean.sh --skip-clean`.

### Do not

- Set `JAVA_HOME` to the repo JDK 25 or guess a single Android Studio path without probing.
- Run `unset JAVA_HOME` and hope Gradle picks a good JDK (PATH may still be 25).
- Use `yarn android:install:debug` alone unless `resolve-android-jdk.sh` is sourced in the same shell session.

### Related scripts

| Script | Use |
|--------|-----|
| `scripts/build-install-debug-clean.sh` | **Default** — clean debug build + install on device |
| `scripts/build-install-release-clean.sh` | Clean **release** build + install (offline-capable; no Metro) |
| `scripts/resolve-android-jdk.sh` | Source to export `JAVA_HOME` for any Gradle command |
| `scripts/android-with-jdk17.sh` | `react-native run-android` with correct JDK |
| `scripts/install-debug-after-uninstall.sh` | Uninstall + `installDebug` (signature mismatch) |

Human-oriented troubleshooting: `README.md` § Android build troubleshooting.

## Build Reset Heuristic

- If Android starts crashing in React Native infrastructure code after a branch switch, dependency change, or reinstall, suspect stale build artifacts before investigating business logic.
- First-line reset sequence:
  - `corepack yarn install --immutable`
  - `./scripts/build-install-debug-clean.sh` (preferred) or `./scripts/build-install-debug-clean.sh --skip-clean` if APK is fresh

## Useful Commands

```bash
corepack yarn install --immutable
corepack yarn generate:api
corepack yarn validate:ci
./scripts/build-install-debug-clean.sh
yarn doctor:curated
```

Real-device Maestro pilot (`TB-2026-0002`): see [`maestro/README.md`](maestro/README.md) and `scripts/run-real-device-pilot-maestro.sh`.

## Mobile doctor-curated pass

- Keyword for humans and agents: **`mobile-doctor-curated`**.
- Purpose: a **punctual** React Native + Kotlin hygiene pass (react-doctor + Semgrep + Detekt →
  curated P1/P2/P3 shortlist). **Not** a CI gate and **not** a zero-warning campaign.
- Default commands from `ezkey_mobile/`:

```bash
yarn doctor:curated
./scripts/mobile-doctor-curated.sh
```

  Optional: `--skip-react-doctor`, `--skip-semgrep`, `--skip-detekt`, `--curate-only`.
- Outputs under `logs/mobile-doctor/` (gitignored via root `logs/`):
  - `mobile-doctor.curated.md` — human-readable shortlist + planning contract
  - `mobile-doctor.curated.json` — machine-readable summary
  - `raw/` — react-doctor / Semgrep / Detekt inputs
- Config: `config/mobile-doctor/suppressions.json` (reasons required).
- Campaign decision notes (HITL): `product-docs/global/hygiene/mobile-doctor/` (copy `TEMPLATE.md`).
- Trace (hygiene, not program): dedicated branch + PR; do **not** invent `I-*` / `TB-*` per finding.
- Authority: `product-docs/global/mobile-doctor-curated-evaluation-2026-07-11.md`,
  `I-2026-07-11-mobile-doctor-curated-hygiene`, `TB-2026-07-11-mobile-doctor-curated-mvp`.

### HITL contract (mandatory for cold agents)

When the operator asks for a **`mobile-doctor-curated`** improvement pass:

1. Run the script; read `logs/mobile-doctor/mobile-doctor.curated.md`.
2. Propose a **small prioritized lot** (usually 3–6 items), not a zero-warning campaign.
3. **Before any code change — interactive HITL loop:** iterate **one finding at a time**; wait for
   Go / No-Go / suppress / skip on that item before the next. Do not replace dialogue with one dense
   options matrix.
4. **Fuzzy signal rule:** if the finding cannot be tied clearly to source, **skip**.
5. **If it ain't broken, don't fix it** — especially in crypto / keystore / proof-token code.
6. Record decisions in a dated campaign note under `product-docs/global/hygiene/mobile-doctor/`.
7. Only then implement accepted fixes on a **dedicated hygiene branch + PR**; put the briefing in
   the PR body. Modest low-signal allotment after high-signal items is allowed when the operator agrees.