import 'models.dart';

/// Hook for persisting [EzkeyEnrollment] records.
///
/// Records contain the enrollment proof token (a bearer credential): back this with secure
/// storage in a Flutter app (e.g. `flutter_secure_storage`: Keychain on iOS,
/// EncryptedSharedPreferences / Keystore-wrapped on Android), serializing with
/// [EzkeyEnrollment.toJson] / [EzkeyEnrollment.fromJson].
abstract interface class EzkeyEnrollmentStore {
  Future<void> save(EzkeyEnrollment enrollment);

  Future<EzkeyEnrollment?> load(String localId);

  Future<List<EzkeyEnrollment>> list();

  Future<void> delete(String localId);
}

/// Volatile store for tests and one-shot tools.
class InMemoryEnrollmentStore implements EzkeyEnrollmentStore {
  final Map<String, EzkeyEnrollment> _records = <String, EzkeyEnrollment>{};

  @override
  Future<void> save(EzkeyEnrollment enrollment) async {
    _records[enrollment.localId] = enrollment;
  }

  @override
  Future<EzkeyEnrollment?> load(String localId) async => _records[localId];

  @override
  Future<List<EzkeyEnrollment>> list() async =>
      _records.values.toList(growable: false);

  @override
  Future<void> delete(String localId) async {
    _records.remove(localId);
  }
}
