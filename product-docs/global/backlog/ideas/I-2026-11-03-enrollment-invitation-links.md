# Backlog Idea — `I-2026-11-03-enrollment-invitation-links` Enrollment invitation links (deep links next to the QR code)

## Metadata

- **ID:** `I-2026-11-03-enrollment-invitation-links`
- **Status:** `captured`
- **Priority:** `P2` (proposed; Marc sets it at triage)
- **Created at:** `2026-11-03`
- **Updated at:** `2026-11-08`
- **Last reviewed at:** `2026-11-03`
- **Component tags:** `admin-api`, `admin-ui`, `auth-api`, `mobile`, `sdk-dart`, `infra`, `docs`, `security`
- **Lane:** program (new client-facing contract across Admin API, Admin UI, `ezkey_dart` and the
  reference app; not hygiene)
- **Captured by:** Marc
- **GitHub issue:** — for the links program (body proposed, not created yet); lot 0 (paste):
  [#1185](https://github.com/mgagp/ezkey/issues/1185)

## Intent

Let an enrollment invitation reach an authenticator as a **link**, not only as a QR code, so
that a user who receives it on the device that will hold the key (email, chat, the integrating
app's page on mobile), a desktop authenticator without a camera, and a third-party authenticator
can all start enrollment. The 6-digit challenge keeps travelling separately; bind and verify
stay unchanged. The invitation payload is the one in ADR-0019: `enrollmentProofToken` and
optional `authUrl`. Links do not carry `enrollmentId`. Bind returns the id.

## Problem and value

- **Problem:** the bind invitation (`enrollmentProofToken`, optional `authUrl`; ADR-0019) only
  exists as QR content. Scanning a QR shown on the same phone is impossible, desktops often have
  no camera, and a third-party authenticator has no documented text form to accept. The only text
  path today is the Admin UI "copy token" button, which copies the raw proof token without the
  server URL.
- **Expected value:** same-device and camera-less enrollment with one defined format that every
  authenticator (reference app, Demo Device, desktop, Open Authenticator fork) parses the same
  way, plus a server-host confirmation that also hardens today's QR path.

## Inventory (verified on `main` @ `e4549b736`, 2026-11-03)

| Claim in the brief | Verdict | Evidence |
| --- | --- | --- |
| Admin API composes JSON `{enrollmentId, enrollmentProofToken, authUrl?}` and renders a PNG | Confirmed | `ezkey-admin-api/.../service/QrCodePayloadService.java` `composePayload`; callers `EnrollmentController` (`GET /api/v1/enrollments/{id}/qrcode`), `AdminProvisioningController` (admin onboarding QR), `AdminBootstrapService` |
| `authUrl` only when `ezkey.qr.auth-base-url` is set | Confirmed | `ezkey-core/.../config/QrCodeProperties.java`; the same value is served as `authApiPublicBaseUrl` by public instance-info |
| Admin UI renders the same payload (`enrollment-qr-data-url.ts`) | **Partly.** `enrollment-qr-data-url.ts` only turns a string into a PNG. The payload is rebuilt client-side by `enrollment-qr-payload.ts` (`buildEnrollmentQrPayloadJson`, falls back to `VITE_QR_AUTH_BASE_URL`) for admin activation and recovery only. End-user enrollment detail fetches the server PNG. | `ezkey-admin-ui/src/lib/enrollment-qr-payload.ts`, `components/feature/login-activation-section.tsx`, `login-recovery-section.tsx`, `pages/enrollment-detail.tsx` (`/qrcode` fetch, "copy token") |
| Challenge travels separately | Confirmed | Admin UI shows `enrollmentChallenge` next to the QR; never in the payload |
| `EnrollmentCode.parse` reads JSON and legacy `id\|token` | Confirmed | `ezkey_dart/lib/src/sdk/models.dart`; `authUrl` checked by `EzkeyUrlPolicy` (fail closed). The reference app has a twin parser, `ezkey_mobile/app/utils/qrPayload.ts` (`parseQrPayload`, `validateAuthUrl`) |
| Auth API `EnrollmentController` Javadoc mentions deep links | Confirmed (stale prose) | `ezkey-auth-api/.../auth/controller/EnrollmentController.java` L80 |
| `EnrollmentBindRequestDto` mentions "deep link navigation" | Confirmed (stale prose) | `ezkey-auth-api/.../enrollment/dto/EnrollmentBindRequestDto.java` L58 |
| Early `ezkey://bind?enrollmentId=…&enrollmentProofToken=…` | Confirmed, historical only. The plan also **logs** that URL with the token in it; no live code contains `ezkey://`. | `docs/plan/ADMIN_MFA_IMPLEMENTATION_PLAN.md` L140, L722 |
| Finding 12 "deep links not verified" | Confirmed, but a side remark: finding 12 is about process death and TTL during respond; deep links were left out of that decision | `ezkey_mobile/docs/MOBILE_APP_STRATEGIC_ORIENTATION.md` L646, L654, L775 |
| Android manifest: launcher filter only | Confirmed. `MainActivity` is already `launchMode="singleTask"` (a warm link arrives as a new intent). | `ezkey_mobile/android/app/src/main/AndroidManifest.xml` |
| "Expo linking" | **Correction:** the app is bare React Native (no Expo). The path is RN `Linking` + React Navigation `linking` config. iOS has no `CFBundleURLTypes` or associated domains yet. | `ezkey_mobile/package.json`, `ezkey_mobile/app.json`, `ezkey_mobile/ios/` |
| Showing the server host before confirmation covers phishing | **Correction: not shown today.** `EnrollmentInfoCard` has `showServerUrl` / `serverUrl` props, but its only call site passes neither, so the verify stage shows tenant, account and role labels only. Those labels come from the instance the user is enrolling with, so they prove nothing about who runs it. | `ezkey_mobile/app/screens/EnrollmentWizard/EnrollmentWizardScreen.tsx` L58–L140, L314 |
| Vision `V-2026-10-27-third-party-authenticator-integration-kit` | Confirmed. On `main` since #1187 (2026-11-03, after this inventory). Its behavioural contract `docs/CLIENT_BEHAVIOUR_CONTRACT.md` was already on `main`. | `product-docs/global/vision/` |

Facts found on the touched path (pre-existing conditions):

- **Bind fires right after the scan, before any user confirmation** (FSM §1: `ScannerOpen →
  Binding` on parse). With a link, opening it would contact the `authUrl` server and consume
  the read-once bind without a user decision.
- **Bind is read-once** (`CREATED → BOUND`, `EnrollmentBindService`); invitations expire after
  `ezkey.enrollment.pending-expiration-days` (default 7) or 24 h for the bootstrap admin.
- **The proof token outlives the invitation.** `docs/ENDPOINT.md` calls it the "permanent
  enrollment token": after verify it identifies the enrollment on `pending` (with a device
  signature) and alone authorizes enrolled `instance-info` (branding only). A leaked link after
  enrollment is therefore low impact, not zero.
- **The challenge is one shot.** A wrong challenge (or a bad verify signature, or a reused
  device key) calls `EnrollmentTxHelper.markInvalidAndClear`: in its own `REQUIRES_NEW`
  transaction it sets `enrollment_status = 'INVALID'`, nulls `enrollment_challenge` and sets
  `active = false`, so the change commits even though the request fails. The next verify fails at
  the `BOUND` pre-check, and the locked re-read (`findAndLockBoundById`: `… AND enrollment_status
  = 'BOUND' FOR NO KEY UPDATE`) also needs `BOUND`. `INVALID` is system-initiated and terminal
  (`V1`/`V7` status `CHECK`); recovery is an operator reset. Brute force is therefore not
  possible; the only theoretical margin is a burst of parallel requests that all read `BOUND`
  before the first `INVALID` commits, bounded by the `verify` rate limits (negligible against
  10⁶ values). Consequence for links: a user's typo on the same device burns the invitation too.
- **#1180** (branch `fix/1180-admin-crypto-exposure`, not merged yet) returns the proof token and
  challenge from the Admin enrollment response **only while `CREATED`**. An invitation link
  should follow the same rule.
- **Enrollment creation is Admin API, operator-authenticated only.** The Integration API exposes
  auth attempts only (`IntegrationApiAuthAttemptController`); the M2M hatch on the Admin API is
  gone (`I-2026-08-29-admin-api-remove-m2m-hatch`). The demo ACME app ships an Admin spec copy but
  never creates enrollments.
- **Ezkey sends no email** (no mail dependency): "send" in the Admin UI can only mean copy, or a
  `mailto:` at most.
- `sites/ezkey-org` is the public project site, **not** an instance. Per-instance well-known
  files or a landing page belong to the instance deployment (`docker/caddy/Caddyfile`, the
  Lightsail Caddyfile), not to `sites/`.

## Use cases

1. **Same device.** The user opens the invitation on the phone that will hold the key: email,
   chat, or the integrating app's mobile web page. Today: impossible.
2. **Email or chat invitation.** An operator sends the invitation remotely; the challenge comes
   through another channel (or the same message, with the risk stated below).
3. **Desktop authenticator** (`I-2026-07-17-desktop-authenticator-reference-app`, Tauri): no
   camera; the link opens through an OS scheme handler, or the user pastes it.
4. **Third-party authenticator** (Open Authenticator fork, `V-2026-10-27`): needs a documented
   text format and, ideally, a way for the OS to route the link to it.

## Link shape options

The payload is the same in every option: the QR field names, URL-encoded, **in the fragment**:

```text
ezkey://enroll#v=1&enrollmentId=123&enrollmentProofToken=…&authUrl=https%3A%2F%2Fauth.example.com
https://auth.example.com/enroll#v=1&enrollmentId=123&enrollmentProofToken=…&authUrl=…
```

One vocabulary across QR, link and docs; `v=1` leaves room for change; the challenge is never
included.

| | a. Custom scheme `ezkey://enroll#…` | b. Verified `https://` (App Links / Universal Links) | c. `https://` landing page on the instance (`/enroll#…`) |
| --- | --- | --- | --- |
| Who can open it | Any authenticator that registers `ezkey` (reference, desktop, third-party) | Only apps listed in the domain's `assetlinks.json` / `apple-app-site-association` **and** built with that domain in their manifest or entitlements | Any browser; the page then hands off to the scheme (a) and shows the QR as a cross-device fallback |
| Self-hosting | Works on any instance, no setup | Breaks: a store app cannot be built with every self-hosted domain. Only works for domains known at build time (community instance) or a central relay domain | Works; one page per instance, served by the proxy |
| Clickable in email / chat | Often **not** (webmail and many chat apps do not linkify custom schemes) | Yes | Yes |
| Hijacking | Any installed app can claim `ezkey`; Android shows a chooser, iOS picks an undefined winner | Verified: the OS opens only a listed app | The handoff step inherits (a); the page itself is safe |
| Desktop | OS scheme registration (Tauri deep-link) | No equivalent on desktop | Page opens; handoff via scheme |
| Extra | Nothing to host | iOS needs an Apple Developer account (none yet); per-instance file listing app signatures favours the reference app | A static page with client-side QR rendering, CSP, `Referrer-Policy: no-referrer`, a hosting decision |

A variant **b′** (central relay such as `link.ezkey.org/enroll#…`, verified for the reference app,
`authUrl` in the fragment) would make verified links work for every instance, but it puts an
Ezkey-run domain in every self-hosted invitation, and the relay's JavaScript can read the
fragment. That contradicts the self-hosted posture; listed only to be rejected or decided
explicitly.

## Secret in a URL

The proof token is a bearer secret until bind, and an identifier after it. What the fragment
covers:

| Exposure | Fragment protects? | Note |
| --- | --- | --- |
| Server, proxy and CDN access logs | Yes | Fragments are never sent in HTTP requests. A query string would be logged by Caddy, Cloudflare and any proxy on the way |
| `Referer` | Yes | Browsers never send fragments; set `no-referrer` on the landing page anyway |
| Chat unfurlers, link previews | Yes, for `https://` | They fetch the page without the fragment. Custom schemes are not fetched at all |
| Email security gateways that rewrite URLs (e.g. Microsoft Safe Links) | **Likely not** | Rewriting encodes the whole original URL, fragment included, into a query parameter of the gateway's URL. To verify |
| Scanners that open links in a sandbox browser | Partly | They see the token. Safe as long as **opening the link never consumes the invitation**: the landing page must not call the Auth API, and bind stays a POST from the authenticator |
| Browser history and history sync | No | The page should `history.replaceState` to drop the fragment after reading it; TTL + read-once bound the rest |
| Chat and mail history, clipboard managers | No | Same exposure as a screenshot of the QR today; bounded by TTL and read-once |
| App and OS logs | Depends on us | The link must never be logged (mobile flow trace, Java, Dart). The old plan's `logger.warn("📱 Enrollment URL: …")` pattern must not come back |

Placing the challenge in the same message as the link turns that message into a complete
enrollment credential. That is the same exposure as the Admin UI showing the QR and the code
side by side today, but a message is easier to forward. Operator copy should recommend a second
channel.

## Threats

| Threat | What already covers it | What does not |
| --- | --- | --- |
| **Hijacking app** claims `ezkey://` and receives the link | Read-once bind; the challenge is not in the link and is **one shot** (one wrong guess → `INVALID`), so without the challenge the hijacker has a 1-in-10⁶ chance, once; TTL | Takeover needs the challenge too (same message, or a second leak). What remains is **denial of service**: once the hijacker binds (or guesses wrong), the legitimate device gets "already bound" or "invalid" and the operator must reset. Only verified links (b) prevent interception |
| **Phishing link to an attacker instance**: attacker creates an enrollment on their own server, sends link + challenge ("Acme MFA setup") | `EzkeyUrlPolicy` / `validateAuthUrl`: HTTPS only, which proves transport, not identity. TTL, read-once and the challenge do **not** help: they are the attacker's own | The user cannot see which server they bind to (host not shown, see Inventory). Branding labels are the attacker's choice, signed with the attacker's own integration key. Result: an approval channel to the victim's phone with arbitrary `contextTitle` / `contextMessage`, usable for social engineering. It does not compromise the victim's real enrollments. Same attack exists with a crafted QR today; links make it cheaper and mass-mailable |
| **Opening the link contacts a server without consent** | — | Today's flow binds immediately on parse; a link would leak the device IP to `authUrl` and consume the invitation before the user decides |
| **Forwarded invitation** | TTL, read-once | Same as forwarding a QR screenshot |
| **Landing page injection** | — | The page must render fragment values as text only, validate `authUrl` with the same policy before offering the handoff, and ship a strict CSP |

Mitigation common to all options and to the QR path: a **review step before bind** that shows
the server host prominently (and flags a host that differs from the user's existing enrollments),
with an explicit Continue. Bind happens only after that tap.

## API surface impact

| Component | Change | Notes |
| --- | --- | --- |
| **Admin API** | One server-side composer next to `composePayload` (e.g. `composeInvitationLink`); an `invitationLink` field (nullable) on the responses that already carry the proof token: enrollment GET/search while `CREATED` (#1180 rule), admin onboarding, activation. No new endpoint needed | `ezkey.qr.auth-base-url` becomes **required for links**: without it the field is `null` (a link cannot rely on an app default URL, and third-party or desktop apps have none). Option c adds a landing base URL property. Contract change → `docs/ENDPOINT.md` section, `CONFIGURATION.md`, Bruno, OpenAPI refresh |
| **Admin UI** | "Copy invitation link" next to the QR and "copy token" on enrollment detail, create dialog and admin onboarding; hint that the challenge goes through another channel. Hidden with a short reason when the link is `null` | No "send": Ezkey has no email. A `mailto:` is possible but puts the token in the mail client's drafts; open question |
| **Integration API** | **None.** Integrating apps do not create enrollments and so never issue invitations (operator-only Admin API since the M2M hatch removal) | Self-service enrollment from the integrating app would be a separate program with a new M2M contract; out of scope |
| **Auth API** | **Bind and verify unchanged.** Fix the stale Javadoc on `EnrollmentController` and `EnrollmentBindRequestDto` once the format exists | The one-shot challenge already bounds guessing; no hardening needed for links |
| **`ezkey_dart`** | `EnrollmentCode.parse` accepts both link forms (same `EzkeyUrlPolicy` on `authUrl`, reject unknown `v`); `toInvitationLink()` helper; shared golden vectors with the Java composer and the mobile parser | `toString()` already hides the token; keep it so |
| **Reference mobile app** | `parseQrPayload` accepts the link form (also inside a QR). Android: `VIEW` / `BROWSABLE` intent filter for `ezkey://enroll`. iOS: `CFBundleURLTypes` (simulator only for now). RN `Linking` + React Navigation `linking`; cold start and warm (`singleTask`) entry. The paste field from lot 0 also accepts the link. **FSM:** new event "invitation received" → wizard with a seed → new state before `Binding` (server review, Continue). Guards: ignore while `isBinding` / `isSubmitting`; never interrupt a pending-auth decision in flight. Same review state for QR scans | `MOBILE_APP_STATE_MACHINE.md` §1 + HTML twin; `CLIENT_BEHAVIOUR_CONTRACT.md` gains the rules (review host before bind, never log the link) |
| **Demo Device** | Accept a pasted link (parity, cheap) | Optional |
| **Desktop authenticator** | Scheme registration + paste field, when that program starts | Consumer of the format, not a blocker |
| **Instance deployment** (option c) | Static `/enroll` page served by Caddy on the Auth API public host (or a configured host); `docker/caddy/Caddyfile`, Lightsail Caddyfile, `docs/OPERATIONAL.md` | Not `sites/` (project site). Option b would add well-known files on the same host |
| **Docs** | `docs/MOBILE_DEVELOPER_GUIDE.md` (format for third parties), `docs/SECURITY_POSTURE.md` (honest claims: custom schemes can be claimed by any app), `ezkey_dart/README.md` | |

## Scope estimate

| Slice | Content | Size |
| --- | --- | --- |
| 0. Paste the enrollment code (separate lot, see below) | Release-build paste field for the existing JSON; Admin UI "copy enrollment code"; server shown at the challenge stage. No server change | **S–M** |
| **1. Format + custom scheme + review step** | Format v1; Java composer + `invitationLink` field + contract refresh; Admin UI copy button (switches from JSON to link); `ezkey_dart` parse/build; mobile parser, Android intent filter, `Linking`, lot 0's paste field also accepts the link, server review before bind (QR too); FSM and contract docs | **M** (several PRs; the mobile review step is the largest piece) |
| 2. Landing page (option c) | Static page + client-side QR + CSP + proxy routes + config + deployment docs | **S–M** after slice 1 |
| 3. Verified links (option b) | Per-domain files, app manifests and entitlements, Apple Developer account, decision on b′ | **L**, and structurally limited to domains known at build time |

**Recommended first slice:** slice 1. The format, the parsers and the review-before-bind step are
needed by every option; the review step also closes the "server never shown" gap on today's QR
path. It unblocks desktop and third-party authenticators (scheme or paste) and same-device
enrollment through paste or chats that linkify custom schemes. Slice 2 follows once the landing
page host is decided, to make links clickable in email. Option b stays deferred.

### Lot 0 — paste the enrollment code (separate, decided 2026-11-03)

Pasting the existing QR JSON in a **release** build is split out of this program: it needs no
server change, no contract refresh and none of the open questions below, and it lays down the
field and FSM entry that slice 1 extends. Tracked as
[#1185](https://github.com/mgagp/ezkey/issues/1185) (Marc, 2026-11-03).

- **Today:** the only text entry is the F2a controlled seed bypass (#254): a text field on the
  wizard, shown only when the native build is debug **and** `EZKEY_ENROLLMENT_SEED_BYPASS_ENABLED`
  **and** ack `F2A_TEST_ONLY` (`app/utils/controlledEnrollmentBypass.ts`; release preflight
  `scripts/assert-release-production-clean-env.sh`). #254 put "permanent product path" out of
  scope: deferred, not rejected. Its pipeline is already the product one: `normalizeSeedPayload`
  → `parseQrPayload` → `performBinding`, with redacted logging (`logEnrollmentSeedIngest`).
- **Nothing to paste today:** the Admin UI copies the raw proof token only, which no
  authenticator accepts alone (no id, no server). Lot 0 needs a "copy enrollment code" (the QR
  JSON) next to it.
- **Doc drift it closes:** `product-docs/components/mobile/screens-and-wireflow.md` claims "QR-first
  with manual fallback", while release builds have none (also stated in
  `V-2026-10-07-multi-tenant-closed-testing-demo-app` tester sheet).
- **Plain JSON is less exposed than a link:** not a URL, so neither rewritten by mail gateways
  nor unfurled. The other "secret in a message" rows above apply unchanged.
- **Review before bind is not a prerequisite here:** the user starts the paste, so nothing
  contacts a server without consent (unlike a link). Showing the server at the challenge stage
  (wire the existing `showServerUrl` prop, every seed source) is enough for lot 0; the pre-bind
  review stays in slice 1.

## Scope

- **In scope:** link format v1, slices 1–2 above, their docs and tests.
- **Out of scope:** changing bind/verify; challenge inside the link; Ezkey-sent email;
  invitation issuing by integrating apps; central relay domain (b′) unless explicitly decided;
  changing the QR content in slice 1 (stays JSON, see open questions).

## Key assumptions

- Read-once bind + TTL are an acceptable bound for a bearer token in chat or mail history.
- Users and operators accept "paste the link" as the fallback when a client does not linkify
  custom schemes.
- A shared `ezkey` scheme is wanted (openness to third parties) despite collision risk.

## Risks and exceptions

- URL-rewriting gateways may defeat the fragment protection (to verify before claiming it).
- A link makes the attacker-instance phishing cheaper; the review step mitigates, it does not
  prevent.
- Interception by a scheme-claiming app can block an invitation (operator reset). It cannot take
  it over without the challenge, which is one shot.
- The one-shot challenge also means a typo on the same device burns the invitation; the
  verify-stage copy should say so before the user submits.
- Fail-closed: a link with an unknown `v`, a malformed field or an `authUrl` the policy rejects is
  refused, as for QR today.

## Open questions for Marc

1. **Scheme:** one shared `ezkey://` for every authenticator, or per-app schemes behind the
   landing page chooser?
2. **Landing page host (slice 2):** Auth API public host via Caddy, the Admin UI host, or a
   separate configurable URL? Acceptable as a self-hosting requirement?
3. **Central relay (b′):** rule out explicitly?
4. **Challenge channel:** recommend only, or should the Admin UI refuse to copy link and code
   together?
5. **QR content:** later switch the QR to the link form (a phone camera then opens the
   authenticator directly), once apps in the field parse both?
6. **"Send":** copy only, or a `mailto:` too?
7. **Priority:** P2 (unblocks the Open Authenticator tracer bullet and desktop), or P3 until that
   tracer bullet starts?

## Promotion notes

Ready for a `TB-*` when questions 1 and 4 are answered, the review-before-bind step has a
wireflow (`MOBILE_SCREENS_AND_WIREFLOWS.md`), and the Safe Links behaviour is verified or the claim
is dropped. A `critical-review` applies if the priority is set to P1.

## Links

- Vision:
  [`V-2026-10-27-third-party-authenticator-integration-kit`](../../vision/V-2026-10-27-third-party-authenticator-integration-kit.md)
- Email sending now has its own program: [`V-2026-11-04-connector-spi`](../../vision/V-2026-11-04-connector-spi.md)
  (`#1206`). It stays out of scope here, and a link is its natural later complement.
- Related ideas:
  [`I-2026-07-17-desktop-authenticator-reference-app`](I-2026-07-17-desktop-authenticator-reference-app.md),
  [`I-2026-06-18-demo-device-qr-auth-url-parity`](I-2026-06-18-demo-device-qr-auth-url-parity.md),
  [`I-2026-0010-phone-to-phone-enrollment-transfer`](I-2026-0010-phone-to-phone-enrollment-transfer.md),
  [`I-2026-08-29-admin-api-remove-m2m-hatch`](I-2026-08-29-admin-api-remove-m2m-hatch.md)
- Contract and behaviour:
  [`docs/ENDPOINT.md`](../../../../docs/ENDPOINT.md),
  [`docs/CLIENT_BEHAVIOUR_CONTRACT.md`](../../../../docs/CLIENT_BEHAVIOUR_CONTRACT.md),
  [`docs/MOBILE_DEVELOPER_GUIDE.md`](../../../../docs/MOBILE_DEVELOPER_GUIDE.md),
  [`docs/SECURITY_POSTURE.md`](../../../../docs/SECURITY_POSTURE.md)
- Mobile:
  [`MOBILE_APP_STATE_MACHINE.md`](../../../../ezkey_mobile/docs/MOBILE_APP_STATE_MACHINE.md) §1,
  [`MOBILE_APP_STRATEGIC_ORIENTATION.md`](../../../../ezkey_mobile/docs/MOBILE_APP_STRATEGIC_ORIENTATION.md) finding 12
- Code: `QrCodePayloadService`, `QrCodeProperties`, `EnrollmentBindService`,
  `EnrollmentVerifyService`, `ezkey_dart` `EnrollmentCode` / `EzkeyUrlPolicy`,
  `ezkey_mobile/app/utils/qrPayload.ts`, `EnrollmentWizardScreen.tsx`
- Related security fix: #1180 (proof token exposed only while `CREATED`)
