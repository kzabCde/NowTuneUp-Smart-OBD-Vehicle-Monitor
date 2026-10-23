import type { ReleaseInfo } from "@/lib/validation/releases";

export const localRelease: ReleaseInfo = {
  version: "1.17.0",
  tagName: "v1.17.0",
  versionCode: 27,
  apkName: "NowTuneUp-v1.17.0-release.apk",
  apkSize: 13006746,
  downloadUrl: "https://github.com/kzabCde/NowTuneUp-Smart-OBD-Vehicle-Monitor/releases/download/v1.17.0/NowTuneUp-v1.17.0-release.apk",
  sha256: "83fe78d76aebd7795991c5b3e2041318135d58409f0f383f1e18a2ebd4f585d9",
  minimumAndroid: "Android 8.0",
  publishedAt: "2026-10-23T16:13:28.000Z",
  releaseNotes: "NowTuneUp 1.17.0 replaces the legacy fixed HUD with Custom HUD Studio: a freeform 12×24 canvas with drag, resize, independent portrait and landscape layouts, reusable OBD widgets, realtime charts, per-widget styling, Undo/Redo, profile import/export and legacy dashboard migration.",
};

export function getLocalRelease(version?: string) {
  if (version && version !== localRelease.version) return null;
  return localRelease;
}
