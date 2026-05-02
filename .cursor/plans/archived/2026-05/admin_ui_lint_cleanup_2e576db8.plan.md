---
name: Admin UI lint cleanup
overview: Investigate and clean up the existing Admin UI ESLint failures by fixing the underlying React lint issues rather than suppressing them, then add a small diagnostic path so future lint failures are easier to triage.
todos:
  - id: fix-static-components
    content: Move local render helper components out of render functions in the pages producing most static-components diagnostics.
    status: completed
  - id: fix-hook-context-rules
    content: Refactor context and hook state patterns that violate React Hooks lint rules.
    status: completed
  - id: fix-refresh-exports
    content: Split Fast Refresh-incompatible mixed exports from component-bearing modules.
    status: completed
  - id: add-lint-diagnostics
    content: Add or document a reliable ESLint diagnostic command for compact future triage.
    status: completed
  - id: validate-admin-ui
    content: Run Admin UI build, lint, and file diagnostics after implementation.
    status: completed
isProject: false
---

> **Plan status:** **Completed** — Admin UI lint now passes cleanly, the dominant React lint patterns were fixed at their source, and `lint:diagnostics` was added for future triage.

# Admin UI Lint Cleanup

## Current Findings

- `ezkey-admin-ui` runs lint as `eslint .` from [`ezkey-admin-ui/package.json`](ezkey-admin-ui/package.json).
- `npx eslint . --format stylish` currently reports `104` errors and `6` warnings.
- The dominant failure is `react-hooks/static-components`: local helper components are declared inside render functions and then used repeatedly. This creates many repeated diagnostics from a few root causes.
- The recent audit timestamp diff in [`ezkey-admin-ui/src/pages/audit-logs.tsx`](ezkey-admin-ui/src/pages/audit-logs.tsx) only changed date rendering and labels. It did not introduce the main lint pattern, but that file already contains one of the affected local helpers.

## Fix Strategy

1. Add or reuse stable top-level helper components for repeated detail rows, starting with the `InfoRow` pattern in [`ezkey-admin-ui/src/pages/audit-logs.tsx`](ezkey-admin-ui/src/pages/audit-logs.tsx), [`ezkey-admin-ui/src/pages/admins.tsx`](ezkey-admin-ui/src/pages/admins.tsx), [`ezkey-admin-ui/src/pages/auth-attempts.tsx`](ezkey-admin-ui/src/pages/auth-attempts.tsx), and [`ezkey-admin-ui/src/pages/encryption-keys.tsx`](ezkey-admin-ui/src/pages/encryption-keys.tsx). Prefer a tiny shared component only if it removes real duplication without forcing awkward page-specific styling.
2. Fix the shared hook/context rule violations in [`ezkey-admin-ui/src/context/display-timezone-context.tsx`](ezkey-admin-ui/src/context/display-timezone-context.tsx), [`ezkey-admin-ui/src/context/help-context.tsx`](ezkey-admin-ui/src/context/help-context.tsx), [`ezkey-admin-ui/src/hooks/use-detail-navigation.ts`](ezkey-admin-ui/src/hooks/use-detail-navigation.ts), [`ezkey-admin-ui/src/hooks/use-paginated-orval.ts`](ezkey-admin-ui/src/hooks/use-paginated-orval.ts), and [`ezkey-admin-ui/src/hooks/use-integrations.ts`](ezkey-admin-ui/src/hooks/use-integrations.ts) by moving derived state into initializers/reducers/callbacks where appropriate.
3. Resolve `react-refresh/only-export-components` by splitting hooks or exported constants from component modules where Fast Refresh expects component-only exports, including the context files and [`ezkey-admin-ui/src/routes.tsx`](ezkey-admin-ui/src/routes.tsx).
4. Address the remaining isolated diagnostics, including `Date.now()` during render in [`ezkey-admin-ui/src/pages/api-key-detail.tsx`](ezkey-admin-ui/src/pages/api-key-detail.tsx), and the smaller `exhaustive-deps` warnings in list/detail pages.
5. Improve lint triage by adding a small diagnostic npm script or documented command that runs ESLint directly with JSON/stylish output. The practical command observed here is `npx eslint . --format stylish`; avoid relying on `npm run lint -- --format json` on this workstation because npm consumed `--format` unexpectedly.

## Validation

- Run `npm run build` in [`ezkey-admin-ui`](ezkey-admin-ui).
- Run `npx eslint . --format stylish` and `npm run lint` in [`ezkey-admin-ui`](ezkey-admin-ui).
- Use `ReadLints` on edited files after implementation.
- Browser testing is not required for the lint-only cleanup unless a refactor accidentally touches behavior. If the detail dialog helpers are moved carefully with no DOM or copy changes, existing build and lint validation should be sufficient.
