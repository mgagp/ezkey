# Ezkey Mobile API Mappings

## Purpose and Reading Scope

This document maps the Auth API contract onto the React Native reference app in `ezkey_mobile/`.
It explains which screens, services, and local concepts own the four mobile operations: enrollment `bind`,
enrollment `verify`, authentication `pending`, and authentication `respond`.

Root-level protocol documents remain canonical for exact wire semantics, signed payload formats, and broader
security guarantees. This document is the mobile interpretation layer that answers: which field comes from where,
which field is persisted, which field is displayed, and which screen or service is responsible for it.

## Mapping Principles and Canonical Sources

Rules:

- [../../docs/ENDPOINT.md](../../docs/ENDPOINT.md) remains canonical for endpoint semantics and HTTP outcomes.
- [../../docs/CRYPTO.md](../../docs/CRYPTO.md) remains canonical for algorithm choices and trust boundaries.
- [../../docs/AUTH_ATTEMPT_SIGNATURE_PAYLOAD.md](../../docs/AUTH_ATTEMPT_SIGNATURE_PAYLOAD.md) and [../../docs/ENROLLMENT_SIGNATURE_PAYLOAD.md](../../docs/ENROLLMENT_SIGNATURE_PAYLOAD.md) remain canonical for exact signed payload definitions.
- `app/services/api/types.ts` is the thin mobile contract layer; generated models remain the DTO source of truth.
- Screen ownership matters: the mobile app does not expose `pending` directly from Home. The user navigates into Enrollment Detail first, then to Pending Authentication.

| Canonical document | Scope | How this document depends on it |
| --- | --- | --- |
| [../../docs/ENDPOINT.md](../../docs/ENDPOINT.md) | Endpoint semantics and statuses | Used to explain expected outcomes, especially `204 No Content` on `pending` and business-level failures on `respond`. |
| [../../docs/CRYPTO.md](../../docs/CRYPTO.md) | Algorithms and trust boundaries | Used to interpret device ECDSA signing and integration Ed25519 verification. |
| [../../docs/ENROLLMENT_SIGNATURE_PAYLOAD.md](../../docs/ENROLLMENT_SIGNATURE_PAYLOAD.md) | Bind and verify signed payloads | Used to describe the bind-result and verify-result trust checks. |
| [../../docs/AUTH_ATTEMPT_SIGNATURE_PAYLOAD.md](../../docs/AUTH_ATTEMPT_SIGNATURE_PAYLOAD.md) | Pending and respond signed payloads | Used to describe the pending-result and respond-result trust checks. |

## API Surface Summary

| Operation | Endpoint | Request DTO | Response DTO | Primary screen owner |
| --- | --- | --- | --- | --- |
| Enrollment bind | `POST /api/v1/enrollments/bind` | `EnrollmentBindRequestDto` | `EnrollmentBindResponseDto` | Enrollment Wizard |
| Enrollment verify | `POST /api/v1/enrollments/verify` | `EnrollmentVerifyRequestDto` | `EnrollmentVerifyResponseDto` | Enrollment Wizard |
| Auth attempt pending | `POST /api/v1/auth-attempts/pending` | `AuthAttemptPendingRequestDto` | `AuthAttemptPendingResponseDto` or `204 No Content` | Pending Authentication |
| Auth attempt respond | `POST /api/v1/auth-attempts/respond` | `AuthAttemptRespondRequestDto` | `AuthAttemptRespondResponseDto` | Pending Authentication |
| Installation public info | `GET /api/v1/public/instance-info` | none | `PublicInstanceInfoResponseDto` | Enrollment Wizard and installation metadata refresh |

## Enrollment Bind Mapping

### Bind Endpoint and DTO Identity

- Service facade: `app/services/api/enrollments.ts`
- Wrapper types: `BindEnrollmentRequest`, `BindEnrollmentResponse` in `app/services/api/types.ts`
- Primary trigger: Enrollment Wizard after QR/manual enrollment payload capture
- Primary local output: enrollment draft, not yet persisted

### Bind Request Fields

| Request field | Meaning | Required? | Source in app | Security note |
| --- | --- | --- | --- | --- |
| `enrollmentId` | Enrollment identifier from QR payload | Yes | Captured in Enrollment Wizard bind form / QR scan | Serialized to JSON number before sending. |
| `enrollmentProofToken` | Enrollment proof token from QR payload | Yes | Captured in Enrollment Wizard bind form / QR scan | Anti-enumeration proof material; must not be guessed or synthesized. |

