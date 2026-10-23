import 'dart:io';

import 'package:args/args.dart';
import 'package:args/command_runner.dart';
import 'package:ezkey_dart/ezkey_dart.dart';
import 'package:ezkey_dart/lab.dart';

/// Software authenticator CLI: enroll, claim pending auth attempts, approve / deny.
///
/// State (enrollment records + PKCS#8 device keys) lives under `--state-dir` with owner-only
/// permissions. Lab tool: keys are software keys (storage tier NONE).
Future<void> main(List<String> arguments) async {
  final runner =
      CommandRunner<int>(
          'ezkey_dart',
          'Ezkey software authenticator (lab CLI).',
        )
        ..argParser.addOption(
          'state-dir',
          defaultsTo: '.ezkey-state',
          help:
              'Directory holding enrollments and device keys (secrets; chmod 600 files).',
        )
        ..argParser.addMultiOption(
          'allow-insecure-http-host',
          help:
              'Extra host allowed over plain http (lab Docker network only, e.g. auth-api). '
              'Loopback and 10.0.2.2 are always allowed.',
        )
        ..addCommand(_EnrollCommand())
        ..addCommand(_ListCommand())
        ..addCommand(_CheckCommand())
        ..addCommand(_InstanceInfoCommand())
        ..addCommand(_ForgetCommand());
  int code;
  try {
    code = await runner.run(arguments) ?? 0;
  } on UsageException catch (error) {
    stderr.writeln(error);
    code = 2;
  } on EzkeyException catch (error) {
    stderr.writeln('error: $error');
    code = 1;
  }
  exit(code);
}

abstract class _BaseCommand extends Command<int> {
  ArgResults get _global => globalResults!;

  ArgResults get _args => argResults!;

  EzkeyAuthenticator buildAuthenticator() {
    final stateDir = Directory(_global['state-dir'] as String);
    return EzkeyAuthenticator(
      keyStore: FileDeviceKeyStore(Directory('${stateDir.path}/keys')),
      enrollmentStore: FileEnrollmentStore(
        Directory('${stateDir.path}/enrollments'),
      ),
      urlPolicy: EzkeyUrlPolicy(
        insecureHttpHosts: <String>{
          ...EzkeyUrlPolicy.defaultInsecureHttpHosts,
          ...(_global['allow-insecure-http-host'] as List<String>).map(
            (h) => h.toLowerCase(),
          ),
        },
      ),
    );
  }

  void addEnrollmentOption() {
    argParser.addOption(
      'enrollment',
      help:
          'Enrollment id or local id (base URL#id). Optional when only one is stored.',
    );
  }

  Future<EzkeyEnrollment> selectEnrollment(
    EzkeyAuthenticator authenticator,
  ) async {
    final all = await authenticator.listEnrollments();
    final wanted = _args['enrollment'] as String?;
    final matches =
        wanted == null
            ? all
            : all
                .where(
                  (e) => e.localId == wanted || '${e.enrollmentId}' == wanted,
                )
                .toList();
    if (matches.length != 1) {
      throw UsageException(
        matches.isEmpty
            ? 'No matching enrollment'
            : 'Several enrollments match; use --enrollment',
        usage,
      );
    }
    return matches.single;
  }
}

class _EnrollCommand extends _BaseCommand {
  _EnrollCommand() {
    argParser
      ..addOption(
        'code',
        help:
            'QR content: JSON {"enrollmentId","enrollmentProofToken",...} or id|token.',
      )
      ..addOption(
        'code-file',
        help: 'Read the QR content from a file instead of --code.',
      )
      ..addOption(
        'challenge',
        help: 'Enrollment challenge shown out of band (6 digits).',
      )
      ..addOption(
        'auth-api-url',
        help: 'Auth API base URL (overrides the code authUrl).',
      );
  }

  @override
  String get name => 'enroll';

  @override
  String get description =>
      'Bind an invitation and verify it with the enrollment challenge.';

  @override
  Future<int> run() async {
    final codeFile = _args['code-file'] as String?;
    final rawCode =
        codeFile != null
            ? File(codeFile).readAsStringSync()
            : _args['code'] as String?;
    final rawChallenge = _args['challenge'] as String?;
    if (rawCode == null || rawChallenge == null) {
      throw UsageException(
        '--code (or --code-file) and --challenge are required',
        usage,
      );
    }
    final authenticator = buildAuthenticator();
    final code = EnrollmentCode.parse(rawCode, policy: authenticator.urlPolicy);
    final bound = await authenticator.bindEnrollment(
      code,
      authApiBaseUrlOverride: _args['auth-api-url'] as String?,
    );
    stdout.writeln(
      'Bound enrollment ${bound.enrollmentId} '
      '(integration: ${bound.integrationName ?? '-'}, tenant: ${bound.tenantName ?? '-'}'
      '${bound.adminType == null ? '' : ', admin: ${bound.adminType}'})',
    );
    final enrollment = await authenticator.completeEnrollment(
      bound,
      enrollmentChallenge: parseChallengeCode(rawChallenge),
    );
    stdout.writeln('Enrolled ${enrollment.localId}');
    return 0;
  }
}

