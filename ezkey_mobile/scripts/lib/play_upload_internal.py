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

from __future__ import annotations

import argparse
import struct
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


def read_version_code_from_aab(aab_path: Path) -> int:
    """Extract android:versionCode from an AAB's base AndroidManifest.xml."""
    with zipfile.ZipFile(aab_path) as zf:
        try:
            data = zf.read("base/manifest/AndroidManifest.xml")
        except KeyError as exc:
            raise ValueError(
                f"AAB missing base/manifest/AndroidManifest.xml: {aab_path}"
            ) from exc
    return _version_code_from_axml(data)


def _version_code_from_axml(data: bytes) -> int:
    """Scan binary AndroidManifest for android:versionCode (INT_DEC/HEX)."""
    if len(data) < 8:
        raise ValueError("AndroidManifest.xml too small")
    # Prefer structured walk; fall back to raw scan of attr resource id.
    try:
        return _version_code_via_chunks(data)
    except ValueError:
        return _version_code_via_raw_scan(data)


def _version_code_via_chunks(data: bytes) -> int:
    # ResChunk header: type(u16) headerSize(u16) size(u32)
    offset = 0
    while offset + 8 <= len(data):
        chunk_type, header_size, chunk_size = struct.unpack_from("<HHI", data, offset)
        if chunk_size < 8 or offset + chunk_size > len(data):
            break
        # RES_XML_START_ELEMENT_TYPE = 0x0102
        if chunk_type == 0x0102 and header_size >= 16:
            # After ResXMLTree_node (lineNumber, comment) comes ResXMLTree_attrExt
            # node: type, headerSize, size, lineNumber, comment = 16 bytes
            # attrExt: ns, name, attributeStart, attributeSize, attributeCount, ...
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
            # attributeStart is relative to the start of ResXMLTree_attrExt.
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
                # When compiled with resource ids, attr_name holds the resid.
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
        # Attribute name at idx; typed value follows: rawValue(4) + size(2)+res0(1)+type(1)+data(4)
        # Full attr: ns(4) name(4) raw(4) size(2) res0(1) type(1) data(4)
        # If needle is the name field, typed value starts at idx+8.
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


def _self_test() -> int:
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

    chunk_ok = False
    try:
        chunk_ok = _version_code_via_chunks(start_el) == 4
    except ValueError:
        chunk_ok = False
    check("chunk-walk versionCode=4 from synthetic start-element", chunk_ok)

    raw_ok = False
    try:
        raw_ok = _version_code_via_raw_scan(start_el) == 4
    except ValueError:
        raw_ok = False
    check("raw-scan versionCode=4 from synthetic attr", raw_ok)

    if failures:
        print(f"self-test: {failures} failure(s)", file=sys.stderr)
        return 1
    print("self-test: all checks passed")
    return 0


if __name__ == "__main__":
    sys.exit(main())
