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
- Treat the current mobile product and security posture as **Android-first**. iOS is a later planned phase, not a short-term parity target, so do not report missing iOS parity as a current defect unless documentation overclaims it.

## Contract-First Rules

- Never hand-edit `openapi-spec.json` in `ezkey_mobile/`.
- Refresh specs only through the root scripts `scripts/update-specs.sh` or `scripts/update-specs.bat` after a human has started a clean Docker stack.
- After refreshing the spec, run `yarn generate:api`.
- Treat `app/services/api/generated/auth-api/model/` as the source of truth for Auth API DTOs.
- Keep `app/services/api/types.ts` thin: local mobile domain types and wrapper input shapes are fine, but no second hand-maintained copy of the Auth API contract.

## Security Rules

- Never break Auth API contracts without backend coordination.
- Generate device proof tokens only via `app/utils/generateProofToken.ts`.
- Keep EC P-256 key handling on the native keystore path.
- For Android wording, prefer `Android Keystore` and `StrongBox when available`.
- Do not revive deleted historical analysis notes when the living corpus already states the current rule.

## Build Reset Heuristic

- If Android starts crashing in React Native infrastructure code after a branch switch, dependency change, or reinstall, suspect stale build artifacts before investigating business logic.
- First-line reset sequence:
  - `yarn install --immutable`
  - from `android/`: `./gradlew clean` or `gradlew.bat clean`
  - rebuild/install the debug app

## Useful Commands

```bash
yarn install --immutable
yarn generate:api
yarn lint
yarn typecheck
yarn test
yarn android:install:debug
```

Real-device Maestro pilot (`TB-2026-0002`): see [`maestro/README.md`](maestro/README.md) and `scripts/run-real-device-pilot-maestro.sh`.
