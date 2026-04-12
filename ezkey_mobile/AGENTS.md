# Ezkey Mobile — Agent Notes

For agents working in `ezkey_mobile/`.

## Purpose

React Native companion app for Ezkey MFA. Core flows only: enroll, list enrollments, approve or deny authentication requests. All user-facing content must stay in English.

## Directives

- Keep the app simple. Avoid feature creep, decorative UI, and extra screens.
- Security first: proof tokens stay in memory or secure storage only.
- Keep the pull model: no background polling for auth attempts.
- Do not reintroduce integration logos or related fields.

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
