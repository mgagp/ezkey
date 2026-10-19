#!/usr/bin/env python3
# Copyright (c) 2026 Ezkey contributors.
# SPDX-License-Identifier: Apache-2.0
#
# Android Publisher API v3 helper for uploading a signed AAB to the Play
# *internal* track only. Invoked by scripts/play-upload-internal.sh.
#
# Prerequisites (host, not app build):
#   pip install -r scripts/requirements-play-upload.txt
#
# Never prints service-account JSON contents.
#
# AAB manifests are aapt2 protobuf (aapt.pb.XmlNode), not binary AXML.
# See frameworks/base/tools/aapt2/Resources.proto.

from __future__ import annotations

import argparse
import os
import struct
import subprocess
import sys
import zipfile
from pathlib import Path
from typing import Any

PACKAGE_NAME = "org.ezkey.mobile"
INTERNAL_TRACK = "internal"
# android:versionCode resource id
_VERSION_CODE_ATTR = 0x0101021B
_TYPE_INT_DEC = 0x10
_TYPE_INT_HEX = 0x11
# Binary AXML file magic (ResChunk RES_XML_TYPE=0x0003, headerSize=8)
_AXML_MAGIC = b"\x03\x00\x08\x00"

_WIRE_VARINT = 0
_WIRE_64 = 1
_WIRE_LEN = 2
_WIRE_32 = 5


def read_version_code_from_aab(aab_path: Path) -> int:
    """Extract android:versionCode from an AAB's base AndroidManifest.xml."""
    with zipfile.ZipFile(aab_path) as zf:
        try:
            data = zf.read("base/manifest/AndroidManifest.xml")
        except KeyError as exc:
            raise ValueError(
                f"AAB missing base/manifest/AndroidManifest.xml: {aab_path}"
            ) from exc
    try:
        return version_code_from_manifest_bytes(data)
    except ValueError as primary:
        # Optional fallback when BUNDLETOOL_JAR is set (operator workstation).
        fallback = _version_code_via_bundletool(aab_path)
        if fallback is not None:
            return fallback
        raise primary


def version_code_from_manifest_bytes(data: bytes) -> int:
    """Decode versionCode from AAB proto XmlNode or (legacy) binary AXML."""
    if len(data) < 4:
        raise ValueError("AndroidManifest.xml too small")
    if data.startswith(_AXML_MAGIC):
        return _version_code_from_axml(data)
    # AAB / aapt2 proto format starts with field 1 (element) length-delimited → 0x0a
    return _version_code_from_proto_xml(data)


def _version_code_via_bundletool(aab_path: Path) -> int | None:
    jar = os.environ.get("BUNDLETOOL_JAR", "").strip()
    if not jar:
        return None
    jar_path = Path(jar).expanduser()
    if not jar_path.is_file():
        print(
            f"Warning: BUNDLETOOL_JAR set but not found: {jar_path}",
            file=sys.stderr,
        )
        return None
    cmd = [
        "java",
        "-jar",
        str(jar_path),
        "dump",
        "manifest",
        f"--bundle={aab_path}",
        "--xpath=/manifest/@android:versionCode",
    ]
    try:
        proc = subprocess.run(
            cmd, check=True, capture_output=True, text=True, timeout=120
        )
    except (OSError, subprocess.SubprocessError) as exc:
        print(f"Warning: bundletool fallback failed: {exc}", file=sys.stderr)
        return None
    text = (proc.stdout or "").strip()
    if not text:
        return None
    try:
        return int(text)
    except ValueError:
        print(
            f"Warning: bundletool returned non-integer versionCode: {text!r}",
            file=sys.stderr,
        )
        return None


# --- protobuf (aapt.pb.XmlNode) walker ---------------------------------


def _read_varint(buf: bytes, pos: int) -> tuple[int, int]:
    result = 0
    shift = 0
    while pos < len(buf):
        byte = buf[pos]
        pos += 1
        result |= (byte & 0x7F) << shift
        if not (byte & 0x80):
            return result, pos
        shift += 7
        if shift > 70:
            raise ValueError("varint too long")
    raise ValueError("truncated varint")


def _skip_field(buf: bytes, pos: int, wire: int) -> int:
    if wire == _WIRE_VARINT:
        _, pos = _read_varint(buf, pos)
        return pos
    if wire == _WIRE_64:
        return pos + 8
    if wire == _WIRE_32:
        return pos + 4
    if wire == _WIRE_LEN:
        length, pos = _read_varint(buf, pos)
        return pos + length
    raise ValueError(f"unsupported wire type {wire}")


