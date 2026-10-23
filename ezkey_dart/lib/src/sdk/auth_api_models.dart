import 'errors.dart';

/// Wire DTOs of the device-facing Auth API (`specs/auth-api/openapi-spec.json`). Ids are JSON
/// integers; Base64 fields are strings. Parsing is strict on required fields and throws
/// [EzkeyIntegrityException] (`malformedResponse`) on contract violations.

class EnrollmentBindRequest {
  EnrollmentBindRequest({
    required this.enrollmentId,
    required this.enrollmentProofToken,
  });

  final int enrollmentId;
  final String enrollmentProofToken;

  Map<String, Object?> toJson() => <String, Object?>{
    'enrollmentId': enrollmentId,
    'enrollmentProofToken': enrollmentProofToken,
  };
}

class EnrollmentBindResponse {
  EnrollmentBindResponse({
    required this.enrollmentId,
    required this.enrollmentProofToken,
    required this.integrationPublicKey,
    required this.integrationKeyAlgorithm,
    required this.isSystemIntegration,
    required this.enrollmentBindPayloadSignedByIntegration,
    this.integrationName,
    this.integrationDescription,
    this.enrollmentName,
    this.tenantId,
    this.tenantName,
    this.tenantDescription,
    this.adminType,
  });

  final int enrollmentId;
  final String enrollmentProofToken;
  final String integrationPublicKey;
  final String integrationKeyAlgorithm;
  final bool isSystemIntegration;
  final String enrollmentBindPayloadSignedByIntegration;
  final String? integrationName;
  final String? integrationDescription;
  final String? enrollmentName;
  final int? tenantId;
  final String? tenantName;
  final String? tenantDescription;
  final String? adminType;

  factory EnrollmentBindResponse.fromJson(Map<String, dynamic> json) {
    return EnrollmentBindResponse(
      enrollmentId: _requiredInt(json, 'enrollmentId'),
      enrollmentProofToken: _requiredString(json, 'enrollmentProofToken'),
      integrationPublicKey: _requiredString(json, 'integrationPublicKey'),
      integrationKeyAlgorithm: _requiredString(json, 'integrationKeyAlgorithm'),
      isSystemIntegration: _requiredBool(json, 'isSystemIntegration'),
      enrollmentBindPayloadSignedByIntegration: _requiredString(
        json,
        'enrollmentBindPayloadSignedByIntegration',
      ),
      integrationName: _rawOptionalString(json, 'integrationName'),
      integrationDescription: _rawOptionalString(
        json,
        'integrationDescription',
      ),
      enrollmentName: _rawOptionalString(json, 'enrollmentName'),
      tenantId: _optionalInt(json, 'tenantId'),
      tenantName: _rawOptionalString(json, 'tenantName'),
      tenantDescription: _rawOptionalString(json, 'tenantDescription'),
      adminType: _rawOptionalString(json, 'adminType'),
    );
  }

  Map<String, Object?> toJson() => <String, Object?>{
    'enrollmentId': enrollmentId,
    'enrollmentProofToken': enrollmentProofToken,
    'integrationPublicKey': integrationPublicKey,
    'integrationKeyAlgorithm': integrationKeyAlgorithm,
    'isSystemIntegration': isSystemIntegration,
    'enrollmentBindPayloadSignedByIntegration':
        enrollmentBindPayloadSignedByIntegration,
    'integrationName': integrationName,
    'integrationDescription': integrationDescription,
    'enrollmentName': enrollmentName,
    'tenantId': tenantId,
    'tenantName': tenantName,
    'tenantDescription': tenantDescription,
    'adminType': adminType,
  };
}

class EnrollmentVerifyRequest {
  EnrollmentVerifyRequest({
    required this.enrollmentId,
    required this.challengeResponse,
    required this.devicePublicKey,
    required this.enrollmentProofTokenSigned,
    this.devicePrivateKeyStorageTier,
  });

  final int enrollmentId;
  final int challengeResponse;
  final String devicePublicKey;
  final String enrollmentProofTokenSigned;
  final String? devicePrivateKeyStorageTier;

  Map<String, Object?> toJson() => <String, Object?>{
    'enrollmentId': enrollmentId,
    'challengeResponse': challengeResponse,
    'devicePublicKey': devicePublicKey,
    'enrollmentProofTokenSigned': enrollmentProofTokenSigned,
    if (devicePrivateKeyStorageTier != null)
      'devicePrivateKeyStorageTier': devicePrivateKeyStorageTier,
  };

