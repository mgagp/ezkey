import 'package:ezkey_dart/ezkey_dart.dart';
import 'package:test/test.dart';

void main() {
  test('generateProofToken returns the EZKey random.salt format', () {
    final token = generateProofToken();
    final parts = token.split('.');

    expect(parts, hasLength(2));
    expect(parts[0], isNotEmpty);
    expect(parts[1], isNotEmpty);
    expect(parts[0], isNot(contains('=')));
    expect(parts[1], isNot(contains('=')));
  });
}
