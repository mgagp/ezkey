---
name: ""
overview: ""
todos: []
isProject: false
---

# Plan: Enrollment proof token encryption integrity

**Status:** Ready for implementation (updated per user decisions)

**User decisions (summary):**

- **CryptoApiClient:** Complete the client so it fully aligns with what the Crypto API backend offers (not just add one method).
- **DatabaseHelper (Option B):** Implement the DB verification helper as a mandatory tool for opportunistic, realistic tests and better quality.
- **Regression test:** Add an **elective test** that ensures data integrity in the BD regarding encryption, to prevent regressions of the same kind.

---

## Problem summary

After running Clean Start and the full functional test suite (elective tests + All Tests profile), some rows in `ezkey_enrollment` have:

- **Enrollment name** "Test Device" (expected for test-created enrollments).
- **Proof token** stored **without** the `ENC:` prefix (plaintext), whereas production and normal API-created enrollments use Google Tink (format `ENC:keyID:Base64(ciphertext)`).

Examples observed: Enrollment IDs 124–125, 156–157. This breaks the invariant that sensitive columns are encrypted at rest.

---

## Root cause (identified)

### 1. Raw SQL INSERTs in functional tests (primary cause)

**[EnrollmentUniquenessIntegrationTest](ezkey-tests/src/test/java/org/ezkey/tests/security/enrollment/EnrollmentUniquenessIntegrationTest.java)** creates a second enrollment row via **direct SQL** to exercise database-level uniqueness constraints. There are **three** such INSERTs (around lines 240–252, 364–376, 464–476). Each uses:

- `enrollment_proof_token` = `**'test-token-2'`** (plaintext).
- **No** `enrollment_proof_token_hash`.
- Other fields copied from the first (API-created) enrollment.

These rows bypass JPA and **[EncryptionEntityListener](ezkey-core/src/main/java/org/ezkey/security/EncryptionEntityListener.java)**, so the proof token is never encrypted.

### 2. Encryption unavailable at persist time (secondary)

If `EncryptionService` is not available when an enrollment is persisted via JPA, the listener stores plaintext. In Docker this is less likely; verify bootstrap/startup order if needed.

---

## Implementation plan

### A. CryptoApiClient — complete alignment with Crypto API

**Goal:** Make the test client **complete** relative to what the Crypto API backend offers, not only add a single method for this fix.

- **Backend** exposes POST `/api/v1/crypto/encrypt` and returns **EncryptResponseDto** with: `encryptedValue`, `encryptionSuccessful`, `keyId`, `encryptedFormat`, `errorMessage`, `encryptionAvailable`.
- **Current client** only has `encryptAndGetKeyId(plaintext)` and returns only the key ID.

**Actions:**

- Add a method that returns the **full encrypted value string** for use in tests (e.g. `String encrypt(String plaintext)` returning `encryptedValue`, or `null` if encryption failed/unavailable).
- Optionally expose other useful response fields (e.g. a small DTO or a method returning `encryptionSuccessful` + `encryptedValue`) so future tests can rely on the client instead of calling the API ad hoc. The minimum required for this plan is a method that returns the encrypted string.
- Document that the client is intended to mirror the Crypto API encrypt/decrypt (and existing) endpoints for test use.

**Files:** [CryptoApiClient](ezkey-tests/src/test/java/org/ezkey/tests/util/CryptoApiClient.java)

---

### B. DatabaseHelper — encryption integrity query (mandatory)

**Goal:** Provide a reusable tool for opportunistic, realistic verification of encryption at rest in the BD.

**Actions:**

- Add a method that returns **enrollment IDs** where the proof token is **not** stored in encrypted form (e.g. `enrollment_proof_token NOT LIKE 'ENC:%'`).
  - Example signature: `List<Integer> getEnrollmentIdsWithPlaintextProofToken()` (or similar).
  - Query: `SELECT enrollment_id FROM ezkey_enrollment WHERE enrollment_proof_token NOT LIKE 'ENC:%'`.
- Optionally add a similar method for **auth_attempt** (e.g. `auth_attempt_proof_token` and/or `device_proof_token` NOT LIKE 'ENC:%') for future use and consistency. If scope is enrollment-only for this plan, document that auth_attempt can be added later.

