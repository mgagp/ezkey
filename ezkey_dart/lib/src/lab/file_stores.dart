import 'dart:convert';
import 'dart:io';

import '../sdk/enrollment_store.dart';
import '../sdk/errors.dart';
import '../sdk/key_store.dart';
import '../sdk/models.dart';

/// Writes [contents] so that only the current user can read it (best effort `chmod 600` on
/// POSIX; Windows relies on the profile directory ACL).
Future<void> writePrivateFile(File file, String contents) async {
  await file.parent.create(recursive: true);
  final tmp = File('${file.path}.tmp');
  await tmp.writeAsString('', flush: true);
  if (!Platform.isWindows) {
    await Process.run('chmod', <String>['600', tmp.path]);
  }
  await tmp.writeAsString(contents, flush: true);
  await tmp.rename(file.path);
}

String _safeFileName(String value) =>
    base64Url.encode(utf8.encode(value)).replaceAll('=', '');

/// [EzkeyEnrollmentStore] persisted as one JSON file per enrollment under [directory].
///
/// Lab / CLI persistence. Files contain the enrollment proof token (bearer secret).
class FileEnrollmentStore implements EzkeyEnrollmentStore {
  FileEnrollmentStore(this.directory);

  final Directory directory;

  File _file(String localId) => File(
    '${directory.path}${Platform.pathSeparator}${_safeFileName(localId)}.json',
  );

  @override
  Future<void> save(EzkeyEnrollment enrollment) => writePrivateFile(
    _file(enrollment.localId),
    jsonEncode(enrollment.toJson()),
  );

  @override
  Future<EzkeyEnrollment?> load(String localId) async {
    final file = _file(localId);
    if (!file.existsSync()) {
      return null;
    }
    return _read(file);
  }

  @override
  Future<List<EzkeyEnrollment>> list() async {
    if (!directory.existsSync()) {
      return const <EzkeyEnrollment>[];
    }
    final files = directory.listSync().whereType<File>().where(
      (f) => f.path.endsWith('.json'),
    );
    final result = <EzkeyEnrollment>[];
    for (final file in files) {
      result.add(await _read(file));
    }
    result.sort((a, b) => a.localId.compareTo(b.localId));
    return result;
  }

  @override
  Future<void> delete(String localId) async {
    final file = _file(localId);
    if (file.existsSync()) {
      await file.delete();
    }
  }

  Future<EzkeyEnrollment> _read(File file) async {
    try {
      return EzkeyEnrollment.fromJson(
        jsonDecode(await file.readAsString()) as Map<String, dynamic>,
      );
    } on FormatException {
      throw EzkeyLocalStateException('Corrupt enrollment file ${file.path}');
    }
  }
}

/// Software key store that persists PKCS#8 keys as files under [directory] (`chmod 600`).
///
/// Lab / CLI only — a Flutter app must use the platform keystore (see [EzkeyDeviceKeyStore]).
class FileDeviceKeyStore implements EzkeyDeviceKeyStore {
  FileDeviceKeyStore(this.directory);

  final Directory directory;
  final SoftwareDeviceKeyStore _memory = SoftwareDeviceKeyStore();

  File _file(String alias) => File(
    '${directory.path}${Platform.pathSeparator}${_safeFileName(alias)}.pk8',
  );

  @override
  DevicePrivateKeyStorageTier get storageTier =>
      DevicePrivateKeyStorageTier.none;

  @override
  Future<String> generateKeyPair(String alias) async {
    final publicKey = await _memory.generateKeyPair(alias);
    await writePrivateFile(_file(alias), _memory.exportPkcs8(alias));
    return publicKey;
  }

  /// Imports an existing PKCS#8 private key (e.g. from the Ezkey Demo Device) under [alias].
  Future<String> importPkcs8(String alias, String pkcs8Base64Std) async {
    final publicKey = _memory.importPkcs8(alias, pkcs8Base64Std);
    await writePrivateFile(_file(alias), _memory.exportPkcs8(alias));
    return publicKey;
  }

  Future<void> _ensureLoaded(String alias) async {
    if (await _memory.publicKey(alias) != null) {
      return;
    }
    final file = _file(alias);
    if (!file.existsSync()) {
      throw EzkeyLocalStateException('No device key for alias "$alias"');
    }
    _memory.importPkcs8(alias, (await file.readAsString()).trim());
  }

  @override
  Future<String> sign(String alias, String payload) async {
    await _ensureLoaded(alias);
    return _memory.sign(alias, payload);
  }

  @override
  Future<String?> publicKey(String alias) async {
    try {
      await _ensureLoaded(alias);
    } on EzkeyLocalStateException {
      return null;
    }
    return _memory.publicKey(alias);
  }

  @override
  Future<void> deleteKeyPair(String alias) async {
    await _memory.deleteKeyPair(alias);
    final file = _file(alias);
    if (file.existsSync()) {
      await file.delete();
    }
  }
}
