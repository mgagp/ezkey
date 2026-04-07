import 'dart:convert';
import 'dart:typed_data';

import 'package:cryptography/cryptography.dart';

import 'encoding.dart';

const List<int> _ed25519Pkcs8Prefix = <int>[
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

Future<bool> verifyEd25519(
  String payload,
  String signatureBase64Url,
  String publicKeyBase64Url,
) async {
  final publicKeyBytes = flexibleBase64Decode(publicKeyBase64Url);
  final signatureBytes = flexibleBase64Decode(signatureBase64Url);
  if (publicKeyBytes.length != 32 || signatureBytes.length != 64) {
    return false;
  }

  final algorithm = Ed25519();
  final publicKey = SimplePublicKey(publicKeyBytes, type: KeyPairType.ed25519);
  final signature = Signature(signatureBytes, publicKey: publicKey);
  return algorithm.verify(utf8.encode(payload), signature: signature);
}

Future<String> signEd25519WithPkcs8(String data, String pkcs8Base64Std) async {
  final pkcs8Bytes = base64StdDecode(pkcs8Base64Std);
  final seed = _extractEd25519Seed(pkcs8Bytes);
  final algorithm = Ed25519();
  final keyPair = await algorithm.newKeyPairFromSeed(seed);
  final signature = await algorithm.sign(utf8.encode(data), keyPair: keyPair);
  return base64UrlEncodeBytes(signature.bytes);
}

SimplePublicKey ed25519PublicKeyFromBase64Url(String base64Value) {
  final bytes = flexibleBase64Decode(base64Value);
  if (bytes.length != 32) {
    throw FormatException(
      'Expected 32-byte Ed25519 public key, got ${bytes.length}',
    );
  }
  return SimplePublicKey(bytes, type: KeyPairType.ed25519);
}

Uint8List _extractEd25519Seed(List<int> pkcs8Bytes) {
  if (pkcs8Bytes.length != _ed25519Pkcs8Prefix.length + 32) {
    throw FormatException(
      'Unexpected Ed25519 PKCS#8 length: ${pkcs8Bytes.length}',
    );
  }
  for (var index = 0; index < _ed25519Pkcs8Prefix.length; index++) {
    if (pkcs8Bytes[index] != _ed25519Pkcs8Prefix[index]) {
      throw const FormatException('Unexpected Ed25519 PKCS#8 prefix');
    }
  }
  return Uint8List.fromList(pkcs8Bytes.sublist(_ed25519Pkcs8Prefix.length));
}
