import 'dart:convert';

/// Base type of every error the SDK throws on purpose.
///
/// `toString()` never contains proof tokens, signatures, private keys, or raw response bodies,
/// so exceptions can be logged safely.
sealed class EzkeyException implements Exception {
  const EzkeyException(this.message);

  /// Developer-facing description (English, safe to log). Do not show it verbatim to end users;
  /// map the exception type / [EzkeyApiException.kind] to localized copy instead.
  final String message;

  /// Whether retrying the same operation later can succeed.
  bool get retryable;

  @override
  String toString() => '$runtimeType: $message';
}

/// Caller input rejected locally before anything was sent (bad enrollment code, malformed
/// challenge, unacceptable URL, unknown enrollment, ...).
final class EzkeyValidationException extends EzkeyException {
  const EzkeyValidationException(super.message);

  @override
  bool get retryable => false;
}

/// Which signed artefact failed verification.
enum EzkeyIntegrityCheck {
  bindSignature,
  bindConsistency,
  unsupportedIntegrationKeyAlgorithm,
  verifySignature,
  verifyNotActive,
  pendingSignature,
  pendingConsistency,
  respondSignature,
  respondConsistency,
  instanceInfoSignature,
  instanceInfoConsistency,
  malformedResponse,
}

/// A server response could not be authenticated or is inconsistent with the request.
///
/// Fail closed: treat the server answer as untrusted (possible MITM, wrong server, or contract
/// drift). Do not display server-provided text, do not persist enrollment state, and do not
/// approve anything based on it.
final class EzkeyIntegrityException extends EzkeyException {
  const EzkeyIntegrityException(this.check, String message) : super(message);

  final EzkeyIntegrityCheck check;

  @override
  bool get retryable => false;

  @override
  String toString() => 'EzkeyIntegrityException(${check.name}): $message';
}

/// Transport failure: timeout, DNS, connection reset, TLS handshake failure.
final class EzkeyNetworkException extends EzkeyException {
  const EzkeyNetworkException(super.message, {this.cause});

  /// Underlying error (e.g. `SocketException`, `TimeoutException`, `HandshakeException`).
  final Object? cause;

  @override
  bool get retryable => true;
}

/// Local state problem (enrollment record or device key missing / unusable).
final class EzkeyLocalStateException extends EzkeyException {
  const EzkeyLocalStateException(super.message);

  @override
  bool get retryable => false;
}

/// Stable classification of Auth API RFC 9457 problem types (`https://ezkey.io/problems/...`).
enum EzkeyProblemKind {
  /// Respond refused: attempt unknown, not yet claimed, already decided, cancelled by the
  /// integration, or superseded by a newer attempt. The decision is final — show "no longer
  /// active" and offer Back only.
  authAttemptStateConflict,

  /// Respond refused because the attempt's `expiresAt` passed. Final — show "expired".
  authAttemptExpired,

  /// Server cannot sign the respond result right now (HTTP 503). Retry later.
  integrationSigningUnavailable,

  /// Pending/respond refused (bad enrollment proof token, invalid device signature, replayed
  /// device proof token, inactive enrollment).
  authAttemptBindingFailed,

  /// Respond could not be processed.
  authAttemptRespondFailed,

  /// Invitation already consumed by another device.
  enrollmentAlreadyBound,

  /// Invitation unknown or invalid.
  enrollmentBindingFailed,

  /// Invitation expired (pending enrollment TTL).
  enrollmentInvitationExpired,

  /// Invitation cannot be used (already used, disabled).
  enrollmentNotAvailable,

  /// Server-side integration configuration problem for this enrollment.
  enrollmentIntegrationNotFound,

  /// Verify rejected (wrong challenge, bad signature, wrong state).
  enrollmentVerifyFailed,

  /// Enrollment lifecycle forbids the operation (e.g. deactivated or revoked).
  enrollmentStateConflict,

  /// Enrolled instance-info could not be produced.
  enrollmentInstanceInfoFailed,

  /// Request body failed validation or could not be parsed.
  validationFailed,

  /// HTTP 429.
  rateLimited,

  /// Audit-chain supervision degraded (fail-closed server mode); retry later.
  auditChainDegraded,

  /// Server error without a more specific type.
  internalError,

  /// Anything else (unknown type, non-JSON body, proxy error page).
  unknown,
}

