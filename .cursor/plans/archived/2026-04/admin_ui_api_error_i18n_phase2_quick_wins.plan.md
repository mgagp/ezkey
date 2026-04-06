---
name: Admin UI API error i18n Phase 2 quick wins
overview: Scope this work plan to Phase 2 (Coverage) quick wins only—static ProblemDetail `type` → errors.* en/fr, safe precedence, and callsite wiring. Defer templating, backend locale, and non–quick-win exhaustive coverage to a separate plan. Phase naming matches the archived backend error i18n strategy (Phase 1 Pilot, Phase 2 Coverage, Phase 3 optional).
todos:
  - id: inventory-static-types
    content: Inventory Admin API `type` URIs reachable from Admin UI where user-facing text is static (no dynamic interpolation in `detail`).
    status: completed
  - id: errors-json-en-fr
    content: Add matching `errors.*` keys in `src/locales/en/errors.json` and `fr/errors.json` for those types; keep fallback to `detail` where gaps remain.
    status: completed
  - id: precedence-quick-wins
    content: Extend `shouldPreferI18nOverDetail` (or equivalent) only for categories where curated copy is safe (no parameterized server `detail`).
    status: completed
  - id: callsites
    content: Replace remaining raw `getApiErrorMessage` with `getTranslatedApiError` where API errors are shown for covered paths.
    status: completed
isProject: false
---

# Admin UI — API error i18n (Phase 2 quick wins)

## Naming (aligned with the archived strategy)

This follows the phase names from `[.cursor/plans/archived/2026-04/backend_error_i18n_strategy_cf455bd9.plan.md](.cursor/plans/archived/2026-04/backend_error_i18n_strategy_cf455bd9.plan.md)`:


| Phase                  | Name                                                                             | Status in this repo                                       |
| ---------------------- | -------------------------------------------------------------------------------- | --------------------------------------------------------- |
| **Phase 1 — Pilot**    | Admin UI infra + Option B (`type` → `errors.`*), minimal paths, tests, AGENTS.md | **Delivered** (see archived plan Closing note)            |
| **Phase 2 — Coverage** | Inventory + en/fr keys + broader adoption                                        | **This plan covers a deliberate subset: quick wins only** |
| **Phase 3** (optional) | Other surfaces, optional `code`, server-side `Accept-Language`, etc.             | **Out of scope** — not in this plan                       |


No alternate numbering (e.g. “phase zero”): **Phase 1** and **Phase 2** mean what the original strategy says.

---

## Objective (primary)

Maximize **tangible** FR/EN improvement for Admin API errors with **reasonable effort**:

- Prefer **stable** RFC 9457 `ProblemDetail.type` URIs as the key source (Option B → `errors.<dotted-path>`).
- Focus on **quick wins**: problem types where the message is **fixed** (no parameters) and where showing curated locale copy does not hide important dynamic server text.
- Adjust **precedence** only where safe (today `authentication.`* prefers i18n; extend similarly for categories like `domain.*` when `detail` is redundant English—see archived plan Closing note).

**References:** `[ezkey-admin-ui/src/lib/api-error-i18n.ts](ezkey-admin-ui/src/lib/api-error-i18n.ts)`, locale files under `[ezkey-admin-ui/src/locales/](ezkey-admin-ui/src/locales/)`.

---

## Out of scope (separate work plan later)

The following are **not** part of this plan:

- Formal evaluation of **templating** (`messageKey` + params, or UI `t(key, params)`) for parameterized errors
- **Backend** localization (`MessageSource`, `Accept-Language`)
- **Exhaustive** Phase 2 coverage of every `type` if it is not a quick win (e.g. heavy copy review, risky precedence)
- **Phase 3** items (mobile, Auth API consumers, optional `code` on ProblemDetail, etc.)

Those can be scheduled in a **follow-up plan** after quick wins land.

---

## Deliverables

1. A **practical inventory** (markdown or doc) of Admin UI–reachable `type` values prioritized for **static** messages.
2. **en** + **fr** entries under `errors` for quick-win types.
3. **Targeted** precedence / policy updates so French and English show where intended without masking parameterized `detail`.
4. **Callsite** updates to use `getTranslatedApiError` for the covered surfaces.

---

## Success criteria

- Operators see **clear FR/EN** for the chosen high-value, static error paths.
- Unknown types or gaps still **fall back** safely (`detail` / `title` / `getApiErrorMessage` per existing rules).
- Review scope stays **bounded**; remaining coverage and advanced i18n contracts are explicitly **deferred** to another plan.

