import type { ReleaseInfo } from "@/lib/validation/releases";

export const localRelease: ReleaseInfo = {
  version: "1.7.1",
  tagName: "v1.7.1",
  versionCode: 15,
  apkName: "NowTuneUp.apk",
  apkSize: 12871362,
  downloadUrl: "/downloads/NowTuneUp.apk",
  sha256: "927ffe62b8fd64ab68f5d986524d20e4288785053df5db938704b67e3561babd",
  minimumAndroid: "Android 8.0",
  publishedAt: "2026-08-03T16:46:42.000Z",
  releaseNotes: "NowTuneUp 1.7.1 adds configurable minimum and maximum value ranges for every dashboard widget, including analog gauges, digital rings and progress bars. Speed defaults to 0–200 km/h and engine RPM to 0–7,000 rpm, while users can set custom ranges per saved profile or return to automatic OBD-derived scaling. Time Slip, Bluetooth, USB, diagnostics, dashboard profiles and sharing remain supported.",
};

export function getLocalRelease(version?: string) {
  if (version && version !== localRelease.version) return null;
  return localRelease;
}
