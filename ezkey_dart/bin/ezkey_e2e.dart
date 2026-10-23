import 'dart:convert';
import 'dart:io';

import 'package:args/args.dart';
import 'package:ezkey_dart/ezkey_dart.dart';
import 'package:ezkey_dart/lab.dart';

/// End-to-end protocol suite against a running (clean-start) Ezkey stack.
///
/// Acts as the authenticator app (this SDK) and as the integrating application (Integration API),
/// and provisions its own integration, API key and enrollments through the Admin API after logging
/// in as the bootstrap Global Admin with the Demo Device key. Prints one PASS / FAIL line per case
/// and never prints secrets. Run via `scripts/e2e.sh`.
Future<void> main(List<String> arguments) async {
  final parser =
      ArgParser()
        ..addOption(
          'demo-device-enrollment',
          help:
              'Demo Device enrollment JSON (bootstrap Global Admin). Secret: delete after use.',
        )
        ..addOption('admin-username', defaultsTo: 'admin.docker')
        ..addOption(
          'env-file',
          help: 'Optional .env file with EZKEY_*_URL overrides.',
        )
        ..addFlag('skip-expiry', help: 'Skip the 15 s TTL expiry case.')
        ..addFlag('skip-cli', help: 'Skip the CLI subprocess smoke case.')
        ..addFlag('help', abbr: 'h', negatable: false);
  final args = parser.parse(arguments);
  if (args['help'] == true || args['demo-device-enrollment'] == null) {
    stdout.writeln(
      'usage: dart run bin/ezkey_e2e.dart --demo-device-enrollment FILE [options]',
    );
    stdout.writeln(parser.usage);
    exit(args['help'] == true ? 0 : 2);
  }
  final envFile = args['env-file'] as String?;
  final suite = _Suite(
    endpoints: LabEndpoints.load(
      envFile: envFile == null ? null : File(envFile),
    ),
    demoDeviceFile: File(args['demo-device-enrollment'] as String),
    adminUsername: args['admin-username'] as String,
    skipExpiry: args['skip-expiry'] as bool,
    skipCli: args['skip-cli'] as bool,
  );
  exit(await suite.run());
}

class _CaseFailure implements Exception {
  _CaseFailure(this.message);

  final String message;

  @override
  String toString() => message;
}

void _expect(bool condition, String message) {
  if (!condition) {
    throw _CaseFailure(message);
  }
}

Future<void> _expectApiError(
  Future<Object?> Function() action,
  EzkeyProblemKind kind,
  String what,
) async {
  try {
    await action();
  } on EzkeyApiException catch (error) {
    _expect(
      error.kind == kind,
      '$what: expected ${kind.name}, got ${error.kind.name} '
      '(HTTP ${error.statusCode})',
    );
    return;
  }
  throw _CaseFailure('$what: expected ${kind.name}, but the call succeeded');
}

class _Suite {
  _Suite({
    required this.endpoints,
    required this.demoDeviceFile,
    required this.adminUsername,
    required this.skipExpiry,
    required this.skipCli,
  });

  final LabEndpoints endpoints;
  final File demoDeviceFile;
  final String adminUsername;
  final bool skipExpiry;
  final bool skipCli;

  final List<String> _failures = <String>[];
  int _passed = 0;
  int _skipped = 0;

  late final Directory _stateDir;
  late final EzkeyAuthenticator _authenticator;
  late final FileDeviceKeyStore _keyStore;
  late final AdminLabClient _admin;
  late final CryptoOracleClient _oracle;
  late IntegrationLabClient _integration;
  late int _integrationId;
  late LabEnrollmentInvitation _plainInvite;
  late LabEnrollmentInvitation _challengeInvite;
  late LabEnrollmentInvitation _wrongChallengeInvite;
  late LabEnrollmentInvitation _cliInvite;
  EzkeyEnrollment? _plain;
  EzkeyEnrollment? _challenge;
  final String _runTag = DateTime.now()
      .toUtc()
      .millisecondsSinceEpoch
      .toRadixString(36);

