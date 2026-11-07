import 'dart:convert';
import 'dart:typed_data';

import 'package:pointycastle/export.dart' as pc;

import 'encoding.dart';
import 'secure_random.dart';

/// DER prefix of an X.509 SubjectPublicKeyInfo for an uncompressed P-256 point
/// (`id-ecPublicKey` + `prime256v1`, BIT STRING of 66 bytes).
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
  0x01, //
  0x06, 0x08, 0x2a, 0x86, 0x48, 0xce, 0x3d, 0x03, 0x01, 0x07, 0x03, 0x42, 0x00,
];

/// AlgorithmIdentifier SEQUENCE { id-ecPublicKey, prime256v1 }.
const List<int> _ecP256AlgorithmIdentifier = <int>[
  0x30, 0x13, 0x06, 0x07, 0x2a, 0x86, 0x48, 0xce, 0x3d, 0x02, 0x01, //
  0x06, 0x08, 0x2a, 0x86, 0x48, 0xce, 0x3d, 0x03, 0x01, 0x07,
];

final pc.ECDomainParameters _domainParameters = pc.ECDomainParameters(
  'prime256v1',
);

/// A software EC P-256 key pair. Production Flutter apps should keep the private key inside the
/// platform keystore instead (see `EzkeyDeviceKeyStore`).
class EcP256KeyPair {
  EcP256KeyPair({required this.privateKey, required this.publicKey});

  final pc.ECPrivateKey privateKey;
  final pc.ECPublicKey publicKey;
}

/// Decoded `r` / `s` integers of an ASN.1 DER ECDSA signature.
class EcdsaDerSignature {
  EcdsaDerSignature(this.r, this.s);

  final BigInt r;
  final BigInt s;
}

/// Generates a P-256 key pair from the platform CSPRNG.
EcP256KeyPair generateEcP256KeyPair() {
  final generator = pc.ECKeyGenerator();
  generator.init(
    pc.ParametersWithRandom<pc.ECKeyGeneratorParameters>(
      pc.ECKeyGeneratorParameters(_domainParameters),
      newSeededSecureRandom(),
    ),
  );
  final pair = generator.generateKeyPair();
  return EcP256KeyPair(privateKey: pair.privateKey, publicKey: pair.publicKey);
}

/// Standard Base64 X.509 SPKI of [publicKey] (the `devicePublicKey` wire format).
String ecPublicKeyToSpkiBase64(pc.ECPublicKey publicKey) {
  final point = publicKey.Q;
  if (point == null) {
    throw StateError('EC P-256 public key does not contain a public point');
  }
  final pointBytes = point.getEncoded(false);
  return base64StdEncodeBytes(<int>[..._ecP256SpkiPrefix, ...pointBytes]);
}

/// Parses a standard Base64 X.509 SPKI for an uncompressed P-256 point.
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

/// Exports [keyPair] as standard Base64 PKCS#8 (RFC 5208 wrapping an RFC 5915 ECPrivateKey with
/// the public key), the format Java `KeyFactory`/`PKCS8EncodedKeySpec` reads.
String ecPrivateKeyToPkcs8Base64(EcP256KeyPair keyPair) {
  final d = keyPair.privateKey.d;
  final q = keyPair.publicKey.Q;
  if (d == null || q == null) {
    throw StateError('EC P-256 key pair is incomplete');
  }
  final ecPrivateKey = _tlv(0x30, <int>[
    ..._tlv(0x02, <int>[0x01]),
    ..._tlv(0x04, _fixedLength(d, 32)),
    ..._tlv(0xa1, _tlv(0x03, <int>[0x00, ...q.getEncoded(false)])),
  ]);
  final pkcs8 = _tlv(0x30, <int>[
    ..._tlv(0x02, <int>[0x00]),
    ..._ecP256AlgorithmIdentifier,
    ..._tlv(0x04, ecPrivateKey),
  ]);
  return base64StdEncodeBytes(pkcs8);
}

