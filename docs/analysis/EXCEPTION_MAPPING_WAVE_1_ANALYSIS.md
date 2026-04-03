# Exception Mapping Analysis - Wave 1

## Context

This document starts the progressive migration from generic service-layer exceptions toward named domain exceptions and RFC 9457-aligned API mapping across:
- `ezkey-admin-api`
- `ezkey-auth-api`
- `ezkey-integration-api`
- shared business services in `ezkey-core`

The migration is intentionally incremental. Each wave must stay small enough that the maintainer can:
1. run unit tests
2. run a clean start
3. run selective functional churn tests
4. give feedback before the next wave begins

This document is the reference note for the first wave.

## Why Wave 1

Wave 1 should target a narrow cluster with all of these properties:
- the semantics are already understandable
- the exceptions are reused across more than one API path
- the current HTTP behavior is inconsistent or partially wrong
- the validation loop can stay short and targeted

The best initial candidate is the shared lifecycle and tenant-state conflict cluster in `ezkey-core`, plus the Integration API mapping gap that currently weakens those semantics.

## Wave 1 Objective

Make lifecycle and tenant-state conflicts explicit and consistent for the first shared business cluster, without widening yet into all provisioning, authorization, not-found, or technical invariant cases.

In practical terms, Wave 1 should prepare and later implement:
- named exceptions for a small set of shared lifecycle/state-conflict rules
- consistent RFC 9457 mapping for those rules
- removal of the current dependency on raw `IllegalStateException` for these cases
- alignment of Integration API so those conflicts no longer degrade into generic `500` responses

## In Scope

### Shared core service rules

The first wave should focus on these business situations:

1. A tenant is inactive, so a new integration cannot be created.
   - `ezkey-core/src/main/java/org/ezkey/integration/service/IntegrationService.java`

2. A tenant is inactive, so a new API key cannot be created.
   - `ezkey-core/src/main/java/org/ezkey/integration/service/ApiKeyService.java`

3. An integration is not in `ACTIVE` lifecycle state, so a new enrollment cannot be created.
   - `ezkey-core/src/main/java/org/ezkey/enrollment/service/EnrollmentService.java`

4. A tenant is inactive, so a new enrollment cannot be created.
   - `ezkey-core/src/main/java/org/ezkey/enrollment/service/EnrollmentService.java`

5. The active API key limit is reached for an integration.
   - `ezkey-core/src/main/java/org/ezkey/integration/service/ApiKeyService.java`

These cases are good Wave 1 material because they are all business-rule conflicts, not low-level technical failures.

### API mapping alignment

Wave 1 should also cover the API-layer behavior that exposes those shared rules:

1. `ezkey-integration-api/src/main/java/org/ezkey/integration/api/exception/GlobalExceptionHandler.java`
   - currently handles `IllegalArgumentException`
   - does not handle `IllegalStateException`
   - therefore some shared core business conflicts can currently collapse into `500 Internal Server Error`

2. `ezkey-admin-api/src/main/java/org/ezkey/exception/ValidationExceptionHandler.java`
   - currently maps `IllegalStateException` to `409`
   - useful as current-state reference, but not the desired end state for named business exceptions

3. `ezkey-auth-api/src/main/java/org/ezkey/exception/GlobalExceptionHandler.java`
   - already contains named RFC 9457 mappings and legacy fallback handlers
   - useful as target-shape reference for the progressive migration

## Out Of Scope

Wave 1 should not yet include:
- `AdminProvisioningService` mixed semantics
- broad authorization vs not-found distinctions
- internal bootstrap or system invariant failures such as missing system tenant
- technical crypto or hashing wrappers
- `AuthAttemptRespondService` catch-and-convert behavior
- broad `catch (Exception)` cleanup outside this cluster

Those remain important, but they would make the first pass too wide.

## Current Semantics And Observations

### 1. Tenant inactive during integration creation

Current behavior:
- `IntegrationService` throws `IllegalStateException`
- the meaning is clear: the operation is blocked because tenant lifecycle does not allow it

