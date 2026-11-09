import type { ReleaseInfo } from "@/lib/validation/releases";

export const localRelease: ReleaseInfo = {
  "version": "1.18.0",
  "tagName": "v1.18.0",
  "versionCode": 28,
  "apkName": "NowTuneUp-v1.18.0-release.apk",
  "apkSize": 13072278,
  "downloadUrl": "https://github.com/kzabCde/NowTuneUp-Smart-OBD-Vehicle-Monitor/releases/download/v1.18.0/NowTuneUp-v1.18.0-release.apk",
  "sha256": "a059ad4354f2eab4f4b0998c3bdc3abaaa2448c5928962abd1c5206dfa3a233e",
  "minimumAndroid": "Android 8.0",
  "publishedAt": "2026-11-09T16:06:34Z",
  "releaseNotes": "# NowTuneUp 1.18.0 — Motorsport Performance Suite\n\nAndroid version code: **28**. Android 8.0+ remains supported.\n\n- Optional Daily Drive, Performance and Diagnostics HUD templates, independent\n  orientation layouts, configurable RPM shift stages and compact digital gauges.\n- Clear disconnected/stale/unsupported states, simulation labels, safe unit/source\n  editing and last-scan DTC availability.\n- Bounded raw telemetry recording with original source timestamps, separate signal\n  axes, measured-sample inspection, local history, confirmed deletion and CSV export.\n- Exact 0–60 mph target, searchable/grouped/sorted performance history, compatible\n  comparisons and split deltas. New runs retain available timestamped RPM/throttle;\n  older records remain accessible without invented measurements.\n- Source, quality and rollout context in results and local sharing. Existing timing\n  calculations, Room profiles, DataStore preferences and transports are retained.\n\nShift thresholds must be configured by the driver; no manufacturer redline is\nassumed. Distance and acceleration are estimates derived from OBD speed and are\nnot certified GPS timing measurements. Core monitoring remains local-first.\n\n## Validation and limitations\n\nThe development suite passed 116 JVM tests, lint, debug build and instrumentation\nAPK assembly using USB serial built from its official v3.9.0 source. Normal CI\nagainst the declared JitPack dependency is required before publication. Device\nUI tests did not complete because the unaccelerated emulator was unstable.\nPhysical USB/Bluetooth compatibility and extended monitoring remain unverified;\nsee `docs/development/motorsport-suite.md` for the complete verification record.\n\nThe production APK must use the existing release signing identity so upgrades\npreserve installed application data. Never publish an unsigned or debug APK as\nthis release. Website size and SHA-256 must come from the verified signed APK.\n\n\n## What's Changed\n* Web: update production site for v1.17.0 by @kzabCde in https://github.com/kzabCde/NowTuneUp-Smart-OBD-Vehicle-Monitor/pull/46\n\n\n**Full Changelog**: https://github.com/kzabCde/NowTuneUp-Smart-OBD-Vehicle-Monitor/compare/v1.17.0...v1.18.0"
};

export function getLocalRelease(version?: string) {
  if (version && version !== localRelease.version) return null;
  return localRelease;
}
