import 'dart:convert';

import 'package:ezkey_dart/ezkey_dart.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:pointycastle/export.dart' as pc;

const String fakeAuthApiBaseUrl = 'https://auth.example.test';

/// Invitation / enrollment row of [FakeEzkeyServer].
class FakeEnrollment {
  FakeEnrollment({
    required this.id,
    required this.proofToken,
    required this.challenge,
    required this.authAttemptChallengeRequired,
    required this.isSystemIntegration,
    this.adminType,
    this.name,
  });

  final int id;
  final String proofToken;
  final int challenge;
  final bool authAttemptChallengeRequired;
  final bool isSystemIntegration;
  final String? adminType;
  final String? name;

  /// PENDING (invitation), BOUND, VERIFIED, INVALID.
  String status = 'PENDING';
  String? devicePublicKey;
  String? storageTier;
}

/// Auth attempt row of [FakeEzkeyServer].
class FakeAttempt {
  FakeAttempt({
    required this.id,
    required this.enrollmentId,
    required this.proofToken,
    required this.challengeRequired,
    required this.challenge,
    required this.expiresAt,
    this.title,
    this.message,
  });

  final int id;
  final int enrollmentId;
  final String proofToken;
  final bool challengeRequired;

  /// What the integrating application displays.
  final int challenge;
  final DateTime expiresAt;
  final String? title;
  final String? message;

  /// PENDING, READ, ACCEPTED, REJECTED, INVALID (wrong code / superseded), CANCELLED.
  String state = 'PENDING';
}

/// In-memory Auth API with real crypto, mirroring the server state machine (one-shot claim,
/// supersede, cancel, TTL, challenge validation on approve only, device proof token replay).
///
/// Tamper hooks let tests prove the SDK fails closed on every signed answer.
class FakeEzkeyServer {
  FakeEzkeyServer._(this._integrationKeys);

  static Future<FakeEzkeyServer> create() async =>
      FakeEzkeyServer._(await generateEd25519TestKeyPair());

  final Ed25519TestKeyPair _integrationKeys;
  final Map<int, FakeEnrollment> enrollments = <int, FakeEnrollment>{};
  final Map<int, FakeAttempt> attempts = <int, FakeAttempt>{};
  final Set<String> _claimedDeviceProofTokens = <String>{};
  final List<String> requestPaths = <String>[];
  final List<Map<String, dynamic>> requestBodies = <Map<String, dynamic>>[];
  int _nextEnrollmentId = 100;
  int _nextAttemptId = 5000;

  /// Server clock (TTL checks).
  DateTime now = DateTime.utc(2026, 1, 1, 12);

  String get integrationPublicKey => _integrationKeys.publicKeyBase64Url;

  /// Algorithm label returned by bind.
  String integrationKeyAlgorithm = 'ed25519';

  /// Changes the JSON answer of [path] after signing (simulates MITM / contract drift).
  Map<String, dynamic> Function(String path, Map<String, dynamic> body)?
  mutateResponse;

  /// Forces an RFC 9457 answer for [path].
  ({String path, int status, String type})? forcedProblem;

  /// Throws a transport error for [path].
  String? failTransportOn;

  String integrationName = 'ACME Portal';
  String tenantName = 'Acme';

  /// NFD on purpose: the signature covers its NFC form.
  String tenantDescription = 'Cafe\u0301 & co';

  http.Client get client => MockClient(handle);

  FakeEnrollment invite({
    bool authAttemptChallengeRequired = false,
    bool isSystemIntegration = false,
    String? adminType,
    String? name = 'Marie',
  }) {
    final id = _nextEnrollmentId++;
    final enrollment = FakeEnrollment(
      id: id,
      proofToken: generateProofToken(),
      challenge: 100000 + id,
      authAttemptChallengeRequired: authAttemptChallengeRequired,
      isSystemIntegration: isSystemIntegration,
      adminType: adminType,
      name: name,
    );
    enrollments[id] = enrollment;
    return enrollment;
  }

