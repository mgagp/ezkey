---
title: "Ezkey protocol trace — enrollment, verify, MFA (access login path)"
audience: "Experienced API developers revisiting Ezkey’s signed enroll + auth posture"
scenario: "access_login_accept_or_reject_via_run_yaml"
---

# Ezkey enrollment and authentication — access-login trace

This article reconstructs one complete **integration access** exchange from a single capture session: an operator issues a pending enrollment, a handset binds and verifies itself, then that same handset approves a sign-in request for the integration. **Recording context:** a disposable lab stack where Admin API, Auth API, and Crypto API were reachable together (typically **9080 / 8080 / 9090** on the workstation that produced this file).

The point is **not** to dump raw JSON and call it documentation. The value here is the stitched reading path: **who acts**, **which UTF‑8 canonical line is signed**, **which key verifies it**, and **what trust decision follows** at each step. Canonical signing lines appear **verbatim** beside the cryptography helper excerpts; no companion reader kit is assumed.

**Lab versus production crypto path:** excerpts labeled **Crypto API** reproduce the same **deterministic canonical strings** (pipe-separated UTF‑8 lines) that a shipping mobile client builds and signs locally. Production devices **never** delegate those steps to Ezkey Crypto API—the service exists in the lab **only** to make payloads inspectable beside the REST traffic. Admin API and Auth API traffic match real deployments.

## What this trace covers (and what it omits)

**On the wire here:** credentials issued with a **Global or scoped-admin Bearer token** via **Admin API**—**pending enrollments** tied to **`integrationId`**, then **authentication attempts** initiated the same way. A **simulated device** consumes **bind**, **verify**, **pending**, and **respond** on **Auth API**. The narrative posture is intentionally simple: this is a user approving access to an integration, not a richer business workflow such as payment approval or batch validation.

**Intentionally off-screen:** fetching the bearer used to call Admin API through **passwordless operator login** (device approval flows). Those steps run **outside** this transcript—they never occupy an enrollment payload line below. Likewise, enrolling the administrator’s personal MFA posture for console access is **a different storyline** than the integration-scoped credential shown here.

**Out of scope for this choreography:** provisioning an integration exclusively through **tenant API keys** or driving every issuance call from integration credentials—the capture below keeps **operator Bearer automation** explicit so the cryptography stays observable.

---

## Scope and posture

Enrollment **activation** binds a pending credential to hardware (via proof-token possession), **verifies** the device signing key against the enrollment challenge, then treats the row as usable. Separately an **authentication attempt** expresses MFA demand: integrating backends create the attempt while the handset **polls pending** and emits a signed decision. In this template the decision is framed as a straightforward **sign-in or access approval** for the integration.

**Algorithms:** Integration authorities sign fixed canonical lines with **Ed25519**. Device attestations rely on **EC P‑256 ECDSA** over deterministic strings—clients never authenticate by blindly signing opaque JSON blobs.

### Outline

| # | Actor | Functional step |
| --- | --- | --- |
| 1 | Operator API | Create enrollment; follow with administrative **GET** to pull **proof token** and challenge—the create envelope alone routinely omits the proof token operators need downstream. |
| 2 | Simulated device | **Bind:** redeem proof token for integration metadata plus integration-signed **`enrollmentBindPayloadSignedByIntegration`**; verify integration Ed25519 before interpreting fields. |
| 3 | Device + oracle | Build bind canonical UTF‑8 line; verify Ed25519; mint device keypair; derive verify-device canonical line; ECDSA-sign for Auth API `/verify`. |
| 4 | Auth API | Approve enrollment when policy allows and return integration-signed **verify-result**. |
| 5 | Simulated device + oracle | Fetch enrolled **`instance-info`**, rebuild the canonical `INSTANCE_INFO` payload, and verify the integration Ed25519 signature before trusting branding fields. |
| 6 | Operator API | Create an authentication attempt representing an integration access request for the enrolled user. |
| 7 | Device + oracle | Fresh **device proof token** pipeline; ECDSA-signed **pending** poll; reconstruct **pending** canonical line; ECDSA-signed **respond** decision; consume integration-signed **respond-result**. |
| 8 | Operator API (often) | **Wait** call blocks until MFA attempt settles—matching synchronous integrator UX probes. |

