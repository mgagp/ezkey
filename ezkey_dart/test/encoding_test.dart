import 'dart:typed_data';

import 'package:ezkey_dart/ezkey_dart.dart';
import 'package:test/test.dart';

void main() {
  test('wrapEd25519Raw32ToSpki produces a 44-byte SPKI blob', () {
    final raw = Uint8List.fromList(List<int>.generate(32, (index) => index));
    final spki = wrapEd25519Raw32ToSpki(raw);

    expect(spki.length, 44);
    expect(spki.first, 0x30);
    expect(spki.sublist(0, ed25519SpkiPrefix.length), ed25519SpkiPrefix);
  });

  test('flexibleBase64Decode accepts URL-safe and standard Base64', () {
    const bytes = <int>[0xfb, 0xef, 0xff, 0x00, 0x10];
    final stdEncoded = base64StdEncodeBytes(bytes);
    final urlEncoded = base64UrlEncodeBytes(bytes);

    expect(flexibleBase64Decode(stdEncoded), bytes);
    expect(flexibleBase64Decode(urlEncoded), bytes);
  });
}