def _read_len_payload(buf: bytes, pos: int) -> tuple[bytes, int]:
    length, pos = _read_varint(buf, pos)
    end = pos + length
    if end > len(buf):
        raise ValueError("truncated length-delimited field")
    return buf[pos:end], end


def _as_signed_int32(n: int) -> int:
    n &= 0xFFFFFFFF
    if n >= 0x80000000:
        return n - 0x100000000
    return n


def _version_code_from_proto_xml(data: bytes) -> int:
    """Walk aapt.pb.XmlNode for android:versionCode on the manifest element."""
    pos = 0
    while pos < len(data):
        tag, pos = _read_varint(data, pos)
        field, wire = tag >> 3, tag & 7
        if field == 1 and wire == _WIRE_LEN:  # XmlNode.element
            payload, pos = _read_len_payload(data, pos)
            found = _find_version_code_in_element(payload, depth=0)
            if found is not None:
                return found
        elif field == 2 and wire == _WIRE_LEN:  # XmlNode.text — ignore
            _, pos = _read_len_payload(data, pos)
        else:
            pos = _skip_field(data, pos, wire)
    raise ValueError("versionCode not found in protobuf AndroidManifest.xml")


def _find_version_code_in_element(buf: bytes, depth: int) -> int | None:
    if depth > 64:
        raise ValueError("protobuf XmlNode nesting too deep")
    pos = 0
    while pos < len(buf):
        tag, pos = _read_varint(buf, pos)
        field, wire = tag >> 3, tag & 7
        if field == 4 and wire == _WIRE_LEN:  # XmlElement.attribute
            payload, pos = _read_len_payload(buf, pos)
            vc = _version_code_from_attribute(payload)
            if vc is not None:
                return vc
        elif field == 5 and wire == _WIRE_LEN:  # XmlElement.child (XmlNode)
            child, pos = _read_len_payload(buf, pos)
            # Child XmlNode: look for element = 1
            cpos = 0
            while cpos < len(child):
                ctag, cpos = _read_varint(child, cpos)
                cfield, cwire = ctag >> 3, ctag & 7
                if cfield == 1 and cwire == _WIRE_LEN:
                    el, cpos = _read_len_payload(child, cpos)
                    found = _find_version_code_in_element(el, depth + 1)
                    if found is not None:
                        return found
                else:
                    cpos = _skip_field(child, cpos, cwire)
        else:
            pos = _skip_field(buf, pos, wire)
    return None


def _version_code_from_attribute(buf: bytes) -> int | None:
    """Return versionCode from one XmlAttribute, or None if not that attr."""
    name = ""
    value_str = ""
    resource_id = 0
    compiled: int | None = None
    pos = 0
    while pos < len(buf):
        tag, pos = _read_varint(buf, pos)
        field, wire = tag >> 3, tag & 7
        if field == 2 and wire == _WIRE_LEN:  # name
            raw, pos = _read_len_payload(buf, pos)
            name = raw.decode("utf-8", errors="replace")
        elif field == 3 and wire == _WIRE_LEN:  # value
            raw, pos = _read_len_payload(buf, pos)
            value_str = raw.decode("utf-8", errors="replace")
        elif field == 5 and wire == _WIRE_VARINT:  # resource_id
            resource_id, pos = _read_varint(buf, pos)
            resource_id &= 0xFFFFFFFF
        elif field == 6 and wire == _WIRE_LEN:  # compiled_item (Item)
            item, pos = _read_len_payload(buf, pos)
            compiled = _int_from_item(item)
        else:
            pos = _skip_field(buf, pos, wire)

    is_vc = resource_id == _VERSION_CODE_ATTR or name == "versionCode"
    if not is_vc:
        return None
    if compiled is not None:
        return compiled
    if value_str:
        try:
            return int(value_str, 0)
        except ValueError as exc:
            raise ValueError(
                f"versionCode attribute has non-integer value: {value_str!r}"
            ) from exc
    return None


def _int_from_item(buf: bytes) -> int | None:
    """Extract int from aapt.pb.Item (prim / str)."""
    pos = 0
    while pos < len(buf):
        tag, pos = _read_varint(buf, pos)
        field, wire = tag >> 3, tag & 7
        if field == 2 and wire == _WIRE_LEN:  # String { value = 1 }
            inner, pos = _read_len_payload(buf, pos)
            return _int_from_string_wrapper(inner)
        if field == 7 and wire == _WIRE_LEN:  # Primitive
            inner, pos = _read_len_payload(buf, pos)
            return _int_from_primitive(inner)
        pos = _skip_field(buf, pos, wire)
    return None


