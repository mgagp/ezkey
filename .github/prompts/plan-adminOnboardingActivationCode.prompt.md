## Detailed Work Plan — Admin Onboarding by Activation Code

### Why this plan exists

This plan replaces the earlier framing around anonymous experimental tenant onboarding.

The earlier draft helped surface the right architectural insight, but its public-shell framing is no longer the right product direction.

The durable product need is now clearer:

- Ezkey needs a clean way to create an administrator without forcing immediate enrollment creation.
- Ezkey needs a one-time activation path for first-time setup that remains separate from recovery.
- Ezkey should support controlled operator-driven onboarding for new admins without overloading the recovery model.
- Ezkey should reuse as much of the existing login/recovery funnel as possible without collapsing the domain concepts.

This plan therefore reframes the feature as a normal platform capability:

- create an admin now,
- defer first enrollment,
- activate first-time setup later with an activation code,
- keep recovery strictly for re-establishing an existing access path.

---

## Explicit triage from the previous draft

### Keep

These elements from the previous work remain correct and should be preserved:

1. Activation code and recovery code must remain distinct concepts.
2. First real enrollment should be created at activation time, not at admin creation time.
3. Recovery codes should not exist before first enrollment exists.
4. `PENDING_ACTIVATION`, `ACTIVE`, and `DEACTIVATED` are the right lifecycle states.
5. Normal passwordless login must stay impossible before activation-created enrollment exists.
6. Activation and admin reactivation must remain separate semantics.
7. The activation token should remain a server-side, one-time, expiring primitive.
8. The feature should reuse existing onboarding/recovery presentation patterns where practical.

### Discard

These elements should be removed from the product framing and plan:

1. Public anonymous activation-code generation.
2. Public `Request experimental access` affordance on the login page.
3. Manual participant email flow to `info@ezkey.org`.
4. Public activation-request endpoints for unknown external visitors.
5. The notion that this feature primarily exists for pre-pilot anonymous feedback collection.

### Rewrite

These elements should be retained only after being reframed:

1. The feature name should move from `anonymous experimental tenant onboarding` to `admin onboarding by activation code`.
2. The activation flow should move from a public visitor flow to an operator-issued onboarding mode.
3. The UI anchor should move from a public request flow to a branch inside the existing login/recovery funnel.
4. The API plan should move from public code generation to admin provisioning plus public code consumption.
5. The audit model should move from experimental access request events to operator-issued onboarding activation events.

---

## Core product model

### Principle

An administrator identity can exist before that administrator has a real enrollment.

That is the core architectural principle this feature formalizes.

### Orthogonal concepts

The platform should keep four concepts separate:

- Admin provisioning: operator creates the admin identity and chooses an onboarding mode.
- Activation: one-time first-time setup authorization via activation code.
- Enrollment: the first normal passwordless credential is created only after activation succeeds.
- Recovery: reset or re-establish an already existing enrollment using break-glass credentials.

### Product interpretation

This feature is not an alternate login mode.
This feature is not a recovery shortcut.
This feature is a controlled onboarding mode for administrator creation.

---

## Recommended user story

### Operator-side story

1. A Global Admin or authorized Tenant Admin creates a new administrator.
2. During provisioning, the operator chooses an onboarding mode.
3. One supported onboarding mode is `activation code`.
4. The platform creates the admin in `PENDING_ACTIVATION`.
5. The platform creates and stores a one-time activation token.
6. The platform does not create enrollment credentials yet.
7. The platform does not generate recovery codes yet.
8. The operator transmits the activation code to the new administrator through an appropriate external channel.

### New administrator story

1. The new administrator reaches the login page.
2. They choose a login-page path that already handles exceptional onboarding/recovery-type flows.
3. They indicate that they have an activation code.
4. They submit the activation code.
5. If valid, the platform creates the first real enrollment.
6. The platform generates the initial recovery codes server-side but does not reveal them yet.
7. The platform returns the QR/manual setup payload for first-time device binding.
8. After binding and first sign-in, recovery codes are revealed or regenerated from an authenticated admin-management flow.
9. The administrator then uses the normal passwordless login flow.

---

## Locked invariants

