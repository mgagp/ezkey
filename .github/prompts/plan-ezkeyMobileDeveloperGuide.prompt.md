# Plan: EZKey Mobile Developer Implementation Guide

## TL;DR

Créer un guide d'implémentation complet à destination des développeurs d'applications MFA mobiles (Android-first) souhaitant supporter le protocole EZKey. Le document est technique, pragmatique, avec des diagrammes Mermaid ciblés, une spécification exhaustive de chaque champ, et un fil directeur de validation locale via Docker.

---

## Decisions & Scope

- **Language**: English (international audience)
- **Output file**: `docs/MOBILE_DEVELOPER_GUIDE.md`
- **Android KeyStore**: Conceptual + code excerpts from `EzkeyCryptoModule.kt` as reference implementation
- **Integration response signature**: Mandatory — integrated into the main flow, not optional
- **Tone**: Expert, opinionated, honest — positioning EZKey vs FIDO2/WebAuthn without overstatement
- **Format**: Markdown with Mermaid diagrams — context overview, enrollment workflow, auth workflow, canonical payload
- **Local reference**: Docker clean-start stack, Crypto API port 9090, OpenAPI on each service
- **Out of scope**: Admin API (provisioning), iOS Keychain detailed coverage, integration provisioning

---

## Document Structure

### Section 1 — Executive Summary (~½ page)

Pragmatic positioning:
- What EZKey is: self-hosted, backend-first cryptographic MFA platform with its own protocol
- What it is **not**: not FIDO2, not WebAuthn, not passkey — deliberately
- Value proposition: end-to-end cryptographic chain, Android KeyStore (private key never exposed), operational transparency, self-hosted
- Target audience: open-source MFA app developers (Aegis, andOTP, etc.) wanting to add EZKey support

---

### Section 2 — System Context Diagram (Mermaid C4-like)

Single diagram providing the global context:
- Actors: End User, Mobile App, **Auth API** (port 8080), **Integration API** (port 7080), Backend System (integration consumer)
- Crypto API (port 9090) appears as "testing only"
- Legend of the two key types: EC P-256 (device) ↔ Ed25519 (integration)
- Data flows: who calls whom, under which protocol

---

### Section 3 — Cryptographic Foundation (~1 page)

The two signing contexts — clearly laid out before entering workflows:

| Context | Algorithm | Public Key Format | Signature Format |
|---|---|---|---|
| Device | EC P-256, ECDSA-SHA256 | X.509 SPKI, standard Base64 | ASN.1 DER, standard Base64 |
| Integration | Ed25519 | Raw 32 bytes, Base64URL no padding | Raw 64 bytes, Base64URL no padding |

- Android KeyStore: EC P-256 generation, StrongBox (API 28+), `UNLOCKED_DEVICE_REQUIRED`, private key never exported — excerpt from `EzkeyCryptoModule.kt`
- Proof tokens: format (`Base64URL.Base64URL`), one-time vs permanent semantics

---

### Section 4 — Enrollment Workflow

#### 4.1 Sequence Diagram (Mermaid)

`CREATED → BOUND → VERIFIED` — with the two HTTP calls and what happens on the device side

#### 4.2 Phase 1 — Bind

`POST /api/v1/enrollments/bind` (Auth API, port 8080)

- **Request**: `enrollmentId` (long), `enrollmentProofToken` (Base64URL)
- **Response**: `integrationPublicKey` (Ed25519, raw 32 bytes Base64URL **no padding**), `integrationKeyAlgorithm`, `enrollmentName`, `enrollmentId`
- **What the device stores**: `enrollmentProofToken`, `integrationPublicKey`, `enrollmentId` — in secure storage
- **OpenAPI reference**: `http://localhost:8080/swagger-ui/index.html`

#### 4.3 Phase 2 — Verify

`POST /api/v1/enrollments/verify` (Auth API, port 8080)

- **Key generation**: EC P-256 via Android KeyStore (alias = enrollmentId)
- **Signed payload**: `enrollmentProofToken` as raw UTF-8 bytes (no canonicalization)
- **Request**: `enrollmentId`, `enrollmentProofToken`, `devicePublicKey` (X.509 SPKI Base64), `enrollmentProofTokenSigned` (ECDSA DER Base64)
- **Response**: `{ "active": true }`
- **What the device stores**: public key (KeyStore alias for signing), `enrollmentProofToken` in permanent secure storage

---

### Section 5 — Authentication Workflow

#### 5.1 Sequence Diagram (Mermaid)

Showing the full flow: Backend → Integration API (create attempt) → Mobile polls pending → Mobile responds → Backend receives result

#### 5.2 Polling — Pending

`POST /api/v1/auth-attempts/pending` (Auth API, port 8080)

- **`deviceProofToken`**: 32 random bytes, Base64URL — generated fresh each poll
- **`deviceProofTokenSigned`**: ECDSA-SHA256 over the raw bytes of `deviceProofToken`
- **HTTP 204**: no pending attempt — same `deviceProofToken` reusable for next poll
- **HTTP 200**: pending attempt found → key fields:
  - `authAttemptProofToken` — store for the respond step
  - `authAttemptProofTokenSignedByIntegration` — **mandatory to verify** (Ed25519)
  - `contextTitle`, `contextMessage` — display to the user