  /// JSON QR code content for [enrollment].
  String qrCode(
    FakeEnrollment enrollment, {
    String? authUrl = fakeAuthApiBaseUrl,
  }) => jsonEncode(<String, Object?>{
    'enrollmentId': enrollment.id,
    'enrollmentProofToken': enrollment.proofToken,
    if (authUrl != null) 'authUrl': authUrl,
  });

  /// Integration API "create auth attempt". Supersedes older open attempts of the enrollment.
  FakeAttempt createAttempt(
    int enrollmentId, {
    bool challengeRequested = false,
    String? title,
    String? message,
    Duration ttl = const Duration(seconds: 120),
  }) {
    for (final open in attempts.values.where(
      (a) =>
          a.enrollmentId == enrollmentId &&
          (a.state == 'PENDING' || a.state == 'READ'),
    )) {
      open.state = 'INVALID';
    }
    final id = _nextAttemptId++;
    final attempt = FakeAttempt(
      id: id,
      enrollmentId: enrollmentId,
      proofToken: generateProofToken(),
      challengeRequired:
          challengeRequested ||
          enrollments[enrollmentId]!.authAttemptChallengeRequired,
      challenge: 200000 + id,
      expiresAt: now.add(ttl),
      title: title,
      message: message,
    );
    attempts[id] = attempt;
    return attempt;
  }

  void cancel(int attemptId) => attempts[attemptId]!.state = 'CANCELLED';

  int countRequests(String path) => requestPaths.where((p) => p == path).length;

  /// Request handler behind [client]; lets tests wrap the fake in their own [MockClient].
  Future<http.Response> handle(http.Request request) async {
    final path = request.url.path;
    requestPaths.add(path);
    if (failTransportOn == path) {
      throw http.ClientException('connection reset', request.url);
    }
    final body = jsonDecode(request.body) as Map<String, dynamic>;
    requestBodies.add(body);
    final forced = forcedProblem;
    if (forced != null && forced.path == path) {
      return _problem(forced.status, forced.type);
    }
    switch (path) {
      case AuthApiPaths.bind:
        return _bind(body);
      case AuthApiPaths.verify:
        return _verify(body);
      case AuthApiPaths.instanceInfo:
        return _instanceInfo(body);
      case AuthApiPaths.pending:
        return _pending(body);
      case AuthApiPaths.respond:
        return _respond(body);
    }
    return _problem(404, 'https://ezkey.io/problems/auth/resource-not-found');
  }

  Future<http.Response> _bind(Map<String, dynamic> body) async {
    final enrollment = enrollments[body['enrollmentId']];
    if (enrollment == null ||
        enrollment.proofToken != body['enrollmentProofToken']) {
      return _problem(
        400,
        'https://ezkey.io/problems/auth/enrollment-binding-failed',
      );
    }
    if (enrollment.status != 'PENDING') {
      return _problem(
        409,
        'https://ezkey.io/problems/auth/enrollment-already-bound',
      );
    }
    enrollment.status = 'BOUND';
    final response = EnrollmentBindResponse(
      enrollmentId: enrollment.id,
      enrollmentProofToken: enrollment.proofToken,
      integrationPublicKey: integrationPublicKey,
      integrationKeyAlgorithm: integrationKeyAlgorithm,
      isSystemIntegration: enrollment.isSystemIntegration,
      integrationName:
          enrollment.isSystemIntegration ? 'Ezkey System' : integrationName,
      enrollmentName: enrollment.name,
      tenantId: 2,
      tenantName: tenantName,
      tenantDescription: tenantDescription,
      adminType: enrollment.adminType,
      enrollmentBindPayloadSignedByIntegration: await _sign(
        buildEnrollmentBindPayload(
          enrollmentProofToken: enrollment.proofToken,
          enrollmentId: enrollment.id,
          integrationPublicKey: integrationPublicKey,
          integrationKeyAlgorithm: integrationKeyAlgorithm,
          integrationName:
              enrollment.isSystemIntegration ? 'Ezkey System' : integrationName,
          enrollmentName: enrollment.name,
          tenantId: 2,
          tenantName: tenantName,
          tenantDescription: tenantDescription,
          isSystemIntegration: enrollment.isSystemIntegration,
          adminType: enrollment.adminType,
        ),
      ),
    );
    return _json(AuthApiPaths.bind, response.toJson());
  }

