import '../ezkey_crypto.dart';
import '../ec_p256.dart';
import '../payload.dart';
import 'auth_api_client.dart';
import 'auth_api_models.dart';
import 'enrollment_code.dart';

class EzkeyAuthSession {
  EzkeyAuthSession._({
    required this.api,
    required this.authApiBaseUri,
    required this.enrollmentId,
    required this.enrollmentProofToken,
    required this.integrationPublicKey,
    required this.integrationName,
    required this.integrationDescription,
    required this.enrollmentName,
    required this.tenantName,
    required this.tenantDescription,
    required this.deviceKeyPair,
    required this.devicePublicKey,
  });

  final EzkeyAuthApi api;
  final Uri authApiBaseUri;
  final String enrollmentId;
  final String enrollmentProofToken;
  final String integrationPublicKey;
  final String? integrationName;
  final String? integrationDescription;
  final String? enrollmentName;
  final String? tenantName;
  final String? tenantDescription;
  final EcP256KeyPair deviceKeyPair;
  final String devicePublicKey;

  static Future<EzkeyAuthSession> enroll({
    required EzkeyAuthApi api,
    required Uri authApiBaseUri,
    required EnrollmentCode code,
    required String bindingChallenge,
    String? language,
  }) async {
    if (!RegExp(r'^\d{6}$').hasMatch(bindingChallenge.trim())) {
      throw const FormatException('Enrollment binding challenge must be exactly 6 digits');
    }

    final bindResponse = await api.bind(
      EnrollmentBindRequest(
        enrollmentId: code.enrollmentId,
        enrollmentProofToken: code.enrollmentProofToken,
        language: language ?? code.language,
      ),
    );

    final algorithm = bindResponse.integrationKeyAlgorithm?.trim().toLowerCase();
    if (algorithm != null && algorithm.isNotEmpty && algorithm != 'ed25519') {
      throw UnsupportedError(
        'Unsupported integrationKeyAlgorithm "$algorithm". The Dart runner currently expects ed25519.',
      );
    }

    final bindPayload = buildEnrollmentBindPayload(
      enrollmentProofToken: bindResponse.enrollmentProofToken,
      enrollmentId: int.parse(bindResponse.enrollmentId),
      integrationPublicKey: bindResponse.integrationPublicKey,
      integrationKeyAlgorithm: bindResponse.integrationKeyAlgorithm?.trim() ?? '',
      integrationName: bindResponse.integrationName,
      integrationDescription: bindResponse.integrationDescription,
      enrollmentName: bindResponse.enrollmentName,
      tenantId: bindResponse.tenantId,
      tenantName: bindResponse.tenantName,
      tenantDescription: bindResponse.tenantDescription,
    );
    final bindSignatureOk = await EzKeyCrypto.verifyIntegrationSignature(
      bindPayload,
      bindResponse.enrollmentBindPayloadSignedByIntegration,
      bindResponse.integrationPublicKey,
    );
    if (!bindSignatureOk) {
      throw StateError(
        'Bind response failed integration signature verification. '
        'This indicates a contract or integrity problem.',
      );
    }

    final deviceKeyPair = EzKeyCrypto.generateDeviceKeyPair();
    final devicePublicKey = EzKeyCrypto.exportDevicePublicKey(deviceKeyPair);
    final challengeInt = int.parse(bindingChallenge.trim());
    final verifyDevicePayload = buildEnrollmentVerifyDevicePayload(
      bindResponse.enrollmentProofToken,
      int.parse(bindResponse.enrollmentId),
      challengeInt,
      devicePublicKey,
    );
    final proofTokenSigned = EzKeyCrypto.signWithDeviceKey(
      deviceKeyPair,
      verifyDevicePayload,
    );

    final verifyResponse = await api.verify(
      EnrollmentVerifyRequest(
        enrollmentId: bindResponse.enrollmentId,
        challengeResponse: bindingChallenge.trim(),
        devicePublicKey: devicePublicKey,
        enrollmentProofTokenSigned: proofTokenSigned,
      ),
    );

    if (!verifyResponse.active) {
      throw StateError('Enrollment verify completed but the enrollment is not active');
    }

    final enrollmentIdInt = int.parse(bindResponse.enrollmentId);
    final verifyResultPayload = buildEnrollmentVerifyResultPayload(
      bindResponse.enrollmentProofToken,
      enrollmentIdInt,
      'VERIFIED',
      verifyResponse.enrollmentVerifyMessage,
    );
    final verifyResultOk = await EzKeyCrypto.verifyIntegrationSignature(
      verifyResultPayload,
      verifyResponse.enrollmentVerifyPayloadSignedByIntegration,
      bindResponse.integrationPublicKey,
    );
    if (!verifyResultOk) {
      throw StateError(
        'Verify response failed integration signature verification. '
        'This indicates a contract or integrity problem.',
      );
    }

    return EzkeyAuthSession._(
      api: api,
      authApiBaseUri: authApiBaseUri,
      enrollmentId: bindResponse.enrollmentId,
      enrollmentProofToken: bindResponse.enrollmentProofToken,
      integrationPublicKey: bindResponse.integrationPublicKey,
      integrationName: bindResponse.integrationName,
      integrationDescription: bindResponse.integrationDescription,
      enrollmentName: bindResponse.enrollmentName,
      tenantName: bindResponse.tenantName,
      tenantDescription: bindResponse.tenantDescription,
      deviceKeyPair: deviceKeyPair,
      devicePublicKey: devicePublicKey,
    );
  }