- **Integration signature verification** (mandatory): canonical payload = `{proofToken}|{challengeRequired}|{contextTitle}|{contextMessage}` (NFC on context fields only)

#### 5.3 Diagram — Canonical Payload & Signature Verification (Mermaid flowchart)

Step-by-step of the integration signature verification after receiving the pending response

#### 5.4 Respond

`POST /api/v1/auth-attempts/respond` (Auth API, port 8080)

- **Device payload**: `{authAttemptProofToken}|{accepted}` → UTF-8 → ECDSA-SHA256 → DER → Base64
- **Request**: `authAttemptId`, `authAttemptAccepted` (bool), `authAttemptProofTokenSignedByDevice`, `authAttemptChallengeResponse` (if required)
- **Response**: `result` (APPROVED/DENIED/FAILED/EXPIRED), `authAttemptProofTokenSignedByIntegration`
- **Mandatory response signature verification**: payload = `{proofToken}|{authAttemptId}|{result}|{message}` (Ed25519)
- **Error behavior**: a single signature failure → attempt INVALID, no retry possible

---

### Section 6 — Security Properties Summary

Synthetic table of cryptographic guarantees:

| Property | Mechanism |
|---|---|
| Anti-replay | One-time proof tokens |
| Anti-enumeration | `enrollmentProofToken` in body (never in URL) |
| End-to-end integrity | Signature chain: integration signs pending, device signs respond, integration signs result |
| Error containment | First signature failure → attempt INVALID |
| Private key protection | Android KeyStore — key material never exported to JS/app layer |
| Rate limiting | bind 5/min, pending 10/min, respond 1/5min per attempt |

---

### Section 7 — Local Validation Guide

#### 7.1 Starting the Stack

- `./ezkey-tests/clean-start.sh` from the repository root
- Services in use: Auth API `localhost:8080`, Crypto API `localhost:9090`
- OpenAPI Swagger UI: `http://localhost:8080/swagger-ui/index.html`

#### 7.2 Using the Crypto API for Testing (`localhost:9090`)

- `GET /api/v1/crypto/keypair` → generate EC P-256 to simulate a device
- `GET /api/v1/crypto/prooftoken` → generate a test token
- `POST /api/v1/crypto/sign` → sign a canonical payload
- `POST /api/v1/crypto/validate` → verify a signature
- Swagger: `http://localhost:9090/swagger-ui/index.html`

#### 7.3 Step-by-Step Manual Validation

Step-by-step sequence mapped to existing Postman collections:
1. Admin → create enrollment (Admin API) → get `enrollmentId` + `enrollmentProofToken`
2. Crypto API → `GET /keypair` → EC P-256 key pair
3. Auth API → `POST /enrollments/bind` → get `integrationPublicKey`
4. Crypto API → `POST /sign` on `enrollmentProofToken` → `enrollmentProofTokenSigned`
5. Auth API → `POST /enrollments/verify` → `{ active: true }`
6. Integration API → `POST /auth-attempts` (via curl example) → create an attempt
7. Auth API → `POST /auth-attempts/pending` → retrieve + verify integration signature
8. Auth API → `POST /auth-attempts/respond` → send response + verify return signature

#### 7.4 Reference Collections

- `postman/collections/v2.1/EZ Key Enrollments auth.postman_collection.json`
- `postman/collections/v2.1/EZ Key Auth Attempts auth.postman_collection.json`
- `postman/collections/v2.1/EZ Key crypto.postman_collection.json`

---

### Section 8 — Implementation Checklist

Critical points to avoid implementation errors:
- NFC normalization on `contextTitle`/`contextMessage` only (not on proof tokens)
- DER signature (ECDSA) vs raw 64 bytes (Ed25519) — do not confuse
- `integrationPublicKey`: Base64URL **no padding**
- `devicePublicKey`: standard Base64 **with padding**, X.509 SPKI format
- `deviceProofToken` reusable on 204, new token required after 200
- Attempt INVALID after a single failure — no retry logic on the app side

---

## Source Files for Authoring

- `docs/CRYPTO.md` — Complete cryptographic specifications
- `docs/ENDPOINT.md` — API reference
- `docs/AUTH_ATTEMPT_SIGNATURE_PAYLOAD.md` — Canonical payloads
- `docs/CONTEXTUAL_AUTH.md` — Context fields
- `docs/ARCHITECTURE.md` — Overall architecture
- `PRD.md` — Product positioning
- `ezkey_mobile/android/app/src/main/java/com/ezkeymobile/crypto/EzkeyCryptoModule.kt` — Android KeyStore reference
- `ezkey_mobile/android/app/src/main/java/com/ezkeymobile/crypto/IntegrationKeyVerifier.kt` — Ed25519 verification reference
- `ezkey-demo-device/src/main/java/org/ezkey/demo/device/service/DeviceCryptoService.java` — JVM protocol reference

---

## Post-Authoring Verification

1. Verify all wire formats are consistent with `docs/CRYPTO.md`
2. Validate all ports match the default Docker configuration (`ezkey-tests/clean-start.sh`)
3. Manually test the step-by-step sequence (section 7.3) with a local clean-start
4. Verify that the positioning vs FIDO2/WebAuthn is affirmative and factual
