import 'dart:convert';

import 'package:http/http.dart' as http;

import 'auth_api_models.dart';

abstract class EzkeyAuthApi {
  Future<EnrollmentBindResponse> bind(EnrollmentBindRequest request);

  Future<EnrollmentVerifyResponse> verify(EnrollmentVerifyRequest request);

  Future<PendingAuthResponse?> pending(PendingAuthRequest request);

  Future<RespondAuthResponse> respond(RespondAuthRequest request);
}

class AuthApiException implements Exception {
  AuthApiException({
    required this.method,
    required this.path,
    required this.statusCode,
    required this.body,
  });

  final String method;
  final String path;
  final int statusCode;
  final String body;

  @override
  String toString() {
    final suffix = body.trim().isEmpty ? '' : ': $body';
    return 'AuthApiException $method $path failed with HTTP $statusCode$suffix';
  }
}

class AuthApiClient implements EzkeyAuthApi {
  AuthApiClient({
    required this.baseUri,
    this.client,
    this.timeout = const Duration(seconds: 15),
  });

  final Uri baseUri;
  final http.Client? client;
  final Duration timeout;

  @override
  Future<EnrollmentBindResponse> bind(EnrollmentBindRequest request) async {
    final response = await _postJson('/api/v1/enrollments/bind', request.toJson());
    return EnrollmentBindResponse.fromJson(_decodeJsonMap(response.body));
  }

  @override
  Future<EnrollmentVerifyResponse> verify(EnrollmentVerifyRequest request) async {
    final response = await _postJson('/api/v1/enrollments/verify', request.toJson());
    return EnrollmentVerifyResponse.fromJson(_decodeJsonMap(response.body));
  }

  @override
  Future<PendingAuthResponse?> pending(PendingAuthRequest request) async {
    http.Response response;
    try {
      response = await _postJson(
        '/api/v1/auth-attempts/pending',
        request.toJson(),
        treatNoContentAsNull: true,
      );
    } on AuthApiException catch (error) {
      if (error.statusCode == 204) {
        return null;
      }
      rethrow;
    }
    if (response.body.trim().isEmpty) {
      return null;
    }
    return PendingAuthResponse.fromJson(_decodeJsonMap(response.body));
  }

  @override
  Future<RespondAuthResponse> respond(RespondAuthRequest request) async {
    final response = await _postJson('/api/v1/auth-attempts/respond', request.toJson());
    return RespondAuthResponse.fromJson(_decodeJsonMap(response.body));
  }

  Future<http.Response> _postJson(
    String path,
    Map<String, Object?> body, {
    bool treatNoContentAsNull = false,
  }) async {
    final activeClient = client ?? http.Client();
    final shouldCloseClient = client == null;
    try {
      final response = await activeClient
          .post(
            _buildUri(path),
            headers: const <String, String>{
              'content-type': 'application/json',
              'accept': 'application/json',
            },
            body: jsonEncode(body),
          )
          .timeout(timeout);
      if (treatNoContentAsNull && response.statusCode == 204) {
        throw AuthApiException(
          method: 'POST',
          path: path,
          statusCode: response.statusCode,
          body: response.body,
        );
      }
      if (response.statusCode < 200 || response.statusCode >= 300) {
        throw AuthApiException(
          method: 'POST',
          path: path,
          statusCode: response.statusCode,
          body: response.body,
        );
      }
      return response;
    } finally {
      if (shouldCloseClient) {
        activeClient.close();
      }
    }
  }

  Uri _buildUri(String path) {
    final normalizedPath = path.startsWith('/') ? path : '/$path';
    final basePath = baseUri.path.endsWith('/')
        ? baseUri.path.substring(0, baseUri.path.length - 1)
        : baseUri.path;
    return baseUri.replace(path: '$basePath$normalizedPath');
  }
}

Map<String, dynamic> _decodeJsonMap(String body) {
  final decoded = jsonDecode(body);
  if (decoded is! Map<String, dynamic>) {
    throw const FormatException('Expected a JSON object response');
  }
  return decoded;
}