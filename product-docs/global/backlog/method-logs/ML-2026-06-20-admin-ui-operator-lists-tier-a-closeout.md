# Method log — `ML-2026-06-20` Admin UI operator lists Tier A closeout

## Metadata

- **Date:** `2026-06-20`
- **Slice:** GitHub #236 / branch `feat/admin-ui-operator-lists-tier-a`
- **Classification:** program (not hygiene)

## Delivered

| TB | Scope | Status |
|----|-------|--------|
| `TB-2026-06-18-admin-ui-lists-tier-a-integrations` | `tenantName` on integrations list | `done` |
| `TB-2026-06-18-admin-ui-lists-tier-a-enrollments` | List DTO joins + column trim | `done` |
| `TB-2026-06-18-admin-ui-lists-tier-a-api-keys` | List DTO joins + column reorder + revoke | `done` |
| `TB-2026-06-20-admin-ui-enrollments-list-quick-actions` | List deactivate/reactivate | `done` |

Canon added/updated: `admin-ui-list-quick-security-actions.md`, matrix rows → `implemented`.

## Evidence (operator)

- Functional exploratory pass on clean-start stack (operator, 2026-06-20)
- Selective automated tests (admin-api modules)
- Maven baseline on branch

## Traceability sync

- `I-2026-0013` / `I-2026-0014`: partial execution notes added (Tier A core complete; matrix rows remain)
- Follow-up queue: [`ideas/I-2026-0028-admin-ui-operator-experience-follow-up.md`](../ideas/I-2026-0028-admin-ui-operator-experience-follow-up.md)
- `spec-test-traceability.md`: no row added (no new feature ID; operability slice under existing matrix)

## Deferred (explicit)

See `I-2026-0028` priority table: Tenants Tier A, integration-detail enrollments drift, Tier B auth
attempts/audit logs, Related details retirement.

## Residual risks

- Auth attempts list still shows placeholder integration column (pre-existing; documented in follow-up)
- Integration detail embedded enrollments list not aligned with main list (UX inconsistency)
- OpenAPI dispatches must stay in sync after Admin API DTO changes (standard workflow)

## Next action

Triage `I-2026-0028` P1 items when resuming Admin UI operator experience work.
