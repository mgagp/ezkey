# Vision Note — `V-2026-09-27-operator-and-mobile-feedback-channel`
# Pragmatic feedback channel (Admin UI + mobile) without an Ezkey mail stack

## Metadata

- **ID:** `V-2026-09-27-operator-and-mobile-feedback-channel`
- **Status:** `draft`
- **Lane:** hygiene / operator clarity (not engagement / stickiness)
- **Created at:** `2026-09-27`
- **Updated at:** `2026-09-27`
- **Captured by:** Marc / Alex
- **Priority:** `P3` (comfort; safe to defer behind Mode C / alpha funnel; worth a thin Phase 0)

## Intent

Give operators and mobile users a **clear, honest way to send feedback** to the project (or to the
installer’s contact), without pretending Ezkey is a support desk and **without** requiring outbound
SMTP, a ticket inbox, or a third-party feedback SaaS in the product.

Default posture for community alpha and for self-hosted installs that have no mail stack (e.g.
Frostude today): **surface a feedback address** already declared in privacy / project contact.
Handoff to the user’s own mail client (`mailto:` / Android send-to-mail) remains a possible later
thin craft pass — **not** authorized by the current product lock (see settled locks below).

## Motivation

Someone suggested in-product feedback. Today neither Admin UI nor the Android app makes the contact
path obvious. Capacity is thin: SMTP-assisted delivery for **enrollment** (`V-2026-0005` /
`I-2026-0023`) is still unfinished; feedback must not invent a second mail pipeline or an in-app
form that posts to a backend Ezkey does not operate.

Adopter posture (product intent): Ezkey gets out of the way. Feedback chrome must stay minimal —
discoverable, not sticky, not “engage with us” theater.

## Product locks (settled — 2026-09-27)

Settled by Marc (2026-09-27) via Alex. Do not reopen Phase scope in execution unless a new product
lock supersedes this section.

1. **Phase 0 only** — show a **visible feedback address** in Admin UI (About / help / footer) and
   mobile Settings (near À propos / Nouveautés). Copy-to-clipboard is OK. No backend; no form POST.
2. **Canonical feedback address:** `support@ezkey.org` (community alpha / project contact).
3. **Privacy inbox stays separate:** `privacy@ezkey.org` for privacy requests only — do **not**
   conflate the feedback CTA with the privacy inbox.
4. **Phase 1 deferred** — `mailto:` (Admin React), Android `ACTION_SENDTO` / RN
   `Linking.openURL('mailto:…')` is **not** authorized by this lock. Keep as a possible later thin
   craft pass; require a **new product lock** before any Phase 1 work.

This note records settled locks and orientation for a later hygiene execution slice. It does **not**
authorize application implementation in this vision PR.

## Orientation (still open for craft)

### What this is / is not

1. **Channel = human email**, not tickets, chat, NPS, or in-product analytics.
2. **Not** the same problem as enrollment email (`V-2026-0005`). Feedback does **not** wait on SMTP
   in Admin API / Admin UI.
3. **Not** a reason to add Instabug / Sentry User Feedback / GitHub-issue-from-app / custom form API
   in Phase 0 (nor in deferred Phase 1 until locked).
4. Self-hosted installs may later point the same UI at **their** contact address (config) — Phase 2+;
   community alpha uses `support@ezkey.org`.

### Phased delivery (pragmatic)

| Phase | What | Surfaces | Status | Depends on Ezkey mail? |
| ----- | ---- | -------- | ------ | ---------------------- |
| **0** | **Documentary / copy** — show the feedback address (and privacy link) so the path is knowable. Copy-to-clipboard OK. | Admin UI About (or footer help); mobile Settings (Engrenage) near À propos / Nouveautés | **Locked** (2026-09-27) | No |
| **1** | **Handoff to user mail client** — `mailto:` (Admin React) and Android `ACTION_SENDTO` / RN `Linking.openURL('mailto:…')` (or equivalent already in stack). Prefill subject only; body left to the user. Graceful if no mail app. | Same entry points as Phase 0 | **Deferred** — possible later thin craft pass; needs a new product lock | No |
| **2+** | Optional install-config contact override; only revisit in-app form / SMTP-backed receipt if product capacity and honesty of claims justify it | TBD | Deferred / out of scope now | Likely yes |

