import type { ReleaseInfo } from "@/lib/validation/releases";

export const localRelease: ReleaseInfo = {
  version: "1.0.0",
  tagName: "v1.0.0",
  versionCode: 1,
  apkName: "NowTuneUp.apk",
  apkSize: 12461554,
  downloadUrl: "/downloads/NowTuneUp.apk",
  sha256: "3f6ee546f158f958eb67acdf8af5ce17b15adbe63feb74cfb88a7580cf024bc9",
  minimumAndroid: "Android 8.0",
  publishedAt: "2026-07-24T00:00:00.000Z",
  releaseNotes: "First public APK release of NowTuneUp.",
};

export function getLocalRelease(version?: string) {
  if (version && version !== localRelease.version) return null;
  return localRelease;
}
