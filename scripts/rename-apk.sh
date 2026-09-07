#!/usr/bin/env bash
set -euo pipefail

version="${1:?usage: rename-apk.sh VERSION}"
[[ "$version" =~ ^[0-9]+\.[0-9]+\.[0-9]+([.-][0-9A-Za-z.-]+)?$ ]] || { echo "Invalid version" >&2; exit 2; }

dir="android-app/app/build/outputs/apk/release"
target="$dir/NowTuneUp-v${version}-release.apk"

# Gradle may already emit the canonical release filename. In that case,
# renaming is unnecessary and the step should remain idempotent.
if [[ -f "$target" ]]; then
  echo "Release APK already named correctly: $target"
  exit 0
fi

source=""
for candidate in \
  "$dir/app-release.apk" \
  "$dir/NowTuneUp-v1.0.0-release.apk"
do
  if [[ -f "$candidate" ]]; then
    source="$candidate"
    break
  fi
done

# Fallback for versioned release APK names emitted by Gradle.
if [[ -z "$source" ]]; then
  mapfile -t candidates < <(find "$dir" -maxdepth 1 -type f -name '*-release.apk' | sort)
  if [[ ${#candidates[@]} -eq 1 ]]; then
    source="${candidates[0]}"
  elif [[ ${#candidates[@]} -gt 1 ]]; then
    echo "Multiple release APK candidates found:" >&2
    printf '  %s\n' "${candidates[@]}" >&2
    exit 1
  fi
fi

[[ -n "$source" && -f "$source" ]] || {
  echo "Release APK not found in $dir" >&2
  find "$dir" -maxdepth 1 -type f -printf '  %f\n' 2>/dev/null || true
  exit 1
}

mv "$source" "$target"
echo "Renamed release APK: $source -> $target"
