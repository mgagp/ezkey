import 'dart:async';
import 'dart:convert';

import 'package:ezkey_dart/ezkey_dart.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:test/test.dart';

PendingAuthRequest _pendingRequest() => PendingAuthRequest(
  enrollmentId: 1,
  enrollmentProofToken: 'enrollment-secret',
  deviceProofToken: 'device-secret',
  deviceProofTokenSigned: 'sig',
);

void main() {
  test(
    'posts JSON to the documented path, honoring a base path prefix',
    () async {
      late http.Request seen;
      final client = AuthApiClient(
        Uri.parse('https://auth.example.org/ezkey/'),
        client: MockClient((request) async {
          seen = request;
          return http.Response('', 204);
        }),
      );
      expect(await client.pending(_pendingRequest()), isNull);
      expect(seen.method, 'POST');
      expect(
        seen.url.toString(),
        'https://auth.example.org/ezkey/api/v1/auth-attempts/pending',
      );
      expect(seen.headers['Content-Type'], startsWith('application/json'));
      expect(jsonDecode(seen.body), <String, Object?>{
        'enrollmentId': 1,
        'enrollmentProofToken': 'enrollment-secret',
        'deviceProofToken': 'device-secret',
        'deviceProofTokenSigned': 'sig',
      });
    },
  );

  test(
    'verify sends challengeResponse as an int and the storage tier',
    () async {
      late Map<String, dynamic> body;
      final client = AuthApiClient(
        Uri.parse('https://a.example'),
        client: MockClient((request) async {
          body = jsonDecode(request.body) as Map<String, dynamic>;
          return http.Response(
            jsonEncode(<String, Object?>{
              'active': true,
              'enrollmentVerifyMessage': 'ok',
              'enrollmentVerifyPayloadSignedByIntegration': 's',
            }),
            200,
          );
        }),
      );
      final response = await client.verify(
        EnrollmentVerifyRequest(
          enrollmentId: 3,
          challengeResponse: 123456,
          devicePublicKey: 'spki',
          enrollmentProofTokenSigned: 'sig',
          devicePrivateKeyStorageTier: 'STRONG',
        ),
      );
      expect(response.active, isTrue);
      expect(body['challengeResponse'], 123456);
      expect(body['devicePrivateKeyStorageTier'], 'STRONG');
    },
  );

  test(
    'problem answers become EzkeyApiException without leaking the body',
    () async {
      final client = AuthApiClient(
        Uri.parse('https://a.example'),
        client: MockClient(
          (_) async => http.Response(
            jsonEncode(<String, Object?>{
              'type':
                  'https://ezkey.io/problems/auth/auth-attempt-state-conflict',
              'title': 'Conflict',
              'status': 409,
              'detail': 'echo enrollment-secret',
            }),
            409,
            headers: <String, String>{
              'content-type': 'application/problem+json',
            },
          ),
        ),
      );
      try {
        await client.pending(_pendingRequest());
        fail('expected an exception');
      } on EzkeyApiException catch (error) {
        expect(error.statusCode, 409);
        expect(error.kind, EzkeyProblemKind.authAttemptStateConflict);
        expect(error.problem!.detail, 'echo enrollment-secret');
        expect('$error', isNot(contains('enrollment-secret')));
      }
    },
  );

  test('proxy error pages map to a retryable unknown problem', () async {
    final client = AuthApiClient(
      Uri.parse('https://a.example'),
      client: MockClient(
        (_) async => http.Response('<html>Bad Gateway</html>', 502),
      ),
    );
    await expectLater(
      client.pending(_pendingRequest()),
      throwsA(
        isA<EzkeyApiException>()
            .having((e) => e.kind, 'kind', EzkeyProblemKind.unknown)
            .having((e) => e.retryable, 'retryable', isTrue),
      ),
    );
  });

  test(
    'transport failures and timeouts become EzkeyNetworkException',
    () async {
      final failing = AuthApiClient(
        Uri.parse('https://a.example'),
        client: MockClient((_) async => throw http.ClientException('reset')),
      );
      await expectLater(
        failing.pending(_pendingRequest()),
        throwsA(isA<EzkeyNetworkException>()),
      );

      final slow = AuthApiClient(
        Uri.parse('https://a.example'),
        timeout: const Duration(milliseconds: 20),
        client: MockClient((_) => Completer<http.Response>().future),
      );
      await expectLater(
        slow.pending(_pendingRequest()),
        throwsA(
          isA<EzkeyNetworkException>().having(
            (e) => e.retryable,
            'retryable',
            isTrue,
          ),
        ),
      );
    },
  );

  test('2xx with a broken body is a malformed response', () async {
    for (final response in <http.Response>[
      http.Response('not json', 200),
      http.Response('[1,2]', 200),
      http.Response('{"authAttemptId":"7"}', 200),
      http.Response('', 204),
    ]) {
      final client = AuthApiClient(
        Uri.parse('https://a.example'),
        client: MockClient((_) async => response),
      );
      await expectLater(
        client.respond(
          RespondAuthRequest(
            authAttemptId: 7,
            authAttemptAccepted: true,
            authAttemptProofTokenSignedByDevice: 's',
          ),
        ),
        throwsA(
          isA<EzkeyIntegrityException>().having(
            (e) => e.check,
            'check',
            EzkeyIntegrityCheck.malformedResponse,
          ),
        ),
        reason: response.body,
      );
    }
  });

  test('respond omits the challenge when not provided', () async {
    late Map<String, dynamic> body;
    final client = AuthApiClient(
      Uri.parse('https://a.example'),
      client: MockClient((request) async {
        body = jsonDecode(request.body) as Map<String, dynamic>;
        return http.Response(
          jsonEncode(<String, Object?>{
            'authAttemptId': 7,
            'authAttemptResult': 'DENIED',
            'authAttemptMessage': 'm',
          }),
          200,
        );
      }),
    );
    final response = await client.respond(
      RespondAuthRequest(
        authAttemptId: 7,
        authAttemptAccepted: false,
        authAttemptProofTokenSignedByDevice: 's',
      ),
    );
    expect(body.containsKey('authAttemptChallengeResponse'), isFalse);
    expect(response.authAttemptProofTokenResultSignedByIntegration, isNull);
  });
}