### Bind Response Fields

| Response field | Meaning | Consumed by | Persisted? | Displayed? | Security note |
| --- | --- | --- | --- | --- | --- |
| `enrollmentId` | Server-confirmed enrollment identifier | Enrollment Wizard | No | Not shown directly | Used to normalize the draft ID. |
| `enrollmentProofToken` | Proof token echoed by server | Enrollment Wizard | Later, after verify | No | Compared/retained for the follow-up verify flow and stored only after successful enrollment. |
| `integrationPublicKey` | Integration verification key | Enrollment Wizard, Pending Authentication | Yes, after verify | No | Used to verify integration Ed25519 signatures later in bind, verify result, pending, and respond result flows. |
| `integrationKeyAlgorithm` | Algorithm tag for `integrationPublicKey` | Enrollment Wizard | No | No | Must be exactly `ed25519`; the app fails closed otherwise. |
| `integrationName` | Integration display name | Enrollment Wizard | Yes, after verify | Yes | Shown in the info card before enrollment is completed. |
| `integrationDescription` | Integration description | Enrollment Wizard | Yes, after verify | Yes | Optional display content in the bind result card. |
| `tenantId` | Tenant identifier | Enrollment Wizard | Yes, after verify | No | Used as local metadata. |
| `tenantName` | Tenant display name | Enrollment Wizard, later Home/Detail | Yes, after verify | Yes | Supports grouping and identity display. |
| `tenantDescription` | Tenant descriptive text | Enrollment Wizard, later Home | Yes, after verify | Sometimes | Optional supporting metadata. |
| `enrollmentName` | Friendly enrollment/device label | Enrollment Wizard, later Detail | Yes, after verify | Yes | Copied into `deviceLabel` locally. |
| `enrollmentBindPayloadSignedByIntegration` | Integration signature on bind payload | Enrollment Wizard | No | No | Must verify before the bind response is trusted. |

### Bind Local Mapping Narrative

The Enrollment Wizard converts the bind response into an in-memory `EnrollmentDraft`. This draft is the staging
object for the rest of the enrollment workflow. At this stage nothing is persisted to local storage. The response is
first validated with two trust checks: `integrationKeyAlgorithm` must be `ed25519`, and the app must verify
`enrollmentBindPayloadSignedByIntegration` over the canonical bind payload. Only after both checks pass does the app
surface the bind result and allow the user to enter the six-digit challenge.

| Local concept | Mapped from | Used by | Notes |
| --- | --- | --- | --- |
| `EnrollmentDraft.id` | `enrollmentId` | Enrollment Wizard | Normalized as string. |
| `EnrollmentDraft.integrationName` | `integrationName` | Enrollment Wizard UI, later persisted record | Primary display label. |
| `EnrollmentDraft.tenantName` | `tenantName` | Enrollment Wizard UI, later grouping | Optional. |
| `EnrollmentDraft.enrollmentProofToken` | `enrollmentProofToken` | Verify flow | Retained in memory until enrollment completes. |
| `EnrollmentDraft.integrationPublicKey` | `integrationPublicKey` | Verify-result trust check and later auth flows | Long-lived persisted crypto material. |

### Bind Trigger and Ownership

| Trigger point | Screen/hook/service | Preconditions | Notes |
| --- | --- | --- | --- |
| Tap scanner / submit bind payload | Enrollment Wizard | Enrollment ID and proof token available; Auth API URL resolved | The wizard owns camera permission and initial QR handling. |
| `enrollmentsApi.bind(...)` | `app/services/api/enrollments.ts` | Wrapper converts ID to number | Optional per-enrollment `authUrl` overrides the global base URL. |

### Bind Outcomes and Error Categories

| Condition | Technical outcome | User-visible outcome | Persistence impact |
| --- | --- | --- | --- |
| Valid response and signature | Draft created in memory | Enrollment info card shown | None yet |
| Missing/invalid `integrationKeyAlgorithm` | Flow fails closed | Bind error shown | None |
| Invalid bind signature | Flow fails closed | "Could not verify server identity" | None |
| Request failure / transport failure | Axios error surfaced | Bind error shown | None |

## Enrollment Verify Mapping

### Verify Endpoint and DTO Identity

