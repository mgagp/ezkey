class EnrollmentBindRequest {
  EnrollmentBindRequest({
    required this.enrollmentId,
    required this.enrollmentProofToken,
  });

  final String enrollmentId;
  final String enrollmentProofToken;

  Map<String, Object?> toJson() {
    return <String, Object?>{
      'enrollmentId': int.parse(enrollmentId),
      'enrollmentProofToken': enrollmentProofToken,
    };
  }
}

class EnrollmentBindResponse {
  EnrollmentBindResponse({
    required this.enrollmentId,
    required this.enrollmentProofToken,
    required this.integrationPublicKey,
    required this.enrollmentBindPayloadSignedByIntegration,
    required this.authAttemptChallengeRequiredByPolicy,
    this.integrationKeyAlgorithm,
    this.integrationName,
    this.integrationDescription,
    this.enrollmentName,
    this.tenantId,
    this.tenantName,
    this.tenantDescription,
  });

  final String enrollmentId;
  final String enrollmentProofToken;
  final String integrationPublicKey;
  /// Ed25519 (Base64URL) over the canonical bind payload string.
  final String enrollmentBindPayloadSignedByIntegration;
  final bool authAttemptChallengeRequiredByPolicy;
  final String? integrationKeyAlgorithm;
  final String? integrationName;
  final String? integrationDescription;
  final String? enrollmentName;
  final int? tenantId;
  final String? tenantName;
  final String? tenantDescription;

  factory EnrollmentBindResponse.fromJson(Map<String, dynamic> json) {
    final challengeRequiredByPolicy = json['authAttemptChallengeRequiredByPolicy'];
    if (challengeRequiredByPolicy is! bool) {
      throw FormatException(
        'Missing or invalid authAttemptChallengeRequiredByPolicy in bind response',
      );
    }
    return EnrollmentBindResponse(
      enrollmentId: _requiredString(json, 'enrollmentId'),
      enrollmentProofToken: _requiredString(json, 'enrollmentProofToken'),
      integrationPublicKey: _requiredString(json, 'integrationPublicKey'),
      enrollmentBindPayloadSignedByIntegration: _requiredString(
        json,
        'enrollmentBindPayloadSignedByIntegration',
      ),
      authAttemptChallengeRequiredByPolicy: challengeRequiredByPolicy,
      integrationKeyAlgorithm: _optionalString(json, 'integrationKeyAlgorithm'),
      integrationName: _optionalString(json, 'integrationName'),
      integrationDescription: _optionalString(json, 'integrationDescription'),
      enrollmentName: _optionalString(json, 'enrollmentName'),
      tenantId: _optionalInt(json, 'tenantId'),
      tenantName: _optionalString(json, 'tenantName'),
      tenantDescription: _optionalString(json, 'tenantDescription'),
    );
  }
}

class EnrollmentVerifyRequest {
  EnrollmentVerifyRequest({
    required this.enrollmentId,
    required this.devicePublicKey,
    required this.enrollmentProofTokenSigned,
    this.challengeResponse,
  });

  final String enrollmentId;
  final String devicePublicKey;
  final String enrollmentProofTokenSigned;
  final String? challengeResponse;

  Map<String, Object?> toJson() {
    return <String, Object?>{
      'enrollmentId': int.parse(enrollmentId),
      'devicePublicKey': devicePublicKey,
      'enrollmentProofTokenSigned': enrollmentProofTokenSigned,
      if (challengeResponse != null && challengeResponse!.isNotEmpty)
        'challengeResponse': int.parse(challengeResponse!),
    };
  }
}

class EnrollmentVerifyResponse {
  EnrollmentVerifyResponse({
    required this.active,
    required this.enrollmentVerifyMessage,
    required this.enrollmentVerifyPayloadSignedByIntegration,
  });

  final bool active;
  final String enrollmentVerifyMessage;
  /// Ed25519 (Base64URL) over the canonical verify-result payload (outcome VERIFIED).
  final String enrollmentVerifyPayloadSignedByIntegration;

  factory EnrollmentVerifyResponse.fromJson(Map<String, dynamic> json) {
    final active = json['active'];
    if (active is! bool) {
      throw FormatException('Missing or invalid active in verify response');
    }
    return EnrollmentVerifyResponse(
      active: active,
      enrollmentVerifyMessage: _requiredString(json, 'enrollmentVerifyMessage'),
      enrollmentVerifyPayloadSignedByIntegration: _requiredString(
        json,
        'enrollmentVerifyPayloadSignedByIntegration',
      ),
    );
  }
}

class PendingAuthRequest {
  PendingAuthRequest({
    required this.enrollmentId,
    required this.enrollmentProofToken,
    required this.deviceProofToken,
    required this.deviceProofTokenSigned,
  });

