# Test Plan Slice — `TB-2026-07-06` Device proof token hash-only storage

## Purpose

Define the minimum test evidence for removing recoverable `device_proof_token` storage while keeping
replay prevention via `device_proof_token_hash`.

## Metadata

- **Target:** `TB-2026-07-06-device-proof-token-hash-only`
- **Related idea:** `I-2026-0032-proof-token-hash-only-storage`
- **Date:** `2026-07-06`
- **Closed at:** _(pending implementation)_
- **Owner:** operator / agent

## Change risk summary

- **Primary risk:** Claim path stops persisting hash → replay check weakened or respond broken.
- **Secondary risk:** Re-encryption or migration tests still reference dropped column.
- **Components touched:** `core` only (no API contract change).

## Unit tests

- **Required:**
  - `mvn test -pl ezkey-core -Dtest=AuthAttemptServiceDeviceProofTokenTest`
  - `mvn test -pl ezkey-core -Dtest=ReencryptionServiceTest,ReencryptionServiceIntegrationTest`
    (after removing `device_proof_token` targets)
- **Optional:** Any pending/replay unit tests under `ezkey-core/.../authattempt/service/`
- **Rationale:** Direct coverage of claim + hash persistence and re-encryption registry.

## Functional tests

- **Required scenario(s):**
  - Auth attempt pending claim → respond accept (existing `ezkey-tests` path that exercises Demo
    Device or RestAssured device simulation)
  - Device proof token replay rejection after successful claim (if covered today; otherwise add one
    targeted functional assertion)
- **Optional scenario(s):** Empty poll (204) behavior unchanged
- **Rationale:** End-to-end proof that hash-only storage does not break MFA pull flow.

## Elective tests

- **Candidate(s):** Full `ezkey-tests` suite post-change
- **Run now?** recommended before PR merge, not gate for first commit
- **Rationale:** Partitioned-table auth attempt coverage is broad; regression signal is high value
  pre-release.

## Operational tests

- **Candidate(s):** Manual clean-start + Demo Device approve
- **Run now?** yes at closeout
- **Rationale:** Operator-visible confirmation; low effort.

## UI tests (Playwright)

- **Candidate(s):** Admin UI test-auth on enrollment detail
- **Run now?** no
- **Rationale:** No Admin UI or contract change; risk-based deferral.

## Build verification (required)

From repository root (Git Bash: `./scripts/build.sh`):

| Step | Command | Pass criterion |
| --- | --- | --- |
| 1 | Maven baseline (spotless, checkstyle, clean, install -DskipTests) | Exit 0 |
| 2 | `mvn test -pl 'ezkey-core,!ezkey-tests'` | Exit 0 |
| 3 | Targeted functional auth-attempt tests (see above) | Exit 0 |

## Manual exploratory summary

1. `./clean-start.sh` from `ezkey-tests/`
2. Admin UI or Postman: create auth attempt for a verified enrollment
3. Demo Device: approve pending request
4. **Expected:** Attempt reaches ACCEPTED; DB row has non-null `device_proof_token_hash`, no
   `device_proof_token` column
5. Retry same device proof token on a new pending claim → **Expected:** authentication failure
   (replay)

## Execution evidence

- **Commands run:** _(fill at implementation closeout)_
- **Result summary:** _(fill at closeout)_
- **Follow-up test debt:** _(if any)_

## Close-out

Pending implementation of `TB-2026-07-06`.