Each section pairs a short explanatory lead with the matching request and response payloads.

---

## Administration — issuing enrollment credentials

The operator inserts a **pending enrollment** keyed to **`integrationId`**. The **create** handshake hands back identifiers and enrollment **challenge**. **Bind** consumes **`enrollmentProofToken`**; that nonce is ordinarily revealed through the administrative **GET** shown next, rather than echoed wholesale in **create**.

Illustrative captures often label enrollments using the familiar cryptographic roster personas (**Alice**, **Bob**, **Carol**, … with realistic surnames) so payloads resemble production roster imports without inventing lore.

### POST `/api/v1/enrollments` (create)

<<<ARTIFACT admin.enrollment_create request>>>

<<<ARTIFACT admin.enrollment_create response>>>

In **create**, **`integrationId`** scopes issuance to exactly one integration record; **`name`** survives into signed payloads with integration-facing title strings; **`authAttemptChallengeRequired`** pins whether MFA attempts may escalate to auxiliary short challenges downstream.

---

### GET `/api/v1/enrollments/:id`

<<<ARTIFACT admin.enrollment_get request>>>

<<<ARTIFACT admin.enrollment_get response>>>

**Bind** prerequisite: **`enrollmentProofToken`** is mandatory. Many stacks omit returning it verbatim on **POST create** responses; authoritative retrieval for operators feeding QR or copy channels is **`GET`** on the enrollment identifier.

---

## Device posture — bind, verify, then fetch signed instance branding

On **bind**, possessing **`enrollmentProofToken`** earns the handset integration-backed metadata bundles plus **`enrollmentBindPayloadSignedByIntegration`**. Clients rebuild the deterministic **canonical bind line**: a single UTF‑8 row with **`|`** between enrollment proof fragments, hashing aids, **`ed25519`**, integration and tenant copy, the **`isSystemIntegration`** boolean, and the optional **`adminType`** literal when the enrollment is administrator-linked. Ed25519 must validate against **`integrationPublicKey`** from the same envelope before any field becomes authoritative.

### POST `/api/v1/enrollments/bind`

<<<ARTIFACT mobile.enrollment_bind request>>>

<<<ARTIFACT mobile.enrollment_bind response>>>

The preceding JSON summarizes human-visible labels and PEM material. Integrity evidence is **`enrollmentBindPayloadSignedByIntegration`**. The paired Crypto excerpts below regenerate the canonical UTF‑8 substrate and certify Ed25519 against **`integrationPublicKey`**—matching how production SDKs wire their local crypto providers.

### Crypto API — bind canonical line and Ed25519 verification

**Helper usage:** `payload-helper` with type `enrollment-bind` materializes the canonical string; `verify-ed25519` pins that signature to the bind response’s **integration** public key and exact bytes.

<<<ARTIFACT crypto.enrollment_bind_payload request>>>

<<<ARTIFACT crypto.enrollment_bind_payload response>>>

<<<ARTIFACT crypto.enrollment_bind_verify request>>>

<<<ARTIFACT crypto.enrollment_bind_verify response>>>

---

### Device keys and verify-device canonical line (`enrollment-verify-device`)

The handset provisions an **EC P‑256** keypair (here via Crypto API `GET /keypair`; shipping apps use keystore / secure enclave surfaces). `payload-helper` type `enrollment-verify-device` concatenates `enrollmentProofToken|enrollmentId|challengeResponse|devicePublicKey` as one canonical UTF‑8 line. **`POST /crypto/sign`** produces ECDSA over that exact string; the signature populates **`enrollmentProofTokenSigned`** on **POST `/api/v1/enrollments/verify`**.

