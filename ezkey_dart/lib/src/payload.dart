import 'package:unorm_dart/unorm_dart.dart' as unorm;

/// Canonical pipe-separated payloads signed by the device or the integration.
///
/// Byte-for-byte equal to `EnrollmentSignaturePayload` / `AuthAttemptSignaturePayload` in
/// `ezkey-core` (see `docs/ENROLLMENT_SIGNATURE_PAYLOAD.md` and
/// `docs/AUTH_ATTEMPT_SIGNATURE_PAYLOAD.md`). Rules: separator `|`, UTF-8, Unicode NFC on
/// user-facing text only, `null` text becomes `""`, booleans are lowercase `true` / `false`.

/// Purpose literal for enrolled instance-info domain separation.
const String instanceInfoPurpose = 'INSTANCE_INFO';

/// Outcome literal signed in the verify response when enrollment succeeds.
const String enrollmentVerifiedOutcome = 'VERIFIED';

/// Bind response payload (integration signs, Ed25519). Twelve segments:
/// `proofToken|enrollmentId|integrationPublicKey|algorithm|integrationName|integrationDescription|
/// enrollmentName|tenantId|tenantName|tenantDescription|isSystemIntegration|adminType`.
String buildEnrollmentBindPayload({
  required String enrollmentProofToken,
  required int enrollmentId,
  required String integrationPublicKey,
  required String integrationKeyAlgorithm,
  String? integrationName,
  String? integrationDescription,
  String? enrollmentName,
  int? tenantId,
  String? tenantName,
  String? tenantDescription,
  bool? isSystemIntegration,
  String? adminType,
}) {
  return [
    enrollmentProofToken,
    '$enrollmentId',
    integrationPublicKey,
    integrationKeyAlgorithm,
    _nfc(integrationName),
    _nfc(integrationDescription),
    _nfc(enrollmentName),
    tenantId != null ? '$tenantId' : '',
    _nfc(tenantName),
    _nfc(tenantDescription),
    isSystemIntegration == true ? 'true' : 'false',
    adminType ?? '',
  ].join('|');
}

/// Verify request payload (device signs, ECDSA-SHA256):
/// `proofToken|enrollmentId|challengeResponse|devicePublicKey`.
String buildEnrollmentVerifyDevicePayload(
  String enrollmentProofToken,
  int enrollmentId,
  int challengeResponse,
  String devicePublicKey,
) {
  return '$enrollmentProofToken|$enrollmentId|$challengeResponse|$devicePublicKey';
}

/// Verify response payload (integration signs): `proofToken|enrollmentId|outcome|message`.
String buildEnrollmentVerifyResultPayload(
  String enrollmentProofToken,
  int enrollmentId,
  String outcome,
  String? message,
) {
  return '$enrollmentProofToken|$enrollmentId|$outcome|${_nfc(message)}';
}

/// Enrolled instance-info payload (integration signs):
/// `proofToken|enrollmentId|INSTANCE_INFO|authApiPublicBaseUrl|instanceName|instanceDescription|aboutUrl`.
String buildEnrollmentInstanceInfoPayload({
  required String enrollmentProofToken,
  required int enrollmentId,
  String? authApiPublicBaseUrl,
  String? instanceName,
  String? instanceDescription,
  String? aboutUrl,
}) {
  return [
    enrollmentProofToken,
    '$enrollmentId',
    instanceInfoPurpose,
    authApiPublicBaseUrl ?? '',
    _nfc(instanceName),
    _nfc(instanceDescription),
    _nfc(aboutUrl),
  ].join('|');
}

/// Pending response payload (integration signs):
/// `authAttemptProofToken|challengeRequired|contextTitle|contextMessage`.
String buildPendingPayload(
  String proofToken,
  bool challengeRequired,
  String? contextTitle,
  String? contextMessage,
) {
  return '$proofToken|$challengeRequired|${_nfc(contextTitle)}|${_nfc(contextMessage)}';
}

/// Respond request payload (device signs): `authAttemptProofToken|accepted`.
String buildRespondPayload(String proofToken, bool accepted) {
  return '$proofToken|$accepted';
}

/// Respond HTTP result payload (integration signs):
/// `authAttemptProofToken|authAttemptId|result|message`.
String buildRespondResultPayload(
  String proofToken,
  String authAttemptId,
  String result,
  String? message,
) {
  return '$proofToken|$authAttemptId|$result|${_nfc(message)}';
}

/// Unicode NFC normalization as applied by the server to user-facing text segments.
String normalizeNfc(String value) => unorm.nfc(value);

String _nfc(String? value) => value == null ? '' : unorm.nfc(value);
