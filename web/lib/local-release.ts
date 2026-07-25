import type { ReleaseInfo } from "@/lib/validation/releases";

export const localRelease: ReleaseInfo = {
  version: "1.2.1",
  tagName: "v1.2.1",
  versionCode: 5,
  apkName: "NowTuneUp.apk",
  apkSize: 12625394,
  downloadUrl: "/downloads/NowTuneUp.apk",
  sha256: "d85f405a27ed793d4a73815928382d08d368d35e9f922622c77397b09dc3b72f",
  minimumAndroid: "Android 8.0",
  publishedAt: "2026-07-25T10:09:13.000Z",
  releaseNotes: "Adds a segmented Digital Ring Gauge with a large center value, animated outer segments, scale labels, threshold-aware colors, Amber, Cyan, Green, Red, Purple and White presets, fully custom colors, and JSON import/export support.",
};

export function getLocalRelease(version?: string) {
  if (version && version !== localRelease.version) return null;
  return localRelease;
}
