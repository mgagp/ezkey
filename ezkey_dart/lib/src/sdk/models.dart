import 'dart:convert';

import 'errors.dart';
import 'url_policy.dart';

/// Server-side upper bound of an auth attempt's lifetime (Integration API clamps `ttlSeconds` to
/// 120; minimum 15). The device-facing Auth API does not return `expiresAt`, so this is the only
/// expiry bound an authenticator can show without extra server calls.
const Duration maxAuthAttemptTtl = Duration(seconds: 120);

/// Client-reported protection tier of the device private key, sent on enrollment verify.
///
/// The server stores it as metadata; it does not verify attestation. Report honestly.
enum DevicePrivateKeyStorageTier {
  /// Software key (tests, demos, this package's [SoftwareDeviceKeyStore]).
  none('NONE'),

  /// Hardware-backed keystore (Android Keystore TEE, iOS Keychain with Secure Enclave access).
  standard('STANDARD'),

  /// StrongBox or equivalent discrete secure element.
  strong('STRONG');

  const DevicePrivateKeyStorageTier(this.wireValue);

  final String wireValue;

  static DevicePrivateKeyStorageTier? fromWire(String? value) {
    for (final tier in values) {
      if (tier.wireValue == value) {
        return tier;
      }
    }
    return null;
  }
}

/// Content of the enrollment QR code / invitation link shown by the Admin UI or the integrating
/// application.
///
/// Primary format (JSON): `{"enrollmentId": 42, "enrollmentProofToken": "...", "authUrl": "https://..."}`
/// (`authUrl` optional; `enrollmentId` may be a number or a string).
/// Legacy format: `42|proofToken`.
///
/// The 6-digit enrollment challenge is **not** in the code: it is shown separately to the user
/// (out of band) and typed into the authenticator.
class EnrollmentCode {
  EnrollmentCode({
    required this.enrollmentId,
    required this.enrollmentProofToken,
    this.authUrl,
  });

  final int enrollmentId;

  /// Secret bearer token for this enrollment. Never log it.
  final String enrollmentProofToken;

  /// Normalized Auth API base URL from the code, when present.
  final String? authUrl;

  /// Parses [value]. Throws [EzkeyValidationException] when the code is malformed or carries an
  /// `authUrl` that [policy] rejects (fail closed: a crafted QR must not redirect the device to an
  /// insecure server).
  factory EnrollmentCode.parse(
    String value, {
    EzkeyUrlPolicy policy = const EzkeyUrlPolicy(),
  }) {
    final trimmed = value.trim();
    if (trimmed.isEmpty) {
      throw const EzkeyValidationException('Empty enrollment code');
    }

    Object? decoded;
    try {
      decoded = jsonDecode(trimmed);
    } on FormatException {
      decoded = null;
    }
    if (decoded is Map<String, dynamic> &&
        decoded['enrollmentId'] != null &&
        decoded['enrollmentProofToken'] != null) {
      final rawAuthUrl = decoded['authUrl'];
      String? authUrl;
      if (rawAuthUrl != null) {
        authUrl = policy.normalize(rawAuthUrl.toString());
        if (authUrl == null) {
          throw const EzkeyValidationException(
            'Invalid Auth API URL in enrollment code',
          );
        }
      }
      return EnrollmentCode(
        enrollmentId: _parseEnrollmentId(decoded['enrollmentId'].toString()),
        enrollmentProofToken: _requireToken(
          decoded['enrollmentProofToken'].toString(),
        ),
        authUrl: authUrl,
      );
    }

    final separator = trimmed.indexOf('|');
    if (separator > 0) {
      return EnrollmentCode(
        enrollmentId: _parseEnrollmentId(trimmed.substring(0, separator)),
        enrollmentProofToken: _requireToken(trimmed.substring(separator + 1)),
      );
    }

    throw const EzkeyValidationException('Unsupported enrollment code format');
  }

  @override
  String toString() =>
      'EnrollmentCode(enrollmentId: $enrollmentId, authUrl: $authUrl)';
}

int _parseEnrollmentId(String value) {
  final id = int.tryParse(value.trim());
  if (id == null || id <= 0 || id > 0x7fffffff) {
    throw const EzkeyValidationException(
      'Enrollment id must be a positive 32-bit integer',
    );
  }
  return id;
}

String _requireToken(String value) {
  final token = value.trim();
  if (token.isEmpty || token.contains(RegExp(r'\s'))) {
    throw const EzkeyValidationException(
      'Enrollment proof token is missing or malformed',
    );
  }
  return token;
}