  EzkeyUrlPolicy get _policy => EzkeyUrlPolicy(
    insecureHttpHosts: <String>{
      ...EzkeyUrlPolicy.defaultInsecureHttpHosts,
      Uri.parse(endpoints.authApi).host,
    },
  );

  Future<int> run() async {
    stdout.writeln('Ezkey Dart SDK end-to-end suite');
    stdout.writeln('  auth-api        ${endpoints.authApi}');
    stdout.writeln('  admin-api       ${endpoints.adminApi}');
    stdout.writeln('  integration-api ${endpoints.integrationApi}');
    stdout.writeln('  crypto-api      ${endpoints.cryptoApi}');
    stdout.writeln(
      '  lab opt-in: plain http allowed for host ${Uri.parse(endpoints.authApi).host}',
    );
    stdout.writeln('');

    _stateDir = Directory.systemTemp.createTempSync('ezkey-e2e-');
    _keyStore = FileDeviceKeyStore(Directory('${_stateDir.path}/keys'));
    _authenticator = EzkeyAuthenticator(
      keyStore: _keyStore,
      enrollmentStore: InMemoryEnrollmentStore(),
      urlPolicy: _policy,
    );
    _admin = AdminLabClient(Uri.parse(endpoints.adminApi));
    _oracle = CryptoOracleClient(Uri.parse(endpoints.cryptoApi));

    try {
      await _case(
        'oracle: canonical payloads match the JVM byte for byte',
        _oraclePayloads,
      );
      await _case(
        'oracle: ECDSA P-256 and Ed25519 signatures interoperate',
        _oracleSignatures,
      );
      if (!await _case(
            'admin login: SDK approves the bootstrap admin attempt',
            _adminLogin,
          ) ||
          !await _case(
            'provision: integration, API key, enrollments',
            _provision,
          )) {
        stdout.writeln('Aborting: no Admin API session or provisioning.');
        return _report();
      }
      await _case(
        'enroll: bind + verify (no challenge enrollment)',
        _enrollPlain,
      );
      await _case(
        'enroll: bind + verify (challenge-required enrollment)',
        _enrollChallenge,
      );
      await _case(
        'enroll: admin MFA invitation binds with adminType TENANT_ADMIN',
        _tenantAdmin,
      );
      await _case(
        'enroll: wrong enrollment challenge fails and leaves no key',
        _wrongChallenge,
      );
      await _case(
        'enroll: re-binding a consumed invitation is refused',
        _rebind,
      );
      await _case('instance-info: signed branding verifies', _instanceInfo);
      await _case('pending: nothing pending returns null (204)', _emptyPending);
      await _case(
        'pending: device proof token is single-use once it claimed an attempt',
        _replayedDeviceProof,
      );
      await _case(
        'approve: NFC context verified, integration sees ACCEPTED',
        _approve,
      );
      await _case('deny: integration sees REJECTED', _deny);
      await _case(
        'respond: answering twice is a state conflict',
        _respondTwice,
      );
      await _case(
        'challenge (enrollment): local refusal, wrong code FAILED, right code',
        _enrollmentChallenge,
      );
      await _case(
        'challenge (per attempt): integration-requested code',
        _attemptChallenge,
      );
      await _case(
        'cancel: after claim, respond is a state conflict',
        _cancelAfterClaim,
      );
      await _case(
        'cancel: before claim, pending returns null',
        _cancelBeforeClaim,
      );
      await _case(
        'supersede: pending returns the most recent attempt',
        _supersede,
      );
      if (skipExpiry) {
        _skip('expiry: ttlSeconds=15 attempt answers auth-attempt-expired');
      } else {
        await _case(
          'expiry: ttlSeconds=15 attempt answers auth-attempt-expired',
          _expiry,
        );
      }
      if (skipCli) {
        _skip('cli: ezkey_dart enroll + check --decide approve');
      } else {
        await _case('cli: ezkey_dart enroll + check --decide approve', _cli);
      }
    } finally {
      _stateDir.deleteSync(recursive: true);
    }
    return _report();
  }

