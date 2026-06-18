# Backlog Idea — `I-2026-06-18-demo-device-qr-auth-url-parity` Demo Device: honor Auth API URL from enrollment QR

## Metadata

- **ID:** `I-2026-06-18-demo-device-qr-auth-url-parity`
- **Status:** `done`
- **Priority:** `P2`
- **Created at:** `2026-06-18`
- **Updated at:** `2026-06-18`
- **Last reviewed at:** `2026-06-18`
- **Progression markers:** `P0-foundations`
- **Component tags:** `demo-device`, `auth-api`, `admin-ui`, `mobile` (parity reference)
- **Lane:** `D` (post-delivery correction — observed gap after standalone vs clean-start bind divergence)
- **Captured by:** Marc

## Intent

Close the gap between **mobile QR-first enrollment** and the **Demo Device simulator**: when an
Admin UI enrollment QR includes `authUrl`, the demo device must route bind/verify and subsequent
Auth API calls for that enrollment to that base URL, while keeping a simple configured default for
manual entry and Exp1-style deployments.

## Problem and value

- **Problem:** Demo Device parses `authUrl` from QR (`enrollment-qr-import.js`) but only shows a
  mismatch warning; all server-side calls use `ezkey.auth.api.url`. Operators running standalone
  Demo Device (default Exp1) against a **local** Admin UI enrollment see bind failures (HTTP 400)
  even with a valid QR — configuration mismatch, not protocol or Java source drift.
- **Expected value:**
  - Parity with mobile `installation.authUrl` routing.
  - Smoother local dev: standalone Demo Device on `:3080` + clean-start stack without mandatory
    `.env` override when QR carries the correct Auth API URL.
  - Clearer simulator credibility for EXP1 and integrator demos.

## Scope

- **In scope:**
  - Use validated `authUrl` from QR through bind → verify → pending → respond for that enrollment.
  - Persist per-enrollment effective Auth API base (reuse `enrollmentUrl` on store `Record` or
    equivalent).
  - Server-side URL validation aligned with mobile / existing JS rules (https, or http on dev hosts).
  - Manual bind without QR: continue using configured default only.
  - **Delivery order:** implement in monorepo `ezkey-demo-device/` first; sync to public
    `mgagp/ezkey-demo-device` as final step; maintainer standalone Docker validation required before
    closeout (see `product-docs/methodology/case-study-ezkey.md` § Dual-repo delivery).
  - Unit tests for URL resolution and Auth API client routing; targeted manual exploratory matrix.
- **Out of scope:**
  - Changing Admin UI QR payload format (already includes `authUrl` when configured).
  - Mobile app changes.
  - Multi-tenant routing beyond one URL per enrollment.
  - Replacing Exp1 as Docker default for standalone repo.
  - Playwright Admin UI browser tests (low risk; manual matrix sufficient).

## Key assumptions

- Admin UI QR includes `authUrl` when instance branding / `authApiPublicBaseUrl` is set for the
  target Auth API (local or Exp1).
- `enrollment-qr-import.js` validation rules remain the client-side first pass; server re-validates.
- Demo Device remains a **simulator**, not a production mobile client — same contract, pragmatic
  security posture.

## Risks and exceptions

- **Open redirect / SSRF posture:** only allowlisted URL shapes (mirror mobile); reject on server.
- **Stale URL in store:** if enrollment file has URL, all later ops use it (intended, like mobile).
- **Pipe-delimited legacy QR** without `authUrl`: falls back to configured default (unchanged).

## Promotion notes

Promoted to `TB-2026-06-18-demo-device-qr-auth-url-parity` after Grill Me settles UX edge cases.
No separate `V-*` required — direction already established by mobile QR-first model and EXP1
positioning.

## Links

- Mobile reference: `ezkey_mobile/docs/MOBILE_API_MAPPINGS.md` (`installation.authUrl`)
- Admin UI QR builder: `ezkey-admin-ui/src/lib/enrollment-qr-payload.ts`
- Demo Device QR import: `ezkey-demo-device/src/main/resources/static/js/enrollment-qr-import.js`
- Grill Me: [`../grill-sessions/2026-06-18-demo-device-qr-auth-url-grill-me.md`](../grill-sessions/2026-06-18-demo-device-qr-auth-url-grill-me.md)
- Tracer bullet: [`TB-2026-06-18-demo-device-qr-auth-url-parity.md`](TB-2026-06-18-demo-device-qr-auth-url-parity.md)
- Implementation handoff: **TB § Implementation handoff (cold session bootstrap)**
- Method log (Lane E retro): [`../method-logs/ML-2026-06-18-demo-device-qr-session.md`](../method-logs/ML-2026-06-18-demo-device-qr-session.md)
- Observed incident: standalone `:3080` → `exp1-auth-api.ezkey.org` while enrollment 180 on local stack

## Closeout (2026-06-18)

- **Evidence:** unit tests (`AuthApiUrlValidator`, `EnrollmentAuthApiUrlResolver`,
  `EnrollmentStoreRecordJsonRoundtripTest`); maintainer standalone `:3080` bind via Admin UI QR
  against ngrok/local Auth API (operator sign-off).
- **Follow-up fix:** client-side `authApiBaseUrl` persistence (`sessionStorage` + submit sync) after
  mismatch banner showed but POST omitted the field.
- **Deferred:** GitHub issue optional; clean-start `:8083` regression smoke not re-run post client fix
  (low risk — Java path unchanged).
- **Residual risk:** pipe-delimited legacy QR without `authUrl` still uses configured default only
  (by design).
