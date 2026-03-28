**Archived — status:** This plan is **implemented** and **tested** (maintainer re-ran validation; dashboard auth widget coherence and 24h badge totals behave as intended).

---

# Dashboard: Auth Attempts (24h) widget coherence

**Status:** Implemented (2026-03-28).

## Agreed approach (delivered)

- **Option B:** One **in progress** badge (`pending` + `readCount` from `overview.auth24h`), plus **accepted**, **rejected**, **invalid**, **expired** — five badges; sum equals `total` (24h cohort).
- **Bug fix:** Removed `GET /auth-attempts/pending-count` from the dashboard; overview fields only.
- **No backend DTO change** (`inFlightTotal` not added).
- **Help copy** updated (EN/FR) for total vs terminal outcomes and both cards.

## Files changed

- [`ezkey-admin-ui/src/pages/dashboard.tsx`](ezkey-admin-ui/src/pages/dashboard.tsx)
- [`ezkey-admin-ui/src/locales/en/dashboard.json`](ezkey-admin-ui/src/locales/en/dashboard.json), [`fr/dashboard.json`](ezkey-admin-ui/src/locales/fr/dashboard.json)
- [`ezkey-admin-ui/src/locales/en/help.json`](ezkey-admin-ui/src/locales/en/help.json), [`fr/help.json`](ezkey-admin-ui/src/locales/fr/help.json)

## Verification

- `npm run build` in `ezkey-admin-ui` (passes).
