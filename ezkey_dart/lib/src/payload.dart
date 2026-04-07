import 'package:unorm_dart/unorm_dart.dart' as unorm;

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