The following invariants should be treated as non-negotiable unless a hard implementation blocker proves otherwise:

1. The durable core is `admin onboarding activation`, not experimental access collection.
2. The admin lifecycle model must explicitly represent `PENDING_ACTIVATION`.
3. Admin creation and first enrollment creation are separate events.
4. Activation must create the first enrollment, not reset a pre-existing one.
5. Recovery codes must only exist after a first enrollment exists.
6. Activation must never be implemented as a disguised recovery flow.
7. The login UI may reuse the recovery funnel shell, but the domain model, endpoints, token types, and audit semantics must remain distinct.
8. Activation must stay reusable as a durable platform primitive for future onboarding modes.
9. If an enrollment is the MFA enrollment of an admin, bind and verify must be blocked whenever that admin is not operational.
10. Bootstrap responses must not reveal recovery codes; recovery-code revelation should be deferred to a later authenticated admin-management step.

---

## Recommended domain model

### 1. Administrator lifecycle

Use an explicit lifecycle status on the administrator.

Recommended status set:

- `PENDING_ACTIVATION`
- `ACTIVE`
- `DEACTIVATED`

Current workspace state:

- this foundation is already in place and should be kept.

### 2. Activation token

Introduce a dedicated activation token entity or persistence model.

Recommended fields:

- token id
- activation code value or code hash
- linked admin id
- linked tenant id when applicable
- issuance timestamp
- expiration timestamp
- consumed timestamp
- status or derivable consumption state
- optional issued-by admin id
- optional onboarding mode metadata

Recommended semantics:

- one-time use
- server-side expiry enforcement
- invalid after consumption
- invalid if the linked admin is no longer pending

### 3. Enrollment boundary

The first real enrollment must be created only when activation succeeds.

Recommended rule:

- no enrollment entity for the new admin before activation success.

### 4. Recovery boundary

Recovery codes must be created only after activation-created enrollment exists.

Recommended rule:

- no recovery code material for pending admins.

---

## Recommended backend scope

### Track A — provisioning mode selection

Extend admin provisioning so the operator can choose an onboarding mode.

Recommended MVP modes:

1. immediate onboarding
2. activation code onboarding

Recommended behavior for activation-code mode:

- create admin in `PENDING_ACTIVATION`;
- create activation token;
- do not create enrollment;
- do not create recovery codes.

### Track B — activation token issuance and validation

Introduce the backend primitive for activation-code lifecycle.

Recommended responsibilities:

- generate activation code;
- hash or otherwise protect it appropriately at rest;
- validate expiry and one-time use;
- resolve linked pending admin;
- support audit events for issuance, failure, and success.

### Track C — activation consumption

Add a dedicated flow that consumes the activation code and creates first-time setup artifacts.

Recommended behavior:

- accept activation code from an unauthenticated login-funnel path;
- resolve linked pending admin;
- verify pending status and token validity;
- create first enrollment;
- generate initial recovery codes;
- mark token consumed;
- transition admin to `ACTIVE`;
- return onboarding payload for QR/manual setup.

### Track D — existing onboarding contract alignment

Reconcile existing provisioning APIs and onboarding payloads with the new deferred model.

Recommended direction:

- activation success should return the onboarding payload directly;
- existing authenticated onboarding endpoints remain relevant for already-created enrollments and operator workflows;
- activation-code mode should not require a second immediate fetch just to display the first QR/setup payload.

### Track E — admin-linked enrollment eligibility guard

Realign the bind and verify flow for admin MFA enrollments with lifecycle governance.

Recommended behavior:

- if an enrollment is linked to an admin, the bind step must require that admin to be operational;
- if an enrollment is linked to an admin, the verify step must require that admin to be operational;
- this guard should cover inactive admins, pending-activation admins, and tenant-scoped admins whose tenant is inactive;
- the guard should be inexpensive and centralized through `EntityEligibilityService`.

---

## Recommended API plan

### Admin provisioning API

Recommended change:

- extend administrator creation requests so the caller can choose onboarding mode.
- when this contract changes, update the Java/OpenAPI annotations and the impacted Postman
	collection entries in the same slice before handoff.

Examples of the shape to evaluate:

- `onboardingMode = IMMEDIATE`
- `onboardingMode = ACTIVATION_CODE`