/// Parses a numeric challenge typed by the user (enrollment challenge or auth-attempt challenge).
///
/// Accepts 1 to 9 digits (server values are 32-bit integers; enrollment challenges are 6 digits
/// and auth-attempt challenges default to 6). Spaces and hyphens are ignored so `123 456` works.
int parseChallengeCode(String value) {
  final compact = value.replaceAll(RegExp(r'[\s-]'), '');
  if (!RegExp(r'^\d{1,9}$').hasMatch(compact)) {
    throw const EzkeyValidationException(
      'Challenge code must be 1 to 9 digits',
    );
  }
  return int.parse(compact);
}

/// Everything an authenticator must persist per enrollment after a successful enrollment.
///
/// Persist it in secure storage (Keychain / EncryptedSharedPreferences): [enrollmentProofToken]
/// is a bearer credential. The device private key is **not** in here; it stays in the
/// `EzkeyDeviceKeyStore` under [deviceKeyAlias].
class EzkeyEnrollment {
  EzkeyEnrollment({
    required this.authApiBaseUrl,
    required this.enrollmentId,
    required this.enrollmentProofToken,
    required this.integrationPublicKey,
    required this.integrationKeyAlgorithm,
    required this.deviceKeyAlias,
    required this.devicePublicKey,
    required this.devicePrivateKeyStorageTier,
    required this.enrolledAt,
    this.integrationName,
    this.integrationDescription,
    this.enrollmentName,
    this.tenantId,
    this.tenantName,
    this.tenantDescription,
    this.isSystemIntegration = false,
    this.adminType,
  });

  /// Normalized Auth API base URL this enrollment belongs to.
  final String authApiBaseUrl;
  final int enrollmentId;

  /// Bearer credential for pending / instance-info. Secret.
  final String enrollmentProofToken;

  /// Integration Ed25519 public key (raw 32 bytes, Base64URL). Pinned at enrollment; every later
  /// server answer is verified against it.
  final String integrationPublicKey;
  final String integrationKeyAlgorithm;

  /// Alias of the device key pair in the key store.
  final String deviceKeyAlias;

  /// Device public key (X.509 SPKI, standard Base64) registered with the server.
  final String devicePublicKey;
  final DevicePrivateKeyStorageTier devicePrivateKeyStorageTier;
  final DateTime enrolledAt;

  /// Display metadata (authenticated by the bind signature).
  final String? integrationName;
  final String? integrationDescription;
  final String? enrollmentName;
  final int? tenantId;
  final String? tenantName;
  final String? tenantDescription;

  /// `true` for Ezkey administrator MFA enrollments (system integration).
  final bool isSystemIntegration;

  /// `GLOBAL_ADMIN` or `TENANT_ADMIN` for administrator MFA enrollments, otherwise `null`.
  final String? adminType;

  /// Stable local identifier (enrollment ids are only unique per server).
  String get localId => localIdFor(authApiBaseUrl, enrollmentId);

  static String localIdFor(String authApiBaseUrl, int enrollmentId) =>
      '$authApiBaseUrl#$enrollmentId';

  Uri get authApiBaseUri => Uri.parse(authApiBaseUrl);

  Map<String, Object?> toJson() => <String, Object?>{
    'schemaVersion': 1,
    'authApiBaseUrl': authApiBaseUrl,
    'enrollmentId': enrollmentId,
    'enrollmentProofToken': enrollmentProofToken,
    'integrationPublicKey': integrationPublicKey,
    'integrationKeyAlgorithm': integrationKeyAlgorithm,
    'deviceKeyAlias': deviceKeyAlias,
    'devicePublicKey': devicePublicKey,
    'devicePrivateKeyStorageTier': devicePrivateKeyStorageTier.wireValue,
    'enrolledAt': enrolledAt.toUtc().toIso8601String(),
    'integrationName': integrationName,
    'integrationDescription': integrationDescription,
    'enrollmentName': enrollmentName,
    'tenantId': tenantId,
    'tenantName': tenantName,
    'tenantDescription': tenantDescription,
    'isSystemIntegration': isSystemIntegration,
    'adminType': adminType,
  };

