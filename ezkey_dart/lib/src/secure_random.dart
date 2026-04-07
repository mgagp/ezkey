import 'dart:math' as math;
import 'dart:typed_data';

import 'package:pointycastle/export.dart' as pc;

import 'encoding.dart';

Uint8List generateSecureRandomBytes(int length) {
  if (length < 1) {
    throw ArgumentError.value(length, 'length', 'Must be positive');
  }

  final seed = Uint8List.fromList(
    List<int>.generate(32, (_) => math.Random.secure().nextInt(256)),
  );
  final random = pc.FortunaRandom()..seed(pc.KeyParameter(seed));
  return random.nextBytes(length);
}

String generateProofToken() {
  final randomPart = base64UrlEncodeBytes(generateSecureRandomBytes(32));
  final saltPart = base64UrlEncodeBytes(generateSecureRandomBytes(16));
  return '$randomPart.$saltPart';
}
