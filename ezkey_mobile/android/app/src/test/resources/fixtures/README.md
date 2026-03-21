# Crypto test fixtures

- **integration_public_key_base64.txt**: Base64-encoded X.509 SubjectPublicKeyInfo (integration public key). Populate by running from repo root:
  ```bash
  ./scripts/export-mobile-crypto-fixture.sh 3 --write
  ```
  (Enrollment ID 3 = Jean-Martin, golden fixture.)

- Optional: add `pending_payload.txt`, `pending_signature_base64.txt` for the known-good Pending triple (payload + signature + same key) to test end-to-end verification. Capture from a real Pending response for enrollment 3 via the Demo Device.
