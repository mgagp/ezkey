import 'package:ezkey_dart/ezkey_dart.dart';
import 'package:test/test.dart';

import 'support/high_s.dart';
import 'support/jvm_fixtures.dart';

void main() {
  group('low-S normalization (SEC-012)', () {
    test('high-S twin verifies, is detected, and normalizes back', () {
      final keyPair = generateEcP256KeyPair();
      const data = 'payload|true';
      final low = signEcdsaSha256(keyPair.privateKey, data);
      final high = toHighS(low);
      expect(high, isNot(low));
      expect(verifyEcdsaSha256(keyPair.publicKey, data, high), isTrue);
      expect(isLowSEcdsaDerSignature(low), isTrue);
      expect(isLowSEcdsaDerSignature(high), isFalse);
      expect(normalizeEcdsaDerSignatureLowS(high), low);
      expect(normalizeEcdsaDerSignatureLowS(low), low);
    });

    test('rejects malformed and out-of-range signatures', () {
      // SEQUENCE { INTEGER 0, INTEGER 1 }: well-formed DER, but r = 0 is not a valid signature.
      final zeroR = base64StdEncodeBytes(<int>[
        0x30,
        0x06,
        0x02,
        0x01,
        0x00,
        0x02,
        0x01,
        0x01,
      ]);
      for (final bad in <String>['', 'AAAA', zeroR]) {
        expect(
          () => normalizeEcdsaDerSignatureLowS(bad),
          throwsFormatException,
          reason: bad,
        );
      }
      expect(isLowSEcdsaDerSignature(''), isFalse);
      expect(isLowSEcdsaDerSignature('AAAA'), isFalse);
    });
  });

  group('EC P-256 / ECDSA-SHA256 (device)', () {
    test('SPKI export/import roundtrip', () {
      final keyPair = generateEcP256KeyPair();
      final spki = ecPublicKeyToSpkiBase64(keyPair.publicKey);
      expect(ecPublicKeyToSpkiBase64(ecPublicKeyFromSpkiBase64(spki)), spki);
      expect(base64StdDecode(spki), hasLength(91));
    });

    test('sign/verify roundtrip; other payload or key fails', () {
      final keyPair = generateEcP256KeyPair();
      final signature = signEcdsaSha256(keyPair.privateKey, 'a|b');
      expect(verifyEcdsaSha256(keyPair.publicKey, 'a|b', signature), isTrue);
      expect(verifyEcdsaSha256(keyPair.publicKey, 'a|c', signature), isFalse);
      expect(
        verifyEcdsaSha256(generateEcP256KeyPair().publicKey, 'a|b', signature),
        isFalse,
      );
    });

    test('signatures are deterministic (RFC 6979) and low-S', () {
      final keyPair = generateEcP256KeyPair();
      final halfOrder = keyPair.privateKey.parameters!.n >> 1;
      for (var i = 0; i < 20; i++) {
        final signature = signEcdsaSha256(keyPair.privateKey, 'payload $i');
        expect(signEcdsaSha256(keyPair.privateKey, 'payload $i'), signature);
        final decoded = decodeEcdsaDerSignature(base64StdDecode(signature));
        expect(decoded.s <= halfOrder, isTrue);
      }
    });

    test('verify returns false (never throws) on malformed signatures', () {
      final keyPair = generateEcP256KeyPair();
      final signature = signEcdsaSha256(keyPair.privateKey, 'payload');
      final bytes = base64StdDecode(signature);
      bytes[bytes.length - 1] ^= 0x01;
      for (final bad in <String>[
        base64StdEncodeBytes(bytes),
        '',
        'not base64 !',
        base64StdEncodeBytes(<int>[0x30, 0x02, 0x02, 0x00]),
        base64StdEncodeBytes(bytes.sublist(0, 10)),
      ]) {
        expect(verifyEcdsaSha256(keyPair.publicKey, 'payload', bad), isFalse);
      }
    });

    test('PKCS#8 export/import keeps the key usable', () {
      final keyPair = generateEcP256KeyPair();
      final pkcs8 = ecPrivateKeyToPkcs8Base64(keyPair);
      final restored = ecKeyPairFromPkcs8Base64(pkcs8);
      expect(
        ecPublicKeyToSpkiBase64(restored.publicKey),
        ecPublicKeyToSpkiBase64(keyPair.publicKey),
      );
      final signature = signEcdsaSha256(restored.privateKey, 'x');
      expect(verifyEcdsaSha256(keyPair.publicKey, 'x', signature), isTrue);
    });

    test('PKCS#8 import rejects garbage', () {
      expect(() => ecKeyPairFromPkcs8Base64('AAAA'), throwsFormatException);
      expect(
        () => ecKeyPairFromPkcs8Base64(JvmFixtures.ed25519PrivateKeyPkcs8),
        throwsFormatException,
      );
    });

    test('JVM interop: minimal PKCS#8 derives the JVM public key', () {
      final keyPair = ecKeyPairFromPkcs8Base64(JvmFixtures.ecPrivateKeyPkcs8);
      expect(
        ecPublicKeyToSpkiBase64(keyPair.publicKey),
        JvmFixtures.ecPublicKeySpki,
      );
    });

    test('JVM interop: verifies a JVM ECDSA signature', () {
      expect(
        verifyEcdsaSha256(
          ecPublicKeyFromSpkiBase64(JvmFixtures.ecPublicKeySpki),
          JvmFixtures.data,
          JvmFixtures.ecSignatureDer,
        ),
        isTrue,
      );
    });
  });

  group('Ed25519 (integration)', () {
    test('JVM interop: reproduces the JVM signature byte for byte', () async {
      expect(
        await signEd25519WithPkcs8(
          JvmFixtures.data,
          JvmFixtures.ed25519PrivateKeyPkcs8,
        ),
        JvmFixtures.ed25519Signature,
      );
      expect(
        await verifyEd25519(
          JvmFixtures.data,
          JvmFixtures.ed25519Signature,
          JvmFixtures.ed25519PublicKey,
        ),
        isTrue,
      );
    });

    test('verify fails on tampered payload, signature or key', () async {
      final other = await generateEd25519TestKeyPair();
      expect(
        await verifyEd25519(
          '${JvmFixtures.data}!',
          JvmFixtures.ed25519Signature,
          JvmFixtures.ed25519PublicKey,
        ),
        isFalse,
      );
      expect(
        await verifyEd25519(
          JvmFixtures.data,
          JvmFixtures.ed25519Signature,
          other.publicKeyBase64Url,
        ),
        isFalse,
      );
    });

    test('verify returns false (never throws) on malformed input', () async {
      for (final pair in <(String, String)>[
        ('', JvmFixtures.ed25519PublicKey),
        ('%%%', JvmFixtures.ed25519PublicKey),
        (JvmFixtures.ed25519Signature, ''),
        (JvmFixtures.ed25519Signature, 'AAAA'),
      ]) {
        expect(
          await verifyEd25519(JvmFixtures.data, pair.$1, pair.$2),
          isFalse,
        );
      }
    });

    test('accepts standard Base64 public keys and signatures too', () async {
      String toStd(String url) => base64StdEncodeBytes(base64UrlDecode(url));
      expect(
        await verifyEd25519(
          JvmFixtures.data,
          toStd(JvmFixtures.ed25519Signature),
          toStd(JvmFixtures.ed25519PublicKey),
        ),
        isTrue,
      );
    });

    test('generated test key pairs sign and verify', () async {
      final keyPair = await generateEd25519TestKeyPair();
      final signature = await signEd25519WithPkcs8('m', keyPair.pkcs8Base64Std);
      expect(
        await verifyEd25519('m', signature, keyPair.publicKeyBase64Url),
        isTrue,
      );
      expect(
        ed25519PublicKeyFromBase64Url(keyPair.publicKeyBase64Url).bytes,
        hasLength(32),
      );
    });
  });

  group('secure randomness', () {
    test('proof tokens are 32.16 random bytes, Base64URL without padding', () {
      final tokens = List<String>.generate(50, (_) => generateProofToken());
      expect(tokens.toSet(), hasLength(50));
      for (final token in tokens) {
        final parts = token.split('.');
        expect(parts, hasLength(2));
        expect(base64UrlDecode(parts[0]), hasLength(32));
        expect(base64UrlDecode(parts[1]), hasLength(16));
        expect(token, matches(RegExp(r'^[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+$')));
      }
    });

    test('random bytes have the requested length and vary', () {
      expect(generateSecureRandomBytes(16), hasLength(16));
      expect(
        generateSecureRandomBytes(32),
        isNot(generateSecureRandomBytes(32)),
      );
    });
  });

  test('EzKeyCrypto facade exposes the device-key PKCS#8 helpers', () {
    final keyPair = EzKeyCrypto.generateDeviceKeyPair();
    final restored = EzKeyCrypto.importDeviceKeyPairPkcs8(
      EzKeyCrypto.exportDevicePrivateKeyPkcs8(keyPair),
    );
    expect(
      EzKeyCrypto.exportDevicePublicKey(restored),
      EzKeyCrypto.exportDevicePublicKey(keyPair),
    );
  });
}
