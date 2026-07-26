# Tracer Bullet Brief — `TB-2026-07-26-admin-ui-help-corpus-overhaul` Admin UI contextual help corpus — comprehensive FR/EN pass

## Metadata

- **ID:** `TB-2026-07-26-admin-ui-help-corpus-overhaul`
- **Status:** `draft`
- **Related idea:** _(none — direct capture per `minimum-viable-method.md` "execution-ready slice" lane; this brief already resolved the open design questions in an alignment session)_
- **Lane:** `D`
- **Posture:** `single-pass`
- **GitHub issue:** _(none yet — optional; open at start of execution if board visibility helps)_
- **Created at:** `2026-07-26`
- **Updated at:** `2026-07-26`
- **Captured by:** Marc (alignment session, Plan mode)

## Objective

Prove that every Admin UI screen has an accurate, proportionate, role-aware, self-contained
contextual help topic in both English and French — with no route silently falling back to the
generic placeholder — and that the drawer's role-aware rendering is a small generalized mechanism
instead of one hardcoded conditional block per topic.

This is an **editorial and light-refactor** slice, not a feature slice: the contextual help drawer
mechanism (`?` key, `HelpProvider`, `HelpDrawer`, `help` i18n namespace) already exists and is wired
into every route. Coverage is uneven — some topics are well developed, three routes have no topic
at all, and the "global vs tenant" differentiation pattern only exists on two topics today, coded
as one-off conditionals rather than a reusable mechanism.

## Why now — product framing a cold agent must not re-derive

