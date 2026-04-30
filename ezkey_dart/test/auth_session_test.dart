import 'dart:typed_data';

import 'package:cryptography/cryptography.dart';
import 'package:ezkey_dart/ezkey_dart.dart';
import 'package:test/test.dart';

const List<int> _pkcs8Prefix = <int>[
  0x30,
  0x2e,
  0x02,
  0x01,
  0x00,
  0x30,
  0x05,
  0x06,
  0x03,
  0x2b,
  0x65,
  0x70,
  0x04,
  0x22,
  0x04,
  0x20,
];

void main() {
  test('EzkeyAuthSession enrolls, verifies pending signature, and verifies respond result', () async {
    final seed = Uint8List.fromList(List<int>.generate(32, (index) => index + 1));
    final pkcs8 = base64StdEncodeBytes(<int>[..._pkcs8Prefix, ...seed]);
    final publicKey = await Ed25519()
        .newKeyPairFromSeed(seed)
        .then((pair) => pair.extractPublicKey());
    final integrationPublicKey = base64UrlEncodeBytes(publicKey.bytes);
    final fakeApi = _FakeAuthApi(
      integrationPublicKey: integrationPublicKey,
      integrationPkcs8: pkcs8,
    );

    final session = await EzkeyAuthSession.enroll(
      api: fakeApi,
      authApiBaseUri: Uri.parse('https://goateed.example.dev'),
      code: EnrollmentCode.parse(
        '{"enrollmentId":123,"enrollmentProofToken":"proof.enroll","authUrl":"https://goateed.example.dev"}',
      ),
      bindingChallenge: '654321',
    );

    expect(session.enrollmentId, '123');
    expect(fakeApi.lastBindRequest?.enrollmentProofToken, 'proof.enroll');

    final pending = await session.checkPending();
    expect(pending, isNotNull);
    expect(pending!.challengeRequired, isTrue);

    final outcome = await session.respond(
      attempt: pending,
      accepted: true,
      challengeResponse: '42',
    );

    expect(outcome.integrationSignatureVerified, isTrue);
    expect(outcome.response.authAttemptResult, 'APPROVED');
  });
}

class _FakeAuthApi implements EzkeyAuthApi {
  _FakeAuthApi({
    required this.integrationPublicKey,
    required this.integrationPkcs8,
  });

  final String integrationPublicKey;
  final String integrationPkcs8;
  EnrollmentBindRequest? lastBindRequest;

  @override
  Future<EnrollmentBindResponse> bind(EnrollmentBindRequest request) async {
    lastBindRequest = request;
    final bindPayload = buildEnrollmentBindPayload(
      enrollmentProofToken: request.enrollmentProofToken,
      enrollmentId: int.parse(request.enrollmentId),
      integrationPublicKey: integrationPublicKey,
      integrationKeyAlgorithm: 'ed25519',
      integrationName: 'Acme Demo',
      tenantName: 'Acme Tenant',
    );
    final bindSig = await signEd25519WithPkcs8(bindPayload, integrationPkcs8);
    return EnrollmentBindResponse(
      enrollmentId: request.enrollmentId,
      enrollmentProofToken: request.enrollmentProofToken,
      integrationPublicKey: integrationPublicKey,
      enrollmentBindPayloadSignedByIntegration: bindSig,
      integrationKeyAlgorithm: 'ed25519',
      integrationName: 'Acme Demo',
      tenantName: 'Acme Tenant',
    );
  }

  @override
  Future<PendingAuthResponse?> pending(PendingAuthRequest request) async {
    final payload = buildPendingPayload(
      'pending.proof.token',
      true,
      'Payment Approval',
      'Authorize test batch',
    );
    final signature = await signEd25519WithPkcs8(payload, integrationPkcs8);
    return PendingAuthResponse(
      authAttemptId: '77',
      authAttemptProofToken: 'pending.proof.token',
      authAttemptProofTokenSignedByIntegration: signature,
      authAttemptChallengeRequired: true,
      contextTitle: 'Payment Approval',
      contextMessage: 'Authorize test batch',
    );
  }

  @override
  Future<RespondAuthResponse> respond(RespondAuthRequest request) async {
    final payload = buildRespondResultPayload(
      'pending.proof.token',
      request.authAttemptId,
      'APPROVED',
      'Approved by integration',
    );
    final signature = await signEd25519WithPkcs8(payload, integrationPkcs8);
    return RespondAuthResponse(
      authAttemptId: request.authAttemptId,
      authAttemptResult: 'APPROVED',
      authAttemptMessage: 'Approved by integration',
      authAttemptProofTokenResultSignedByIntegration: signature,
    );
  }

  @override
  Future<EnrollmentVerifyResponse> verify(EnrollmentVerifyRequest request) async {
    expect(request.challengeResponse, '654321');
    expect(request.devicePublicKey, isNotEmpty);
    expect(request.enrollmentProofTokenSigned, isNotEmpty);
    final canonical = buildEnrollmentVerifyDevicePayload(
      'proof.enroll',
      123,
      654321,
      request.devicePublicKey,
    );
    final pub = ecPublicKeyFromSpkiBase64(request.devicePublicKey);
    expect(
      EzKeyCrypto.verifyDeviceSignature(
        pub,
        canonical,
        request.enrollmentProofTokenSigned,
      ),
      isTrue,
    );
    const verifyMessage = 'Enrollment verified successfully';
    final resultPayload = buildEnrollmentVerifyResultPayload(
      'proof.enroll',
      123,
      'VERIFIED',
      verifyMessage,
    );
    final resultSig = await signEd25519WithPkcs8(resultPayload, integrationPkcs8);
    return EnrollmentVerifyResponse(
      active: true,
      enrollmentVerifyMessage: verifyMessage,
      enrollmentVerifyPayloadSignedByIntegration: resultSig,
    );
  }
}