  Future<http.Response> _verify(Map<String, dynamic> json) async {
    final request = EnrollmentVerifyRequest.fromJson(json);
    final enrollment = enrollments[request.enrollmentId];
    if (enrollment == null || enrollment.status != 'BOUND') {
      return _problem(
        409,
        'https://ezkey.io/problems/auth/enrollment-state-conflict',
      );
    }
    final payload = buildEnrollmentVerifyDevicePayload(
      enrollment.proofToken,
      enrollment.id,
      request.challengeResponse,
      request.devicePublicKey,
    );
    if (!_verifyDeviceSignature(
          ecPublicKeyFromSpkiBase64(request.devicePublicKey),
          payload,
          request.enrollmentProofTokenSigned,
        ) ||
        request.challengeResponse != enrollment.challenge) {
      enrollment.status = 'INVALID';
      return _problem(
        400,
        'https://ezkey.io/problems/auth/enrollment-verify-failed',
      );
    }
    enrollment
      ..status = 'VERIFIED'
      ..devicePublicKey = request.devicePublicKey
      ..storageTier = request.devicePrivateKeyStorageTier;
    const message = 'Enrollment verified successfully';
    return _json(
      AuthApiPaths.verify,
      EnrollmentVerifyResponse(
        active: true,
        enrollmentVerifyMessage: message,
        enrollmentVerifyPayloadSignedByIntegration: await _sign(
          buildEnrollmentVerifyResultPayload(
            enrollment.proofToken,
            enrollment.id,
            enrollmentVerifiedOutcome,
            message,
          ),
        ),
      ).toJson(),
    );
  }

  Future<http.Response> _instanceInfo(Map<String, dynamic> body) async {
    final enrollment =
        enrollments.values
            .where(
              (e) =>
                  e.proofToken == body['enrollmentProofToken'] &&
                  e.status == 'VERIFIED',
            )
            .firstOrNull;
    if (enrollment == null) {
      return _problem(
        400,
        'https://ezkey.io/problems/auth/enrollment-instance-info-failed',
      );
    }
    const name = 'Ezkey Lab';
    const description = 'Instance de de\u0301monstration';
    const about = 'https://example.test/about';
    return _json(
      AuthApiPaths.instanceInfo,
      InstanceInfoResponse(
        enrollmentId: enrollment.id,
        authApiPublicBaseUrl: fakeAuthApiBaseUrl,
        instanceName: name,
        instanceDescription: description,
        aboutUrl: about,
        instanceInfoPayloadSignedByIntegration: await _sign(
          buildEnrollmentInstanceInfoPayload(
            enrollmentProofToken: enrollment.proofToken,
            enrollmentId: enrollment.id,
            authApiPublicBaseUrl: fakeAuthApiBaseUrl,
            instanceName: name,
            instanceDescription: description,
            aboutUrl: about,
          ),
        ),
      ).toJson(),
    );
  }

