# Test Plan Slice — `TB-2026-0002` Mobile real-device churn harness (F1 + F2a)

## Purpose

Define the minimum and optional test layers for extending the validated Maestro pilot into a
repeatable real-device operational churn harness, with optional F2a enrollment bootstrap for agent
autonomy.

## Metadata

- **Target:** `TB-2026-0002-android-real-device-functional-pilot`
- **Related ideas:** `I-2026-0019`, `I-2026-05-31-mobile-android-stack-followups`
- **Date:** `2026-08-26` (updated from `2026-06-26`)
- **Owner:** Marc / agent (documentation alignment pass; hardware validation pending)

## Change risk summary

- **Primary risk:** Intermittent first check-pending / device-proof failures on real hardware are
  not reproduced reliably; automation must produce **correlated evidence** (`auth_attempt_id`,
  Maestro JUnit/XML, logcat) rather than assume a single brittle repro sequence.
- **Secondary risk:** Operator lockout when automation shares `admin.docker` recovery material;
  mitigated by dedicated mobile test admin (`mobile_tester`, target `admin.mobile`) and Demo Device
  lane separation from the real phone.
- **F2a secondary risk:** Controlled enrollment seed bypass must stay **debug-build-only**,
  opt-in, mechanically gated (native `BuildConfig.DEBUG` + env + ack — not `__DEV__` alone), and
  must not weaken bind/verify trust checks. Contract:
  `ezkey_mobile/docs/MOBILE_TEST_AUTOMATION_PRODUCTION_CLEAN.md`.
- **Components touched:** `ezkey_mobile` (Maestro flows, optional bypass harness), orchestration
  scripts, future thin `ezkey-tests` touchpoints; **not** Auth API contract changes in this slice.

## Unit tests

- **Required:** `yarn validate:ci` (Jest + typecheck + lint) when mobile JS/TS changes land.
- **Required for F2a gate changes:** unit tests on
  `app/utils/controlledEnrollmentBypass.ts` (debug build + enable + ack).
- **Rationale:** Existing Jest coverage remains the fast gate; real-device churn is not a substitute
  for unit tests on parsers, hooks, and storage boundaries.

## Functional tests

- **Required scenario(s):**
  - **Pilot (done):** single pending/respond on real device via Maestro after enrollment
    (`pilot_pending_respond`, `pilot_pending_respond_with_challenge`).
  - **Phase A (next):** one end-to-end iteration — API creates auth attempt → Maestro consumes on
    phone → iteration artifacts written per
    `ezkey_mobile/docs/MOBILE_REAL_DEVICE_CHURN_AND_EVIDENCE.md`.
  - **Phase B:** deterministic multi-iteration loop (bounded N) without manual re-queue between
    iterations.
- **Optional scenario(s):**
  - F2a full bind+verify via `pilot_enrollment_full_runtime.yaml` after fresh seed generation.
  - Deny path once `pilot_pending_deny.yaml` (or equivalent) exists.
- **Rationale:** Critical path is backend-to-phone trust; API functional tests in `ezkey-tests`
  remain necessary but insufficient for keystore-backed signing and RN accessibility quirks.

## Elective tests

- **Candidate(s):** none for this slice.
- **Run now?** no
- **Rationale:** N/A.

## Operational tests

- **Candidate(s):**
  - Backend reference: `ezkey-tests/scripts/run-operational-churn.sh` (`OperationalChurnTest`,
    peer Global Admin `churnglo` pattern).
  - **Mobile target:** real-device churn session (Maestro + API/JUnit truth), seeded scenario deck
    in Phase C.
- **Run now?** **partial** — pilot single-attempt Maestro validated; **full mobile operational
  churn deferred** until Phase A artifact contract is green on hardware.
- **Rationale:** Mobile operational churn mirrors backend churn **values** (split admin identities,
  reproducible seed, bounded duration, evidence folders) but adds a **phone UI lane** Maestro must
  own. Do not conflate Demo Device enrollment state with phone Home list visibility.

## UI tests (Playwright)

- **Candidate(s):** Admin UI browser suite.
- **Run now?** no
- **Rationale:** No Admin UI behaviour change in this slice; phone UX is exercised via Maestro on
  device, not Playwright.

## Real-device / Maestro layer (slice-specific — required when executing)

Run from `ezkey_mobile/` with clean-start Docker stack, debug APK, and `adb` device connected.

| Phase | Goal | Command / entry (current) | Pass criterion |
| --- | --- | --- | --- |
| Pilot | Single pending/respond | `ENROLLMENT_ID=<id> ./scripts/run-real-device-pilot-maestro.sh` | Maestro exit 0; attempt consumed |
| F2a smoke | Bypass entry visible (debug build) | `pilot_enrollment_seed_bypass_visibility.yaml` | Visibility assertion passes |
| F2a E2E | Bind+verify without camera | `get-fresh-enrollment-seed.ps1` + `pilot_enrollment_full_runtime.yaml` (explicit opt-in; uses recovery) | Enrollment reaches Home tile |
| Phase A | One churn iteration + artifacts | `./ezkey-tests/scripts/run-mobile-real-device.sh --enrollment-id N` | `rca.md` correlates `auth_attempt_id` with Maestro XML/logcat |
| Phase B | N-iteration loop | `--iterations N` | `SESSION-table.md`; no manual re-queue |
| Phase C | Seeded bounded deck | `--seed` + `--scenarios` | Replayable; not a 2 h vanity goal |
| Deny | Pending deny | `--scenarios deny` | `pilot_pending_deny.yaml` |
| Skip-consume | Attempt left pending | `--scenarios skip-consume` | API still `PENDING` |

**Interim orchestration:** PowerShell helpers remain; **canonical** path is Bash `run-mobile-real-device.sh`.

## Execution evidence

- **Commands run (2026-08-26):** Pixel 7 Pro debug reinstall; F2a visibility Maestro green; campaign runner added.
- **Result summary:** JUnit + Bash + RCA harness shipped; GitHub #179/#239 closed.
- **Follow-up test debt:** F2b camera/QR; deterministic admin-timeout TTL if ever needed.

## Close-out

**Closed 2026-08-26** for the F1/F2a harness slice (named Bash command exists). Revisit only if F2b QR or a deterministic timeout knob is funded.
