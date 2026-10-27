import 'dart:async';

import 'package:ezkey_dart/ezkey_dart.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:test/test.dart';

import 'support/fake_ezkey_server.dart';

Matcher _integrity(EzkeyIntegrityCheck check) => throwsA(
  isA<EzkeyIntegrityException>().having((e) => e.check, 'check', check),
);

Matcher _api(EzkeyProblemKind kind) =>
    throwsA(isA<EzkeyApiException>().having((e) => e.kind, 'kind', kind));

void main() {
  late FakeEzkeyServer server;
  late SoftwareDeviceKeyStore keys;
  late InMemoryEnrollmentStore store;
  late EzkeyAuthenticator auth;

  setUp(() async {
    server = await FakeEzkeyServer.create();
    keys = SoftwareDeviceKeyStore();
    store = InMemoryEnrollmentStore();
    auth = EzkeyAuthenticator(
      keyStore: keys,
      enrollmentStore: store,
      httpClient: server.client,
      clock: () => server.now,
    );
  });

  EnrollmentCode codeFor(FakeEnrollment invitation) =>
      EnrollmentCode.parse(server.qrCode(invitation));

  Future<EzkeyEnrollment> enroll(FakeEnrollment invitation) => auth.enroll(
    codeFor(invitation),
    enrollmentChallenge: invitation.challenge,
  );

  group('enrollment', () {
    test(
      'bind + verify persists the enrollment and registers the device key',
      () async {
        final invitation = server.invite();
        final enrollment = await enroll(invitation);

        expect(enrollment.enrollmentId, invitation.id);
        expect(enrollment.authApiBaseUrl, fakeAuthApiBaseUrl);
        expect(enrollment.integrationPublicKey, server.integrationPublicKey);
        expect(enrollment.integrationName, 'ACME Portal');
        expect(enrollment.tenantDescription, 'Cafe\u0301 & co');
        expect(
          enrollment.devicePrivateKeyStorageTier,
          DevicePrivateKeyStorageTier.none,
        );
        expect(await store.load(enrollment.localId), isNotNull);
        expect(
          await keys.publicKey(enrollment.deviceKeyAlias),
          enrollment.devicePublicKey,
        );
        expect(invitation.status, 'VERIFIED');
        expect(invitation.devicePublicKey, enrollment.devicePublicKey);
        expect(invitation.storageTier, 'NONE');
        expect(server.requestPaths, <String>[
          AuthApiPaths.bind,
          AuthApiPaths.verify,
        ]);
      },
    );

    test(
      'two-step flow exposes verified metadata before the challenge',
      () async {
        final invitation = server.invite(
          isSystemIntegration: true,
          adminType: 'GLOBAL_ADMIN',
        );
        final bound = await auth.bindEnrollment(codeFor(invitation));

        expect(bound.integrationName, 'Ezkey System');
        expect(bound.isSystemIntegration, isTrue);
        expect(bound.adminType, 'GLOBAL_ADMIN');
        expect(await store.list(), isEmpty);
        expect(keys.aliases, isEmpty);

        final enrollment = await auth.completeEnrollment(
          bound,
          enrollmentChallenge: invitation.challenge,
        );
        expect(enrollment.adminType, 'GLOBAL_ADMIN');
        expect(enrollment.isSystemIntegration, isTrue);
      },
    );

    test('authApiBaseUrlOverride wins over the code authUrl', () async {
      final invitation = server.invite();
      final code = EnrollmentCode.parse(
        server.qrCode(invitation, authUrl: null),
      );
      await expectLater(
        auth.bindEnrollment(code),
        throwsA(isA<EzkeyValidationException>()),
      );
      final bound = await auth.bindEnrollment(
        code,
        authApiBaseUrlOverride: '$fakeAuthApiBaseUrl/',
      );
      expect(bound.authApiBaseUrl, fakeAuthApiBaseUrl);
    });

    test('rejects an insecure override URL', () async {
      final invitation = server.invite();
      await expectLater(
        auth.bindEnrollment(
          codeFor(invitation),
          authApiBaseUrlOverride: 'http://evil.example',
        ),
        throwsA(isA<EzkeyValidationException>()),
      );
      expect(server.requestPaths, isEmpty);
    });

    test('refuses to enroll twice locally', () async {
      final invitation = server.invite();
      await enroll(invitation);
      await expectLater(
        auth.bindEnrollment(codeFor(invitation)),
        throwsA(isA<EzkeyValidationException>()),
      );
    });

    test('server refuses re-binding a consumed invitation', () async {
      final invitation = server.invite();
      await auth.bindEnrollment(codeFor(invitation));
      final other = EzkeyAuthenticator(
        keyStore: SoftwareDeviceKeyStore(),
        enrollmentStore: InMemoryEnrollmentStore(),
        httpClient: server.client,
      );
      await expectLater(
        other.bindEnrollment(codeFor(invitation)),
        _api(EzkeyProblemKind.enrollmentAlreadyBound),
      );
    });

    test('bind: tampered display text fails the signature', () async {
      server.mutateResponse =
          (path, body) => body..['integrationName'] = 'Evil Corp';
      await expectLater(
        auth.bindEnrollment(codeFor(server.invite())),
        _integrity(EzkeyIntegrityCheck.bindSignature),
      );
    });

    test('bind: tampered adminType fails the signature', () async {
      server.mutateResponse =
          (path, body) => body..['adminType'] = 'GLOBAL_ADMIN';
      await expectLater(
        auth.bindEnrollment(codeFor(server.invite())),
        _integrity(EzkeyIntegrityCheck.bindSignature),
      );
    });

    test('bind: substituted integration key fails the signature', () async {
      final attacker = await generateEd25519TestKeyPair();
      server.mutateResponse =
          (path, body) =>
              body..['integrationPublicKey'] = attacker.publicKeyBase64Url;
      await expectLater(
        auth.bindEnrollment(codeFor(server.invite())),
        _integrity(EzkeyIntegrityCheck.bindSignature),
      );
    });

    test('bind: algorithm must be exactly ed25519', () async {
      server.integrationKeyAlgorithm = 'ED25519';
      await expectLater(
        auth.bindEnrollment(codeFor(server.invite())),
        _integrity(EzkeyIntegrityCheck.unsupportedIntegrationKeyAlgorithm),
      );
    });

    test('bind: answer for another enrollment is rejected', () async {
      server.mutateResponse = (path, body) => body..['enrollmentId'] = 1;
      await expectLater(
        auth.bindEnrollment(codeFor(server.invite())),
        _integrity(EzkeyIntegrityCheck.bindConsistency),
      );
    });

    test('bind: missing required field is a malformed response', () async {
      server.mutateResponse =
          (path, body) => body..remove('isSystemIntegration');
      await expectLater(
        auth.bindEnrollment(codeFor(server.invite())),
        _integrity(EzkeyIntegrityCheck.malformedResponse),
      );
    });

    test(
      'verify: wrong challenge -> verify-failed, key deleted, nothing stored',
      () async {
        final invitation = server.invite();
        await expectLater(
          auth.enroll(
            codeFor(invitation),
            enrollmentChallenge: invitation.challenge + 1,
          ),
          _api(EzkeyProblemKind.enrollmentVerifyFailed),
        );
        expect(keys.aliases, isEmpty);
        expect(await store.list(), isEmpty);
        expect(invitation.status, 'INVALID');
      },
    );

    test(
      'verify: tampered signature -> integrity error, key deleted',
      () async {
        server.mutateResponse = (path, body) {
          if (path == AuthApiPaths.verify) {
            body['enrollmentVerifyMessage'] = 'Enrollment verified!';
          }
          return body;
        };
        final invitation = server.invite();
        await expectLater(
          enroll(invitation),
          _integrity(EzkeyIntegrityCheck.verifySignature),
        );
        expect(keys.aliases, isEmpty);
        expect(await store.list(), isEmpty);
      },
    );

    test(
      'verify: active=false is refused even with a valid signature',
      () async {
        server.mutateResponse = (path, body) {
          if (path == AuthApiPaths.verify) {
            body['active'] = false;
          }
          return body;
        };
        await expectLater(
          enroll(server.invite()),
          _integrity(EzkeyIntegrityCheck.verifyNotActive),
        );
        expect(keys.aliases, isEmpty);
      },
    );

    test('verify: transport failure -> network error, key deleted', () async {
      server.failTransportOn = AuthApiPaths.verify;
      await expectLater(
        enroll(server.invite()),
        throwsA(isA<EzkeyNetworkException>()),
      );
      expect(keys.aliases, isEmpty);
    });

    test('forget removes the record and the key', () async {
      final enrollment = await enroll(server.invite());
      await auth.forget(enrollment);
      expect(await auth.listEnrollments(), isEmpty);
      expect(await keys.publicKey(enrollment.deviceKeyAlias), isNull);
    });

    test('instance-info is verified', () async {
      final enrollment = await enroll(server.invite());
      final info = await auth.fetchInstanceInfo(enrollment);
      expect(info.instanceName, 'Ezkey Lab');
      expect(info.instanceDescription, 'Instance de de\u0301monstration');

      server.mutateResponse = (path, body) => body..['instanceName'] = 'Phish';
      await expectLater(
        auth.fetchInstanceInfo(enrollment),
        _integrity(EzkeyIntegrityCheck.instanceInfoSignature),
      );
    });
  });

  group('auth attempts', () {
    late FakeEnrollment invitation;
    late EzkeyEnrollment enrollment;

    setUp(() async {
      invitation = server.invite();
      enrollment = await enroll(invitation);
    });

    test(
      'nothing pending -> null; every poll signs a fresh device proof token',
      () async {
        expect(await auth.checkPending(enrollment), isNull);
        expect(await auth.checkPending(enrollment), isNull);
        final tokens =
            server.requestBodies
                .where((b) => b.containsKey('deviceProofToken'))
                .map((b) => b['deviceProofToken'])
                .toSet();
        expect(tokens, hasLength(2));
      },
    );

    test('claim is one-shot and carries verified context (NFC)', () async {
      final created = server.createAttempt(
        invitation.id,
        title: 'Paiement approuve\u0301',
        message: 'Virement de 50 \u20ac',
      );
      final attempt = await auth.checkPending(enrollment);
      expect(attempt!.authAttemptId, created.id);
      expect(attempt.contextTitle, 'Paiement approuve\u0301');
      expect(attempt.challengeRequired, isFalse);
      expect(attempt.receivedAt, server.now);
      expect(await auth.checkPending(enrollment), isNull);
    });

    test('approve -> APPROVED; deny -> DENIED', () async {
      server.createAttempt(invitation.id);
      final first = await auth.approve(
        enrollment,
        (await auth.checkPending(enrollment))!,
      );
      expect(first.result, AuthAttemptResult.approved);
      expect(first.accepted, isTrue);
      expect(first.message, 'Authentication approved');

      final created = server.createAttempt(invitation.id);
      final second = await auth.deny(
        enrollment,
        (await auth.checkPending(enrollment))!,
      );
      expect(second.result, AuthAttemptResult.denied);
      expect(created.state, 'REJECTED');
    });

    test('pending: tampered context fails the signature', () async {
      server.createAttempt(invitation.id, title: 'Login');
      server.mutateResponse =
          (path, body) => body..['contextTitle'] = 'Wire 10 000 EUR';
      await expectLater(
        auth.checkPending(enrollment),
        _integrity(EzkeyIntegrityCheck.pendingSignature),
      );
    });

    test('pending: tampered challenge flag fails the signature', () async {
      server.createAttempt(invitation.id, challengeRequested: true);
      server.mutateResponse =
          (path, body) => body..['authAttemptChallengeRequired'] = false;
      await expectLater(
        auth.checkPending(enrollment),
        _integrity(EzkeyIntegrityCheck.pendingSignature),
      );
    });

    test('respond: missing result signature fails closed (#844)', () async {
      server.createAttempt(invitation.id);
      final attempt = (await auth.checkPending(enrollment))!;
      server.mutateResponse =
          (path, body) =>
              body..remove('authAttemptProofTokenResultSignedByIntegration');
      await expectLater(
        auth.approve(enrollment, attempt),
        _integrity(EzkeyIntegrityCheck.respondSignature),
      );
    });

    test('respond: forged result fails the signature', () async {
      server.createAttempt(invitation.id);
      final attempt = (await auth.checkPending(enrollment))!;
      server.mutateResponse =
          (path, body) => body..['authAttemptResult'] = 'APPROVED';
      await expectLater(
        auth.deny(enrollment, attempt),
        _integrity(EzkeyIntegrityCheck.respondSignature),
      );
    });

    test('respond: answer for another attempt id is inconsistent', () async {
      server.createAttempt(invitation.id);
      final attempt = (await auth.checkPending(enrollment))!;
      server.mutateResponse = (path, body) => body..['authAttemptId'] = 1;
      await expectLater(
        auth.approve(enrollment, attempt),
        _integrity(EzkeyIntegrityCheck.respondConsistency),
      );
    });

    test('respond: unknown result value is inconsistent', () async {
      server.createAttempt(invitation.id);
      final attempt = (await auth.checkPending(enrollment))!;
      server.mutateResponse =
          (path, body) => body..['authAttemptResult'] = 'MAYBE';
      await expectLater(
        auth.approve(enrollment, attempt),
        _integrity(EzkeyIntegrityCheck.respondSignature),
      );
    });

    test('respond: 503 signing unavailable is retryable', () async {
      server.createAttempt(invitation.id);
      final attempt = (await auth.checkPending(enrollment))!;
      server.forcedProblem = (
        path: AuthApiPaths.respond,
        status: 503,
        type: 'https://ezkey.io/problems/auth/integration-signing-unavailable',
      );
      await expectLater(
        auth.approve(enrollment, attempt),
        throwsA(
          isA<EzkeyApiException>()
              .having(
                (e) => e.kind,
                'kind',
                EzkeyProblemKind.integrationSigningUnavailable,
              )
              .having((e) => e.retryable, 'retryable', isTrue)
              .having((e) => e.isAttemptNoLongerActionable, 'final', isFalse),
        ),
      );
    });

    test(
      'respond twice: refused locally; the server also refuses a copy',
      () async {
        server.createAttempt(invitation.id);
        final attempt = (await auth.checkPending(enrollment))!;
        await auth.approve(enrollment, attempt);
        expect(attempt.decisionState, AuthAttemptDecisionState.locked);
        await expectLater(
          auth.deny(enrollment, attempt),
          throwsA(isA<EzkeyValidationException>()),
        );
        expect(server.countRequests(AuthApiPaths.respond), 1);

        // Server guarantee, independent of the client lock: a second decision is a final 409.
        final copy = PendingAuthAttempt(
          enrollmentLocalId: attempt.enrollmentLocalId,
          authAttemptId: attempt.authAttemptId,
          authAttemptProofToken: attempt.authAttemptProofToken,
          challengeRequired: attempt.challengeRequired,
          receivedAt: attempt.receivedAt,
        );
        await expectLater(
          auth.approve(enrollment, copy),
          throwsA(
            isA<EzkeyApiException>()
                .having(
                  (e) => e.kind,
                  'kind',
                  EzkeyProblemKind.authAttemptStateConflict,
                )
                .having((e) => e.isAttemptNoLongerActionable, 'final', isTrue)
                .having((e) => e.retryable, 'retryable', isFalse),
          ),
        );
        expect(copy.decisionState, AuthAttemptDecisionState.locked);
      },
    );

    group('decision lock and unknown outcome (contract §2)', () {
      Matcher outcomeUnknown<T extends EzkeyException>({
        required bool accepted,
      }) => throwsA(
        isA<EzkeyRespondOutcomeUnknownException>()
            .having((e) => e.accepted, 'accepted', accepted)
            .having((e) => e.retryable, 'retryable', isFalse)
            .having((e) => e.cause, 'cause', isA<T>()),
      );

      test('a claimed attempt starts open', () async {
        server.createAttempt(invitation.id);
        final attempt = (await auth.checkPending(enrollment))!;
        expect(attempt.decisionState, AuthAttemptDecisionState.open);
        expect(attempt.canDecide, isTrue);
      });

      test(
        'a racing second decision is refused while the first is in flight',
        () async {
          final created = server.createAttempt(invitation.id);
          final attempt = (await auth.checkPending(enrollment))!;
          final approving = auth.approve(enrollment, attempt);
          expect(attempt.decisionState, AuthAttemptDecisionState.sending);
          expect(attempt.canDecide, isFalse);
          await expectLater(
            auth.deny(enrollment, attempt),
            throwsA(isA<EzkeyValidationException>()),
          );
          expect((await approving).result, AuthAttemptResult.approved);
          expect(created.state, 'ACCEPTED');
          expect(server.countRequests(AuthApiPaths.respond), 1);
        },
      );

      test(
        'connection cut after the server recorded the decision -> unknown, locked',
        () async {
          final created = server.createAttempt(invitation.id);
          final cutAfterSend = EzkeyAuthenticator(
            keyStore: keys,
            enrollmentStore: store,
            httpClient: MockClient((request) async {
              final response = await server.handle(request);
              if (request.url.path == AuthApiPaths.respond) {
                throw http.ClientException('connection reset', request.url);
              }
              return response;
            }),
            clock: () => server.now,
          );
          final attempt = (await cutAfterSend.checkPending(enrollment))!;
          await expectLater(
            cutAfterSend.approve(enrollment, attempt),
            outcomeUnknown<EzkeyNetworkException>(accepted: true),
          );
          expect(created.state, 'ACCEPTED');
          expect(attempt.decisionState, AuthAttemptDecisionState.locked);
          // No leave-as-deny and no second decision after an unknown outcome (Q10).
          await expectLater(
            cutAfterSend.deny(enrollment, attempt),
            throwsA(isA<EzkeyValidationException>()),
          );
          expect(server.countRequests(AuthApiPaths.respond), 1);
        },
      );

      test('transport failure on respond -> unknown, locked', () async {
        server.createAttempt(invitation.id);
        final attempt = (await auth.checkPending(enrollment))!;
        server.failTransportOn = AuthApiPaths.respond;
        await expectLater(
          auth.deny(enrollment, attempt),
          outcomeUnknown<EzkeyNetworkException>(accepted: false),
        );
        expect(attempt.decisionState, AuthAttemptDecisionState.locked);
      });

      test('ambiguous 5xx on respond -> unknown, locked', () async {
        for (final status in <int>[500, 502]) {
          server.forcedProblem = null;
          server.createAttempt(invitation.id);
          final attempt = (await auth.checkPending(enrollment))!;
          server.forcedProblem = (
            path: AuthApiPaths.respond,
            status: status,
            type:
                status == 500
                    ? 'https://ezkey.io/problems/auth/internal-error'
                    : 'https://example.org/proxy-error',
          );
          await expectLater(
            auth.approve(enrollment, attempt),
            outcomeUnknown<EzkeyApiException>(accepted: true),
            reason: 'HTTP $status',
          );
          expect(attempt.decisionState, AuthAttemptDecisionState.locked);
        }
      });

      test(
        '503 signing unavailable reopens the attempt; a retry succeeds',
        () async {
          server.createAttempt(invitation.id);
          final attempt = (await auth.checkPending(enrollment))!;
          server.forcedProblem = (
            path: AuthApiPaths.respond,
            status: 503,
            type:
                'https://ezkey.io/problems/auth/integration-signing-unavailable',
          );
          await expectLater(
            auth.approve(enrollment, attempt),
            _api(EzkeyProblemKind.integrationSigningUnavailable),
          );
          expect(attempt.decisionState, AuthAttemptDecisionState.open);
          server.forcedProblem = null;
          expect(
            (await auth.approve(enrollment, attempt)).result,
            AuthAttemptResult.approved,
          );
        },
      );

      test('429 reopens the attempt', () async {
        server.createAttempt(invitation.id);
        final attempt = (await auth.checkPending(enrollment))!;
        server.forcedProblem = (
          path: AuthApiPaths.respond,
          status: 429,
          type: 'https://example.org/rate-limit',
        );
        await expectLater(
          auth.deny(enrollment, attempt),
          _api(EzkeyProblemKind.rateLimited),
        );
        expect(attempt.decisionState, AuthAttemptDecisionState.open);
      });

      test('final 409 locks the attempt', () async {
        final created = server.createAttempt(invitation.id);
        final attempt = (await auth.checkPending(enrollment))!;
        server.cancel(created.id);
        await expectLater(
          auth.approve(enrollment, attempt),
          _api(EzkeyProblemKind.authAttemptStateConflict),
        );
        expect(attempt.decisionState, AuthAttemptDecisionState.locked);
      });

      test(
        'integrity failure after HTTP 200 locks the attempt (Q2, Q9)',
        () async {
          server.createAttempt(invitation.id);
          final attempt = (await auth.checkPending(enrollment))!;
          server.mutateResponse =
              (path, body) => body..['authAttemptResult'] = 'DENIED';
          await expectLater(
            auth.approve(enrollment, attempt),
            _integrity(EzkeyIntegrityCheck.respondSignature),
          );
          expect(attempt.decisionState, AuthAttemptDecisionState.locked);
        },
      );

      test(
        'signing failure sends nothing and keeps the attempt open',
        () async {
          server.createAttempt(invitation.id);
          final attempt = (await auth.checkPending(enrollment))!;
          await keys.deleteKeyPair(enrollment.deviceKeyAlias);
          await expectLater(
            auth.approve(enrollment, attempt),
            throwsA(isA<EzkeyException>()),
          );
          expect(attempt.decisionState, AuthAttemptDecisionState.open);
          expect(server.countRequests(AuthApiPaths.respond), 0);
        },
      );

      test(
        'a newer attempt is not blocked by the lock of an older one (Q13)',
        () async {
          server.createAttempt(invitation.id);
          final older = (await auth.checkPending(enrollment))!;
          await auth.deny(enrollment, older);
          server.createAttempt(invitation.id);
          final newer = (await auth.checkPending(enrollment))!;
          expect(older.canDecide, isFalse);
          expect(newer.canDecide, isTrue);
          expect(
            (await auth.approve(enrollment, newer)).result,
            AuthAttemptResult.approved,
          );
        },
      );
    });

    test('attempt of another enrollment is refused locally', () async {
      final other = await enroll(server.invite());
      server.createAttempt(invitation.id);
      final attempt = (await auth.checkPending(enrollment))!;
      await expectLater(
        auth.approve(other, attempt),
        throwsA(isA<EzkeyValidationException>()),
      );
    });

    group('challenge', () {
      test(
        'enrollment-level: missing code refused locally, nothing sent',
        () async {
          final strict = server.invite(authAttemptChallengeRequired: true);
          final strictEnrollment = await enroll(strict);
          server.createAttempt(strict.id);
          final attempt = (await auth.checkPending(strictEnrollment))!;
          expect(attempt.challengeRequired, isTrue);
          final before = server.countRequests(AuthApiPaths.respond);
          await expectLater(
            auth.approve(strictEnrollment, attempt),
            throwsA(isA<EzkeyValidationException>()),
          );
          expect(server.countRequests(AuthApiPaths.respond), before);
        },
      );

      test(
        'wrong code -> FAILED (attempt invalid); right code -> APPROVED',
        () async {
          final created = server.createAttempt(
            invitation.id,
            challengeRequested: true,
          );
          final attempt = (await auth.checkPending(enrollment))!;
          final failed = await auth.approve(
            enrollment,
            attempt,
            challengeResponse: created.challenge + 1,
          );
          expect(failed.result, AuthAttemptResult.failed);
          expect(created.state, 'INVALID');

          final retry = server.createAttempt(
            invitation.id,
            challengeRequested: true,
          );
          final ok = await auth.approve(
            enrollment,
            (await auth.checkPending(enrollment))!,
            challengeResponse: retry.challenge,
          );
          expect(ok.result, AuthAttemptResult.approved);
          expect(
            server.requestBodies.last['authAttemptChallengeResponse'],
            retry.challenge,
          );
        },
      );

      test('deny needs no code and never sends one', () async {
        server.createAttempt(invitation.id, challengeRequested: true);
        final attempt = (await auth.checkPending(enrollment))!;
        final outcome = await auth.deny(enrollment, attempt);
        expect(outcome.result, AuthAttemptResult.denied);
        expect(
          server.requestBodies.last.containsKey('authAttemptChallengeResponse'),
          isFalse,
        );
      });
    });

    group('cancel, supersede, expiry', () {
      test('cancel after claim -> state conflict', () async {
        final created = server.createAttempt(invitation.id);
        final attempt = (await auth.checkPending(enrollment))!;
        server.cancel(created.id);
        await expectLater(
          auth.approve(enrollment, attempt),
          _api(EzkeyProblemKind.authAttemptStateConflict),
        );
      });

      test('cancel before claim -> nothing pending', () async {
        final created = server.createAttempt(invitation.id);
        server.cancel(created.id);
        expect(await auth.checkPending(enrollment), isNull);
      });

      test('newer attempt supersedes an older claimed one', () async {
        server.createAttempt(invitation.id);
        final older = (await auth.checkPending(enrollment))!;
        final newer = server.createAttempt(invitation.id);
        await expectLater(
          auth.approve(enrollment, older),
          _api(EzkeyProblemKind.authAttemptStateConflict),
        );
        expect((await auth.checkPending(enrollment))!.authAttemptId, newer.id);
      });

      test(
        'expired attempt -> auth-attempt-expired; local advisory bound',
        () async {
          server.createAttempt(invitation.id, ttl: const Duration(seconds: 15));
          final attempt = (await auth.checkPending(enrollment))!;
          expect(attempt.isCertainlyExpired(server.now), isFalse);
          expect(
            attempt.latestPossibleExpiry,
            server.now.add(maxAuthAttemptTtl),
          );

          server.now = server.now.add(const Duration(seconds: 16));
          await expectLater(
            auth.approve(enrollment, attempt),
            throwsA(
              isA<EzkeyApiException>()
                  .having(
                    (e) => e.kind,
                    'kind',
                    EzkeyProblemKind.authAttemptExpired,
                  )
                  .having(
                    (e) => e.isAttemptNoLongerActionable,
                    'final',
                    isTrue,
                  ),
            ),
          );
          expect(
            attempt.isCertainlyExpired(server.now.add(maxAuthAttemptTtl)),
            isTrue,
          );
        },
      );

      test('expired attempt is not delivered', () async {
        server.createAttempt(invitation.id, ttl: const Duration(seconds: 15));
        server.now = server.now.add(const Duration(seconds: 20));
        expect(await auth.checkPending(enrollment), isNull);
      });
    });

    group('polling', () {
      test('pollPending returns the attempt once it appears', () async {
        Timer(
          const Duration(milliseconds: 40),
          () => server.createAttempt(invitation.id),
        );
        final realClockAuth = EzkeyAuthenticator(
          keyStore: keys,
          enrollmentStore: store,
          httpClient: server.client,
        );
        final attempt = await realClockAuth.pollPending(
          enrollment,
          interval: const Duration(milliseconds: 10),
          timeout: const Duration(seconds: 5),
        );
        expect(attempt, isNotNull);
      });

      test('pollPending gives up after the timeout', () async {
        final realClockAuth = EzkeyAuthenticator(
          keyStore: keys,
          enrollmentStore: store,
          httpClient: server.client,
        );
        final attempt = await realClockAuth.pollPending(
          enrollment,
          interval: const Duration(milliseconds: 10),
          timeout: const Duration(milliseconds: 60),
        );
        expect(attempt, isNull);
      });

      test(
        'pollPending retries network errors but surfaces API errors',
        () async {
          var calls = 0;
          final flaky = MockClient((request) async {
            calls++;
            if (calls == 1) {
              throw http.ClientException('offline');
            }
            return server.handle(request);
          });
          server.createAttempt(invitation.id);
          final flakyAuth = EzkeyAuthenticator(
            keyStore: keys,
            enrollmentStore: store,
            httpClient: flaky,
          );
          final attempt = await flakyAuth.pollPending(
            enrollment,
            interval: const Duration(milliseconds: 5),
            timeout: const Duration(seconds: 5),
          );
          expect(attempt, isNotNull);
          expect(calls, 2);

          server.forcedProblem = (
            path: AuthApiPaths.pending,
            status: 400,
            type: 'https://ezkey.io/problems/auth/auth-attempt-binding-failed',
          );
          await expectLater(
            flakyAuth.pollPending(
              enrollment,
              interval: const Duration(milliseconds: 5),
            ),
            _api(EzkeyProblemKind.authAttemptBindingFailed),
          );
        },
      );

      test('watchPending yields attempts until cancelled', () async {
        final realClockAuth = EzkeyAuthenticator(
          keyStore: keys,
          enrollmentStore: store,
          httpClient: server.client,
        );
        final first = server.createAttempt(invitation.id);
        final received = <int>[];
        final done = Completer<void>();
        final subscription = realClockAuth
            .watchPending(
              enrollment,
              interval: const Duration(milliseconds: 10),
            )
            .listen((attempt) {
              received.add(attempt.authAttemptId);
              if (received.length == 1) {
                server.createAttempt(invitation.id);
              } else {
                done.complete();
              }
            });
        await done.future.timeout(const Duration(seconds: 5));
        await subscription.cancel();
        expect(received.first, first.id);
        expect(received, hasLength(2));
      });
    });

    test('nothing secret leaks through toString', () async {
      final created = server.createAttempt(invitation.id, title: 'Login');
      final attempt = (await auth.checkPending(enrollment))!;
      final rendered = <String>[
        '$enrollment',
        '$attempt',
        '${codeFor(server.invite())}',
        '${await auth.approve(enrollment, attempt)}',
      ].join('\n');
      expect(rendered, isNot(contains(enrollment.enrollmentProofToken)));
      expect(rendered, isNot(contains(created.proofToken)));
      expect(rendered, isNot(contains(invitation.proofToken)));
    });
  });
}