For `ACTIVATION_CODE`, the response should contain activation-related operator output instead of immediate enrollment credentials.

Recommended contract behavior for the provisioning response:

- `onboardingMode = IMMEDIATE` should remain backward-compatible: create the first enrollment,
	return `enrollmentId`, and return plain `recoveryCodes` once;
- `onboardingMode = ACTIVATION_CODE` should create the administrator in
	`PENDING_ACTIVATION`, return a one-time `activationCode` and its expiry, and leave
	`enrollmentId` and `recoveryCodes` empty until activation is consumed;
- authenticated onboarding retrieval should remain a post-enrollment path and is therefore not
	applicable to admins still in `PENDING_ACTIVATION`.

### Activation consumption API

Recommended addition:

- a dedicated activation-code consumption endpoint for first-time setup.

This endpoint should be distinct from:

- recovery code submission,
- admin reactivation,
- authenticated onboarding retrieval.

Recommended contract behavior:

- consume activation code;
- return first enrollment setup payload;
- avoid leaking unnecessary identity details on failure.

### Recovery API

Recommended rule:

- keep the existing recovery contract semantically intact;
- do not overload recovery endpoints to also mean first activation.

---

## Recommended Admin UI scope

### 1. Provisioning UI

Add or extend admin creation UI so the operator can choose onboarding mode.

Recommended UX:

- default stays aligned with current simple provisioning behavior unless product decides otherwise;
- activation-code mode is clearly described as deferred first-time setup;
- UI explains that enrollment and recovery codes will be created only when the admin activates.

### 2. Login-page funnel reuse

Reuse the existing login/recovery funnel shell as the UI anchor for activation.

Recommended UX principle:

- one shared exceptional-access entry surface is acceptable;
- separate branches and wording must make recovery and activation clearly distinct.

Recommended copy direction:

- `Lost access? Use a recovery code.`
- `New administrator with an activation code? Activate first-time setup.`

### 3. Activation branch UI

Add an activation-code branch alongside the existing recovery-code branch.

Recommended behavior:

- allow the user to enter an activation code;
- on success, display the QR/manual first-time setup payload;
- defer recovery-code revelation until after first authenticated access;
- reuse existing QR/manual presentation patterns where practical;
- avoid implying that activation code itself is a login credential.

### 4. Login surface simplification

The login page is becoming heavier as passwordless login, recovery, and activation flows coexist.

Recommended direction:

- keep the default screen focused on normal passwordless login;
- treat recovery and activation as secondary branches behind a clearer chooser or segmented switch;
- consider a second-step branch selector or a lightweight dedicated sub-route if the card becomes too dense;
- do not stack all exceptional flows with equal visual weight on first paint.

---

## Recommended audit model

Add explicit audit events for onboarding activation rather than overloading recovery or admin reactivation events.

Recommended event milestones:

- admin created with activation-code onboarding mode
- activation code issued
- activation attempt failed
- activation code consumed successfully
- first enrollment created via activation

Recommended rule:

- activation and recovery must remain distinguishable in both event type and operator-readable event details.

---

## Recommended security scope

### Activation token security

Recommended requirements:

- one-time use enforced transactionally;
- bounded expiry;
- no plaintext activation code logging in normal request logs;
- neutral failure responses where appropriate;
- auditable failures and successes.

### Admin-linked enrollment security

Recommended requirements:

- an admin enrollment that belongs to a non-operational admin must not be allowed to progress through bind or verify;
- the platform must not rely only on later login failure for this protection;
- this must be treated as lifecycle governance, not as a UI-only concern.

### Login-funnel protection

Recommended requirements:

- rate limiting on activation-code submission;
- no escalation into a full authenticated session before enrollment binding;
- return only the minimum payload needed for first-time setup.

---

## Recommended MVP boundary

### Included in MVP

- admin lifecycle foundation with pending activation;
- activation-code onboarding mode on admin provisioning;
- activation token persistence and validation;
- activation-code login-funnel branch;
- first enrollment creation at activation time;
- recovery-code generation only after activation;
- targeted backend tests and documentation;
- UI follow-up after backend contract stabilization.

### Excluded from MVP

