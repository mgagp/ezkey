import 'errors.dart';

final RegExp _urlPattern = RegExp(
  r'^(https?):\/\/([^/?#\s@]+)(\/[^?#\s]*)?$',
  caseSensitive: false,
);

/// Decides which Auth API base URLs an authenticator may talk to.
///
/// HTTPS is always allowed. Plain HTTP is allowed only for [insecureHttpHosts] (default: loopback
/// and the Android emulator alias `10.0.2.2`, same as the reference mobile app). A lab that runs
/// the stack on a Docker network (e.g. host `auth-api`) must opt in explicitly; never ship a
/// production build with extra insecure hosts.
class EzkeyUrlPolicy {
  const EzkeyUrlPolicy({this.insecureHttpHosts = defaultInsecureHttpHosts});

  /// Hosts the reference mobile app accepts over plain HTTP (development only).
  static const Set<String> defaultInsecureHttpHosts = <String>{
    'localhost',
    '127.0.0.1',
    '10.0.2.2',
  };

  /// Strict policy: HTTPS only, no HTTP exception at all.
  static const EzkeyUrlPolicy httpsOnly = EzkeyUrlPolicy(
    insecureHttpHosts: <String>{},
  );

  final Set<String> insecureHttpHosts;

  /// Returns the normalized base URL (`scheme://host[:port][/path]`, no trailing slash, default
  /// HTTPS port dropped), or `null` when [value] is empty or not acceptable.
  String? normalize(String? value) {
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
    final scheme = match.group(1)!.toLowerCase();
    final authority = match.group(2)!;
    final path = match.group(3);
    final parts = authority.split(':');
    if (parts.length > 2 || parts.first.isEmpty) {
      return null;
    }
    final hostname = parts.first.toLowerCase();
    String? port;
    if (parts.length == 2) {
      port = parts[1];
      final portNumber = int.tryParse(port);
      if (portNumber == null || portNumber < 1 || portNumber > 65535) {
        return null;
      }
    }
    if (scheme == 'http' && !insecureHttpHosts.contains(hostname)) {
      return null;
    }
    if (scheme == 'https' && port == '443') {
      port = null;
    }
    var normalized = '$scheme://$hostname${port == null ? '' : ':$port'}';
    if (path != null && path != '/') {
      normalized += path.replaceAll(RegExp(r'/+$'), '');
    }
    return normalized;
  }

  /// Like [normalize] but throws [EzkeyValidationException] when the URL is not acceptable.
  Uri require(String value, {String field = 'authApiBaseUrl'}) {
    final normalized = normalize(value);
    if (normalized == null) {
      throw EzkeyValidationException(
        '$field must be an https:// URL (plain http only for allowed development hosts)',
      );
    }
    return Uri.parse(normalized);
  }
}
