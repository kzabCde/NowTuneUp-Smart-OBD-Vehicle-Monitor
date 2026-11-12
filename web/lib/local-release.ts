import type { ReleaseInfo } from "@/lib/validation/releases";

export const localRelease: ReleaseInfo = {
  "version": "1.19.2",
  "tagName": "v1.19.2",
  "versionCode": 31,
  "apkName": "NowTuneUp-v1.19.2-release.apk",
  "apkSize": 13203354,
  "downloadUrl": "https://github.com/kzabCde/NowTuneUp-Smart-OBD-Vehicle-Monitor/releases/download/v1.19.2/NowTuneUp-v1.19.2-release.apk",
  "sha256": "a2696b6b0405c369868d9a44afb31f665875906504d4bab323fa663584dd476b",
  "minimumAndroid": "Android 8.0",
  "publishedAt": "2026-11-12T10:45:14Z",
  "releaseNotes": "# NowTuneUp 1.19.2 — Dashboard & Gauge Hotfix\n\nAndroid version **1.19.2**, build code **31**, Android 8.0 or newer.\n\n## What's improved\n\n- Dashboard profiles now have a visible **ลบ** (Delete) action in the workspace list, rather than requiring a nested overflow menu. Deletion still asks for confirmation.\n- The app keeps the deletion dialog open if the storage operation fails and explains the failure rather than closing as though it succeeded.\n- Dashboard management remains accessible during Driving Mode so the user can see why controls are locked. Changes and deletion still require Driving Mode to be turned off safely while parked.\n- The analog dial reduces crowded tick marks, needle weight and scale chrome. Digital gauge typography is quieter, with aligned labels, values and units.\n- Existing two gauge types, OBD read-only monitoring, Time Slip measurements, workspace persistence, vehicle/trip/run history and local-first behavior are preserved.\n\n## Important limitations\n\n- A profile deleted on an older version may require review on a real device. Users should check Home → Manage dashboards and confirm deletion before assuming success.\n- This UI release does **not** change OBD timing accuracy, sensor precision, connection compatibility, or performance-test algorithms. Estimates remain estimates; no GPS-grade precision is claimed.\n\n## Release checks\n\nBuild from a clean, immutable `v1.19.2` source tag using the repository's **Publish Android release** workflow. Release is only valid if Android tests/lint, production signing, APK metadata verification and SHA-256 checks succeed. Website sync must verify the published APK and propose its own website PR.\n\nDo not overwrite the existing `v1.19.1` tag or signed APK. The app version and code increase together for upgrade compatibility.\n\n\n## What's Changed\n* Release NowTuneUp 1.19.1 and publish the signed APK download by @kzabCde in https://github.com/kzabCde/NowTuneUp-Smart-OBD-Vehicle-Monitor/pull/48\n* fix(android): make dashboard deletion discoverable and simplify gauge visuals by @kzabCde in https://github.com/kzabCde/NowTuneUp-Smart-OBD-Vehicle-Monitor/pull/49\n* release(android): prepare NowTuneUp 1.19.2 signed APK and verified website sync by @kzabCde in https://github.com/kzabCde/NowTuneUp-Smart-OBD-Vehicle-Monitor/pull/50\n\n\n**Full Changelog**: https://github.com/kzabCde/NowTuneUp-Smart-OBD-Vehicle-Monitor/compare/v1.19.1...v1.19.2"
};

export function getLocalRelease(version?: string) {
  if (version && version !== localRelease.version) return null;
  return localRelease;
}
