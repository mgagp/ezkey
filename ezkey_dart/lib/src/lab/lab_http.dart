import 'dart:async';
import 'dart:convert';

import 'package:http/http.dart' as http;

import '../sdk/errors.dart';

/// Lab-side HTTP failure. Carries status and RFC 9457 type/title only — never the body, because
/// Admin / Integration API answers can contain secrets (API secret keys, proof tokens).
class LabHttpException implements Exception {
  LabHttpException(this.method, this.path, this.statusCode, this.problem);

  final String method;
  final String path;
  final int statusCode;
  final EzkeyProblem? problem;

  @override
  String toString() =>
      'LabHttpException: $method $path -> HTTP $statusCode'
      '${problem?.type == null ? '' : ' ${problem!.type}'}'
      '${problem?.title == null ? '' : ' (${problem!.title})'}';
}

/// Minimal JSON-over-HTTP helper used by the lab clients (Admin API, Integration API, crypto-api).
class LabHttp {
  LabHttp(
    this.baseUri, {
    http.Client? client,
    Map<String, String> headers = const <String, String>{},
    this.timeout = const Duration(seconds: 30),
  }) : _client = client ?? http.Client(),
       _headers = <String, String>{
         'Accept': 'application/json, application/problem+json',
         ...headers,
       };

  final Uri baseUri;
  final Duration timeout;
  final http.Client _client;
  final Map<String, String> _headers;

  Uri resolve(String path, [Map<String, String>? query]) {
    final prefix = baseUri.path.replaceAll(RegExp(r'/+$'), '');
    return baseUri.replace(path: '$prefix$path', queryParameters: query);
  }

  Future<Object?> get(
    String path, {
    Map<String, String>? query,
    Map<String, String>? headers,
    Duration? timeout,
  }) {
    return _send('GET', path, query: query, headers: headers, timeout: timeout);
  }

  Future<Object?> post(
    String path,
    Object? body, {
    Map<String, String>? headers,
    Duration? timeout,
  }) {
    return _send('POST', path, body: body, headers: headers, timeout: timeout);
  }

  Future<Object?> _send(
    String method,
    String path, {
    Object? body,
    Map<String, String>? query,
    Map<String, String>? headers,
    Duration? timeout,
  }) async {
    final request =
        http.Request(method, resolve(path, query))
          ..headers.addAll(_headers)
          ..headers.addAll(headers ?? const <String, String>{});
    if (body != null) {
      request.headers['Content-Type'] = 'application/json';
      request.body = jsonEncode(body);
    }
    final effectiveTimeout = timeout ?? this.timeout;
    http.Response response;
    try {
      response = await http.Response.fromStream(
        await _client.send(request).timeout(effectiveTimeout),
      ).timeout(effectiveTimeout);
    } on TimeoutException {
      throw LabHttpException(
        method,
        path,
        0,
        const EzkeyProblem(title: 'timeout'),
      );
    }
    final text = utf8.decode(response.bodyBytes, allowMalformed: true);
    if (response.statusCode < 200 || response.statusCode >= 300) {
      throw LabHttpException(
        method,
        path,
        response.statusCode,
        EzkeyProblem.tryParse(text),
      );
    }
    if (text.trim().isEmpty) {
      return null;
    }
    try {
      return jsonDecode(text);
    } on FormatException {
      // The default message quotes the source text, which may contain secrets.
      throw FormatException('$method $path returned a non-JSON body');
    }
  }

  void close() => _client.close();
}

Map<String, dynamic> asJsonObject(Object? value, String what) {
  if (value is Map<String, dynamic>) {
    return value;
  }
  throw FormatException('$what: expected a JSON object');
}