/// Imports a standard Base64 PKCS#8 P-256 private key (with or without the optional embedded
/// public key) and derives the matching public key.
EcP256KeyPair ecKeyPairFromPkcs8Base64(String base64Std) {
  final reader = _DerReader(base64StdDecode(base64Std));
  final outer = reader.expect(0x30);
  final version = outer.expect(0x02);
  if (version.bytes.length != 1 || version.bytes.first != 0) {
    throw const FormatException('Unsupported PKCS#8 version');
  }
  final algorithm = outer.expect(0x30);
  final algorithmBytes = <int>[0x30, 0x13, ...algorithm.bytes];
  if (!_listEquals(algorithmBytes, _ecP256AlgorithmIdentifier)) {
    throw const FormatException('PKCS#8 key is not an EC P-256 key');
  }
  final privateKeyOctets = outer.expect(0x04);
  final ecPrivateKey = _DerReader(privateKeyOctets.bytes).expect(0x30);
  final ecVersion = ecPrivateKey.expect(0x02);
  if (ecVersion.bytes.length != 1 || ecVersion.bytes.first != 1) {
    throw const FormatException('Unsupported ECPrivateKey version');
  }
  final scalar = ecPrivateKey.expect(0x04);
  final d = _decodeUnsignedBigInt(scalar.bytes);
  if (d <= BigInt.zero || d >= _domainParameters.n) {
    throw const FormatException('EC P-256 private scalar out of range');
  }
  final q = _domainParameters.G * d;
  return EcP256KeyPair(
    privateKey: pc.ECPrivateKey(d, _domainParameters),
    publicKey: pc.ECPublicKey(q, _domainParameters),
  );
}

/// Signs the UTF-8 bytes of [data] with ECDSA-SHA256 and returns standard Base64 ASN.1 DER.
///
/// Uses deterministic nonces (RFC 6979) so signing never depends on runtime RNG quality, and
/// normalizes to low-S for parity with Android Keystore / Conscrypt verifiers.
String signEcdsaSha256(pc.ECPrivateKey key, String data) {
  final signer = pc.ECDSASigner(
    pc.SHA256Digest(),
    pc.HMac(pc.SHA256Digest(), 64),
  );
  signer.init(true, pc.PrivateKeyParameter<pc.ECPrivateKey>(key));
  final signature =
      signer.generateSignature(_utf8Bytes(data)) as pc.ECSignature;
  final lowS = _normalizeLowS(signature.s, key.parameters!.n);
  return base64StdEncodeBytes(_encodeEcdsaDer(signature.r, lowS));
}

/// Verifies a standard Base64 DER ECDSA-SHA256 signature over the UTF-8 bytes of [data].
///
/// Returns `false` for malformed signatures instead of throwing.
bool verifyEcdsaSha256(
  pc.ECPublicKey key,
  String data,
  String signatureBase64Std,
) {
  final EcdsaDerSignature decoded;
  try {
    decoded = decodeEcdsaDerSignature(base64StdDecode(signatureBase64Std));
  } on FormatException {
    return false;
  } on RangeError {
    return false;
  }
  final signer = pc.Signer('SHA-256/ECDSA');
  signer.init(false, pc.PublicKeyParameter<pc.ECPublicKey>(key));
  return signer.verifySignature(
    _utf8Bytes(data),
    pc.ECSignature(decoded.r, decoded.s),
  );
}

/// Returns [signatureBase64Std] (standard Base64 ASN.1 DER ECDSA P-256) with `s` normalized to
/// the lower half of the curve order, re-encoded as minimal DER.
///
/// The Auth API rejects high-S signatures (malleability, SEC-012), and platform keystores such as
/// Android Keystore return high-S for about half of all signatures. `(r, n - s)` is the same valid
/// signature, so normalizing never needs the private key. Throws [FormatException] on malformed
/// input.
String normalizeEcdsaDerSignatureLowS(String signatureBase64Std) {
  final EcdsaDerSignature decoded;
  try {
    decoded = decodeEcdsaDerSignature(base64StdDecode(signatureBase64Std));
  } on RangeError {
    throw const FormatException('Malformed ECDSA DER signature');
  }
  final n = _domainParameters.n;
  if (decoded.r <= BigInt.zero ||
      decoded.r >= n ||
      decoded.s <= BigInt.zero ||
      decoded.s >= n) {
    throw const FormatException('ECDSA signature component out of range');
  }
  return base64StdEncodeBytes(
    _encodeEcdsaDer(decoded.r, _normalizeLowS(decoded.s, n)),
  );
}

