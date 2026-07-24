import type { ReleaseInfo } from "@/lib/validation/releases";

export const localRelease: ReleaseInfo = {
  version: "1.0.0",
  tagName: "v1.0.0",
  versionCode: 1,
  apkName: "NowTuneUp.apk",
  apkSize: 0,
  downloadUrl: "/downloads/NowTuneUp.apk",
  sha256: null,
  minimumAndroid: "Android 8.0",
  publishedAt: "2026-07-24T00:00:00.000Z",
  releaseNotes: "First public APK release of NowTuneUp.",
};

export function getLocalRelease(version?: string) {
  if (version && version !== localRelease.version) return null;
  return localRelease;
}
