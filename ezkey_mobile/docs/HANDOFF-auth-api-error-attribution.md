# Handoff — Auth API error attribution (Ezkey problem allowlist)

**Status:** `ready to implement` in the **mobile** workspace.
**Keyword:** hygiene / client honesty — not a methodology `I-*` / `TB-*` unless the operator asks.
**Origin:** EXP1 Cloudflare API Shield schema-validation check (2026-08-23). Operator confirmed:
no Cloudflare-specific mobile UX; categorize errors and show Auth `detail` only for the Ezkey
problem namespace.

Use the paste-ready prompt below to start a **new Cursor session** in the mobile workspace.
This file is the briefing. After the slice lands, fold the durable rule into
`docs/MOBILE_API_MAPPINGS.md` (already sketched) and delete or mark this handoff `done`.

---

## Paste-ready operator prompt

```text
Implement Auth API error attribution in Ezkey Mobile: show origin Problem Details only when
type is under the Ezkey namespace.

## Context (already decided — do not reopen)

Auth API origin errors are RFC 9457 with type under https://ezkey.io/problems/ (Auth catalog
https://ezkey.io/problems/auth/… plus https://ezkey.io/problems/system/audit-chain-heartbeat-degraded).

When Auth is reached through Cloudflare (EXP1 https://exp1-auth-api.ezkey.org), a schema-validation
Block or other WAF rule can return BEFORE origin. Clients that send Accept: application/json or
application/problem+json get Cloudflare's own RFC 9457 JSON: HTTP 403, Error 1020,
cloudflare_error: true, type under https://developers.cloudflare.com/…, retryable: false.
That is an edge contract, not Auth API.

A schema-valid but failed pending probe returns origin 400, for example:
type https://ezkey.io/problems/auth/auth-attempt-binding-failed
detail "The authentication request could not be processed."

A schema-invalid bind (enrollmentId as string) returns Cloudflare 403/1020. The mobile app cannot
produce that request if it follows the OpenAPI schema. Do NOT add a Cloudflare 1xxx catalog, do
NOT show what_you_should_do / footer / ray_id in production UI, do NOT retry 1020.

Operator canon (monorepo, if present): docs/cloudflare/auth-api-schema-validation.md section
"Reading responses: origin vs Cloudflare". Wire-protocol canon:
docs/MOBILE_DEVELOPER_GUIDE.md section "Error Handling Expectations".

Admin UI already scopes translations to https://ezkey.io/problems (EZKEY_PROBLEM_TYPE_BASE in
ezkey-admin-ui/src/lib/api-error-i18n.ts). Mirror that idea, do not copy Admin UI i18n machinery.

## Problem in this app today

parseAuthApiProblemDetail (app/services/api/authApiProblem.ts) accepts ANY object with type or
title. It is unused in UI (MQ-03 in docs/security/mobile-protocol-crypto-assessment-2026-07.md).
Three extractErrorMessage copies exist:

- app/hooks/useEnrollmentWizard.ts — prefers data.detail without a type allowlist (Cloudflare
  1020 detail would leak if bind/verify hit the edge).
- app/hooks/usePendingAuth.ts — prefers data.message / data.error / Axios message (less leaky
  today, still not Ezkey-aware).
- app/screens/EnrollmentDetail/EnrollmentDetailScreen.tsx — same pattern as pending.

Native codes (EZK_AUTH_CANCELLED, EZK_AUTH_UNAVAILABLE, EZK_KEY_INVALIDATED) must stay as they
are. Local crypto / signature fail-closed paths are out of scope.

## Solution to implement

Two user-visible families only:

1. Ezkey domain — HTTP error whose JSON type starts with https://ezkey.io/problems/
   → show the API detail (safe catalog strings from AuthApiProblemCatalog). Optional: map a
   few types to existing i18n keys if they already exist; do not invent a full type catalog.
2. Everything else (network, timeout, HTML 1020, Cloudflare JSON, proxy, 5xx without Ezkey
   type, about:blank) → existing generic localized fallback (requestFailed / unexpectedError
   or a single new installation-unreachable string if you must distinguish; prefer reuse).

Keep parseAuthApiProblemDetail structural (any RFC 9457-shaped body) for tests/diagnostics.
Add isEzkeyProblemType(type) and a single userFacingAuthApiError(error, fallback) helper that:
- uses the structural parser;
- returns detail only when type is under https://ezkey.io/problems/;
- otherwise returns the localized fallback (never Cloudflare title/detail).

Wire the three extractErrorMessage sites to that helper. Do not add the helper into generated
Orval files.

Production UI: no ray_id, no cloudflare_error fields. Debug-only pending support box
(MOBILE_PENDING_DEBUG_PLAN / gated debug) may keep raw text; do not promote that to production
chrome.

Do not retry on 403/1020. Do not change Auth API, OpenAPI specs, or Bruno.

## Tests (minimum)

- Unit: Ezkey type + detail → detail shown.
- Unit: Cloudflare 1020 fixture (cloudflare_error, type on developers.cloudflare.com) → fallback,
  not Cloudflare detail.
- Unit: title-only / about:blank / HTML / non-object → fallback.
- Existing parseAuthApiProblemDetail tests can stay structural; add allowlist tests on the new
  helper. Current fixtures use https://ezkey.example/… — production prefix is
  https://ezkey.io/problems/.

## Constraints

- English only in repo files.
- Android-first; do not file iOS parity as a defect.
- No new libraries. Stay in app/services/api/.
- Do not invent I-* / TB-* / GitHub issues unless the operator asks after the slice.
- Do not hand-edit openapi-spec.json.
- Keep the change small: helper + wire three call sites + tests + a short note in
  docs/MOBILE_API_MAPPINGS.md if the allowlist sentence is not already there.

## Out of scope

- Cloudflare Custom Error Pages.
- Demo Device Java UI.
- Mapping every AuthApiProblemCatalog type to a unique mobile string.
- Showing Ray ID in production.
- Signed instance-info EXP1 deploy.
```

---

## Operator decisions (already made)

1. **No** Cloudflare-specific mobile error catalog or 1020 screens.
2. **Yes** allowlist `https://ezkey.io/problems/` before showing `detail`.
3. Schema-invalid 1020 is not a path a correct mobile client should hit; generic copy is enough.
4. Implement in the dedicated mobile workspace, not as part of the Auth Cloudflare schema TB.

## Evidence (EXP1, 2026-08-23)

Origin 400 (schema-valid, failed pending bind) vs Cloudflare 403/1020 (schema-invalid bind)
recorded in `docs/cloudflare/auth-api-schema-validation.md` § Reading responses.
