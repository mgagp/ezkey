---
audience: "Technically engaged readers: backend developers, architects, security teams, and integrators who want to inspect a real Ezkey flow with its signatures, state transitions, and verification points. Not a first-touch product-discovery audience."
planned_slug_en: "technical-trace-ezkey-enrollment-authentication.html"
planned_canonical_en: "https://ezkey.org/technical-trace-ezkey-enrollment-authentication.html"
planned_slug_fr: "technical-trace-ezkey-enrollment-authentication.html"
planned_canonical_fr: "https://ezkey.org/fr/technical-trace-ezkey-enrollment-authentication.html"
status: published
published_html_en: /technical-trace-ezkey-enrollment-authentication.html
published_html_fr: /fr/technical-trace-ezkey-enrollment-authentication.html
published_date: 2026-10-05
html_amended_post_publish: false
source_of_truth: html
---

# A technical trace of one complete Ezkey flow

<!-- ezkey-org:exclude-start
Placement recommendation:
- Lane: Articles -> Craft & engineering, not Guides and not the landing page.
- This should be linked intentionally for readers who already have product context.
- Good companion entry points later: API docs, Source & evaluation, or a shorter conceptual article on why Ezkey signs every step.

Positioning note:
- Start as one standalone technical article, not a brand-new public site section.
- If we later publish 2-3 protocol walkthroughs (accept, deny, challenge, API-key posture), revisit a small series model or a lightweight technical sub-index inside Articles.
- Do not surface this as a first-touch homepage destination.

Drafting posture:
- This article comes from a validated local didactic run and may intentionally publish the full transcript, including bearer tokens, proof tokens, private keys, and signatures, because the stack was a disposable local Docker instance dedicated to this single publication exercise and is now dead.
- Keep it readable for expert readers anyway: explicit trust-chain explanation, clear sectioning, and enough editorial framing that the detail feels intentional rather than dumped.
 ezkey-org:exclude-end -->

*Technical note · October 5, 2026*

Many strong-authentication texts promise cryptography. Fewer show, in a readable way, **where cryptography actually enters the flow**, **what is signed**, **which key verifies it**, and **when the backend or the mobile client should decide to trust what they just received**.

Ezkey lends itself unusually well to that kind of article because the product is deliberately **backend-first**. Useful state remains on the server, meaningful transitions are exposed through APIs, and the mobile client does not merely display an approval request: it participates in an explicit cryptographic chain.

What follows is not a general product tour. It is a **commented technical trace** of one fully validated local-stack run: enrollment issuance, device binding, signed instance branding, and a completed authentication request. The goal is not to publish raw JSON for its own sake. The goal is to make the protocol legible without flattening away the details that actually matter.

## Who this is for

This text is written for readers who already have the right kind of context:

- backend developers who want to understand what they would really integrate;
- architects evaluating the readability of a proprietary MFA protocol;
- security teams looking at continuity between enrollment and authentication;
- technically curious readers who are already past the “what is Ezkey?” stage.

If you are discovering Ezkey for the first time, this is probably not the right entry point. The purpose here is different: **show one real flow at technical resolution**, not summarize the whole product in beginner-friendly terms.

## What this trace shows, and what it does not

The scenario is intentionally plain.

An authenticated operator uses the **Admin API** to create an enrollment tied to one integration. A simulated device then performs **bind**, **verify**, **pending**, and **respond** against the **Auth API**, with the **Crypto API** used only as a lab oracle to rebuild canonical strings and verify signatures.

The use case is equally simple: **a user approves access to an integration**. No business-heavy message, no payment approval, no extra storytelling layer. The point is to let the protocol speak for itself.

Not shown here:

- the bootstrap of the administrative passwordless login;
- the `deny` branch;
- a `challengeRequired=true` branch;
- or an integration-API-key-led issuance posture.

## Why publish the secrets at all?

Normally, publishing bearer tokens, proof tokens, private keys, and signatures in full would be irresponsible.

This article is an intentional exception. The demonstrated flow was produced on a **disposable local Docker stack** dedicated to the article itself. That stack is already gone. Nothing published here is a still-live credential or a reusable secret. The open-book transcript is part of the value: it lets the reader inspect the protocol at full resolution instead of trusting a curated summary.

## How to read the detailed trace

The detailed trace below is the validated run itself. It keeps the exact request/response blocks and cryptographic helper steps.

The key reading pattern is simple:

1. identify who is acting;
2. identify the canonical string being signed;
3. identify which key verifies that step;
4. identify what state transition or trust decision becomes valid only after that verification.

Read that way, the trace is more than a transcript. It becomes a visible argument for the kind of backend-first MFA Ezkey is trying to build.
