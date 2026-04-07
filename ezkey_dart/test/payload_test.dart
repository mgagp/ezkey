import 'package:ezkey_dart/ezkey_dart.dart';
import 'package:test/test.dart';

void main() {
  test('buildPendingPayload uses empty strings for null text fields', () {
    expect(buildPendingPayload('tok', false, null, null), 'tok|false||');
  });

  test('buildPendingPayload normalizes title and message to NFC', () {
    final payload = buildPendingPayload(
      'tok',
      true,
      'Cafe\u0301',
      'Resume\u0301',
    );

    expect(payload, 'tok|true|Café|Resumé');
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
}
