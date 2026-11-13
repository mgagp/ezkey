import 'package:ezkey_dart/ezkey_dart.dart';
import 'package:test/test.dart';

/// Golden vectors shared with `ezkey-core` (`EnrollmentSignaturePayload`,
/// `AuthAttemptSignaturePayload`) and the mobile app's payload tests. The live E2E suite also
/// compares every builder with the JVM `payload-helper` oracle.
void main() {
  group('auth attempt payloads', () {
    test('pending: null text becomes empty, booleans lowercase', () {
      expect(buildPendingPayload('tok', false, null, null), 'tok|false||');
    });

    test('pending: title and message are NFC-normalized', () {
      expect(
        buildPendingPayload('tok', true, 'Cafe\u0301', 'Resume\u0301'),
        'tok|true|Caf\u00e9|Resum\u00e9',
      );
    });

    test('pending: pipes inside text are kept verbatim', () {
      expect(buildPendingPayload('t', true, 'a|b', ''), 't|true|a|b|');
    });

    test('respond: token and lowercase decision', () {
      expect(buildRespondPayload('tok', true), 'tok|true');
      expect(buildRespondPayload('tok', false), 'tok|false');
    });

    test('respond-result: message NFC-normalized', () {
      expect(
        buildRespondResultPayload('tok', '42', 'APPROVED', 'Cafe\u0301'),
        'tok|42|APPROVED|Caf\u00e9',
      );
      expect(
        buildRespondResultPayload('tok', '42', 'FAILED', null),
        'tok|42|FAILED|',
      );
    });
  });

  group('enrollment payloads', () {
    test('bind: twelve segments, mobile golden vector', () {
      expect(
        buildEnrollmentBindPayload(
          enrollmentProofToken: 'pt',
          enrollmentId: 1,
          integrationPublicKey: 'pk',
          integrationKeyAlgorithm: 'ed25519',
          integrationName: 'n',
          integrationDescription: 'd',
          enrollmentName: 'Marie',
          tenantId: 2,
          tenantName: 'tn',
          tenantDescription: 'td',
          isSystemIntegration: true,
          adminType: 'GLOBAL_ADMIN',
        ),
        'pt|1|pk|ed25519|n|d|Marie|2|tn|td|true|GLOBAL_ADMIN',
      );
    });

    test(
      'bind: optional fields empty, isSystemIntegration defaults to false',
      () {
        final payload = buildEnrollmentBindPayload(
          enrollmentProofToken: 't',
          enrollmentId: 1,
          integrationPublicKey: 'ipk',
          integrationKeyAlgorithm: 'ed25519',
          integrationName: 'Cafe\u0301',
          tenantId: 9,
        );
        expect(payload, 't|1|ipk|ed25519|Caf\u00e9|||9|||false|');
        expect(payload.split('|'), hasLength(12));
      },
    );

    test('verify device payload', () {
      expect(
        buildEnrollmentVerifyDevicePayload('pt', 7, 123456, 'spki'),
        'pt|7|123456|spki',
      );
    });

    test('verify result payload', () {
      expect(
        buildEnrollmentVerifyResultPayload(
          'pt',
          2,
          enrollmentVerifiedOutcome,
          'Cafe\u0301',
        ),
        'pt|2|VERIFIED|Caf\u00e9',
      );
    });

    test('instance-info payload: purpose literal, URL not normalized', () {
      expect(
        buildEnrollmentInstanceInfoPayload(
          enrollmentProofToken: 'pt',
          enrollmentId: 3,
          authApiPublicBaseUrl: 'https://auth.example.org',
          instanceName: 'Cafe\u0301',
          aboutUrl: 'https://example.org/a',
        ),
        'pt|3|INSTANCE_INFO|https://auth.example.org|Caf\u00e9||https://example.org/a',
      );
      expect(
        buildEnrollmentInstanceInfoPayload(
          enrollmentProofToken: 'pt',
          enrollmentId: 3,
        ),
        'pt|3|$instanceInfoPurpose||||',
      );
    });
  });
}
