#!/usr/bin/env bash
set -euo pipefail
version="${1:?usage: rename-apk.sh VERSION}"
[[ "$version" =~ ^[0-9]+\.[0-9]+\.[0-9]+([.-][0-9A-Za-z.-]+)?$ ]] || { echo "Invalid version" >&2; exit 2; }
dir="android-app/app/build/outputs/apk/release"
source="$dir/NowTuneUp-v1.0.0-release.apk"
[[ -f "$source" ]] || source="$dir/app-release.apk"
[[ -f "$source" ]] || { echo "Release APK not found" >&2; exit 1; }
target="$dir/NowTuneUp-v${version}-release.apk"
[[ "$source" == "$target" ]] || mv "$source" "$target"
