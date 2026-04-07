import 'package:ezkey_dart/ezkey_dart.dart';
import 'package:test/test.dart';

void main() {
  test('EnrollmentCode parses JSON payload with authUrl', () {
    final code = EnrollmentCode.parse(
      '{"enrollmentId":123,"enrollmentProofToken":"abc.def","authUrl":"https://ezkey.acme.com"}',
    );

    expect(code.enrollmentId, '123');
    expect(code.enrollmentProofToken, 'abc.def');
    expect(code.authUrl, 'https://ezkey.acme.com');
  });

  test('EnrollmentCode parses legacy pipe payload', () {
    final code = EnrollmentCode.parse('123|abc|def');

    expect(code.enrollmentId, '123');
    expect(code.enrollmentProofToken, 'abc|def');
    expect(code.authUrl, isNull);
  });

  test('EnrollmentCode rejects unsupported format', () {
    expect(() => EnrollmentCode.parse('not-supported'), throwsFormatException);
  });
}