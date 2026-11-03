import 'package:ezkey_dart/ezkey_dart.dart';
import 'package:pointycastle/export.dart' as pc;

final BigInt _n = pc.ECCurve_secp256r1().n;

/// Returns the high-S twin `(r, n - s)` of a low-S DER signature (what Android Keystore returns
/// about half of the time). Both verify; the Auth API only accepts the low-S one.
String toHighS(String lowSBase64Std) {
  final decoded = decodeEcdsaDerSignature(base64StdDecode(lowSBase64Std));
  final s = decoded.s > _n >> 1 ? decoded.s : _n - decoded.s;
  return base64StdEncodeBytes(<int>[
    ..._tlv(0x30, <int>[..._integer(decoded.r), ..._integer(s)]),
  ]);
}

List<int> _integer(BigInt value) {
  final bytes = <int>[];
  var current = value;
  while (current > BigInt.zero) {
    bytes.insert(0, (current & BigInt.from(0xff)).toInt());
    current >>= 8;
  }
  if (bytes.first & 0x80 != 0) {
    bytes.insert(0, 0);
  }
  return _tlv(0x02, bytes);
}

List<int> _tlv(int tag, List<int> content) {
  assert(content.length < 0x80);
  return <int>[tag, content.length, ...content];
}

/// Key store that behaves like a platform keystore returning only high-S signatures, or a fixed
/// [overrideSignature] when set.
class HighSKeyStore implements EzkeyDeviceKeyStore {
  final SoftwareDeviceKeyStore _inner = SoftwareDeviceKeyStore();

  /// When non-null, returned verbatim by [sign] (e.g. a malformed signature).
  String? overrideSignature;

  int signCount = 0;

  @override
  DevicePrivateKeyStorageTier get storageTier => _inner.storageTier;

  @override
  Future<String> generateKeyPair(String alias) => _inner.generateKeyPair(alias);

  @override
  Future<String> sign(String alias, String payload) async {
    signCount++;
    return overrideSignature ?? toHighS(await _inner.sign(alias, payload));
  }

  @override
  Future<String?> publicKey(String alias) => _inner.publicKey(alias);

  @override
  Future<void> deleteKeyPair(String alias) => _inner.deleteKeyPair(alias);
}