- Ezkey is the deliberate middle path: stronger than passwords, SMS, or email one-time codes;
  explicitly **not** chasing passkey/FIDO2 ceremony or claiming standards equivalence
  (`product-docs/global/product-intent.md` § Non-Goals, § Core Product Requirements #1). Help copy
  must never name competing MFA products and must never imply passkey/FIDO2/WebAuthn equivalence.
- For an adopting organization, Ezkey is **never the core business**
  (`product-docs/global/operator-alignment-guide.md`). Help copy must stay proportionate — enough
  to operate confidently, not an exhaustive manual. Depth calibration matters as much as accuracy.
- Global Admin vs Tenant Admin use the **same UI** with different scope
  (`product-docs/global/product-intent.md` § Target Audience,
  `product-docs/global/operator-alignment-guide.md` § Admin role analogies). Copy should read as
  **one shared narrative per topic**, differentiated only through short, explicitly role-scoped
  asides — not fully separate topic bodies, and not silently wrong for one of the two roles.
- Lifecycle, reversibility, and reason-policy claims must trace back to
  `docs/LIFECYCLE_GOVERNANCE.md` (mandatory reading for this class of task per root `AGENTS.md`),
  not to the current `help.json` prose, which predates parts of that canon and has drifted in
  places (see topic inventory below).

## Boundaries in scope

- `ezkey-admin-ui/src/locales/en/help.json` and `ezkey-admin-ui/src/locales/fr/help.json` — full
  content pass (rewrite/deepen existing topics, add three new topics).
- `ezkey-admin-ui/src/lib/help-topics.ts` — add `auth-attempts`, `alerts`, `alert-detail` to
  `HelpTopicId` and to `resolveHelpTopicId()`; add the small `extraSections` config described below.
- `ezkey-admin-ui/src/components/help/help-drawer.tsx` — generalize the role-aware/extra-section
  rendering (see Component change specification).

## Out of scope

- The sidebar "About EZKey" panel (`ezkey-admin-ui/src/locales/*/layout.json` § `about`,
  `productIntroTenant` / `productIntroGlobal`) — a related but separate mechanism; do not touch.
- Per-field `ContextHelp` popovers (`ezkey-admin-ui/src/components/ui/context-help.tsx`) and their
  call sites (`alerts.tsx`, `audit-logs.tsx`, `enrollment-detail.tsx`, etc.) — separate mechanism,
  separate future pass.
- Any "contact us" / escape-hatch CTA (email, GitHub issue). **Explicitly deferred** — do not add
  this in this slice, in the drawer footer or in any topic body. Revisit as its own later slice
  once the repository's public-opening posture is closer.
- New routes, new lifecycle behavior, new API calls, new permissions. Content and one internal
  rendering refactor only.
- Playwright test additions (see Evidence plan).

## Topic inventory and required action per topic

Every `HelpTopicId` in `ezkey-admin-ui/src/lib/help-topics.ts`, with the action required this pass:

- `default` — light polish only; stays generic (no escape hatch, per Out of scope).
- `login` — review pass; verify wording still matches the current integrated-delivery posture
  (`TB-2026-07-25-integrated-delivery-posture`).
- `dashboard` — review pass; already has `globalContext` / `tenantContext` / `authHealth` — becomes
  the **first reference example** for the generalized extra-sections mechanism.
- `tenants` — deepen. During execution, confirm whether a role-scoped aside is even meaningful here
  (Tenant Admins do not see this screen today) or whether the topic is implicitly Global-Admin-only
  and should say so plainly instead of pretending both roles read it.
- `tenant-detail` — deepen; same Global-Admin-only scoping question as `tenants`.
- `integrations` — deepen; make the "no intermediate Inactive state — only Active/Retired" rule
  explicit (`docs/LIFECYCLE_GOVERNANCE.md` § 3.2, a documented recurring point of confusion).
- `integration-detail` — deepen; align retire/delete guard wording with § 3.2 exactly (RETIRED +
  zero enrollments + reason required for delete).
- `enrollments` — deepen; keep the deactivate-vs-revoke distinction crisp; verify against § 3.3 and
  Scenario 1 (suspected credential compromise).
- `enrollment-detail` — review pass; today's best-developed topic — use its length, structure, and
  tone as the **calibration bar** for the rest of the corpus.
- `api-keys` — deepen; reinforce the "no deactivate, no delete, revoke-and-replace" asymmetry
  (§ 3.4) — API keys differ structurally from every other entity and this is worth stating plainly.
- `api-key-detail` — review pass.
- `admins` — deepen and **extend**. Current body omits: (a) an admin cannot deactivate themselves,
  (b) the minimum-Global-Admin guard (cannot deactivate the last one), (c) activation vs recovery as
  distinct concepts, (d) recovery codes entirely. Add a new `recoveryCodes` extra section
  (audience: `all`) covering issue-initial vs regenerate, and that recovery codes are independent
  from the admin's MFA enrollment (§ 3.5, § 3.8).
- `encryption-keys` — review pass; already has `globalOps` / `developerContext` — becomes the
  **second reference example** for the generalized mechanism.
- `audit-logs` — review pass; already the richest topic (four extra sections) — verify nothing
  drifts when the rendering mechanism is generalized.

New topics to create (these routes currently render the generic `default` placeholder):

- `auth-attempts` (map `/auth-attempts`) — explain this is a mostly read-only, event-driven,
  investigative surface (§ 3.6): attempts are created and resolved automatically from the
  enrollment/integration/tenant eligibility chain, not operator-managed like the other entities.
  Cover the status set (pending, read, accepted, rejected, invalid, expired) and the challenge-code
  field. Cross-reference the existing `audit-logs.mfaAndLogin` explanation rather than duplicating
  it.
- `alerts` (map `/alerts`) — Global-Admin-only investigative surface; severity (CRITICAL, WARNING,
  INFO) vs status (OPEN, RESOLVED, snoozed). Keep short: `alerts.tsx` already has its own inline
  `ContextHelp` for list-specific nuance — this topic should orient, not duplicate that inline copy.
- `alert-detail` (map `/alerts/:alertId`) — what resolving vs snoozing means and when to use which;
  stay short.

## Editorial guardrails (state explicitly, do not leave implicit)

- **Self-contained.** No external links, no "see `docs/ENDPOINT.md`" style references in
  operator-facing bodies. The one existing exception — `encryption-keys.developerContext`
  referencing `docs/REENCRYPTION_OPERATIONS.md` — is a deliberate, clearly separated
  developer-audience aside, not the operator default. Preserve that distinction; do not generalize
  it into the main body, and do not remove it.
- **No escape hatch this round** (see Out of scope).
- **No competing-product names; no standards-equivalence claims** (passkey, FIDO2, WebAuthn).
- **One shared narrative per topic, role-scoped asides only** where a real difference exists —
  matches `operator-alignment-guide.md` § Admin UI copy posture. Use "within your scope" style
  wording where copy would otherwise be scope-dependent and awkward to split.
- **Lifecycle accuracy anchor:** every claim about reversibility, reason requirements, or
  parent-chain blocking must trace to `docs/LIFECYCLE_GOVERNANCE.md` §§ 2–5 — treat that document,
  not the current `help.json` prose, as the source of truth when they disagree.
- **Depth calibration:** proportionate, not exhaustive. Target `enrollment-detail` / `admins`
  length and structure (a short summary line, two to four body paragraphs, at most one or two short
  extra sections) as the bar for every topic, old and new.
- **FR is not a literal translation pass.** Same structure and equivalence, natural French
  phrasing — matches the existing corpus's voice.

## Component change specification

Today `ezkey-admin-ui/src/components/help/help-drawer.tsx` (lines ~106–149) hardcodes three
clusters of `topicId === 'dashboard' && ...`, `topicId === 'encryption-keys' && ...`, and
`topicId === 'audit-logs' && ...` to render extra sections beyond `summary`/`body`. Generalize this:

1. Define a small per-topic config (e.g. exported from `help-topics.ts` or a sibling file) mapping
   `HelpTopicId` to an ordered list of extra-section descriptors:
   `{ key: string; audience?: 'global' | 'tenant' }[]`. Omit `audience` for sections shown to both
   roles (e.g. `dashboard.authHealth`, `audit-logs.eventTypeVsStatus`).
2. In `HelpDrawer`, replace the three hardcoded clusters with one loop over that topic's configured
   sections, rendering each with ``t(`${base}.${section.key}`)`` and filtering by `isGlobalAdmin`
   when `audience` is set — same visual treatment as today (border-top divider; muted vs regular
   text per section, matching current per-section styling).
3. Preserve `demoExtra` as its own special case — unrelated to this refactor, already generic.
4. Extend the config to cover the new role-scoped asides added in this pass (`tenants`,
   `integrations`, `enrollments`, `admins`, `api-keys` where a real role difference exists) so they
   render through the same mechanism as `dashboard` and `encryption-keys`, not new one-off blocks.

## First executable slice

1. Re-read `docs/LIFECYCLE_GOVERNANCE.md`, `product-docs/global/product-intent.md`, and
   `product-docs/global/operator-alignment-guide.md` once, as the accuracy and voice anchor for the
   whole pass.
2. Implement the `help-drawer.tsx` generalization first (Component change specification) so the new
   content can be written directly against the final rendering mechanism.
3. Write English content for all fourteen review/deepen topics and the three new topics, applying
   the editorial guardrails and depth calibration.
4. Mirror into French with the same structure and equivalent (not literal) phrasing.
5. Wire the three new topics into `HelpTopicId` and `resolveHelpTopicId()`.
6. Run the evidence plan below.

## Rollback or fallback posture

- Content- and one-component-file change; revert the commit/PR to restore prior behavior. No
  runtime feature flag needed.
- If the `help-drawer.tsx` generalization proves riskier than expected mid-pass, it can be deferred
  and the three new topics can temporarily reuse the existing per-topic hardcoded-block pattern —
  but the corpus content pass (all fourteen + three topics) should still complete in this session.

## Critical flows

| Flow | Nominal | Exception |
|------|---------|-----------|
| Open `?` on any route as Global Admin | Correct, role-appropriate topic renders (no `default` fallback except intentionally generic screens) | N/A (content only) |
| Open `?` on any route as Tenant Admin | Same topic renders with tenant-scoped asides only (no global-only sections shown) | N/A |
| Open `?` on `/auth-attempts`, `/alerts`, `/alerts/:alertId` | New dedicated topic renders instead of `default` | N/A |
| Switch language EN ↔ FR while drawer is open | Same topic, equivalent content, no missing keys | Missing FR key falls back to i18next default — must not happen |
| `admins` topic → recovery codes aside | New `recoveryCodes` section renders for both admin types | N/A |

## Evidence plan

### Automated

- `ezkey-admin-ui` build (`tsc`) and existing lint after the `help-drawer.tsx` refactor and
  `help-topics.ts` additions.
- FR/EN key-parity check: every key added under `en/help.json` must have an exact-structure
  counterpart under `fr/help.json` (manual diff is sufficient at this corpus size).

### Manual exploratory (required — short)

1. Clean-start or `npm run dev`; log in as a Global Admin; open `?` on each route in
   `ezkey-admin-ui/src/routes.tsx` and confirm the topic matches the route (no unexpected `default`).
2. Log in as a Tenant Admin; repeat on the routes a Tenant Admin can reach; confirm global-only
   asides (e.g. `encryption-keys.globalOps`, `tenants` role note) do not render.
3. Toggle EN/FR in the header while a topic with extra sections is open (e.g. `admins`,
   `audit-logs`) and confirm both languages render fully with no missing-key fallback text.

### Playwright

- Not warranted for this slice. Per `.cursor/rules/ui-browser-test-autonomy.mdc` and root
  `AGENTS.md` § UI Test Autonomy, this is copy plus one internal rendering refactor with no new
  workflow, confirmation path, or role-based access change. State the manual exploratory pass above
  as the validation instead of skipping validation silently.

## Quality gates

- **Analysis:** `docs/LIFECYCLE_GOVERNANCE.md`, `product-intent.md`, and
  `operator-alignment-guide.md` re-read and treated as the accuracy/voice source of truth for every
  topic touched.
- **Design:** the extra-sections rendering mechanism in `help-drawer.tsx` is declarative and
  generic — no new `topicId === 'x' && ...` clusters added.
- **Implementation:** FR/EN parity; depth calibration matches `enrollment-detail` / `admins` bar;
  editorial guardrails respected (no competitor names, no standards-equivalence claims, no escape
  hatch, no external doc links in operator-facing bodies).

## Exit criteria

1. Every route in `routes.tsx` resolves to a purpose-built help topic; only `default` itself (and
   any screen intentionally left generic, confirmed during execution) still renders the generic
   placeholder.
2. `auth-attempts`, `alerts`, and `alert-detail` topics exist in both languages and are wired into
   `resolveHelpTopicId()`.
3. `admins` includes a `recoveryCodes` aside; every topic's lifecycle/action claims trace cleanly to
   `docs/LIFECYCLE_GOVERNANCE.md`.
4. `help-drawer.tsx` renders all role-scoped extra sections through one generalized mechanism, not
   hardcoded per-topic conditionals.
5. FR and EN `help.json` have exact key parity; build and lint are green; the manual exploratory
   pass above has been run and reported.
6. No escape-hatch/contact content, no About-panel or `ContextHelp` changes, no competitor naming,
   no standards-equivalence claims anywhere in the touched files.

## Links

- Idea (closed program, related surface): [`ideas/I-2026-0028-admin-ui-operator-experience-follow-up.md`](ideas/I-2026-0028-admin-ui-operator-experience-follow-up.md)
- Precedent (most recent related help-content change): [`TB-2026-07-25-integrated-delivery-posture.md`](TB-2026-07-25-integrated-delivery-posture.md)
- Product framing: [`../product-intent.md`](../product-intent.md), [`../operator-alignment-guide.md`](../operator-alignment-guide.md)
- Lifecycle accuracy anchor: [`../../../docs/LIFECYCLE_GOVERNANCE.md`](../../../docs/LIFECYCLE_GOVERNANCE.md)
- Methodology: [`../../methodology/minimum-viable-method.md`](../../methodology/minimum-viable-method.md)
