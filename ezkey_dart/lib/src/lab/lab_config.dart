import 'dart:io';

/// Base URLs of a running Ezkey stack.
///
/// Defaults target the Docker network used by `scripts/e2e.sh` (service hostnames on
/// `ezkey_ezkey-network`). From the host, use `http://localhost:8080` etc. Each value can be
/// overridden with an environment variable or a `.env` file entry.
class LabEndpoints {
  LabEndpoints({
    required this.authApi,
    required this.adminApi,
    required this.integrationApi,
    required this.cryptoApi,
  });

  static const String authApiVar = 'EZKEY_AUTH_API_URL';
  static const String adminApiVar = 'EZKEY_ADMIN_API_URL';
  static const String integrationApiVar = 'EZKEY_INTEGRATION_API_URL';
  static const String cryptoApiVar = 'EZKEY_CRYPTO_API_URL';

  final String authApi;
  final String adminApi;
  final String integrationApi;
  final String cryptoApi;

  /// Merges, by increasing priority: Docker-network defaults, [envFile] entries, process
  /// environment.
  static LabEndpoints load({File? envFile, Map<String, String>? environment}) {
    final values = <String, String>{
      if (envFile != null && envFile.existsSync())
        ...parseEnvFileContents(envFile.readAsStringSync()),
      ...(environment ?? Platform.environment),
    };
    String pick(String key, String fallback) {
      final value = values[key]?.trim();
      return value == null || value.isEmpty ? fallback : value;
    }

    return LabEndpoints(
      authApi: pick(authApiVar, 'http://auth-api:8080'),
      adminApi: pick(adminApiVar, 'http://admin-api:9080'),
      integrationApi: pick(integrationApiVar, 'http://integration-api:7080'),
      cryptoApi: pick(cryptoApiVar, 'http://crypto-api:9090'),
    );
  }
}

/// Parses `KEY=value` lines (`#` comments, optional `export `, optional matching quotes).
Map<String, String> parseEnvFileContents(String contents) {
  final values = <String, String>{};
  for (final rawLine in contents.split(RegExp(r'\r?\n'))) {
    final line = rawLine.trim();
    if (line.isEmpty || line.startsWith('#')) {
      continue;
    }
    final withoutExport =
        line.startsWith('export ') ? line.substring(7).trim() : line;
    final separatorIndex = withoutExport.indexOf('=');
    if (separatorIndex <= 0) {
      continue;
    }
    final key = withoutExport.substring(0, separatorIndex).trim();
    var value = withoutExport.substring(separatorIndex + 1).trim();
    if (value.length >= 2) {
      final first = value[0];
      final last = value[value.length - 1];
      if ((first == '"' && last == '"') || (first == "'" && last == "'")) {
        value = value.substring(1, value.length - 1);
      }
    }
    values[key] = value;
  }
  return values;
}
