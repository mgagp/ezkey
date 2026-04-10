## 1.0.0

- Initial version.

## Unreleased

- Align enrollment bind/verify with `docs/ENROLLMENT_SIGNATURE_PAYLOAD.md`: canonical device verify
  payload (`{proofToken}|{enrollmentId}|{challenge}|{devicePublicKey}`), bind and verify-response
  integration signatures; extend `EnrollmentBindResponse` and `EnrollmentVerifyResponse` JSON models.
- Declare `executables` in `pubspec.yaml` for `dart run ezkey_dart:ezkey_auth_runner` (and the main
  CLI). Document shortcuts in `README.md`.
