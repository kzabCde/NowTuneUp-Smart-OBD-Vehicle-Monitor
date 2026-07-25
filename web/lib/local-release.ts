import type { ReleaseInfo } from "@/lib/validation/releases";

export const localRelease: ReleaseInfo = {
  version: "1.2.0",
  tagName: "v1.2.0",
  versionCode: 4,
  apkName: "NowTuneUp.apk",
  apkSize: 12625394,
  downloadUrl: "/downloads/NowTuneUp.apk",
  sha256: "7faf59524b02e69ed5d92976725a03ff39adfd68a5a30aaaaa40998440635ddf",
  minimumAndroid: "Android 8.0",
  publishedAt: "2026-07-25T10:02:16.000Z",
  releaseNotes: "Dashboard Editor release with long-press drag and drop, width and height resizing, a full widget configuration sheet, dashboard theme editor, warning and critical thresholds, five selectable analog gauge styles, and JSON file import/export.",
};

export function getLocalRelease(version?: string) {
  if (version && version !== localRelease.version) return null;
  return localRelease;
}
