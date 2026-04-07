import 'package:ezkey_dart/ezkey_dart.dart';
import 'package:test/test.dart';

void main() {
  test('parseEnvFileContents supports export and quoted values', () {
    final parsed = parseEnvFileContents('''
export EZKEY_AUTH_API_URL="https://goateed.example.dev"
EZKEY_LANGUAGE='fr'
# comment
EZKEY_AUTH_TIMEOUT_MS=20000
''');

    expect(parsed['EZKEY_AUTH_API_URL'], 'https://goateed.example.dev');
    expect(parsed['EZKEY_LANGUAGE'], 'fr');
    expect(parsed['EZKEY_AUTH_TIMEOUT_MS'], '20000');
  });

  test('validateAuthUrl accepts https and dev loopback http', () {
    expect(validateAuthUrl('https://ezkey.acme.com/'), 'https://ezkey.acme.com');
    expect(validateAuthUrl('http://localhost:8080'), 'http://localhost:8080');
    expect(validateAuthUrl('http://example.com'), isNull);
  });

  test('resolveAuthApiBaseUri warns on mismatch and prefers enrollment code', () {
    final config = EzkeyRunnerConfig(
      authApiBaseUri: Uri.parse('https://env.example.dev'),
      timeout: const Duration(seconds: 15),
      language: null,
      envFilePath: '.env',
      envFileFound: true,
    );

    final resolved = config.resolveAuthApiBaseUri('https://qr.example.dev');

    expect(resolved.uri, Uri.parse('https://qr.example.dev'));
    expect(resolved.warning, isNotNull);
  });
}