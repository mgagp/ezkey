import 'package:http/http.dart' as http;

import 'lab_http.dart';

/// Client for the stack's `ezkey-crypto-api` test oracle (JVM implementation of the same
/// primitives and canonical payloads). Used to prove byte-for-byte interoperability. Never use
/// it in an app: it generates and receives private keys over HTTP by design.
class CryptoOracleClient {
  CryptoOracleClient(Uri baseUri, {http.Client? client})
    : _http = LabHttp(baseUri, client: client);

  final LabHttp _http;

  /// `POST /api/v1/crypto/payload-helper`. [type] is one of `pending`, `respond`,
  /// `respond-result`, `enrollment-bind`, `enrollment-verify-device`, `enrollment-verify-result`,
  /// `enrollment-instance-info`.
  Future<String> payload(String type, Map<String, Object?> fields) async {
    final json = asJsonObject(
      await _http.post('/api/v1/crypto/payload-helper', <String, Object?>{
        'type': type,
        ...fields,
      }),
      'payload-helper',
    );
    return json['payload'] as String;
  }

  /// EC P-256 key pair: `privateKey` PKCS#8 and `publicKey` SPKI, both standard Base64.
  Future<({String privateKey, String publicKey})> ecKeyPair() async {
    final json = asJsonObject(
      await _http.get('/api/v1/crypto/keypair'),
      'keypair',
    );
    return (
      privateKey: json['privateKey'] as String,
      publicKey: json['publicKey'] as String,
    );
  }

  /// Ed25519 key pair as the server stores integration keys.
  Future<({String privateKey, String publicKey})> ed25519KeyPair() async {
    final json = asJsonObject(
      await _http.get('/api/v1/crypto/integration-keypair'),
      'integration-keypair',
    );
    return (
      privateKey: json['privateKey'] as String,
      publicKey: json['publicKey'] as String,
    );
  }

  Future<String> signEcdsa(String data, String privateKeyPkcs8) =>
      _sign('/sign', data, privateKeyPkcs8);

  Future<String> signEd25519(String data, String privateKey) =>
      _sign('/sign-ed25519', data, privateKey);

  Future<bool> validateEcdsa(String data, String signature, String publicKey) =>
      _validate('/validate', data, signature, publicKey);

  Future<bool> verifyEd25519(String data, String signature, String publicKey) =>
      _validate('/verify-ed25519', data, signature, publicKey);

  Future<String> _sign(String path, String data, String privateKey) async {
    final json = asJsonObject(
      await _http.post('/api/v1/crypto$path', <String, Object?>{
        'data': data,
        'privateKey': privateKey,
      }),
      path,
    );
    return json['signature'] as String;
  }

  Future<bool> _validate(
    String path,
    String data,
    String signature,
    String publicKey,
  ) async {
    final json = asJsonObject(
      await _http.post('/api/v1/crypto$path', <String, Object?>{
        'data': data,
        'signature': signature,
        'publicKey': publicKey,
      }),
      path,
    );
    return json['valid'] == true;
  }

  void close() => _http.close();
}