class _ListCommand extends _BaseCommand {
  @override
  String get name => 'list';

  @override
  String get description => 'List local enrollments (no secrets printed).';

  @override
  Future<int> run() async {
    for (final e in await buildAuthenticator().listEnrollments()) {
      stdout.writeln(
        '${e.localId}  name=${e.enrollmentName ?? '-'}  integration=${e.integrationName ?? '-'}'
        '  tier=${e.devicePrivateKeyStorageTier.wireValue}',
      );
    }
    return 0;
  }
}

class _CheckCommand extends _BaseCommand {
  _CheckCommand() {
    addEnrollmentOption();
    argParser
      ..addOption(
        'wait',
        defaultsTo: '0',
        help: 'Poll up to N seconds for an attempt.',
      )
      ..addOption(
        'decide',
        allowed: <String>['ask', 'approve', 'deny', 'none'],
        defaultsTo: 'ask',
        help:
            'What to do with a claimed attempt. "none" leaves it unanswered (it will expire).',
      )
      ..addOption(
        'challenge',
        help: 'Challenge code displayed by the integrating application.',
      );
  }

  @override
  String get name => 'check';

  @override
  String get description =>
      'Claim the pending auth attempt (one-shot) and approve or deny it. Exit 3 when none.';

  @override
  Future<int> run() async {
    final authenticator = buildAuthenticator();
    final enrollment = await selectEnrollment(authenticator);
    final waitSeconds = int.parse(_args['wait'] as String);
    final attempt =
        waitSeconds > 0
            ? await authenticator.pollPending(
              enrollment,
              interval: const Duration(seconds: 1),
              timeout: Duration(seconds: waitSeconds),
            )
            : await authenticator.checkPending(enrollment);
    if (attempt == null) {
      stdout.writeln('No pending auth attempt.');
      return 3;
    }
    stdout.writeln(
      'Auth attempt ${attempt.authAttemptId}: ${attempt.contextTitle ?? '(no title)'}'
      '${attempt.contextMessage == null ? '' : ' - ${attempt.contextMessage}'}'
      '${attempt.challengeRequired ? ' [challenge code required]' : ''}',
    );

    var decision = _args['decide'] as String;
    String? rawChallenge = _args['challenge'] as String?;
    if (decision == 'none') {
      return 0;
    }
    if (decision == 'ask') {
      stdout.write('Approve? [y/N] ');
      decision =
          (stdin.readLineSync() ?? '').trim().toLowerCase() == 'y'
              ? 'approve'
              : 'deny';
      if (decision == 'approve' &&
          attempt.challengeRequired &&
          rawChallenge == null) {
        stdout.write('Code shown by the application: ');
        rawChallenge = stdin.readLineSync();
      }
    }

    try {
      final outcome =
          decision == 'approve'
              ? await authenticator.approve(
                enrollment,
                attempt,
                challengeResponse:
                    rawChallenge == null
                        ? null
                        : parseChallengeCode(rawChallenge),
              )
              : await authenticator.deny(enrollment, attempt);
      stdout.writeln(
        'Result: ${outcome.result.wireValue} (${outcome.message})',
      );
      return outcome.result == AuthAttemptResult.failed ? 1 : 0;
    } on EzkeyApiException catch (error) {
      if (error.isAttemptNoLongerActionable) {
        stdout.writeln(
          error.kind == EzkeyProblemKind.authAttemptExpired
              ? 'This request expired.'
              : 'This request is no longer active (cancelled, replaced or already answered).',
        );
        return 1;
      }
      rethrow;
    }
  }
}

class _InstanceInfoCommand extends _BaseCommand {
  _InstanceInfoCommand() {
    addEnrollmentOption();
  }

  @override
  String get name => 'instance-info';

  @override
  String get description =>
      'Fetch the signed installation branding for an enrollment.';

  @override
  Future<int> run() async {
    final authenticator = buildAuthenticator();
    final info = await authenticator.fetchInstanceInfo(
      await selectEnrollment(authenticator),
    );
    stdout.writeln('instanceName=${info.instanceName ?? '-'}');
    stdout.writeln('instanceDescription=${info.instanceDescription ?? '-'}');
    stdout.writeln('authApiPublicBaseUrl=${info.authApiPublicBaseUrl ?? '-'}');
    stdout.writeln('aboutUrl=${info.aboutUrl ?? '-'}');
    return 0;
  }
}

class _ForgetCommand extends _BaseCommand {
  _ForgetCommand() {
    addEnrollmentOption();
  }

  @override
  String get name => 'forget';

  @override
  String get description => 'Delete an enrollment and its device key locally.';

  @override
  Future<int> run() async {
    final authenticator = buildAuthenticator();
    final enrollment = await selectEnrollment(authenticator);
    await authenticator.forget(enrollment);
    stdout.writeln(
      'Forgot ${enrollment.localId} (server-side revocation is an admin action).',
    );
    return 0;
  }
}
