import type { ReleaseInfo } from "@/lib/validation/releases";

export const localRelease: ReleaseInfo = {
  version: "1.7.2",
  tagName: "v1.7.2",
  versionCode: 16,
  apkName: "NowTuneUp.apk",
  apkSize: 12871362,
  downloadUrl: "/downloads/NowTuneUp.apk",
  sha256: "69dcd5df8f8c711c1f094a6ad2e6d38f73a6e052ff4d3934db666e855c8494e0",
  minimumAndroid: "Android 8.0",
  publishedAt: "2026-08-04T05:31:04.000Z",
  releaseNotes: "NowTuneUp 1.7.2 stabilizes Turbo Pressure with timestamp-aware MAP/BARO pairing, median outlier rejection, adaptive smoothing, zero deadband, stale-data handling and reconnect reset. Time Slip now has quick presets, separate setup/ready/running/result states, a distraction-reduced live screen, fixed-width timer, delayed-data status, landscape track mode and clearer OBD-only distance confidence labels.",
};

export function getLocalRelease(version?: string) {
  if (version && version !== localRelease.version) return null;
  return localRelease;
}