Execute **Phase 0 only** under the current lock (both surfaces if cheap; Admin UI alone is already
better than today). Do **not** open Phase 1 until Marc issues a new product lock (e.g. after a real
evaluator signal asks for in-app handoff).

### UI placement (orientation)

- **Admin UI:** discreet entry — About / help / footer note (“Envoyer un commentaire” → address;
  copy OK). Avoid floating always-on widgets. No `mailto:` until Phase 1 is locked.
- **Mobile (Android first):** Settings gear row alongside À propos / Nouveautés — e.g. « Donner un
  feedback » or « Commentaire ». FR operator wording Julie when executing.
- Zero jargon; no promise of SLA / response time.

### Common platform patterns (context for deferred Phase 1 — not mandates)

- **React Admin:** plain link `mailto:addr?subject=…`, or button that opens the same; fallback show
  address + copy. No new dependency required.
- **React Native / Android:** `Linking.openURL('mailto:…')` is the usual minimal path; native
  `Intent.ACTION_SENDTO` with `mailto:` is the platform idiom. Prefer stack already present
  (React Navigation / Linking) over a new mail library. Do **not** pull a full email composer SDK.

Craft (Patrick, via Mathieu when funded — **only after a new Phase 1 lock**): confirm one-liner plan
on current RN stack; Justin only if device verify needed.

## Relationship to email integration

| Artifact | Relationship |
| -------- | ------------- |
| [`V-2026-0005-email-integration-strategy`](V-2026-0005-email-integration-strategy.md) | **Orthogonal** — optional SMTP for enrolment / activation delivery. Feedback must not block on it or reuse it as an inbox. |
| `I-2026-0023` | Enrollment mail R1 — do not expand into feedback tickets. |

## Rejected intentions (for now)

- In-app feedback **form** that POSTs to Admin API / a new endpoint (spam, retention, no operator
  inbox story).
- Third-party feedback / session-replay SDKs for “send feedback.”
- Bundling feedback with enrollment SMTP work.
- Turning feedback into engagement / retention product work.
- Claiming support SLA in alpha.

## Consequences (when Phase 0 executed)

| Surface | Phase 0 (locked) | Phase 1 (deferred) |
| ------- | ---------------- | ------------------ |
| **Canon address** | `support@ezkey.org` (privacy stays `privacy@ezkey.org`) | Same, if later locked |
| **Admin UI** | Julie: About / help shows address (+ copy). | `mailto:` handoff — not authorized yet |
| **Mobile** | Julie / Justin: Settings row shows address. | `mailto:` / Linking handoff — not authorized yet |
| **ezkey.org** | Audrey only if public copy needs the same address visible outside apps. | — |
| **Backend** | None | None |
| **Security** | No new PII path (address display + optional clipboard only). | User’s mail client would own the send |

## Non-goals

- Implementing Phase 0 or Phase 1 in this vision PR.
- Phase 1 mailto / Linking / ACTION_SENDTO under the current lock.
- Building Ezkey-operated support inbox or helpdesk.
- iOS-specific work until Android path is settled (Android-first today).

## Promotion criteria

1. ~~Marc locks Phase 0 vs park, and the canonical feedback address string~~ — **done** (2026-09-27:
   Phase 0 + `support@ezkey.org`).
2. Phase 0 hygiene PR lands (Admin UI + mobile Settings address visibility).
3. Phase 1 stays parked until a **new** product lock; then Alex + Mathieu agree a thin craft pass is
   worth it (or explicitly park again). Spawn light `I-*` only if Phase 1 needs cross-surface
   coordination.

## Next step

Phase 0 hygiene execution (visible `support@ezkey.org` + copy) when Marc says go on UI — separate
branch from this vision PR. No Phase 1 craft brief until a new lock. Alex does not implement.