Observation:
- this is not a generic server failure
- this is not input validation
- this is a business lifecycle conflict

Likely direction:
- `TenantInactiveException`

### 2. Tenant inactive during API key creation

Current behavior:
- `ApiKeyService` throws `IllegalStateException`
- the meaning is the same business rule as above, expressed in another service

Observation:
- this should converge on the same named exception family as integration creation
- keeping the same business meaning across services will reduce drift

Likely direction:
- `TenantInactiveException`

### 3. Integration lifecycle blocks enrollment creation

Current behavior:
- `EnrollmentService` throws `IllegalStateException` when integration lifecycle is not `ACTIVE`

Observation:
- the rule is precise and stable
- the current exception type is too generic for long-term RFC 9457 mapping

Likely direction:
- `IntegrationNotActiveException`
- or `IntegrationLifecycleConflictException`

The naming should emphasize that the enrollment creation rule depends on integration lifecycle state.

### 4. Tenant inactive during enrollment creation

Current behavior:
- `EnrollmentService` throws `IllegalStateException`

Observation:
- same business meaning as the tenant-inactive cases above
- this is another candidate for convergence on one shared named exception

Likely direction:
- `TenantInactiveException`

### 5. API key active limit reached

Current behavior:
- `ApiKeyService` throws `IllegalStateException`

Observation:
- this is not really a state conflict in the lifecycle sense
- it is a business quota or limit rule
- it is still a good Wave 1 candidate because it sits in the same service cluster and has a clear meaning

Likely direction:
- `ApiKeyLimitExceededException`

## Why This Cluster Is A Good First Pass

This cluster is a good Wave 1 because:
- it is small
- the business meaning is already clear
- most cases are in shared core services
- the APIs already expose the consequences of these rules
- one visible inconsistency already exists in Integration API mapping

This gives a fast feedback loop with meaningful value, while avoiding the risk of rewriting too many mixed-semantics service classes at once.

## Detailed Inventory

### Inventory Table

| ID | Current location | Method / context | Current exception | Current meaning | Current API exposure | Wave 1 direction |
|---|---|---|---|---|---|---|
| W1-1 | `ezkey-core/src/main/java/org/ezkey/integration/service/IntegrationService.java` | integration creation when target tenant is inactive | `IllegalStateException` | business rule: inactive tenant cannot receive new integrations | Admin API today reaches legacy `409` path through `ValidationExceptionHandler` | replace with named tenant-lifecycle exception |
| W1-2 | `ezkey-core/src/main/java/org/ezkey/integration/service/ApiKeyService.java` | API key creation when integration tenant is inactive | `IllegalStateException` | business rule: inactive tenant cannot receive new API keys | Admin API today reaches legacy `409` path through `ValidationExceptionHandler` | replace with same tenant-lifecycle exception family as W1-1 |
| W1-3 | `ezkey-core/src/main/java/org/ezkey/integration/service/ApiKeyService.java` | API key creation when active key count reaches max | `IllegalStateException` | business rule: integration quota / active key limit reached | Admin API today reaches legacy `409` path through `ValidationExceptionHandler` | replace with named limit exception |
| W1-4 | `ezkey-core/src/main/java/org/ezkey/enrollment/service/EnrollmentService.java` | enrollment creation when integration lifecycle is not `ACTIVE` | `IllegalStateException` | business rule: integration lifecycle blocks new enrollment creation | Admin API today reaches legacy `409`; any future reuse by other APIs would inherit generic semantics | replace with named integration-lifecycle exception |
| W1-5 | `ezkey-core/src/main/java/org/ezkey/enrollment/service/EnrollmentService.java` | enrollment creation when tenant is inactive | `IllegalStateException` | business rule: inactive tenant cannot receive new enrollments | Admin API today reaches legacy `409`; any future reuse by other APIs would inherit generic semantics | replace with same tenant-lifecycle exception family as W1-1 / W1-2 |

### Current Mapping Snapshot