- anonymous public onboarding acquisition flows;
- public request collection;
- email-based applicant workflows;
- generalized invitation-management platform;
- mixing activation with recovery semantics.

---

## Recommended implementation sequence

### Phase 1 — already started foundation

Goal:
Lock the admin lifecycle model before implementing activation.

Current status:

- already started and worth keeping.

Includes:

- explicit admin lifecycle status;
- login rejection for `PENDING_ACTIVATION`;
- response DTO exposure for lifecycle visibility.

### Phase 2 — provisioning contract revision

Goal:
Make onboarding mode an explicit part of admin creation.

Work:

- revise admin create request/response contract;
- support `ACTIVATION_CODE` mode;
- return operator-facing activation output;
- stop forcing immediate enrollment/recovery creation in that mode.

### Phase 3 — activation token backend

Goal:
Add token persistence, validation, and lifecycle handling.

Work:

- entity and migration;
- repository and service;
- issuance and consumption semantics;
- audit hooks.

### Phase 4 — activation consumption endpoint

Goal:
Create first-time enrollment from activation code.

Work:

- add activation endpoint;
- validate code and pending admin;
- create first enrollment;
- generate initial recovery codes;
- activate the admin.

### Phase 5 — login-funnel UI alignment

Goal:
Reuse the recovery-shell UX while keeping semantics clean.

Work:

- add activation-code branch in login UI;
- reuse QR/manual setup presentation;
- update wording and help text.

### Phase 6 — documentation and verification

Goal:
Finish the backend tranche cleanly before UI completion and functional test passes.

Work:

- update endpoint and operator-flow documentation;
- update lifecycle governance documentation for pending activation and admin-linked enrollment eligibility;
- run targeted backend tests;
- run broader build verification;
- follow with clean start and functional validation;
- update specs once backend contract is stable and validated.

---

## Recommended testing strategy

### Backend unit and service tests

Add or update tests for:

- provisioning with activation-code mode;
- no enrollment created at admin creation in activation mode;
- no recovery codes created at admin creation in activation mode;
- activation token issuance and expiry;
- activation success creating first enrollment and recovery codes;
- pending admin becoming active only after successful activation;
- recovery remaining unavailable before activation.
- admin-linked enrollment bind rejection when the linked admin is inactive or pending activation;
- admin-linked enrollment verify rejection when the linked admin is inactive or pending activation;

### API tests

Add or update integration tests for:

- create admin with onboarding mode selection;
- activation-code consumption endpoint;
- failure paths for expired, invalid, or already-used codes;
- no secret leakage in error bodies.

### Functional validation sequence

Recommended order after backend changes are complete:

1. targeted unit and integration tests,
2. broader build verification,
3. clean start,
4. functional validation battery,
5. specs update,
6. Admin UI completion.

This sequencing matches the intended delivery flow for this backend-first tranche.

---

## Main risks and mitigations

### Risk 1 — accidental collapse into recovery semantics

Risk:
Implementation convenience could push activation to reuse too much recovery behavior.

Mitigation:

- share UI shell if useful, but keep backend contract, token type, state model, and audit semantics distinct.

### Risk 2 — partial deferred model

Risk:
The codebase could end up half-deferred, with pending admins but still eager enrollment creation in some paths.

Mitigation:

- make onboarding mode explicit in provisioning contract and test both modes clearly.

### Risk 3 — operator confusion

Risk:
Operators may not understand when to choose activation-code mode or what gets created immediately.

Mitigation:

- keep provisioning UX explicit;
- describe what is deferred and what is not;
- keep audit and docs aligned.

### Risk 4 — overly broad first implementation

Risk:
The team could try to solve backend contract, operator UX, login funnel, and every future invitation scenario at once.

Mitigation:

- keep the current tranche backend-first and finish the core contract before expanding further.

---

## Final recommendation

The right product direction is to make activation code a first-class onboarding mode for administrator provisioning, not a public experimental acquisition mechanism.

This keeps the durable value:

- deferred first enrollment,
- explicit pending admin lifecycle,
- recovery kept orthogonal,
- reusable onboarding primitive for the platform.

That is the version of the feature whose code weight remains justified in the mature product.