<<<ARTIFACT crypto.device_keypair request>>>

<<<ARTIFACT crypto.device_keypair response>>>

<<<ARTIFACT crypto.enrollment_verify_device_payload request>>>

<<<ARTIFACT crypto.enrollment_verify_device_payload response>>>

<<<ARTIFACT crypto.enrollment_verify_sign request>>>

<<<ARTIFACT crypto.enrollment_verify_sign response>>>

---

### POST `/api/v1/enrollments/verify`

Auth API validates ECDSA device evidence, activates enrollment consistent with tenancy policy, then returns **`enrollmentVerifyPayloadSignedByIntegration`** attesting integration approval over the canonical **verify-result** line.

<<<ARTIFACT mobile.enrollment_verify request>>>

<<<ARTIFACT mobile.enrollment_verify response>>>

### Crypto API — verify-result integration signature

Rebuild the canonical **verify-result** line with the same tooling pattern as bind, then certify Ed25519 with the unchanged integration signing key surfaced earlier.

<<<ARTIFACT crypto.enrollment_verify_result_payload request>>>

<<<ARTIFACT crypto.enrollment_verify_result_payload response>>>

<<<ARTIFACT crypto.enrollment_verify_result_verify request>>>

<<<ARTIFACT crypto.enrollment_verify_result_verify response>>>

---

### POST `/api/v1/enrollments/instance-info`

Once enrollment is active, the official mobile path does **not** trust unsigned public branding. It calls enrolled **`instance-info`** with the same **`enrollmentProofToken`** used during bind/verify, receives deployment branding plus **`instanceInfoPayloadSignedByIntegration`**, and must verify that Ed25519 signature before applying **`authApiPublicBaseUrl`**, **`instanceName`**, **`instanceDescription`**, or **`aboutUrl`**.

<<<ARTIFACT mobile.enrollment_instance_info request>>>

<<<ARTIFACT mobile.enrollment_instance_info response>>>

### Crypto API — enrolled instance-info integration signature

The lab oracle reconstructs the canonical line `proofToken|enrollmentId|INSTANCE_INFO|authApiPublicBaseUrl|instanceName|instanceDescription|aboutUrl`, then verifies the returned Ed25519 signature with the same integration key used for bind and verify-result.

<<<ARTIFACT crypto.enrollment_instance_info_payload request>>>

<<<ARTIFACT crypto.enrollment_instance_info_payload response>>>

<<<ARTIFACT crypto.enrollment_instance_info_verify request>>>

<<<ARTIFACT crypto.enrollment_instance_info_verify response>>>

---

## Administration — opening an MFA attempt

### Authentication attempt TTL

After **POST** creating an MFA attempt the stack enforces **`timeoutSeconds`** with an absolute **`expiresAt`**. **`respond`** on the handset must conclude inside that horizon—TTL metadata for this capture fills the block below.

<<<SUMMARY_TTL>>>

Opening an MFA **authentication attempt** for a verified enrollment yields attempt identifiers plus metadata echoed in transcripts. In this template the example remains intentionally plain: the handset is asked to approve access to the integration, not to authorize a richer business transaction. If **`contextTitle`** or **`contextMessage`** are present, they are still covered by the signed **pending** payload, but they should read as simple sign-in or access copy.

<<<ARTIFACT admin.auth_attempt_create request>>>

<<<ARTIFACT admin.auth_attempt_create response>>>

---

## Device posture — pending then respond

MFA choreography reuses the disciplined pattern above: ECDSA-signed **proof token**, **pending** retrieval with **`authAttemptProofTokenSignedByIntegration`** requiring Ed25519 confirmation, ECDSA-signed **respond**, then **`authAttemptProofTokenResultSignedByIntegration`** validating the eventual integration stance over the **respond-result** canonical substrate.

### Read-once `pending` and chaining `respond`