  int _report() {
    stdout.writeln('');
    stdout.writeln(
      'passed=$_passed failed=${_failures.length} skipped=$_skipped',
    );
    for (final failure in _failures) {
      stdout.writeln('  FAILED: $failure');
    }
    return _failures.isEmpty ? 0 : 1;
  }

  void _skip(String name) {
    _skipped++;
    stdout.writeln('SKIP  $name');
  }

  Future<bool> _case(String name, Future<void> Function() body) async {
    final watch = Stopwatch()..start();
    try {
      await body();
      _passed++;
      stdout.writeln('PASS  $name (${watch.elapsedMilliseconds} ms)');
      return true;
    } on Object catch (error) {
      _failures.add(name);
      stdout.writeln('FAIL  $name (${watch.elapsedMilliseconds} ms): $error');
      return false;
    }
  }

  // --- oracle ------------------------------------------------------------------------------------

  Future<void> _oraclePayloads() async {
    const decomposed =
        'Cafe\u0301 Paiement \u00e9 | 50 \u20ac'; // NFD + pipe + euro sign
    final token = generateProofToken();
    final cases = <String, (Map<String, Object?>, String)>{
      'pending': (
        <String, Object?>{
          'proofToken': token,
          'challengeRequired': true,
          'contextTitle': decomposed,
          'contextMessage': null,
        },
        buildPendingPayload(token, true, decomposed, null),
      ),
      'respond': (
        <String, Object?>{'proofToken': token, 'accepted': false},
        buildRespondPayload(token, false),
      ),
      'respond-result': (
        <String, Object?>{
          'proofToken': token,
          'authAttemptId': 42,
          'result': 'APPROVED',
          'message': decomposed,
        },
        buildRespondResultPayload(token, '42', 'APPROVED', decomposed),
      ),
      'enrollment-bind': (
        <String, Object?>{
          'proofToken': token,
          'enrollmentId': 7,
          'integrationPublicKey': 'pk',
          'integrationKeyAlgorithm': 'ed25519',
          'integrationName': decomposed,
          'integrationDescription': null,
          'enrollmentName': 'Marie',
          'tenantId': 2,
          'tenantName': 'T',
          'tenantDescription': decomposed,
          'isSystemIntegration': true,
          'adminType': 'GLOBAL_ADMIN',
        },
        buildEnrollmentBindPayload(
          enrollmentProofToken: token,
          enrollmentId: 7,
          integrationPublicKey: 'pk',
          integrationKeyAlgorithm: 'ed25519',
          integrationName: decomposed,
          enrollmentName: 'Marie',
          tenantId: 2,
          tenantName: 'T',
          tenantDescription: decomposed,
          isSystemIntegration: true,
          adminType: 'GLOBAL_ADMIN',
        ),
      ),
      'enrollment-verify-device': (
        <String, Object?>{
          'proofToken': token,
          'enrollmentId': 7,
          'challengeResponse': 123456,
          'devicePublicKey': 'spki',
        },
        buildEnrollmentVerifyDevicePayload(token, 7, 123456, 'spki'),
      ),
      'enrollment-verify-result': (
        <String, Object?>{
          'proofToken': token,
          'enrollmentId': 7,
          'result': 'VERIFIED',
          'message': decomposed,
        },
        buildEnrollmentVerifyResultPayload(token, 7, 'VERIFIED', decomposed),
      ),
      'enrollment-instance-info': (
        <String, Object?>{
          'proofToken': token,
          'enrollmentId': 7,
          'authApiPublicBaseUrl': 'https://auth.example.org',
          'instanceName': decomposed,
          'instanceDescription': null,
          'aboutUrl': 'https://example.org/about',
        },
        buildEnrollmentInstanceInfoPayload(
          enrollmentProofToken: token,
          enrollmentId: 7,
          authApiPublicBaseUrl: 'https://auth.example.org',
          instanceName: decomposed,
          aboutUrl: 'https://example.org/about',
        ),
      ),
    };
    for (final entry in cases.entries) {
      final jvm = await _oracle.payload(entry.key, entry.value.$1);
      _expect(
        jvm == entry.value.$2,
        'payload "${entry.key}" differs from the JVM oracle',
      );
    }
  }

