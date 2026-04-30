---
name: Signed Respond response analysis
overview: Deep analysis of signing the Auth API Respond response so the mobile can verify it with the integration public key, completing the cryptographic chain (Pending → Respond). Covers payload design, naming consistency with existing Auth API patterns, canonical form (NFC/UTF-8), and edge cases.
todos: []
isProject: false
---

# Signed Respond response — analysis and design

## 1. Goal and threat model

**Goal:** Close the last gap in the chain: the Respond **response** is today plain JSON. A MITM can flip `result` (e.g. backend returns DENIED, attacker changes to APPROVED) so the user sees a false outcome. Signing the response with the **integration** private key and having the mobile verify with the integration public key (same as Pending) makes the outcome non-repudiable and tamper-proof.

**Threat addressed:** Tampering in transit on the Auth API → mobile leg. Impact is modest for the “portal + mobile” flow (user sees portal outcome anyway) but completes the EZ Key proof-token story and keeps UX consistent (mobile only shows verified data).

**Scope:** Response of `POST /api/v1/auth-attempts/respond` only. No change to request or to other endpoints.

**Client update requirement (mandatory):** Because the response fields are renamed (`result` → `authAttemptResult`, `message` → `authAttemptMessage`) and new fields are added (`authAttemptId`, `authAttemptProofTokenResultSignedByIntegration`), existing clients that read the Respond response will break if they are not updated. Updating the **Demo Device** and the **mobile app** (ezkey_mobile) is therefore **not optional**—it is an integral part of this plan. All three clients must be revised in lockstep with the backend and Auth API DTO so they remain functional. (If we had kept `result` and `message` and only added the signature, client updates could have been optional; the renaming makes them mandatory.)

---

## 2. Current state

- **Respond response (API):** `[AuthAttemptRespondResponseDto](ezkey-auth-api/src/main/java/org/ezkey/authattempt/dto/AuthAttemptRespondResponseDto.java)`: `result` (String, e.g. `"APPROVED"`), `message` (String). No `authAttemptId`, no signature.
- **Respond response (domain):** `[AuthAttemptRespondResponse](ezkey-core/src/main/java/org/ezkey/authattempt/domain/AuthAttemptRespondResponse.java)`: `result`, `message`, `authAttemptId`, `createdAt`. Latter two are not exposed in the DTO today.
- **Pending response (naming):** All attempt-related fields are prefixed: `authAttemptId`, `authAttemptProofToken`, `authAttemptProofTokenSignedByIntegration`, `authAttemptChallengeRequired`. Context fields are not prefixed: `contextTitle`, `contextMessage`.
- **Respond request (naming):** All prefixed: `authAttemptId`, `authAttemptAccepted`, `authAttemptProofTokenSignedByDevice`, `authAttemptChallengeResponse`.

So the **only** inconsistency is the Respond response: `result` and `message` are not prefixed and `authAttemptId` is missing from the API (even though present in the domain).

---

## 3. Naming proposal (align with existing patterns)

To align with Pending and Respond **request** and with the concept “authentication attempt result signed by integration”:


| Current (response) | Proposed (response)                              | Rationale                                                                                                                                                             |
| ------------------ | ------------------------------------------------ | --------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| (missing)          | `authAttemptId`                                  | Required in signed payload so the result is bound to the attempt; already in domain, just expose in DTO.                                                              |
| `result`           | `authAttemptResult`                              | Same pattern as `authAttemptAccepted`, `authAttemptChallengeRequired`: outcome of the attempt is prefixed.                                                            |
| `message`          | `authAttemptMessage`                             | Same: message describes the attempt outcome.                                                                                                                          |
| (new)              | `authAttemptProofTokenResultSignedByIntegration` | Signature over (proof token + result + message). Proof token is part of the signed payload (continuity; see section 3b). “this outcome is signed by the integration.” |


**Resulting Respond response shape:**

```json
{
  "authAttemptId": 123,
  "authAttemptResult": "APPROVED",
  "authAttemptMessage": "Auth attempt completed",
  "authAttemptProofTokenResultSignedByIntegration": "<base64-signature>"
}
```