While an MFA attempt sits **`PENDING`**, callers may probe with fresh ephemeral **`deviceProofToken`** payloads until Ezkey validates one complete poll. That successful poll flips the attempt from **`PENDING`** to **`READ`** and returns the one proof token the handset needs for the next step. From that moment, the mobile side must verify the integration Ed25519 signature over the reconstructed **pending** canonical line before trusting the displayed title, message, or challenge posture. Only then can it sign the follow-up **respond** payload with the exact **`authAttemptProofToken`** it just received.

Cryptography snippets below occupy the same structural role handset firmware plays in deployment.

### Crypto API — device proof token and ECDSA

Issue a fresh ephemeral **`deviceProofToken`**, ECDSA-signed with enrollment’s device private key—the signature rides **pending** as proof-of-handset.

<<<ARTIFACT crypto.device_proof_token request>>>

<<<ARTIFACT crypto.device_proof_token response>>>

<<<ARTIFACT crypto.device_proof_token_sign request>>>

<<<ARTIFACT crypto.device_proof_token_sign response>>>

---

### POST `/api/v1/auth-attempts/pending`

Determine whether MFA state exists for handset plus enrollment linkage. **`200`** embeds **`authAttemptProofTokenSignedByIntegration`**; authenticate Ed25519 on the reconstructed **pending** canonical line exactly like bind before treating the MFA payload fields as authoritative. If the integration supplied a simple access title or short message, those strings are signed too; they are secondary to the protocol, but not outside it.

<<<ARTIFACT mobile.auth_pending request>>>

<<<ARTIFACT mobile.auth_pending response>>>

### Crypto API — pending integration signature verification

<<<ARTIFACT crypto.pending_payload request>>>

<<<ARTIFACT crypto.pending_payload response>>>

<<<ARTIFACT crypto.pending_verify request>>>

<<<ARTIFACT crypto.pending_verify response>>>

---

### Respond canonical line (`authAttemptProofToken|true|false`)

The accept/deny decision reduces to an ECDSA commitment over **`authAttemptProofToken|accepted`**. On the acceptance branch, that means the handset signs **`authAttemptProofToken|true`** and the server recomputes the exact same two-segment line before recording the result.

<<<ARTIFACT crypto.respond_device_sign request>>>

<<<ARTIFACT crypto.respond_device_sign response>>>

---

### POST `/api/v1/auth-attempts/respond`

<<<ARTIFACT mobile.auth_respond request>>>

<<<ARTIFACT mobile.auth_respond response>>>

### Crypto API — respond-result integration signature

The JSON embeds **`authAttemptProofTokenResultSignedByIntegration`**. Reproduce the **`respond-result`** canonical string using `payload-helper` type **`respond-result`**, then certify Ed25519—closing the MFA loop exactly as integrations signed earlier segments.

<<<ARTIFACT crypto.respond_result_payload request>>>

<<<ARTIFACT crypto.respond_result_payload response>>>

<<<ARTIFACT crypto.respond_result_verify request>>>

<<<ARTIFACT crypto.respond_result_verify response>>>

---

## Administration — synchronous wait

When tooling chains **admin `/wait`**, transcripts include the blocking poll that observes Auth API attempt convergence—useful symmetry for backends that hinge UX on deterministic completion cues.

<<<ARTIFACT admin.auth_attempt_wait request>>>

<<<ARTIFACT admin.auth_attempt_wait response>>>

---

## Closing

Ezkey concentrates trust in explicit **canonical UTF‑8 scaffolding**: integration Ed25519 for integration-issued payloads, ECDSA handset math for proofs and MFA decisions, and a read-once pending handshake that forces the device to bind its final decision to the exact attempt it claimed. In an access-login walkthrough like this one, any sign-in copy displayed to the user is inside the same signed envelope as the proof token and challenge posture. The Crypto API excerpts prove the strings **mechanically**; production mobiles implement the identical math locally without round-tripping through lab services.