**Files:** [DatabaseHelper](ezkey-tests/src/test/java/org/ezkey/tests/util/DatabaseHelper.java)

---

### C. EnrollmentUniquenessIntegrationTest — use encrypted tokens in raw INSERTs

- For each of the **three** raw `INSERT INTO ezkey_enrollment` that set `enrollment_proof_token`:
  1. Call Crypto API (via the **completed** client) to encrypt the proof token (e.g. `"test-token-2"`); use the returned encrypted value.
  2. Compute **enrollment_proof_token_hash** = SHA-256 hex of the **plaintext** token (same as entity behavior). Use standard Java `MessageDigest.getInstance("SHA-256")` in the test (ezkey-tests does not depend on ezkey-core).
  3. In the INSERT: set `enrollment_proof_token` to the encrypted value (with SQL escaping for single quotes); add `enrollment_proof_token_hash` to the column list and bind the computed hash.
- If encryption is unavailable, assume it is required and fail (or skip) with a clear message so the suite does not leave plaintext in the BD.

**Files:** [EnrollmentUniquenessIntegrationTest](ezkey-tests/src/test/java/org/ezkey/tests/security/enrollment/EnrollmentUniquenessIntegrationTest.java)

---

### D. Elective test — encryption data integrity (mandatory)

**Goal:** An **elective test** that ensures data integrity in the BD regarding encryption, to catch regressions (e.g. new plaintext INSERTs or listener bypass).

**Actions:**

- Create a **new test class** (e.g. `EncryptionIntegrityElectiveTest`) in a suitable package (e.g. `org.ezkey.tests.security.crypto` or `org.ezkey.tests.security.integrity`).
- **Tags:** `@Tag(TestTags.ELECTIVE)`, `@Tag(TestTags.ENCRYPTION)`, `@Tag(TestTags.DATABASE)` (and optionally `TestTags.SECURITY`).
- **Behavior:**
  - Use **DatabaseHelper** (the new method) to get enrollment IDs with plaintext proof token.
  - **Assert** that the list is empty; if not, fail with a clear message listing the IDs (e.g. "Enrollments with unencrypted proof token: [ids]").
  - Optionally extend later to auth_attempt proof tokens if DatabaseHelper is extended.
- **Invocation:** Run with the elective profile: `mvn test -pl ezkey-tests -P elective-tests` or `./scripts/run-elective-tests.sh`.
- **Grace on empty DB:** If the query returns no rows, the test passes (integrity satisfied). No need to skip; an empty result is the desired state.
- Follow the style of existing elective tests (e.g. [AuditIntegrityElectiveTest](ezkey-tests/src/test/java/org/ezkey/tests/security/audit/AuditIntegrityElectiveTest.java)): extend `AbstractSecurityTest`, use `DatabaseHelper` in `@BeforeEach` or per test, document invocation and purpose in the class Javadoc.
- Update **run-elective-tests.sh** (or its comment) to mention the new test (e.g. "EncryptionIntegrityElectiveTest (enrollment proof token encryption at rest)").

**Files:**

- New: e.g. `ezkey-tests/src/test/java/org/ezkey/tests/security/crypto/EncryptionIntegrityElectiveTest.java` (or `security/integrity/`).
- [run-elective-tests.sh](ezkey-tests/scripts/run-elective-tests.sh) — update comment listing elective tests.

---

### E. Bootstrap / startup (verification only)

- Confirm that when the global admin enrollment (and any other early enrollments) is created, `EncryptionEntityListener` has access to `EncryptionService` and Tink is initialized. No code change required if already correct; document or fix startup order if evidence shows bootstrap enrollments with plaintext.

---

## Result

- **CryptoApiClient** is complete relative to the Crypto API encrypt (and useful for tests).
- **DatabaseHelper** offers a reusable way to detect enrollments (and optionally later auth_attempts) with plaintext proof tokens.
- **All** enrollments created during the functional suite (including raw SQL in EnrollmentUniquenessIntegrationTest) store `enrollment_proof_token` in `ENC:...` format with a valid hash.
- **Elective test** runs on demand and fails if any enrollment has a non-ENC proof token, preserving data integrity and preventing regressions.