  final String enrollmentId;
  final String enrollmentProofToken;
  final String deviceProofToken;
  final String deviceProofTokenSigned;

  Map<String, Object?> toJson() {
    return <String, Object?>{
      'enrollmentId': int.parse(enrollmentId),
      'enrollmentProofToken': enrollmentProofToken,
      'deviceProofToken': deviceProofToken,
      'deviceProofTokenSigned': deviceProofTokenSigned,
    };
  }
}

class PendingAuthResponse {
  PendingAuthResponse({
    required this.authAttemptId,
    required this.authAttemptProofToken,
    required this.authAttemptProofTokenSignedByIntegration,
    required this.authAttemptChallengeRequired,
    required this.authAttemptChallengeRequiredByPolicy,
    this.contextTitle,
    this.contextMessage,
  });

  final String authAttemptId;
  final String authAttemptProofToken;
  final String authAttemptProofTokenSignedByIntegration;
  final bool authAttemptChallengeRequired;
  final bool authAttemptChallengeRequiredByPolicy;
  final String? contextTitle;
  final String? contextMessage;

  factory PendingAuthResponse.fromJson(Map<String, dynamic> json) {
    final challengeRequired = json['authAttemptChallengeRequired'];
    final challengeRequiredByPolicy = json['authAttemptChallengeRequiredByPolicy'];
    if (challengeRequired is! bool) {
      throw FormatException(
        'Missing or invalid authAttemptChallengeRequired in pending response',
      );
    }
    if (challengeRequiredByPolicy is! bool) {
      throw FormatException(
        'Missing or invalid authAttemptChallengeRequiredByPolicy in pending response',
      );
    }
    return PendingAuthResponse(
      authAttemptId: _requiredString(json, 'authAttemptId'),
      authAttemptProofToken: _requiredString(json, 'authAttemptProofToken'),
      authAttemptProofTokenSignedByIntegration: _requiredString(
        json,
        'authAttemptProofTokenSignedByIntegration',
      ),
      authAttemptChallengeRequired: challengeRequired,
      authAttemptChallengeRequiredByPolicy: challengeRequiredByPolicy,
      contextTitle: _optionalString(json, 'contextTitle'),
      contextMessage: _optionalString(json, 'contextMessage'),
    );
  }
}

class RespondAuthRequest {
  RespondAuthRequest({
    required this.authAttemptId,
    required this.authAttemptAccepted,
    required this.authAttemptProofTokenSignedByDevice,
    this.authAttemptChallengeResponse,
  });

  final String authAttemptId;
  final bool authAttemptAccepted;
  final String authAttemptProofTokenSignedByDevice;
  final String? authAttemptChallengeResponse;

  Map<String, Object?> toJson() {
    return <String, Object?>{
      'authAttemptId': int.parse(authAttemptId),
      'authAttemptAccepted': authAttemptAccepted,
      'authAttemptProofTokenSignedByDevice': authAttemptProofTokenSignedByDevice,
      if (authAttemptChallengeResponse != null &&
          authAttemptChallengeResponse!.isNotEmpty)
        'authAttemptChallengeResponse': int.parse(authAttemptChallengeResponse!),
    };
  }
}

class RespondAuthResponse {
  RespondAuthResponse({
    required this.authAttemptId,
    required this.authAttemptResult,
    required this.authAttemptMessage,
    required this.authAttemptProofTokenResultSignedByIntegration,
  });

  final String authAttemptId;
  final String authAttemptResult;
  final String authAttemptMessage;
  final String? authAttemptProofTokenResultSignedByIntegration;

  factory RespondAuthResponse.fromJson(Map<String, dynamic> json) {
    return RespondAuthResponse(
      authAttemptId: _requiredString(json, 'authAttemptId'),
      authAttemptResult: _requiredString(json, 'authAttemptResult'),
      authAttemptMessage: _requiredString(json, 'authAttemptMessage'),
      authAttemptProofTokenResultSignedByIntegration: _optionalString(
        json,
        'authAttemptProofTokenResultSignedByIntegration',
      ),
    );
  }
}

String _requiredString(Map<String, dynamic> json, String key) {
  final value = json[key];
  if (value == null) {
    throw FormatException('Missing $key');
  }
  final asString = value.toString();
  if (asString.isEmpty) {
    throw FormatException('Empty $key');
  }
  return asString;
}

String? _optionalString(Map<String, dynamic> json, String key) {
  final value = json[key];
  if (value == null) {
    return null;
  }
  final asString = value.toString().trim();
  return asString.isEmpty ? null : asString;
}

int? _optionalInt(Map<String, dynamic> json, String key) {
  final value = json[key];
  if (value == null) {
    return null;
  }
  if (value is int) {
    return value;
  }
  return int.tryParse(value.toString());
}