  Future<void> _oracleSignatures() async {
    const data = 'ezkey|interop|\u00e9';
    final jvmEc = await _oracle.ecKeyPair();
    final imported = EzKeyCrypto.importDeviceKeyPairPkcs8(jvmEc.privateKey);
    _expect(
      EzKeyCrypto.exportDevicePublicKey(imported) == jvmEc.publicKey,
      'PKCS#8 import does not derive the JVM public key',
    );
    final dartSignature = EzKeyCrypto.signWithDeviceKey(imported, data);
    _expect(
      await _oracle.validateEcdsa(data, dartSignature, jvmEc.publicKey),
      'JVM rejects a Dart ECDSA signature',
    );
    final jvmSignature = await _oracle.signEcdsa(data, jvmEc.privateKey);
    _expect(
      verifyEcdsaSha256(
        ecPublicKeyFromSpkiBase64(jvmEc.publicKey),
        data,
        jvmSignature,
      ),
      'Dart rejects a JVM ECDSA signature',
    );
    final dartKey = generateEcP256KeyPair();
    _expect(
      await _oracle.validateEcdsa(
        data,
        signEcdsaSha256(dartKey.privateKey, data),
        ecPublicKeyToSpkiBase64(dartKey.publicKey),
      ),
      'JVM rejects a Dart-generated key',
    );

    final jvmEd = await _oracle.ed25519KeyPair();
    final jvmEdSignature = await _oracle.signEd25519(data, jvmEd.privateKey);
    _expect(
      await verifyEd25519(data, jvmEdSignature, jvmEd.publicKey),
      'Dart rejects a JVM Ed25519 signature',
    );
    final dartEdSignature = await signEd25519WithPkcs8(data, jvmEd.privateKey);
    _expect(
      await _oracle.verifyEd25519(data, dartEdSignature, jvmEd.publicKey),
      'JVM rejects a Dart Ed25519 signature',
    );
  }

  // --- admin session + provisioning ------------------------------------------------------------

  Future<void> _adminLogin() async {
    final adminEnrollment = await importDemoDeviceEnrollment(
      demoDeviceJson: demoDeviceFile.readAsStringSync(),
      authApiBaseUrl: _policy.require(endpoints.authApi).toString(),
      keyStore: _keyStore,
    );
    final challenge = await _admin.startLogin(adminUsername);
    final attempt = await _authenticator.pollPending(
      adminEnrollment,
      interval: const Duration(milliseconds: 500),
      timeout: const Duration(seconds: 20),
    );
    _expect(attempt != null, 'no pending attempt for the bootstrap admin');
    _expect(
      attempt!.authAttemptId == challenge.authAttemptId,
      'claimed another attempt',
    );
    _expect(
      attempt.challengeRequired == (challenge.challengeCode != null),
      'challenge flag mismatch',
    );
    final outcome = await _authenticator.approve(
      adminEnrollment,
      attempt,
      challengeResponse: challenge.challengeCode,
    );
    _expect(
      outcome.result == AuthAttemptResult.approved,
      'admin login result ${outcome.result}',
    );
    await _admin.completeLogin(challenge);
  }