  factory EnrollmentVerifyRequest.fromJson(Map<String, dynamic> json) {
    return EnrollmentVerifyRequest(
      enrollmentId: _requiredInt(json, 'enrollmentId'),
      challengeResponse: _requiredInt(json, 'challengeResponse'),
      devicePublicKey: _requiredString(json, 'devicePublicKey'),
      enrollmentProofTokenSigned: _requiredString(
        json,
        'enrollmentProofTokenSigned',
      ),
      devicePrivateKeyStorageTier: _optionalString(
        json,
        'devicePrivateKeyStorageTier',
      ),
    );
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
  final String enrollmentVerifyPayloadSignedByIntegration;

  factory EnrollmentVerifyResponse.fromJson(Map<String, dynamic> json) {
    return EnrollmentVerifyResponse(
      active: _requiredBool(json, 'active'),
      enrollmentVerifyMessage: _requiredString(json, 'enrollmentVerifyMessage'),
      enrollmentVerifyPayloadSignedByIntegration: _requiredString(
        json,
        'enrollmentVerifyPayloadSignedByIntegration',
      ),
    );
  }

  Map<String, Object?> toJson() => <String, Object?>{
    'active': active,
    'enrollmentVerifyMessage': enrollmentVerifyMessage,
    'enrollmentVerifyPayloadSignedByIntegration':
        enrollmentVerifyPayloadSignedByIntegration,
  };
}

class InstanceInfoRequest {
  InstanceInfoRequest({required this.enrollmentProofToken});

  final String enrollmentProofToken;

  Map<String, Object?> toJson() => <String, Object?>{
    'enrollmentProofToken': enrollmentProofToken,
  };
}

class InstanceInfoResponse {
  InstanceInfoResponse({
    required this.enrollmentId,
    required this.instanceInfoPayloadSignedByIntegration,
    this.authApiPublicBaseUrl,
    this.instanceName,
    this.instanceDescription,
    this.aboutUrl,
  });

  final int enrollmentId;
  final String instanceInfoPayloadSignedByIntegration;
  final String? authApiPublicBaseUrl;
  final String? instanceName;
  final String? instanceDescription;
  final String? aboutUrl;

  /// Signed segments must use the exact JSON strings, so text fields are not trimmed here.
  factory InstanceInfoResponse.fromJson(Map<String, dynamic> json) {
    return InstanceInfoResponse(
      enrollmentId: _requiredInt(json, 'enrollmentId'),
      instanceInfoPayloadSignedByIntegration: _requiredString(
        json,
        'instanceInfoPayloadSignedByIntegration',
      ),
      authApiPublicBaseUrl: _rawOptionalString(json, 'authApiPublicBaseUrl'),
      instanceName: _rawOptionalString(json, 'instanceName'),
      instanceDescription: _rawOptionalString(json, 'instanceDescription'),
      aboutUrl: _rawOptionalString(json, 'aboutUrl'),
    );
  }

  Map<String, Object?> toJson() => <String, Object?>{
    'enrollmentId': enrollmentId,
    'instanceInfoPayloadSignedByIntegration':
        instanceInfoPayloadSignedByIntegration,
    'authApiPublicBaseUrl': authApiPublicBaseUrl,
    'instanceName': instanceName,
    'instanceDescription': instanceDescription,
    'aboutUrl': aboutUrl,
  };
}

class PendingAuthRequest {
  PendingAuthRequest({
    required this.enrollmentId,
    required this.enrollmentProofToken,
    required this.deviceProofToken,
    required this.deviceProofTokenSigned,
  });

  final int enrollmentId;
  final String enrollmentProofToken;
  final String deviceProofToken;
  final String deviceProofTokenSigned;

  Map<String, Object?> toJson() => <String, Object?>{
    'enrollmentId': enrollmentId,
    'enrollmentProofToken': enrollmentProofToken,
    'deviceProofToken': deviceProofToken,
    'deviceProofTokenSigned': deviceProofTokenSigned,
  };

  factory PendingAuthRequest.fromJson(Map<String, dynamic> json) {
    return PendingAuthRequest(
      enrollmentId: _requiredInt(json, 'enrollmentId'),
      enrollmentProofToken: _requiredString(json, 'enrollmentProofToken'),
      deviceProofToken: _requiredString(json, 'deviceProofToken'),
      deviceProofTokenSigned: _requiredString(json, 'deviceProofTokenSigned'),
    );
  }
}

class PendingAuthResponse {
  PendingAuthResponse({
    required this.authAttemptId,
    required this.authAttemptProofToken,
    required this.authAttemptProofTokenSignedByIntegration,
    required this.authAttemptChallengeRequired,
    this.contextTitle,
    this.contextMessage,
  });

  final int authAttemptId;
  final String authAttemptProofToken;
  final String authAttemptProofTokenSignedByIntegration;
  final bool authAttemptChallengeRequired;
  final String? contextTitle;
  final String? contextMessage;

