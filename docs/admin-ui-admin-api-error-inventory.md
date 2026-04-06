# Admin UI — Admin API RFC 9457 `type` inventory (Phase 2 quick wins)

This document lists `https://ezkey.io/problems/...` URIs emitted by **ezkey-admin-api** that the Admin UI may surface. It classifies each type for i18n:

- **Static `detail` (quick win):** A single stable English `detail` from production code paths, or a fixed exception message. Safe to prefer curated **en**/**fr** strings when policy allows.
- **Dynamic `detail`:** Message includes variable data (IDs, names, counts). **Do not** prefer generic i18n over `detail` when specifics matter; templating is a follow-up.
- **Keys in UI:** All listed types have entries under [`ezkey-admin-ui/src/locales/en/errors.json`](../ezkey-admin-ui/src/locales/en/errors.json) (and `fr`) for fallback when `detail` is absent and for types where `getTranslatedApiError` prefers the catalog (see [`api-error-i18n.ts`](../ezkey-admin-ui/src/lib/api-error-i18n.ts)).

## Prefer curated locale over non-empty `detail`

These are allowlisted in `shouldPreferI18nOverDetail` because messages are fixed and redundant with the catalog:

| `type` (suffix) | Notes |
| ----------------| ----- |
| `authentication/*` | Pilot coverage |
| `domain/integration-has-enrollments` | Fixed `IntegrationHasEnrollmentsException` message |
| `domain/pending-encryption-key-exists` | Fixed message from key introduction path |
| `enrollment/system-integration-create-not-allowed` | Single fixed message |
| `enrollment/active-verified-enrollment-exists` | Single fixed message |
| `enrollment/cannot-delete-with-history` | Single fixed message |
| `enrollment/cannot-delete-linked-as-admin` | Single fixed message |

## Catalogued types with dynamic or variant `detail` (detail wins when present)

| Category | Examples |
| -------- | -------- |
| `admin/*` | `invalid-argument` often includes duplicate name / validation specifics |
| `authorization/*` | Limits, tenant messages vary |
| `domain/integration-code-already-exists` | Includes code and tenant name |
| `domain/api-key-limit-exceeded` | Includes limit and integration id |
| `domain/auth-attempt-state-conflict` | Multiple reason strings |
| `domain/integration-lifecycle-state`, `domain/system-integration-lifecycle` | State-specific text |
| `enrollment/enrollment-inactive` | May include enrollment id / status |
| `enrollment/self-revocation-not-allowed` | Revoke vs delete wording |
| `enrollment/system-integration-revocation` | Revoke-all vs deactivate-all wording |
| `validation/*` | Bean validation / request specifics |
| `admin-provisioning/*` | Mostly static per `type` suffix; some `invalid-request` paths use `e.getMessage()` |

## Maintenance

When adding a new problem `type` in **ezkey-admin-api**, add matching keys under `errors` in **en** and **fr**, and update this table if the type is user-visible from the Admin UI.
