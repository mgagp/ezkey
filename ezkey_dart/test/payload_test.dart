import 'package:ezkey_dart/ezkey_dart.dart';
import 'package:test/test.dart';

void main() {
  test('buildPendingPayload uses empty strings for null text fields', () {
    expect(buildPendingPayload('tok', false, false, null, null), 'tok|false|false||');
  });

  test('buildPendingPayload normalizes title and message to NFC', () {
    final payload = buildPendingPayload(
      'tok',
      true,
      false,
      'Cafe\u0301',
      'Resume\u0301',
    );

    expect(payload, 'tok|true|false|Café|Resumé');
  });

  test('buildRespondPayload uses lowercase booleans', () {
    expect(buildRespondPayload('tok', true), 'tok|true');
    expect(buildRespondPayload('tok', false), 'tok|false');
  });

  test('buildRespondResultPayload normalizes message to NFC', () {
    final payload = buildRespondResultPayload(
      'tok',
      '42',
      'APPROVED',
      'Cafe\u0301',
    );

    expect(payload, 'tok|42|APPROVED|Café');
  });

  test('buildEnrollmentVerifyDevicePayload uses four pipe-separated segments', () {
    expect(
      buildEnrollmentVerifyDevicePayload('pt', 7, 123456, 'spkiB64'),
      'pt|7|123456|spkiB64',
    );
  });

  test('buildEnrollmentBindPayload matches documented field order', () {
    expect(
      buildEnrollmentBindPayload(
        enrollmentProofToken: 't',
        enrollmentId: 1,
        integrationPublicKey: 'ipk',
        integrationKeyAlgorithm: 'ed25519',
        authAttemptChallengeRequiredByPolicy: true,
        integrationName: 'Cafe\u0301',
        tenantId: 9,
      ),
      't|1|ipk|ed25519|Café|||9|||true',
    );
  });

  test('buildEnrollmentVerifyResultPayload normalizes message to NFC', () {
    expect(
      buildEnrollmentVerifyResultPayload('pt', 2, 'VERIFIED', 'Cafe\u0301'),
      'pt|2|VERIFIED|Café',
    );
  });
}
