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
