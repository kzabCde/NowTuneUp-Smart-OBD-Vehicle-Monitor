import type { ReleaseInfo } from "@/lib/validation/releases";

export const localRelease: ReleaseInfo = {
  version: "1.7.1",
  tagName: "v1.7.1",
  versionCode: 15,
  apkName: "NowTuneUp.apk",
  apkSize: 12887746,
  downloadUrl: "/downloads/NowTuneUp.apk",
  sha256: "1c20e84de7938c52a582a6db883913ad5852c3958d2bb70cc85fdaef279112fa",
  minimumAndroid: "Android 8.0",
  publishedAt: "2026-08-04T04:40:10.000Z",
  releaseNotes: "NowTuneUp 1.7.1 adds configurable minimum and maximum value ranges for every dashboard widget, including analog gauges, digital rings and progress bars. Speed defaults to 0–200 km/h and engine RPM to 0–7,000 rpm, while users can set custom ranges per saved profile or return to automatic OBD-derived scaling. Time Slip, Bluetooth, USB, diagnostics, dashboard profiles and sharing remain supported.",
};

export function getLocalRelease(version?: string) {
  if (version && version !== localRelease.version) return null;
  return localRelease;
}
