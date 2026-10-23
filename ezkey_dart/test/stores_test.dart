import 'dart:io';

import 'package:ezkey_dart/ezkey_dart.dart';
import 'package:ezkey_dart/lab.dart';
import 'package:test/test.dart';

import 'support/jvm_fixtures.dart';

EzkeyEnrollment _enrollment(int id) => EzkeyEnrollment(
  authApiBaseUrl: 'https://auth.example.org',
  enrollmentId: id,
  enrollmentProofToken: 'proof-$id',
  integrationPublicKey: 'pk',
  integrationKeyAlgorithm: 'ed25519',
  deviceKeyAlias: 'alias-$id',
  devicePublicKey: 'spki',
  devicePrivateKeyStorageTier: DevicePrivateKeyStorageTier.none,
  enrolledAt: DateTime.utc(2026),
);

void main() {
  group('SoftwareDeviceKeyStore', () {
    test('generate, sign, verify, delete', () async {
      final store = SoftwareDeviceKeyStore();
      expect(store.storageTier, DevicePrivateKeyStorageTier.none);
      final spki = await store.generateKeyPair('a');
      expect(await store.publicKey('a'), spki);
      final signature = await store.sign('a', 'payload');
      expect(
        verifyEcdsaSha256(
          ecPublicKeyFromSpkiBase64(spki),
          'payload',
          signature,
        ),
        isTrue,
      );
      await store.deleteKeyPair('a');
      expect(await store.publicKey('a'), isNull);
      await expectLater(
        store.sign('a', 'x'),
        throwsA(isA<EzkeyLocalStateException>()),
      );
    });

    test('imports the JVM / Demo Device PKCS#8 format', () async {
      final store = SoftwareDeviceKeyStore();
      expect(
        store.importPkcs8('jvm', JvmFixtures.ecPrivateKeyPkcs8),
        JvmFixtures.ecPublicKeySpki,
      );
      final restored =
          SoftwareDeviceKeyStore()
            ..importPkcs8('copy', store.exportPkcs8('jvm'));
      expect(await restored.publicKey('copy'), JvmFixtures.ecPublicKeySpki);
    });
  });

  test('InMemoryEnrollmentStore CRUD', () async {
    final store = InMemoryEnrollmentStore();
    await store.save(_enrollment(1));
    await store.save(_enrollment(2));
    expect((await store.list()).map((e) => e.enrollmentId), <int>[1, 2]);
    expect((await store.load(_enrollment(1).localId))!.enrollmentId, 1);
    await store.delete(_enrollment(1).localId);
    expect(await store.load(_enrollment(1).localId), isNull);
  });

  group('lab file stores', () {
    late Directory dir;

    setUp(() => dir = Directory.systemTemp.createTempSync('ezkey-stores-'));
    tearDown(() => dir.deleteSync(recursive: true));

    test('FileEnrollmentStore survives a restart', () async {
      await FileEnrollmentStore(dir).save(_enrollment(5));
      final reopened = FileEnrollmentStore(dir);
      expect((await reopened.list()).single.enrollmentProofToken, 'proof-5');
      await reopened.delete(_enrollment(5).localId);
      expect(await reopened.list(), isEmpty);
    });

    test(
      'FileDeviceKeyStore persists keys with owner-only permissions',
      () async {
        final spki = await FileDeviceKeyStore(dir).generateKeyPair('k');
        final reopened = FileDeviceKeyStore(dir);
        final signature = await reopened.sign('k', 'm');
        expect(
          verifyEcdsaSha256(ecPublicKeyFromSpkiBase64(spki), 'm', signature),
          isTrue,
        );
        final file = dir.listSync().whereType<File>().single;
        if (!Platform.isWindows) {
          expect(file.statSync().mode & 0x1ff, 0x180); // 0600
        }
        await reopened.deleteKeyPair('k');
        expect(await FileDeviceKeyStore(dir).publicKey('k'), isNull);
      },
    );
  });

  group('lab helpers', () {
    test('parseEnvFileContents handles comments, export and quotes', () {
      expect(
        parseEnvFileContents(
          '# c\nexport EZKEY_AUTH_API_URL="https://a.example"\nX=\'1\'\nBROKEN\n=v\nY = 2 \n',
        ),
        <String, String>{
          'EZKEY_AUTH_API_URL': 'https://a.example',
          'X': '1',
          'Y': '2',
        },
      );
    });

    test('LabEndpoints defaults to Docker service names, env overrides', () {
      final defaults = LabEndpoints.load(environment: const <String, String>{});
      expect(defaults.authApi, 'http://auth-api:8080');
      expect(defaults.cryptoApi, 'http://crypto-api:9090');
      final overridden = LabEndpoints.load(
        environment: const <String, String>{
          'EZKEY_ADMIN_API_URL': 'http://localhost:9080',
        },
      );
      expect(overridden.adminApi, 'http://localhost:9080');
    });

    test('importDemoDeviceEnrollment derives and checks the device key', () async {
      final dir = Directory.systemTemp.createTempSync('ezkey-demo-');
      addTearDown(() => dir.deleteSync(recursive: true));
      final record =
          '{"enrollmentId":1,"enrollmentProofToken":"p","integrationPublicKey":"pk",'
          '"devicePrivateKey":"${JvmFixtures.ecPrivateKeyPkcs8}",'
          '"devicePublicKey":"${JvmFixtures.ecPublicKeySpki}","integrationName":"Ezkey System"}';
      final enrollment = await importDemoDeviceEnrollment(
        demoDeviceJson: record,
        authApiBaseUrl: 'http://auth-api:8080',
        keyStore: FileDeviceKeyStore(dir),
      );
      expect(enrollment.devicePublicKey, JvmFixtures.ecPublicKeySpki);
      expect(enrollment.isSystemIntegration, isTrue);

      final mismatched = record.replaceFirst(
        JvmFixtures.ecPublicKeySpki,
        ecPublicKeyToSpkiBase64(generateEcP256KeyPair().publicKey),
      );
      await expectLater(
        importDemoDeviceEnrollment(
          demoDeviceJson: mismatched,
          authApiBaseUrl: 'http://auth-api:8080',
          keyStore: FileDeviceKeyStore(dir),
        ),
        throwsFormatException,
      );
    });
  });
}
