import 'dart:convert';

import 'package:http/http.dart' as http;

import 'lab_http.dart';

/// Auth attempt as created by the integrating application.
class LabAuthAttempt {
  LabAuthAttempt({
    required this.authAttemptId,
    required this.status,
    this.authAttemptChallenge,
    this.expiresAt,
  });

  final int authAttemptId;
  final String status;

  /// Code the integrating application displays to the user (when a challenge is required).
  final int? authAttemptChallenge;
  final DateTime? expiresAt;

  factory LabAuthAttempt.fromJson(Map<String, dynamic> json) => LabAuthAttempt(
    authAttemptId: json['authAttemptId'] as int,
    status: json['authAttemptStatus'] as String? ?? 'UNKNOWN',
    authAttemptChallenge: json['authAttemptChallenge'] as int?,
    expiresAt:
        json['expiresAt'] == null
            ? null
            : DateTime.parse(json['expiresAt'] as String),
  );
}

/// `GET /api/v1/auth-attempts/{id}/wait` result.
class LabWaitResult {
  LabWaitResult({
    required this.status,
    required this.completed,
    required this.timeoutReached,
  });

  /// PENDING, READ, INVALID, REJECTED, ACCEPTED or EXPIRED.
  final String status;
  final bool completed;
  final bool timeoutReached;
}

/// Integration API (HTTP Basic `integrationKey:secretKey`) — the relying-party side that creates
/// auth attempts. Lab tooling: a Flutter authenticator never calls it.
class IntegrationLabClient {
  IntegrationLabClient(
    Uri baseUri, {
    required String integrationKey,
    required String secretKey,
    http.Client? client,
  }) : _http = LabHttp(
         baseUri,
         client: client,
         headers: <String, String>{
           'Authorization':
               'Basic ${base64.encode(utf8.encode('$integrationKey:$secretKey'))}',
         },
       );

  final LabHttp _http;

  Future<LabAuthAttempt> createAuthAttempt({
    int? enrollmentId,
    String? userIdentifier,
    bool challengeRequested = false,
    String? contextTitle,
    String? contextMessage,
    int? ttlSeconds,
  }) async {
    final json = asJsonObject(
      await _http.post('/api/v1/auth-attempts', <String, Object?>{
        if (enrollmentId != null) 'enrollmentId': enrollmentId,
        if (userIdentifier != null) 'userIdentifier': userIdentifier,
        'challengeRequested': challengeRequested,
        if (contextTitle != null) 'contextTitle': contextTitle,
        if (contextMessage != null) 'contextMessage': contextMessage,
        if (ttlSeconds != null) 'ttlSeconds': ttlSeconds,
      }),
      'create auth attempt',
    );
    return LabAuthAttempt.fromJson(json);
  }

  Future<LabWaitResult> waitForAuthAttempt(
    int authAttemptId, {
    int timeoutSeconds = 10,
    int pollingSeconds = 1,
  }) async {
    final json = asJsonObject(
      await _http.get(
        '/api/v1/auth-attempts/$authAttemptId/wait',
        query: <String, String>{
          'timeout': '$timeoutSeconds',
          'polling': '$pollingSeconds',
        },
        timeout: Duration(seconds: timeoutSeconds + 15),
      ),
      'wait auth attempt',
    );
    return LabWaitResult(
      status: json['status'] as String,
      completed: json['completed'] == true,
      timeoutReached: json['timeoutReached'] == true,
    );
  }

  Future<LabAuthAttempt> cancelAuthAttempt(int authAttemptId) async {
    final json = asJsonObject(
      await _http.post('/api/v1/auth-attempts/$authAttemptId/cancel', null),
      'cancel auth attempt',
    );
    return LabAuthAttempt.fromJson(json);
  }

  void close() => _http.close();
}
