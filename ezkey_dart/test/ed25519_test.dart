import 'dart:typed_data';

import 'package:cryptography/cryptography.dart';
import 'package:ezkey_dart/ezkey_dart.dart';
import 'package:test/test.dart';

const List<int> _pkcs8Prefix = <int>[
  0x30,
  0x2e,
  0x02,
  0x01,
  0x00,
  0x30,
  0x05,
  0x06,
  0x03,
  0x2b,
  0x65,
  0x70,
  0x04,
  0x22,
  0x04,
  0x20,
];

void main() {
  test('signEd25519WithPkcs8 and verifyEd25519 roundtrip', () async {
    final seed = Uint8List.fromList(
      List<int>.generate(32, (index) => index + 1),
    );
    final pkcs8 = base64StdEncodeBytes(<int>[..._pkcs8Prefix, ...seed]);
    const payload = 'tok|true|Title|Msg';

    final signature = await signEd25519WithPkcs8(payload, pkcs8);
    final publicKey = await Ed25519()
        .newKeyPairFromSeed(seed)
        .then((pair) => pair.extractPublicKey());
    final publicKeyBase64 = base64UrlEncodeBytes(publicKey.bytes);

    expect(await verifyEd25519(payload, signature, publicKeyBase64), isTrue);
    expect(
      await verifyEd25519('$payload!', signature, publicKeyBase64),
      isFalse,
    );
  });

  test(
    'ed25519PublicKeyFromBase64Url accepts standard Base64 through flexible decode',
    () {
      final raw = Uint8List.fromList(List<int>.generate(32, (index) => index));
      final standardBase64 = base64StdEncodeBytes(raw);

      final publicKey = ed25519PublicKeyFromBase64Url(standardBase64);
      expect(publicKey.bytes, raw);
    },
  );
}
