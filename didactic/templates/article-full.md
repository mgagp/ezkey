---
title: "Ezkey protocol trace — enrollment, verify, MFA (generated)"
audience: "Experienced API developers revisiting Ezkey’s signed enroll + auth posture"
scenario: "accept_or_reject_via_run_yaml"
---

# Ezkey enrollment and authentication — instrumented trace

This narrative stitches **HTTP and Crypto API transcripts** from a single capture session. **Recording context:** a disposable lab stack where Admin API, Auth API, and Crypto API were reachable together (typically **9080 / 8080 / 9090** on the workstation that produced this file).

The point is **not** scrolling raw JSON alone. Protocol meaning—**actors**, **which UTF‑8 payloads are signed**, **under which keys**, and **why** integration signatures must be verified before trusting response fields—is stated **alongside** the artifacts. Canonical signing lines appear **verbatim** beside the cryptography helper excerpts; no companion reader kit is assumed.

**Lab versus production crypto path:** excerpts labeled **Crypto API** reproduce the same **deterministic canonical strings** (pipe-separated UTF‑8 lines) that a shipping mobile client builds and signs locally. Production devices **never** delegate those steps to Ezkey Crypto API—the service exists in the lab **only** to make payloads inspectable beside the REST traffic. Admin API and Auth API traffic match real deployments.

## What this trace covers (and what it omits)

**On the wire here:** credentials issued with a **Global or scoped-admin Bearer token** via **Admin API**—**pending enrollments** tied to **`integrationId`**, then **authentication attempts** initiated the same way. A **simulated device** consumes **bind**, **verify**, **pending**, and **respond** on **Auth API**. Behaviorally these rows match what an integration would mint for an **end-user handset**: proof tokens, enrollment challenge handling, EC P‑256 device keys, MFA polling and approve/deny—**shown end-to-end below**.

**Intentionally off-screen:** fetching the bearer used to call Admin API through **passwordless operator login** (device approval flows). Those steps run **outside** this transcript—they never occupy an enrollment payload line below. Likewise, enrolling the administrator’s personal MFA posture for console access is **a different storyline** than the integration-scoped credential shown here.

**Out of scope for this choreography:** provisioning an integration exclusively through **tenant API keys** or driving every issuance call from integration credentials—the capture below keeps **operator Bearer automation** explicit so the cryptography stays observable.

---

## Scope and posture

Enrollment **activation** binds a pending credential to hardware (via proof-token possession), **verifies** the device signing key against the enrollment challenge, then treats the row as usable. Separately an **authentication attempt** expresses MFA demand: integrating backends create the attempt while the handset **polls pending** and emits a signed **approve / deny**.

**Algorithms:** Integration authorities sign fixed canonical lines with **Ed25519**. Device attestations rely on **EC P‑256 ECDSA** over deterministic strings—clients never authenticate by blindly signing opaque JSON blobs.

### Outline

| # | Actor | Functional step |
|---|--------|-----------------|
| 1 | Operator API | Create enrollment; follow with administrative **GET** to pull **proof token** and challenge—the create envelope alone routinely omits the proof token operators need downstream. |
| 2 | Simulated device | **Bind:** redeem proof token for integration metadata plus integration-signed **`enrollmentBindPayloadSignedByIntegration`**; verify integration Ed25519 before interpreting fields. |
| 3 | Device + oracle | Build bind canonical UTF‑8 line; verify Ed25519; mint device keypair; derive verify-device canonical line; ECDSA-sign for Auth API `/verify`. |
| 4 | Auth API | Approve enrollment when policy allows and return integration-signed **verify-result**. |
| 5 | Operator API | Instantiate **authentication attempt** for verified enrollment row. |
| 6 | Device + oracle | Fresh **device proof token** pipeline; ECDSA-signed **pending** poll; reconstruct **pending** canonical line; ECDSA-signed **respond** decision; consume integration-signed **respond-result**. |
| 7 | Operator API (often) | **Wait** call blocks until MFA attempt settles—matching synchronous integrator UX probes. |

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

## Device posture — bind and verify enrollment

On **bind**, possessing **`enrollmentProofToken`** earns the handset integration-backed metadata bundles plus **`enrollmentBindPayloadSignedByIntegration`**. Clients rebuild the deterministic **canonical bind line**: a single UTF‑8 row with **`|`** between enrollment proof fragments, hashing aids, **`ed25519`**, integration title copy, **`enrollmentName`**, and empty slots where optional tails stay blank—matching the verifier byte-for-byte. Ed25519 must validate against **`integrationPublicKey`** from the same envelope before any field becomes authoritative.

### POST `/api/v1/enrollments/bind`

<<<ARTIFACT mobile.enrollment_bind request>>>

<<<ARTIFACT mobile.enrollment_bind response>>>

The preceding JSON summarizes human-visible labels and PEM material. Integrity evidence is **`enrollmentBindPayloadSignedByIntegration`**. The paired Crypto excerpts below regenerate the hashed UTF‑8 substrate and certify Ed25519 against **`integrationPublicKey`**—matching how production SDKs wire their local crypto providers.

### Crypto API — bind canonical line and Ed25519 verification

**Helper usage:** `payload-helper` with type `enrollment-bind` materializes the canonical string; `verify-ed25519` pins that signature to the bind response’s **integration** public key and exact bytes.

