/// Interop vectors produced by the stack's JVM `ezkey-crypto-api` (`/keypair`,
/// `/integration-keypair`, `/sign`, `/sign-ed25519`). Throwaway test keys generated only for
/// these fixtures — they protect nothing.
abstract final class JvmFixtures {
  static const String data = 'tok|true|Caf\u00e9|50 \u20ac';

  /// PKCS#8 EC P-256 key in the JVM's minimal form (no embedded public key).
  static const String ecPrivateKeyPkcs8 =
      'MEECAQAwEwYHKoZIzj0CAQYIKoZIzj0DAQcEJzAlAgEBBCD8WSgyc+D3kMFGgdD9d/KQGtGD5Qw29utvXCn3h5RwQQ==';
  static const String ecPublicKeySpki =
      'MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEMbY4XCURIy8GmvENAJII1ecaEuiHDudxr8JJ+qJlIeBGAUgwg05OqQ0k/6qfiPWsu9Yrpmhg02JiizO1bEYqjQ==';
  static const String ecSignatureDer =
      'MEQCIE8o1zg6ijCECT2NS99wRxyu/uiBrY6jtyYpiRjod+FkAiBZfq9sGAWrYTJzIuPAIGGIJqWxaQvODaU/RjNBu2Nqpw==';

  static const String ed25519PrivateKeyPkcs8 =
      'MC4CAQAwBQYDK2VwBCIEIN7CE+S2fE0puqcxlBgZmC3zqL6/R0AVbU3fvIGJTnDn';
  static const String ed25519PublicKey =
      '5rma8B3ByRvneLYF4Sx0C8c_AQxsmQxv_n79LiUpmGQ';

  /// Ed25519 is deterministic: the Dart signer must reproduce this exact value.
  static const String ed25519Signature =
      '1P5EAvK2A1tv1rPza4gsGlZmVBzK4VKw06d1w9zLC9-3e68Y_aABCIJL1NAZGtOgPFKns320k4kF0_PFV2PvAA';
}
