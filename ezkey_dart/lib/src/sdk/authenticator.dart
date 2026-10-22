import 'dart:async';

import 'package:http/http.dart' as http;

import '../ed25519.dart';
import '../encoding.dart';
import '../payload.dart';
import '../secure_random.dart';
import 'auth_api_client.dart';
import 'auth_api_models.dart';
import 'enrollment_store.dart';
import 'errors.dart';
import 'key_store.dart';
import 'models.dart';
import 'url_policy.dart';

/// Builds the transport for one Auth API base URL.
typedef EzkeyAuthApiFactory = EzkeyAuthApi Function(Uri authApiBaseUri);

/// The only integration key algorithm the protocol defines. Anything else fails closed.
const String supportedIntegrationKeyAlgorithm = 'ed25519';

/// Result of the first enrollment step: the invitation was bound to this device and the bind
/// answer was authenticated. Show [integrationName] / [tenantName] / [enrollmentName] to the user
/// and ask for the enrollment challenge, then call [EzkeyAuthenticator.completeEnrollment].
///
/// Binding is one-shot on the server: if the user abandons here, the invitation cannot be bound
/// again (a new invitation is required).
class EzkeyBoundEnrollment {
  EzkeyBoundEnrollment._({
    required this.authApiBaseUrl,
    required this.response,
  });

  final String authApiBaseUrl;
  final EnrollmentBindResponse response;

  int get enrollmentId => response.enrollmentId;
  String? get integrationName => response.integrationName;
  String? get integrationDescription => response.integrationDescription;
  String? get enrollmentName => response.enrollmentName;
  String? get tenantName => response.tenantName;
  bool get isSystemIntegration => response.isSystemIntegration;
  String? get adminType => response.adminType;

  @override
  String toString() =>
      'EzkeyBoundEnrollment(enrollmentId: $enrollmentId, integrationName: $integrationName)';
}

/// Device-side Ezkey protocol engine for an authenticator app.
///
/// Every server answer that the protocol signs is verified before it is returned; any mismatch
/// throws [EzkeyIntegrityException] (fail closed). The facade holds no secrets itself: keys live
/// in [keyStore], enrollment records in [enrollmentStore].
class EzkeyAuthenticator {
  EzkeyAuthenticator({
    required this.keyStore,
    required this.enrollmentStore,
    EzkeyAuthApiFactory? apiFactory,
    http.Client? httpClient,
    Duration requestTimeout = const Duration(seconds: 15),
    this.urlPolicy = const EzkeyUrlPolicy(),
    DateTime Function()? clock,
    String Function(int enrollmentId)? keyAliasFactory,
  }) : _apiFactory =
           apiFactory ??
           ((uri) =>
               AuthApiClient(uri, client: httpClient, timeout: requestTimeout)),
       _clock = clock ?? DateTime.now,
       _keyAliasFactory = keyAliasFactory ?? _defaultKeyAlias;

  final EzkeyDeviceKeyStore keyStore;
  final EzkeyEnrollmentStore enrollmentStore;
  final EzkeyUrlPolicy urlPolicy;
  final EzkeyAuthApiFactory _apiFactory;
  final DateTime Function() _clock;
  final String Function(int enrollmentId) _keyAliasFactory;
  final Map<String, EzkeyAuthApi> _apis = <String, EzkeyAuthApi>{};

  // ---------------------------------------------------------------------------------------------
  // Enrollment
  // ---------------------------------------------------------------------------------------------

