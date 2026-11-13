# Posture alignment inventory — issue #984

- **Issue:** [#984](https://github.com/mgagp/ezkey/issues/984) — align project presentation with the
  2026-10-22 posture compass
- **Step:** 1 of 4 (inventory only). No passage is rewritten here.
- **Next:** Audrey (communication) drafts rewrites → Alex (product direction) validates → Marc
  reviews → one docs PR.
- **Baseline:** `main` at `9076cb4c` (2026-10-22). Line numbers refer to that commit.
- **Out of scope:** the ezkey.org site (`sites/**`); already-published security and interaction
  design decisions (they stay as documented — this inventory only flags presentation wording
  around them).

## Compass (as given, 2026-10-22)

1. **Lab first.** Ezkey's main value is learning and demonstrating AI-first, multi-agent software
   engineering.
2. **One realistic niche.** Passwordless, strong-crypto access protection for a company's
   **internal apps** (billing, metrics, admin or process consoles) used by a **limited internal
   staff** that has no other reason to pay for a platform like Duo.
3. **Not targeted:** the general public; enterprises already equipped with Duo (or similar).
4. **Mass-market adoption is less likely.** SOC 2 would be a prerequisite for the niche — a big
   assumption, not a plan.
5. **Favour** simple self-hosting, small internal audiences, no password rotation.
6. **Build on** the alpha posture already announced
   ([`V-2026-09-26-public-alpha-posture-closeout`](../../product-docs/global/vision/V-2026-09-26-public-alpha-posture-closeout.md)).
7. **Tone:** honest and positive, realistic, not a denial of possible broader success.

Compass items are referenced below as **C1–C7**.

## Summary of main gaps

1. **The lab-first value is missing from every top-level surface** (C1). `README.md`,
   `product-intent.md`, `PROJECT_POSITIONING.md`, `product-vision.md` and the methodology README
   never say that learning and demonstrating AI-first, multi-agent engineering is the project's
   main value. Today it only shows up indirectly in `SECURITY.md`, the alpha closeout note,
   `product-docs/README.md` and `team-roles.md`.
2. **The internal-apps niche is never named** (C2, C5). The audience is generic ("developers",
   "teams that want…", "organizations that want…"). Nothing says "internal consoles for a small
   staff", and nothing says that a company already on Duo should keep it.
3. **Passwordless and "no password rotation" are not stated as values** (C2, C5). Top-level copy
   leads with "cryptographic MFA platform". Passwordless only appears in feature docs and in the
   demo app README, even though admin login itself is passwordless.
4. **The SOC 2 assumption is unstated** (C4). Canon says SOC 2 is a non-goal and forbids
   "working towards SOC 2" language. That is consistent with "not a plan", but nothing says that
   the niche would likely require it. The rewrite has to add that honestly without breaking the
   normative-posture lock.
5. **The alpha posture is inconsistent across surfaces** (C6). The roadmap still calls the
   September 2026 compass "current", `product-vision.md` doesn't mention alpha at all, and the
   README mobile status may be out of date relative to Play closed testing.
6. **Simple self-hosting is not foregrounded** (C5). The README "Quick Start" is the developer and
   test clean-start from `ezkey-tests/`, so a small internal team has no obvious self-host entry
   point.
7. **Legacy overclaims remain in older technical docs**: "PRODUCTION READY", "Enterprise Ready",
   the EasyKey brand recommendation and the "PassKey parallel". These are low traffic but
   contradict the alpha posture and product identity.

## A. Passages that conflict with or under-serve the compass

Priority: **P1** = landing or canonical product surface (read first by visitors and cold agents);
**P2** = secondary presentation canon; **P3** = legacy or low-traffic text.

### A.1 GitHub landing — `README.md`

| # | file:line | Current wording (short quote) | Issue | Proposed direction | Prio |
|---|-----------|-------------------------------|-------|--------------------|------|
| R1 | `README.md:7` | "an open-source cryptographic MFA platform built as a distinct alternative to browser-centric authentication models" | The opening line positions Ezkey as a general MFA platform. It names neither the lab-first value (C1) nor the niche (C2). | Open with two short sentences: (1) Ezkey is a lab project, built in the open as a demonstration of AI-first, multi-agent engineering; (2) its realistic use is passwordless, strong-crypto protection of a company's internal apps for a small staff. Keep the "distinct protocol, not FIDO2/WebAuthn" line (R2) right after. | P1 |
| R2 | `README.md:9` | "It does not implement FIDO2 or WebAuthn…" | Aligned in substance (published identity). Only a placement question. | Keep the wording. It moves below the new lab and niche lines. | — |
| R3 | `README.md:11` | "designed for developers, especially backend developers, who want a self-hosted MFA system…" | The audience is generic developers (C2). It doesn't say "internal apps, small staff, no Duo budget", and it says "MFA" rather than passwordless (C5). | Reframe the audience as "a small team that protects its own internal consoles (billing, metrics, admin or process tools) and has no other need for a paid platform like Duo". Developers stay as the integrators of that niche. | P1 |
| R4 | `README.md` (no line; absent) | — | There is no "who it is not for" statement (C3), and no positive note that Duo-equipped enterprises are well served elsewhere. | Add a short "Who it is for / not for" block: not the general public; if you already run Duo or similar, keep it. Keep the tone positive, not dismissive (C7). | P1 |
| R5 | `README.md` (no line; absent) | — | Nothing names the main project value: learning and demonstration of AI-first, multi-agent engineering (C1). | Add a short "Why this project exists" paragraph that points to `product-docs/methodology/README.md` and `product-docs/global/team-roles.md` as evidence of how the work is done. | P1 |
| R6 | `README.md:17` | "Ezkey is in **public alpha**…" | Aligned (C6). Status reads as a release-engineering note, though, not as posture. | Keep. Optionally add one line that links the closeout note's honesty bounds (lab · discreet · alpha ≠ prod · no SLA). | P2 |
| R7 | `README.md:21` | "**Mobile app:** still in **internal testing** — not on app stores." | Possibly stale: the posture closeout (lock 3, amended 2026-10-08) and `MOBILE_PLAY_PUBLISHING.md` describe a closed-testing track toward Play production for the mobile app. | Verify the current Play track with Marc or Justin, then state it consistently with V-2026-09-26 lock 3. Mobile can be production while the platform stays alpha. | P2 |
| R8 | `README.md:31`, `:47–49` | "`Self-hosted stack` for local development, demonstrations, and integration work." / "The supported local **stack** path is the Bash clean start from `ezkey-tests`" | Self-hosting is only presented as a dev/test path. Nothing signals "simple self-hosting for a small internal team" (C5). | Name simple self-hosting as a goal and point to the operator path (`docs/OPERATIONAL.md`, `docker/README.md`). If no simple self-host path exists yet, say so honestly instead of implying one. | P2 |
| R9 | `README.md:102–104` | "## Security and Compliance … aims to move in a direction that is favorable to operator-visible operational discipline, without claiming certification" | The "Compliance" heading invites an enterprise reading. Nothing says SOC 2 would likely be a prerequisite for the niche (C4). | Rename to "Security posture". Keep the no-certification claim, and add one honest line: a company adopting Ezkey for internal apps would likely expect a SOC 2 report; that is an open assumption, not a plan. | P2 |
| R10 | GitHub repo "About" (not a file) | "ezkey – Rethinking MFA with Simplicity and Security" | Generic and slightly grand framing (C7). It is a repository setting, not repo text. | Suggest a short About line, e.g. "Lab project: passwordless, strong-crypto access for internal apps — built AI-first, multi-agent." Marc applies it in GitHub settings. | P3 |

### A.2 Product intent and positioning (canonical)

| # | file:line | Current wording (short quote) | Issue | Proposed direction | Prio |
|---|-----------|-------------------------------|-------|--------------------|------|
| P1 | `product-docs/global/product-intent.md:9` | "Ezkey is an open-source cryptographic MFA platform created as a distinct alternative…" | Same as R1: no lab-first value (C1), no niche (C2). This is the canonical product identity, so the README must follow it. | Add a "Project nature" lead: lab first (AI-first, multi-agent engineering), with one realistic product niche. Keep the distinct-protocol sentence. | P1 |
| P2 | `product-docs/global/product-intent.md:11` | "Ezkey exists for teams that want strong authentication with direct backend control…" | "Teams" is unbounded (C2, C3). | Narrow it to "small internal teams protecting their own internal apps", and keep the backend-first rationale. | P1 |
| P3 | `product-docs/global/product-intent.md:15–24` | Problem statement: "how to provide a strong, modern MFA path that is: practical for backend teams…" | A generic MFA problem. The niche problem is missing: internal consoles protected by shared or rotated passwords, or by nothing, because a paid platform isn't justified for a few staff (C2, C5). | Restate the problem around the niche (internal apps, limited staff, no Duo budget, no password rotation burden) and keep the developer-practicality bullets as secondary. | P1 |
| P4 | `product-docs/global/product-intent.md:37–46` | "Primary audience — Backend developers integrating MFA into existing systems…" | Primary audience lists roles, not the adopter shape (C2). | Make the primary adopter "a company protecting internal apps for a small internal staff". Developers and operators become the people inside that adopter. Add a lab-audience line: people learning AI-first, multi-agent engineering (C1). | P1 |
| P5 | `product-docs/global/product-intent.md:48–51` | "Organizations that want a developer-friendly MFA option without depending on a closed vendor model." | Open-ended "organizations" leans toward broad enterprise reach (C3). | Replace with an explicit "not targeted" note: the general public, and enterprises already equipped with Duo or similar. Optionally keep "broader adoption is possible but not assumed" (C7). | P1 |
| P6 | `product-docs/global/product-intent.md:172–183` | Non-goals: "… a SOC 2 Type I/II or other formal-attestation program, a feature-maximal identity platform." / "Its goal is narrower…" | The SOC 2 non-goal is consistent with C4 ("not a plan") but doesn't say the niche would likely need it. The "narrower" goal is still generic. | Keep the non-goal. Add one honest boundary line: "Real adoption in the internal-apps niche would likely require a SOC 2 report; that is an open assumption, not a plan." Restate the narrower goal around the niche. | P1 |
| P7 | `product-docs/global/product-intent.md:185–193` | Success criteria: "developers can understand the trust model…" | Only product criteria. No success criterion for the main value: learning and demonstration (C1). | Add lab success criteria (for example: the engineering method is visible, reproducible and documented; multi-agent delivery stays reviewable and honest) alongside the product ones. | P2 |
| P8 | `product-docs/global/product-intent.md:163–168` | "A maintained **public alpha** community instance runs on **ezkey.online** for evaluation (lab · discreet; alpha ≠ production…)" | Aligned (C6). It is buried at the bottom of "Current Product Surface". | Keep the wording. Consider surfacing it near the top as part of "Project nature". | P3 |
| PP1 | `docs/PROJECT_POSITIONING.md:3–9` | "an open-source cryptographic MFA platform…" / "a parallel path … for teams that want direct control" | The positioning essay has no lab framing (C1), no niche (C2), and no alpha mention (C6). | Add a lab-first opening and an explicit niche paragraph that link product intent as canon, so the essay doesn't duplicate it (one canonical place per concept). | P1 |
| PP2 | `docs/PROJECT_POSITIONING.md:22` | "a coherent alternative for teams that want strong authentication…" | Unbounded "teams" (C2, C3). | Bound it to the internal-apps niche. | P2 |
| PP3 | `docs/PROJECT_POSITIONING.md:100–106` | "## Closing Position … That is the path Ezkey chooses on purpose." | The close is about protocol philosophy only. It misses the lab value and the realistic adoption path (C1, C4, C7). | Close on the honest and positive position: a lab that also aims to be genuinely useful for one niche, open to broader success without assuming it. | P2 |
| PP4 | `docs/README.md:13`, `:22` | "PROJECT_POSITIONING.md for the product thesis and backend-first framing." | The index points newcomers to a thesis without the lab framing. | Once PP1 lands, the description can mention "lab-first posture and niche". Low effort. | P3 |
| PR1 | `PRD.md:15` | "`docs/PROJECT_POSITIONING.md` — strategic positioning essay" | It is a stub, so no conflict. It inherits PP1. | No change unless the description of PROJECT_POSITIONING changes. | — |

### A.3 Vision documents

| # | file:line | Current wording (short quote) | Issue | Proposed direction | Prio |
|---|-----------|-------------------------------|-------|--------------------|------|
| V1 | `product-docs/global/vision/product-vision.md:9–21` | "Current orientation — Keep Ezkey backend-first … Strengthen adoption through clear integration boundaries…" / "**Distribution**: improve integration and SDK/CLI adoption paths." | The "current orientation" doc carries none of C1–C6: no lab value, no niche, no alpha. "Strengthen adoption" and "Distribution" read as growth goals. | Restate the current orientation as the 2026-10-22 compass: lab first, internal-apps niche, alpha, simple self-hosting. Distribution becomes "make the niche path easy" rather than general adoption. | P1 |
| V2 | `product-docs/global/vision/product-vision.md:7` | "It complements the PRD…" | Refers to the PRD as if it were canon; `PRD.md` is a stub that points to product intent. | Reword to "complements product intent". | P3 |
| V3 | `product-docs/global/vision/V-2026-09-26-public-alpha-posture-closeout.md:24–29` | "**Laboratory** — opinionated, experimental, serious engineering; not world-conquest." … "**No IdP parity** — not Duo / Okta / Keycloak…" | Aligned base for C6 and C7, and the anchor to build on. "Laboratory" describes how the work is done, not that learning and demonstrating multi-agent engineering is the main value (C1). The niche (C2) is absent. | Don't rewrite the locks. Add a dated **amendment (2026-10-22)** that records the compass: lab-first value, internal-apps niche, SOC 2 assumption, and "if you have Duo, keep it". This mirrors the existing 2026-10-08 amendment pattern. | P1 |
| V4 | `product-docs/global/vision/V-2026-09-22-exp1-to-ezkey-online-alpha.md:31` | "Continuing to brand the host as “experimental lab” understates product intent…" | Read out of context, this sentence argues against "lab" framing, which C1 now puts first. It is a closed decision record about the **host label** (EXP1 → ezkey.online). It is not the project identity. | Don't reopen the host-label decision (closed uncertainty stays closed). Optionally add a one-line forward note that the 2026-10-22 compass frames the **project** as lab-first, while the host keeps its alpha community label. | P3 |
| V5 | `product-docs/global/vision/V-2026-08-02-mobile-official-play-release-posture.md:14–17` | "a **credible official release** posture … that a long-lived public app needs" | A Play-public app can read as "general public" targeting (C3). In substance it is the companion app for organizations that run Ezkey. | Keep the decision. When store and listing copy is drafted, frame the app as the companion for staff of an organization that runs Ezkey, not a consumer authenticator. The listing itself is outside this repo inventory. | P3 |
| V6 | `product-docs/global/vision/product-orientation-notes.md:36` | "Public alpha posture closeout (lab · discreet · mobile Play production ok; platform stays alpha)" | Index row. Aligned. | Update the row summary only if V3 adds an amendment. | — |

### A.4 Methodology documents (lab / multi-agent framing)

| # | file:line | Current wording (short quote) | Issue | Proposed direction | Prio |
|---|-----------|-------------------------------|-------|--------------------|------|
| M1 | `product-docs/methodology/README.md:1–17` | "Ablation note (2026-08) …" / "Posture: Plan mode first" | The method is the main artifact of the lab value (C1), but the README never says it comes from, and is exercised in, a multi-agent lab with named human or agent roles. Constraint: per root `AGENTS.md` § *Methodology publication boundary*, this file is **published method canon**, so it must not absorb Ezkey delivery detail. | Add a short, method-level "Origin" paragraph: the method was developed and is exercised on Ezkey, a lab project run AI-first with a multi-agent team. Keep Ezkey specifics out and link to them from the repo side. Alex should confirm this respects the publication boundary. | P2 |
| M2 | `product-docs/README.md:7`, `:11` | "designed for healthy collaboration between humans and AI coding agents" / "reusable in any monorepo-based product. Ezkey is the reference instance" | Partly aligned (C1): it names human and AI collaboration, but as a documentation property, not as the project's main value. | Keep. Optionally link to the lab-first statement in product intent once P1 lands. | P3 |
| M3 | `product-docs/global/roadmap.md:13–21` | Milestones `P0-foundations` … `P4-compliance-readiness` | Product milestones only. The lab value (C1) is not represented as a direction. | Don't invent a lab milestone (no new program). Add one sentence in Purpose saying the roadmap serves both the lab value and the niche, and that milestones are not market-readiness promises. | P2 |
| M4 | `product-docs/global/roadmap.md:70–72`, `:102`, `:123` | "**Release-order compass (September 2026 target)**" / "Current funded work remains the September operable-release compass" / "**current release-order compass**" | Stale against the alpha closeout, which superseded the September note as the live gate (C6). | Point to `V-2026-09-26-public-alpha-posture-closeout` as the live compass and mark the September note as historical, matching `AGENTS.md` and `normative-posture.md:22–28`. | P1 |
| M5 | `product-docs/global/roadmap.md:89`, `:116`, `:119` | "Make Ezkey easy to adopt from integrating applications…" / "Ecosystem growth around SDKs…" / "Broader third-party and OSS collaboration surface." | Growth-oriented horizon with no realism qualifier (C4, C7). It doesn't deny possible success, but reads as a mass-adoption plan. | Keep the themes, but frame them as "if real adoption earns it" (consistent with design principle #14, beautiful problems), and tie P3 to the niche path. | P2 |
| M6 | `CONTRIBUTING.md:3` | "This document summarises how we work and the values that guide analysis and design decisions." | Contributors aren't told this is a lab run AI-first with a multi-agent team (C1). | Add one sentence about the lab nature and how human and agent roles work (link `team-roles.md`). | P3 |

### A.5 Other project-presentation text

| # | file:line | Current wording (short quote) | Issue | Proposed direction | Prio |
|---|-----------|-------------------------------|-------|--------------------|------|
| O1 | `product-docs/global/normative-posture.md:3–5`, `:52–55` | "Operational discipline that a self-hoster's GRC team — or a future auditor — could map onto SOC 2…" / "No 'working towards SOC 2 certification' language…" | Consistent with C4 ("not a plan"). The rewrite of P6 and R9 must add the niche assumption **without** reading as "working towards SOC 2". | Keep the locks. Add one clarifying line: naming SOC 2 as a likely prerequisite of the niche is an honest assumption, allowed; claiming progress toward it is not. This is wording guidance for Audrey. | P2 |
| O2 | `docs/SECURITY_POSTURE.md:147` | "Self-hosted, opinionated, stronger than passwords and classic TOTP for backend-oriented contexts." | An honest claim that is already published. It could name the niche ("internal apps") instead of "backend-oriented contexts". | Security claims stay as documented. At most, Alex may approve an equivalent wording that names the internal-apps context. Otherwise leave it. | P3 |
| O3 | `product-docs/global/operator-alignment-guide.md:38`, `:62` | "### All-in-one (simplified PME)" / "**Marketing / presales:** … captures client-specific choices" | "PME" is French in an English corpus. "Marketing / presales … client-specific" implies a sales motion that the lab posture (discreet, no marketing campaign) doesn't have (C6, C7). | Rename to "simplified SME / small team". Reframe the presales bullet as "adopter-facing explanation" with no sales connotation. | P3 |
| O4 | `product-docs/global/operator-alignment-guide.md:56` | "Richer org chart, separation of duties, multi-tenant production" | The segmented geometry hints at larger organizations. It is a valid design shape, but it is not the realistic niche (C2). | Keep the geometry (published design). Add a note that all-in-one is the expected shape for the niche and that segmented exists for when a larger internal setup needs it. | P3 |
| O5 | `docs/ECOSYSTEM_REPOSITORIES.md:26`, `:48–49`, `:58` | "so corporate IdP/sync stories stay clear" / "Directory ↔ Ezkey enrollment (Microsoft AD)" / "the corporate or operator problem" | The planned AD/LDAP shells suggest enterprise IdP integration (C3). They could fit a company syncing internal staff, but they read as an enterprise roadmap. | Keep the naming rules. Frame the shells as optional, internal-staff conveniences that are placeholders until real demand appears, and keep the existing "no timelines" honesty. | P3 |
| O6 | `ezkey_mobile/docs/MOBILE_POSITIONING.md:23` | "a demonstration-quality product surface for prospective adopters" | Neutral. "Prospective adopters" is unbounded but harmless. Line 43 ("pilots and internal rollout") already fits C2. | Optionally specify "organizations protecting internal apps". Low priority. | P3 |
| O7 | `ezkey_mobile/PRD.md:9` | "makes Ezkey enrollments and authentication approvals effortless for end users, while showcasing the Ezkey platform for prospective adopters" | Historical v1 PRD. "End users" could be read as the general public (C3). | Optional historical banner or one-line clarification: end users are staff of the organization running Ezkey. | P3 |
| O8 | `docs/BRANDING_NAMING_ANALYSIS.md:13`, `:349` | "adopting 'EasyKey' as the public-facing brand name" / "Clear, memorable name with strong PassKey parallel" | Mass-market marketing framing (C3, C7). The "PassKey parallel" contradicts the published product identity (not a passkey variant), and the EasyKey brand was not adopted. | Add a "Historical — not adopted" banner that points to product intent. No rewrite of the body. | P3 |
| O9 | `docs/features/ADMIN_PASSWORDLESS_LOGIN.md:7`; `docs/API_KEYS_IMPLEMENTATION.md:430`; `docs/DATABASE_PARTITIONING_STRATEGY_SUMMARY.md:6`; `docs/plan/README_ARCHIVE.md:12`; `ezkey_mobile/plan.md:4` | "This feature is now PRODUCTION READY and fully deployed." / "✅ **Production Ready**" / "Implemented and Production-Ready" / "now **production ready**" / "Deliver a production-ready React Native app" | Legacy status markers that contradict "alpha ≠ production" (C6). They meant "feature complete". | Replace "production ready" with "implemented" in status lines (mechanical hygiene). Leave the bodies as they are. | P3 |
| O10 | `docs/ADMIN_API_SECURITY_GUIDE.md:875` | "3. **Enterprise Ready:** Recovery codes, rate limiting, audit trail" | Enterprise-readiness claim (C3, C6). | Reword to a neutral capability line, for example "Operational safeguards: recovery codes, rate limiting, audit trail". The documented security behavior is unchanged. | P3 |
| O11 | `docs/analysis/multi-tenancy-strategy.md:1587` | "positioning Ezkey as a production-ready, enterprise-grade MFA solution" | Enterprise overclaim. The file already carries a "Historical analysis (Jan 2025) … not adopted" banner at the top (lines 3–10), so the risk is low. | No change needed beyond the existing banner. Optionally strike the closing sentence. | P3 |

## B. Passages already well aligned

These are anchors that rewrites can reuse or link to. They need no change for #984.

| file:line | Wording (short quote) | Why it fits |
|-----------|-----------------------|-------------|
| `product-docs/global/vision/V-2026-09-26-public-alpha-posture-closeout.md:24–29` | "**Laboratory** … not world-conquest" · "**Discreet** — no marketing campaign" · "**Alpha ≠ production**" · "**No SLA**" · "**No IdP parity**" | The already-announced alpha posture to build on (C6). It is honest without being negative (C7). |
| `product-docs/global/vision/V-2026-09-26-public-alpha-posture-closeout.md:44–49`, `:58–64` | Lock 3 + 2026-10-08 amendment: mobile may reach Play production; "the **rest of the platform** … remains **alpha / laboratory**" | A clear split that keeps the mobile success path open without overclaiming the platform (C6, C7). |
| `SECURITY.md:3` | "a **public, experimental security and MFA laboratory** (community lab, public alpha). It is **not** production-qualified" | Lab-first and alpha in one line (C1, C6). |
| `SECURITY.md:35`, `:67` | "a small community lab project maintained primarily by @mgagp, with AI-assisted review" / "Periodic AI-assisted security and hygiene passes, reviewed by the maintainer" | Shows the AI-assisted engineering practice honestly (C1). |
| `product-docs/global/team-roles.md:3` | "Roles may be played by a human or an AI agent. The owner (**Marc**) decides merges." | Concrete evidence of the multi-agent model (C1). It is a natural link target from README and methodology. |
| `product-docs/global/operator-alignment-guide.md:38–46`, `:66` | All-in-one geometry: "Small team, one integration, pragmatism over role separation" / "For an adopting SME or team, **Ezkey is never the core business**." | Closest existing statement of the niche and of simple, small-audience operation (C2, C5). |
| `product-docs/global/product-intent.md:163–168` | "public alpha community instance … (lab · discreet; alpha ≠ production; no SLA; no IdP parity)" · evaluator self-registration "OFF by default" | Alpha posture and self-host defaults stated honestly (C5, C6). |
| `product-docs/global/product-intent.md:172–181` | Non-goals: not FIDO2 / WebAuthn / browser-first / SOC 2 program / feature-maximal identity platform | Already rules out mass-market and enterprise-suite framing (C3, C4). Only P6's assumption line is missing. |
| `docs/PROJECT_POSITIONING.md:24–36`, `:80` | "Built By a Developer for Developers" / "not … a feature race against broader identity platforms" | Pragmatic, non-competitive tone (C3, C7). |
| `docs/SECURITY_POSTURE.md:115` | "What Ezkey aims to do (planned, if adoption justifies)" | Realistic, conditional ambition. Broader success stays possible without being promised (C7). |
| `docs/SECURITY_POSTURE.md:296–299` | "**Supersede, do not silently inflate**, if the threat model or target market changes (e.g. a move toward regulated/enterprise customers…)" | Already treats enterprise as a different, future posture (C3, C4). |
| `docs/SECURITY_POSTURE.md:103`, `:147–149` | "makes no SOC 2 / formal-attestation equivalence claim" / "SOC 2 remains a **reference vocabulary** … not a certification target" | Honest about SOC 2 (C4). See O1 for how to add the niche assumption without breaking it. |
| `product-docs/global/normative-posture.md:22–28` | "intention-first laboratory / public-alpha posture — not a September P0 release gate" | Correct live compass reference (C6). Use it as the model for M4. |
| `product-docs/global/design-principles.md:33`, `:67–74` | #5 "not for marketing or certification narratives" · #14 "Beautiful problems (defer sophistication until earned)" | Honest, adoption-earned scaling stance (C4, C7). |
| `product-docs/global/vision/V-2026-09-22-exp1-to-ezkey-online-alpha.md:47–49`, `:89–90` | "Alpha is **not** a production SLA. Ezkey is **not** Duo / Okta / Keycloak parity." / "No … 'enterprise support' posture." | Explicit rejection of enterprise and Duo-class positioning (C3). |
| `README.md:17` | "Ezkey is in **public alpha** … **no published release packages** yet" | Honest alpha status on the landing page (C6). |
| `ezkey_mobile/docs/MOBILE_POSITIONING.md:43` | "Secondary audience during pilots and internal rollout." | Already frames evaluation as internal rollout (C2). |
| `ezkey-demo-app-acme/README.md:3` | "Demo application showcasing Ezkey passwordless login integration…" | The only top-of-file "passwordless" statement. It shows the niche pattern concretely (C2, C5) and is a good link target. |
| `product-docs/README.md:7` | "designed for healthy collaboration between humans and AI coding agents" | Partial evidence of C1 (see M2). |

## C. Open questions for Audrey and Alex

1. **Lead word:** should the landing copy lead with "passwordless" or with "cryptographic MFA"?
   Both are true. The compass says passwordless, while published canon says MFA (R1, R3, P1).
2. **Where the lab statement lives:** product intent is canon, and the README and positioning link
   to it. The methodology README gets only a method-level "Origin" line because of the publication
   boundary (M1). Alex should confirm.
3. **README mobile status (R7):** confirm the current Play track before rewriting.
4. **Simple self-hosting (R8):** is there a self-host path today that is simple enough to name for
   a small internal team, or should the copy honestly say it is a goal?
5. **SOC 2 phrasing (P6, R9, O1):** agree on one sentence, used in every surface, that names SOC 2
   as a likely niche prerequisite without reading as "working towards SOC 2".
