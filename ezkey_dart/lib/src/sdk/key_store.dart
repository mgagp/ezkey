import '../ec_p256.dart';
import 'errors.dart';
import 'models.dart';

/// Hook for device key custody. One EC P-256 key pair per enrollment, addressed by alias.
///
/// A Flutter app should implement this on top of the platform keystore so the private key never
/// enters Dart memory:
///
/// * Android: Android Keystore `KeyPairGenerator("EC")` with `setAlgorithmParameterSpec(
///   ECGenParameterSpec("secp256r1"))`, `PURPOSE_SIGN`, `DIGEST_SHA256`, `setIsStrongBoxBacked(true)`
///   when available; sign with `Signature.getInstance("SHA256withECDSA")` (output is already DER).
/// * iOS: `SecKeyCreateRandomKey` with `kSecAttrKeyTypeECSECPrimeRandom`, 256 bits,
///   `kSecAttrTokenIDSecureEnclave`; sign with `.ecdsaSignatureMessageX962SHA256` (DER). Export
///   the public key with `SecKeyCopyExternalRepresentation` (65-byte X9.63 point) and prepend the
///   P-256 SPKI header to get the wire format.
///
/// Contract:
/// * [generateKeyPair] creates (or replaces) the key for [alias] and returns the public key as
///   X.509 SubjectPublicKeyInfo, **standard** Base64.
/// * [sign] signs the UTF-8 bytes of the payload with ECDSA over SHA-256 and returns the ASN.1
///   DER signature, **standard** Base64. It must hash the message itself (do not pre-hash).
/// * [storageTier] is reported to the server as client metadata.
abstract interface class EzkeyDeviceKeyStore {
  DevicePrivateKeyStorageTier get storageTier;

  Future<String> generateKeyPair(String alias);

  Future<String> sign(String alias, String payload);

  /// Returns the SPKI public key for [alias], or `null` when no key exists.
  Future<String?> publicKey(String alias);

  Future<void> deleteKeyPair(String alias);
}

/// Pure-Dart software key store (keys in process memory).
///
/// For tests, CLIs and labs. Reports [DevicePrivateKeyStorageTier.none]. Do not use it in a
/// production authenticator: keys are extractable and lost on restart unless exported.
class SoftwareDeviceKeyStore implements EzkeyDeviceKeyStore {
  final Map<String, EcP256KeyPair> _keys = <String, EcP256KeyPair>{};

  @override
  DevicePrivateKeyStorageTier get storageTier =>
      DevicePrivateKeyStorageTier.none;

  @override
  Future<String> generateKeyPair(String alias) async {
    final keyPair = generateEcP256KeyPair();
    _keys[alias] = keyPair;
    return ecPublicKeyToSpkiBase64(keyPair.publicKey);
  }

  @override
  Future<String> sign(String alias, String payload) async {
    final keyPair = _keys[alias];
    if (keyPair == null) {
      throw EzkeyLocalStateException('No device key for alias "$alias"');
    }
    return signEcdsaSha256(keyPair.privateKey, payload);
  }

  @override
  Future<String?> publicKey(String alias) async {
    final keyPair = _keys[alias];
    return keyPair == null ? null : ecPublicKeyToSpkiBase64(keyPair.publicKey);
  }

  @override
  Future<void> deleteKeyPair(String alias) async {
    _keys.remove(alias);
  }

  /// Imports a PKCS#8 (standard Base64) private key, e.g. one written by the Ezkey Demo Device.
  /// Returns the derived SPKI public key.
  String importPkcs8(String alias, String pkcs8Base64Std) {
    final keyPair = ecKeyPairFromPkcs8Base64(pkcs8Base64Std);
    _keys[alias] = keyPair;
    return ecPublicKeyToSpkiBase64(keyPair.publicKey);
  }

  /// Exports the private key as PKCS#8 (standard Base64). Lab persistence only.
  String exportPkcs8(String alias) {
    final keyPair = _keys[alias];
    if (keyPair == null) {
      throw EzkeyLocalStateException('No device key for alias "$alias"');
    }
    return ecPrivateKeyToPkcs8Base64(keyPair);
  }

  Iterable<String> get aliases => _keys.keys;
}