  Future<VerifiedPendingAuthAttempt?> checkPending() async {
    final deviceProofToken = EzKeyCrypto.generateDeviceProofToken();
    final deviceProofTokenSigned = EzKeyCrypto.signWithDeviceKey(
      deviceKeyPair,
      deviceProofToken,
    );
    final response = await api.pending(
      PendingAuthRequest(
        enrollmentId: enrollmentId,
        enrollmentProofToken: enrollmentProofToken,
        deviceProofToken: deviceProofToken,
        deviceProofTokenSigned: deviceProofTokenSigned,
      ),
    );
    if (response == null) {
      return null;
    }

    final pendingPayload = buildPendingPayload(
      response.authAttemptProofToken,
      response.authAttemptChallengeRequired,
      response.contextTitle,
      response.contextMessage,
    );
    final signatureValid = await EzKeyCrypto.verifyIntegrationSignature(
      pendingPayload,
      response.authAttemptProofTokenSignedByIntegration,
      integrationPublicKey,
    );
    if (!signatureValid) {
      throw StateError(
        'Pending auth response failed integration signature verification. '
        'This indicates a contract or integrity problem.',
      );
    }

    return VerifiedPendingAuthAttempt(response: response);
  }

  Future<RespondAuthOutcome> respond({
    required VerifiedPendingAuthAttempt attempt,
    required bool accepted,
    String? challengeResponse,
  }) async {
    final normalizedChallenge = _normalizeChallenge(challengeResponse);
    final respondPayload = buildRespondPayload(
      attempt.authAttemptProofToken,
      accepted,
    );
    final signature = EzKeyCrypto.signWithDeviceKey(deviceKeyPair, respondPayload);
    final response = await api.respond(
      RespondAuthRequest(
        authAttemptId: attempt.authAttemptId,
        authAttemptAccepted: accepted,
        authAttemptProofTokenSignedByDevice: signature,
        authAttemptChallengeResponse: normalizedChallenge,
      ),
    );

    final resultSignature = response.authAttemptProofTokenResultSignedByIntegration;
    if (resultSignature == null || resultSignature.trim().isEmpty) {
      return RespondAuthOutcome(
        response: response,
        integrationSignatureVerified: false,
        warning:
            'Auth API did not return an integration result signature. The server response '
            'cannot be cryptographically verified end-to-end.',
      );
    }

    final resultPayload = buildRespondResultPayload(
      attempt.authAttemptProofToken,
      response.authAttemptId,
      response.authAttemptResult,
      response.authAttemptMessage,
    );
    final signatureValid = await EzKeyCrypto.verifyIntegrationSignature(
      resultPayload,
      resultSignature,
      integrationPublicKey,
    );
    if (!signatureValid) {
      throw StateError(
        'Respond result failed integration signature verification. '
        'This indicates a contract or integrity problem.',
      );
    }

    return RespondAuthOutcome(
      response: response,
      integrationSignatureVerified: true,
    );
  }
}

class VerifiedPendingAuthAttempt {
  VerifiedPendingAuthAttempt({required this.response});

  final PendingAuthResponse response;

  String get authAttemptId => response.authAttemptId;

  String get authAttemptProofToken => response.authAttemptProofToken;

  bool get challengeRequired => response.authAttemptChallengeRequired;

  String? get contextTitle => response.contextTitle;

  String? get contextMessage => response.contextMessage;
}

class RespondAuthOutcome {
  RespondAuthOutcome({
    required this.response,
    required this.integrationSignatureVerified,
    this.warning,
  });

  final RespondAuthResponse response;
  final bool integrationSignatureVerified;
  final String? warning;
}

String? _normalizeChallenge(String? value) {
  if (value == null) {
    return null;
  }
  final normalized = value.trim();
  if (normalized.isEmpty) {
    return null;
  }
  if (!RegExp(r'^\d+$').hasMatch(normalized)) {
    throw const FormatException('Challenge response must be numeric');
  }
  return normalized;
}