#!/usr/bin/env python3
"""Materialize lumen-crash Maven coordinates from Project-Lumen GitHub Release assets."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import shutil
import urllib.error
import urllib.request
from pathlib import Path


DEFAULT_OWNER = "Chloemlla"
DEFAULT_REPO = "Project-Lumen"
DEFAULT_VERSION_FILE = Path("android/lumen-crash.version")
DEFAULT_LOCAL_MAVEN = Path("android/local-maven")


def read_version(path: Path) -> str:
    for line in path.read_text(encoding="utf-8").splitlines():
        text = line.strip()
        if text and not text.startswith("#"):
            return text
    raise SystemExit(f"No version found in {path}")


def auth_headers(accept: str = "*/*") -> dict[str, str]:
    headers = {
        "Accept": accept,
        "User-Agent": "clens-lumen-crash-bootstrap",
    }
    token = (
        os.environ.get("LUMEN_CRASH_READ_PACKAGES_TOKEN")
        or os.environ.get("GH_TOKEN")
        or os.environ.get("GITHUB_TOKEN")
        or ""
    ).strip()
    if token:
        headers["Authorization"] = f"Bearer {token}"
        headers["X-GitHub-Api-Version"] = "2022-11-28"
    return headers


def http_get(url: str, accept: str = "*/*") -> bytes:
    request = urllib.request.Request(url, headers=auth_headers(accept))
    try:
        with urllib.request.urlopen(request, timeout=90) as response:
            return response.read()
    except urllib.error.HTTPError as error:
        body = error.read().decode("utf-8", errors="replace")
        raise SystemExit(f"HTTP {error.code} for {url}: {body}") from error


def sha256_bytes(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def materialize(version: str, owner: str, repo: str, local_maven: Path) -> None:
    tag = f"lumen-crash-v{version}"
    base = f"https://github.com/{owner}/{repo}/releases/download/{tag}"

    required = [
        f"lumen-crash-{version}.aar",
        f"lumen-crash-{version}.pom",
    ]
    optional = [
        f"lumen-crash-{version}.module",
        f"lumen-crash-{version}-sources.jar",
        "checksums.txt",
        "sdk-manifest.json",
    ]

    target_dir = local_maven / "com" / "chloemlla" / "lumen" / "lumen-crash" / version
    if target_dir.exists():
        shutil.rmtree(target_dir)
    target_dir.mkdir(parents=True, exist_ok=True)

    downloaded: dict[str, bytes] = {}
    for name in required:
        data = http_get(f"{base}/{name}")
        downloaded[name] = data
        (target_dir / name).write_bytes(data)

    for name in optional:
        try:
            data = http_get(f"{base}/{name}")
        except SystemExit as error:
            # Optional assets may be absent; only fail hard for required ones.
            if name in required:
                raise
            print(f"Optional asset skipped: {name} ({error})")
            continue
        downloaded[name] = data
        (target_dir / name).write_bytes(data)

    aar_name = required[0]
    if not downloaded[aar_name].startswith(b"PK"):
        raise SystemExit(f"{aar_name} does not look like a ZIP/AAR (missing PK header)")

    if "checksums.txt" in downloaded:
        expected: dict[str, str] = {}
        for line in downloaded["checksums.txt"].decode("utf-8").splitlines():
            parts = line.split()
            if len(parts) >= 2:
                expected[parts[-1]] = parts[0].lower()
        for name in required:
            digest = sha256_bytes(downloaded[name])
            if expected.get(name) and expected[name] != digest:
                raise SystemExit(
                    f"Checksum mismatch for {name}: expected {expected[name]}, got {digest}"
                )

    if "sdk-manifest.json" in downloaded:
        manifest = json.loads(downloaded["sdk-manifest.json"].decode("utf-8"))
        if manifest.get("version") != version:
            raise SystemExit(
                f"sdk-manifest version mismatch: expected {version}, got {manifest.get('version')}"
            )
        coords = manifest.get("maven", {}).get("coordinates")
        expected_coords = f"com.chloemlla.lumen:lumen-crash:{version}"
        if coords and coords != expected_coords:
            raise SystemExit(f"Unexpected maven coordinates in manifest: {coords}")

    print(f"Materialized com.chloemlla.lumen:lumen-crash:{version} into {target_dir}")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--version-file", type=Path, default=DEFAULT_VERSION_FILE)
    parser.add_argument("--local-maven", type=Path, default=DEFAULT_LOCAL_MAVEN)
    parser.add_argument("--owner", default=DEFAULT_OWNER)
    parser.add_argument("--repo", default=DEFAULT_REPO)
    parser.add_argument("--version", default="")
    args = parser.parse_args()

    version = args.version.strip() or read_version(args.version_file)
    materialize(version, args.owner, args.repo, args.local_maven)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
