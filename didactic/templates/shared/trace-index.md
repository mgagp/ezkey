---
title: "Ezkey protocol trace — enrollment and MFA (index)"
---

# Enrollment and MFA — trace index

This index accompanies **`steps.jsonl`**: that file preserves every HTTP/Crypto step in structured form; **this Markdown** summarizes the human reading order within the **same exported bundle**. Publication-friendly **single-scroll** narratives consolidate every payload inline—those exports omit reliance on auxiliary documentation.

## Narrative framing

An **operator-issued Bearer** provisions **pending enrollments** (`integration_id` determines which integration row absorbs the issuance) and launches **authentication attempts** via the **same Auth API endpoints** an enrolled handset drives once credentials exist. Bearer acquisition through passwordless console login stays **upstream** from the transcript bundle—the enrollment excerpts never depict that administration login choreography.

Separate publications may explore **integration API keys driving identical cryptography**: this capture concentrates on Bearer-backed issuance so payloads stay legible alongside signing helpers.

### MFA pacing

After **`admin.auth_attempt_create`**, the capture’s TTL metadata (**`timeoutSeconds`**, **`expiresAt`**) governs **`pending`** and **`respond`**—values appear beside the TTL insert in companions that flatten everything into Markdown, or in **`summary.json`** when distributing the tooling archive.

### MFA continuity (pending → respond)

Ezkey consumes a successful **`pending`** claim **once** per auth attempt (**`READ`** after validations), so **`authAttemptProofToken`** exits the verifier only inside **`authAttemptProofTokenSignedByIntegration`**. ECDSA **`respond`** concatenates **`authAttemptProofToken|accepted`**—reusing byte-identical literals from that issuance—meaning **`respond`** without a truthful prior **`pending`** cannot align with verifier math.

## Suggested traversal of `steps.jsonl`

1. `admin.enrollment_create` → `admin.enrollment_get` (expose proof token for bind).
2. `mobile.enrollment_bind` with `crypto.enrollment_bind_*` (bind canonical + Ed25519 check).
3. `crypto.device_keypair` → verify-device canonical ECDSA chain → `mobile.enrollment_verify` → `crypto.enrollment_verify_result_*`.
4. `admin.auth_attempt_create` (**TTL** informs pending/respond timing).
5. `crypto.device_proof_token` → `mobile.auth_pending` → pending reconstruction + verification → **`respond`** flow closing with `crypto.respond_result_*`.
6. Optional **`admin.auth_attempt_wait`** synchronous terminal polling.
