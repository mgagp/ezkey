import 'dart:convert';
import 'dart:typed_data';

import 'package:cryptography/cryptography.dart';

import 'encoding.dart';
import 'secure_random.dart';

const List<int> _ed25519Pkcs8Prefix = <int>[
  0x30, 0x2e, 0x02, 0x01, 0x00, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65, 0x70, //
  0x04, 0x22, 0x04, 0x20,
];

/// Verifies an integration Ed25519 signature over the exact UTF-8 bytes of [payload].
///
/// Public key: raw 32 bytes; signature: raw 64 bytes; both Base64URL (no padding) on the wire,
/// standard Base64 also accepted. Returns `false` (never throws) for malformed input so callers
/// can fail closed uniformly.
Future<bool> verifyEd25519(
  String payload,
  String signatureBase64Url,
  String publicKeyBase64Url,
) async {
  final Uint8List publicKeyBytes;
  final Uint8List signatureBytes;
  try {
    publicKeyBytes = flexibleBase64Decode(publicKeyBase64Url);
    signatureBytes = flexibleBase64Decode(signatureBase64Url);
  } on FormatException {
    return false;
  }
  if (publicKeyBytes.length != 32 || signatureBytes.length != 64) {
    return false;
  }

  final algorithm = Ed25519();
  final publicKey = SimplePublicKey(publicKeyBytes, type: KeyPairType.ed25519);
  final signature = Signature(signatureBytes, publicKey: publicKey);
  return algorithm.verify(utf8.encode(payload), signature: signature);
}

/// Signs [data] with an Ed25519 PKCS#8 private key (standard Base64) and returns a Base64URL
/// signature. Integration-side operation: used by tests and lab fakes, never by authenticators.
Future<String> signEd25519WithPkcs8(String data, String pkcs8Base64Std) async {
  final pkcs8Bytes = base64StdDecode(pkcs8Base64Std);
  final seed = _extractEd25519Seed(pkcs8Bytes);
  final algorithm = Ed25519();
  final keyPair = await algorithm.newKeyPairFromSeed(seed);
  final signature = await algorithm.sign(utf8.encode(data), keyPair: keyPair);
  return base64UrlEncodeBytes(signature.bytes);
}

/// Integration-style Ed25519 key material for tests and lab fakes.
class Ed25519TestKeyPair {
  Ed25519TestKeyPair({
    required this.pkcs8Base64Std,
    required this.publicKeyBase64Url,
  });

  /// PKCS#8 private key, standard Base64 (server storage format).
  final String pkcs8Base64Std;

  /// Raw 32-byte public key, Base64URL without padding (wire format).
  final String publicKeyBase64Url;
}

/// Generates an Ed25519 key pair in the server's wire formats. For tests and fakes only.
Future<Ed25519TestKeyPair> generateEd25519TestKeyPair() async {
  final seed = generateSecureRandomBytes(32);
  final keyPair = await Ed25519().newKeyPairFromSeed(seed);
  final publicKey = await keyPair.extractPublicKey();
  return Ed25519TestKeyPair(
    pkcs8Base64Std: base64StdEncodeBytes(<int>[
      ..._ed25519Pkcs8Prefix,
      ...seed,
    ]),
    publicKeyBase64Url: base64UrlEncodeBytes(publicKey.bytes),
  );
}

/// Parses a raw 32-byte Ed25519 public key from Base64URL or standard Base64.
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
