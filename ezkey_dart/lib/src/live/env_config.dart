import 'dart:io';

const List<String> _devHttpHosts = <String>['localhost', '127.0.0.1', '10.0.2.2'];
final RegExp _urlPattern = RegExp(r'^(https?):\/\/([^/?#\s]+)(\/[^?#\s]*)?$');

class EzkeyRunnerConfig {
  EzkeyRunnerConfig({
    required this.authApiBaseUri,
    required this.timeout,
    required this.language,
    required this.envFilePath,
    required this.envFileFound,
  });

  final Uri? authApiBaseUri;
  final Duration timeout;
  final String? language;
  final String envFilePath;
  final bool envFileFound;

  static Future<EzkeyRunnerConfig> load({
    Directory? workingDirectory,
    String envFileName = '.env',
  }) async {
    final directory = workingDirectory ?? Directory.current;
    final envFile = File('${directory.path}${Platform.pathSeparator}$envFileName');
    final fileValues = envFile.existsSync()
        ? parseEnvFileContents(await envFile.readAsString())
        : <String, String>{};
    final merged = <String, String>{...fileValues, ...Platform.environment};
    final authUrlValue = merged['EZKEY_AUTH_API_URL'] ?? merged['EZKEY_API_BASE_URL'];
    final timeoutValue = merged['EZKEY_AUTH_TIMEOUT_MS'];
    final timeoutMs = timeoutValue == null ? 15000 : int.tryParse(timeoutValue.trim());
    if (timeoutMs == null || timeoutMs <= 0) {
      throw const FormatException('EZKEY_AUTH_TIMEOUT_MS must be a positive integer');
    }

    return EzkeyRunnerConfig(
      authApiBaseUri: _toUri(validateAuthUrl(authUrlValue)),
      timeout: Duration(milliseconds: timeoutMs),
      language: _normalizeOptional(merged['EZKEY_LANGUAGE']),
      envFilePath: envFile.path,
      envFileFound: envFile.existsSync(),
    );
  }

  ResolvedAuthApiBaseUri resolveAuthApiBaseUri(String? enrollmentAuthUrl) {
    final codeUri = _toUri(validateAuthUrl(enrollmentAuthUrl));
    if (codeUri != null && authApiBaseUri != null && codeUri != authApiBaseUri) {
      return ResolvedAuthApiBaseUri(
        uri: codeUri,
        warning:
            'Enrollment code authUrl ($codeUri) differs from env Auth API URL '
            '($authApiBaseUri). The enrollment code URL will be used.',
      );
    }
    if (codeUri != null) {
      return ResolvedAuthApiBaseUri(uri: codeUri);
    }
    if (authApiBaseUri != null) {
      return ResolvedAuthApiBaseUri(uri: authApiBaseUri!);
    }
    throw const FormatException(
      'No Auth API URL available. Provide authUrl in the enrollment code or set '
      'EZKEY_AUTH_API_URL in the env file.',
    );
  }
}

class ResolvedAuthApiBaseUri {
  ResolvedAuthApiBaseUri({required this.uri, this.warning});

  final Uri uri;
  final String? warning;
}

Map<String, String> parseEnvFileContents(String contents) {
  final values = <String, String>{};
  for (final rawLine in contents.split(RegExp(r'\r?\n'))) {
    final line = rawLine.trim();
    if (line.isEmpty || line.startsWith('#')) {
      continue;
    }
    final withoutExport = line.startsWith('export ') ? line.substring(7).trim() : line;
    final separatorIndex = withoutExport.indexOf('=');
    if (separatorIndex <= 0) {
      continue;
    }
    final key = withoutExport.substring(0, separatorIndex).trim();
    final rawValue = withoutExport.substring(separatorIndex + 1).trim();
    values[key] = _stripMatchingQuotes(rawValue);
  }
  return values;
}

String? validateAuthUrl(String? value) {
  if (value == null) {
    return null;
  }
  final trimmed = value.trim();
  if (trimmed.isEmpty) {
    return null;
  }
  final match = _urlPattern.firstMatch(trimmed);
  if (match == null) {
    return null;
  }
  final scheme = match.group(1)!;
  final host = match.group(2)!;
  final path = match.group(3);
  final hostname = host.split(':').first;
  final isHttps = scheme == 'https';
  final isDevHttp = scheme == 'http' && _devHttpHosts.contains(hostname);
  if (!isHttps && !isDevHttp) {
    return null;
  }

  var normalized = '$scheme://$host';
  if (path != null && path != '/') {
    normalized += path.replaceAll(RegExp(r'/+$'), '');
  }
  return normalized;
}

Uri? _toUri(String? value) => value == null ? null : Uri.parse(value);

String? _normalizeOptional(String? value) {
  if (value == null) {
    return null;
  }
  final normalized = value.trim();
  return normalized.isEmpty ? null : normalized;
}

String _stripMatchingQuotes(String value) {
  if (value.length >= 2) {
    final first = value[0];
    final last = value[value.length - 1];
    if ((first == '"' && last == '"') || (first == "'" && last == "'")) {
      return value.substring(1, value.length - 1);
    }
  }
  return value;
}