- Service facade: `app/services/api/enrollments.ts`
- Wrapper types: `VerifyEnrollmentRequest`, `VerifyEnrollmentResponse`
- Primary trigger: Enrollment Wizard after user enters the six-digit challenge
- Primary local output: persisted `StoredEnrollment`

### Verify Request Fields

| Request field | Meaning | Required? | Source in app | Security note |
| --- | --- | --- | --- | --- |
| `enrollmentId` | Enrollment identifier | Yes | From `EnrollmentDraft.id` | Serialized to JSON number before sending. |
| `challengeResponse` | User-entered enrollment challenge | Yes | Enrollment Wizard challenge input | Locally validated for length before submission. |
| `devicePublicKey` | Device EC P-256 public key | Yes | `cryptoService.getPublicKey(enrollmentId)` | Must match the private key used to sign the verify payload. |
| `enrollmentProofTokenSigned` | Device signature on canonical verify payload | Yes | `cryptoService.sign(...)` over `buildVerifyDevicePayload(...)` | Critical device proof that binds proof token, challenge, and public key. |
| `devicePrivateKeyStorageTier` | Client-reported storage tier | Optional | `cryptoService.getEnrollmentPrivateKeyStorageTier(...)` | Informational only; not independently verified by the backend. |

### Verify Response Fields

| Response field | Meaning | Consumed by | Persisted? | Displayed? | Security note |
| --- | --- | --- | --- | --- | --- |
| `active` | Enrollment activation state | Enrollment Wizard | Indirectly, via successful completion | No | The mobile app also verifies the signed result before trusting completion. |
| `enrollmentVerifyMessage` | Result message | Enrollment Wizard | No | Not currently surfaced prominently | Part of the signed result payload. |
| `enrollmentVerifyPayloadSignedByIntegration` | Integration signature on verify result | Enrollment Wizard | No | No | Must verify before the app persists the enrollment and exits the wizard. |

### Verify Local Mapping Narrative

The verify step finalizes the enrollment. The app generates or ensures the device EC P-256 key pair, derives the
public key, signs the canonical verify payload, submits the request, and then verifies the integration-signed result.
Only after the verify-result signature passes does the wizard persist a `StoredEnrollment` record. Persistence also
hydrates local installation metadata when an Auth API base URL is known.

| Local concept | Mapped from | Used by | Notes |
| --- | --- | --- | --- |
| `StoredEnrollment.id` | Draft ID | Home, Detail, Pending | Primary local identifier. |
| `StoredEnrollment.integrationPublicKey` | Bind response | Pending and respond verification | Stored for future integration-signature checks. |
| `StoredEnrollment.enrollmentProofToken` | Draft proof token | Pending flow | Sensitive enrollment token stored in the local record. |
| `StoredEnrollment.installation.authUrl` | QR or resolved installation URL | All subsequent API calls | Allows per-installation server targeting. |
| `StoredEnrollment.installation` | `instanceInfoApi.get(...)` plus URL derivation | Home and Detail | Supports installation grouping and display as a first-class object. |

### Installation Association and Refresh Mapping

The mobile app does not wait for the backend to send a dedicated installation DTO during bind or verify. Instead it
builds the local `Installation` object intentionally from the enrollment context.

| Local installation field | Derived from | Stage | Notes |
| --- | --- | --- | --- |
| `installation.id` | Normalized effective `authUrl` | Enrollment verify and later hydration | Canonical trust-zone identity used for grouping. |
| `installation.authUrl` | QR payload override or configured base URL | Enrollment verify and later hydration | Canonical routing base URL for later Auth API calls. |
| `installation.host` | Normalized effective `authUrl` | Enrollment verify and later hydration | Used for fallback display and ambiguity hints. |
| `installation.name` | `instanceInfo.instanceName` or host fallback | Enrollment verify and background refresh | Display-layer metadata only, not a cryptographic anchor. |
| `installation.description` | `instanceInfo.instanceDescription` | Enrollment verify and background refresh | Optional supporting text. |
| `installation.aboutUrl` | `instanceInfo.aboutUrl` | Enrollment verify and background refresh | Reserved for low-prominence informational affordances. |
| `installation.lastRefreshedAt` | Local refresh timestamp | Enrollment verify and background refresh | Drives stale-while-revalidate behavior. |

Association pipeline:

1. Resolve the effective `authUrl` from QR or environment fallback.
2. Normalize it into the canonical installation identity.
3. Build `installation` immediately, even if only host fallback metadata is available.
4. Enrich it with `GET /api/v1/public/instance-info` when the call succeeds.
5. Refresh stale installations opportunistically by installation ID so all enrollments sharing the same
	installation receive the same updated metadata.

### Verify Trigger and Ownership

| Trigger point | Screen/hook/service | Preconditions | Notes |
| --- | --- | --- | --- |
| Tap "Complete enrollment" | Enrollment Wizard | Valid draft exists; six-digit challenge present | Wizard owns input validation and persistence orchestration. |
| `enrollmentsApi.verify(...)` | `app/services/api/enrollments.ts` | Public key and device signature prepared | Wrapper converts numeric fields to JSON numbers. |
| `useSaveEnrollment().mutateAsync(...)` | Enrollment hook / storage layer | Verify-result signature already accepted | This is the first durable write of the enrollment. |

### Verify Outcomes and Error Categories

| Condition | Technical outcome | User-visible outcome | Persistence impact |
| --- | --- | --- | --- |
| Valid verify response and result signature | Record persisted; wizard exits to Home | Enrollment completed | Enrollment saved |
| Invalid challenge length locally | Request not sent | Challenge error shown | None |
| Invalid verify-result signature | Flow fails closed | Challenge error shown | None |
| Request failure / backend rejection | Error surfaced | Challenge error shown | None |
| Persistence failure | Enrollment not completed locally | Error surfaced | No durable record |

## Auth Attempt Pending Mapping

### Pending Endpoint and DTO Identity

- Service facade: `app/services/api/authAttempts.ts`
- Wrapper types: `PendingAuthRequest`, `PendingAuthResponse`
- Primary trigger: Pending Authentication screen load or manual "Check again"
- Primary local output: in-memory `PendingAttempt`

### Pending Request Fields

| Request field | Meaning | Required? | Source in app | Security note |
| --- | --- | --- | --- | --- |
| `enrollmentId` | Enrollment identifier | Yes | Persisted enrollment record | Serialized to JSON number before sending. |
| `enrollmentProofToken` | Enrollment proof token | Yes | Persisted enrollment record | Stable enrollment-side proof material. |
| `deviceProofToken` | Fresh client-generated proof token | Yes | `generateProofToken()` | Intended to prove fresh device participation on each poll cycle. |
| `deviceProofTokenSigned` | Device signature on the proof token | Yes | `cryptoService.sign(...)` | Prevents unauthenticated enumeration of pending attempts. |

### Pending Response Fields

| Response field | Meaning | Consumed by | Persisted? | Displayed? | Security note |
| --- | --- | --- | --- | --- | --- |
| `authAttemptId` | Authentication attempt identifier | Pending Authentication | No | No | Used for the follow-up respond call. |
| `authAttemptProofToken` | One-time proof token for respond | Pending Authentication | No | No | Must be signed later for `respond`. |
| `authAttemptProofTokenSignedByIntegration` | Integration signature on pending payload | Pending Authentication | No | No | Must verify before displaying request context. |
| `authAttemptChallengeRequired` | Whether a 2-digit challenge is required | Pending Authentication | No | Yes | Controls challenge input visibility and validation. |
| `contextTitle` | Context title | Pending Authentication | No | Yes | Primary request header when present. |
| `contextMessage` | Context message | Pending Authentication | No | Yes | User-visible request explanation when present. |

### Pending Local Mapping Narrative

The Pending Authentication screen owns the `pending` call. It first ensures the enrollment key pair exists,
generates a fresh `deviceProofToken`, signs it, then calls the Auth API. On a successful pending response, it
rebuilds the canonical pending payload and verifies the integration Ed25519 signature with the stored
`integrationPublicKey`. Only then does it create an in-memory `PendingAttempt` object and render the request.

| Local concept | Mapped from | Used by | Notes |
| --- | --- | --- | --- |
| `PendingAttempt.authAttemptId` | `authAttemptId` | Respond flow | Stored only in component state. |
| `PendingAttempt.authAttemptProofToken` | `authAttemptProofToken` | Respond signature creation | Never displayed. |
| `PendingAttempt.challengeRequired` | `authAttemptChallengeRequired` | Pending screen UI | Controls challenge input requirement. |
| `PendingAttempt.contextTitle` | `contextTitle` | Pending screen UI | Optional primary content. |
| `PendingAttempt.contextMessage` | `contextMessage` | Pending screen UI | Optional request explanation. |

