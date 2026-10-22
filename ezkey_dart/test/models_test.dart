import 'dart:convert';

import 'package:ezkey_dart/ezkey_dart.dart';
import 'package:test/test.dart';

void main() {
  group('EnrollmentCode.parse', () {
    test('JSON with numeric id and authUrl (normalized)', () {
      final code = EnrollmentCode.parse(
        '{"enrollmentId": 42, "enrollmentProofToken": "abc.def", "authUrl": "HTTPS://Auth.Example.org:443/"}',
      );
      expect(code.enrollmentId, 42);
      expect(code.enrollmentProofToken, 'abc.def');
      expect(code.authUrl, 'https://auth.example.org');
    });

    test('JSON with string id and no authUrl', () {
      final code = EnrollmentCode.parse(
        '{"enrollmentId": "7", "enrollmentProofToken": "t"}',
      );
      expect(code.enrollmentId, 7);
      expect(code.authUrl, isNull);
    });

    test('legacy id|token format', () {
      final code = EnrollmentCode.parse(' 9|tok.en ');
      expect(code.enrollmentId, 9);
      expect(code.enrollmentProofToken, 'tok.en');
    });

    test('plain http only for development hosts', () {
      expect(
        EnrollmentCode.parse(
          '{"enrollmentId":1,"enrollmentProofToken":"t","authUrl":"http://10.0.2.2:8080"}',
        ).authUrl,
        'http://10.0.2.2:8080',
      );
      expect(
        () => EnrollmentCode.parse(
          '{"enrollmentId":1,"enrollmentProofToken":"t","authUrl":"http://auth.example.org"}',
        ),
        throwsA(isA<EzkeyValidationException>()),
      );
      expect(
        EnrollmentCode.parse(
          '{"enrollmentId":1,"enrollmentProofToken":"t","authUrl":"http://auth-api:8080"}',
          policy: const EzkeyUrlPolicy(insecureHttpHosts: <String>{'auth-api'}),
        ).authUrl,
        'http://auth-api:8080',
      );
    });

    test('rejects malformed codes', () {
      for (final bad in <String>[
        '',
        'nonsense',
        '|tok',
        'x|tok',
        '0|tok',
        '1|',
        '1|to ken',
        '{"enrollmentId": 1}',
        '{"enrollmentId": -1, "enrollmentProofToken": "t"}',
        '{"enrollmentId": 1, "enrollmentProofToken": "t", "authUrl": "javascript:alert(1)"}',
        '{"enrollmentId": 1, "enrollmentProofToken": "t", "authUrl": "https://u:p@host"}',
      ]) {
        expect(
          () => EnrollmentCode.parse(bad),
          throwsA(isA<EzkeyValidationException>()),
          reason: bad,
        );
      }
    });

    test('toString does not reveal the proof token', () {
      final code = EnrollmentCode.parse('5|super-secret-token');
      expect('$code', isNot(contains('super-secret-token')));
    });
  });

  group('parseChallengeCode', () {
    test('accepts digits with spaces or hyphens', () {
      expect(parseChallengeCode('123456'), 123456);
      expect(parseChallengeCode(' 123 456 '), 123456);
      expect(parseChallengeCode('123-456'), 123456);
      expect(parseChallengeCode('007'), 7);
    });

    test('rejects anything else', () {
      for (final bad in <String>['', 'abc', '12a', '1234567890', '-', '+1']) {
        expect(
          () => parseChallengeCode(bad),
          throwsA(isA<EzkeyValidationException>()),
          reason: bad,
        );
      }
    });
  });

  group('EzkeyEnrollment', () {
    EzkeyEnrollment sample() => EzkeyEnrollment(
      authApiBaseUrl: 'https://auth.example.org',
      enrollmentId: 12,
      enrollmentProofToken: 'secret-proof',
      integrationPublicKey: 'pk',
      integrationKeyAlgorithm: 'ed25519',
      deviceKeyAlias: 'alias-12',
      devicePublicKey: 'spki',
      devicePrivateKeyStorageTier: DevicePrivateKeyStorageTier.strong,
      enrolledAt: DateTime.utc(2026, 3, 4, 5, 6, 7),
      integrationName: 'ACME',
      tenantId: 3,
      isSystemIntegration: true,
      adminType: 'TENANT_ADMIN',
    );

    test('JSON roundtrip', () {
      final original = sample();
      final restored = EzkeyEnrollment.fromJson(
        jsonDecode(jsonEncode(original.toJson())) as Map<String, dynamic>,
      );
      expect(restored.toJson(), original.toJson());
      expect(restored.localId, 'https://auth.example.org#12');
      expect(
        restored.devicePrivateKeyStorageTier,
        DevicePrivateKeyStorageTier.strong,
      );
    });

    test('corrupt records are a local-state error', () {
      final json = sample().toJson()..remove('deviceKeyAlias');
      expect(
        () => EzkeyEnrollment.fromJson(json.cast<String, dynamic>()),
        throwsA(isA<EzkeyLocalStateException>()),
      );
      final badTier =
          sample().toJson()..['devicePrivateKeyStorageTier'] = 'TEE';
      expect(
        () => EzkeyEnrollment.fromJson(badTier.cast<String, dynamic>()),
        throwsA(isA<EzkeyLocalStateException>()),
      );
    });

    test('toString is redacted', () {
      expect('${sample()}', isNot(contains('secret-proof')));
    });
  });

  test('storage tier and result wire values', () {
    expect(DevicePrivateKeyStorageTier.values.map((t) => t.wireValue), <String>[
      'NONE',
      'STANDARD',
      'STRONG',
    ]);
    expect(
      DevicePrivateKeyStorageTier.fromWire('STANDARD'),
      DevicePrivateKeyStorageTier.standard,
    );
    expect(DevicePrivateKeyStorageTier.fromWire('none'), isNull);
    expect(AuthAttemptResult.fromWire('FAILED'), AuthAttemptResult.failed);
    expect(AuthAttemptResult.fromWire('approved'), isNull);
  });

  test('PendingAuthAttempt advisory expiry uses the 120 s server ceiling', () {
    final received = DateTime.utc(2026, 1, 1);
    final attempt = PendingAuthAttempt(
      enrollmentLocalId: 'x#1',
      authAttemptId: 1,
      authAttemptProofToken: 'secret-attempt',
      challengeRequired: false,
      receivedAt: received,
    );
    expect(
      attempt.latestPossibleExpiry,
      received.add(const Duration(seconds: 120)),
    );
    expect(
      attempt.isCertainlyExpired(received.add(const Duration(seconds: 120))),
      isFalse,
    );
    expect(
      attempt.isCertainlyExpired(received.add(const Duration(seconds: 121))),
      isTrue,
    );
    expect('$attempt', isNot(contains('secret-attempt')));
  });

  group('EzkeyUrlPolicy', () {
    const policy = EzkeyUrlPolicy();

    test('normalizes https URLs', () {
      expect(
        policy.normalize('https://Auth.Example.org/'),
        'https://auth.example.org',
      );
      expect(
        policy.normalize('https://a.example:8443/ezkey//'),
        'https://a.example:8443/ezkey',
      );
      expect(policy.normalize('https://a.example:443'), 'https://a.example');
    });

    test('http only for allowed hosts', () {
      expect(
        policy.normalize('http://localhost:8080'),
        'http://localhost:8080',
      );
      expect(policy.normalize('http://127.0.0.1'), 'http://127.0.0.1');
      expect(policy.normalize('http://example.org'), isNull);
      expect(EzkeyUrlPolicy.httpsOnly.normalize('http://localhost'), isNull);
    });

    test('rejects malformed values', () {
      for (final bad in <String?>[
        null,
        '',
        'ftp://a',
        'https://',
        'https://a:0',
        'https://a:70000',
        'https://a:b:c',
        'https://a?x=1',
        'https://a#f',
        'https://user@a',
      ]) {
        expect(policy.normalize(bad), isNull, reason: '$bad');
      }
      expect(
        () => policy.require('http://x.org'),
        throwsA(isA<EzkeyValidationException>()),
      );
    });
  });
}