def _int_from_string_wrapper(buf: bytes) -> int | None:
    pos = 0
    while pos < len(buf):
        tag, pos = _read_varint(buf, pos)
        field, wire = tag >> 3, tag & 7
        if field == 1 and wire == _WIRE_LEN:
            raw, pos = _read_len_payload(buf, pos)
            text = raw.decode("utf-8", errors="replace")
            try:
                return int(text, 0)
            except ValueError:
                return None
        pos = _skip_field(buf, pos, wire)
    return None


def _int_from_primitive(buf: bytes) -> int | None:
    """
    Decode aapt.pb.Primitive integer fields.

    Current AOSP Resources.proto: int_decimal_value = 6, int_hexadecimal_value = 7.
    Older schemas (and some third-party docs) used 4 / 5 — accept both.
    """
    pos = 0
    while pos < len(buf):
        tag, pos = _read_varint(buf, pos)
        field, wire = tag >> 3, tag & 7
        if wire == _WIRE_VARINT and field in (4, 5, 6, 7):
            raw, pos = _read_varint(buf, pos)
            if field in (4, 6):  # int_decimal (legacy 4 or current 6)
                return _as_signed_int32(raw)
            # hexadecimal / unsigned
            return int(raw & 0xFFFFFFFF)
        pos = _skip_field(buf, pos, wire)
    return None


# --- binary AXML (APK-style; rare inside modern AABs) ------------------


def _version_code_from_axml(data: bytes) -> int:
    """Scan binary AndroidManifest for android:versionCode (INT_DEC/HEX)."""
    try:
        return _version_code_via_chunks(data)
    except ValueError:
        return _version_code_via_raw_scan(data)


def _version_code_via_chunks(data: bytes) -> int:
    offset = 0
    while offset + 8 <= len(data):
        chunk_type, header_size, chunk_size = struct.unpack_from("<HHI", data, offset)
        if chunk_size < 8 or offset + chunk_size > len(data):
            break
        if chunk_type == 0x0102 and header_size >= 16:
            node_base = offset
            attr_ext = node_base + 16
            if attr_ext + 20 > offset + chunk_size:
                offset += chunk_size
                continue
            (
                _ns,
                _name,
                attribute_start,
                attribute_size,
                attribute_count,
                _id_index,
                _class_index,
                _style_index,
            ) = struct.unpack_from("<IIHHHHHH", data, attr_ext)
            if attribute_size < 20:
                offset += chunk_size
                continue
            attrs_base = attr_ext + attribute_start
            for i in range(attribute_count):
                attr_off = attrs_base + i * attribute_size
                if attr_off + 20 > offset + chunk_size:
                    break
                (
                    _attr_ns,
                    attr_name,
                    _raw,
                    _value_size,
                    _res0,
                    value_type,
                    value_data,
                ) = struct.unpack_from("<IIIHBBI", data, attr_off)
                if attr_name == _VERSION_CODE_ATTR and value_type in (
                    _TYPE_INT_DEC,
                    _TYPE_INT_HEX,
                ):
                    return int(value_data)
        offset += chunk_size
    raise ValueError("versionCode not found in structured AXML walk")


def _version_code_via_raw_scan(data: bytes) -> int:
    needle = struct.pack("<I", _VERSION_CODE_ATTR)
    start = 0
    while True:
        idx = data.find(needle, start)
        if idx < 0:
            break
        typed_off = idx + 8
        if typed_off + 8 <= len(data):
            _size, _res0, value_type, value_data = struct.unpack_from(
                "<HBBI", data, typed_off
            )
            if value_type in (_TYPE_INT_DEC, _TYPE_INT_HEX):
                return int(value_data)
        start = idx + 4
    raise ValueError("versionCode not found in AndroidManifest.xml")


def highest_version_code_from_tracks(tracks_payload: dict[str, Any]) -> int:
    """Return the max versionCode across all tracks in an edits.tracks.list response."""
    highest = 0
    for track in tracks_payload.get("tracks") or []:
        for release in track.get("releases") or []:
            for code in release.get("versionCodes") or []:
                try:
                    highest = max(highest, int(code))
                except (TypeError, ValueError):
                    continue
    return highest


def _build_service(json_path: Path):
    try:
        from google.oauth2 import service_account
        from googleapiclient.discovery import build
    except ImportError as exc:
        raise SystemExit(
            "Missing Python deps for Play upload. Install with:\n"
            "  pip install -r ezkey_mobile/scripts/requirements-play-upload.txt\n"
            f"({exc})"
        ) from exc

    scopes = ["https://www.googleapis.com/auth/androidpublisher"]
    credentials = service_account.Credentials.from_service_account_file(
        str(json_path), scopes=scopes
    )
    return build("androidpublisher", "v3", credentials=credentials, cache_discovery=False)


