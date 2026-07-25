import type { ReleaseInfo } from "@/lib/validation/releases";

export const localRelease: ReleaseInfo = {
  version: "1.2.2",
  tagName: "v1.2.2",
  versionCode: 6,
  apkName: "NowTuneUp.apk",
  apkSize: 12641778,
  downloadUrl: "/downloads/NowTuneUp.apk",
  sha256: "7c6def5add5bc459c97cab9deab220a9027f5ac2ea3a2d76a3d754e0029ba231",
  minimumAndroid: "Android 8.0",
  publishedAt: "2026-07-25T11:03:34.000Z",
  releaseNotes: "Restores Digital Ring Gauge source and adds responsive portrait and landscape dashboard layouts, independent orientation editing, copy-layout actions, compact landscape connection UI, responsive grid spacing, and JSON persistence for both orientations.",
};

export function getLocalRelease(version?: string) {
  if (version && version !== localRelease.version) return null;
  return localRelease;
}
