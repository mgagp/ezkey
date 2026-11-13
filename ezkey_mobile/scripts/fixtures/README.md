# Play upload fixtures

`aab-proto-manifest-vc42.bin` is a minimal hand-encoded `aapt.pb.XmlNode` (aapt2
Resources.proto) with `android:versionCode=42` and `android:versionName=0.1.0-alpha`
on the root `manifest` element. It is **not** from the Ezkey signed AAB — only
enough protobuf to exercise `play_upload_internal.version_info_from_manifest_bytes`
the same way a real AAB `base/manifest/AndroidManifest.xml` is encoded (starts
with `0x0a`, not binary AXML magic `03 00 08 00`).
