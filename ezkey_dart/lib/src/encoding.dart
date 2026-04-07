import 'dart:convert';
import 'dart:typed_data';

const List<int> ed25519SpkiPrefix = <int>[
  0x30,
  0x2a,
  0x30,
  0x05,
  0x06,
  0x03,
  0x2b,
  0x65,
  0x70,
  0x03,
  0x21,
  0x00,
];

String base64UrlEncodeBytes(List<int> bytes) =>
    base64Url.encode(bytes).replaceAll('=', '');

String base64StdEncodeBytes(List<int> bytes) => base64.encode(bytes);

Uint8List base64UrlDecode(String input) {
  return Uint8List.fromList(
    base64Url.decode(_withPadding(_stripPemAndWhitespace(input))),
  );
}

Uint8List base64StdDecode(String input) {
  return Uint8List.fromList(
    base64.decode(_withPadding(_stripPemAndWhitespace(input))),
  );
}

Uint8List flexibleBase64Decode(String input) {
  final normalized = _stripPemAndWhitespace(input);
  try {
    return base64UrlDecode(normalized);
  } on FormatException {
    return base64StdDecode(normalized);
  }
}

Uint8List wrapEd25519Raw32ToSpki(List<int> raw32) {
  if (raw32.length != 32) {
    throw ArgumentError.value(
      raw32.length,
      'raw32',
      'Expected exactly 32 bytes',
    );
  }
  return Uint8List.fromList(<int>[...ed25519SpkiPrefix, ...raw32]);
}

String stripPemAndWhitespace(String input) => _stripPemAndWhitespace(input);

String _stripPemAndWhitespace(String input) {
  if (input.contains('-----BEGIN')) {
    return input
        .split('\n')
        .where((line) => line.isNotEmpty && !line.startsWith('-----'))
        .map((line) => line.trim())
        .join();
  }
  return input.replaceAll(RegExp(r'\s+'), '');
}

String _withPadding(String input) {
  final remainder = input.length % 4;
  if (remainder == 0) {
    return input;
  }
  return input.padRight(input.length + (4 - remainder), '=');
}