  /// Step 1: bind the invitation in [code] to this device and authenticate the answer.
  ///
  /// The Auth API URL is [authApiBaseUrlOverride] when given, else the code's `authUrl`; it must
  /// pass [urlPolicy]. Throws [EzkeyValidationException] when no URL is available or the
  /// enrollment already exists locally.
  Future<EzkeyBoundEnrollment> bindEnrollment(
    EnrollmentCode code, {
    String? authApiBaseUrlOverride,
  }) async {
    final rawUrl = authApiBaseUrlOverride ?? code.authUrl;
    if (rawUrl == null) {
      throw const EzkeyValidationException(
        'Enrollment code has no authUrl; an Auth API base URL must be configured',
      );
    }
    final baseUrl = urlPolicy.require(rawUrl).toString();
    final localId = EzkeyEnrollment.localIdFor(baseUrl, code.enrollmentId);
    if (await enrollmentStore.load(localId) != null) {
      throw const EzkeyValidationException(
        'This enrollment already exists on this device',
      );
    }

    final response = await _api(baseUrl).bind(
      EnrollmentBindRequest(
        enrollmentId: code.enrollmentId,
        enrollmentProofToken: code.enrollmentProofToken,
      ),
    );

    if (response.enrollmentId != code.enrollmentId ||
        response.enrollmentProofToken != code.enrollmentProofToken) {
      throw const EzkeyIntegrityException(
        EzkeyIntegrityCheck.bindConsistency,
        'Bind answer does not match the scanned invitation',
      );
    }
    if (response.integrationKeyAlgorithm != supportedIntegrationKeyAlgorithm) {
      throw const EzkeyIntegrityException(
        EzkeyIntegrityCheck.unsupportedIntegrationKeyAlgorithm,
        'Unsupported integration key algorithm',
      );
    }
    final payload = buildEnrollmentBindPayload(
      enrollmentProofToken: response.enrollmentProofToken,
      enrollmentId: response.enrollmentId,
      integrationPublicKey: response.integrationPublicKey,
      integrationKeyAlgorithm: response.integrationKeyAlgorithm,
      integrationName: response.integrationName,
      integrationDescription: response.integrationDescription,
      enrollmentName: response.enrollmentName,
      tenantId: response.tenantId,
      tenantName: response.tenantName,
      tenantDescription: response.tenantDescription,
      isSystemIntegration: response.isSystemIntegration,
      adminType: response.adminType,
    );
    if (!await verifyEd25519(
      payload,
      response.enrollmentBindPayloadSignedByIntegration,
      response.integrationPublicKey,
    )) {
      throw const EzkeyIntegrityException(
        EzkeyIntegrityCheck.bindSignature,
        'Bind answer integration signature is invalid',
      );
    }
    return EzkeyBoundEnrollment._(authApiBaseUrl: baseUrl, response: response);
  }

  /// Step 2: create the device key, prove possession with the user-typed [enrollmentChallenge],
  /// and persist the enrollment once the server confirmed it with a valid signature.
  ///
  /// A wrong challenge invalidates the invitation server-side (HTTP 400
  /// `enrollment-verify-failed`). On any failure the freshly generated key is deleted and nothing
  /// is persisted.
  Future<EzkeyEnrollment> completeEnrollment(
    EzkeyBoundEnrollment bound, {
    required int enrollmentChallenge,
  }) async {
    final bind = bound.response;
    final alias = _keyAliasFactory(bind.enrollmentId);
    final devicePublicKey = await keyStore.generateKeyPair(alias);
    try {
      final devicePayload = buildEnrollmentVerifyDevicePayload(
        bind.enrollmentProofToken,
        bind.enrollmentId,
        enrollmentChallenge,
        devicePublicKey,
      );
      final signed = await keyStore.sign(alias, devicePayload);
      final response = await _api(bound.authApiBaseUrl).verify(
        EnrollmentVerifyRequest(
          enrollmentId: bind.enrollmentId,
          challengeResponse: enrollmentChallenge,
          devicePublicKey: devicePublicKey,
          enrollmentProofTokenSigned: signed,
          devicePrivateKeyStorageTier: keyStore.storageTier.wireValue,
        ),
      );
      final resultPayload = buildEnrollmentVerifyResultPayload(
        bind.enrollmentProofToken,
        bind.enrollmentId,
        enrollmentVerifiedOutcome,
        response.enrollmentVerifyMessage,
      );
      if (!await verifyEd25519(
        resultPayload,
        response.enrollmentVerifyPayloadSignedByIntegration,
        bind.integrationPublicKey,
      )) {
        throw const EzkeyIntegrityException(
          EzkeyIntegrityCheck.verifySignature,
          'Verify answer integration signature is invalid',
        );
      }
      if (!response.active) {
        throw const EzkeyIntegrityException(
          EzkeyIntegrityCheck.verifyNotActive,
          'Verify answer did not activate the enrollment',
        );
      }
      final enrollment = EzkeyEnrollment(
        authApiBaseUrl: bound.authApiBaseUrl,
        enrollmentId: bind.enrollmentId,
        enrollmentProofToken: bind.enrollmentProofToken,
        integrationPublicKey: bind.integrationPublicKey,
        integrationKeyAlgorithm: bind.integrationKeyAlgorithm,
        deviceKeyAlias: alias,
        devicePublicKey: devicePublicKey,
        devicePrivateKeyStorageTier: keyStore.storageTier,
        enrolledAt: _clock().toUtc(),
        integrationName: bind.integrationName,
        integrationDescription: bind.integrationDescription,
        enrollmentName: bind.enrollmentName,
        tenantId: bind.tenantId,
        tenantName: bind.tenantName,
        tenantDescription: bind.tenantDescription,
        isSystemIntegration: bind.isSystemIntegration,
        adminType: bind.adminType,
      );
      await enrollmentStore.save(enrollment);
      return enrollment;
    } catch (_) {
      await _deleteKeyQuietly(alias);
      rethrow;
    }
  }

