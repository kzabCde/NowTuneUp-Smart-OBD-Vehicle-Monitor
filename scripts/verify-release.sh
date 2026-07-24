#!/usr/bin/env bash
set -euo pipefail
apk="${1:?usage: verify-release.sh APK}"
[[ "$apk" =~ NowTuneUp-v[0-9]+\.[0-9]+\.[0-9]+.*-release\.apk$ ]] || { echo "Unexpected APK filename" >&2; exit 2; }
[[ -s "$apk" && -s "$apk.sha256" ]] || { echo "APK/checksum missing or empty" >&2; exit 1; }
(cd "$(dirname "$apk")" && sha256sum -c "$(basename "$apk").sha256")
