import 'package:http/http.dart' as http;

import 'lab_http.dart';

/// Pending passwordless admin login (`POST /api/v1/admin/auth/login`, two-step mode).
class AdminLoginChallenge {
  AdminLoginChallenge({
    required this.authAttemptId,
    required this.challengeCode,
    required this.waiterSecret,
  });

  final int authAttemptId;

  /// Code the Admin UI would display; the authenticator must type it when approving.
  final int? challengeCode;

  /// One-time capability for `/passwordless-wait`. Secret.
  final String waiterSecret;
}

/// Fresh API key (the secret is only returned once). Secret.
class LabApiKey {
  LabApiKey({required this.integrationKey, required this.secretKey});

  final String integrationKey;
  final String secretKey;
}

/// Enrollment invitation created by an administrator.
class LabEnrollmentInvitation {
  LabEnrollmentInvitation({
    required this.enrollmentId,
    required this.enrollmentChallenge,
    this.enrollmentProofToken,
  });

  final int enrollmentId;

  /// The 6-digit code the user types on the device (out-of-band from the QR code).
  final int enrollmentChallenge;

  /// QR secret, fetched separately (`GET /api/v1/enrollments/{id}` or admin onboarding).
  final String? enrollmentProofToken;

  LabEnrollmentInvitation withProofToken(String token) =>
      LabEnrollmentInvitation(
        enrollmentId: enrollmentId,
        enrollmentChallenge: enrollmentChallenge,
        enrollmentProofToken: token,
      );
}

/// Admin API calls needed to provision an end-to-end protocol test. Lab tooling only: a Flutter
/// authenticator never talks to the Admin API.
class AdminLabClient {
  AdminLabClient(Uri baseUri, {http.Client? client})
    : _http = LabHttp(baseUri, client: client);

  final LabHttp _http;
  String? _bearer;

  bool get isAuthenticated => _bearer != null;

  Map<String, String> get _auth {
    final token = _bearer;
    if (token == null) {
      throw StateError('Admin API: call completeLogin first');
    }
    return <String, String>{'Authorization': 'Bearer $token'};
  }

  Future<AdminLoginChallenge> startLogin(String username) async {
    final json = asJsonObject(
      await _http.post('/api/v1/admin/auth/login', <String, Object?>{
        'username': username,
        'challengeRequested': true,
      }),
      'admin login',
    );
    if (json['status'] != 'pending' || json['authAttemptId'] is! int) {
      throw const FormatException(
        'admin login: expected a pending two-step answer',
      );
    }
    return AdminLoginChallenge(
      authAttemptId: json['authAttemptId'] as int,
      challengeCode: json['challengeCode'] as int?,
      waiterSecret: json['waiterSecret'] as String,
    );
  }

  /// Waits for the device decision and stores the bearer token on success.
  Future<void> completeLogin(AdminLoginChallenge challenge) async {
    final json = asJsonObject(
      await _http
          .post('/api/v1/admin/auth/passwordless-wait', <String, Object?>{
            'authAttemptId': challenge.authAttemptId,
            'challengeCode': challenge.challengeCode,
            'waiterSecret': challenge.waiterSecret,
          }, timeout: const Duration(seconds: 90)),
      'admin passwordless-wait',
    );
    final token = json['token'];
    if (json['success'] != true || token is! String || token.isEmpty) {
      throw const FormatException(
        'admin passwordless-wait: login was not accepted',
      );
    }
    _bearer = token;
  }

  Future<int> createIntegration({
    required String code,
    required String name,
    String? description,
  }) async {
    final json = asJsonObject(
      await _http.post('/api/v1/integrations', <String, Object?>{
        'code': code,
        'name': name,
        if (description != null) 'description': description,
      }, headers: _auth),
      'create integration',
    );
    return json['id'] as int;
  }

  Future<LabApiKey> createApiKey(
    int integrationId, {
    String? description,
  }) async {
    final json = asJsonObject(
      await _http.post('/api/v1/api-keys', <String, Object?>{
        'integrationId': integrationId,
        if (description != null) 'description': description,
      }, headers: _auth),
      'create api key',
    );
    return LabApiKey(
      integrationKey: json['integrationKey'] as String,
      secretKey: json['secretKey'] as String,
    );
  }

  /// Creates an enrollment and fetches its proof token (the QR secret).
  Future<LabEnrollmentInvitation> createEnrollment({
    required int integrationId,
    required String name,
    bool authAttemptChallengeRequired = false,
    String? userIdentifier,
  }) async {
    final json = asJsonObject(
      await _http.post('/api/v1/enrollments', <String, Object?>{
        'integrationId': integrationId,
        'name': name,
        'authAttemptChallengeRequired': authAttemptChallengeRequired,
        if (userIdentifier != null) 'userIdentifier': userIdentifier,
      }, headers: _auth),
      'create enrollment',
    );
    final invitation = LabEnrollmentInvitation(
      enrollmentId: json['enrollmentId'] as int,
      enrollmentChallenge: json['enrollmentChallenge'] as int,
    );
    final details = await getEnrollment(invitation.enrollmentId);
    return invitation.withProofToken(details['enrollmentProofToken'] as String);
  }

  Future<Map<String, dynamic>> getEnrollment(int enrollmentId) async {
    return asJsonObject(
      await _http.get('/api/v1/enrollments/$enrollmentId', headers: _auth),
      'get enrollment',
    );
  }

  Future<List<Map<String, dynamic>>> listEnrollments({
    int size = 20,
    String? status,
  }) async {
    final json = asJsonObject(
      await _http.get(
        '/api/v1/enrollments',
        query: <String, String>{
          'page': '0',
          'size': '$size',
          'sort': 'createdAt,desc',
          if (status != null) 'status': status,
        },
        headers: _auth,
      ),
      'list enrollments',
    );
    final embedded = json['_embedded'];
    final content =
        json['content'] ??
        (embedded is Map && embedded.isNotEmpty ? embedded.values.first : null);
    return (content as List<dynamic>? ?? const <dynamic>[])
        .cast<Map<String, dynamic>>();
  }

  Future<int> createTenant(String tenantName) async {
    final json = asJsonObject(
      await _http.post('/api/v1/tenants', <String, Object?>{
        'tenantName': tenantName,
      }, headers: _auth),
      'create tenant',
    );
    return json['tenantId'] as int;
  }

  /// Creates a tenant administrator with immediate onboarding and returns the admin MFA
  /// enrollment invitation (system integration, `adminType = TENANT_ADMIN`).
  Future<LabEnrollmentInvitation> createTenantAdminInvitation({
    required int tenantId,
    required String username,
  }) async {
    final created = asJsonObject(
      await _http.post('/api/v1/admins/tenant', <String, Object?>{
        'username': username,
        'tenantId': tenantId,
        'firstName': 'Dart',
        'lastName': 'Lab',
        'onboardingMode': 'IMMEDIATE',
      }, headers: _auth),
      'create tenant admin',
    );
    final adminId = (created['adminId'] ?? created['id']) as int;
    final onboarding = asJsonObject(
      await _http.get('/api/v1/admins/$adminId/onboarding', headers: _auth),
      'admin onboarding',
    );
    return LabEnrollmentInvitation(
      enrollmentId: onboarding['enrollmentId'] as int,
      enrollmentChallenge: onboarding['enrollmentChallenge'] as int,
      enrollmentProofToken: onboarding['enrollmentProofToken'] as String,
    );
  }

  void close() => _http.close();
}
