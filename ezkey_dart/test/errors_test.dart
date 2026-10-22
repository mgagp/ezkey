import 'package:ezkey_dart/ezkey_dart.dart';
import 'package:test/test.dart';

void main() {
  group('EzkeyProblem.tryParse', () {
    test('parses RFC 9457 documents', () {
      final problem =
          EzkeyProblem.tryParse(
            '{"type":" https://ezkey.io/problems/auth/auth-attempt-expired ","title":"Expired",'
            '"status":409,"detail":"d","instance":"/api/v1/auth-attempts/respond"}',
          )!;
      expect(
        problem.type,
        'https://ezkey.io/problems/auth/auth-attempt-expired',
      );
      expect(problem.title, 'Expired');
      expect(problem.status, 409);
      expect(problem.detail, 'd');
      expect(problem.instance, '/api/v1/auth-attempts/respond');
    });

    test('returns null for non-problem bodies', () {
      for (final body in <String>[
        '',
        '  ',
        '<html>502</html>',
        '[]',
        '{"error":"x"}',
        '42',
      ]) {
        expect(EzkeyProblem.tryParse(body), isNull, reason: body);
      }
    });
  });

  group('classification', () {
    const base = 'https://ezkey.io/problems/auth/';
    final cases = <String, EzkeyProblemKind>{
      '${base}auth-attempt-state-conflict':
          EzkeyProblemKind.authAttemptStateConflict,
      '${base}auth-attempt-expired': EzkeyProblemKind.authAttemptExpired,
      '${base}integration-signing-unavailable':
          EzkeyProblemKind.integrationSigningUnavailable,
      '${base}auth-attempt-binding-failed':
          EzkeyProblemKind.authAttemptBindingFailed,
      '${base}auth-attempt-respond-failed':
          EzkeyProblemKind.authAttemptRespondFailed,
      '${base}enrollment-already-bound':
          EzkeyProblemKind.enrollmentAlreadyBound,
      '${base}enrollment-binding-failed':
          EzkeyProblemKind.enrollmentBindingFailed,
      '${base}enrollment-invitation-expired':
          EzkeyProblemKind.enrollmentInvitationExpired,
      '${base}enrollment-not-available':
          EzkeyProblemKind.enrollmentNotAvailable,
      '${base}enrollment-integration-not-found':
          EzkeyProblemKind.enrollmentIntegrationNotFound,
      '${base}enrollment-verify-failed':
          EzkeyProblemKind.enrollmentVerifyFailed,
      '${base}enrollment-state-conflict':
          EzkeyProblemKind.enrollmentStateConflict,
      '${base}enrollment-instance-info-failed':
          EzkeyProblemKind.enrollmentInstanceInfoFailed,
      '${base}validation-failed': EzkeyProblemKind.validationFailed,
      '${base}invalid-request-body': EzkeyProblemKind.validationFailed,
      '${base}internal-error': EzkeyProblemKind.internalError,
      'https://ezkey.io/problems/system/audit-chain-heartbeat-degraded':
          EzkeyProblemKind.auditChainDegraded,
    };

    test('maps every Auth API catalog type', () {
      cases.forEach(
        (type, kind) => expect(classifyProblem(400, type), kind, reason: type),
      );
    });

    test('falls back on status', () {
      expect(classifyProblem(429, null), EzkeyProblemKind.rateLimited);
      expect(classifyProblem(503, null), EzkeyProblemKind.unknown);
      expect(
        classifyProblem(409, 'https://example.org/other'),
        EzkeyProblemKind.unknown,
      );
    });
  });

  EzkeyApiException apiError(int status, String? type) => EzkeyApiException(
    method: 'POST',
    path: '/api/v1/auth-attempts/respond',
    statusCode: status,
    problem:
        type == null ? null : EzkeyProblem(type: type, detail: 'server detail'),
  );

  test('retryable and final flags', () {
    const base = 'https://ezkey.io/problems/auth/';
    expect(
      apiError(
        409,
        '${base}auth-attempt-state-conflict',
      ).isAttemptNoLongerActionable,
      isTrue,
    );
    expect(
      apiError(409, '${base}auth-attempt-expired').isAttemptNoLongerActionable,
      isTrue,
    );
    expect(apiError(409, '${base}auth-attempt-expired').retryable, isFalse);
    expect(
      apiError(503, '${base}integration-signing-unavailable').retryable,
      isTrue,
    );
    expect(apiError(429, null).retryable, isTrue);
    expect(apiError(502, null).retryable, isTrue);
    expect(apiError(400, null).retryable, isFalse);
    expect(apiError(400, '${base}enrollment-verify-failed').retryable, isFalse);
    expect(const EzkeyNetworkException('timeout').retryable, isTrue);
    expect(
      const EzkeyIntegrityException(
        EzkeyIntegrityCheck.pendingSignature,
        'x',
      ).retryable,
      isFalse,
    );
  });

  test('messages carry method, path, status and type but never the detail', () {
    final error = apiError(
      409,
      'https://ezkey.io/problems/auth/auth-attempt-expired',
    );
    expect('$error', contains('POST /api/v1/auth-attempts/respond'));
    expect('$error', contains('409'));
    expect('$error', contains('auth-attempt-expired'));
    expect('$error', isNot(contains('server detail')));
  });

  test('exceptions form a sealed hierarchy', () {
    String describe(EzkeyException e) => switch (e) {
      EzkeyValidationException() => 'validation',
      EzkeyIntegrityException() => 'integrity',
      EzkeyNetworkException() => 'network',
      EzkeyLocalStateException() => 'local',
      EzkeyApiException() => 'api',
    };
    expect(describe(const EzkeyLocalStateException('x')), 'local');
    expect(describe(apiError(500, null)), 'api');
  });
}
