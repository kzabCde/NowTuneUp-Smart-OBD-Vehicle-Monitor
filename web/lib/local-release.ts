import type { ReleaseInfo } from "@/lib/validation/releases";

export const localRelease: ReleaseInfo = {
  version: "1.2.0",
  tagName: "v1.2.0",
  versionCode: 4,
  apkName: "NowTuneUp.apk",
  apkSize: 12625394,
  downloadUrl: "/downloads/NowTuneUp.apk",
  sha256: "ec835e9560708bb25f7377ba752c142c60ebf3f28459f70c2cb95e6732bc2a1b",
  minimumAndroid: "Android 8.0",
  publishedAt: "2026-07-25T06:15:56.000Z",
  releaseNotes: "Dashboard Editor release with long-press drag and drop, width and height resizing, a full widget configuration sheet, dashboard theme editor, warning and critical thresholds, five selectable analog gauge styles, and JSON file import/export.",
};

export function getLocalRelease(version?: string) {
  if (version && version !== localRelease.version) return null;
  return localRelease;
}