/// Whether the standard Base64 DER ECDSA P-256 signature has `s` in the lower half of the curve
/// order (what the Auth API accepts). Returns `false` for malformed input.
bool isLowSEcdsaDerSignature(String signatureBase64Std) {
  try {
    final decoded = decodeEcdsaDerSignature(
      base64StdDecode(signatureBase64Std),
    );
    return decoded.s <= _domainParameters.n >> 1;
  } on FormatException {
    return false;
  } on RangeError {
    return false;
  }
}

/// Decodes an ASN.1 DER `SEQUENCE { INTEGER r, INTEGER s }`.
EcdsaDerSignature decodeEcdsaDerSignature(List<int> der) {
  final sequence = _DerReader(der).expect(0x30);
  final r = _decodeUnsignedBigInt(sequence.expect(0x02).bytes);
  final s = _decodeUnsignedBigInt(sequence.expect(0x02).bytes);
  return EcdsaDerSignature(r, s);
}

Uint8List _encodeEcdsaDer(BigInt r, BigInt s) {
  return Uint8List.fromList(
    _tlv(0x30, <int>[..._encodeInteger(r), ..._encodeInteger(s)]),
  );
}

List<int> _encodeInteger(BigInt value) {
  var bytes = _encodeUnsignedBigInt(value);
  if (bytes.first & 0x80 != 0) {
    bytes = <int>[0x00, ...bytes];
  }
  return _tlv(0x02, bytes);
}

List<int> _tlv(int tag, List<int> content) {
  return <int>[tag, ..._encodeDerLength(content.length), ...content];
}

List<int> _fixedLength(BigInt value, int length) {
  final bytes = _encodeUnsignedBigInt(value);
  if (bytes.length > length) {
    throw StateError('Integer does not fit in $length bytes');
  }
  return <int>[...List<int>.filled(length - bytes.length, 0), ...bytes];
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

BigInt _normalizeLowS(BigInt s, BigInt curveOrder) {
  final halfOrder = curveOrder >> 1;
  if (s > halfOrder) {
    return curveOrder - s;
  }
  return s;
}

bool _listEquals(List<int> a, List<int> b) {
  if (a.length != b.length) {
    return false;
  }
  for (var index = 0; index < a.length; index++) {
    if (a[index] != b[index]) {
      return false;
    }
  }
  return true;
}

Uint8List _utf8Bytes(String data) => Uint8List.fromList(utf8.encode(data));

/// Minimal bounds-checked DER TLV reader (definite lengths only).
class _DerReader {
  _DerReader(List<int> bytes) : _bytes = bytes;

  final List<int> _bytes;
  int _offset = 0;

  _DerValue expect(int tag) {
    if (_offset >= _bytes.length) {
      throw const FormatException('Truncated DER structure');
    }
    final actual = _bytes[_offset++];
    if (actual != tag) {
      throw FormatException(
        'Unexpected DER tag 0x${actual.toRadixString(16)} (expected 0x${tag.toRadixString(16)})',
      );
    }
    final length = _readLength();
    if (_offset + length > _bytes.length) {
      throw const FormatException('DER length exceeds buffer');
    }
    final value = _bytes.sublist(_offset, _offset + length);
    _offset += length;
    return _DerValue(value);
  }

  int _readLength() {
    if (_offset >= _bytes.length) {
      throw const FormatException('Truncated DER length');
    }
    final first = _bytes[_offset++];
    if (first & 0x80 == 0) {
      return first;
    }
    final count = first & 0x7f;
    if (count == 0 || count > 3 || _offset + count > _bytes.length) {
      throw const FormatException('Unsupported DER length encoding');
    }
    var result = 0;
    for (var index = 0; index < count; index++) {
      result = (result << 8) | _bytes[_offset++];
    }
    return result;
  }
}

class _DerValue {
  _DerValue(this.bytes) : _reader = _DerReader(bytes);

  final List<int> bytes;
  final _DerReader _reader;

  _DerValue expect(int tag) => _reader.expect(tag);
}