**Alternative (minimal change):** Keep `result` and `message` as-is, add only `authAttemptId` and `authAttemptProofTokenResultSignedByIntegration`. The signed payload would still use the same logical values; only the JSON keys would stay shorter. Recommendation: **adopt the prefixed names** so the whole Auth API uses one rule: “auth-attempt-related fields carry the `authAttempt`* prefix.”

---

### 3b. Why include the proof token in the payload (design rationale)

In EZ Key's protocol, the **authentication attempt proof token** is the value that ensures **cryptographic continuity**:

- **Pending:** The integration issues the proof token and signs it (with context). The only way the device can learn that token is to have received the Pending response.
- **Respond request:** The device signs that **same** proof token (with accepted). The only way to produce a valid Respond request is to have obtained the token from Pending.
- **Respond response (this design):** The integration signs **that same proof token** again, together with the result and message. So the response is bound to the **exact attempt** identified by the proof token.

Including the proof token in the Respond response signed payload:

1. **Continuity:** The same secret (proof token) appears in all three steps. The chain is one continuous story: Pending to Respond request to Respond response, all tied by the same token.
2. **Entropy:** The payload contains high-entropy material (the random proof token when the attempt exists), which is good practice for signed payloads.
3. **Naming accuracy:** The signature field is named `authAttemptProofTokenResultSignedByIntegration` because the signed payload **actually contains** the proof token (plus result and message). The term Proof Token denotes the same concept everywhere.
4. **Coherence with EZ Key DNA:** The protocol is proof token-based; the response is the final link in that chain. Including the proof token in the response signature keeps the design consistent.

---

## 4. Signed payload design

**Canonical payload (integration signs):**

```text
{authAttemptProofToken}|{authAttemptId}|{result}|{message}
```

- **Separator:** Single `|`, no spaces, same as Pending/Respond request in [proof token plan](c:\Users\marcg.cursor\plans\proof_token_payload_binding_6384e23a.plan.md).
- **Nulls:** Empty string `""` (no literal `"null"`).
- **authAttemptProofToken:** The same proof token issued in Pending and signed by the device in the Respond request. When the backend has no attempt, use `""` so the payload format is unchanged; client uses `""` when it never had a token for this id. **authAttemptId:** String representation of the integer (e.g. `"123"`). When the backend has no attempt (e.g. “record not found”), use the id from the request so the response is still verifiable: payload = `request.getAuthAttemptId()|FAILED|message`.
- **result:** Enum name, ASCII: `APPROVED`, `DENIED`, `FAILED`, `EXPIRED`.
- **message:** User-facing text; **must be normalized to Unicode NFC** before being placed in the payload (same rule as `contextTitle` / `contextMessage` in the Proof Token Payload Binding). Then UTF-8 for the whole payload.

**Where to apply NFC:** Only to `authAttemptMessage`. `authAttemptId` and `result` are ASCII. Spec should state: “Apply NFC to any user-facing text that may be localized (e.g. authAttemptMessage); encode payload as UTF-8.”

**Backend:** Build this string (NFC on message, null → `""`), sign with `signatureService.generateSignature(payload, integrationPrivateKey)`, attach the signature as `authAttemptProofTokenResultSignedByIntegration`.

**Mobile / demo device:** Rebuild the same string from the response body (using the proof token received in Pending; use `""` when response is FAILED and client never had a token for this id), verify with `integrationPublicKey`. If verification fails, do not trust the result (show a generic “Response could not be verified” or equivalent).

---

## 5. Coherence with Proof Token Payload Binding

- **Same canonical rules:** Separator `|`, null→`""`, NFC for user-facing text, UTF-8 for the payload. No canonical JSON needed.
- **Same signer for “from backend to device”:** Integration signs both Pending (extended payload) and Respond response. Device verifies with integration public key (already present from bind).
- **Continuity:** Pending binds context + proof token; Respond request binds device decision; **Respond response** binds backend outcome. End-to-end: every security-relevant datum is covered by a signature.

---

## 6. Edge cases and blind spots

