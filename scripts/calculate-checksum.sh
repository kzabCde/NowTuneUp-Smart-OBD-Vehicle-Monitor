#!/usr/bin/env bash
set -euo pipefail
file="${1:?usage: calculate-checksum.sh APK}"
[[ -f "$file" ]] || { echo "APK not found: $file" >&2; exit 1; }
(cd "$(dirname "$file")" && sha256sum "$(basename "$file")" > "$(basename "$file").sha256")