| API | Current generic mapping relevant to Wave 1 | Consequence |
|---|---|---|
| Admin API | `IllegalStateException -> 409` in `ValidationExceptionHandler` with legacy `ErrorResponseDto` | behavior is workable but not yet RFC 9457-shaped for these rules |
| Auth API | `IllegalStateException -> 409` in legacy fallback inside `GlobalExceptionHandler` | target shape exists for named RFC 9457 mapping, but current fallback is semantically broad |
| Integration API | no dedicated `IllegalStateException` handler in `GlobalExceptionHandler` | some shared-core state conflicts can degrade to generic `500` |

### Observations Per Inventory Item

#### W1-1 `IntegrationService` tenant inactive

- The message is clear and already safe for operators.
- The semantics are lifecycle/policy, not technical failure.
- This is a strong candidate for a shared exception because the same rule reappears in other services.

#### W1-2 `ApiKeyService` tenant inactive

- Same business meaning as W1-1.
- This should not become a second exception with slightly different wording unless the product really wants separate categories.
- Best default is one shared tenant-inactive business exception reused across services.

#### W1-3 `ApiKeyService` active key limit reached

- This is the only Wave 1 item that is quota-oriented rather than lifecycle-oriented.
- It still belongs in Wave 1 because it lives in the same service cluster and has precise semantics.
- The future exception should not be named as a generic state conflict; quota is clearer.

#### W1-4 `EnrollmentService` integration lifecycle not active

- This is a pure lifecycle rule and the most semantically stable candidate in the wave.
- The exception name should describe the enrollment creation rule, not just the raw integration state.

#### W1-5 `EnrollmentService` tenant inactive

- Same business meaning as W1-1 and W1-2.
- This is the best place to prove that one shared exception can serve more than one core service without becoming vague.

## Proposed Wave 1 Shape

### Step 1

Define the first small exception family for:
- inactive tenant
- integration not active for enrollment creation
- API key limit reached

### Step 2

Map those exceptions explicitly in the relevant API layers with RFC 9457 responses.

### Step 3

Keep legacy generic handlers in place for everything else outside the wave.

This preserves the progressive migration strategy and limits regression risk.

## Technical Breakdown

### Implementation Goal

Keep Wave 1 intentionally small:
- introduce only the minimum named exceptions needed for this cluster
- wire only the minimum handlers needed for explicit mapping
- do not refactor unrelated generic exception paths in the same pass

### Proposed Exception Set

Recommended first set:
- `TenantInactiveException`
- `IntegrationEnrollmentLifecycleException` or `IntegrationNotActiveForEnrollmentException`
- `ApiKeyLimitExceededException`

Preferred design characteristics:
- one precise business meaning per class
- reusable across modules when the meaning is identical
- safe message strategy compatible with RFC 9457

### Recommended Package Direction

For consistency with the progressive migration, the cleanest direction is:
- shared business exceptions used by multiple APIs should live in a shared package already visible from the API modules, likely under `ezkey-core` shared exception space
- Admin-only business exceptions should stay in admin-specific packages, but that is not required for Wave 1

Wave 1 should avoid creating duplicate exceptions with the same meaning in multiple modules.

### Candidate File Touch Set

Likely implementation touch points:

Core business services:
- `ezkey-core/src/main/java/org/ezkey/integration/service/IntegrationService.java`
- `ezkey-core/src/main/java/org/ezkey/integration/service/ApiKeyService.java`
- `ezkey-core/src/main/java/org/ezkey/enrollment/service/EnrollmentService.java`

Exception classes:
- one or more new shared exception classes under the common exception package used by API handlers

API handlers:
- `ezkey-admin-api/src/main/java/org/ezkey/exception/ValidationExceptionHandler.java` or a more specific RFC 9457-capable handler if that is already the preferred direction for the selected exception family
- `ezkey-integration-api/src/main/java/org/ezkey/integration/api/exception/GlobalExceptionHandler.java`
- possibly `ezkey-auth-api/src/main/java/org/ezkey/exception/GlobalExceptionHandler.java` only if the new shared exception types are relevant there now or in near-term reuse