  Future<void> _provision() async {
    _integrationId = await _admin.createIntegration(
      code: 'dart-e2e-$_runTag',
      name: 'Dart SDK E2E $_runTag',
      description: 'Created by ezkey_dart bin/ezkey_e2e.dart',
    );
    final key = await _admin.createApiKey(
      _integrationId,
      description: 'dart e2e',
    );
    _integration = IntegrationLabClient(
      Uri.parse(endpoints.integrationApi),
      integrationKey: key.integrationKey,
      secretKey: key.secretKey,
    );
    _plainInvite = await _admin.createEnrollment(
      integrationId: _integrationId,
      name: 'Marie plain',
    );
    _challengeInvite = await _admin.createEnrollment(
      integrationId: _integrationId,
      name: 'Marie challenge',
      authAttemptChallengeRequired: true,
    );
    _wrongChallengeInvite = await _admin.createEnrollment(
      integrationId: _integrationId,
      name: 'Wrong challenge',
    );
    _cliInvite = await _admin.createEnrollment(
      integrationId: _integrationId,
      name: 'CLI',
    );
  }

  EnrollmentCode _code(LabEnrollmentInvitation invite) => EnrollmentCode.parse(
    jsonEncode(<String, Object?>{
      'enrollmentId': invite.enrollmentId,
      'enrollmentProofToken': invite.enrollmentProofToken,
      'authUrl': endpoints.authApi,
    }),
    policy: _policy,
  );

  // --- enrollment --------------------------------------------------------------------------------

  Future<EzkeyEnrollment> _enrollAndCheck(
    LabEnrollmentInvitation invite,
  ) async {
    final bound = await _authenticator.bindEnrollment(_code(invite));
    _expect(
      bound.integrationName == 'Dart SDK E2E $_runTag',
      'bind integrationName',
    );
    _expect(bound.tenantName != null, 'bind tenantName missing');
    _expect(
      !bound.isSystemIntegration && bound.adminType == null,
      'unexpected admin metadata',
    );
    final enrollment = await _authenticator.completeEnrollment(
      bound,
      enrollmentChallenge: invite.enrollmentChallenge,
    );
    final server = await _admin.getEnrollment(invite.enrollmentId);
    _expect(
      server['enrollmentStatus'] == 'VERIFIED',
      'server status ${server['enrollmentStatus']}',
    );
    _expect(
      server['devicePublicKey'] == enrollment.devicePublicKey,
      'server device key differs',
    );
    _expect(
      server['devicePrivateKeyStorageTier'] == 'NONE',
      'server storage tier',
    );
    return enrollment;
  }

  Future<void> _enrollPlain() async =>
      _plain = await _enrollAndCheck(_plainInvite);

  Future<void> _enrollChallenge() async =>
      _challenge = await _enrollAndCheck(_challengeInvite);

  Future<void> _tenantAdmin() async {
    final tenantId = await _admin.createTenant('Dart E2E $_runTag');
    final invite = await _admin.createTenantAdminInvitation(
      tenantId: tenantId,
      username: 'dart.e2e.$_runTag',
    );
    final bound = await _authenticator.bindEnrollment(_code(invite));
    _expect(
      bound.isSystemIntegration,
      'admin MFA enrollment must be a system integration',
    );
    _expect(bound.adminType == 'TENANT_ADMIN', 'adminType ${bound.adminType}');
    final enrollment = await _authenticator.completeEnrollment(
      bound,
      enrollmentChallenge: invite.enrollmentChallenge,
    );
    _expect(enrollment.adminType == 'TENANT_ADMIN', 'stored adminType');
  }

  Future<void> _wrongChallenge() async {
    final invite = _wrongChallengeInvite;
    final bound = await _authenticator.bindEnrollment(_code(invite));
    final wrong = (invite.enrollmentChallenge + 1) % 1000000;
    await _expectApiError(
      () =>
          _authenticator.completeEnrollment(bound, enrollmentChallenge: wrong),
      EzkeyProblemKind.enrollmentVerifyFailed,
      'verify with wrong challenge',
    );
    final local = await _authenticator.listEnrollments();
    _expect(
      local.every((e) => e.enrollmentId != invite.enrollmentId),
      'enrollment was persisted',
    );
    final keyFiles = Directory('${_stateDir.path}/keys').listSync().length;
    _expect(
      keyFiles == local.length + 1,
      'device key was not cleaned up',
    ); // +1: admin key
  }

