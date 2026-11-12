#!/usr/bin/env python3
"""Verify the signed production artifact before changing website download metadata."""
import argparse
import hashlib
import json
import re
import shutil
import subprocess
from datetime import datetime
from pathlib import Path

REPOSITORY = "kzabCde/NowTuneUp-Smart-OBD-Vehicle-Monitor"


def metadata(release, version, version_code, size, digest):
    name = f"NowTuneUp-v{version}-release.apk"
    url = f"https://github.com/{REPOSITORY}/releases/download/v{version}/{name}"
    if release.get("tag_name") != f"v{version}" or release.get("draft") is not False or release.get("prerelease") is not False:
        raise ValueError("Only the matching published stable release can update the website")
    published = release.get("published_at")
    if not published or datetime.fromisoformat(published.replace("Z", "+00:00")).tzinfo is None:
        raise ValueError("A real publication timestamp is required")
    assets = [a for a in release.get("assets", []) if a.get("name") == name]
    if len(assets) != 1 or assets[0].get("size") != size or assets[0].get("browser_download_url") != url:
        raise ValueError("Published artifact name, size or trusted URL does not match")
    return dict(version=version, tagName=f"v{version}", versionCode=version_code,
                apkName=name, apkSize=size, downloadUrl=url, sha256=digest,
                minimumAndroid="Android 8.0", publishedAt=published,
                releaseNotes=release.get("body") or f"NowTuneUp {version}")


def run(*args):
    return subprocess.check_output([str(a) for a in args], text=True, stderr=subprocess.STDOUT)


def certificate(apksigner, apk):
    output = run(apksigner, "verify", "--print-certs", apk)
    found = re.findall(r"Signer #\d+ certificate SHA-256 digest: ([a-fA-F0-9]+)", output)
    if not found:
        raise ValueError("No verified APK signing certificate was found")
    return {value.lower() for value in found}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--version", required=True)
    parser.add_argument("--apk", type=Path, required=True)
    parser.add_argument("--sdk-root", type=Path, required=True)
    parser.add_argument("--release-json", type=Path)
    parser.add_argument("--verify-only", action="store_true")
    args = parser.parse_args()
    if not re.fullmatch(r"\d+\.\d+\.\d+", args.version):
        raise ValueError("A stable semantic version is required")
    root = Path(__file__).resolve().parent.parent
    apk = args.apk.resolve()
    if apk.name != f"NowTuneUp-v{args.version}-release.apk":
        raise ValueError("Unexpected artifact filename")
    with apk.open("rb") as stream:
        digest = hashlib.file_digest(stream, "sha256").hexdigest()
    checksum = Path(str(apk) + ".sha256").read_text().strip().split()
    if len(checksum) != 2 or checksum[0].lower() != digest or checksum[1].lstrip("*") != apk.name:
        raise ValueError("Published checksum does not match the APK")
    tools = args.sdk_root / "build-tools" / "35.0.0"
    previous = root / "web/public/downloads/NowTuneUp.apk"
    if certificate(tools / "apksigner", apk) != certificate(tools / "apksigner", previous):
        raise ValueError("Signing identity differs from the website's existing production APK")
    badging = run(tools / "aapt", "dump", "badging", apk)
    package = re.search(r"package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'", badging)
    if not package or package[1] != "com.nowtuneup.app" or package[3] != args.version or "application-debuggable" in badging:
        raise ValueError("APK package, release version or debuggable flag is invalid")
    declared = (root / "android-app/app/build.gradle.kts").read_text()
    expected_code = int(re.search(r"versionCode = (\d+)", declared)[1])
    if int(package[2]) != expected_code or f'versionName = "{args.version}"' not in declared:
        raise ValueError("APK and source version metadata do not match")
    if args.verify_only:
        print(f"Verified signed release {args.version}, code {expected_code}, SHA-256 {digest}")
        return
    if not args.release_json:
        raise ValueError("--release-json is required to update the website")
    result = metadata(json.loads(args.release_json.read_text()), args.version, expected_code, apk.stat().st_size, digest)
    contents = 'import type { ReleaseInfo } from "@/lib/validation/releases";\n\n'
    contents += "export const localRelease: ReleaseInfo = " + json.dumps(result, ensure_ascii=False, indent=2) + ";\n\n"
    contents += 'export function getLocalRelease(version?: string) {\n  if (version && version !== localRelease.version) return null;\n  return localRelease;\n}\n'
    # Every verification above completes before either production file is changed.
    shutil.copyfile(apk, previous)
    (root / "web/lib/local-release.ts").write_text(contents)
    print(f"Website synchronized to verified published release {args.version}")


if __name__ == "__main__":
    main()