  /// Convenience: [bindEnrollment] then [completeEnrollment] (use when the challenge is already
  /// known, e.g. typed on the same screen as the scan).
  Future<EzkeyEnrollment> enroll(
    EnrollmentCode code, {
    required int enrollmentChallenge,
    String? authApiBaseUrlOverride,
  }) async {
    final bound = await bindEnrollment(
      code,
      authApiBaseUrlOverride: authApiBaseUrlOverride,
    );
    return completeEnrollment(bound, enrollmentChallenge: enrollmentChallenge);
  }

  /// Signed installation branding for an existing enrollment.
  Future<EzkeyInstanceInfo> fetchInstanceInfo(
    EzkeyEnrollment enrollment,
  ) async {
    final response = await _api(enrollment.authApiBaseUrl).instanceInfo(
      InstanceInfoRequest(
        enrollmentProofToken: enrollment.enrollmentProofToken,
      ),
    );
    if (response.enrollmentId != enrollment.enrollmentId) {
      throw const EzkeyIntegrityException(
        EzkeyIntegrityCheck.instanceInfoConsistency,
        'Instance-info answer belongs to another enrollment',
      );
    }
    final payload = buildEnrollmentInstanceInfoPayload(
      enrollmentProofToken: enrollment.enrollmentProofToken,
      enrollmentId: enrollment.enrollmentId,
      authApiPublicBaseUrl: response.authApiPublicBaseUrl,
      instanceName: response.instanceName,
      instanceDescription: response.instanceDescription,
      aboutUrl: response.aboutUrl,
    );
    if (!await verifyEd25519(
      payload,
      response.instanceInfoPayloadSignedByIntegration,
      enrollment.integrationPublicKey,
    )) {
      throw const EzkeyIntegrityException(
        EzkeyIntegrityCheck.instanceInfoSignature,
        'Instance-info integration signature is invalid',
      );
    }
    return EzkeyInstanceInfo(
      enrollmentId: response.enrollmentId,
      authApiPublicBaseUrl: response.authApiPublicBaseUrl,
      instanceName: response.instanceName,
      instanceDescription: response.instanceDescription,
      aboutUrl: response.aboutUrl,
    );
  }

  Future<List<EzkeyEnrollment>> listEnrollments() => enrollmentStore.list();

