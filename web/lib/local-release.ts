import type { ReleaseInfo } from "@/lib/validation/releases";

export const localRelease: ReleaseInfo = {
  "version": "1.19.1",
  "tagName": "v1.19.1",
  "versionCode": 30,
  "apkName": "NowTuneUp-v1.19.1-release.apk",
  "apkSize": 13203350,
  "downloadUrl": "https://github.com/kzabCde/NowTuneUp-Smart-OBD-Vehicle-Monitor/releases/download/v1.19.1/NowTuneUp-v1.19.1-release.apk",
  "sha256": "9ebeb8d486e91599aa10229b14c559e3eb608edf966608ca5fe93c23542446d9",
  "minimumAndroid": "Android 8.0",
  "publishedAt": "2026-11-12T09:32:05Z",
  "releaseNotes": "# NowTuneUp 1.19.1 — Professional Automotive Dashboard\n\nAndroid build code: **30**. Requires Android 8.0 or later.\n\n- Five primary destinations: Home, Performance, History, Garage and Settings,\n  with adaptive navigation, light/dark themes and reduced-motion support.\n- Two primary gauge presentations: DialGauge and DigitalGauge. Older saved\n  gauge configurations retain IDs, geometry, sources, ranges and compatibility data.\n- Dashboard create, rename, duplicate, reorder, preferred landing, reset and\n  confirmed deletion. Deleting the final dashboard leaves an empty Home with a\n  Create Dashboard action, including after restart.\n- Widget add/remove, reorder, resize, style/source/range editing and undo/redo.\n  Daily, Engine and Performance templates are explicit editable copies.\n- Dedicated performance history, clear legacy/unavailable states and locally\n  generated PNG reports shared through Android Sharesheet.\n- Garage groups vehicle profiles, adapter connection, read-only diagnostics,\n  live telemetry and observed connection health. Unknown health is not shown as 100%.\n- Compact gauge labels, fullscreen status placement and system-bar contrast\n  protect readability across supported layouts.\n\nExisting OBD transports, session handling, PID parsing, measurement calculations,\nanalysis and stored run formats are retained. Dashboard deletion does not delete\nvehicles, trips, telemetry or performance history. No account or backend is required.\nDistance and acceleration remain estimates from OBD speed, not GPS-grade timing.\n\n## Verification\n\n132 JVM tests passed in each of debug and release configurations. Debug and\nrelease lint passed with zero errors and 42 warnings each. All 21 device test\ncases passed across a full run and one unchanged focused rerun after a Compose\nidle timeout on the software emulator. The final dashboard's deletion was verified\nthrough the real UI, database inspection and process restart.\n\nPhysical USB/Bluetooth compatibility, extended monitoring, startup/frame-rate\nprofiling and OEM lifecycle/cutout behavior still require real-device validation.\nSee [the audit, migration guide and screenshots](../development/professional-redesign.md).\n\n\n**Full Changelog**: https://github.com/kzabCde/NowTuneUp-Smart-OBD-Vehicle-Monitor/compare/v1.19.0...v1.19.1"
};

export function getLocalRelease(version?: string) {
  if (version && version !== localRelease.version) return null;
  return localRelease;
}