<<<ARTIFACT crypto.enrollment_bind_payload request>>>

<<<ARTIFACT crypto.enrollment_bind_payload response>>>

<<<ARTIFACT crypto.enrollment_bind_verify request>>>

<<<ARTIFACT crypto.enrollment_bind_verify response>>>

---

### Device keys and verify-device canonical line (`enrollment-verify-device`)

The handset provisions an **EC P‑256** keypair (here via Crypto API `GET /keypair`; shipping apps use keystore / secure enclave surfaces). `payload-helper` type `enrollment-verify-device` concatenates  
`enrollmentProofToken|enrollmentId|challengeResponse|devicePublicKey` **as one line** with the separators shown. **`POST /crypto/sign`** produces ECDSA over that UTF‑8 string; the signature populates **`enrollmentProofTokenSigned`** on **POST `/api/v1/enrollments/verify`**—the commitment covers the **entire canonical line**, not an isolated random-looking token fragment.

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

## Administration — opening an MFA attempt

### Authentication attempt TTL

After **POST** creating an MFA attempt the stack enforces **`timeoutSeconds`** with an absolute **`expiresAt`**. **`respond`** on the handset must conclude inside that horizon—TTL metadata for this capture fills the block below.

<<<SUMMARY_TTL>>>

Opening an MFA **authentication attempt** for a verified enrollment yields attempt identifiers plus metadata echoed in transcripts. Subsequent integration-signed JSON sections each map to deterministic **canonical UTF‑8 pipes** regenerated from literals in the payloads. Device ECDSA attestations authenticate short-lived **`deviceProofToken`** values first, later binding **approval bits** spelled lowercase **`true`** or **`false`** between identical pipe scaffolding so verifiers recombine bytes without ambiguity.

<<<ARTIFACT admin.auth_attempt_create request>>>

<<<ARTIFACT admin.auth_attempt_create response>>>

---

## Device posture — pending then respond

MFA choreography reuses the disciplined pattern above: ECDSA-signed **proof token**, **pending** retrieval with **`authAttemptProofTokenSignedByIntegration`** requiring Ed25519 confirmation, ECDSA-signed **respond**, then **`authAttemptProofTokenResultSignedByIntegration`** validating the eventual integration stance over the **respond-result** canonical substrate.

### Read-once `pending` and chaining `respond`

While an MFA attempt sits **`PENDING`**, callers may probe with fresh ephemeral **`deviceProofToken`** payloads until Ezkey validates one complete poll (**`pending`** excerpts below illustrate the succeeding round). Persistence then marks the authentication attempt **`READ`**, pinning the asserted handset proof alongside it, **closing further successful reads** of matching pending context for **that auth attempt.** **`authAttemptProofToken`** is delivered inside **`authAttemptProofTokenSignedByIntegration`** exclusively through that acknowledgement; validating Ed25519 on the reconstructed **`pending`** canonical line is mandatory before handset UX treats operator wording as authoritative. ECDSA **`respond`** signs **`authAttemptProofToken|accepted`**, reusing **verbatim** token bytes introduced only during that acknowledgement. Omitting **`pending`** withholds verifier‑matching literals—Ezkey binds **`respond`** to cryptographic state surfaced **exactly once** when **`pending`** first succeeds—structural reinforcement against replay harvesting after consumption.

Cryptography snippets below occupy the same structural role handset firmware plays in deployment.

### Crypto API — device proof token and ECDSA

Issue a fresh ephemeral **`deviceProofToken`**, ECDSA-signed with enrollment’s device private key—the signature rides **pending** as proof-of-handset.

<<<ARTIFACT crypto.device_proof_token request>>>

<<<ARTIFACT crypto.device_proof_token response>>>

<<<ARTIFACT crypto.device_proof_token_sign request>>>

<<<ARTIFACT crypto.device_proof_token_sign response>>>

---

### POST `/api/v1/auth-attempts/pending`

Determine whether MFA state exists for handset plus enrollment linkage. **`200`** embeds **`authAttemptProofTokenSignedByIntegration`**; authenticate Ed25519 on the reconstructed **pending** canonical line exactly like bind before treating the MFA payload fields as authoritative.

<<<ARTIFACT mobile.auth_pending request>>>

<<<ARTIFACT mobile.auth_pending response>>>

### Crypto API — pending integration signature verification

<<<ARTIFACT crypto.pending_payload request>>>

<<<ARTIFACT crypto.pending_payload response>>>

<<<ARTIFACT crypto.pending_verify request>>>

<<<ARTIFACT crypto.pending_verify response>>>

---

### Respond canonical line (`authAttemptProofToken|true|false`)

Approval reduces to ECDSA commitment over **`authAttemptProofToken|true|false`**: literal acceptance strings stay lowercase ASCII squeezed between untouched pipe separators so servers bit-match reconstructions anchored on **`respond-device`** transcripts.

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

Ezkey concentrates trust in explicit **canonical UTF‑8 scaffolding**: integration Ed25519 for integration-issued payloads, ECDSA handset math for proofs and MFA decisions—each verified before acceptance. Cryptography API excerpts in this artifact prove those strings **mechanically**; production mobiles implement the identical math locally without round-tripping through lab services.