  Future<void> _rebind() async {
    final fresh = EzkeyAuthenticator(
      keyStore: SoftwareDeviceKeyStore(),
      enrollmentStore: InMemoryEnrollmentStore(),
      urlPolicy: _policy,
    );
    await _expectApiError(
      () => fresh.bindEnrollment(_code(_plainInvite)),
      EzkeyProblemKind.enrollmentAlreadyBound,
      'second bind',
    );
  }

  Future<void> _instanceInfo() async {
    final info = await _authenticator.fetchInstanceInfo(_plain!);
    _expect(
      info.enrollmentId == _plain!.enrollmentId,
      'instance-info enrollment id',
    );
  }

  // --- auth attempts -----------------------------------------------------------------------------

  Future<PendingAuthAttempt> _claim(
    EzkeyEnrollment enrollment,
    int expectedId,
  ) async {
    final attempt = await _authenticator.pollPending(
      enrollment,
      interval: const Duration(milliseconds: 300),
      timeout: const Duration(seconds: 10),
    );
    _expect(attempt != null, 'attempt $expectedId was not delivered');
    _expect(
      attempt!.authAttemptId == expectedId,
      'claimed ${attempt.authAttemptId}, wanted $expectedId',
    );
    return attempt;
  }

  Future<void> _expectIntegrationStatus(int id, String status) async {
    final result = await _integration.waitForAuthAttempt(id, timeoutSeconds: 5);
    _expect(
      result.status == status,
      'integration sees ${result.status}, wanted $status',
    );
  }

  Future<void> _emptyPending() async {
    _expect(
      await _authenticator.checkPending(_plain!) == null,
      'unexpected pending attempt',
    );
  }

  Future<void> _replayedDeviceProof() async {
    final enrollment = _plain!;
    final api = AuthApiClient(Uri.parse(enrollment.authApiBaseUrl));
    final token = generateProofToken();
    final request = PendingAuthRequest(
      enrollmentId: enrollment.enrollmentId,
      enrollmentProofToken: enrollment.enrollmentProofToken,
      deviceProofToken: token,
      deviceProofTokenSigned: await _keyStore.sign(
        enrollment.deviceKeyAlias,
        token,
      ),
    );
    // Empty polls persist nothing, so the same token may be reused until a claim records it.
    _expect(await api.pending(request) == null, 'empty poll should be 204');
    final created = await _integration.createAuthAttempt(
      enrollmentId: enrollment.enrollmentId,
    );
    final claimed = await api.pending(request);
    _expect(
      claimed?.authAttemptId == created.authAttemptId,
      'claim with the token failed',
    );
    await _expectApiError(
      () => api.pending(request),
      EzkeyProblemKind.authAttemptBindingFailed,
      'replayed pending after claim',
    );
    api.close();
    await _authenticator.deny(
      enrollment,
      PendingAuthAttempt(
        enrollmentLocalId: enrollment.localId,
        authAttemptId: claimed!.authAttemptId,
        authAttemptProofToken: claimed.authAttemptProofToken,
        challengeRequired: claimed.authAttemptChallengeRequired,
        receivedAt: DateTime.now().toUtc(),
      ),
    );
  }

