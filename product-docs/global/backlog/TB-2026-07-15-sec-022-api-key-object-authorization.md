# Tracer Bullet Brief — `TB-2026-07-15` SEC-022 API key object authorization

## Metadata

- **ID:** `TB-2026-07-15-sec-022-api-key-object-authorization`
- **Status:** `done`
- **Related idea:** `I-2026-07-15-sec-022-api-key-object-authorization`
- **Parent context:** Security Challenge Report 2026-07 P0 (includes SEC-023)
- **Lane:** `B`
- **Posture:** `single-pass`
- **GitHub issue:** `#362`
- **Issue labels:** `lane:b`, `type:security`, `component:admin-api`, `priority:p0`, `status:ready`
- **Created at:** `2026-07-15`
- **Updated at:** `2026-07-15`
- **Captured by:** Marc (plan incubation)

## Objective

Close SEC-022 (cross-tenant revoke) and SEC-023 (cross-tenant metadata reads) with the same
`canAccessIntegration` gate used by API key PATCH.

## Evidence summary

| Check | Pre-fix | Post-fix (target) |
| --- | --- | --- |
| Tenant A `DELETE` Tenant B `keyId` | 204 (breach) | 403; key stays active |
| Tenant A `GET` Tenant B `keyId` | 200 | 403 |
| Tenant A `GET …/integration/{B}` | 200 | 403 |
| Same-tenant revoke | 204 | 204 |

## Boundaries in scope

- `ApiKeyController` revoke / get / list-by-integration authorization
- Unit tests in `ApiKeyControllerTest`
- Functional `ApiKeyObjectAuthorizationSecurityTest`
- `ENDPOINT.md` status codes

## Out of scope

- SEC-017, SEC-024+, pending-count SEC-025
- Dual revoke authorization in repository `UPDATE` WHERE

## First executable slice

1. Red functional object-authorization tests
2. Controller ACL gates matching update
3. Green unit + functional ladder; `./scripts/build.sh`; clean-start

## Exit criteria

- [x] Cross-tenant get/list/revoke return 403
- [x] Cross-tenant revoke leaves key active
- [x] Same-tenant revoke still 204
- [x] Labeled GitHub issue for P0 transparency

## Links

- Idea: [`ideas/I-2026-07-15-sec-022-api-key-object-authorization.md`](ideas/I-2026-07-15-sec-022-api-key-object-authorization.md)
- GitHub issue: [#362](https://github.com/mgagp/ezkey/issues/362)
- Report: [`docs/SECURITY_CHALLENGE_REPORT_2026-07.md`](../../../docs/SECURITY_CHALLENGE_REPORT_2026-07.md)