### Pending Trigger and Ownership

| Trigger point | Screen/hook/service | Preconditions | Notes |
| --- | --- | --- | --- |
| Screen mount | Pending Authentication | Enrollment exists locally | The screen loads pending immediately once the enrollment is available. |
| Tap "Check again" | Pending Authentication | None beyond enrollment availability | Manual re-check preserves the user-initiated polling model. |
| `authAttemptsApi.pending(...)` | `app/services/api/authAttempts.ts` | Proof token and signature already prepared | Optional `authUrl` routes to the correct Auth API base URL. |

### Pending Outcomes and Error Categories

| Condition | Technical outcome | User-visible outcome | Persistence impact |
| --- | --- | --- | --- |
| `200 OK` with valid signature | `PendingAttempt` created in memory | Request details shown | None |
| `204 No Content` / no body | No local attempt created | "No pending requests" state | None |
| Missing integration public key | Flow blocked locally | Global error shown | None |
| Invalid pending signature | Flow fails closed | Global error shown | None |
| Request failure / transport failure | Error surfaced | Global error shown | None |

## Auth Attempt Respond Mapping

### Respond Endpoint and DTO Identity

- Service facade: `app/services/api/authAttempts.ts`
- Wrapper types: `RespondAuthRequest`, `RespondAuthResponse`
- Primary trigger: Pending Authentication screen after explicit user approve or deny action
- Primary local output: volatile latest-response summary on Enrollment Detail

### Respond Request Fields

| Request field | Meaning | Required? | Source in app | Security note |
| --- | --- | --- | --- | --- |
| `authAttemptId` | Authentication attempt identifier | Yes | Current `PendingAttempt` | Serialized to JSON number before sending. |
| `authAttemptAccepted` | User decision | Yes | Approve or deny action | Encodes the human decision. |
| `authAttemptProofTokenSignedByDevice` | Device signature on canonical respond payload | Yes | `cryptoService.sign(...)` over `buildRespondPayload(...)` | Cryptographically binds the decision to the one-time proof token. |
| `authAttemptChallengeResponse` | User-entered challenge response | Optional | Pending screen challenge input | Required locally only when the pending response requires it. |

### Respond Response Fields

| Response field | Meaning | Consumed by | Persisted? | Displayed? | Security note |
| --- | --- | --- | --- | --- | --- |
| `authAttemptId` | Attempt identifier echoed by server | Pending Authentication | No | No | Used to rebuild the signed result payload. |
| `authAttemptResult` | Outcome enum | Pending Authentication | No | Yes | Drives accepted, rejected, or failed state. |
| `authAttemptMessage` | Human-readable result message | Pending Authentication | No | Sometimes | Used especially on failed outcomes. |
| `authAttemptProofTokenResultSignedByIntegration` | Integration signature on result payload | Pending Authentication | No | No | Must verify before the app trusts and displays the result. |

### Respond Local Mapping Narrative

The Pending Authentication screen signs the canonical respond payload with the enrollment device key and sends the
decision. It then verifies the integration-signed result with the stored `integrationPublicKey`. A verified
response updates a volatile latest-response summary for the enrollment and returns immediately to Enrollment Detail.
`APPROVED`, `DENIED`, and other verified outcomes are summarized there without creating a durable local history.

| Local concept | Mapped from | Used by | Notes |
| --- | --- | --- | --- |
| Accept / deny decision | User action | Respond request body | Chosen explicitly on the pending screen. |
| Challenge input | User input | Optional respond field | Used only when challenge is required. |
| Latest response summary | `authAttemptResult`, `authAttemptMessage`, context title/message | Enrollment Detail UI | Volatile only; not persisted. |
| Failure message | `authAttemptMessage` | Enrollment Detail summary or Pending screen error UI | Used for failed summaries or request errors. |

### Respond Trigger and Ownership

| Trigger point | Screen/hook/service | Preconditions | Notes |
| --- | --- | --- | --- |
| Tap approve | Pending Authentication | Pending attempt loaded; challenge valid if required | User-driven action only. |
| Tap deny | Pending Authentication | Pending attempt loaded | Deny path does not require a challenge. |
| `authAttemptsApi.respond(...)` | `app/services/api/authAttempts.ts` | Device signature already created | Wrapper serializes numeric fields and includes challenge only when present. |

### Respond Outcomes and Error Categories

