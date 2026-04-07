import 'dart:convert';
import 'dart:typed_data';

import 'package:pointycastle/export.dart' as pc;

import 'encoding.dart';
import 'secure_random.dart';

const List<int> _ecP256SpkiPrefix = <int>[
  0x30,
  0x59,
  0x30,
  0x13,
  0x06,
  0x07,
  0x2a,
  0x86,
  0x48,
  0xce,
  0x3d,
  0x02,
  0x01,
  0x06,
  0x08,
  0x2a,
  0x86,
  0x48,
  0xce,
  0x3d,
  0x03,
  0x01,
  0x07,
  0x03,
  0x42,
  0x00,
];

final pc.ECDomainParameters _domainParameters = pc.ECDomainParameters(
  'prime256v1',
);

class EcP256KeyPair {
  EcP256KeyPair({required this.privateKey, required this.publicKey});

  final pc.ECPrivateKey privateKey;
  final pc.ECPublicKey publicKey;
}

class EcdsaDerSignature {
  EcdsaDerSignature(this.r, this.s);

  final BigInt r;
  final BigInt s;
}

EcP256KeyPair generateEcP256KeyPair() {
  final generator = pc.ECKeyGenerator();
  final random = pc.FortunaRandom()
    ..seed(pc.KeyParameter(generateSecureRandomBytes(32)));
  generator.init(
    pc.ParametersWithRandom<pc.ECKeyGeneratorParameters>(
      pc.ECKeyGeneratorParameters(_domainParameters),
      random,
    ),
  );
  final pair = generator.generateKeyPair();
  return EcP256KeyPair(privateKey: pair.privateKey, publicKey: pair.publicKey);
}

String ecPublicKeyToSpkiBase64(pc.ECPublicKey publicKey) {
  final point = publicKey.Q;
  if (point == null) {
    throw StateError('EC P-256 public key does not contain a public point');
  }
  final pointBytes = point.getEncoded(false);
  return base64StdEncodeBytes(<int>[..._ecP256SpkiPrefix, ...pointBytes]);
}

pc.ECPublicKey ecPublicKeyFromSpkiBase64(String base64Std) {
  final bytes = base64StdDecode(base64Std);
  if (bytes.length != _ecP256SpkiPrefix.length + 65) {
    throw FormatException('Unexpected EC P-256 SPKI length: ${bytes.length}');
  }
  for (var index = 0; index < _ecP256SpkiPrefix.length; index++) {
    if (bytes[index] != _ecP256SpkiPrefix[index]) {
      throw const FormatException('Unexpected EC P-256 SPKI prefix');
    }
  }

  final point = _domainParameters.curve.decodePoint(
    bytes.sublist(_ecP256SpkiPrefix.length),
  );
  if (point == null) {
    throw const FormatException('Unable to decode EC P-256 public point');
  }
  return pc.ECPublicKey(point, _domainParameters);
}

String signEcdsaSha256(pc.ECPrivateKey key, String data) {
  final signer = pc.Signer('SHA-256/ECDSA');
  final random = pc.FortunaRandom()
    ..seed(pc.KeyParameter(generateSecureRandomBytes(32)));
  signer.init(
    true,
    pc.ParametersWithRandom<pc.PrivateKeyParameter<pc.ECPrivateKey>>(
      pc.PrivateKeyParameter<pc.ECPrivateKey>(key),
      random,
    ),
  );
  final signature =
      signer.generateSignature(_utf8Bytes(data)) as pc.ECSignature;
  final lowS = _normalizeLowS(signature.s, key.parameters!.n);
  return base64StdEncodeBytes(_encodeEcdsaDer(signature.r, lowS));
}

bool verifyEcdsaSha256(
  pc.ECPublicKey key,
  String data,
  String signatureBase64Std,
) {
  final decoded = decodeEcdsaDerSignature(base64StdDecode(signatureBase64Std));
  final signer = pc.Signer('SHA-256/ECDSA');
  signer.init(false, pc.PublicKeyParameter<pc.ECPublicKey>(key));
  return signer.verifySignature(
    _utf8Bytes(data),
    pc.ECSignature(decoded.r, decoded.s),
  );
}

EcdsaDerSignature decodeEcdsaDerSignature(List<int> der) {
  if (der.length < 8 || der.first != 0x30) {
    throw const FormatException('Invalid ECDSA DER sequence');
  }
  var offset = 1;
  final sequenceLength = _readDerLength(der, offset);
  offset += _lengthFieldSize(der[offset]);
  if (offset + sequenceLength > der.length) {
    throw const FormatException('ECDSA DER sequence length exceeds buffer');
  }
  if (der[offset++] != 0x02) {
    throw const FormatException('Expected INTEGER for r');
  }
  final rLength = _readDerLength(der, offset);
  offset += _lengthFieldSize(der[offset]);
  final r = _decodeUnsignedBigInt(der.sublist(offset, offset + rLength));
  offset += rLength;
  if (der[offset++] != 0x02) {
    throw const FormatException('Expected INTEGER for s');
  }
  final sLength = _readDerLength(der, offset);
  offset += _lengthFieldSize(der[offset]);
  final s = _decodeUnsignedBigInt(der.sublist(offset, offset + sLength));
  return EcdsaDerSignature(r, s);
}

Uint8List _encodeEcdsaDer(BigInt r, BigInt s) {
  final rDer = _encodeInteger(r);
  final sDer = _encodeInteger(s);
  final content = <int>[...rDer, ...sDer];
  return Uint8List.fromList(<int>[
    0x30,
    ..._encodeDerLength(content.length),
    ...content,
  ]);
}

List<int> _encodeInteger(BigInt value) {
  var bytes = _encodeUnsignedBigInt(value);
  if (bytes.first & 0x80 != 0) {
    bytes = <int>[0x00, ...bytes];
  }
  return <int>[0x02, ..._encodeDerLength(bytes.length), ...bytes];
}

List<int> _encodeUnsignedBigInt(BigInt value) {
  if (value == BigInt.zero) {
    return <int>[0];
  }
  final result = <int>[];
  var current = value;
  while (current > BigInt.zero) {
    result.insert(0, (current & BigInt.from(0xff)).toInt());
    current >>= 8;
  }
  return result;
}

BigInt _decodeUnsignedBigInt(List<int> bytes) {
  var result = BigInt.zero;
  for (final byte in bytes) {
    result = (result << 8) | BigInt.from(byte);
  }
  return result;
}

List<int> _encodeDerLength(int value) {
  if (value < 0x80) {
    return <int>[value];
  }
  final bytes = _encodeUnsignedBigInt(BigInt.from(value));
  return <int>[0x80 | bytes.length, ...bytes];
}

int _readDerLength(List<int> bytes, int offset) {
  final first = bytes[offset];
  if (first & 0x80 == 0) {
    return first;
  }
  final count = first & 0x7f;
  var result = 0;
  for (var index = 0; index < count; index++) {
    result = (result << 8) | bytes[offset + index + 1];
  }
  return result;
}

int _lengthFieldSize(int firstByte) {
  return firstByte & 0x80 == 0 ? 1 : 1 + (firstByte & 0x7f);
}

BigInt _normalizeLowS(BigInt s, BigInt curveOrder) {
  final halfOrder = curveOrder >> 1;
  if (s > halfOrder) {
    return curveOrder - s;
  }
  return s;
}

Uint8List _utf8Bytes(String data) => Uint8List.fromList(utf8.encode(data));