  /// Removes the enrollment record and its device key locally. The server-side enrollment stays
  /// active until an administrator revokes it (there is no device-initiated unenroll endpoint).
  Future<void> forget(EzkeyEnrollment enrollment) async {
    await enrollmentStore.delete(enrollment.localId);
    await _deleteKeyQuietly(enrollment.deviceKeyAlias);
  }

  // ---------------------------------------------------------------------------------------------
  // Auth attempts
  // ---------------------------------------------------------------------------------------------

  /// Claims the most recent pending auth attempt for [enrollment], or returns `null` (HTTP 204).
  ///
  /// One-shot: a returned attempt moved to `READ` on the server and will not be returned again.
  /// Each call signs a fresh random device proof token (replays are rejected server-side).
  Future<PendingAuthAttempt?> checkPending(EzkeyEnrollment enrollment) async {
    final deviceProofToken = generateProofToken();
    final signed = await keyStore.sign(
      enrollment.deviceKeyAlias,
      deviceProofToken,
    );
    final response = await _api(enrollment.authApiBaseUrl).pending(
      PendingAuthRequest(
        enrollmentId: enrollment.enrollmentId,
        enrollmentProofToken: enrollment.enrollmentProofToken,
        deviceProofToken: deviceProofToken,
        deviceProofTokenSigned: signed,
      ),
    );
    if (response == null) {
      return null;
    }
    final payload = buildPendingPayload(
      response.authAttemptProofToken,
      response.authAttemptChallengeRequired,
      response.contextTitle,
      response.contextMessage,
    );
    if (!await verifyEd25519(
      payload,
      response.authAttemptProofTokenSignedByIntegration,
      enrollment.integrationPublicKey,
    )) {
      throw const EzkeyIntegrityException(
        EzkeyIntegrityCheck.pendingSignature,
        'Pending answer integration signature is invalid',
      );
    }
    return PendingAuthAttempt(
      enrollmentLocalId: enrollment.localId,
      authAttemptId: response.authAttemptId,
      authAttemptProofToken: response.authAttemptProofToken,
      challengeRequired: response.authAttemptChallengeRequired,
      contextTitle: response.contextTitle,
      contextMessage: response.contextMessage,
      receivedAt: _clock().toUtc(),
    );
  }

  /// Polls [checkPending] every [interval] until an attempt arrives or [timeout] elapses
  /// (returns `null`). Transient [EzkeyNetworkException]s are retried; other errors propagate.
  ///
  /// The reference mobile app polls only on user action ("Check now") or while a dedicated screen
  /// is open; do not poll in the background indefinitely (battery, rate limits).
  Future<PendingAuthAttempt?> pollPending(
    EzkeyEnrollment enrollment, {
    Duration interval = const Duration(seconds: 2),
    Duration timeout = const Duration(seconds: 60),
  }) async {
    final deadline = _clock().add(timeout);
    while (true) {
      try {
        final attempt = await checkPending(enrollment);
        if (attempt != null) {
          return attempt;
        }
      } on EzkeyNetworkException {
        // Retry until the deadline.
      }
      final now = _clock();
      if (!now.isBefore(deadline)) {
        return null;
      }
      final remaining = deadline.difference(now);
      await Future<void>.delayed(remaining < interval ? remaining : interval);
    }
  }

  /// Stream of claimed attempts, polling every [interval] while listened to. Cancel the
  /// subscription to stop polling. Network errors are skipped; other errors end the stream.
  Stream<PendingAuthAttempt> watchPending(
    EzkeyEnrollment enrollment, {
    Duration interval = const Duration(seconds: 2),
  }) async* {
    while (true) {
      PendingAuthAttempt? attempt;
      try {
        attempt = await checkPending(enrollment);
      } on EzkeyNetworkException {
        attempt = null;
      }
      if (attempt != null) {
        yield attempt;
      }
      await Future<void>.delayed(interval);
    }
  }

