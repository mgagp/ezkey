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
Frostude today): **surface a feedback address** already declared in privacy / project contact, and
optionally hand off to the user’s own mail client via `mailto:` / Android send-to-mail.

## Motivation

Someone suggested in-product feedback. Today neither Admin UI nor the Android app makes the contact
path obvious. Capacity is thin: SMTP-assisted delivery for **enrollment** (`V-2026-0005` /
`I-2026-0023`) is still unfinished; feedback must not invent a second mail pipeline or an in-app
form that posts to a backend Ezkey does not operate.

Adopter posture (product intent): Ezkey gets out of the way. Feedback chrome must stay minimal —
discoverable, not sticky, not “engage with us” theater.

## Product locks (working — discuss before promote)

### What this is / is not

1. **Channel = human email**, not tickets, chat, NPS, or in-product analytics.
2. **Not** the same problem as enrollment email (`V-2026-0005`). Feedback does **not** wait on SMTP
   in Admin API / Admin UI.
3. **Not** a reason to add Instabug / Sentry User Feedback / GitHub-issue-from-app / custom form API
   in Phase 0–1.
4. Canonical project addresses (from ezkey.org privacy / contact, 2026-09-23):
   - **Feedback / general:** `support@ezkey.org`
   - **Privacy requests only:** `privacy@ezkey.org` (do not conflate feedback CTA with privacy inbox)
   Self-hosted installs may later point the same UI at **their** contact address (config); community
   alpha uses `support@ezkey.org`.

### Phased delivery (pragmatic)

| Phase | What | Surfaces | Depends on Ezkey mail? |
| ----- | ---- | -------- | ---------------------- |
| **0** | **Documentary / copy** — show the feedback address (and privacy link) so the path is knowable. Copy-to-clipboard OK. | Admin UI About (or footer help); mobile Settings (Engrenage) near À propos / Nouveautés | No |
| **1** | **Handoff to user mail client** — `mailto:` (Admin React) and Android `ACTION_SENDTO` / RN `Linking.openURL('mailto:…')` (or equivalent already in stack). Prefill subject only; body left to the user. Graceful if no mail app. | Same entry points as Phase 0 | No |
| **2+** | Deferred — optional install-config contact override; only revisit in-app form / SMTP-backed receipt if product capacity and honesty of claims justify it | TBD | Likely yes — **out of scope now** |

**Prioritize Phase 0 first** (both surfaces if cheap; Admin UI alone is already better than today).
Phase 1 is a thin craft pass when Phase 0 wording is locked — still no backend.

If capacity says “not worth it,” **park after Phase 0 docs** (site + privacy already enough for some
users) and do not open Phase 1 until a real evaluator signal asks for in-app handoff.

### UI placement (orientation)

- **Admin UI:** discreet entry — About / help / footer note (“Envoyer un commentaire” → address or
  mailto). Avoid floating always-on widgets.
- **Mobile (Android first):** Settings gear row alongside À propos / Nouveautés — e.g. « Donner un
  feedback » or « Commentaire ». FR operator wording Julie when executing.
- Zero jargon; no promise of SLA / response time.

### Common platform patterns (context, not mandates)

- **React Admin:** plain link `mailto:addr?subject=…`, or button that opens the same; fallback show
  address + copy. No new dependency required.
- **React Native / Android:** `Linking.openURL('mailto:…')` is the usual minimal path; native
  `Intent.ACTION_SENDTO` with `mailto:` is the platform idiom. Prefer stack already present
  (React Navigation / Linking) over a new mail library. Do **not** pull a full email composer SDK.

Craft (Patrick, via Mathieu when funded): confirm one-liner plan for Phase 1 on current RN stack;
Justin only if device verify needed.

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

## Consequences (when executed)

| Surface | Phase 0 | Phase 1 |
| ------- | ------- | ------- |
| **Canon address** | Single source: privacy / project contact (and optional later install config). | Same |
| **Admin UI** | Julie: About / help shows address (+ copy). | `mailto:` handoff |
| **Mobile** | Julie / Justin: Settings row shows address. | `mailto:` / Linking handoff |
| **ezkey.org** | Audrey only if public copy needs the same address visible outside apps. | — |
| **Backend** | None | None |
| **Security** | No new PII path in Phase 0–1 (user’s mail client owns the send). | Same |

## Non-goals

- Implementing Phase 1+ in this vision PR.
- Building Ezkey-operated support inbox or helpdesk.
- iOS-specific work until Android path is settled (Android-first today).

## Promotion criteria

1. Marc locks Phase 0 vs park, and the canonical feedback address string (from privacy / contact).
2. Alex + Mathieu agree Phase 1 is still worth a thin craft pass (or explicitly park).
3. Spawn hygiene PR or light `I-*` only if Phase 1 needs cross-surface coordination; Phase 0 can be
   hygiene commits + this V-* promoted.

## Next step

Discuss and lock Phase 0 address + placement. Docs-only until Marc says go on UI. Patrick craft
brief only for Phase 1 mailto/RN handoff — via Mathieu, no agent ping-pong. Alex does not implement.