1. **FAILED with no attempt:** When `validateAndGetAttempt` throws, the backend has no proof token. Use `""` for the proof token in the payload so the format stays `proofToken|authAttemptId|result|message`. When (e.g. “Auth attempt record not found”), domain response has `authAttemptId == null`. For a self-contained signed payload, the backend should set `response.setAuthAttemptId(request.getAuthAttemptId())` in the catch path so the response always carries the attempt id the client sent; payload becomes `request.getAuthAttemptId()|FAILED|message`. Client can then verify without special-casing “no id.”
2. **Message source:** Today messages are backend-defined (default or exception message). If later they are localized (i18n), NFC applies to the string that goes into the payload (same as context in Pending).
3. **Replay:** The payload includes `authAttemptId` and `result`/`message`. Replaying an old response would match a past attempt; the mobile typically does not reuse the same attempt id for a new flow, so replay is limited. No need to add a timestamp to the payload for this feature.
4. **Order of fields:** Fix order as `authAttemptProofToken|authAttemptId|result|message` in a short spec (e.g. in `docs/`) so all stacks (Java, Kotlin, JS) build the same string.
5. **Backward compatibility:** Per plan “Critical observations,” we are in full development; no compatibility contract. Renaming `result` → `authAttemptResult` and `message` → `authAttemptMessage` and adding two fields is a breaking change for the Auth API response; Demo Device and the mobile app (ezkey_mobile) must be updated as part of this plan—their update is mandatory for the feature to work, not optional.

---

## 7. What the mobile does with the response (reminder)

Contribution remains modest: after verifying the signature, the app shows a final confirmation that the backend accepted the response (e.g. “Approved” / “Denied” / error). The primary outcome is still the portal. The signed response ensures that what the mobile shows is what the Auth API actually returned.

---

## 8. Implementation outline (for later)

**Note:** Demo Device and the mobile app (ezkey_mobile) are **required** to be updated with this plan; see §1 "Client update requirement (mandatory)."

- **Backend (ezkey-core):** In `AuthAttemptRespondService.buildResponse` (and in the catch path that builds FAILED), after building the domain response, build the canonical payload; call `signatureService.generateSignature(payload, integrationPrivateKey)` (integration from enrollment). Attach signature to response. Need to pass enrollment/integration key into the response builder (or have the service set the signature on the response before return). Domain object may need a new field `authAttemptProofTokenResultSignedByIntegration`; alternatively the signature is added at the DTO layer if the controller has access to integration key (cleaner: keep signing in core, domain response carries the signature).
- **Auth API DTO:** Extend `AuthAttemptRespondResponseDto` with `authAttemptId`, rename `result` → `authAttemptResult`, `message` → `authAttemptMessage`, add `authAttemptProofTokenResultSignedByIntegration`. Mapper and domain updated accordingly.
- **Mobile (ezkey_mobile):** **Mandatory.** Update response handling to use `authAttemptResult`, `authAttemptMessage`, `authAttemptId`, and `authAttemptProofTokenResultSignedByIntegration` (no longer `result` / `message`). After receiving the respond response, build payload from `authAttemptProofToken` (from Pending), `authAttemptId`, `authAttemptResult`, `authAttemptMessage` (NFC on message), verify with existing native `verify(data, signatureBase64, integrationPublicKey)`. If verification fails, treat as invalid and do not trust the result.
- **Demo device:** **Mandatory.** Same as mobile: update to new response field names and add signature verification; build payload, verify with integration public key, then show result.
- **Docs:** Update [ENDPOINT.md](docs/ENDPOINT.md) and add a short canonical-payload spec (or extend the one for Proof Token) to include Respond response payload format.

---

## 9. Summary


| Topic                      | Recommendation                                                                                                                        |
| -------------------------- | ------------------------------------------------------------------------------------------------------------------------------------- |
| **Naming**                 | Prefix response fields: `authAttemptId`, `authAttemptResult`, `authAttemptMessage`, `authAttemptProofTokenResultSignedByIntegration`. |
| **Payload**                | `authAttemptProofToken                                                                                                                |
| **Signer**                 | Integration (same as Pending). Mobile verifies with integration public key.                                                           |
| **FAILED without attempt** | Set `authAttemptId` from request in catch path so response is always verifiable.                                                      |
| **Spec**                   | Document payload order and NFC/UTF-8 in `docs/` and reference from all clients.                                                       |
| **Clients**                | Demo Device and ezkey_mobile must be updated in lockstep (mandatory; renaming breaks existing response handling).  |


This keeps the Auth API naming consistent (auth-attempt-related fields prefixed), reuses the same canonical and signing approach as the Proof Token Payload Binding, and completes the cryptographic chain from Pending to Respond without changing the protocol’s structure.