  factory PendingAuthResponse.fromJson(Map<String, dynamic> json) {
    return PendingAuthResponse(
      authAttemptId: _requiredInt(json, 'authAttemptId'),
      authAttemptProofToken: _requiredString(json, 'authAttemptProofToken'),
      authAttemptProofTokenSignedByIntegration: _requiredString(
        json,
        'authAttemptProofTokenSignedByIntegration',
      ),
      authAttemptChallengeRequired: _requiredBool(
        json,
        'authAttemptChallengeRequired',
      ),
      contextTitle: _rawOptionalString(json, 'contextTitle'),
      contextMessage: _rawOptionalString(json, 'contextMessage'),
    );
  }

  Map<String, Object?> toJson() => <String, Object?>{
    'authAttemptId': authAttemptId,
    'authAttemptProofToken': authAttemptProofToken,
    'authAttemptProofTokenSignedByIntegration':
        authAttemptProofTokenSignedByIntegration,
    'authAttemptChallengeRequired': authAttemptChallengeRequired,
    'contextTitle': contextTitle,
    'contextMessage': contextMessage,
  };
}

class RespondAuthRequest {
  RespondAuthRequest({
    required this.authAttemptId,
    required this.authAttemptAccepted,
    required this.authAttemptProofTokenSignedByDevice,
    this.authAttemptChallengeResponse,
  });

  final int authAttemptId;
  final bool authAttemptAccepted;
  final String authAttemptProofTokenSignedByDevice;
  final int? authAttemptChallengeResponse;

  Map<String, Object?> toJson() => <String, Object?>{
    'authAttemptId': authAttemptId,
    'authAttemptAccepted': authAttemptAccepted,
    'authAttemptProofTokenSignedByDevice': authAttemptProofTokenSignedByDevice,
    if (authAttemptChallengeResponse != null)
      'authAttemptChallengeResponse': authAttemptChallengeResponse,
  };

  factory RespondAuthRequest.fromJson(Map<String, dynamic> json) {
    return RespondAuthRequest(
      authAttemptId: _requiredInt(json, 'authAttemptId'),
      authAttemptAccepted: _requiredBool(json, 'authAttemptAccepted'),
      authAttemptProofTokenSignedByDevice: _requiredString(
        json,
        'authAttemptProofTokenSignedByDevice',
      ),
      authAttemptChallengeResponse: _optionalInt(
        json,
        'authAttemptChallengeResponse',
      ),
    );
  }
}

class RespondAuthResponse {
  RespondAuthResponse({
    required this.authAttemptId,
    required this.authAttemptResult,
    required this.authAttemptMessage,
    this.authAttemptProofTokenResultSignedByIntegration,
  });

  final int authAttemptId;
  final String authAttemptResult;
  final String authAttemptMessage;
  final String? authAttemptProofTokenResultSignedByIntegration;

  factory RespondAuthResponse.fromJson(Map<String, dynamic> json) {
    return RespondAuthResponse(
      authAttemptId: _requiredInt(json, 'authAttemptId'),
      authAttemptResult: _requiredString(json, 'authAttemptResult'),
      authAttemptMessage: _rawOptionalString(json, 'authAttemptMessage') ?? '',
      authAttemptProofTokenResultSignedByIntegration: _optionalString(
        json,
        'authAttemptProofTokenResultSignedByIntegration',
      ),
    );
  }

  Map<String, Object?> toJson() => <String, Object?>{
    'authAttemptId': authAttemptId,
    'authAttemptResult': authAttemptResult,
    'authAttemptMessage': authAttemptMessage,
    'authAttemptProofTokenResultSignedByIntegration':
        authAttemptProofTokenResultSignedByIntegration,
  };
}

Never _malformed(String message) {
  throw EzkeyIntegrityException(EzkeyIntegrityCheck.malformedResponse, message);
}

String _requiredString(Map<String, dynamic> json, String key) {
  final value = json[key];
  if (value is! String || value.isEmpty) {
    _malformed('Missing or invalid $key');
  }
  return value;
}

int _requiredInt(Map<String, dynamic> json, String key) {
  final value = json[key];
  if (value is! int) {
    _malformed('Missing or invalid $key');
  }
  return value;
}

bool _requiredBool(Map<String, dynamic> json, String key) {
  final value = json[key];
  if (value is! bool) {
    _malformed('Missing or invalid $key');
  }
  return value;
}

String? _optionalString(Map<String, dynamic> json, String key) {
  final value = json[key];
  if (value == null) {
    return null;
  }
  if (value is! String) {
    _malformed('Invalid $key');
  }
  final trimmed = value.trim();
  return trimmed.isEmpty ? null : trimmed;
}

String? _rawOptionalString(Map<String, dynamic> json, String key) {
  final value = json[key];
  if (value == null) {
    return null;
  }
  if (value is! String) {
    _malformed('Invalid $key');
  }
  return value;
}

int? _optionalInt(Map<String, dynamic> json, String key) {
  final value = json[key];
  if (value == null) {
    return null;
  }
  if (value is! int) {
    _malformed('Invalid $key');
  }
  return value;
}