def _run_upload(
    *,
    aab_path: Path,
    json_path: Path,
    status: str,
    dry_run: bool,
) -> int:
    from googleapiclient.http import MediaFileUpload

    version_code = read_version_code_from_aab(aab_path)
    print(f"AAB versionCode={version_code} package={PACKAGE_NAME}")

    service = _build_service(json_path)
    edit = service.edits().insert(body={}, packageName=PACKAGE_NAME).execute()
    edit_id = edit["id"]
    print(f"Opened edit id={edit_id}")

    try:
        tracks_payload = (
            service.edits()
            .tracks()
            .list(packageName=PACKAGE_NAME, editId=edit_id)
            .execute()
        )
        print("Tracks / releases (versionCodes):")
        for track in tracks_payload.get("tracks") or []:
            track_name = track.get("track", "?")
            releases = track.get("releases") or []
            if not releases:
                print(f"  {track_name}: (no releases)")
                continue
            for release in releases:
                codes = release.get("versionCodes") or []
                rel_status = release.get("status", "?")
                print(f"  {track_name}: status={rel_status} versionCodes={codes}")

        highest = highest_version_code_from_tracks(tracks_payload)
        print(f"Highest versionCode on any track: {highest}")
        if version_code <= highest:
            print(
                f"Refusing upload: AAB versionCode {version_code} is not strictly "
                f"greater than highest on Play ({highest}).",
                file=sys.stderr,
            )
            return 1

        if dry_run:
            print(
                "Dry-run: skipping bundles.upload / tracks.update / commit; "
                "deleting edit."
            )
            return 0

        print(f"Uploading AAB to edit (track={INTERNAL_TRACK}, status={status})…")
        media = MediaFileUpload(
            str(aab_path),
            mimetype="application/octet-stream",
            resumable=True,
        )
        bundle = (
            service.edits()
            .bundles()
            .upload(packageName=PACKAGE_NAME, editId=edit_id, media_body=media)
            .execute()
        )
        uploaded_vc = int(bundle.get("versionCode", version_code))
        print(f"Uploaded bundle versionCode={uploaded_vc}")

        track_body = {
            "track": INTERNAL_TRACK,
            "releases": [
                {
                    "versionCodes": [str(uploaded_vc)],
                    "status": status,
                }
            ],
        }
        service.edits().tracks().update(
            packageName=PACKAGE_NAME,
            editId=edit_id,
            track=INTERNAL_TRACK,
            body=track_body,
        ).execute()
        print(f"Updated track '{INTERNAL_TRACK}' status={status}")

        service.edits().commit(packageName=PACKAGE_NAME, editId=edit_id).execute()
        print("Committed edit.")
        edit_id = None  # committed; do not delete
        return 0
    finally:
        if edit_id is not None:
            try:
                service.edits().delete(
                    packageName=PACKAGE_NAME, editId=edit_id
                ).execute()
                print(f"Deleted edit id={edit_id}")
            except Exception as exc:  # noqa: BLE001 — best-effort cleanup
                print(f"Warning: failed to delete edit {edit_id}: {exc}", file=sys.stderr)


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(
        description="Upload a signed AAB to Play internal track (draft by default)."
    )
    parser.add_argument("aab", type=Path, nargs="?", help="Path to signed .aab")
    parser.add_argument(
        "--json",
        type=Path,
        required=False,
        help="Service account JSON path (from EZKEY_PLAY_SERVICE_ACCOUNT_JSON)",
    )
    parser.add_argument(
        "--status",
        choices=("draft", "completed"),
        default="draft",
        help="Release status on the internal track (default: draft)",
    )
    parser.add_argument(
        "--dry-run",
        action="store_true",
        help="Auth + list tracks/versionCodes, then delete edit without committing",
    )
    parser.add_argument(
        "--print-version-code",
        action="store_true",
        help="Only print versionCode from the AAB and exit (no API calls)",
    )
    parser.add_argument(
        "--self-test",
        action="store_true",
        help="Run offline unit checks and exit",
    )
    args = parser.parse_args(argv)

    if args.self_test:
        return _self_test()

    if args.aab is None:
        parser.error("aab path is required unless --self-test")

    aab_path = args.aab.expanduser().resolve()
    if not aab_path.is_file():
        print(f"AAB not found: {aab_path}", file=sys.stderr)
        return 1
    if aab_path.suffix.lower() != ".aab":
        print(f"Expected a .aab file, got: {aab_path}", file=sys.stderr)
        return 1

    if args.print_version_code:
        print(read_version_code_from_aab(aab_path))
        return 0

    if args.json is None:
        print("--json is required for upload / dry-run", file=sys.stderr)
        return 1
    json_path = args.json.expanduser().resolve()
    if not json_path.is_file():
        print(
            f"Service account JSON not found: {json_path}\n"
            "Set EZKEY_PLAY_SERVICE_ACCOUNT_JSON to an existing key file "
            "(outside the repo).",
            file=sys.stderr,
        )
        return 1

    return _run_upload(
        aab_path=aab_path,
        json_path=json_path,
        status=args.status,
        dry_run=args.dry_run,
    )


