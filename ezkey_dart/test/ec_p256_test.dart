import 'package:ezkey_dart/ezkey_dart.dart';
import 'package:test/test.dart';

void main() {
  test('generateEcP256KeyPair roundtrips through SPKI export/import', () {
    final keyPair = generateEcP256KeyPair();
    final spki = ecPublicKeyToSpkiBase64(keyPair.publicKey);
    final decoded = ecPublicKeyFromSpkiBase64(spki);

    expect(ecPublicKeyToSpkiBase64(decoded), spki);
  });

  test('signEcdsaSha256 and verifyEcdsaSha256 roundtrip', () {
    final keyPair = generateEcP256KeyPair();
    const payload = 'pending|true|Title|Message';
    final signature = signEcdsaSha256(keyPair.privateKey, payload);

    expect(verifyEcdsaSha256(keyPair.publicKey, payload, signature), isTrue);
  });

  test('signEcdsaSha256 emits low-S signatures', () {
    final keyPair = generateEcP256KeyPair();
    final signature = signEcdsaSha256(keyPair.privateKey, 'payload');
    final decoded = decodeEcdsaDerSignature(base64StdDecode(signature));
    final halfOrder = keyPair.privateKey.parameters!.n >> 1;

    expect(decoded.s <= halfOrder, isTrue);
  });

  test('verifyEcdsaSha256 rejects modified DER signatures', () {
    final keyPair = generateEcP256KeyPair();
    final signature = signEcdsaSha256(keyPair.privateKey, 'payload');
    final bytes = base64StdDecode(signature);
    bytes[bytes.length - 1] ^= 0x01;
    final modified = base64StdEncodeBytes(bytes);

    expect(verifyEcdsaSha256(keyPair.publicKey, 'payload', modified), isFalse);
  });
}
