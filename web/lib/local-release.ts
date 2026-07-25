import type { ReleaseInfo } from "@/lib/validation/releases";

export const localRelease: ReleaseInfo = {
  version: "1.2.0",
  tagName: "v1.2.0",
  versionCode: 4,
  apkName: "NowTuneUp.apk",
  apkSize: 12641778,
  downloadUrl: "/downloads/NowTuneUp.apk",
  sha256: "15e96ca9efbb51d2b1c683a59ab345f1cdf5b923f4ea01d5bded3ccf25e81d4e",
  minimumAndroid: "Android 8.0",
  publishedAt: "2026-07-25T08:36:30.000Z",
  releaseNotes: "Dashboard Editor release with long-press drag and drop, width and height resizing, a full widget configuration sheet, dashboard theme editor, warning and critical thresholds, five selectable analog gauge styles, and JSON file import/export.",
};

export function getLocalRelease(version?: string) {
  if (version && version !== localRelease.version) return null;
  return localRelease;
}