def _fixture_proto_manifest_path() -> Path:
    return Path(__file__).resolve().parent.parent / "fixtures" / (
        "aab-proto-manifest-vc42.bin"
    )


def _self_test() -> int:
    import tempfile

    failures = 0

    def check(label: str, cond: bool) -> None:
        nonlocal failures
        if cond:
            print(f"  OK  {label}")
        else:
            print(f"  FAIL {label}", file=sys.stderr)
            failures += 1

    check(
        "highest_version_code empty tracks → 0",
        highest_version_code_from_tracks({}) == 0,
    )
    check(
        "highest_version_code across tracks",
        highest_version_code_from_tracks(
            {
                "tracks": [
                    {
                        "track": "internal",
                        "releases": [
                            {"versionCodes": ["2", "3"], "status": "completed"}
                        ],
                    },
                    {
                        "track": "production",
                        "releases": [{"versionCodes": ["1"], "status": "completed"}],
                    },
                ]
            }
        )
        == 3,
    )

    # Real AAB format: checked-in aapt.pb.XmlNode fixture (not Ezkey signed AAB).
    fixture = _fixture_proto_manifest_path()
    check("proto fixture file exists", fixture.is_file())
    if fixture.is_file():
        proto_bytes = fixture.read_bytes()
        check("proto fixture starts with 0x0a (not AXML magic)", proto_bytes[:1] == b"\n")
        check("proto fixture is not AXML magic", not proto_bytes.startswith(_AXML_MAGIC))
        try:
            vc = version_code_from_manifest_bytes(proto_bytes)
            check(f"proto fixture versionCode == 42 (got {vc})", vc == 42)
        except ValueError as exc:
            check(f"proto fixture decode ({exc})", False)

        with tempfile.TemporaryDirectory() as tmp:
            aab = Path(tmp) / "sample.aab"
            with zipfile.ZipFile(aab, "w") as zf:
                zf.writestr("base/manifest/AndroidManifest.xml", proto_bytes)
            try:
                vc_aab = read_version_code_from_aab(aab)
                check(f"zip-wrapped proto AAB versionCode == 42 (got {vc_aab})", vc_aab == 42)
            except ValueError as exc:
                check(f"zip-wrapped proto AAB decode ({exc})", False)

    # Legacy binary-AXML path (APK-style); synthetic start-element chunk.
    attr = struct.pack(
        "<IIIHBBI",
        0xFFFFFFFF,
        _VERSION_CODE_ATTR,
        0xFFFFFFFF,
        8,
        0,
        _TYPE_INT_DEC,
        4,
    )
    attr_ext = struct.pack(
        "<IIHHHHHH",
        0xFFFFFFFF,
        0xFFFFFFFF,
        20,
        20,
        1,
        0,
        0,
        0,
    )
    chunk_size = 16 + 20 + 20
    node_prefix = struct.pack("<HHIIi", 0x0102, 16, chunk_size, 1, -1)
    start_el = node_prefix + attr_ext + attr
    try:
        check("AXML chunk-walk versionCode=4", _version_code_via_chunks(start_el) == 4)
    except ValueError as exc:
        check(f"AXML chunk-walk ({exc})", False)
    # Dispatcher: AXML magic → AXML path (raw scan finds attr after 8-byte hdr).
    file_hdr = struct.pack("<HHI", 0x0003, 8, 8 + len(start_el))
    try:
        check(
            "AXML magic dispatch versionCode=4",
            version_code_from_manifest_bytes(file_hdr + start_el) == 4,
        )
    except ValueError as exc:
        check(f"AXML magic dispatch ({exc})", False)

    if failures:
        print(f"self-test: {failures} failure(s)", file=sys.stderr)
        return 1
    print("self-test: all checks passed")
    return 0


if __name__ == "__main__":
    sys.exit(main())
