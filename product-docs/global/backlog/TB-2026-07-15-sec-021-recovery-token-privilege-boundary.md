# Tracer Bullet Brief — `TB-2026-07-15` SEC-021 Recovery token privilege boundary

## Metadata

- **ID:** `TB-2026-07-15-sec-021-recovery-token-privilege-boundary`
- **Status:** `done`
- **Related idea:** `I-2026-07-15-sec-021-recovery-token-privilege-boundary`
- **Parent context:** Security Challenge Report 2026-07 P0
- **Lane:** `B`
- **Posture:** `single-pass`
- **GitHub issue:** `#357`
- **Issue labels:** `lane:b`, `type:security`, `component:admin-api`, `component:core`, `priority:p0`, `status:ready`
- **Created at:** `2026-07-15`
- **Updated at:** `2026-07-15`
- **Captured by:** Marc (plan incubation)

## Objective

Confirm and close SEC-021: recovery tokens must not inherit full administrator authority.

## Evidence summary

| Check | Pre-fix | Post-fix |
| --- | --- | --- |
| Functional `GET /integrations` with recovery Bearer | **200** (breach confirmed) | **401** |
| Functional enrollment reset with recovery Bearer | 200 | 200 |
| Session Bearer on enrollment reset | 403 | 403 |
| Unit `validateTokenWithRelations` for `RECOVERY` | N/A | empty Optional |

## Boundaries in scope

- `AdminTokenPurpose` + Flyway V18 `token_purpose`
- `AdminRecoveryService` issue/validate/deactivate
- `AdminTokenValidationService` session gate + purpose-based sliding TTL
- `AdminEnrollmentController` deactivate after reset
- Functional + unit regression tests
- `ENDPOINT.md` / `ADMIN_UI_RECOVERY.md`

## Out of scope

- SEC-017, SEC-022, Admin UI recovery session lifecycle (SEC-026/027)

## First executable slice (completed)

1. Red functional privilege-boundary test (confirmed 200 → gap)
2. Durable purpose column + validation reject
3. Green functional + unit ladder; `./scripts/build.sh`; clean-start

## Exit criteria

- [x] Recovery Bearer denied on ordinary Admin API
- [x] Reset still works; session token still rejected on reset
- [x] Recovery token deactivated after successful reset
- [x] Docs state enforced purpose boundary
- [x] Labeled GitHub issue for P0 transparency

## Links

- Idea: [`ideas/I-2026-07-15-sec-021-recovery-token-privilege-boundary.md`](ideas/I-2026-07-15-sec-021-recovery-token-privilege-boundary.md)
- GitHub issue: [#357](https://github.com/mgagp/ezkey/issues/357)
- Report: [`docs/SECURITY_CHALLENGE_REPORT_2026-07.md`](../../../docs/SECURITY_CHALLENGE_REPORT_2026-07.md)
