# Ezkey Mobile — Agent Notes

For agents working in `ezkey_mobile/`.

## Purpose

React Native companion app for Ezkey MFA. Core flows only: enroll, list enrollments, approve or deny authentication requests. User-facing content supports English and French, with English as the default runtime language and a manual language switch in Settings.

## Documentation Routing

- Start with `docs/README.md` for the current mobile documentation corpus.
- Test layer contract (Jest vs JVM vs instrumented vs Maestro): [`docs/MOBILE_TEST_STRATEGY.md`](docs/MOBILE_TEST_STRATEGY.md).
- Treat `docs/MOBILE_API_MAPPINGS.md`, `docs/MOBILE_FUNCTIONAL_FLOWS.md`, `docs/MOBILE_DATA_MODEL.md`, `docs/MOBILE_SCREENS_AND_WIREFLOWS.md`, `docs/MOBILE_STACK_AND_ARCHITECTURE.md`, and `docs/MOBILE_POSITIONING.md` as the primary conceptual set.
- Wire protocol for third-party or alternative mobile clients is the repository-level companion [`docs/MOBILE_DEVELOPER_GUIDE.md`](../docs/MOBILE_DEVELOPER_GUIDE.md) — not the RN screen corpus.
- For release and publishing work, read `docs/MOBILE_RELEASE_SIGNING.md`, `docs/MOBILE_PLAY_PUBLISHING.md`, `docs/MOBILE_PLAY_RELEASE_READINESS_AUDIT.md`, and `docs/MOBILE_RELEASE_DECISION_MEMO.md` before proposing release conclusions.
- Treat `package.json`, Android Gradle files, and manifests as the source of truth for the current workspace stack and release configuration.

## Directives

- Keep the app simple. Avoid feature creep, decorative UI, and extra screens.
- Production IA: Home list + primary add + Settings hub (What's new, About, Language, Danger Zone,
  Licenses). Release announcements are a top-of-home card plus Settings → What's new — not a
  floating bottom-left badge. About stays static identity (version, site, support).
  Do not reintroduce a Diagnostics screen in production chrome. Delete-one and clear-all live only
  in Danger Zone. Card title fallback: `enrollmentName` → `deviceLabel` → `integrationName`. After
  a successful delete-one, `navigation.goBack()` — exceptional cleanup, not a batch-delete loop.
- Nested `installation` on each stored enrollment is the local trust zone. Identity is the
  normalized `authUrl` (`installation.id`). Do not re-flatten `installation*` onto enrollment,
  and do not add a second persisted installation collection. Enrolled branding
  (`name` / `description` / `aboutUrl`) comes from signed
  `POST /api/v1/enrollments/instance-info` and is display-only — never a cryptographic trust
  anchor or identity. Do not call or fall back to public `GET /api/v1/public/instance-info` on
  enrolled paths. Canon: [`docs/MOBILE_DATA_MODEL.md`](docs/MOBILE_DATA_MODEL.md).
- Security first: proof tokens stay in memory or secure storage only.
- Keep the pull model: no background polling for auth attempts.
- Do not reintroduce integration logos or related fields.
- When discussing stack modernization, treat React, React Native, `react-native-vision-camera`, and camera-adjacent dependencies as a coupled compatibility slice rather than as independent bumps.
- Do **not** add `react-native-worklets/plugin` to `babel.config.js` while the project is on **Babel 8**: the Worklets plugin still pulls Babel-7-era presets and fails the JS bundle (`Requires Babel "^7.0.0-0", but was loaded with "8.0.1"`). Revisit when Software Mansion ships Babel 8–compatible Worklets, or when a real frame-processor/`'worklet'` path needs that transform. The enrollment QR path (`useBarcodeScannerOutput` / ML Kit) does not require the plugin today.
- For VisionCamera **5.1+**, resolve a concrete device with `useCameraDevice('back')` before mounting `<Camera />`. Passing the position string `device="back"` can throw while the device list is still empty (`This device does not have any "back" Cameras!`) and trip `AppErrorBoundary`.
- Treat the current mobile product and security posture as **Android-first**. iOS is a later planned milestone, not a short-term parity target, so do not report missing iOS parity as a current defect unless documentation overclaims it.
- **Android platform support floor:** Android **12+** (`minSdk` **31**) in product policy and
  Gradle (`android/build.gradle`). Canon: `docs/MOBILE_ANDROID_PLATFORM_SUPPORT.md`.
  **Annual review** of that doc is due **2027-07** (habit title: Platform support floor — annual
  review). Do not “fix” MOB-012 by raising `minSdk` to 35; that finding is a runtime Keystore
  flag gate.

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

- Orval is pinned at **8.33.0** (exact). Config uses verb-aware defaults only (`query: { version: 5 }` —
  no global `useQuery` / `useMutation`). Same Option B as Admin UI `TB-2026-05-28` checkpoints 8.10 / 8.11.
  Treat later **8.34+** minors as a new validation ladder (regenerate + typecheck/test/lint).
  Do not enable `useDatesTransform` unless Admin UI does — it is opt-in date deserialization.
  Orval 8.33 types `getHeaders` via `RequestInit['headers']` (RN types already cover this); the
  former `orval-dom-shim.d.ts` (`HeadersInit`) is no longer required.
- Never hand-edit `openapi-spec.json` in `ezkey_mobile/`.
- Refresh specs only through the root script `scripts/update-specs.sh` (Git Bash) after a human has started a clean Docker stack.
- After refreshing the spec, run `yarn generate:api`.
- Treat `app/services/api/generated/auth-api/model/` as the source of truth for Auth API DTOs.
- Keep `app/services/api/types.ts` thin: local mobile domain types and wrapper input shapes are fine, but no second hand-maintained copy of the Auth API contract.
- Verify `challengeResponse` is required. The UI wrapper may keep it as `string`; `enrollmentsApi.verify`
  serializes it as a JSON number. Do not make it optional.

## Security Rules

- Never break Auth API contracts without backend coordination.
- Add a mobile protocol field only if removing it breaks bind, verify, pending, respond, or
  signature verification. Do not reintroduce `authAttemptChallengeRequiredByPolicy` (rolled back:
  it disclosed enrollment policy and was not required for protocol execution).
- After enrollment bind, require `integrationKeyAlgorithm === "ed25519"` via
  `integrationKeyAlgorithmBindError` (`app/utils/integrationKeyAlgorithm.ts`). Abort enrollment on
  mismatch (fail closed). Do not assume Ed25519 from `integrationPublicKey` alone.
- Generate device proof tokens only via `app/utils/generateProofToken.ts` (native CSPRNG in
  `EzkeyCryptoModule`). Do not mint `deviceProofToken` with `Date.now()` or
  `react-native-get-random-values`.
- Keep EC P-256 key handling on the native keystore path.
- For Android wording, prefer `Android Keystore` and `StrongBox when available`.
- Do not revive deleted historical analysis notes when the living corpus already states the current rule.
- **Auth API base URL:** Prefer per-enrollment `authUrl` from the enrollment QR (server
  `ezkey.qr.auth-base-url`). Fall back to configured `EZKEY_API_BASE_URL` only when QR omits
  `authUrl`. Do **not** hard-code tunnel hostnames (e.g. ngrok) in committed env defaults.

## Android debug build (agents — read first)

**Do not improvise `JAVA_HOME` or bare `./gradlew` on Windows.** The maintainer PATH often exposes **JDK 25** (`C:\Tools\jdk-25…`), which breaks React Native 0.86 Android (`Unsupported class file major version 69`, `com.facebook.react.settings` plugin errors). Android Studio JBR is also **not** always at `C:\Program Files\Android\Android Studio\jbr` (this workstation uses `Android Studio1\jbr`).

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
| `scripts/run-android-instrumented-crypto-tests.sh` | MOB-006 — `EzkeyCryptoModule` Keystore `androidTest` (emulator OK; not StrongBox CI) |

Human-oriented troubleshooting: `README.md` § Android build troubleshooting.

### Native Keystore instrumentation (MOB-006)

- **Emulator / any adb device** (JDK 17 resolver, Git Bash from `ezkey_mobile/`):

  ```bash
  adb devices -l
  yarn android:test:instrumented:crypto
  # or: ./scripts/run-android-instrumented-crypto-tests.sh
  ```

- Covers enrollment key create → `getPublicKey` → `sign` round-trip, `deleteKeyPair`,
  `sealSecret` / `unsealSecret` via the module, and
  `getEnrollmentPrivateKeyStorageTier` ∈ `{NONE,STANDARD,STRONG}`.
- **Do not** treat a green emulator run as StrongBox proof. Physical StrongBox evidence:
  [`docs/MOBILE_STRONGBOX_MANUAL_CHECKLIST.md`](docs/MOBILE_STRONGBOX_MANUAL_CHECKLIST.md).

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
yarn android:test:instrumented:crypto
yarn doctor:curated
```

Real-device Maestro campaign (`TB-2026-0002`): canonical runner
`./ezkey-tests/scripts/run-mobile-real-device.sh` (JUnit API + Maestro UI + compact RCA). Single-flow
inventory still uses [`maestro/README.md`](maestro/README.md) and `scripts/run-real-device-pilot-maestro.sh`.
On failure, read `SESSION-table.md` then `iterations/<n>/rca.md` before full Maestro logs. F2b camera/QR
is out of scope. Campaigns keep the screen awake for the run (see `maestro/README.md` § Screen stay-awake).
StrongBox physical checklist (MOB-006): [`docs/MOBILE_STRONGBOX_MANUAL_CHECKLIST.md`](docs/MOBILE_STRONGBOX_MANUAL_CHECKLIST.md).

## Production-clean test automation

- Canonical contract: [`docs/MOBILE_TEST_AUTOMATION_PRODUCTION_CLEAN.md`](docs/MOBILE_TEST_AUTOMATION_PRODUCTION_CLEAN.md).
- **Release / production-intent builds** must not activate F2a enrollment seed bypass, respond-path
  flow trace, or pending-auth debug panel — even if a local `.env` still has those flags set for
  harness work.
- F2a availability = native **debug** build type (`BuildConfig.DEBUG`) + explicit env enable +
  ack `F2A_TEST_ONLY`. Do **not** equate this with React Native `__DEV__`.
- Release install script runs `scripts/assert-release-production-clean-env.sh` before Gradle.
- Do not reintroduce `RespondMitmLabControl` or in-app respond MITM tamper into a release/Play JS
  bundle (removed 2026-04; Play malicious-behavior risk).
- When `mobile-doctor-curated` or a skeptical review flags harness/bypass code: read the contract
  first; missing mechanical gates are P1; gated intentional harness code is not.

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