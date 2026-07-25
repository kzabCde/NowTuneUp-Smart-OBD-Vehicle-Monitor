import type { ReleaseInfo } from "@/lib/validation/releases";

export const localRelease: ReleaseInfo = {
  version: "1.2.1",
  tagName: "v1.2.1",
  versionCode: 5,
  apkName: "NowTuneUp.apk",
  apkSize: 12641778,
  downloadUrl: "/downloads/NowTuneUp.apk",
  sha256: "e6d5136b4df63b60d782be0f49fdc0f9611bd2f5048e58b71f7277fea8392e1e",
  minimumAndroid: "Android 8.0",
  publishedAt: "2026-07-25T09:39:01.000Z",
  releaseNotes: "Adds a segmented Digital Ring Gauge with a large center value, animated outer segments, scale labels, threshold-aware colors, Amber, Cyan, Green, Red, Purple and White presets, fully custom colors, and JSON import/export support.",
};

export function getLocalRelease(version?: string) {
  if (version && version !== localRelease.version) return null;
  return localRelease;
}
