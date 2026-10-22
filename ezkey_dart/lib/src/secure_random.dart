import 'dart:math' as math;
import 'dart:typed_data';

import 'package:pointycastle/export.dart' as pc;

import 'encoding.dart';

final math.Random _osRandom = math.Random.secure();

/// Returns [length] bytes from the platform CSPRNG (`Random.secure()`).
///
/// Throws [UnsupportedError] (from `dart:math`) on platforms without a secure source. Never
/// substitute `Random()` here: proof tokens and key seeds depend on unpredictability.
Uint8List generateSecureRandomBytes(int length) {
  if (length < 1) {
    throw ArgumentError.value(length, 'length', 'Must be positive');
  }
  return Uint8List.fromList(
    List<int>.generate(length, (_) => _osRandom.nextInt(256)),
  );
}

/// A pointycastle [pc.SecureRandom] seeded from the platform CSPRNG, for key generation.
pc.SecureRandom newSeededSecureRandom() {
  return pc.FortunaRandom()
    ..seed(pc.KeyParameter(generateSecureRandomBytes(32)));
}

/// Proof token in the server wire format: `base64url(32 random bytes).base64url(16 random bytes)`,
/// no padding (same as `SignatureService.generateProofToken()` in Java).
String generateProofToken() {
  final randomPart = base64UrlEncodeBytes(generateSecureRandomBytes(32));
  final saltPart = base64UrlEncodeBytes(generateSecureRandomBytes(16));
  return '$randomPart.$saltPart';
}
