# Mobile Dependency Lot - PR Template

Use this template for each isolated dependency lot.

## Summary

- Lot scope:
- Why this lot now:
- Risk level (low / medium / high):

## Changes

- package(s) updated:
- lockfile updated: yes/no
- generated code impacted: yes/no (if yes, list directories)

## Validation Evidence

- `yarn deps:monitor` snapshot before lot:
  - actionable:
  - deferred:
- `yarn lint`:
- `yarn typecheck`:
- `yarn test --runInBand`:
- Android debug install (`./scripts/build-install-debug-clean.sh`):

## Outcome

- Decision: keep / rollback
- If rollback, reason:
- Follow-up needed:

## Notes

- Ecosystem gates considered (ESLint10 / Jest30 etc):
- Windows path-length preflight status:
