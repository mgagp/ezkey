import 'dart:async';
import 'dart:convert';

import 'package:http/http.dart' as http;

import 'auth_api_models.dart';
import 'errors.dart';

/// Device-facing Auth API endpoints, relative to the Auth API base URL.
abstract final class AuthApiPaths {
  static const String bind = '/api/v1/enrollments/bind';
  static const String verify = '/api/v1/enrollments/verify';
  static const String instanceInfo = '/api/v1/enrollments/instance-info';
  static const String pending = '/api/v1/auth-attempts/pending';
  static const String respond = '/api/v1/auth-attempts/respond';
}

/// Raw transport of the five device-facing Auth API calls.
///
/// No signature checks happen here; [EzkeyAuthenticator] verifies every answer. Implement this
/// interface to plug in another HTTP stack (Dio, certificate pinning, proxies) or a fake.
abstract interface class EzkeyAuthApi {
  Future<EnrollmentBindResponse> bind(EnrollmentBindRequest request);

  Future<EnrollmentVerifyResponse> verify(EnrollmentVerifyRequest request);

  Future<InstanceInfoResponse> instanceInfo(InstanceInfoRequest request);

  /// Returns `null` on HTTP 204 (nothing pending).
  Future<PendingAuthResponse?> pending(PendingAuthRequest request);

  Future<RespondAuthResponse> respond(RespondAuthRequest request);
}

/// [EzkeyAuthApi] over `package:http`.
///
/// * Non-2xx answers become [EzkeyApiException] with the parsed RFC 9457 problem.
/// * Timeouts, socket, TLS and client errors become [EzkeyNetworkException].
/// * Exception messages never contain request or response bodies (they carry secrets).
///
/// TLS: certificate validation is whatever the supplied [http.Client] does (system trust store by
/// default). Do not install a permissive `badCertificateCallback` in production.
class AuthApiClient implements EzkeyAuthApi {
  AuthApiClient(
    this.baseUri, {
    http.Client? client,
    this.timeout = const Duration(seconds: 15),
  }) : _client = client ?? http.Client(),
       _ownsClient = client == null;

  /// Normalized Auth API base URL (may include a path prefix when behind a reverse proxy).
  final Uri baseUri;
  final Duration timeout;
  final http.Client _client;
  final bool _ownsClient;

  @override
  Future<EnrollmentBindResponse> bind(EnrollmentBindRequest request) async {
    final json = await _postJson(AuthApiPaths.bind, request.toJson());
    return EnrollmentBindResponse.fromJson(_requireObject(json));
  }

  @override
  Future<EnrollmentVerifyResponse> verify(
    EnrollmentVerifyRequest request,
  ) async {
    final json = await _postJson(AuthApiPaths.verify, request.toJson());
    return EnrollmentVerifyResponse.fromJson(_requireObject(json));
  }

  @override
  Future<InstanceInfoResponse> instanceInfo(InstanceInfoRequest request) async {
    final json = await _postJson(AuthApiPaths.instanceInfo, request.toJson());
    return InstanceInfoResponse.fromJson(_requireObject(json));
  }

  @override
  Future<PendingAuthResponse?> pending(PendingAuthRequest request) async {
    final json = await _postJson(
      AuthApiPaths.pending,
      request.toJson(),
      allowNoContent: true,
    );
    if (json == null) {
      return null;
    }
    return PendingAuthResponse.fromJson(_requireObject(json));
  }

  @override
  Future<RespondAuthResponse> respond(RespondAuthRequest request) async {
    final json = await _postJson(AuthApiPaths.respond, request.toJson());
    return RespondAuthResponse.fromJson(_requireObject(json));
  }

  /// Closes the underlying client when this instance created it.
  void close() {
    if (_ownsClient) {
      _client.close();
    }
  }

  Uri _resolve(String path) {
    final prefix = baseUri.path.replaceAll(RegExp(r'/+$'), '');
    return baseUri.replace(path: '$prefix$path');
  }

  Future<Object?> _postJson(
    String path,
    Map<String, Object?> body, {
    bool allowNoContent = false,
  }) async {
    http.Response response;
    try {
      response = await _client
          .post(
            _resolve(path),
            headers: const <String, String>{
              'Content-Type': 'application/json',
              'Accept': 'application/json, application/problem+json',
            },
            body: jsonEncode(body),
          )
          .timeout(timeout);
    } on TimeoutException catch (error) {
      throw EzkeyNetworkException(
        'POST $path timed out after ${timeout.inSeconds}s',
        cause: error,
      );
    } on http.ClientException catch (error) {
      throw EzkeyNetworkException(
        'POST $path failed: transport error',
        cause: error,
      );
    } on Exception catch (error) {
      // SocketException / HandshakeException / TlsException on dart:io; kept generic so the
      // library stays importable on every platform.
      throw EzkeyNetworkException(
        'POST $path failed: ${error.runtimeType}',
        cause: error,
      );
    }

    final status = response.statusCode;
    if (status == 204 && allowNoContent) {
      return null;
    }
    if (status < 200 || status >= 300) {
      throw EzkeyApiException(
        method: 'POST',
        path: path,
        statusCode: status,
        problem: EzkeyProblem.tryParse(
          utf8.decode(response.bodyBytes, allowMalformed: true),
        ),
      );
    }
    if (status == 204) {
      throw EzkeyIntegrityException(
        EzkeyIntegrityCheck.malformedResponse,
        'POST $path returned no content',
      );
    }
    try {
      return jsonDecode(utf8.decode(response.bodyBytes));
    } on FormatException {
      throw EzkeyIntegrityException(
        EzkeyIntegrityCheck.malformedResponse,
        'POST $path returned a non-JSON body',
      );
    }
  }
}

Map<String, dynamic> _requireObject(Object? json) {
  if (json is Map<String, dynamic>) {
    return json;
  }
  throw const EzkeyIntegrityException(
    EzkeyIntegrityCheck.malformedResponse,
    'Expected a JSON object',
  );
}