  factory EzkeyEnrollment.fromJson(Map<String, dynamic> json) {
    String req(String key) {
      final value = json[key];
      if (value is! String || value.isEmpty) {
        throw EzkeyLocalStateException('Stored enrollment is missing $key');
      }
      return value;
    }

    final id = json['enrollmentId'];
    if (id is! int) {
      throw const EzkeyLocalStateException(
        'Stored enrollment is missing enrollmentId',
      );
    }
    final tier = DevicePrivateKeyStorageTier.fromWire(
      json['devicePrivateKeyStorageTier'] as String?,
    );
    if (tier == null) {
      throw const EzkeyLocalStateException(
        'Stored enrollment has an unknown storage tier',
      );
    }
    return EzkeyEnrollment(
      authApiBaseUrl: req('authApiBaseUrl'),
      enrollmentId: id,
      enrollmentProofToken: req('enrollmentProofToken'),
      integrationPublicKey: req('integrationPublicKey'),
      integrationKeyAlgorithm: req('integrationKeyAlgorithm'),
      deviceKeyAlias: req('deviceKeyAlias'),
      devicePublicKey: req('devicePublicKey'),
      devicePrivateKeyStorageTier: tier,
      enrolledAt: DateTime.parse(req('enrolledAt')).toUtc(),
      integrationName: json['integrationName'] as String?,
      integrationDescription: json['integrationDescription'] as String?,
      enrollmentName: json['enrollmentName'] as String?,
      tenantId: json['tenantId'] as int?,
      tenantName: json['tenantName'] as String?,
      tenantDescription: json['tenantDescription'] as String?,
      isSystemIntegration: json['isSystemIntegration'] == true,
      adminType: json['adminType'] as String?,
    );
  }

  @override
  String toString() =>
      'EzkeyEnrollment(localId: $localId, enrollmentName: $enrollmentName, '
      'integrationName: $integrationName, tenantName: $tenantName)';
}

/// A pending auth attempt claimed by this device, after its integration signature was verified.
///
/// Claiming is one-shot: the server moved the attempt from `PENDING` to `READ`, and the next
/// `pending` call will not return it again. Keep this object until the user decides.
class PendingAuthAttempt {
  PendingAuthAttempt({
    required this.enrollmentLocalId,
    required this.authAttemptId,
    required this.authAttemptProofToken,
    required this.challengeRequired,
    required this.receivedAt,
    this.contextTitle,
    this.contextMessage,
  });

  final String enrollmentLocalId;
  final int authAttemptId;

  /// Secret per-attempt token; signed on respond. Never log or display it.
  final String authAttemptProofToken;

  /// When `true`, the integrating application is displaying a numeric code to the user, and the
  /// user must type it into the authenticator before approving.
  final bool challengeRequired;

  /// Optional integration-provided context (authenticated by the pending signature). Show these
  /// so the user knows what they approve ("Payment approval", "Wire 50 € to ACME").
  final String? contextTitle;
  final String? contextMessage;

  /// Local clock time when the attempt was claimed.
  final DateTime receivedAt;

  /// Latest instant at which the attempt can still be valid on the server (it may expire or be
  /// cancelled earlier). Use it to hide stale prompts, not as an exact countdown.
  DateTime get latestPossibleExpiry => receivedAt.add(maxAuthAttemptTtl);

  bool isCertainlyExpired(DateTime now) => now.isAfter(latestPossibleExpiry);

  @override
  String toString() =>
      'PendingAuthAttempt(authAttemptId: $authAttemptId, challengeRequired: $challengeRequired, '
      'contextTitle: $contextTitle)';
}

/// Server outcome of a respond call (as signed by the integration).
enum AuthAttemptResult {
  /// User approved and the server accepted it.
  approved('APPROVED'),

  /// User denied.
  denied('DENIED'),

  /// Approve refused, typically a wrong challenge code. The attempt is now invalid; the
  /// integrating application must start a new one.
  failed('FAILED'),

  /// The attempt expired.
  expired('EXPIRED');

  const AuthAttemptResult(this.wireValue);

  final String wireValue;

  static AuthAttemptResult? fromWire(String? value) {
    for (final result in values) {
      if (result.wireValue == value) {
        return result;
      }
    }
    return null;
  }
}

/// Verified result of approving or denying an attempt.
class AuthAttemptOutcome {
  AuthAttemptOutcome({
    required this.authAttemptId,
    required this.accepted,
    required this.result,
    required this.message,
  });

  final int authAttemptId;

  /// What the device sent (`true` = approve).
  final bool accepted;
  final AuthAttemptResult result;

  /// Integration-signed message (authenticated, English server text).
  final String message;

  @override
  String toString() =>
      'AuthAttemptOutcome(authAttemptId: $authAttemptId, accepted: $accepted, result: ${result.name})';
}

/// Installation branding returned by the enrolled instance-info endpoint (signature verified).
class EzkeyInstanceInfo {
  EzkeyInstanceInfo({
    required this.enrollmentId,
    this.authApiPublicBaseUrl,
    this.instanceName,
    this.instanceDescription,
    this.aboutUrl,
  });

  final int enrollmentId;
  final String? authApiPublicBaseUrl;
  final String? instanceName;
  final String? instanceDescription;
  final String? aboutUrl;

  @override
  String toString() =>
      'EzkeyInstanceInfo(instanceName: $instanceName, authApiPublicBaseUrl: $authApiPublicBaseUrl)';
}
