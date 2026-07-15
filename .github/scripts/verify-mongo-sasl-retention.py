#!/usr/bin/env python3
"""Fail CI when release APK/DEX is missing Mongo SCRAM classes required at runtime."""

from __future__ import annotations

import argparse
import struct
import sys
import zipfile
from pathlib import Path


REQUIRED_CLASS_SUBSTRINGS = (
    "Ljavax/security/sasl/SaslClient;",
    "Ljavax/security/sasl/SaslException;",
    "Lcom/mongodb/internal/connection/SaslAuthenticator;",
    "Lcom/mongodb/internal/connection/SaslAuthenticator$SaslClientImpl;",
    "Lcom/mongodb/internal/connection/DefaultAuthenticator;",
    "Lcom/mongodb/internal/connection/ScramShaAuthenticator;",
    "Lcom/mongodb/internal/connection/ScramShaAuthenticator$ScramShaSaslClient;",
)


def collect_candidate_paths(root: Path) -> list[Path]:
    patterns = (
        "**/app-production-universal-release.apk",
        "**/app-production-release.apk",
        "**/app-production-*-release.apk",
        "**/minifyProductionReleaseWithR8/**/*.jar",
        "**/minifyProductionReleaseWithR8/**/*.dex",
        "**/intermediates/dex/productionRelease/**/*.dex",
        "**/outputs/mapping/productionRelease/mapping.txt",
    )
    found: list[Path] = []
    for pattern in patterns:
        found.extend(path for path in root.glob(pattern) if path.is_file())
    # Prefer unique, stable order.
    return sorted({path.resolve() for path in found}, key=lambda path: str(path))


def read_blob(path: Path) -> bytes:
    if path.suffix.lower() == ".apk":
        with zipfile.ZipFile(path) as archive:
            parts: list[bytes] = []
            for name in archive.namelist():
                lower = name.lower()
                if lower.endswith(".dex") or lower.endswith(".jar"):
                    parts.append(archive.read(name))
            if not parts:
                raise SystemExit(f"No dex/jar entries found in APK: {path}")
            return b"".join(parts)
    return path.read_bytes()


def contains_all(blob: bytes, needles: tuple[str, ...]) -> tuple[bool, list[str]]:
    missing: list[str] = []
    for needle in needles:
        # mapping.txt uses java names; dex uses descriptors. Check both shapes.
        java_name = (
            needle[1:-1].replace("/", ".")
            if needle.startswith("L") and needle.endswith(";")
            else needle
        )
        descriptor = needle.encode("utf-8")
        java_bytes = java_name.encode("utf-8")
        if descriptor not in blob and java_bytes not in blob:
            missing.append(java_name)
    return (not missing, missing)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--root",
        default="android",
        help="Android project root to search for release outputs",
    )
    args = parser.parse_args()
    root = Path(args.root)
    if not root.exists():
        print(f"Root not found: {root}", file=sys.stderr)
        return 2

    candidates = collect_candidate_paths(root)
    if not candidates:
        print(
            "No productionRelease APK/DEX/mapping artifacts found to verify Mongo SASL classes.",
            file=sys.stderr,
        )
        return 2

    print("Checking Mongo SASL class retention in:")
    for path in candidates:
        print(f"  - {path}")

    # Require at least one artifact to contain every required class/string.
    for path in candidates:
        try:
            blob = read_blob(path)
        except Exception as error:  # noqa: BLE001 - CI diagnostic path
            print(f"Skip unreadable artifact {path}: {error}")
            continue
        ok, missing = contains_all(blob, REQUIRED_CLASS_SUBSTRINGS)
        if ok:
            print(f"OK: {path} retains required Mongo SASL classes")
            return 0
        print(f"Missing in {path}: {', '.join(missing)}")

    print(
        "ERROR: required Mongo SCRAM/SASL classes are absent from all inspected "
        "release artifacts. R8 likely stripped SaslAuthenticator$SaslClientImpl.",
        file=sys.stderr,
    )
    return 1


if __name__ == "__main__":
    raise SystemExit(main())