| Condition | Technical outcome | User-visible outcome | Persistence impact |
| --- | --- | --- | --- |
| Verified `APPROVED` | Latest response summary updated | Approved summary shown on Enrollment Detail | None |
| Verified `DENIED` | Latest response summary updated | Rejected summary shown on Enrollment Detail | None |
| Verified non-success result | Latest response summary updated | Failed summary shown on Enrollment Detail | None |
| Missing result signature | Flow blocked locally | Global error shown | None |
| Invalid result signature | Flow fails closed | Global error shown | None |
| Request failure / transport failure | Error surfaced | Global error shown | None |

## Screen-to-Field Matrix

| Field | Bind | Verify | Pending | Respond | Home | Wizard | Detail | Pending screen | Persisted local model |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `enrollmentId` | request + response | request | request | no | hidden | hidden | indirect via route | hidden | `id`, `enrollmentId` |
| `enrollmentProofToken` | request + response | signed input | request | no | hidden | hidden | hidden | hidden | `enrollmentProofToken` |
| `integrationPublicKey` | response | verify-result trust check | trust check | result trust check | hidden | hidden | hidden | hidden | `integrationPublicKey` |
| `integrationKeyAlgorithm` | response | no | no | no | hidden | hidden | hidden | hidden | not persisted |
| `integrationName` | response | no | enrollment context only | enrollment context only | shown | shown | shown | shown | `integrationName` |
| `tenantName` | response | no | enrollment context only | enrollment context only | shown | shown | shown | shown when no context title | `tenantName` |
| `tenantDescription` | response | no | no | no | shown in grouped list headers | shown | no | no | `tenantDescription` |
| `enrollmentName` | response | no | enrollment context only | enrollment context only | not shown on Home card today | shown | shown | shown in result fallback | `enrollmentName`, `deviceLabel` |
| `devicePublicKey` | no | request | no | no | hidden | hidden | hidden | hidden | not in `StoredEnrollment` |
| `devicePrivateKeyStorageTier` | no | request | no | no | hidden | hidden | hidden | hidden | not persisted locally |
| `authAttemptId` | no | no | response | request + response | hidden | hidden | hidden | hidden | not persisted |
| `authAttemptProofToken` | no | no | response | signed input | hidden | hidden | hidden | hidden | not persisted |
| `authAttemptChallengeRequired` | no | no | response | request precondition | hidden | hidden | hidden | shown | not persisted |
| `contextTitle` | no | no | response | response context | hidden | hidden | hidden | shown | not persisted |
| `contextMessage` | no | no | response | response context | hidden | hidden | hidden | shown | not persisted |
| `authUrl` | QR-derived precondition | request routing | request routing | request routing | hidden | optional server info | shown when custom | hidden | `authUrl` |

## Error and Outcome Mapping Summary

| Operation | Error/Outcome | Technical meaning | UX handling |
| --- | --- | --- | --- |
| Bind | Invalid algorithm or invalid signature | Cannot trust integration identity | Bind error and flow stop |
| Bind | Transport/backend error | Request failed before draft creation | Bind error |
| Verify | Invalid challenge locally | Submission blocked in UI | Challenge error |
| Verify | Invalid signed result | Cannot trust enrollment completion | Challenge error and no persistence |
| Verify | Request/persistence error | Enrollment not completed | Challenge error |
| Pending | `204 No Content` | No available auth attempt | Empty state with manual re-check |
| Pending | Invalid integration signature | Cannot trust request context | Global error |
| Pending | Request failure | Loading failed | Global error with retry |
| Respond | Verified approved/denied result | Final trusted outcome received | Latest response summary shown on Enrollment Detail |
| Respond | Verified failed result | Attempt ended unsuccessfully | Failed summary shown on Enrollment Detail |
| Respond | Missing/invalid result signature | Cannot trust outcome payload | Global error |
| Respond | Request failure | Response submission failed | Global error |

## Notes on Contract Drift and Verification Rules

- The app intentionally validates `integrationKeyAlgorithm` during bind before trusting `integrationPublicKey`.
- Home does not trigger `pending`; the explicit user path is Home -> Enrollment Detail -> Pending Authentication.
- The current implementation persists the enrollment proof token and integration public key in the local record so later pending/respond trust checks can run without refetching bind state.
- The current mobile documentation should explicitly note that some PRD expectations remain ahead of the implementation, especially around activity history and certain placeholder states.