  /// Approves [attempt]. When [PendingAuthAttempt.challengeRequired] is `true`,
  /// [challengeResponse] (the code the integrating application displays) is mandatory.
  ///
  /// Returns the signed server outcome: [AuthAttemptResult.approved], or
  /// [AuthAttemptResult.failed] when the code was wrong (the attempt is then invalid). Throws
  /// [EzkeyApiException] with [EzkeyApiException.isAttemptNoLongerActionable] when the attempt was
  /// cancelled, superseded, already answered or expired.
  Future<AuthAttemptOutcome> approve(
    EzkeyEnrollment enrollment,
    PendingAuthAttempt attempt, {
    int? challengeResponse,
  }) async {
    if (attempt.challengeRequired && challengeResponse == null) {
      throw const EzkeyValidationException(
        'This attempt requires the challenge code',
      );
    }
    return _respond(
      enrollment,
      attempt,
      accepted: true,
      challengeResponse: challengeResponse,
    );
  }

  /// Denies [attempt]. No challenge is needed to deny.
  Future<AuthAttemptOutcome> deny(
    EzkeyEnrollment enrollment,
    PendingAuthAttempt attempt,
  ) {
    return _respond(enrollment, attempt, accepted: false);
  }

  Future<AuthAttemptOutcome> _respond(
    EzkeyEnrollment enrollment,
    PendingAuthAttempt attempt, {
    required bool accepted,
    int? challengeResponse,
  }) async {
    if (attempt.enrollmentLocalId != enrollment.localId) {
      throw const EzkeyValidationException(
        'Attempt belongs to another enrollment',
      );
    }
    final signed = await keyStore.sign(
      enrollment.deviceKeyAlias,
      buildRespondPayload(attempt.authAttemptProofToken, accepted),
    );
    final response = await _api(enrollment.authApiBaseUrl).respond(
      RespondAuthRequest(
        authAttemptId: attempt.authAttemptId,
        authAttemptAccepted: accepted,
        authAttemptProofTokenSignedByDevice: signed,
        authAttemptChallengeResponse: accepted ? challengeResponse : null,
      ),
    );

    final signature = response.authAttemptProofTokenResultSignedByIntegration;
    if (signature == null) {
      throw const EzkeyIntegrityException(
        EzkeyIntegrityCheck.respondSignature,
        'Respond answer is not signed by the integration',
      );
    }
    final payload = buildRespondResultPayload(
      attempt.authAttemptProofToken,
      '${attempt.authAttemptId}',
      response.authAttemptResult,
      response.authAttemptMessage,
    );
    if (!await verifyEd25519(
      payload,
      signature,
      enrollment.integrationPublicKey,
    )) {
      throw const EzkeyIntegrityException(
        EzkeyIntegrityCheck.respondSignature,
        'Respond answer integration signature is invalid',
      );
    }
    final result = AuthAttemptResult.fromWire(response.authAttemptResult);
    if (response.authAttemptId != attempt.authAttemptId || result == null) {
      throw const EzkeyIntegrityException(
        EzkeyIntegrityCheck.respondConsistency,
        'Respond answer does not match the attempt',
      );
    }
    if (!accepted && result == AuthAttemptResult.approved) {
      throw const EzkeyIntegrityException(
        EzkeyIntegrityCheck.respondConsistency,
        'Server reported APPROVED for a denial',
      );
    }
    return AuthAttemptOutcome(
      authAttemptId: attempt.authAttemptId,
      accepted: accepted,
      result: result,
      message: response.authAttemptMessage,
    );
  }

  EzkeyAuthApi _api(String baseUrl) =>
      _apis.putIfAbsent(baseUrl, () => _apiFactory(Uri.parse(baseUrl)));

  Future<void> _deleteKeyQuietly(String alias) async {
    try {
      await keyStore.deleteKeyPair(alias);
    } on Object {
      // Best effort: a leftover key without an enrollment record is harmless.
    }
  }
}

String _defaultKeyAlias(int enrollmentId) =>
    'ezkey-device-$enrollmentId-${base64UrlEncodeBytes(generateSecureRandomBytes(9))}';
