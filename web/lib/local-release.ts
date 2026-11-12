import type { ReleaseInfo } from "@/lib/validation/releases";

export const localRelease: ReleaseInfo = {
  version: "1.18.0",
  tagName: "v1.18.0",
  versionCode: 28,
  apkName: "NowTuneUp-v1.18.0-release.apk",
  apkSize: 13072278,
  downloadUrl: "https://github.com/kzabCde/NowTuneUp-Smart-OBD-Vehicle-Monitor/releases/download/v1.18.0/NowTuneUp-v1.18.0-release.apk",
  sha256: "a059ad4354f2eab4f4b0998c3bdc3abaaa2448c5928962abd1c5206dfa3a233e",
  minimumAndroid: "Android 8.0",
  publishedAt: "2026-11-09T16:06:34.000Z",
  releaseNotes: "NowTuneUp 1.18.0 introduces the Motorsport Performance Suite: optional Daily Drive, Performance and Diagnostics HUD templates; configurable multi-stage RPM shift lights; clearer telemetry states; timestamped raw OBD recording with local history and CSV export; exact 0–60 mph Time Slip targets; and searchable performance comparisons with split deltas. Acceleration and distance remain OBD-speed estimates, not certified GPS measurements.",
};

export function getLocalRelease(version?: string) {
  if (version && version !== localRelease.version) return null;
  return localRelease;
}