  Future<http.Response> _pending(Map<String, dynamic> json) async {
    final request = PendingAuthRequest.fromJson(json);
    final enrollment = enrollments[request.enrollmentId];
    const failed = 'https://ezkey.io/problems/auth/auth-attempt-binding-failed';
    if (enrollment == null ||
        enrollment.status != 'VERIFIED' ||
        enrollment.proofToken != request.enrollmentProofToken) {
      return _problem(400, failed);
    }
    if (!_verifyDeviceSignature(
      ecPublicKeyFromSpkiBase64(enrollment.devicePublicKey!),
      request.deviceProofToken,
      request.deviceProofTokenSigned,
    )) {
      return _problem(400, failed);
    }
    if (_claimedDeviceProofTokens.contains(request.deviceProofToken)) {
      return _problem(400, failed);
    }
    final candidates =
        attempts.values
            .where(
              (a) =>
                  a.enrollmentId == enrollment.id &&
                  a.state == 'PENDING' &&
                  a.expiresAt.isAfter(now),
            )
            .toList()
          ..sort((a, b) => b.id.compareTo(a.id));
    if (candidates.isEmpty) {
      return http.Response('', 204);
    }
    final attempt = candidates.first..state = 'READ';
    _claimedDeviceProofTokens.add(request.deviceProofToken);
    return _json(
      AuthApiPaths.pending,
      PendingAuthResponse(
        authAttemptId: attempt.id,
        authAttemptProofToken: attempt.proofToken,
        authAttemptChallengeRequired: attempt.challengeRequired,
        contextTitle: attempt.title,
        contextMessage: attempt.message,
        authAttemptProofTokenSignedByIntegration: await _sign(
          buildPendingPayload(
            attempt.proofToken,
            attempt.challengeRequired,
            attempt.title,
            attempt.message,
          ),
        ),
      ).toJson(),
    );
  }

  Future<http.Response> _respond(Map<String, dynamic> json) async {
    final request = RespondAuthRequest.fromJson(json);
    final attempt = attempts[request.authAttemptId];
    const conflict =
        'https://ezkey.io/problems/auth/auth-attempt-state-conflict';
    if (attempt == null || attempt.state != 'READ') {
      return _problem(409, conflict);
    }
    final enrollment = enrollments[attempt.enrollmentId]!;
    if (!_verifyDeviceSignature(
      ecPublicKeyFromSpkiBase64(enrollment.devicePublicKey!),
      buildRespondPayload(attempt.proofToken, request.authAttemptAccepted),
      request.authAttemptProofTokenSignedByDevice,
    )) {
      return _problem(
        400,
        'https://ezkey.io/problems/auth/auth-attempt-binding-failed',
      );
    }
    if (!attempt.expiresAt.isAfter(now)) {
      return _problem(
        409,
        'https://ezkey.io/problems/auth/auth-attempt-expired',
      );
    }
    String result;
    String message;
    if (!request.authAttemptAccepted) {
      attempt.state = 'REJECTED';
      result = 'DENIED';
      message = 'Authentication denied';
    } else if (attempt.challengeRequired &&
        request.authAttemptChallengeResponse != attempt.challenge) {
      attempt.state = 'INVALID';
      result = 'FAILED';
      message = 'Invalid challenge response';
    } else {
      attempt.state = 'ACCEPTED';
      result = 'APPROVED';
      message = 'Authentication approved';
    }
    return _json(
      AuthApiPaths.respond,
      RespondAuthResponse(
        authAttemptId: attempt.id,
        authAttemptResult: result,
        authAttemptMessage: message,
        authAttemptProofTokenResultSignedByIntegration: await _sign(
          buildRespondResultPayload(
            attempt.proofToken,
            '${attempt.id}',
            result,
            message,
          ),
        ),
      ).toJson(),
    );
  }

  Future<String> _sign(String payload) =>
      signEd25519WithPkcs8(payload, _integrationKeys.pkcs8Base64Std);

  http.Response _json(String path, Map<String, Object?> body) {
    final mutate = mutateResponse;
    final out =
        mutate == null ? body : mutate(path, Map<String, dynamic>.of(body));
    return http.Response(
      jsonEncode(out),
      200,
      headers: <String, String>{
        'content-type': 'application/json; charset=utf-8',
      },
    );
  }

  http.Response _problem(int status, String type) => http.Response(
    jsonEncode(<String, Object?>{
      'type': type,
      'title': 'Problem',
      'status': status,
      'detail': 'Server detail that must not be shown to end users',
    }),
    status,
    headers: <String, String>{'content-type': 'application/problem+json'},
  );

  /// Device ECDSA check as the Auth API does it: valid signature **and** low-S (SEC-012).
  bool _verifyDeviceSignature(
    pc.ECPublicKey key,
    String data,
    String signatureBase64Std,
  ) =>
      isLowSEcdsaDerSignature(signatureBase64Std) &&
      verifyEcdsaSha256(key, data, signatureBase64Std);
}
