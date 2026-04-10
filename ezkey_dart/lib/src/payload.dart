import 'package:unorm_dart/unorm_dart.dart' as unorm;

/// Canonical bind payload (integration signs). Matches `docs/ENROLLMENT_SIGNATURE_PAYLOAD.md`.
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
}) {
  final tenantIdStr = tenantId != null ? '$tenantId' : '';
  return [
    enrollmentProofToken,
    '$enrollmentId',
    integrationPublicKey,
    integrationKeyAlgorithm,
    _nfc(integrationName ?? ''),
    _nfc(integrationDescription ?? ''),
    _nfc(enrollmentName ?? ''),
    tenantIdStr,
    _nfc(tenantName ?? ''),
    _nfc(tenantDescription ?? ''),
  ].join('|');
}

/// Canonical verify request payload (device signs with ECDSA-SHA256 over UTF-8).
String buildEnrollmentVerifyDevicePayload(
  String enrollmentProofToken,
  int enrollmentId,
  int challengeResponse,
  String devicePublicKey,
) {
  return '$enrollmentProofToken|$enrollmentId|$challengeResponse|$devicePublicKey';
}

/// Canonical verify HTTP response payload (integration signs with Ed25519).
String buildEnrollmentVerifyResultPayload(
  String enrollmentProofToken,
  int enrollmentId,
  String outcome,
  String? message,
) {
  return '$enrollmentProofToken|$enrollmentId|$outcome|${_nfc(message ?? '')}';
}

String buildPendingPayload(
  String proofToken,
  bool challengeRequired,
  String? contextTitle,
  String? contextMessage,
) {
  return '$proofToken|$challengeRequired|${_nfc(contextTitle ?? '')}|${_nfc(contextMessage ?? '')}';
}

String buildRespondPayload(String proofToken, bool accepted) {
  return '$proofToken|$accepted';
}

String buildRespondResultPayload(
  String proofToken,
  String authAttemptId,
  String result,
  String? message,
) {
  return '$proofToken|$authAttemptId|$result|${_nfc(message ?? '')}';
}

String normalizeNfc(String value) => _nfc(value);

String _nfc(String value) => unorm.nfc(value);