  Future<void> _approve() async {
    const title = 'Paiement approuve\u0301';
    const message = 'Virement de 50 \u20ac | ACME';
    final created = await _integration.createAuthAttempt(
      enrollmentId: _plain!.enrollmentId,
      contextTitle: title,
      contextMessage: message,
    );
    _expect(
      created.expiresAt != null,
      'integration create returned no expiresAt',
    );
    final attempt = await _claim(_plain!, created.authAttemptId);
    _expect(!attempt.challengeRequired, 'no challenge expected');
    _expect(
      attempt.contextTitle != null &&
          normalizeNfc(attempt.contextTitle!) == normalizeNfc(title),
      'contextTitle',
    );
    _expect(attempt.contextMessage == message, 'contextMessage');
    final outcome = await _authenticator.approve(_plain!, attempt);
    _expect(
      outcome.result == AuthAttemptResult.approved,
      'result ${outcome.result}',
    );
    await _expectIntegrationStatus(created.authAttemptId, 'ACCEPTED');
  }

  Future<void> _deny() async {
    final created = await _integration.createAuthAttempt(
      enrollmentId: _plain!.enrollmentId,
    );
    final attempt = await _claim(_plain!, created.authAttemptId);
    final outcome = await _authenticator.deny(_plain!, attempt);
    _expect(
      outcome.result == AuthAttemptResult.denied,
      'result ${outcome.result}',
    );
    await _expectIntegrationStatus(created.authAttemptId, 'REJECTED');
  }

  Future<void> _respondTwice() async {
    final created = await _integration.createAuthAttempt(
      enrollmentId: _plain!.enrollmentId,
    );
    final attempt = await _claim(_plain!, created.authAttemptId);
    await _authenticator.approve(_plain!, attempt);
    await _expectApiError(
      () => _authenticator.approve(_plain!, attempt),
      EzkeyProblemKind.authAttemptStateConflict,
      'second respond',
    );
  }

  Future<void> _enrollmentChallenge() async {
    final enrollment = _challenge!;
    final first = await _integration.createAuthAttempt(
      enrollmentId: enrollment.enrollmentId,
    );
    _expect(
      first.authAttemptChallenge != null,
      'integration got no challenge to display',
    );
    final attempt = await _claim(enrollment, first.authAttemptId);
    _expect(attempt.challengeRequired, 'challengeRequired must be true');
    try {
      await _authenticator.approve(enrollment, attempt);
      throw _CaseFailure('approve without a code was not refused locally');
    } on EzkeyValidationException {
      // Expected: nothing was sent.
    }
    final wrong = (first.authAttemptChallenge! + 1) % 1000000;
    final failed = await _authenticator.approve(
      enrollment,
      attempt,
      challengeResponse: wrong,
    );
    _expect(
      failed.result == AuthAttemptResult.failed,
      'wrong code result ${failed.result}',
    );
    await _expectIntegrationStatus(first.authAttemptId, 'INVALID');

    final second = await _integration.createAuthAttempt(
      enrollmentId: enrollment.enrollmentId,
    );
    final retry = await _claim(enrollment, second.authAttemptId);
    final ok = await _authenticator.approve(
      enrollment,
      retry,
      challengeResponse: second.authAttemptChallenge,
    );
    _expect(
      ok.result == AuthAttemptResult.approved,
      'right code result ${ok.result}',
    );
    await _expectIntegrationStatus(second.authAttemptId, 'ACCEPTED');
  }

  Future<void> _attemptChallenge() async {
    final created = await _integration.createAuthAttempt(
      enrollmentId: _plain!.enrollmentId,
      challengeRequested: true,
    );
    final attempt = await _claim(_plain!, created.authAttemptId);
    _expect(attempt.challengeRequired, 'challengeRequired must be true');
    final outcome = await _authenticator.approve(
      _plain!,
      attempt,
      challengeResponse: created.authAttemptChallenge,
    );
    _expect(
      outcome.result == AuthAttemptResult.approved,
      'result ${outcome.result}',
    );
  }

  Future<void> _cancelAfterClaim() async {
    final created = await _integration.createAuthAttempt(
      enrollmentId: _plain!.enrollmentId,
    );
    final attempt = await _claim(_plain!, created.authAttemptId);
    await _integration.cancelAuthAttempt(created.authAttemptId);
    await _expectApiError(
      () => _authenticator.approve(_plain!, attempt),
      EzkeyProblemKind.authAttemptStateConflict,
      'approve after cancel',
    );
  }