Documentation:
- `docs/ENDPOINT.md` only if any externally visible error contract becomes more precise during implementation
- this analysis document after validation feedback

### Recommended Implementation Sequence

#### Pass A: Introduce named exceptions only for the selected cluster

Do first:
- create the new exception classes
- replace the raw `IllegalStateException` throws in the Wave 1 service locations

Do not yet:
- rewrite unrelated `IllegalArgumentException` paths
- collapse broader handler architecture
- rename unrelated exception packages

#### Pass B: Add explicit API mapping for the new exceptions

Do next:
- add explicit handler coverage in Integration API
- decide whether Admin API keeps temporary legacy mapping or gains explicit named mapping now for this cluster

Recommended default:
- Integration API should gain explicit named exception handling in this wave because it currently has the clearest behavioral gap
- Admin API can either map the new exceptions explicitly now or temporarily continue to produce equivalent `409` responses if that keeps the pass smaller

#### Pass C: Verify externally visible contract

Check:
- status code remains correct
- problem `type` is stable and coherent
- title and detail remain safe and operator-usable
- no selected path now falls through to generic `500`

### Handler Strategy Options

#### Option 1: Minimal-risk Wave 1

- add named exceptions
- add explicit handler support only where behavior is currently wrong or incomplete
- keep legacy generic handlers untouched for the rest

Why this fits Wave 1:
- smallest regression surface
- fastest maintainer feedback loop

#### Option 2: Slightly more ambitious Wave 1

- add named exceptions
- add explicit named mapping in both Admin API and Integration API
- leave generic fallbacks in place for non-migrated code

Why this may still be acceptable:
- still narrow in scope
- produces cleaner end-user consistency for the selected cluster

Recommended choice:
- start from Option 1 unless implementation shows the additional Admin API mapping is almost free

### Technical Risks

1. Reusing one tenant-inactive exception across services may expose subtle differences in wording or status expectations.
2. Creating too many new exception classes in the first pass would dilute the benefit of the cluster strategy.
3. If Integration API adds mapping but Admin API does not, temporary cross-API asymmetry may remain for payload shape.
4. If problem `type` URIs are invented too early without taxonomy discipline, later waves may inherit naming drift.

### Recommended Test Focus For The Future Implementation

Unit and focused integration coverage should target:
- integration creation blocked for inactive tenant
- API key creation blocked for inactive tenant
- API key creation blocked at active key limit
- enrollment creation blocked for non-active integration
- enrollment creation blocked for inactive tenant
- Integration API response shape for the named exception path that previously risked generic `500`

### Exit Criteria Before Starting Implementation

Wave 1 is ready to implement when:
- the exception names for the three selected meanings are accepted
- the handler strategy for Admin API vs Integration API is chosen
- the maintainers agree that no additional mixed-semantics paths are pulled into the same pass

## Validation Strategy For Wave 1

Wave 1 should be validated as a tight loop:

1. Apply only the selected cluster changes.
2. Run unit tests for the impacted modules.
3. Run a clean start.
4. Run selective functional churn around:
   - integration creation
   - enrollment creation
   - API key creation and limit behavior
   - tenant inactive operational paths
5. Adjust before opening Wave 2.

The purpose is not only correctness. It is also to keep diagnosis easy if a functional behavior changes unexpectedly.

## Acceptance Signals

Wave 1 is successful when:
- the selected business rules no longer depend on raw `IllegalStateException`
- Integration API no longer turns those business conflicts into generic `500` responses
- the affected APIs expose stable RFC 9457 problem categories for these cases
- no unrelated exception families are pulled into the same pass

## Candidate Wave 2 Preview

If Wave 1 is stable, the next logical candidates are:
- auth-attempt state conflicts
- admin provisioning mixed semantics
- authorization vs not-found normalization
- catch-and-mask behaviors that currently hide programming faults behind business-level failures

## Reference Note

This document is intentionally scoped as a working migration reference, not a full inventory of every generic exception in the repository. It should be updated after implementation and validation so it remains useful for later waves and regression analysis.