const Map<String, EzkeyProblemKind> _kindsByType = <String, EzkeyProblemKind>{
  'https://ezkey.io/problems/auth/auth-attempt-state-conflict':
      EzkeyProblemKind.authAttemptStateConflict,
  'https://ezkey.io/problems/auth/auth-attempt-expired':
      EzkeyProblemKind.authAttemptExpired,
  'https://ezkey.io/problems/auth/integration-signing-unavailable':
      EzkeyProblemKind.integrationSigningUnavailable,
  'https://ezkey.io/problems/auth/auth-attempt-binding-failed':
      EzkeyProblemKind.authAttemptBindingFailed,
  'https://ezkey.io/problems/auth/auth-attempt-respond-failed':
      EzkeyProblemKind.authAttemptRespondFailed,
  'https://ezkey.io/problems/auth/enrollment-already-bound':
      EzkeyProblemKind.enrollmentAlreadyBound,
  'https://ezkey.io/problems/auth/enrollment-binding-failed':
      EzkeyProblemKind.enrollmentBindingFailed,
  'https://ezkey.io/problems/auth/enrollment-invitation-expired':
      EzkeyProblemKind.enrollmentInvitationExpired,
  'https://ezkey.io/problems/auth/enrollment-not-available':
      EzkeyProblemKind.enrollmentNotAvailable,
  'https://ezkey.io/problems/auth/enrollment-integration-not-found':
      EzkeyProblemKind.enrollmentIntegrationNotFound,
  'https://ezkey.io/problems/auth/enrollment-verify-failed':
      EzkeyProblemKind.enrollmentVerifyFailed,
  'https://ezkey.io/problems/auth/enrollment-state-conflict':
      EzkeyProblemKind.enrollmentStateConflict,
  'https://ezkey.io/problems/auth/enrollment-instance-info-failed':
      EzkeyProblemKind.enrollmentInstanceInfoFailed,
  'https://ezkey.io/problems/auth/validation-failed':
      EzkeyProblemKind.validationFailed,
  'https://ezkey.io/problems/auth/invalid-request-body':
      EzkeyProblemKind.validationFailed,
  'https://ezkey.io/problems/auth/internal-error':
      EzkeyProblemKind.internalError,
  'https://ezkey.io/problems/system/audit-chain-heartbeat-degraded':
      EzkeyProblemKind.auditChainDegraded,
};

/// Parsed RFC 9457 problem detail (`application/problem+json`).
class EzkeyProblem {
  const EzkeyProblem({
    this.type,
    this.title,
    this.status,
    this.detail,
    this.instance,
  });

  final String? type;
  final String? title;
  final int? status;

  /// Server-provided explanation. Catalog text, but do not display it to end users: the reference
  /// app maps [type] to local copy only.
  final String? detail;
  final String? instance;

  /// Parses [body] when it looks like a problem document; returns `null` otherwise.
  static EzkeyProblem? tryParse(String body) {
    if (body.trim().isEmpty) {
      return null;
    }
    Object? decoded;
    try {
      decoded = jsonDecode(body);
    } on FormatException {
      return null;
    }
    if (decoded is! Map<String, dynamic>) {
      return null;
    }
    final type = decoded['type'];
    final title = decoded['title'];
    if (type is! String && title is! String) {
      return null;
    }
    final status = decoded['status'];
    return EzkeyProblem(
      type: type is String ? type.trim() : null,
      title: title is String ? title : null,
      status: status is int ? status : null,
      detail: decoded['detail'] is String ? decoded['detail'] as String : null,
      instance:
          decoded['instance'] is String ? decoded['instance'] as String : null,
    );
  }
}

/// Non-2xx HTTP answer from an Ezkey API.
final class EzkeyApiException extends EzkeyException {
  EzkeyApiException({
    required this.method,
    required this.path,
    required this.statusCode,
    this.problem,
  }) : kind = classifyProblem(statusCode, problem?.type),
       super(
         '$method $path failed with HTTP $statusCode'
         '${problem?.type == null ? '' : ' (${problem!.type})'}',
       );

  final String method;
  final String path;
  final int statusCode;
  final EzkeyProblem? problem;
  final EzkeyProblemKind kind;

  /// `true` for respond outcomes that are final: the attempt is gone (cancelled, superseded,
  /// already answered) or expired. UI: show a neutral message and offer Back only.
  bool get isAttemptNoLongerActionable =>
      kind == EzkeyProblemKind.authAttemptStateConflict ||
      kind == EzkeyProblemKind.authAttemptExpired;

  @override
  bool get retryable {
    switch (kind) {
      case EzkeyProblemKind.integrationSigningUnavailable:
      case EzkeyProblemKind.rateLimited:
      case EzkeyProblemKind.auditChainDegraded:
      case EzkeyProblemKind.internalError:
        return true;
      case EzkeyProblemKind.unknown:
        return statusCode >= 500;
      default:
        return false;
    }
  }
}

/// Maps an HTTP status and problem `type` URI to an [EzkeyProblemKind].
EzkeyProblemKind classifyProblem(int statusCode, String? type) {
  final known = type == null ? null : _kindsByType[type.trim()];
  if (known != null) {
    return known;
  }
  if (statusCode == 429) {
    return EzkeyProblemKind.rateLimited;
  }
  return EzkeyProblemKind.unknown;
}