  Future<void> _cancelBeforeClaim() async {
    final created = await _integration.createAuthAttempt(
      enrollmentId: _plain!.enrollmentId,
    );
    await _integration.cancelAuthAttempt(created.authAttemptId);
    _expect(
      await _authenticator.checkPending(_plain!) == null,
      'cancelled attempt was delivered',
    );
  }

  Future<void> _supersede() async {
    final older = await _integration.createAuthAttempt(
      enrollmentId: _plain!.enrollmentId,
    );
    final newer = await _integration.createAuthAttempt(
      enrollmentId: _plain!.enrollmentId,
    );
    final attempt = await _claim(_plain!, newer.authAttemptId);
    await _authenticator.deny(_plain!, attempt);
    final next = await _authenticator.checkPending(_plain!);
    if (next != null) {
      await _authenticator.deny(_plain!, next);
    }
    _expect(
      next == null,
      'superseded attempt ${older.authAttemptId} was still delivered',
    );
  }

  Future<void> _expiry() async {
    final created = await _integration.createAuthAttempt(
      enrollmentId: _plain!.enrollmentId,
      ttlSeconds: 15,
    );
    final attempt = await _claim(_plain!, created.authAttemptId);
    _expect(
      attempt.latestPossibleExpiry.isAfter(
        created.expiresAt!.subtract(const Duration(seconds: 5)),
      ),
      'advisory expiry bound earlier than the real expiresAt',
    );
    await Future<void>.delayed(const Duration(seconds: 17));
    await _expectApiError(
      () => _authenticator.approve(_plain!, attempt),
      EzkeyProblemKind.authAttemptExpired,
      'approve after TTL',
    );
  }

  Future<void> _cli() async {
    final cliState = Directory('${_stateDir.path}/cli');
    final codeFile = File('${_stateDir.path}/cli-code.json');
    await writePrivateFile(
      codeFile,
      jsonEncode(<String, Object?>{
        'enrollmentId': _cliInvite.enrollmentId,
        'enrollmentProofToken': _cliInvite.enrollmentProofToken,
        'authUrl': endpoints.authApi,
      }),
    );
    final host = Uri.parse(endpoints.authApi).host;
    Future<ProcessResult> cli(List<String> args) =>
        Process.run(Platform.resolvedExecutable, <String>[
          'run',
          'bin/ezkey_dart.dart',
          '--state-dir',
          cliState.path,
          '--allow-insecure-http-host',
          host,
          ...args,
        ]);

    final enroll = await cli(<String>[
      'enroll',
      '--code-file',
      codeFile.path,
      '--challenge',
      '${_cliInvite.enrollmentChallenge}',
    ]);
    _expect(
      enroll.exitCode == 0,
      'cli enroll exit ${enroll.exitCode}: ${enroll.stderr}',
    );
    final created = await _integration.createAuthAttempt(
      enrollmentId: _cliInvite.enrollmentId,
      contextTitle: 'CLI login',
    );
    final check = await cli(<String>[
      'check',
      '--enrollment',
      '${_cliInvite.enrollmentId}',
      '--wait',
      '10',
      '--decide',
      'approve',
    ]);
    _expect(
      check.exitCode == 0,
      'cli check exit ${check.exitCode}: ${check.stderr}',
    );
    _expect(
      '${check.stdout}'.contains('Result: APPROVED'),
      'cli did not report APPROVED',
    );
    await _expectIntegrationStatus(created.authAttemptId, 'ACCEPTED');
    final empty = await cli(<String>[
      'check',
      '--enrollment',
      '${_cliInvite.enrollmentId}',
    ]);
    _expect(empty.exitCode == 3, 'cli empty check exit ${empty.exitCode}');
  }
}
