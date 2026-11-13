/// Ezkey device-side protocol SDK (pure Dart, no `dart:io`, usable from Flutter).
///
/// Start with [EzkeyAuthenticator]; implement [EzkeyDeviceKeyStore] and [EzkeyEnrollmentStore]
/// on top of the platform keystore and secure storage. See `README.md`.
library;

export 'src/ec_p256.dart';
export 'src/ed25519.dart';
export 'src/encoding.dart';
export 'src/ezkey_crypto.dart';
export 'src/payload.dart';
export 'src/sdk/auth_api_client.dart';
export 'src/sdk/auth_api_models.dart';
export 'src/sdk/authenticator.dart';
export 'src/sdk/enrollment_store.dart';
export 'src/sdk/errors.dart';
export 'src/sdk/key_store.dart';
export 'src/sdk/models.dart' hide setAuthAttemptDecisionState;
export 'src/sdk/url_policy.dart';
export 'src/secure_random.dart';
